package com.prizm.search.evaluation.searchv3.typed;

import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.CandidateObservation;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DateConstraint;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DateObservation;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DiagnosticReason;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.Direction;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.EvaluationResult;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.IdentifierNumberConstraint;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.IdentifierNumberObservation;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.LiteralIdentifierConstraint;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.LiteralIdentifierObservation;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.MatchState;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QuantityConstraint;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QuantityObservation;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QueryConstraint;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Three-state, ontology-free constraint matcher and deterministic reduction. */
public final class TypedConstraintEvaluator {

    public MatchState evaluate(QueryConstraint constraint, List<CandidateObservation> observations) {
        return evaluateDetailed(constraint, observations).state();
    }

    public EvaluationResult evaluateDetailed(
            QueryConstraint constraint,
            List<CandidateObservation> observations) {
        Objects.requireNonNull(constraint, "constraint");
        Objects.requireNonNull(observations, "observations");
        List<EvaluationResult> sameKind = new ArrayList<>();
        for (CandidateObservation observation : observations) {
            if (observation.kind() == constraint.kind()) {
                sameKind.add(evaluateOne(constraint, observation));
            }
        }
        if (sameKind.isEmpty()) {
            return EvaluationResult.of(MatchState.UNKNOWN, DiagnosticReason.NO_MATCHING_OBSERVATION);
        }
        if (containsState(sameKind, MatchState.SATISFIED)) {
            return mergeState(sameKind, MatchState.SATISFIED);
        }
        if (containsState(sameKind, MatchState.CONTRADICTED)) {
            return mergeState(sameKind, MatchState.CONTRADICTED);
        }
        return mergeState(sameKind, MatchState.UNKNOWN);
    }

    public MatchState evaluateAll(
            List<QueryConstraint> constraints,
            List<CandidateObservation> observations) {
        return evaluateAllDetailed(constraints, observations).state();
    }

    public EvaluationResult evaluateAllDetailed(
            List<QueryConstraint> constraints,
            List<CandidateObservation> observations) {
        Objects.requireNonNull(constraints, "constraints");
        Objects.requireNonNull(observations, "observations");
        if (constraints.isEmpty()) {
            return EvaluationResult.of(MatchState.UNKNOWN, DiagnosticReason.NO_MATCHING_OBSERVATION);
        }
        List<EvaluationResult> results = constraints.stream()
                .map(value -> evaluateDetailed(value, observations))
                .toList();
        if (containsState(results, MatchState.CONTRADICTED)) {
            return mergeState(results, MatchState.CONTRADICTED);
        }
        if (results.stream().allMatch(value -> value.state() == MatchState.SATISFIED)) {
            return mergeState(results, MatchState.SATISFIED);
        }
        return mergeState(results, MatchState.UNKNOWN);
    }

    private EvaluationResult evaluateOne(QueryConstraint constraint, CandidateObservation observation) {
        if (constraint instanceof QuantityConstraint quantity
                && observation instanceof QuantityObservation value) {
            return evaluateQuantity(quantity, value);
        }
        if (constraint instanceof DateConstraint date && observation instanceof DateObservation value) {
            return evaluateDate(date, value);
        }
        if (constraint instanceof IdentifierNumberConstraint identifier
                && observation instanceof IdentifierNumberObservation value) {
            return evaluateIdentifierNumber(identifier, value);
        }
        if (constraint instanceof LiteralIdentifierConstraint literal
                && observation instanceof LiteralIdentifierObservation value) {
            return literal.normalizedLiteral().equals(value.normalizedLiteral())
                    ? EvaluationResult.of(MatchState.SATISFIED, DiagnosticReason.MATCHED)
                    : EvaluationResult.of(MatchState.UNKNOWN, DiagnosticReason.NO_MATCHING_OBSERVATION);
        }
        return EvaluationResult.of(MatchState.UNKNOWN, DiagnosticReason.NO_MATCHING_OBSERVATION);
    }

    private EvaluationResult evaluateQuantity(QuantityConstraint constraint, QuantityObservation observation) {
        if (!constraint.normalizedUnit().equals(observation.normalizedUnit())) {
            return EvaluationResult.of(MatchState.UNKNOWN, DiagnosticReason.UNIT_MISMATCH);
        }
        if (!TypedTextSupport.qualifierCompatible(constraint.qualifier(), observation.qualifier())) {
            return EvaluationResult.of(MatchState.UNKNOWN, DiagnosticReason.QUALIFIER_MISMATCH);
        }
        Direction required = constraint.direction().direction();
        Direction actual = observation.direction().direction();
        if (required != Direction.NONE) {
            if (actual == Direction.NONE) {
                return EvaluationResult.of(MatchState.UNKNOWN, DiagnosticReason.AMBIGUOUS_OBSERVATION);
            }
            if (required != actual) {
                return EvaluationResult.of(MatchState.CONTRADICTED, DiagnosticReason.DIRECTION_MISMATCH);
            }
        }
        int lower = observation.value().compareTo(constraint.value());
        boolean satisfied = switch (constraint.operator()) {
            case EQ -> lower == 0;
            case GT -> lower > 0;
            case GTE -> lower >= 0;
            case LT -> lower < 0;
            case LTE -> lower <= 0;
            case RANGE -> lower >= 0 && observation.value().compareTo(constraint.upperValue()) <= 0;
        };
        return satisfied
                ? EvaluationResult.of(MatchState.SATISFIED, DiagnosticReason.MATCHED)
                : EvaluationResult.of(MatchState.CONTRADICTED, DiagnosticReason.VALUE_MISMATCH);
    }

