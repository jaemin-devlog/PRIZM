package com.prizm.search.v3.indexing.model;

/** PRZ-026·034·035에서 채택한 Search V3 shadow indexing policy identity다. */
public final class SearchV3IndexingPolicies {

    public static final String STRUCTURE = "structural-block-v1";
    public static final String PASSAGE = "retrieval-passage-b3-v1";
    public static final String CHILD = "evidence-child-v1";
    public static final String PASSAGE_INPUT = "retrieval-passage-text-v1";
    public static final String CHILD_INPUT = "evidence-child-source-text-v1";

    private SearchV3IndexingPolicies() {
    }
}
