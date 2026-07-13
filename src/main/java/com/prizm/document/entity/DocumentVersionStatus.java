package com.prizm.document.entity;

/** 문서 버전이 업로드된 뒤 승인·색인·활성화되는 처리 상태다. */
public enum DocumentVersionStatus {
    QUARANTINED,
    APPROVED,
    INDEXING,
    ACTIVE,
    FAILED
}
