package com.prizm.careerkeyword.service;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.ingestion.entity.ChunkSourceType;

record AssembledKeywordSource(
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
        String content) {
}
