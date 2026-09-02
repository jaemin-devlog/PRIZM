package com.prizm.search.v3.indexing.model;

/** 자동 dispatch가 원자적으로 만든 generation과 PENDING job의 full lineage다. */
public record SearchV3DispatchedJob(
        long jobId,
        long generationId,
        long ownerUserId,
        long documentId,
        long documentVersionId) {

    public SearchV3DispatchedJob {
        if (jobId < 1 || generationId < 1 || ownerUserId < 1 || documentId < 1 || documentVersionId < 1) {
            throw new IllegalArgumentException("Search V3 dispatched job lineage must be positive.");
        }
    }
}
