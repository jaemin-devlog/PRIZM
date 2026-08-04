package com.prizm.auth.controller;

import com.prizm.auth.dto.response.LoginResponse;
import com.prizm.auth.service.LocalSingleUserSessionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(prefix = "prizm.local-demo", name = "enabled", havingValue = "true")
public class LocalSingleUserAuthController {

    private final LocalSingleUserSessionService localSingleUserSessionService;

    public LocalSingleUserAuthController(LocalSingleUserSessionService localSingleUserSessionService) {
        this.localSingleUserSessionService = localSingleUserSessionService;
    }

    @PostMapping("/local-session")
    public LoginResponse startLocalSession() {
        return localSingleUserSessionService.startSession();
    }
}
