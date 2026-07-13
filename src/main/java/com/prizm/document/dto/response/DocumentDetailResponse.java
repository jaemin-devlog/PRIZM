package com.prizm.document.dto.response;

import java.time.Instant;
import java.util.List;

public record DocumentDetailResponse(
        Long documentId,
        String title,
        Long activeVersionId,
        Instant createdAt,
        Instant updatedAt,
        List<DocumentVersionResponse> versions) {
}
