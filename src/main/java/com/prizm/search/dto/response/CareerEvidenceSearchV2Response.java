package com.prizm.search.dto.response;

import java.util.List;
import java.util.Objects;

/** 근거 발견과 서로 다른 빈 결과 상태를 구분하는 검색 응답이다. */
public record CareerEvidenceSearchV2Response(
        CareerEvidenceSearchState state,
        List<CareerEvidenceSearchResponse> results) {

    public CareerEvidenceSearchV2Response {
        state = Objects.requireNonNull(state, "state must not be null");
        results = List.copyOf(Objects.requireNonNull(results, "results must not be null"));
        if (state == CareerEvidenceSearchState.EVIDENCE_FOUND && results.isEmpty()) {
            throw new IllegalArgumentException("EVIDENCE_FOUND requires at least one result.");
        }
        if (state != CareerEvidenceSearchState.EVIDENCE_FOUND && !results.isEmpty()) {
            throw new IllegalArgumentException("Empty search states must not contain results.");
        }
    }
}
