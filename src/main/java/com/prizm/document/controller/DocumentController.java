package com.prizm.document.controller;

import com.prizm.auth.security.CurrentUserProvider;
import com.prizm.document.dto.request.DocumentMetadataUpdateRequest;
import com.prizm.document.dto.response.DocumentDetailResponse;
import com.prizm.document.dto.response.DocumentSummaryResponse;
import com.prizm.document.dto.response.DocumentUploadResponse;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.service.DocumentManagementService;
import com.prizm.document.service.DocumentQueryService;
import com.prizm.document.service.DocumentUploadService;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequestMapping("/api/documents")
public class DocumentController {

    private final DocumentUploadService documentUploadService;
    private final DocumentQueryService documentQueryService;
    private final DocumentManagementService documentManagementService;
    private final CurrentUserProvider currentUserProvider;

    public DocumentController(
            DocumentUploadService documentUploadService,
            DocumentQueryService documentQueryService,
            DocumentManagementService documentManagementService,
            CurrentUserProvider currentUserProvider) {
        this.documentUploadService = documentUploadService;
        this.documentQueryService = documentQueryService;
        this.documentManagementService = documentManagementService;
        this.currentUserProvider = currentUserProvider;
    }

    /** TXT 원본을 QUARANTINED 문서 버전으로 등록한다. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> upload(
            @RequestParam @NotBlank @Size(max = 200) String title,
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) List<Long> tagIds,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentUploadService.upload(
                        currentUserProvider.userId(),
                        title,
                        documentType,
                        tagIds == null ? List.of() : tagIds,
                        file));
    }

    /** Adds a revised TXT/PDF source as the next immutable version of an existing owner-scoped document. */
    @PostMapping(value = "/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> uploadVersion(
            @PathVariable Long documentId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentUploadService.uploadVersion(currentUserProvider.userId(), documentId, file));
    }

    /** 문서 목록을 조회한다. */
    @GetMapping
    public List<DocumentSummaryResponse> list(
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) @Size(max = 200) String title,
            @RequestParam(required = false) ProcessingJobStatus processingStatus) {
        return documentQueryService.list(currentUserProvider.userId(), documentType, title, processingStatus);
    }

    /** 문서와 버전 메타데이터를 상세 조회한다. */
    @GetMapping("/{documentId}")
    public DocumentDetailResponse get(@PathVariable Long documentId) {
        return documentQueryService.get(currentUserProvider.userId(), documentId);
    }

    /** Updates only user-managed metadata; original files and versions remain immutable. */
    @PatchMapping("/{documentId}")
    public DocumentDetailResponse updateMetadata(
            @PathVariable Long documentId,
            @Valid @RequestBody DocumentMetadataUpdateRequest request) {
        Long ownerUserId = currentUserProvider.userId();
        documentManagementService.updateMetadata(ownerUserId, documentId, request.title(), request.documentType());
        return documentQueryService.get(ownerUserId, documentId);
    }

    /** Queues orphan-file cleanup and removes terminal document metadata for the current owner only. */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable Long documentId) {
        documentManagementService.delete(currentUserProvider.userId(), documentId);
        return ResponseEntity.noContent().build();
    }

    /** Removes one terminal historical version for the current owner without touching the active version. */
    @DeleteMapping("/{documentId}/versions/{versionId}")
    public ResponseEntity<Void> deleteVersion(
            @PathVariable Long documentId,
            @PathVariable Long versionId) {
        documentManagementService.deleteVersion(currentUserProvider.userId(), documentId, versionId);
        return ResponseEntity.noContent().build();
    }
}
