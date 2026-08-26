package com.prizm.embedding.service;

import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import java.util.Locale;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Spring AI의 Ollama 모델을 {@link EmbeddingService} 포트에 연결한다.
 * 제공자·모델 오류를 도메인 오류 코드로 바꾸고, 성공 응답도 공통 벡터 계약을 통과시킨다.
 */
@Service
public class OllamaEmbeddingService implements EmbeddingService {

    private final EmbeddingModel embeddingModel;
    private final String modelName;
    private final EmbeddingValidator embeddingValidator;

    public OllamaEmbeddingService(
            EmbeddingModel embeddingModel,
            @Value("${spring.ai.ollama.embedding.model}") String modelName,
            EmbeddingValidator embeddingValidator) {
        this.embeddingModel = embeddingModel;
        this.modelName = modelName;
        this.embeddingValidator = embeddingValidator;
    }

    /** 제공자 응답이 설정된 차원과 값 계약을 만족할 때만 저장·검색 계층으로 반환한다. */
    @Override
    public float[] embed(String text) {
        final float[] embedding;
        try {
            embedding = embeddingModel.embed(text);
        }
        catch (RuntimeException exception) {
            throw classifyFailure(exception);
        }

        embeddingValidator.validate(embedding);
        return embedding;
    }

    private EmbeddingException classifyFailure(RuntimeException exception) {
        StringBuilder failureMessages = new StringBuilder();
        for (Throwable current = exception; current != null; current = current.getCause()) {
            failureMessages.append(' ').append(String.valueOf(current.getMessage()).toLowerCase(Locale.ROOT));
        }
        String message = failureMessages.toString();
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
