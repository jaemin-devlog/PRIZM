package com.prizm.search.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class SearchPropertiesTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SearchPropertiesConfiguration.class);

    @Test
    void defaultsToSourceDedupEvidenceSignalsProfile() {
        SearchProperties properties = new SearchProperties(null);

        assertThat(properties.profile()).isEqualTo(SearchProperties.DEFAULT_PROFILE);
        assertThat(properties.selectedProfile()).isEqualTo(SearchProfile.SOURCE_DEDUP_EVIDENCE_SIGNALS_V1);
    }

    @Test
    void acceptsTheLegacyRollbackProfile() {
        SearchProperties properties = new SearchProperties(SearchProfile.LEGACY_DENSE_V1.propertyValue());

        assertThat(properties.selectedProfile()).isEqualTo(SearchProfile.LEGACY_DENSE_V1);
    }

    @Test
    void rejectsUnknownAndBlankProfiles() {
        assertThatThrownBy(() -> new SearchProperties("future-profile"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported search profile: future-profile");
        assertThatThrownBy(() -> new SearchProperties(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported search profile: ");
    }

    @Test
    void bindsTheLegacyRollbackProfileThroughSpringConfigurationProperties() {
        contextRunner
                .withPropertyValues("prizm.search.profile=" + SearchProfile.LEGACY_DENSE_V1.propertyValue())
                .run(context -> assertThat(context.getBean(SearchProperties.class).selectedProfile())
                        .isEqualTo(SearchProfile.LEGACY_DENSE_V1));
    }

    @Test
    void rejectsAnUnknownProfileDuringSpringContextStartup() {
        contextRunner
                .withPropertyValues("prizm.search.profile=unknown-profile")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SearchProperties.class)
    static class SearchPropertiesConfiguration {}
}
