package com.prizm.document.exception;

public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(Long documentId) {
        super("Document %d was not found.".formatted(documentId));
    }
}
