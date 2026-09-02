package com.prizm.search.v3.query.service;

import com.prizm.search.v3.query.model.SearchV3EvidenceChildCandidate;
import com.prizm.search.v3.query.model.SearchV3PassageCandidate;
import com.prizm.search.v3.query.model.SearchV3TypedEvidenceState;
import com.prizm.search.v3.query.typed.DeterministicTypedObservationExtractor;
import com.prizm.search.v3.query.typed.DeterministicTypedQueryParser;
import com.prizm.search.v3.query.typed.TypedConstraintEvaluator;
import com.prizm.search.v3.query.typed.TypedValueModel.CandidateObservation;
import com.prizm.search.v3.query.typed.TypedValueModel.DiagnosticReason;
import com.prizm.search.v3.query.typed.TypedValueModel.EvaluationResult;
import com.prizm.search.v3.query.typed.TypedValueModel.MatchState;
import com.prizm.search.v3.query.typed.TypedValueModel.QueryConstraint;
import com.prizm.search.v3.query.typed.TypedValueModel.SourceSlice;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

/** PRZ-028/029 three-state 조건 검증과 source-grounded 근거 선택을 runtime에 적용한다. */
@Component
public class SearchV3TypedEvidenceSelector {

    private static final int RESULT_LIMIT = 5;

    private final DeterministicTypedQueryParser queryParser = new DeterministicTypedQueryParser();
    private final DeterministicTypedObservationExtractor observationExtractor =
            new DeterministicTypedObservationExtractor();
    private final TypedConstraintEvaluator evaluator = new TypedConstraintEvaluator();

    Selection select(String query, List<RankedPassage> passages) {
        Objects.requireNonNull(query, "query");
        List<QueryConstraint> constraints = queryParser.parse(query);
        List<PreparedPassage> prepared = passages.stream().map(this::prepare).toList();
        if (constraints.isEmpty()) {
            return new Selection(
                    SearchV3TypedEvidenceState.UNASSESSED,
                    0,
                    baseline(prepared));
        }

        List<PassageValidation> validations = prepared.stream()
                .map(passage -> validate(passage, constraints))
                .toList();
        SearchV3TypedEvidenceState state = reduceState(validations);
        return new Selection(state, constraints.size(), selectForState(state, validations));
    }

    private PreparedPassage prepare(RankedPassage passage) {
        List<PreparedChild> children = passage.children().stream().map(child -> {
            SourceSlice source = new SourceSlice(
                    Long.toString(child.child().documentId()),
                    Long.toString(child.child().documentVersionId()),
                    child.child().childKey(),
                    child.child().pageNo(),
                    child.child().codePointStart(),
                    child.child().sourceText());
            return new PreparedChild(child, observationExtractor.extract(source));
        }).toList();
        return new PreparedPassage(passage.passage(), children);
    }

    private PassageValidation validate(
            PreparedPassage passage,
            List<QueryConstraint> constraints) {
        List<CandidateObservation> observations = passage.children().stream()
                .flatMap(child -> child.observations().stream())
                .toList();
        List<ConstraintValidation> passageConstraints = constraints.stream()
                .map(constraint -> new ConstraintValidation(
                        constraint,
                        evaluator.evaluateDetailed(constraint, observations)))
                .toList();
        List<ChildValidation> children = passage.children().stream()
                .map(child -> validateChild(child, constraints))
                .toList();
        return new PassageValidation(
                passage.passage(),
                priorityResult(passageConstraints),
                passageConstraints,
                children);
    }

    private ChildValidation validateChild(
            PreparedChild child,
            List<QueryConstraint> constraints) {
        List<ConstraintValidation> results = constraints.stream()
                .map(constraint -> new ConstraintValidation(
                        constraint,
                        evaluator.evaluateDetailed(constraint, child.observations())))
                .toList();
        return new ChildValidation(child.child(), priorityResult(results), results);
    }

    private SearchV3TypedEvidenceState reduceState(List<PassageValidation> validations) {
        if (validations.stream().anyMatch(value -> value.result().state() == MatchState.SATISFIED)) {
            return SearchV3TypedEvidenceState.FOUND;
        }
        if (validations.stream().anyMatch(this::isRelatedUnknown)) {
            return SearchV3TypedEvidenceState.PARTIAL;
        }
        if (validations.stream().anyMatch(value -> value.result().state() == MatchState.CONTRADICTED)) {
            return SearchV3TypedEvidenceState.NONE;
        }
        return SearchV3TypedEvidenceState.PARTIAL;
    }

    private EvaluationResult priorityResult(List<ConstraintValidation> constraints) {
        if (constraints.stream().allMatch(value -> value.result().state() == MatchState.SATISFIED)) {
            return EvaluationResult.of(MatchState.SATISFIED, DiagnosticReason.MATCHED);
        }
        EnumSet<DiagnosticReason> unknown = reasons(constraints, MatchState.UNKNOWN);
        if (!unknown.isEmpty()) {
            return new EvaluationResult(MatchState.UNKNOWN, List.copyOf(unknown));
        }
        return new EvaluationResult(
                MatchState.CONTRADICTED,
                List.copyOf(reasons(constraints, MatchState.CONTRADICTED)));
    }

    private EnumSet<DiagnosticReason> reasons(
            List<ConstraintValidation> constraints,
            MatchState state) {
        EnumSet<DiagnosticReason> reasons = EnumSet.noneOf(DiagnosticReason.class);
        constraints.stream()
                .filter(value -> value.result().state() == state)
                .flatMap(value -> value.result().reasons().stream())
                .forEach(reasons::add);
        return reasons;
    }

    private boolean isRelatedUnknown(PassageValidation validation) {
        return validation.result().state() == MatchState.UNKNOWN
                && isRelatedUnknown(validation.constraints());
    }

