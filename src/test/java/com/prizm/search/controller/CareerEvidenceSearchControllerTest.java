package com.prizm.search.controller;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prizm.auth.security.CurrentUserProvider;
import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.service.SearchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class CareerEvidenceSearchControllerTest {

    @Mock
    SearchService searchService;

    @Mock
    CurrentUserProvider currentUserProvider;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        lenient().when(currentUserProvider.userId()).thenReturn(7L);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new CareerEvidenceSearchController(searchService, currentUserProvider))
                .setControllerAdvice(new SearchExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsAnOrderedArrayOfActualEvidenceFields() throws Exception {
        when(searchService.searchCareerEvidence(7L, "Spring Boot experience"))
                .thenReturn(List.of(new CareerEvidenceSearchResponse(
                        30L,
                        10L,
                        20L,
                        "Career record",
                        2,
                        "Spring Boot and Redis experience",
                        "Spring Boot and Redis experience",
                        ChunkSourceType.TEXT_CHUNK,
                        1,
                        "텍스트 구간 1",
                        30L,
                        ChunkSourceType.TEXT_CHUNK,
                        1,
                        "텍스트 구간 1",
                        0.2d,
                        0.8d)));

        mockMvc.perform(post("/api/career-evidence/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Spring Boot experience\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].chunkId").value(30L))
                .andExpect(jsonPath("$[0].documentId").value(10L))
                .andExpect(jsonPath("$[0].documentVersionId").value(20L))
                .andExpect(jsonPath("$[0].documentTitle").value("Career record"))
                .andExpect(jsonPath("$[0].versionNo").value(2))
                .andExpect(jsonPath("$[0].content").value("Spring Boot and Redis experience"))
                .andExpect(jsonPath("$[0].snippet").value("Spring Boot and Redis experience"))
                .andExpect(jsonPath("$[0].sourceType").value("TEXT_CHUNK"))
                .andExpect(jsonPath("$[0].sourceIndex").value(1))
                .andExpect(jsonPath("$[0].sourceLabel").value("텍스트 구간 1"))
                .andExpect(jsonPath("$[0].evidenceChunkId").value(30L))
                .andExpect(jsonPath("$[0].evidenceSourceType").value("TEXT_CHUNK"))
                .andExpect(jsonPath("$[0].evidenceSourceIndex").value(1))
                .andExpect(jsonPath("$[0].evidenceSourceLabel").value("텍스트 구간 1"))
                .andExpect(jsonPath("$[0].distance").value(0.2d))
                .andExpect(jsonPath("$[0].score").value(0.8d));
    }

    @Test
    void returnsEmptyArrayWhenNoEvidenceIsSearchable() throws Exception {
        when(searchService.searchCareerEvidence(7L, "no evidence")).thenReturn(List.of());

        mockMvc.perform(post("/api/career-evidence/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"no evidence\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsBlankAndOverlongQueries() throws Exception {
        mockMvc.perform(post("/api/career-evidence/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));

        String query = "x".repeat(SearchService.MAX_QUERY_LENGTH + 1);
        mockMvc.perform(post("/api/career-evidence/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"%s\"}".formatted(query)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
    }

    @Test
    void returnsSafeGatewayErrorForInvalidQueryEmbedding() throws Exception {
        when(searchService.searchCareerEvidence(7L, "zero vector"))
                .thenThrow(new EmbeddingException(
                        EmbeddingErrorCode.EMBEDDING_INVALID_RESPONSE,
                        "Embedding service returned an invalid response."));

        mockMvc.perform(post("/api/career-evidence/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"zero vector\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EMBEDDING_INVALID_RESPONSE"))
                .andExpect(jsonPath("$.message").value("Embedding service returned an invalid response."));
    }
}
