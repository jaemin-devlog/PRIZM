package com.prizm.document.exception;

public class DocumentVersionNotFoundException extends RuntimeException {

    public DocumentVersionNotFoundException(Long versionId) {
        super("Document version %s was not found.".formatted(versionId));
    }
}
