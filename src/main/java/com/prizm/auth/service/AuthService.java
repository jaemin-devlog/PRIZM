package com.prizm.auth.service;

import com.prizm.auth.bootstrap.BcryptPasswordPolicy;
import com.prizm.auth.dto.request.LoginRequest;
import com.prizm.auth.dto.response.AuthenticatedUserResponse;
import com.prizm.auth.dto.response.LoginResponse;
import com.prizm.auth.exception.InvalidCredentialsException;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 계정의 회원가입과 비밀번호 로그인을 처리한다.
 * 회원가입 역할은 USER로 고정하고, 로그인은 현재 활성화된 계정에만 access token을 발급한다.
 */
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
        // 존재하지 않는 이메일도 BCrypt 비교를 거쳐 계정 유무에 따른 처리 시간 차이를 줄인다.
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

    @Transactional
    public void signup(LoginRequest request) {
        String normalizedEmail = UserAccount.normalizeEmail(request.email());
        if (userAccountRepository.findByEmail(normalizedEmail).isPresent()) {
            throw new DataIntegrityViolationException("Email is already registered");
        }

        String passwordHash = passwordPolicy.encode(request.password());
        try {
            userAccountRepository.saveAndFlush(UserAccount.create(
                    normalizedEmail,
                    passwordHash,
                    UserRole.USER));
        }
        catch (DataIntegrityViolationException exception) {
            throw new DataIntegrityViolationException("Email is already registered", exception);
        }
    }
}
