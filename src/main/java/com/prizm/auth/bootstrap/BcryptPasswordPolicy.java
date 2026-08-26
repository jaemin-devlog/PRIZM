package com.prizm.auth.bootstrap;

import java.nio.charset.StandardCharsets;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * BCrypt가 처리하는 72 UTF-8 바이트 경계를 인코딩과 비교에 똑같이 적용한다.
 * 한도를 넘긴 비밀번호를 암묵적으로 잘라 서로 다른 입력이 같은 값으로 취급되는 일을 막는다.
 */
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
