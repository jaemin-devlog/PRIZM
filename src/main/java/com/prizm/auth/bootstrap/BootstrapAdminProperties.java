package com.prizm.auth.bootstrap;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("prizm.bootstrap-admin")
public record BootstrapAdminProperties(
        boolean enabled,
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 12, max = 200) String password) {

    @Override
    public String toString() {
        return "BootstrapAdminProperties[enabled=" + enabled + ", email=" + email + ", password=[REDACTED]]";
    }
}
