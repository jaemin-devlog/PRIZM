package com.prizm.search.v3.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

import com.prizm.search.v3.query.model.SearchV3EvidenceChildCandidate;
import com.prizm.search.v3.query.model.SearchV3PassageCandidate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchV3Top2DocumentRankerTest {

    private final SearchV3Top2DocumentRanker ranker = new SearchV3Top2DocumentRanker();

    @Test
    void keepsTheOnlyPassageScore() {
        var passage = passage(1, 11, 51, 0.83);

        var result = ranker.rank(List.of(passage), List.of(child(101, 11, 51, 0, 10)));

        assertThat(result).singleElement().satisfies(document -> {
            assertThat(document.documentId()).isEqualTo(51);
            assertThat(document.score()).isEqualTo(0.83);
            assertThat(document.passageCount()).isEqualTo(1);
        });
    }

    @Test
    void averagesTheTopTwoNonDuplicatePassages() {
        var first = passage(1, 11, 51, 0.90);
        var second = passage(2, 12, 51, 0.70);

        var result = ranker.rank(
                List.of(first, second),
                List.of(child(101, 11, 51, 0, 10), child(102, 12, 51, 20, 30)));

        assertThat(result).singleElement().satisfies(document -> {
            assertThat(document.score()).isEqualTo(0.80);
            assertThat(document.passageCount()).isEqualTo(2);
        });
    }

    @Test
    void countsNeitherTheSameChildNorTheSameSourceSpanTwice() {
        var first = passage(1, 11, 51, 0.90);
        var sameChild = passage(2, 12, 51, 0.20);
        var sameSpan = passage(3, 13, 51, 0.30);
        var distinct = passage(4, 14, 51, 0.80);

        var result = ranker.rank(
                List.of(first, sameChild, sameSpan, distinct),
                List.of(
                        child(101, 11, 51, 0, 10),
                        child(101, 12, 51, 20, 30),
                        child(103, 13, 51, 0, 10),
                        child(104, 14, 51, 40, 50)));

        assertThat(result).singleElement().satisfies(document -> {
            assertThat(document.score()).isCloseTo(0.85, offset(1.0e-12));
            assertThat(document.passageCount()).isEqualTo(2);
        });
    }

    @Test
    void appliesTheSameAverageToOneStrongAndOneWeakPassage() {
        var strong = passage(1, 11, 51, 0.95);
        var weak = passage(2, 12, 51, 0.15);

        var result = ranker.rank(
                List.of(strong, weak),
                List.of(child(101, 11, 51, 0, 10), child(102, 12, 51, 20, 30)));

        assertThat(result).singleElement().satisfies(document ->
                assertThat(document.score()).isCloseTo(0.55, offset(1.0e-12)));
    }

    @Test
    void ordersDocumentsByTheirAggregatedScore() {
        var firstDocumentOne = passage(1, 11, 51, 0.95);
        var firstDocumentTwo = passage(2, 12, 52, 0.90);
        var secondDocumentTwo = passage(3, 13, 52, 0.88);
        var secondDocumentOne = passage(4, 14, 51, 0.10);

        var result = ranker.rank(
                List.of(firstDocumentOne, firstDocumentTwo, secondDocumentTwo, secondDocumentOne),
                List.of(
                        child(101, 11, 51, 0, 10),
                        child(102, 12, 52, 0, 10),
                        child(103, 13, 52, 20, 30),
                        child(104, 14, 51, 20, 30)));

        assertThat(result).extracting(SearchV3Top2DocumentRanker.RankedDocument::documentId)
                .containsExactly(52L, 51L);
    }

    @Test
    void preservesTheBestExistingPassageOrderWhenScoresTie() {
        var earlier = passage(1, 11, 52, 0.80);
        var later = passage(2, 12, 51, 0.80);

        var result = ranker.rank(
                List.of(later, earlier),
                List.of(child(101, 11, 52, 0, 10), child(102, 12, 51, 0, 10)));

        assertThat(result).extracting(SearchV3Top2DocumentRanker.RankedDocument::documentId)
                .containsExactly(52L, 51L);
    }

    private static SearchV3PassageCandidate passage(
            int rank, long passageId, long documentId, double score) {
        return new SearchV3PassageCandidate(
                rank, passageId, 31, 41, documentId, 61, "P-" + passageId, rank - 1,
                "parent-" + passageId, 1 - score, score);
    }

    private static SearchV3EvidenceChildCandidate child(
            long childId,
            long passageId,
            long documentId,
            int start,
            int end) {
        return new SearchV3EvidenceChildCandidate(
                childId, passageId, 31, 41, documentId, 61, "C-" + childId, 0, 0,
                "PARAGRAPH", "source-" + childId, "a".repeat(64), "fixture.txt", null,
                1, 1, start, end, "block-" + childId, "parent-" + passageId, "b".repeat(64));
    }
}
