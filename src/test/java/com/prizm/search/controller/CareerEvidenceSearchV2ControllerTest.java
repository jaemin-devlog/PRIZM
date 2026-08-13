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
import com.prizm.search.dto.response.CareerEvidenceSearchState;
import com.prizm.search.dto.response.CareerEvidenceSearchV2Response;
import com.prizm.search.service.SearchService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class CareerEvidenceSearchV2ControllerTest {

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
                        new CareerEvidenceSearchV2Controller(searchService, currentUserProvider))
                .setControllerAdvice(new SearchExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsEvidenceFoundWithTheExistingEvidenceFields() throws Exception {
        when(searchService.searchCareerEvidenceV2(7L, "Spring Boot experience"))
                .thenReturn(new CareerEvidenceSearchV2Response(
                        CareerEvidenceSearchState.EVIDENCE_FOUND,
                        List.of(new CareerEvidenceSearchResponse(
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
                                0.8d))));

        mockMvc.perform(post("/api/v2/career-evidence/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Spring Boot experience\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("EVIDENCE_FOUND"))
                .andExpect(jsonPath("$.results.length()").value(1))
                .andExpect(jsonPath("$.results[0].chunkId").value(30L))
                .andExpect(jsonPath("$.results[0].content").value("Spring Boot and Redis experience"))
                .andExpect(jsonPath("$.results[0].snippet").value("Spring Boot and Redis experience"))
                .andExpect(jsonPath("$.results[0].sourceType").value("TEXT_CHUNK"))
                .andExpect(jsonPath("$.results[0].evidenceChunkId").value(30L))
                .andExpect(jsonPath("$.results[0].evidenceSourceLabel").value("텍스트 구간 1"))
                .andExpect(jsonPath("$.results[0].score").value(0.8d))
                .andExpect(jsonPath("$.profile").doesNotExist())
                .andExpect(jsonPath("$.rejectionReasons").doesNotExist());
    }

    @Test
    void returnsNoRelevantResultsForGeneralSearchWithAnEmptyResultArray() throws Exception {
        when(searchService.searchCareerEvidenceV2(7L, "unrelated evidence"))
                .thenReturn(new CareerEvidenceSearchV2Response(
                        CareerEvidenceSearchState.NO_RELEVANT_RESULTS, List.of()));

        mockMvc.perform(post("/api/v2/career-evidence/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"unrelated evidence\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NO_RELEVANT_RESULTS"))
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void returnsNoEvidenceForCompletedReleaseSearchWithAnEmptyResultArray() throws Exception {
        when(searchService.searchCareerEvidenceV2(7L, "PRIZM 배포 경험 있나요?"))
                .thenReturn(new CareerEvidenceSearchV2Response(
                        CareerEvidenceSearchState.NO_EVIDENCE, List.of()));

        mockMvc.perform(post("/api/v2/career-evidence/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"PRIZM 배포 경험 있나요?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NO_EVIDENCE"))
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void returnsNoSearchableDocumentsWithAnEmptyResultArray() throws Exception {
        when(searchService.searchCareerEvidenceV2(7L, "empty owner scope"))
                .thenReturn(new CareerEvidenceSearchV2Response(
                        CareerEvidenceSearchState.NO_SEARCHABLE_DOCUMENTS, List.of()));

        mockMvc.perform(post("/api/v2/career-evidence/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"empty owner scope\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("NO_SEARCHABLE_DOCUMENTS"))
                .andExpect(jsonPath("$.results").isEmpty());
    }

    @Test
    void preservesBadRequestAndEmbeddingFailureContracts() throws Exception {
        mockMvc.perform(post("/api/v2/career-evidence/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));

        when(searchService.searchCareerEvidenceV2(7L, "zero vector"))
                .thenThrow(new EmbeddingException(
                        EmbeddingErrorCode.EMBEDDING_INVALID_RESPONSE,
                        "Embedding service returned an invalid response."));

        mockMvc.perform(post("/api/v2/career-evidence/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"zero vector\"}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("EMBEDDING_INVALID_RESPONSE"));
    }

    @Test
    void returnsInternalServerErrorWithoutExposingDatabaseFailureDetails() throws Exception {
        when(searchService.searchCareerEvidenceV2(7L, "database failure"))
                .thenThrow(new DataAccessResourceFailureException("sensitive database details"));

        mockMvc.perform(post("/api/v2/career-evidence/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"database failure\"}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SEARCH_DATABASE_ERROR"))
                .andExpect(jsonPath("$.message").value("Search database request failed."));
    }
}
