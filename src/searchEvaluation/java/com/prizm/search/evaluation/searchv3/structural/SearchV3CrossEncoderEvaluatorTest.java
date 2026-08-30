package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.ScorePair;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.ScoreQuestion;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchV3CrossEncoderEvaluatorTest {

    private final SearchV3CrossEncoderEvaluator evaluator = new SearchV3CrossEncoderEvaluator();

    @Test
    void reranksOnlyScoredPrefixWithoutAddingDeletingOrMutatingB3Payload() {
        List<SearchV3DenseAblationEngine.RankedCandidate> dense = List.of(
                candidate(1, "p1", "source one", "child-1"),
                candidate(2, "p2", "source two", "child-2"),
                candidate(3, "p3", "source three", "child-3"));
        ScoreQuestion scores = new ScoreQuestion(
                "dataset", "DEV", "query", "q".repeat(64), 2, 1.0d,
                List.of(score("pair-2", "p2", 2, 1, 0.9d), score("pair-1", "p1", 1, 2, 0.8d)));

        List<SearchV3DenseAblationEngine.RankedCandidate> reranked = evaluator.rerank(dense, scores);

        assertThat(reranked).extracting(SearchV3DenseAblationEngine.RankedCandidate::candidateId)
                .containsExactly("p2", "p1", "p3");
        assertThat(reranked).extracting(SearchV3DenseAblationEngine.RankedCandidate::rank)
                .containsExactly(1, 2, 3);
        assertThat(reranked).extracting(SearchV3DenseAblationEngine.RankedCandidate::sourceText)
                .containsExactly("source two", "source one", "source three");
        assertThat(reranked).extracting(SearchV3DenseAblationEngine.RankedCandidate::evidenceChildIds)
                .containsExactly(List.of("child-2"), List.of("child-1"), List.of("child-3"));
        assertThat(reranked.get(0).documentId()).isEqualTo(dense.get(1).documentId());
        assertThat(reranked.get(0).versionId()).isEqualTo(dense.get(1).versionId());
        assertThat(reranked.get(0).parentAnnotationCandidateId())
                .isEqualTo(dense.get(1).parentAnnotationCandidateId());
        assertThat(reranked.get(0).contextSourceBlockIds())
                .isEqualTo(dense.get(1).contextSourceBlockIds());
    }

    @Test
    void rejectsUnknownCandidateAndDenseRankMutation() {
        List<SearchV3DenseAblationEngine.RankedCandidate> dense = List.of(
                candidate(1, "p1", "source one", "child-1"),
                candidate(2, "p2", "source two", "child-2"));
        ScoreQuestion unknown = new ScoreQuestion(
                "dataset", "DEV", "query", "q".repeat(64), 1, 1.0d,
                List.of(score("pair-x", "unknown", 1, 1, 0.9d)));
        ScoreQuestion moved = new ScoreQuestion(
                "dataset", "DEV", "query", "q".repeat(64), 1, 1.0d,
                List.of(score("pair-1", "p1", 2, 1, 0.9d)));

        assertThatThrownBy(() -> evaluator.rerank(dense, unknown))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> evaluator.rerank(dense, moved))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SearchV3DenseAblationEngine.RankedCandidate candidate(
            int rank,
            String id,
            String source,
            String childId) {
        return new SearchV3DenseAblationEngine.RankedCandidate(
                rank, id, 1.0d / rank, "document", "version", "RETRIEVAL_PASSAGE", source, "", source,
                "parent", source.codePointCount(0, source.length()), List.of(childId), List.of("block"),
                List.of("unit-" + id), List.of("group-" + id), List.of("parent"));
    }

    private ScorePair score(String pairId, String candidateId, int denseRank, int rerankRank, double score) {
        return new ScorePair(
                pairId, candidateId, denseRank, rerankRank, "q".repeat(64), "s".repeat(64), score);
    }
}
