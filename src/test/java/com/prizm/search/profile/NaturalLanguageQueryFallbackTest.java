package com.prizm.search.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NaturalLanguageQueryFallbackTest {

    @Test
    void buildsConservativeVariantsWithoutReducingQueriesToOneKeyword() {
        assertThat(Map.of(
                        "AtlasBoard에서 뭐했어?", "AtlasBoard 수행 경험",
                        "Springboot", "Spring Boot 경험",
                        "springboot", "Spring Boot 경험",
                        "Spring Boot", "Spring Boot 경험",
                        "SpringBoot를 활용한 경험", "Spring Boot 활용 경험",
                        "배포 경험 알려줘", "배포 환경 구축 경험"))
                .allSatisfy((query, expected) -> assertThat(NaturalLanguageQueryFallback.variant(query))
                        .contains(expected));
    }

    @Test
    void doesNotRewriteQueriesThatAlreadyPreserveTheirRelationship() {
        assertThat(NaturalLanguageQueryFallback.variant("Redis를 왜 사용했어?"))
                .contains("Redis 왜 사용했어?");
        assertThat(NaturalLanguageQueryFallback.variant("결제 시스템 구현 경험")).isEmpty();
        assertThat(NaturalLanguageQueryFallback.variant("Kubernetes 사용 경험")).isEmpty();
    }

    @Test
    void extractsCanonicalAnchorsAcrossFormattingAndKoreanParticles() {
        assertThat(NaturalLanguageQueryFallback.anchorTerms("SpringBoot를 활용한 경험"))
                .containsExactly("springboot");
        assertThat(NaturalLanguageQueryFallback.anchorTerms("AtlasBoard에서 뭐했어?"))
                .containsExactly("atlasboard");
        assertThat(NaturalLanguageQueryFallback.anchorTerms("Redis는 왜 사용했어?"))
                .containsExactly("redis");
    }

    @Test
    void directAnchorRejectsUnrelatedExperienceEvidence() {
        assertThat(NaturalLanguageQueryFallback.hasDirectAnchor(
                        "결제 시스템 구현 경험",
                        "동일 이메일 계정 연결과 비밀번호 재설정 인증 상태를 구현했습니다."))
                .isFalse();
        assertThat(NaturalLanguageQueryFallback.hasDirectAnchor(
                        "Springboot",
                        "Spring Boot 기반 서버를 운영했습니다."))
                .isTrue();
    }

    @Test
    void createsAtMostTwoGeneralSemanticVariantsWithoutInjectingSolutions() {
        assertThat(NaturalLanguageQueryFallback.variants(
                        "실제 운영 환경에 배포해본 경험 알려줘"))
                .containsExactly(
                        "실제 운영 환경에 배포해본 경험",
                        "운영 환경 배포 환경 구축 경험");
        assertThat(NaturalLanguageQueryFallback.variants(
                        "스프레드시트에서 기존 데이터만 골라 갱신한 적 있어?"))
                .contains("스프레드시트 엑셀에서 기존 데이터 갱신한 적 있어?");
        assertThat(NaturalLanguageQueryFallback.variants(
                        "업로드 파일을 애플리케이션 대신 웹 서버가 제공하게 한 경험은?"))
                .anySatisfy(variant -> assertThat(variant).contains("웹 서버가 직접 서빙"))
                .contains("웹 서버 파일 직접 서빙 경험");
        assertThat(NaturalLanguageQueryFallback.variants(
                        "후보 상태가 바뀌었는지 확정 직전에 다시 확인한 경험은?"))
                .anySatisfy(variant -> assertThat(variant)
                        .contains("상태가 변경 여부", "확정 전", "재검증")
                        .doesNotContain("row lock"))
                .contains("확정 전 상태 재검증 경험");
        assertThat(NaturalLanguageQueryFallback.variants("느린 API 개선 방법"))
                .doesNotContain("병렬 처리");
        assertThat(NaturalLanguageQueryFallback.variants(
                        "실제 운영 환경에 배포해본 경험 알려줘"))
                .hasSizeLessThanOrEqualTo(NaturalLanguageQueryFallback.MAX_VARIANTS);
    }

    @Test
    void preservesStrongIdentifiersAndNumericAnchorsAcrossVariants() {
        List<String> identifierVariants = NaturalLanguageQueryFallback.variants(
                "GCP에서 Docker Compose와 Nginx로 Spring Boot 서비스를 배포한 경험은?");
        assertThat(identifierVariants).isNotEmpty().allSatisfy(variant -> assertThat(
                        NaturalLanguageQueryFallback.preservesRequiredAnchors(
                                "GCP에서 Docker Compose와 Nginx로 Spring Boot 서비스를 배포한 경험은?",
                                variant,
                                Set.of("gcp", "docker", "compose", "nginx", "springboot")))
                .isTrue());

        List<String> numericVariants = NaturalLanguageQueryFallback.variants(
                "TourAPI를 처리하면서 엑셀 2,329행 중 675건을 갱신한 경험 알려줘");
        assertThat(numericVariants).allSatisfy(variant -> assertThat(
                        NaturalLanguageQueryFallback.preservesRequiredAnchors(
                                "TourAPI를 처리하면서 엑셀 2,329행 중 675건을 갱신한 경험 알려줘",
                                variant,
                                Set.of("tourapi")))
                .isTrue());
    }
}
