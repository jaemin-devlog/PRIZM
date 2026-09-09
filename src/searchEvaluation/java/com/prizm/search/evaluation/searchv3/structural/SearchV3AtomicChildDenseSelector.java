package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.AtomicEvidence;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.ConstraintValidation;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.DenseCandidate;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.PreparedCandidate;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.PreparedChild;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.PreparedCorpus;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.SelectedEvidence;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.SelectionResult;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.SourceCandidate;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.EvaluationResult;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Evaluation-only sourceText dense ordering inside a frozen B3 RetrievalPassage. */
final class SearchV3AtomicChildDenseSelector {

    static final int SCHEMA_VERSION = 1;
    static final String POLICY_VERSION = "CHILD_DENSE_V1";
    static final String INPUT_ARTIFACT = "PRZ034_CHILD_DENSE_V1_INPUT";
    static final String PREDICTION_ARTIFACT = "PRZ034_CHILD_DENSE_V1_PREDICTION";
    static final int TOP_PASSAGE_K = 5;
    static final int RESULT_LIMIT = 5;
    static final int DIMENSIONS = 1024;
    static final int EMBEDDING_BATCH_SIZE = 32;
    static final double SEVERE_SLICE_TOP1_REGRESSION = 0.10d;
    static final String EXPECTED_BGE_DIGEST =
            SearchV3AtomicChildSelectionCeiling.EXPECTED_BGE_DIGEST;
    static final String EXPECTED_CANDIDATE_FILE_SHA256 =
            "b6d70c26164aa5234ad5f49148e490ca8b25571ef040113a7149cec5b4c526da";
    static final String EXPECTED_CANDIDATE_CANONICAL_SHA256 =
            "9d056dffc19a3e919b0da5bd6fd1ce0b2f3d2b7bb9d0dab892b95de1e8fd3c9b";
    static final String EXPECTED_PRZ033_REPORT_SHA256 =
            "700a39a80865af0c83c806e7f284f820448c43f902a4dc66230a38ecbe35f7d8";
    static final String EXPECTED_PRZ033_CANDIDATE_IDENTITY_SHA256 =
            "6ab67cf3277c97b628a8e5e6ec1e14aabf6fa121da4e2cc0c28d8a0378ec3e17";
    static final String EXPECTED_INPUT_CANONICAL_SHA256 =
            "778b79117d47344433bed8d01f0f18a39ab4ae20f8f0ff444b2d8d5bd41c43ca";
    static final String POLICY_CANONICAL = String.join("\n",
            "policy=CHILD_DENSE_V1",
            "passageK=5",
            "childInput=EvidenceChild.sourceText",
            "similarity=COSINE",
            "outerPassageOrder=FROZEN",
            "samePassageOnly=true",
            "tieBreak=SOURCE_ORDER",
            "semanticEligibility=ALL_TOP5_CHILDREN",
            "typedEligibility=PRZ029_PREPARED_CORPUS_OVERLAY",
            "typedState=FROZEN",
            "resultLimit=5",
            "goldAvailable=false") + "\n";
    static final String POLICY_SHA256 = sha256(POLICY_CANONICAL);

    private final ObjectMapper mapper = new ObjectMapper();
    private final StructuralBlockParser parser = new StructuralBlockParser();
    private final StructuralEvidenceChildBuilder childBuilder = new StructuralEvidenceChildBuilder();
    private final StructuralRetrievalPassageBuilder passageBuilder =
            new StructuralRetrievalPassageBuilder();
    private final EvidenceValidationSelector evidenceSelector = new EvidenceValidationSelector();

    FrozenSelectorInput deriveInput(
            SearchV3AtomicChildSelectionCeiling.VerifiedCandidateInput candidate,
            SearchV3MinimalShadowDataset.RuntimeInput runtime) {
        Objects.requireNonNull(candidate, "verified PRZ-033 candidate");
        Objects.requireNonNull(runtime, "runtime input");
        if (!EXPECTED_CANDIDATE_FILE_SHA256.equals(candidate.fileSha256())
                || !EXPECTED_CANDIDATE_CANONICAL_SHA256.equals(
                        candidate.frozen().canonicalSha256())
                || !SearchV3AtomicChildSelectionCeiling.EXPECTED_INPUT_SHA256.equals(
                        runtime.canonicalSha256())) {
            throw blocked("candidate/runtime identity changed");
        }

        Map<String, SearchV3MinimalShadowDataset.RuntimeQuery> runtimeQueries = runtime.queries().stream()
                .collect(Collectors.toMap(
                        SearchV3MinimalShadowDataset.RuntimeQuery::queryId,
                        Function.identity(),
                        (left, right) -> { throw blocked("duplicate runtime query"); },
                        LinkedHashMap::new));
        LinkedHashMap<String, UniqueChildInput> uniqueChildren = new LinkedHashMap<>();
        List<SelectorQueryInput> queries = new ArrayList<>();
        int passageOccurrences = 0;
        int childOccurrences = 0;
        for (SearchV3AtomicChildSelectionCeiling.QueryCandidateInput query
                : candidate.frozen().input().queries()) {
            SearchV3MinimalShadowDataset.RuntimeQuery runtimeQuery = runtimeQueries.get(query.queryId());
            if (runtimeQuery == null
                    || !query.userBundleId().equals(runtimeQuery.userBundleId())) {
                throw blocked("runtime query identity changed: " + query.queryId());
            }
            String queryHash = sha256(runtimeQuery.text());
            List<SelectorPassageInput> passages = new ArrayList<>();
            for (SearchV3AtomicChildSelectionCeiling.PassageCandidateInput passage
                    : query.passages().stream().limit(TOP_PASSAGE_K).toList()) {
                if (passage.rank() != passages.size() + 1) {
                    throw blocked("Top5 Passage rank is not contiguous: " + query.queryId());
                }
                List<SelectorChildInput> children = new ArrayList<>();
                int ordinal = 0;
                for (SearchV3AtomicChildSelectionCeiling.ChildInput child : passage.children()) {
                    ProductionV2ShadowAdapter.SourceSpan span = child.span();
                    String sourceHash = sha256(span.sourceText());
                    if (!query.userBundleId().equals(span.userBundleId())
                            || !passage.parentId().equals(child.parentId())
                            || !sourceHash.equals(span.sourceTextSha256())) {
                        throw blocked("Child source/provenance changed: " + child.evidenceChildId());
                    }
                    SelectorChildInput value = new SelectorChildInput(
                            child.evidenceChildId(), child.parentId(), ordinal++,
                            span.sourceText(), sourceHash, span);
                    UniqueChildInput unique = new UniqueChildInput(
                            value.evidenceChildId(), value.sourceText(), value.sourceTextSha256());
                    UniqueChildInput previous = uniqueChildren.putIfAbsent(value.evidenceChildId(), unique);
                    if (previous != null && !previous.equals(unique)) {
                        throw blocked("Child ID/source changed across queries: " + value.evidenceChildId());
                    }
                    children.add(value);
                    childOccurrences++;
                }
                passages.add(new SelectorPassageInput(
                        passage.rank(), passage.passageId(), passage.cosineScore(),
                        passage.parentId(), List.copyOf(children)));
                passageOccurrences++;
            }
            if (passages.isEmpty()) {
                throw blocked("query has no Top5 Passage: " + query.queryId());
            }
            queries.add(new SelectorQueryInput(
                    query.queryId(), query.userBundleId(), runtimeQuery.text(), queryHash,
                    List.copyOf(passages)));
        }
        if (queries.size() != 117 || passageOccurrences != 507
                || childOccurrences != 804 || uniqueChildren.size() != 227) {
            throw blocked("selector input inventory changed");
        }
        SelectorInput input = new SelectorInput(
                SCHEMA_VERSION, INPUT_ARTIFACT, POLICY_VERSION, POLICY_SHA256,
                TOP_PASSAGE_K, RESULT_LIMIT,
                SearchV3AtomicChildSelectionCeiling.EXPECTED_OUTPUT_CANONICAL_SHA256,
                EXPECTED_CANDIDATE_CANONICAL_SHA256,
                SearchV3AtomicChildSelectionCeiling.EXPECTED_INPUT_SHA256,
                EXPECTED_BGE_DIGEST,
                List.copyOf(queries), List.copyOf(uniqueChildren.values()),
                passageOccurrences, childOccurrences);
        byte[] canonical = canonicalBytes(input);
        FrozenSelectorInput frozen = new FrozenSelectorInput(input, sha256(canonical), canonical.length);
        if (!EXPECTED_INPUT_CANONICAL_SHA256.equals(frozen.canonicalSha256())) {
            throw blocked("selector input canonical hash changed");
        }
        return frozen;
    }

