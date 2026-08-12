package com.prizm.search.evaluation;

import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.repository.VectorSearchResult;
import java.util.List;

/**
 * Evaluation-only profile that defers source-location deduplication until every candidate has
 * passed the current product eligibility and completed-release claim checks.
 */
public final class SearchEvaluationDeferredPageDedupProfile {

    public static final String PROFILE_ID = "deferred-page-dedup-v1";

    private final CompositeSearchProfile delegate = new CompositeSearchProfile();

    public CompositeSearchProfile.Decision apply(
            String query,
            List<VectorSearchResult> denseCandidates) {
        if (denseCandidates.isEmpty()) {
            return delegate.apply(query, denseCandidates);
        }

        List<VectorSearchResult> eligibleCandidates = denseCandidates.stream()
                .filter(candidate -> !delegate.apply(query, List.of(candidate)).rejected())
                .toList();
        if (eligibleCandidates.isEmpty()) {
            return delegate.apply(query, denseCandidates);
        }

        CompositeSearchProfile.Decision deferredDecision =
                delegate.apply(query, eligibleCandidates);
        return new CompositeSearchProfile.Decision(
                denseCandidates,
                deferredDecision.results(),
                deferredDecision.rejectionReasons());
    }
}
