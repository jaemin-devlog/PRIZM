package com.prizm.user.controller;

import com.prizm.user.dto.response.UserResponse;
import com.prizm.user.service.UserQueryService;
import java.security.Principal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserQueryService userQueryService;

    public UserController(UserQueryService userQueryService) {
        this.userQueryService = userQueryService;
    }

    @GetMapping("/me")
    public UserResponse me(Principal principal) {
        return userQueryService.getCurrentUser(principal.getName());
    }
}
