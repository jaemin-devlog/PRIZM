package com.prizm.auth.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.Ordered;

class BootstrapAccountConflictGuardTest {

    @Test
    void failsBeforeAccountBootstrapWhenBothModesAreEnabled() {
        BootstrapAccountConflictGuard guard = new BootstrapAccountConflictGuard(
                systemAdmin(true), demoUser(true));

        assertThat(guard.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThatThrownBy(() -> guard.run(new DefaultApplicationArguments(new String[0])))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot be enabled at the same time");
    }

    @Test
    void allowsEitherBootstrapModeByItself() {
        assertThatCode(() -> new BootstrapAccountConflictGuard(systemAdmin(true), demoUser(false))
                        .run(new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
        assertThatCode(() -> new BootstrapAccountConflictGuard(systemAdmin(false), demoUser(true))
                        .run(new DefaultApplicationArguments(new String[0])))
                .doesNotThrowAnyException();
    }

    private BootstrapSystemAdminProperties systemAdmin(boolean enabled) {
        return new BootstrapSystemAdminProperties(enabled, "admin@prizm.local", "strong-password");
    }

    private BootstrapDemoUserProperties demoUser(boolean enabled) {
        return new BootstrapDemoUserProperties(enabled, "user@prizm.local", "strong-password");
    }
}
