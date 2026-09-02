package com.prizm.search.v3.query.model;

/** 정확 조건에 대한 source-grounded 검색 상태이며, 경력의 존재나 진위 판정이 아니다. */
public enum SearchV3TypedEvidenceState {
    FOUND,
    PARTIAL,
    NONE,
    UNASSESSED
}
