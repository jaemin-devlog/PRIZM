package com.prizm.cleanup.service;

import java.time.Instant;

public record ClaimedFileCleanupJob(
        long fileCleanupJobId,
        String storageKey,
        int attempts,
        long claimVersion,
        Instant leaseExpiresAt) {
}
