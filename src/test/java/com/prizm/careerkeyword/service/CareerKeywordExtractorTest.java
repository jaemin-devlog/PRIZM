package com.prizm.careerkeyword.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.careerkeyword.model.CareerKeywordCategory;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CareerKeywordExtractorTest {

    private final CareerKeywordExtractor extractor = new CareerKeywordExtractor();

    @Test
    void extractsSourceGroundedTechnologyPhrasesAndNormalizesCase() {
        Map<String, ExtractedKeyword> keywords = extractor.extract(
                "Spring Boot로 API를 구현했습니다. spring boot와 Redis, REDIS를 사용했습니다.");

        assertThat(keywords.get("spring boot"))
                .extracting(ExtractedKeyword::keyword, ExtractedKeyword::frequency)
                .containsExactly("Spring Boot", 2);
        assertThat(keywords.get("redis"))
                .extracting(ExtractedKeyword::keyword, ExtractedKeyword::frequency)
                .containsExactly("Redis", 2);
        assertThat(keywords).doesNotContainKeys(
                "spring", "boot", "api를", "구현", "사용", "사용했습니다");
        assertThat(keywords.get("api").keyword()).isEqualTo("API");
    }

    @Test
    void removesGenericWordsNumbersAndKoreanParticles() {
        Map<String, ExtractedKeyword> keywords = extractor.extract(
                "프로젝트를 진행하며 PostgreSQL을 활용하고 2026 결과를 관리했습니다. "
                        + "Implemented Redis using Java.");

        assertThat(keywords).containsKeys("postgresql", "redis", "java");
        assertThat(keywords).doesNotContainKeys(
                "프로젝트", "진행", "활용", "2026", "결과", "관리", "관리했습니다",
                "implemented", "using");
    }

    @Test
    void keepsTechnicalKeywordsAndRejectsDomainNamesAndSentenceWords() {
        Map<String, ExtractedKeyword> keywords = extractor.extract(
                "처리 발송 알림 중복 매칭 실패 저장 기준 상태 호출 DB 대기 동기화 API Redis 상세 "
                        + "백엔드 병렬 선점 최종 흐름 FCM 통합 관광지 요청 Outbox 문제 소개 후보 "
                        + "Spring Boot TourAPI 같은 시간 외부 테스트 확정 Worker 분리 작업자 Java21 "
                        + "MySQL 데이터 조건 조회 확인 id 구조 동시 제외 팀방 AirConnect 검증 그룹 "
                        + "순차 이벤트 읽음 정재민 정합성 조합 차단 OAuth2 JWT Docker Nginx GCP Apache POI");

        assertThat(keywords.values()).extracting(ExtractedKeyword::keyword)
                .contains(
                        "DB", "동기화", "API", "Redis", "Backend", "병렬", "선점", "FCM",
                        "Outbox", "Spring Boot", "테스트", "Worker", "Java", "MySQL",
                        "이벤트", "정합성", "OAuth2", "JWT", "Docker", "Nginx", "GCP", "Apache POI")
                .doesNotContain(
                        "처리", "발송", "알림", "중복", "매칭", "실패", "저장", "기준", "상태",
                        "TourAPI", "AirConnect", "정재민", "데이터", "확인", "구조");
    }

    @Test
    void mergesDeclaredAliasesAndPreservesObservedVariantsAndCategory() {
        Map<String, ExtractedKeyword> keywords = extractor.extract(
                "Backend와 백엔드, Java21과 Java17, SpringBoot와 Spring Boot, DB와 데이터베이스");

        assertThat(keywords).containsKeys("backend", "java", "spring boot", "db");
        assertThat(keywords.get("backend"))
                .extracting(ExtractedKeyword::keyword, ExtractedKeyword::category, ExtractedKeyword::frequency)
                .containsExactly("Backend", CareerKeywordCategory.WEB, 2);
        assertThat(keywords.get("backend").matchedTerms()).containsExactly("Backend", "백엔드");
        assertThat(keywords.get("java").keyword()).isEqualTo("Java");
        assertThat(keywords.get("java").frequency()).isEqualTo(2);
        assertThat(keywords.get("java").matchedTerms()).containsExactly("Java21", "Java17");
        assertThat(keywords.get("spring boot").frequency()).isEqualTo(2);
        assertThat(keywords.get("db").frequency()).isEqualTo(2);
        assertThat(keywords.get("db").category()).isEqualTo(CareerKeywordCategory.DATABASE);
    }

    @Test
    void returnsNoKeywordsForBlankText() {
        assertThat(extractor.extract("  ")).isEmpty();
    }
}
