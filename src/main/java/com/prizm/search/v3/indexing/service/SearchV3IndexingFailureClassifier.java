package com.prizm.search.v3.indexing.service;

import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import com.prizm.infrastructure.storage.TransientFileStorageException;
import com.prizm.ingestion.exception.DocumentIndexingException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Component;

/** Search V3 단계별 오류를 기존 Worker와 같은 보수적 retry 경계로 분류한다. */
@Component
public class SearchV3IndexingFailureClassifier {

    public boolean isRetryable(RuntimeException exception) {
        if (exception instanceof DocumentIndexingException indexingException) {
            return indexingException.isRetryable();
        }
        if (exception instanceof TransientFileStorageException
                || exception instanceof TransientDataAccessException) {
            return true;
        }
        if (exception instanceof EmbeddingException embeddingException) {
            return embeddingException.code() != EmbeddingErrorCode.EMBEDDING_DIMENSION_MISMATCH;
        }
        return false;
    }
}
