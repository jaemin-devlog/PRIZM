package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.repository.VectorSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchEvaluationCompositeProfileTest {

    private final SearchEvaluationCompositeProfile profile = new SearchEvaluationCompositeProfile();

    @Test
    void keepsOneResultPerPdfPageAndChoosesTheBetterLexicalRepresentative() {
        VectorSearchResult denseFirst = candidate(
                1L, 1, 2, ChunkSourceType.PAGE,
                "같은 팀 조합은 고유 제약으로 중복을 차단했다.", 0.64d);
        VectorSearchResult direct = candidate(
                2L, 2, 2, ChunkSourceType.PAGE,
                "MatchLedger DB 행 잠금과 상태 재확인으로 매칭 중복 확정을 방지했다.", 0.62d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "MatchLedger의 DB 잠금 근거를 같은 페이지 중복 없이 보여줘.",
                List.of(denseFirst, direct));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).extracting(VectorSearchResult::chunkId).containsExactly(2L);
    }

    @Test
    void removesAdjacentTxtChunkWhoseBoundaryContentIsRepeated() {
        String overlap = "고정 길이 청크의 경계에서 반복되는 합성 근거 문장이다.".repeat(4);
        VectorSearchResult first = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "합성 요청 2400건을 처리했다. " + overlap, 0.72d);
        VectorSearchResult second = candidate(
                2L, 2, 2, ChunkSourceType.TEXT_CHUNK,
                overlap + " 뒤쪽 설명", 0.70d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "합성 요청 2400건 처리 근거를 보여줘.",
                List.of(first, second));

        assertThat(decision.results()).hasSize(1);
    }

    @Test
    void keepsOneDiversifiedResultWhenResumeAndPortfolioRepeatTheSameAnchoredAnswer() {
        VectorSearchResult portfolio = candidate(
                1L,
                10L,
                20L,
                1,
                2,
                ChunkSourceType.PAGE,
                "MatchLedger는 매칭 중복 확정을 DB 행 잠금과 고유 제약으로 방지했다.",
                0.70d);
        VectorSearchResult resume = candidate(
                2L,
                11L,
                21L,
                1,
                1,
                ChunkSourceType.PAGE,
                "MatchLedger 요약: 행 잠금으로 매칭 확정을 제어하고 중복 저장을 차단했다.",
                0.65d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "MatchLedger에서 매칭 중복 확정을 어떻게 방지했어?",
                List.of(portfolio, resume));

        assertThat(decision.results()).extracting(VectorSearchResult::chunkId).containsExactly(1L);
        assertThat(decision.candidates()).hasSize(2);
    }

    @Test
    void keepsTheMoreSpecificPageWhenTheSameDocumentRepeatsAnAnchoredSummary() {
        VectorSearchResult directPage = candidate(
                1L,
                10L,
                20L,
                2,
                2,
                ChunkSourceType.PAGE,
                "MatchLedger는 매칭 중복 확정을 DB 행 잠금과 고유 제약으로 방지했다.",
                0.70d);
        VectorSearchResult introductionPage = candidate(
                2L,
                10L,
                20L,
                1,
                1,
                ChunkSourceType.PAGE,
                "MatchLedger는 매칭 확정 흐름을 소개하는 합성 프로젝트다.",
                0.65d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "MatchLedger에서 매칭 중복 확정을 어떻게 방지했어?",
                List.of(directPage, introductionPage));

        assertThat(decision.results()).extracting(VectorSearchResult::chunkId).containsExactly(1L);
    }

    @Test
    void doesNotCollapseDifferentDocumentsWithComplementaryCoreTerms() {
        VectorSearchResult first = candidate(
                1L,
                10L,
                20L,
                1,
                1,
                ChunkSourceType.PAGE,
                "훈련 날짜는 2026년 7월 1일로 기록했다.",
                0.70d);
        VectorSearchResult second = candidate(
                2L,
                11L,
                21L,
                1,
                1,
                ChunkSourceType.PAGE,
                "장애 발생 뒤 정상화 절차와 경보 정책을 기록했다.",
                0.65d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "장애 훈련 날짜와 정상화 근거를 확인해줘.",
                List.of(first, second));

        assertThat(decision.results()).extracting(VectorSearchResult::chunkId).containsExactly(1L, 2L);
    }

    @Test
    void rejectsAHighDenseScoreWhenARequiredIdentifierIsMissing() {
        VectorSearchResult candidate = candidate(
                1L, 1, 3, ChunkSourceType.PAGE,
                "외부 푸시 장애에도 내부 알림 데이터는 보존된다.", 0.78d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "Kafka 장애가 발생해도 내부 알림 데이터가 보존된다는 근거가 있어?",
                List.of(candidate));

        assertThat(decision.rejected()).isTrue();
        assertThat(decision.rejectionReasons()).contains("MISSING_IDENTIFIER:kafka");
    }

    @Test
    void rejectsAnExplicitlyNegatedPositiveClaim() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "Kafka를 학습했지만 프로젝트에 적용하지 않았고 운영하지 않았다.", 0.70d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "Kafka를 실제 프로젝트에 적용해 운영했나요?",
                List.of(candidate));

        assertThat(decision.rejected()).isTrue();
        assertThat(decision.rejectionReasons()).contains("NEGATED_CLAIM");
    }

    @Test
    void acceptsAQuestionWithTyposWhenItsCoreEvidenceIsPresent() {
        VectorSearchResult candidate = candidate(
                1L, 1, 3, ChunkSourceType.PAGE,
                "외부 푸시 서비스 장애와 분리해 내부 알림 데이터를 먼저 보존했다.", 0.60d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "외붓 푸시 서비스에 장애가 나도 내부 알림 데이터가 보존되는 이유는?",
                List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);
    }

    @Test
    void rejectsLowDenseScoreAndMissingNumericEvidence() {
        VectorSearchResult lowScore = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "위성 관제 시스템 개발 근거", 0.49d);
        VectorSearchResult wrongNumber = candidate(
                2L, 2, 1, ChunkSourceType.TEXT_CHUNK,
                "합성 요청 1200건 처리 근거", 0.70d);

        assertThat(profile.apply("위성 관제 시스템 개발 근거가 있어?", List.of(lowScore))
                        .rejectionReasons())
                .contains("DENSE_SCORE_BELOW_TUNING_FLOOR");
        assertThat(profile.apply("합성 요청 2400건 처리 근거를 보여줘.", List.of(wrongNumber))
                        .rejectionReasons())
                .contains("MISSING_NUMBER:2400");
    }

    @Test
    void capsAcceptedUniqueResultsAtFive() {
        List<VectorSearchResult> candidates = java.util.stream.LongStream.rangeClosed(1, 7)
                .mapToObj(id -> candidate(
                        id,
                        (int) id,
                        (int) id,
                        ChunkSourceType.TEXT_CHUNK,
                        "주문 API 배포 근거 " + id,
                        0.70d - (id / 100.0d)))
                .toList();

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "주문 API 배포 근거를 보여줘.", candidates);

        assertThat(decision.results()).hasSize(5);
    }

    private VectorSearchResult candidate(
            long chunkId,
            int chunkNo,
            int sourceIndex,
            ChunkSourceType sourceType,
            String content,
            double score) {
        return candidate(
                chunkId,
                10L,
                20L,
                chunkNo,
                sourceIndex,
                sourceType,
                content,
                score);
    }

    private VectorSearchResult candidate(
            long chunkId,
            long documentId,
            long documentVersionId,
            int chunkNo,
            int sourceIndex,
            ChunkSourceType sourceType,
            String content,
            double score) {
        return new VectorSearchResult(
                chunkId,
                documentId,
                documentVersionId,
                "합성 검색 문서",
                1,
                chunkNo,
                sourceType == ChunkSourceType.PAGE ? sourceIndex : null,
                sourceType,
                sourceIndex,
                sourceType == ChunkSourceType.PAGE ? sourceIndex + "페이지" : "텍스트 구간 " + sourceIndex,
                content,
                1.0d - score,
                score);
    }
}
