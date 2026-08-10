package com.prizm.careerkeyword.dto.response;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.ingestion.entity.ChunkSourceType;
import java.util.List;

public record CareerKeywordEvidenceItemResponse(
        Long documentId,
        Long documentVersionId,
        String documentTitle,
        DocumentType documentType,
        int versionNo,
        String originalFileName,
        DocumentFileType fileType,
        ChunkSourceType sourceType,
        int sourceIndex,
        String sourceLabel,
        int occurrenceCount,
        String excerpt,
        List<String> matchedTerms) {
}
