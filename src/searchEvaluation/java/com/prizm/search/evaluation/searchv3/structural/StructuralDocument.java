package com.prizm.search.evaluation.searchv3.structural;

import java.util.Objects;

/** Immutable source input for the evaluation-only structural parser. */
public record StructuralDocument(
        String userBundleId,
        String documentId,
        String versionId,
        String sourcePath,
        Integer page,
        String sourceText,
        String sourceSha256) {

    public StructuralDocument {
        Objects.requireNonNull(userBundleId, "userBundleId");
        Objects.requireNonNull(documentId, "documentId");
        Objects.requireNonNull(versionId, "versionId");
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(sourceSha256, "sourceSha256");
        if (page != null && page < 1) {
            throw new IllegalArgumentException("page must be null or one-based");
        }
    }
}
