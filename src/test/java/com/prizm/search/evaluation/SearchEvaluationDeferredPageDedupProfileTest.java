package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.repository.VectorSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchEvaluationDeferredPageDedupProfileTest {

    private final CompositeSearchProfile productionProfile = new CompositeSearchProfile();
    private final SearchEvaluationDeferredPageDedupProfile deferredProfile =
            new SearchEvaluationDeferredPageDedupProfile();

    @Test
    void deferredPageDedupKeepsGeneralCandidateThatPassesAfterPreferredCandidateIsRejected() {
        VectorSearchResult rejectedRepresentative = pageCandidate(
                1L,
                1,
                "Redis 경험은 없다.",
                0.90d);
        VectorSearchResult eligibleEvidence = pageCandidate(
                2L,
                1,
                "Redis 캐싱을 운영 환경에 적용했다.",
                0.70d);
        String query = "Redis 경험 근거가 있어?";

        assertThat(productionProfile.apply(
                        query, List.of(rejectedRepresentative, eligibleEvidence))
                .rejected())
                .isTrue();
        assertThat(deferredProfile.apply(
                        query, List.of(rejectedRepresentative, eligibleEvidence))
                .results())
                .containsExactly(eligibleEvidence);
    }

    @Test
    void deferredPageDedupSelectsOneHighestRankedEligibleCandidatePerPage() {
        VectorSearchResult denseOnly = pageCandidate(
                1L,
                1,
                "캐시를 운영 환경에 적용했다.",
                0.61d);
        VectorSearchResult identifierMatch = pageCandidate(
                2L,
                1,
                "Redis 캐싱을 운영 환경에 적용했다.",
                0.60d);

        assertThat(deferredProfile.apply(
                        "Redis 캐싱 경험", List.of(denseOnly, identifierMatch))
                .results())
                .containsExactly(identifierMatch);
    }

    @Test
    void rejectedCompletedClaimCannotDisplaceValidatedClaimOnTheSamePage() {
        VectorSearchResult unverifiedRepresentative = pageCandidate(
                1L,
                1,
                "주문 API 출시 이력을 확인하기 위해 배포 여부를 검토했습니다.",
                0.90d);
        VectorSearchResult validatedClaim = pageCandidate(
                2L,
                1,
                "주문 API를 배포했습니다.",
                0.60d);
        String query = "주문 API를 출시한 이력이 있나요?";

        assertThat(productionProfile.apply(query, List.of(unverifiedRepresentative)).rejected())
                .isTrue();
        assertThat(productionProfile.apply(query, List.of(validatedClaim)).rejected())
                .isFalse();
        assertThat(deferredProfile.apply(
                        query, List.of(unverifiedRepresentative, validatedClaim))
                .results())
                .containsExactly(validatedClaim);
    }

    @Test
    void deferredPageDedupStillRejectsQuestionNegationAndRetractionClaims() {
        List<String> rejectedContents = List.of(
                "주문 API를 배포했나요?",
                "주문 API를 배포하지 않았습니다.",
                "주문 API를 배포했습니다. 이후 해당 내용을 정정했습니다.");

        for (int index = 0; index < rejectedContents.size(); index++) {
            VectorSearchResult candidate = pageCandidate(
                    index + 1L,
                    1,
                    rejectedContents.get(index),
                    0.90d);

            assertThat(deferredProfile.apply(
                            "주문 API를 출시한 이력이 있나요?", List.of(candidate))
                    .rejected())
                    .as(rejectedContents.get(index))
                    .isTrue();
        }
    }

    private VectorSearchResult pageCandidate(
            long chunkId,
            int page,
            String content,
            double score) {
        return new VectorSearchResult(
                chunkId,
                10L,
                20L,
                "합성 검색 문서",
                1,
                Math.toIntExact(chunkId),
                page,
                ChunkSourceType.PAGE,
                page,
                page + "페이지",
                content,
                1.0d - score,
                score);
    }
}