    void writeInputCreateNew(Path path, FrozenSelectorInput frozen) {
        writeWrappedCreateNew(path, "selectorInput", frozen.canonicalSha256(),
                frozen.canonicalByteLength(), frozen.input());
    }

    VerifiedSelectorInput verifyInput(Path path, FrozenSelectorInput expected) {
        WrappedVerification verified = verifyWrapped(
                path, "selectorInput", expected.canonicalSha256(), expected.canonicalByteLength());
        SelectorInput read = mapper.treeToValue(verified.payload(), SelectorInput.class);
        if (!read.equals(expected.input())) {
            throw blocked("selector input payload changed");
        }
        return new VerifiedSelectorInput(expected, verified.fileSha256(), verified.fileBytes());
    }

    QueryVector verifiedB3QueryVector(
            SearchV3MinimalShadowFreeze.QueryOutput frozen,
            MinimalV3ShadowAdapter.QueryRun fresh,
            String queryText,
            float[] vector) {
        Objects.requireNonNull(frozen, "frozen query");
        Objects.requireNonNull(fresh, "fresh B3 query");
        Objects.requireNonNull(queryText, "queryText");
        assertFreshB3QueryParity(frozen, fresh);
        String queryTextSha = sha256(queryText);
        if (!frozen.queryTextSha256().equals(queryTextSha)) {
            throw new IllegalStateException("fresh B3 query text changed: " + frozen.queryId());
        }
        validateVector(vector);
        String candidateSha = sha256(canonicalBytes(fresh.candidates()));
        return new QueryVector(
                frozen.queryId(), queryTextSha, candidateSha, vector,
                vectorSha256(vector), true);
    }

    FrozenPrediction predict(
            VerifiedSelectorInput verified,
            ModelIdentity model,
            Map<String, QueryVector> queryVectors,
            Map<String, float[]> childVectors,
            EmbeddingCostObservation embeddingCost) {
        Objects.requireNonNull(verified, "verified selector input");
        Objects.requireNonNull(model, "model");
        Objects.requireNonNull(embeddingCost, "embedding cost");
        if (!"bge-m3:latest".equals(model.name())
                || !EXPECTED_BGE_DIGEST.equals(model.digest())
                || model.dimensions() != DIMENSIONS
                || !"COSINE".equals(model.similarity())) {
            throw new IllegalStateException("PRZ-034 model identity changed");
        }
        SelectorInput input = verified.frozen().input();
        int expectedBatchCount = (input.uniqueChildren().size() + EMBEDDING_BATCH_SIZE - 1)
                / EMBEDDING_BATCH_SIZE;
        long expectedStorage = (long) input.uniqueChildren().size() * DIMENSIONS * Float.BYTES;
        if (embeddingCost.b3PassageEmbeddingCount() != 160
                || embeddingCost.uniqueChildEmbeddingCount() != input.uniqueChildren().size()
                || embeddingCost.childEmbeddingBatchCount() != expectedBatchCount
                || embeddingCost.additionalVectorStorageBytes() != expectedStorage
                || !nonNegativeFinite(embeddingCost.b3PassageEmbeddingMs())
                || !nonNegativeFinite(embeddingCost.queryEmbeddingMs())
                || !nonNegativeFinite(embeddingCost.childEmbeddingMs())) {
            throw new IllegalStateException("PRZ-034 embedding cost/inventory changed");
        }
        if (!queryVectors.keySet().equals(input.queries().stream()
                        .map(SelectorQueryInput::queryId).collect(Collectors.toCollection(LinkedHashSet::new)))
                || !childVectors.keySet().equals(input.uniqueChildren().stream()
                        .map(UniqueChildInput::evidenceChildId)
                        .collect(Collectors.toCollection(LinkedHashSet::new)))) {
            throw new IllegalStateException("PRZ-034 embedding inventory changed");
        }
        List<QueryPrediction> queries = new ArrayList<>();
        for (SelectorQueryInput query : input.queries()) {
            QueryVector queryVector = queryVectors.get(query.queryId());
            if (!query.queryId().equals(queryVector.queryId())
                    || !query.queryTextSha256().equals(queryVector.queryTextSha256())
                    || queryVector.b3CandidateIdentitySha256().isBlank()
                    || !queryVector.b3ParityVerified()) {
                throw new IllegalStateException("query vector token mismatch");
            }
            if (!queryVector.sha256().equals(vectorSha256(queryVector.vector()))) {
                throw new IllegalStateException("shared query vector changed after B3 ranking");
            }
            long started = System.nanoTime();
            List<PassagePrediction> passages = query.passages().stream()
                    .map(passage -> scorePassage(passage, queryVector.vector(), childVectors))
                    .toList();
            double selectionMs = millis(System.nanoTime() - started);
            queries.add(new QueryPrediction(
                    query.queryId(), queryVector.sha256(), selectionMs, passages));
        }
        List<Double> selectorTimes = queries.stream().map(QueryPrediction::selectionMs).sorted().toList();
        CostObservation cost = new CostObservation(
                embeddingCost.b3PassageEmbeddingCount(),
                embeddingCost.uniqueChildEmbeddingCount(),
                embeddingCost.childEmbeddingBatchCount(),
                embeddingCost.b3PassageEmbeddingMs(),
                embeddingCost.queryEmbeddingMs(),
                embeddingCost.childEmbeddingMs(),
                percentile(selectorTimes, 0.50d),
                percentile(selectorTimes, 0.95d),
                embeddingCost.additionalVectorStorageBytes());
        boolean allQueryVectorsB3Verified = queryVectors.values().stream()
                .allMatch(QueryVector::b3ParityVerified);
        Prediction prediction = new Prediction(
                SCHEMA_VERSION, PREDICTION_ARTIFACT, POLICY_VERSION, POLICY_SHA256,
                verified.frozen().canonicalSha256(), model, allQueryVectorsB3Verified,
                "NOT_VERIFIABLE_HISTORICAL_VECTOR_NOT_STORED",
                input.queries().size(), input.uniqueChildren().size(),
                cost, List.copyOf(queries));
        byte[] canonical = canonicalBytes(prediction);
        return new FrozenPrediction(prediction, sha256(canonical), canonical.length);
    }

    void writePredictionCreateNew(Path path, FrozenPrediction frozen) {
        writeWrappedCreateNew(path, "prediction", frozen.canonicalSha256(),
                frozen.canonicalByteLength(), frozen.prediction());
    }

    VerifiedPrediction verifyPrediction(Path path, FrozenPrediction expected) {
        WrappedVerification verified = verifyWrapped(
                path, "prediction", expected.canonicalSha256(), expected.canonicalByteLength());
        Prediction read = mapper.treeToValue(verified.payload(), Prediction.class);
        if (!read.equals(expected.prediction())) {
            throw new IllegalStateException("PRZ-034 prediction payload changed");
        }
        return new VerifiedPrediction(expected, verified.fileSha256(), verified.fileBytes());
    }

