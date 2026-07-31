package com.prizm.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prizm.auth.bootstrap.BcryptPasswordPolicy;
import com.prizm.auth.dto.request.LoginRequest;
import com.prizm.auth.dto.response.LoginResponse;
import com.prizm.auth.exception.InvalidCredentialsException;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthServiceTest {

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private final BcryptPasswordPolicy passwordPolicy = new BcryptPasswordPolicy(passwordEncoder);
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userAccountRepository, passwordPolicy, jwtTokenService);
    }

    @Test
    void logsInWithARealBcryptPasswordHash() {
        UserAccount user = UserAccount.create(
                "system-admin@example.com",
                passwordEncoder.encode("correct-password"),
                UserRole.SYSTEM_ADMIN);
        when(userAccountRepository.findByEmail("system-admin@example.com")).thenReturn(Optional.of(user));
        when(jwtTokenService.issue(user)).thenReturn(new IssuedAccessToken("signed-token", 3600));

        LoginResponse response = authService.login(
                new LoginRequest("SYSTEM-ADMIN@example.com", "correct-password"));

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().role()).isEqualTo(UserRole.SYSTEM_ADMIN);
    }

    @Test
    void rejectsWrongPassword() {
        UserAccount user = UserAccount.create(
                "user@example.com",
                passwordEncoder.encode("correct-password"),
                UserRole.USER);
        when(userAccountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("user@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email or password is incorrect");
    }

    @Test
    void rejectsMissingAccountWithTheSamePublicError() {
        when(userAccountRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("missing@example.com", "any-password")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email or password is incorrect");
        verify(userAccountRepository).findByEmail("missing@example.com");
    }

    @Test
    void rejectsDisabledAccount() {
        UserAccount user = UserAccount.createDisabled(
                "disabled@example.com",
                passwordEncoder.encode("correct-password"),
                UserRole.USER);
        when(userAccountRepository.findByEmail("disabled@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> authService.login(new LoginRequest("disabled@example.com", "correct-password")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email or password is incorrect");
    }

    @Test
    void rejectsPasswordOverBcryptUtf8LimitBeforeLookingUpAccount() {
        assertThatThrownBy(() -> authService.login(
                        new LoginRequest("user@example.com", "가".repeat(25))))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Email or password is incorrect");
        verifyNoInteractions(userAccountRepository, jwtTokenService);
    }

    @Test
    void redactsLoginEmailAndPasswordFromToString() {
        LoginRequest request = new LoginRequest("user@example.com", "strong-password");

        assertThat(request.toString())
                .doesNotContain("user@example.com", "strong-password")
                .contains("email=[REDACTED]", "password=[REDACTED]");
    }
}
