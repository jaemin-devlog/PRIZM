package com.prizm.document.entity;

/** 문서 버전이 업로드된 뒤 자동 처리되어 활성화되는 상태다. */
public enum DocumentVersionStatus {
    QUARANTINED,
    PROCESSING,
    ACTIVE,
    FAILED
}
