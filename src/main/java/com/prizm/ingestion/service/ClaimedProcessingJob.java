package com.prizm.ingestion.service;

public record ClaimedProcessingJob(Long jobId, Long documentVersionId, int retryCount) {
}
