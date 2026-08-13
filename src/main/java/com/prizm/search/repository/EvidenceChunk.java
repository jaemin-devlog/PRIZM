package com.prizm.search.repository;

import com.prizm.ingestion.entity.ChunkSourceType;

/** An original chunk that may supply presentation evidence for an already selected result. */
public record EvidenceChunk(
        Long chunkId,
        int chunkNo,
        ChunkSourceType sourceType,
        int sourceIndex,
        String sourceLabel,
        String content) {
}
