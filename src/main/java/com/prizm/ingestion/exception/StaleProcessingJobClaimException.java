package com.prizm.ingestion.exception;

/** 임대가 만료되거나 다른 Worker가 다시 선점해 더 이상 유효하지 않은 처리 시도다. */
public class StaleProcessingJobClaimException extends RuntimeException {

    public StaleProcessingJobClaimException(Long processingJobId, long claimVersion) {
        super("Processing job %d claim version %d is no longer active."
                .formatted(processingJobId, claimVersion));
    }
}
