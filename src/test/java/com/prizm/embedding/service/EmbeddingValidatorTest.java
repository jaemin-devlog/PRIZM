package com.prizm.embedding.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import org.junit.jupiter.api.Test;

class EmbeddingValidatorTest {

    private static final int DIMENSIONS = 1024;

    private final EmbeddingValidator validator = new EmbeddingValidator(DIMENSIONS);

    @Test
    void acceptsExpectedDimensionNonZeroEmbedding() {
        assertThatCode(() -> validator.validate(nonZeroEmbedding(1.0f))).doesNotThrowAnyException();
    }

    @Test
    void rejectsZeroNormEmbedding() {
        assertInvalidResponse(new float[DIMENSIONS]);
    }

    @Test
    void rejectsNanValue() {
        assertInvalidResponse(nonZeroEmbedding(Float.NaN));
    }

    @Test
    void rejectsPositiveInfinity() {
        assertInvalidResponse(nonZeroEmbedding(Float.POSITIVE_INFINITY));
    }

    @Test
    void rejectsNegativeInfinity() {
        assertInvalidResponse(nonZeroEmbedding(Float.NEGATIVE_INFINITY));
    }

    @Test
    void rejectsUnexpectedDimension() {
        assertThatThrownBy(() -> validator.validate(new float[DIMENSIONS - 1]))
                .isInstanceOf(EmbeddingException.class)
                .extracting(exception -> ((EmbeddingException) exception).code())
                .isEqualTo(EmbeddingErrorCode.EMBEDDING_DIMENSION_MISMATCH);
    }

    @Test
    void acceptsSmallestPositiveNonZeroValue() {
        assertThatCode(() -> validator.validate(nonZeroEmbedding(Float.MIN_VALUE)))
                .doesNotThrowAnyException();
    }

    private void assertInvalidResponse(float[] embedding) {
        assertThatThrownBy(() -> validator.validate(embedding))
                .isInstanceOf(EmbeddingException.class)
                .extracting(exception -> ((EmbeddingException) exception).code())
                .isEqualTo(EmbeddingErrorCode.EMBEDDING_INVALID_RESPONSE);
    }

    private float[] nonZeroEmbedding(float firstValue) {
        float[] embedding = new float[DIMENSIONS];
        embedding[0] = firstValue;
        return embedding;
    }
}
