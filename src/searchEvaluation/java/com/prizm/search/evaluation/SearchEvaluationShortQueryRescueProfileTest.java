package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.profile.SearchIntent;
import com.prizm.search.repository.VectorSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchEvaluationShortQueryRescueProfileTest {

    private final CompositeSearchProfile productionProfile = new CompositeSearchProfile();
    private final SearchEvaluationShortQueryRescueProfile rescueProfile =
            new SearchEvaluationShortQueryRescueProfile();

    @Test
    void rescuesExactSingleTokenGeneralCandidateInsideBoundaryWindowAndRestoresOriginalScore() {
        VectorSearchResult candidate = pageCandidate(
                1L,
                1,
                "검색 문서",
                "Outbox 기반 알림 처리 경험",
                0.494675d);

        assertThat(productionProfile.apply("알림", List.of(candidate)).rejected()).isTrue();

        CompositeSearchProfile.Decision decision = rescueProfile.apply("알림", List.of(candidate));

        assertThat(decision.results()).containsExactly(candidate);
        assertThat(decision.results().get(0).score()).isEqualTo(0.494675d);
        assertThat(decision.results().get(0).distance()).isEqualTo(1.0d - 0.494675d);
    }

    @Test
    void doesNotRescueExactTokenCandidateBelowBoundaryWindow() {
        VectorSearchResult candidate = pageCandidate(
                1L,
                1,
                "검색 문서",
                "동시성 제어 경험",
                Math.nextDown(SearchEvaluationShortQueryRescueProfile.RESCUE_MINIMUM_DENSE_SCORE));

        assertThat(rescueProfile.apply("동시성", List.of(candidate)).rejected()).isTrue();
    }

    @Test
    void includesExactRescueLowerBoundary() {
        VectorSearchResult candidate = pageCandidate(
                1L,
                1,
                "검색 문서",
                "동시성 제어 경험",
                SearchEvaluationShortQueryRescueProfile.RESCUE_MINIMUM_DENSE_SCORE);

        assertThat(rescueProfile.apply("동시성", List.of(candidate)).results())
                .containsExactly(candidate);
    }

    @Test
    void doesNotRescueSubstringOrDocumentTitleOnlyMatch() {
        VectorSearchResult substringOnly = pageCandidate(
                1L,
                1,
                "검색 문서",
                "알림톡 연동 경험",
                0.499d);
        VectorSearchResult titleOnly = pageCandidate(
                2L,
                2,
                "알림 프로젝트",
                "푸시 발송 경험",
                0.499d);

        assertThat(rescueProfile.apply("알림", List.of(substringOnly)).rejected()).isTrue();
        assertThat(rescueProfile.apply("알림", List.of(titleOnly)).rejected()).isTrue();
    }

    @Test
    void doesNotRescueMultiTokenGeneralQuery() {
        VectorSearchResult candidate = pageCandidate(
                1L,
                1,
                "검색 문서",
                "알림 처리 경험",
                0.499d);

        assertThat(rescueProfile.apply("알림 경험", List.of(candidate)).rejected()).isTrue();
    }

    @Test
    void doesNotRescueLongSingleTokenQueryEvenWhenNormalizedTokenMatches() {
        VectorSearchResult candidate = pageCandidate(
                1L,
                1,
                "검색 문서",
                "Spring Boot 활용 경험",
                0.499d);

        assertThat(rescueProfile.apply("Springboot", List.of(candidate)).rejected()).isTrue();
    }

    @Test
    void keepsSupportedAndUnsupportedCompletedReleaseQueriesStrict() {
        List<String> completedQueries = List.of(
                "PRIZM 서비스를 배포했나요?",
                "주문 API를 출시했습니까?");
        VectorSearchResult candidate = pageCandidate(
                1L,
                1,
                "검색 문서",
                "PRIZM 서비스를 배포했습니다.",
                0.499d);

        for (String query : completedQueries) {
            assertThat(productionProfile.resolveIntent(query))
                    .as(query)
                    .isEqualTo(SearchIntent.COMPLETED_RELEASE_EVIDENCE);
            assertThat(rescueProfile.apply(query, List.of(candidate)))
                    .as(query)
                    .isEqualTo(productionProfile.apply(query, List.of(candidate)));
        }
    }

    @Test
    void leavesExactProductionBoundaryResultUnchanged() {
        VectorSearchResult candidate = pageCandidate(
                1L,
                1,
                "검색 문서",
                "알림 처리 경험",
                0.50d);

        assertThat(rescueProfile.apply("알림", List.of(candidate)))
                .isEqualTo(productionProfile.apply("알림", List.of(candidate)));
    }

    @Test
    void limitsBoundaryRescueToOneResult() {
        VectorSearchResult first = pageCandidate(
                1L,
                1,
                "첫 번째 문서",
                "동시성 제어 경험",
                0.499d);
        VectorSearchResult second = pageCandidate(
                2L,
                2,
                "두 번째 문서",
                "동시성 문제 해결",
                0.498d);

        assertThat(rescueProfile.apply("동시성", List.of(first, second)).results())
                .containsExactly(first);
    }

    @Test
    void reusesSearchTokenNormalizerForExactTokenComparison() {
        VectorSearchResult candidate = pageCandidate(
                1L,
                1,
                "검색 문서",
                "FCM 알림 처리 경험",
                0.495d);

        assertThat(rescueProfile.apply("ＦＣＭ", List.of(candidate)).results())
                .containsExactly(candidate);
    }

    private VectorSearchResult pageCandidate(
            long chunkId,
            int page,
            String documentTitle,
            String content,
            double score) {
        return new VectorSearchResult(
                chunkId,
                chunkId,
                chunkId,
                documentTitle,
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
