package com.prizm.document.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.prizm.auth.security.CurrentUserProvider;
import com.prizm.document.dto.response.DocumentSummaryResponse;
import com.prizm.document.dto.response.DocumentDetailResponse;
import com.prizm.document.dto.response.DocumentUploadResponse;
import com.prizm.document.dto.response.DocumentVersionResponse;
import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
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

    @Mock
    CurrentUserProvider currentUserProvider;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        lenient().when(currentUserProvider.userId()).thenReturn(7L);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new DocumentController(documentUploadService, documentQueryService, currentUserProvider))
                .setControllerAdvice(new DocumentExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void acceptsMultipartTxtUpload() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "guide.txt", "text/plain", "hello".getBytes());
        when(documentUploadService.upload(7L, "Guide", DocumentType.PORTFOLIO, file)).thenReturn(new DocumentUploadResponse(
                1L, 2L, "Guide", "guide.txt", DocumentType.PORTFOLIO,
                DocumentVersionStatus.QUARANTINED, Instant.parse("2026-07-13T00:00:00Z")));

        mockMvc.perform(multipart("/api/documents").file(file).param("title", "Guide").param("documentType", "PORTFOLIO"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(1))
                .andExpect(jsonPath("$.versionId").value(2))
                .andExpect(jsonPath("$.documentType").value("PORTFOLIO"))
                .andExpect(jsonPath("$.status").value("QUARANTINED"));
    }

    @Test
    void acceptsMultipartUploadWithoutDocumentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "guide.txt", "text/plain", "hello".getBytes());
        when(documentUploadService.upload(7L, "Guide", null, file)).thenReturn(new DocumentUploadResponse(
                1L, 2L, "Guide", "guide.txt", DocumentType.OTHER,
                DocumentVersionStatus.QUARANTINED, Instant.parse("2026-07-13T00:00:00Z")));

        mockMvc.perform(multipart("/api/documents").file(file).param("title", "Guide"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentType").value("OTHER"));
    }

    @Test
    void rejectsUnknownDocumentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "guide.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/documents").file(file).param("title", "Guide").param("documentType", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsDocumentList() throws Exception {
        when(documentQueryService.list(7L, null)).thenReturn(List.of(new DocumentSummaryResponse(
                1L, "Guide", DocumentType.PROJECT_REPORT, null, 2L,
                DocumentVersionStatus.QUARANTINED, Instant.parse("2026-07-13T00:00:00Z"))));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentType").value("PROJECT_REPORT"))
                .andExpect(jsonPath("$[0].latestVersionStatus").value("QUARANTINED"));
    }

    @Test
    void filtersDocumentListByDocumentType() throws Exception {
        when(documentQueryService.list(7L, DocumentType.PORTFOLIO)).thenReturn(List.of(new DocumentSummaryResponse(
                1L, "Portfolio", DocumentType.PORTFOLIO, null, 2L,
                DocumentVersionStatus.QUARANTINED, Instant.parse("2026-07-13T00:00:00Z"))));

        mockMvc.perform(get("/api/documents").param("documentType", "PORTFOLIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Portfolio"))
                .andExpect(jsonPath("$[0].documentType").value("PORTFOLIO"));

        verify(documentQueryService).list(7L, DocumentType.PORTFOLIO);
    }

    @Test
    void rejectsUnknownDocumentTypeFilter() throws Exception {
        mockMvc.perform(get("/api/documents").param("documentType", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void doesNotExposeInternalStoredFilePathInDocumentDetail() throws Exception {
        Instant createdAt = Instant.parse("2026-07-13T00:00:00Z");
        when(documentQueryService.get(7L, 1L)).thenReturn(new DocumentDetailResponse(
                1L,
                "Guide",
                DocumentType.PORTFOLIO,
                null,
                createdAt,
                createdAt,
                List.of(new DocumentVersionResponse(
                        2L,
                        1,
                        "guide.txt",
                        DocumentFileType.TXT,
                        DocumentVersionStatus.QUARANTINED,
                        createdAt))));

        mockMvc.perform(get("/api/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("PORTFOLIO"))
                .andExpect(jsonPath("$.versions[0].originalFileName").value("guide.txt"))
                .andExpect(jsonPath("$.versions[0].storedFilePath").doesNotExist());
    }
}
