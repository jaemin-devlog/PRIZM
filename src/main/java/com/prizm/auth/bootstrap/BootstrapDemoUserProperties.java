package com.prizm.auth.bootstrap;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("prizm.bootstrap-demo-user")
public record BootstrapDemoUserProperties(
        boolean enabled,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 72) String password) {

    @Override
    public String toString() {
        return "BootstrapDemoUserProperties[enabled=" + enabled
                + ", email=[REDACTED], password=[REDACTED]]";
    }
}
