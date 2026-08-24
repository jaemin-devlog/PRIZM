package com.prizm.embedding.exception;

/** 임베딩 제공자 호출이나 벡터 계약 검증에 실패했을 때 오류 코드를 보존하는 예외다. */
public class EmbeddingException extends RuntimeException {

    private final EmbeddingErrorCode code;

    public EmbeddingException(EmbeddingErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public EmbeddingException(EmbeddingErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public EmbeddingErrorCode code() {
        return code;
    }
}
