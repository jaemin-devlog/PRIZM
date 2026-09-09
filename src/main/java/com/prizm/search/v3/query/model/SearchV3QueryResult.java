package com.prizm.search.v3.query.model;

import java.util.List;

/** Passage 후보와 최종 atomic 근거를 분리해 보존하는 shadow query 결과다. */
public record SearchV3QueryResult(
        SearchV3TypedEvidenceState state,
        int parsedConstraintCount,
        int passageCandidateCount,
        List<SearchV3EvidenceResult> evidence) {

    public SearchV3QueryResult {
        evidence = List.copyOf(evidence);
        if (parsedConstraintCount < 0 || passageCandidateCount < 0 || evidence.size() > 5) {
            throw new IllegalArgumentException("Search V3 query result inventory is invalid.");
        }
    }
}
