package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.profile.SearchIntent;
import com.prizm.search.repository.VectorSearchResult;
import java.util.List;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;

class SearchEvaluationCompositeProfileTest {

    private final SearchEvaluationCompositeProfile profile = new SearchEvaluationCompositeProfile();

    @Test
    void resolvesGeneralTechnicalSearchIntent() {
        assertThat(profile.resolveIntent("Springboot 활용 경험"))
                .isEqualTo(SearchIntent.GENERAL);
    }

    @Test
    void resolvesSupportedCompletedReleaseEvidenceIntent() {
        assertThat(profile.resolveIntent("주문 API를 출시했나요?"))
                .isEqualTo(SearchIntent.COMPLETED_RELEASE_EVIDENCE);
    }

    @Test
    void keepsUnsupportedCompletedReleaseQueryOutOfGeneralIntent() {
        assertThat(profile.resolveIntent("주문 API를 출시했습니까?"))
                .isEqualTo(SearchIntent.COMPLETED_RELEASE_EVIDENCE);
    }

    @Test
    void consolidatesOverlappingPdfChunksAndChoosesTheBetterLexicalRepresentative() {
        String overlap = "같은 PDF 경계에서 반복되는 합성 매칭 근거 문장이다. ".repeat(4);
        VectorSearchResult denseFirst = candidate(
                1L, 1, 2, ChunkSourceType.PAGE,
                "같은 팀 조합은 고유 제약으로 중복을 차단했다. " + overlap, 0.64d);
        VectorSearchResult direct = candidate(
                2L, 2, 2, ChunkSourceType.PAGE,
                overlap + "MatchLedger DB 행 잠금과 상태 재확인으로 매칭 중복 확정을 방지했다.", 0.62d);

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
    void generalProfileDoesNotRejectAHighDenseScoreWhenARequiredIdentifierIsMissing() {
        VectorSearchResult candidate = candidate(
                1L, 1, 3, ChunkSourceType.PAGE,
                "외부 푸시 장애에도 내부 알림 데이터는 보존된다.", 0.78d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "Kafka 장애가 발생해도 내부 알림 데이터가 보존된다는 근거가 있어?",
                List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);
    }

    @Test
    void generalProfileDoesNotRejectUnmatchedSpringbootIdentifier() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "Spring Boot 서비스 구현 경험", 0.90d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "Springboot 서비스 구현 경험", List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);
    }

    @Test
    void generalRankingUsesNormalizedIdentifierMatchAsASmallBoost() {
        VectorSearchResult higherDenseWithoutIdentifier = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "백엔드 서비스 구현 경험", 0.805d);
        VectorSearchResult nearbyDenseWithIdentifier = candidate(
                2L, 2, 2, ChunkSourceType.TEXT_CHUNK,
                "Spring Boot 서비스 구현 경험", 0.800d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "Springboot 서비스 구현 경험",
                List.of(higherDenseWithoutIdentifier, nearbyDenseWithIdentifier));

        assertThat(decision.results())
                .extracting(VectorSearchResult::chunkId)
                .containsExactly(2L, 1L);
    }

    @Test
    void generalRankingUsesCoreTermCoverageAsASmallBoost() {
        VectorSearchResult higherDenseWithoutCoreTerms = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "서비스 안정성 개선 기록", 0.804d);
        VectorSearchResult nearbyDenseWithCoreTerms = candidate(
                2L, 2, 2, ChunkSourceType.TEXT_CHUNK,
                "장애 복구 절차를 정리한 기록", 0.800d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "장애 복구 절차",
                List.of(higherDenseWithoutCoreTerms, nearbyDenseWithCoreTerms));

        assertThat(decision.results())
                .extracting(VectorSearchResult::chunkId)
                .containsExactly(2L, 1L);
    }

    @Test
    void generalRankingUsesNumberMatchAsASmallBoost() {
        VectorSearchResult higherDenseWithoutNumber = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "API 여러 개를 구현한 경험", 0.804d);
        VectorSearchResult nearbyDenseWithNumber = candidate(
                2L, 2, 2, ChunkSourceType.TEXT_CHUNK,
                "API 8개를 구현한 경험", 0.800d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "API 8개 구현 경험",
                List.of(higherDenseWithoutNumber, nearbyDenseWithNumber));

        assertThat(decision.results())
                .extracting(VectorSearchResult::chunkId)
                .containsExactly(2L, 1L);
    }

    @Test
    void generalRankingDoesNotLetAllStringSignalsOvertakeAMuchHigherDenseScore() {
        VectorSearchResult muchHigherDenseWithoutStringSignals = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "분산 시스템 운영 경험", 0.900d);
        VectorSearchResult lowerDenseWithAllStringSignals = candidate(
                2L, 2, 2, ChunkSourceType.TEXT_CHUNK,
                "Spring Boot 캐싱 8건 경험", 0.800d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "Springboot 캐싱 8건 경험",
                List.of(muchHigherDenseWithoutStringSignals, lowerDenseWithAllStringSignals));

        assertThat(decision.results())
                .extracting(VectorSearchResult::chunkId)
                .containsExactly(1L, 2L);
    }

    @Test
    void completedReleaseEvidenceTreatsSpringBootFormattingAsTheSameTarget() {
        assertThat(profile.resolveIntent("Springboot 서비스를 출시했나요?"))
                .isEqualTo(SearchIntent.COMPLETED_RELEASE_EVIDENCE);

        List<String> formattingVariants = List.of(
                "Spring Boot",
                "SpringBoot",
                "Springboot",
                "Spring-Boot",
                "spring_boot");
        for (int index = 0; index < formattingVariants.size(); index++) {
            String formattingVariant = formattingVariants.get(index);
            VectorSearchResult candidate = candidate(
                    index + 1L,
                    index + 1,
                    index + 1,
                    ChunkSourceType.TEXT_CHUNK,
                    formattingVariant + " 서비스를 배포했습니다.",
                    0.90d);

            SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                    "Springboot 서비스를 출시했나요?", List.of(candidate));

            assertThat(decision.rejected()).as(formattingVariant).isFalse();
            assertThat(decision.results()).as(formattingVariant).containsExactly(candidate);
        }
    }

    @Test
    void completedReleaseEvidenceDoesNotTreatSpringBatchAsSpringBoot() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "Spring Batch 서비스를 배포했습니다.", 0.90d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "Springboot 서비스를 출시했나요?", List.of(candidate));

        assertThat(decision.rejected()).isTrue();
        assertThat(decision.rejectionReasons())
                .contains("MISSING_IDENTIFIER:springboot");
    }

    @Test
    void keepsExplicitlyNegatedContentWhenItRemainsRelevantToTheQuery() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "Kafka를 학습했지만 프로젝트에 적용하지 않았고 운영하지 않았다.", 0.70d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "Kafka를 실제 프로젝트에 적용해 운영했나요?",
                List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);
        assertThat(decision.rejectionReasons()).isEmpty();
    }

    @Test
    void keepsKafkaRelatedContentRegardlessOfActorAdoptionOrPolarity() {
        List<VectorSearchResult> related = List.of(
                candidate(11L, 11L, 21L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                        "Kafka를 사용해 메시징 시스템을 구현했다.", 0.84d),
                candidate(12L, 12L, 22L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                        "Kafka를 사용하지 않았다.", 0.82d),
                candidate(13L, 13L, 23L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                        "Kafka 도입을 검토했다.", 0.80d),
                candidate(14L, 14L, 24L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                        "다른 팀이 Kafka를 사용했다.", 0.78d));

        SearchEvaluationCompositeProfile.Decision decision = profile.apply("Kafka", related);

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactlyElementsOf(related);
        assertThat(decision.rejectionReasons()).isEmpty();
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
    void acceptsAnAllAsciiQuestionWhenItsExactTechnicalIdentifiersArePresent() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "Built Spring Boot services with Redis caching experience.", 0.90d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "Spring Boot and Redis experience", List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);
    }

    @Test
    void generalProfileDoesNotRejectAnAllAsciiQuestionWhenItsRequiredTechnologyIsAbsent() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "RabbitMQ production experience with durable notifications.", 0.90d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "Kafka production experience", List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);
    }

    @Test
    void generalProfileEligibilityDoesNotRequireAnIdentifierMatchInsideALongerIdentifier() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "JavaScript production experience with frontend services.", 0.90d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "Java production experience", List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);
    }

    @Test
    void generalProfileDoesNotRejectInsufficientCoreTermCoverage() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "자바스크립트 운영 경험과 프론트엔드 서비스 근거", 0.90d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "자바 운영 근거", List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);
    }

    @Test
    void acceptsASingleUnicodeProperNounAfterSuffixNormalization() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "루미나에서 수행한 합성 프로젝트 기록", 0.90d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "루미나 근거", List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);
    }

    @Test
    void generalProfileEligibilityDoesNotRequireASingleUnicodeAnchor() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "루미나랩 합성 프로젝트 기록", 0.90d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "루미나 근거", List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);
    }

    @Test
    void acceptsASingleExactAsciiIdentifierAsEvidence() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "Kafka를 운영 환경에서 사용한 경험", 0.90d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "Kafka 근거", List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);
    }

    @Test
    void generalProfileEligibilityDoesNotRequireAnExactAsciiIdentifier() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "Kafka랩 관련 합성 문서", 0.90d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "Kafka 근거", List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);
    }

    @Test
    void treatsOnlyCompletedReleaseActionFormsAsEquivalent() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "2025년 3월 14일에 주문 API를 배포했다.", 0.90d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "주문 API를 실제로 출시한 이력이 있나요?", List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);

        VectorSearchResult reverseCandidate = candidate(
                2L, 2, 2, ChunkSourceType.TEXT_CHUNK,
                "2025년 3월 14일에 주문 API를 출시했다!", 0.90d);

        assertThat(profile.apply("주문 API를 배포한 이력이 있나요?", List.of(reverseCandidate))
                        .rejected())
                .isFalse();

        VectorSearchResult formalCandidate = candidate(
                3L, 3, 3, ChunkSourceType.TEXT_CHUNK,
                "2025년 3월 14일에 주문 API를 배포했습니다.", 0.90d);

        assertThat(profile.apply("주문 API를 출시했나요?", List.of(formalCandidate))
                        .rejected())
                .isFalse();
    }

    @Test
    void keepsReleaseAssertionsQuestionsReportsAndRetractionsWhenTheyRemainRelevant() {
        List<String> relatedReleaseContent = List.of(
                "주문 API를 배포했습니다.",
                "주문 API를 배포했습니다?",
                "주문 API를 배포했습니다, 맞나요?",
                "“주문 API를 배포했습니다”라고 했나요?",
                "주문 API 배포 여부를 확인했다.",
                "주문 API를 배포했습니다, 그러나 실제로는 하지 않았습니다.",
                "주문 API를 배포했습니다. 그러나 실제로는 하지 않았습니다.",
                "담당자가 \"주문 API를 배포했습니다\"라고 전했다.",
                "담당자가 \"주문 API를 배포했습니다\"라고 물었다.",
                "주문 API를 배포했습니다. 맞나요?",
                "주문 API를 배포했습니다. 아니, 아직 배포하지 않았습니다.");

        for (int index = 0; index < relatedReleaseContent.size(); index++) {
            VectorSearchResult candidate = candidate(
                    index + 1L,
                    index + 1,
                    index + 1,
                    ChunkSourceType.TEXT_CHUNK,
                    relatedReleaseContent.get(index),
                    0.90d);

            assertThat(profile.apply("주문 API를 출시한 이력이 있나요?", List.of(candidate))
                            .rejected())
                    .as(relatedReleaseContent.get(index))
                    .isFalse();
        }
    }

    @Test
    void keepsTargetBoundReleaseReferencesAcrossClaimUnitTransformations() {
        List<String> targets = List.of("주문 API", "주문 결제 API", "주문 취소 API");
        List<UnaryOperator<String>> assertiveTransformations = List.of(
                target -> target + "를 배포했습니다.",
                target -> "문제없이 " + target + "를 배포했습니다.",
                target -> "2025년 3월 14일에 " + target + "를 배포했습니다.",
                target -> target + " “오로라”를 배포했습니다.",
                target -> target + " “오로라-v1”을 배포했습니다.",
                target -> target + "를 배포했습니다.운영팀에 인계했습니다.",
                target -> target + " v1.2를 배포했습니다.",
                target -> target + "를 배포했다.",
                target -> target + "를 배포하였습니다.");
        List<UnaryOperator<String>> nonAssertiveTransformations = List.of(
                target -> target + "를 배포했습니다?",
                target -> target + "를 배포했습니다, 맞습니까.",
                target -> target + "를 배포했습니다. 맞나요",
                target -> "담당자가 말하길 " + target + "를 배포했습니다.",
                target -> "담당자가 전한 내용은 " + target + "를 배포했습니다.",
                target -> "담당자에 따르면 " + target + "를 배포했습니다.",
                target -> "인용문: " + target + "를 배포했습니다.",
                target -> "확인 질문: " + target + "를 배포했습니다.",
                target -> "담당자가 주장하기를 " + target + "를 배포했습니다.",
                target -> "인용 내용: " + target + "를 배포했습니다.",
                target -> "[" + target + "를 배포했습니다]라고 적혀 있습니다.",
                target -> target + " 배포 여부를 확인했다.",
                target -> target + "를 배포했습니다. 사실이 아닙니다.",
                target -> target + "를 배포했습니다. 이는 거짓입니다.",
                target -> target + "를 배포했습니다. 앞 문장을 부인합니다.",
                target -> target + "를 배포했습니다. 정말 맞나요?",
                target -> target + "를 배포했습니다. 그건 거짓입니다.",
                target -> target + "를 배포했습니다. 이를 철회합니다.",
                target -> target + "를 배포했습니다.\n그러나 실제로는 하지 않았습니다.",
                target -> target + "를 배포했습니다.\r\n맞나요?",
                target -> target + "를 배포했습니다... 사실이 아닙니다.",
                target -> target + "를 배포했습니다. 앞선 발언을 거둡니다.",
                target -> target + "를 배포했습니다. 그러나 v1.2 배포 주장은 철회합니다.",
                target -> target + "를 배포했습니다. 배송 API는 운영하지 않습니다.",
                target -> "“오로라” " + target + "를 배포했습니다.",
                target -> target + "를 배포했고 운영팀에 인계했습니다.",
                target -> target + "를 배포했습니다 (2025년 3월).",
                target -> target + " v1.2 “오로라”를 배포했습니다.",
                target -> target + " “오로라” v1.2를 배포했습니다.",
                target -> target + " “오:로라”를 배포했습니다.",
                target -> target + " “오/로라”를 배포했습니다.",
                target -> target + " “오,로라”를 배포했습니다.",
                target -> target + " “오.로라”를 배포했습니다.",
                target -> target + " “오(로라)”를 배포했습니다.",
                target -> target + "를 배포했습니다");

        long candidateId = 100L;
        for (String target : targets) {
            String query = target + "를 출시한 이력이 있나요?";
            for (UnaryOperator<String> transformation : assertiveTransformations) {
                String transformedClaim = transformation.apply(target);
                VectorSearchResult transformedCandidate = candidate(
                        candidateId++, 1, 1, ChunkSourceType.TEXT_CHUNK, transformedClaim, 0.90d);

                assertThat(profile.apply(query, List.of(transformedCandidate)).rejected())
                        .as("direct assertion transformation: %s", transformedClaim)
                        .isFalse();
            }

            for (UnaryOperator<String> transformation : nonAssertiveTransformations) {
                String transformedClaim = transformation.apply(target);
                VectorSearchResult transformedCandidate = candidate(
                        candidateId++, 1, 1, ChunkSourceType.TEXT_CHUNK, transformedClaim, 0.90d);
                SearchEvaluationCompositeProfile.Decision decision =
                        profile.apply(query, List.of(transformedCandidate));

                assertThat(decision.rejected())
                        .as("related transformation: %s", transformedClaim)
                        .isFalse();
                assertThat(decision.rejectionReasons())
                        .as("truth reasons removed: %s", transformedClaim)
                        .doesNotContain(
                                "UNSUPPORTED_COMPLETED_RELEASE_QUERY",
                                "MISSING_ASSERTED_COMPLETED_RELEASE_CLAIM");
            }
        }
    }

    @Test
    void keepsReleaseReferencesWithoutComposingACompletedTruthClaimAcrossUnits() {
        List<SearchScenario> scenarios = List.of(
                new SearchScenario(
                        "주문 결제 API를 출시한 이력이 있나요?",
                        "주문 결제 API를 배포했습니다? 정산 API를 배포했습니다."),
                new SearchScenario(
                        "주문 결제 API를 출시한 이력이 있나요?",
                        "주문 결제 API 배포 여부를 확인했고 정산 API를 배포했습니다."),
                new SearchScenario(
                        "주문 결제 API 출시 이력이 있나요?",
                        "주문 결제 API를 배포했습니다?"),
                new SearchScenario(
                        "Kafka를 출시한 이력이 있나요?",
                        "Kafka를 배포하지 않고 RabbitMQ를 배포했습니다."),
                new SearchScenario(
                        "주문 API v1.2 출시 이력이 있나요?",
                        "주문 API v1.2는 배포하지 않고 v1.3을 배포했습니다."),
                new SearchScenario(
                        "주문 결제 API를 출시한 이력이 있나요?",
                        "주문 결제 API를 검토한 뒤 정산 API를 배포했습니다."));

        for (int index = 0; index < scenarios.size(); index++) {
            SearchScenario scenario = scenarios.get(index);
            VectorSearchResult candidate = candidate(
                    1_000L + index,
                    index + 1,
                    index + 1,
                    ChunkSourceType.TEXT_CHUNK,
                    scenario.content(),
                    0.90d);

            SearchEvaluationCompositeProfile.Decision decision =
                    profile.apply(scenario.query(), List.of(candidate));

            assertThat(decision.rejected()).as(scenario.toString()).isFalse();
            assertThat(decision.rejectionReasons())
                    .as(scenario.toString())
                    .doesNotContain("MISSING_ASSERTED_COMPLETED_RELEASE_CLAIM");
        }

        List<UnaryOperator<String>> opaqueTargetTransformations = List.of(
                target -> target + "하는",
                target -> target + "한",
                target -> target + "된");
        for (int index = 0; index < opaqueTargetTransformations.size(); index++) {
            String transformedTarget = opaqueTargetTransformations.get(index).apply("주문");
            String query = transformedTarget + " API를 출시한 이력이 있나요?";
            VectorSearchResult candidate = candidate(
                    1_500L + index,
                    index + 7,
                    index + 7,
                    ChunkSourceType.TEXT_CHUNK,
                    "주문 API를 배포했습니다.",
                    0.90d);

            SearchEvaluationCompositeProfile.Decision decision =
                    profile.apply(query, List.of(candidate));

            assertThat(decision.rejected()).as(query).isFalse();
            assertThat(decision.rejectionReasons())
                    .as("opaque target token: %s", query)
                    .doesNotContain("MISSING_ASSERTED_COMPLETED_RELEASE_CLAIM");
        }

        List<String> opaqueReservedTargetPhrases = List.of(
                "경험",
                "이력",
                "여부",
                "있나요",
                "출시",
                "배포",
                "배포 경험",
                "출시한 기능",
                "출시하는 기능",
                "PRIZM-v1",
                "Node.js v1.2");
        for (int index = 0; index < opaqueReservedTargetPhrases.size(); index++) {
            String target = opaqueReservedTargetPhrases.get(index) + " API";
            SearchScenario scenario = new SearchScenario(
                    target + "를 출시한 이력이 있나요?",
                    target + "를 배포했습니다.");
            VectorSearchResult candidate = candidate(
                    1_600L + index,
                    index + 40,
                    index + 40,
                    ChunkSourceType.TEXT_CHUNK,
                    scenario.content(),
                    0.90d);

            assertThat(profile.apply(scenario.query(), List.of(candidate)).rejected())
                    .as("opaque reserved target: %s", scenario)
                    .isFalse();
        }

        List<String> opaqueConjunctiveTargetTokens = List.of(
                "실험했고",
                "실험하고",
                "실험됐고",
                "실험되었고",
                "실험였고",
                "실험했으며",
                "실험했지만",
                "실험했으나",
                "실험했는데");
        for (int index = 0; index < opaqueConjunctiveTargetTokens.size(); index++) {
            String target = opaqueConjunctiveTargetTokens.get(index) + " API";
            SearchScenario scenario = new SearchScenario(
                    target + "를 출시한 이력이 있나요?",
                    target + "를 배포했습니다.");
            VectorSearchResult candidate = candidate(
                    1_700L + index,
                    index + 60,
                    index + 60,
                    ChunkSourceType.TEXT_CHUNK,
                    scenario.content(),
                    0.90d);

            assertThat(profile.apply(scenario.query(), List.of(candidate)).rejected())
                    .as("opaque conjunctive-looking target: %s", scenario)
                    .isFalse();
        }

        for (String trailingBoundary : List.of(".", "_", "-")) {
            String query = "PRIZM API를 출시한 이력이 있나요?";
            String content = "PRIZM" + trailingBoundary + " API를 배포했습니다.";
            VectorSearchResult candidate = candidate(
                    1_800L + trailingBoundary.charAt(0),
                    80,
                    80,
                    ChunkSourceType.TEXT_CHUNK,
                    content,
                    0.90d);

            SearchEvaluationCompositeProfile.Decision decision =
                    profile.apply(query, List.of(candidate));

            assertThat(decision.rejected()).as(content).isFalse();
            assertThat(decision.rejectionReasons())
                    .as("target token boundary: %s", content)
                    .doesNotContain("MISSING_ASSERTED_COMPLETED_RELEASE_CLAIM");
        }

        List<SearchScenario> acceptedIntentForms = List.of(
                new SearchScenario(
                        "주문 결제 API 출시 이력이 있나요?",
                        "주문 결제 API를 배포했습니다."),
                new SearchScenario(
                        "주문 API를 출시하였나요?",
                        "주문 API를 배포하였습니다."),
                new SearchScenario(
                        "주문하는 API를 출시한 이력이 있나요?",
                        "주문하는 API를 배포했습니다."),
                new SearchScenario(
                        "문제없이 주문 API를 출시한 이력이 있나요?",
                        "문제없이 주문 API를 배포했습니다."),
                new SearchScenario(
                        "2025년 3월 14일에 주문 API를 출시한 이력이 있나요?",
                        "2025년 3월 14일에 주문 API를 배포했습니다."),
                new SearchScenario(
                        "문제없이 주문 API를 출시한 이력이 있나요?",
                        "실제로 문제없이 주문 API를 배포했습니다."));
        for (int index = 0; index < acceptedIntentForms.size(); index++) {
            SearchScenario scenario = acceptedIntentForms.get(index);
            VectorSearchResult candidate = candidate(
                    2_000L + index,
                    index + 10,
                    index + 10,
                    ChunkSourceType.TEXT_CHUNK,
                    scenario.content(),
                    0.90d);

            assertThat(profile.apply(scenario.query(), List.of(candidate)).rejected())
                    .as(scenario.toString())
                    .isFalse();
        }
    }

    @Test
    void unsupportedLegacyCompletionGrammarStillUsesReleaseRelevanceInsteadOfTruthRejection() {
        List<SearchScenario> unsupportedScenarios = List.of(
                new SearchScenario(
                        "주문 API를 출시했습니까?",
                        "주문 API를 출시했습니까?"),
                new SearchScenario(
                        "주문 API가 출시됐나요?",
                        "주문 API가 출시됐나요?"),
                new SearchScenario(
                        "주문 API를 출시 했습니까?",
                        "주문 API를 출시 했습니까?"),
                new SearchScenario(
                        "주문 API 출시. 이력 있나요?",
                        "주문 API를 배포했습니다."),
                new SearchScenario(
                        "주문 API 출시 이력 있나요? 경험",
                        "주문 API를 배포했습니다."),
                new SearchScenario(
                        "주문 API를 출시하는 건가요?",
                        "주문 API를 배포했습니다."),
                new SearchScenario(
                        "주문 API 출시하는 이력 있나요?",
                        "주문 API를 배포했습니다."),
                new SearchScenario(
                        "주문 API 출시 경험하는",
                        "주문 API를 배포했습니다."),
                new SearchScenario(
                        "PRIZM- API를 출시한 이력이 있나요?",
                        "PRIZM API를 배포했습니다."),
                new SearchScenario(
                        "C#.NET API를 출시한 이력이 있나요?",
                        "C#.NET API를 배포했습니다."));

        for (int index = 0; index < unsupportedScenarios.size(); index++) {
            SearchScenario scenario = unsupportedScenarios.get(index);
            VectorSearchResult candidate = candidate(
                    3_000L + index,
                    index + 20,
                    index + 20,
                    ChunkSourceType.TEXT_CHUNK,
                    scenario.content(),
                    0.90d);

            SearchEvaluationCompositeProfile.Decision decision =
                    profile.apply(scenario.query(), List.of(candidate));

            assertThat(decision.rejected()).as(scenario.toString()).isFalse();
            assertThat(decision.rejectionReasons())
                    .as(scenario.toString())
                    .doesNotContain("UNSUPPORTED_COMPLETED_RELEASE_QUERY");
        }
    }

    @Test
    void doesNotComposeABareReleaseNounWithAnIntentMarkerFromAnotherQueryPhrase() {
        String query = "주문 API 출시 계획과 운영 경험을 보여줘.";
        VectorSearchResult candidate = candidate(
                3_100L,
                30,
                30,
                ChunkSourceType.TEXT_CHUNK,
                "주문 API 출시 계획과 운영 경험을 기록했다.",
                0.90d);

        SearchEvaluationCompositeProfile.Decision decision =
                profile.apply(query, List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.rejectionReasons())
                .doesNotContain(
                        "UNSUPPORTED_COMPLETED_RELEASE_QUERY",
                        "MISSING_ASSERTED_COMPLETED_RELEASE_CLAIM");
    }

    @Test
    void rejectsACompletedReleaseClaimThatAppearsOnlyInTheDocumentTitle() {
        VectorSearchResult candidate = candidateWithTitle(
                1L,
                "주문 API를 배포했습니다",
                "향후 계획만 검토한다.",
                0.90d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "주문 API를 출시했나요?", List.of(candidate));

        assertThat(decision.rejected()).isTrue();
        assertThat(decision.results()).isEmpty();
    }

    @Test
    void keepsReleasePlansAutomationNegationAndReleaseReferencesAsRelevantEvidence() {
        List<String> relatedReleaseContent = List.of(
                "주문 API 배포 계획만 세웠다.",
                "주문 API 배포 자동화만 구현했다.",
                "주문 API를 배포하지 않았다.",
                "주문 API를 배포했나요?",
                "주문 API를 배포했습니다?",
                "주문 API를 출시했습니다?",
                "주문 API를 출시한 경험이 있나요?",
                "주문 API를 배포했다는 계획만 세웠다.",
                "주문 API를 배포했다는 자동화 예제를 검토했다.",
                "주문 API 출시일 문서를 작성했다.",
                "주문 API 배포판을 검토했다.",
                "주문 API 재배포 절차를 정리했다.");

        for (int index = 0; index < relatedReleaseContent.size(); index++) {
            VectorSearchResult candidate = candidate(
                    index + 1L,
                    index + 1,
                    index + 1,
                    ChunkSourceType.TEXT_CHUNK,
                    relatedReleaseContent.get(index),
                    0.90d);

            assertThat(profile.apply("주문 API를 출시한 이력이 있나요?", List.of(candidate))
                            .rejected())
                    .as(relatedReleaseContent.get(index))
                    .isFalse();
        }

        VectorSearchResult unrelatedEnglishMarker = candidate(
                99L, 99, 99, ChunkSourceType.TEXT_CHUNK,
                "주문 completed-release-action 근거를 작성했다.", 0.90d);
        assertThat(profile.apply("주문 API를 출시한 이력이 있나요?", List.of(unrelatedEnglishMarker))
                        .rejected())
                .isTrue();
    }

    @Test
    void ignoresSentencePunctuationAtAnExactUnicodeTokenBoundary() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "담당 프로젝트는 루미나. 합성 기록이다.", 0.90d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "루미나 근거", List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);
    }

    @Test
    void preservesTechnicalIdentifierPunctuationWhileDroppingSentencePeriods() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "Node.js. C++. C#.", 0.90d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "Node.js C++ C# 근거", List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);
    }

    @Test
    void generalProfileDoesNotRejectAQueryWithoutAnExplicitEvidenceSignal() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "루미나에서 수행한 합성 프로젝트 기록", 0.90d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "근거 보여줘", List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);
    }

    @Test
    void keepsAnExactUnicodeAnchorWhenTheRelatedContentIsNegated() {
        VectorSearchResult candidate = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "루미나 경험은 없다.", 0.90d);

        SearchEvaluationCompositeProfile.Decision decision = profile.apply(
                "루미나 근거가 있어?", List.of(candidate));

        assertThat(decision.rejected()).isFalse();
        assertThat(decision.results()).containsExactly(candidate);
        assertThat(decision.rejectionReasons()).isEmpty();
    }

    @Test
    void generalProfileDoesNotRejectMismatchedNumbers() {
        VectorSearchResult exactFormatting = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "합성 요청 1,200건 처리 근거", 0.90d);
        VectorSearchResult longerNumber = candidate(
                2L, 2, 2, ChunkSourceType.TEXT_CHUNK,
                "합성 요청 18건 처리 근거", 0.90d);

        assertThat(profile.apply("합성 요청 1200건 처리 근거를 보여줘.", List.of(exactFormatting))
                        .rejected())
                .isFalse();
        assertThat(profile.apply("합성 요청 8건 처리 근거를 보여줘.", List.of(longerNumber))
                        .rejected())
                .isFalse();
    }

    @Test
    void rescuesStrongNaturalLanguageOverlapWhileNumericBindingRemainsAServiceConcern() {
        VectorSearchResult lowScore = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "위성 관제 시스템 개발 근거", 0.49d);
        VectorSearchResult wrongNumber = candidate(
                2L, 2, 1, ChunkSourceType.TEXT_CHUNK,
                "합성 요청 1200건 처리 근거", 0.70d);

        assertThat(profile.apply("위성 관제 시스템 개발 근거가 있어?", List.of(lowScore))
                        .results())
                .containsExactly(lowScore);
        assertThat(profile.apply("합성 요청 2400건 처리 근거를 보여줘.", List.of(wrongNumber))
                        .rejected())
                .isFalse();
    }

    @Test
    void rescuesOnlyTopFiveNaturalLanguageCandidatesWithSufficientExactCoreTerms() {
        VectorSearchResult related = candidate(
                10L, 10, 10, ChunkSourceType.TEXT_CHUNK,
                "장애 상황에서 복구 절차를 문서화했다.", 0.42d);
        VectorSearchResult oneTermOnly = candidate(
                11L, 11, 11, ChunkSourceType.TEXT_CHUNK,
                "장애 알림 기록", 0.42d);
        String query = "장애 상황 복구 절차를 설명해줘.";

        assertThat(profile.apply(query, List.of(related)).results())
                .containsExactly(related);
        assertThat(profile.apply(query, List.of(oneTermOnly)).rejectionReasons())
                .contains("DENSE_SCORE_BELOW_TUNING_FLOOR");

        List<VectorSearchResult> outsideTopFive = List.of(
                candidate(21L, 21, 21, ChunkSourceType.TEXT_CHUNK, "무관한 회계 문서", 0.49d),
                candidate(22L, 22, 22, ChunkSourceType.TEXT_CHUNK, "무관한 인사 문서", 0.48d),
                candidate(23L, 23, 23, ChunkSourceType.TEXT_CHUNK, "무관한 계약 문서", 0.47d),
                candidate(24L, 24, 24, ChunkSourceType.TEXT_CHUNK, "무관한 구매 문서", 0.46d),
                candidate(25L, 25, 25, ChunkSourceType.TEXT_CHUNK, "무관한 정산 문서", 0.45d),
                candidate(26L, 26, 26, ChunkSourceType.TEXT_CHUNK,
                        "장애 상황에서 복구 절차를 문서화했다.", 0.44d));

        assertThat(profile.apply(query, outsideTopFive).results()).isEmpty();
    }

    @Test
    void rescuesBelowFloorEvidenceWithReliableExactIdentifiers() {
        VectorSearchResult directEvidence = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "FCM 전송 실패가 핵심 기능에 영향을 주지 않도록 Outbox로 전송을 분리했다.",
                0.425d);
        VectorSearchResult tauri = candidate(
                2L, 2, 2, ChunkSourceType.TEXT_CHUNK,
                "검토용 reference shell은 Tauri 기반으로 구성했다.",
                0.337798d);
        VectorSearchResult quartzHarborMesh = candidate(
                3L, 3, 3, ChunkSourceType.TEXT_CHUNK,
                "격리된 라우팅 fixture는 Quartz Harbor Mesh 기반으로 기록했다.",
                0.442412d);

        assertThat(profile.apply(
                "FCM 전송 실패가 핵심 기능에 영향을 주지 않게 어떻게 설계했나요?",
                List.of(directEvidence)).rejected()).isFalse();
        assertThat(profile.apply("Tauri", List.of(tauri)).results()).containsExactly(tauri);
        assertThat(profile.apply("Quartz Harbor Mesh", List.of(quartzHarborMesh)).results())
                .containsExactly(quartzHarborMesh);
    }

    @Test
    void rescuesExactIdentifierMentionsRegardlessOfActorNegationOrReviewState() {
        List<VectorSearchResult> related = List.of(
                candidate(1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                        "FCM은 외부 전송 서비스다.", 0.425d),
                candidate(2L, 2, 2, ChunkSourceType.TEXT_CHUNK,
                        "다른 팀이 FCM을 사용했다.", 0.425d),
                candidate(3L, 3, 3, ChunkSourceType.TEXT_CHUNK,
                        "FCM을 사용하지 않았다.", 0.425d),
                candidate(4L, 4, 4, ChunkSourceType.TEXT_CHUNK,
                        "FCM 도입을 검토했다.", 0.425d));

        assertThat(profile.apply("FCM", related).results())
                .containsExactlyInAnyOrderElementsOf(related);
    }

    @Test
    void keepsDenseFloorWhenReliableExactIdentifierAnchorIsAbsentOrOnlyASubstring() {
        VectorSearchResult absent = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "Spring Boot 기반 API와 PostgreSQL을 사용했다.", 0.425d);
        VectorSearchResult substring = candidate(
                2L, 2, 2, ChunkSourceType.TEXT_CHUNK,
                "FooEngineX를 플러그인 호스트 후보로 검토했다.", 0.425d);
        VectorSearchResult partialMultiWord = candidate(
                3L, 3, 3, ChunkSourceType.TEXT_CHUNK,
                "Quartz Harbor 라우팅 구성을 검토했다.", 0.442412d);

        assertThat(profile.apply("Kafka", List.of(absent)).rejectionReasons())
                .contains("DENSE_SCORE_BELOW_TUNING_FLOOR");
        assertThat(profile.apply("FooEngine", List.of(substring)).rejectionReasons())
                .contains("DENSE_SCORE_BELOW_TUNING_FLOOR");
        assertThat(profile.apply("Quartz Harbor Mesh", List.of(partialMultiWord)).rejectionReasons())
                .contains("DENSE_SCORE_BELOW_TUNING_FLOOR");
    }

    @Test
    void currentStrictProfileKeepsPointFiveAsTheDefaultFloorWithoutAnExactAnchor() {
        VectorSearchResult atBoundary = candidate(
                1L, 1, 1, ChunkSourceType.TEXT_CHUNK,
                "Kafka를 운영 환경에서 사용한 근거", 0.50d);
        VectorSearchResult belowBoundary = candidate(
                2L, 2, 2, ChunkSourceType.TEXT_CHUNK,
                "Spring Boot 기반 API를 구현한 근거", Math.nextDown(0.50d));

        assertThat(profile.apply("Kafka 근거", List.of(atBoundary)).rejected()).isFalse();
        assertThat(profile.apply("Kafka 근거", List.of(belowBoundary)).rejectionReasons())
                .contains("DENSE_SCORE_BELOW_TUNING_FLOOR");
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

    @Test
    void supportsBoundedProjectIdentityDirectEvidenceAndExactCompletionFacts() {
        VectorSearchResult candidate = candidate(
                1L,
                1,
                1,
                ChunkSourceType.TEXT_CHUNK,
                "프로젝트 이름은 Aurora이다. 2024년 11월 8일에 8개의 REST API를 배포했다.",
                0.90d);

        List<String> directEvidenceQueries = List.of(
                "Aurora 프로젝트에서 구현한 API 하나의 직접 근거를 보여줘.",
                "Aurora에서 만든 REST endpoint가 몇 개인가요?",
                "Aurora라는 고유명사의 프로젝트 근거를 보여줘.");
        for (String query : directEvidenceQueries) {
            assertThat(profile.apply(query, List.of(candidate)).rejected())
                    .as(query)
                    .isFalse();
        }

        assertThat(profile.apply("REST API 8개를 배포했다는 직접 근거가 있나요?", List.of(candidate))
                        .rejected())
                .isFalse();
        assertThat(profile.apply("Aurora API를 배포한 날짜가 2024년 11월 8일인지 확인해줘.", List.of(candidate))
                        .rejected())
                .isFalse();

        VectorSearchResult directReleaseCandidate = candidate(
                2L,
                2,
                2,
                ChunkSourceType.TEXT_CHUNK,
                "Lumen 주문 API를 배포했다.",
                0.90d);
        assertThat(profile.apply("Lumen 주문 API를 배포한 직접 근거를 찾아줘.",
                        List.of(directReleaseCandidate)).rejected())
                .isFalse();

        VectorSearchResult scopedDirectReleaseCandidate = candidate(
                3L,
                3,
                3,
                ChunkSourceType.TEXT_CHUNK,
                "Lumen 프로젝트에서 백엔드 개발자로 참여했다. 주문 API를 배포했다.",
                0.90d);
        assertThat(profile.apply("Lumen 주문 API를 배포한 직접 근거를 찾아줘.",
                        List.of(scopedDirectReleaseCandidate)).rejected())
                .isFalse();
    }

    @Test
    void completedRetrievalKeepsRelevanceAnchorsWithoutTruthGates() {
        VectorSearchResult differentProject = candidate(
                1L,
                1,
                1,
                ChunkSourceType.TEXT_CHUNK,
                "프로젝트 이름은 Aurora이다. 프로젝트 이름은 Vega이다. 8개의 REST API를 배포했다.",
                0.90d);
        VectorSearchResult quotedOrRetractedClaim = candidate(
                2L,
                2,
                2,
                ChunkSourceType.TEXT_CHUNK,
                "프로젝트 이름은 Aurora이다. 8개의 REST API를 배포했습니다?",
                0.90d);
        VectorSearchResult compoundIdentifier = candidate(
                3L,
                3,
                3,
                ChunkSourceType.TEXT_CHUNK,
                "프로젝트 이름은 Kafka랩이다. 8개의 REST API를 배포했다.",
                0.90d);
        VectorSearchResult titleOnly = candidateWithTitle(
                4L,
                "Aurora 프로젝트 기록",
                "2024년 11월 8일에 8개의 REST API를 배포했다.",
                0.90d);

        assertThat(profile.apply("Aurora 프로젝트에서 구현한 API 하나의 직접 근거를 보여줘.",
                        List.of(differentProject)).rejected())
                .isFalse();
        assertThat(profile.apply("Aurora API를 배포한 날짜가 2024년 11월 8일인지 확인해줘.",
                        List.of(quotedOrRetractedClaim)).rejected())
                .isTrue();
        assertThat(profile.apply("Aurora API를 배포한 날짜가 2024년 11월 8일인지 확인해줘.",
                        List.of(quotedOrRetractedClaim)).rejectionReasons())
                .contains("MISSING_NUMBER:2024", "MISSING_NUMBER:11");
        assertThat(profile.apply("Kafka 프로젝트에서 구현한 API 하나의 직접 근거를 보여줘.",
                        List.of(compoundIdentifier)).rejected())
                .isFalse();
        assertThat(profile.apply("Aurora 프로젝트에서 구현한 API 하나의 직접 근거를 보여줘.",
                        List.of(titleOnly)).rejected())
                .isFalse();
        assertThat(profile.apply("Aurora API를 배포한 이력이 있나요?", List.of(titleOnly)).rejected())
                .isTrue();
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
        return candidateWithTitle(
                chunkId,
                documentId,
                documentVersionId,
                chunkNo,
                sourceIndex,
                sourceType,
                "합성 검색 문서",
                content,
                score);
    }

    private VectorSearchResult candidateWithTitle(
            long chunkId,
            String documentTitle,
            String content,
            double score) {
        return candidateWithTitle(
                chunkId,
                10L,
                20L,
                1,
                1,
                ChunkSourceType.TEXT_CHUNK,
                documentTitle,
                content,
                score);
    }

    private VectorSearchResult candidateWithTitle(
            long chunkId,
            long documentId,
            long documentVersionId,
            int chunkNo,
            int sourceIndex,
            ChunkSourceType sourceType,
            String documentTitle,
            String content,
            double score) {
        return new VectorSearchResult(
                chunkId,
                documentId,
                documentVersionId,
                documentTitle,
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

    private record SearchScenario(String query, String content) {
    }
}
