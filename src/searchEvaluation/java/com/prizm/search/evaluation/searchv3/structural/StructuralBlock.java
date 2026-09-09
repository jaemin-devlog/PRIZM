package com.prizm.search.evaluation.searchv3.structural;

import java.util.Objects;

/** A source block observed by the parser, before searchable child construction. */
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
