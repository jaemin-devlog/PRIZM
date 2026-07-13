package com.prizm.auth.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("prizm.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32) String secret,
        @Positive long expirationSeconds) {
}
