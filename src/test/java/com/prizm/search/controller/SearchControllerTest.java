package com.prizm.search.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prizm.auth.security.CurrentUserProvider;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.dto.response.SearchResponse;
import com.prizm.search.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** 검색 API의 JSON 응답과 입력 길이 검증 목적을 확인한다. */
@ExtendWith(MockitoExtension.class)
class SearchControllerTest {

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
        mockMvc = MockMvcBuilders.standaloneSetup(new SearchController(searchService, currentUserProvider))
                .setControllerAdvice(new SearchExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsActualSearchValues() throws Exception {
        when(searchService.search(7L, "휴가는 어디에서 신청하나요?"))
                .thenReturn(new SearchResponse(
                        10L,
                        20L,
                        "휴가 안내",
                        1,
                        1,
                        null,
                        ChunkSourceType.TEXT_CHUNK,
                        1,
                        "텍스트 구간 1",
                        "연차 신청은 인사 시스템에서 진행합니다.",
                        0.25d,
                        0.75d));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"휴가는 어디에서 신청하나요?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value(10L))
                .andExpect(jsonPath("$.documentVersionId").value(20L))
                .andExpect(jsonPath("$.documentTitle").value("휴가 안내"))
                .andExpect(jsonPath("$.chunkNo").value(1))
                .andExpect(jsonPath("$.pageNo").doesNotExist())
                .andExpect(jsonPath("$.sourceType").value("TEXT_CHUNK"))
                .andExpect(jsonPath("$.sourceIndex").value(1))
                .andExpect(jsonPath("$.sourceLabel").value("텍스트 구간 1"))
                .andExpect(jsonPath("$.content").value("연차 신청은 인사 시스템에서 진행합니다."))
                .andExpect(jsonPath("$.distance").value(0.25d))
                .andExpect(jsonPath("$.score").value(0.75d));
    }

    @Test
    void rejectsBlankQuery() throws Exception {
        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
    }

    @Test
    void rejectsOverlongQuery() throws Exception {
        String query = "x".repeat(SearchService.MAX_QUERY_LENGTH + 1);

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"%s\"}".formatted(query)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_SEARCH_QUERY"));
    }
}
