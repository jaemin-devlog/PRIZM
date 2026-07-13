package com.prizm.embedding;

import java.util.Locale;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class OllamaEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final String modelName;
    private final int expectedDimensions;

    public OllamaEmbeddingService(
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.ollama.embedding.model}") String modelName,
            @Value("${prizm.embedding.dimensions}") int expectedDimensions) {
        this.embeddingModel = embeddingModel;
        this.modelName = modelName;
        this.expectedDimensions = expectedDimensions;
    }

    @Override
    public float[] embed(String text) {
        final float[] embedding;
        try {
            embedding = embeddingModel.embed(text);
        }
        catch (RuntimeException exception) {
            throw classifyFailure(exception);
        }

        if (embedding == null || embedding.length == 0) {
            throw new EmbeddingException(
                    EmbeddingErrorCode.EMBEDDING_EMPTY_RESPONSE,
                    "Ollama returned no embedding values.");
        }
        if (embedding.length != expectedDimensions) {
            throw new EmbeddingException(
                    EmbeddingErrorCode.EMBEDDING_DIMENSION_MISMATCH,
                    "Expected a %d-dimensional embedding but received %d."
                            .formatted(expectedDimensions, embedding.length));
        }
        return embedding;
    }

    private EmbeddingException classifyFailure(RuntimeException exception) {
        String message = String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT);
        if (message.contains("model") && (message.contains("not found") || message.contains("pull"))) {
            return new EmbeddingException(
                    EmbeddingErrorCode.OLLAMA_MODEL_NOT_INSTALLED,
                    "Ollama model '%s' is not installed.".formatted(modelName),
                    exception);
        }
        return new EmbeddingException(
                EmbeddingErrorCode.OLLAMA_UNAVAILABLE,
                "Ollama embedding service is unavailable.",
                exception);
    }
}
