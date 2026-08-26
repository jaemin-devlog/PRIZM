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
    void treatsMixedBulletMarkersAtTheSameIndentationAsSiblingLeaves() {
        List<JobPostingItemResponse> items = service.segment("""
                담당업무
                • 첫 번째 업무 문장
                - 두 번째 업무 문장
                • 세 번째 업무 문장
                """);

        assertThat(items).containsExactly(
                new JobPostingItemResponse(1, "담당업무", "첫 번째 업무 문장"),
                new JobPostingItemResponse(2, "담당업무", "두 번째 업무 문장"),
                new JobPostingItemResponse(3, "담당업무", "세 번째 업무 문장"));
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
    void keepsOnlySearchableLeafRequirementsFromRealisticNestedPosting() {
        List<JobPostingItemResponse> items = service.segment("""
                포지션 상세
                아타드(ATAD)는,
                전세계 클라우드 인프라를 AI를 통해 24시간 자율운용하는 차세대 가상 데이터 센터(V.D.C) 플랫폼 '오딘(ODiiN)'을 서비스하는 B2B 딥테크 기업입니다.

                ► AI-Native Engineer
                AI를 활용해 제품의 속도를 높일 백엔드 개발자를 찾습니다.
                반복적인 구현은 AI에 맡기고, 개발자는 무엇을 왜 만들어야하는지 판단하며, 제품 설계와 검증, 복잡한 문제 해결에 집중합니다.

                주요업무
                ► 제품 설계·개발
                • 서비스 설계 및 백엔드 개발
                  - 기능 설계, API, 권한 정책, 핵심 도메인 설계 및 구현
                • 안정적인 서비스 구조 설계
                  - 인증·인가, 데이터 정합성, 트랜잭션 관리

                ► 멀티클라우드
                • 멀티클라우드 연동
                  - AWS-Azure-GCP등 API 및 계정 연동
                • 비용·결제 시스템 운영
                  - 비용 파이프라인, 배치, 구독·결제 프로세스 관리

                ► 운영·개선
                • 서비스 운영 및 개선
                  - 피드백 기반 기능 개선과 안정적인 배포
                • AI 기반 개발 생산성 향상
                  - 코드 리뷰, 문서화, 리서치 등 개발 자동화

                자격요건
                • 학력 : 초대졸이상
                • 백엔드 개발 경력 1년 이상
                • Kotlin, TypeScript, Python, Go, Java 중 1개 이상 개발 가능자
                • MySQL 등 RDBMS 기반 서비스 개발 경험
                • Docker, Git, Gradle 등 개발 도구 사용 경험

                ► 필수 역량
                • 설계 판단
                  - "왜 이 구조인가"를 트레이드오프로 설명할 수 있고. 코드 리뷰를 통해 함께 성장하려는 의지
                • AI 활용
                  - AI를 도구로 쓰되 결과를 검증하고 최종 책임을 지는 태도
                • 적극적인 커뮤니케이션
                  - 근거 있는 의견을 적극적으로 제시하고, 팀원들과 원활하게 소통하며 협업하는 자세

                우대사항
                • 확장 가능한 서비스 및 대규모 시스템 설계·운영 경험
                • 장애 대응 및 서비스 안정화 경험
                • OOP, DDD 기반 설계 및 개발 역량
                • 복잡한 비즈니스 요구사항을 설계로 해결한 경험
                • AI 개발 도구를 실무에 적극 활용해 본 경험

                혜택 및 복지
                • 유연한 근무 시간과 원격 근무 가능
                • 직원 건강 및 복지를 위한 다양한 프로그램 제공
                • 개인의 성장과 발전을 위한 교육 지원
                • 성과에 따른 보상 및 인센티브 제공
                • 최신장비 지원
                • 야근수당 및 식대 지원
                • 커피데이 + 치킨데이 + 무한간식
                • OTT지원 + 생일 이벤트
                """);

        assertThat(items).containsExactly(
                new JobPostingItemResponse(1, "서비스 설계 및 백엔드 개발", "기능 설계, API, 권한 정책, 핵심 도메인 설계 및 구현"),
                new JobPostingItemResponse(2, "안정적인 서비스 구조 설계", "인증·인가, 데이터 정합성, 트랜잭션 관리"),
                new JobPostingItemResponse(3, "멀티클라우드 연동", "AWS-Azure-GCP등 API 및 계정 연동"),
                new JobPostingItemResponse(4, "비용·결제 시스템 운영", "비용 파이프라인, 배치, 구독·결제 프로세스 관리"),
                new JobPostingItemResponse(5, "서비스 운영 및 개선", "피드백 기반 기능 개선과 안정적인 배포"),
                new JobPostingItemResponse(6, "AI 기반 개발 생산성 향상", "코드 리뷰, 문서화, 리서치 등 개발 자동화"),
                new JobPostingItemResponse(7, "자격요건", "백엔드 개발 경력 1년 이상"),
                new JobPostingItemResponse(8, "자격요건", "Kotlin, TypeScript, Python, Go, Java 중 1개 이상 개발 가능자"),
                new JobPostingItemResponse(9, "자격요건", "MySQL 등 RDBMS 기반 서비스 개발 경험"),
                new JobPostingItemResponse(10, "자격요건", "Docker, Git, Gradle 등 개발 도구 사용 경험"),
                new JobPostingItemResponse(11, "설계 판단", "\"왜 이 구조인가\"를 트레이드오프로 설명할 수 있고. 코드 리뷰를 통해 함께 성장하려는 의지"),
                new JobPostingItemResponse(12, "AI 활용", "AI를 도구로 쓰되 결과를 검증하고 최종 책임을 지는 태도"),
                new JobPostingItemResponse(13, "적극적인 커뮤니케이션", "근거 있는 의견을 적극적으로 제시하고, 팀원들과 원활하게 소통하며 협업하는 자세"),
                new JobPostingItemResponse(14, "우대사항", "확장 가능한 서비스 및 대규모 시스템 설계·운영 경험"),
                new JobPostingItemResponse(15, "우대사항", "장애 대응 및 서비스 안정화 경험"),
                new JobPostingItemResponse(16, "우대사항", "OOP, DDD 기반 설계 및 개발 역량"),
                new JobPostingItemResponse(17, "우대사항", "복잡한 비즈니스 요구사항을 설계로 해결한 경험"),
                new JobPostingItemResponse(18, "우대사항", "AI 개발 도구를 실무에 적극 활용해 본 경험"));
    }

    @Test
    void treatsBothTriangleMarkersAsStructuralHeadings() {
        List<JobPostingItemResponse> items = service.segment("""
                주요업무
                ► 제품 개발
                • API 개발 경험
                ▶ 운영 개선
                • 장애 대응 경험
                """);

        assertThat(items).containsExactly(
                new JobPostingItemResponse(1, "제품 개발", "API 개발 경험"),
                new JobPostingItemResponse(2, "운영 개선", "장애 대응 경험"));
    }

    @Test
    void keepsSearchableLeavesAndExcludesIntroBenefitsAndLegalSections() {
        List<JobPostingItemResponse> items = service.segment("""
                Platform Engineer
                About the organization
                We create digital products for growing teams.

                Responsibilities
                - Design reliable backend services
                - Operate event processing pipelines

                Qualifications
                - 3+ years of backend development experience
                - Strong knowledge of distributed systems

                Benefits and well-being
                - Flexible working hours
                - Learning budget

                Candidate privacy notice
                Personal information is processed for recruitment purposes.
                """);

        assertThat(items).containsExactly(
                new JobPostingItemResponse(1, "Responsibilities", "Design reliable backend services"),
                new JobPostingItemResponse(2, "Responsibilities", "Operate event processing pipelines"),
                new JobPostingItemResponse(3, "Qualifications", "3+ years of backend development experience"),
                new JobPostingItemResponse(4, "Qualifications", "Strong knowledge of distributed systems"));
    }

    @Test
    void excludesRecruitmentProcessAndStandaloneWorkMetadata() {
        List<JobPostingItemResponse> items = service.segment("""
                주요 업무
                - 인증 API를 설계하고 운영합니다

                근무 정보
                Location: Example City
                Employment Type: Full-time
                Workplace / Hybrid
                Deadline / 2027-03-15

                채용 절차
                서류 접수 > 기술 인터뷰 > 최종 합류

                지원 안내
                온라인 지원서를 제출해 주세요.

                참고 정보
                - 지원자는 여러 직군 중 하나를 선택할 수 있습니다

                성장 기회
                - 다양한 분야를 폭넓게 둘러볼 수 있습니다

                자격 요건
                - 사용자 요구를 분석해 서비스로 구현한 경험
                """);

        assertThat(items).containsExactly(
                new JobPostingItemResponse(1, "주요 업무", "인증 API를 설계하고 운영합니다"),
                new JobPostingItemResponse(2, "자격 요건", "사용자 요구를 분석해 서비스로 구현한 경험"));
    }

    @Test
    void usesHeadingAndParentChildStructureBeforeLeafSelection() {
        List<JobPostingItemResponse> items = service.segment("""
                Responsibilities
                Backend Platform
                • Service architecture
                  - Implement authorization boundaries
                  - Maintain transactional consistency
                • Production operations
                  - Improve deployment reliability
                • Data operations
                  ∘ Validate ingestion workflows
                  ∘ Monitor storage consistency
                """);

        assertThat(items).containsExactly(
                new JobPostingItemResponse(1, "Service architecture", "Implement authorization boundaries"),
                new JobPostingItemResponse(2, "Service architecture", "Maintain transactional consistency"),
                new JobPostingItemResponse(3, "Production operations", "Improve deployment reliability"),
                new JobPostingItemResponse(4, "Data operations", "Validate ingestion workflows"),
                new JobPostingItemResponse(5, "Data operations", "Monitor storage consistency"));
    }

    @Test
    void preservesRequirementLikeLeavesUnderUnknownHeadingsButDropsNarrative() {
        List<JobPostingItemResponse> items = service.segment("""
                Delivery Excellence
                - API 설계 및 구현 경험
                - 장애 원인 분석과 운영 개선 역량

                Culture Notes
                구성원은 새로운 아이디어를 나누며 함께 성장합니다.

                우대 사항
                - Cloud 환경을 활용한 프로젝트 경험
                """);

        assertThat(items).containsExactly(
                new JobPostingItemResponse(1, "Delivery Excellence", "API 설계 및 구현 경험"),
                new JobPostingItemResponse(2, "Delivery Excellence", "장애 원인 분석과 운영 개선 역량"),
                new JobPostingItemResponse(3, "우대 사항", "Cloud 환경을 활용한 프로젝트 경험"));
    }

    @Test
    void recognizesAnImplicitEnglishRequirementRunAfterNarrative() {
        List<JobPostingItemResponse> items = service.segment("""
                About the team
                The group works across several product areas.
                Engineers collaborate with multiple disciplines.

                Design and operate low-latency backend services.
                Lead architecture and code reviews.
                5+ years of professional software development experience.
                Proficiency with a JVM language and a server framework.

                Benefits
                Flexible paid time off.
                """);

        assertTexts(items,
                "Design and operate low-latency backend services.",
                "Lead architecture and code reviews.",
                "5+ years of professional software development experience.",
                "Proficiency with a JVM language and a server framework.");
    }

    @Test
    void keepsMixedLanguageRequirementsWhileDiscardingMixedMetadata() {
        List<JobPostingItemResponse> items = service.segment("""
                Who we are looking for
                - Backend 서비스 설계 experience
                - Kubernetes 운영 및 troubleshooting 역량

                Work conditions
                고용형태 / Full-time
                오피스 / Hybrid
                """);

        assertThat(items).containsExactly(
                new JobPostingItemResponse(1, "Who we are looking for", "Backend 서비스 설계 experience"),
                new JobPostingItemResponse(2, "Who we are looking for", "Kubernetes 운영 및 troubleshooting 역량"));
    }

    @Test
    void stopsSearchableContentAtARepeatedApplicationFormBlock() {
        List<JobPostingItemResponse> items = service.segment("""
                Responsibilities
                Design reliable service boundaries.
                Maintain production observability.

                Apply for this opportunity
                Required fields are marked
                Applicant identity *
                Preferred contact
                Work sample upload
                Attach a document
                Additional response (optional)
                Submit application

                Qualifications
                3+ years of backend engineering experience.
                """);

        assertTexts(items,
                "Design reliable service boundaries.",
                "Maintain production observability.",
                "3+ years of backend engineering experience.");
    }

    @Test
    void excludesCompensationAndKeyValueMetadataRunsButKeepsInlineRequirements() {
        List<JobPostingItemResponse> items = service.segment("""
                Responsibilities
                Build fault-tolerant backend services.

                Total rewards snapshot
                Annual salary
                80,000 | 120,000 units
                Performance award
                Up to 10% annually
                Region: Example District
                Work arrangement | Hybrid
                Contract type | Full-time

                지원자격: Java 기반 API 개발 경험

                Qualifications
                Experience operating a relational database.
                """);

        assertTexts(items,
                "Build fault-tolerant backend services.",
                "지원자격: Java 기반 API 개발 경험",
                "Experience operating a relational database.");
    }

    @Test
    void removesMultiwordSubheadingsFromUnbulletedSearchableRuns() {
        List<JobPostingItemResponse> items = service.segment("""
                Responsibilities
                Core Service Platform
                Design authenticated APIs.
                Operate distributed services.

                Data Processing Layer
                Build reliable ingestion pipelines.
                Improve storage consistency.

                Qualifications
                Strong knowledge of transactional systems.
                """);

        assertTexts(items,
                "Design authenticated APIs.",
                "Operate distributed services.",
                "Build reliable ingestion pipelines.",
                "Improve storage consistency.",
                "Strong knowledge of transactional systems.");
    }

    @Test
    void keepsUnknownBulletLeavesAndUnmarkedRequirementRunsButDropsNarrative() {
        List<JobPostingItemResponse> items = service.segment("""
                About the group
                The group shares product stories and celebrates milestones.

                Delivery Notes
                - API 설계 및 구현 경험
                - Experience operating distributed systems

                Culture Notes
                구성원은 다양한 관심사를 나누며 함께 성장합니다.

                Design and maintain low-latency services.
                Lead architecture reviews across teams.
                4+ years of production engineering experience.
                """);

        assertTexts(items,
                "API 설계 및 구현 경험",
                "Experience operating distributed systems",
                "Design and maintain low-latency services.",
                "Lead architecture reviews across teams.",
                "4+ years of production engineering experience.");
    }

    @Test
    void excludesMixedLanguageApplicationAndPrivacyBlocks() {
        List<JobPostingItemResponse> items = service.segment("""
                주요 업무
                - 고객 API를 설계하고 운영합니다

                지원서 작성
                필수 항목은 별표로 표시됩니다
                지원자 식별 정보 *
                Contact preference
                경력자료 upload
                파일 attach

                개인정보 처리 안내
                제출 정보는 recruitment 목적으로만 처리됩니다.

                자격 요건
                - Cloud 환경에서 서비스 운영 경험
                """);

        assertTexts(items,
                "고객 API를 설계하고 운영합니다",
                "Cloud 환경에서 서비스 운영 경험");
    }

    @Test
    void excludesBracketedRecruitmentMetadataChildrenButKeepsCareerRequirements() {
        List<JobPostingItemResponse> items = service.segment("""
                [경력]
                신입 - 경력무관
                무관

                [모집인원]
                0명

                [자격요건]
                - 백엔드 개발 경력 3년 이상
                """);

        assertTexts(items, "백엔드 개발 경력 3년 이상");
    }

    @Test
    void keepsTheFirstBulletInABracketedPersonalitySection() {
        List<JobPostingItemResponse> items = service.segment("""
                [인재상]
                - 적극적으로 업무를 수행하는 사람
                - 협업을 중요하게 생각하는 사람
                """);

        assertTexts(items,
                "적극적으로 업무를 수행하는 사람",
                "협업을 중요하게 생각하는 사람");
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
    void dropsApplicationAndMetadataNoiseWithoutDroppingTechnologyRows() {
        List<JobPostingItemResponse> items = service.segment("""
                Responsibilities
                - Design high-throughput backend services.
                - Operate distributed production systems.

                Technology Stack
                The team selects tools according to each system.
                | ZephyrDB, NimbusQueue, AuroraMesh
                | Spring Boot, Kafka, Redis

                Remarks
                Read the separate role guide before applying.
                Team principles
                Refer to the company values page.

                Apply for this role
                indicates a required field
                First Name*
                Last Name*
                Email*
                Resume/CV*
                Attach a document
                Submit application

                Qualifications
                - Experience designing authenticated APIs.
                """);

        assertTexts(items,
                "Design high-throughput backend services.",
                "Operate distributed production systems.",
                "ZephyrDB, NimbusQueue, AuroraMesh",
                "Spring Boot, Kafka, Redis",
                "Experience designing authenticated APIs.");
    }

    @Test
    void detectsApplicationFieldsWhenTheFormHeadingIsMissing() {
        List<JobPostingItemResponse> items = service.segment("""
                Responsibilities
                - Design high-throughput backend services.

                First Name*
                Last Name*
                Email*
                Resume/CV*
                Attach a document
                Submit application

                Qualifications
                - 3+ years of backend engineering experience.
                """);

        assertTexts(items,
                "Design high-throughput backend services.",
                "3+ years of backend engineering experience.");
    }

    @Test
    void doesNotTreatRequirementSentencesAsApplicationFields() {
        List<JobPostingItemResponse> items = service.segment("""
                Qualifications
                Required experience with distributed systems
                Required knowledge of relational databases
                Required ability to review production incidents
                """);

        assertTexts(items,
                "Required experience with distributed systems",
                "Required knowledge of relational databases",
                "Required ability to review production incidents");
    }

    @Test
    void keepsExperienceRangesWhileExcludingCompensationRanges() {
        List<JobPostingItemResponse> items = service.segment("""
                Qualifications
                - 3-5 years of backend experience.
                - 1-2 production systems operated at scale.

                Compensation
                $80,000 - $120,000 per year
                """);

        assertTexts(items,
                "3-5 years of backend experience.",
                "1-2 production systems operated at scale.");
    }

    @Test
    void preservesVersionedTechnologyRowsSeparatedByPipes() {
        List<JobPostingItemResponse> items = service.segment("""
                Technology Stack
                The team selects versions according to each service.
                Java 17 | Spring Boot 3
                PostgreSQL 16 | Redis 7

                Compensation
                $80,000 - $120,000 per year

                Qualifications
                - Experience designing authenticated APIs.
                """);

        assertTexts(items,
                "Java 17 | Spring Boot 3",
                "PostgreSQL 16 | Redis 7",
                "Experience designing authenticated APIs.");
    }

    @Test
    void removesStructuralSubheadingsButKeepsActionRequirements() {
        List<JobPostingItemResponse> items = service.segment("""
                What You'll Do
                Build the backend
                - Design and deploy scalable APIs.
                - Troubleshoot production services.

                Engineer the data
                - Build reliable ingestion pipelines.
                - Improve warehouse consistency.
                """);

        assertTexts(items,
                "Build the backend",
                "Design and deploy scalable APIs.",
                "Troubleshoot production services.",
                "Build reliable ingestion pipelines.",
                "Improve warehouse consistency.");
    }

    @Test
    void removesStructuralSubheadingsBeforeUnbulletedRequirements() {
        List<JobPostingItemResponse> items = service.segment("""
                What You'll Do
                Build the backend
                Design and deploy scalable APIs.
                Troubleshoot production services.

                Engineer the data
                Build reliable ingestion pipelines.
                Improve warehouse consistency.
                """);

        assertTexts(items,
                "Build the backend",
                "Design and deploy scalable APIs.",
                "Troubleshoot production services.",
                "Build reliable ingestion pipelines.",
                "Improve warehouse consistency.");
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
