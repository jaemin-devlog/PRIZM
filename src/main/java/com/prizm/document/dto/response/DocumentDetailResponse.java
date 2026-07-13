package com.prizm.document.dto.response;

import com.prizm.document.entity.DocumentType;
import java.time.Instant;
import java.util.List;

public record DocumentDetailResponse(
        Long documentId,
        String title,
        DocumentType documentType,
        Long activeVersionId,
        Instant createdAt,
        Instant updatedAt,
        List<DocumentVersionResponse> versions) {
}
