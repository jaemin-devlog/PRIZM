package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.changelog.service.ChangeLogDispatchTransaction;
import com.prizm.document.dto.response.DocumentUploadResponse;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.service.DocumentUploadService;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.ingestion.service.ClaimedProcessingJob;
import com.prizm.ingestion.service.DocumentIndexingProcessor;
import com.prizm.ingestion.service.ProcessingJobClaimService;
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
import java.util.HexFormat;
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
import org.springframework.mock.web.MockMultipartFile;
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

/** P9 after-run over the immutable P8.1 corpus, questions and ground truth. */
@ActiveProfiles("search-evaluation")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
class P9StructuredClaimSupportBenchmarkTest {

    private static final Path INPUT_PHASE = Path.of(
            "specs/PRZ-016-search-performance-v2/p8-1-judge-realistic-retrieval-stress");
    private static final Path PHASE = Path.of(
            "specs/PRZ-016-search-performance-v2/p9-structured-claim-support-eligibility");
    private static final Path JUDGE = INPUT_PHASE.resolve("dataset/judge");
    private static final Path STRESS = INPUT_PHASE.resolve("dataset/stress");
    private static final Path STORAGE_ROOT = Path.of("local/p9-storage").toAbsolutePath().normalize();
    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm_p9")
            .withUsername("prizm")
            .withPassword("p9-benchmark");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("prizm.storage.root", STORAGE_ROOT::toString);
        registry.add("prizm.change-log.scheduler.enabled", () -> "false");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired DocumentUploadService uploadService;
    @Autowired ChangeLogDispatchTransaction dispatchTransaction;
    @Autowired ProcessingJobClaimService claimService;
    @Autowired DocumentIndexingProcessor indexingProcessor;
    @Autowired EmbeddingService embeddingService;
    @Autowired EmbeddingValidator embeddingValidator;
    @Autowired VectorSearchRepository vectorRepository;
    @Autowired CompositeSearchProfile searchProfile;
    @Autowired EvidenceExpansionService expansionService;
    @Autowired SearchService searchService;

    private final ObjectMapper mapper = new ObjectMapper();
    private final GroundTruthV2Evaluator groundTruthEvaluator = new GroundTruthV2Evaluator();

    @Test
    void runsP9AgainstFrozenP81JudgeAndStress() throws Exception {
        verifyFreezeManifest();
        SeededCorpus judgeCorpus = ingest(JUDGE);
        SeededCorpus stressCorpus = ingest(STRESS);

        assertThat(stressCorpus.activeChunksByOwner().values()).allMatch(count -> count >= 25);
        assertThat(stressCorpus.activeChunksByOwner().values().stream().mapToInt(Integer::intValue).sum())
                .isGreaterThanOrEqualTo(100);
        assertThat(stressCorpus.sameSourceMultiChunkGroups()).isPositive();

        ProductionSearchDecisionTracer tracer = new ProductionSearchDecisionTracer(
                embeddingService,
                embeddingValidator,
                vectorRepository,
                searchProfile,
                expansionService,
                searchService);

        BenchmarkResult judge = evaluate("JUDGE_REALISTIC", JUDGE, judgeCorpus, tracer, true);
        BenchmarkResult stress = evaluate("RETRIEVAL_STRESS", STRESS, stressCorpus, tracer, false);
        writeArtifacts("judge-realistic", judge);
        writeArtifacts("retrieval-stress", stress);
        writeIngestionAudit(judgeCorpus, stressCorpus);

        assertThat(judge.productionParity()).isTrue();
        assertThat(stress.productionParity()).isTrue();
        assertThat(judge.ownerIsolation()).isTrue();
        assertThat(stress.ownerIsolation()).isTrue();
        assertThat(judge.activeIsolation()).isTrue();
        assertThat(stress.activeIsolation()).isTrue();
        assertThat(judge.metrics().positiveCount).isEqualTo(16);
        assertThat(judge.metrics().negativeCount).isEqualTo(20);
        assertThat(stress.metrics().positiveCount).isEqualTo(20);
        assertThat(stress.metrics().negativeCount).isEqualTo(8);
        assertDenseRegressionZero("judge-realistic", judge.metrics());
        assertDenseRegressionZero("retrieval-stress", stress.metrics());
        assertThat(judge.metrics().falsePositives).isLessThanOrEqualTo(1);
        assertThat(judge.metrics().recallAt5).isGreaterThanOrEqualTo(14);
        assertThat(judge.metrics().selectedResult).isGreaterThanOrEqualTo(14);
        assertThat(stress.metrics().falsePositives).isLessThanOrEqualTo(1);
        assertThat(stress.metrics().recallAt5).isGreaterThanOrEqualTo(18);

        System.out.println("P9_JUDGE_METRICS=" + judge.metrics().summary());
        System.out.println("P9_STRESS_METRICS=" + stress.metrics().summary());
        System.out.println("P9_RESULTS=" + PHASE.toAbsolutePath());
    }

