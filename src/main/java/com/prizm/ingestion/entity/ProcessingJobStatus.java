package com.prizm.ingestion.entity;

public enum ProcessingJobStatus {
    PENDING,
    RETRY_WAIT,
    PROCESSING,
    COMPLETED,
    FAILED
}
