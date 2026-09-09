package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Official opt-in one-shot PRZ-035 Child embedding operation comparison. */
class Prz035ChildEmbeddingOperationStrategyBenchmarkTest {

    static final String PREFREEZE_PROPERTY = "prizm.prz035.prefreeze";
    static final String CODE_FREEZE_PROPERTY = "prizm.prz035.code-freeze-commit";
    static final String PREFREEZE_ENV = "PRIZM_PRZ035_PREFREEZE";
    static final String CODE_FREEZE_ENV = "PRIZM_PRZ035_CODE_FREEZE_COMMIT";
    static final Path CONTRACT = Path.of(
            "specs/PRZ-035-child-embedding-operation-strategy/execution-contract.json");
    static final Path PRZ032_OUTPUT = Path.of(
            "local/search-v3-evaluation/prz032/minimal-v3-shadow-output.json");
    static final Path PRZ032_REPORT = Path.of(
            "local/search-v3-evaluation/prz032/minimal-v3-shadow-report.json");
    static final Path PRZ033_CANDIDATE = Path.of(
            "local/search-v3-evaluation/prz033/candidate-input.json");
    static final Path PRZ034_INPUT = Path.of(
            "local/search-v3-evaluation/prz034/child-dense-v1-input.json");
    static final Path PRZ034_PREDICTION = Path.of(
            "local/search-v3-evaluation/prz034/child-dense-v1-prediction.json");
    static final Path PRZ034_REPORT = Path.of(
            "local/search-v3-evaluation/prz034/atomic-child-dense-selector-report.json");
    static final Path STRATEGY_A_OUTPUT = Path.of(
            "local/search-v3-evaluation/prz035/precomputed-output.json");
    static final Path STRATEGY_B_OUTPUT = Path.of(
            "local/search-v3-evaluation/prz035/on-demand-output.json");
    static final Path REPORT = Path.of(
            "local/search-v3-evaluation/prz035/child-embedding-operation-report.json");
    static final Path SEALED_MANIFEST = Path.of(
            "src/test/resources/search-v3-evaluation/sealed-final/manifest.json");
    static final String SEALED_GIT_PATH =
            "src/test/resources/search-v3-evaluation/sealed-final";
    static final String SEALED_COMBINED =
            "e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383";
    static final String SEALED_MANIFEST_SHA =
            "d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa";
    static final String SEALED_TREE = "a129080861d7dafd32a9b3b3357b61aebb237e59";
    static final String EXPECTED_MODEL = "bge-m3:latest";
    static final String REPORT_ARTIFACT = "PRZ035_CHILD_EMBEDDING_OPERATION_REPORT";
    static final String CONTRACT_ARTIFACT =
            "PRZ035_CHILD_EMBEDDING_OPERATION_EXECUTION_CONTRACT";
    static final String CONTRACT_STATUS = "OFFICIAL_COMPARISON_NOT_RUN";
    static final String EXPECTED_PRZ034_INPUT_FILE_SHA256 =
            "a709088efddfc7d4e849c0839718928e91634b38f80afb40c1e4ea65f9d9bb2c";
    static final String EXPECTED_PRZ034_PREDICTION_FILE_SHA256 =
            "1fccea4a36893ec379bfa61d6bfeafbe823d59c963378ffc26d884b1b03b28b1";
    static final String EXPECTED_PRZ034_PREDICTION_CANONICAL_SHA256 =
            "7d3023903fa4d1178dd0bf624f042d3fb09a9b54f2e1f4b1b5942f0ff241bab0";
    static final String EXPECTED_PRZ034_REPORT_SHA256 =
            "3ab5915c6fca15ceb30515f731c081c2321fb95a5f17c7141163005b55511ec1";
    static final Pattern SHA40 = Pattern.compile("^[0-9a-f]{40}$");

    static final String COMPARISON_CONFIG = String.join("\n",
            "version=PRZ035_COMPARISON_V1",
            "input=PRZ034_DEV_CAL_117",
            "b3PassageOrder=FROZEN",
            "selector=CHILD_DENSE_V1",
            "strategyA=PRECOMPUTE_ALL_CORPUS_CHILDREN",
            "strategyB=ON_DEMAND_TOP5_PER_QUERY_NO_APPLICATION_CACHE",
            "executionOrder=FRESH_B3_REPLAY_THEN_A_THEN_B",
            "strategyResultParity=EXACT",
            "goldAfterBothOutputs=true",
            "projectionQueries=1,10,50,100",
            "decisionPrecomputeStorageMaxBytes=1048576",
            "decisionOnDemandP95PenaltyMinMs=10",
            "decisionOnDemandP95RatioMin=1.25",
            "decisionBreakEvenMaxQueries=50",
            "sealedFinal=false",
            "oneShot=true") + "\n";

