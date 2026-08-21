package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class SearchEvaluationLiteralAnchorExtractorTest {

    private final SearchEvaluationLiteralAnchorExtractor extractor =
            new SearchEvaluationLiteralAnchorExtractor();

    @Test
    void extractsGenericNumericMultiTokenCamelCaseAndAcronymAnchors() {
        assertThat(normalized("TourAPI 680건을 6.8초에 처리했나?"))
                .containsExactly("tourapi", "680건", "6.8초");
        assertThat(normalized("Spring Boot와 Docker Compose를 사용했나?"))
                .containsExactly("spring boot", "docker compose");
        assertThat(normalized("GCP에서 AtlasBoard와 JWT를 사용했나?"))
                .containsExactly("gcp", "atlasboard", "jwt");
        assertThat(normalized("FOR UPDATE SKIP LOCKED로 선점했나?"))
                .containsExactly("for update skip locked");
    }

    @Test
    void keepsMaximalPartialAndVersionPhrasesForNegativeNearMisses() {
        assertThat(normalized("Redis Streams를 운영했나?"))
                .containsExactly("redis streams");
        assertThat(normalized("OAuth2 Device Authorization Grant를 구현했나?"))
                .containsExactly("oauth2 device authorization grant");
        assertThat(normalized("Spring Boot 3.5로 개발했나?"))
                .containsExactly("spring boot 3.5");
        assertThat(normalized("TourAPI v2를 연동했나?"))
                .containsExactly("tourapi v2");
    }

    @Test
    void excludesGenericWordsAndDoesNotInferSolutions() {
        assertThat(normalized("API 서비스 개발 구현 경험과 데이터 처리 문제"))
                .isEmpty();
        assertThat(normalized("웹 서버 경험"))
                .isEmpty();
    }

    @Test
    void normalizationIsConservative() {
        assertThat(SearchEvaluationLiteralAnchorExtractor.normalize(" AWS RDS Multi‑AZ "))
                .isEqualTo("aws rds multi-az");
        assertThat(SearchEvaluationLiteralAnchorExtractor.normalize("없는 웹 서버"))
                .doesNotContain("nginx");
    }

    private List<String> normalized(String query) {
        return extractor.extract(query).stream().map(
                SearchEvaluationLiteralAnchorExtractor.Anchor::normalized).toList();
    }
}
