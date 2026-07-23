package com.prizm.document.controller;

import com.prizm.auth.security.CurrentUserProvider;
import com.prizm.document.dto.response.DocumentOriginalResponse;
import com.prizm.document.dto.response.DocumentThumbnailResponse;
import com.prizm.document.service.DocumentThumbnailService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public class DocumentThumbnailController {

    private static final String RENDER_VARIANT = "thumbnail-v1-480x640";

    private final DocumentThumbnailService documentThumbnailService;
    private final CurrentUserProvider currentUserProvider;

    public DocumentThumbnailController(
            DocumentThumbnailService documentThumbnailService,
            CurrentUserProvider currentUserProvider) {
        this.documentThumbnailService = documentThumbnailService;
        this.currentUserProvider = currentUserProvider;
    }

    /** Returns a bounded PNG preview without exposing the stored original PDF or its path. */
    @GetMapping(value = "/{documentId}/versions/{versionId}/thumbnail", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> get(
            @PathVariable Long documentId,
            @PathVariable Long versionId) {
        DocumentThumbnailResponse thumbnail = documentThumbnailService.get(
                currentUserProvider.userId(), documentId, versionId);
        String etag = "W/\"" + RENDER_VARIANT + "-" + thumbnail.contentHash() + "\"";
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600, must-revalidate, no-transform")
                .header(HttpHeaders.ETAG, etag)
                .contentType(MediaType.IMAGE_PNG)
                .body(thumbnail.pngBytes());
    }

    /** Streams an owner-scoped PDF inline without exposing its storage key or local path. */
    @GetMapping(value = "/{documentId}/versions/{versionId}/original", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> getOriginal(
            @PathVariable Long documentId,
            @PathVariable Long versionId) {
        DocumentOriginalResponse original = documentThumbnailService.getOriginal(
                currentUserProvider.userId(), documentId, versionId);
        ContentDisposition disposition = ContentDisposition.inline()
                .filename(sanitizeHeaderFileName(original.originalFileName()), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store, no-cache, must-revalidate")
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(original.pdfBytes().length)
                .body(original.pdfBytes());
    }

    private String sanitizeHeaderFileName(String originalFileName) {
        StringBuilder sanitized = new StringBuilder(originalFileName.length());
        originalFileName.codePoints().forEach(codePoint -> {
            if (codePoint < 0x20 || codePoint == 0x7f) {
                sanitized.append('_');
            }
            else {
                sanitized.appendCodePoint(codePoint);
            }
        });
        return sanitized.toString().isBlank() ? "document.pdf" : sanitized.toString();
    }
}
