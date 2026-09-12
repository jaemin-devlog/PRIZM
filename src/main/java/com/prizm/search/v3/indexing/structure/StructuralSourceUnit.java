package com.prizm.search.v3.indexing.structure;

import java.util.Objects;

/** One independently parsed TXT document or PDF page within an extracted document version. */
public record StructuralSourceUnit(
        long documentId,
        long documentVersionId,
        String sourceUnitKey,
        int sourceUnitOrder,
        String sourcePath,
        Integer pageNo,
        String sourceText,
        String sourceUnitSha256,
        String documentSourceSha256) {

    public StructuralSourceUnit {
        Objects.requireNonNull(sourceUnitKey, "sourceUnitKey");
        Objects.requireNonNull(sourcePath, "sourcePath");
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(sourceUnitSha256, "sourceUnitSha256");
        Objects.requireNonNull(documentSourceSha256, "documentSourceSha256");
        if (documentId < 1 || documentVersionId < 1) {
            throw new IllegalArgumentException("document lineage IDs must be positive");
        }
        if (sourceUnitKey.isBlank() || sourcePath.isBlank()) {
            throw new IllegalArgumentException("source unit key and path must be nonblank");
        }
        if (sourceUnitKey.length() > 180) {
            throw new IllegalArgumentException("sourceUnitKey is too long for derived artifact keys");
        }
        if (sourcePath.length() > 500) {
            throw new IllegalArgumentException("sourcePath exceeds the Search V3 storage limit");
        }
        if (sourceUnitOrder < 0) {
            throw new IllegalArgumentException("sourceUnitOrder must be non-negative");
        }
        if (pageNo != null && pageNo < 1) {
            throw new IllegalArgumentException("pageNo must be null or one-based");
        }
        requireSha256(sourceUnitSha256, "sourceUnitSha256");
        requireSha256(documentSourceSha256, "documentSourceSha256");
        if (!SearchV3StructureHashes.sha256Utf8(sourceText).equals(sourceUnitSha256)) {
            throw new IllegalArgumentException("sourceUnitSha256 does not match sourceText");
        }
    }

    private static void requireSha256(String value, String label) {
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be lowercase SHA-256");
        }
    }
}
