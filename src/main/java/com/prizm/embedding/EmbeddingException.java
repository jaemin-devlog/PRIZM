package com.prizm.embedding;

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
