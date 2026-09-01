package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SearchV3AtomicChildDenseSelectorTest {

    private static final String QUERY_ID = "Q1";
    private static final String OWNER = "U1";
    private static final String PARENT = "P1";
    private static final String PASSAGE = "RP1";

    private final SearchV3AtomicChildDenseSelector selector =
            new SearchV3AtomicChildDenseSelector();

    @Test
    void queryVectorRetainsOriginalArrayAndPredictDetectsMutationAfterTokenization() {
        float[] shared = axisVector(0);
        SearchV3AtomicChildDenseSelector.QueryVector token = queryVector(shared);
        SearchV3AtomicChildDenseSelector.VerifiedSelectorInput input = verifiedInput(List.of(
                child("E1", 0, 0, "source one")));

        assertThat(token.vector()).isSameAs(shared);
        assertThat(token.sha256()).isEqualTo(SearchV3AtomicChildDenseSelector.vectorSha256(shared));

        shared[0] = 0.0f;
        shared[1] = 1.0f;

        assertThat(SearchV3AtomicChildDenseSelector.vectorSha256(shared)).isNotEqualTo(token.sha256());
        assertThatThrownBy(() -> selector.predict(
                        input,
                        model(),
                        Map.of(QUERY_ID, token),
                        Map.of("E1", axisVector(0)),
                        cost(1)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("shared query vector changed after B3 ranking");
    }

    @Test
    void predictSortsByCosineOnlyInsidePassageAndUsesSourceOrdinalForStableTie() {
        SearchV3AtomicChildDenseSelector.SelectorChildInput low =
                child("LOW", 0, 0, "low score");
        SearchV3AtomicChildDenseSelector.SelectorChildInput zTie =
                child("Z-TIE", 1, 10, "first tie");
        SearchV3AtomicChildDenseSelector.SelectorChildInput aTie =
                child("A-TIE", 2, 20, "second tie");
        SearchV3AtomicChildDenseSelector.VerifiedSelectorInput input =
                verifiedInput(List.of(low, zTie, aTie));
        float[] shared = axisVector(0);

        Map<String, float[]> childVectors = new LinkedHashMap<>();
        childVectors.put("LOW", axisVector(1));
        childVectors.put("Z-TIE", axisVector(0));
        childVectors.put("A-TIE", axisVector(0));
        SearchV3AtomicChildDenseSelector.FrozenPrediction frozen = selector.predict(
                input,
                model(),
                Map.of(QUERY_ID, queryVector(shared)),
                childVectors,
                cost(3));

        SearchV3AtomicChildDenseSelector.PassagePrediction passage =
                frozen.prediction().queries().get(0).passages().get(0);
        assertThat(passage.rank()).isEqualTo(1);
        assertThat(passage.passageId()).isEqualTo(PASSAGE);
        assertThat(passage.parentId()).isEqualTo(PARENT);
        assertThat(passage.passageCosineScore()).isEqualTo(0.91d);
        assertThat(passage.children())
                .extracting(SearchV3AtomicChildDenseSelector.ScoredChild::evidenceChildId)
                .containsExactly("Z-TIE", "A-TIE", "LOW");
        assertThat(passage.children())
                .extracting(SearchV3AtomicChildDenseSelector.ScoredChild::cosineScore)
                .containsExactly(1.0d, 1.0d, 0.0d);
        assertThat(frozen.prediction().queryVectorSharedWithB3()).isTrue();
        assertThat(frozen.prediction().queries().get(0).queryVectorSha256())
                .isEqualTo(SearchV3AtomicChildDenseSelector.vectorSha256(shared));
        assertThat(frozen.prediction().cost().selectorP50Ms()).isGreaterThanOrEqualTo(0.0d);
        assertThat(frozen.prediction().cost().selectorP95Ms())
                .isEqualTo(frozen.prediction().cost().selectorP50Ms());
    }

    @Test
    void selectorPassageInputRejectsChildFromAnotherParent() {
        SearchV3AtomicChildDenseSelector.SelectorChildInput crossed = newChild(
                "E1", "P2", 0, 0, "crossed parent");

        assertThatThrownBy(() -> new SearchV3AtomicChildDenseSelector.SelectorPassageInput(
                        1, PASSAGE, 0.91d, PARENT, List.of(crossed)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("crossed parent");
    }

    @Test
    void phaseGuardAllowsGoldOnlyAfterPredictionOutputVerification() {
        SearchV3AtomicChildDenseSelector.PhaseGuard guard =
                new SearchV3AtomicChildDenseSelector.PhaseGuard();

        assertThatThrownBy(() -> guard.joinGold("artifact", "output", (artifact, output) -> "gold"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected OUTPUT_VERIFIED");
        assertThat(guard.phase()).isEqualTo(SearchV3AtomicChildDenseSelector.Phase.SOURCE_ONLY);

        guard.verifyArtifact(() -> "artifact");
        guard.freezeInput(() -> "frozen input");
        guard.verifyInput(() -> "verified input");
        guard.verifyModel(() -> "verified model");
        guard.freezePrediction(() -> "frozen prediction");

        assertThatThrownBy(() -> guard.joinGold("artifact", "output", (artifact, output) -> "gold"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected OUTPUT_VERIFIED");
        assertThat(guard.phase()).isEqualTo(SearchV3AtomicChildDenseSelector.Phase.PREDICTION_FROZEN);

        guard.verifyOutput(() -> "verified output");
        String gold = guard.joinGold("artifact", "output", (artifact, output) -> "gold");
        assertThat(gold).isEqualTo("gold");
        assertThat(guard.phase()).isEqualTo(SearchV3AtomicChildDenseSelector.Phase.GOLD_JOINED);
        assertThatThrownBy(() -> guard.joinOracle(() -> "oracle-too-early"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected COMPARISON_EVALUATED");
        String comparison = guard.evaluateComparison(() -> "comparison");
        assertThat(comparison).isEqualTo("comparison");
        assertThat(guard.phase())
                .isEqualTo(SearchV3AtomicChildDenseSelector.Phase.COMPARISON_EVALUATED);
        String oracle = guard.joinOracle(() -> "oracle");
        assertThat(oracle).isEqualTo("oracle");
        assertThat(guard.phase()).isEqualTo(SearchV3AtomicChildDenseSelector.Phase.ORACLE_JOINED);
        String evaluation = guard.finalizeEvaluation(() -> "evaluation");
        assertThat(evaluation).isEqualTo("evaluation");
        assertThat(guard.phase()).isEqualTo(SearchV3AtomicChildDenseSelector.Phase.EVALUATED);
    }

    @Test
    void artifactWritesRejectSealedAndOutsideLocalPathsBeforeFileIo() {
        SearchV3AtomicChildDenseSelector.FrozenSelectorInput frozen =
                verifiedInput(List.of(child("E1", 0, 0, "source one"))).frozen();

        assertThatThrownBy(() -> selector.writeInputCreateNew(
                        Path.of("local/search-v3-evaluation/prz034/sealed/attempt.json"), frozen))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside approved local scope");
        assertThatThrownBy(() -> selector.writeInputCreateNew(
                        Path.of("local/search-v3-evaluation/prz033/attempt.json"), frozen))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside approved local scope");
    }

    @Test
    void policyFreezesSourceTextOnlyTopFiveSamePassageAndNoGold() {
        assertThat(SearchV3AtomicChildDenseSelector.POLICY_VERSION).isEqualTo("CHILD_DENSE_V1");
        assertThat(SearchV3AtomicChildDenseSelector.TOP_PASSAGE_K).isEqualTo(5);
        assertThat(SearchV3AtomicChildDenseSelector.POLICY_CANONICAL.lines().toList())
                .contains(
                        "passageK=5",
                        "childInput=EvidenceChild.sourceText",
                        "samePassageOnly=true",
                        "goldAvailable=false")
                .doesNotContain(
                        "childInput=EvidenceChild.retrievalText",
                        "childInput=headingContext",
                        "goldAvailable=true");
        assertThat(SearchV3AtomicChildDenseSelector.POLICY_SHA256)
                .isEqualTo(SearchV3AtomicChildDenseSelector.sha256(
                        SearchV3AtomicChildDenseSelector.POLICY_CANONICAL));
    }

    private SearchV3AtomicChildDenseSelector.VerifiedSelectorInput verifiedInput(
            List<SearchV3AtomicChildDenseSelector.SelectorChildInput> children) {
        SearchV3AtomicChildDenseSelector.SelectorPassageInput passage =
                new SearchV3AtomicChildDenseSelector.SelectorPassageInput(
                        1, PASSAGE, 0.91d, PARENT, children);
        SearchV3AtomicChildDenseSelector.SelectorQueryInput query =
                new SearchV3AtomicChildDenseSelector.SelectorQueryInput(
                        QUERY_ID,
                        OWNER,
                        "query",
                        SearchV3AtomicChildDenseSelector.sha256("query"),
                        List.of(passage));
        List<SearchV3AtomicChildDenseSelector.UniqueChildInput> unique = children.stream()
                .map(value -> new SearchV3AtomicChildDenseSelector.UniqueChildInput(
                        value.evidenceChildId(), value.sourceText(), value.sourceTextSha256()))
                .toList();
        SearchV3AtomicChildDenseSelector.SelectorInput input =
                new SearchV3AtomicChildDenseSelector.SelectorInput(
                        SearchV3AtomicChildDenseSelector.SCHEMA_VERSION,
                        SearchV3AtomicChildDenseSelector.INPUT_ARTIFACT,
                        SearchV3AtomicChildDenseSelector.POLICY_VERSION,
                        SearchV3AtomicChildDenseSelector.POLICY_SHA256,
                        SearchV3AtomicChildDenseSelector.TOP_PASSAGE_K,
                        SearchV3AtomicChildDenseSelector.RESULT_LIMIT,
                        "prz032",
                        "prz033",
                        "runtime",
                        SearchV3AtomicChildDenseSelector.EXPECTED_BGE_DIGEST,
                        List.of(query),
                        unique,
                        1,
                        children.size());
        SearchV3AtomicChildDenseSelector.FrozenSelectorInput frozen =
                new SearchV3AtomicChildDenseSelector.FrozenSelectorInput(input, "input-sha", 1);
        return new SearchV3AtomicChildDenseSelector.VerifiedSelectorInput(
                frozen, "input-file-sha", 1L);
    }

    private SearchV3AtomicChildDenseSelector.SelectorChildInput child(
            String id,
            int ordinal,
            int start,
            String text) {
        return newChild(id, PARENT, ordinal, start, text);
    }

    private SearchV3AtomicChildDenseSelector.SelectorChildInput newChild(
            String id,
            String parent,
            int ordinal,
            int start,
            String text) {
        String sourceHash = SearchV3AtomicChildDenseSelector.sha256(text);
        ProductionV2ShadowAdapter.SourceSpan span = new ProductionV2ShadowAdapter.SourceSpan(
                OWNER,
                "D1",
                "V1",
                "fixture.txt",
                null,
                start,
                start + text.codePointCount(0, text.length()),
                text,
                sourceHash);
        return new SearchV3AtomicChildDenseSelector.SelectorChildInput(
                id, parent, ordinal, text, sourceHash, span);
    }

    private SearchV3AtomicChildDenseSelector.ModelIdentity model() {
        return new SearchV3AtomicChildDenseSelector.ModelIdentity(
                "bge-m3:latest",
                SearchV3AtomicChildDenseSelector.EXPECTED_BGE_DIGEST,
                SearchV3AtomicChildDenseSelector.DIMENSIONS,
                "COSINE");
    }

    private SearchV3AtomicChildDenseSelector.QueryVector queryVector(float[] vector) {
        return new SearchV3AtomicChildDenseSelector.QueryVector(
                QUERY_ID,
                SearchV3AtomicChildDenseSelector.sha256("query"),
                "verified-b3-candidate-identity",
                vector,
                SearchV3AtomicChildDenseSelector.vectorSha256(vector),
                true);
    }

    private SearchV3AtomicChildDenseSelector.EmbeddingCostObservation cost(int childCount) {
        return new SearchV3AtomicChildDenseSelector.EmbeddingCostObservation(
                160,
                childCount,
                1,
                0.0d,
                0.0d,
                0.0d,
                (long) childCount * SearchV3AtomicChildDenseSelector.DIMENSIONS * Float.BYTES);
    }

    private float[] axisVector(int axis) {
        float[] vector = new float[SearchV3AtomicChildDenseSelector.DIMENSIONS];
        vector[axis] = 1.0f;
        return vector;
    }
}
