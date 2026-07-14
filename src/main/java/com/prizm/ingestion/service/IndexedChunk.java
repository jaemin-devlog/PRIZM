package com.prizm.ingestion.service;

import com.prizm.ingestion.entity.ChunkSourceType;

public record IndexedChunk(
        int chunkNo,
        ChunkSourceType sourceType,
        int sourceIndex,
        String sourceLabel,
        String content,
        float[] embedding) {
}
