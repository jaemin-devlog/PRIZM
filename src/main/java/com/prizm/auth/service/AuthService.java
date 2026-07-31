package com.prizm.auth.service;

import com.prizm.auth.bootstrap.BcryptPasswordPolicy;
import com.prizm.auth.dto.request.LoginRequest;
import com.prizm.auth.dto.response.AuthenticatedUserResponse;
import com.prizm.auth.dto.response.LoginResponse;
import com.prizm.auth.exception.InvalidCredentialsException;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.repository.UserAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserAccountRepository userAccountRepository;
    private final BcryptPasswordPolicy passwordPolicy;
    private final JwtTokenService jwtTokenService;
    private final String missingUserPasswordHash;

    public AuthService(
            UserAccountRepository userAccountRepository,
            BcryptPasswordPolicy passwordPolicy,
            JwtTokenService jwtTokenService) {
        this.userAccountRepository = userAccountRepository;
        this.passwordPolicy = passwordPolicy;
        this.jwtTokenService = jwtTokenService;
        this.missingUserPasswordHash = passwordPolicy.encode("missing-user-password-check");
    }

    /** 계정 존재 여부를 노출하지 않도록 모든 로그인 실패를 같은 응답으로 처리한다. */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        if (!passwordPolicy.isWithinLimit(request.password())) {
            throw new InvalidCredentialsException();
        }
        UserAccount user = userAccountRepository.findByEmail(UserAccount.normalizeEmail(request.email())).orElse(null);
        String passwordHash = user == null ? missingUserPasswordHash : user.getPasswordHash();
        boolean passwordMatches = passwordPolicy.matches(request.password(), passwordHash);
        if (user == null || !passwordMatches || !user.isEnabled()) {
            throw new InvalidCredentialsException();
        }

        IssuedAccessToken token = jwtTokenService.issue(user);
        return new LoginResponse(
                token.value(),
                "Bearer",
                token.expiresInSeconds(),
                AuthenticatedUserResponse.from(user));
    }
}
