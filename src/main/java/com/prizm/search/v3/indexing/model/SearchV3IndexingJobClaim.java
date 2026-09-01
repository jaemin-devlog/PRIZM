package com.prizm.search.v3.indexing.model;

import java.time.Instant;
import java.util.Objects;

/** Search V3 Worker가 소유한 한 번의 처리 시도를 식별하는 full-lineage fencing token이다. */
public record SearchV3IndexingJobClaim(
        long jobId,
        long generationId,
        long ownerUserId,
        long documentId,
        long documentVersionId,
        long claimVersion,
        int attemptCount,
        Instant leaseExpiresAt) {

    public SearchV3IndexingJobClaim {
        if (jobId < 1 || generationId < 1 || ownerUserId < 1 || documentId < 1 || documentVersionId < 1) {
            throw new IllegalArgumentException("Search V3 job lineage identifiers must be positive.");
        }
        if (claimVersion < 1 || attemptCount < 1) {
            throw new IllegalArgumentException("Search V3 claim and attempt counters must be positive.");
        }
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
    }
}
