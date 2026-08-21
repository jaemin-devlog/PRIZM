package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.service.IndexedChunk;
import com.prizm.ingestion.service.TextChunk;
import com.prizm.ingestion.service.TextChunker;
import com.prizm.search.evaluation.trace.GroundTruthV2Evaluator;
import com.prizm.search.evaluation.trace.ProductionSearchDecisionTracer;
import com.prizm.search.evaluation.trace.SearchDecisionTrace;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.service.EvidenceExpansionService;
import com.prizm.search.service.SearchService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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

/** Executes the frozen P8 corpus against the unchanged production search implementation. */
@ActiveProfiles("search-evaluation")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
class FreshGeneralizationV2BenchmarkTest {

    private static final Path PHASE = Path.of(
            "specs/PRZ-016-search-performance-v2/fresh-generalization-evaluation-v2");
    private static final Path DATASET = PHASE.resolve("dataset");
    private static final Path STORAGE_ROOT = Path.of("local/p8-storage").toAbsolutePath().normalize();
    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm_fresh_generalization_v2")
            .withUsername("prizm")
            .withPassword("fresh-generalization-v2");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("prizm.storage.root", STORAGE_ROOT::toString);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired EmbeddingService embeddingService;
    @Autowired EmbeddingValidator embeddingValidator;
    @Autowired TextChunker textChunker;
    @Autowired DocumentChunkRepository chunkRepository;
    @Autowired VectorSearchRepository vectorRepository;
    @Autowired CompositeSearchProfile profile;
    @Autowired EvidenceExpansionService expansionService;
    @Autowired SearchService searchService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void runsFreshGeneralizationV2() throws Exception {
        verifyFreezeManifest();
        JsonNode corpus = mapper.readTree(DATASET.resolve("corpus-manifest.json").toFile());
        JsonNode questions = mapper.readTree(DATASET.resolve("questions.json").toFile());
        JsonNode groundTruth = mapper.readTree(DATASET.resolve("ground-truth.json").toFile());
        SeededCorpus seeded = seed(corpus);

        ProductionSearchDecisionTracer tracer = new ProductionSearchDecisionTracer(
                embeddingService,
                embeddingValidator,
                vectorRepository,
                profile,
                expansionService,
                searchService);
        GroundTruthV2Evaluator evaluator = new GroundTruthV2Evaluator();
        Map<String, JsonNode> truthById = index(groundTruth.path("queries"));

        ArrayNode traceOutput = mapper.createArrayNode();
        ArrayNode queryResults = mapper.createArrayNode();
        Metrics metrics = new Metrics();
        boolean ownerIsolation = true;
        boolean activeIsolation = true;
        for (JsonNode question : questions.path("questions")) {
            String id = question.path("id").asText();
            String userKey = question.path("userKey").asText();
            Long owner = seeded.ownerByKey().get(userKey);
            long started = System.nanoTime();
            SearchDecisionTrace trace = tracer.trace(owner, question.path("query").asText());
            double latencyMs = (System.nanoTime() - started) / 1_000_000.0d;
            traceOutput.add(mapper.valueToTree(Map.of("id", id, "trace", trace)));

            ownerIsolation &= trace.candidates().stream().allMatch(candidate ->
                    Objects.equals(owner, seeded.ownerByDocumentId().get(candidate.documentId())));
            activeIsolation &= trace.candidates().stream().noneMatch(candidate ->
                    seeded.inactiveVersionIds().contains(candidate.documentVersionId()));
            activeIsolation &= trace.localization().stream().noneMatch(evidence ->
                    seeded.inactiveChunkIds().contains(evidence.evidenceChunkId()));

            ObjectNode result = mapper.createObjectNode();
            result.put("id", id);
            result.put("userKey", userKey);
            result.put("label", question.path("label").asText());
            result.put("type", question.path("type").asText());
            result.put("query", question.path("query").asText());
            result.put("latencyMs", latencyMs);
            result.put("responseState", trace.responseState());
            result.put("productionResponseMatch", trace.productionResponseMatch());
            result.set("finalResults", mapper.valueToTree(trace.finalResults()));
            result.set("displayedEvidence", mapper.valueToTree(trace.localization()));

            JsonNode truth = truthById.get(id);
            if ("POSITIVE".equals(question.path("label").asText())) {
                SearchDecisionTrace.GroundTruthOutcome outcome = evaluator.evaluatePositive(
                        trace, truth, seeded.fixtureByDocumentId());
                metrics.addPositive(outcome);
                result.set("groundTruthOutcome", mapper.valueToTree(outcome));
            } else {
                boolean falsePositive = evaluator.isFalsePositive(trace);
                metrics.addNegative(question.path("type").asText(), falsePositive);
                result.put("falsePositive", falsePositive);
                result.set("negativeEvidence", truth.path("negativeEvidence"));
            }
            queryResults.add(result);
        }

        int activeChunks = jdbc.queryForObject("""
                SELECT count(*) FROM document_chunks c
                JOIN document_versions v ON v.id = c.document_version_id
                JOIN documents d ON d.id = v.document_id AND d.active_version_id = v.id
                WHERE v.status = 'ACTIVE'
                """, Integer.class);

        ObjectNode baseline = mapper.createObjectNode();
        baseline.put("schemaVersion", 2);
        baseline.put("phase", "PRZ-016-P8-FRESH-GENERALIZATION-V2");
        baseline.put("executedAt", Instant.now().toString());
        baseline.put("productionBehaviorChanges", 0);
        baseline.put("userCount", seeded.ownerByKey().size());
        baseline.put("activeDocumentCount", seeded.fixtureByDocumentId().size());
        baseline.put("documentVersionCount", seeded.allVersionIds().size());
        baseline.put("activeChunkCount", activeChunks);
        baseline.put("positiveQueryCount", metrics.positiveCount);
        baseline.put("negativeQueryCount", metrics.negativeCount);
        baseline.set("negativeTypeDistribution", mapper.valueToTree(metrics.negativeTypeCounts));
        baseline.set("metrics", metrics.toJson(mapper));
        baseline.put("ownerIsolation", ownerIsolation ? "PASS" : "FAIL");
        baseline.put("activeVersionIsolation", activeIsolation ? "PASS" : "FAIL");
        baseline.set("rootCauseDistribution", mapper.valueToTree(metrics.firstFailures));
        baseline.set("queries", queryResults);

        Files.writeString(
                PHASE.resolve("decision-traces.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(traceOutput) + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(
                PHASE.resolve("fresh-baseline.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(baseline) + "\n",
                StandardCharsets.UTF_8);

        assertThat(ownerIsolation).isTrue();
        assertThat(activeIsolation).isTrue();
        assertThat(metrics.positiveCount).isEqualTo(24);
        assertThat(metrics.negativeCount).isEqualTo(20);
        assertThat(traceOutput).hasSize(44);
        System.out.println("P8_FRESH_BASELINE=" + PHASE.resolve("fresh-baseline.json").toAbsolutePath());
        System.out.println("P8_FRESH_METRICS=" + metrics.summary());
    }

    private SeededCorpus seed(JsonNode corpus) throws Exception {
        Map<String, Long> ownerByKey = new LinkedHashMap<>();
        Map<Long, Long> ownerByDocument = new LinkedHashMap<>();
        Map<Long, String> fixtureByDocument = new LinkedHashMap<>();
        Set<Long> inactiveVersions = new LinkedHashSet<>();
        Set<Long> inactiveChunks = new LinkedHashSet<>();
        Set<Long> allVersions = new LinkedHashSet<>();

        for (JsonNode user : corpus.path("users")) {
            String userKey = user.path("userKey").asText();
            Long owner = jdbc.queryForObject("""
                    INSERT INTO users(email, password_hash, role, enabled)
                    VALUES (?, '{noop}fresh-generalization-v2', 'USER', TRUE)
                    RETURNING id
                    """, Long.class, userKey.toLowerCase(Locale.ROOT) + "@prizm.invalid");
            ownerByKey.put(userKey, owner);

            JsonNode resume = user.path("activeDocuments").get(0);
            Long resumeDocument = createDocument(owner, resume.path("title").asText(),
                    resume.path("documentType").asText());
            ownerByDocument.put(resumeDocument, owner);
            String inactiveFixture = user.path("inactiveVersion").path("fixture").asText();
            Long inactiveVersion = createVersion(owner, resumeDocument, 1, inactiveFixture, false);
            inactiveVersions.add(inactiveVersion);
            allVersions.add(inactiveVersion);
            inactiveChunks.addAll(chunkIds(inactiveVersion));
            String activeResumeFixture = resume.path("fixture").asText();
            Long activeResumeVersion = createVersion(owner, resumeDocument, 2, activeResumeFixture, true);
            allVersions.add(activeResumeVersion);
            fixtureByDocument.put(resumeDocument, activeResumeFixture);

            JsonNode portfolio = user.path("activeDocuments").get(1);
            Long portfolioDocument = createDocument(owner, portfolio.path("title").asText(),
                    portfolio.path("documentType").asText());
            ownerByDocument.put(portfolioDocument, owner);
            String portfolioFixture = portfolio.path("fixture").asText();
            Long portfolioVersion = createVersion(owner, portfolioDocument, 1, portfolioFixture, true);
            allVersions.add(portfolioVersion);
            fixtureByDocument.put(portfolioDocument, portfolioFixture);
        }
        return new SeededCorpus(
                Map.copyOf(ownerByKey),
                Map.copyOf(ownerByDocument),
                Map.copyOf(fixtureByDocument),
                Set.copyOf(inactiveVersions),
                Set.copyOf(inactiveChunks),
                Set.copyOf(allVersions));
    }

    private Long createDocument(Long owner, String title, String type) {
        return jdbc.queryForObject("""
                INSERT INTO documents(title, owner_user_id, document_type)
                VALUES (?, ?, ?)
                RETURNING id
                """, Long.class, title, owner, type);
    }

    private Long createVersion(
            Long owner,
            Long document,
            int versionNo,
            String fixture,
            boolean activate) throws Exception {
        String content = Files.readString(DATASET.resolve(fixture));
        Long version = jdbc.queryForObject("""
                INSERT INTO document_versions(
                    document_id, version_no, original_file_name, stored_file_path,
                    file_type, content_hash, status, owner_user_id
                ) VALUES (?, ?, ?, ?, 'TXT', ?, 'ACTIVE', ?)
                RETURNING id
                """,
                Long.class,
                document,
                versionNo,
                Path.of(fixture).getFileName().toString(),
                "fresh-generalization-v2/" + fixture,
                sha256(DATASET.resolve(fixture)),
                owner);
        List<IndexedChunk> chunks = new ArrayList<>();
        for (TextChunk text : textChunker.split(content)) {
            float[] embedding = embeddingService.embed(text.content());
            embeddingValidator.validate(embedding);
            chunks.add(new IndexedChunk(
                    text.chunkNo(),
                    ChunkSourceType.TEXT_CHUNK,
                    text.chunkNo(),
                    "텍스트 구간 " + text.chunkNo(),
                    text.content(),
                    embedding));
        }
        chunkRepository.replaceAll(owner, version, chunks);
        if (activate) {
            jdbc.update("UPDATE documents SET active_version_id = ?, updated_at = now() "
                    + "WHERE id = ? AND owner_user_id = ?", version, document, owner);
        }
        return version;
    }

    private Set<Long> chunkIds(Long version) {
        return new LinkedHashSet<>(jdbc.queryForList(
                "SELECT id FROM document_chunks WHERE document_version_id = ? ORDER BY id",
                Long.class,
                version));
    }

    private void verifyFreezeManifest() throws Exception {
        Path freeze = PHASE.resolve("freeze-manifest.json");
        if (!Files.isRegularFile(freeze)) {
            throw new IllegalStateException("freeze-manifest.json is required before benchmark execution");
        }
        JsonNode manifest = mapper.readTree(freeze.toFile());
        for (JsonNode file : manifest.path("files")) {
            Path path = Path.of(file.path("path").asText()).toAbsolutePath().normalize();
            String actual = sha256(path);
            if (!actual.equals(file.path("sha256").asText())) {
                throw new IllegalStateException("Frozen input changed: " + path);
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
        return Map.copyOf(result);
    }

    private record SeededCorpus(
            Map<String, Long> ownerByKey,
            Map<Long, Long> ownerByDocumentId,
            Map<Long, String> fixtureByDocumentId,
            Set<Long> inactiveVersionIds,
            Set<Long> inactiveChunkIds,
            Set<Long> allVersionIds) {
    }

    private static final class Metrics {
        private int positiveCount;
        private int negativeCount;
        private int candidateRecall;
        private int postSource;
        private int postEligibility;
        private int postQuery;
        private int recallAt5;
        private int top1;
        private int resultCorrect;
        private int evidenceCorrect;
        private int localizationCorrect;
        private int falsePositives;
        private final Map<String, Integer> firstFailures = new LinkedHashMap<>();
        private final Map<String, Integer> negativeTypeCounts = new LinkedHashMap<>();

        private void addPositive(SearchDecisionTrace.GroundTruthOutcome outcome) {
            positiveCount++;
            candidateRecall += outcome.candidateRecallAt20() ? 1 : 0;
            postSource += outcome.postSourceRetention() ? 1 : 0;
            postEligibility += outcome.postEligibilityRetention() ? 1 : 0;
            postQuery += outcome.postQueryConsolidationRetention() ? 1 : 0;
            recallAt5 += outcome.finalRecallAt5() ? 1 : 0;
            top1 += outcome.top1() ? 1 : 0;
            resultCorrect += outcome.selectedResultCorrect() ? 1 : 0;
            evidenceCorrect += outcome.displayedEvidenceCorrect() ? 1 : 0;
            localizationCorrect += outcome.localizationCorrect() ? 1 : 0;
            firstFailures.merge(outcome.firstFailureStage().name(), 1, Integer::sum);
        }

        private void addNegative(String type, boolean falsePositive) {
            negativeCount++;
            falsePositives += falsePositive ? 1 : 0;
            negativeTypeCounts.merge(type, 1, Integer::sum);
        }

        private ObjectNode toJson(ObjectMapper mapper) {
            ObjectNode node = mapper.createObjectNode();
            node.put("top1", ratio(top1, positiveCount));
            node.put("recallAt5", ratio(recallAt5, positiveCount));
            node.put("candidateRecallAt20", ratio(candidateRecall, positiveCount));
            node.put("negativeFpr", ratio(falsePositives, negativeCount));
            node.put("postSourceConsolidationRetention", ratio(postSource, positiveCount));
            node.put("postEligibilityRetention", ratio(postEligibility, positiveCount));
            node.put("postQueryConsolidationRetention", ratio(postQuery, positiveCount));
            node.put("selectedResultCorrectness", ratio(resultCorrect, positiveCount));
            node.put("displayedEvidenceCorrectness", ratio(evidenceCorrect, positiveCount));
            node.put("localizationCorrectness", ratio(localizationCorrect, positiveCount));
            node.put("top1Count", top1);
            node.put("recallAt5Count", recallAt5);
            node.put("candidateRecallAt20Count", candidateRecall);
            node.put("falsePositiveCount", falsePositives);
            return node;
        }

        private String summary() {
            return String.format(Locale.ROOT,
                    "Top1=%.4f Recall@5=%.4f CandidateRecall@20=%.4f NegativeFPR=%.4f",
                    ratio(top1, positiveCount), ratio(recallAt5, positiveCount),
                    ratio(candidateRecall, positiveCount), ratio(falsePositives, negativeCount));
        }

        private static double ratio(int numerator, int denominator) {
            return denominator == 0 ? 0.0d : (double) numerator / denominator;
        }
    }
}
