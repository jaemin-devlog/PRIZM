package com.prizm.search.evaluation.searchv3.structural;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Evaluation-only A/B simulator for the PRZ-035 Child embedding operation decision. */
final class SearchV3ChildEmbeddingOperationStrategy {

    static final int DIMENSIONS = SearchV3AtomicChildDenseSelector.DIMENSIONS;
    static final int EMBEDDING_BATCH_SIZE = SearchV3AtomicChildDenseSelector.EMBEDDING_BATCH_SIZE;
    static final int EXPECTED_QUERY_COUNT = 117;
    static final int EXPECTED_PASSAGE_COUNT = 160;
    static final int EXPECTED_CORPUS_CHILD_COUNT = 241;
    static final int EXPECTED_TOP5_UNIQUE_CHILD_COUNT = 227;
    static final int EXPECTED_TOP5_CHILD_OCCURRENCE_COUNT = 804;
    static final int EXPECTED_ON_DEMAND_RECALCULATION_COUNT = 577;
    static final long VECTOR_BYTES = (long) DIMENSIONS * Float.BYTES;
    static final String STRATEGY_DEFINITION = String.join("\n",
            "version=PRZ035_CHILD_EMBEDDING_OPERATION_V1",
            "selector=CHILD_DENSE_V1_UNCHANGED",
            "strategyA=INDEX_ALL_241_CHILDREN_THEN_STORE",
            "strategyB=QUERY_TOP5_CHILDREN_NO_APPLICATION_CACHE",
            "queryVector=SHARED_B3_VECTOR",
            "childInput=EvidenceChild.sourceText",
            "model=bge-m3:latest",
            "dimension=1024",
            "similarity=COSINE",
            "batchSize=32",
            "resultParity=EXACT",
            "newDatabase=false",
            "sealedFinal=false") + "\n";
    static final String STRATEGY_DEFINITION_SHA256 =
            SearchV3AtomicChildDenseSelector.sha256(STRATEGY_DEFINITION);

    CorpusInventory inventory(
            MinimalV3ShadowAdapter.IndexedCorpus corpus,
            SearchV3AtomicChildDenseSelector.VerifiedSelectorInput selectorInput) {
        Objects.requireNonNull(corpus, "B3 corpus");
        Objects.requireNonNull(selectorInput, "verified selector input");
        if (corpus.passages().size() != EXPECTED_PASSAGE_COUNT) {
            throw new IllegalStateException("PRZ-035 B3 Passage inventory changed");
        }

        LinkedHashMap<String, CorpusChild> all = new LinkedHashMap<>();
        for (MinimalV3ShadowAdapter.IndexedPassage indexed : corpus.passages()) {
            RetrievalPassage passage = indexed.passage();
            for (EvidenceChild child : passage.evidenceChildren()) {
                SourceProvenance source = child.provenance();
                CorpusChild value = new CorpusChild(
                        child.childId(), child.sourceText(),
                        SearchV3AtomicChildDenseSelector.sha256(child.sourceText()),
                        source.documentId(), source.versionId(),
                        source.parentAnnotationCandidateId(), source.codePointStart(),
                        source.codePointEnd());
                CorpusChild previous = all.putIfAbsent(value.evidenceChildId(), value);
                if (previous != null && !previous.equals(value)) {
                    throw new IllegalStateException(
                            "EvidenceChild identity/source changed across B3 passages");
                }
            }
        }

        SearchV3AtomicChildDenseSelector.SelectorInput input = selectorInput.frozen().input();
        LinkedHashSet<String> top5 = new LinkedHashSet<>();
        int occurrences = 0;
        for (SearchV3AtomicChildDenseSelector.SelectorQueryInput query : input.queries()) {
            for (SearchV3AtomicChildDenseSelector.SelectorPassageInput passage : query.passages()) {
                for (SearchV3AtomicChildDenseSelector.SelectorChildInput child : passage.children()) {
                    CorpusChild corpusChild = all.get(child.evidenceChildId());
                    if (corpusChild == null
                            || !corpusChild.sourceText().equals(child.sourceText())
                            || !corpusChild.sourceTextSha256().equals(child.sourceTextSha256())
                            || !corpusChild.parentId().equals(child.parentId())) {
                        throw new IllegalStateException(
                                "Top5 Child is not an unchanged member of the indexed corpus");
                    }
                    top5.add(child.evidenceChildId());
                    occurrences++;
                }
            }
        }
        if (all.size() != EXPECTED_CORPUS_CHILD_COUNT
                || top5.size() != EXPECTED_TOP5_UNIQUE_CHILD_COUNT
                || occurrences != EXPECTED_TOP5_CHILD_OCCURRENCE_COUNT
                || input.queries().size() != EXPECTED_QUERY_COUNT) {
            throw new IllegalStateException("PRZ-035 Child inventory changed");
        }
        return new CorpusInventory(
                List.copyOf(all.values()), Set.copyOf(top5), occurrences);
    }

