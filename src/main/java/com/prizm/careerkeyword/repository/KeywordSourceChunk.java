package com.prizm.careerkeyword.repository;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.ingestion.entity.ChunkSourceType;

/** One owner-scoped chunk from the active version of a resume or portfolio. */
public record KeywordSourceChunk(
        Long documentId,
        Long documentVersionId,
        String documentTitle,
        DocumentType documentType,
        int versionNo,
        String originalFileName,
        DocumentFileType fileType,
        int chunkNo,
        ChunkSourceType sourceType,
        int sourceIndex,
        String sourceLabel,
        String content) {
}
