package com.prizm.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EvidenceSentenceScorerTest {

    private final SearchSnippetGenerator generator = new SearchSnippetGenerator();

    @Test
    void selectsTheShortestCompleteActionAndDeploymentWindow() {
        String content = String.join(
                "\n",
                "서버 재시작이나 배포 시 Spring Boot / MySQL / Redis를 각각 관리해야 하는 운영 부담 확인.",
                "배포 환경 구축을 담당해 GCP Ubuntu 서버에서 Docker Compose로 세 서비스를 함께 실행하도록 구성.",
                "도메인 연결과 HTTPS 설정도 적용했습니다.");

        var selection = generator.select("Springboot 활용 경험", content);

        assertThat(selection.snippet())
                .as("candidates=%s", selection.candidateWindows())
                .isEqualTo(content.lines().limit(2).collect(java.util.stream.Collectors.joining("\n")));
    }

    @Test
    void combinesHardWrappedImplementationAndProductionState() {
        String content = String.join(
                "\r\n",
                "검증 가능한 핵심 경험",
                "ROS 2 lifecycle 전환기를 직접 구현했다. 제어 노드가 준비되지 않으면 주행 명령을 열지 않는",
                "상태기를 생산 로봇 186대에 배포했다.",
                "1.1 요구사항과 소유 범위",
                "다른 설명이다.");

        String snippet = generator.generate(
                "ROS 2 lifecycle 상태기를 직접 구현해 생산 로봇에 배포했나요?",
                content);

        assertThat(snippet)
                .contains("ROS 2 lifecycle 전환기를 직접 구현했다.")
                .contains("생산 로봇 186대에 배포했다.")
                .doesNotContain("다른 설명");
    }

    @Test
    void includesAdjacentNumericResultForAnIncidentQuestion() {
        String content = String.join(
                "\n",
                "GDAL worker의 메모리 급증 장애를 대응했다. 타일 단위 처리와 작업 상한으로 peak RSS를",
                "11기가바이트에서 3.2기가바이트로 줄였다.");

        String snippet = generator.generate(
                "GDAL worker 메모리 장애에서 peak RSS를 11GB에서 3.2GB로 줄였나요?",
                content);

        assertThat(snippet)
                .contains("메모리 급증 장애")
                .contains("11기가바이트에서 3.2기가바이트");
    }

    @Test
    void keepsASingleCompleteSentenceWhenAdjacentContextAddsNoClaimDetail() {
        String content = "Redis 캐시를 적용해 조회 지연을 줄였다. "
                + "장애 시 캐시를 우회하도록 구성했다. 배포 절차를 문서화했다.";

        assertThat(generator.generate("Redis 캐싱 경험", content))
                .isEqualTo("Redis 캐시를 적용해 조회 지연을 줄였다.");
    }

    @Test
    void includesConcreteAdjacentActionDetailForAProblemSolvingQuestion() {
        String content = "모바일 네트워크 지연 때문에 한 시간 뒤 도착하는 로그가 일 집계에서 빠지는 문제를 해결했다. "
                + "워터마크를 무작정 늘리지 않고 27분까지는 누적 창에 반영하고 그 이후 데이터만 보정 작업으로 보냈다. "
                + "누락률은 1.8퍼센트에서 0.16퍼센트로 줄었다.";

        String snippet = generator.generate("늦게 도착한 이벤트가 일 집계에서 빠지는 문제를 해결했나요?", content);

        assertThat(snippet)
                .contains("일 집계에서 빠지는 문제")
                .contains("그 이후 데이터만 보정 작업으로 보냈다")
                .doesNotContain("누락률은");
    }

    @Test
    void prefersTheShortestContiguousProblemAndRecoveryWindowForHowQuestion() {
        String content = "새 입력 처리 실패로 이전 조회 결과도 사라질 수 있었습니다. "
                + "검증이 끝난 뒤에만 새 상태를 전환하고 중단된 작업을 회복해 이전 결과를 유지했습니다. "
                + "별도 안내 문구를 정리했습니다.";

        String snippet = generator.generate("새 입력 처리 실패에도 이전 조회 결과를 어떻게 유지했나요?", content);

        assertThat(snippet)
                .contains("새 입력 처리 실패")
                .contains("검증이 끝난 뒤에만 새 상태를 전환")
                .doesNotContain("별도 안내 문구");
    }
}
