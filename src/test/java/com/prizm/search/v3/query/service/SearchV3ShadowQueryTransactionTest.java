package com.prizm.search.v3.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.prizm.search.v3.indexing.model.SearchV3EmbeddingModelContract;
import com.prizm.search.v3.query.model.SearchV3EvidenceChildCandidate;
import com.prizm.search.v3.query.model.SearchV3PassageCandidate;
import com.prizm.search.v3.query.repository.SearchV3ShadowQueryRepository;
import com.prizm.search.v3.query.repository.SearchV3ShadowQueryRepository.ChildScore;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchV3ShadowQueryTransactionTest {

    @Mock
    SearchV3ShadowQueryRepository repository;

    SearchV3ShadowQueryTransaction transaction;
    SearchV3EmbeddingModelContract model;
    float[] queryVector;

    @BeforeEach
    void setUp() {
        transaction = new SearchV3ShadowQueryTransaction(repository, new SearchV3TypedEvidenceSelector());
        model = new SearchV3EmbeddingModelContract("bge-m3", "a".repeat(64), 1024);
        queryVector = new float[1024];
        queryVector[0] = 1.0f;
    }

    @Test
    void reordersOnlyChildrenInsideEachPassageAndKeepsPassageRank() {
        var p1 = passage(1, 11);
        var p2 = passage(2, 12);
        var p1SourceFirst = child(101, 11, 0, "첫 번째 세부 설명");
        var p1DenseFirst = child(102, 11, 1, "장애 재발을 줄인 직접 근거");
        var p2Child = child(103, 12, 0, "다른 경험의 근거");
        when(repository.findPassages(41, queryVector, model)).thenReturn(List.of(p1, p2));
        when(repository.findChildren(41, List.of(p1, p2)))
                .thenReturn(List.of(p1SourceFirst, p1DenseFirst, p2Child));
        when(repository.scoreChildren(41, List.of(p1, p2), queryVector, model))
                .thenReturn(List.of(
                        new ChildScore(102, 11, 0.05, 0.95),
                        new ChildScore(101, 11, 0.30, 0.70),
                        new ChildScore(103, 12, 0.01, 0.99)));

        var result = transaction.search(41, "장애 재발 방지 경험", queryVector, model);

        assertThat(result.evidence()).extracting(value -> value.evidenceChildId())
                .containsExactly(102L, 101L, 103L);
        assertThat(result.evidence()).extracting(value -> value.passageRank())
                .containsExactly(1, 1, 2);
    }

    @Test
    void rejectsChildWhoseParentLineageDoesNotMatchItsPassage() {
        var passage = passage(1, 11);
        var invalid = new SearchV3EvidenceChildCandidate(
                101, 11, 31, 41, 51, 61, "C-101", 0, 0,
                "PARAGRAPH", "다른 구조 경계의 근거", "a".repeat(64), "fixture.txt", null,
                1, 1, 0, 12, "block-101", "different-parent", "b".repeat(64));
        when(repository.findPassages(41, queryVector, model)).thenReturn(List.of(passage));
        when(repository.findChildren(41, List.of(passage))).thenReturn(List.of(invalid));
        when(repository.scoreChildren(41, List.of(passage), queryVector, model))
                .thenReturn(List.of(new ChildScore(101, 11, 0.1, 0.9)));

        assertThatThrownBy(() -> transaction.search(41, "근거", queryVector, model))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("lineage boundary");
    }

    @Test
    void rejectsMissingTopFiveChildVector() {
        var passage = passage(1, 11);
        var child = child(101, 11, 0, "직접 근거");
        when(repository.findPassages(41, queryVector, model)).thenReturn(List.of(passage));
        when(repository.findChildren(41, List.of(passage))).thenReturn(List.of(child));
        when(repository.scoreChildren(41, List.of(passage), queryVector, model)).thenReturn(List.of());

        assertThatThrownBy(() -> transaction.search(41, "근거", queryVector, model))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inventory is incomplete");
    }

    private static SearchV3PassageCandidate passage(int rank, long id) {
        return new SearchV3PassageCandidate(
                rank, id, 31, 41, 51, 61, "P-" + id, rank - 1,
                "parent-" + id, 0.1 * rank, 1 - 0.1 * rank);
    }

    private static SearchV3EvidenceChildCandidate child(long id, long passage, int order, String text) {
        return new SearchV3EvidenceChildCandidate(
                id, passage, 31, 41, 51, 61, "C-" + id, order, order,
                "PARAGRAPH", text, "a".repeat(64), "fixture.txt", null,
                order + 1, order + 1, order * 100,
                order * 100 + text.codePointCount(0, text.length()),
                "block-" + id, "parent-" + passage, "b".repeat(64));
    }
}
