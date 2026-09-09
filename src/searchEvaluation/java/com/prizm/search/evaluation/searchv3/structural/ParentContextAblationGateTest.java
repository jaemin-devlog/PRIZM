package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ParentContextAblationGateTest {

    private final ParentContextAblationGate gate = new ParentContextAblationGate();

    @Test
    void returnsPromisingOnlyForInvariantSafeNonRegressingMeaningfulGain() {
        assertThat(gate.decide(true, true, true)).isEqualTo("PROMISING");
    }

    @Test
    void returnsNoGoForSourceOrBoundaryInvariantFailure() {
        assertThat(gate.decide(false, true, true)).isEqualTo("NO_GO");
    }

    @Test
    void returnsNeedsAdjustmentForRankingOrSliceRegression() {
        assertThat(gate.decide(true, false, true)).isEqualTo("NEEDS_ADJUSTMENT");
    }

    @Test
    void returnsNoGoWhenContextHasNoMeaningfulGain() {
        assertThat(gate.decide(true, true, false)).isEqualTo("NO_GO");
    }
}
