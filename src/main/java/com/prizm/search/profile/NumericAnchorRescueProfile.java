package com.prizm.search.profile;

import com.prizm.search.repository.VectorSearchResult;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Rescues exact number-and-unit evidence only after the normal dense result is empty. */
public final class NumericAnchorRescueProfile {

    private static final double PRODUCTION_MINIMUM_DENSE_SCORE = 0.50d;

    private final CompositeSearchProfile delegate;

    public NumericAnchorRescueProfile(CompositeSearchProfile delegate) {
        this.delegate = delegate;
    }

    public List<VectorSearchResult> apply(String query, List<VectorSearchResult> candidates) {
        List<VectorSearchResult> contextualCandidates = candidates.stream()
                .filter(candidate -> NumericQueryAnchors.hasContextualMatch(query, candidate.content()))
                .toList();
        if (contextualCandidates.isEmpty()) {
            return List.of();
        }

        Map<Long, VectorSearchResult> originalsByChunkId = contextualCandidates.stream()
                .collect(Collectors.toMap(VectorSearchResult::chunkId, Function.identity()));
        List<VectorSearchResult> promoted = contextualCandidates.stream()
                .map(NumericAnchorRescueProfile::promoteForEligibility)
                .toList();
        return delegate.apply(query, promoted).results().stream()
                .map(result -> originalsByChunkId.get(result.chunkId()))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private static VectorSearchResult promoteForEligibility(VectorSearchResult candidate) {
        double promotedScore = Math.max(PRODUCTION_MINIMUM_DENSE_SCORE, candidate.score());
        return new VectorSearchResult(
                candidate.chunkId(),
                candidate.documentId(),
                candidate.documentVersionId(),
                candidate.documentTitle(),
                candidate.versionNo(),
                candidate.chunkNo(),
                candidate.pageNo(),
                candidate.sourceType(),
                candidate.sourceIndex(),
                candidate.sourceLabel(),
                candidate.content(),
                1.0d - promotedScore,
                promotedScore);
    }
}
