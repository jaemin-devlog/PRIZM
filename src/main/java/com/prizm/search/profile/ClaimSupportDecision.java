package com.prizm.search.profile;

import java.util.Set;

/** Structured eligibility result kept internal to search and evaluation tracing. */
public record ClaimSupportDecision(
        Status status,
        Set<Reason> reasons,
        boolean directSupport,
        String matchedClaimWindow) {

    public ClaimSupportDecision {
        reasons = Set.copyOf(reasons);
        matchedClaimWindow = matchedClaimWindow == null ? "" : matchedClaimWindow;
    }

    public enum Status {
        SUPPORTED,
        CONTRADICTED,
        UNSUPPORTED
    }

    public enum Reason {
        NON_CLAIM_QUERY,
        DIRECT_CLAIM_SUPPORT,
        ACTOR_MISMATCH,
        NEGATED_TARGET_CLAIM,
        NOT_ADOPTED,
        STATE_MISMATCH,
        ENTITY_NOT_BOUND_TO_ACTION,
        ACTION_NOT_SUPPORTED,
        METRIC_MISMATCH,
        NUMERIC_VALUE_MISMATCH,
        UNIT_MISMATCH,
        INSUFFICIENT_CLAIM_SUPPORT
    }
}