    private void assertDenseRegressionZero(String prefix, Metrics after) throws Exception {
        JsonNode before = mapper.readTree(INPUT_PHASE.resolve(prefix + "-baseline.json").toFile())
                .path("metrics");
        ObjectNode actual = after.toJson(mapper);
        for (String metric : List.of(
                "rawDenseRecallAt1", "rawDenseRecallAt5",
                "rawDenseRecallAt10", "rawDenseRecallAt20")) {
            assertThat(actual.path(metric).asDouble())
                    .as(metric + " retrieval regression")
                    .isEqualTo(before.path(metric).asDouble());
        }
    }

    private BenchmarkResult evaluate(
            String benchmark,
            Path dataset,
            SeededCorpus corpus,
            ProductionSearchDecisionTracer tracer,
            boolean measureLatency) throws Exception {
        JsonNode questions = mapper.readTree(dataset.resolve("questions.json").toFile());
        Map<String, JsonNode> truthById = index(
                mapper.readTree(dataset.resolve("ground-truth.json").toFile()).path("queries"));
        List<Double> warmLatencies = measureLatency
                ? measureWarmProductionLatency(questions, corpus)
                : List.of();
        ArrayNode traces = mapper.createArrayNode();
        ArrayNode queryResults = mapper.createArrayNode();
        Metrics metrics = new Metrics();
        boolean productionParity = true;
        boolean ownerIsolation = true;
        boolean activeIsolation = true;

        for (JsonNode question : questions.path("questions")) {
            String id = question.path("id").asText();
            String query = question.path("query").asText();
            Long owner = corpus.ownerByKey().get(question.path("userKey").asText());
            JsonNode truth = truthById.get(id);
            List<RawDenseHit> rawDense = rawDenseRanking(owner, query);
            ExpectedDense expectedDense = expectedDense(rawDense, truth, corpus.fixtureByDocumentId());
            SearchDecisionTrace trace = tracer.trace(owner, query);
            productionParity &= trace.productionResponseMatch();
            ownerIsolation &= isOwnerIsolated(owner, trace);
            activeIsolation &= isActiveIsolated(corpus, trace);

            ObjectNode result = mapper.createObjectNode();
            result.put("id", id);
            result.put("userKey", question.path("userKey").asText());
            result.put("label", question.path("label").asText());
            result.put("type", question.path("type").asText());
            result.put("query", query);
            result.put("ownerActiveChunkCount", corpus.activeChunksByOwner().get(owner));
            result.put("responseState", trace.responseState());
            result.put("productionResponseMatch", trace.productionResponseMatch());
            result.put("expectedEvidenceRawDenseRank", expectedDense.rank());
            if (expectedDense.rank() != null) {
                result.put("expectedEvidenceRawDenseScore", expectedDense.score());
                result.put("expectedEvidenceChunkId", expectedDense.chunkId());
            } else {
                result.putNull("expectedEvidenceRawDenseScore");
                result.putNull("expectedEvidenceChunkId");
            }
            result.set("finalResults", mapper.valueToTree(trace.finalResults()));
            result.set("displayedEvidence", mapper.valueToTree(trace.localization()));

            if ("POSITIVE".equals(question.path("label").asText())) {
                SearchDecisionTrace.GroundTruthOutcome outcome = groundTruthEvaluator.evaluatePositive(
                        trace, truth, corpus.fixtureByDocumentId());
                metrics.addPositive(outcome, expectedDense);
                result.set("acceptableEvidenceSets", truth.path("acceptableEvidenceSets"));
                result.set("groundTruthOutcome", mapper.valueToTree(outcome));
                result.set("failureAudit", positiveFailureAudit(trace, outcome, expectedDense));
            } else {
                boolean falsePositive = groundTruthEvaluator.isFalsePositive(trace);
                String type = question.path("type").asText();
                String leakageMode = inactiveLeakageMode(type, falsePositive, corpus, trace);
                metrics.addNegative(type, falsePositive, classifyNegative(type, falsePositive));
                result.put("falsePositive", falsePositive);
                result.put("negativeFailureClass", classifyNegative(type, falsePositive));
                result.put("inactiveVersionOutcome", leakageMode);
                result.set("negativeEvidence", truth.path("negativeEvidence"));
                result.set("traceSignals", negativeTraceSignals(trace));
            }
            queryResults.add(result);
            ObjectNode traceEnvelope = mapper.createObjectNode();
            traceEnvelope.put("id", id);
            traceEnvelope.set("trace", mapper.valueToTree(trace));
            traces.add(traceEnvelope);
        }

        ObjectNode baseline = mapper.createObjectNode();
        baseline.put("schemaVersion", 1);
        baseline.put("phase", "PRZ-016-P9");
        baseline.put("benchmark", benchmark);
        baseline.put("executedAt", Instant.now().toString());
        baseline.put("productionBehaviorChanges", 1);
        baseline.put("userCount", corpus.ownerByKey().size());
        baseline.put("sourceDocumentCount", corpus.documentIds().size());
        baseline.put("documentVersionCount", corpus.allVersionIds().size());
        baseline.put("activeChunkCount", corpus.activeChunksByOwner().values().stream()
                .mapToInt(Integer::intValue).sum());
        baseline.set("activeChunksByUser", mapper.valueToTree(activeChunksByUser(corpus)));
        baseline.put("sameSourceMultiChunkGroupCount", corpus.sameSourceMultiChunkGroups());
        baseline.put("positiveQueryCount", metrics.positiveCount);
        baseline.put("negativeQueryCount", metrics.negativeCount);
        baseline.set("negativeTypeDistribution", mapper.valueToTree(metrics.negativeTypeCounts));
        baseline.set("metrics", metrics.toJson(mapper));
        baseline.put("ownerIsolation", ownerIsolation ? "PASS" : "FAIL");
        baseline.put("activeVersionIsolation", activeIsolation ? "PASS" : "FAIL");
        baseline.put("inactiveVersionLeakage", activeIsolation ? "0" : "DETECTED");
        baseline.put("crossUserLeakage", ownerIsolation ? "0" : "DETECTED");
        baseline.put("traceProductionParity", productionParity ? "PASS" : "FAIL");
        baseline.set("rootCauseDistribution", mapper.valueToTree(metrics.firstFailures));
        baseline.set("negativeFalsePositivesByType", mapper.valueToTree(metrics.negativeFalsePositives));
        baseline.set("negativeFailureClassDistribution", mapper.valueToTree(metrics.negativeFailureClasses));
        if (measureLatency) {
            baseline.put("latencyStatus", "LOCAL_WARM_BASELINE");
            baseline.put("warmLatencyP50Ms", percentile(warmLatencies, 0.50d));
            baseline.put("warmLatencyP95Ms", percentile(warmLatencies, 0.95d));
            baseline.put("warmLatencySampleCount", warmLatencies.size());
            baseline.put("latencyEnvironment", "single-process Spring context, Testcontainers pgvector, local Ollama BGE-M3");
        } else {
            baseline.put("latencyStatus", "NOT_MEASURED");
        }
        baseline.set("queries", queryResults);
        return new BenchmarkResult(
                baseline,
                traces,
                metrics,
                productionParity,
                ownerIsolation,
                activeIsolation);
    }

