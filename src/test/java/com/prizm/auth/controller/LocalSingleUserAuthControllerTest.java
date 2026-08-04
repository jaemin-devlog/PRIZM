package com.prizm.auth.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prizm.auth.dto.response.AuthenticatedUserResponse;
import com.prizm.auth.dto.response.LoginResponse;
import com.prizm.auth.service.LocalSingleUserSessionService;
import com.prizm.user.entity.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LocalSingleUserAuthControllerTest {

    private final LocalSingleUserSessionService service = mock(LocalSingleUserSessionService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new LocalSingleUserAuthController(service))
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    @Test
    void returnsTheNormalJwtLoginResponse() throws Exception {
        when(service.startSession()).thenReturn(new LoginResponse(
                "local-token",
                "Bearer",
                3600,
                new AuthenticatedUserResponse(1L, "local@prizm.local", UserRole.USER)));

        mockMvc.perform(post("/api/auth/local-session"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("USER"));
    }
}
