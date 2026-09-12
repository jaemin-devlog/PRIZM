package com.prizm.search.v3.indexing.model;

import java.util.Objects;

/** Search V3 generation에 동결된 실제 embedding model identity다. */
public record SearchV3EmbeddingModelContract(
        String modelId,
        String resolvedModelDigest,
        int dimension) {

    public SearchV3EmbeddingModelContract {
        if (modelId == null || modelId.isBlank()) {
            throw new IllegalArgumentException("Search V3 embedding model ID must not be blank.");
        }
        if (resolvedModelDigest == null || !resolvedModelDigest.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Search V3 embedding model digest must be lowercase SHA-256.");
        }
        if (dimension < 1) {
            throw new IllegalArgumentException("Search V3 embedding dimension must be positive.");
        }
        Objects.requireNonNull(modelId, "modelId");
    }
}
