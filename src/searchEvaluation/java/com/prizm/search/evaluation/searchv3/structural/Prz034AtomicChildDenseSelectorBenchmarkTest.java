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
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Official opt-in one-shot PRZ-034 CHILD_DENSE_V1 comparison. */
class Prz034AtomicChildDenseSelectorBenchmarkTest {

    static final String PREFREEZE_PROPERTY = "prizm.prz034.prefreeze-input";
    static final String CODE_FREEZE_PROPERTY = "prizm.prz034.code-freeze-commit";
    static final String PREFREEZE_ENV = "PRIZM_PRZ034_PREFREEZE_INPUT";
    static final String CODE_FREEZE_ENV = "PRIZM_PRZ034_CODE_FREEZE_COMMIT";
    static final Path CONTRACT = Path.of(
            "specs/PRZ-034-atomic-evidence-child-selector/execution-contract.json");
    static final Path PRZ032_OUTPUT = Path.of(
            "local/search-v3-evaluation/prz032/minimal-v3-shadow-output.json");
    static final Path PRZ032_REPORT = Path.of(
            "local/search-v3-evaluation/prz032/minimal-v3-shadow-report.json");
    static final Path PRZ033_CANDIDATE = Path.of(
            "local/search-v3-evaluation/prz033/candidate-input.json");
    static final Path PRZ033_REPORT = Path.of(
            "local/search-v3-evaluation/prz033/atomic-child-selection-ceiling.json");
    static final Path SELECTOR_INPUT = Path.of(
            "local/search-v3-evaluation/prz034/child-dense-v1-input.json");
    static final Path PREDICTION = Path.of(
            "local/search-v3-evaluation/prz034/child-dense-v1-prediction.json");
    static final Path REPORT = Path.of(
            "local/search-v3-evaluation/prz034/atomic-child-dense-selector-report.json");
    static final Path SEALED_MANIFEST = Path.of(
            "src/test/resources/search-v3-evaluation/sealed-final/manifest.json");
    static final String SEALED_GIT_PATH =
            "src/test/resources/search-v3-evaluation/sealed-final";
    static final String SEALED_COMBINED =
            "e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383";
    static final String SEALED_MANIFEST_SHA =
            "d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa";
    static final String SEALED_TREE = "a129080861d7dafd32a9b3b3357b61aebb237e59";
    static final String CONTRACT_ARTIFACT =
            "PRZ034_ATOMIC_CHILD_DENSE_SELECTOR_EXECUTION_CONTRACT";
    static final String REPORT_ARTIFACT = "PRZ034_ATOMIC_CHILD_DENSE_SELECTOR_REPORT";
    static final String CONTRACT_STATUS = "OFFICIAL_COMPARISON_NOT_RUN";
    static final String EXPECTED_MODEL = "bge-m3:latest";
    static final Pattern SHA40 = Pattern.compile("^[0-9a-f]{40}$");

    static final String COMPARISON_POLICY = String.join("|",
            "PRZ034_POLICY_V1",
            "PRZ032_FROZEN_S0",
            "FRESH_B3_IDENTITY_ORDER_SCORE_AND_S0_PARITY",
            "CHILD_DENSE_V1_SOURCE_TEXT_ONLY",
            "BGE_M3_SAME_QUERY_VECTOR",
            "TOP5_PASSAGES",
            "SAME_PASSAGE_ONLY",
            "NO_CANDIDATE_ADD_DELETE_MERGE",
            "SOURCE_ORDER_STABLE_TIE",
            "PRZ029_TYPED_STATE_LOCK",
            "SLICE_REGRESSION_NEEDS_ADJUSTMENT_LT_MINUS_0_10",
            "RESULT_LIMIT_5",
            "DEV_CAL_117",
            "PREDICTION_FREEZE_BEFORE_GOLD",
            "PRZ033_ORACLE_AFTER_S0_S1_EVALUATION",
            "ONE_SHOT");

