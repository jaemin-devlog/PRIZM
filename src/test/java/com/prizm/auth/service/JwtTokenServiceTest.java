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
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.test.util.ReflectionTestUtils;

class JwtTokenServiceTest {

    private static final String SECRET = "jwt-unit-test-secret-at-least-32-characters-long";

    @Test
    void issuesAndExtractsIdentityAndRoleClaims() {
        JwtTokenService service = tokenService(Clock.systemUTC(), 3600);
        UserAccount user = user(7L, UserRole.SYSTEM_ADMIN);

        IssuedAccessToken issued = service.issue(user);
        Jwt decoded = service.decode(issued.value());

        assertThat(decoded.getSubject()).isEqualTo("7");
        assertThat(decoded.getClaimAsString("iss")).isEqualTo("prizm");
        assertThat(decoded.hasClaim("userId")).isFalse();
        assertThat(decoded.getClaimAsString("email")).isEqualTo("user@example.com");
        assertThat(decoded.getClaimAsString("role")).isEqualTo("SYSTEM_ADMIN");
        assertThat(issued.expiresInSeconds()).isEqualTo(3600);
    }

    @Test
    void rejectsExpiredToken() {
        Clock pastClock = Clock.fixed(Instant.parse("2020-01-01T00:00:00Z"), ZoneOffset.UTC);
        JwtTokenService service = tokenService(pastClock, 60);
        String token = service.issue(user(8L, UserRole.USER)).value();

        assertThatThrownBy(() -> service.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenWithoutExpiration() {
        JwtTokenService service = tokenService(Clock.systemUTC(), 3600);

        assertThatThrownBy(() -> service.decode(customTokenWithoutExpiration()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTamperedToken() {
        JwtTokenService service = tokenService(Clock.systemUTC(), 3600);
        String token = service.issue(user(9L, UserRole.USER)).value();
        String[] parts = token.split("\\.");
        char replacement = parts[1].charAt(0) == 'a' ? 'b' : 'a';
        parts[1] = replacement + parts[1].substring(1);
        String tampered = String.join(".", parts);

        assertThatThrownBy(() -> service.decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenWithoutIssuer() {
        JwtTokenService service = tokenService(Clock.systemUTC(), 3600);

        assertThatThrownBy(() -> service.decode(customToken(null)))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenFromAnotherIssuer() {
        JwtTokenService service = tokenService(Clock.systemUTC(), 3600);

        assertThatThrownBy(() -> service.decode(customToken("another-service")))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void redactsIssuedAccessTokenFromStringRepresentation() {
        IssuedAccessToken token = tokenService(Clock.systemUTC(), 3600).issue(user(11L, UserRole.USER));

        assertThat(token.toString())
                .doesNotContain(token.value())
                .contains("[REDACTED]")
                .contains("expiresInSeconds=3600");
    }

    private JwtTokenService tokenService(Clock clock, long expirationSeconds) {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        timestampValidator.setAllowEmptyExpiryClaim(false);
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(List.of(
                timestampValidator,
                new JwtIssuerValidator("prizm"))));
        return new JwtTokenService(
                NimbusJwtEncoder.withSecretKey(key).build(),
                decoder,
                new JwtProperties(SECRET, expirationSeconds, "prizm"),
                clock);
    }

    private String customToken(String issuer) {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .subject("10")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .claim("email", "user@example.com")
                .claim("role", "USER");
        if (issuer != null) {
            claims.issuer(issuer);
        }
        return NimbusJwtEncoder.withSecretKey(key).build()
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                        claims.build()))
                .getTokenValue();
    }

    private String customTokenWithoutExpiration() {
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("prizm")
                .subject("10")
                .issuedAt(Instant.now())
                .claim("email", "user@example.com")
                .claim("role", "USER")
                .build();
        return NimbusJwtEncoder.withSecretKey(key).build()
                .encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
                        claims))
                .getTokenValue();
    }

    private UserAccount user(Long id, UserRole role) {
        UserAccount user = UserAccount.create("user@example.com", "unused-hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }
}
