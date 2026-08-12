package com.prizm.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchSnippetGeneratorTest {

    private final SearchSnippetGenerator generator = new SearchSnippetGenerator();

    @Test
    void prioritizesTheMostRelevantSentenceAndIncludesAdjacentContext() {
        String content = String.join(
                " ",
                "프로젝트 개요를 정리했다.",
                "사용자 세션을 개선했다.",
                "Redis 캐시를 적용해 조회 지연을 줄였다.",
                "장애 시 캐시를 우회하도록 구성했다.",
                "배포 절차를 문서화했다.");

        String snippet = generator.generate("Redis 캐싱 경험", content);

        assertThat(snippet).isEqualTo(String.join(
                "\n",
                "사용자 세션을 개선했다.",
                "Redis 캐시를 적용해 조회 지연을 줄였다.",
                "장애 시 캐시를 우회하도록 구성했다."));
    }

    @Test
    void reusesTechnicalIdentifierNormalizationWhenSelectingTheRelevantSentence() {
        String content = String.join(
                " ",
                "문서의 목적을 설명했다.",
                "Spring Boot로 내부 API를 구현했다.",
                "운영 절차를 정리했다.",
                "회고 내용을 기록했다.");

        String snippet = generator.generate("Springboot 활용 경험", content);

        assertThat(snippet)
                .contains("Spring Boot로 내부 API를 구현했다.")
                .doesNotContain("회고 내용을 기록했다.");
    }

    @Test
    void experienceQueryPrefersTheIdentifierWindowWithConcreteProjectEvidence() {
        String content = String.join(
                "\n",
                "정재민",
                "Java / Spring Boot",
                "Backend",
                "신입 백엔드 개발자 | Java / Spring Boot",
                "교내 매칭 서비스를 운영하며 알림 시스템을 개선했습니다.",
                "동시성과 외부 서비스 실패 상황에서도 사용자 흐름이 유지되도록 설계했습니다.");

        String snippet = generator.generate("Springboot 활용 경험", content);

        assertThat(snippet)
                .contains("신입 백엔드 개발자 | Java / Spring Boot")
                .contains("교내 매칭 서비스를 운영하며 알림 시스템을 개선했습니다.")
                .doesNotContain("정재민\nJava / Spring Boot\nBackend");
    }

    @Test
    void keepsTheAuthenticationProblemAndSolutionAheadOfALongTechnicalStack() {
        String content = String.join(
                "\n",
                "Database / Cache MySQL / Redis ".repeat(20),
                "이메일 로그인과 Kakao 로그인이 분리되어 계정 관리 기준이 달라지는 문제 확인.",
                "Spring Security를 공통 인증 진입점으로 두고 OAuth2/JWT 흐름을 통합.",
                "동일 이메일 계정 연결과 닉네임 충돌 처리를 구현했습니다.");

        String snippet = generator.generate("이메일 로그인과 카카오 로그인을 통합한 경험", content);

        assertThat(snippet)
                .startsWith("이메일 로그인과 Kakao 로그인이 분리되어")
                .contains("Spring Security를 공통 인증 진입점으로 두고 OAuth2/JWT 흐름을 통합.")
                .doesNotContain("Database / Cache MySQL / Redis");
    }

    @Test
    void usesLeadingContextWhenNoQueryTermMatches() {
        String content = "첫 번째 문장이다. 두 번째 문장이다. 세 번째 문장이다. 네 번째 문장이다.";

        String snippet = generator.generate("Kafka 운영", content);

        assertThat(snippet).isEqualTo("첫 번째 문장이다.\n두 번째 문장이다.\n세 번째 문장이다.");
    }

    @Test
    void keepsTheSnippetWithinTheBoundedPresentationLength() {
        String content = "Redis " + "캐시 운영 근거를 상세히 기록하고 ".repeat(40) + "완료했다.";

        String snippet = generator.generate("Redis 캐싱 경험", content);

        assertThat(snippet)
                .hasSizeLessThanOrEqualTo(SearchSnippetGenerator.MAX_SNIPPET_CHARACTERS)
                .endsWith("…");
    }
}
