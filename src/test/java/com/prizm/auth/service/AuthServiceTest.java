package com.prizm.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prizm.auth.dto.request.LoginRequest;
import com.prizm.auth.dto.response.LoginResponse;
import com.prizm.auth.exception.InvalidCredentialsException;
import com.prizm.auth.security.BcryptPasswordPolicy;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
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
                "user@example.com",
                passwordEncoder.encode("correct-password"),
                UserRole.USER);
        when(userAccountRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtTokenService.issue(user)).thenReturn(new IssuedAccessToken("signed-token", 3600));

        LoginResponse response = authService.login(
                new LoginRequest("USER@example.com", "correct-password"));

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.user().role()).isEqualTo(UserRole.USER);
    }

    @Test
    void signsUpAnEnabledUserWithANormalizedEmailAndBcryptPassword() {
        when(userAccountRepository.findByEmail("new-user@example.com")).thenReturn(Optional.empty());

        authService.signup(new LoginRequest(" NEW-USER@example.com ", "plain-password"));

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).saveAndFlush(userCaptor.capture());
        UserAccount savedUser = userCaptor.getValue();
        assertThat(savedUser.getEmail()).isEqualTo("new-user@example.com");
        assertThat(savedUser.getPasswordHash()).isNotEqualTo("plain-password");
        assertThat(passwordEncoder.matches("plain-password", savedUser.getPasswordHash())).isTrue();
        assertThat(savedUser.getRole()).isEqualTo(UserRole.USER);
        assertThat(savedUser.isEnabled()).isTrue();
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void rejectsDuplicateSignupBeforeEncodingOrSaving() {
        UserAccount existing = UserAccount.create(
                "existing@example.com",
                passwordEncoder.encode("existing-password"),
                UserRole.USER);
        when(userAccountRepository.findByEmail("existing@example.com")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> authService.signup(
                        new LoginRequest("EXISTING@example.com", "different-password")))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessage("Email is already registered");
        verifyNoInteractions(jwtTokenService);
    }

    @Test
    void newlySignedUpAccountCanUseTheExistingLoginFlow() {
        AtomicReference<UserAccount> storedUser = new AtomicReference<>();
        when(userAccountRepository.findByEmail("new-user@example.com"))
                .thenAnswer(invocation -> Optional.ofNullable(storedUser.get()));
        when(userAccountRepository.saveAndFlush(any(UserAccount.class))).thenAnswer(invocation -> {
            UserAccount savedUser = invocation.getArgument(0);
            storedUser.set(savedUser);
            return savedUser;
        });
        when(jwtTokenService.issue(any(UserAccount.class)))
                .thenReturn(new IssuedAccessToken("signed-token", 3600));

        authService.signup(new LoginRequest("new-user@example.com", "plain-password"));
        LoginResponse response = authService.login(
                new LoginRequest("new-user@example.com", "plain-password"));

        assertThat(response.accessToken()).isEqualTo("signed-token");
        assertThat(response.user().role()).isEqualTo(UserRole.USER);
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
