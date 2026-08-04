package com.prizm.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.auth.bootstrap.BcryptPasswordPolicy;
import com.prizm.auth.dto.response.LoginResponse;
import com.prizm.auth.exception.LocalSingleUserUnavailableException;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class LocalSingleUserSessionServiceTest {

    private final UserAccountRepository userAccountRepository = mock(UserAccountRepository.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4);
    private LocalSingleUserSessionService service;

    @BeforeEach
    void setUp() {
        service = new LocalSingleUserSessionService(
                userAccountRepository,
                new BcryptPasswordPolicy(passwordEncoder),
                jwtTokenService);
        when(jwtTokenService.issue(any(UserAccount.class))).thenReturn(new IssuedAccessToken("local-token", 3600));
    }

    @Test
    void createsTheLocalUserAndIssuesTheExistingJwtContract() {
        when(userAccountRepository.findByEmail(LocalSingleUserSessionService.LOCAL_USER_EMAIL))
                .thenReturn(Optional.empty());
        when(userAccountRepository.saveAndFlush(any(UserAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LoginResponse response = service.startSession();

        ArgumentCaptor<UserAccount> userCaptor = ArgumentCaptor.forClass(UserAccount.class);
        verify(userAccountRepository).saveAndFlush(userCaptor.capture());
        UserAccount user = userCaptor.getValue();
        assertThat(user.getEmail()).isEqualTo(LocalSingleUserSessionService.LOCAL_USER_EMAIL);
        assertThat(user.getRole()).isEqualTo(UserRole.USER);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.getPasswordHash()).isNotBlank();
        assertThat(response.accessToken()).isEqualTo("local-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        verify(jwtTokenService).issue(user);
    }

    @Test
    void reusesTheExpectedEnabledUser() {
        UserAccount localUser = UserAccount.create(
                LocalSingleUserSessionService.LOCAL_USER_EMAIL,
                passwordEncoder.encode("existing-password"),
                UserRole.USER);
        when(userAccountRepository.findByEmail(LocalSingleUserSessionService.LOCAL_USER_EMAIL))
                .thenReturn(Optional.of(localUser));

        LoginResponse response = service.startSession();

        assertThat(response.accessToken()).isEqualTo("local-token");
        verify(userAccountRepository, never()).saveAndFlush(any(UserAccount.class));
        verify(jwtTokenService).issue(localUser);
    }

    @Test
    void refusesALocalAccountWithUnexpectedRole() {
        UserAccount localUser = UserAccount.create(
                LocalSingleUserSessionService.LOCAL_USER_EMAIL,
                passwordEncoder.encode("existing-password"),
                UserRole.SYSTEM_ADMIN);
        when(userAccountRepository.findByEmail(LocalSingleUserSessionService.LOCAL_USER_EMAIL))
                .thenReturn(Optional.of(localUser));

        assertThatThrownBy(service::startSession)
                .isInstanceOf(LocalSingleUserUnavailableException.class);
        verify(jwtTokenService, never()).issue(any(UserAccount.class));
    }
}
