package com.prizm.search.evaluation.searchv3.typed;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;

/** Evaluation-only, source-grounded values used by deterministic typed matching. */
public final class TypedValueModel {

    private TypedValueModel() {
    }

    public enum Kind {
        QUANTITY,
        DATE,
        IDENTIFIER_NUMBER,
        LITERAL_IDENTIFIER
    }

    public enum MatchState {
        SATISFIED,
        CONTRADICTED,
        UNKNOWN
    }

    /** Deterministic explanation for a three-state evaluation; never a ranking signal. */
    public enum DiagnosticReason {
        MATCHED,
        VALUE_MISMATCH,
        DIRECTION_MISMATCH,
        QUALIFIER_MISMATCH,
        UNIT_MISMATCH,
        NO_MATCHING_OBSERVATION,
        AMBIGUOUS_OBSERVATION
    }

    /** State plus deterministic, de-duplicated reasons in enum declaration order. */
    public record EvaluationResult(MatchState state, List<DiagnosticReason> reasons) {
        public EvaluationResult {
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(reasons, "reasons");
            EnumSet<DiagnosticReason> ordered = EnumSet.noneOf(DiagnosticReason.class);
            for (DiagnosticReason reason : reasons) {
                ordered.add(Objects.requireNonNull(reason, "diagnostic reason"));
            }
            if (ordered.isEmpty()) {
                throw new IllegalArgumentException("evaluation result requires at least one diagnostic reason");
            }
            if (state == MatchState.SATISFIED && !ordered.equals(EnumSet.of(DiagnosticReason.MATCHED))) {
                throw new IllegalArgumentException("SATISFIED must contain only MATCHED");
            }
            if (state != MatchState.SATISFIED && ordered.contains(DiagnosticReason.MATCHED)) {
                throw new IllegalArgumentException("only SATISFIED may contain MATCHED");
            }
            reasons = List.copyOf(ordered);
        }

        public static EvaluationResult of(MatchState state, DiagnosticReason reason) {
            return new EvaluationResult(state, List.of(reason));
        }
    }

    public enum QuantityOperator {
        EQ,
        GT,
        GTE,
        LT,
        LTE,
        RANGE
    }

    public enum DateOperator {
        EQ,
        GT,
        GTE,
        LT,
        RANGE
    }

    public enum Direction {
        NONE,
        DECREASE,
        INCREASE
    }

    public enum DatePrecision {
        YEAR,
        YEAR_MONTH,
        FULL_DATE
    }

    /** Unicode code-point [start, end) coordinates in the owning query or document. */
    public record CodePointSpan(String surface, int startInclusive, int endExclusive) {
        public CodePointSpan {
            Objects.requireNonNull(surface, "surface");
            if (surface.isBlank() || startInclusive < 0 || endExclusive <= startInclusive) {
                throw new IllegalArgumentException("source span must be non-blank and ordered");
            }
            if (surface.codePointCount(0, surface.length()) != endExclusive - startInclusive) {
                throw new IllegalArgumentException("surface length must equal its code-point range");
            }
        }
    }

    /** A normalized qualifier and the exact source phrase from which it was derived. */
    public record Qualifier(String normalized, List<String> orderedTokens, CodePointSpan span) {
        public Qualifier {
            Objects.requireNonNull(normalized, "normalized");
            Objects.requireNonNull(orderedTokens, "orderedTokens");
            orderedTokens = List.copyOf(orderedTokens);
            if (normalized.isBlank()) {
                if (!orderedTokens.isEmpty() || span != null) {
                    throw new IllegalArgumentException("empty qualifier cannot retain tokens or a span");
                }
            }
            else if (orderedTokens.isEmpty() || span == null) {
                throw new IllegalArgumentException("non-empty qualifier requires ordered tokens and a span");
            }
        }

        public static Qualifier empty() {
            return new Qualifier("", List.of(), null);
        }
    }

