package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.evaluation.SearchEvaluationHybridRrfProfile.FusedCandidate;
import com.prizm.search.evaluation.SearchEvaluationHybridRrfProfile.Outcome;
import com.prizm.search.evaluation.SearchEvaluationLexicalCandidateRepository.LexicalCandidate;
import com.prizm.search.repository.VectorSearchResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchEvaluationHybridRrfProfileTest {

    private final SearchEvaluationHybridRrfProfile profile =
            new SearchEvaluationHybridRrfProfile();

    @Test
    void rrfUsesUnweightedOneBasedRanksAndTheFixedK60Constant() {
        assertThat(SearchEvaluationHybridRrfProfile.rrfScore(1, 3))
                .isEqualTo((1.0d / 61.0d) + (1.0d / 63.0d));
        assertThat(SearchEvaluationHybridRrfProfile.rrfScore(2, null))
                .isEqualTo(1.0d / 62.0d);
    }

    @Test
    void candidatePresentInBothBranchesOutranksSingleBranchCandidates() {
        VectorSearchResult denseFirst = candidate(1L, 1, "dense only", 0.90d);
        VectorSearchResult shared = candidate(2L, 2, "shared", 0.80d);
        VectorSearchResult lexicalOnly = candidate(3L, 3, "lexical only", 0.70d);

        List<FusedCandidate> fused = SearchEvaluationHybridRrfProfile.fuse(
                List.of(denseFirst, shared),
                List.of(lexical(shared, 0.01d), lexical(lexicalOnly, 100.0d)));

        assertThat(fused).extracting(candidate -> candidate.candidate().chunkId())
                .containsExactly(2L, 1L, 3L);
        assertThat(fused.get(0).rrfScore())
                .isEqualTo((1.0d / 62.0d) + (1.0d / 61.0d));
    }

    @Test
    void lexicalBranchCanReturnAnExactMatchBelowTheDenseFloorWithoutUsingP12Rescue() {
        VectorSearchResult lexicalMatch = candidate(
                1L,
                1,
                "알림 이벤트를 Outbox에 저장했다.",
                0.30d);

        Outcome outcome = profile.apply(
                "알림",
                List.of(lexicalMatch),
                List.of(lexical(lexicalMatch, 0.10d)));

        assertThat(outcome.decision().results()).containsExactly(lexicalMatch);
        assertThat(outcome.decision().results().get(0).score()).isEqualTo(0.30d);
        assertThat(outcome.decision().results().get(0).distance()).isEqualTo(0.70d);
    }

    @Test
    void denseOnlyCandidateBelowTheFloorIsNotRescued() {
        VectorSearchResult belowFloor = candidate(
                1L,
                1,
                "알림 이벤트를 Outbox에 저장했다.",
                0.499d);

        Outcome outcome = profile.apply("알림", List.of(belowFloor), List.of());

        assertThat(outcome.decision().results()).isEmpty();
        assertThat(outcome.decision().rejectionReasons())
                .contains("DENSE_SCORE_BELOW_TUNING_FLOOR");
    }

    @Test
    void lexicalScoreMagnitudeDoesNotReplaceLexicalRank() {
        VectorSearchResult first = candidate(1L, 1, "Redis first", 0.80d);
        VectorSearchResult second = candidate(2L, 2, "Redis second", 0.80d);

        List<FusedCandidate> fused = SearchEvaluationHybridRrfProfile.fuse(
                List.of(),
                List.of(lexical(first, 0.01d), lexical(second, 100.0d)));

        assertThat(fused).extracting(candidate -> candidate.candidate().chunkId())
                .containsExactly(1L, 2L);
    }

    @Test
    void existingSamePageDedupAndMaximumFiveResultsRemainInForce() {
        List<VectorSearchResult> dense = new ArrayList<>();
        List<LexicalCandidate> lexical = new ArrayList<>();
        for (long id = 1L; id <= 7L; id++) {
            int page = id <= 2L ? 1 : (int) id;
            VectorSearchResult candidate = candidate(
                    id,
                    page,
                    "Redis 캐시 근거 " + id,
                    0.90d - (id / 100.0d));
            dense.add(candidate);
            lexical.add(lexical(candidate, 1.0d / id));
        }

        Outcome outcome = profile.apply("Redis", dense, lexical);

        assertThat(outcome.decision().results()).hasSize(5);
        assertThat(outcome.decision().results().stream()
                        .filter(result -> result.sourceIndex() == 1)
                        .count())
                .isEqualTo(1L);
    }

    @Test
    void completedReleaseClaimGateAcceptsOnlyTheDirectAffirmativeClaim() {
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
                        lexical(question, 0.40d),
                        lexical(negated, 0.30d),
                        lexical(retracted, 0.20d),
                        lexical(direct, 0.10d)));

        assertThat(outcome.decision().results()).containsExactly(direct);
    }

    @Test
    void completedReleaseEvidenceKeepsTheStrictDenseFloorOnTheLexicalBranch() {
        VectorSearchResult belowFloorDirectClaim = candidate(
                1L,
                1,
                "PRIZM 서비스를 배포했습니다.",
                0.499d);

        Outcome outcome = profile.apply(
                "PRIZM 서비스를 배포했나요?",
                List.of(belowFloorDirectClaim),
                List.of(lexical(belowFloorDirectClaim, 0.10d)));

        assertThat(outcome.decision().results()).isEmpty();
        assertThat(outcome.decision().rejectionReasons())
                .contains("DENSE_SCORE_BELOW_TUNING_FLOOR");
    }

    @Test
    void unsupportedCompletedReleaseQueryCannotFallBackThroughTheLexicalBranch() {
        VectorSearchResult candidate = candidate(
                1L,
                1,
                "주문 API를 배포했습니다.",
                0.90d);

        Outcome outcome = profile.apply(
                "주문 API를 출시했습니까?",
                List.of(candidate),
                List.of(lexical(candidate, 0.10d)));

        assertThat(outcome.decision().results()).isEmpty();
        assertThat(outcome.decision().rejectionReasons())
                .contains("UNSUPPORTED_COMPLETED_RELEASE_QUERY");
    }

    @Test
    void branchCandidateLimitRemainsTwentyPerRetrieverAndAllowsAUnionOfForty() {
        List<VectorSearchResult> dense = new ArrayList<>();
        List<LexicalCandidate> lexical = new ArrayList<>();
        for (long id = 1L; id <= 25L; id++) {
            dense.add(candidate(id, (int) id, "dense candidate " + id, 0.80d));
            VectorSearchResult lexicalCandidate = candidate(
                    100L + id,
                    100 + (int) id,
                    "lexical candidate " + id,
                    0.40d);
            lexical.add(lexical(lexicalCandidate, 1.0d));
        }

        assertThatCode(() -> profile.apply("candidate", dense, lexical))
                .doesNotThrowAnyException();
        assertThat(SearchEvaluationHybridRrfProfile.fuse(dense, lexical)).hasSize(40);
    }

    private static LexicalCandidate lexical(
            VectorSearchResult candidate,
            double lexicalScore) {
        return new LexicalCandidate(candidate, lexicalScore);
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
