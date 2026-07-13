package com.prizm.document.exception;

/** 요청한 문서 버전이 존재하지 않을 때 발생한다. */
public class DocumentVersionNotFoundException extends RuntimeException {

    public DocumentVersionNotFoundException(Long versionId) {
        super("Document version %s was not found.".formatted(versionId));
    }
}
