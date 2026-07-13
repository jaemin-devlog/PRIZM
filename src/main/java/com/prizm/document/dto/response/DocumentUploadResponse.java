package com.prizm.document.dto.response;

import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.entity.DocumentType;
import java.time.Instant;

public record DocumentUploadResponse(
        Long documentId,
        Long versionId,
        String title,
        String originalFileName,
        DocumentType documentType,
        DocumentVersionStatus status,
        Instant createdAt) {
}