    /** Direction is independently grounded; NONE intentionally has no source span. */
    public record DirectionMark(Direction direction, CodePointSpan span) {
        public DirectionMark {
            Objects.requireNonNull(direction, "direction");
            if ((direction == Direction.NONE) != (span == null)) {
                throw new IllegalArgumentException("only an explicit direction may retain a span");
            }
        }

        public static DirectionMark none() {
            return new DirectionMark(Direction.NONE, null);
        }
    }

    /** An interval derived only from the precision explicitly present in source text. */
    public record DateInterval(
            LocalDate startInclusive,
            LocalDate endInclusive,
            DatePrecision precision) {
        public DateInterval {
            Objects.requireNonNull(startInclusive, "startInclusive");
            Objects.requireNonNull(endInclusive, "endInclusive");
            Objects.requireNonNull(precision, "precision");
            if (endInclusive.isBefore(startInclusive)) {
                throw new IllegalArgumentException("date interval must be ordered");
            }
        }
    }

    /** A contiguous atomic source segment; never a synthetic RetrievalPassage join. */
    public record SourceSlice(
            String documentId,
            String versionId,
            String sourceKey,
            Integer page,
            int documentCodePointStart,
            String sourceText) {
        public SourceSlice {
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(versionId, "versionId");
            Objects.requireNonNull(sourceKey, "sourceKey");
            Objects.requireNonNull(sourceText, "sourceText");
            if (documentCodePointStart < 0 || sourceText.isBlank()) {
                throw new IllegalArgumentException("source slice must be non-blank with a valid origin");
            }
        }
    }

    public sealed interface QueryConstraint permits QuantityConstraint, DateConstraint,
            IdentifierNumberConstraint, LiteralIdentifierConstraint {
        Kind kind();

        CodePointSpan span();
    }

    public sealed interface CandidateObservation permits QuantityObservation, DateObservation,
            IdentifierNumberObservation, LiteralIdentifierObservation {
        Kind kind();

        CodePointSpan span();

        SourceSlice source();
    }

    public record QuantityConstraint(
            CodePointSpan span,
            QuantityOperator operator,
            BigDecimal value,
            BigDecimal upperValue,
            String normalizedUnit,
            Qualifier qualifier,
            DirectionMark direction) implements QueryConstraint {
        public QuantityConstraint {
            Objects.requireNonNull(span, "span");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(normalizedUnit, "normalizedUnit");
            Objects.requireNonNull(qualifier, "qualifier");
            Objects.requireNonNull(direction, "direction");
            if (normalizedUnit.isBlank()) {
                throw new IllegalArgumentException("quantity unit cannot be blank");
            }
            if (operator == QuantityOperator.RANGE) {
                if (upperValue == null || upperValue.compareTo(value) < 0) {
                    throw new IllegalArgumentException("quantity range must have an ordered upper value");
                }
            }
            else if (upperValue != null) {
                throw new IllegalArgumentException("only RANGE may have an upper value");
            }
        }

        @Override
        public Kind kind() {
            return Kind.QUANTITY;
        }
    }

    public record QuantityObservation(
            CodePointSpan span,
            BigDecimal value,
            String normalizedUnit,
            Qualifier qualifier,
            DirectionMark direction,
            SourceSlice source) implements CandidateObservation {
        public QuantityObservation {
            Objects.requireNonNull(span, "span");
            Objects.requireNonNull(value, "value");
            Objects.requireNonNull(normalizedUnit, "normalizedUnit");
            Objects.requireNonNull(qualifier, "qualifier");
            Objects.requireNonNull(direction, "direction");
            Objects.requireNonNull(source, "source");
            if (normalizedUnit.isBlank()) {
                throw new IllegalArgumentException("quantity unit cannot be blank");
            }
        }

        @Override
        public Kind kind() {
            return Kind.QUANTITY;
        }
    }

