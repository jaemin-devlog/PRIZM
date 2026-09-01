package com.prizm.search.v3.indexing.structure;

import java.util.List;
import java.util.Objects;

/** Atomic source evidence with display text separated from its retrieval context. */
public record EvidenceChild(
        String childId,
        StructuralBlockType sourceBlockType,
        String sourceText,
        String retrievalText,
        List<String> sourceBlockIds,
        List<String> contextSourceBlockIds,
        SourceProvenance provenance) {

    public EvidenceChild {
        Objects.requireNonNull(childId, "childId");
        Objects.requireNonNull(sourceBlockType, "sourceBlockType");
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(retrievalText, "retrievalText");
        Objects.requireNonNull(sourceBlockIds, "sourceBlockIds");
        Objects.requireNonNull(contextSourceBlockIds, "contextSourceBlockIds");
        Objects.requireNonNull(provenance, "provenance");
        sourceBlockIds = List.copyOf(sourceBlockIds);
        contextSourceBlockIds = List.copyOf(contextSourceBlockIds);
        if (childId.isBlank() || childId.length() > 200) {
            throw new IllegalArgumentException("childId must fit the Search V3 storage key");
        }
        if (sourceText.isBlank() || retrievalText.isBlank() || sourceBlockIds.isEmpty()) {
            throw new IllegalArgumentException("child text and source block IDs must be non-empty");
        }
        if (!sourceBlockIds.contains(provenance.sourceBlockId())) {
            throw new IllegalArgumentException("provenance source block must be tracked");
        }
        if (!SearchV3StructureHashes.sha256Utf8(sourceText).equals(provenance.exactTextSha256())) {
            throw new IllegalArgumentException("EvidenceChild sourceText must match exact provenance");
        }
    }

    public String sourceTextSha256() {
        return provenance.exactTextSha256();
    }
}
