package com.prizm.document.exception;

/** 요청한 문서가 DB에 없을 때 발생하는 조회 예외다. */
public class DocumentNotFoundException extends RuntimeException {

    public DocumentNotFoundException(Long documentId) {
        super("Document %d was not found.".formatted(documentId));
    }
}
