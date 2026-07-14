package com.prizm.search.repository;

import com.prizm.ingestion.entity.ChunkSourceType;

public record VectorSearchResult(
        Long documentId,
        Long documentVersionId,
        String documentTitle,
        int versionNo,
        int chunkNo,
        Integer pageNo,
        ChunkSourceType sourceType,
        int sourceIndex,
        String sourceLabel,
        String content,
        double distance,
        double score) {
}
