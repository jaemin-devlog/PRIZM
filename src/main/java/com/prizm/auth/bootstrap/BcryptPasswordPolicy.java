package com.prizm.auth.bootstrap;

import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Enforces BCrypt's raw-password limit before encoding or matching. */
@Component
public class BcryptPasswordPolicy {

    static final int MAX_UTF8_BYTES = 72;

    private final PasswordEncoder passwordEncoder;

    public BcryptPasswordPolicy(PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    public boolean isWithinLimit(String rawPassword) {
        return rawPassword != null
                && rawPassword.getBytes(StandardCharsets.UTF_8).length <= MAX_UTF8_BYTES;
    }

    public String encode(String rawPassword) {
        if (!isWithinLimit(rawPassword)) {
            throw new IllegalArgumentException("Password must be at most 72 UTF-8 bytes for BCrypt");
        }
        return passwordEncoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        return isWithinLimit(rawPassword) && passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
