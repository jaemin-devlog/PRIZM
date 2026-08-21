package com.prizm.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SearchSnippetGeneratorTest {

    private final SearchSnippetGenerator generator = new SearchSnippetGenerator();

    @Test
    void excludesPersonalAndProfileMetadataFromEvidenceCandidates() {
        String content = String.join(
                "\n",
                "홍길동",
                "CONTACT",
                "EMAIL developer@example.com",
                "PHONE 010-1234-5678",
                "GITHUB https://github.com/example",
                "EDUCATION 한빛대학교",
                "GPA 4.13 / 4.5",
                "2027.02 졸업 예정",
                "Spring Boot에서 Outbox Worker를 구현해 알림 중복 처리를 방지했습니다.");

        String snippet = generator.generate("Spring Boot로 어떤 백엔드 경험이 있어?", content);

        assertThat(snippet)
                .contains("Outbox Worker를 구현해")
                .doesNotContain("developer@example.com")
                .doesNotContain("010-1234-5678")
                .doesNotContain("github.com")
                .doesNotContain("GPA")
                .doesNotContain("졸업 예정");
    }

    @Test
    void separatesInlineContactMetadataFromAValidEvidenceSentence() {
        String content = "동시 요청의 중복 저장을 막아 처리 흐름을 개선했습니다. EMAIL sample@example.com";

        String snippet = generator.generate("동시성 문제를 어떻게 해결했어?", content);

        assertThat(snippet).isEqualTo("동시 요청의 중복 저장을 막아 처리 흐름을 개선했습니다.");
        assertThat(snippet).doesNotContain("sample@example.com");
        assertThat(generator.addFollowingSourceSentence(content, snippet)).isEqualTo(snippet);
    }

    @Test
    void excludesHeadingsAndGuideCopyInFavorOfConcreteResolutionEvidence() {
        String content = String.join(
                "\n",
                "동시성 정합성 테스트 결과",
                "04 대표 문제 해결 사례",
                "포트폴리오에서 상세하게 정리한 3개 대표 문제 해결 사례를 간단히 요약했습니다.",
                "여러 Worker가 같은 이벤트를 처리하는 문제를 FOR UPDATE SKIP LOCKED로 해결했습니다.",
                "통합 테스트에서 중복 처리 0건을 검증했습니다.");

        String snippet = generator.generate("동시성 문제를 어떻게 해결했어?", content);

        assertThat(snippet)
                .contains("FOR UPDATE SKIP LOCKED로 해결했습니다.")
                .doesNotContain("동시성 정합성 테스트 결과")
                .doesNotContain("대표 문제 해결 사례를 간단히 요약했습니다.");
    }

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

        assertThat(snippet).isEqualTo("Redis 캐시를 적용해 조회 지연을 줄였다.");
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
                "테스트 사용자",
                "Java / Spring Boot",
                "Backend",
                "신입 백엔드 개발자 | Java / Spring Boot",
                "지역 커뮤니티 서비스를 운영하며 알림 시스템을 개선했습니다.",
                "동시성과 외부 서비스 실패 상황에서도 사용자 흐름이 유지되도록 설계했습니다.");

        String snippet = generator.generate("Springboot 활용 경험", content);

        assertThat(snippet)
                .startsWith("지역 커뮤니티 서비스를 운영하며 알림 시스템을 개선했습니다.")
                .doesNotContain("Java / Spring Boot")
                .doesNotContain("테스트 사용자");
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
    void directAuthenticationCoverageOutranksUnrelatedProblemActionResultSignals() {
        String content = String.join(
                "\n",
                "동시 매칭 요청에서 중복 확정 문제가 발생했습니다.",
                "Redis 잠금과 상태 재검증을 적용해 중복 저장을 방지했습니다.",
                "이메일 로그인과 Kakao 로그인이 분리되어 계정 관리 기준이 달라지는 문제를 확인했습니다.",
                "Spring Security를 공통 인증 진입점으로 두고 OAuth2/JWT 흐름을 통합했습니다.",
                "동일 이메일 계정 연결과 닉네임 충돌 처리를 구현했습니다.");

        String snippet = generator.generate("이메일 로그인과 카카오 로그인을 통합한 경험", content);

        assertThat(snippet)
                .startsWith("이메일 로그인과 Kakao 로그인이 분리되어")
                .contains("OAuth2/JWT 흐름을 통합했습니다.")
                .doesNotContain("동시 매칭 요청")
                .doesNotContain("Redis 잠금");
    }

    @Test
    void exactIdentifierAnchorKeepsWrappedOriginalSentenceComplete() {
        String content = String.join(
                "\n",
                "상태를 대기 / 처리 중 / 발송 완료 / 최종 실패로 나누어 추적했습니다.",
                "Worker는 처리 대상 이벤트를 FOR UPDATE SKIP LOCKED로 조회해 여러 Worker가 동시에 실행되어도 같은 이벤트를 중복 처리하지",
                "않도록 했습니다.",
                "운영 절차를 문서화했습니다.");

        String snippet = generator.generate("FOR UPDATE SKIP LOCKED", content);

        assertThat(snippet)
                .isEqualTo("Worker는 처리 대상 이벤트를 FOR UPDATE SKIP LOCKED로 조회해 여러 Worker가 동시에 실행되어도 "
                        + "같은 이벤트를 중복 처리하지\n않도록 했습니다.");
    }

    @Test
    void experienceQueryDemotesGenericCommaSeparatedTechnologyLists() {
        String content = String.join(
                "\n",
                "Java, Spring Boot, MySQL, Redis",
                "Spring Boot를 기반으로 인증 API를 구현했습니다.",
                "통합 테스트를 적용해 인증 흐름을 검증했습니다.");

        String snippet = generator.generate("Springboot 활용 경험", content);

        assertThat(snippet)
                .startsWith("Spring Boot를 기반으로 인증 API를 구현했습니다.")
                .doesNotContain("Java, Spring Boot, MySQL, Redis");
    }

    @Test
    void usesLeadingContextWhenNoQueryTermMatches() {
        String content = "첫 번째 문장이다. 두 번째 문장이다. 세 번째 문장이다. 네 번째 문장이다.";

        String snippet = generator.generate("Kafka 운영", content);

        assertThat(snippet).isEqualTo("첫 번째 문장이다.");
    }

    @Test
    void preservesOneLongOriginalSentenceWithoutGeneratedTruncation() {
        String content = "Redis " + "캐시 운영 근거를 상세히 기록하고 ".repeat(40) + "완료했다.";

        String snippet = generator.generate("Redis 캐싱 경험", content);

        assertThat(snippet).isEqualTo(content);
    }

    @Test
    void returnsEmptyForMissingContentAndPreservesShortContent() {
        assertThat(generator.generate("동시성", null)).isEmpty();
        assertThat(generator.generate("동시성", "   ")).isEmpty();
        assertThat(generator.generate("동시성", "동시성 제어를 구현했다."))
                .isEqualTo("동시성 제어를 구현했다.");
    }

    @Test
    void selectsAtMostThreeCompleteSentencesFromTheOriginalContent() {
        String content = String.join(
                " ",
                "초기 요청은 동시에 같은 행을 갱신해 중복 결과가 생기는 문제가 있었다.",
                "트랜잭션 범위를 분리했다.",
                "FOR UPDATE SKIP LOCKED로 처리 대상을 선점했다.",
                "상태를 다시 검증해 중복 갱신을 방지했다.",
                "운영 절차도 문서화했다.");

        String snippet = generator.generate("FOR UPDATE SKIP LOCKED 적용 경험", content);

        assertThat(snippet.lines()).hasSizeLessThanOrEqualTo(SearchSnippetGenerator.MAX_SNIPPET_SENTENCES);
        assertThat(snippet.lines()).allMatch(content::contains);
        assertThat(snippet)
                .contains("FOR UPDATE SKIP LOCKED로 처리 대상을 선점했다.")
                .doesNotContain("…");
    }

    @Test
    void representativeQueriesPreferDirectExtractiveEvidence() {
        assertRepresentativeSelection(
                "동시성",
                "기술 스택은 Java와 PostgreSQL이다. "
                        + "동시성 상황에서 같은 매칭이 두 번 확정되는 문제가 있었다. "
                        + "행 잠금과 상태 재확인을 적용해 중복 확정을 방지했다.",
                "동시성 상황에서 같은 매칭이 두 번 확정되는 문제가 있었다.");
        assertRepresentativeSelection(
                "알림",
                "FCM / Redis / Java. "
                        + "내부 알림은 DB에 먼저 저장하고 외부 전송 요청은 Outbox로 분리했다. "
                        + "외부 전송 실패에도 내부 알림은 유지됐다.",
                "내부 알림은 DB에 먼저 저장하고 외부 전송 요청은 Outbox로 분리했다.");
        assertRepresentativeSelection(
                "Springboot 활용 경험",
                "Java / Spring Boot / MySQL. "
                        + "Spring Boot를 기반으로 인증 API를 구현했다. "
                        + "공통 예외 처리와 통합 테스트를 적용했다.",
                "Spring Boot를 기반으로 인증 API를 구현했다.");
        assertRepresentativeSelection(
                "FOR UPDATE SKIP LOCKED",
                "작업자가 같은 대상을 처리하는 문제가 있었다. "
                        + "FOR UPDATE SKIP LOCKED로 한 작업만 선점했다. "
                        + "중복 처리를 방지했다.",
                "FOR UPDATE SKIP LOCKED로 한 작업만 선점했다.");
        assertRepresentativeSelection(
                "이메일 로그인과 카카오 로그인을 통합한 경험",
                "이메일 로그인과 Kakao 로그인이 분리되어 계정 관리 기준이 달라지는 문제를 확인했다. "
                        + "Spring Security를 공통 인증 진입점으로 두고 OAuth2/JWT 흐름을 통합했다.",
                "Spring Security를 공통 인증 진입점으로 두고 OAuth2/JWT 흐름을 통합했다.");
        assertRepresentativeSelection(
                "TourAPI 병렬 처리 경험",
                "Tour API 호출을 순차 처리해 응답이 지연되는 문제가 있었다. "
                        + "독립 요청을 병렬로 전환하고 실패 결과를 분리했다.",
                "독립 요청을 병렬로 전환하고 실패 결과를 분리했다.");
        assertRepresentativeSelection(
                "2,329행 중 675건 갱신",
                "전체 2,329행을 검증했다. "
                        + "조건에 맞는 675건만 선별해 갱신했다. "
                        + "나머지 행은 원래 상태를 유지했다.",
                "조건에 맞는 675건만 선별해 갱신했다.");
    }

    private void assertRepresentativeSelection(String query, String content, String expectedSentence) {
        String snippet = generator.generate(query, content);

        assertThat(snippet).contains(expectedSentence);
        assertThat(snippet.lines()).hasSizeBetween(1, SearchSnippetGenerator.MAX_SNIPPET_SENTENCES);
        assertThat(snippet.lines()).allMatch(content::contains);
    }
}
