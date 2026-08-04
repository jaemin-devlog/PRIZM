package com.prizm.auth.controller;

import com.prizm.auth.config.LocalDemoProperties;
import com.prizm.auth.dto.request.LoginRequest;
import com.prizm.auth.dto.response.LocalDemoAvailabilityResponse;
import com.prizm.auth.dto.response.LoginResponse;
import com.prizm.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final LocalDemoProperties localDemoProperties;

    public AuthController(AuthService authService, LocalDemoProperties localDemoProperties) {
        this.authService = authService;
        this.localDemoProperties = localDemoProperties;
    }

    @GetMapping("/local-demo")
    public LocalDemoAvailabilityResponse localDemo() {
        return new LocalDemoAvailabilityResponse(localDemoProperties.enabled());
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }
}
