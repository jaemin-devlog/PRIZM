package com.prizm.auth.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * 인증 변환기가 DB 상태까지 확인한 JWT의 subject를 양의 사용자 ID로 해석한다.
 * 인증을 새로 수행하지 않고, 이미 구성된 요청 보안 컨텍스트만 읽는다.
 */
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
