package com.prizm.auth.controller;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prizm.auth.config.LocalDemoProperties;
import com.prizm.auth.service.AuthService;
import com.prizm.auth.dto.request.LoginRequest;
import com.prizm.auth.dto.response.AuthenticatedUserResponse;
import com.prizm.auth.dto.response.LoginResponse;
import com.prizm.user.entity.UserRole;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(
                        mock(AuthService.class),
                        new LocalDemoProperties(true)))
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    @Test
    void rejectsBlankEmail() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":"password"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN_REQUEST"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());
    }

    @Test
    void exposesOnlyWhetherLocalDemoStartIsAvailableBeforeLogin() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/auth/local-demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void rejectsBlankPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.com","password":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_LOGIN_REQUEST"));
    }

    @Test
    void redactsPasswordAndAccessTokenFromDtoStrings() {
        LoginRequest request = new LoginRequest("user@example.com", "plain-password");
        LoginResponse response = new LoginResponse(
                "complete.jwt.value",
                "Bearer",
                3600,
                new AuthenticatedUserResponse(1L, "user@example.com", UserRole.USER));

        assertThat(request.toString()).doesNotContain("plain-password").contains("[REDACTED]");
        assertThat(response.toString()).doesNotContain("complete.jwt.value").contains("[REDACTED]");
    }
}
