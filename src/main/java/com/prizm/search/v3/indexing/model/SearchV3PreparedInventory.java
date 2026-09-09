package com.prizm.search.v3.indexing.model;

import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.ChildRow;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.PassageRow;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** DB 저장 전에 완성되고 manifest가 동결된 Passage·Child와 vector inventory다. */
public record SearchV3PreparedInventory(
        List<EmbeddedPassage> passages,
        List<EmbeddedChild> children,
        String logicalManifestSha256) {

    public SearchV3PreparedInventory {
        passages = List.copyOf(passages);
        children = List.copyOf(children);
        if (passages.isEmpty() || children.isEmpty()) {
            throw new IllegalArgumentException("Search V3 inventory must contain Passage and Child artifacts.");
        }
        if (logicalManifestSha256 == null || !logicalManifestSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Search V3 logical manifest must be lowercase SHA-256.");
        }
    }

    public record EmbeddedPassage(PassageRow row, float[] embedding) {
        public EmbeddedPassage {
            Objects.requireNonNull(row, "row");
            embedding = copyVector(embedding);
        }

        @Override
        public float[] embedding() {
            return Arrays.copyOf(embedding, embedding.length);
        }
    }

    public record EmbeddedChild(ChildRow row, float[] embedding) {
        public EmbeddedChild {
            Objects.requireNonNull(row, "row");
            embedding = copyVector(embedding);
        }

        @Override
        public float[] embedding() {
            return Arrays.copyOf(embedding, embedding.length);
        }
    }

    private static float[] copyVector(float[] embedding) {
        Objects.requireNonNull(embedding, "embedding");
        return Arrays.copyOf(embedding, embedding.length);
    }
}