    static final List<String> SOURCE_FILES = List.of(
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "StructuralDocument.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "StructuralBlock.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "StructuralBlockType.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "EvidenceChild.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "RetrievalPassage.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "SourceProvenance.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "StructuralBlockParser.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "StructuralEvidenceChildBuilder.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "StructuralRetrievalPassageBuilder.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "EvidenceValidationSelector.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "OllamaBgeM3EmbeddingClient.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "MinimalV3ShadowAdapter.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "ProductionV2ShadowAdapter.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "SearchV3MinimalShadowDataset.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "SearchV3MinimalShadowFreeze.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "SearchV3MinimalShadowGold.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "SearchV3MinimalShadowEvaluator.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "SearchV3AtomicChildSelectionCeiling.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "SearchV3AtomicChildDenseSelector.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/"
                    + "Prz034AtomicChildDenseSelectorBenchmarkTest.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/typed/"
                    + "DeterministicTypedObservationExtractor.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/typed/"
                    + "DeterministicTypedQueryParser.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/typed/"
                    + "TypedConstraintEvaluator.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/typed/"
                    + "TypedTextSupport.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/typed/"
                    + "TypedValueModel.java");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void printsGoldFreeSelectorInputIdentityWithoutCallingModel() throws Exception {
        assumeTrue(prefreezeEnabled(),
                "PRZ-034 input prefreeze is opt-in and model-free");

        BaseArtifacts base = loadBaseArtifacts();
        SearchV3AtomicChildDenseSelector selector = new SearchV3AtomicChildDenseSelector();
        SearchV3AtomicChildDenseSelector.FrozenSelectorInput frozen =
                selector.deriveInput(base.candidate(), base.runtime());

        assertThat(frozen.input().queries()).hasSize(117);
        assertThat(frozen.input().uniqueChildren()).hasSize(227);
        assertThat(frozen.input().passageOccurrenceCount()).isEqualTo(507);
        assertThat(frozen.input().childOccurrenceCount()).isEqualTo(804);
        System.out.println("PRZ034_INPUT_CANONICAL_SHA256=" + frozen.canonicalSha256());
        System.out.println("PRZ034_INPUT_CANONICAL_BYTE_LENGTH=" + frozen.canonicalByteLength());
        System.out.println("PRZ034_SELECTOR_POLICY_SHA256="
                + SearchV3AtomicChildDenseSelector.POLICY_SHA256);
        System.out.println("PRZ034_COMPARISON_POLICY_SHA256=" + comparisonPolicySha256());
        System.out.println("PRZ034_SOURCE_SHA256=" + sourceHash(SOURCE_FILES));
        System.out.println("PRZ034_MODEL_DIGEST="
                + SearchV3AtomicChildDenseSelector.EXPECTED_BGE_DIGEST);
        System.out.println("PRZ034_TOP_PASSAGE_K="
                + SearchV3AtomicChildDenseSelector.TOP_PASSAGE_K);
    }

