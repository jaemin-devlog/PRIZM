package com.prizm.search.v3.query.model;

import java.util.Objects;

/** 원문과 source provenance를 보존한 저장형 EvidenceChild다. */
public record SearchV3EvidenceChildCandidate(
        long childId,
        long passageId,
        long generationId,
        long ownerUserId,
        long documentId,
        long documentVersionId,
        String childKey,
        int childOrder,
        int passageChildOrder,
        String sourceBlockType,
        String sourceText,
        String sourceTextSha256,
        String sourcePath,
        Integer pageNo,
        int lineStart,
        int lineEnd,
        int codePointStart,
        int codePointEnd,
        String sourceBlockId,
        String parentAnnotationCandidateId,
        String documentSourceSha256) {

    public SearchV3EvidenceChildCandidate {
        Objects.requireNonNull(childKey, "childKey");
        Objects.requireNonNull(sourceBlockType, "sourceBlockType");
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(sourceTextSha256, "sourceTextSha256");
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(sourceBlockId, "sourceBlockId");
        Objects.requireNonNull(parentAnnotationCandidateId, "parentAnnotationCandidateId");
        Objects.requireNonNull(documentSourceSha256, "documentSourceSha256");
        if (childId < 1 || passageId < 1 || generationId < 1 || ownerUserId < 1
                || documentId < 1 || documentVersionId < 1 || childOrder < 0
                || passageChildOrder < 0 || (pageNo != null && pageNo < 1)
                || lineStart < 1 || lineEnd < lineStart
                || codePointStart < 0 || codePointEnd <= codePointStart) {
            throw new IllegalArgumentException("Search V3 EvidenceChild candidate is invalid.");
        }
    }
}
