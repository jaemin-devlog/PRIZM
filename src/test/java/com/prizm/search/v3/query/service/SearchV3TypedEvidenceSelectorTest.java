package com.prizm.search.v3.query.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.v3.query.model.SearchV3EvidenceChildCandidate;
import com.prizm.search.v3.query.model.SearchV3PassageCandidate;
import com.prizm.search.v3.query.model.SearchV3TypedEvidenceState;
import com.prizm.search.v3.query.service.SearchV3TypedEvidenceSelector.RankedChild;
import com.prizm.search.v3.query.service.SearchV3TypedEvidenceSelector.RankedPassage;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchV3TypedEvidenceSelectorTest {

    private final SearchV3TypedEvidenceSelector selector = new SearchV3TypedEvidenceSelector();

    @Test
    void semanticQueryKeepsChildDenseOverlayOrderAndRemainsUnassessed() {
        var passage = passage(1, 11);
        var first = child(101, 11, 1, "복합 인덱스를 적용해 조회 시간을 줄였다.");
        var second = child(102, 11, 0, "PostgreSQL을 사용했다.");

        var result = selector.select(
                "조회 성능을 개선한 경험",
                List.of(new RankedPassage(
                        passage,
                        List.of(new RankedChild(first, 0.91), new RankedChild(second, 0.72)))));

        assertThat(result.state()).isEqualTo(SearchV3TypedEvidenceState.UNASSESSED);
        assertThat(result.parsedConstraintCount()).isZero();
        assertThat(result.children()).extracting(value -> value.child().child().childId())
                .containsExactly(101L, 102L);
    }

    @Test
    void typedQuantitySelectsTheExactSourceGroundedContributor() {
        var passage = passage(1, 11);
        var wrong = child(101, 11, 0, "데이터 1,300건을 처리했다.");
        var direct = child(102, 11, 1, "사용자 1,300명이 서비스를 이용했다.");

        var result = selector.select(
                "사용자 1,000명 이상 경험",
                List.of(new RankedPassage(
                        passage,
                        List.of(new RankedChild(wrong, 0.92), new RankedChild(direct, 0.88)))));

        assertThat(result.state()).isEqualTo(SearchV3TypedEvidenceState.FOUND);
        assertThat(result.children()).extracting(value -> value.child().child().childId())
                .containsExactly(102L);
    }

    private static SearchV3PassageCandidate passage(int rank, long passageId) {
        return new SearchV3PassageCandidate(
                rank, passageId, 31, 41, 51, 61,
                "P-" + passageId, rank - 1, "parent-1", 0.1, 0.9);
    }

    private static SearchV3EvidenceChildCandidate child(
            long childId,
            long passageId,
            int order,
            String text) {
        return new SearchV3EvidenceChildCandidate(
                childId, passageId, 31, 41, 51, 61,
                "C-" + childId, order, order, "PARAGRAPH", text, "a".repeat(64),
                "fixture.txt", null, order + 1, order + 1, order * 100,
                order * 100 + text.codePointCount(0, text.length()),
                "block-" + childId, "parent-1", "b".repeat(64));
    }
}