    StrategyRun runPrecomputed(
            CorpusInventory inventory,
            SearchV3AtomicChildDenseSelector.VerifiedSelectorInput selectorInput,
            Map<String, SearchV3AtomicChildDenseSelector.QueryVector> queryVectors,
            BatchEmbedder embedder) {
        Objects.requireNonNull(inventory, "corpus inventory");
        EmbeddingResult embedded = embedder.embed(
                inventory.allChildren().stream().map(CorpusChild::sourceText).toList());
        validateEmbeddingResult(embedded, inventory.allChildren().size());

        Map<String, float[]> vectors = new LinkedHashMap<>();
        Map<String, String> vectorHashes = new LinkedHashMap<>();
        for (int index = 0; index < inventory.allChildren().size(); index++) {
            CorpusChild child = inventory.allChildren().get(index);
            float[] vector = embedded.embeddings().get(index);
            vectors.put(child.evidenceChildId(), vector);
            vectorHashes.put(
                    child.evidenceChildId(),
                    SearchV3AtomicChildDenseSelector.vectorSha256(vector));
        }

        List<SearchV3AtomicChildDenseSelector.QueryPrediction> predictions = new ArrayList<>();
        List<QueryOperation> queryOperations = new ArrayList<>();
        for (SearchV3AtomicChildDenseSelector.SelectorQueryInput query
                : selectorInput.frozen().input().queries()) {
            long started = System.nanoTime();
            SearchV3AtomicChildDenseSelector.QueryPrediction prediction = scoreQuery(
                    query, requiredQueryVector(queryVectors, query), vectors, 0.0d);
            double selectionMs = millis(System.nanoTime() - started);
            predictions.add(new SearchV3AtomicChildDenseSelector.QueryPrediction(
                    prediction.queryId(), prediction.queryVectorSha256(), selectionMs,
                    prediction.passages()));
            queryOperations.add(new QueryOperation(
                    query.queryId(), childCount(query), 0.0d, selectionMs,
                    selectionMs));
        }
        return new StrategyRun(
                Strategy.PRECOMPUTE_CHILD_EMBEDDINGS,
                inventory.allChildren().size(), 1,
                divideRoundingUp(inventory.allChildren().size(), EMBEDDING_BATCH_SIZE),
                inventory.allChildren().size(), inventory.allChildren().size(), 0,
                millis(embedded.elapsedNanos()), List.copyOf(queryOperations),
                List.copyOf(predictions), Map.copyOf(vectorHashes));
    }