    SearchV3MinimalShadowFreeze.OutputArtifact buildS1Output(
            VerifiedSelectorInput input,
            VerifiedPrediction prediction,
            SearchV3MinimalShadowFreeze.OutputArtifact s0,
            SearchV3MinimalShadowDataset.RuntimeInput runtime) {
        Objects.requireNonNull(input, "verified input");
        Objects.requireNonNull(prediction, "verified prediction");
        Objects.requireNonNull(s0, "S0");
        Objects.requireNonNull(runtime, "runtime");
        if (!input.frozen().canonicalSha256().equals(
                prediction.frozen().prediction().selectorInputCanonicalSha256())) {
            throw new IllegalStateException("prediction is not bound to selector input");
        }
        Map<String, SelectorQueryInput> inputs = input.frozen().input().queries().stream()
                .collect(Collectors.toMap(SelectorQueryInput::queryId, Function.identity()));
        Map<String, QueryPrediction> predictions = prediction.frozen().prediction().queries().stream()
                .collect(Collectors.toMap(QueryPrediction::queryId, Function.identity()));
        Map<String, SearchV3MinimalShadowDataset.RuntimeQuery> runtimeQueries = runtime.queries().stream()
                .collect(Collectors.toMap(
                        SearchV3MinimalShadowDataset.RuntimeQuery::queryId,
                        Function.identity(),
                        (left, right) -> { throw new IllegalStateException("duplicate runtime query"); },
                        LinkedHashMap::new));
        Map<String, SourceCandidate> sourceCandidates = sourceCandidatesByPassage(runtime);
        List<SearchV3MinimalShadowFreeze.QueryOutput> queries = new ArrayList<>();
        for (SearchV3MinimalShadowFreeze.QueryOutput query : s0.queries()) {
            SelectorQueryInput selectorInput = required(inputs, query.queryId(), "selector input");
            QueryPrediction selectorPrediction = required(predictions, query.queryId(), "prediction");
            SearchV3MinimalShadowDataset.RuntimeQuery runtimeQuery = required(
                    runtimeQueries, query.queryId(), "runtime query");
            if (!query.queryTextSha256().equals(sha256(runtimeQuery.text()))
                    || query.typedApplicabilityVerified()
                            != runtimeQuery.typedApplicabilityVerified()) {
                throw new IllegalStateException("runtime query contract changed: " + query.queryId());
            }
            MinimalV3ShadowAdapter.QueryRun s1 = reselectFinal(
                    query.v3(), runtimeQuery, selectorInput, selectorPrediction, sourceCandidates);
            queries.add(new SearchV3MinimalShadowFreeze.QueryOutput(
                    query.suite(), query.datasetVersion(), query.split(), query.queryId(),
                    query.userBundleId(), query.professionGroup(), query.language(),
                    query.queryTextSha256(), query.typedApplicabilityVerified(), query.v2(), s1));
        }
        return new SearchV3MinimalShadowFreeze.OutputArtifact(
                s0.schemaVersion(), s0.artifactType(), s0.codeFreezeCommit(), s0.sourceFreeze(),
                s0.model(), s0.v2Profile(), s0.v3Profile(), s0.jdbcExecutionBoundary(),
                s0.queryCount(), s0.userCount(), s0.documentVersionCount(),
                s0.v2Indexing(), s0.v3Indexing(), s0.v2IndexUnits(), s0.v3IndexUnits(),
                List.copyOf(queries), s0.sealedState());
    }

    ComparisonEvaluation evaluateComparison(
            SearchV3MinimalShadowFreeze.OutputArtifact s0Output,
            SearchV3MinimalShadowFreeze.OutputArtifact s1Output,
            SearchV3MinimalShadowGold.GoldSnapshot gold,
            VerifiedSelectorInput selectorInput,
            VerifiedPrediction prediction) {
        SearchV3MinimalShadowEvaluator evaluator = new SearchV3MinimalShadowEvaluator();
        SearchV3MinimalShadowEvaluator.EvaluationReport s0 = evaluator.evaluate(s0Output, gold);
        SearchV3MinimalShadowEvaluator.EvaluationReport s1 = evaluator.evaluate(s1Output, gold);
        assertS0Parity(s0);
        String frozenCandidateIdentity = candidateIdentity(s0Output);
        if (!frozenCandidateIdentity.equals(candidateIdentity(s1Output))
                || !EXPECTED_PRZ033_CANDIDATE_IDENTITY_SHA256.equals(frozenCandidateIdentity)) {
            throw new IllegalStateException("S0/S1 Passage candidate identity changed");
        }

        Map<String, SearchV3MinimalShadowEvaluator.QueryEvaluation> before = s0.queries().stream()
                .collect(Collectors.toMap(SearchV3MinimalShadowEvaluator.QueryEvaluation::queryId,
                        Function.identity()));
        Map<String, SearchV3MinimalShadowEvaluator.QueryEvaluation> after = s1.queries().stream()
                .collect(Collectors.toMap(SearchV3MinimalShadowEvaluator.QueryEvaluation::queryId,
                        Function.identity()));
        int wins = 0;
        int losses = 0;
        int ties = 0;
        long retained = 0;
        for (SearchV3MinimalShadowEvaluator.QueryEvaluation left : s0.queries()) {
            if (!left.directPositive()) continue;
            SearchV3MinimalShadowEvaluator.QueryEvaluation right = after.get(left.queryId());
            int leftRank = rank(left.v3().finalRanking().firstDirectRank());
            int rightRank = rank(right.v3().finalRanking().firstDirectRank());
            if (rightRank < leftRank) wins++;
            else if (rightRank > leftRank) losses++;
            else ties++;
            if (left.v3().finalRanking().top1() && right.v3().finalRanking().top1()) retained++;
        }
        if (wins + losses + ties != 85) {
            throw new IllegalStateException("win/loss/tie inventory changed");
        }
        long baselineTop1 = before.values().stream().filter(SearchV3MinimalShadowEvaluator.QueryEvaluation::directPositive)
                .filter(value -> value.v3().finalRanking().top1()).count();
        double retention = ratio(retained, baselineTop1);
        if (baselineTop1 != 46) {
            throw new IllegalStateException("S0 rank1 Direct inventory changed");
        }

        long typedSelectionLimitQueries = s1.queries().stream()
                .map(SearchV3MinimalShadowEvaluator.QueryEvaluation::typed)
                .filter(Objects::nonNull)
                .filter(SearchV3MinimalShadowEvaluator.TypedQueryDiagnostic::stateCorrect)
                .filter(value -> value.correctEvidenceCount() < value.assessedEvidenceCount())
                .count();
        MetricSnapshot s0Metrics = metrics(s0);
        MetricSnapshot s1Metrics = metrics(s1);
        Safety safety = safety(s0Output, s1Output, s0, s1, selectorInput, prediction);
        return new ComparisonEvaluation(
                s0Output, s1Output, s0, s1, s0Metrics, s1Metrics,
                wins, losses, ties, retention, typedSelectionLimitQueries, safety);
    }

