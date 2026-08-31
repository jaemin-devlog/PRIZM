package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.evaluation.searchv3.typed.TypedConstraintStressDataset;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.MatchState;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Executes the pre-registered PRZ-028 T0/T1 comparison on DEV/CAL inputs only.
 *
 * <p>Each suite builds one complete owner-scoped B3 Dense ranking. T0 consumes that ranking as-is;
 * T1 consumes the same candidate identities and scores through the typed stable partition. The
 * permitted SEALED FINAL operation is metadata/file-hash verification only.
 */
class Prz028TypedConstraintBenchmarkTest {

    private static final Path OUTPUT_ROOT = Path.of("local/search-v3-evaluation/prz028");
    private static final Path DEFAULT_OUTPUT = OUTPUT_ROOT.resolve("typed-constraint-t1.json");
    private static final Pattern COMMIT_SHA = Pattern.compile("^[0-9a-f]{40}$");
    private static final double EPSILON = 1.0e-12d;

    private static final String HISTORICAL_INVALID_INPUT_FREEZE_COMMIT =
            "4bbbc5de040aa3c84fcb9869ece2fce85d983c0c";
    private static final String OFFICIAL_INPUT_FREEZE_COMMIT =
            "3e3bf652c5661a5bab34eb68e174dcea7459d6b5";
    private static final String EXPECTED_MODEL_NAME = "bge-m3:latest";
    private static final String EXPECTED_MODEL_DIGEST =
            "7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab";
    private static final String FROZEN_EVIDENCE_CHILD_BUILDER_SHA256 =
            "6ff76f49df332319fac987a59be4ead11d7ecda90b44f0d11e0cb538acd6cb83";
    private static final String FROZEN_RETRIEVAL_PASSAGE_BUILDER_SHA256 =
            "64c93a0ba50ec2785209a85abd339fa0e4d6de0dc6a99ac29dedfa3a93dc2c39";
    private static final Path EVIDENCE_CHILD_BUILDER = Path.of(
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "StructuralEvidenceChildBuilder.java");
    private static final Path RETRIEVAL_PASSAGE_BUILDER = Path.of(
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "StructuralRetrievalPassageBuilder.java");

    /** Frozen before the official run; changing it invalidates the code-freeze commit. */
    private static final VerdictPolicy VERDICT_POLICY = new VerdictPolicy(
            "PRZ-028-TYPED-GATE-1",
            "All suites preserve candidate identity, semantic order, Recall@5/10/20, nDCG@5, "
                    + "predicted SATISFIED@1 safety, zero persistent index/storage, and typed added p95 "
                    + "no greater than the shared B3 query embedding plus Dense-ranking p95 envelope.",
            "PROMISING requires typed-stress Top1 or MRR improvement, at least two direct-rank wins "
                    + "across at least two users and two typed kinds, zero losses, and fewer Gold-expected "
                    + "CONTRADICTED rank-1 candidates in qualifier, date, and identifier-number mismatch families.",
            "With hard gates satisfied, any direct-rank win or Gold-expected hard-negative improvement "
                    + "is NEEDS_ADJUSTMENT; otherwise NO_GO. Predicted SATISFIED@1 is a safety metric, "
                    + "not an improvement metric.");