    static final List<String> SOURCE_FILES = List.of(
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "SearchV3ChildEmbeddingOperationStrategy.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "Prz035ChildEmbeddingOperationStrategyBenchmarkTest.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "SearchV3AtomicChildDenseSelector.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "SearchV3AtomicChildSelectionCeiling.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "SearchV3MinimalShadowDataset.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "SearchV3MinimalShadowFreeze.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "SearchV3MinimalShadowGold.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "SearchV3MinimalShadowEvaluator.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "MinimalV3ShadowAdapter.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "OllamaBgeM3EmbeddingClient.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "StructuralBlockParser.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "StructuralEvidenceChildBuilder.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "StructuralRetrievalPassageBuilder.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "EvidenceValidationSelector.java");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void printsFrozenContractInputsWithoutCallingModelOrGold() throws Exception {
        assumeTrue(prefreezeEnabled(), "PRZ-035 prefreeze is opt-in and model-free");
        BaseArtifacts base = loadBaseArtifacts();
        SearchV3AtomicChildDenseSelector selector = new SearchV3AtomicChildDenseSelector();
        SearchV3AtomicChildDenseSelector.FrozenSelectorInput frozen =
                selector.deriveInput(base.candidate(), base.runtime());
        assertThat(frozen.canonicalSha256())
                .isEqualTo(SearchV3AtomicChildDenseSelector.EXPECTED_INPUT_CANONICAL_SHA256);
        assertThat(fileSha256(PRZ034_INPUT)).isEqualTo(EXPECTED_PRZ034_INPUT_FILE_SHA256);
        assertThat(fileSha256(PRZ034_PREDICTION))
                .isEqualTo(EXPECTED_PRZ034_PREDICTION_FILE_SHA256);
        assertThat(fileSha256(PRZ034_REPORT)).isEqualTo(EXPECTED_PRZ034_REPORT_SHA256);
        System.out.println("PRZ035_INPUT_SHA256=" + frozen.canonicalSha256());
        System.out.println("PRZ035_STRATEGY_DEFINITION_SHA256="
                + SearchV3ChildEmbeddingOperationStrategy.STRATEGY_DEFINITION_SHA256);
        System.out.println("PRZ035_COMPARISON_CONFIG_SHA256=" + comparisonConfigSha256());
        System.out.println("PRZ035_SOURCE_SHA256=" + sourceHash(SOURCE_FILES));
        System.out.println("PRZ035_BGE_DIGEST="
                + SearchV3AtomicChildDenseSelector.EXPECTED_BGE_DIGEST);
    }

