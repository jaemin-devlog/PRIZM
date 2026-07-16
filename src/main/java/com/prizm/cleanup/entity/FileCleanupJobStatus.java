package com.prizm.cleanup.entity;

public enum FileCleanupJobStatus {
    PENDING,
    PROCESSING,
    RETRY_WAIT,
    COMPLETED,
    FAILED
}
