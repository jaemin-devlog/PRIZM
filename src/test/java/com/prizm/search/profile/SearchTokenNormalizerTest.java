package com.prizm.search.profile;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SearchTokenNormalizerTest {

    @ParameterizedTest
    @ValueSource(strings = {
        "Spring Boot",
        "SpringBoot",
        "Springboot",
        "Spring-Boot",
        "spring_boot"
    })
    void comparesSupportedSpringBootFormattingAsOneIdentifier(String value) {
        assertThat(SearchTokenNormalizer.normalize(value)).isEqualTo("springboot");
    }

    @Test
    void doesNotTreatSpringBatchAsSpringBoot() {
        assertThat(SearchTokenNormalizer.normalize("Spring Batch"))
                .isNotEqualTo(SearchTokenNormalizer.normalize("Spring Boot"));
    }

    @Test
    void normalizesSpringBootFormattingBeforeAKoreanParticle() {
        assertThat(SearchTokenNormalizer.normalize("Spring Boot를"))
                .isEqualTo("springboot를");
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "Spring Boot", "SpringBoot", "Springboot", "springboot"
    })
    void canonicalizesSpringBootForEmbeddingVariants(String value) {
        assertThat(SearchTokenNormalizer.canonicalizeTechnologyNames(value))
                .isEqualTo("Spring Boot");
    }

    @Test
    void preservesMeaningfulTechnicalIdentifierPunctuation() {
        assertThat(List.of("C++", "C#", "Node.js").stream()
                        .map(SearchTokenNormalizer::normalize))
                .containsExactly("c++", "c#", "node.js");
    }
}
