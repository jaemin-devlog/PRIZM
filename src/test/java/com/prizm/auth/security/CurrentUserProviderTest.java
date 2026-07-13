package com.prizm.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CurrentUserProviderTest {

    private final CurrentUserProvider provider = new CurrentUserProvider();

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsUserIdFromValidatedJwtSubject() {
        Jwt jwt = jwt("42");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));

        assertThat(provider.userId()).isEqualTo(42L);
    }

    @Test
    void rejectsMissingOrInvalidAuthenticatedUserId() {
        assertThatThrownBy(provider::userId)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);

        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt("not-a-number"), List.of()));
        assertThatThrownBy(provider::userId)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test
    void rejectsUnexpectedAuthenticatedPrincipalType() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", "credentials", List.of()));

        assertThatThrownBy(provider::userId)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private Jwt jwt(String subject) {
        Instant now = Instant.now();
        return new Jwt(
                "token",
                now,
                now.plusSeconds(60),
                java.util.Map.of("alg", "HS256"),
                java.util.Map.of("sub", subject));
    }
}
