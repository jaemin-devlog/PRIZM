package com.prizm.user.dto.response;

import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;

public record UserResponse(
        Long id,
        String email,
        UserRole role) {

    public static UserResponse from(UserAccount user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getRole());
    }
}
