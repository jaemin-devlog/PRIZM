package com.prizm.search.repository;

import com.prizm.ingestion.entity.ChunkSourceType;

/** 이미 선택된 결과의 원문 위치를 보완할 수 있는 같은 ACTIVE 버전의 청크다. */
public record EvidenceChunk(
        Long chunkId,
        int chunkNo,
        ChunkSourceType sourceType,
        int sourceIndex,
        String sourceLabel,
        String content) {
}
