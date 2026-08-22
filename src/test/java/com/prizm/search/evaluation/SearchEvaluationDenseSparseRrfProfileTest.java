package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.evaluation.SearchEvaluationDenseSparseRrfProfile.FusedCandidate;
import com.prizm.search.evaluation.SearchEvaluationDenseSparseRrfProfile.Outcome;
import com.prizm.search.evaluation.SearchEvaluationDenseSparseRrfProfile.SparseCandidate;
import com.prizm.search.repository.VectorSearchResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchEvaluationDenseSparseRrfProfileTest {

    private final SearchEvaluationDenseSparseRrfProfile profile =
            new SearchEvaluationDenseSparseRrfProfile();

    @Test
    void rrfUsesUnweightedOneBasedRanksAndTheFixedK60Constant() {
        assertThat(SearchEvaluationDenseSparseRrfProfile.rrfScore(1, 3))
                .isEqualTo((1.0d / 61.0d) + (1.0d / 63.0d));
        assertThat(SearchEvaluationDenseSparseRrfProfile.rrfScore(2, null))
                .isEqualTo(1.0d / 62.0d);
    }

    @Test
    void candidatePresentInBothBranchesOutranksSingleBranchCandidates() {
        VectorSearchResult denseFirst = candidate(1L, 1, "dense only", 0.90d);
        VectorSearchResult shared = candidate(2L, 2, "shared", 0.80d);
        VectorSearchResult sparseOnly = candidate(3L, 3, "sparse only", 0.70d);

        List<FusedCandidate> fused = SearchEvaluationDenseSparseRrfProfile.fuse(
                List.of(denseFirst, shared),
                List.of(sparse(shared, 0.01d), sparse(sparseOnly, 100.0d)));

        assertThat(fused).extracting(value -> value.candidate().chunkId())
                .containsExactly(2L, 1L, 3L);
    }

    @Test
    void generalSparseBranchCanReturnARelevantCandidateBelowTheDenseFloor() {
        VectorSearchResult sparseMatch = candidate(
                1L,
                1,
                "알림 이벤트를 Outbox에 저장했습니다.",
                0.30d);

        Outcome outcome = profile.apply(
                "알림",
                List.of(sparseMatch),
                List.of(sparse(sparseMatch, 0.10d)));

        assertThat(outcome.decision().results()).containsExactly(sparseMatch);
        assertThat(outcome.decision().results().get(0).score()).isEqualTo(0.30d);
        assertThat(outcome.decision().results().get(0).distance()).isEqualTo(0.70d);
    }

    @Test
    void denseOnlyCandidateBelowTheFloorIsNotRescued() {
        VectorSearchResult belowFloor = candidate(
                1L,
                1,
                "알림 이벤트를 Outbox에 저장했습니다.",
                0.499d);

        Outcome outcome = profile.apply("알림", List.of(belowFloor), List.of());

        assertThat(outcome.decision().results()).isEmpty();
        assertThat(outcome.decision().rejectionReasons())
                .contains("DENSE_SCORE_BELOW_TUNING_FLOOR");
    }

    @Test
    void sparseScoreMagnitudeDoesNotReplaceSparseRank() {
        VectorSearchResult first = candidate(1L, 1, "Redis first", 0.80d);
        VectorSearchResult second = candidate(2L, 2, "Redis second", 0.80d);

        List<FusedCandidate> fused = SearchEvaluationDenseSparseRrfProfile.fuse(
                List.of(),
                List.of(sparse(first, 0.01d), sparse(second, 100.0d)));

        assertThat(fused).extracting(value -> value.candidate().chunkId())
                .containsExactly(1L, 2L);
    }

    @Test
    void overlappingSamePageDedupAndMaximumFiveResultsRemainInForce() {
        List<VectorSearchResult> dense = new ArrayList<>();
        List<SparseCandidate> sparse = new ArrayList<>();
        String overlap = "같은 PDF 청크 경계에서 반복되는 Redis 합성 근거다. ".repeat(4);
        for (long id = 1L; id <= 7L; id++) {
            int page = id <= 2L ? 1 : (int) id;
            String content = id == 1L
                    ? "Redis 캐시 첫 근거. " + overlap
                    : id == 2L ? overlap + "Redis 캐시 둘째 근거." : "Redis 캐시 근거 " + id;
            VectorSearchResult candidate = candidate(
                    id,
                    page,
                    content,
                    0.90d - (id / 100.0d));
            dense.add(candidate);
            sparse.add(sparse(candidate, 1.0d / id));
        }

        Outcome outcome = profile.apply("Redis", dense, sparse);

        assertThat(outcome.decision().results()).hasSize(5);
        assertThat(outcome.decision().results().stream()
                        .filter(result -> result.sourceIndex() == 1)
                        .count())
                .isEqualTo(1L);
    }

    @Test
    void completedReleaseRetrievalContractRemainsOnTheProductionDecisionPath() {
        VectorSearchResult direct = candidate(
                1L,
                1,
                "PRIZM 서비스를 배포했습니다.",
                0.90d);
        VectorSearchResult question = candidate(
                2L,
                2,
                "PRIZM 서비스를 배포했나요?",
                0.90d);
        VectorSearchResult negated = candidate(
                3L,
                3,
                "PRIZM 서비스를 배포하지 않았습니다.",
                0.90d);
        VectorSearchResult retracted = candidate(
                4L,
                4,
                "PRIZM 서비스를 배포했습니다. 이후 해당 내용을 정정했습니다.",
                0.90d);

        Outcome outcome = profile.apply(
                "PRIZM 서비스를 배포했나요?",
                List.of(question, negated, retracted, direct),
                List.of(
                        sparse(question, 0.40d),
                        sparse(negated, 0.30d),
                        sparse(retracted, 0.20d),
                        sparse(direct, 0.10d)));

        assertThat(outcome.decision().results()).containsExactly(question, negated, retracted, direct);
    }

    @Test
    void exactIdentifierCompletedReleaseEvidenceCanPassBelowTheDenseFloorOnTheSparseBranch() {
        VectorSearchResult belowFloorDirectClaim = candidate(
                1L,
                1,
                "PRIZM 서비스를 배포했습니다.",
                0.499d);

        Outcome outcome = profile.apply(
                "PRIZM 서비스를 배포했나요?",
                List.of(belowFloorDirectClaim),
                List.of(sparse(belowFloorDirectClaim, 0.10d)));

        assertThat(outcome.decision().results()).containsExactly(belowFloorDirectClaim);
        assertThat(outcome.decision().rejectionReasons()).isEmpty();
    }

    @Test
    void legacyUnsupportedCompletionGrammarNoLongerTriggersATruthRejection() {
        VectorSearchResult candidate = candidate(
                1L,
                1,
                "주문 API를 배포했습니다.",
                0.90d);

        Outcome outcome = profile.apply(
                "주문 API를 출시했습니까?",
                List.of(candidate),
                List.of(sparse(candidate, 0.10d)));

        assertThat(outcome.decision().results()).containsExactly(candidate);
        assertThat(outcome.decision().rejectionReasons())
                .doesNotContain("UNSUPPORTED_COMPLETED_RELEASE_QUERY");
    }

    @Test
    void branchCandidateLimitRemainsTwentyPerRetrieverAndAllowsAUnionOfForty() {
        List<VectorSearchResult> dense = new ArrayList<>();
        List<SparseCandidate> sparse = new ArrayList<>();
        for (long id = 1L; id <= 25L; id++) {
            dense.add(candidate(id, (int) id, "dense candidate " + id, 0.80d));
            VectorSearchResult sparseCandidate = candidate(
                    100L + id,
                    100 + (int) id,
                    "sparse candidate " + id,
                    0.40d);
            sparse.add(sparse(sparseCandidate, 1.0d));
        }

        assertThatCode(() -> profile.apply("candidate", dense, sparse))
                .doesNotThrowAnyException();
        assertThat(SearchEvaluationDenseSparseRrfProfile.fuse(dense, sparse)).hasSize(40);
    }

    private static SparseCandidate sparse(
            VectorSearchResult candidate,
            double sparseScore) {
        return new SparseCandidate(candidate, sparseScore);
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
}
