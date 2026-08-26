package com.prizm.changelog.service;

/** 별도 실패 기록 트랜잭션이 처리할 전달 실패의 최소 분류다. */
public enum ChangeLogDispatchFailureDisposition {
    RETRYABLE,
    PERMANENT
}
