package com.prizm.search.v3.indexing.structure;

/** Canonical text operations shared by EvidenceChild and RetrievalPassage construction. */
final class SearchV3RetrievalTextPolicy {

    private SearchV3RetrievalTextPolicy() {
    }

    static String canonicalizeLineEndings(String value) {
        return value.replace("\r\n", "\n").replace('\r', '\n');
    }

    static int codePointLength(String value) {
        return value.codePointCount(0, value.length());
    }
}
