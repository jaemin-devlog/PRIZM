package com.prizm.search.v3.indexing.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** 만료된 Search V3 claim을 회수할 수 있는 exact recovery token이다. */
public record SearchV3RecoveryLock(
        SearchV3IndexingJobClaim expiredClaim,
        UUID recoveryToken,
        Instant recoveryLockedAt) {

    public SearchV3RecoveryLock {
        Objects.requireNonNull(expiredClaim, "expiredClaim");
        Objects.requireNonNull(recoveryToken, "recoveryToken");
        Objects.requireNonNull(recoveryLockedAt, "recoveryLockedAt");
    }
}
