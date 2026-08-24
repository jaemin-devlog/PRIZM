package com.prizm.auth.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenErrors;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;

/**
 * 하나의 Authorization 헤더에 담긴 정상적인 Bearer 값만 인증 계층으로 넘긴다.
 * 중복 헤더와 쉼표로 합친 값은 어느 token을 인증에 쓸지 모호하므로 거부한다.
 */
@Component
public class StrictBearerTokenResolver implements BearerTokenResolver {

    private static final Pattern BEARER_PATTERN = Pattern.compile(
            "^Bearer (?<token>[A-Za-z0-9\\-._~+/]+=*)$",
            Pattern.CASE_INSENSITIVE);

    @Override
    public String resolve(HttpServletRequest request) {
        List<String> authorizationHeaders = Collections.list(request.getHeaders(HttpHeaders.AUTHORIZATION));
        if (authorizationHeaders.isEmpty()) {
            return null;
        }
        if (authorizationHeaders.size() != 1) {
            throw invalidBearerToken();
        }

        String authorization = authorizationHeaders.get(0);
        if (!authorization.regionMatches(true, 0, "Bearer", 0, "Bearer".length())) {
            return null;
        }
        if (authorization.indexOf(',') >= 0) {
            throw invalidBearerToken();
        }

        Matcher matcher = BEARER_PATTERN.matcher(authorization);
        if (!matcher.matches()) {
            throw invalidBearerToken();
        }
        return matcher.group("token");
    }

    private OAuth2AuthenticationException invalidBearerToken() {
        return new OAuth2AuthenticationException(BearerTokenErrors.invalidToken("Invalid bearer token"));
    }
}
