package com.prizm.changelog.service;

/** Transaction A가 claim한 ChangeLog의 실패를 scheduler 밖 경계까지 전달한다. */
public class ChangeLogDispatchFailureException extends RuntimeException {

    private final Long changeLogId;

    public ChangeLogDispatchFailureException(Long changeLogId, RuntimeException cause) {
        super("ChangeLog dispatch failed for " + changeLogId + ".", cause);
        if (changeLogId == null) {
            throw new IllegalArgumentException("changeLogId is required");
        }
        this.changeLogId = changeLogId;
    }

    public Long getChangeLogId() {
        return changeLogId;
    }
}
