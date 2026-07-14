package com.prizm.ingestion.service;

import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import com.prizm.ingestion.exception.DocumentIndexingException;
import com.prizm.ingestion.exception.DocumentTextExtractionException;
import org.springframework.stereotype.Component;

/** 일시적인 Ollama 문제와 파일·차원처럼 반복해도 해결되지 않는 실패를 구분한다. */
@Component
public class IndexingFailureClassifier {

    public boolean isRetryable(RuntimeException exception) {
        if (exception instanceof DocumentIndexingException indexingException) {
            return indexingException.isRetryable();
        }
        if (exception instanceof DocumentTextExtractionException) {
            return false;
        }
        if (exception instanceof EmbeddingException embeddingException) {
            return embeddingException.code() != EmbeddingErrorCode.EMBEDDING_DIMENSION_MISMATCH;
        }
        return false;
    }
}
