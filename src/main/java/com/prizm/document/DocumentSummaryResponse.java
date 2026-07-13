package com.prizm.document;

import java.time.Instant;

public record DocumentSummaryResponse(
        Long documentId,
        String title,
        Long activeVersionId,
        Long latestVersionId,
        DocumentVersionStatus latestVersionStatus,
        Instant createdAt) {
}
