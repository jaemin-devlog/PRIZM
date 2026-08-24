package com.prizm.changelog.service;

/** 선점한 ChangeLog 식별자를 트랜잭션 밖의 실패 기록 경계까지 전달한다. */
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
