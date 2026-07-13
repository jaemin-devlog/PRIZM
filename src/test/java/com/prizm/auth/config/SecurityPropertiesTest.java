package com.prizm.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SecurityPropertiesTest {

    @Test
    void acceptsExplicitHttpOrigins() {
        SecurityProperties properties = new SecurityProperties(
                List.of("http://localhost:5173", "https://prizm.example.com"));

        assertThat(properties.allowedOrigins()).containsExactly(
                "http://localhost:5173", "https://prizm.example.com");
    }

    @Test
    void rejectsWildcardBlankAndInvalidOrigins() {
        assertThatThrownBy(() -> new SecurityProperties(List.of("*")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecurityProperties(List.of("   ")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SecurityProperties(List.of("not-a-url")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