    @Test
    void runsOfficialOperationComparisonOnceAfterCodeFreeze() throws Exception {
        String codeFreeze = System.getProperty(
                CODE_FREEZE_PROPERTY,
                System.getenv().getOrDefault(CODE_FREEZE_ENV, ""));
        assumeTrue(!codeFreeze.isBlank(), "PRZ-035 official comparison is opt-in");
        assertThat(codeFreeze).matches(SHA40);

        String runHead = git("rev-parse", "HEAD");
        assertThat(git("rev-parse", codeFreeze)).isEqualTo(codeFreeze);
        assertThat(git("merge-base", "--is-ancestor", codeFreeze, runHead)).isBlank();
        assertThat(git("diff", "--name-only", codeFreeze + ".." + runHead))
                .isEqualTo(CONTRACT.toString().replace('\\', '/'));
        assertThat(git("status", "--porcelain")).isBlank();
        assertThat(Files.exists(STRATEGY_A_OUTPUT)).isFalse();
        assertThat(Files.exists(STRATEGY_B_OUTPUT)).isFalse();
        assertThat(Files.exists(REPORT)).isFalse();

        Contract contract = readContract();
        assertThat(contract.status()).isEqualTo(CONTRACT_STATUS);
        assertThat(contract.codeFreezeCommit()).isEqualTo(codeFreeze);
        assertThat(contract.selectorInputCanonicalSha256())
                .isEqualTo(SearchV3AtomicChildDenseSelector.EXPECTED_INPUT_CANONICAL_SHA256);
        assertThat(contract.prz034PredictionCanonicalSha256())
                .isEqualTo(EXPECTED_PRZ034_PREDICTION_CANONICAL_SHA256);
        assertThat(contract.bgeM3Digest())
                .isEqualTo(SearchV3AtomicChildDenseSelector.EXPECTED_BGE_DIGEST);
        assertThat(contract.selectorSourceSha256())
                .isEqualTo(fileSha256(Path.of(
                        "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                                + "SearchV3AtomicChildDenseSelector.java")));
        assertThat(contract.strategyDefinitionSha256())
                .isEqualTo(SearchV3ChildEmbeddingOperationStrategy.STRATEGY_DEFINITION_SHA256);
        assertThat(contract.comparisonConfigSha256()).isEqualTo(comparisonConfigSha256());
        assertThat(contract.sourceSha256()).isEqualTo(sourceHash(SOURCE_FILES));

        SealedSnapshot sealedBefore = sealedSnapshot();
        BaseArtifacts base = loadBaseArtifacts();
        SearchV3AtomicChildDenseSelector selector = new SearchV3AtomicChildDenseSelector();
        SearchV3AtomicChildDenseSelector.FrozenSelectorInput derived =
                selector.deriveInput(base.candidate(), base.runtime());
        SearchV3AtomicChildDenseSelector.VerifiedSelectorInput input =
                selector.verifyInput(PRZ034_INPUT, derived);
        assertThat(input.fileSha256()).isEqualTo(EXPECTED_PRZ034_INPUT_FILE_SHA256);
        SearchV3AtomicChildDenseSelector.VerifiedPrediction frozenPrz034 =
                loadAndVerifyPrz034Prediction(selector);

        OllamaBgeM3EmbeddingClient model = new OllamaBgeM3EmbeddingClient();
        OllamaBgeM3EmbeddingClient.ModelMetadata modelBefore = model.inspectModel();
        assertThat(modelBefore.resolvedName()).isEqualTo(EXPECTED_MODEL);
        assertThat(modelBefore.digest())
                .isEqualTo(SearchV3AtomicChildDenseSelector.EXPECTED_BGE_DIGEST);
        assertThat(modelBefore.dimensions()).isEqualTo(SearchV3AtomicChildDenseSelector.DIMENSIONS);
        assertThat(modelBefore.embeddingCapable()).isTrue();

        MinimalV3ShadowAdapter v3 = new MinimalV3ShadowAdapter(model);
        MinimalV3ShadowAdapter.IndexedCorpus corpus = v3.index(base.runtime().documents());
        assertThat(corpus.passages()).hasSize(
                SearchV3ChildEmbeddingOperationStrategy.EXPECTED_PASSAGE_COUNT);
        assertThat(v3IndexUnits(corpus)).isEqualTo(base.artifact().output().v3IndexUnits());

        Map<String, SearchV3MinimalShadowFreeze.QueryOutput> frozenQueries = new LinkedHashMap<>();
        for (SearchV3MinimalShadowFreeze.QueryOutput query : base.artifact().output().queries()) {
            assertThat(frozenQueries.put(query.queryId(), query)).isNull();
        }
        Map<String, SearchV3AtomicChildDenseSelector.QueryVector> queryVectors =
                new LinkedHashMap<>();
        Map<String, Double> freshB3QueryMs = new LinkedHashMap<>();
        long queryEmbeddingNanos = 0L;
        for (SearchV3MinimalShadowDataset.RuntimeQuery query : base.runtime().queries()) {
            OllamaBgeM3EmbeddingClient.EmbeddingBatch embedded = model.embedOne(query.text());
            queryEmbeddingNanos += embedded.elapsedNanos();
            float[] shared = embedded.embeddings().get(0);
            MinimalV3ShadowAdapter.QueryRun fresh = v3.query(
                    corpus, query, shared, millis(embedded.elapsedNanos()));
            SearchV3AtomicChildDenseSelector.QueryVector token =
                    selector.verifiedB3QueryVector(
                            required(frozenQueries, query.queryId()), fresh, query.text(), shared);
            assertThat(token.vector()).isSameAs(shared);
            assertThat(queryVectors.put(query.queryId(), token)).isNull();
            assertThat(freshB3QueryMs.put(query.queryId(), fresh.totalMs())).isNull();
        }
        assertThat(queryVectors).hasSize(117);
        assertQueryVectorParity(queryVectors, frozenPrz034);

        SearchV3ChildEmbeddingOperationStrategy operation =
                new SearchV3ChildEmbeddingOperationStrategy();
        SearchV3ChildEmbeddingOperationStrategy.CorpusInventory inventory =
                operation.inventory(corpus, input);
        SearchV3ChildEmbeddingOperationStrategy.BatchEmbedder embedder = texts -> {
            OllamaBgeM3EmbeddingClient.EmbeddingBatch batch = model.embedAll(texts);
            return new SearchV3ChildEmbeddingOperationStrategy.EmbeddingResult(
                    batch.embeddings(), batch.elapsedNanos());
        };
        SearchV3ChildEmbeddingOperationStrategy.StrategyRun precomputed =
                operation.runPrecomputed(inventory, input, queryVectors, embedder);
        SearchV3ChildEmbeddingOperationStrategy.StrategyRun onDemand =
                operation.runOnDemandNoCache(
                        inventory, input, queryVectors, precomputed.childVectorHashes(), embedder);
        SearchV3ChildEmbeddingOperationStrategy.ResultParity predictionParity =
                operation.assertResultParity(precomputed, onDemand, frozenPrz034);

        OllamaBgeM3EmbeddingClient.ModelMetadata modelAfter = model.inspectModel();
        assertThat(modelAfter).isEqualTo(modelBefore);

        SearchV3AtomicChildDenseSelector.VerifiedPrediction precomputedPrediction =
                carrierPrediction(frozenPrz034, precomputed);
        SearchV3AtomicChildDenseSelector.VerifiedPrediction onDemandPrediction =
                carrierPrediction(frozenPrz034, onDemand);
        SearchV3MinimalShadowFreeze.OutputArtifact expectedOutput = selector.buildS1Output(
                input, frozenPrz034, base.artifact().output(), base.runtime());
        SearchV3MinimalShadowFreeze.OutputArtifact precomputedOutput = selector.buildS1Output(
                input, precomputedPrediction, base.artifact().output(), base.runtime());
        SearchV3MinimalShadowFreeze.OutputArtifact onDemandOutput = selector.buildS1Output(
                input, onDemandPrediction, base.artifact().output(), base.runtime());
        FinalIdentity expectedFinal = finalIdentity(expectedOutput);
        FinalIdentity precomputedFinal = finalIdentity(precomputedOutput);
        FinalIdentity onDemandFinal = finalIdentity(onDemandOutput);
        assertThat(precomputedFinal).isEqualTo(expectedFinal);
        assertThat(onDemandFinal).isEqualTo(expectedFinal);

        SearchV3MinimalShadowFreeze freezer = new SearchV3MinimalShadowFreeze();
        SearchV3MinimalShadowFreeze.FrozenOutput frozenA = freezer.freeze(precomputedOutput);
        SearchV3MinimalShadowFreeze.FrozenOutput frozenB = freezer.freeze(onDemandOutput);
        freezer.writeCreateNew(STRATEGY_A_OUTPUT, frozenA);
        freezer.writeCreateNew(STRATEGY_B_OUTPUT, frozenB);
        SearchV3MinimalShadowFreeze.VerifiedOutput verifiedA =
                freezer.verify(STRATEGY_A_OUTPUT, frozenA);
        SearchV3MinimalShadowFreeze.VerifiedOutput verifiedB =
                freezer.verify(STRATEGY_B_OUTPUT, frozenB);

        GoldAfterBothOutputsGuard goldGuard = new GoldAfterBothOutputsGuard();
        goldGuard.verifyOutputs(verifiedA, verifiedB);
        SearchV3MinimalShadowGold.GoldSnapshot gold = goldGuard.joinGold(() ->
                new SearchV3MinimalShadowGold().loadAfterOutputVerified(verifiedA, base.runtime()));
        SearchV3MinimalShadowEvaluator evaluator = new SearchV3MinimalShadowEvaluator();
        MetricSnapshot expectedMetrics = metrics(evaluator.evaluate(expectedOutput, gold));
        MetricSnapshot precomputedMetrics = metrics(evaluator.evaluate(precomputedOutput, gold));
        MetricSnapshot onDemandMetrics = metrics(evaluator.evaluate(onDemandOutput, gold));
        assertThat(precomputedMetrics).isEqualTo(expectedMetrics);
        assertThat(onDemandMetrics).isEqualTo(expectedMetrics);

        SearchV3ChildEmbeddingOperationStrategy.StorageEstimate storage =
                operation.storageEstimate(inventory);
        SearchV3ChildEmbeddingOperationStrategy.QueryDistribution distribution =
                operation.queryDistribution(onDemand);
        SearchV3ChildEmbeddingOperationStrategy.QueryLatency precomputedLatency =
                operation.queryLatency(precomputed, freshB3QueryMs);
        SearchV3ChildEmbeddingOperationStrategy.QueryLatency onDemandLatency =
                operation.queryLatency(onDemand, freshB3QueryMs);
        List<SearchV3ChildEmbeddingOperationStrategy.Projection> projections =
                operation.projections(precomputed, onDemand, 1, 10, 50, 100);
        List<StorageProjection> storageProjections = List.of(
                storageProjection(100), storageProjection(1_000), storageProjection(10_000));
        double breakEvenQueries = (double) precomputed.embeddedVectorOccurrences()
                / distribution.childCountAverage();
        IndexingObservation indexing = new IndexingObservation(
                "SUMMED_OBSERVATION_NOT_CONTIGUOUS_WALL_TIME",
                corpus.constructionMs(), corpus.embeddingMs(), corpus.indexingWallMs(),
                precomputed.embeddingWallMs(),
                corpus.constructionMs() + corpus.indexingWallMs()
                        + precomputed.embeddingWallMs(),
                corpus.constructionMs() + corpus.indexingWallMs());
        Decision decision = decide(
                storage, precomputedLatency, onDemandLatency, breakEvenQueries);
        ChangeAnalysis changeAnalysis = new ChangeAnalysis(
                "PROJECTED_DESIGN_ONLY", 100, 20, 100, 20, 80, 0,
                "EXACT_SOURCE_TEXT_SHA256+MODEL_DIGEST+DIMENSION+INPUT_POLICY",
                "NEW_VERSION_PROVENANCE_REMAINS_VERSION_SCOPED",
                "EMBEDDING_GENERATION_SHOULD_BE_SEPARATE_FROM_DOCUMENT_VERSION");
        RuntimeBoundary runtimeBoundary = new RuntimeBoundary(
                "EVALUATION_ONLY_OLLAMA_BATCH_SIMULATION",
                "PRODUCTION_EMBEDDING_SERVICE_IS_SINGLE_TEXT",
                "NO_APPLICATION_CHILD_VECTOR_CACHE",
                "OLLAMA_INTERNAL_CACHE_BEHAVIOR_NOT_VERIFIED",
                "LOCAL_WARM_ORDER_BIASED_IN_FAVOR_OF_STRATEGY_B",
                "STORED_VECTOR_DATABASE_READ_AND_INDEX_OVERHEAD_NOT_MEASURED",
                "CPU_ONLY_NOT_MEASURED",
                "GPU_OR_ACCELERATOR_STATUS_RECORDED_OUTSIDE_RUNNER");

        SealedSnapshot sealedAfter = sealedSnapshot();
        assertThat(sealedAfter).isEqualTo(sealedBefore);
        assertThat(sourceHash(SOURCE_FILES)).isEqualTo(contract.sourceSha256());
        assertThat(git("rev-parse", "HEAD")).isEqualTo(runHead);
        assertThat(git("status", "--porcelain")).isBlank();

        OfficialReport report = new OfficialReport(
                1, REPORT_ARTIFACT, codeFreeze, fileSha256(CONTRACT),
                contract.sourceSha256(), contract.strategyDefinitionSha256(),
                contract.comparisonConfigSha256(),
                base.artifact().verifiedOutput().fileSha256(),
                input.fileSha256(), frozenPrz034.fileSha256(),
                modelIdentity(modelBefore), base.runtime().queries().size(),
                corpus.passages().size(), inventory.allChildren().size(),
                input.frozen().input().uniqueChildren().size(),
                input.frozen().input().childOccurrenceCount(),
                predictionParity,
                new OutputParity(
                        true, true, true, true, true,
                        expectedFinal.sha256(), precomputedMetrics),
                strategySummary(precomputed), strategySummary(onDemand),
                indexing, storage, storageProjections, distribution,
                precomputedLatency, onDemandLatency,
                projections, breakEvenQueries, changeAnalysis, runtimeBoundary,
                decision, sealedAfter, millis(queryEmbeddingNanos));
        writeCreateNew(REPORT, report);

        System.out.println("PRZ035_STRATEGY_A_OUTPUT_SHA256=" + verifiedA.fileSha256());
        System.out.println("PRZ035_STRATEGY_B_OUTPUT_SHA256=" + verifiedB.fileSha256());
        System.out.println("PRZ035_REPORT_SHA256=" + fileSha256(REPORT));
        System.out.println("PRZ035_DECISION=" + decision);
    }

    private BaseArtifacts loadBaseArtifacts() {
        SearchV3AtomicChildSelectionCeiling ceiling =
                new SearchV3AtomicChildSelectionCeiling();
        SearchV3AtomicChildSelectionCeiling.VerifiedPrz032 artifact =
                ceiling.verifyPrz032(PRZ032_OUTPUT, PRZ032_REPORT);
        SearchV3MinimalShadowDataset.RuntimeInput runtime =
                new SearchV3MinimalShadowDataset().loadRuntime();
        SearchV3AtomicChildSelectionCeiling.FrozenCandidateInput expected =
                ceiling.deriveCandidateInput(artifact, runtime);
        SearchV3AtomicChildSelectionCeiling.VerifiedCandidateInput candidate =
                ceiling.verifyCandidateInput(PRZ033_CANDIDATE, expected);
        return new BaseArtifacts(ceiling, artifact, candidate, runtime);
    }

    private SearchV3AtomicChildDenseSelector.VerifiedPrediction loadAndVerifyPrz034Prediction(
            SearchV3AtomicChildDenseSelector selector) throws IOException {
        byte[] bytes = Files.readAllBytes(PRZ034_PREDICTION);
        assertThat(SearchV3AtomicChildDenseSelector.sha256(bytes))
                .isEqualTo(EXPECTED_PRZ034_PREDICTION_FILE_SHA256);
        JsonNode root = mapper.readTree(bytes);
        SearchV3AtomicChildDenseSelector.Prediction prediction = mapper.treeToValue(
                root.path("prediction"), SearchV3AtomicChildDenseSelector.Prediction.class);
        SearchV3AtomicChildDenseSelector.FrozenPrediction frozen =
                new SearchV3AtomicChildDenseSelector.FrozenPrediction(
                        prediction,
                        root.path("canonicalSha256").asText(),
                        root.path("canonicalByteLength").asInt(-1));
        assertThat(frozen.canonicalSha256())
                .isEqualTo(EXPECTED_PRZ034_PREDICTION_CANONICAL_SHA256);
        return selector.verifyPrediction(PRZ034_PREDICTION, frozen);
    }

    private SearchV3AtomicChildDenseSelector.VerifiedPrediction carrierPrediction(
            SearchV3AtomicChildDenseSelector.VerifiedPrediction original,
            SearchV3ChildEmbeddingOperationStrategy.StrategyRun run) {
        SearchV3AtomicChildDenseSelector.Prediction value = original.frozen().prediction();
        SearchV3AtomicChildDenseSelector.Prediction prediction =
                new SearchV3AtomicChildDenseSelector.Prediction(
                        value.schemaVersion(), value.artifactType(), value.policyVersion(),
                        value.policySha256(), value.selectorInputCanonicalSha256(), value.model(),
                        value.queryVectorSharedWithB3(), value.historicalVectorParity(),
                        value.queryCount(), value.uniqueChildEmbeddingCount(), value.cost(),
                        run.predictions());
        SearchV3AtomicChildDenseSelector.FrozenPrediction frozen =
                new SearchV3AtomicChildDenseSelector.FrozenPrediction(
                        prediction,
                        SearchV3AtomicChildDenseSelector.sha256(
                                run.strategy().name() + "|" + run.predictions().size()),
                        run.predictions().size());
        return new SearchV3AtomicChildDenseSelector.VerifiedPrediction(
                frozen, frozen.canonicalSha256(), frozen.canonicalByteLength());
    }

    private void assertQueryVectorParity(
            Map<String, SearchV3AtomicChildDenseSelector.QueryVector> fresh,
            SearchV3AtomicChildDenseSelector.VerifiedPrediction frozen) {
        Map<String, String> expected = new LinkedHashMap<>();
        frozen.frozen().prediction().queries().forEach(value ->
                assertThat(expected.put(value.queryId(), value.queryVectorSha256())).isNull());
        assertThat(fresh).hasSameSizeAs(expected);
        fresh.forEach((queryId, vector) ->
                assertThat(vector.sha256()).isEqualTo(required(expected, queryId)));
    }

    private List<SearchV3MinimalShadowFreeze.IndexUnit> v3IndexUnits(
            MinimalV3ShadowAdapter.IndexedCorpus corpus) {
        return corpus.passages().stream().map(value -> new SearchV3MinimalShadowFreeze.IndexUnit(
                value.passage().passageId(),
                value.passage().parentAnnotationCandidateId(),
                value.passage().evidenceChildren().stream().map(child -> {
                    SourceProvenance source = child.provenance();
                    return new ProductionV2ShadowAdapter.SourceSpan(
                            value.userBundleId(), source.documentId(), source.versionId(),
                            source.sourcePath(), source.page(), source.codePointStart(),
                            source.codePointEnd(), child.sourceText(), source.exactTextSha256());
                }).toList())).toList();
    }

    private FinalIdentity finalIdentity(SearchV3MinimalShadowFreeze.OutputArtifact output) {
        StringBuilder canonical = new StringBuilder();
        for (SearchV3MinimalShadowFreeze.QueryOutput query : output.queries()) {
            canonical.append(query.queryId()).append('|')
                    .append(query.v3().state()).append('|')
                    .append(query.v3().typedApplicabilityVerified()).append('|')
                    .append(query.v3().parsedConstraintCount()).append('\n');
            query.v3().candidates().forEach(value -> canonical.append("C|")
                    .append(value.rank()).append('|').append(value.candidateId()).append('|')
                    .append(Double.toHexString(value.cosineScore())).append('|')
                    .append(value.parentId()).append('|').append(value.spans()).append('\n'));
            query.v3().finalResults().forEach(value -> canonical.append("F|")
                    .append(value.rank()).append('|').append(value.candidateId()).append('|')
                    .append(value.denseRank()).append('|')
                    .append(Double.toHexString(value.cosineScore())).append('|')
                    .append(value.evidenceChildId()).append('|').append(value.span()).append('|')
                    .append(value.matchState()).append('\n'));
        }
        return new FinalIdentity(
                SearchV3AtomicChildDenseSelector.sha256(canonical.toString()),
                output.queries().stream().mapToInt(value -> value.v3().finalResults().size()).sum());
    }

    private MetricSnapshot metrics(SearchV3MinimalShadowEvaluator.EvaluationReport report) {
        SearchV3MinimalShadowEvaluator.RankingAggregate query =
                report.queryMicro().v3().finalRanking();
        SearchV3MinimalShadowEvaluator.RankingAggregate user =
                report.userMacro().v3().finalRanking();
        return new MetricSnapshot(
                query.top1(), query.mrr(), query.ndcgAt5(), query.directRecallAt5(),
                user.top1(), user.mrr());
    }

    private StrategySummary strategySummary(
            SearchV3ChildEmbeddingOperationStrategy.StrategyRun run) {
        return new StrategySummary(
                run.strategy(), run.modelInvocationCount(), run.physicalBatchCount(),
                run.embeddedVectorOccurrences(), run.uniqueAccessedChildCount(),
                run.repeatedRecalculationCount(), run.embeddingWallMs());
    }

    private Decision decide(
            SearchV3ChildEmbeddingOperationStrategy.StorageEstimate storage,
            SearchV3ChildEmbeddingOperationStrategy.QueryLatency precomputed,
            SearchV3ChildEmbeddingOperationStrategy.QueryLatency onDemand,
            double breakEvenQueries) {
        double p95Penalty = onDemand.p95Ms() - precomputed.p95Ms();
        double p95Ratio = precomputed.p95Ms() == 0.0d
                ? Double.POSITIVE_INFINITY : onDemand.p95Ms() / precomputed.p95Ms();
        boolean precompute = storage.childVectorBytes() <= 1_048_576L
                && p95Penalty >= 10.0d
                && p95Ratio >= 1.25d
                && breakEvenQueries <= 50.0d;
        if (precompute) return Decision.PRECOMPUTE_CHILD_EMBEDDINGS;
        boolean onDemandIsCheap = p95Ratio <= 1.10d && breakEvenQueries > 100.0d;
        return onDemandIsCheap
                ? Decision.ON_DEMAND_CHILD_EMBEDDINGS
                : Decision.NEEDS_HYBRID_LATER;
    }

    private StorageProjection storageProjection(int childCount) {
        return new StorageProjection(
                childCount,
                childCount * SearchV3ChildEmbeddingOperationStrategy.VECTOR_BYTES,
                "PROJECTED_RAW_VECTOR_BYTES_ONLY");
    }

    private Contract readContract() throws IOException {
        JsonNode root = mapper.readTree(Files.readString(CONTRACT, StandardCharsets.UTF_8));
        assertThat(root.path("artifactType").asText()).isEqualTo(CONTRACT_ARTIFACT);
        assertThat(root.path("schemaVersion").asInt(-1)).isEqualTo(1);
        return new Contract(
                root.path("status").asText(), root.path("codeFreezeCommit").asText(),
                root.path("selectorInputCanonicalSha256").asText(),
                root.path("prz034PredictionCanonicalSha256").asText(),
                root.path("bgeM3Digest").asText(),
                root.path("selectorSourceSha256").asText(),
                root.path("strategyDefinitionSha256").asText(),
                root.path("comparisonConfigSha256").asText(),
                root.path("sourceSha256").asText());
    }

    private SealedSnapshot sealedSnapshot() throws Exception {
        byte[] bytes = Files.readAllBytes(SEALED_MANIFEST);
        JsonNode manifest = mapper.readTree(bytes);
        assertThat(SearchV3AtomicChildDenseSelector.sha256(bytes)).isEqualTo(SEALED_MANIFEST_SHA);
        assertThat(manifest.path("combinedSha256").asText()).isEqualTo(SEALED_COMBINED);
        assertThat(manifest.path("opened").asBoolean(true)).isFalse();
        assertThat(manifest.path("searchExecuted").asBoolean(true)).isFalse();
        assertThat(git("rev-parse", "HEAD:" + SEALED_GIT_PATH)).isEqualTo(SEALED_TREE);
        return new SealedSnapshot(
                SEALED_COMBINED, SEALED_MANIFEST_SHA, SEALED_TREE,
                false, false, "NOT_RUN");
    }

    static String sourceHash(List<String> paths) throws IOException {
        StringBuilder canonical = new StringBuilder();
        for (String path : paths.stream().sorted().toList()) {
            byte[] bytes = Files.readAllBytes(Path.of(path));
            canonical.append(path.replace('\\', '/')).append('|').append(bytes.length)
                    .append('|').append(SearchV3AtomicChildDenseSelector.sha256(bytes)).append('\n');
        }
        return SearchV3AtomicChildDenseSelector.sha256(canonical.toString());
    }

    private String comparisonConfigSha256() {
        return SearchV3AtomicChildDenseSelector.sha256(COMPARISON_CONFIG);
    }

    private String fileSha256(Path path) throws IOException {
        return SearchV3AtomicChildDenseSelector.sha256(Files.readAllBytes(path));
    }

    private void writeCreateNew(Path path, Object value) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        String portable = normalized.toString().replace('\\', '/').toLowerCase();
        if (!portable.contains("/local/search-v3-evaluation/prz035/")
                || portable.contains("sealed")) {
            throw new IllegalArgumentException("invalid PRZ-035 local report path");
        }
        Files.createDirectories(normalized.getParent());
        Files.writeString(
                normalized,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
    }

    private String git(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(
                process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) throw new IllegalStateException("git failed: " + output);
        return output;
    }

    private boolean prefreezeEnabled() {
        return Boolean.getBoolean(PREFREEZE_PROPERTY)
                || Boolean.parseBoolean(System.getenv().getOrDefault(PREFREEZE_ENV, "false"));
    }

    private static <K, V> V required(Map<K, V> values, K key) {
        V value = values.get(key);
        if (value == null) throw new IllegalStateException("missing required value: " + key);
        return value;
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    private SearchV3AtomicChildDenseSelector.ModelIdentity modelIdentity(
            OllamaBgeM3EmbeddingClient.ModelMetadata value) {
        return new SearchV3AtomicChildDenseSelector.ModelIdentity(
                value.resolvedName(), value.digest(), value.dimensions(), "COSINE");
    }

    enum Decision {
        PRECOMPUTE_CHILD_EMBEDDINGS,
        ON_DEMAND_CHILD_EMBEDDINGS,
        NEEDS_HYBRID_LATER
    }

    static final class GoldAfterBothOutputsGuard {
        private Phase phase = Phase.SOURCE_ONLY;

        void verifyOutputs(
                SearchV3MinimalShadowFreeze.VerifiedOutput precomputed,
                SearchV3MinimalShadowFreeze.VerifiedOutput onDemand) {
            require(Phase.SOURCE_ONLY);
            Objects.requireNonNull(precomputed, "precomputed output");
            Objects.requireNonNull(onDemand, "on-demand output");
            phase = Phase.BOTH_OUTPUTS_VERIFIED;
        }

        <T> T joinGold(Supplier<T> supplier) {
            require(Phase.BOTH_OUTPUTS_VERIFIED);
            T value = Objects.requireNonNull(supplier.get(), "Gold");
            phase = Phase.GOLD_JOINED;
            return value;
        }

        private void require(Phase expected) {
            if (phase != expected) {
                throw new IllegalStateException(
                        "PRZ-035 phase violation: expected " + expected + " but was " + phase);
            }
        }
    }

    enum Phase {
        SOURCE_ONLY,
        BOTH_OUTPUTS_VERIFIED,
        GOLD_JOINED
    }

    record Contract(
            String status,
            String codeFreezeCommit,
            String selectorInputCanonicalSha256,
            String prz034PredictionCanonicalSha256,
            String bgeM3Digest,
            String selectorSourceSha256,
            String strategyDefinitionSha256,
            String comparisonConfigSha256,
            String sourceSha256) {
    }

    record BaseArtifacts(
            SearchV3AtomicChildSelectionCeiling ceiling,
            SearchV3AtomicChildSelectionCeiling.VerifiedPrz032 artifact,
            SearchV3AtomicChildSelectionCeiling.VerifiedCandidateInput candidate,
            SearchV3MinimalShadowDataset.RuntimeInput runtime) {
    }

    record FinalIdentity(String sha256, int finalEvidenceCount) {
    }

    record MetricSnapshot(
            double top1,
            double mrr,
            double ndcgAt5,
            double recallAt5,
            double userMacroTop1,
            double userMacroMrr) {
    }

    record OutputParity(
            boolean retrievalPassageOrderExact,
            boolean evidenceChildCandidatesExact,
            boolean finalEvidenceChildIdsExact,
            boolean metricsExact,
            boolean provenanceExact,
            String finalIdentitySha256,
            MetricSnapshot metrics) {
    }

    record StrategySummary(
            SearchV3ChildEmbeddingOperationStrategy.Strategy strategy,
            int modelInvocationCount,
            int physicalBatchCount,
            int embeddedVectorOccurrences,
            int uniqueAccessedChildCount,
            int repeatedRecalculationCount,
            double embeddingWallMs) {
    }

    record IndexingObservation(
            String measurementBoundary,
            double b3ConstructionMs,
            double b3PassageEmbeddingMs,
            double b3IndexingWallMs,
            double precomputedChildEmbeddingMs,
            double precomputedFullEvaluationIndexMs,
            double onDemandFullEvaluationIndexMs) {
    }

    record ChangeAnalysis(
            String status,
            int exampleChildCount,
            int exampleChangedChildCount,
            int precomputeWithoutReuseEmbeddingCount,
            int precomputeWithExactHashEmbeddingCount,
            int exactHashReusableVectorCount,
            int onDemandStoredChildVectorCount,
            String reuseIdentity,
            String provenanceBoundary,
            String generationBoundary) {
    }

    record RuntimeBoundary(
            String executionBoundary,
            String productionInterface,
            String applicationCache,
            String modelRuntimeCache,
            String warmOrder,
            String persistentRead,
            String cpuOnly,
            String gpu) {
    }

    record StorageProjection(int childCount, long rawVectorBytes, String status) {
    }

    record SealedSnapshot(
            String combinedSha256,
            String manifestSha256,
            String gitTree,
            boolean opened,
            boolean searchExecuted,
            String currentFreshBaseline) {
    }

    record OfficialReport(
            int schemaVersion,
            String artifactType,
            String codeFreezeCommit,
            String contractFileSha256,
            String sourceSha256,
            String strategyDefinitionSha256,
            String comparisonConfigSha256,
            String prz032OutputFileSha256,
            String selectorInputFileSha256,
            String prz034PredictionFileSha256,
            SearchV3AtomicChildDenseSelector.ModelIdentity model,
            int queryCount,
            int passageCount,
            int corpusChildCount,
            int top5UniqueChildCount,
            int top5ChildOccurrenceCount,
            SearchV3ChildEmbeddingOperationStrategy.ResultParity predictionParity,
            OutputParity outputParity,
            StrategySummary precomputed,
            StrategySummary onDemand,
            IndexingObservation indexing,
            SearchV3ChildEmbeddingOperationStrategy.StorageEstimate storage,
            List<StorageProjection> storageProjections,
            SearchV3ChildEmbeddingOperationStrategy.QueryDistribution onDemandDistribution,
            SearchV3ChildEmbeddingOperationStrategy.QueryLatency precomputedQueryLatency,
            SearchV3ChildEmbeddingOperationStrategy.QueryLatency onDemandQueryLatency,
            List<SearchV3ChildEmbeddingOperationStrategy.Projection> projections,
            double embeddingCountBreakEvenQueries,
            ChangeAnalysis documentVersionChange,
            RuntimeBoundary runtimeBoundary,
            Decision decision,
            SealedSnapshot sealedFinal,
            double commonQueryEmbeddingMs) {
        OfficialReport {
            projections = List.copyOf(projections);
            storageProjections = List.copyOf(storageProjections);
        }
    }
}
