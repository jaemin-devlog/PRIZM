package com.prizm.user.service;

import com.prizm.user.dto.response.UserResponse;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.repository.UserAccountRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserQueryService {

    private final UserAccountRepository userAccountRepository;

    public UserQueryService(UserAccountRepository userAccountRepository) {
        this.userAccountRepository = userAccountRepository;
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(String email) {
        UserAccount user = userAccountRepository.findByEmail(UserAccount.normalizeEmail(email))
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new BadCredentialsException("Authenticated user is unavailable"));
        return UserResponse.from(user);
    }
}
