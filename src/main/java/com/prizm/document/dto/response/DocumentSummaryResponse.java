package com.prizm.document.dto.response;

import com.prizm.document.entity.DocumentVersionStatus;
import java.time.Instant;

public record DocumentSummaryResponse(
        Long documentId,
        String title,
        Long activeVersionId,
        Long latestVersionId,
        DocumentVersionStatus latestVersionStatus,
        Instant createdAt) {
}
