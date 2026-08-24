package com.prizm.auth.bootstrap;

import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 명시적으로 활성화한 실행에서 로컬 데모용 USER 한 명을 만든다.
 * 기존 이메일을 덮어쓰지 않으며, SYSTEM_ADMIN 생성 경로와 함께 사용할 수 없다.
 */
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
        String normalizedEmail = normalizeAndValidateEmail();
        validatePassword();
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

    private String normalizeAndValidateEmail() {
        String configuredEmail = properties.email();
        if (configuredEmail == null) {
            throw new IllegalStateException("Invalid bootstrap demo USER settings: email");
        }
        String normalizedEmail = UserAccount.normalizeEmail(configuredEmail);
        if (normalizedEmail.isBlank()) {
            throw new IllegalStateException("Invalid bootstrap demo USER settings: email");
        }
        Set<ConstraintViolation<NormalizedDemoEmail>> violations =
                validator.validate(new NormalizedDemoEmail(normalizedEmail));
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Invalid bootstrap demo USER settings: email");
        }
        return normalizedEmail;
    }

    private void validatePassword() {
        Set<ConstraintViolation<BootstrapDemoUserProperties>> violations =
                validator.validateProperty(properties, "password");
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

    private record NormalizedDemoEmail(
            @NotBlank @Email @Size(max = 320) String email) {
    }
}
