package com.prizm.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.auth.config.JwtProperties;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenServiceTest {

    private static final String SECRET = "jwt-unit-test-secret-at-least-32-characters-long";

    @Test
    void issuesAndExtractsIdentityAndRoleClaims() {
        JwtTokenService service = tokenService(Clock.systemUTC(), 3600);
        UserAccount user = user(7L, UserRole.ADMIN);

        IssuedAccessToken issued = service.issue(user);
        Jwt decoded = service.decode(issued.value());

        assertThat(decoded.getSubject()).isEqualTo("7");
        assertThat(decoded.getClaimAsString("userId")).isEqualTo("7");
        assertThat(decoded.getClaimAsString("email")).isEqualTo("user@example.com");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("ADMIN");
        assertThat(issued.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void rejectsExpiredToken() {
        Clock pastClock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        JwtTokenService service = tokenService(pastClock, 1);
        String token = service.issue(user(8L, UserRole.USER)).value();

        assertThatThrownBy(() -> service.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTamperedToken() {
        JwtTokenService service = tokenService(Clock.systemUTC(), 3600);
        String token = service.issue(user(9L, UserRole.USER)).value();
        char replacement = token.endsWith("a") ? 'b' : 'a';
        String tampered = token.substring(0, token.length() - 1) + replacement;

        assertThatThrownBy(() -> service.decode(tampered)).isInstanceOf(JwtException.class);
    }

    private JwtTokenService tokenService(Clock clock, long expirationSeconds) {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new JwtTokenService(
                NimbusJwtEncoder.withSecretKey(key).build(),
                NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build(),
                new JwtProperties(SECRET, expirationSeconds),
                clock);
    }

    private UserAccount user(Long id, UserRole role) {
        UserAccount user = UserAccount.create("user@example.com", "unused-hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
