package com.prizm.document.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prizm.document.dto.response.DocumentApprovalResponse;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.service.DocumentApprovalService;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class DocumentVersionControllerTest {

    @Mock
    DocumentApprovalService approvalService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new DocumentVersionController(approvalService))
                .setControllerAdvice(new DocumentExceptionHandler())
                .build();
    }

    @Test
    void approvesDocumentVersionAndReturnsPendingJob() throws Exception {
        when(approvalService.approve(10L)).thenReturn(new DocumentApprovalResponse(
                10L, DocumentVersionStatus.APPROVED, 20L, ProcessingJobStatus.PENDING));

        mockMvc.perform(post("/api/document-versions/10/approve"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionId").value(10L))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.jobId").value(20L))
                .andExpect(jsonPath("$.jobStatus").value("PENDING"));
    }

    @Test
    void returnsNotFoundForMissingVersion() throws Exception {
        when(approvalService.approve(99L)).thenThrow(new DocumentVersionNotFoundException(99L));

        mockMvc.perform(post("/api/document-versions/99/approve"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DOCUMENT_VERSION_NOT_FOUND"));
    }
}
