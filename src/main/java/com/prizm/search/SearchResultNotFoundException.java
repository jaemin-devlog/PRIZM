package com.prizm.search;

public class SearchResultNotFoundException extends RuntimeException {

    public SearchResultNotFoundException() {
        super("No document chunks are available for search.");
    }
}
