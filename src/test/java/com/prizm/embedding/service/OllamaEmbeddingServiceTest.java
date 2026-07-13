package com.prizm.embedding.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;

/** Ollama 오류 분류와 1024차원 응답 계약을 단위 검증한다. */
@ExtendWith(MockitoExtension.class)
class OllamaEmbeddingServiceTest {

    @Mock
    EmbeddingModel embeddingModel;

    @Test
    void returnsEmbeddingWhenDimensionMatches() {
        float[] expected = new float[1024];
        when(embeddingModel.embed("query")).thenReturn(expected);

        float[] result = new OllamaEmbeddingService(embeddingModel, "bge-m3", 1024).embed("query");

        assertThat(result).isSameAs(expected);
    }

    @Test
    void rejectsUnexpectedEmbeddingDimension() {
        when(embeddingModel.embed("query")).thenReturn(new float[768]);

        assertThatThrownBy(() -> new OllamaEmbeddingService(embeddingModel, "bge-m3", 1024).embed("query"))
                .isInstanceOf(EmbeddingException.class)
                .extracting(exception -> ((EmbeddingException) exception).code())
                .isEqualTo(EmbeddingErrorCode.EMBEDDING_DIMENSION_MISMATCH);
    }

    @Test
    void rejectsEmptyEmbeddingResponse() {
        when(embeddingModel.embed("query")).thenReturn((float[]) null);

        assertThatThrownBy(() -> new OllamaEmbeddingService(embeddingModel, "bge-m3", 1024).embed("query"))
                .isInstanceOf(EmbeddingException.class)
                .extracting(exception -> ((EmbeddingException) exception).code())
                .isEqualTo(EmbeddingErrorCode.EMBEDDING_EMPTY_RESPONSE);
    }

    @Test
    void classifiesMissingModelResponse() {
        when(embeddingModel.embed("query")).thenThrow(new RuntimeException("model 'bge-m3' not found"));

        assertThatThrownBy(() -> new OllamaEmbeddingService(embeddingModel, "bge-m3", 1024).embed("query"))
                .isInstanceOf(EmbeddingException.class)
                .extracting(exception -> ((EmbeddingException) exception).code())
                .isEqualTo(EmbeddingErrorCode.OLLAMA_MODEL_NOT_INSTALLED);
    }

    @Test
    void classifiesConnectionFailure() {
        when(embeddingModel.embed("query")).thenThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> new OllamaEmbeddingService(embeddingModel, "bge-m3", 1024).embed("query"))
                .isInstanceOf(EmbeddingException.class)
                .extracting(exception -> ((EmbeddingException) exception).code())
                .isEqualTo(EmbeddingErrorCode.OLLAMA_UNAVAILABLE);
    }
}
