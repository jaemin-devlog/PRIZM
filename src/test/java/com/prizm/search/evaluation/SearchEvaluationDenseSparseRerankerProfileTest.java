package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.evaluation.SearchEvaluationDenseSparseRerankerProfile.RerankerCandidate;
import com.prizm.search.evaluation.SearchEvaluationDenseSparseRerankerProfile.RerankerOutcome;
import com.prizm.search.evaluation.SearchEvaluationDenseSparseRrfProfile.FusedCandidate;
import com.prizm.search.evaluation.SearchEvaluationDenseSparseRrfProfile.SparseCandidate;
import com.prizm.search.repository.VectorSearchResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SearchEvaluationDenseSparseRerankerProfileTest {

    private final SearchEvaluationDenseSparseRerankerProfile profile =
            new SearchEvaluationDenseSparseRerankerProfile();

    @Test
    void rerankerRecoversTheDenseRankOneCandidateFromP14EligibleRankNine() {
        VectorSearchResult answer = candidate(
                1L,
                1,
                "FOR UPDATE SKIP LOCKED를 적용해 동시 요청 처리를 구현했다.",
                0.505d);
        List<VectorSearchResult> dense = new ArrayList<>();
        List<SparseCandidate> sparse = new ArrayList<>();
        dense.add(answer);
        for (long id = 2L; id <= 9L; id++) {
            VectorSearchResult distractor = candidate(
                    id,
                    (int) id,
                    "generic concurrency evidence " + id,
                    0.70d - (id / 100.0d));
            dense.add(distractor);
            sparse.add(new SparseCandidate(distractor, 1.0d / id));
        }

        List<FusedCandidate> p14Eligible = eligible(dense, sparse);
        assertThat(p14Eligible).extracting(value -> value.candidate().chunkId())
                .endsWith(1L);

        RerankerOutcome outcome = profile.apply(
                "for update skip locked 사용 경험",
                dense,
                sparse,
                rerankerScores(p14Eligible, Map.of(1L, 10.0d)));

        assertThat(outcome.decision().results().get(0)).isSameAs(answer);
        assertThat(outcome.rerankedCandidates().get(0).p14Rank()).isEqualTo(9);
        assertThat(outcome.rerankedCandidates().get(0).rerankerRank()).isEqualTo(1);
        assertThat(outcome.decision().results().get(0).score()).isEqualTo(0.505d);
        assertThat(outcome.decision().results().get(0).distance()).isEqualTo(0.495d);
    }

    @Test
    void rerankerCannotIntroduceACandidateOutsideTheP14EligiblePool() {
        VectorSearchResult eligible = candidate(1L, 1, "Redis evidence", 0.80d);
        VectorSearchResult outsider = candidate(2L, 2, "Redis outsider", 0.40d);

        assertThatThrownBy(() -> profile.apply(
                "Redis",
                List.of(eligible),
                List.of(),
                List.of(
                        reranker(eligible, 1, 2, 0.1d),
                        reranker(outsider, 2, 1, 10.0d))))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessageContaining("exactly cover");
    }

    @Test
    void denseOnlyCandidateBelowPointFiveRemainsIneligibleEvenWithAHighRerankerScore() {
        VectorSearchResult eligible = candidate(1L, 1, "알림 evidence", 0.70d);
        VectorSearchResult belowFloor = candidate(2L, 2, "알림 below floor", 0.499d);
        List<FusedCandidate> p14Eligible = eligible(List.of(eligible, belowFloor), List.of());

        RerankerOutcome outcome = profile.apply(
                "알림 경험",
                List.of(eligible, belowFloor),
                List.of(),
                rerankerScores(p14Eligible, Map.of(2L, 100.0d)));

        assertThat(outcome.decision().results()).containsExactly(eligible);
    }

    @Test
    void sparseCandidateBelowPointFiveKeepsItsExistingP14Eligibility() {
        VectorSearchResult sparseMatch = candidate(1L, 1, "알림 evidence", 0.30d);
        List<SparseCandidate> sparse = List.of(new SparseCandidate(sparseMatch, 0.1d));
        List<FusedCandidate> p14Eligible = eligible(List.of(sparseMatch), sparse);

        RerankerOutcome outcome = profile.apply(
                "알림",
                List.of(sparseMatch),
                sparse,
                rerankerScores(p14Eligible, Map.of(1L, 5.0d)));

        assertThat(outcome.decision().results()).containsExactly(sparseMatch);
        assertThat(outcome.decision().results().get(0).score()).isEqualTo(0.30d);
    }

    @Test
    void overlappingSamePageDedupAndMaximumFiveRemainAfterReranking() {
        List<VectorSearchResult> dense = new ArrayList<>();
        String overlap = "같은 PDF 청크 경계에서 반복되는 Redis 합성 근거다. ".repeat(4);
        for (long id = 1L; id <= 7L; id++) {
            int page = id <= 2L ? 1 : (int) id;
            String content = id == 1L
                    ? "Redis evidence first. " + overlap
                    : id == 2L ? overlap + "Redis evidence second." : "Redis evidence " + id;
            dense.add(candidate(id, page, content, 0.90d - (id / 100.0d)));
        }
        List<FusedCandidate> p14Eligible = eligible(dense, List.of());

        RerankerOutcome outcome = profile.apply(
                "Redis",
                dense,
                List.of(),
                rerankerScores(p14Eligible, Map.of()));

        assertThat(outcome.decision().results()).hasSize(5);
        assertThat(outcome.decision().results().stream()
                        .filter(result -> result.sourceIndex() == 1)
                        .count())
                .isEqualTo(1L);
    }

    @Test
    void equalRerankerScoresPreserveTheExistingP14Order() {
        VectorSearchResult first = candidate(1L, 1, "Redis first", 0.90d);
        VectorSearchResult second = candidate(2L, 2, "Redis second", 0.80d);
        List<FusedCandidate> p14Eligible = eligible(List.of(first, second), List.of());

        RerankerOutcome outcome = profile.apply(
                "Redis",
                List.of(first, second),
                List.of(),
                rerankerScores(p14Eligible, Map.of(1L, 1.0d, 2L, 1.0d)));

        assertThat(outcome.rerankedCandidates())
                .extracting(value -> value.candidate().chunkId())
                .containsExactly(1L, 2L);
    }

    @Test
    void completedRetrievalDecisionIsExactlyTheUnrerankedP14Decision() {
        VectorSearchResult question = candidate(1L, 1, "PRIZM 서비스를 배포했나요?", 0.90d);
        VectorSearchResult negated = candidate(2L, 2, "PRIZM 서비스를 배포하지 않았습니다.", 0.90d);
        VectorSearchResult retracted = candidate(
                3L,
                3,
                "PRIZM 서비스를 배포했습니다. 이후 해당 내용을 정정했습니다.",
                0.90d);
        VectorSearchResult direct = candidate(4L, 4, "PRIZM 서비스를 배포했습니다.", 0.90d);
        List<VectorSearchResult> dense = List.of(question, negated, retracted, direct);
        List<SparseCandidate> sparse = dense.stream()
                .map(candidate -> new SparseCandidate(candidate, 1.0d))
                .toList();

        RerankerOutcome outcome = profile.apply(
                "PRIZM 서비스를 배포했나요?",
                dense,
                sparse,
                List.of());
        SearchEvaluationDenseSparseRrfProfile.Outcome p14 =
                new SearchEvaluationDenseSparseRrfProfile().apply(
                        "PRIZM 서비스를 배포했나요?",
                        dense,
                        sparse);

        assertThat(outcome.decision()).isEqualTo(p14.decision());
        assertThat(outcome.decision().results()).containsExactly(question, negated, retracted, direct);
        assertThat(outcome.rerankedCandidates()).isEmpty();
    }

    @Test
    void legacyUnsupportedCompletionGrammarNoLongerTriggersATruthRejection() {
        VectorSearchResult candidate = candidate(1L, 1, "주문 API를 배포했습니다.", 0.90d);

        RerankerOutcome outcome = profile.apply(
                "주문 API를 출시했습니까?",
                List.of(candidate),
                List.of(new SparseCandidate(candidate, 1.0d)),
                List.of());

        assertThat(outcome.decision().results()).containsExactly(candidate);
        assertThat(outcome.decision().rejectionReasons())
                .doesNotContain("UNSUPPORTED_COMPLETED_RELEASE_QUERY");
    }

    @Test
    void missingRerankerScoreFailsClosed() {
        VectorSearchResult first = candidate(1L, 1, "Redis first", 0.90d);
        VectorSearchResult second = candidate(2L, 2, "Redis second", 0.80d);

        assertThatThrownBy(() -> profile.apply(
                "Redis",
                List.of(first, second),
                List.of(),
                List.of(reranker(first, 1, 1, 1.0d))))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessageContaining("exactly cover");
    }

    private static List<FusedCandidate> eligible(
            List<VectorSearchResult> dense,
            List<SparseCandidate> sparse) {
        return SearchEvaluationDenseSparseRrfProfile.fuse(dense, sparse).stream()
                .filter(candidate -> candidate.candidate().score() >= 0.50d
                        || candidate.sparseRank() != null)
                .toList();
    }

    private static List<RerankerCandidate> rerankerScores(
            List<FusedCandidate> p14Eligible,
            Map<Long, Double> overrides) {
        List<Scored> scored = new ArrayList<>();
        for (int index = 0; index < p14Eligible.size(); index++) {
            VectorSearchResult candidate = p14Eligible.get(index).candidate();
            scored.add(new Scored(
                    candidate,
                    index + 1,
                    overrides.getOrDefault(candidate.chunkId(), -(double) index)));
        }
        scored.sort(Comparator.comparingDouble(Scored::score)
                .reversed()
                .thenComparingInt(Scored::p14Rank)
                .thenComparing(value -> value.candidate().chunkId()));
        Map<Long, Integer> rerankerRanks = new HashMap<>();
        for (int index = 0; index < scored.size(); index++) {
            rerankerRanks.put(scored.get(index).candidate().chunkId(), index + 1);
        }
        return scored.stream()
                .map(value -> reranker(
                        value.candidate(),
                        value.p14Rank(),
                        rerankerRanks.get(value.candidate().chunkId()),
                        value.score()))
                .toList();
    }

    private static RerankerCandidate reranker(
            VectorSearchResult candidate,
            int p14Rank,
            int rerankerRank,
            double score) {
        return new RerankerCandidate(candidate, p14Rank, rerankerRank, score);
    }

    private static VectorSearchResult candidate(
            long id,
            int page,
            String content,
            double score) {
        return new VectorSearchResult(
                id,
                10L,
                20L,
                "portfolio.pdf",
                1,
                (int) id,
                page,
                ChunkSourceType.PAGE,
                page,
                "page " + page,
                content,
                1.0d - score,
                score);
    }

    private record Scored(
            VectorSearchResult candidate,
            int p14Rank,
            double score) {
    }
}