    Evaluation finalizeWithOracle(
            ComparisonEvaluation comparison,
            SearchV3AtomicChildSelectionCeiling.CeilingEvaluation oracle,
            VerifiedPrediction prediction) {
        Objects.requireNonNull(comparison, "S0/S1 comparison");
        Objects.requireNonNull(oracle, "verified PRZ-033 Oracle");
        Objects.requireNonNull(prediction, "verified prediction");
        Map<SearchV3AtomicChildSelectionCeiling.FailureStage, Long> expectedOracleStages = Map.of(
                SearchV3AtomicChildSelectionCeiling.FailureStage.FINAL_ALREADY_CORRECT, 46L,
                SearchV3AtomicChildSelectionCeiling.FailureStage.TOP_PASSAGE_CHILD_RECOVERABLE, 32L,
                SearchV3AtomicChildSelectionCeiling.FailureStage.LOWER_PASSAGE_RECOVERABLE, 6L,
                SearchV3AtomicChildSelectionCeiling.FailureStage.DEEP_PASSAGE_RECOVERABLE, 0L,
                SearchV3AtomicChildSelectionCeiling.FailureStage.RETRIEVAL_MISS, 0L,
                SearchV3AtomicChildSelectionCeiling.FailureStage.MULTI_ASPECT_SELECTION_ERROR, 1L);
        String frozenCandidateIdentity = candidateIdentity(comparison.s0Output());
        if (!EXPECTED_PRZ033_CANDIDATE_IDENTITY_SHA256.equals(frozenCandidateIdentity)
                || !frozenCandidateIdentity.equals(oracle.candidateIdentitySha256())
                || !oracle.f0Output().equals(comparison.s0Output())
                || !oracle.failureStages().equals(expectedOracleStages)
                || oracle.queryTraces().size() != 85
                || !oracle.safety().valid()) {
            throw new IllegalStateException("PRZ-033 Oracle identity changed");
        }
        Map<String, SearchV3MinimalShadowEvaluator.QueryEvaluation> after =
                comparison.s1().queries().stream().collect(Collectors.toMap(
                        SearchV3MinimalShadowEvaluator.QueryEvaluation::queryId,
                        Function.identity()));

        Set<String> recoverable = oracle.queryTraces().stream()
                .filter(value -> value.failureStage()
                        == SearchV3AtomicChildSelectionCeiling.FailureStage.TOP_PASSAGE_CHILD_RECOVERABLE)
                .map(SearchV3AtomicChildSelectionCeiling.QueryTrace::queryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        long recovered = recoverable.stream().filter(queryId -> {
            SearchV3MinimalShadowEvaluator.QueryEvaluation value = after.get(queryId);
            if (value != null && value.v3().finalRanking().top1()) {
                return true;
            }
            return false;
        }).count();
        Set<String> recoverableUsers = oracle.queryTraces().stream()
                .filter(value -> value.failureStage()
                        == SearchV3AtomicChildSelectionCeiling.FailureStage.TOP_PASSAGE_CHILD_RECOVERABLE)
                .map(SearchV3AtomicChildSelectionCeiling.QueryTrace::userBundleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int improvedRecoverableUsers = (int) recoverableUsers.stream()
                .filter(user -> directTop1ForUser(comparison.s1(), user)
                        > directTop1ForUser(comparison.s0(), user))
                .count();
        if (recoverable.size() != 32 || recoverableUsers.size() != 9) {
            throw new IllegalStateException("PRZ-033 recoverable inventory changed");
        }

        Map<FailureType, Long> failures = new EnumMap<>(FailureType.class);
        for (FailureType type : FailureType.values()) failures.put(type, 0L);
        for (SearchV3AtomicChildSelectionCeiling.QueryTrace trace : oracle.queryTraces()) {
            SearchV3MinimalShadowEvaluator.QueryEvaluation right = after.get(trace.queryId());
            if (trace.failureStage()
                    == SearchV3AtomicChildSelectionCeiling.FailureStage.FINAL_ALREADY_CORRECT) {
                continue;
            }
            if (right.v3().finalRanking().top1()) {
                failures.merge(FailureType.CHILD_SELECTOR_FIXED, 1L, Long::sum);
            }
            else if (trace.failureStage()
                    == SearchV3AtomicChildSelectionCeiling.FailureStage.MULTI_ASPECT_SELECTION_ERROR) {
                failures.merge(FailureType.MULTI_ASPECT_LIMIT, 1L, Long::sum);
            }
            else if (trace.failureStage()
                    == SearchV3AtomicChildSelectionCeiling.FailureStage.TOP_PASSAGE_CHILD_RECOVERABLE) {
                failures.merge(FailureType.CHILD_SELECTOR_FAILED, 1L, Long::sum);
            }
            else if (trace.failureStage()
                    == SearchV3AtomicChildSelectionCeiling.FailureStage.LOWER_PASSAGE_RECOVERABLE
                    || trace.failureStage()
                    == SearchV3AtomicChildSelectionCeiling.FailureStage.DEEP_PASSAGE_RECOVERABLE
                    || trace.failureStage()
                    == SearchV3AtomicChildSelectionCeiling.FailureStage.RETRIEVAL_MISS) {
                failures.merge(FailureType.PASSAGE_RANKING_LIMIT, 1L, Long::sum);
            }
        }
        long classifiedHeadroom = failures.values().stream().mapToLong(Long::longValue).sum();
        if (classifiedHeadroom != 39) {
            throw new IllegalStateException("PRZ-034 failure-stage headroom inventory changed");
        }
        MetricSnapshot s0Metrics = comparison.s0Metrics();
        MetricSnapshot s1Metrics = comparison.s1Metrics();
        MetricSnapshot oracleMetrics = metrics(oracle.oracle());
        Capture capture = new Capture(
                capture(s1Metrics.queryTop1(), s0Metrics.queryTop1(), oracleMetrics.queryTop1()),
                capture(s1Metrics.userTop1(), s0Metrics.userTop1(), oracleMetrics.userTop1()),
                capture(s1Metrics.queryMrr(), s0Metrics.queryMrr(), oracleMetrics.queryMrr()));
        SliceAudit sliceAudit = sliceAudit(comparison.s0(), comparison.s1());
        Decision decision = decide(
                s0Metrics, s1Metrics, comparison.wins(), comparison.losses(),
                comparison.rankOneRetention(), recovered,
                improvedRecoverableUsers, capture, prediction.frozen().prediction().cost(),
                comparison.safety(), sliceAudit);
        return new Evaluation(
                comparison.s0(), comparison.s1(), s0Metrics, s1Metrics, oracleMetrics,
                comparison.wins(), comparison.losses(), comparison.ties(),
                comparison.rankOneRetention(), recovered, improvedRecoverableUsers,
                capture, Collections.unmodifiableMap(new EnumMap<>(failures)),
                comparison.typedSelectionLimitQueries(), sliceAudit, comparison.safety(), decision);
    }

    void assertFreshB3QueryParity(
            SearchV3MinimalShadowFreeze.QueryOutput frozen,
            MinimalV3ShadowAdapter.QueryRun fresh) {
        MinimalV3ShadowAdapter.QueryRun expected = frozen.v3();
        if (!expected.state().equals(fresh.state())
                || expected.typedApplicabilityVerified() != fresh.typedApplicabilityVerified()
                || expected.parsedConstraintCount() != fresh.parsedConstraintCount()
                || !expected.candidates().equals(fresh.candidates())
                || !expected.finalResults().equals(fresh.finalResults())
                || expected.ownerLeakage() != fresh.ownerLeakage()
                || expected.crossParentPassageViolations() != fresh.crossParentPassageViolations()) {
            throw new IllegalStateException("fresh B3/S0 parity failed: " + frozen.queryId());
        }
    }

    private PassagePrediction scorePassage(
            SelectorPassageInput passage,
            float[] queryVector,
            Map<String, float[]> childVectors) {
        List<ScoredChild> scored = passage.children().stream()
                .map(child -> new ScoredChild(
                        child.evidenceChildId(), child.originalOrdinal(),
                        cosine(queryVector, required(childVectors, child.evidenceChildId(), "Child vector"))))
                .sorted(Comparator.comparingDouble(ScoredChild::cosineScore).reversed()
                        .thenComparingInt(ScoredChild::originalOrdinal)
                        .thenComparing(ScoredChild::evidenceChildId))
                .toList();
        return new PassagePrediction(
                passage.rank(), passage.passageId(), passage.parentId(), passage.passageCosineScore(), scored);
    }

    private MinimalV3ShadowAdapter.QueryRun reselectFinal(
            MinimalV3ShadowAdapter.QueryRun original,
            SearchV3MinimalShadowDataset.RuntimeQuery query,
            SelectorQueryInput input,
            QueryPrediction prediction,
            Map<String, SourceCandidate> sourceCandidates) {
        if (input.passages().size() != prediction.passages().size()) {
            throw new IllegalStateException("selector prediction Passage count changed");
        }
        List<SourceCandidate> rankedSources = new ArrayList<>();
        List<DenseCandidate> dense = new ArrayList<>();
        for (MinimalV3ShadowAdapter.CandidateResult candidate : original.candidates()) {
            SourceCandidate source = required(sourceCandidates, candidate.candidateId(), "source candidate");
            assertSourceCandidateParity(query.userBundleId(), candidate, source);
            rankedSources.add(source);
            dense.add(new DenseCandidate(
                    candidate.rank(), candidate.candidateId(), candidate.cosineScore()));
        }
        PreparedCorpus prepared = evidenceSelector.prepare(rankedSources);
        EvidenceValidationSelector.ParsedQuery parsed =
                evidenceSelector.parse(query.queryId(), query.text());
        SelectionResult sourceOrderReplay = evidenceSelector.select(
                prepared,
                parsed,
                query.userBundleId(),
                query.typedApplicabilityVerified(),
                dense);
        List<MinimalV3ShadowAdapter.FinalResult> replayFinals = sourceOrderReplay.selectedEvidence().stream()
                .map(selected -> finalResult(query.userBundleId(), selected))
                .toList();
        if (!original.finalResults().equals(replayFinals)
                || !original.state().equals(sourceOrderReplay.state().name())
                || original.typedApplicabilityVerified()
                        != sourceOrderReplay.typedApplicabilityVerified()
                || original.parsedConstraintCount() != sourceOrderReplay.parsedConstraintCount()) {
            throw new IllegalStateException("source-order PRZ-029 replay changed S0: " + query.queryId());
        }
        PreparedCorpus overlay = denseOverlay(prepared, input, prediction);
        SelectionResult selection = evidenceSelector.select(
                overlay,
                parsed,
                query.userBundleId(),
                query.typedApplicabilityVerified(),
                dense);
        assertValidationParity(sourceOrderReplay, selection, query.queryId());
        if (!original.state().equals(selection.state().name())
                || original.typedApplicabilityVerified()
                        != selection.typedApplicabilityVerified()
                || original.parsedConstraintCount() != selection.parsedConstraintCount()
                || !selection.originalCandidateIds().equals(
                        original.candidates().stream()
                                .map(MinimalV3ShadowAdapter.CandidateResult::candidateId).toList())) {
            throw new IllegalStateException("CHILD_DENSE_V1 changed PRZ-029 state contract: "
                    + query.queryId());
        }
        List<MinimalV3ShadowAdapter.FinalResult> finals = selection.selectedEvidence().stream()
                .map(selected -> finalResult(query.userBundleId(), selected))
                .toList();
        double selectionMs = prediction.selectionMs();
        return new MinimalV3ShadowAdapter.QueryRun(
                original.state(), original.typedApplicabilityVerified(), original.parsedConstraintCount(),
                original.queryEmbeddingMs(), original.rankingMs(), original.preparationMs(),
                original.selectionMs() + selectionMs, original.totalMs() + selectionMs,
                original.candidates(), List.copyOf(finals), original.ownerLeakage(),
                original.crossParentPassageViolations());
    }

    private void assertValidationParity(
            SelectionResult sourceOrder,
            SelectionResult overlay,
            String queryId) {
        if (sourceOrder.state() != overlay.state()
                || sourceOrder.typedApplicabilityVerified()
                        != overlay.typedApplicabilityVerified()
                || sourceOrder.parsedConstraintCount() != overlay.parsedConstraintCount()
                || !sourceOrder.originalCandidateIds().equals(overlay.originalCandidateIds())
                || sourceOrder.validationTrace().size() != overlay.validationTrace().size()) {
            throw new IllegalStateException("Typed validation contract changed: " + queryId);
        }
        Map<String, EvidenceValidationSelector.CandidateValidation> right =
                overlay.validationTrace().stream().collect(Collectors.toMap(
                        value -> value.dense().candidateId(),
                        Function.identity(),
                        (left, duplicate) -> { throw new IllegalStateException(
                                "duplicate overlay validation"); },
                        LinkedHashMap::new));
        for (EvidenceValidationSelector.CandidateValidation left : sourceOrder.validationTrace()) {
            EvidenceValidationSelector.CandidateValidation candidate = required(
                    right, left.dense().candidateId(), "overlay validation");
            if (!left.dense().equals(candidate.dense())
                    || !sameEvaluationResult(left.result(), candidate.result())
                    || !sameConstraintValidations(left.constraints(), candidate.constraints())) {
                throw new IllegalStateException("Passage validation changed: " + queryId);
            }
            Map<String, EvidenceValidationSelector.ChildValidation> children =
                    candidate.children().stream().collect(Collectors.toMap(
                            value -> value.source().childId(),
                            Function.identity(),
                            (first, duplicate) -> { throw new IllegalStateException(
                                    "duplicate overlay Child validation"); },
                            LinkedHashMap::new));
            for (EvidenceValidationSelector.ChildValidation child : left.children()) {
                EvidenceValidationSelector.ChildValidation actual = required(
                        children, child.source().childId(), "overlay Child validation");
                if (!child.source().equals(actual.source())
                        || !sameEvaluationResult(child.result(), actual.result())
                        || !sameConstraintValidations(child.constraints(), actual.constraints())) {
                    throw new IllegalStateException("Child validation changed: " + queryId);
                }
            }
        }
    }

    private PreparedCorpus denseOverlay(
            PreparedCorpus prepared,
            SelectorQueryInput input,
            QueryPrediction prediction) {
        Map<String, SelectorPassageInput> passages = input.passages().stream()
                .collect(Collectors.toMap(SelectorPassageInput::passageId, Function.identity()));
        Map<String, PreparedCandidate> overlaid = new LinkedHashMap<>(prepared.candidatesById());
        for (PassagePrediction ranked : prediction.passages()) {
            SelectorPassageInput frozen = required(passages, ranked.passageId(), "Passage");
            if (frozen.rank() != ranked.rank()
                    || !frozen.parentId().equals(ranked.parentId())
                    || Double.compare(frozen.passageCosineScore(), ranked.passageCosineScore()) != 0) {
                throw new IllegalStateException("selector changed outer Passage identity/order/score");
            }
            PreparedCandidate original = required(
                    prepared.candidatesById(), ranked.passageId(), "prepared Passage");
            if (!original.source().parentId().equals(ranked.parentId())) {
                throw new IllegalStateException("selector crossed Parent: " + ranked.passageId());
            }
            Map<String, PreparedChild> children = original.children().stream()
                    .collect(Collectors.toMap(
                            value -> value.source().childId(),
                            Function.identity(),
                            (left, right) -> { throw new IllegalStateException("duplicate prepared Child"); },
                            LinkedHashMap::new));
            List<PreparedChild> ordered = ranked.children().stream()
                    .map(value -> required(children, value.evidenceChildId(), "prepared Child"))
                    .toList();
            Set<String> expected = children.keySet();
            Set<String> actual = ordered.stream().map(value -> value.source().childId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!actual.equals(expected)) {
                throw new IllegalStateException("selector changed Child membership: " + ranked.passageId());
            }
            overlaid.put(ranked.passageId(), new PreparedCandidate(original.source(), ordered));
        }
        return new PreparedCorpus(
                overlaid,
                prepared.observationExtractionLatencyMs(),
                prepared.observationCount(),
                prepared.sourcePayloadUtf8Bytes());
    }

    private Map<String, SourceCandidate> sourceCandidatesByPassage(
            SearchV3MinimalShadowDataset.RuntimeInput runtime) {
        Map<String, SourceCandidate> candidates = new LinkedHashMap<>();
        for (SearchV3MinimalShadowDataset.RuntimeDocument document : runtime.activeDocuments().stream()
                .sorted(Comparator.comparing(SearchV3MinimalShadowDataset.RuntimeDocument::versionId))
                .toList()) {
            StructuralDocument structural = new StructuralDocument(
                    document.userBundleId(), document.documentId(), document.versionId(),
                    document.sourcePath(), null, document.sourceText(), document.contentSha256());
            List<EvidenceChild> children = childBuilder.build(parser.parse(structural));
            for (RetrievalPassage passage : passageBuilder.build(children)) {
                SourceCandidate candidate = new SourceCandidate(
                        document.userBundleId(),
                        passage.passageId(),
                        passage.documentId(),
                        passage.versionId(),
                        passage.parentAnnotationCandidateId(),
                        passage.evidenceChildren().stream()
                                .map(child -> new AtomicEvidence(
                                        child.childId(), child.sourceText(), child.provenance()))
                                .toList());
                if (candidates.put(candidate.candidateId(), candidate) != null) {
                    throw new IllegalStateException("duplicate reconstructed Passage: "
                            + candidate.candidateId());
                }
            }
        }
        return Map.copyOf(candidates);
    }

    private void assertSourceCandidateParity(
            String owner,
            MinimalV3ShadowAdapter.CandidateResult frozen,
            SourceCandidate source) {
        if (!owner.equals(source.userBundleId())
                || !frozen.parentId().equals(source.parentId())) {
            throw new IllegalStateException("source candidate scope changed: " + frozen.candidateId());
        }
        List<ProductionV2ShadowAdapter.SourceSpan> actual = source.children().stream()
                .map(child -> span(owner, child.sourceText(), child.provenance()))
                .toList();
        if (!frozen.spans().equals(actual)) {
            throw new IllegalStateException("source candidate provenance changed: "
                    + frozen.candidateId());
        }
    }

    private MinimalV3ShadowAdapter.FinalResult finalResult(
            String owner,
            SelectedEvidence selected) {
        return new MinimalV3ShadowAdapter.FinalResult(
                selected.selectedRank(),
                selected.candidateId(),
                selected.denseRank(),
                selected.cosineScore(),
                selected.evidenceChildId(),
                span(owner, selected.sourceText(), selected.provenance()),
                selected.matchState() == null ? null : selected.matchState().name());
    }

    private ProductionV2ShadowAdapter.SourceSpan span(
            String owner,
            String sourceText,
            SourceProvenance source) {
        return new ProductionV2ShadowAdapter.SourceSpan(
                owner,
                source.documentId(),
                source.versionId(),
                source.sourcePath(),
                source.page(),
                source.codePointStart(),
                source.codePointEnd(),
                sourceText,
                source.exactTextSha256());
    }

    private Safety safety(
            SearchV3MinimalShadowFreeze.OutputArtifact s0Output,
            SearchV3MinimalShadowFreeze.OutputArtifact s1Output,
            SearchV3MinimalShadowEvaluator.EvaluationReport s0,
            SearchV3MinimalShadowEvaluator.EvaluationReport s1,
            VerifiedSelectorInput selectorInput,
            VerifiedPrediction prediction) {
        boolean typedStateContractExact = true;
        boolean provenanceExact = true;
        Map<String, SearchV3MinimalShadowFreeze.QueryOutput> before = s0Output.queries().stream()
                .collect(Collectors.toMap(SearchV3MinimalShadowFreeze.QueryOutput::queryId, Function.identity()));
        Map<String, QueryPrediction> predictions = prediction.frozen().prediction().queries().stream()
                .collect(Collectors.toMap(QueryPrediction::queryId, Function.identity()));
        Map<String, SelectorQueryInput> selectorQueries = selectorInput.frozen().input().queries().stream()
                .collect(Collectors.toMap(SelectorQueryInput::queryId, Function.identity()));
        for (SearchV3MinimalShadowFreeze.QueryOutput query : s1Output.queries()) {
            SearchV3MinimalShadowFreeze.QueryOutput original = before.get(query.queryId());
            SelectorQueryInput frozenSelectorQuery = selectorQueries.get(query.queryId());
            if (!original.v3().candidates().equals(query.v3().candidates())) {
                provenanceExact = false;
            }
            if (query.typedApplicabilityVerified()) {
                if (!original.v3().state().equals(query.v3().state())
                        || original.v3().typedApplicabilityVerified()
                                != query.v3().typedApplicabilityVerified()
                        || original.v3().parsedConstraintCount()
                                != query.v3().parsedConstraintCount()) {
                    typedStateContractExact = false;
                }
            }
            Set<String> selectedChildren = new LinkedHashSet<>();
            int expectedSelectedRank = 1;
            for (MinimalV3ShadowAdapter.FinalResult result : query.v3().finalResults()) {
                MinimalV3ShadowAdapter.CandidateResult candidate = query.v3().candidates().stream()
                        .filter(value -> value.candidateId().equals(result.candidateId()))
                        .findFirst().orElse(null);
                SelectorPassageInput frozenPassage = frozenSelectorQuery == null ? null
                        : frozenSelectorQuery.passages().stream()
                                .filter(value -> value.passageId().equals(result.candidateId()))
                                .findFirst().orElse(null);
                SelectorChildInput frozenChild = frozenPassage == null ? null
                        : frozenPassage.children().stream()
                                .filter(value -> value.evidenceChildId().equals(
                                        result.evidenceChildId()))
                                .findFirst().orElse(null);
                String expectedMatchState = switch (query.v3().state()) {
                    case "FOUND" -> "SATISFIED";
                    case "PARTIAL" -> "UNKNOWN";
                    case "NONE" -> "CONTRADICTED";
                    case "UNASSESSED" -> null;
                    default -> throw new IllegalStateException(
                            "unknown Evidence state: " + query.v3().state());
                };
                boolean typedMatchState = Objects.equals(expectedMatchState, result.matchState());
                if (candidate == null
                        || frozenPassage == null
                        || frozenChild == null
                        || result.rank() != expectedSelectedRank++
                        || !selectedChildren.add(result.evidenceChildId())
                        || candidate.rank() != result.denseRank()
                        || Double.compare(candidate.cosineScore(), result.cosineScore()) != 0
                        || !frozenChild.span().equals(result.span())
                        || !frozenPassage.parentId().equals(frozenChild.parentId())
                        || !typedMatchState) {
                    provenanceExact = false;
                }
            }
            if (query.v3().finalResults().size() > RESULT_LIMIT
                    || !predictions.containsKey(query.queryId())) provenanceExact = false;
        }
        SearchV3MinimalShadowEvaluator.StructureAggregate left = s0.queryMicro().v3().finalStructure();
        SearchV3MinimalShadowEvaluator.StructureAggregate right = s1.queryMicro().v3().finalStructure();
        boolean valid = candidateIdentity(s0Output).equals(candidateIdentity(s1Output))
                && prediction.frozen().prediction().queryVectorSharedWithB3()
                && typedStateContractExact && provenanceExact
                && right.crossParentContaminationRate() == 0.0d
                && right.fragmentationRate() <= left.fragmentationRate()
                && right.duplicateRate() <= left.duplicateRate();
        return new Safety(
                valid, typedStateContractExact, provenanceExact,
                right.crossParentContaminationRate(), right.fragmentationRate(), right.duplicateRate(),
                s1.v3IndexStructure().crossParentContaminatedUnitCount(),
                s1.v3IndexStructure().indexUnitCount());
    }

    private Decision decide(
            MetricSnapshot s0,
            MetricSnapshot s1,
            int wins,
            int losses,
            double retention,
            long recovered,
            int recoveredUsers,
            Capture capture,
            CostObservation cost,
            Safety safety,
            SliceAudit sliceAudit) {
        boolean quality = retention + 1.0e-12 >= 0.98d
                && wins > losses
                && s1.userTop1() > s0.userTop1()
                && s1.queryRecallAt5() + 1.0e-12 >= s0.queryRecallAt5();
        boolean headroom = capture.queryTop1() + 1.0e-12 >= 0.25d
                || recovered >= 8 || recoveredUsers >= 3;
        boolean operation = cost.uniqueChildEmbeddingCount() <= 250
                && cost.selectorP95Ms() <= 5.0d;
        if (safety.valid() && quality && headroom && operation
                && !sliceAudit.severeRegression()) return Decision.PROMISING;
        boolean anyGain = s1.userTop1() > s0.userTop1()
                || s1.queryTop1() > s0.queryTop1()
                || wins > 0;
        if (!safety.valid()
                || retention + 1.0e-12 < 0.98d
                || losses >= wins
                || !anyGain) {
            return Decision.NO_GO;
        }
        return Decision.NEEDS_ADJUSTMENT;
    }

    private SliceAudit sliceAudit(
            SearchV3MinimalShadowEvaluator.EvaluationReport s0,
            SearchV3MinimalShadowEvaluator.EvaluationReport s1) {
        Map<String, Double> professions = sliceTop1Deltas(
                s0.professionSlices(), s1.professionSlices(), "profession");
        Map<String, Double> languages = sliceTop1Deltas(
                s0.languageSlices(), s1.languageSlices(), "language");
        boolean severe = professions.values().stream()
                        .anyMatch(value -> value < -SEVERE_SLICE_TOP1_REGRESSION - 1.0e-12)
                || languages.values().stream()
                        .anyMatch(value -> value < -SEVERE_SLICE_TOP1_REGRESSION - 1.0e-12);
        return new SliceAudit(professions, languages, severe,
                SEVERE_SLICE_TOP1_REGRESSION);
    }

    private Map<String, Double> sliceTop1Deltas(
            Map<String, SearchV3MinimalShadowEvaluator.ComparisonAggregate> before,
            Map<String, SearchV3MinimalShadowEvaluator.ComparisonAggregate> after,
            String label) {
        if (!before.keySet().equals(after.keySet())) {
            throw new IllegalStateException(label + " slice inventory changed");
        }
        Map<String, Double> values = new LinkedHashMap<>();
        for (Map.Entry<String, SearchV3MinimalShadowEvaluator.ComparisonAggregate> entry
                : before.entrySet()) {
            double left = entry.getValue().v3().finalRanking().top1();
            double right = required(after, entry.getKey(), label + " slice")
                    .v3().finalRanking().top1();
            values.put(entry.getKey(), right - left);
        }
        return Collections.unmodifiableMap(values);
    }

    private double directTop1ForUser(
            SearchV3MinimalShadowEvaluator.EvaluationReport report,
            String userBundleId) {
        List<SearchV3MinimalShadowEvaluator.QueryEvaluation> values = report.queries().stream()
                .filter(SearchV3MinimalShadowEvaluator.QueryEvaluation::directPositive)
                .filter(value -> userBundleId.equals(value.userBundleId()))
                .toList();
        if (values.isEmpty()) {
            throw new IllegalStateException("recoverable user has no Direct-positive query: "
                    + userBundleId);
        }
        return ratio(values.stream().filter(value -> value.v3().finalRanking().top1()).count(),
                values.size());
    }

    private boolean sameConstraintValidations(
            List<ConstraintValidation> left,
            List<ConstraintValidation> right) {
        if (left.size() != right.size()) return false;
        for (int index = 0; index < left.size(); index++) {
            ConstraintValidation first = left.get(index);
            ConstraintValidation second = right.get(index);
            if (!first.constraint().equals(second.constraint())
                    || !sameEvaluationResult(first.result(), second.result())) return false;
        }
        return true;
    }

    private boolean sameEvaluationResult(EvaluationResult left, EvaluationResult right) {
        return left.state() == right.state()
                && new LinkedHashSet<>(left.reasons()).equals(new LinkedHashSet<>(right.reasons()));
    }

    private MetricSnapshot metrics(SearchV3MinimalShadowEvaluator.EvaluationReport report) {
        SearchV3MinimalShadowEvaluator.RankingAggregate query = report.queryMicro().v3().finalRanking();
        SearchV3MinimalShadowEvaluator.RankingAggregate user = report.userMacro().v3().finalRanking();
        return new MetricSnapshot(
                query.top1(), query.mrr(), query.ndcgAt5(), query.directRecallAt5(),
                user.top1(), user.mrr());
    }

    private void assertS0Parity(SearchV3MinimalShadowEvaluator.EvaluationReport s0) {
        MetricSnapshot metric = metrics(s0);
        requireClose(0.5411764705882353d, metric.queryTop1(), "S0 Top1");
        requireClose(0.7576470588235295d, metric.queryMrr(), "S0 MRR");
        requireClose(0.7942377401291943d, metric.queryNdcgAt5(), "S0 nDCG@5");
        requireClose(0.9882352941176471d, metric.queryRecallAt5(), "S0 Recall@5");
        requireClose(0.587991718426501d, metric.userTop1(), "S0 user Top1");
        requireClose(0.7827122153209111d, metric.userMrr(), "S0 user MRR");
    }

    private String candidateIdentity(SearchV3MinimalShadowFreeze.OutputArtifact output) {
        List<QueryCandidateIdentity> values = output.queries().stream()
                .map(value -> new QueryCandidateIdentity(value.queryId(), value.v3().candidates()))
                .toList();
        return sha256(canonicalBytes(values));
    }

    private static double capture(double actual, double baseline, double oracle) {
        double denominator = oracle - baseline;
        if (denominator <= 0.0d) throw new IllegalStateException("Oracle headroom is not positive");
        return (actual - baseline) / denominator;
    }

    private static int rank(Integer value) {
        return value == null ? Integer.MAX_VALUE : value;
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0d : (double) numerator / denominator;
    }

    private static boolean nonNegativeFinite(double value) {
        return Double.isFinite(value) && value >= 0.0d;
    }

    private static double percentile(List<Double> sorted, double fraction) {
        if (sorted.isEmpty()) throw new IllegalArgumentException("percentile input is empty");
        int index = (int) Math.ceil(fraction * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    static double cosine(float[] left, float[] right) {
        validateVector(left);
        validateVector(right);
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int index = 0; index < left.length; index++) {
            dot += (double) left[index] * right[index];
            leftNorm += (double) left[index] * left[index];
            rightNorm += (double) right[index] * right[index];
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static void validateVector(float[] vector) {
        if (vector == null || vector.length != DIMENSIONS) {
            throw new IllegalArgumentException("BGE vector must be float[1024]");
        }
        double norm = 0.0d;
        for (float value : vector) {
            if (!Float.isFinite(value)) throw new IllegalArgumentException("BGE vector must be finite");
            norm += (double) value * value;
        }
        if (norm == 0.0d) throw new IllegalArgumentException("BGE vector must have non-zero norm");
    }

    static String vectorSha256(float[] vector) {
        validateVector(vector);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(vector.length);
                for (float value : vector) output.writeInt(Float.floatToIntBits(value));
            }
            return sha256(bytes.toByteArray());
        }
        catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private void writeWrappedCreateNew(
            Path path,
            String field,
            String canonicalSha,
            int canonicalLength,
            Object payload) {
        try {
            Path normalized = requireLocalPath(path);
            Files.createDirectories(normalized.getParent());
            ObjectNode wrapper = mapper.createObjectNode();
            wrapper.put("canonicalSha256", canonicalSha);
            wrapper.put("canonicalByteLength", canonicalLength);
            wrapper.set(field, mapper.valueToTree(payload));
            Files.writeString(
                    normalized,
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot CREATE_NEW PRZ-034 artifact", exception);
        }
    }

    private WrappedVerification verifyWrapped(
            Path path,
            String field,
            String expectedSha,
            int expectedLength) {
        try {
            Path normalized = requireLocalPath(path);
            if (!Files.isRegularFile(normalized) || Files.isSymbolicLink(normalized)) {
                throw new IllegalStateException("PRZ-034 artifact must be regular non-symbolic file");
            }
            byte[] file = Files.readAllBytes(normalized);
            JsonNode wrapper = mapper.readTree(file);
            JsonNode payload = wrapper.path(field);
            byte[] canonical = mapper.writeValueAsBytes(payload);
            if (!expectedSha.equals(wrapper.path("canonicalSha256").asText())
                    || expectedLength != wrapper.path("canonicalByteLength").asInt(-1)
                    || !expectedSha.equals(sha256(canonical))
                    || expectedLength != canonical.length) {
                throw new IllegalStateException("PRZ-034 artifact canonical identity changed");
            }
            return new WrappedVerification(payload, sha256(file), file.length);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot verify PRZ-034 artifact", exception);
        }
    }

    private static Path requireLocalPath(Path path) {
        Path normalized = Objects.requireNonNull(path, "path").toAbsolutePath().normalize();
        String portable = normalized.toString().replace('\\', '/').toLowerCase();
        if (!portable.contains("/local/search-v3-evaluation/prz034/")
                || portable.contains("sealed")) {
            throw new IllegalArgumentException("PRZ-034 artifact path is outside approved local scope");
        }
        return normalized;
    }

    private static byte[] canonicalBytes(Object value) {
        return new ObjectMapper().writeValueAsBytes(value);
    }

    static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    private static <K, V> V required(Map<K, V> values, K key, String label) {
        V value = values.get(key);
        if (value == null) throw new IllegalStateException(label + " missing: " + key);
        return value;
    }

    private static void requireClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > 1.0e-12) {
            throw new IllegalStateException(label + " changed: " + actual);
        }
    }

    private static IllegalStateException blocked(String message) {
        return new IllegalStateException("BLOCKED_PRZ034_INPUT_PARITY: " + message);
    }

    private static IllegalStateException blocked(String message, Throwable cause) {
        return new IllegalStateException("BLOCKED_PRZ034_INPUT_PARITY: " + message, cause);
    }

    enum Phase {
        SOURCE_ONLY,
        ARTIFACT_VERIFIED,
        INPUT_FROZEN,
        INPUT_VERIFIED,
        MODEL_VERIFIED,
        PREDICTION_FROZEN,
        OUTPUT_VERIFIED,
        GOLD_JOINED,
        COMPARISON_EVALUATED,
        ORACLE_JOINED,
        EVALUATED
    }

    enum FailureType {
        CHILD_SELECTOR_FIXED,
        CHILD_SELECTOR_FAILED,
        PASSAGE_RANKING_LIMIT,
        MULTI_ASPECT_LIMIT
    }

    enum Decision {
        PROMISING,
        NEEDS_ADJUSTMENT,
        NO_GO
    }

    static final class PhaseGuard {
        private Phase phase = Phase.SOURCE_ONLY;

        Phase phase() {
            return phase;
        }

        <T> T verifyArtifact(Supplier<T> supplier) {
            require(Phase.SOURCE_ONLY);
            T value = Objects.requireNonNull(supplier.get(), "artifact");
            phase = Phase.ARTIFACT_VERIFIED;
            return value;
        }

        <T> T freezeInput(Supplier<T> supplier) {
            require(Phase.ARTIFACT_VERIFIED);
            T value = Objects.requireNonNull(supplier.get(), "input");
            phase = Phase.INPUT_FROZEN;
            return value;
        }

        <T> T verifyInput(Supplier<T> supplier) {
            require(Phase.INPUT_FROZEN);
            T value = Objects.requireNonNull(supplier.get(), "verified input");
            phase = Phase.INPUT_VERIFIED;
            return value;
        }

        <T> T verifyModel(Supplier<T> supplier) {
            require(Phase.INPUT_VERIFIED);
            T value = Objects.requireNonNull(supplier.get(), "model");
            phase = Phase.MODEL_VERIFIED;
            return value;
        }

        <T> T freezePrediction(Supplier<T> supplier) {
            require(Phase.MODEL_VERIFIED);
            T value = Objects.requireNonNull(supplier.get(), "prediction");
            phase = Phase.PREDICTION_FROZEN;
            return value;
        }

        <T> T verifyOutput(Supplier<T> supplier) {
            require(Phase.PREDICTION_FROZEN);
            T value = Objects.requireNonNull(supplier.get(), "verified output");
            phase = Phase.OUTPUT_VERIFIED;
            return value;
        }

        <A, B, T> T joinGold(A artifact, B output, BiFunction<A, B, T> loader) {
            require(Phase.OUTPUT_VERIFIED);
            T value = Objects.requireNonNull(loader.apply(artifact, output), "Gold");
            phase = Phase.GOLD_JOINED;
            return value;
        }

        <T> T evaluateComparison(Supplier<T> supplier) {
            require(Phase.GOLD_JOINED);
            T value = Objects.requireNonNull(supplier.get(), "S0/S1 comparison");
            phase = Phase.COMPARISON_EVALUATED;
            return value;
        }

        <T> T joinOracle(Supplier<T> supplier) {
            require(Phase.COMPARISON_EVALUATED);
            T value = Objects.requireNonNull(supplier.get(), "PRZ-033 Oracle");
            phase = Phase.ORACLE_JOINED;
            return value;
        }

        <T> T finalizeEvaluation(Supplier<T> supplier) {
            require(Phase.ORACLE_JOINED);
            T value = Objects.requireNonNull(supplier.get(), "final evaluation");
            phase = Phase.EVALUATED;
            return value;
        }

        private void require(Phase expected) {
            if (phase != expected) {
                throw new IllegalStateException(
                        "PRZ-034 phase violation: expected " + expected + " but was " + phase);
            }
        }
    }

    record SelectorInput(
            int schemaVersion,
            String artifactType,
            String policyVersion,
            String policySha256,
            int topPassageK,
            int resultLimit,
            String prz032OutputCanonicalSha256,
            String prz033CandidateCanonicalSha256,
            String runtimeInputSha256,
            String bgeM3Digest,
            List<SelectorQueryInput> queries,
            List<UniqueChildInput> uniqueChildren,
            int passageOccurrenceCount,
            int childOccurrenceCount) {
        SelectorInput {
            queries = List.copyOf(queries);
            uniqueChildren = List.copyOf(uniqueChildren);
        }
    }

    record SelectorQueryInput(
            String queryId,
            String userBundleId,
            String queryText,
            String queryTextSha256,
            List<SelectorPassageInput> passages) {
        SelectorQueryInput {
            passages = List.copyOf(passages);
        }
    }

    record SelectorPassageInput(
            int rank,
            String passageId,
            double passageCosineScore,
            String parentId,
            List<SelectorChildInput> children) {
        SelectorPassageInput {
            children = List.copyOf(children);
            if (children.stream().anyMatch(value -> !parentId.equals(value.parentId()))) {
                throw new IllegalArgumentException("selector Passage crossed parent");
            }
        }
    }

    record SelectorChildInput(
            String evidenceChildId,
            String parentId,
            int originalOrdinal,
            String sourceText,
            String sourceTextSha256,
            ProductionV2ShadowAdapter.SourceSpan span) {
    }

    record UniqueChildInput(String evidenceChildId, String sourceText, String sourceTextSha256) {
    }

    record FrozenSelectorInput(SelectorInput input, String canonicalSha256, int canonicalByteLength) {
    }

    record VerifiedSelectorInput(FrozenSelectorInput frozen, String fileSha256, long fileBytes) {
    }

    /** The array identity is intentionally retained so fresh B3 and Child scoring share one vector. */
    record QueryVector(
            String queryId,
            String queryTextSha256,
            String b3CandidateIdentitySha256,
            float[] vector,
            String sha256,
            boolean b3ParityVerified) {
    }

    record ModelIdentity(String name, String digest, int dimensions, String similarity) {
    }

    record ScoredChild(String evidenceChildId, int originalOrdinal, double cosineScore) {
    }

    record PassagePrediction(
            int rank,
            String passageId,
            String parentId,
            double passageCosineScore,
            List<ScoredChild> children) {
        PassagePrediction {
            children = List.copyOf(children);
        }
    }

    record QueryPrediction(
            String queryId,
            String queryVectorSha256,
            double selectionMs,
            List<PassagePrediction> passages) {
        QueryPrediction {
            passages = List.copyOf(passages);
        }
    }

    record EmbeddingCostObservation(
            int b3PassageEmbeddingCount,
            int uniqueChildEmbeddingCount,
            int childEmbeddingBatchCount,
            double b3PassageEmbeddingMs,
            double queryEmbeddingMs,
            double childEmbeddingMs,
            long additionalVectorStorageBytes) {
    }

    record CostObservation(
            int b3PassageEmbeddingCount,
            int uniqueChildEmbeddingCount,
            int childEmbeddingBatchCount,
            double b3PassageEmbeddingMs,
            double queryEmbeddingMs,
            double childEmbeddingMs,
            double selectorP50Ms,
            double selectorP95Ms,
            long additionalVectorStorageBytes) {
    }

    record Prediction(
            int schemaVersion,
            String artifactType,
            String policyVersion,
            String policySha256,
            String selectorInputCanonicalSha256,
            ModelIdentity model,
            boolean queryVectorSharedWithB3,
            String historicalVectorParity,
            int queryCount,
            int uniqueChildEmbeddingCount,
            CostObservation cost,
            List<QueryPrediction> queries) {
        Prediction {
            queries = List.copyOf(queries);
        }
    }

    record FrozenPrediction(Prediction prediction, String canonicalSha256, int canonicalByteLength) {
    }

    record VerifiedPrediction(FrozenPrediction frozen, String fileSha256, long fileBytes) {
    }

    record MetricSnapshot(
            double queryTop1,
            double queryMrr,
            double queryNdcgAt5,
            double queryRecallAt5,
            double userTop1,
            double userMrr) {
    }

    record Capture(double queryTop1, double userTop1, double queryMrr) {
    }

    record Safety(
            boolean valid,
            boolean typedStateContractExact,
            boolean provenanceExact,
            double finalCrossParentContaminationRate,
            double finalFragmentationRate,
            double finalDuplicateRate,
            long frozenIndexCrossParentContaminatedPassages,
            long frozenIndexPassageCount) {
    }

    record SliceAudit(
            Map<String, Double> professionTop1Deltas,
            Map<String, Double> languageTop1Deltas,
            boolean severeRegression,
            double severeRegressionThreshold) {
        SliceAudit {
            professionTop1Deltas = Map.copyOf(professionTop1Deltas);
            languageTop1Deltas = Map.copyOf(languageTop1Deltas);
        }
    }

    record ComparisonEvaluation(
            SearchV3MinimalShadowFreeze.OutputArtifact s0Output,
            SearchV3MinimalShadowFreeze.OutputArtifact s1Output,
            SearchV3MinimalShadowEvaluator.EvaluationReport s0,
            SearchV3MinimalShadowEvaluator.EvaluationReport s1,
            MetricSnapshot s0Metrics,
            MetricSnapshot s1Metrics,
            int wins,
            int losses,
            int ties,
            double rankOneRetention,
            long typedSelectionLimitQueries,
            Safety safety) {
    }

    record Evaluation(
            SearchV3MinimalShadowEvaluator.EvaluationReport s0,
            SearchV3MinimalShadowEvaluator.EvaluationReport s1,
            MetricSnapshot s0Metrics,
            MetricSnapshot s1Metrics,
            MetricSnapshot oracleMetrics,
            int wins,
            int losses,
            int ties,
            double rankOneRetention,
            long recoverableFixed,
            int recoverableUserBundlesImproved,
            Capture capture,
            Map<FailureType, Long> failureTypes,
            long typedSelectionLimitQueries,
            SliceAudit sliceAudit,
            Safety safety,
            Decision decision) {
    }

    private record WrappedVerification(JsonNode payload, String fileSha256, long fileBytes) {
    }

    private record QueryCandidateIdentity(
            String queryId,
            List<MinimalV3ShadowAdapter.CandidateResult> candidates) {
    }
}
