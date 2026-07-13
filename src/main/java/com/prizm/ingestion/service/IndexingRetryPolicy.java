package com.prizm.ingestion.service;

import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** 색인 실패 후 최대 세 번을 1분·5분·15분 간격으로 재시도한다. */
@Component
public class IndexingRetryPolicy {

    public static final int MAX_RETRIES = 3;

    public boolean canRetry(int currentRetryCount) {
        return currentRetryCount < MAX_RETRIES;
    }

    public Instant nextRetryAt(int currentRetryCount, Instant now) {
        int nextRetryCount = currentRetryCount + 1;
        Duration delay = switch (nextRetryCount) {
            case 1 -> Duration.ofMinutes(1);
            case 2 -> Duration.ofMinutes(5);
            case 3 -> Duration.ofMinutes(15);
            default -> throw new IllegalArgumentException("Retry count exceeds the configured maximum.");
        };
        return now.plus(delay);
    }
}
