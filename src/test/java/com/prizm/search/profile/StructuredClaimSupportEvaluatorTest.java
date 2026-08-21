package com.prizm.search.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.repository.VectorSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuredClaimSupportEvaluatorTest {

    private final StructuredClaimSupportEvaluator evaluator = new StructuredClaimSupportEvaluator();

    @Test
    void supportsAnAffirmativeEntityActionAndProductionStateInOneClaimWindow() {
        ClaimSupportDecision decision = evaluator.evaluate(
                "KeyDB consumer를 직접 구현해 production에 운영했나요?",
                "KeyDB consumer를 직접 구현했다. 해당 경로를 production 환경에 배포하고 운영했다.");

        assertThat(decision.status()).isEqualTo(ClaimSupportDecision.Status.SUPPORTED);
        assertThat(decision.directSupport()).isTrue();
    }

    @Test
    void contradictsAnExplicitNotAdoptedTargetInsteadOfTreatingSimilarityAsEvidence() {
        ClaimSupportDecision decision = evaluator.evaluate(
                "ActiveMQ를 알림 서비스에 도입했나요?",
                "ActiveMQ를 후보로 검토했지만 알림 서비스에는 도입하지 않았다.");

        assertThat(decision.status()).isEqualTo(ClaimSupportDecision.Status.CONTRADICTED);
        assertThat(decision.reasons()).contains(ClaimSupportDecision.Reason.NOT_ADOPTED);
    }

    @Test
    void prototypeUseSupportsUseExperienceButNotAProductionRequirement() {
        String candidate = "KeyDB를 prototype에서 직접 사용해 만료 정책을 검증했다.";

        assertThat(evaluator.evaluate("KeyDB를 사용한 경험이 있나요?", candidate).status())
                .isEqualTo(ClaimSupportDecision.Status.SUPPORTED);
        assertThat(evaluator.evaluate("KeyDB를 production에 적용했나요?", candidate).status())
                .isEqualTo(ClaimSupportDecision.Status.CONTRADICTED);
    }

    @Test
    void supportsProjectScopedTechnologyDeclarationsAndDirectTechnologyUse() {
        ClaimSupportDecision stackDeclaration = evaluator.evaluate(
                "PostgreSQL을 사용한 프로젝트가 있나요?",
                "Project Alpha\n기술: Java, PostgreSQL, Redis");
        ClaimSupportDecision directUse = evaluator.evaluate(
                "OAuth2 사용 경험이 있나요?",
                "OAuth2로 사용자 계정을 연결했다.");

        assertThat(stackDeclaration.status()).isEqualTo(ClaimSupportDecision.Status.SUPPORTED);
        assertThat(directUse.status()).isEqualTo(ClaimSupportDecision.Status.SUPPORTED);
    }

    @Test
    void keepsSimpleTechnologyUsageRejectionsForNotAdoptedAndOtherActorClaims() {
        ClaimSupportDecision notAdopted = evaluator.evaluate(
                "Kafka를 사용한 경험이 있나요?",
                "Kafka를 검토했지만 도입하지 않았다.");
        ClaimSupportDecision otherActor = evaluator.evaluate(
                "Redis를 사용한 경험이 있나요?",
                "다른 팀에서 Redis를 사용했다.");
        ClaimSupportDecision comparison = evaluator.evaluate(
                "Kafka를 사용한 경험이 있나요?",
                "Kafka와 RabbitMQ 기술을 비교했다.");

        assertThat(notAdopted.status()).isEqualTo(ClaimSupportDecision.Status.CONTRADICTED);
        assertThat(notAdopted.reasons()).contains(ClaimSupportDecision.Reason.NOT_ADOPTED);
        assertThat(otherActor.status()).isEqualTo(ClaimSupportDecision.Status.CONTRADICTED);
        assertThat(otherActor.reasons()).contains(ClaimSupportDecision.Reason.ACTOR_MISMATCH);
        assertThat(comparison.status()).isEqualTo(ClaimSupportDecision.Status.UNSUPPORTED);
    }

    @Test
    void rejectsAPrototypeThatWasRemovedFromTheRequestedProductionPath() {
        ClaimSupportDecision decision = evaluator.evaluate(
                "Pulsar를 생산 이벤트 파이프라인에서 운영했나요?",
                "Pulsar는 prototype에서 비교했지만 생산 이벤트 파이프라인에서는 제거했다.");

        assertThat(decision.status()).isEqualTo(ClaimSupportDecision.Status.CONTRADICTED);
        assertThat(decision.reasons()).contains(ClaimSupportDecision.Reason.NOT_ADOPTED);
    }

    @Test
    void bindsAnEntityAndActionAcrossAThreeSentenceClaimWindow() {
        ClaimSupportDecision decision = evaluator.evaluate(
                "VictoriaMetrics 수집 경로를 생산 트래픽에 적용했나요?",
                "VictoriaMetrics로 수집 경로를 구축했다. 큐는 tenant별로 분리했다. "
                        + "설계와 검증을 직접 수행해 생산 트래픽에 적용했다.");

        assertThat(decision.status()).isEqualTo(ClaimSupportDecision.Status.SUPPORTED);
    }

    @Test
    void compositeAcceptsACompoundDeploymentClaimWhenEachActionIsDirectlySupported() {
        CompositeSearchProfile profile = new CompositeSearchProfile();
        VectorSearchResult candidate = candidate(
                30L,
                "Argo Rollouts로 canary revision을 배포하고 오류 revision을 즉시 중단했다.",
                0.48d);

        CompositeSearchProfile.Decision decision = profile.apply(
                "Argo Rollouts로 canary revision을 배포하고 오류 revision을 중단했나요?",
                List.of(candidate));

        assertThat(decision.results()).containsExactly(candidate);
    }

    @Test
    void scopesNegationToTheRequestedAction() {
        ClaimSupportDecision supported = evaluator.evaluate(
                "KeyDB를 사용해 DB를 직접 조회하지 않도록 개선했나요?",
                "KeyDB를 사용해 요청마다 DB를 직접 조회하지 않도록 캐시 경로를 개선했다.");
        ClaimSupportDecision contradicted = evaluator.evaluate(
                "충돌 데이터를 자동 덮어썼나요?",
                "충돌 데이터는 자동 덮어쓰지 않고 검토 대기열로 보냈다.");

        assertThat(supported.status()).isEqualTo(ClaimSupportDecision.Status.SUPPORTED);
        assertThat(contradicted.status()).isEqualTo(ClaimSupportDecision.Status.CONTRADICTED);
        assertThat(contradicted.reasons()).contains(ClaimSupportDecision.Reason.NEGATED_TARGET_CLAIM);
    }

    @Test
    void rejectsAnExplicitOtherActorForTheTargetClaim() {
        ClaimSupportDecision decision = evaluator.evaluate(
                "Nomad cluster를 직접 운영했나요?",
                "다른 팀이 Nomad cluster를 운영했으며 본인은 배포 상태만 조회했다.");

        assertThat(decision.status()).isEqualTo(ClaimSupportDecision.Status.CONTRADICTED);
        assertThat(decision.reasons()).contains(ClaimSupportDecision.Reason.ACTOR_MISMATCH);
    }

    @Test
    void bindsNegationActorAndActionInsideTheRelatedClaimScope() {
        ClaimSupportDecision unrelatedNotAdopted = evaluator.evaluate(
                "Redpanda를 사용한 production 주문 서비스가 있나요?",
                "Redpanda로 주문 이벤트 consumer를 직접 구현해 production에 배포했다. "
                        + "Apache Flink는 비교했지만 집계 경로에는 채택하지 않았다.");
        ClaimSupportDecision directRecovery = evaluator.evaluate(
                "실패한 settlement batch를 어떻게 복구했나요?",
                "실패한 settlement batch를 마지막 checkpoint부터 다시 실행했다.");
        ClaimSupportDecision otherActor = evaluator.evaluate(
                "관리 화면을 직접 구현했나요?",
                "관리 화면은 디자인 파트너가 만들었으며 본인은 화면 구현을 담당하지 않았다.");

        assertThat(unrelatedNotAdopted.status()).isEqualTo(ClaimSupportDecision.Status.SUPPORTED);
        assertThat(directRecovery.status()).isEqualTo(ClaimSupportDecision.Status.SUPPORTED);
        assertThat(otherActor.status()).isEqualTo(ClaimSupportDecision.Status.CONTRADICTED);
        assertThat(otherActor.reasons()).containsAnyOf(
                ClaimSupportDecision.Reason.ACTOR_MISMATCH,
                ClaimSupportDecision.Reason.NEGATED_TARGET_CLAIM);
    }

    @Test
    void bindsNumericValueAndUnitInsteadOfAcceptingANearbyDifferentValue() {
        ClaimSupportDecision supported = evaluator.evaluate(
                "P95 응답 시간을 760밀리초로 개선했나요?",
                "P95 응답 시간을 1.9초에서 760밀리초로 개선했다.");
        ClaimSupportDecision contradicted = evaluator.evaluate(
                "P95 응답 시간을 710밀리초로 개선했나요?",
                "P95 응답 시간을 1.9초에서 760밀리초로 개선했다.");

        assertThat(supported.status()).isEqualTo(ClaimSupportDecision.Status.SUPPORTED);
        assertThat(contradicted.status()).isEqualTo(ClaimSupportDecision.Status.CONTRADICTED);
        assertThat(contradicted.reasons()).contains(ClaimSupportDecision.Reason.NUMERIC_VALUE_MISMATCH);
    }

    @Test
    void distinguishesCostFromLatencyEvenWhenTheNumericAnchorMatches() {
        ClaimSupportDecision decision = evaluator.evaluate(
                "스토리지 비용을 35퍼센트 줄였나요?",
                "API 응답 지연을 35퍼센트 줄였다.");

        assertThat(decision.status()).isEqualTo(ClaimSupportDecision.Status.CONTRADICTED);
        assertThat(decision.reasons()).contains(ClaimSupportDecision.Reason.METRIC_MISMATCH);
    }

    @Test
    void compositeEligibilityRescuesDirectSupportBelowFloorAndRejectsUnboundSimilarity() {
        CompositeSearchProfile profile = new CompositeSearchProfile();
        VectorSearchResult direct = candidate(
                1L,
                "ScyllaDB compaction 작업을 직접 구현해 실행 시간을 21분에서 8분으로 줄였다.",
                0.46d);
        VectorSearchResult relatedOnly = candidate(
                2L,
                "분산 데이터베이스 운영 원칙과 compaction 개념을 교육했다.",
                0.81d);

        CompositeSearchProfile.Decision decision = profile.apply(
                "ScyllaDB compaction 실행 시간을 21분에서 8분으로 줄였나요?",
                List.of(relatedOnly, direct));

        assertThat(decision.results()).extracting(VectorSearchResult::chunkId).contains(1L);
        assertThat(decision.results()).extracting(VectorSearchResult::chunkId).doesNotContain(2L);
    }

    @Test
    void supportsDirectUnknownVocabularyWithoutReopeningExplicitContradictions() {
        ClaimSupportDecision unknownMetric = evaluator.evaluate(
                "야간 정산을 61분에서 19분으로 개선했나요?",
                "야간 정산은 병렬 처리로 61분에서 19분으로 단축했습니다.");
        ClaimSupportDecision unknownAction = evaluator.evaluate(
                "packet decoder 실패 후 점검 결과를 어떻게 복구했나요?",
                "packet decoder가 실패하면 마지막 cursor 이후부터 이어 처리해 점검 결과를 보존했습니다.");
        ClaimSupportDecision mereMention = evaluator.evaluate(
                "packet decoder 실패 후 점검 결과를 어떻게 복구했나요?",
                "packet decoder 실패와 점검 결과는 운영 문서의 목차 항목입니다.");
        ClaimSupportDecision wrongMetric = evaluator.evaluate(
                "스토리지 비용을 35퍼센트 줄였나요?",
                "API 응답 지연을 35퍼센트 줄였습니다.");
        ClaimSupportDecision wrongNumber = evaluator.evaluate(
                "야간 정산을 61분에서 17분으로 개선했나요?",
                "야간 정산은 병렬 처리로 61분에서 19분으로 단축했습니다.");

        assertThat(unknownMetric.status()).isEqualTo(ClaimSupportDecision.Status.SUPPORTED);
        assertThat(unknownMetric.directSupport()).isTrue();
        assertThat(unknownAction.status()).isEqualTo(ClaimSupportDecision.Status.SUPPORTED);
        assertThat(unknownAction.directSupport()).isTrue();
        assertThat(mereMention.status()).isEqualTo(ClaimSupportDecision.Status.UNSUPPORTED);
        assertThat(wrongMetric.status()).isEqualTo(ClaimSupportDecision.Status.CONTRADICTED);
        assertThat(wrongNumber.status()).isEqualTo(ClaimSupportDecision.Status.CONTRADICTED);
    }

    @Test
    void treatsDescriptiveActionQuestionsAsDirectClaimsBelowTheDenseFloor() {
        CompositeSearchProfile profile = new CompositeSearchProfile();
        VectorSearchResult direct = candidate(
                41L,
                "요청이 급증하면 읽기 전용 경로로 전환하는 격리 절차를 설계했습니다.",
                0.44d);
        VectorSearchResult mention = candidate(
                42L,
                "요청 급증과 격리 절차를 다룬 운영 가이드입니다.",
                0.83d);

        CompositeSearchProfile.Decision decision = profile.apply(
                "요청 급증 시 어떤 격리 절차로 전환했나요?",
                List.of(mention, direct));

        assertThat(decision.results()).extracting(VectorSearchResult::chunkId).contains(41L);
        assertThat(decision.results()).extracting(VectorSearchResult::chunkId).doesNotContain(42L);
    }

    private static VectorSearchResult candidate(Long id, String content, double score) {
        return new VectorSearchResult(
                id,
                id,
                id,
                "synthetic",
                1,
                id.intValue(),
                null,
                ChunkSourceType.TEXT_CHUNK,
                id.intValue(),
                "텍스트 구간 " + id,
                content,
                1.0d - score,
                score);
    }
}
