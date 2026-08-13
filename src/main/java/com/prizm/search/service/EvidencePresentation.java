package com.prizm.search.service;

import com.prizm.ingestion.entity.ChunkSourceType;

/** Extractive snippet plus the exact chunk location from which it was selected. */
public record EvidencePresentation(
        String snippet,
        Long evidenceChunkId,
        ChunkSourceType evidenceSourceType,
        int evidenceSourceIndex,
        String evidenceSourceLabel) {
}
