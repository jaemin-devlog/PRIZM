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

/** Creates one local demo USER only during an explicitly enabled bootstrap run. */
@Component
@ConditionalOnProperty(prefix = "prizm.bootstrap-demo-user", name = "enabled", havingValue = "true")
public class DemoUserBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoUserBootstrapRunner.class);

    private final BootstrapDemoUserProperties properties;
    private final UserAccountRepository userAccountRepository;
    private final BcryptPasswordPolicy passwordPolicy;
    private final Validator validator;

    public DemoUserBootstrapRunner(
            BootstrapDemoUserProperties properties,
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
        if (userAccountRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new IllegalStateException(
                    "Bootstrap demo USER was not created because the configured email already exists");
        }

        UserAccount demoUser = UserAccount.create(
                normalizedEmail,
                passwordPolicy.encode(properties.password()),
                UserRole.USER);
        userAccountRepository.saveAndFlush(demoUser);
        log.info("One-time bootstrap demo USER created. Disable bootstrap before the next start.");
    }

    private void validateSettings() {
        Set<ConstraintViolation<BootstrapDemoUserProperties>> violations = validator.validate(properties);
        if (!violations.isEmpty()) {
            String fields = violations.stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .sorted()
                    .collect(Collectors.joining(", "));
            throw new IllegalStateException("Invalid bootstrap demo USER settings: " + fields);
        }
        if (!passwordPolicy.isWithinLimit(properties.password())) {
            throw new IllegalStateException("Invalid bootstrap demo USER settings: password");
        }
    }
}
