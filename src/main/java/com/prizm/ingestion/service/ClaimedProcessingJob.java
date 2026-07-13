package com.prizm.ingestion.service;

import java.time.Instant;

/** Worker가 선점한 한 번의 처리 시도를 식별하는 fencing 정보다. */
public record ClaimedProcessingJob(
        Long processingJobId,
        Long documentVersionId,
        long claimVersion,
        Instant leaseExpiresAt) {
}
