package com.prizm.search.service;

import com.prizm.ingestion.entity.ChunkSourceType;

/** 원문에서 발췌한 snippet과 그 구간을 가져온 청크 위치다. */
public record EvidencePresentation(
        String snippet,
        Long evidenceChunkId,
        ChunkSourceType evidenceSourceType,
        int evidenceSourceIndex,
        String evidenceSourceLabel) {
}