    StrategyRun runOnDemandNoCache(
            CorpusInventory inventory,
            SearchV3AtomicChildDenseSelector.VerifiedSelectorInput selectorInput,
            Map<String, SearchV3AtomicChildDenseSelector.QueryVector> queryVectors,
            Map<String, String> expectedVectorHashes,
            BatchEmbedder embedder) {
        Objects.requireNonNull(inventory, "corpus inventory");
        Objects.requireNonNull(expectedVectorHashes, "precomputed vector hashes");
        List<SearchV3AtomicChildDenseSelector.QueryPrediction> predictions = new ArrayList<>();
        List<QueryOperation> queryOperations = new ArrayList<>();
        LinkedHashSet<String> accessedUnique = new LinkedHashSet<>();
        LinkedHashMap<String, String> firstVectorHashes = new LinkedHashMap<>();
        int embeddedOccurrences = 0;
        int physicalBatches = 0;
        long embeddingNanos = 0L;

        for (SearchV3AtomicChildDenseSelector.SelectorQueryInput query
                : selectorInput.frozen().input().queries()) {
            List<SearchV3AtomicChildDenseSelector.SelectorChildInput> children = query.passages().stream()
                    .flatMap(passage -> passage.children().stream())
                    .toList();
            EmbeddingResult embedded = embedder.embed(
                    children.stream()
                            .map(SearchV3AtomicChildDenseSelector.SelectorChildInput::sourceText)
                            .toList());
            validateEmbeddingResult(embedded, children.size());
            double embeddingMs = millis(embedded.elapsedNanos());
            embeddingNanos += embedded.elapsedNanos();
            embeddedOccurrences += children.size();
            physicalBatches += divideRoundingUp(children.size(), EMBEDDING_BATCH_SIZE);

            LinkedHashMap<String, float[]> queryVectorsByChild = new LinkedHashMap<>();
            for (int index = 0; index < children.size(); index++) {
                SearchV3AtomicChildDenseSelector.SelectorChildInput child = children.get(index);
                float[] vector = embedded.embeddings().get(index);
                String hash = SearchV3AtomicChildDenseSelector.vectorSha256(vector);
                String expected = expectedVectorHashes.get(child.evidenceChildId());
                if (!hash.equals(expected)) {
                    throw new IllegalStateException(
                            "RESULT_PARITY_FAIL: on-demand Child vector changed");
                }
                float[] previous = queryVectorsByChild.putIfAbsent(child.evidenceChildId(), vector);
                if (previous != null
                        && !SearchV3AtomicChildDenseSelector.vectorSha256(previous).equals(hash)) {
                    throw new IllegalStateException(
                            "RESULT_PARITY_FAIL: repeated Child vector changed within query");
                }
                accessedUnique.add(child.evidenceChildId());
                String first = firstVectorHashes.putIfAbsent(child.evidenceChildId(), hash);
                if (first != null && !first.equals(hash)) {
                    throw new IllegalStateException(
                            "RESULT_PARITY_FAIL: repeated Child vector changed across queries");
                }
            }

            long selectionStarted = System.nanoTime();
            SearchV3AtomicChildDenseSelector.QueryPrediction prediction = scoreQuery(
                    query, requiredQueryVector(queryVectors, query), queryVectorsByChild,
                    embeddingMs);
            double selectionMs = millis(System.nanoTime() - selectionStarted);
            double queryIncrementMs = embeddingMs + selectionMs;
            predictions.add(new SearchV3AtomicChildDenseSelector.QueryPrediction(
                    prediction.queryId(), prediction.queryVectorSha256(), queryIncrementMs,
                    prediction.passages()));
            queryOperations.add(new QueryOperation(
                    query.queryId(), children.size(), embeddingMs, selectionMs,
                    queryIncrementMs));
        }
        int recalculations = embeddedOccurrences - accessedUnique.size();
        SearchV3AtomicChildDenseSelector.SelectorInput input = selectorInput.frozen().input();
        int expectedBatches = input.queries().stream()
                .mapToInt(query -> divideRoundingUp(childCount(query), EMBEDDING_BATCH_SIZE))
                .sum();
        if (embeddedOccurrences != input.childOccurrenceCount()
                || accessedUnique.size() != input.uniqueChildren().size()
                || recalculations != input.childOccurrenceCount() - input.uniqueChildren().size()
                || physicalBatches != expectedBatches) {
            throw new IllegalStateException("on-demand no-cache inventory changed");
        }
        return new StrategyRun(
                Strategy.ON_DEMAND_CHILD_EMBEDDINGS,
                inventory.allChildren().size(), input.queries().size(), physicalBatches,
                embeddedOccurrences, accessedUnique.size(), recalculations,
                millis(embeddingNanos), List.copyOf(queryOperations),
                List.copyOf(predictions), Map.copyOf(firstVectorHashes));
    }

