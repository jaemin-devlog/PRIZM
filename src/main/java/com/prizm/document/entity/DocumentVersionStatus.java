package com.prizm.document.entity;

/**
 * 문서 원본의 처리 상태다. ACTIVE만 검색 후보가 되며, FAILED 버전은 기존 ACTIVE 포인터를
 * 대체하지 않는다.
 */
public enum DocumentVersionStatus {
    QUARANTINED,
    PROCESSING,
    ACTIVE,
    FAILED
}
