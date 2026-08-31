package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.evaluation.searchv3.typed.TypedConstraintStressDataset;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.MatchState;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
    private static final Path DEFAULT_OUTPUT = OUTPUT_ROOT.resolve("typed-constraint-role-1.1.0.json");
    private static final Path INVALID_OUTPUT = OUTPUT_ROOT.resolve("typed-constraint-role-1.1.0.invalid.json");
    private static final Pattern COMMIT_SHA = Pattern.compile("^[0-9a-f]{40}$");
    private static final double EPSILON = 1.0e-12d;

    private static final String HISTORICAL_INVALID_INPUT_FREEZE_COMMIT =
            "4bbbc5de040aa3c84fcb9869ece2fce85d983c0c";
    private static final String OFFICIAL_INPUT_FREEZE_COMMIT =
            "3e3bf652c5661a5bab34eb68e174dcea7459d6b5";
    private static final String OFFICIAL_CAPABILITY_INPUT_FREEZE_COMMIT =
            "e32b9683a7e366e9f7298dc94f04657410abc08e";
    private static final String EXPECTED_MODEL_NAME = "bge-m3:latest";
    private static final String EXPECTED_MODEL_DIGEST =
            "7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab";
    private static final String CANDIDATE_K = "ALL_OWNER_SCOPED_B3_PASSAGES";
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
            "PRZ-028-FINAL-ROLE-GATE-2",
            "Integrity failure yields INVALID_RESULT / ROLE_NOT_ASSESSED. Valid runs require exact candidate "
                    + "and semantic parity across five separately reported suites.",
            "Validation requires query F1=1, observation F1>=.95, state accuracy>=.95, SAT precision=1, "
                    + "CONTR precision>=.95/recall>=.90, qualifier SAT false positives=0, same-qualifier "
                    + "wrong-value CONTR recall=1, exact frozen reason conformance, Recall@5/20 and nDCG@5 "
                    + "non-degradation, direct-rank1 loss=0, "
                    + "zero persistent storage, and accepted p95 latency.",
            "RANKING_COMPONENT additionally requires Stress 1.1 Top1 or MRR strict improvement, wins>=2, "
                    + "losses=0, >=2 winning users and primary families, at least one Gold-expected wrong-condition "
                    + "rank-1 demotion, and non-increasing predicted SATISFIED@1. Validation-only success is "
                    + "EVIDENCE_VALIDATION_ONLY; validation failure is DROP.");

    private static final List<String> EXECUTION_SOURCE_FILES = List.of(
            "scripts/evaluation/search-v3/materialize-prz028-typed-stress-1.0.1.mjs",
            "scripts/evaluation/search-v3/materialize-prz028-typed-stress-1.1.0.mjs",
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
    void comparesFrozenB3DenseAndTypedStablePartitionOnDevCalibrationOnly() throws Throwable {
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
        List<TypedConstraintStressDataset.DatasetSlice> historicalStrictTyped = List.of(
                strictTypedLoader.load(TypedConstraintStressDataset.HISTORICAL_1_0_1,
                        TypedConstraintStressDataset.Split.DEV),
                strictTypedLoader.load(TypedConstraintStressDataset.HISTORICAL_1_0_1,
                        TypedConstraintStressDataset.Split.CALIBRATION));
        List<TypedConstraintStressDataset.DatasetSlice> officialStrictTyped = List.of(
                strictTypedLoader.load(TypedConstraintStressDataset.OFFICIAL_1_1_0,
                        TypedConstraintStressDataset.Split.DEV),
                strictTypedLoader.load(TypedConstraintStressDataset.OFFICIAL_1_1_0,
                        TypedConstraintStressDataset.Split.CALIBRATION));
        assertStrictTypedIdentity(
                historicalStrictTyped, TypedConstraintStressDataset.HISTORICAL_1_0_1, 26, 25, 104);
        assertStrictTypedIdentity(
                officialStrictTyped, TypedConstraintStressDataset.OFFICIAL_1_1_0, 24, 24, 96);

        List<SearchV3DenseAblationDataset.DatasetSlice> original = List.of(
                denseLoader.load(SearchV3DenseAblationDataset.Split.DEV),
                denseLoader.load(SearchV3DenseAblationDataset.Split.CALIBRATION));
        List<SearchV3DenseAblationDataset.DatasetSlice> longForm = List.of(
                denseLoader.loadLongForm(SearchV3DenseAblationDataset.Split.DEV),
                denseLoader.loadLongForm(SearchV3DenseAblationDataset.Split.CALIBRATION));
        List<SearchV3DenseAblationDataset.DatasetSlice> robustness = List.of(
                denseLoader.loadRobustness(SearchV3DenseAblationDataset.Split.DEV),
                denseLoader.loadRobustness(SearchV3DenseAblationDataset.Split.CALIBRATION));
        List<SearchV3DenseAblationDataset.DatasetSlice> historicalTypedStress = List.of(
                denseLoader.loadTypedStressHistorical(SearchV3DenseAblationDataset.Split.DEV),
                denseLoader.loadTypedStressHistorical(SearchV3DenseAblationDataset.Split.CALIBRATION));
        List<SearchV3DenseAblationDataset.DatasetSlice> officialTypedStress = List.of(
                denseLoader.loadTypedStressOfficial(SearchV3DenseAblationDataset.Split.DEV),
                denseLoader.loadTypedStressOfficial(SearchV3DenseAblationDataset.Split.CALIBRATION));

        OllamaBgeM3EmbeddingClient client = new OllamaBgeM3EmbeddingClient();
        OllamaBgeM3EmbeddingClient.ModelMetadata model = client.inspectModel();
        assertThat(model.resolvedName()).isEqualTo(EXPECTED_MODEL_NAME);
        assertThat(model.digest()).isEqualTo(EXPECTED_MODEL_DIGEST);
        assertThat(model.dimensions()).isEqualTo(OllamaBgeM3EmbeddingClient.DIMENSIONS).isEqualTo(1024);
        assertThat(model.embeddingCapable()).isTrue();
        assertThat(OllamaBgeM3EmbeddingClient.MODEL).isEqualTo("bge-m3");
        assertThat(OllamaBgeM3EmbeddingClient.SIMILARITY).isEqualTo("COSINE");

        InputSnapshot inputsBefore = inputSnapshot(
                original,
                longFormBefore,
                robustnessBefore,
                historicalStrictTyped,
                officialStrictTyped);
        ExecutionSourceSnapshot sourceBefore = executionSourceSnapshot();
        Path output = canonicalOutputPath();
        Path invalidOutput = canonicalInvalidOutputPath();
        ClaimContract claimContract = new ClaimContract(
                codeFreezeCommit,
                TypedConstraintStressDataset.OFFICIAL_1_1_0.rootSha256(),
                model.resolvedName(),
                model.digest(),
                model.dimensions(),
                OllamaBgeM3EmbeddingClient.SIMILARITY,
                CANDIDATE_K,
                SearchV3TypedConstraintAblationEngine.T0_PROFILE,
                SearchV3TypedConstraintAblationEngine.T1_PROFILE,
                VERDICT_POLICY.version());
        Path claim = claimOfficialRun(
                output,
                invalidOutput,
                canonicalClaimRoot(),
                claimContract);

        PublishedSummary published = executeAfterClaim(invalidOutput, claim, claimContract, () -> {
            MemoryUsage heapBefore = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
            SearchV3DenseAblationEngine denseEngine = new SearchV3DenseAblationEngine();
            SearchV3TypedConstraintAblationEngine typedEngine = new SearchV3TypedConstraintAblationEngine();
            SuiteReport originalReport = runSuite(
                    "ORIGINAL_SEED", original, List.of(), denseEngine, typedEngine, client, model);
            SuiteReport longFormReport = runSuite(
                    "LONG_FORM", longForm, List.of(), denseEngine, typedEngine, client, model);
            SuiteReport robustnessReport = runSuite(
                    "ROBUSTNESS", robustness, List.of(), denseEngine, typedEngine, client, model);
            SuiteReport historicalTypedStressReport = runSuite(
                    "TYPED_STRESS_1_0_1_HISTORICAL", historicalTypedStress, historicalStrictTyped,
                    denseEngine, typedEngine, client, model);
            SuiteReport officialTypedStressReport = runSuite(
                    "TYPED_STRESS_1_1_0_OFFICIAL", officialTypedStress, officialStrictTyped,
                    denseEngine, typedEngine, client, model);
            List<SuiteReport> suites = List.of(
                    originalReport, longFormReport, robustnessReport,
                    historicalTypedStressReport, officialTypedStressReport);
            MemoryUsage heapAfter = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();

            suites.forEach(this::assertRuntimeInvariants);
            Assessment assessment = assess(suites, officialTypedStressReport.result());
            if (!assessment.integrityFailures().isEmpty()
                    || "ROLE_NOT_ASSESSED".equals(assessment.role())) {
                throw new OfficialIntegrityException(
                        "ASSESSMENT_INTEGRITY_FAILURE: " + assessment.integrityFailures());
            }

            PostRunSnapshot postRun = capturePostRunSnapshot(client);
            requirePostRunIntegrity(
                    codeFreezeCommit,
                    model,
                    inputsBefore,
                    sourceBefore,
                    sealedBefore,
                    postRun);

            MemoryObservation memory = new MemoryObservation(
                    "JVM_HEAP_POINT_OBSERVATION_NOT_ISOLATED",
                    heapBefore.getUsed(),
                    heapAfter.getUsed(),
                    heapAfter.getUsed() - heapBefore.getUsed(),
                    officialTypedStressReport.result().runtimeCost().atomicSourceCount(),
                    officialTypedStressReport.result().runtimeCost().extractedObservationCount(),
                    officialTypedStressReport.result().runtimeCost().observationCacheCandidateCount(),
                    officialTypedStressReport.result().runtimeCost().observationCacheCanonicalPayloadUtf8Bytes(),
                    officialTypedStressReport.result().runtimeCost().exactAdditionalHeapBytes(),
                    0,
                    0);

            OfficialReport report = new OfficialReport(
                    1,
                    "PRZ-028-TYPED-EXACT-CONSTRAINTS-T0-T1",
                    codeFreezeCommit,
                    claim.toString(),
                    sourceBefore,
                    VERDICT_POLICY,
                    inputsBefore,
                    model,
                    OllamaBgeM3EmbeddingClient.SIMILARITY,
                    claimContract.candidateK(),
                    suites,
                    assessment,
                    memory,
                    postRun.sealedFinal(),
                    "NOT_RUN",
                    false,
                    false,
                    false);
            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n";
            String reportSha256 = sha256(json.getBytes(StandardCharsets.UTF_8));
            PublishedSummary summary = new PublishedSummary(
                    output, reportSha256, model, suites, officialTypedStressReport.result(),
                    assessment, postRun.sealedFinal());
            // Success publication is the final side effect inside the fail-to-invalid transition.
            publishCanonicalCreateNew(output, json);
            return summary;
        });
        printSummary(
                published.output(),
                published.reportSha256(),
                published.model(),
                published.suites(),
                published.stress(),
                published.assessment(),
                published.sealedFinal());
    }

    static <T> T executeAfterClaim(
            Path invalidOutput,
            Path claim,
            ClaimContract claimContract,
            ThrowingOfficialAction<T> action) throws Throwable {
        try {
            return action.run();
        }
        catch (Throwable failure) {
            try {
                writeInvalidArtifact(invalidOutput, claim, claimContract, failure);
            }
            catch (Exception invalidWriteFailure) {
                failure.addSuppressed(invalidWriteFailure);
            }
            throw failure;
        }
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

    private void assertStrictTypedIdentity(
            List<TypedConstraintStressDataset.DatasetSlice> slices,
            TypedConstraintStressDataset.DatasetIdentity identity,
            int evidenceUnits,
            int observations,
            int expectedStates) {
        assertThat(slices).hasSize(2);
        assertThat(slices).allSatisfy(slice -> {
            assertThat(slice.datasetVersion()).isEqualTo(identity.version());
            assertThat(slice.rootSha256()).isEqualTo(identity.rootSha256());
            assertThat(slice.splitSha256()).isEqualTo(switch (slice.split()) {
                case DEV -> identity.devSha256();
                case CALIBRATION -> identity.calibrationSha256();
            });
        });
        assertThat(slices.stream().mapToInt(slice -> slice.runtimeInputs().questions().size()).sum())
                .isEqualTo(24);
        assertThat(slices.stream().mapToInt(slice -> slice.runtimeInputs().documents().size()).sum())
                .isEqualTo(6);
        assertThat(slices.stream().mapToInt(slice -> slice.evaluationGold().units().size()).sum())
                .isEqualTo(evidenceUnits);
        assertThat(slices.stream().mapToInt(slice -> slice.evaluationGold().observations().size()).sum())
                .isEqualTo(observations);
        assertThat(slices.stream().flatMap(slice -> slice.evaluationGold().queryAnnotations().values().stream())
                .mapToInt(annotation -> annotation.expectedEvidenceStates().size()).sum()).isEqualTo(expectedStates);
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
        assertThat(report.slices()).flatExtracting(SearchV3TypedConstraintAblationEngine.SliceReport::queries)
                .allSatisfy(query -> {
                    Map<String, Double> t0Scores = query.t0Ranking().stream().collect(
                            LinkedHashMap::new,
                            (scores, candidate) -> scores.put(candidate.candidateId(), candidate.cosineScore()),
                            Map::putAll);
                    Map<String, Double> t1Scores = query.t1Ranking().stream().collect(
                            LinkedHashMap::new,
                            (scores, candidate) -> scores.put(candidate.candidateId(), candidate.cosineScore()),
                            Map::putAll);
                    assertThat(t1Scores).as("T0/T1 dense score parity: %s", query.queryId())
                            .isEqualTo(t0Scores);
                });
    }

    private Assessment assess(
            List<SuiteReport> suites,
            SearchV3TypedConstraintAblationEngine.ExperimentReport stress) {
        List<String> integrityFailures = new ArrayList<>();
        List<String> validationFailures = new ArrayList<>();
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
        long directRank1Losses = suites.stream()
                .mapToLong(suite -> suite.result().queryMicro().directRank1Losses()).sum();
        if (!candidateParity) integrityFailures.add("CANDIDATE_IDENTITY_PARITY");
        if (!semanticParity) integrityFailures.add("SEMANTIC_EXACT_ORDER_PARITY");
        if (!recallNonDegraded) validationFailures.add("RECALL_AT_5_20_REGRESSION");
        if (!ndcgNonDegraded) validationFailures.add("NDCG_AT_5_REGRESSION");
        if (directRank1Losses != 0) validationFailures.add("DIRECT_RANK1_LOSS");
        if (!operationalCostAccepted) validationFailures.add("OPERATIONAL_COST");

        SearchV3TypedConstraintAblationEngine.ExactSetMetrics queryExtraction =
                stress.extraction().queryConstraints();
        SearchV3TypedConstraintAblationEngine.ExactSetMetrics observationExtraction =
                stress.extraction().candidateObservations();
        SearchV3TypedConstraintAblationEngine.ClassMetrics satisfied =
                stress.states().perState().get(MatchState.SATISFIED.name());
        SearchV3TypedConstraintAblationEngine.ClassMetrics contradicted =
                stress.states().perState().get(MatchState.CONTRADICTED.name());
        var diagnostics = stress.states().diagnostics();
        if (Math.abs(queryExtraction.f1() - 1.0d) > EPSILON) validationFailures.add("QUERY_EXTRACTION_F1");
        if (observationExtraction.f1() + EPSILON < 0.95d) validationFailures.add("OBSERVATION_EXTRACTION_F1");
        if (stress.states().accuracy() + EPSILON < 0.95d) validationFailures.add("STATE_ACCURACY");
        if (satisfied == null || Math.abs(satisfied.precision() - 1.0d) > EPSILON) {
            validationFailures.add("SATISFIED_PRECISION");
        }
        if (contradicted == null || contradicted.precision() + EPSILON < 0.95d) {
            validationFailures.add("CONTRADICTED_PRECISION");
        }
        if (contradicted == null || contradicted.recall() + EPSILON < 0.90d) {
            validationFailures.add("CONTRADICTED_RECALL");
        }
        if (diagnostics.qualifierMismatchSatisfiedFalsePositiveCount() != 0) {
            validationFailures.add("QUALIFIER_MISMATCH_SATISFIED_FALSE_POSITIVE");
        }
        if (diagnostics.labeledReasonCount() == 0
                || diagnostics.correctReasonCount() != diagnostics.labeledReasonCount()) {
            validationFailures.add("DIAGNOSTIC_REASON_CONFORMANCE");
        }
        if (Math.abs(diagnostics.sameQualifierWrongValueContradictedRecall() - 1.0d) > EPSILON) {
            validationFailures.add("SAME_QUALIFIER_WRONG_VALUE_CONTRADICTED_RECALL");
        }

        SearchV3TypedConstraintAblationEngine.ComparisonMetrics typed = stress.queryMicro();
        boolean rankQualityImproved = typed.t1().top1() > typed.t0().top1() + EPSILON
                || typed.t1().mrr() > typed.t0().mrr() + EPSILON;
        boolean predictedSatisfiedAt1NotWorse = stress.hardNegatives().t1SatisfiedAt1()
                <= stress.hardNegatives().t0SatisfiedAt1();
        boolean hardNegativeImproved = stress.hardNegatives().t1ExpectedContradictedAt1()
                < stress.hardNegatives().t0ExpectedContradictedAt1();

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
        long winningPrimaryFamilies = stressQueries.stream()
                .filter(query -> "WIN".equals(query.directOutcome()))
                .map(SearchV3TypedConstraintAblationEngine.QueryReport::primaryFamily)
                .filter(value -> !value.isBlank()).distinct().count();
        String role = decideRole(
                integrityFailures,
                validationFailures,
                rankQualityImproved,
                typed.directWins(),
                typed.directLosses(),
                winningUsers,
                winningPrimaryFamilies,
                hardNegativeImproved,
                predictedSatisfiedAt1NotWorse);

        return new Assessment(
                candidateParity,
                semanticParity,
                recallNonDegraded,
                ndcgNonDegraded,
                operationalCostAccepted,
                directRank1Losses,
                List.copyOf(integrityFailures),
                List.copyOf(validationFailures),
                rankQualityImproved,
                predictedSatisfiedAt1NotWorse,
                hardNegativeImproved,
                winningUsers,
                winningPrimaryFamilies,
                typed.directWins(),
                typed.directLosses(),
                typed.directTies(),
                wins,
                losses,
                role);
    }

    static String decideRole(
            List<String> integrityFailures,
            List<String> validationFailures,
            boolean rankQualityImproved,
            long directWins,
            long directLosses,
            long winningUsers,
            long winningPrimaryFamilies,
            boolean hardNegativeImproved,
            boolean predictedSatisfiedAt1NotWorse) {
        if (!integrityFailures.isEmpty()) return "ROLE_NOT_ASSESSED";
        if (!validationFailures.isEmpty()) return "DROP";
        if (rankQualityImproved
                && directWins >= 2
                && directLosses == 0
                && winningUsers >= 2
                && winningPrimaryFamilies >= 2
                && hardNegativeImproved
                && predictedSatisfiedAt1NotWorse) {
            return "RANKING_COMPONENT";
        }
        return "EVIDENCE_VALIDATION_ONLY";
    }

    private boolean recallNonDegraded(SearchV3TypedConstraintAblationEngine.ExperimentReport report) {
        for (int cutoff : List.of(5, 20)) {
            if (report.queryMicro().t1().recallAtK().get(cutoff) + EPSILON
                    < report.queryMicro().t0().recallAtK().get(cutoff)) {
                return false;
            }
        }
        return true;
    }

    static Path canonicalOutputPath() {
        return repositoryCommonRoot().resolve(DEFAULT_OUTPUT).normalize();
    }

    static Path canonicalInvalidOutputPath() {
        return repositoryCommonRoot().resolve(INVALID_OUTPUT).normalize();
    }

    static Path canonicalClaimRoot() {
        return repositoryCommonRoot().resolve(OUTPUT_ROOT).resolve("claims").normalize();
    }

    static Path repositoryCommonRoot() {
        try {
            Path commonGitDirectory = Path.of(git(
                    "rev-parse", "--path-format=absolute", "--git-common-dir"))
                    .toAbsolutePath().normalize();
            Path root = commonGitDirectory.getParent();
            if (root == null) throw new IllegalStateException("git common directory has no repository root");
            return root;
        }
        catch (Exception exception) {
            throw new IllegalStateException("cannot resolve repository-common official artifact root", exception);
        }
    }

    static Path claimOfficialRun(
            Path output,
            Path invalidOutput,
            Path claimRoot,
            ClaimContract contract) throws Exception {
        if (!COMMIT_SHA.matcher(contract.codeFreezeCommit()).matches()
                || !Pattern.compile("^[0-9a-f]{64}$").matcher(contract.inputSha256()).matches()
                || !Pattern.compile("^[0-9a-f]{64}$").matcher(contract.modelDigest()).matches()
                || contract.modelDimensions() <= 0
                || contract.modelName().isBlank()
                || contract.similarity().isBlank()
                || contract.candidateK().isBlank()
                || contract.t0Profile().isBlank()
                || contract.t1Profile().isBlank()
                || contract.verdictPolicyVersion().isBlank()) {
            throw new IllegalArgumentException("official claim requires frozen code/input/model/profile/policy identity");
        }
        Path normalizedOutput = output.toAbsolutePath().normalize();
        Path normalizedInvalidOutput = invalidOutput.toAbsolutePath().normalize();
        Path normalizedClaimRoot = claimRoot.toAbsolutePath().normalize();
        Files.createDirectories(normalizedOutput.getParent());
        Files.createDirectories(normalizedInvalidOutput.getParent());
        Files.createDirectories(normalizedClaimRoot);
        if (Files.exists(normalizedOutput)) {
            throw new IllegalStateException("official PRZ-028 output already exists: " + normalizedOutput);
        }
        if (Files.exists(normalizedInvalidOutput)) {
            throw new IllegalStateException("invalid PRZ-028 output already exists: " + normalizedInvalidOutput);
        }
        // Dataset-global identity: code or output changes never create a second claim for Stress 1.1.0.
        Path claim = normalizedClaimRoot.resolve("stress-1.1.0-" + contract.inputSha256() + ".claim");
        String content = "codeFreezeCommit=" + contract.codeFreezeCommit() + "\n"
                + "inputSha256=" + contract.inputSha256() + "\n"
                + "modelName=" + contract.modelName() + "\n"
                + "modelDigest=" + contract.modelDigest() + "\n"
                + "modelDimensions=" + contract.modelDimensions() + "\n"
                + "similarity=" + contract.similarity() + "\n"
                + "candidateK=" + contract.candidateK() + "\n"
                + "t0Profile=" + contract.t0Profile() + "\n"
                + "t1Profile=" + contract.t1Profile() + "\n"
                + "verdictPolicyVersion=" + contract.verdictPolicyVersion() + "\n"
                + "status=OFFICIAL_RUN_CLAIMED_BEFORE_BGE\n";
        publishCanonicalCreateNew(claim, content);
        return claim;
    }

    static void writeInvalidArtifact(
            Path invalidOutput,
            Path claim,
            ClaimContract contract,
            Throwable failure) throws Exception {
        InvalidOfficialReport invalid = new InvalidOfficialReport(
                1,
                "PRZ-028-TYPED-EXACT-CONSTRAINTS-T0-T1",
                "INVALID_RESULT",
                "ROLE_NOT_ASSESSED",
                claim.toAbsolutePath().normalize().toString(),
                contract,
                failure.getClass().getName(),
                failure.getMessage() == null ? "NO_MESSAGE" : failure.getMessage());
        String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(invalid) + "\n";
        Path normalized = invalidOutput.toAbsolutePath().normalize();
        Files.createDirectories(normalized.getParent());
        publishCanonicalCreateNew(normalized, json);
    }

    static void publishCanonicalCreateNew(Path target, String content) throws Exception {
        Path normalized = target.toAbsolutePath().normalize();
        Path parent = normalized.getParent();
        if (parent == null) throw new IllegalArgumentException("canonical artifact requires a parent directory");
        Files.createDirectories(parent);
        Path temporary = parent.resolve(normalized.getFileName() + ".tmp-" + UUID.randomUUID());
        Path publicationGuard = parent.resolve(normalized.getFileName() + ".publish-guard");
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        boolean ownsTemporary = false;
        boolean ownsPublicationGuard = false;
        try {
            FileChannel opened = FileChannel.open(
                    temporary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            ownsTemporary = true;
            try (FileChannel channel = opened) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) channel.write(buffer);
                channel.force(true);
            }
            Files.writeString(
                    publicationGuard,
                    temporary.getFileName().toString() + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            ownsPublicationGuard = true;
            if (!Files.notExists(normalized)) {
                throw new java.nio.file.FileAlreadyExistsException(normalized.toString());
            }
            try {
                Files.move(temporary, normalized, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException unsupported) {
                throw new IllegalStateException("atomic canonical artifact publication is unsupported: "
                        + normalized, unsupported);
            }
        }
        catch (Exception | Error failure) {
            if (ownsTemporary) {
                try {
                    Files.deleteIfExists(temporary);
                }
                catch (Exception cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            if (ownsPublicationGuard) {
                try {
                    Files.deleteIfExists(publicationGuard);
                }
                catch (Exception cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
            }
            throw failure;
        }
        if (ownsPublicationGuard) {
            try {
                Files.deleteIfExists(publicationGuard);
            }
            catch (Exception ignored) {
                // The complete canonical target is already atomically published; a stale guard remains fail-closed.
            }
        }
    }

    private Map<String, String> splitHashes(List<SearchV3DenseAblationDataset.DatasetSlice> slices) {
        Map<String, String> hashes = new LinkedHashMap<>();
        for (SearchV3DenseAblationDataset.DatasetSlice slice : slices) {
            hashes.put(slice.split().name(), slice.manifestCombinedSha256());
        }
        return Map.copyOf(hashes);
    }

    private InputSnapshot inputSnapshot(
            List<SearchV3DenseAblationDataset.DatasetSlice> original,
            SearchV3DenseAblationDataset.LongFormManifestMetadata longForm,
            SearchV3DenseAblationDataset.RobustnessManifestMetadata robustness,
            List<TypedConstraintStressDataset.DatasetSlice> historicalStrictTyped,
            List<TypedConstraintStressDataset.DatasetSlice> officialStrictTyped) {
        return new InputSnapshot(
                HISTORICAL_INVALID_INPUT_FREEZE_COMMIT,
                OFFICIAL_INPUT_FREEZE_COMMIT,
                OFFICIAL_CAPABILITY_INPUT_FREEZE_COMMIT,
                splitHashes(original),
                longForm,
                robustness,
                typedInputSnapshot(TypedConstraintStressDataset.HISTORICAL_1_0_1, historicalStrictTyped),
                typedInputSnapshot(TypedConstraintStressDataset.OFFICIAL_1_1_0, officialStrictTyped));
    }

    private TypedInputSnapshot typedInputSnapshot(
            TypedConstraintStressDataset.DatasetIdentity identity,
            List<TypedConstraintStressDataset.DatasetSlice> slices) {
        return new TypedInputSnapshot(
                identity.version(),
                identity.rootSha256(),
                slices.stream().collect(LinkedHashMap::new,
                        (values, slice) -> values.put(slice.split().name(), slice.splitSha256()),
                        Map::putAll));
    }

    private PostRunSnapshot capturePostRunSnapshot(
            OllamaBgeM3EmbeddingClient client) throws Exception {
        SearchV3DenseAblationDataset denseLoader = new SearchV3DenseAblationDataset();
        SearchV3DenseAblationDataset.SealedManifestMetadata sealed =
                denseLoader.readSealedManifestMetadata();
        SearchV3DenseAblationDataset.LongFormManifestMetadata longForm =
                denseLoader.readLongFormManifestMetadata();
        SearchV3DenseAblationDataset.RobustnessManifestMetadata robustness =
                denseLoader.readRobustnessManifestMetadata();
        List<SearchV3DenseAblationDataset.DatasetSlice> original = List.of(
                denseLoader.load(SearchV3DenseAblationDataset.Split.DEV),
                denseLoader.load(SearchV3DenseAblationDataset.Split.CALIBRATION));
        // Reload every regression input after the run so mutation cannot hide behind root metadata.
        denseLoader.loadLongForm(SearchV3DenseAblationDataset.Split.DEV);
        denseLoader.loadLongForm(SearchV3DenseAblationDataset.Split.CALIBRATION);
        denseLoader.loadRobustness(SearchV3DenseAblationDataset.Split.DEV);
        denseLoader.loadRobustness(SearchV3DenseAblationDataset.Split.CALIBRATION);
        denseLoader.loadTypedStressHistorical(SearchV3DenseAblationDataset.Split.DEV);
        denseLoader.loadTypedStressHistorical(SearchV3DenseAblationDataset.Split.CALIBRATION);
        denseLoader.loadTypedStressOfficial(SearchV3DenseAblationDataset.Split.DEV);
        denseLoader.loadTypedStressOfficial(SearchV3DenseAblationDataset.Split.CALIBRATION);

        TypedConstraintStressDataset strictLoader = new TypedConstraintStressDataset();
        List<TypedConstraintStressDataset.DatasetSlice> historical = List.of(
                strictLoader.load(TypedConstraintStressDataset.HISTORICAL_1_0_1,
                        TypedConstraintStressDataset.Split.DEV),
                strictLoader.load(TypedConstraintStressDataset.HISTORICAL_1_0_1,
                        TypedConstraintStressDataset.Split.CALIBRATION));
        List<TypedConstraintStressDataset.DatasetSlice> official = List.of(
                strictLoader.load(TypedConstraintStressDataset.OFFICIAL_1_1_0,
                        TypedConstraintStressDataset.Split.DEV),
                strictLoader.load(TypedConstraintStressDataset.OFFICIAL_1_1_0,
                        TypedConstraintStressDataset.Split.CALIBRATION));
        assertStrictTypedIdentity(
                historical, TypedConstraintStressDataset.HISTORICAL_1_0_1, 26, 25, 104);
        assertStrictTypedIdentity(
                official, TypedConstraintStressDataset.OFFICIAL_1_1_0, 24, 24, 96);
        OllamaBgeM3EmbeddingClient.ModelMetadata model = client.inspectModel();
        return new PostRunSnapshot(
                git("rev-parse", "HEAD"),
                git("status", "--porcelain", "--untracked-files=all"),
                model,
                inputSnapshot(original, longForm, robustness, historical, official),
                executionSourceSnapshot(),
                sealed);
    }

    static void requirePostRunIntegrity(
            String codeFreezeCommit,
            OllamaBgeM3EmbeddingClient.ModelMetadata modelBefore,
            InputSnapshot inputsBefore,
            ExecutionSourceSnapshot sourceBefore,
            SearchV3DenseAblationDataset.SealedManifestMetadata sealedBefore,
            PostRunSnapshot after) {
        requireIntegrity(codeFreezeCommit.equals(after.headCommit()), "POST_RUN_HEAD_CHANGED");
        requireIntegrity(after.gitStatus().isBlank(), "POST_RUN_WORKTREE_NOT_CLEAN");
        requireIntegrity(modelBefore.equals(after.model()), "POST_RUN_MODEL_CHANGED");
        requireIntegrity(inputsBefore.equals(after.inputs()), "POST_RUN_INPUT_CHANGED");
        requireIntegrity(sourceBefore.equals(after.executionSource()), "POST_RUN_SOURCE_CHANGED");
        requireIntegrity(sealedBefore.equals(after.sealedFinal()), "POST_RUN_SEALED_METADATA_CHANGED");
        requireIntegrity(
                SearchV3DenseAblationDataset.SEALED_FINAL_SHA256.equals(after.sealedFinal().combinedSha256())
                        && !after.sealedFinal().opened()
                        && !after.sealedFinal().searchExecuted(),
                "POST_RUN_SEALED_GUARD_FAILED");
    }

    private static void requireIntegrity(boolean condition, String finding) {
        if (!condition) throw new OfficialIntegrityException(finding);
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
        System.out.println("PRZ028_FINAL_ROLE=" + assessment.role());
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

    private static String git(String... arguments) throws Exception {
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

    record ClaimContract(
            String codeFreezeCommit,
            String inputSha256,
            String modelName,
            String modelDigest,
            int modelDimensions,
            String similarity,
            String candidateK,
            String t0Profile,
            String t1Profile,
            String verdictPolicyVersion) {
        ClaimContract {
            Objects.requireNonNull(codeFreezeCommit, "codeFreezeCommit");
            Objects.requireNonNull(inputSha256, "inputSha256");
            Objects.requireNonNull(modelName, "modelName");
            Objects.requireNonNull(modelDigest, "modelDigest");
            Objects.requireNonNull(similarity, "similarity");
            Objects.requireNonNull(candidateK, "candidateK");
            Objects.requireNonNull(t0Profile, "t0Profile");
            Objects.requireNonNull(t1Profile, "t1Profile");
            Objects.requireNonNull(verdictPolicyVersion, "verdictPolicyVersion");
        }
    }

    @FunctionalInterface
    interface ThrowingOfficialAction<T> {
        T run() throws Throwable;
    }

    record PublishedSummary(
            Path output,
            String reportSha256,
            OllamaBgeM3EmbeddingClient.ModelMetadata model,
            List<SuiteReport> suites,
            SearchV3TypedConstraintAblationEngine.ExperimentReport stress,
            Assessment assessment,
            SearchV3DenseAblationDataset.SealedManifestMetadata sealedFinal) {
        PublishedSummary {
            suites = List.copyOf(suites);
        }
    }

    record Assessment(
            boolean candidateIdentityParity,
            boolean semanticExactOrderParity,
            boolean recallNonDegraded,
            boolean ndcgNonDegraded,
            boolean operationalCostAccepted,
            long directRank1LossCount,
            List<String> integrityFailures,
            List<String> validationFailures,
            boolean typedRankQualityImproved,
            boolean predictedSatisfiedAt1NotWorse,
            boolean hardNegativeImproved,
            long winningUserCount,
            long winningPrimaryFamilyCount,
            long directWins,
            long directLosses,
            long directTies,
            List<String> winQueryIds,
            List<String> lossQueryIds,
            String role) {
    }

    record TypedInputSnapshot(String datasetVersion, String rootSha256, Map<String, String> splitSha256) {
    }

    record InputSnapshot(
            String historicalInvalidInputFreezeCommit,
            String officialInputFreezeCommit,
            String officialCapabilityInputFreezeCommit,
            Map<String, String> originalSplitSha256,
            SearchV3DenseAblationDataset.LongFormManifestMetadata longForm,
            SearchV3DenseAblationDataset.RobustnessManifestMetadata robustness,
            TypedInputSnapshot historicalTypedStress,
            TypedInputSnapshot officialTypedStress) {
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

    record PostRunSnapshot(
            String headCommit,
            String gitStatus,
            OllamaBgeM3EmbeddingClient.ModelMetadata model,
            InputSnapshot inputs,
            ExecutionSourceSnapshot executionSource,
            SearchV3DenseAblationDataset.SealedManifestMetadata sealedFinal) {
    }

    record InvalidOfficialReport(
            int schemaVersion,
            String phase,
            String resultStatus,
            String role,
            String officialRunClaim,
            ClaimContract claimContract,
            String failureType,
            String failureReason) {
    }

    record OfficialReport(
            int schemaVersion,
            String phase,
            String codeFreezeCommit,
            String officialRunClaim,
            ExecutionSourceSnapshot executionSource,
            VerdictPolicy verdictPolicy,
            InputSnapshot inputs,
            OllamaBgeM3EmbeddingClient.ModelMetadata model,
            String similarity,
            String candidateK,
            List<SuiteReport> suites,
            Assessment assessment,
            MemoryObservation memory,
            SearchV3DenseAblationDataset.SealedManifestMetadata sealedFinal,
            String currentFreshBaseline,
            boolean sealedFinalSemanticAccess,
            boolean sealedFinalPredictionGenerated,
            boolean sealedFinalResultGenerated) {
    }

    static final class OfficialIntegrityException extends IllegalStateException {
        OfficialIntegrityException(String message) {
            super(message);
        }
    }
}
