package com.prizm.embedding.service;

import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 저장할 문서 벡터와 검색 질의 벡터에 같은 차원·유한값·0이 아닌 norm 계약을 적용한다.
 * 차원이 다르면 고정 차원 {@code vector} 컬럼에 저장할 수 없다. NaN과 무한대는 유사도 점수를 무효로 만들고,
 * norm이 0인 벡터는 cosine similarity에 필요한 방향이 없으므로 DB로 보내지 않는다.
 */
@Component
public class EmbeddingValidator {

    private final int expectedDimensions;

    public EmbeddingValidator(@Value("${prizm.embedding.dimensions}") int expectedDimensions) {
        this.expectedDimensions = expectedDimensions;
    }

    /** 계약을 어기면 오류 코드가 있는 {@link EmbeddingException}을 던져 저장·검색을 중단한다. */
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
