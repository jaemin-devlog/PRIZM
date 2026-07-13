package com.prizm.document.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prizm.document.dto.response.DocumentSummaryResponse;
import com.prizm.document.dto.response.DocumentUploadResponse;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.service.DocumentQueryService;
import com.prizm.document.service.DocumentUploadService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/** 문서 업로드 multipart API와 목록 응답의 계약을 검증한다. */
@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock
    DocumentUploadService documentUploadService;

    @Mock
    DocumentQueryService documentQueryService;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new DocumentController(documentUploadService, documentQueryService))
                .setControllerAdvice(new DocumentExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void acceptsMultipartTxtUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "guide.txt", "text/plain", "hello".getBytes());
        when(documentUploadService.upload("Guide", file)).thenReturn(new DocumentUploadResponse(
                1L, 2L, "Guide", "guide.txt", DocumentVersionStatus.QUARANTINED, Instant.parse("2026-07-13T00:00:00Z")));

        mockMvc.perform(multipart("/api/documents").file(file).param("title", "Guide"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(1))
                .andExpect(jsonPath("$.versionId").value(2))
                .andExpect(jsonPath("$.status").value("QUARANTINED"));
    }

    @Test
    void returnsDocumentList() throws Exception {
        when(documentQueryService.list()).thenReturn(List.of(new DocumentSummaryResponse(
                1L, "Guide", null, 2L, DocumentVersionStatus.QUARANTINED, Instant.parse("2026-07-13T00:00:00Z"))));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].latestVersionStatus").value("QUARANTINED"));
    }
}