    @Test
    void dryRunsStableOverlayWithoutModelOrGoldAndPreservesTypedStateAndProvenance() {
        assumeTrue(prefreezeEnabled(),
                "PRZ-034 overlay preflight is opt-in and model-free");
        BaseArtifacts base = loadBaseArtifacts();
        SearchV3AtomicChildDenseSelector selector = new SearchV3AtomicChildDenseSelector();
        SearchV3AtomicChildDenseSelector.FrozenSelectorInput frozen =
                selector.deriveInput(base.candidate(), base.runtime());
        SearchV3AtomicChildDenseSelector.VerifiedSelectorInput input =
                new SearchV3AtomicChildDenseSelector.VerifiedSelectorInput(
                        frozen, "MODEL_FREE_PREFLIGHT", 0L);

        Map<String, SearchV3AtomicChildDenseSelector.QueryVector> queries = new LinkedHashMap<>();
        for (SearchV3AtomicChildDenseSelector.SelectorQueryInput query : frozen.input().queries()) {
            float[] vector = axisVector();
            queries.put(query.queryId(), new SearchV3AtomicChildDenseSelector.QueryVector(
                    query.queryId(), query.queryTextSha256(), "MODEL_FREE_B3_PARITY_TOKEN",
                    vector, SearchV3AtomicChildDenseSelector.vectorSha256(vector), true));
        }
        Map<String, float[]> children = new LinkedHashMap<>();
        for (SearchV3AtomicChildDenseSelector.UniqueChildInput child
                : frozen.input().uniqueChildren()) {
            children.put(child.evidenceChildId(), axisVector());
        }
        SearchV3AtomicChildDenseSelector.EmbeddingCostObservation cost =
                new SearchV3AtomicChildDenseSelector.EmbeddingCostObservation(
                        160, 227, 8, 0.0d, 0.0d, 0.0d, 929_792L);
        SearchV3AtomicChildDenseSelector.FrozenPrediction predicted = selector.predict(
                input,
                new SearchV3AtomicChildDenseSelector.ModelIdentity(
                        EXPECTED_MODEL,
                        SearchV3AtomicChildDenseSelector.EXPECTED_BGE_DIGEST,
                        SearchV3AtomicChildDenseSelector.DIMENSIONS,
                        "COSINE"),
                queries,
                children,
                cost);
        SearchV3AtomicChildDenseSelector.VerifiedPrediction output =
                new SearchV3AtomicChildDenseSelector.VerifiedPrediction(
                        predicted, "MODEL_FREE_PREFLIGHT", 0L);

        SearchV3MinimalShadowFreeze.OutputArtifact replay = selector.buildS1Output(
                input, output, base.artifact().output(), base.runtime());
        assertThat(replay.queries()).hasSameSizeAs(base.artifact().output().queries());
        for (int index = 0; index < replay.queries().size(); index++) {
            SearchV3MinimalShadowFreeze.QueryOutput before =
                    base.artifact().output().queries().get(index);
            SearchV3MinimalShadowFreeze.QueryOutput after = replay.queries().get(index);
            assertThat(after.queryId()).isEqualTo(before.queryId());
            assertThat(after.v3().state()).isEqualTo(before.v3().state());
            assertThat(after.v3().typedApplicabilityVerified())
                    .isEqualTo(before.v3().typedApplicabilityVerified());
            assertThat(after.v3().parsedConstraintCount())
                    .isEqualTo(before.v3().parsedConstraintCount());
            assertThat(after.v3().candidates()).isEqualTo(before.v3().candidates());
            assertThat(after.v3().finalResults()).isEqualTo(before.v3().finalResults());
        }
    }

