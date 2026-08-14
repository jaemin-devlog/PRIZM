package com.prizm.search.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.repository.VectorSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class EvidenceQualityRerankerTest {

    private final EvidenceQualityReranker reranker = new EvidenceQualityReranker();

    @Test
    void detailedProblemActionResultEvidenceReceivesABoundedPositiveAdjustment() {
        VectorSearchResult detailed = candidate(
                1L,
                "TourAPI 호출 대기가 누적되는 문제를 확인했습니다. "
                        + "호출 흐름을 병렬 작업으로 분리해 1,252건 처리 시간을 19분에서 11초로 단축했습니다.",
                0.55d);

        EvidenceQualityReranker.Evaluation evaluation =
                reranker.evaluate("TourAPI 호출 병목 개선 경험", detailed);

        assertThat(evaluation.adjustment()).isPositive().isLessThanOrEqualTo(0.065d);
        assertThat(evaluation.action()).isTrue();
        assertThat(evaluation.problem()).isTrue();
        assertThat(evaluation.result()).isTrue();
    }

    @Test
    void profileMetadataAndTechnicalListsDoNotReceiveAQualityAdvantage() {
        VectorSearchResult profile = candidate(
                2L,
                "Java / Spring Boot Backend Developer. EMAIL user@example.com PHONE 010-1234-5678 "
                        + "GITHUB github.com/example EDUCATION Example University GPA 4.0 / 4.5",
                0.58d);

        EvidenceQualityReranker.Evaluation evaluation =
                reranker.evaluate("Spring Boot 백엔드 경험", profile);

        assertThat(evaluation.adjustment()).isNegative();
        assertThat(evaluation.profileMetadata()).isTrue();
    }

    @Test
    void unrelatedDetailCannotGainAPositiveAdjustmentWithoutQueryCoverage() {
        VectorSearchResult unrelated = candidate(
                3L,
                "Redis 중복 처리 문제를 분석하고 row lock을 적용해 4,400회 검증에서 중복 저장 0건을 확인했습니다.",
                0.58d);

        EvidenceQualityReranker.Evaluation evaluation =
                reranker.evaluate("Spring Boot 백엔드 경험", unrelated);

        assertThat(evaluation.queryCoverage()).isZero();
        assertThat(evaluation.adjustment()).isLessThanOrEqualTo(0.0d);
    }

    @Test
    void compositeProfileCanPromoteDetailedEvidenceWithoutChangingCandidateScores() {
        CompositeSearchProfile profile = new CompositeSearchProfile();
        VectorSearchResult summary = candidate(
                4L,
                "Java / Spring Boot Backend Developer. EMAIL user@example.com PHONE 010-1234-5678 "
                        + "GITHUB github.com/example EDUCATION Example University GPA 4.0 / 4.5",
                0.58d);
        VectorSearchResult detailed = candidate(
                5L,
                "Spring Boot 서비스에서 인증 흐름이 분리된 문제를 확인하고 공통 진입점을 설계했습니다. "
                        + "OAuth2 흐름을 통합해 계정 충돌을 방지하고 통합 테스트로 검증했습니다.",
                0.54d);

        List<VectorSearchResult> results =
                profile.apply("Spring Boot 백엔드 경험", List.of(summary, detailed)).results();

        assertThat(results).extracting(VectorSearchResult::chunkId).containsExactly(5L, 4L);
        assertThat(results).extracting(VectorSearchResult::score).containsExactly(0.54d, 0.58d);
    }

    private static VectorSearchResult candidate(long chunkId, String content, double score) {
        return new VectorSearchResult(
                chunkId,
                chunkId,
                chunkId,
                "Career document",
                1,
                1,
                1,
                ChunkSourceType.PAGE,
                1,
                "1페이지",
                content,
                1.0d - score,
                score);
    }
}
