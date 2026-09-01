package com.prizm.search.v3.indexing.structure;

import java.util.Objects;

/** Source-grounded coordinates; character offsets are Unicode code-point [start, end). */
public record SourceProvenance(
        long documentId,
        long documentVersionId,
        String sourceUnitKey,
        String sourcePath,
        Integer pageNo,
        int lineStart,
        int lineEnd,
        int codePointStart,
        int codePointEnd,
        String sourceBlockId,
        String parentAnnotationCandidateId,
        String documentSourceSha256,
        String sourceUnitSha256,
        String exactTextSha256) {

    public SourceProvenance {
        Objects.requireNonNull(sourceUnitKey, "sourceUnitKey");
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(sourceBlockId, "sourceBlockId");
        Objects.requireNonNull(parentAnnotationCandidateId, "parentAnnotationCandidateId");
        Objects.requireNonNull(documentSourceSha256, "documentSourceSha256");
        Objects.requireNonNull(sourceUnitSha256, "sourceUnitSha256");
        Objects.requireNonNull(exactTextSha256, "exactTextSha256");
        if (documentId < 1 || documentVersionId < 1) {
            throw new IllegalArgumentException("document lineage IDs must be positive");
        }
        if (pageNo != null && pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be null or one-based");
        }
        if (lineStart < 1 || lineEnd < lineStart) {
            throw new IllegalArgumentException("line range must be one-based and ordered");
        }
        if (codePointStart < 0 || codePointEnd <= codePointStart) {
            throw new IllegalArgumentException("code-point range must be non-empty and ordered");
        }
    }
}
