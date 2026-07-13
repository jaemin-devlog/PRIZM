package com.prizm.search.repository;

public record VectorSearchResult(
        Long documentId,
        Long documentVersionId,
        String documentTitle,
        int versionNo,
        int chunkNo,
        Integer pageNo,
        String content,
        double distance,
        double score) {
}
