package com.prizm.auth.security;

import com.prizm.user.entity.UserAccount;
import com.prizm.user.repository.UserAccountRepository;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * 서명 검증을 통과한 JWT를 현재 DB 계정과 대조해 요청의 인증 정보를 만든다.
 * 토큰의 이메일과 역할은 발급 당시의 값이므로, 계정 비활성화나 속성 변경을 즉시 반영하려면
 * 활성 상태·이메일·역할을 요청마다 다시 확인해야 한다. 계정이 없거나 값이 다르면 인증 실패로 처리한다.
 */
@Component
public class DatabaseJwtAuthenticationConverter
        implements Converter<Jwt, AbstractOAuth2TokenAuthenticationToken<Jwt>> {

    private final UserAccountRepository userAccountRepository;

    public DatabaseJwtAuthenticationConverter(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Override
    public AbstractOAuth2TokenAuthenticationToken<Jwt> convert(Jwt jwt) {
        Long userId = parseUserId(jwt.getSubject());
        UserAccount user = userAccountRepository.findById(userId)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Invalid access token"));

        if (!user.getEmail().equals(jwt.getClaimAsString("email"))
                || !user.getRole().name().equals(jwt.getClaimAsString("role"))) {
            throw new BadCredentialsException("Invalid access token");
        }

        return new JwtAuthenticationToken(
                jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())),
                user.getEmail());
    }

    private Long parseUserId(String subject) {
        try {
            Long userId = Long.valueOf(subject);
            if (userId <= 0) {
                throw new BadCredentialsException("Invalid access token");
            }
            return userId;
        }
        catch (NumberFormatException | NullPointerException exception) {
            throw new BadCredentialsException("Invalid access token", exception);
        }
    }
}
