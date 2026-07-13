package com.prizm.document;

import java.time.Instant;

public record DocumentUploadResponse(
        Long documentId,
        Long versionId,
        String title,
        String originalFileName,
        DocumentVersionStatus status,
        Instant createdAt) {
}
