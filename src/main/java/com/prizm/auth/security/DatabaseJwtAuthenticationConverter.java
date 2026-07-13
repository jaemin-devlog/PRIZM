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

/** JWT 서명뿐 아니라 현재 DB의 사용자 상태와 권한도 요청마다 확인한다. */
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