    ResultParity assertResultParity(
            StrategyRun precomputed,
            StrategyRun onDemand,
            SearchV3AtomicChildDenseSelector.VerifiedPrediction frozenPrz034) {
        PredictionIdentity left = identity(precomputed.predictions());
        PredictionIdentity right = identity(onDemand.predictions());
        PredictionIdentity expected = identity(
                frozenPrz034.frozen().prediction().queries());
        if (!left.equals(right) || !left.equals(expected)) {
            throw new IllegalStateException(
                    "RESULT_PARITY_FAIL: A/B or PRZ-034 Child ordering changed");
        }
        return new ResultParity(true, true, true, left.sha256());
    }

    StorageEstimate storageEstimate(CorpusInventory inventory) {
        long passageBytes = (long) EXPECTED_PASSAGE_COUNT * VECTOR_BYTES;
        long childBytes = (long) inventory.allChildren().size() * VECTOR_BYTES;
        long combined = passageBytes + childBytes;
        return new StorageEstimate(
                VECTOR_BYTES, passageBytes, childBytes, combined,
                ratio(childBytes, passageBytes), ratio(combined - passageBytes, passageBytes));
    }

    List<Projection> projections(
            StrategyRun precomputed,
            StrategyRun onDemand,
            int... queryCounts) {
        double averageChildren = (double) onDemand.embeddedVectorOccurrences()
                / onDemand.queryOperations().size();
        double averageOnDemandMs = onDemand.embeddingWallMs()
                / onDemand.queryOperations().size();
        List<Projection> values = new ArrayList<>();
        for (int queryCount : queryCounts) {
            if (queryCount <= 0) throw new IllegalArgumentException("queryCount must be positive");
            double onDemandChildren = averageChildren * queryCount;
            values.add(new Projection(
                    queryCount,
                    precomputed.embeddedVectorOccurrences(),
                    onDemandChildren,
                    EXPECTED_PASSAGE_COUNT + precomputed.embeddedVectorOccurrences(),
                    EXPECTED_PASSAGE_COUNT + onDemandChildren,
                    precomputed.embeddingWallMs(),
                    averageOnDemandMs * queryCount,
                    true));
        }
        return List.copyOf(values);
    }

    QueryDistribution queryDistribution(StrategyRun onDemand) {
        List<Integer> counts = onDemand.queryOperations().stream()
                .map(QueryOperation::childCount).sorted().toList();
        List<Double> embeddingMs = onDemand.queryOperations().stream()
                .map(QueryOperation::embeddingMs).sorted().toList();
        return new QueryDistribution(
                counts.get(0), counts.stream().mapToInt(Integer::intValue).average().orElseThrow(),
                counts.get(counts.size() - 1), percentile(embeddingMs, 0.50d),
                percentile(embeddingMs, 0.95d));
    }

    QueryLatency queryLatency(
            StrategyRun run,
            Map<String, Double> freshB3QueryMs) {
        List<Double> totals = run.queryOperations().stream().map(value -> {
            Double base = freshB3QueryMs.get(value.queryId());
            if (base == null || !Double.isFinite(base) || base < 0.0d) {
                throw new IllegalStateException("missing fresh B3 query timing");
            }
            return base + value.queryIncrementMs();
        }).sorted().toList();
        return new QueryLatency(percentile(totals, 0.50d), percentile(totals, 0.95d));
    }

