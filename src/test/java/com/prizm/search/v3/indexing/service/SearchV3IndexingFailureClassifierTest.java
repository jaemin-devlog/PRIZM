package com.prizm.search.v3.indexing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import com.prizm.infrastructure.storage.TransientFileStorageException;
import com.prizm.ingestion.exception.DocumentIndexingException;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

class SearchV3IndexingFailureClassifierTest {

    private final SearchV3IndexingFailureClassifier classifier = new SearchV3IndexingFailureClassifier();

    @Test
    void preservesTheDocumentIndexingRetryDecision() {
        assertThat(classifier.isRetryable(new DocumentIndexingException("temporary", true))).isTrue();
        assertThat(classifier.isRetryable(new DocumentIndexingException("permanent", false))).isFalse();
    }

    @Test
    void retriesTransientFileAndDatabaseFailures() {
        assertThat(classifier.isRetryable(
                        new TransientFileStorageException("temporary file failure", new IOException())))
                .isTrue();
        assertThat(classifier.isRetryable(
                        new TransientDataAccessResourceException("temporary database failure")))
                .isTrue();
    }

    @Test
    void retriesEmbeddingProviderFailures() {
        assertThat(classifier.isRetryable(
                        new EmbeddingException(EmbeddingErrorCode.OLLAMA_UNAVAILABLE, "unavailable")))
                .isTrue();
        assertThat(classifier.isRetryable(
                        new EmbeddingException(EmbeddingErrorCode.EMBEDDING_INVALID_RESPONSE, "invalid")))
                .isTrue();
    }

    @Test
    void treatsEmbeddingDimensionMismatchAsPermanent() {
        assertThat(classifier.isRetryable(
                        new EmbeddingException(EmbeddingErrorCode.EMBEDDING_DIMENSION_MISMATCH, "mismatch")))
                .isFalse();
    }

    @Test
    void treatsUnknownRuntimeFailuresAsPermanent() {
        assertThat(classifier.isRetryable(new IllegalStateException("unknown"))).isFalse();
    }
}
