package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.search.evaluation.searchv3.typed.DeterministicTypedObservationExtractor;
import com.prizm.search.evaluation.searchv3.typed.DeterministicTypedQueryParser;
import com.prizm.search.evaluation.searchv3.typed.TypedConstraintEvaluator;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.CandidateObservation;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DiagnosticReason;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.EvaluationResult;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.MatchState;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QueryConstraint;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.SourceSlice;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Evaluation-only adapter that validates B3 passages before selecting atomic evidence.
 *
 * <p>The adapter accepts source-only DTOs. It cannot receive retrieval text, headings, answerability,
 * categories, Gold IDs, or runtime database IDs.
 */
final class EvidenceValidationSelector {

    static final int VALIDATION_CANDIDATE_K = 20;
    static final int MAX_SELECTED_EVIDENCE = 5;

    private final DeterministicTypedQueryParser queryParser;
    private final DeterministicTypedObservationExtractor observationExtractor;
    private final TypedConstraintEvaluator evaluator;

    EvidenceValidationSelector() {
        this(
                new DeterministicTypedQueryParser(),
                new DeterministicTypedObservationExtractor(),
                new TypedConstraintEvaluator());
    }

    EvidenceValidationSelector(
            DeterministicTypedQueryParser queryParser,
            DeterministicTypedObservationExtractor observationExtractor,
            TypedConstraintEvaluator evaluator) {
        this.queryParser = Objects.requireNonNull(queryParser, "queryParser");
        this.observationExtractor = Objects.requireNonNull(observationExtractor, "observationExtractor");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    PreparedCorpus prepare(List<SourceCandidate> candidates) {
        Objects.requireNonNull(candidates, "candidates");
        Map<String, PreparedCandidate> byId = new LinkedHashMap<>();
        Set<String> allChildIds = new HashSet<>();
        Set<SourceIdentity> allSourceSpans = new HashSet<>();
        long started = System.nanoTime();
        long observationCount = 0L;
        long payloadBytes = 0L;
        for (SourceCandidate candidate : candidates) {
            List<PreparedChild> children = new ArrayList<>();
            for (AtomicEvidence child : candidate.children()) {
                SourceProvenance provenance = child.provenance();
                SourceIdentity identity = new SourceIdentity(
                        provenance.documentId(), provenance.versionId(), provenance.page(),
                        provenance.codePointStart(), provenance.codePointEnd());
                if (!allChildIds.add(child.childId()) || !allSourceSpans.add(identity)) {
                    throw new IllegalArgumentException(
                            "atomic evidence appears in multiple B3 passages: " + child.childId());
                }
                SourceSlice source = new SourceSlice(
                        provenance.documentId(),
                        provenance.versionId(),
                        child.childId(),
                        provenance.page(),
                        provenance.codePointStart(),
                        child.sourceText());
                List<CandidateObservation> observations = observationExtractor.extract(source);
                observationCount += observations.size();
                payloadBytes += child.sourceText().getBytes(StandardCharsets.UTF_8).length;
                children.add(new PreparedChild(child, observations));
            }
            PreparedCandidate prepared = new PreparedCandidate(candidate, children);
            if (byId.put(candidate.candidateId(), prepared) != null) {
                throw new IllegalArgumentException("duplicate source candidate: " + candidate.candidateId());
            }
        }
        return new PreparedCorpus(
                Map.copyOf(byId),
                nanosToMillis(System.nanoTime() - started),
                observationCount,
                payloadBytes);
    }

    ParsedQuery parse(String queryId, String text) {
        Objects.requireNonNull(queryId, "queryId");
        Objects.requireNonNull(text, "text");
        long started = System.nanoTime();
        List<QueryConstraint> constraints = queryParser.parse(text);
        return new ParsedQuery(
                queryId,
                text,
                constraints,
                nanosToMillis(System.nanoTime() - started));
    }

    SelectionResult select(
            PreparedCorpus corpus,
            ParsedQuery query,
            String userBundleId,
            boolean fullTypedApplicabilityVerified,
            List<DenseCandidate> denseRanking) {
        Objects.requireNonNull(corpus, "corpus");
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(userBundleId, "userBundleId");
        Objects.requireNonNull(denseRanking, "denseRanking");
        validateRanking(corpus, query.queryId(), userBundleId, denseRanking);

        List<SelectedEvidence> baseline = baselineSelection(corpus, denseRanking);
        if (!fullTypedApplicabilityVerified || query.constraints().isEmpty()) {
            return new SelectionResult(
                    TypedEvidenceState.UNASSESSED,
                    false,
                    query.constraints().size(),
                    denseRanking.stream().map(DenseCandidate::candidateId).toList(),
                    List.of(),
                    baseline,
                    baseline,
                    query.parseLatencyMs(),
                    0.0d,
                    0.0d);
        }

        long validationStarted = System.nanoTime();
        List<CandidateValidation> validations = new ArrayList<>();
        int shortlistSize = Math.min(VALIDATION_CANDIDATE_K, denseRanking.size());
        for (int index = 0; index < shortlistSize; index++) {
            long candidateStarted = System.nanoTime();
            DenseCandidate dense = denseRanking.get(index);
            PreparedCandidate candidate = requiredCandidate(corpus, dense.candidateId());
            List<CandidateObservation> passageObservations = candidate.children().stream()
                    .flatMap(child -> child.observations().stream())
                    .toList();
            List<ConstraintValidation> passageConstraints = query.constraints().stream()
                    .map(constraint -> new ConstraintValidation(
                            constraint,
                            evaluator.evaluateDetailed(constraint, passageObservations)))
                    .toList();
            EvaluationResult passage = priorityResult(passageConstraints);
            List<ChildValidation> children = candidate.children().stream()
                    .map(child -> validateChild(query.constraints(), child))
                    .toList();
            validations.add(new CandidateValidation(
                    dense,
                    passage,
                    passageConstraints,
                    children,
                    nanosToMillis(System.nanoTime() - candidateStarted)));
        }
        double validationLatency = nanosToMillis(System.nanoTime() - validationStarted);

        long selectionStarted = System.nanoTime();
        TypedEvidenceState state = reduceState(validations);
        List<SelectedEvidence> selected = selectForState(state, validations);
        double selectionLatency = nanosToMillis(System.nanoTime() - selectionStarted);
        return new SelectionResult(
                state,
                true,
                query.constraints().size(),
                denseRanking.stream().map(DenseCandidate::candidateId).toList(),
                validations,
                baseline,
                selected,
                query.parseLatencyMs(),
                validationLatency,
                selectionLatency);
    }

    private ChildValidation validateChild(List<QueryConstraint> constraints, PreparedChild child) {
        List<ConstraintValidation> perConstraint = constraints.stream()
                .map(constraint -> new ConstraintValidation(
                        constraint,
                        evaluator.evaluateDetailed(constraint, child.observations())))
                .toList();
        return new ChildValidation(
                child.source(),
                priorityResult(perConstraint),
                perConstraint);
    }

    private TypedEvidenceState reduceState(List<CandidateValidation> validations) {
        if (validations.stream().anyMatch(value -> value.result().state() == MatchState.SATISFIED)) {
            return TypedEvidenceState.FOUND;
        }
        if (validations.stream().anyMatch(this::isRelatedUnknown)) {
            return TypedEvidenceState.PARTIAL;
        }
        if (validations.stream().anyMatch(value -> value.result().state() == MatchState.CONTRADICTED)) {
            return TypedEvidenceState.NONE;
        }
        // Absence of a matching typed observation is uncertainty, never evidence of absence.
        return TypedEvidenceState.PARTIAL;
    }

    private EvaluationResult priorityResult(List<ConstraintValidation> constraints) {
        if (constraints.stream().allMatch(value -> value.result().state() == MatchState.SATISFIED)) {
            return EvaluationResult.of(MatchState.SATISFIED, DiagnosticReason.MATCHED);
        }
        List<DiagnosticReason> unknownReasons = constraints.stream()
                .filter(value -> value.result().state() == MatchState.UNKNOWN)
                .flatMap(value -> value.result().reasons().stream())
                .toList();
        if (!unknownReasons.isEmpty()) {
            return new EvaluationResult(MatchState.UNKNOWN, unknownReasons);
        }
        List<DiagnosticReason> contradictedReasons = constraints.stream()
                .filter(value -> value.result().state() == MatchState.CONTRADICTED)
                .flatMap(value -> value.result().reasons().stream())
                .toList();
        return new EvaluationResult(MatchState.CONTRADICTED, contradictedReasons);
    }

    private boolean isRelatedUnknown(CandidateValidation validation) {
        return validation.result().state() == MatchState.UNKNOWN
                && isRelatedUnknown(validation.constraints());
    }

    private boolean isRelatedUnknown(List<ConstraintValidation> constraints) {
        return constraints.stream().anyMatch(value ->
                value.result().state() != MatchState.UNKNOWN
                        || value.result().reasons().stream()
                                .anyMatch(reason -> reason == DiagnosticReason.AMBIGUOUS_OBSERVATION));
    }

    private List<SelectedEvidence> baselineSelection(
            PreparedCorpus corpus,
            List<DenseCandidate> denseRanking) {
        List<SelectedEvidence> selected = new ArrayList<>();
        Set<String> seenChildren = new LinkedHashSet<>();
        Set<SourceIdentity> seenSpans = new LinkedHashSet<>();
        for (DenseCandidate dense : denseRanking) {
            PreparedCandidate candidate = requiredCandidate(corpus, dense.candidateId());
            for (PreparedChild child : candidate.children()) {
                if (addIfUnique(seenChildren, seenSpans, child.source())) {
                    selected.add(toSelected(
                            selected.size() + 1, dense, child.source(), null, List.of()));
                    if (selected.size() == MAX_SELECTED_EVIDENCE) {
                        return List.copyOf(selected);
                    }
                }
            }
        }
        return List.copyOf(selected);
    }

    private List<SelectedEvidence> selectForState(
            TypedEvidenceState state,
            List<CandidateValidation> validations) {
        MatchState required = switch (state) {
            case FOUND -> MatchState.SATISFIED;
            case PARTIAL -> MatchState.UNKNOWN;
            case NONE -> MatchState.CONTRADICTED;
            case UNASSESSED -> throw new IllegalArgumentException("UNASSESSED uses baseline selection");
        };
        List<SelectedEvidence> selected = new ArrayList<>();
        Set<String> seenChildren = new LinkedHashSet<>();
        Set<SourceIdentity> seenSpans = new LinkedHashSet<>();
        boolean requireRelatedUnknown = state == TypedEvidenceState.PARTIAL
                && validations.stream().anyMatch(this::isRelatedUnknown);
        for (CandidateValidation candidate : validations) {
            if (candidate.result().state() != required) {
                continue;
            }
            if (requireRelatedUnknown && !isRelatedUnknown(candidate)) {
                continue;
            }
            List<ChildValidation> contributors = contributingChildren(
                    state, candidate.children(), requireRelatedUnknown);
            for (ChildValidation child : contributors) {
                if (addIfUnique(seenChildren, seenSpans, child.source())) {
                    selected.add(toSelected(
                            selected.size() + 1,
                            candidate.dense(),
                            child.source(),
                            child.result(),
                            child.constraints()));
                    if (selected.size() == MAX_SELECTED_EVIDENCE) {
                        return List.copyOf(selected);
                    }
                }
            }
        }
        if (selected.isEmpty()) {
            throw new IllegalStateException("typed state has no source-grounded contributing child: " + state);
        }
        return List.copyOf(selected);
    }

    private List<ChildValidation> contributingChildren(
            TypedEvidenceState state,
            List<ChildValidation> children,
            boolean requireRelatedUnknown) {
        if (state != TypedEvidenceState.FOUND) {
            MatchState required = state == TypedEvidenceState.PARTIAL
                    ? MatchState.UNKNOWN : MatchState.CONTRADICTED;
            return children.stream()
                    .filter(value -> value.result().state() == required)
                    .filter(value -> !requireRelatedUnknown || isRelatedUnknown(value.constraints()))
                    .toList();
        }

        List<ChildValidation> individuallyComplete = children.stream()
                .filter(value -> value.result().state() == MatchState.SATISFIED)
                .toList();
        if (!individuallyComplete.isEmpty()) {
            return individuallyComplete;
        }

        // A bounded B3 passage may ground different required constraints in adjacent children of the
        // same structural Parent. Select those exact contributors without ever joining passages.
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
                throw new IllegalStateException("SATISFIED passage lacks an atomic constraint contributor");
            }
            if (!contributors.contains(contributor)) {
                contributors.add(contributor);
            }
        }
        return List.copyOf(contributors);
    }

    private boolean addIfUnique(
            Set<String> seenChildren,
            Set<SourceIdentity> seenSpans,
            AtomicEvidence child) {
        SourceProvenance source = child.provenance();
        SourceIdentity identity = new SourceIdentity(
                source.documentId(), source.versionId(), source.page(),
                source.codePointStart(), source.codePointEnd());
        if (seenChildren.contains(child.childId()) || seenSpans.contains(identity)) {
            return false;
        }
        seenChildren.add(child.childId());
        seenSpans.add(identity);
        return true;
    }

    private SelectedEvidence toSelected(
            int selectedRank,
            DenseCandidate dense,
            AtomicEvidence child,
            EvaluationResult result,
            List<ConstraintValidation> constraintTrace) {
        return new SelectedEvidence(
                selectedRank,
                dense.denseRank(),
                dense.candidateId(),
                dense.cosineScore(),
                child.childId(),
                child.sourceText(),
                child.provenance(),
                result == null ? null : result.state(),
                result == null ? List.of() : result.reasons(),
                constraintTrace);
    }

    private void validateRanking(
            PreparedCorpus corpus,
            String queryId,
            String userBundleId,
            List<DenseCandidate> denseRanking) {
        if (denseRanking.isEmpty()) {
            throw new IllegalArgumentException("Dense ranking is empty: " + queryId);
        }
        Set<String> identities = new HashSet<>();
        for (int index = 0; index < denseRanking.size(); index++) {
            DenseCandidate dense = denseRanking.get(index);
            if (dense.denseRank() != index + 1 || !identities.add(dense.candidateId())) {
                throw new IllegalArgumentException("Dense ranking identity/order is invalid: " + queryId);
            }
            PreparedCandidate candidate = requiredCandidate(corpus, dense.candidateId());
            if (!candidate.source().userBundleId().equals(userBundleId)) {
                throw new IllegalArgumentException("Dense ranking crosses owner scope: " + queryId);
            }
        }
        Set<String> expected = corpus.candidatesById().values().stream()
                .filter(candidate -> candidate.source().userBundleId().equals(userBundleId))
                .map(candidate -> candidate.source().candidateId())
                .collect(java.util.stream.Collectors.toSet());
        if (!expected.equals(identities)) {
            throw new IllegalArgumentException("Dense ranking is not the full owner-scoped B3 candidate set: "
                    + queryId);
        }
    }

    private PreparedCandidate requiredCandidate(PreparedCorpus corpus, String candidateId) {
        PreparedCandidate candidate = corpus.candidatesById().get(candidateId);
        if (candidate == null) {
            throw new IllegalArgumentException("Dense ranking references an unknown candidate: " + candidateId);
        }
        return candidate;
    }

    private double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    enum TypedEvidenceState {
        FOUND,
        PARTIAL,
        NONE,
        UNASSESSED
    }

    record AtomicEvidence(String childId, String sourceText, SourceProvenance provenance) {
        AtomicEvidence {
            Objects.requireNonNull(childId, "childId");
            Objects.requireNonNull(sourceText, "sourceText");
            Objects.requireNonNull(provenance, "provenance");
            if (childId.isBlank() || sourceText.isBlank()) {
                throw new IllegalArgumentException("atomic evidence must be source-grounded");
            }
            int length = sourceText.codePointCount(0, sourceText.length());
            if (provenance.codePointEnd() - provenance.codePointStart() != length) {
                throw new IllegalArgumentException("atomic source text/range mismatch: " + childId);
            }
            if (!provenance.exactTextSha256().equals(sha256(sourceText))) {
                throw new IllegalArgumentException("atomic source text/hash mismatch: " + childId);
            }
        }
    }

    record SourceCandidate(
            String userBundleId,
            String candidateId,
            String documentId,
            String versionId,
            String parentId,
            List<AtomicEvidence> children) {
        SourceCandidate {
            Objects.requireNonNull(userBundleId, "userBundleId");
            Objects.requireNonNull(candidateId, "candidateId");
            Objects.requireNonNull(documentId, "documentId");
            Objects.requireNonNull(versionId, "versionId");
            Objects.requireNonNull(parentId, "parentId");
            children = List.copyOf(children);
            if (children.isEmpty()) {
                throw new IllegalArgumentException("source candidate requires atomic evidence");
            }
            int previousEnd = -1;
            Set<String> childIds = new HashSet<>();
            String sourcePath = null;
            Integer page = null;
            String documentHash = null;
            for (AtomicEvidence child : children) {
                SourceProvenance source = child.provenance();
                if (!childIds.add(child.childId())
                        || !documentId.equals(source.documentId())
                        || !versionId.equals(source.versionId())
                        || !parentId.equals(source.parentAnnotationCandidateId())) {
                    throw new IllegalArgumentException("candidate mixes child scope: " + candidateId);
                }
                if (sourcePath == null) {
                    sourcePath = source.sourcePath();
                    page = source.page();
                    documentHash = source.documentSourceSha256();
                }
                else if (!sourcePath.equals(source.sourcePath())
                        || !Objects.equals(page, source.page())
                        || !documentHash.equals(source.documentSourceSha256())) {
                    throw new IllegalArgumentException("candidate mixes source provenance: " + candidateId);
                }
                if (previousEnd > source.codePointStart()) {
                    throw new IllegalArgumentException("candidate children are not source ordered: " + candidateId);
                }
                previousEnd = source.codePointEnd();
            }
        }
    }

    record DenseCandidate(int denseRank, String candidateId, double cosineScore) {
        DenseCandidate {
            if (denseRank < 1 || candidateId == null || candidateId.isBlank()
                    || !Double.isFinite(cosineScore)) {
                throw new IllegalArgumentException("Dense candidate is invalid");
            }
        }
    }

    record PreparedCorpus(
            Map<String, PreparedCandidate> candidatesById,
            double observationExtractionLatencyMs,
            long observationCount,
            long sourcePayloadUtf8Bytes) {
        PreparedCorpus {
            candidatesById = Map.copyOf(candidatesById);
        }
    }

    record PreparedCandidate(SourceCandidate source, List<PreparedChild> children) {
        PreparedCandidate {
            children = List.copyOf(children);
        }
    }

    record PreparedChild(AtomicEvidence source, List<CandidateObservation> observations) {
        PreparedChild {
            observations = List.copyOf(observations);
        }
    }

    record ParsedQuery(
            String queryId,
            String text,
            List<QueryConstraint> constraints,
            double parseLatencyMs) {
        ParsedQuery {
            constraints = List.copyOf(constraints);
        }
    }

    record ConstraintValidation(QueryConstraint constraint, EvaluationResult result) {
    }

    record ChildValidation(
            AtomicEvidence source,
            EvaluationResult result,
            List<ConstraintValidation> constraints) {
        ChildValidation {
            constraints = List.copyOf(constraints);
        }
    }

    record CandidateValidation(
            DenseCandidate dense,
            EvaluationResult result,
            List<ConstraintValidation> constraints,
            List<ChildValidation> children,
            double validationLatencyMs) {
        CandidateValidation {
            constraints = List.copyOf(constraints);
            children = List.copyOf(children);
        }
    }

    record SelectedEvidence(
            int selectedRank,
            int denseRank,
            String candidateId,
            double cosineScore,
            String evidenceChildId,
            String sourceText,
            SourceProvenance provenance,
            MatchState matchState,
            List<DiagnosticReason> reasons,
            List<ConstraintValidation> constraintTrace) {
        SelectedEvidence {
            reasons = List.copyOf(reasons);
            constraintTrace = List.copyOf(constraintTrace);
        }
    }

    record SelectionResult(
            TypedEvidenceState state,
            boolean typedApplicabilityVerified,
            int parsedConstraintCount,
            List<String> originalCandidateIds,
            List<CandidateValidation> validationTrace,
            List<SelectedEvidence> baselineEvidence,
            List<SelectedEvidence> selectedEvidence,
            double queryParseLatencyMs,
            double validationLatencyMs,
            double selectionLatencyMs) {
        SelectionResult {
            originalCandidateIds = List.copyOf(originalCandidateIds);
            validationTrace = List.copyOf(validationTrace);
            baselineEvidence = List.copyOf(baselineEvidence);
            selectedEvidence = List.copyOf(selectedEvidence);
            if (selectedEvidence.size() > MAX_SELECTED_EVIDENCE) {
                throw new IllegalArgumentException("selected evidence exceeds max size");
            }
        }
    }

    private record SourceIdentity(
            String documentId,
            String versionId,
            Integer page,
            int start,
            int end) {
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
