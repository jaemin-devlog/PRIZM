package com.prizm.document.controller;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import com.prizm.document.exception.DocumentManagementErrorCode;
import com.prizm.document.exception.DocumentManagementException;
import com.prizm.document.service.DocumentQueryService;
import com.prizm.document.service.DocumentManagementService;
import com.prizm.document.service.DocumentUploadService;
import com.prizm.ingestion.entity.ProcessingJobStatus;
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
    DocumentManagementService documentManagementService;

    @Mock
    CurrentUserProvider currentUserProvider;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        lenient().when(currentUserProvider.userId()).thenReturn(7L);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new DocumentController(
                                documentUploadService,
                                documentQueryService,
                                documentManagementService,
                                currentUserProvider))
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
    void addsMultipartFileAsANewVersionOfTheOwnersDocument() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "guide-v2.pdf", "application/pdf", "pdf".getBytes());
        when(documentUploadService.uploadVersion(7L, 1L, file)).thenReturn(new DocumentUploadResponse(
                1L, 3L, "Guide", "guide-v2.pdf", DocumentType.PORTFOLIO,
                DocumentVersionStatus.QUARANTINED, Instant.parse("2026-07-14T00:00:00Z")));

        mockMvc.perform(multipart("/api/documents/1/versions").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value(1))
                .andExpect(jsonPath("$.versionId").value(3))
                .andExpect(jsonPath("$.originalFileName").value("guide-v2.pdf"));

        verify(documentUploadService).uploadVersion(7L, 1L, file);
    }

    @Test
    void rejectsUnknownDocumentType() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "guide.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/api/documents").file(file).param("title", "Guide").param("documentType", "UNKNOWN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsDocumentList() throws Exception {
        when(documentQueryService.list(7L, null, null, null)).thenReturn(List.of(new DocumentSummaryResponse(
                1L, "Guide", DocumentType.PROJECT_REPORT, null, 2L,
                DocumentVersionStatus.QUARANTINED, "guide.pdf", DocumentFileType.PDF,
                ProcessingJobStatus.PENDING, null, null, null, null, null,
                0, 3, null, null, 1,
                Instant.parse("2026-07-13T00:00:00Z"), Instant.parse("2026-07-13T00:00:00Z"))));

        mockMvc.perform(get("/api/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentType").value("PROJECT_REPORT"))
                .andExpect(jsonPath("$[0].latestVersionStatus").value("QUARANTINED"))
                .andExpect(jsonPath("$[0].latestOriginalFileName").value("guide.pdf"))
                .andExpect(jsonPath("$[0].latestFileType").value("PDF"));
    }

    @Test
    void filtersDocumentListByDocumentType() throws Exception {
        when(documentQueryService.list(7L, DocumentType.PORTFOLIO, null, null)).thenReturn(List.of(new DocumentSummaryResponse(
                1L, "Portfolio", DocumentType.PORTFOLIO, null, 2L,
                DocumentVersionStatus.QUARANTINED, "portfolio.txt", DocumentFileType.TXT,
                ProcessingJobStatus.PENDING, null, null, null, null, null,
                0, 3, null, null, 1,
                Instant.parse("2026-07-13T00:00:00Z"), Instant.parse("2026-07-13T00:00:00Z"))));

        mockMvc.perform(get("/api/documents").param("documentType", "PORTFOLIO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Portfolio"))
                .andExpect(jsonPath("$[0].documentType").value("PORTFOLIO"));

        verify(documentQueryService).list(7L, DocumentType.PORTFOLIO, null, null);
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
                true,
                null,
                createdAt,
                createdAt,
                List.of(new DocumentVersionResponse(
                        2L,
                        1,
                        "guide.txt",
                        DocumentFileType.TXT,
                        DocumentVersionStatus.QUARANTINED,
                        ProcessingJobStatus.PENDING,
                        null,
                        null,
                        null,
                        null,
                        null,
                        false,
                        0,
                        3,
                        null,
                        createdAt))));

        mockMvc.perform(get("/api/documents/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentType").value("PORTFOLIO"))
                .andExpect(jsonPath("$.versions[0].originalFileName").value("guide.txt"))
                .andExpect(jsonPath("$.versions[0].maxRetries").value(3))
                .andExpect(jsonPath("$.versions[0].storedFilePath").doesNotExist());
    }

    @Test
    void updatesMetadataForTheCurrentUserAndReturnsRefreshedDetail() throws Exception {
        Instant createdAt = Instant.parse("2026-07-13T00:00:00Z");
        doNothing().when(documentManagementService).updateMetadata(7L, 1L, "Updated", DocumentType.RESUME);
        when(documentQueryService.get(7L, 1L)).thenReturn(new DocumentDetailResponse(
                1L, "Updated", DocumentType.RESUME, true, null, createdAt, createdAt, List.of()));

        mockMvc.perform(patch("/api/documents/1")
                        .contentType("application/json")
                        .content("{\"title\":\"Updated\",\"documentType\":\"RESUME\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"));

        verify(documentManagementService).updateMetadata(7L, 1L, "Updated", DocumentType.RESUME);
    }

    @Test
    void rejectsBlankMetadataTitle() throws Exception {
        mockMvc.perform(patch("/api/documents/1")
                        .contentType("application/json")
                        .content("{\"title\":\" \",\"documentType\":\"RESUME\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deletesForTheCurrentUserIdempotently() throws Exception {
        mockMvc.perform(delete("/api/documents/1"))
                .andExpect(status().isNoContent());

        verify(documentManagementService).delete(7L, 1L);
    }

    @Test
    void deletesOneHistoricalVersionForTheCurrentUser() throws Exception {
        mockMvc.perform(delete("/api/documents/1/versions/2"))
                .andExpect(status().isNoContent());

        verify(documentManagementService).deleteVersion(7L, 1L, 2L);
    }

    @Test
    void reportsAConflictWhenDeletingTheCurrentVersion() throws Exception {
        doThrow(new DocumentManagementException(
                DocumentManagementErrorCode.DOCUMENT_VERSION_ACTIVE,
                "The active document version cannot be deleted."))
                .when(documentManagementService).deleteVersion(7L, 1L, 2L);

        mockMvc.perform(delete("/api/documents/1/versions/2"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOCUMENT_VERSION_ACTIVE"));
    }
}