    public record DateConstraint(
            CodePointSpan span,
            DateOperator operator,
            DateInterval interval,
            Qualifier qualifier) implements QueryConstraint {
        public DateConstraint {
            Objects.requireNonNull(span, "span");
            Objects.requireNonNull(operator, "operator");
            Objects.requireNonNull(interval, "interval");
            Objects.requireNonNull(qualifier, "qualifier");
            if (operator == DateOperator.RANGE && interval.startInclusive().equals(interval.endInclusive())) {
                throw new IllegalArgumentException("date RANGE must contain more than one day");
            }
        }

        @Override
        public Kind kind() {
            return Kind.DATE;
        }
    }

    public record DateObservation(
            CodePointSpan span,
            DateInterval interval,
            Qualifier qualifier,
            SourceSlice source) implements CandidateObservation {
        public DateObservation {
            Objects.requireNonNull(span, "span");
            Objects.requireNonNull(interval, "interval");
            Objects.requireNonNull(qualifier, "qualifier");
            Objects.requireNonNull(source, "source");
        }

        @Override
        public Kind kind() {
            return Kind.DATE;
        }
    }

    public record IdentifierNumberConstraint(
            CodePointSpan span,
            String identifierSurface,
            String normalizedIdentifier,
            String numberSurface,
            List<BigInteger> normalizedSegments) implements QueryConstraint {
        public IdentifierNumberConstraint {
            Objects.requireNonNull(span, "span");
            Objects.requireNonNull(identifierSurface, "identifierSurface");
            Objects.requireNonNull(normalizedIdentifier, "normalizedIdentifier");
            Objects.requireNonNull(numberSurface, "numberSurface");
            Objects.requireNonNull(normalizedSegments, "normalizedSegments");
            normalizedSegments = List.copyOf(normalizedSegments);
            if (normalizedIdentifier.isBlank() || normalizedSegments.isEmpty()) {
                throw new IllegalArgumentException("identifier-number must retain both parts");
            }
        }

        @Override
        public Kind kind() {
            return Kind.IDENTIFIER_NUMBER;
        }
    }

    public record IdentifierNumberObservation(
            CodePointSpan span,
            String identifierSurface,
            String normalizedIdentifier,
            String numberSurface,
            List<BigInteger> normalizedSegments,
            SourceSlice source) implements CandidateObservation {
        public IdentifierNumberObservation {
            Objects.requireNonNull(span, "span");
            Objects.requireNonNull(identifierSurface, "identifierSurface");
            Objects.requireNonNull(normalizedIdentifier, "normalizedIdentifier");
            Objects.requireNonNull(numberSurface, "numberSurface");
            Objects.requireNonNull(normalizedSegments, "normalizedSegments");
            Objects.requireNonNull(source, "source");
            normalizedSegments = List.copyOf(normalizedSegments);
            if (normalizedIdentifier.isBlank() || normalizedSegments.isEmpty()) {
                throw new IllegalArgumentException("identifier-number must retain both parts");
            }
        }

        @Override
        public Kind kind() {
            return Kind.IDENTIFIER_NUMBER;
        }
    }

    public record LiteralIdentifierConstraint(
            CodePointSpan span,
            String normalizedLiteral) implements QueryConstraint {
        public LiteralIdentifierConstraint {
            Objects.requireNonNull(span, "span");
            Objects.requireNonNull(normalizedLiteral, "normalizedLiteral");
            if (normalizedLiteral.isBlank()) {
                throw new IllegalArgumentException("literal identifier cannot be blank");
            }
        }

        @Override
        public Kind kind() {
            return Kind.LITERAL_IDENTIFIER;
        }
    }

    public record LiteralIdentifierObservation(
            CodePointSpan span,
            String normalizedLiteral,
            SourceSlice source) implements CandidateObservation {
        public LiteralIdentifierObservation {
            Objects.requireNonNull(span, "span");
            Objects.requireNonNull(normalizedLiteral, "normalizedLiteral");
            Objects.requireNonNull(source, "source");
            if (normalizedLiteral.isBlank()) {
                throw new IllegalArgumentException("literal identifier cannot be blank");
            }
        }

        @Override
        public Kind kind() {
            return Kind.LITERAL_IDENTIFIER;
        }
    }
}
