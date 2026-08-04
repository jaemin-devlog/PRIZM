package com.prizm.auth.service;

import com.prizm.auth.bootstrap.BcryptPasswordPolicy;
import com.prizm.auth.dto.response.AuthenticatedUserResponse;
import com.prizm.auth.dto.response.LoginResponse;
import com.prizm.auth.exception.LocalSingleUserUnavailableException;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Issues a normal JWT for the local demo account configured by the Docker Compose environment. */
@Service
@ConditionalOnProperty(prefix = "prizm.local-demo", name = "enabled", havingValue = "true")
public class LocalSingleUserSessionService {

    static final String LOCAL_USER_EMAIL = "local@prizm.local";

    private final UserAccountRepository userAccountRepository;
    private final BcryptPasswordPolicy passwordPolicy;
    private final JwtTokenService jwtTokenService;
    private final SecureRandom secureRandom;

    @Autowired
    public LocalSingleUserSessionService(
            UserAccountRepository userAccountRepository,
            BcryptPasswordPolicy passwordPolicy,
            JwtTokenService jwtTokenService) {
        this(userAccountRepository, passwordPolicy, jwtTokenService, new SecureRandom());
    }

    LocalSingleUserSessionService(
            UserAccountRepository userAccountRepository,
            BcryptPasswordPolicy passwordPolicy,
            JwtTokenService jwtTokenService,
            SecureRandom secureRandom) {
        this.userAccountRepository = userAccountRepository;
        this.passwordPolicy = passwordPolicy;
        this.jwtTokenService = jwtTokenService;
        this.secureRandom = secureRandom;
    }

    @Transactional
    public synchronized LoginResponse startSession() {
        UserAccount user = userAccountRepository.findByEmail(LOCAL_USER_EMAIL).orElse(null);

        if (user == null) {
            user = userAccountRepository.saveAndFlush(UserAccount.create(
                    LOCAL_USER_EMAIL,
                    passwordPolicy.encode(newInternalPassword()),
                    UserRole.USER));
        }
        else if (!user.isEnabled() || user.getRole() != UserRole.USER) {
            throw new LocalSingleUserUnavailableException();
        }

        IssuedAccessToken token = jwtTokenService.issue(user);
        return new LoginResponse(
                token.value(),
                "Bearer",
                token.expiresInSeconds(),
                AuthenticatedUserResponse.from(user));
    }

    private String newInternalPassword() {
        byte[] bytes = new byte[48];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
