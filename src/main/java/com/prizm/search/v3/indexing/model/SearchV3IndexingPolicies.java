package com.prizm.search.v3.indexing.model;

/** PRZ-026·034·035에서 채택한 Search V3 shadow indexing policy identity다. */
public final class SearchV3IndexingPolicies {

    public static final String STRUCTURE = "structural-block-v1";
    public static final String PASSAGE = "retrieval-passage-b3-v1";
    public static final String CHILD = "evidence-child-v2";
    public static final String PASSAGE_INPUT = "retrieval-passage-text-v2";
    public static final String CHILD_INPUT = "evidence-child-source-text-v1";

    public static final int RETRIEVAL_PASSAGE_MIN_TARGET_CODE_POINTS = 120;
    public static final int RETRIEVAL_PASSAGE_TARGET_MAX_CODE_POINTS = 320;
    public static final int RETRIEVAL_PASSAGE_ABSOLUTE_MAX_CODE_POINTS = 480;

    private SearchV3IndexingPolicies() {
    }
}
