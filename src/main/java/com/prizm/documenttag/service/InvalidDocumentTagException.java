package com.prizm.documenttag.service;

public class InvalidDocumentTagException extends RuntimeException {

    private final DocumentTagErrorCode code;

    public InvalidDocumentTagException(DocumentTagErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public DocumentTagErrorCode code() {
        return code;
    }
}
