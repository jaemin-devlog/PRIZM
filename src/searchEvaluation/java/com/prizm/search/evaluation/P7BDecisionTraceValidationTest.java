package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.service.DocumentTextExtractor;
import com.prizm.ingestion.service.IndexedChunk;
import com.prizm.ingestion.service.PageText;
import com.prizm.ingestion.service.TextChunk;
import com.prizm.ingestion.service.TextChunker;
import com.prizm.search.evaluation.trace.ProductionSearchDecisionTracer;
import com.prizm.search.evaluation.trace.SearchDecisionTrace;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.profile.NaturalLanguageQueryFallback;
import com.prizm.search.profile.NumericQueryAnchors;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.service.EvidenceExpansionService;
import com.prizm.search.service.SearchService;
import com.prizm.search.service.SearchSnippetGenerator;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Validates the new general trace against the historical P7-B 14-query stage audit. */
@ActiveProfiles("search-evaluation")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
class P7BDecisionTraceValidationTest {

    private static final Path PHASE = Path.of("specs/PRZ-016-search-performance-v2");
    private static final Path P7 = PHASE.resolve("p7-cross-document-generalization-v2");
    private static final Path DATASET = P7.resolve("dataset");
    private static final Path OUTPUT = PHASE.resolve("fresh-generalization-evaluation-v2");
    private static final Set<String> TARGET_IDS = Set.of(
            "V2-U01-D01", "V2-U01-NV02", "V2-U02-D02", "V2-U02-NV02",
            "V2-U02-IP02", "V2-U02-NI01", "V2-U02-CN01", "V2-U02-CN02",
            "V2-U03-NI01", "V2-U04-D01", "V2-U04-D02", "V2-U04-IP01",
            "V2-U04-NI01", "V2-U04-CN02");
    private static final Set<String> NATURAL_LANGUAGE_FLOOR_LOSS_IDS = Set.of(
            "V2-U01-N02", "V2-U02-N02", "V2-U04-IP01");
    private static final Set<String> LOCALIZATION_FAILURE_IDS = Set.of(
            "V2-U01-NV01", "V2-U01-CN01", "V2-U02-IP02",
            "V2-U03-NV01", "V2-U03-IP01", "V2-U04-CN02");
    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm_p7b_trace_validation")
            .withUsername("prizm")
            .withPassword("p7b-trace-validation");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("prizm.storage.root", () -> Path.of("local/p8-p7-storage").toAbsolutePath().toString());
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired EmbeddingService embeddingService;
    @Autowired EmbeddingValidator embeddingValidator;
    @Autowired TextChunker textChunker;
    @Autowired DocumentTextExtractor textExtractor;
    @Autowired DocumentChunkRepository chunkRepository;
    @Autowired VectorSearchRepository vectorRepository;
    @Autowired CompositeSearchProfile profile;
    @Autowired EvidenceExpansionService expansionService;
    @Autowired SearchService searchService;
    @Autowired SearchSnippetGenerator snippetGenerator;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void traceMatchesHistoricalP7BStageAudit() throws Exception {
        JsonNode manifest = mapper.readTree(DATASET.resolve("corpus-manifest.json").toFile());
        JsonNode questions = mapper.readTree(DATASET.resolve("questions.json").toFile());
        JsonNode groundTruth = mapper.readTree(DATASET.resolve("ground-truth.json").toFile());
        JsonNode historical = mapper.readTree(
                PHASE.resolve("p7-b-stage-ceiling-audit/filter-rejection-trace-14.json").toFile());
        JsonNode raw = mapper.readTree(
                PHASE.resolve("p7-b-independent-generalization/raw-results.json").toFile());
        verifyFrozenP7Hashes();
        Seeded seeded = seed(manifest);

        ProductionSearchDecisionTracer tracer = new ProductionSearchDecisionTracer(
                embeddingService, embeddingValidator, vectorRepository, profile,
                expansionService, searchService);
        Map<String, JsonNode> truthById = index(groundTruth.path("entries"));
        Map<String, JsonNode> rawById = index(raw.path("queries"));
        Map<String, JsonNode> historicalById = index(historical.path("queries"));
        ArrayNode outputQueries = mapper.createArrayNode();
        Map<String, Integer> observed = new LinkedHashMap<>();
        Map<String, Integer> historicalCounts = new LinkedHashMap<>();
        int finalReproduction = 0;
        int productionResponseParity = 0;
        int targetEvidenceRetrieved = 0;
        int localizationFailuresRecovered = 0;
        int expectedPdfPagesRecovered = 0;

        for (JsonNode question : questions.path("questions")) {
            String id = question.path("id").asText();
            SearchDecisionTrace trace = tracer.trace(
                    seeded.ownerByKey().get(question.path("userKey").asText()),
                    question.path("query").asText());
            List<Long> frozenIds = new ArrayList<>();
            rawById.get(id).path("response").path("results")
                    .forEach(result -> frozenIds.add(result.path("chunkId").asLong()));
            List<Long> actualIds = trace.finalResults().stream()
                    .map(SearchDecisionTrace.FinalResultTrace::chunkId).toList();
            boolean reproductionMatch = frozenIds.equals(actualIds);
            finalReproduction += reproductionMatch ? 1 : 0;
            productionResponseParity += trace.productionResponseMatch() ? 1 : 0;

            ObjectNode node = mapper.createObjectNode();
            node.put("id", id);
            node.put("productionResponseMatch", trace.productionResponseMatch());
            node.put("frozenFinalReproductionMatch", reproductionMatch);
            node.set("trace", mapper.valueToTree(trace));
            if (NATURAL_LANGUAGE_FLOOR_LOSS_IDS.contains(id)) {
                SearchDecisionTrace.CandidateTrace expected = floorLossCandidate(
                        trace, truthById.get(id));
                node.set("floorLossSignals", relevanceSignals(
                        question.path("query").asText(), expected));
            }
            if (LOCALIZATION_FAILURE_IDS.contains(id)) {
                node.set("localizationWindowSignals", localizationWindowSignals(
                        question.path("query").asText(), trace));
                JsonNode truth = truthById.get(id);
                if (localizationMatches(trace, truth, false)) {
                    localizationFailuresRecovered++;
                }
                if ("PAGE".equals(truth.path("source").path("kind").asText())
                        && localizationMatches(trace, truth, true)) {
                    expectedPdfPagesRecovered++;
                }
            }
            if (TARGET_IDS.contains(id)) {
                JsonNode truth = truthById.get(id);
                List<SearchDecisionTrace.CandidateTrace> acceptable = acceptableCandidates(trace, truth);
                long historicallyTracedChunkId = historicalById.get(id).path("tracedCorrectChunkId").asLong(-1);
                SearchDecisionTrace.CandidateTrace deepest = acceptable.stream()
                        .filter(candidate -> candidate.chunkId() == historicallyTracedChunkId)
                        .findFirst()
                        .or(() -> acceptable.stream()
                                .max(java.util.Comparator.comparingInt(candidate -> depth(candidate.firstFailureStage()))))
                        .orElseThrow(() -> new IllegalStateException("Expected P7 evidence not retrieved: " + id));
                targetEvidenceRetrieved++;
                String observedReason = canonicalObservedReason(deepest);
                String expectedReason = historicalById.get(id).path("firstRejection").asText();
                observed.merge(observedReason, 1, Integer::sum);
                historicalCounts.merge(expectedReason, 1, Integer::sum);
                node.put("expectedFirstRejection", expectedReason);
                node.put("observedFirstFailureStage", deepest.firstFailureStage().name());
                node.put("observedFirstRejection", observedReason);
                node.put("traceValidationMatch", expectedReason.equals(observedReason));
                node.set("acceptableCandidateChunkIds", mapper.valueToTree(
                        acceptable.stream().map(SearchDecisionTrace.CandidateTrace::chunkId).toList()));
            }
            outputQueries.add(node);
        }

        ObjectNode report = mapper.createObjectNode();
        report.put("schemaVersion", 1);
        report.put("phase", "PRZ-016-P8-P7-B-TRACE-VALIDATION");
        report.put("executedAt", Instant.now().toString());
        report.put("queryCount", 48);
        report.put("targetPositiveFailureCount", 14);
        report.put("productionResponseParity", productionResponseParity + "/48");
        report.put("targetEvidenceRetrieved", targetEvidenceRetrieved + "/14");
        report.put("localizationFailuresRecovered",
                localizationFailuresRecovered + "/" + LOCALIZATION_FAILURE_IDS.size());
        report.put("expectedPdfPagesRecovered", expectedPdfPagesRecovered + "/2");
        report.put("frozenFinalReproduction", finalReproduction + "/48");
        report.set("historicalPrimaryRejection", mapper.valueToTree(historicalCounts));
        report.set("observedPrimaryRejection", mapper.valueToTree(observed));
        report.put("historicalAuditMatch", observed.equals(historicalCounts));
        report.set("queries", outputQueries);
        Files.createDirectories(OUTPUT);
        Files.writeString(
                OUTPUT.resolve("p7-b-trace-validation.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n",
                StandardCharsets.UTF_8);

        // Historical differences are evidence, not a reason to rewrite either the
        // frozen audit or current search behavior. The hard invariant is that the
        // observer reproduces the current production response and sees all targets.
        assertThat(productionResponseParity).isEqualTo(48);
        assertThat(targetEvidenceRetrieved).isEqualTo(14);
        assertThat(localizationFailuresRecovered).isEqualTo(LOCALIZATION_FAILURE_IDS.size());
        assertThat(expectedPdfPagesRecovered).isEqualTo(2);
        System.out.println("P8_P7B_TRACE_VALIDATION=" + OUTPUT.resolve("p7-b-trace-validation.json"));
    }

    private static boolean localizationMatches(
            SearchDecisionTrace trace,
            JsonNode truth,
            boolean requireExpectedPage) {
        List<String> anchors = new ArrayList<>();
        truth.path("acceptableAnchors").forEach(anchor -> anchors.add(anchor.asText()));
        Map<Long, SearchDecisionTrace.CandidateTrace> candidates = new HashMap<>();
        trace.candidates().forEach(candidate -> candidates.putIfAbsent(candidate.chunkId(), candidate));
        int expectedPage = truth.path("source").path("page").asInt(-1);
        for (SearchDecisionTrace.FinalResultTrace result : trace.finalResults()) {
            SearchDecisionTrace.CandidateTrace candidate = candidates.get(result.chunkId());
            if (candidate == null || anchors.stream().noneMatch(candidate.content()::contains)) {
                continue;
            }
            boolean matched = trace.localization().stream()
                    .filter(evidence -> evidence.resultRank() == result.rank())
                    .filter(evidence -> anchors.stream().anyMatch(evidence.snippet()::contains))
                    .anyMatch(evidence -> !requireExpectedPage
                            || (evidence.evidenceSourceType() == ChunkSourceType.PAGE
                                    && evidence.evidenceSourceIndex() == expectedPage));
            if (matched) {
                return true;
            }
        }
        return false;
    }

    private ArrayNode localizationWindowSignals(String query, SearchDecisionTrace trace) {
        Map<Long, SearchDecisionTrace.CandidateTrace> candidates = new HashMap<>();
        trace.candidates().forEach(candidate -> candidates.putIfAbsent(candidate.chunkId(), candidate));
        ArrayNode values = mapper.createArrayNode();
        for (SearchDecisionTrace.FinalResultTrace result : trace.finalResults()) {
            SearchDecisionTrace.CandidateTrace candidate = candidates.get(result.chunkId());
            if (candidate == null) {
                continue;
            }
            ObjectNode value = mapper.createObjectNode();
            value.put("rank", result.rank());
            value.put("chunkId", result.chunkId());
            value.set("selection", mapper.valueToTree(
                    snippetGenerator.select(query, candidate.content())));
            values.add(value);
        }
        return values;
    }

    private SearchDecisionTrace.CandidateTrace floorLossCandidate(
            SearchDecisionTrace trace,
            JsonNode truth) {
        List<String> anchors = new ArrayList<>();
        truth.path("acceptableAnchors").forEach(anchor -> anchors.add(anchor.asText()));
        if (truth.path("similarButReject").has("anchor")) {
            anchors.add(truth.path("similarButReject").path("anchor").asText());
        }
        return trace.candidates().stream()
                .filter(candidate -> anchors.stream().anyMatch(candidate.content()::contains))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Expected floor-loss evidence was not retrieved: " + truth.path("id").asText()));
    }

    @SuppressWarnings("unchecked")
    private ObjectNode relevanceSignals(
            String query,
            SearchDecisionTrace.CandidateTrace candidate) {
        try {
            Method querySignalsMethod = CompositeSearchProfile.class
                    .getDeclaredMethod("querySignals", String.class);
            querySignalsMethod.setAccessible(true);
            Object querySignals = querySignalsMethod.invoke(profile, query);
            Method candidateSignalsMethod = CompositeSearchProfile.class
                    .getDeclaredMethod("candidateSignals", String.class);
            candidateSignalsMethod.setAccessible(true);
            Object candidateSignals = candidateSignalsMethod.invoke(null, candidate.content());
            Set<String> identifiers = signalSet(querySignals, "requiredIdentifiers");
            Set<String> numbers = signalSet(querySignals, "requiredNumbers");
            Set<String> coreTerms = signalSet(querySignals, "coreTerms");
            Set<String> candidateCoreTerms = signalSet(candidateSignals, "coreTerms");
            Set<String> matchedCoreTerms = new LinkedHashSet<>(coreTerms);
            matchedCoreTerms.retainAll(candidateCoreTerms);

            ObjectNode signals = mapper.createObjectNode();
            signals.put("chunkId", candidate.chunkId());
            signals.set("requiredIdentifiers", mapper.valueToTree(identifiers));
            signals.set("requiredNumbers", mapper.valueToTree(numbers));
            signals.set("numericAnchors", mapper.valueToTree(NumericQueryAnchors.extract(query)));
            signals.set("coreTerms", mapper.valueToTree(coreTerms));
            signals.set("candidateCoreTerms", mapper.valueToTree(candidateCoreTerms));
            signals.set("matchedCoreTerms", mapper.valueToTree(matchedCoreTerms));
            signals.put("coreTermMatchCount", matchedCoreTerms.size());
            signals.put("coreTermCoverage", coreTerms.isEmpty()
                    ? 0.0d
                    : (double) matchedCoreTerms.size() / coreTerms.size());
            signals.put("directAnchor", NaturalLanguageQueryFallback.hasDirectAnchor(
                    query, candidate.content()));
            signals.put("firstFailureStage", candidate.firstFailureStage().name());
            signals.put("firstFailureReason", candidate.firstFailureReason());
            return signals;
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Composite relevance signals changed", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static Set<String> signalSet(Object signals, String accessor)
            throws ReflectiveOperationException {
        Method method = signals.getClass().getDeclaredMethod(accessor);
        method.setAccessible(true);
        return (Set<String>) method.invoke(signals);
    }

    private Seeded seed(JsonNode manifest) throws Exception {
        Map<String, Long> owners = new LinkedHashMap<>();
        for (JsonNode user : manifest.path("users")) {
            String key = user.path("userKey").asText();
            owners.put(key, createUser(key));
        }
        Map<String, Long> documents = new HashMap<>();
        // Preserve the historical insertion order and IDs used by frozen P7-B raw results.
        JsonNode inactive = manifest.path("inactiveVersionFixtures").get(0);
        Long inactiveDocument = createDocument(
                owners.get(inactive.path("userKey").asText()),
                inactive.path("documentKey").asText(),
                inactive.path("documentType").asText());
        documents.put(inactive.path("documentKey").asText(), inactiveDocument);
        createVersion(owners.get(inactive.path("userKey").asText()), inactiveDocument, 1,
                inactive.path("path").asText(), inactive.path("format").asText(), false);

        // The frozen P7-B run activated U03's replacement before inserting the other
        // users' documents. Reproduce that order so document/version/chunk IDs remain
        // comparable with raw-results.json.
        JsonNode activeReplacement = null;
        for (JsonNode document : manifest.path("activeDocuments")) {
            if (inactive.path("documentKey").asText().equals(document.path("documentKey").asText())) {
                activeReplacement = document;
                break;
            }
        }
        if (activeReplacement == null) {
            throw new IllegalStateException("Active replacement missing for inactive P7 fixture");
        }
        createVersion(
                owners.get(activeReplacement.path("userKey").asText()),
                inactiveDocument,
                2,
                activeReplacement.path("path").asText(),
                activeReplacement.path("format").asText(),
                true);

        for (JsonNode document : manifest.path("activeDocuments")) {
            String documentKey = document.path("documentKey").asText();
            if (documentKey.equals(inactive.path("documentKey").asText())) {
                continue;
            }
            Long owner = owners.get(document.path("userKey").asText());
            Long documentId = documents.get(documentKey);
            int versionNo = 1;
            if (documentId == null) {
                documentId = createDocument(owner, documentKey, document.path("documentType").asText());
                documents.put(documentKey, documentId);
            } else {
                versionNo = 2;
            }
            createVersion(owner, documentId, versionNo, document.path("path").asText(),
                    document.path("format").asText(), true);
        }
        return new Seeded(Map.copyOf(owners));
    }

    private Long createUser(String key) {
        return jdbc.queryForObject("""
                INSERT INTO users(email, password_hash, role, enabled)
                VALUES (?, '{noop}p7b-trace', 'USER', TRUE) RETURNING id
                """, Long.class, key.toLowerCase(Locale.ROOT) + "@prizm.invalid");
    }

    private Long createDocument(Long owner, String title, String type) {
        return jdbc.queryForObject("""
                INSERT INTO documents(title, owner_user_id, document_type)
                VALUES (?, ?, ?) RETURNING id
                """, Long.class, title, owner, type);
    }

    private Long createVersion(
            Long owner, Long document, int versionNo, String relativePath, String format, boolean activate)
            throws Exception {
        Path path = P7.resolve(relativePath.replace("dataset/", "dataset/"));
        byte[] bytes = Files.readAllBytes(path);
        Long version = jdbc.queryForObject("""
                INSERT INTO document_versions(
                    document_id, version_no, original_file_name, stored_file_path,
                    file_type, content_hash, status, owner_user_id
                ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?) RETURNING id
                """, Long.class, document, versionNo, path.getFileName().toString(),
                "p7b-trace/" + path.getFileName(), format, sha256(path), owner);
        DocumentFileType fileType = DocumentFileType.valueOf(format);
        List<IndexedChunk> chunks = new ArrayList<>();
        int chunkNo = 1;
        for (PageText page : textExtractor.extract(fileType, bytes)) {
            for (TextChunk text : textChunker.split(page.text())) {
                float[] embedding = embeddingService.embed(text.content());
                embeddingValidator.validate(embedding);
                ChunkSourceType sourceType = fileType == DocumentFileType.PDF
                        ? ChunkSourceType.PAGE : ChunkSourceType.TEXT_CHUNK;
                int sourceIndex = sourceType == ChunkSourceType.PAGE ? page.pageNumber() : chunkNo;
                chunks.add(new IndexedChunk(
                        chunkNo++, sourceType, sourceIndex,
                        sourceType == ChunkSourceType.PAGE
                                ? sourceIndex + "페이지" : "텍스트 구간 " + sourceIndex,
                        text.content(), embedding));
            }
        }
        chunkRepository.replaceAll(owner, version, chunks);
        if (activate) {
            jdbc.update("UPDATE documents SET active_version_id = ?, updated_at = now() "
                    + "WHERE id = ? AND owner_user_id = ?", version, document, owner);
        }
        return version;
    }

    private static List<SearchDecisionTrace.CandidateTrace> acceptableCandidates(
            SearchDecisionTrace trace, JsonNode truth) {
        List<String> anchors = new ArrayList<>();
        truth.path("acceptableAnchors").forEach(anchor -> anchors.add(anchor.asText()));
        return trace.candidates().stream()
                .filter(candidate -> anchors.stream().anyMatch(candidate.content()::contains))
                .toList();
    }

    private static String canonicalObservedReason(SearchDecisionTrace.CandidateTrace candidate) {
        return switch (candidate.firstFailureStage()) {
            case SOURCE_CONSOLIDATION -> "SOURCE_LOCATION_CONSOLIDATION";
            case QUERY_EVIDENCE_CONSOLIDATION -> "QUERY_EVIDENCE_CONSOLIDATION";
            case ELIGIBILITY -> candidate.firstFailureReason().contains("BELOW_DENSE_FLOOR")
                    ? "DENSE_SCORE_BELOW_TUNING_FLOOR" : "NEGATED_CLAIM";
            default -> candidate.firstFailureStage().name();
        };
    }

    private static int depth(SearchDecisionTrace.FirstFailureStage stage) {
        return switch (stage) {
            case RETRIEVAL -> 0;
            case SOURCE_CONSOLIDATION -> 1;
            case ELIGIBILITY -> 2;
            case QUERY_EVIDENCE_CONSOLIDATION -> 3;
            case RANKING -> 4;
            case POST_FILTER -> 5;
            case LOCALIZATION -> 6;
            case NONE -> 7;
        };
    }

    private void verifyFrozenP7Hashes() throws Exception {
        JsonNode freeze = mapper.readTree(P7.resolve("freeze-manifest.json").toFile());
        for (JsonNode file : freeze.path("files")) {
            Path path = P7.resolve(file.path("path").asText()).normalize();
            if (Files.isRegularFile(path)) {
                assertThat(sha256(path)).isEqualTo(file.path("sha256").asText());
            }
        }
    }

    private static String sha256(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static Map<String, JsonNode> index(JsonNode values) {
        Map<String, JsonNode> result = new HashMap<>();
        values.forEach(value -> result.put(value.path("id").asText(), value));
        return result;
    }

    private record Seeded(Map<String, Long> ownerByKey) {
    }
}
