package com.prizm.search.v3.indexing.exception;

/** 현재 Production version 또는 잠금 경계 때문에 READY generation 활성화를 나중으로 미룬다. */
public class SearchV3ActivationDeferredException extends SearchV3InventoryActivationException {

    private final Reason reason;

    public SearchV3ActivationDeferredException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }

    public enum Reason {
        NOT_CURRENT_VERSION,
        DOCUMENT_LOCKED
    }
}
