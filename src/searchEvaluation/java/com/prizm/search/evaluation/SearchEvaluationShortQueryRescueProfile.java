package com.prizm.search.evaluation;

import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.profile.ShortGeneralExactTokenRescueProfile;
import com.prizm.search.repository.VectorSearchResult;
import java.util.List;

/**
 * Evaluation-only profile that rescues at most one exact-token GENERAL result immediately below
 * the production dense floor.
 */
final class SearchEvaluationShortQueryRescueProfile {

    static final String PROFILE_ID = "short-general-exact-token-rescue-v1";
    static final double RESCUE_MINIMUM_DENSE_SCORE =
            ShortGeneralExactTokenRescueProfile.RESCUE_MINIMUM_DENSE_SCORE;

    private final ShortGeneralExactTokenRescueProfile productionProfile =
            new ShortGeneralExactTokenRescueProfile(new CompositeSearchProfile());

    CompositeSearchProfile.Decision apply(
            String query,
            List<VectorSearchResult> denseCandidates) {
        return productionProfile.apply(query, denseCandidates);
    }
}
