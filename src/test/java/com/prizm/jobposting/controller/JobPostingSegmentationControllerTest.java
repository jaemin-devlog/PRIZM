package com.prizm.jobposting.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prizm.jobposting.dto.response.JobPostingItemResponse;
import com.prizm.jobposting.exception.JobPostingItemLimitExceededException;
import com.prizm.jobposting.service.JobPostingSegmentationService;
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
class JobPostingSegmentationControllerTest {

    @Mock
    JobPostingSegmentationService segmentationService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new JobPostingSegmentationController(segmentationService))
                .setControllerAdvice(new JobPostingSegmentationExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void returnsOrderedItemsWithNullableSections() throws Exception {
        when(segmentationService.segment("기본 역량\n- API 개발\n- 운영 경험"))
                .thenReturn(List.of(
                        new JobPostingItemResponse(1, "기본 역량", "API 개발"),
                        new JobPostingItemResponse(2, null, "운영 경험")));

        mockMvc.perform(post("/api/job-postings/segment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"content":"기본 역량\\n- API 개발\\n- 운영 경험"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].itemId").value(1))
                .andExpect(jsonPath("$[0].section").value("기본 역량"))
                .andExpect(jsonPath("$[0].text").value("API 개발"))
                .andExpect(jsonPath("$[1].itemId").value(2))
                .andExpect(jsonPath("$[1].section").isEmpty())
                .andExpect(jsonPath("$[1].text").value("운영 경험"));

        verify(segmentationService).segment("기본 역량\n- API 개발\n- 운영 경험");
    }

    @Test
    void rejectsBlankContentBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/job-postings/segment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JOB_POSTING_CONTENT"));

        verify(segmentationService, never()).segment(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsUnicodeSeparatorOnlyContentBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/job-postings/segment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"\\u00a0\\u2003\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JOB_POSTING_CONTENT"));

        verify(segmentationService, never()).segment(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsContentOverTwentyThousandCharacters() throws Exception {
        String content = "x".repeat(20_001);

        mockMvc.perform(post("/api/job-postings/segment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"%s\"}".formatted(content)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JOB_POSTING_CONTENT"))
                .andExpect(jsonPath("$.message").value("content must be at most 20000 characters"));

        verify(segmentationService, never()).segment(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsSafeBadRequestWhenSegmentationWouldExceedOneHundredItems() throws Exception {
        when(segmentationService.segment("too many structural items"))
                .thenThrow(new JobPostingItemLimitExceededException(100));

        mockMvc.perform(post("/api/job-postings/segment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"too many structural items\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("JOB_POSTING_ITEM_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.message").value("job posting must produce at most 100 items"));
    }

    @Test
    void rejectsMissingOrUnreadableRequestBodies() throws Exception {
        mockMvc.perform(post("/api/job-postings/segment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JOB_POSTING_CONTENT"));

        mockMvc.perform(post("/api/job-postings/segment")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_JOB_POSTING_CONTENT"));
    }
}
