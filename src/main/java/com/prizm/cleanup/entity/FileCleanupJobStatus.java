package com.prizm.cleanup.entity;

/**
 * 파일 삭제 작업의 상태다. PENDING과 RETRY_WAIT만 claim할 수 있고 PROCESSING에는 lease가 적용된다.
 */
public enum FileCleanupJobStatus {
    PENDING,
    PROCESSING,
    RETRY_WAIT,
    COMPLETED,
    FAILED
}
