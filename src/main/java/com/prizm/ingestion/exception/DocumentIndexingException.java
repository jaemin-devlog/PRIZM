package com.prizm.ingestion.exception;

/** 파일 내용이나 임베딩을 청크로 변환할 수 없을 때 재시도 가능 여부를 함께 전달한다. */
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
