package com.prizm.document.controller;

import com.prizm.document.dto.response.DocumentDetailResponse;
import com.prizm.document.dto.response.DocumentSummaryResponse;
import com.prizm.document.dto.response.DocumentUploadResponse;
import com.prizm.document.service.DocumentQueryService;
import com.prizm.document.service.DocumentUploadService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public DocumentController(DocumentUploadService documentUploadService, DocumentQueryService documentQueryService) {
        this.documentUploadService = documentUploadService;
        this.documentQueryService = documentQueryService;
    }

    /** TXT 원본을 QUARANTINED 문서 버전으로 등록한다. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentUploadResponse> upload(
            @RequestParam @NotBlank @Size(max = 200) String title,
            @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(documentUploadService.upload(title, file));
    }

    /** 문서 목록을 조회한다. */
    @GetMapping
    public List<DocumentSummaryResponse> list() {
        return documentQueryService.list();
    }

    /** 문서와 버전 메타데이터를 상세 조회한다. */
    @GetMapping("/{documentId}")
    public DocumentDetailResponse get(@PathVariable Long documentId) {
        return documentQueryService.get(documentId);
    }
}
