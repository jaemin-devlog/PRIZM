package com.prizm.ingestion.exception;

public class DocumentTextExtractionException extends RuntimeException {

    public DocumentTextExtractionException(String message) {
        super(message);
    }

    public DocumentTextExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
