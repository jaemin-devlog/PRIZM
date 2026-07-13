package com.prizm.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtPropertiesTest {

    @Test
    void rejectsBlankSecret() {
        assertThatThrownBy(() -> new JwtProperties(null, 3600, "prizm"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties("   ", 3600, "prizm"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPublicPlaceholder() {
        assertThatThrownBy(() -> new JwtProperties(
                "replace-with-a-long-random-secret", 3600, "prizm"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("placeholder");
    }

    @Test
    void rejectsSecretShorterThan32Utf8Bytes() {
        assertThatThrownBy(() -> new JwtProperties("a".repeat(31), 3600, "prizm"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 UTF-8 bytes");
    }

    @Test
    void acceptsArbitrarySecretWithAtLeast32Utf8Bytes() {
        String multiByteSecret = "가".repeat(11);
        JwtProperties properties = new JwtProperties(multiByteSecret, 3600, "prizm");

        assertThat(properties.secret()).hasSize(11);
        assertThat(properties.secret().getBytes(java.nio.charset.StandardCharsets.UTF_8)).hasSize(33);
    }

    @Test
    void validatesExpirationRange() {
        String secret = "0123456789abcdef".repeat(2);

        assertThatThrownBy(() -> new JwtProperties(secret, 59, "prizm"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JwtProperties(secret, 86_401, "prizm"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new JwtProperties(secret, 3600, "prizm").expirationSeconds()).isEqualTo(3600);
    }

    @Test
    void redactsSecretFromToString() {
        String secret = "0123456789abcdef".repeat(2);
        JwtProperties properties = new JwtProperties(secret, 3600, "prizm");

        assertThat(properties.toString()).doesNotContain(secret).contains("[REDACTED]");
    }
}
