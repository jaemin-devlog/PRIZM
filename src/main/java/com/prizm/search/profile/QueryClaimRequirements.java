package com.prizm.search.profile;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/** Query constraints that must be bound inside one local candidate claim window. */
public record QueryClaimRequirements(
        boolean claimQuestion,
        boolean actorRequired,
        Set<Action> actions,
        Set<String> entities,
        Set<String> subjectTerms,
        Polarity polarity,
        State requiredState,
        Metric metric,
        List<NumericConstraint> numericConstraints,
        Direction direction) {

    public QueryClaimRequirements {
        actions = Set.copyOf(actions);
        entities = Set.copyOf(entities);
        subjectTerms = Set.copyOf(subjectTerms);
        numericConstraints = List.copyOf(numericConstraints);
    }

    public enum Action {
        IMPLEMENT,
        USE,
        APPLY,
        DEPLOY,
        IMPROVE,
        SOLVE,
        DISCARD,
        STOP,
        OVERWRITE,
        SERIALIZE,
        VERIFY,
        RESTRUCTURE
    }

    public enum Polarity {
        POSITIVE,
        UNSPECIFIED
    }

    public enum State {
        ANY,
        USED,
        PRODUCTION,
        CURRENT
    }

    public enum Metric {
        UNKNOWN,
        LATENCY,
        DURATION,
        COST,
        STORAGE_VOLUME,
        MEMORY,
        RATE,
        COUNT,
        THROUGHPUT
    }

    public enum Direction {
        UNKNOWN,
        DECREASE,
        INCREASE,
        PREVENT
    }

    public record NumericConstraint(BigDecimal value, String unit) {

        public NumericConstraint {
            value = value.stripTrailingZeros();
        }
    }
}
