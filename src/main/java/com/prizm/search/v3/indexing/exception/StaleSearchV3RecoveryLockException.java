package com.prizm.search.v3.indexing.exception;

import com.prizm.search.v3.indexing.model.SearchV3RecoveryLock;

/** 현재 DB recovery lock과 일치하지 않는 reclaim을 거부한다. */
public class StaleSearchV3RecoveryLockException extends IllegalStateException {

    public StaleSearchV3RecoveryLockException(SearchV3RecoveryLock recoveryLock) {
        super("Search V3 recovery lock is stale: job=" + recoveryLock.expiredClaim().jobId()
                + ", generation=" + recoveryLock.expiredClaim().generationId()
                + ", token=" + recoveryLock.recoveryToken());
    }
}
