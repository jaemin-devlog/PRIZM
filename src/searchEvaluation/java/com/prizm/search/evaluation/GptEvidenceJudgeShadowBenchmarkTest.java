package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.dto.response.CareerEvidenceSearchV2Response;
import com.prizm.search.evaluation.judge.EvidenceJudgeCall;
import com.prizm.search.evaluation.judge.EvidenceJudgeCandidate;
import com.prizm.search.evaluation.judge.EvidenceJudgeProtocolException;
import com.prizm.search.evaluation.judge.EvidenceJudgeVerifier;
import com.prizm.search.evaluation.judge.EvidenceJudgeVerifier.VerifiedEvidenceDecision;
import com.prizm.search.evaluation.judge.JdbcEvidenceJudgeVerificationRepository;
import com.prizm.search.evaluation.judge.OpenAiResponsesEvidenceJudgeClient;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.repository.VectorSearchResult;
import com.prizm.search.service.SearchService;
import com.prizm.search.service.SearchSnippetGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Live, read-only GPT-J1 shadow benchmark. It is never part of the production request path. */
@ActiveProfiles("local")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.flyway.enabled=false",
            "prizm.change-log.scheduler.enabled=false",
            "prizm.ingestion.worker-enabled=false",
            "prizm.cleanup.worker-enabled=false"
        })
class GptEvidenceJudgeShadowBenchmarkTest {

    private static final long OWNER_ID = 1L;
    private static final int CANDIDATE_LIMIT = 10;
    private static final Path DATASET = Path.of(
            "specs/PRZ-016-search-performance-v2/p5-final-holdout/holdout-dataset.json");
    private static final Path GROUND_TRUTH = Path.of(
            "specs/PRZ-016-search-performance-v2/p5-final-holdout/holdout-ground-truth.json");
    private static final Path PRODUCTION_SEARCH = Path.of("src/main/java/com/prizm/search");
    private static final String EXPECTED_DATASET_HASH =
            "4e28c0fb2b99b31f15640eb39776e04a533938b63db02e1ba0bfec22168532aa";
    private static final String EXPECTED_GROUND_TRUTH_HASH =
            "da915300974c27967e859c0586ec1f76347c314c62f0ca57f77b5b64c3e0180d";
    private static final String EXPECTED_PRODUCTION_HASH =
            "32d8e31d7f5eeb3bf64033ce2b9db7c58347cbfe3d6f540b792e44c04951df31";
    private static final int EXPECTED_PRODUCTION_FILES = 30;

    @Autowired
    SearchService searchService;

    @Autowired
    EmbeddingService embeddingService;

    @Autowired
    EmbeddingValidator embeddingValidator;

    @Autowired
    VectorSearchRepository vectorSearchRepository;

    @Autowired
    SearchSnippetGenerator snippetGenerator;