    @Test
    void runsOfficialChildDenseComparisonOnceAfterContractFreeze() throws Exception {
        String codeFreeze = System.getProperty(
                CODE_FREEZE_PROPERTY,
                System.getenv().getOrDefault(CODE_FREEZE_ENV, ""));
        assumeTrue(!codeFreeze.isBlank(), "PRZ-034 official comparison is opt-in");
        assertThat(codeFreeze).matches(SHA40);

        String runHead = git("rev-parse", "HEAD");
        assertThat(git("rev-parse", codeFreeze)).isEqualTo(codeFreeze);
        assertThat(git("merge-base", "--is-ancestor", codeFreeze, runHead)).isBlank();
        assertThat(git("diff", "--name-only", codeFreeze + ".." + runHead))
                .isEqualTo(CONTRACT.toString().replace('\\', '/'));
        assertThat(git("status", "--porcelain")).isBlank();
        assertThat(Files.exists(SELECTOR_INPUT)).isFalse();
        assertThat(Files.exists(PREDICTION)).isFalse();
        assertThat(Files.exists(REPORT)).isFalse();

        Contract contract = readContract();
        assertThat(contract.status()).isEqualTo(CONTRACT_STATUS);
        assertThat(contract.codeFreezeCommit()).isEqualTo(codeFreeze);
        assertThat(contract.selectorPolicyVersion())
                .isEqualTo(SearchV3AtomicChildDenseSelector.POLICY_VERSION);
        assertThat(contract.selectorPolicySha256())
                .isEqualTo(SearchV3AtomicChildDenseSelector.POLICY_SHA256);
        assertThat(contract.sourceSha256()).isEqualTo(sourceHash(SOURCE_FILES));
        assertThat(contract.modelDigest())
                .isEqualTo(SearchV3AtomicChildDenseSelector.EXPECTED_BGE_DIGEST);
        assertThat(contract.topPassageK())
                .isEqualTo(SearchV3AtomicChildDenseSelector.TOP_PASSAGE_K);
        assertThat(contract.comparisonPolicySha256()).isEqualTo(comparisonPolicySha256());
        assertThat(contract.selectorInputCanonicalSha256())
                .isEqualTo(SearchV3AtomicChildDenseSelector.EXPECTED_INPUT_CANONICAL_SHA256);

        SealedSnapshot sealedBefore = sealedSnapshot();
        SearchV3AtomicChildDenseSelector selector = new SearchV3AtomicChildDenseSelector();
        SearchV3AtomicChildDenseSelector.PhaseGuard guard =
                new SearchV3AtomicChildDenseSelector.PhaseGuard();
        BaseArtifacts base = guard.verifyArtifact(this::loadBaseArtifacts);
        SearchV3AtomicChildDenseSelector.FrozenSelectorInput frozenInput = guard.freezeInput(
                () -> selector.deriveInput(base.candidate(), base.runtime()));
        assertThat(frozenInput.canonicalSha256())
                .isEqualTo(contract.selectorInputCanonicalSha256());
        selector.writeInputCreateNew(SELECTOR_INPUT, frozenInput);
        SearchV3AtomicChildDenseSelector.VerifiedSelectorInput verifiedInput = guard.verifyInput(
                () -> selector.verifyInput(SELECTOR_INPUT, frozenInput));

        OllamaBgeM3EmbeddingClient model = new OllamaBgeM3EmbeddingClient();
        SearchV3AtomicChildDenseSelector.ModelIdentity modelIdentity = guard.verifyModel(() -> {
            OllamaBgeM3EmbeddingClient.ModelMetadata metadata = model.inspectModel();
            assertThat(metadata.resolvedName()).isEqualTo(EXPECTED_MODEL);
            assertThat(metadata.digest())
                    .isEqualTo(SearchV3AtomicChildDenseSelector.EXPECTED_BGE_DIGEST);
            assertThat(metadata.dimensions()).isEqualTo(SearchV3AtomicChildDenseSelector.DIMENSIONS);
            assertThat(metadata.embeddingCapable()).isTrue();
            return new SearchV3AtomicChildDenseSelector.ModelIdentity(
                    metadata.resolvedName(), metadata.digest(), metadata.dimensions(), "COSINE");
        });

        MinimalV3ShadowAdapter v3 = new MinimalV3ShadowAdapter(model);
        MinimalV3ShadowAdapter.IndexedCorpus v3Corpus = v3.index(base.runtime().documents());
        assertThat(v3Corpus.passages()).hasSize(160);
        assertThat(v3IndexUnits(v3Corpus)).isEqualTo(base.artifact().output().v3IndexUnits());

        Map<String, SearchV3MinimalShadowFreeze.QueryOutput> frozenQueries = new LinkedHashMap<>();
        for (SearchV3MinimalShadowFreeze.QueryOutput query : base.artifact().output().queries()) {
            assertThat(frozenQueries.put(query.queryId(), query)).isNull();
        }
        Map<String, SearchV3AtomicChildDenseSelector.QueryVector> queryVectors =
                new LinkedHashMap<>();
        long queryEmbeddingNanos = 0L;
        for (SearchV3MinimalShadowDataset.RuntimeQuery query : base.runtime().queries()) {
            OllamaBgeM3EmbeddingClient.EmbeddingBatch embedded = model.embedOne(query.text());
            assertThat(embedded.embeddings()).hasSize(1);
            queryEmbeddingNanos += embedded.elapsedNanos();
            float[] sharedQueryVector = embedded.embeddings().get(0);
            MinimalV3ShadowAdapter.QueryRun fresh = v3.query(
                    v3Corpus, query, sharedQueryVector, millis(embedded.elapsedNanos()));
            SearchV3AtomicChildDenseSelector.QueryVector token =
                    selector.verifiedB3QueryVector(
                            required(frozenQueries, query.queryId()),
                            fresh,
                            query.text(),
                            sharedQueryVector);
            assertThat(token.vector()).isSameAs(sharedQueryVector);
            assertThat(queryVectors.put(query.queryId(), token)).isNull();
        }
        assertThat(queryVectors).hasSize(117);

        List<SearchV3AtomicChildDenseSelector.UniqueChildInput> uniqueChildren =
                verifiedInput.frozen().input().uniqueChildren();
        OllamaBgeM3EmbeddingClient.EmbeddingBatch childEmbedding = model.embedAll(
                uniqueChildren.stream()
                        .map(SearchV3AtomicChildDenseSelector.UniqueChildInput::sourceText)
                        .toList());
        assertThat(childEmbedding.embeddings()).hasSize(uniqueChildren.size());
        Map<String, float[]> childVectors = new LinkedHashMap<>();
        for (int index = 0; index < uniqueChildren.size(); index++) {
            assertThat(childVectors.put(
                    uniqueChildren.get(index).evidenceChildId(),
                    childEmbedding.embeddings().get(index))).isNull();
        }
        assertThat(childVectors).hasSize(227);

        OllamaBgeM3EmbeddingClient.ModelMetadata metadataAfterEmbeddings = model.inspectModel();
        assertThat(metadataAfterEmbeddings.resolvedName()).isEqualTo(modelIdentity.name());
        assertThat(metadataAfterEmbeddings.digest()).isEqualTo(modelIdentity.digest());
        assertThat(metadataAfterEmbeddings.dimensions()).isEqualTo(modelIdentity.dimensions());

        int childBatchCount = divideRoundingUp(
                uniqueChildren.size(), SearchV3AtomicChildDenseSelector.EMBEDDING_BATCH_SIZE);
        SearchV3AtomicChildDenseSelector.EmbeddingCostObservation embeddingCost =
                new SearchV3AtomicChildDenseSelector.EmbeddingCostObservation(
                        v3Corpus.passages().size(),
                        uniqueChildren.size(),
                        childBatchCount,
                        v3Corpus.embeddingMs(),
                        millis(queryEmbeddingNanos),
                        millis(childEmbedding.elapsedNanos()),
                        (long) uniqueChildren.size()
                                * SearchV3AtomicChildDenseSelector.DIMENSIONS
                                * Float.BYTES);
        SearchV3AtomicChildDenseSelector.FrozenPrediction frozenPrediction = guard.freezePrediction(
                () -> selector.predict(
                        verifiedInput, modelIdentity, queryVectors, childVectors, embeddingCost));
        selector.writePredictionCreateNew(PREDICTION, frozenPrediction);
        SearchV3AtomicChildDenseSelector.VerifiedPrediction verifiedPrediction = guard.verifyOutput(
                () -> selector.verifyPrediction(PREDICTION, frozenPrediction));

        SearchV3MinimalShadowFreeze.OutputArtifact s1Output = selector.buildS1Output(
                verifiedInput, verifiedPrediction, base.artifact().output(), base.runtime());
        SearchV3MinimalShadowGold.GoldSnapshot gold = guard.joinGold(
                base,
                verifiedPrediction,
                (verifiedBase, ignoredPrediction) ->
                        loadGoldAfterPrediction(verifiedBase));
        SearchV3AtomicChildDenseSelector.ComparisonEvaluation comparison =
                guard.evaluateComparison(() -> selector.evaluateComparison(
                        base.artifact().output(), s1Output, gold,
                        verifiedInput, verifiedPrediction));
        SearchV3AtomicChildSelectionCeiling.CeilingEvaluation oracle = guard.joinOracle(
                () -> loadOracleAfterComparison(base, gold));
        SearchV3AtomicChildDenseSelector.Evaluation evaluation = guard.finalizeEvaluation(
                () -> selector.finalizeWithOracle(
                        comparison, oracle, verifiedPrediction));
        assertThat(guard.phase()).isEqualTo(SearchV3AtomicChildDenseSelector.Phase.EVALUATED);

        SearchV3AtomicChildDenseSelector.CostObservation observedCost =
                verifiedPrediction.frozen().prediction().cost();
        assertThat(observedCost.b3PassageEmbeddingCount()).isEqualTo(160);
        assertThat(observedCost.uniqueChildEmbeddingCount()).isEqualTo(227);
        assertThat(observedCost.childEmbeddingBatchCount()).isEqualTo(8);
        assertThat(observedCost.additionalVectorStorageBytes()).isEqualTo(929_792L);

        OfficialReport report = new OfficialReport(
                1,
                REPORT_ARTIFACT,
                codeFreeze,
                fileSha256(CONTRACT),
                contract.sourceSha256(),
                contract.comparisonPolicySha256(),
                contract.selectorPolicySha256(),
                base.artifact().verifiedOutput().fileSha256(),
                base.candidate().fileSha256(),
                SearchV3AtomicChildDenseSelector.EXPECTED_PRZ033_REPORT_SHA256,
                verifiedInput.frozen().canonicalSha256(),
                verifiedInput.fileSha256(),
                verifiedPrediction.frozen().canonicalSha256(),
                verifiedPrediction.fileSha256(),
                modelIdentity,
                base.runtime().queries().size(),
                observedCost,
                evaluation,
                sealedBefore);
        writeCreateNew(REPORT, report);

        assertThat(sourceHash(SOURCE_FILES)).isEqualTo(contract.sourceSha256());
        assertThat(sealedSnapshot()).isEqualTo(sealedBefore);
        assertThat(git("rev-parse", "HEAD")).isEqualTo(runHead);
        assertThat(git("status", "--porcelain")).isBlank();

        System.out.println("PRZ034_INPUT_CANONICAL_SHA256=" + frozenInput.canonicalSha256());
        System.out.println("PRZ034_INPUT_FILE_SHA256=" + verifiedInput.fileSha256());
        System.out.println("PRZ034_PREDICTION_CANONICAL_SHA256="
                + frozenPrediction.canonicalSha256());
        System.out.println("PRZ034_PREDICTION_FILE_SHA256=" + verifiedPrediction.fileSha256());
        System.out.println("PRZ034_REPORT_SHA256=" + fileSha256(REPORT));
        System.out.println("PRZ034_DECISION=" + evaluation.decision());
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

    private SearchV3MinimalShadowGold.GoldSnapshot loadGoldAfterPrediction(
            BaseArtifacts base) {
        return base.ceiling().loadGoldAfterCandidateVerified(
                base.artifact(), base.candidate(), base.runtime());
    }

    private SearchV3AtomicChildSelectionCeiling.CeilingEvaluation loadOracleAfterComparison(
            BaseArtifacts base,
            SearchV3MinimalShadowGold.GoldSnapshot gold) {
        try {
            assertThat(fileSha256(PRZ033_REPORT))
                    .isEqualTo(SearchV3AtomicChildDenseSelector.EXPECTED_PRZ033_REPORT_SHA256);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot verify PRZ-033 Oracle report", exception);
        }
        return base.ceiling().evaluate(
                base.candidate(), base.artifact().output(), gold);
    }

    private List<SearchV3MinimalShadowFreeze.IndexUnit> v3IndexUnits(
            MinimalV3ShadowAdapter.IndexedCorpus corpus) {
        return corpus.passages().stream().map(value -> new SearchV3MinimalShadowFreeze.IndexUnit(
                value.passage().passageId(),
                value.passage().parentAnnotationCandidateId(),
                value.passage().evidenceChildren().stream().map(child -> {
                    SourceProvenance source = child.provenance();
                    return new ProductionV2ShadowAdapter.SourceSpan(
                            value.userBundleId(),
                            source.documentId(),
                            source.versionId(),
                            source.sourcePath(),
                            source.page(),
                            source.codePointStart(),
                            source.codePointEnd(),
                            child.sourceText(),
                            source.exactTextSha256());
                }).toList())).toList();
    }

    private Contract readContract() throws IOException {
        JsonNode root = mapper.readTree(Files.readString(CONTRACT, StandardCharsets.UTF_8));
        assertThat(root.path("artifactType").asText()).isEqualTo(CONTRACT_ARTIFACT);
        assertThat(root.path("schemaVersion").asInt(-1)).isEqualTo(1);
        return new Contract(
                root.path("status").asText(),
                root.path("codeFreezeCommit").asText(),
                root.path("selectorInputCanonicalSha256").asText(),
                root.path("selectorPolicyVersion").asText(),
                root.path("selectorPolicySha256").asText(),
                root.path("sourceSha256").asText(),
                root.path("modelDigest").asText(),
                root.path("topPassageK").asInt(-1),
                root.path("comparisonPolicySha256").asText());
    }

    private SealedSnapshot sealedSnapshot() throws Exception {
        byte[] manifestBytes = Files.readAllBytes(SEALED_MANIFEST);
        JsonNode manifest = mapper.readTree(manifestBytes);
        assertThat(SearchV3AtomicChildDenseSelector.sha256(manifestBytes))
                .isEqualTo(SEALED_MANIFEST_SHA);
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
            canonical.append(path.replace('\\', '/'))
                    .append('|').append(bytes.length)
                    .append('|').append(SearchV3AtomicChildDenseSelector.sha256(bytes))
                    .append('\n');
        }
        return SearchV3AtomicChildDenseSelector.sha256(canonical.toString());
    }

