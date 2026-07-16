package com.prizm.cleanup.exception;

public class StaleFileCleanupJobClaimException extends RuntimeException {

    public StaleFileCleanupJobClaimException(long fileCleanupJobId, long claimVersion) {
        super("File cleanup job claim is no longer current: id=" + fileCleanupJobId + ", claimVersion=" + claimVersion);
    }
}
