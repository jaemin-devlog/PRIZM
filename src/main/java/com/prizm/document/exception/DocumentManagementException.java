package com.prizm.document.exception;

public class DocumentManagementException extends RuntimeException {

    private final DocumentManagementErrorCode code;

    public DocumentManagementException(DocumentManagementErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public DocumentManagementErrorCode code() {
        return code;
    }
}