    private boolean isRelatedUnknown(List<ConstraintValidation> constraints) {
        return constraints.stream().anyMatch(value ->
                value.result().state() != MatchState.UNKNOWN
                        || value.result().reasons().contains(DiagnosticReason.AMBIGUOUS_OBSERVATION));
    }

    private List<SelectedChild> baseline(List<PreparedPassage> passages) {
        List<SelectedChild> selected = new ArrayList<>();
        Set<SourceIdentity> seen = new LinkedHashSet<>();
        for (PreparedPassage passage : passages) {
            for (PreparedChild child : passage.children()) {
                if (seen.add(identity(child.child().child()))) {
                    selected.add(new SelectedChild(
                            passage.passage(), child.child(), null, List.of()));
                    if (selected.size() == RESULT_LIMIT) return List.copyOf(selected);
                }
            }
        }
        return List.copyOf(selected);
    }

    private List<SelectedChild> selectForState(
            SearchV3TypedEvidenceState state,
            List<PassageValidation> validations) {
        MatchState required = switch (state) {
            case FOUND -> MatchState.SATISFIED;
            case PARTIAL -> MatchState.UNKNOWN;
            case NONE -> MatchState.CONTRADICTED;
            case UNASSESSED -> throw new IllegalArgumentException("UNASSESSED uses baseline selection.");
        };
        boolean requireRelatedUnknown = state == SearchV3TypedEvidenceState.PARTIAL
                && validations.stream().anyMatch(this::isRelatedUnknown);
        List<SelectedChild> selected = new ArrayList<>();
        Set<SourceIdentity> seen = new LinkedHashSet<>();
        for (PassageValidation passage : validations) {
            if (passage.result().state() != required
                    || (requireRelatedUnknown && !isRelatedUnknown(passage))) {
                continue;
            }
            for (ChildValidation child : contributingChildren(state, passage.children(), requireRelatedUnknown)) {
                if (seen.add(identity(child.child().child()))) {
                    selected.add(new SelectedChild(
                            passage.passage(), child.child(), child.result(), child.constraints()));
                    if (selected.size() == RESULT_LIMIT) return List.copyOf(selected);
                }
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalStateException("Typed state has no source-grounded contributing Child: " + state);
        }
        return List.copyOf(selected);
    }

    private List<ChildValidation> contributingChildren(
            SearchV3TypedEvidenceState state,
            List<ChildValidation> children,
            boolean requireRelatedUnknown) {
        if (state != SearchV3TypedEvidenceState.FOUND) {
            MatchState required = state == SearchV3TypedEvidenceState.PARTIAL
                    ? MatchState.UNKNOWN : MatchState.CONTRADICTED;
            return children.stream()
                    .filter(value -> value.result().state() == required)
                    .filter(value -> !requireRelatedUnknown || isRelatedUnknown(value.constraints()))
                    .toList();
        }
        List<ChildValidation> complete = children.stream()
                .filter(value -> value.result().state() == MatchState.SATISFIED)
                .toList();
        if (!complete.isEmpty()) return complete;

        List<ChildValidation> contributors = new ArrayList<>();
        int constraintCount = children.get(0).constraints().size();
        for (int index = 0; index < constraintCount; index++) {
            ChildValidation contributor = null;
            for (ChildValidation child : children) {
                if (child.constraints().get(index).result().state() == MatchState.SATISFIED
                        && child.constraints().stream().noneMatch(value ->
                                value.result().state() == MatchState.CONTRADICTED)) {
                    contributor = child;
                    break;
                }
            }
            if (contributor == null) {
                for (ChildValidation child : children) {
                    if (child.constraints().get(index).result().state() == MatchState.SATISFIED) {
                        contributor = child;
                        break;
                    }
                }
            }
            if (contributor == null) {
                throw new IllegalStateException("SATISFIED Passage lacks an atomic constraint contributor.");
            }
            if (!contributors.contains(contributor)) contributors.add(contributor);
        }
        return List.copyOf(contributors);
    }

    private static SourceIdentity identity(SearchV3EvidenceChildCandidate child) {
        return new SourceIdentity(
                child.documentId(), child.documentVersionId(), child.pageNo(),
                child.codePointStart(), child.codePointEnd());
    }

    record RankedPassage(
            SearchV3PassageCandidate passage,
            List<RankedChild> children) {
        RankedPassage {
            children = List.copyOf(children);
            if (children.isEmpty()) throw new IllegalArgumentException("Passage has no EvidenceChild.");
        }
    }

    record RankedChild(SearchV3EvidenceChildCandidate child, Double cosineScore) {
    }

    record Selection(
            SearchV3TypedEvidenceState state,
            int parsedConstraintCount,
            List<SelectedChild> children) {
        Selection {
            children = List.copyOf(children);
        }
    }

    record SelectedChild(
            SearchV3PassageCandidate passage,
            RankedChild child,
            EvaluationResult typedResult,
            List<ConstraintValidation> constraintTrace) {
        SelectedChild {
            constraintTrace = List.copyOf(constraintTrace);
        }
    }

    private record PreparedPassage(
            SearchV3PassageCandidate passage,
            List<PreparedChild> children) {
    }

    private record PreparedChild(RankedChild child, List<CandidateObservation> observations) {
    }

    private record ConstraintValidation(QueryConstraint constraint, EvaluationResult result) {
    }

    private record ChildValidation(
            RankedChild child,
            EvaluationResult result,
            List<ConstraintValidation> constraints) {
    }

    private record PassageValidation(
            SearchV3PassageCandidate passage,
            EvaluationResult result,
            List<ConstraintValidation> constraints,
            List<ChildValidation> children) {
    }

    private record SourceIdentity(long documentId, long versionId, Integer page, int start, int end) {
    }
}
