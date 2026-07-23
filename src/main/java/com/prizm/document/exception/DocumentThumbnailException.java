package com.prizm.document.exception;

public class DocumentThumbnailException extends RuntimeException {

    private final DocumentThumbnailErrorCode code;

    public DocumentThumbnailException(DocumentThumbnailErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public DocumentThumbnailException(DocumentThumbnailErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public DocumentThumbnailErrorCode code() {
        return code;
    }
}
