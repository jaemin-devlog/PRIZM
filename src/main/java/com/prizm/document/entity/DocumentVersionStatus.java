package com.prizm.document.entity;

/** 현재 문서 버전의 저장·활성 상태다. 업로드 직후에는 QUARANTINED만 사용한다. */
public enum DocumentVersionStatus {
    QUARANTINED,
    ACTIVE,
    FAILED
}
