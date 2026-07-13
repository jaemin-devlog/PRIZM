package com.prizm.document;

public class DocumentUploadException extends RuntimeException {

    private final DocumentUploadErrorCode code;

    public DocumentUploadException(DocumentUploadErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public DocumentUploadException(DocumentUploadErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public DocumentUploadErrorCode code() {
        return code;
    }
}
