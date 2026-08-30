package com.prizm.search.evaluation.searchv3.structural;

import java.util.List;
import java.util.Objects;

/** Searchable child with exact display source separated from embedding retrieval text. */
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
        if (sourceText.isBlank() || retrievalText.isBlank() || sourceBlockIds.isEmpty()) {
            throw new IllegalArgumentException("child text and source block IDs must be non-empty");
        }
        if (!sourceBlockIds.contains(provenance.sourceBlockId())) {
            throw new IllegalArgumentException("provenance source block must be tracked");
        }
    }
}
