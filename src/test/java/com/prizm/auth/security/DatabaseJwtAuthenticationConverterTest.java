package com.prizm.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.test.util.ReflectionTestUtils;

class DatabaseJwtAuthenticationConverterTest {

    private final UserAccountRepository repository = mock(UserAccountRepository.class);
    private final DatabaseJwtAuthenticationConverter converter = new DatabaseJwtAuthenticationConverter(repository);

    @Test
    void convertsAdminRoleToSpringSecurityAuthority() {
        UserAccount user = user(11L, UserRole.ADMIN, true);
        when(repository.findById(11L)).thenReturn(Optional.of(user));

        AbstractOAuth2TokenAuthenticationToken<Jwt> authentication = converter.convert(jwt(user));

        assertThat(authentication.getAuthorities()).extracting("authority").containsExactly("ROLE_ADMIN");
        assertThat(authentication.getName()).isEqualTo("user@example.com");
    }

    @Test
    void rejectsTokenAfterAccountIsDisabled() {
        UserAccount user = user(12L, UserRole.USER, false);
        when(repository.findById(12L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> converter.convert(jwt(user))).isInstanceOf(BadCredentialsException.class);
    }

    private UserAccount user(Long id, UserRole role, boolean enabled) {
        UserAccount user = enabled
                ? UserAccount.create("user@example.com", "hash", role)
                : UserAccount.createDisabled("user@example.com", "hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Jwt jwt(UserAccount user) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(user.getId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name())
                .build();
    }
}
