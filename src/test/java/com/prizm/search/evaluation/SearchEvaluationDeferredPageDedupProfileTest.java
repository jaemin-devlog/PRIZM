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
    void productionKeepsDistinctRelatedTruthVariantsOnTheSamePage() {
        VectorSearchResult negatedEvidence = pageCandidate(
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
                        query, List.of(negatedEvidence, eligibleEvidence))
                .results())
                .containsExactly(negatedEvidence, eligibleEvidence);
        assertThat(deferredProfile.apply(
                        query, List.of(negatedEvidence, eligibleEvidence))
                .results())
                .containsExactly(negatedEvidence, eligibleEvidence);
    }

    @Test
    void productionAndDeferredProfilesPreserveDistinctEligibleSamePageEvidence() {
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

        String query = "Redis 캐싱 경험";

        assertThat(productionProfile.apply(query, List.of(denseOnly, identifierMatch)).results())
                .containsExactly(identifierMatch, denseOnly);
        assertThat(deferredProfile.apply(query, List.of(denseOnly, identifierMatch)).results())
                .containsExactly(identifierMatch, denseOnly);
    }

    @Test
    void distinctRelevantReleaseReferencesOnTheSamePageAreBothPreserved() {
        VectorSearchResult planningReference = pageCandidate(
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

        assertThat(productionProfile.apply(query, List.of(planningReference)).rejected())
                .isFalse();
        assertThat(productionProfile.apply(query, List.of(validatedClaim)).rejected())
                .isFalse();
        assertThat(deferredProfile.apply(
                        query, List.of(planningReference, validatedClaim))
                .results())
                .containsExactly(planningReference, validatedClaim);
    }

    @Test
    void deferredPageDedupKeepsRelevantQuestionNegationAndRetractionContent() {
        List<String> relatedContents = List.of(
                "주문 API를 배포했나요?",
                "주문 API를 배포하지 않았습니다.",
                "주문 API를 배포했습니다. 이후 해당 내용을 정정했습니다.");

        for (int index = 0; index < relatedContents.size(); index++) {
            VectorSearchResult candidate = pageCandidate(
                    index + 1L,
                    1,
                    relatedContents.get(index),
                    0.90d);

            assertThat(deferredProfile.apply(
                            "주문 API를 출시한 이력이 있나요?", List.of(candidate))
                    .rejected())
                    .as(relatedContents.get(index))
                    .isFalse();
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
