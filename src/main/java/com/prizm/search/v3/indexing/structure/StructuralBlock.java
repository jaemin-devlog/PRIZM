package com.prizm.search.v3.indexing.structure;

import java.util.Objects;

/** A source block observed before searchable EvidenceChild construction. */
public record StructuralBlock(
        String blockId,
        StructuralBlockType type,
        String sourceText,
        SourceProvenance provenance) {

    public StructuralBlock {
        Objects.requireNonNull(blockId, "blockId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(sourceText, "sourceText");
        Objects.requireNonNull(provenance, "provenance");
        if (sourceText.isBlank()) {
            throw new IllegalArgumentException("sourceText must be nonblank");
        }
        if (!blockId.equals(provenance.sourceBlockId())) {
            throw new IllegalArgumentException("blockId and provenance sourceBlockId must match");
        }
    }
}
