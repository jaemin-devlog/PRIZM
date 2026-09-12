package com.prizm.search.v3.indexing.exception;

import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;

/** 현재 DB 소유권과 일치하지 않는 Search V3 Worker mutation을 거부한다. */
public class StaleSearchV3IndexingJobClaimException extends IllegalStateException {

    public StaleSearchV3IndexingJobClaimException(SearchV3IndexingJobClaim claim) {
        super("Search V3 indexing claim is stale: job=" + claim.jobId()
                + ", generation=" + claim.generationId()
                + ", claimVersion=" + claim.claimVersion());
    }
}
