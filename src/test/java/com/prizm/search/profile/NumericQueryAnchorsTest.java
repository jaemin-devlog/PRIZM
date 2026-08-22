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

    @Test
    void requiresEveryContextualQueryNumberAtAnExactBoundary() {
        assertThat(NumericQueryAnchors.hasAllContextualMatches(
                        "응답 시간이 340ms였나요?", "응답 시간이 340 ms였다."))
                .isTrue();
        assertThat(NumericQueryAnchors.hasAllContextualMatches(
                        "P95가 1.4초에서 340ms로 줄었나요?", "P95는 1.4초에서 340밀리초로 줄었다."))
                .isTrue();
        assertThat(NumericQueryAnchors.hasAllContextualMatches(
                        "응답 시간이 340ms였나요?", "응답 시간이 380밀리초였다."))
                .isFalse();
        assertThat(NumericQueryAnchors.hasAllContextualMatches(
                        "응답 시간이 340ms였나요?", "응답 시간이 1,340ms였다."))
                .isFalse();
    }
}
