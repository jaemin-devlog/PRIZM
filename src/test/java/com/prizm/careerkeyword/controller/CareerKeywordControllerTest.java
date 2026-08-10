package com.prizm.careerkeyword.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prizm.careerkeyword.dto.response.CareerKeywordEvidenceItemResponse;
import com.prizm.careerkeyword.dto.response.CareerKeywordEvidenceResponse;
import com.prizm.careerkeyword.dto.response.CareerKeywordMapResponse;
import com.prizm.careerkeyword.dto.response.CareerKeywordSummaryResponse;
import com.prizm.careerkeyword.model.CareerKeywordCategory;
import com.prizm.careerkeyword.service.CareerKeywordService;
import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.ingestion.entity.ChunkSourceType;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class CareerKeywordControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    CareerKeywordService service;

    @Test
    void returnsOwnerScopedKeywordMapAndEvidenceToAUser() throws Exception {
        when(service.getKeywordMap(7L)).thenReturn(new CareerKeywordMapResponse(
                1,
                List.of(new CareerKeywordSummaryResponse(
                        "Spring Boot",
                        CareerKeywordCategory.FRAMEWORK,
                        3,
                        1,
                        List.of("Spring Boot")))));
        when(service.getEvidence(7L, "Spring Boot")).thenReturn(new CareerKeywordEvidenceResponse(
                "Spring Boot",
                3,
                List.of(new CareerKeywordEvidenceItemResponse(
                        10L,
                        20L,
                        "Resume",
                        DocumentType.RESUME,
                        1,
                        "resume.txt",
                        DocumentFileType.TXT,
                        ChunkSourceType.TEXT_CHUNK,
                        1,
                        "텍스트 전체",
                        3,
                        "Spring Boot 원문",
                        List.of("Spring Boot")))));

        mockMvc.perform(get("/api/career-keywords").with(userJwt(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentCount").value(1))
                .andExpect(jsonPath("$.keywords[0].keyword").value("Spring Boot"))
                .andExpect(jsonPath("$.keywords[0].category").value("FRAMEWORK"))
                .andExpect(jsonPath("$.keywords[0].frequency").value(3));

        mockMvc.perform(get("/api/career-keywords/evidence")
                        .param("keyword", "Spring Boot")
                        .with(userJwt(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFrequency").value(3))
                .andExpect(jsonPath("$.evidence[0].documentId").value(10L))
                .andExpect(jsonPath("$.evidence[0].sourceLabel").value("텍스트 전체"))
                .andExpect(jsonPath("$.evidence[0].matchedTerms[0]").value("Spring Boot"));
    }

    @Test
    void rejectsUnauthenticatedAndNonUserRequests() throws Exception {
        mockMvc.perform(get("/api/career-keywords"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/career-keywords").with(jwt().jwt(token -> token.subject("7"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/career-keywords").with(jwt()
                        .jwt(token -> token.subject("7"))
                        .authorities(new SimpleGrantedAuthority("ROLE_SYSTEM_ADMIN"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsBlankAndOverlongEvidenceKeywords() throws Exception {
        mockMvc.perform(get("/api/career-keywords/evidence")
                        .param("keyword", " ")
                        .with(userJwt(7L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CAREER_KEYWORD"));
        mockMvc.perform(get("/api/career-keywords/evidence")
                        .param("keyword", "x".repeat(101))
                        .with(userJwt(7L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CAREER_KEYWORD"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor userJwt(long ownerUserId) {
        return jwt()
                .jwt(token -> token.subject(Long.toString(ownerUserId)))
                .authorities(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
