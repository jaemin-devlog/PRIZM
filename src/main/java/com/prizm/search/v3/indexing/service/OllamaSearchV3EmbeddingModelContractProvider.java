package com.prizm.search.v3.indexing.service;

import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import com.prizm.search.v3.indexing.model.SearchV3EmbeddingModelContract;
import java.util.List;
import java.util.Locale;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Ollama local model 목록에서 configured BGE-M3의 immutable digest를 확인한다. */
@Service
public class OllamaSearchV3EmbeddingModelContractProvider
        implements SearchV3EmbeddingModelContractProvider {

    private final OllamaApi ollamaApi;
    private final String configuredModelId;
    private final int dimension;

    public OllamaSearchV3EmbeddingModelContractProvider(
            OllamaApi ollamaApi,
            @Value("${spring.ai.ollama.embedding.model}") String configuredModelId,
            @Value("${prizm.embedding.dimensions}") int dimension) {
        this.ollamaApi = ollamaApi;
        this.configuredModelId = configuredModelId;
        this.dimension = dimension;
    }

    @Override
    public SearchV3EmbeddingModelContract resolve() {
        final List<OllamaApi.Model> models;
        try {
            OllamaApi.ListModelResponse response = ollamaApi.listModels();
            models = response.models() == null ? List.of() : response.models();
        }
        catch (RuntimeException exception) {
            throw new EmbeddingException(
                    EmbeddingErrorCode.OLLAMA_UNAVAILABLE,
                    "Could not resolve the Search V3 Ollama model digest.",
                    exception);
        }

        OllamaApi.Model model = models.stream()
                .filter(candidate -> matches(candidate.name()) || matches(candidate.model()))
                .findFirst()
                .orElseThrow(() -> new EmbeddingException(
                        EmbeddingErrorCode.OLLAMA_MODEL_NOT_INSTALLED,
                        "Ollama model '%s' is not installed.".formatted(configuredModelId)));
        String digest = model.digest() == null ? "" : model.digest().toLowerCase(Locale.ROOT);
        try {
            return new SearchV3EmbeddingModelContract(configuredModelId, digest, dimension);
        }
        catch (IllegalArgumentException exception) {
            throw new EmbeddingException(
                    EmbeddingErrorCode.EMBEDDING_INVALID_RESPONSE,
                    "Ollama returned an invalid Search V3 model digest.",
                    exception);
        }
    }

    private boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }
        return candidate.equals(configuredModelId)
                || stripLatest(candidate).equals(stripLatest(configuredModelId));
    }

    private String stripLatest(String value) {
        return value.endsWith(":latest") ? value.substring(0, value.length() - 7) : value;
    }
}
