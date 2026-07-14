package com.prizm.search.dto.response;

import com.prizm.ingestion.entity.ChunkSourceType;

/** A searchable source chunk for a user's career evidence query. */
public record CareerEvidenceSearchResponse(
        Long chunkId,
        Long documentId,
        Long documentVersionId,
        String documentTitle,
        int versionNo,
        String content,
        ChunkSourceType sourceType,
        int sourceIndex,
        String sourceLabel,
        double distance,
        double score) {
}
