package com.prizm.changelog.service;

/** Transaction B가 처리할 dispatch 실패의 최소 분류다. */
public enum ChangeLogDispatchFailureDisposition {
    RETRYABLE,
    PERMANENT
}
