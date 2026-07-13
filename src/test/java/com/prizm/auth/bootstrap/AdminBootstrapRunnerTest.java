package com.prizm.auth.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

class AdminBootstrapRunnerTest {

    private UserAccountRepository repository;
    private PasswordEncoder passwordEncoder;
    private Validator validator;

    @BeforeEach
    void setUp() {
        repository = mock(UserAccountRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(4);
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void isNotRegisteredWhenBootstrapIsDisabledByDefault() {
        new ApplicationContextRunner()
                .withUserConfiguration(BootstrapTestConfiguration.class)
                .run(context -> assertThat(context).doesNotHaveBean(AdminBootstrapRunner.class));
    }

    @Test
    void createsOneEnabledAdminWithNormalizedEmailAndBcryptHash() throws Exception {
        BootstrapAdminProperties properties = properties("ADMIN@Prizm.Local", "strong-password");
        when(repository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(repository.findByEmail("admin@prizm.local")).thenReturn(Optional.empty());

        runner(properties).run(new DefaultApplicationArguments(new String[0]));

        ArgumentCaptor<UserAccount> captor = ArgumentCaptor.forClass(UserAccount.class);
        verify(repository).saveAndFlush(captor.capture());
        UserAccount saved = captor.getValue();
        assertThat(saved.getEmail()).isEqualTo("admin@prizm.local");
        assertThat(saved.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getPasswordHash()).isNotEqualTo("strong-password");
        assertThat(passwordEncoder.matches("strong-password", saved.getPasswordHash())).isTrue();
    }

    @Test
    void rejectsCreationWhenAdminAlreadyExists() {
        when(repository.existsByRole(UserRole.ADMIN)).thenReturn(true);

        assertThatThrownBy(() -> runner(properties("admin@prizm.local", "strong-password"))
                        .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN account already exists");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsCreationWhenEmailAlreadyExistsWithoutChangingAccount() {
        UserAccount existing = UserAccount.create(
                "admin@prizm.local", passwordEncoder.encode("existing-password"), UserRole.USER);
        when(repository.existsByRole(UserRole.ADMIN)).thenReturn(false);
        when(repository.findByEmail("admin@prizm.local")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> runner(properties("admin@prizm.local", "strong-password"))
                        .run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email already exists");
        verify(repository, never()).saveAndFlush(any());
        assertThat(passwordEncoder.matches("existing-password", existing.getPasswordHash())).isTrue();
    }

    @Test
    void failsSafelyWhenRequiredSettingsAreMissing() {
        BootstrapAdminProperties properties = new BootstrapAdminProperties(true, "", "");

        assertThatThrownBy(() -> runner(properties).run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("email")
                .hasMessageContaining("password");
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void redactsBootstrapPasswordFromToString() {
        BootstrapAdminProperties properties = properties("admin@prizm.local", "strong-password");

        assertThat(properties.toString()).doesNotContain("strong-password").contains("[REDACTED]");
    }

    private BootstrapAdminProperties properties(String email, String password) {
        return new BootstrapAdminProperties(true, email, password);
    }

    private AdminBootstrapRunner runner(BootstrapAdminProperties properties) {
        return new AdminBootstrapRunner(properties, repository, passwordEncoder, validator);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(BootstrapAdminProperties.class)
    @Import(AdminBootstrapRunner.class)
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
        Validator validator() {
            return Validation.buildDefaultValidatorFactory().getValidator();
        }
    }
}
