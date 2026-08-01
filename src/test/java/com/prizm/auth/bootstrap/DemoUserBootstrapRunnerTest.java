package com.prizm.auth.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class DemoUserBootstrapRunnerTest {

    private UserAccountRepository repository;
    private PasswordEncoder passwordEncoder;
    private BcryptPasswordPolicy passwordPolicy;
    private Validator validator;

    @BeforeEach
    void setUp() {
        repository = mock(UserAccountRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        passwordPolicy = new BcryptPasswordPolicy(passwordEncoder);
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void isNotRegisteredWhenBootstrapIsDisabledByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(BootstrapTestConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(DemoUserBootstrapRunner.class));
    }

    @Test
    void createsOneEnabledUserWithNormalizedEmailAndBcryptHash() throws Exception {
        when(repository.findByEmail("user@prizm.local")).thenReturn(Optional.empty());

        runner(properties("  USER@Prizm.Local  ", "strong-password"))
                .run(new DefaultApplicationArguments(new String[0]));

        verify(repository).findByEmail("user@prizm.local");
        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).saveAndFlush(captor.capture());
        UserAccount saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("user@prizm.local");
        assertThat(saved.getRole()).isEqualTo(UserRole.USER);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getPasswordHash()).isNotEqualTo("strong-password");
        assertThat(passwordEncoder.matches("strong-password", saved.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsExistingEmailWithoutChangingTheAccount() {
        UserAccount existing = UserAccount.create(
                "user@prizm.local", passwordEncoder.encode("existing-password"), UserRole.SYSTEM_ADMIN);
        String originalPasswordHash = existing.getPasswordHash();
        when(repository.findByEmail("user@prizm.local")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> runner(properties("USER@PRIZM.LOCAL", "strong-password"))
                        .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email already exists");
        verify(repository, never()).saveAndFlush(any());
        assertThat(existing.getRole()).isEqualTo(UserRole.SYSTEM_ADMIN);
        assertThat(existing.isEnabled()).isTrue();
        assertThat(existing.getPasswordHash()).isEqualTo(originalPasswordHash);
    }

    @Test
    void rejectsBlankEmailBeforeRepositoryAccess() {
        assertThatThrownBy(() -> runner(properties("   ", "strong-password"))
                        .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid bootstrap demo USER settings: email");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsNullEmailBeforeRepositoryAccess() {
        assertThatThrownBy(() -> runner(properties(null, "strong-password"))
                        .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid bootstrap demo USER settings: email");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsMalformedNormalizedEmailBeforeRepositoryAccess() {
        assertThatThrownBy(() -> runner(properties("  not-an-email  ", "strong-password"))
                        .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid bootstrap demo USER settings: email");
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsPasswordOverBcryptUtf8LimitBeforeSaving() {
        assertThatThrownBy(() -> runner(properties("user@prizm.local", "가".repeat(25)))
                        .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Invalid bootstrap demo USER settings: password");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsBlankPasswordBeforeRepositoryAccess() {
        assertThatThrownBy(() -> runner(properties("user@prizm.local", ""))
                        .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("password");
        verifyNoInteractions(repository);
    }

    @Test
    void redactsEmailAndPasswordFromToString() {
        BootstrapDemoUserProperties properties = properties("user@prizm.local", "strong-password");

        assertThat(properties.toString())
                .doesNotContain("user@prizm.local", "strong-password")
                .contains("email=[REDACTED]", "password=[REDACTED]");
    }

    private BootstrapDemoUserProperties properties(String email, String password) {
        return new BootstrapDemoUserProperties(true, email, password);
    }

    private DemoUserBootstrapRunner runner(BootstrapDemoUserProperties properties) {
        return new DemoUserBootstrapRunner(properties, repository, passwordPolicy, validator);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BootstrapDemoUserProperties.class)
    @Import(DemoUserBootstrapRunner.class)
    static class BootstrapTestConfiguration {

        @Bean
        UserAccountRepository userAccountRepository() {
            return mock(UserAccountRepository.class);
        }

        @Bean
        PasswordEncoder passwordEncoder() {
            return new BCryptPasswordEncoder(4);
        }

        @Bean
        BcryptPasswordPolicy bcryptPasswordPolicy(PasswordEncoder passwordEncoder) {
            return new BcryptPasswordPolicy(passwordEncoder);
        }

        @Bean
        Validator validator() {
            return Validation.buildDefaultValidatorFactory().getValidator();
        }
    }
}