    @Autowired
    JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void comparesFrozenP5WithVerifiedGptEvidenceJudge() throws Exception {
        FrozenState before = frozenState();
        assertFrozen(before);
        JsonNode dataset = objectMapper.readTree(DATASET.toFile());
        JsonNode groundTruth = objectMapper.readTree(GROUND_TRUTH.toFile());
        assertThat(dataset.path("status").asText()).isEqualTo("FROZEN_PRE_SEARCH");
        assertThat(groundTruth.path("status").asText()).isEqualTo("FROZEN_PRE_SEARCH");
        assertThat(groundTruth.path("ownerId").asLong()).isEqualTo(OWNER_ID);

        OpenAiResponsesEvidenceJudgeClient client =
                OpenAiResponsesEvidenceJudgeClient.fromEnvironment(objectMapper);
        EvidenceJudgeVerifier verifier = new EvidenceJudgeVerifier(
                new JdbcEvidenceJudgeVerificationRepository(jdbcTemplate));
        Map<String, JsonNode> positiveGroundTruth = positiveGroundTruth(groundTruth);
        List<QueryOutcome> outcomes = new ArrayList<>();

        int index = 0;
        for (JsonNode definition : dataset.path("queries")) {
            index++;
            String id = definition.path("id").asText();
            String query = definition.path("query").asText();
            boolean positive = "EVIDENCE_EXISTS".equals(definition.path("expected").asText());
            JsonNode expected = positiveGroundTruth.get(id);

            CareerEvidenceSearchV2Response baseline = searchService.searchCareerEvidenceV2(OWNER_ID, query);
            Integer baselineCorrectRank = positive ? correctRank(baseline.results(), expected) : null;
            boolean baselineFalsePositive = !positive && !baseline.results().isEmpty();

            float[] embedding = embeddingService.embed(query);
            embeddingValidator.validate(embedding);
            List<VectorSearchResult> rawCandidates = vectorSearchRepository
                    .findCareerEvidenceCandidates(OWNER_ID, embedding)
                    .stream()
                    .limit(CANDIDATE_LIMIT)
                    .toList();
            List<EvidenceJudgeCandidate> submitted = rawCandidates.stream()
                    .map(candidate -> new EvidenceJudgeCandidate(
                            candidate.chunkId(),
                            snippetGenerator.generate(query, candidate.content())))
                    .toList();

            EvidenceJudgeCall call = null;
            VerifiedEvidenceDecision verified;
            String error = null;
            if (submitted.isEmpty()) {
                verified = verifier.verify(
                        OWNER_ID,
                        submitted,
                        new com.prizm.search.evaluation.judge.EvidenceJudgeDecision(
                                false, null, null, "No retrieval candidates"));
            } else {
                try {
                    call = client.judge(query, submitted);
                    verified = verifier.verify(OWNER_ID, submitted, call.decision());
                } catch (EvidenceJudgeProtocolException exception) {
                    verified = null;
                    error = exception.getMessage();
                }
            }

            Long selectedChunkId = verified == null || !verified.evidenceFound()
                    ? null
                    : verified.chunkId();
            VectorSearchResult selected = selectedChunkId == null
                    ? null
                    : rawCandidates.stream()
                            .filter(candidate -> candidate.chunkId().equals(selectedChunkId))
                            .findFirst()
                            .orElse(null);
            boolean judgeCorrect = positive
                    && selected != null
                    && matches(selected, verified.evidenceSentence(), expected);
            boolean judgeFalsePositive = !positive && selected != null;
            outcomes.add(new QueryOutcome(
                    id,
                    query,
                    positive,
                    baseline.state().name(),
                    baselineCorrectRank,
                    baselineFalsePositive,
                    rawCandidates,
                    submitted,
                    call,
                    verified,
                    error,
                    judgeCorrect,
                    judgeFalsePositive));
            System.out.printf(
                    Locale.ROOT,
                    "GPT-J1 %02d/48 %s baseline=%s judge=%s verification=%s error=%s%n",
                    index,
                    id,
                    baselineCorrectRank,
                    judgeCorrect,
                    verified == null ? "NOT_RUN" : verified.status(),
                    error == null ? "none" : "present");
        }

        FrozenState after = frozenState();
        assertThat(after).isEqualTo(before);
        ComparisonSummary summary = summarize(outcomes);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("phase", "PRZ-016-GPT-J1");
        report.put("executedAt", Instant.now().toString());
        report.put("environment", Map.of(
                "database", "external PostgreSQL+pgvector on configured local datasource",
                "embedding", "external Ollama bge-m3",
                "openAiEndpoint", OpenAiResponsesEvidenceJudgeClient.DEFAULT_ENDPOINT.toString(),
                "candidateLimit", CANDIDATE_LIMIT,
                "ownerId", OWNER_ID,
                "databaseMutation", 0,
                "productionMutation", 0));
        report.put("frozenBefore", before);
        report.put("frozenAfter", after);
        report.put("summary", summary);
        report.put("queries", outcomes.stream().map(this::diagnostic).toList());
        Path output = outputPath();
        Files.createDirectories(output.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(summary));
    }