    private SeededCorpus ingest(Path dataset) throws Exception {
        JsonNode manifest = mapper.readTree(dataset.resolve("corpus-manifest.json").toFile());
        Map<String, Long> ownerByKey = new LinkedHashMap<>();
        Map<Long, Long> ownerByDocument = new LinkedHashMap<>();
        Map<Long, String> fixtureByDocument = new LinkedHashMap<>();
        Set<Long> documentIds = new LinkedHashSet<>();
        Set<Long> allVersionIds = new LinkedHashSet<>();
        Set<Long> inactiveVersionIds = new LinkedHashSet<>();
        Set<Long> inactiveChunkIds = new LinkedHashSet<>();

        for (JsonNode user : manifest.path("users")) {
            Long owner = jdbc.queryForObject("""
                    INSERT INTO users(email, password_hash, role, enabled)
                    VALUES (?, '{noop}p81-evaluation', 'USER', TRUE)
                    RETURNING id
                    """, Long.class, user.path("email").asText());
            ownerByKey.put(user.path("userKey").asText(), owner);
            for (JsonNode document : user.path("documents")) {
                String activeFixture = document.path("fixture").asText();
                String firstFixture = document.hasNonNull("inactiveFixture")
                        ? document.path("inactiveFixture").asText()
                        : activeFixture;
                DocumentUploadResponse first = uploadService.upload(
                        owner,
                        document.path("title").asText(),
                        DocumentType.valueOf(document.path("documentType").asText()),
                        multipart(dataset.resolve(firstFixture)));
                process(first.versionId());
                documentIds.add(first.documentId());
                allVersionIds.add(first.versionId());
                ownerByDocument.put(first.documentId(), owner);
                DocumentUploadResponse active = first;
                if (document.hasNonNull("inactiveFixture")) {
                    inactiveVersionIds.add(first.versionId());
                    inactiveChunkIds.addAll(chunkIds(first.versionId()));
                    active = uploadService.uploadVersion(owner, first.documentId(), multipart(dataset.resolve(activeFixture)));
                    process(active.versionId());
                    allVersionIds.add(active.versionId());
                }
                fixtureByDocument.put(active.documentId(), activeFixture);
            }
        }

        Map<Long, Integer> activeChunksByOwner = new LinkedHashMap<>();
        for (Long owner : ownerByKey.values()) {
            activeChunksByOwner.put(owner, jdbc.queryForObject("""
                    SELECT count(*)
                    FROM document_chunks chunk
                    JOIN document_versions version ON version.id = chunk.document_version_id
                    JOIN documents document ON document.id = version.document_id
                      AND document.active_version_id = version.id
                    WHERE document.owner_user_id = ?
                      AND version.owner_user_id = ?
                      AND chunk.owner_user_id = ?
                      AND version.status = 'ACTIVE'
                    """, Integer.class, owner, owner, owner));
        }
        int sameSourceMultiChunkGroups = jdbc.queryForObject("""
                SELECT count(*) FROM (
                    SELECT chunk.document_version_id, chunk.source_type, chunk.source_index
                    FROM document_chunks chunk
                    JOIN document_versions version ON version.id = chunk.document_version_id
                    JOIN documents document ON document.id = version.document_id
                      AND document.active_version_id = version.id
                    WHERE document.id IN (%s) AND version.status = 'ACTIVE'
                    GROUP BY chunk.document_version_id, chunk.source_type, chunk.source_index
                    HAVING count(*) > 1
                ) grouped
                """.formatted(documentIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","))),
                Integer.class,
                documentIds.toArray());
        return new SeededCorpus(
                Map.copyOf(ownerByKey),
                Map.copyOf(ownerByDocument),
                Map.copyOf(fixtureByDocument),
                Set.copyOf(documentIds),
                Set.copyOf(allVersionIds),
                Set.copyOf(inactiveVersionIds),
                Set.copyOf(inactiveChunkIds),
                Map.copyOf(activeChunksByOwner),
                sameSourceMultiChunkGroups);
    }

    private MockMultipartFile multipart(Path path) throws Exception {
        String name = path.getFileName().toString();
        String type = name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? "application/pdf" : "text/plain";
        return new MockMultipartFile("file", name, type, Files.readAllBytes(path));
    }

    private void process(Long expectedVersionId) {
        dispatchTransaction.dispatchNext();
        ClaimedProcessingJob claimed = claimService.claimNext().orElseThrow();
        assertThat(claimed.documentVersionId()).isEqualTo(expectedVersionId);
        indexingProcessor.process(claimed);
    }

    private List<Long> chunkIds(Long versionId) {
        return jdbc.queryForList(
                "SELECT id FROM document_chunks WHERE document_version_id = ? ORDER BY id",
                Long.class,
                versionId);
    }

    private List<RawDenseHit> rawDenseRanking(Long owner, String query) {
        float[] embedding = embeddingService.embed(query);
        embeddingValidator.validate(embedding);
        String vector = toVectorLiteral(embedding);
        return jdbc.query("""
                SELECT chunk.id AS chunk_id,
                       document.id AS document_id,
                       chunk.content,
                       1.0 - (chunk.embedding <=> CAST(? AS vector)) AS score
                FROM document_chunks chunk
                JOIN document_versions version ON version.id = chunk.document_version_id
                JOIN documents document ON document.id = version.document_id
                  AND document.active_version_id = version.id
                WHERE version.status = 'ACTIVE'
                  AND document.owner_user_id = ?
                  AND version.owner_user_id = ?
                  AND chunk.owner_user_id = ?
                ORDER BY chunk.embedding <=> CAST(? AS vector), chunk.id
                """, (rs, row) -> new RawDenseHit(
                        row + 1,
                        rs.getLong("chunk_id"),
                        rs.getLong("document_id"),
                        rs.getString("content"),
                        rs.getDouble("score")),
                vector, owner, owner, owner, vector);
    }

    private ExpectedDense expectedDense(
            List<RawDenseHit> ranking,
            JsonNode truth,
            Map<Long, String> fixtureByDocument) {
        if (!"SUPPORTED".equals(truth.path("expected").asText())) {
            return new ExpectedDense(null, null, null);
        }
        return ranking.stream()
                .filter(hit -> matchesAnyEvidenceSet(hit, truth.path("acceptableEvidenceSets"), fixtureByDocument))
                .findFirst()
                .map(hit -> new ExpectedDense(hit.rank(), hit.score(), hit.chunkId()))
                .orElseGet(() -> new ExpectedDense(null, null, null));
    }

    private boolean matchesAnyEvidenceSet(
            RawDenseHit hit,
            JsonNode evidenceSets,
            Map<Long, String> fixtureByDocument) {
        for (JsonNode evidenceSet : evidenceSets) {
            if (!Objects.equals(
                    evidenceSet.path("documentFixture").asText(),
                    fixtureByDocument.get(hit.documentId()))) {
                continue;
            }
            String content = normalize(hit.content());
            boolean allClauses = true;
            for (JsonNode clause : evidenceSet.path("requiredClauses")) {
                boolean anchorMatched = false;
                for (JsonNode anchor : clause.path("anchorAny")) {
                    if (content.contains(normalize(anchor.asText()))) {
                        anchorMatched = true;
                        break;
                    }
                }
                if (!anchorMatched) {
                    allClauses = false;
                    break;
                }
            }
            if (allClauses) {
                return true;
            }
        }
        return false;
    }

    private List<Double> measureWarmProductionLatency(JsonNode questions, SeededCorpus corpus) {
        for (JsonNode question : questions.path("questions")) {
            searchService.searchCareerEvidenceV2(
                    corpus.ownerByKey().get(question.path("userKey").asText()),
                    question.path("query").asText());
        }
        List<Double> samples = new ArrayList<>();
        for (JsonNode question : questions.path("questions")) {
            long started = System.nanoTime();
            searchService.searchCareerEvidenceV2(
                    corpus.ownerByKey().get(question.path("userKey").asText()),
                    question.path("query").asText());
            samples.add((System.nanoTime() - started) / 1_000_000.0d);
        }
        return List.copyOf(samples);
    }

    private boolean isOwnerIsolated(Long owner, SearchDecisionTrace trace) {
        for (SearchDecisionTrace.CandidateTrace candidate : trace.candidates()) {
            Long actual = jdbc.queryForObject("SELECT owner_user_id FROM document_chunks WHERE id = ?",
                    Long.class, candidate.chunkId());
            if (!Objects.equals(owner, actual)) {
                return false;
            }
        }
        for (SearchDecisionTrace.FinalResultTrace result : trace.finalResults()) {
            Long actual = jdbc.queryForObject("SELECT owner_user_id FROM documents WHERE id = ?",
                    Long.class, result.documentId());
            if (!Objects.equals(owner, actual)) {
                return false;
            }
        }
        return true;
    }

    private boolean isActiveIsolated(SeededCorpus corpus, SearchDecisionTrace trace) {
        return trace.candidates().stream().noneMatch(candidate ->
                        corpus.inactiveVersionIds().contains(candidate.documentVersionId()))
                && trace.localization().stream().noneMatch(evidence ->
                        corpus.inactiveChunkIds().contains(evidence.evidenceChunkId()));
    }

    private String inactiveLeakageMode(
            String type,
            boolean falsePositive,
            SeededCorpus corpus,
            SearchDecisionTrace trace) {
        if (!"INACTIVE_VERSION_ONLY".equals(type)) {
            return "NOT_APPLICABLE";
        }
        boolean rowLeak = trace.candidates().stream().anyMatch(candidate ->
                        corpus.inactiveVersionIds().contains(candidate.documentVersionId()))
                || trace.localization().stream().anyMatch(evidence ->
                        corpus.inactiveChunkIds().contains(evidence.evidenceChunkId()));
        if (rowLeak) {
            return "INACTIVE_ROW_OR_CHUNK_LEAKAGE";
        }
        return falsePositive ? "ACTIVE_SEMANTIC_FALSE_POSITIVE" : "NO_LEAKAGE";
    }

    private ObjectNode positiveFailureAudit(
            SearchDecisionTrace trace,
            SearchDecisionTrace.GroundTruthOutcome outcome,
            ExpectedDense expectedDense) {
        ObjectNode node = mapper.createObjectNode();
        node.put("firstFailureStage", outcome.firstFailureStage().name());
        node.put("rawDenseRank", expectedDense.rank());
        if (expectedDense.score() == null) {
            node.putNull("rawDenseScore");
        } else {
            node.put("rawDenseScore", expectedDense.score());
        }
        node.set("groundTruthDiagnostics", mapper.valueToTree(outcome.diagnostics()));
        List<SearchDecisionTrace.CandidateGroupTrace> groups = trace.sourceConsolidation().stream()
                .filter(group -> expectedDense.chunkId() != null
                        && group.memberChunkIds().contains(expectedDense.chunkId()))
                .toList();
        node.set("sourceConsolidationGroups", mapper.valueToTree(groups));
        node.set("finalResults", mapper.valueToTree(trace.finalResults()));
        node.set("displayedEvidence", mapper.valueToTree(trace.localization()));
        return node;
    }

    private ArrayNode negativeTraceSignals(SearchDecisionTrace trace) {
        ArrayNode signals = mapper.createArrayNode();
        trace.candidates().stream().limit(5).forEach(candidate -> {
            ObjectNode signal = mapper.createObjectNode();
            signal.put("chunkId", candidate.chunkId());
            signal.put("firstFailureStage", candidate.firstFailureStage().name());
            signal.put("firstFailureReason", candidate.firstFailureReason());
            signal.set("decisions", mapper.valueToTree(candidate.decisions()));
            signals.add(signal);
        });
        return signals;
    }

    private static String classifyNegative(String type, boolean falsePositive) {
        if (!falsePositive) {
            return "NONE";
        }
        return switch (type) {
            case "NEGATED" -> "POLARITY_FAILURE";
            case "NOT_ADOPTED", "PROTOTYPE_ONLY" -> "ADOPTION_STATE_FAILURE";
            case "HISTORICAL_ONLY", "INACTIVE_VERSION_ONLY" -> "HISTORICAL_STATE_FAILURE";
            case "CROSS_USER_ONLY" -> "ACTOR_FAILURE";
            case "WRONG_NUMBER" -> "NUMERIC_VALUE_FAILURE";
            case "WRONG_METRIC" -> "METRIC_BINDING_FAILURE";
            case "RELATED_BUT_NOT_SUPPORTED" -> "RELATEDNESS_ONLY_FAILURE";
            default -> "OTHER";
        };
    }

    private void writeArtifacts(String prefix, BenchmarkResult result) throws Exception {
        Files.writeString(
                PHASE.resolve(prefix + "-baseline.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.baseline()) + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(
                PHASE.resolve(prefix + "-decision-traces.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(result.traces()) + "\n",
                StandardCharsets.UTF_8);
    }

    private void writeIngestionAudit(SeededCorpus judge, SeededCorpus stress) throws Exception {
        ObjectNode audit = mapper.createObjectNode();
        audit.put("schemaVersion", 1);
        audit.put("productionUploadService", DocumentUploadService.class.getName());
        audit.put("changeLogDispatch", ChangeLogDispatchTransaction.class.getName());
        audit.put("processingJobClaim", ProcessingJobClaimService.class.getName());
        audit.put("indexingProcessor", DocumentIndexingProcessor.class.getName());
        audit.put("directChunkSeed", false);
        audit.put("synchronousEvaluationOrchestration", true);
        audit.set("judgeActiveChunksByUser", mapper.valueToTree(activeChunksByUser(judge)));
        audit.set("stressActiveChunksByUser", mapper.valueToTree(activeChunksByUser(stress)));
        audit.put("judgeInactiveVersionCount", judge.inactiveVersionIds().size());
        audit.put("stressSameSourceMultiChunkGroups", stress.sameSourceMultiChunkGroups());
        Files.writeString(
                PHASE.resolve("ingestion-audit.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(audit) + "\n",
                StandardCharsets.UTF_8);
    }

    private Map<String, Integer> activeChunksByUser(SeededCorpus corpus) {
        Map<String, Integer> result = new LinkedHashMap<>();
        corpus.ownerByKey().forEach((key, owner) -> result.put(key, corpus.activeChunksByOwner().get(owner)));
        return result;
    }

    private void verifyFreezeManifest() throws Exception {
        JsonNode manifest = mapper.readTree(INPUT_PHASE.resolve("freeze-manifest.json").toFile());
        int immutableInputMismatches = 0;
        int productionSourceChangedFiles = 0;
        for (JsonNode file : manifest.path("files")) {
            Path path = Path.of(file.path("path").asText()).toAbsolutePath().normalize();
            String actual = sha256(path);
            if (!actual.equals(file.path("sha256").asText())) {
                if ("productionSearchSource".equals(file.path("group").asText())) {
                    productionSourceChangedFiles++;
                } else {
                    immutableInputMismatches++;
                }
            }
        }
        ObjectNode audit = mapper.createObjectNode();
        audit.put("schemaVersion", 1);
        audit.put("frozenPhase", "PRZ-016-P8.1");
        audit.put("immutableDatasetAndRunnerMismatchCount", immutableInputMismatches);
        audit.put("productionSourceChangedFileCount", productionSourceChangedFiles);
        audit.put("productionSourceDeltaExpectedForP9", true);
        Files.writeString(
                PHASE.resolve("freeze-audit.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(audit) + "\n",
                StandardCharsets.UTF_8);
        if (immutableInputMismatches != 0) {
            throw new IllegalStateException(
                    "Frozen P8.1 dataset or runner changed: " + immutableInputMismatches);
        }
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static String toVectorLiteral(float[] embedding) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (!Float.isFinite(embedding[index])) {
                throw new IllegalArgumentException("Embedding values must be finite");
            }
            if (index > 0) {
                result.append(',');
            }
            result.append(embedding[index]);
        }
        return result.append(']').toString();
    }

    private static Map<String, JsonNode> index(JsonNode values) {
        Map<String, JsonNode> result = new HashMap<>();
        values.forEach(value -> result.put(value.path("id").asText(), value));
        return Map.copyOf(result);
    }

    private static double percentile(List<Double> values, double percentile) {
        List<Double> ordered = values.stream().sorted().toList();
        int index = Math.max(0, (int) Math.ceil(percentile * ordered.size()) - 1);
        return ordered.get(index);
    }

    private record RawDenseHit(int rank, Long chunkId, Long documentId, String content, double score) {
    }

    private record ExpectedDense(Integer rank, Double score, Long chunkId) {
    }

    private record SeededCorpus(
            Map<String, Long> ownerByKey,
            Map<Long, Long> ownerByDocumentId,
            Map<Long, String> fixtureByDocumentId,
            Set<Long> documentIds,
            Set<Long> allVersionIds,
            Set<Long> inactiveVersionIds,
            Set<Long> inactiveChunkIds,
            Map<Long, Integer> activeChunksByOwner,
            int sameSourceMultiChunkGroups) {
    }

    private record BenchmarkResult(
            ObjectNode baseline,
            ArrayNode traces,
            Metrics metrics,
            boolean productionParity,
            boolean ownerIsolation,
            boolean activeIsolation) {
    }

    private static final class Metrics {
        private int positiveCount;
        private int negativeCount;
        private int denseAt1;
        private int denseAt5;
        private int denseAt10;
        private int denseAt20;
        private int candidateAt20;
        private int postSource;
        private int postEligibility;
        private int postQuery;
        private int top1;
        private int recallAt5;
        private int selectedResult;
        private int displayedEvidence;
        private int localization;
        private int falsePositives;
        private final List<Integer> expectedRanks = new ArrayList<>();
        private final Map<String, Integer> firstFailures = new LinkedHashMap<>();
        private final Map<String, Integer> negativeTypeCounts = new LinkedHashMap<>();
        private final Map<String, Integer> negativeFalsePositives = new LinkedHashMap<>();
        private final Map<String, Integer> negativeFailureClasses = new LinkedHashMap<>();

        private void addPositive(
                SearchDecisionTrace.GroundTruthOutcome outcome,
                ExpectedDense expectedDense) {
            positiveCount++;
            if (expectedDense.rank() != null) {
                expectedRanks.add(expectedDense.rank());
                denseAt1 += expectedDense.rank() <= 1 ? 1 : 0;
                denseAt5 += expectedDense.rank() <= 5 ? 1 : 0;
                denseAt10 += expectedDense.rank() <= 10 ? 1 : 0;
                denseAt20 += expectedDense.rank() <= 20 ? 1 : 0;
            }
            candidateAt20 += outcome.candidateRecallAt20() ? 1 : 0;
            postSource += outcome.postSourceRetention() ? 1 : 0;
            postEligibility += outcome.postEligibilityRetention() ? 1 : 0;
            postQuery += outcome.postQueryConsolidationRetention() ? 1 : 0;
            top1 += outcome.top1() ? 1 : 0;
            recallAt5 += outcome.finalRecallAt5() ? 1 : 0;
            selectedResult += outcome.selectedResultCorrect() ? 1 : 0;
            displayedEvidence += outcome.displayedEvidenceCorrect() ? 1 : 0;
            localization += outcome.localizationCorrect() ? 1 : 0;
            firstFailures.merge(outcome.firstFailureStage().name(), 1, Integer::sum);
        }

        private void addNegative(String type, boolean falsePositive, String failureClass) {
            negativeCount++;
            falsePositives += falsePositive ? 1 : 0;
            negativeTypeCounts.merge(type, 1, Integer::sum);
            negativeFalsePositives.merge(type, falsePositive ? 1 : 0, Integer::sum);
            negativeFailureClasses.merge(failureClass, 1, Integer::sum);
        }

        private ObjectNode toJson(ObjectMapper mapper) {
            ObjectNode node = mapper.createObjectNode();
            node.put("rawDenseRecallAt1", ratio(denseAt1, positiveCount));
            node.put("rawDenseRecallAt5", ratio(denseAt5, positiveCount));
            node.put("rawDenseRecallAt10", ratio(denseAt10, positiveCount));
            node.put("rawDenseRecallAt20", ratio(denseAt20, positiveCount));
            node.put("candidateRecallAt20", ratio(candidateAt20, positiveCount));
            node.put("medianExpectedEvidenceRank", rankPercentile(0.50d));
            node.put("p90ExpectedEvidenceRank", rankPercentile(0.90d));
            node.put("postSourceConsolidationRetention", ratio(postSource, positiveCount));
            node.put("postEligibilityRetention", ratio(postEligibility, positiveCount));
            node.put("postQueryConsolidationRetention", ratio(postQuery, positiveCount));
            node.put("top1", ratio(top1, positiveCount));
            node.put("recallAt5", ratio(recallAt5, positiveCount));
            node.put("selectedResultCorrectness", ratio(selectedResult, positiveCount));
            node.put("displayedEvidenceCorrectness", ratio(displayedEvidence, positiveCount));
            node.put("localizationCorrectness", ratio(localization, positiveCount));
            node.put("negativeFpr", ratio(falsePositives, negativeCount));
            node.put("falsePositiveCount", falsePositives);
            node.put("positiveCount", positiveCount);
            node.put("negativeCount", negativeCount);
            return node;
        }

        private int rankPercentile(double percentile) {
            if (expectedRanks.isEmpty()) {
                return 0;
            }
            List<Integer> ordered = expectedRanks.stream().sorted().toList();
            int index = Math.max(0, (int) Math.ceil(percentile * ordered.size()) - 1);
            return ordered.get(index);
        }

        private String summary() {
            return String.format(Locale.ROOT,
                    "Dense@20=%.4f Top1=%.4f Recall@5=%.4f NegativeFPR=%.4f",
                    ratio(denseAt20, positiveCount),
                    ratio(top1, positiveCount),
                    ratio(recallAt5, positiveCount),
                    ratio(falsePositives, negativeCount));
        }

        private static double ratio(int numerator, int denominator) {
            return denominator == 0 ? 0.0d : (double) numerator / denominator;
        }
    }
}
