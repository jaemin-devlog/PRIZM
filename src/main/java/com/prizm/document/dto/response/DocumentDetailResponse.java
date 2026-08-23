package com.prizm.document.dto.response;

import com.prizm.document.entity.DocumentType;
import com.prizm.documenttag.dto.TagResponse;
import java.time.Instant;
import java.util.List;

public record DocumentDetailResponse(
        Long documentId,
        String title,
        DocumentType documentType,
        boolean ownerConfirmed,
        Long activeVersionId,
        Instant createdAt,
        Instant updatedAt,
        List<TagResponse> tags,
        List<DocumentVersionResponse> versions) {
}
