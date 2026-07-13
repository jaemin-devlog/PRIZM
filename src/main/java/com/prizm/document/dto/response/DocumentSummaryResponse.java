package com.prizm.document.dto.response;

import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.entity.DocumentType;
import java.time.Instant;

public record DocumentSummaryResponse(
        Long documentId,
        String title,
        DocumentType documentType,
        Long activeVersionId,
        Long latestVersionId,
        DocumentVersionStatus latestVersionStatus,
        Instant createdAt) {
}
