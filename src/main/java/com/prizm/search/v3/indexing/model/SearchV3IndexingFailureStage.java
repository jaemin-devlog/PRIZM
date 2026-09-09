package com.prizm.search.v3.indexing.model;

/** V18 Search V3 generation과 job에 저장하는 terminal failure 단계다. */
public enum SearchV3IndexingFailureStage {
    PASSAGE_GENERATION,
    PASSAGE_EMBEDDING,
    CHILD_GENERATION,
    CHILD_EMBEDDING,
    STORAGE,
    ACTIVATION
}
