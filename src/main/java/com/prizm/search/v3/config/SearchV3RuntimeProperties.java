package com.prizm.search.v3.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Search V3 shadow dispatch·processing·recovery scheduler 설정이다. */
@ConfigurationProperties(prefix = "prizm.search-v3")
public class SearchV3RuntimeProperties {

    private boolean workerEnabled;
    private long dispatchDelayMs = 5_000;
    private long pollDelayMs = 1_000;
    private long recoveryDelayMs = 60_000;

    @PostConstruct
    void validate() {
        if (dispatchDelayMs < 1 || pollDelayMs < 1 || recoveryDelayMs < 1) {
            throw new IllegalArgumentException("Search V3 scheduler delays must be positive.");
        }
    }

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public long getDispatchDelayMs() {
        return dispatchDelayMs;
    }

    public void setDispatchDelayMs(long dispatchDelayMs) {
        this.dispatchDelayMs = dispatchDelayMs;
    }

    public long getPollDelayMs() {
        return pollDelayMs;
    }

    public void setPollDelayMs(long pollDelayMs) {
        this.pollDelayMs = pollDelayMs;
    }

    public long getRecoveryDelayMs() {
        return recoveryDelayMs;
    }

    public void setRecoveryDelayMs(long recoveryDelayMs) {
        this.recoveryDelayMs = recoveryDelayMs;
    }
}
