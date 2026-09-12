package com.prizm.search.v3.indexing.exception;

/** Search V3 inventory 또는 같은-version 활성화 계약 위반을 나타낸다. */
public class SearchV3InventoryActivationException extends IllegalStateException {

    public SearchV3InventoryActivationException(String message) {
        super(message);
    }
}
