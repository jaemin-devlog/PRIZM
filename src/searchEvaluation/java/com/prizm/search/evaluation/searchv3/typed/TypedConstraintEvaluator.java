package com.prizm.search.evaluation.searchv3.typed;

import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.CandidateObservation;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DateConstraint;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DateObservation;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.Direction;
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
        Objects.requireNonNull(constraint, "constraint");
        Objects.requireNonNull(observations, "observations");
        List<MatchState> compatible = new ArrayList<>();
        for (CandidateObservation observation : observations) {
            if (observation.kind() == constraint.kind()) {
                compatible.add(evaluateOne(constraint, observation));
            }
        }
        if (compatible.contains(MatchState.SATISFIED)) {
            return MatchState.SATISFIED;
        }
        if (compatible.contains(MatchState.CONTRADICTED)) {
            return MatchState.CONTRADICTED;
        }
        return MatchState.UNKNOWN;
    }

    public MatchState evaluateAll(
            List<QueryConstraint> constraints,
            List<CandidateObservation> observations) {
        Objects.requireNonNull(constraints, "constraints");
        Objects.requireNonNull(observations, "observations");
        if (constraints.isEmpty()) {
            return MatchState.UNKNOWN;
        }
        List<MatchState> states = constraints.stream().map(value -> evaluate(value, observations)).toList();
        if (states.contains(MatchState.CONTRADICTED)) {
            return MatchState.CONTRADICTED;
        }
        return states.stream().allMatch(value -> value == MatchState.SATISFIED)
                ? MatchState.SATISFIED
                : MatchState.UNKNOWN;
    }

    private MatchState evaluateOne(QueryConstraint constraint, CandidateObservation observation) {
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
                    ? MatchState.SATISFIED
                    : MatchState.UNKNOWN;
        }
        return MatchState.UNKNOWN;
    }

    private MatchState evaluateQuantity(QuantityConstraint constraint, QuantityObservation observation) {
        if (!constraint.normalizedUnit().equals(observation.normalizedUnit())
                || !sameQualifier(constraint.qualifier(), observation.qualifier())) {
            return MatchState.UNKNOWN;
        }
        Direction required = constraint.direction().direction();
        Direction actual = observation.direction().direction();
        if (required != Direction.NONE) {
            if (actual == Direction.NONE) {
                return MatchState.UNKNOWN;
            }
            if (required != actual) {
                return MatchState.CONTRADICTED;
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
        return satisfied ? MatchState.SATISFIED : MatchState.CONTRADICTED;
    }

    private MatchState evaluateDate(DateConstraint constraint, DateObservation observation) {
        if (!sameQualifier(constraint.qualifier(), observation.qualifier())) {
            return MatchState.UNKNOWN;
        }
        var required = constraint.interval();
        var actual = observation.interval();
        return switch (constraint.operator()) {
            case EQ, RANGE -> {
                if (!actual.startInclusive().isBefore(required.startInclusive())
                        && !actual.endInclusive().isAfter(required.endInclusive())) {
                    yield MatchState.SATISFIED;
                }
                if (actual.endInclusive().isBefore(required.startInclusive())
                        || actual.startInclusive().isAfter(required.endInclusive())) {
                    yield MatchState.CONTRADICTED;
                }
                yield MatchState.UNKNOWN;
            }
            case GT -> {
                if (actual.startInclusive().isAfter(required.endInclusive())) {
                    yield MatchState.SATISFIED;
                }
                if (!actual.endInclusive().isAfter(required.endInclusive())) {
                    yield MatchState.CONTRADICTED;
                }
                yield MatchState.UNKNOWN;
            }
            case GTE -> {
                if (!actual.startInclusive().isBefore(required.startInclusive())) {
                    yield MatchState.SATISFIED;
                }
                if (actual.endInclusive().isBefore(required.startInclusive())) {
                    yield MatchState.CONTRADICTED;
                }
                yield MatchState.UNKNOWN;
            }
            case LT -> {
                if (actual.endInclusive().isBefore(required.startInclusive())) {
                    yield MatchState.SATISFIED;
                }
                if (!actual.startInclusive().isBefore(required.startInclusive())) {
                    yield MatchState.CONTRADICTED;
                }
                yield MatchState.UNKNOWN;
            }
        };
    }

    private MatchState evaluateIdentifierNumber(
            IdentifierNumberConstraint constraint,
            IdentifierNumberObservation observation) {
        if (!constraint.normalizedIdentifier().equals(observation.normalizedIdentifier())) {
            return MatchState.UNKNOWN;
        }
        return constraint.normalizedSegments().equals(observation.normalizedSegments())
                ? MatchState.SATISFIED
                : MatchState.CONTRADICTED;
    }

    private boolean sameQualifier(TypedValueModel.Qualifier left, TypedValueModel.Qualifier right) {
        boolean leftEmpty = left.normalized().isBlank();
        boolean rightEmpty = right.normalized().isBlank();
        if (leftEmpty || rightEmpty) {
            return leftEmpty == rightEmpty;
        }
        return left.normalized().equals(right.normalized())
                && left.orderedTokens().equals(right.orderedTokens());
    }
}
