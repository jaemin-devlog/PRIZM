package com.prizm.search.v3.indexing.model;

/** Search V3 failure 처리 결과로 외부 Worker에 노출하는 상태다. */
public enum SearchV3IndexingJobStatus {
    RETRY_WAIT,
    FAILED
}
