package com.prizm.search.v3.indexing.model;

import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.ChildRow;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.PassageRow;
import java.util.List;

/** 구조 생성 직후, embedding·DB insert 전에 동결하는 논리 inventory다. */
public record SearchV3LogicalInventoryPlan(
        List<PassageRow> passages,
        List<ChildRow> children,
        String logicalManifestSha256) {

    public SearchV3LogicalInventoryPlan {
        passages = List.copyOf(passages);
        children = List.copyOf(children);
        if (passages.isEmpty() || children.isEmpty()) {
            throw new IllegalArgumentException("Search V3 logical inventory must not be empty.");
        }
        if (logicalManifestSha256 == null || !logicalManifestSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("Search V3 logical inventory manifest must be lowercase SHA-256.");
        }
    }
}
