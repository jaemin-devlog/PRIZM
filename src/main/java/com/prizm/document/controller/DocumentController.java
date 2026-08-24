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

/** 인증된 사용자의 문서 업로드·조회·메타데이터 수정·삭제 요청을 문서 서비스에 연결한다. */
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

    /** TXT/PDF 원본을 검색 전 격리 상태인 QUARANTINED 버전으로 등록한다. */
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

    /** 기존 문서에 수정된 TXT/PDF 원본을 다음 불변 버전으로 추가한다. */
    @PostMapping(value = "/{documentId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> uploadVersion(
            @PathVariable Long documentId,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentUploadService.uploadVersion(currentUserProvider.userId(), documentId, file));
    }

    @GetMapping
    public List<DocumentSummaryResponse> list(
            @RequestParam(required = false) DocumentType documentType,
            @RequestParam(required = false) @Size(max = 200) String title,
            @RequestParam(required = false) ProcessingJobStatus processingStatus) {
        return documentQueryService.list(currentUserProvider.userId(), documentType, title, processingStatus);
    }

    @GetMapping("/{documentId}")
    public DocumentDetailResponse get(@PathVariable Long documentId) {
        return documentQueryService.get(currentUserProvider.userId(), documentId);
    }

    /** 사용자가 관리하는 메타데이터만 바꾸며 원본과 버전은 수정하지 않는다. */
    @PatchMapping("/{documentId}")
    public DocumentDetailResponse updateMetadata(
            @PathVariable Long documentId,
            @Valid @RequestBody DocumentMetadataUpdateRequest request) {
        Long ownerUserId = currentUserProvider.userId();
        documentManagementService.updateMetadata(ownerUserId, documentId, request.title(), request.documentType());
        return documentQueryService.get(ownerUserId, documentId);
    }

    /** 현재 사용자의 종료된 문서 메타데이터를 지우고 원본 cleanup을 예약한다. */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable Long documentId) {
        documentManagementService.delete(currentUserProvider.userId(), documentId);
        return ResponseEntity.noContent().build();
    }

    /** ACTIVE 버전을 건드리지 않고 현재 사용자의 종료된 과거 버전 하나를 삭제한다. */
    @DeleteMapping("/{documentId}/versions/{versionId}")
    public ResponseEntity<Void> deleteVersion(
            @PathVariable Long documentId,
            @PathVariable Long versionId) {
        documentManagementService.deleteVersion(currentUserProvider.userId(), documentId, versionId);
        return ResponseEntity.noContent().build();
    }
}