    private EvaluationResult evaluateDate(DateConstraint constraint, DateObservation observation) {
        if (!TypedTextSupport.qualifierCompatible(constraint.qualifier(), observation.qualifier())) {
            return EvaluationResult.of(MatchState.UNKNOWN, DiagnosticReason.QUALIFIER_MISMATCH);
        }
        var required = constraint.interval();
        var actual = observation.interval();
        return switch (constraint.operator()) {
            case EQ, RANGE -> {
                if (!actual.startInclusive().isBefore(required.startInclusive())
                        && !actual.endInclusive().isAfter(required.endInclusive())) {
                    yield EvaluationResult.of(MatchState.SATISFIED, DiagnosticReason.MATCHED);
                }
                if (actual.endInclusive().isBefore(required.startInclusive())
                        || actual.startInclusive().isAfter(required.endInclusive())) {
                    yield EvaluationResult.of(MatchState.CONTRADICTED, DiagnosticReason.VALUE_MISMATCH);
                }
                yield EvaluationResult.of(MatchState.UNKNOWN, DiagnosticReason.AMBIGUOUS_OBSERVATION);
            }
            case GT -> {
                if (actual.startInclusive().isAfter(required.endInclusive())) {
                    yield EvaluationResult.of(MatchState.SATISFIED, DiagnosticReason.MATCHED);
                }
                if (!actual.endInclusive().isAfter(required.endInclusive())) {
                    yield EvaluationResult.of(MatchState.CONTRADICTED, DiagnosticReason.VALUE_MISMATCH);
                }
                yield EvaluationResult.of(MatchState.UNKNOWN, DiagnosticReason.AMBIGUOUS_OBSERVATION);
            }
            case GTE -> {
                if (!actual.startInclusive().isBefore(required.startInclusive())) {
                    yield EvaluationResult.of(MatchState.SATISFIED, DiagnosticReason.MATCHED);
                }
                if (actual.endInclusive().isBefore(required.startInclusive())) {
                    yield EvaluationResult.of(MatchState.CONTRADICTED, DiagnosticReason.VALUE_MISMATCH);
                }
                yield EvaluationResult.of(MatchState.UNKNOWN, DiagnosticReason.AMBIGUOUS_OBSERVATION);
            }
            case LT -> {
                if (actual.endInclusive().isBefore(required.startInclusive())) {
                    yield EvaluationResult.of(MatchState.SATISFIED, DiagnosticReason.MATCHED);
                }
                if (!actual.startInclusive().isBefore(required.startInclusive())) {
                    yield EvaluationResult.of(MatchState.CONTRADICTED, DiagnosticReason.VALUE_MISMATCH);
                }
                yield EvaluationResult.of(MatchState.UNKNOWN, DiagnosticReason.AMBIGUOUS_OBSERVATION);
            }
        };
    }

    private EvaluationResult evaluateIdentifierNumber(
            IdentifierNumberConstraint constraint,
            IdentifierNumberObservation observation) {
        if (!constraint.normalizedIdentifier().equals(observation.normalizedIdentifier())) {
            return EvaluationResult.of(MatchState.UNKNOWN, DiagnosticReason.NO_MATCHING_OBSERVATION);
        }
        return constraint.normalizedSegments().equals(observation.normalizedSegments())
                ? EvaluationResult.of(MatchState.SATISFIED, DiagnosticReason.MATCHED)
                : EvaluationResult.of(MatchState.CONTRADICTED, DiagnosticReason.VALUE_MISMATCH);
    }

    private boolean containsState(List<EvaluationResult> results, MatchState state) {
        return results.stream().anyMatch(value -> value.state() == state);
    }

    private EvaluationResult mergeState(List<EvaluationResult> results, MatchState state) {
        List<DiagnosticReason> reasons = results.stream()
                .filter(value -> value.state() == state)
                .flatMap(value -> value.reasons().stream())
                .toList();
        if (reasons.isEmpty()) {
            throw new IllegalStateException("evaluation reduction selected a state without reasons");
        }
        return new EvaluationResult(state, reasons);
    }
}
