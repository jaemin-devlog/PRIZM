package com.prizm.search.dto.response;

import com.prizm.ingestion.entity.ChunkSourceType;

/**
 * 선택된 검색 결과와 사용자에게 보여 줄 원문 snippet의 위치를 함께 담는다.
 * snippet은 같은 ACTIVE 문서 버전 안에서 더 직접적인 주변 청크를 가리킬 수 있다.
 */
public record CareerEvidenceSearchResponse(
        Long chunkId,
        Long documentId,
        Long documentVersionId,
        String documentTitle,
        int versionNo,
        String content,
        String snippet,
        ChunkSourceType sourceType,
        int sourceIndex,
        String sourceLabel,
        Long evidenceChunkId,
        ChunkSourceType evidenceSourceType,
        int evidenceSourceIndex,
        String evidenceSourceLabel,
        double distance,
        double score) {
}