    private SearchV3AtomicChildDenseSelector.QueryPrediction scoreQuery(
            SearchV3AtomicChildDenseSelector.SelectorQueryInput query,
            SearchV3AtomicChildDenseSelector.QueryVector queryVector,
            Map<String, float[]> childVectors,
            double selectionMs) {
        if (!query.queryId().equals(queryVector.queryId())
                || !query.queryTextSha256().equals(queryVector.queryTextSha256())
                || !queryVector.b3ParityVerified()
                || !queryVector.sha256().equals(
                        SearchV3AtomicChildDenseSelector.vectorSha256(queryVector.vector()))) {
            throw new IllegalStateException("PRZ-035 query vector parity failed");
        }
        List<SearchV3AtomicChildDenseSelector.PassagePrediction> passages = new ArrayList<>();
        for (SearchV3AtomicChildDenseSelector.SelectorPassageInput passage : query.passages()) {
            List<SearchV3AtomicChildDenseSelector.ScoredChild> scored = passage.children().stream()
                    .map(child -> new SearchV3AtomicChildDenseSelector.ScoredChild(
                            child.evidenceChildId(), child.originalOrdinal(),
                            SearchV3AtomicChildDenseSelector.cosine(
                                    queryVector.vector(), required(childVectors, child.evidenceChildId()))))
                    .sorted(Comparator.comparingDouble(
                                    SearchV3AtomicChildDenseSelector.ScoredChild::cosineScore)
                            .reversed()
                            .thenComparingInt(
                                    SearchV3AtomicChildDenseSelector.ScoredChild::originalOrdinal)
                            .thenComparing(
                                    SearchV3AtomicChildDenseSelector.ScoredChild::evidenceChildId))
                    .toList();
            if (passage.children().stream().anyMatch(
                    child -> !passage.parentId().equals(child.parentId()))) {
                throw new IllegalStateException("PRZ-035 crossed Parent while selecting Child");
            }
            passages.add(new SearchV3AtomicChildDenseSelector.PassagePrediction(
                    passage.rank(), passage.passageId(), passage.parentId(),
                    passage.passageCosineScore(), scored));
        }
        return new SearchV3AtomicChildDenseSelector.QueryPrediction(
                query.queryId(), queryVector.sha256(), selectionMs, passages);
    }

    private PredictionIdentity identity(
            List<SearchV3AtomicChildDenseSelector.QueryPrediction> predictions) {
        List<QueryIdentity> queries = predictions.stream()
                .map(value -> new QueryIdentity(
                        value.queryId(), value.queryVectorSha256(), value.passages()))
                .toList();
        StringBuilder canonical = new StringBuilder();
        for (QueryIdentity query : queries) {
            canonical.append(query.queryId()).append('|')
                    .append(query.queryVectorSha256()).append('\n');
            for (SearchV3AtomicChildDenseSelector.PassagePrediction passage : query.passages()) {
                canonical.append(passage.rank()).append('|')
                        .append(passage.passageId()).append('|')
                        .append(passage.parentId()).append('|')
                        .append(Double.toHexString(passage.passageCosineScore())).append('\n');
                for (SearchV3AtomicChildDenseSelector.ScoredChild child : passage.children()) {
                    canonical.append(child.evidenceChildId()).append('|')
                            .append(child.originalOrdinal()).append('|')
                            .append(Double.toHexString(child.cosineScore())).append('\n');
                }
            }
        }
        return new PredictionIdentity(
                queries, SearchV3AtomicChildDenseSelector.sha256(canonical.toString()));
    }

    private SearchV3AtomicChildDenseSelector.QueryVector requiredQueryVector(
            Map<String, SearchV3AtomicChildDenseSelector.QueryVector> values,
            SearchV3AtomicChildDenseSelector.SelectorQueryInput query) {
        SearchV3AtomicChildDenseSelector.QueryVector value = values.get(query.queryId());
        if (value == null) throw new IllegalStateException("missing shared query vector");
        return value;
    }

    private int childCount(SearchV3AtomicChildDenseSelector.SelectorQueryInput query) {
        return query.passages().stream().mapToInt(value -> value.children().size()).sum();
    }

    private void validateEmbeddingResult(EmbeddingResult embedded, int expectedCount) {
        if (embedded == null || embedded.embeddings().size() != expectedCount
                || embedded.elapsedNanos() < 0L) {
            throw new IllegalStateException("embedding result count/time changed");
        }
        embedded.embeddings().forEach(value -> {
            if (value.length != DIMENSIONS) {
                throw new IllegalStateException("embedding dimension changed");
            }
            SearchV3AtomicChildDenseSelector.vectorSha256(value);
        });
    }

