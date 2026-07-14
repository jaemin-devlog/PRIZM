package com.prizm.embedding.service;

import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Validates embedding values before they are stored or used in cosine search. */
@Component
public class EmbeddingValidator {

    private final int expectedDimensions;

    public EmbeddingValidator(@Value("${prizm.embedding.dimensions}") int expectedDimensions) {
        this.expectedDimensions = expectedDimensions;
    }

    public void validate(float[] embedding) {
        if (embedding == null || embedding.length == 0) {
            throw new EmbeddingException(
                    EmbeddingErrorCode.EMBEDDING_EMPTY_RESPONSE,
                    "Embedding service returned no values.");
        }
        if (embedding.length != expectedDimensions) {
            throw new EmbeddingException(
                    EmbeddingErrorCode.EMBEDDING_DIMENSION_MISMATCH,
                    "Expected a %d-dimensional embedding but received %d."
                            .formatted(expectedDimensions, embedding.length));
        }

        double squaredNorm = 0.0d;
        for (float value : embedding) {
            if (!Float.isFinite(value)) {
                throw invalidResponse();
            }
            squaredNorm += (double) value * value;
        }
        if (squaredNorm == 0.0d) {
            throw invalidResponse();
        }
    }

    private EmbeddingException invalidResponse() {
        return new EmbeddingException(
                EmbeddingErrorCode.EMBEDDING_INVALID_RESPONSE,
                "Embedding service returned an invalid response.");
    }
}
