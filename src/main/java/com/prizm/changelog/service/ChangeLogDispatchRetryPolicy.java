package com.prizm.changelog.service;

import java.time.Duration;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 최초 시도 뒤 1분, 5분, 15분의 세 번 재시도만 허용한다. */
@Component
public class ChangeLogDispatchRetryPolicy {

    public Optional<Duration> nextDelay(int persistedRetryCount) {
        return switch (persistedRetryCount) {
            case 0 -> Optional.of(Duration.ofMinutes(1));
            case 1 -> Optional.of(Duration.ofMinutes(5));
            case 2 -> Optional.of(Duration.ofMinutes(15));
            default -> Optional.empty();
        };
    }
}
