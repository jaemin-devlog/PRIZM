package com.prizm.search.profile;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NumericQueryAnchorsTest {

    @Test
    void normalizesThousandsSeparatorsAndKeepsTheAdjacentUnit() {
        assertThat(NumericQueryAnchors.extract("2,329행 처리"))
                .containsExactly(new NumericQueryAnchors.NumericAnchor("2329", "행"));
    }

    @Test
    void matchesOnlyTheExactNumericBoundaryAndUnit() {
        assertThat(NumericQueryAnchors.hasContextualMatch(
                        "675건 갱신", "엑셀 업로드 2,329행 중 갱신 675건을 확인했다."))
                .isTrue();
        assertThat(NumericQueryAnchors.hasContextualMatch(
                        "675건 갱신", "엑셀 업로드에서 1,675건을 갱신했다."))
                .isFalse();
        assertThat(NumericQueryAnchors.hasContextualMatch(
                        "675건 갱신", "엑셀 업로드에서 6,750건을 갱신했다."))
                .isFalse();
    }

    @Test
    void doesNotEnableContextualRescueWithoutAnExplicitUnit() {
        assertThat(NumericQueryAnchors.hasContextualMatch(
                        "675 갱신", "엑셀 업로드에서 675건을 갱신했다."))
                .isFalse();
    }
}
