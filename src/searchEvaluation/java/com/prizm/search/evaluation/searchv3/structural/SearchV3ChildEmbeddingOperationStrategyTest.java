package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SearchV3ChildEmbeddingOperationStrategyTest {

    private final SearchV3ChildEmbeddingOperationStrategy strategy =
            new SearchV3ChildEmbeddingOperationStrategy();

    @Test
    void precomputeAndNoCacheOnDemandProduceTheSameFrozenChildOrder() {
        Fixture fixture = fixture();
        AtomicInteger precomputeCalls = new AtomicInteger();
        AtomicInteger onDemandCalls = new AtomicInteger();

        SearchV3ChildEmbeddingOperationStrategy.StrategyRun precomputed = strategy.runPrecomputed(
                fixture.inventory(), fixture.input(), fixture.queryVectors(),
                texts -> embeddings(texts, precomputeCalls));
        SearchV3ChildEmbeddingOperationStrategy.StrategyRun onDemand = strategy.runOnDemandNoCache(
                fixture.inventory(), fixture.input(), fixture.queryVectors(),
                precomputed.childVectorHashes(),
                texts -> embeddings(texts, onDemandCalls));

        SearchV3AtomicChildDenseSelector.VerifiedPrediction frozen = verifiedPrediction(
                fixture.input(), precomputed.predictions());
        SearchV3ChildEmbeddingOperationStrategy.ResultParity parity =
                strategy.assertResultParity(precomputed, onDemand, frozen);

        assertThat(parity.abChildOrderExact()).isTrue();
        assertThat(parity.frozenPrz034ChildOrderExact()).isTrue();
        assertThat(precomputeCalls).hasValue(1);
        assertThat(onDemandCalls).hasValue(2);
        assertThat(precomputed.embeddedVectorOccurrences()).isEqualTo(3);
        assertThat(onDemand.embeddedVectorOccurrences()).isEqualTo(4);
        assertThat(onDemand.uniqueAccessedChildCount()).isEqualTo(3);
        assertThat(onDemand.repeatedRecalculationCount()).isEqualTo(1);
        assertThat(onDemand.queryOperations()).allSatisfy(
                value -> assertThat(value.embeddingMs()).isPositive());
    }

    @Test
    void rejectsAnyOnDemandVectorThatDiffersFromPrecomputedOutput() {
        Fixture fixture = fixture();
        SearchV3ChildEmbeddingOperationStrategy.StrategyRun precomputed = strategy.runPrecomputed(
                fixture.inventory(), fixture.input(), fixture.queryVectors(),
                texts -> embeddings(texts, new AtomicInteger()));

        assertThatThrownBy(() -> strategy.runOnDemandNoCache(
                fixture.inventory(), fixture.input(), fixture.queryVectors(),
                precomputed.childVectorHashes(), texts -> {
                    SearchV3ChildEmbeddingOperationStrategy.EmbeddingResult result =
                            embeddings(texts, new AtomicInteger());
                    result.embeddings().get(0)[0] += 0.25f;
                    return result;
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RESULT_PARITY_FAIL");
    }

    @Test
    void calculatesRawStorageAndProjectedNoCacheWorkWithoutInventingDatabaseOverhead() {
        Fixture fixture = fixture();
        SearchV3ChildEmbeddingOperationStrategy.StrategyRun precomputed = strategy.runPrecomputed(
                fixture.inventory(), fixture.input(), fixture.queryVectors(),
                texts -> embeddings(texts, new AtomicInteger()));
        SearchV3ChildEmbeddingOperationStrategy.StrategyRun onDemand = strategy.runOnDemandNoCache(
                fixture.inventory(), fixture.input(), fixture.queryVectors(),
                precomputed.childVectorHashes(),
                texts -> embeddings(texts, new AtomicInteger()));

        SearchV3ChildEmbeddingOperationStrategy.StorageEstimate storage =
                strategy.storageEstimate(fixture.inventory());
        List<SearchV3ChildEmbeddingOperationStrategy.Projection> projection =
                strategy.projections(precomputed, onDemand, 1, 10, 50, 100);

        assertThat(storage.bytesPerVector()).isEqualTo(4096L);
        assertThat(storage.childVectorBytes()).isEqualTo(3L * 4096L);
        assertThat(projection).extracting(
                        SearchV3ChildEmbeddingOperationStrategy.Projection::queryCount)
                .containsExactly(1, 10, 50, 100);
        assertThat(projection.get(0).onDemandChildEmbeddingCount()).isEqualTo(2.0d);
        assertThat(projection).allSatisfy(
                value -> assertThat(value.projected()).isTrue());
    }

    @Test
    void blocksGoldUntilBothStrategyOutputsAreVerified() {
        Prz035ChildEmbeddingOperationStrategyBenchmarkTest.GoldAfterBothOutputsGuard guard =
                new Prz035ChildEmbeddingOperationStrategyBenchmarkTest.GoldAfterBothOutputsGuard();

        assertThatThrownBy(() -> guard.joinGold(() -> "gold"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("phase violation");
    }

    private Fixture fixture() {
        SearchV3AtomicChildDenseSelector.SelectorChildInput a = child("C-A", "P-1", 0, "alpha");
        SearchV3AtomicChildDenseSelector.SelectorChildInput b = child("C-B", "P-1", 1, "beta");
        SearchV3AtomicChildDenseSelector.SelectorChildInput c = child("C-C", "P-2", 0, "gamma");
        SearchV3AtomicChildDenseSelector.SelectorPassageInput p1 =
                new SearchV3AtomicChildDenseSelector.SelectorPassageInput(
                        1, "RP-1", 0.8d, "P-1", List.of(a, b));
        SearchV3AtomicChildDenseSelector.SelectorPassageInput p2 =
                new SearchV3AtomicChildDenseSelector.SelectorPassageInput(
                        1, "RP-2", 0.7d, "P-2", List.of(c));
        SearchV3AtomicChildDenseSelector.SelectorPassageInput p3 =
                new SearchV3AtomicChildDenseSelector.SelectorPassageInput(
                        2, "RP-3", 0.6d, "P-1", List.of(a));
        SearchV3AtomicChildDenseSelector.SelectorQueryInput q1 = query("Q-1", p1);
        SearchV3AtomicChildDenseSelector.SelectorQueryInput q2 = query("Q-2", p2, p3);
        List<SearchV3AtomicChildDenseSelector.UniqueChildInput> unique = List.of(
                unique(a), unique(b), unique(c));
        SearchV3AtomicChildDenseSelector.SelectorInput input =
                new SearchV3AtomicChildDenseSelector.SelectorInput(
                        1, "TEST_INPUT", "CHILD_DENSE_V1", "policy", 5, 5,
                        "p32", "p33", "runtime", "digest",
                        List.of(q1, q2), unique, 3, 4);
        SearchV3AtomicChildDenseSelector.FrozenSelectorInput frozen =
                new SearchV3AtomicChildDenseSelector.FrozenSelectorInput(input, "input", 1);
        SearchV3AtomicChildDenseSelector.VerifiedSelectorInput verified =
                new SearchV3AtomicChildDenseSelector.VerifiedSelectorInput(frozen, "file", 1L);
        SearchV3ChildEmbeddingOperationStrategy.CorpusInventory inventory =
                new SearchV3ChildEmbeddingOperationStrategy.CorpusInventory(
                        List.of(corpus(a), corpus(b), corpus(c)), Set.of("C-A", "C-B", "C-C"), 4);
        Map<String, SearchV3AtomicChildDenseSelector.QueryVector> vectors = new LinkedHashMap<>();
        vectors.put("Q-1", queryVector(q1, vector("alpha")));
        vectors.put("Q-2", queryVector(q2, vector("gamma")));
        return new Fixture(verified, inventory, vectors);
    }

    private SearchV3AtomicChildDenseSelector.SelectorQueryInput query(
            String id,
            SearchV3AtomicChildDenseSelector.SelectorPassageInput... passages) {
        String text = "query-" + id;
        return new SearchV3AtomicChildDenseSelector.SelectorQueryInput(
                id, "U-1", text, SearchV3AtomicChildDenseSelector.sha256(text), List.of(passages));
    }

    private SearchV3AtomicChildDenseSelector.SelectorChildInput child(
            String id,
            String parent,
            int ordinal,
            String text) {
        ProductionV2ShadowAdapter.SourceSpan span = new ProductionV2ShadowAdapter.SourceSpan(
                "U-1", "D-1", "V-1", "fixture.txt", null,
                ordinal * 10, ordinal * 10 + text.codePointCount(0, text.length()),
                text, SearchV3AtomicChildDenseSelector.sha256(text));
        return new SearchV3AtomicChildDenseSelector.SelectorChildInput(
                id, parent, ordinal, text, SearchV3AtomicChildDenseSelector.sha256(text), span);
    }

    private SearchV3AtomicChildDenseSelector.UniqueChildInput unique(
            SearchV3AtomicChildDenseSelector.SelectorChildInput child) {
        return new SearchV3AtomicChildDenseSelector.UniqueChildInput(
                child.evidenceChildId(), child.sourceText(), child.sourceTextSha256());
    }

    private SearchV3ChildEmbeddingOperationStrategy.CorpusChild corpus(
            SearchV3AtomicChildDenseSelector.SelectorChildInput child) {
        return new SearchV3ChildEmbeddingOperationStrategy.CorpusChild(
                child.evidenceChildId(), child.sourceText(), child.sourceTextSha256(),
                "D-1", "V-1", child.parentId(), child.span().codePointStart(),
                child.span().codePointEnd());
    }

    private SearchV3AtomicChildDenseSelector.QueryVector queryVector(
            SearchV3AtomicChildDenseSelector.SelectorQueryInput query,
            float[] vector) {
        return new SearchV3AtomicChildDenseSelector.QueryVector(
                query.queryId(), query.queryTextSha256(), "candidate", vector,
                SearchV3AtomicChildDenseSelector.vectorSha256(vector), true);
    }

    private SearchV3ChildEmbeddingOperationStrategy.EmbeddingResult embeddings(
            List<String> texts,
            AtomicInteger calls) {
        calls.incrementAndGet();
        List<float[]> values = new ArrayList<>();
        texts.forEach(text -> values.add(vector(text)));
        return new SearchV3ChildEmbeddingOperationStrategy.EmbeddingResult(
                values, Math.max(1, texts.size()) * 1_000_000L);
    }

    private float[] vector(String text) {
        float[] value = new float[SearchV3AtomicChildDenseSelector.DIMENSIONS];
        int index = Math.floorMod(text.hashCode(), value.length);
        value[index] = 1.0f;
        return value;
    }

    private SearchV3AtomicChildDenseSelector.VerifiedPrediction verifiedPrediction(
            SearchV3AtomicChildDenseSelector.VerifiedSelectorInput input,
            List<SearchV3AtomicChildDenseSelector.QueryPrediction> predictions) {
        SearchV3AtomicChildDenseSelector.CostObservation cost =
                new SearchV3AtomicChildDenseSelector.CostObservation(
                        160, 3, 1, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 3L * 4096L);
        SearchV3AtomicChildDenseSelector.Prediction prediction =
                new SearchV3AtomicChildDenseSelector.Prediction(
                        1, "TEST", "CHILD_DENSE_V1", "policy",
                        input.frozen().canonicalSha256(),
                        new SearchV3AtomicChildDenseSelector.ModelIdentity(
                                "bge-m3:latest", "digest", 1024, "COSINE"),
                        true, "TEST", predictions.size(), 3, cost, predictions);
        SearchV3AtomicChildDenseSelector.FrozenPrediction frozen =
                new SearchV3AtomicChildDenseSelector.FrozenPrediction(prediction, "prediction", 1);
        return new SearchV3AtomicChildDenseSelector.VerifiedPrediction(frozen, "file", 1L);
    }

    private record Fixture(
            SearchV3AtomicChildDenseSelector.VerifiedSelectorInput input,
            SearchV3ChildEmbeddingOperationStrategy.CorpusInventory inventory,
            Map<String, SearchV3AtomicChildDenseSelector.QueryVector> queryVectors) {
    }
}
