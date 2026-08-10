package com.prizm.careerkeyword.service;

import com.prizm.careerkeyword.repository.KeywordSourceChunk;
import com.prizm.ingestion.entity.ChunkSourceType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Reassembles searchable chunks while removing the exact configured-style overlap. */
@Component
public class KeywordSourceAssembler {

    private static final int MAX_OVERLAP_SCAN = 2_000;

    List<AssembledKeywordSource> assemble(List<KeywordSourceChunk> chunks) {
        List<KeywordSourceChunk> ordered = chunks.stream()
                .sorted(Comparator.comparing(KeywordSourceChunk::documentVersionId)
                        .thenComparingInt(KeywordSourceChunk::chunkNo))
                .toList();
        Map<SourceKey, List<KeywordSourceChunk>> groups = new LinkedHashMap<>();
        for (KeywordSourceChunk chunk : ordered) {
            int sourceIndex = chunk.sourceType() == ChunkSourceType.PAGE ? chunk.sourceIndex() : 1;
            SourceKey key = new SourceKey(chunk.documentVersionId(), chunk.sourceType(), sourceIndex);
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(chunk);
        }

        return groups.values().stream().map(this::assembleGroup).toList();
    }

    private AssembledKeywordSource assembleGroup(List<KeywordSourceChunk> chunks) {
        KeywordSourceChunk first = chunks.get(0);
        String content = merge(chunks.stream().map(KeywordSourceChunk::content).toList());
        boolean page = first.sourceType() == ChunkSourceType.PAGE;
        return new AssembledKeywordSource(
                first.documentId(),
                first.documentVersionId(),
                first.documentTitle(),
                first.documentType(),
                first.versionNo(),
                first.originalFileName(),
                first.fileType(),
                first.sourceType(),
                page ? first.sourceIndex() : 1,
                page ? first.sourceLabel() : "텍스트 전체",
                content);
    }

    private String merge(List<String> chunks) {
        StringBuilder merged = new StringBuilder(chunks.get(0));
        for (int index = 1; index < chunks.size(); index++) {
            String next = chunks.get(index);
            int overlap = longestOverlap(merged, next);
            if (overlap == 0 && !merged.isEmpty()) {
                merged.append('\n');
            }
            merged.append(next, overlap, next.length());
        }
        return merged.toString();
    }

    private int longestOverlap(StringBuilder left, String right) {
        int maxLength = Math.min(MAX_OVERLAP_SCAN, Math.min(left.length(), right.length()));
        for (int length = maxLength; length > 0; length--) {
            if (left.substring(left.length() - length).contentEquals(right.subSequence(0, length))) {
                return length;
            }
        }
        return 0;
    }

    private record SourceKey(Long documentVersionId, ChunkSourceType sourceType, int sourceIndex) {
    }
}
