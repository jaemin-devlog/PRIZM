package com.prizm.auth.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/** 검증된 JWT의 subject에서 현재 사용자 ID를 제공한다. */
@Component
public class CurrentUserProvider {

    public Long userId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)
                || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated user is required.");
        }

        try {
            long userId = Long.parseLong(jwtAuthentication.getToken().getSubject());
            if (userId <= 0) {
                throw new NumberFormatException("User ID must be positive.");
            }
            return userId;
        }
        catch (NumberFormatException exception) {
            throw new AuthenticationCredentialsNotFoundException("Authenticated user ID is invalid.", exception);
        }
    }
}