    private ComparisonSummary summarize(List<QueryOutcome> outcomes) {
        List<QueryOutcome> positives = outcomes.stream().filter(QueryOutcome::positive).toList();
        List<QueryOutcome> negatives = outcomes.stream().filter(outcome -> !outcome.positive()).toList();
        MetricSummary baseline = new MetricSummary(
                ratio(positives, outcome -> Objects.equals(outcome.baselineCorrectRank(), 1)),
                ratio(positives, outcome -> rankAtMost(outcome.baselineCorrectRank(), 3)),
                ratio(positives, outcome -> rankAtMost(outcome.baselineCorrectRank(), 5)),
                positives.stream().mapToDouble(outcome -> reciprocal(outcome.baselineCorrectRank())).sum()
                        / positives.size(),
                ratio(negatives, QueryOutcome::baselineFalsePositive));
        MetricSummary judge = new MetricSummary(
                ratio(positives, QueryOutcome::judgeCorrect),
                ratio(positives, QueryOutcome::judgeCorrect),
                ratio(positives, QueryOutcome::judgeCorrect),
                ratio(positives, QueryOutcome::judgeCorrect),
                ratio(negatives, QueryOutcome::judgeFalsePositive));
        long errors = outcomes.stream().filter(outcome -> outcome.error() != null).count();
        long verificationRejections = outcomes.stream()
                .filter(outcome -> outcome.verified() != null)
                .filter(outcome -> outcome.verified().status()
                        != EvidenceJudgeVerifier.VerificationStatus.ACCEPTED)
                .filter(outcome -> outcome.verified().status()
                        != EvidenceJudgeVerifier.VerificationStatus.MODEL_NONE)
                .count();
        long positiveRegressions = positives.stream()
                .filter(outcome -> outcome.baselineCorrectRank() != null && !outcome.judgeCorrect())
                .count();
        String verdict = errors == 0
                        && judge.negativeFalsePositiveRate() == 0.0d
                        && positiveRegressions == 0
                ? "GO_FOR_UNSEEN_HOLDOUT"
                : "NO_GO";
        return new ComparisonSummary(
                outcomes.size(),
                positives.size(),
                negatives.size(),
                baseline,
                judge,
                errors,
                verificationRejections,
                positiveRegressions,
                outcomes.stream().map(QueryOutcome::call).filter(Objects::nonNull)
                        .mapToLong(EvidenceJudgeCall::inputTokens).sum(),
                outcomes.stream().map(QueryOutcome::call).filter(Objects::nonNull)
                        .mapToLong(EvidenceJudgeCall::outputTokens).sum(),
                verdict);
    }

    private Map<String, Object> diagnostic(QueryOutcome outcome) {
        Map<String, Object> diagnostic = new LinkedHashMap<>();
        diagnostic.put("id", outcome.id());
        diagnostic.put("query", outcome.query());
        diagnostic.put("positive", outcome.positive());
        diagnostic.put("baselineState", outcome.baselineState());
        diagnostic.put("baselineCorrectRank", outcome.baselineCorrectRank());
        diagnostic.put("baselineFalsePositive", outcome.baselineFalsePositive());
        diagnostic.put("submittedCandidates", outcome.submittedCandidates());
        diagnostic.put("call", outcome.call());
        diagnostic.put("verified", outcome.verified());
        diagnostic.put("error", outcome.error());
        diagnostic.put("judgeCorrect", outcome.judgeCorrect());
        diagnostic.put("judgeFalsePositive", outcome.judgeFalsePositive());
        return diagnostic;
    }

    private Integer correctRank(List<CareerEvidenceSearchResponse> results, JsonNode expected) {
        for (int index = 0; index < results.size(); index++) {
            CareerEvidenceSearchResponse result = results.get(index);
            Long chunkId = result.evidenceChunkId() == null ? result.chunkId() : result.evidenceChunkId();
            int page = result.evidenceChunkId() == null ? result.sourceIndex() : result.evidenceSourceIndex();
            if (matches(
                    result.documentId(),
                    result.documentVersionId(),
                    chunkId,
                    page,
                    result.snippet() + "\n" + result.content(),
                    expected)) {
                return index + 1;
            }
        }
        return null;
    }

    private boolean matches(VectorSearchResult candidate, String evidenceSentence, JsonNode expected) {
        return matches(
                candidate.documentId(),
                candidate.documentVersionId(),
                candidate.chunkId(),
                candidate.sourceIndex(),
                evidenceSentence + "\n" + candidate.content(),
                expected);
    }

