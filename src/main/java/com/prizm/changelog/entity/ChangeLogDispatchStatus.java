package com.prizm.changelog.entity;

/** Indexing consumer로의 전달 상태다. 색인 완료 상태는 ProcessingJob이 표현한다. */
public enum ChangeLogDispatchStatus {
    PENDING,
    RETRY_WAIT,
    DISPATCHED,
    FAILED
}
