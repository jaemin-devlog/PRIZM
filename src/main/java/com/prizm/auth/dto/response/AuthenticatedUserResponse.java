package com.prizm.auth.dto.response;

import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;

public record AuthenticatedUserResponse(Long id, String email, UserRole role) {

    public static AuthenticatedUserResponse from(UserAccount user) {
        return new AuthenticatedUserResponse(user.getId(), user.getEmail(), user.getRole());
    }
}
