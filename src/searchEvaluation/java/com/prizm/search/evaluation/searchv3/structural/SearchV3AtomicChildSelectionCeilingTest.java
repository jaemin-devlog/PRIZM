package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SearchV3AtomicChildSelectionCeilingTest {

    private final SearchV3AtomicChildSelectionCeiling ceiling =
            new SearchV3AtomicChildSelectionCeiling();

    @Test
    void stablePartitionsOnlyWithinOnePassageAndPreservesOtherOrder() {
        SearchV3AtomicChildSelectionCeiling.ChildInput conflict = child("E1", 0, 8, "conflict");
        SearchV3AtomicChildSelectionCeiling.ChildInput related = child("E2", 8, 15, "related");
        SearchV3AtomicChildSelectionCeiling.ChildInput unjudged = child("E3", 15, 23, "unjudged");
        SearchV3AtomicChildSelectionCeiling.ChildInput direct = child("E4", 23, 29, "direct");
        SearchV3AtomicChildSelectionCeiling.PassageCandidateInput passage = passage(
                List.of(conflict, related, unjudged, direct));

        Map<String, SearchV3MinimalShadowGold.GoldUnit> units = new LinkedHashMap<>();
        units.put("G-CONFLICT", unit("G-CONFLICT", 0, 8));
        units.put("G-RELATED", unit("G-RELATED", 8, 15));
        units.put("G-DIRECT", unit("G-DIRECT", 23, 29));
        SearchV3MinimalShadowGold.GoldSnapshot gold = new SearchV3MinimalShadowGold.GoldSnapshot(
                Map.of(), Map.copyOf(units), Map.of("P1", parent("P1", "D1", 0, 29)), Map.of());
        SearchV3MinimalShadowGold.GoldQuery query = query(Map.of(
                "G-CONFLICT", "CONTRADICTS",
                "G-RELATED", "RELATED",
                "G-DIRECT", "DIRECT_SUPPORT"));

        assertThat(ceiling.stableLocalPartition(passage, query, gold))
                .extracting(SearchV3AtomicChildSelectionCeiling.ChildInput::evidenceChildId)
                .containsExactly("E4", "E2", "E1", "E3");
    }

    @Test
    void rejectsGoldRelationWhoseAnnotationParentDoesNotContainItsUnit() {
        SearchV3AtomicChildSelectionCeiling.ChildInput unrelated = child("E0", 6, 11, "other");
        SearchV3AtomicChildSelectionCeiling.ChildInput child = child("E1", 0, 6, "direct");
        SearchV3AtomicChildSelectionCeiling.PassageCandidateInput passage =
                passage(List.of(child, unrelated));
        SearchV3MinimalShadowGold.GoldUnit wrongParent = new SearchV3MinimalShadowGold.GoldUnit(
                "G1", "U1", "P2", "GROUP", "D1", "V1",
                List.of(new SearchV3MinimalShadowGold.GoldSpan("D1", "V1", null, 0, 6, null)));
        SearchV3MinimalShadowGold.GoldSnapshot gold = new SearchV3MinimalShadowGold.GoldSnapshot(
                Map.of(), Map.of("G1", wrongParent),
                Map.of("P2", parent("P2", "D2", 0, 6)), Map.of());

        assertThatThrownBy(() -> ceiling.stableLocalPartition(
                passage, query(Map.of("G1", "DIRECT_SUPPORT")), gold))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("source-grounded parent scope");
    }

    @Test
    void rejectsChildWhoseParentDiffersFromPassage() {
        SearchV3AtomicChildSelectionCeiling.ChildInput child = new SearchV3AtomicChildSelectionCeiling.ChildInput(
                "E1", "P2", span(0, 6, "direct"));
        assertThatThrownBy(() -> passage(List.of(child)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("crossed passage parent");
    }

    @Test
    void goldCannotBeJoinedBeforeCandidateInputIsVerified() {
        SearchV3AtomicChildSelectionCeiling.PhaseGuard guard =
                new SearchV3AtomicChildSelectionCeiling.PhaseGuard();

        assertThatThrownBy(() -> guard.joinGold((artifact, candidate) -> "gold"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expected CANDIDATE_INPUT_VERIFIED");
        assertThat(guard.phase()).isEqualTo(SearchV3AtomicChildSelectionCeiling.Phase.SOURCE_ONLY);
    }

    @Test
    void phaseGuardRequiresArtifactFreezeVerificationGoldAndOracleOrder() {
        SearchV3AtomicChildSelectionCeiling.PhaseGuard guard =
                new SearchV3AtomicChildSelectionCeiling.PhaseGuard();
        SearchV3AtomicChildSelectionCeiling.VerifiedPrz032 artifact =
                new SearchV3AtomicChildSelectionCeiling.VerifiedPrz032(null, null, "report");
        SearchV3AtomicChildSelectionCeiling.CandidateInput input =
                new SearchV3AtomicChildSelectionCeiling.CandidateInput(
                        1, "type", "output", "runtime", "model", List.of());
        SearchV3AtomicChildSelectionCeiling.FrozenCandidateInput frozen =
                new SearchV3AtomicChildSelectionCeiling.FrozenCandidateInput(input, "candidate", 1);
        SearchV3AtomicChildSelectionCeiling.VerifiedCandidateInput verified =
                new SearchV3AtomicChildSelectionCeiling.VerifiedCandidateInput(frozen, "file", 1);
        SearchV3AtomicChildSelectionCeiling.CeilingEvaluation oracle =
                new SearchV3AtomicChildSelectionCeiling.CeilingEvaluation(
                        null, null, null, null, List.of(), Map.of(), "candidate", null,
                        SearchV3AtomicChildSelectionCeiling.Decision.CHILD_SELECTOR_NOT_JUSTIFIED);

        guard.verifyArtifact(() -> artifact);
        guard.freezeCandidate(() -> frozen);
        guard.verifyCandidate(ignored -> verified);
        String joinedGold = guard.joinGold((ignoredArtifact, ignoredCandidate) -> "gold");
        assertThat(joinedGold).isEqualTo("gold");
        assertThat(guard.oracle(ignored -> oracle)).isSameAs(oracle);
        assertThat(guard.phase()).isEqualTo(SearchV3AtomicChildSelectionCeiling.Phase.ORACLE_EVALUATED);
    }

    @Test
    void failureStageIsExclusiveWithMultiAspectPrecedence() {
        assertThat(ceiling.classifyFailureStage(
                true, true, SearchV3AtomicChildSelectionCeiling.PassageBand.TOP))
                .isEqualTo(SearchV3AtomicChildSelectionCeiling.FailureStage.MULTI_ASPECT_SELECTION_ERROR);
        assertThat(ceiling.classifyFailureStage(
                false, true, SearchV3AtomicChildSelectionCeiling.PassageBand.TOP))
                .isEqualTo(SearchV3AtomicChildSelectionCeiling.FailureStage.FINAL_ALREADY_CORRECT);
        assertThat(ceiling.classifyFailureStage(
                false, false, SearchV3AtomicChildSelectionCeiling.PassageBand.TOP))
                .isEqualTo(SearchV3AtomicChildSelectionCeiling.FailureStage.TOP_PASSAGE_CHILD_RECOVERABLE);
        assertThat(ceiling.classifyFailureStage(
                false, false, SearchV3AtomicChildSelectionCeiling.PassageBand.LOWER))
                .isEqualTo(SearchV3AtomicChildSelectionCeiling.FailureStage.LOWER_PASSAGE_RECOVERABLE);
        assertThat(ceiling.classifyFailureStage(
                false, false, SearchV3AtomicChildSelectionCeiling.PassageBand.DEEP))
                .isEqualTo(SearchV3AtomicChildSelectionCeiling.FailureStage.DEEP_PASSAGE_RECOVERABLE);
        assertThat(ceiling.classifyFailureStage(
                false, false, SearchV3AtomicChildSelectionCeiling.PassageBand.MISS))
                .isEqualTo(SearchV3AtomicChildSelectionCeiling.FailureStage.RETRIEVAL_MISS);
    }

    private SearchV3AtomicChildSelectionCeiling.PassageCandidateInput passage(
            List<SearchV3AtomicChildSelectionCeiling.ChildInput> children) {
        return new SearchV3AtomicChildSelectionCeiling.PassageCandidateInput(
                1, "RP1", 0.9d, "P1", children);
    }

    private SearchV3AtomicChildSelectionCeiling.ChildInput child(
            String id, int start, int end, String text) {
        return new SearchV3AtomicChildSelectionCeiling.ChildInput(id, "P1", span(start, end, text));
    }

    private ProductionV2ShadowAdapter.SourceSpan span(int start, int end, String text) {
        return new ProductionV2ShadowAdapter.SourceSpan(
                "U1", "D1", "V1", "fixture.txt", null, start, end, text,
                SearchV3MinimalShadowDataset.sha256(text));
    }

    private SearchV3MinimalShadowGold.GoldUnit unit(String id, int start, int end) {
        return new SearchV3MinimalShadowGold.GoldUnit(
                id, "U1", "P1", "GROUP-" + id, "D1", "V1",
                List.of(new SearchV3MinimalShadowGold.GoldSpan("D1", "V1", null, start, end, null)));
    }

    private SearchV3MinimalShadowGold.GoldParent parent(
            String id, String documentId, int start, int end) {
        return new SearchV3MinimalShadowGold.GoldParent(
                id, "U1", documentId, "V1",
                new SearchV3MinimalShadowGold.GoldSpan(
                        documentId, "V1", null, start, end, null));
    }

    private SearchV3MinimalShadowGold.GoldQuery query(Map<String, String> relations) {
        return new SearchV3MinimalShadowGold.GoldQuery(
                "TEST", "1", "DEV", "Q1", "U1", "query", "SUPPORTED", "EN", List.of(),
                new SearchV3MinimalShadowGold.AspectExpression("ALL", List.of(), 0),
                List.of(), Map.copyOf(relations), null);
    }
}
