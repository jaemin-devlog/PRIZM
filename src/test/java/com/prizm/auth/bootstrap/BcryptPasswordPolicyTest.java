package com.prizm.auth.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class BcryptPasswordPolicyTest {

    @Test
    void acceptsExactlySeventyTwoUtf8BytesForAsciiAndMultibytePasswords() {
        BcryptPasswordPolicy policy = new BcryptPasswordPolicy(new BCryptPasswordEncoder(4));
        String asciiPassword = "a".repeat(72);
        String multibytePassword = "가".repeat(24);

        assertThat(policy.matches(asciiPassword, policy.encode(asciiPassword))).isTrue();
        assertThat(policy.matches(multibytePassword, policy.encode(multibytePassword))).isTrue();
    }

    @Test
    void rejectsPasswordOverSeventyTwoUtf8BytesBeforeEncodingOrMatching() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        BcryptPasswordPolicy policy = new BcryptPasswordPolicy(encoder);
        String asciiPassword = "a".repeat(73);
        String multibytePassword = "가".repeat(25);

        assertThatThrownBy(() -> policy.encode(asciiPassword))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 UTF-8 bytes");
        assertThat(policy.matches(multibytePassword, "encoded-password")).isFalse();
        verify(encoder, never()).encode(anyString());
        verify(encoder, never()).matches(anyString(), anyString());
    }
}
