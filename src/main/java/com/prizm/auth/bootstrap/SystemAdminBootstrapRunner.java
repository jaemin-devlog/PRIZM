package com.prizm.auth.bootstrap;

import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 명시적으로 활성화된 한 번의 실행에서만 최초 SYSTEM_ADMIN 계정을 생성한다. */
@Component
@ConditionalOnProperty(prefix = "prizm.bootstrap-system-admin", name = "enabled", havingValue = "true")
public class SystemAdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SystemAdminBootstrapRunner.class);

    private final BootstrapSystemAdminProperties properties;
    private final UserAccountRepository userAccountRepository;
    private final BcryptPasswordPolicy passwordPolicy;
    private final Validator validator;

    public SystemAdminBootstrapRunner(
            BootstrapSystemAdminProperties properties,
            UserAccountRepository userAccountRepository,
            BcryptPasswordPolicy passwordPolicy,
            Validator validator) {
        this.properties = properties;
        this.userAccountRepository = userAccountRepository;
        this.passwordPolicy = passwordPolicy;
        this.validator = validator;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        validateSettings();
        String normalizedEmail = UserAccount.normalizeEmail(properties.email());

        if (userAccountRepository.existsByRole(UserRole.SYSTEM_ADMIN)) {
            throw new IllegalStateException(
                    "Bootstrap SYSTEM_ADMIN was not created because a SYSTEM_ADMIN account already exists");
        }
        if (userAccountRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalStateException(
                    "Bootstrap SYSTEM_ADMIN was not created because the configured email already exists");
        }

        UserAccount systemAdmin = UserAccount.create(
                normalizedEmail,
                passwordPolicy.encode(properties.password()),
                UserRole.SYSTEM_ADMIN);
        userAccountRepository.saveAndFlush(systemAdmin);
        log.info("One-time bootstrap SYSTEM_ADMIN created. Disable bootstrap before the next start.");
    }

    private void validateSettings() {
        Set<ConstraintViolation<BootstrapSystemAdminProperties>> violations = validator.validate(properties);
        if (!violations.isEmpty()) {
            String fields = violations.stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Invalid bootstrap SYSTEM_ADMIN settings: " + fields);
        }
        if (!passwordPolicy.isWithinLimit(properties.password())) {
            throw new IllegalStateException("Invalid bootstrap SYSTEM_ADMIN settings: password");
        }
    }
}