    private static final List<String> EXECUTION_SOURCE_FILES = List.of(
            "scripts/evaluation/search-v3/materialize-prz028-typed-stress-1.0.1.mjs",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralBlockParser.java",
            EVIDENCE_CHILD_BUILDER.toString().replace('\\', '/'),
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/RetrievalPassage.java",
            RETRIEVAL_PASSAGE_BUILDER.toString().replace('\\', '/'),
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/SearchV3DenseAblationDataset.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/SearchV3DenseAblationEngine.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/OllamaBgeM3EmbeddingClient.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "SearchV3TypedConstraintAblationEngine.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/typed/TypedValueModel.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/typed/TypedTextSupport.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/typed/"
                    + "DeterministicTypedQueryParser.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/typed/"
                    + "DeterministicTypedObservationExtractor.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/typed/TypedConstraintEvaluator.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/typed/TypedStablePartitioner.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/typed/TypedConstraintStressDataset.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "Prz028TypedConstraintBenchmarkTest.java");

    @Test
    void comparesFrozenB3DenseAndTypedStablePartitionOnDevCalibrationOnly() throws Exception {
        String codeFreezeCommit = System.getProperty("prizm.prz028.code-freeze-commit", "");
        assertThat(codeFreezeCommit).matches(COMMIT_SHA);
        assertThat(git("rev-parse", "HEAD")).isEqualTo(codeFreezeCommit);
        assertThat(git("status", "--porcelain", "--untracked-files=all")).isBlank();
        assertThat(sha256(EVIDENCE_CHILD_BUILDER)).isEqualTo(FROZEN_EVIDENCE_CHILD_BUILDER_SHA256);
        assertThat(sha256(RETRIEVAL_PASSAGE_BUILDER)).isEqualTo(FROZEN_RETRIEVAL_PASSAGE_BUILDER_SHA256);

        SearchV3DenseAblationDataset denseLoader = new SearchV3DenseAblationDataset();
        SearchV3DenseAblationDataset.SealedManifestMetadata sealedBefore =
                denseLoader.readSealedManifestMetadata();
        SearchV3DenseAblationDataset.LongFormManifestMetadata longFormBefore =
                denseLoader.readLongFormManifestMetadata();
        SearchV3DenseAblationDataset.RobustnessManifestMetadata robustnessBefore =
                denseLoader.readRobustnessManifestMetadata();

        // Strict typed loader verifies root/split manifests, hashes, inventory, grounding, and freeze flags
        // before the structurally comparable dataset view is permitted to load.
        TypedConstraintStressDataset strictTypedLoader = new TypedConstraintStressDataset();
        List<TypedConstraintStressDataset.DatasetSlice> strictTyped = List.of(
                strictTypedLoader.load(TypedConstraintStressDataset.Split.DEV),
                strictTypedLoader.load(TypedConstraintStressDataset.Split.CALIBRATION));
        assertStrictTypedIdentity(strictTyped);

        List<SearchV3DenseAblationDataset.DatasetSlice> original = List.of(
                denseLoader.load(SearchV3DenseAblationDataset.Split.DEV),
                denseLoader.load(SearchV3DenseAblationDataset.Split.CALIBRATION));
        List<SearchV3DenseAblationDataset.DatasetSlice> longForm = List.of(
                denseLoader.loadLongForm(SearchV3DenseAblationDataset.Split.DEV),
                denseLoader.loadLongForm(SearchV3DenseAblationDataset.Split.CALIBRATION));
        List<SearchV3DenseAblationDataset.DatasetSlice> robustness = List.of(
                denseLoader.loadRobustness(SearchV3DenseAblationDataset.Split.DEV),
                denseLoader.loadRobustness(SearchV3DenseAblationDataset.Split.CALIBRATION));
        List<SearchV3DenseAblationDataset.DatasetSlice> typedStress = List.of(
                denseLoader.loadTypedStress(SearchV3DenseAblationDataset.Split.DEV),
                denseLoader.loadTypedStress(SearchV3DenseAblationDataset.Split.CALIBRATION));

        OllamaBgeM3EmbeddingClient client = new OllamaBgeM3EmbeddingClient();
        OllamaBgeM3EmbeddingClient.ModelMetadata model = client.inspectModel();
        assertThat(model.resolvedName()).isEqualTo(EXPECTED_MODEL_NAME);
        assertThat(model.digest()).isEqualTo(EXPECTED_MODEL_DIGEST);
        assertThat(model.dimensions()).isEqualTo(OllamaBgeM3EmbeddingClient.DIMENSIONS).isEqualTo(1024);
        assertThat(model.embeddingCapable()).isTrue();
        assertThat(OllamaBgeM3EmbeddingClient.MODEL).isEqualTo("bge-m3");
        assertThat(OllamaBgeM3EmbeddingClient.SIMILARITY).isEqualTo("COSINE");

        MemoryUsage heapBefore = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        SearchV3DenseAblationEngine denseEngine = new SearchV3DenseAblationEngine();
        SearchV3TypedConstraintAblationEngine typedEngine = new SearchV3TypedConstraintAblationEngine();
        SuiteReport originalReport = runSuite(
                "ORIGINAL_SEED", original, List.of(), denseEngine, typedEngine, client, model);
        SuiteReport longFormReport = runSuite(
                "LONG_FORM", longForm, List.of(), denseEngine, typedEngine, client, model);
        SuiteReport robustnessReport = runSuite(
                "ROBUSTNESS", robustness, List.of(), denseEngine, typedEngine, client, model);
        SuiteReport typedStressReport = runSuite(
                "TYPED_STRESS", typedStress, strictTyped, denseEngine, typedEngine, client, model);
        List<SuiteReport> suites = List.of(
                originalReport, longFormReport, robustnessReport, typedStressReport);
        MemoryUsage heapAfter = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();

        suites.forEach(this::assertRuntimeInvariants);
        Assessment assessment = assess(suites, typedStressReport.result());

        SearchV3DenseAblationDataset.SealedManifestMetadata sealedAfter =
                denseLoader.readSealedManifestMetadata();
        SearchV3DenseAblationDataset.LongFormManifestMetadata longFormAfter =
                denseLoader.readLongFormManifestMetadata();
        SearchV3DenseAblationDataset.RobustnessManifestMetadata robustnessAfter =
                denseLoader.readRobustnessManifestMetadata();
        assertThat(sealedAfter).isEqualTo(sealedBefore);
        assertThat(longFormAfter).isEqualTo(longFormBefore);
        assertThat(robustnessAfter).isEqualTo(robustnessBefore);
        assertThat(sealedAfter.combinedSha256()).isEqualTo(SearchV3DenseAblationDataset.SEALED_FINAL_SHA256);
        assertThat(sealedAfter.opened()).isFalse();
        assertThat(sealedAfter.searchExecuted()).isFalse();

        InputSnapshot inputs = new InputSnapshot(
                HISTORICAL_INVALID_INPUT_FREEZE_COMMIT,
                OFFICIAL_INPUT_FREEZE_COMMIT,
                splitHashes(original),
                longFormAfter,
                robustnessAfter,
                new TypedInputSnapshot(
                        TypedConstraintStressDataset.DATASET_VERSION,
                        TypedConstraintStressDataset.ROOT_SHA256,
                        strictTyped.stream().collect(LinkedHashMap::new,
                                (values, slice) -> values.put(slice.split().name(), slice.splitSha256()),
                                Map::putAll)));
        MemoryObservation memory = new MemoryObservation(
                "JVM_HEAP_POINT_OBSERVATION_NOT_ISOLATED",
                heapBefore.getUsed(),
                heapAfter.getUsed(),
                heapAfter.getUsed() - heapBefore.getUsed(),
                typedStressReport.result().runtimeCost().atomicSourceCount(),
                typedStressReport.result().runtimeCost().extractedObservationCount(),
                typedStressReport.result().runtimeCost().observationCacheCandidateCount(),
                typedStressReport.result().runtimeCost().observationCacheCanonicalPayloadUtf8Bytes(),
                typedStressReport.result().runtimeCost().exactAdditionalHeapBytes(),
                0,
                0);

        Path output = outputPath();
        Files.createDirectories(output.getParent());
        OfficialReport report = new OfficialReport(
                1,
                "PRZ-028-TYPED-EXACT-CONSTRAINTS-T0-T1",
                codeFreezeCommit,
                executionSourceSnapshot(),
                VERDICT_POLICY,
                inputs,
                model,
                OllamaBgeM3EmbeddingClient.SIMILARITY,
                suites,
                assessment,
                memory,
                sealedAfter,
                "NOT_RUN",
                false,
                false,
                false);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n";
        Files.writeString(output, json, StandardCharsets.UTF_8);
        String reportSha256 = sha256(json.getBytes(StandardCharsets.UTF_8));

        printSummary(output, reportSha256, model, suites, typedStressReport.result(), assessment, sealedAfter);

        // Candidate/semantic invariants throw inside the engine. Recall is a measured adoption hard gate:
        // preserve the JSON evidence first, then fail the official task if the pre-registered gate regresses.
        assertThat(assessment.candidateIdentityParity()).isTrue();
        assertThat(assessment.semanticExactOrderParity()).isTrue();
        assertThat(assessment.recallNonDegraded()).isTrue();
        assertThat(assessment.ndcgNonDegraded()).isTrue();
        assertThat(assessment.predictedSatisfiedAt1NotWorse()).isTrue();
        assertThat(assessment.operationalCostAccepted()).isTrue();
    }

    private SuiteReport runSuite(
            String suite,
            List<SearchV3DenseAblationDataset.DatasetSlice> slices,
            List<TypedConstraintStressDataset.DatasetSlice> strictTyped,
            SearchV3DenseAblationEngine denseEngine,
            SearchV3TypedConstraintAblationEngine typedEngine,
            OllamaBgeM3EmbeddingClient client,
            OllamaBgeM3EmbeddingClient.ModelMetadata model) {
        SearchV3DenseAblationEngine.PassageDenseRun sharedDense = denseEngine.runPassageDenseOnly(
                slices, client, model, "PRZ-028-" + suite + "-DEV-CAL");
        SearchV3TypedConstraintAblationEngine.ExperimentReport result = strictTyped.isEmpty()
                ? typedEngine.evaluate(sharedDense)
                : typedEngine.evaluate(sharedDense, strictTyped);
        return new SuiteReport(suite, result);
    }

    private void assertStrictTypedIdentity(List<TypedConstraintStressDataset.DatasetSlice> slices) {
        assertThat(slices).hasSize(2);
        assertThat(slices).allSatisfy(slice -> {
            assertThat(slice.datasetVersion()).isEqualTo(TypedConstraintStressDataset.DATASET_VERSION);
            assertThat(slice.rootSha256()).isEqualTo(TypedConstraintStressDataset.ROOT_SHA256);
            assertThat(slice.splitSha256()).isEqualTo(switch (slice.split()) {
                case DEV -> TypedConstraintStressDataset.DEV_SHA256;
                case CALIBRATION -> TypedConstraintStressDataset.CALIBRATION_SHA256;
            });
        });
        assertThat(slices.stream().mapToInt(slice -> slice.runtimeInputs().questions().size()).sum())
                .isEqualTo(24);
        assertThat(slices.stream().mapToInt(slice -> slice.runtimeInputs().documents().size()).sum())
                .isEqualTo(6);
        assertThat(slices.stream().mapToInt(slice -> slice.evaluationGold().units().size()).sum())
                .isEqualTo(26);
        assertThat(slices.stream().mapToInt(slice -> slice.evaluationGold().observations().size()).sum())
                .isEqualTo(25);
        assertThat(slices.stream().flatMap(slice -> slice.evaluationGold().queryAnnotations().values().stream())
                .mapToInt(annotation -> annotation.expectedEvidenceStates().size()).sum()).isEqualTo(104);
    }

    private void assertRuntimeInvariants(SuiteReport suite) {
        SearchV3TypedConstraintAblationEngine.ExperimentReport report = suite.result();
        long queryCount = report.slices().stream()
                .mapToLong(SearchV3TypedConstraintAblationEngine.SliceReport::queryCount).sum();
        assertThat(report.slices()).extracting(SearchV3TypedConstraintAblationEngine.SliceReport::split)
                .containsExactlyInAnyOrder("DEV", "CALIBRATION");
        assertThat(report.runtimeCost().candidateIdentityParityQueryCount()).isEqualTo(queryCount);
        assertThat(report.runtimeCost().semanticExactOrderParityQueryCount())
                .isEqualTo(report.runtimeCost().semanticQueryCount());
        assertThat(report.runtimeCost().persistentIndexCount()).isZero();
        assertThat(report.runtimeCost().persistentStorageWriteCount()).isZero();
        assertThat(report.model().resolvedName()).isEqualTo(EXPECTED_MODEL_NAME);
        assertThat(report.model().digest()).isEqualTo(EXPECTED_MODEL_DIGEST);
    }

    private Assessment assess(
            List<SuiteReport> suites,
            SearchV3TypedConstraintAblationEngine.ExperimentReport stress) {
        List<String> failures = new ArrayList<>();
        boolean candidateParity = suites.stream().allMatch(suite -> {
            long queryCount = suite.result().slices().stream()
                    .mapToLong(SearchV3TypedConstraintAblationEngine.SliceReport::queryCount).sum();
            return suite.result().runtimeCost().candidateIdentityParityQueryCount() == queryCount;
        });
        boolean semanticParity = suites.stream().allMatch(suite ->
                suite.result().runtimeCost().semanticQueryCount()
                        == suite.result().runtimeCost().semanticExactOrderParityQueryCount());
        boolean recallNonDegraded = suites.stream().allMatch(suite -> recallNonDegraded(suite.result()));
        boolean ndcgNonDegraded = suites.stream().allMatch(suite ->
                suite.result().queryMicro().t1().ndcgAt5() + EPSILON
                        >= suite.result().queryMicro().t0().ndcgAt5());
        boolean operationalCostAccepted = suites.stream().allMatch(suite ->
                suite.result().runtimeCost().persistentIndexCount() == 0
                        && suite.result().runtimeCost().persistentStorageWriteCount() == 0
                        && suite.result().latency().onlineAdded().samples() > 0
                        && Double.isFinite(suite.result().latency().onlineAdded().p95Ms())
                        && suite.result().latency().onlineAdded().p95Ms()
                                <= suite.result().latency().sharedQueryEmbedding().p95Ms()
                                + suite.result().latency().sharedDenseRanking().p95Ms() + EPSILON);
        if (!candidateParity) failures.add("CANDIDATE_IDENTITY_PARITY");
        if (!semanticParity) failures.add("SEMANTIC_EXACT_ORDER_PARITY");
        if (!recallNonDegraded) failures.add("RECALL_AT_5_10_20_REGRESSION");
        if (!ndcgNonDegraded) failures.add("NDCG_AT_5_REGRESSION");
        if (!operationalCostAccepted) failures.add("OPERATIONAL_COST");

        SearchV3TypedConstraintAblationEngine.ComparisonMetrics typed = stress.queryMicro();
        boolean rankQualityImproved = typed.t1().top1() > typed.t0().top1() + EPSILON
                || typed.t1().mrr() > typed.t0().mrr() + EPSILON;
        boolean predictedSatisfiedAt1NotWorse = stress.hardNegatives().t1SatisfiedAt1()
                <= stress.hardNegatives().t0SatisfiedAt1();
        boolean hardNegativeImproved = stress.hardNegatives().t1ExpectedContradictedAt1()
                < stress.hardNegatives().t0ExpectedContradictedAt1();
        if (!predictedSatisfiedAt1NotWorse) failures.add("HARD_NEGATIVE_PREDICTED_SATISFIED_AT1_REGRESSION");

        List<SearchV3TypedConstraintAblationEngine.QueryReport> stressQueries = stress.slices().stream()
                .flatMap(slice -> slice.queries().stream()).toList();
        List<String> wins = stressQueries.stream()
                .filter(query -> "WIN".equals(query.directOutcome()))
                .map(SearchV3TypedConstraintAblationEngine.QueryReport::queryId).toList();
        List<String> losses = stressQueries.stream()
                .filter(query -> "LOSS".equals(query.directOutcome()))
                .map(SearchV3TypedConstraintAblationEngine.QueryReport::queryId).toList();
        long winningUsers = stressQueries.stream()
                .filter(query -> "WIN".equals(query.directOutcome()))
                .map(query -> query.datasetVersion() + ":" + query.userBundleId())
                .distinct().count();
        long winningKinds = stressQueries.stream()
                .filter(query -> "WIN".equals(query.directOutcome()))
                .map(SearchV3TypedConstraintAblationEngine.QueryReport::typedKind)
                .filter(value -> !value.isBlank()).distinct().count();
        Map<String, FamilySafety> familySafety = new LinkedHashMap<>();
        familySafety.put("numeric_qualifier_mismatch", familySafety(stressQueries,
                query -> query.typedFamilies().contains("qualifier_mismatch")));
        familySafety.put("date_mismatch", familySafety(stressQueries,
                query -> "DATE".equals(query.typedKind())
                        && query.typedFamilies().contains("not_supported_hard_negative")));
        familySafety.put("identifier_number_mismatch", familySafety(stressQueries,
                query -> query.typedFamilies().contains("identifier_number_mismatch")));
        boolean keyMismatchFamiliesImproved = familySafety.values().stream()
                .allMatch(FamilySafety::improved);
        boolean keyMismatchFamiliesNotWorse = familySafety.values().stream()
                .allMatch(FamilySafety::notWorse);
        if (!keyMismatchFamiliesNotWorse) failures.add("KEY_MISMATCH_FAMILY_REGRESSION");

        String decision = decide(
                failures,
                rankQualityImproved,
                typed.directWins(),
                typed.directLosses(),
                winningUsers,
                winningKinds,
                hardNegativeImproved,
                keyMismatchFamiliesImproved);

        return new Assessment(
                candidateParity,
                semanticParity,
                recallNonDegraded,
                ndcgNonDegraded,
                operationalCostAccepted,
                List.copyOf(failures),
                rankQualityImproved,
                predictedSatisfiedAt1NotWorse,
                hardNegativeImproved,
                keyMismatchFamiliesImproved,
                winningUsers,
                winningKinds,
                typed.directWins(),
                typed.directLosses(),
                typed.directTies(),
                wins,
                losses,
                familySafety,
                decision);
    }

    static String decide(
            List<String> hardGateFailures,
            boolean rankQualityImproved,
            long directWins,
            long directLosses,
            long winningUsers,
            long winningKinds,
            boolean hardNegativeImproved,
            boolean keyMismatchFamiliesImproved) {
        if (!hardGateFailures.isEmpty()) return "NO_GO";
        if (rankQualityImproved
                && directWins >= 2
                && directLosses == 0
                && winningUsers >= 2
                && winningKinds >= 2
                && hardNegativeImproved
                && keyMismatchFamiliesImproved) {
            return "PROMISING";
        }
        if (directWins > 0 || hardNegativeImproved) return "NEEDS_ADJUSTMENT";
        return "NO_GO";
    }

    private FamilySafety familySafety(
            List<SearchV3TypedConstraintAblationEngine.QueryReport> queries,
            java.util.function.Predicate<SearchV3TypedConstraintAblationEngine.QueryReport> selector) {
        List<SearchV3TypedConstraintAblationEngine.QueryReport> selected = queries.stream()
                .filter(selector)
                .filter(query -> query.t0ExpectedRank1State() != null && query.t1ExpectedRank1State() != null)
                .toList();
        long t0 = selected.stream().filter(query ->
                query.t0ExpectedRank1State() == MatchState.CONTRADICTED).count();
        long t1 = selected.stream().filter(query ->
                query.t1ExpectedRank1State() == MatchState.CONTRADICTED).count();
        return new FamilySafety(selected.size(), t0, t1, t1 <= t0, t1 < t0);
    }

    private boolean recallNonDegraded(SearchV3TypedConstraintAblationEngine.ExperimentReport report) {
        for (int cutoff : List.of(5, 10, 20)) {
            if (report.queryMicro().t1().recallAtK().get(cutoff) + EPSILON
                    < report.queryMicro().t0().recallAtK().get(cutoff)) {
                return false;
            }
        }
        return true;
    }

    private Path outputPath() {
        Path root = OUTPUT_ROOT.toAbsolutePath().normalize();
        Path output = Path.of(System.getProperty("prizm.prz028.output", DEFAULT_OUTPUT.toString()))
                .toAbsolutePath().normalize();
        if (!output.startsWith(root) || !output.getFileName().toString().endsWith(".json")) {
            throw new IllegalArgumentException("PRZ-028 raw output must remain under " + root);
        }
        return output;
    }

    private Map<String, String> splitHashes(List<SearchV3DenseAblationDataset.DatasetSlice> slices) {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (SearchV3DenseAblationDataset.DatasetSlice slice : slices) {
            hashes.put(slice.split().name(), slice.manifestCombinedSha256());
        }
        return Map.copyOf(hashes);
    }

    private ExecutionSourceSnapshot executionSourceSnapshot() throws Exception {
        Map<String, String> fileHashes = new LinkedHashMap<>();
        for (String sourceFile : EXECUTION_SOURCE_FILES) {
            fileHashes.put(sourceFile, sha256(Path.of(sourceFile)));
        }
        String hashInput = fileHashes.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
        return new ExecutionSourceSnapshot(
                "CONTENT_ADDRESSED_WORKTREE_SNAPSHOT",
                Map.copyOf(fileHashes),
                sha256(hashInput.getBytes(StandardCharsets.UTF_8)),
                FROZEN_EVIDENCE_CHILD_BUILDER_SHA256,
                FROZEN_RETRIEVAL_PASSAGE_BUILDER_SHA256);
    }

    private void printSummary(
            Path output,
            String reportSha256,
            OllamaBgeM3EmbeddingClient.ModelMetadata model,
            List<SuiteReport> suites,
            SearchV3TypedConstraintAblationEngine.ExperimentReport stress,
            Assessment assessment,
            SearchV3DenseAblationDataset.SealedManifestMetadata sealed) {
        System.out.println("PRZ028_REPORT=" + output);
        System.out.println("PRZ028_REPORT_SHA256=" + reportSha256);
        System.out.println("PRZ028_MODEL=" + model.resolvedName());
        System.out.println("PRZ028_MODEL_DIGEST=" + model.digest());
        for (SuiteReport suite : suites) {
            SearchV3TypedConstraintAblationEngine.ComparisonMetrics metrics = suite.result().queryMicro();
            System.out.println("PRZ028_" + suite.name() + "_T0_TOP1=" + metrics.t0().top1());
            System.out.println("PRZ028_" + suite.name() + "_T1_TOP1=" + metrics.t1().top1());
            System.out.println("PRZ028_" + suite.name() + "_T0_MRR=" + metrics.t0().mrr());
            System.out.println("PRZ028_" + suite.name() + "_T1_MRR=" + metrics.t1().mrr());
            System.out.println("PRZ028_" + suite.name() + "_T0_NDCG5=" + metrics.t0().ndcgAt5());
            System.out.println("PRZ028_" + suite.name() + "_T1_NDCG5=" + metrics.t1().ndcgAt5());
        }
        System.out.println("PRZ028_STRESS_WINS=" + stress.queryMicro().directWins());
        System.out.println("PRZ028_STRESS_LOSSES=" + stress.queryMicro().directLosses());
        System.out.println("PRZ028_STRESS_TIES=" + stress.queryMicro().directTies());
        System.out.println("PRZ028_STRESS_HARD_NEGATIVE_T0_SATISFIED_AT1="
                + stress.hardNegatives().t0SatisfiedAt1());
        System.out.println("PRZ028_STRESS_HARD_NEGATIVE_T1_SATISFIED_AT1="
                + stress.hardNegatives().t1SatisfiedAt1());
        System.out.println("PRZ028_STRESS_HARD_NEGATIVE_T0_EXPECTED_CONTRADICTED_AT1="
                + stress.hardNegatives().t0ExpectedContradictedAt1());
        System.out.println("PRZ028_STRESS_HARD_NEGATIVE_T1_EXPECTED_CONTRADICTED_AT1="
                + stress.hardNegatives().t1ExpectedContradictedAt1());
        System.out.println("PRZ028_DECISION=" + assessment.decision());
        System.out.println("PRZ028_SEALED_FINAL_SHA256=" + sealed.combinedSha256());
        System.out.println("PRZ028_SEALED_FINAL_OPENED=false");
        System.out.println("PRZ028_SEALED_FINAL_SEARCH_EXECUTED=false");
        System.out.println("PRZ028_CURRENT_FRESH_BASELINE=NOT_RUN");
    }

    private String sha256(Path path) throws Exception {
        return sha256(Files.readAllBytes(path));
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private String git(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exit = process.waitFor();
        if (exit != 0) throw new IllegalStateException("git guard failed: " + output);
        return output;
    }

    record SuiteReport(String name, SearchV3TypedConstraintAblationEngine.ExperimentReport result) {
    }

    record VerdictPolicy(String version, String hardGate, String promising, String fallback) {
    }

    record Assessment(
            boolean candidateIdentityParity,
            boolean semanticExactOrderParity,
            boolean recallNonDegraded,
            boolean ndcgNonDegraded,
            boolean operationalCostAccepted,
            List<String> hardGateFailures,
            boolean typedRankQualityImproved,
            boolean predictedSatisfiedAt1NotWorse,
            boolean hardNegativeImproved,
            boolean keyMismatchFamiliesImproved,
            long winningUserCount,
            long winningTypedKindCount,
            long directWins,
            long directLosses,
            long directTies,
            List<String> winQueryIds,
            List<String> lossQueryIds,
            Map<String, FamilySafety> familySafety,
            String decision) {
    }

    record FamilySafety(
            long queryCount,
            long t0ExpectedContradictedAt1,
            long t1ExpectedContradictedAt1,
            boolean notWorse,
            boolean improved) {
    }

    record TypedInputSnapshot(String datasetVersion, String rootSha256, Map<String, String> splitSha256) {
    }

    record InputSnapshot(
            String historicalInvalidInputFreezeCommit,
            String officialInputFreezeCommit,
            Map<String, String> originalSplitSha256,
            SearchV3DenseAblationDataset.LongFormManifestMetadata longForm,
            SearchV3DenseAblationDataset.RobustnessManifestMetadata robustness,
            TypedInputSnapshot typedStress) {
    }

    record MemoryObservation(
            String kind,
            long heapUsedBeforeBytes,
            long heapUsedAfterBytes,
            long observedDeltaBytes,
            long atomicSourceCount,
            long extractedObservationCount,
            long observationCacheCandidateCount,
            long observationCacheCanonicalPayloadUtf8Bytes,
            String exactAdditionalHeapBytes,
            long persistentIndexCount,
            long persistentStorageWriteCount) {
    }

    record ExecutionSourceSnapshot(
            String kind,
            Map<String, String> fileSha256,
            String combinedSha256,
            String frozenEvidenceChildBuilderSha256,
            String frozenRetrievalPassageBuilderSha256) {
    }

    record OfficialReport(
            int schemaVersion,
            String phase,
            String codeFreezeCommit,
            ExecutionSourceSnapshot executionSource,
            VerdictPolicy verdictPolicy,
            InputSnapshot inputs,
            OllamaBgeM3EmbeddingClient.ModelMetadata model,
            String similarity,
            List<SuiteReport> suites,
            Assessment assessment,
            MemoryObservation memory,
            SearchV3DenseAblationDataset.SealedManifestMetadata sealedFinal,
            String currentFreshBaseline,
            boolean sealedFinalSemanticAccess,
            boolean sealedFinalPredictionGenerated,
            boolean sealedFinalResultGenerated) {
    }
}
