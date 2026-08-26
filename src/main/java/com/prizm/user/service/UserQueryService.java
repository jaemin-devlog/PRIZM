package com.prizm.user.service;

import com.prizm.user.dto.response.UserResponse;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.repository.UserAccountRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 요청의 사용자를 DB에서 다시 읽어 API 응답으로 만든다.
 * 인증이 끝난 뒤 계정이 비활성화됐더라도 사용자 정보가 노출되지 않도록 활성 상태를 다시 확인한다.
 */
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
