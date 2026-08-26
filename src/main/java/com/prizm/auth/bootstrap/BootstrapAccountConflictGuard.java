package com.prizm.auth.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * SYSTEM_ADMIN과 데모 USER 부트스트랩이 동시에 켜진 설정을 시작 단계에서 거부한다.
 * 두 runner보다 먼저 실행해 부트스트랩 계정이 하나라도 생성되기 전에 실패시킨다.
 */
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
