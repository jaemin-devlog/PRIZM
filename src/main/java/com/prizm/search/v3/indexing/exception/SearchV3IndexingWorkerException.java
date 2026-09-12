package com.prizm.search.v3.indexing.exception;

import com.prizm.search.v3.indexing.model.SearchV3IndexingFailureStage;
import java.util.Objects;

/** 한 Worker 처리 시도의 실패 단계와 재시도 가능 여부를 함께 보존한다. */
public class SearchV3IndexingWorkerException extends RuntimeException {

    private final SearchV3IndexingFailureStage failureStage;
    private final boolean retryable;

    public SearchV3IndexingWorkerException(
            SearchV3IndexingFailureStage failureStage,
            boolean retryable,
            String message,
            Throwable cause) {
        super(message, cause);
        this.failureStage = Objects.requireNonNull(failureStage, "failureStage");
        this.retryable = retryable;
    }

    public SearchV3IndexingFailureStage failureStage() {
        return failureStage;
    }

    public boolean retryable() {
        return retryable;
    }
}
