package com.prizm.ingestion.exception;

/** 색인 처리 실패와 해당 실패의 재시도 가능 여부를 Coordinator에 전달한다. */
public class DocumentIndexingException extends RuntimeException {

    private final boolean retryable;

    public DocumentIndexingException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public DocumentIndexingException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
