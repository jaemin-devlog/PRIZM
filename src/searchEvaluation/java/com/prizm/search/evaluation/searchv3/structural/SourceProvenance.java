package com.prizm.search.evaluation.searchv3.structural;

import java.util.Objects;

/** Source-grounded coordinates. Character offsets are Unicode code-point [start, end). */
public record SourceProvenance(
        String documentId,
        String versionId,
        String sourcePath,
        Integer page,
        int lineStart,
        int lineEnd,
        int codePointStart,
        int codePointEnd,
        String sourceBlockId,
        String parentAnnotationCandidateId,
        String documentSourceSha256,
        String exactTextSha256) {

    public SourceProvenance {
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(sourceBlockId, "sourceBlockId");
        Objects.requireNonNull(parentAnnotationCandidateId, "parentAnnotationCandidateId");
        Objects.requireNonNull(documentSourceSha256, "documentSourceSha256");
        Objects.requireNonNull(exactTextSha256, "exactTextSha256");
        if (lineStart < 1 || lineEnd < lineStart) {
            throw new IllegalArgumentException("line range must be one-based and ordered");
        }
        if (codePointStart < 0 || codePointEnd <= codePointStart) {
            throw new IllegalArgumentException("code-point range must be non-empty and ordered");
        }
    }
}
