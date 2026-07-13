package com.prizm.ingestion.config;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** TXT 청크 분할과 Worker 실행 주기를 관리하는 설정이다. */
@ConfigurationProperties(prefix = "prizm.ingestion")
public class IngestionProperties {

    private int maxChunkLength = 800;
    private int overlap = 120;
    private long pollDelayMs = 1000;
    private Duration leaseDuration = Duration.ofMinutes(10);
    private int leaseRefreshChunkInterval = 10;
    private long recoveryDelayMs = 60_000;
    private boolean workerEnabled = true;

    @PostConstruct
    public void validate() {
        if (maxChunkLength < 1) {
            throw new IllegalArgumentException("maxChunkLength must be at least 1");
        }
        if (overlap < 0 || overlap >= maxChunkLength) {
            throw new IllegalArgumentException("overlap must be between 0 and maxChunkLength - 1");
        }
        if (pollDelayMs < 1) {
            throw new IllegalArgumentException("pollDelayMs must be at least 1");
        }
        if (leaseDuration == null || leaseDuration.isZero() || leaseDuration.isNegative()) {
            throw new IllegalArgumentException("leaseDuration must be positive");
        }
        if (leaseRefreshChunkInterval < 1) {
            throw new IllegalArgumentException("leaseRefreshChunkInterval must be at least 1");
        }
        if (recoveryDelayMs < 1) {
            throw new IllegalArgumentException("recoveryDelayMs must be at least 1");
        }
    }

    public int getMaxChunkLength() {
        return maxChunkLength;
    }

    public void setMaxChunkLength(int maxChunkLength) {
        this.maxChunkLength = maxChunkLength;
    }

    public int getOverlap() {
        return overlap;
    }

    public void setOverlap(int overlap) {
        this.overlap = overlap;
    }

    public long getPollDelayMs() {
        return pollDelayMs;
    }

    public void setPollDelayMs(long pollDelayMs) {
        this.pollDelayMs = pollDelayMs;
    }

    public Duration getLeaseDuration() {
        return leaseDuration;
    }

    public void setLeaseDuration(Duration leaseDuration) {
        this.leaseDuration = leaseDuration;
    }

    public int getLeaseRefreshChunkInterval() {
        return leaseRefreshChunkInterval;
    }

    public void setLeaseRefreshChunkInterval(int leaseRefreshChunkInterval) {
        this.leaseRefreshChunkInterval = leaseRefreshChunkInterval;
    }

    public long getRecoveryDelayMs() {
        return recoveryDelayMs;
    }

    public void setRecoveryDelayMs(long recoveryDelayMs) {
        this.recoveryDelayMs = recoveryDelayMs;
    }

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }
}
