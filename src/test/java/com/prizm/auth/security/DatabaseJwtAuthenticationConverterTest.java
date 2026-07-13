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

    @Test
    void rejectsDeletedUser() {
        when(repository.findById(13L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> converter.convert(jwt("13", "user@example.com", "USER")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsNonNumericSubject() {
        assertThatThrownBy(() -> converter.convert(jwt("not-a-number", "user@example.com", "USER")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsNonPositiveSubject() {
        assertThatThrownBy(() -> converter.convert(jwt("0", "user@example.com", "USER")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsSubjectOutsideLongRange() {
        assertThatThrownBy(() -> converter.convert(jwt("9223372036854775808", "user@example.com", "USER")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsEmailThatDiffersFromDatabase() {
        UserAccount user = user(14L, UserRole.USER, true);
        when(repository.findById(14L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> converter.convert(jwt("14", "other@example.com", "USER")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsRoleThatDiffersFromDatabase() {
        UserAccount user = user(15L, UserRole.USER, true);
        when(repository.findById(15L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> converter.convert(jwt("15", "user@example.com", "ADMIN")))
                .isInstanceOf(BadCredentialsException.class);
    }

    private UserAccount user(Long id, UserRole role, boolean enabled) {
        UserAccount user = enabled
                ? UserAccount.create("user@example.com", "hash", role)
                : UserAccount.createDisabled("user@example.com", "hash", role);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Jwt jwt(UserAccount user) {
        return jwt(user.getId().toString(), user.getEmail(), user.getRole().name());
    }

    private Jwt jwt(String subject, String email, String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "HS256")
                .subject(subject)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim("email", email)
                .claim("role", role)
                .build();
    }
}
