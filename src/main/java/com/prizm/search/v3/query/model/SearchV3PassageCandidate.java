package com.prizm.search.v3.query.model;

import java.util.Objects;

/** ACTIVE generation에서 exact cosine으로 조회한 owner-scoped RetrievalPassage다. */
public record SearchV3PassageCandidate(
        int rank,
        long passageId,
        long generationId,
        long ownerUserId,
        long documentId,
        long documentVersionId,
        String passageKey,
        int passageOrder,
        String parentAnnotationCandidateId,
        double cosineDistance,
        double cosineScore) {

    public SearchV3PassageCandidate {
        Objects.requireNonNull(passageKey, "passageKey");
        Objects.requireNonNull(parentAnnotationCandidateId, "parentAnnotationCandidateId");
        if (rank < 1 || passageId < 1 || generationId < 1 || ownerUserId < 1
                || documentId < 1 || documentVersionId < 1 || passageOrder < 0
                || !Double.isFinite(cosineDistance) || !Double.isFinite(cosineScore)) {
            throw new IllegalArgumentException("Search V3 Passage candidate is invalid.");
        }
    }
}
