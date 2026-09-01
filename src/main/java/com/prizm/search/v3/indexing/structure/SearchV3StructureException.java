package com.prizm.search.v3.indexing.structure;

/** Fail-closed structural construction error that a Worker maps to its failure stage. */
public class SearchV3StructureException extends RuntimeException {

    private final Reason reason;

    public SearchV3StructureException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        EMPTY_STRUCTURE,
        ATOMIC_CHILD_EXCEEDS_PASSAGE_BOUND
    }
}
