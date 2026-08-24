package com.prizm.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT 서명 키, 만료 시간, 발급자를 시작 시점에 검증한다.
 * 공개 예시 키나 너무 짧은 키로 애플리케이션이 실행되는 것을 설정 바인딩 단계에서 막는다.
 */
@Validated
@ConfigurationProperties("prizm.jwt")
public record JwtProperties(
        @NotBlank String secret,
        @Min(60) @Max(86_400) long expirationSeconds,
        @NotBlank String issuer) {

    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final Set<String> REJECTED_SECRETS = Set.of(
            "replace-with-a-random-secret-at-least-32-characters",
            "replace-with-a-long-random-secret",
            "change-me",
            "changeme",
            "example",
            "secret");

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalArgumentException("prizm.jwt.secret must not be blank");
        }
        if (!secret.equals(secret.trim())) {
            throw new IllegalArgumentException("prizm.jwt.secret must not contain surrounding whitespace");
        }
        if (REJECTED_SECRETS.contains(secret.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("prizm.jwt.secret must not use a public placeholder");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            throw new IllegalArgumentException("prizm.jwt.secret must contain at least 32 UTF-8 bytes");
        }
        if (expirationSeconds < 60 || expirationSeconds > 86_400) {
            throw new IllegalArgumentException("prizm.jwt.expiration-seconds must be between 60 and 86400");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalArgumentException("prizm.jwt.issuer must not be blank");
        }
        issuer = issuer.trim();
    }

    @Override
    public String toString() {
        return "JwtProperties[secret=[REDACTED], expirationSeconds=" + expirationSeconds
                + ", issuer=" + issuer + "]";
    }
}