    private boolean matches(
            Long documentId,
            Long versionId,
            Long chunkId,
            int sourceIndex,
            String searchable,
            JsonNode expected) {
        if (expected == null) {
            return false;
        }
        String normalized = normalize(searchable);
        for (JsonNode acceptable : expected.path("acceptableEvidence")) {
            if (acceptable.path("documentId").asLong() != documentId
                    || acceptable.path("versionId").asLong() != versionId
                    || !containsLong(acceptable.path("chunkIds"), chunkId)
                    || !containsLong(acceptable.path("pages"), sourceIndex)) {
                continue;
            }
            for (JsonNode anchor : acceptable.path("anchorsAny")) {
                if (normalized.contains(normalize(anchor.asText()))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Map<String, JsonNode> positiveGroundTruth(JsonNode groundTruth) {
        Map<String, JsonNode> byId = new LinkedHashMap<>();
        for (JsonNode positive : groundTruth.path("positives")) {
            byId.put(positive.path("id").asText(), positive);
        }
        return Map.copyOf(byId);
    }

    private FrozenState frozenState() throws IOException {
        ProductionHash production = hashProductionSearch();
        return new FrozenState(sha256(DATASET), sha256(GROUND_TRUTH), production.fileCount(), production.aggregate());
    }

    private void assertFrozen(FrozenState frozen) {
        assertThat(frozen.dataset()).isEqualTo(EXPECTED_DATASET_HASH);
        assertThat(frozen.groundTruth()).isEqualTo(EXPECTED_GROUND_TRUTH_HASH);
        assertThat(frozen.productionFileCount()).isEqualTo(EXPECTED_PRODUCTION_FILES);
        assertThat(frozen.production()).isEqualTo(EXPECTED_PRODUCTION_HASH);
    }

    private ProductionHash hashProductionSearch() throws IOException {
        List<Path> files;
        try (Stream<Path> paths = Files.walk(PRODUCTION_SEARCH)) {
            files = paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(path -> path.toString().replace('\\', '/')))
                    .toList();
        }
        StringBuilder manifest = new StringBuilder();
        for (Path file : files) {
            manifest.append(sha256(file))
                    .append("  ")
                    .append(file.toString().replace('\\', '/'))
                    .append('\n');
        }
        if (!manifest.isEmpty()) {
            manifest.setLength(manifest.length() - 1);
        }
        return new ProductionHash(files.size(), sha256(manifest.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private Path outputPath() {
        return Path.of(System.getProperty(
                "prizm.gpt-evidence-judge.output",
                "local/gpt-evidence-judge/results.json"));
    }

    private static String sha256(Path path) throws IOException {
        return sha256(Files.readAllBytes(path));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private static String normalize(String value) {
        return Normalizer.normalize(Objects.requireNonNullElse(value, ""), Normalizer.Form.NFKC)
                .toLowerCase(Locale.KOREAN)
                .replace(",", "")
                .replaceAll("\\s+", " ");
    }

    private static boolean containsLong(JsonNode values, long expected) {
        for (JsonNode value : values) {
            if (value.asLong() == expected) {
                return true;
            }
        }
        return false;
    }

    private static boolean rankAtMost(Integer rank, int maximum) {
        return rank != null && rank <= maximum;
    }

    private static double reciprocal(Integer rank) {
        return rank == null || rank > 5 ? 0.0d : 1.0d / rank;
    }

    private static double ratio(List<QueryOutcome> values, java.util.function.Predicate<QueryOutcome> predicate) {
        return values.stream().filter(predicate).count() / (double) values.size();
    }

    private record FrozenState(
            String dataset,
            String groundTruth,
            int productionFileCount,
            String production) {
    }

    private record ProductionHash(int fileCount, String aggregate) {
    }

    private record MetricSummary(
            double top1Accuracy,
            double recallAt3,
            double recallAt5,
            double mrrAt5,
            double negativeFalsePositiveRate) {
    }

    private record ComparisonSummary(
            int totalQueries,
            int positiveQueries,
            int negativeQueries,
            MetricSummary p4,
            MetricSummary p4PlusGptJudge,
            long judgeErrors,
            long verificationRejections,
            long positiveRegressions,
            long inputTokens,
            long outputTokens,
            String verdict) {
    }

    private record QueryOutcome(
            String id,
            String query,
            boolean positive,
            String baselineState,
            Integer baselineCorrectRank,
            boolean baselineFalsePositive,
            List<VectorSearchResult> rawCandidates,
            List<EvidenceJudgeCandidate> submittedCandidates,
            EvidenceJudgeCall call,
            VerifiedEvidenceDecision verified,
            String error,
            boolean judgeCorrect,
            boolean judgeFalsePositive) {
    }
}
