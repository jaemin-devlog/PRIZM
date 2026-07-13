package com.prizm.ingestion.exception;

/** 같은 문서 버전에 동일한 색인 작업을 다시 만들려 할 때 발생한다. */
public class DuplicateProcessingJobException extends RuntimeException {

    public DuplicateProcessingJobException(Long versionId) {
        super("An indexing job already exists for document version %s.".formatted(versionId));
    }

    public DuplicateProcessingJobException(Long versionId, Throwable cause) {
        super("An indexing job already exists for document version %s.".formatted(versionId), cause);
    }
}
