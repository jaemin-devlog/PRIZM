package com.prizm.embedding.exception;

/** Ollama 임베딩 호출 또는 응답 검증에 실패했을 때 사용하는 예외다. */
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
