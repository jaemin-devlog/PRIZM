package com.prizm.ingestion.service;

import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import com.prizm.ingestion.exception.DocumentIndexingException;
import com.prizm.ingestion.exception.DocumentTextExtractionException;
import com.prizm.ingestion.entity.ProcessingFailureCode;
import org.springframework.stereotype.Component;
import java.util.Locale;

/**
 * 색인 예외를 재시도 가능 여부와 API용 실패 코드로 나눈다.
 * 저장소의 일시 오류와 차원 불일치를 제외한 임베딩 오류는 재시도한다. 잘못된 문서·추출 실패·임베딩 차원
 * 불일치처럼 같은 입력으로 해결되지 않을 오류는 즉시 실패 처리한다.
 */
@Component
public class IndexingFailureClassifier {

    public boolean isRetryable(RuntimeException exception) {
        if (exception instanceof DocumentIndexingException indexingException) {
            return indexingException.isRetryable();
        }
        if (exception instanceof DocumentTextExtractionException) {
            return false;
        }
        if (exception instanceof EmbeddingException embeddingException) {
            return embeddingException.code() != EmbeddingErrorCode.EMBEDDING_DIMENSION_MISMATCH;
        }
        return false;
    }

    public ProcessingFailureCode failureCode(RuntimeException exception) {
        if (exception instanceof EmbeddingException embeddingException) {
            return switch (embeddingException.code()) {
                case OLLAMA_UNAVAILABLE -> isRuntimeFailure(embeddingException)
                        ? ProcessingFailureCode.OLLAMA_RUNTIME_FAILURE
                        : ProcessingFailureCode.OLLAMA_UNAVAILABLE;
                case OLLAMA_MODEL_NOT_INSTALLED -> ProcessingFailureCode.OLLAMA_MODEL_NOT_INSTALLED;
                case EMBEDDING_EMPTY_RESPONSE,
                        EMBEDDING_DIMENSION_MISMATCH,
                        EMBEDDING_INVALID_RESPONSE -> ProcessingFailureCode.DOCUMENT_PROCESSING_FAILED;
            };
        }
        return ProcessingFailureCode.DOCUMENT_PROCESSING_FAILED;
    }

    private boolean isRuntimeFailure(Throwable exception) {
        StringBuilder messages = new StringBuilder();
        Throwable current = exception;
        while (current != null) {
            messages.append(' ').append(String.valueOf(current.getMessage()).toLowerCase(Locale.ROOT));
            current = current.getCause();
        }
        String message = messages.toString();
        return message.contains("runner")
                || message.contains("rocm")
                || message.contains("cuda")
                || message.contains("invalid device function")
                || message.contains("failed to load model")
                || message.contains("error loading model")
                || message.contains("status code: 500")
                || message.contains("500 internal server error");
    }
}
