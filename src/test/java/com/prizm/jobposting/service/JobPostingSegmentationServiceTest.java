package com.prizm.jobposting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.jobposting.dto.response.JobPostingItemResponse;
import com.prizm.jobposting.exception.JobPostingItemLimitExceededException;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class JobPostingSegmentationServiceTest {

    private final JobPostingSegmentationService service = new JobPostingSegmentationService();

    @Test
    void separatesBulletItemsAndRemovesTheirPrefixes() {
        List<JobPostingItemResponse> items = service.segment("""
                - 플랫폼 API 개발 경험
                * 분산 캐시 운영 경험
                • 클라우드 서비스 관리 경험
                ·이벤트 처리 개선 경험
                ▪ 메시지 브로커 운영 경험
                ○관측 가능성 구축 경험
                -Spring 기반 서비스 개발 경험
                *REST API 설계 경험
                """);

        assertTexts(items,
                "플랫폼 API 개발 경험",
                "분산 캐시 운영 경험",
                "클라우드 서비스 관리 경험",
                "이벤트 처리 개선 경험",
                "메시지 브로커 운영 경험",
                "관측 가능성 구축 경험",
                "Spring 기반 서비스 개발 경험",
                "REST API 설계 경험");
    }

    @Test
    void separatesDotAndParenthesisNumberedLists() {
        List<JobPostingItemResponse> items = service.segment("""
                1. 서버 애플리케이션 개발
                2) 데이터 파이프라인 운영
                3)Quality automation
                """);

        assertTexts(items,
                "서버 애플리케이션 개발",
                "데이터 파이프라인 운영",
                "Quality automation");
    }

    @Test
    void treatsIndependentPlainLinesAsItemsAndNormalizesLineEndings() {
        List<JobPostingItemResponse> items = service.segment(
                "첫 번째 일반 항목\r\n두 번째 일반 항목\rThird plain item");

        assertTexts(items, "첫 번째 일반 항목", "두 번째 일반 항목", "Third plain item");
    }

    @Test
    void carriesGenericSectionHeadingsWithoutReturningThemAsItems() {
        List<JobPostingItemResponse> items = service.segment("""
                기본 역량
                - 서버 기능 구현 경험
                - 데이터 저장 구조 이해
                추가 조건:
                * 운영 자동화 경험
                """);

        assertThat(items).extracting(JobPostingItemResponse::section)
                .containsExactly("기본 역량", "기본 역량", "추가 조건");
        assertTexts(items, "서버 기능 구현 경험", "데이터 저장 구조 이해", "운영 자동화 경험");
    }

    @Test
    void keepsAnAmbiguousPlainLineWhenOnlyOneListItemFollows() {
        List<JobPostingItemResponse> items = service.segment("""
                Spring Boot 기반 서비스 개발 경험
                - Redis 사용 경험
                """);

        assertThat(items).extracting(JobPostingItemResponse::section).containsOnlyNulls();
        assertTexts(items, "Spring Boot 기반 서비스 개발 경험", "Redis 사용 경험");
    }

    @Test
    void keepsAMultiTokenRequirementWhenMultipleListItemsFollow() {
        List<JobPostingItemResponse> items = service.segment("""
                Spring Boot 기반 서비스 개발 경험
                - Redis 사용 경험
                - AWS 운영 경험
                """);

        assertThat(items).extracting(JobPostingItemResponse::section).containsOnlyNulls();
        assertTexts(items,
                "Spring Boot 기반 서비스 개발 경험",
                "Redis 사용 경험",
                "AWS 운영 경험");
    }

    @Test
    void keepsSectionsForListBlocksSeparatedByABlankLine() {
        List<JobPostingItemResponse> items = service.segment("""
                자격요건
                - Spring Boot 기반 서비스 개발 경험
                - Redis 사용 경험

                우대사항
                - AWS 운영 경험
                """);

        assertThat(items).extracting(JobPostingItemResponse::section)
                .containsExactly("자격요건", "자격요건", "우대사항");
        assertTexts(items,
                "Spring Boot 기반 서비스 개발 경험",
                "Redis 사용 경험",
                "AWS 운영 경험");
    }

    @Test
    void keepsCareerItemsGroupedAndExcludesRecruitingMetadataInSourceOrder() {
        List<JobPostingItemResponse> items = service.segment("""
                모집부문 및 상세내용
                백엔드 개발자 0명

                함께 할 업무에요
                ·Spring 기반 API를 설계하고 운영합니다
                ○대용량 요청 처리 구조를 개선합니다

                공통 자격요건
                - Java로 서버 애플리케이션을 개발한 경험
                - Git을 활용한 협업 경험

                이런 분이면 더 좋아요
                ▪컨테이너 환경을 운영한 경험

                복지사항
                - 점심 식대를 지원합니다
                - 장비 구매비를 지원합니다

                전형절차
                서류전형
                1차면접
                최종합격

                근무조건
                근무일시 : 09:00~18:00
                근무지 : 서울특별시

                접수기간 및 방법
                접수기간 : 2026.08.01~2026.08.31
                접수방법 : 온라인 지원

                새로운 도전:
                - 분산 환경의 장애 원인을 분석하는 분
                """);

        assertThat(items).containsExactly(
                new JobPostingItemResponse(1, "함께 할 업무에요", "Spring 기반 API를 설계하고 운영합니다"),
                new JobPostingItemResponse(2, "함께 할 업무에요", "대용량 요청 처리 구조를 개선합니다"),
                new JobPostingItemResponse(3, "공통 자격요건", "Java로 서버 애플리케이션을 개발한 경험"),
                new JobPostingItemResponse(4, "공통 자격요건", "Git을 활용한 협업 경험"),
                new JobPostingItemResponse(5, "이런 분이면 더 좋아요", "컨테이너 환경을 운영한 경험"),
                new JobPostingItemResponse(6, "새로운 도전", "분산 환경의 장애 원인을 분석하는 분"));
    }

    @Test
    void normalizesMarkdownTablesAndKoreanBulletsBeforeCreatingSearchItems() {
        List<JobPostingItemResponse> items = service.segment("""
                ## 모집부문 및 상세내용
                | **공통 자격요건** |
                | ----------- |
                | ㆍ학력 : 대졸 이상 (2,3년)<br>ㆍ경력 : 신입 ~ 1년 |
                | **백엔드 개발자** 0명 |
                | <br> |

                | **함께 할 업무에요** |
                | ------------- |
                ㆍSpring 기반 백엔드 서비스 개발 및 기존 기능 개선
                - ㆍLLM Agent를 활용한 서비스 연계 기능 개발
                - ㆍMCP 등 AI Agent 관련 기술을 활용한 기능 개발
                - ㆍREST API 및 마이크로서비스 기반 시스템 개발
                - ㆍ팀원들과 코드 리뷰 및 협업을 통한 서비스 개선

                | **이런 분을 찾고 있어요** |
                | ---------------- |
                ㆍJava와 서버 프레임워크를 활용한 개발 경험이 있으신 분
                - ㆍREST API와 기본적인 웹 서비스 구조를 이해하고 계신 분
                - ㆍGit을 활용한 프로젝트 또는 협업 경험이 있으신 분
                - **ㆍAI 등 새로운 기술에 관심이 있고 적극적으로 배우고 싶으신 분**
                - ㆍ동료와 원활하게 소통하며 함께 문제를 해결할 수 있으신 분

                **이런 분이면 더 좋아요**\\
                ㆍ생성형 AI 또는 Agent를 활용한 프로젝트 경험
                - ㆍPython 또는 React를 활용한 개발 경험
                - ㆍ컨테이너 또는 클라우드 환경을 사용해 본 경험
                - ㆍ마이크로서비스 아키텍처에 대한 학습·프로젝트 경험
                - ㆍLinux 환경 및 Shell 사용 경험

                ## 복지사항
                | ㆍ생일자 케이크 제공 |
                | ㆍ음료와 간식 제공<br>ㆍ회식 지원 |
                """);

        assertThat(items).containsExactly(
                new JobPostingItemResponse(1, "함께 할 업무에요", "Spring 기반 백엔드 서비스 개발 및 기존 기능 개선"),
                new JobPostingItemResponse(2, "함께 할 업무에요", "LLM Agent를 활용한 서비스 연계 기능 개발"),
                new JobPostingItemResponse(3, "함께 할 업무에요", "MCP 등 AI Agent 관련 기술을 활용한 기능 개발"),
                new JobPostingItemResponse(4, "함께 할 업무에요", "REST API 및 마이크로서비스 기반 시스템 개발"),
                new JobPostingItemResponse(5, "함께 할 업무에요", "팀원들과 코드 리뷰 및 협업을 통한 서비스 개선"),
                new JobPostingItemResponse(6, "이런 분을 찾고 있어요", "Java와 서버 프레임워크를 활용한 개발 경험이 있으신 분"),
                new JobPostingItemResponse(7, "이런 분을 찾고 있어요", "REST API와 기본적인 웹 서비스 구조를 이해하고 계신 분"),
                new JobPostingItemResponse(8, "이런 분을 찾고 있어요", "Git을 활용한 프로젝트 또는 협업 경험이 있으신 분"),
                new JobPostingItemResponse(9, "이런 분을 찾고 있어요", "AI 등 새로운 기술에 관심이 있고 적극적으로 배우고 싶으신 분"),
                new JobPostingItemResponse(10, "이런 분을 찾고 있어요", "동료와 원활하게 소통하며 함께 문제를 해결할 수 있으신 분"),
                new JobPostingItemResponse(11, "이런 분이면 더 좋아요", "생성형 AI 또는 Agent를 활용한 프로젝트 경험"),
                new JobPostingItemResponse(12, "이런 분이면 더 좋아요", "Python 또는 React를 활용한 개발 경험"),
                new JobPostingItemResponse(13, "이런 분이면 더 좋아요", "컨테이너 또는 클라우드 환경을 사용해 본 경험"),
                new JobPostingItemResponse(14, "이런 분이면 더 좋아요", "마이크로서비스 아키텍처에 대한 학습·프로젝트 경험"),
                new JobPostingItemResponse(15, "이런 분이면 더 좋아요", "Linux 환경 및 Shell 사용 경험"));
    }

    @Test
    void removesOnlyConservativeStandaloneMetadataAndKeepsRequirementLabels() {
        List<JobPostingItemResponse> items = service.segment("""
                백엔드 엔지니어 3명 모집
                근무시간: 09:00~18:00
                2차 인터뷰
                지원자격: API 설계 경험
                장애 대응 시간이 09:00~18:00인 시스템을 운영한 경험
                """);

        assertThat(items).containsExactly(
                new JobPostingItemResponse(1, null, "지원자격: API 설계 경험"),
                new JobPostingItemResponse(2, null, "장애 대응 시간이 09:00~18:00인 시스템을 운영한 경험"));
    }

    @Test
    void doesNotLetExcludedDuplicatesHideLaterCareerItems() {
        List<JobPostingItemResponse> items = service.segment("""
                복지사항
                - 기술 세미나 참여 지원
                우대사항
                - 기술 세미나 참여 지원
                """);

        assertThat(items).containsExactly(
                new JobPostingItemResponse(1, "우대사항", "기술 세미나 참여 지원"));
    }

    @Test
    void removesExactDuplicatesAfterUnicodeWhitespaceNormalizationAndKeepsFirstSection() {
        List<JobPostingItemResponse> items = service.segment("""
                첫 구역:
                - API\u00a0설계 경험
                다음 구역:
                * API   설계 경험
                """);

        assertThat(items).containsExactly(
                new JobPostingItemResponse(1, "첫 구역", "API 설계 경험"));
    }

    @Test
    void removesEmptyAndItemsWithFewerThanTwoLettersOrDigits() {
        List<JobPostingItemResponse> items = service.segment("""
                -
                *
                • A
                1. #
                - AB
                """);

        assertTexts(items, "AB");
    }

    @Test
    void preservesOriginalOrderAcrossPlainBulletAndNumberedItems() {
        List<JobPostingItemResponse> items = service.segment("""
                Alpha first.
                - 두 번째 항목
                3) Gamma third
                마지막 항목
                """);

        assertTexts(items, "Alpha first.", "두 번째 항목", "Gamma third", "마지막 항목");
        assertThat(items).extracting(JobPostingItemResponse::itemId).containsExactly(1, 2, 3, 4);
    }

    @Test
    void keepsMixedKoreanAndEnglishContent() {
        List<JobPostingItemResponse> items = service.segment("""
                - API 서버 design 경험
                - event-driven 아키텍처 운영
                """);

        assertTexts(items, "API 서버 design 경험", "event-driven 아키텍처 운영");
    }

    @Test
    void handlesArbitraryTechnologyNamesWithoutAProductDictionary() {
        List<JobPostingItemResponse> items = service.segment("""
                - ZephyrDB 기반 처리 경험
                - NimbusQueue 운영 자동화
                - AuroraMesh troubleshooting
                """);

        assertTexts(items,
                "ZephyrDB 기반 처리 경험",
                "NimbusQueue 운영 자동화",
                "AuroraMesh troubleshooting");
    }

    @Test
    void preservesLeadingSyntaxThatIsNotAStructuralBullet() {
        List<JobPostingItemResponse> items = service.segment("""
                -10ms 응답 목표
                --enable-preview 옵션 사용
                *.java 파일 처리
                """);

        assertTexts(items, "-10ms 응답 목표", "--enable-preview 옵션 사용", "*.java 파일 처리");
    }

    @Test
    void separatesClearSentenceBoundariesAndNormalizesUnicodeWhitespace() {
        List<JobPostingItemResponse> items = service.segment(
                "서비스\u2003개발 경험. 데이터\t운영 경험! English quality work?");

        assertTexts(items, "서비스 개발 경험.", "데이터 운영 경험!", "English quality work?");
    }

    @Test
    void splitsLongItemsAtGenericBoundariesWithoutExceedingTheSearchQueryLimit() {
        String content = String.join(" ", Collections.nCopies(90, "일반경계문장단위"));

        List<JobPostingItemResponse> items = service.segment("- " + content);

        assertThat(items).hasSizeGreaterThan(1);
        assertThat(items).allSatisfy(item ->
                assertThat(item.text()).hasSizeLessThanOrEqualTo(JobPostingSegmentationService.MAX_ITEM_LENGTH));
        assertThat(String.join(" ", items.stream().map(JobPostingItemResponse::text).toList()))
                .isEqualTo(content);
    }

    @Test
    void rebalancesAOneCharacterTailWithoutWhitespace() {
        String content = "x".repeat(JobPostingSegmentationService.MAX_ITEM_LENGTH + 1);

        List<JobPostingItemResponse> items = service.segment(content);

        assertThat(items).extracting(item -> item.text().length()).containsExactly(499, 2);
        assertThat(items).allSatisfy(item -> {
            assertThat(item.text()).hasSizeLessThanOrEqualTo(JobPostingSegmentationService.MAX_ITEM_LENGTH);
            assertThat(item.text().codePoints().filter(Character::isLetterOrDigit).count())
                    .isGreaterThanOrEqualTo(2);
        });
        assertThat(items.stream().map(JobPostingItemResponse::text).collect(Collectors.joining()))
                .isEqualTo(content);
    }

    @Test
    void allowsOneHundredItemsAndRejectsAdditionalSearchFanOut() {
        String oneHundred = numberedItems(JobPostingSegmentationService.MAX_ITEM_COUNT);
        String oneHundredAndOne = numberedItems(JobPostingSegmentationService.MAX_ITEM_COUNT + 1);

        assertThat(service.segment(oneHundred)).hasSize(JobPostingSegmentationService.MAX_ITEM_COUNT);
        assertThatThrownBy(() -> service.segment(oneHundredAndOne))
                .isInstanceOf(JobPostingItemLimitExceededException.class)
                .hasMessage("job posting must produce at most 100 items");
    }

    private static String numberedItems(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(index -> "- 일반 항목 %03d".formatted(index))
                .collect(Collectors.joining("\n"));
    }

    private static void assertTexts(List<JobPostingItemResponse> items, String... expected) {
        assertThat(items).extracting(JobPostingItemResponse::text).containsExactly(expected);
    }
}
