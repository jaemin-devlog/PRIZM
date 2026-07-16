package com.prizm.cleanup.config;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration shared by cleanup polling, recovery, and short-lived claims. */
@ConfigurationProperties(prefix = "prizm.cleanup")
public class CleanupProperties {

    private boolean workerEnabled = true;
    private long pollDelayMs = 5_000;
    private long recoveryDelayMs = 60_000;
    private int batchSize = 10;
    private Duration leaseDuration = Duration.ofMinutes(5);

    @PostConstruct
    public void validate() {
        if (pollDelayMs < 1) {
            throw new IllegalArgumentException("cleanup pollDelayMs must be at least 1");
        }
        if (recoveryDelayMs < 1) {
            throw new IllegalArgumentException("cleanup recoveryDelayMs must be at least 1");
        }
        if (batchSize < 1) {
            throw new IllegalArgumentException("cleanup batchSize must be at least 1");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("cleanup leaseDuration must be positive");
        }
    }

    public boolean isWorkerEnabled() { return workerEnabled; }
    public void setWorkerEnabled(boolean workerEnabled) { this.workerEnabled = workerEnabled; }
    public long getPollDelayMs() { return pollDelayMs; }
    public void setPollDelayMs(long pollDelayMs) { this.pollDelayMs = pollDelayMs; }
    public long getRecoveryDelayMs() { return recoveryDelayMs; }
    public void setRecoveryDelayMs(long recoveryDelayMs) { this.recoveryDelayMs = recoveryDelayMs; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public Duration getLeaseDuration() { return leaseDuration; }
    public void setLeaseDuration(Duration leaseDuration) { this.leaseDuration = leaseDuration; }
}