    private String comparisonPolicySha256() {
        return SearchV3AtomicChildDenseSelector.sha256(COMPARISON_POLICY);
    }

    private String fileSha256(Path path) throws IOException {
        return SearchV3AtomicChildDenseSelector.sha256(Files.readAllBytes(path));
    }

    private void writeCreateNew(Path path, Object value) throws IOException {
        Path normalized = path.toAbsolutePath().normalize();
        String portable = normalized.toString().replace('\\', '/').toLowerCase();
        if (!portable.contains("/local/search-v3-evaluation/prz034/")
                || portable.contains("sealed")) {
            throw new IllegalArgumentException("invalid PRZ-034 local report path");
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
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git failed: " + output);
        }
        return output;
    }

    private static int divideRoundingUp(int numerator, int denominator) {
        return (numerator + denominator - 1) / denominator;
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    private boolean prefreezeEnabled() {
        return Boolean.getBoolean(PREFREEZE_PROPERTY)
                || Boolean.parseBoolean(System.getenv().getOrDefault(PREFREEZE_ENV, "false"));
    }

    private float[] axisVector() {
        float[] vector = new float[SearchV3AtomicChildDenseSelector.DIMENSIONS];
        vector[0] = 1.0f;
        return vector;
    }

    private static <K, V> V required(Map<K, V> values, K key) {
        V value = values.get(key);
        if (value == null) {
            throw new IllegalStateException("missing frozen query: " + key);
        }
        return value;
    }

    record Contract(
            String status,
            String codeFreezeCommit,
            String selectorInputCanonicalSha256,
            String selectorPolicyVersion,
            String selectorPolicySha256,
            String sourceSha256,
            String modelDigest,
            int topPassageK,
            String comparisonPolicySha256) {
    }

    record BaseArtifacts(
            SearchV3AtomicChildSelectionCeiling ceiling,
            SearchV3AtomicChildSelectionCeiling.VerifiedPrz032 artifact,
            SearchV3AtomicChildSelectionCeiling.VerifiedCandidateInput candidate,
            SearchV3MinimalShadowDataset.RuntimeInput runtime) {
    }

    record OfficialReport(
            int schemaVersion,
            String artifactType,
            String codeFreezeCommit,
            String contractFileSha256,
            String sourceSha256,
            String comparisonPolicySha256,
            String selectorPolicySha256,
            String prz032OutputFileSha256,
            String prz033CandidateFileSha256,
            String prz033OracleReportFileSha256,
            String selectorInputCanonicalSha256,
            String selectorInputFileSha256,
            String predictionCanonicalSha256,
            String predictionFileSha256,
            SearchV3AtomicChildDenseSelector.ModelIdentity model,
            int queryCount,
            SearchV3AtomicChildDenseSelector.CostObservation cost,
            SearchV3AtomicChildDenseSelector.Evaluation evaluation,
            SealedSnapshot sealedFinal) {
    }

    record SealedSnapshot(
            String combinedSha256,
            String manifestSha256,
            String gitTree,
            boolean opened,
            boolean searchExecuted,
            String currentFreshBaseline) {
    }
}
