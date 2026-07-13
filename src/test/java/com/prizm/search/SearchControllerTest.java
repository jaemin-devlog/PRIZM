package com.prizm.search;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class SearchControllerTest {

    @Mock
    SearchService searchService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new SearchController(searchService))
                .setControllerAdvice(new SearchExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsActualSearchValues() throws Exception {
        when(searchService.search("휴가는 어디에서 신청하나요?"))
                .thenReturn(new SearchResponse("연차 신청은 인사 시스템에서 진행합니다.", 0.25d, 0.75d));

        mockMvc.perform(post("/api/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"휴가는 어디에서 신청하나요?\"}"))
                .andExpect(status().isOk())
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