    private static float[] required(Map<String, float[]> values, String id) {
        float[] value = values.get(id);
        if (value == null) throw new IllegalStateException("missing Child vector: " + id);
        return value;
    }

    private static double percentile(List<Double> sorted, double fraction) {
        if (sorted.isEmpty()) return 0.0d;
        int index = Math.max(0, Math.min(sorted.size() - 1,
                (int) Math.ceil(sorted.size() * fraction) - 1));
        return sorted.get(index);
    }

    private static int divideRoundingUp(int numerator, int denominator) {
        return (numerator + denominator - 1) / denominator;
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0L ? 0.0d : (double) numerator / denominator;
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    enum Strategy {
        PRECOMPUTE_CHILD_EMBEDDINGS,
        ON_DEMAND_CHILD_EMBEDDINGS
    }

    @FunctionalInterface
    interface BatchEmbedder {
        EmbeddingResult embed(List<String> sourceTexts);
    }

    record EmbeddingResult(List<float[]> embeddings, long elapsedNanos) {
        EmbeddingResult {
            embeddings = List.copyOf(embeddings);
        }
    }

    record CorpusChild(
            String evidenceChildId,
            String sourceText,
            String sourceTextSha256,
            String documentId,
            String versionId,
            String parentId,
            int codePointStart,
            int codePointEnd) {
    }

    record CorpusInventory(
            List<CorpusChild> allChildren,
            Set<String> top5UniqueChildIds,
            int top5ChildOccurrenceCount) {
        CorpusInventory {
            allChildren = List.copyOf(allChildren);
            top5UniqueChildIds = Set.copyOf(top5UniqueChildIds);
        }
    }

    record QueryOperation(
            String queryId,
            int childCount,
            double embeddingMs,
            double selectionMs,
            double queryIncrementMs) {
    }

    record StrategyRun(
            Strategy strategy,
            int corpusChildCount,
            int modelInvocationCount,
            int physicalBatchCount,
            int embeddedVectorOccurrences,
            int uniqueAccessedChildCount,
            int repeatedRecalculationCount,
            double embeddingWallMs,
            List<QueryOperation> queryOperations,
            List<SearchV3AtomicChildDenseSelector.QueryPrediction> predictions,
            Map<String, String> childVectorHashes) {
        StrategyRun {
            queryOperations = List.copyOf(queryOperations);
            predictions = List.copyOf(predictions);
            childVectorHashes = Map.copyOf(childVectorHashes);
        }
    }

    record QueryIdentity(
            String queryId,
            String queryVectorSha256,
            List<SearchV3AtomicChildDenseSelector.PassagePrediction> passages) {
        QueryIdentity {
            passages = List.copyOf(passages);
        }
    }

    record PredictionIdentity(List<QueryIdentity> queries, String sha256) {
        PredictionIdentity {
            queries = List.copyOf(queries);
        }
    }

    record ResultParity(
            boolean abChildOrderExact,
            boolean frozenPrz034ChildOrderExact,
            boolean queryVectorExact,
            String predictionIdentitySha256) {
    }

    record StorageEstimate(
            long bytesPerVector,
            long passageVectorBytes,
            long childVectorBytes,
            long passageAndChildVectorBytes,
            double childToPassageRatio,
            double passageOnlyIncreaseRatio) {
    }

    record Projection(
            int queryCount,
            double precomputedChildEmbeddingCount,
            double onDemandChildEmbeddingCount,
            double precomputedTotalVectorComputationCount,
            double onDemandTotalVectorComputationCount,
            double precomputedEmbeddingMs,
            double onDemandEmbeddingMs,
            boolean projected) {
    }

    record QueryDistribution(
            int childCountMin,
            double childCountAverage,
            int childCountMax,
            double childEmbeddingP50Ms,
            double childEmbeddingP95Ms) {
    }

    record QueryLatency(double p50Ms, double p95Ms) {
    }
}
