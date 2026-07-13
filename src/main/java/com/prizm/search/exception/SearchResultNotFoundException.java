package com.prizm.search.exception;

public class SearchResultNotFoundException extends RuntimeException {

    public SearchResultNotFoundException() {
        super("No document chunks are available for search.");
    }
}
