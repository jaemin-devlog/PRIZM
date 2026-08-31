package com.prizm.search.evaluation.searchv3.typed;

import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.CandidateObservation;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.MatchState;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QueryConstraint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

/** Candidate-preserving SATISFIED -> UNKNOWN -> CONTRADICTED stable partition. */
public final class TypedStablePartitioner {

    private final TypedConstraintEvaluator evaluator;

    public TypedStablePartitioner() {
        this(new TypedConstraintEvaluator());
    }

    TypedStablePartitioner(TypedConstraintEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    /**
     * Returns the original ranking object when no constraint exists. For a typed query, every input
     * candidate appears exactly once and state-local order is unchanged.
     */
    public <T> List<T> partition(
            List<T> fullRanking,
            List<QueryConstraint> constraints,
            Function<T, List<CandidateObservation>> observationProvider) {
        Objects.requireNonNull(fullRanking, "fullRanking");
        Objects.requireNonNull(constraints, "constraints");
        Objects.requireNonNull(observationProvider, "observationProvider");
        if (constraints.isEmpty()) {
            return fullRanking;
        }

        return partitionEvaluated(
                fullRanking,
                true,
                candidate -> evaluator.evaluateAll(
                        constraints,
                        List.copyOf(Objects.requireNonNull(
                                observationProvider.apply(candidate), "candidate observations"))));
    }

    /**
     * Stable-partitions candidates using states already evaluated by the caller. This avoids parsing
     * and evaluating the same candidate twice in measured online paths.
     */
    public <T> List<T> partitionEvaluated(
            List<T> fullRanking,
            boolean constraintBearing,
            Function<T, MatchState> stateProvider) {
        Objects.requireNonNull(fullRanking, "fullRanking");
        Objects.requireNonNull(stateProvider, "stateProvider");
        if (!constraintBearing) {
            return fullRanking;
        }

        List<T> satisfied = new ArrayList<>();
        List<T> unknown = new ArrayList<>();
        List<T> contradicted = new ArrayList<>();
        for (T candidate : fullRanking) {
            MatchState state = Objects.requireNonNull(stateProvider.apply(candidate), "candidate state");
            switch (state) {
                case SATISFIED -> satisfied.add(candidate);
                case UNKNOWN -> unknown.add(candidate);
                case CONTRADICTED -> contradicted.add(candidate);
            }
        }
        List<T> result = new ArrayList<>(fullRanking.size());
        result.addAll(satisfied);
        result.addAll(unknown);
        result.addAll(contradicted);
        if (result.size() != fullRanking.size()) {
            throw new IllegalStateException("typed partition changed candidate cardinality");
        }
        return List.copyOf(result);
    }
}
