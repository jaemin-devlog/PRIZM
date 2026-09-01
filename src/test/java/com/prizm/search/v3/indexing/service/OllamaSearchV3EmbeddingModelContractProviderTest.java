package com.prizm.search.v3.indexing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import com.prizm.search.v3.indexing.model.SearchV3EmbeddingModelContract;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.ollama.api.OllamaApi;

@ExtendWith(MockitoExtension.class)
class OllamaSearchV3EmbeddingModelContractProviderTest {

    @Mock
    OllamaApi ollamaApi;

    @Test
    void resolvesTheExactConfiguredModelAndNormalizesItsImmutableDigest() {
        when(ollamaApi.listModels()).thenReturn(response(model(
                "bge-m3",
                "bge-m3:latest",
                "A".repeat(64))));

        SearchV3EmbeddingModelContract contract = provider("bge-m3").resolve();

        assertThat(contract).isEqualTo(new SearchV3EmbeddingModelContract(
                "bge-m3",
                "a".repeat(64),
                1024));
    }

    @Test
    void treatsTheImplicitAndExplicitLatestTagsAsTheSameLocalModel() {
        when(ollamaApi.listModels()).thenReturn(response(model(
                "ollama-local-alias",
                "bge-m3:latest",
                "b".repeat(64))));

        assertThat(provider("bge-m3").resolve())
                .isEqualTo(new SearchV3EmbeddingModelContract(
                        "bge-m3",
                        "b".repeat(64),
                        1024));
    }

    @Test
    void rejectsAnInstalledModelListThatDoesNotContainTheConfiguredModel() {
        when(ollamaApi.listModels()).thenReturn(response(model(
                "different-model:latest",
                "different-model:latest",
                "c".repeat(64))));

        assertThatThrownBy(() -> provider("bge-m3").resolve())
                .isInstanceOfSatisfying(EmbeddingException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(EmbeddingErrorCode.OLLAMA_MODEL_NOT_INSTALLED);
                    assertThat(exception).hasMessageContaining("bge-m3");
                });
    }

    @Test
    void rejectsAnInvalidResolvedDigest() {
        when(ollamaApi.listModels()).thenReturn(response(model(
                "bge-m3",
                "bge-m3:latest",
                "not-a-sha-256")));

        assertThatThrownBy(() -> provider("bge-m3").resolve())
                .isInstanceOfSatisfying(EmbeddingException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(EmbeddingErrorCode.EMBEDDING_INVALID_RESPONSE);
                    assertThat(exception).hasMessageContaining("invalid Search V3 model digest");
                });
    }

    @Test
    void reportsTheOllamaRuntimeFailureWithoutChangingItsMeaning() {
        IllegalStateException runtimeFailure = new IllegalStateException("Ollama is unavailable");
        when(ollamaApi.listModels()).thenThrow(runtimeFailure);

        assertThatThrownBy(() -> provider("bge-m3").resolve())
                .isInstanceOfSatisfying(EmbeddingException.class, exception -> {
                    assertThat(exception.code()).isEqualTo(EmbeddingErrorCode.OLLAMA_UNAVAILABLE);
                    assertThat(exception).hasCause(runtimeFailure);
                });
    }

    private OllamaSearchV3EmbeddingModelContractProvider provider(String configuredModelId) {
        return new OllamaSearchV3EmbeddingModelContractProvider(ollamaApi, configuredModelId, 1024);
    }

    private OllamaApi.ListModelResponse response(OllamaApi.Model... models) {
        return new OllamaApi.ListModelResponse(List.of(models));
    }

    private OllamaApi.Model model(String name, String model, String digest) {
        return new OllamaApi.Model(
                name,
                model,
                Instant.parse("2026-09-02T00:00:00Z"),
                1L,
                digest,
                null);
    }
}
