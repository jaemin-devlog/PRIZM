package com.prizm.document.dto.response;

import com.prizm.document.entity.DocumentVersionStatus;
import java.time.Instant;

public record DocumentUploadResponse(
        Long documentId,
        Long versionId,
        String title,
        String originalFileName,
        DocumentVersionStatus status,
        Instant createdAt) {
}
