package com.prizm.auth.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/** Stops startup before either bootstrap runner can write when both account modes are enabled. */
@Component
public class BootstrapAccountConflictGuard implements ApplicationRunner, Ordered {

    private final BootstrapSystemAdminProperties systemAdminProperties;
    private final BootstrapDemoUserProperties demoUserProperties;

    public BootstrapAccountConflictGuard(
            BootstrapSystemAdminProperties systemAdminProperties,
            BootstrapDemoUserProperties demoUserProperties) {
        this.systemAdminProperties = systemAdminProperties;
        this.demoUserProperties = demoUserProperties;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        if (systemAdminProperties.enabled() && demoUserProperties.enabled()) {
            throw new IllegalStateException(
                    "SYSTEM_ADMIN and demo USER bootstrap cannot be enabled at the same time");
        }
    }
}
