package com.prizm.search.dto.response;

import java.util.List;
import java.util.Objects;

/** Versioned evidence-search response that distinguishes normal empty-result states. */
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
