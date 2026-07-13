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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 명시적으로 활성화된 한 번의 실행에서만 최초 ADMIN을 생성한다. */
@Component
@ConditionalOnProperty(prefix = "prizm.bootstrap-admin", name = "enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final BootstrapAdminProperties properties;
    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Validator validator;

    public AdminBootstrapRunner(
            BootstrapAdminProperties properties,
            UserAccountRepository userAccountRepository,
            PasswordEncoder passwordEncoder,
            Validator validator) {
        this.properties = properties;
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.validator = validator;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        validateSettings();
        String normalizedEmail = UserAccount.normalizeEmail(properties.email());

        if (userAccountRepository.existsByRole(UserRole.ADMIN)) {
            throw new IllegalStateException(
                    "Bootstrap ADMIN was not created because an ADMIN account already exists");
        }
        if (userAccountRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalStateException(
                    "Bootstrap ADMIN was not created because the configured email already exists");
        }

        UserAccount admin = UserAccount.create(
                normalizedEmail,
                passwordEncoder.encode(properties.password()),
                UserRole.ADMIN);
        userAccountRepository.saveAndFlush(admin);
        log.info("One-time bootstrap ADMIN created for {}. Disable bootstrap before the next start.", normalizedEmail);
    }

    private void validateSettings() {
        Set<ConstraintViolation<BootstrapAdminProperties>> violations = validator.validate(properties);
        if (!violations.isEmpty()) {
            String fields = violations.stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Invalid bootstrap ADMIN settings: " + fields);
        }
    }
}
