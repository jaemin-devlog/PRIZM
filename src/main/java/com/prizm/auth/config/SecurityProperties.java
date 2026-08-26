package com.prizm.auth.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** CORS에 허용할 명시적인 HTTP(S) origin만 보관하며 wildcard와 경로가 붙은 값을 거부한다. */
@ConfigurationProperties("prizm.security")
public record SecurityProperties(List<String> allowedOrigins) {

    public SecurityProperties {
        if (allowedOrigins == null || allowedOrigins.isEmpty()) {
            throw new IllegalArgumentException("prizm.security.allowed-origins must not be empty");
        }
        allowedOrigins = allowedOrigins.stream()
                .map(SecurityProperties::validateOrigin)
                .toList();
    }

    private static String validateOrigin(String origin) {
        if (origin == null || origin.isBlank() || "*".equals(origin.trim())) {
            throw new IllegalArgumentException("prizm.security.allowed-origins must contain explicit origins");
        }
        String normalized = origin.trim();
        try {
            URI uri = new URI(normalized);
            boolean http = "http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme());
            if (!http
                    || uri.getHost() == null
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty())) {
                throw new IllegalArgumentException("Invalid CORS origin: " + normalized);
            }
            return normalized;
        }
        catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Invalid CORS origin: " + normalized, exception);
        }
    }
}
