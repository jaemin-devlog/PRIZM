package com.prizm.auth.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prizm.auth.service.AuthService;
import com.prizm.auth.dto.request.LoginRequest;
import com.prizm.auth.dto.response.AuthenticatedUserResponse;
import com.prizm.auth.dto.response.LoginResponse;
import com.prizm.user.entity.UserRole;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthControllerTest {

    private MockMvc mockMvc;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new AuthExceptionHandler())
                .build();
    }

    @Test
    void signsUpWithoutIssuingAJwt() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new-user@example.com","password":"password","role":"SYSTEM_ADMIN"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(""));

        verify(authService).signup(new LoginRequest("new-user@example.com", "password"));
    }

    @Test
    void returnsConflictForADuplicateSignup() throws Exception {
        LoginRequest request = new LoginRequest("existing@example.com", "password");
        doThrow(new DataIntegrityViolationException("Email is already registered"))
                .when(authService).signup(request);

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"existing@example.com","password":"password"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void reportsSignupValidationWithoutChangingTheLoginErrorContract() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"","password":"password"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SIGNUP_REQUEST"));
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
