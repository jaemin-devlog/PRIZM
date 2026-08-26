package com.prizm.search.dto.response;

/** 근거를 찾지 못한 경우까지 오류가 아닌 정상 검색 결과로 구분한다. */
public enum CareerEvidenceSearchState {
    /** 관련 근거를 하나 이상 찾았다. */
    EVIDENCE_FOUND,
    /** 검색할 문서는 있지만 일반 질의와 관련된 후보가 없다. */
    NO_RELEVANT_RESULTS,
    /** 완료된 출시·배포를 묻는 질의에 필요한 직접 근거가 없다. */
    NO_EVIDENCE,
    /** 사용자의 ACTIVE 문서에 검색할 청크가 없다. */
    NO_SEARCHABLE_DOCUMENTS
}
