package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class Prz028TypedConstraintVerdictPolicyTest {

    @Test
    void hardGateFailureIsAlwaysNoGo() {
        assertThat(Prz028TypedConstraintBenchmarkTest.decide(
                List.of("SEMANTIC_EXACT_ORDER_PARITY"),
                true, 4, 0, 3, 3, true, true))
                .isEqualTo("NO_GO");
    }

    @Test
    void promisingRequiresAllPreRegisteredQualityAndGeneralizationEvidence() {
        assertThat(Prz028TypedConstraintBenchmarkTest.decide(
                List.of(), true, 2, 0, 2, 2, true, true))
                .isEqualTo("PROMISING");
        assertThat(Prz028TypedConstraintBenchmarkTest.decide(
                List.of(), true, 2, 0, 1, 2, true, true))
                .isEqualTo("NEEDS_ADJUSTMENT");
        assertThat(Prz028TypedConstraintBenchmarkTest.decide(
                List.of(), true, 2, 0, 2, 2, true, false))
                .isEqualTo("NEEDS_ADJUSTMENT");
    }

    @Test
    void noMeasuredRankOrGoldExpectedHardNegativeBenefitIsNoGo() {
        assertThat(Prz028TypedConstraintBenchmarkTest.decide(
                List.of(), false, 0, 0, 0, 0, false, false))
                .isEqualTo("NO_GO");
    }
}
