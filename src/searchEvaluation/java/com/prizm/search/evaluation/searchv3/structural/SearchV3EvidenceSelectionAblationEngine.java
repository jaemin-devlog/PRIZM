package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.AtomicEvidence;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.DenseCandidate;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.ParsedQuery;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.PreparedCorpus;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.SelectedEvidence;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.SelectionResult;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.SourceCandidate;
import com.prizm.search.evaluation.searchv3.structural.EvidenceValidationSelector.TypedEvidenceState;
import com.prizm.search.evaluation.searchv3.typed.TypedConstraintStressDataset;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DateConstraint;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.IdentifierNumberConstraint;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.LiteralIdentifierConstraint;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QuantityConstraint;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QueryConstraint;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** E0 B3 evidence selection versus E1 typed validation-before-selection measurement. */
final class SearchV3EvidenceSelectionAblationEngine {

    static final String E0_PROFILE = "E0_B3_DENSE_EVIDENCE_SELECTION";
    static final String E1_PROFILE = "E1_B3_TYPED_VALIDATION_EVIDENCE_SELECTION";

    private final EvidenceValidationSelector selector;

    SearchV3EvidenceSelectionAblationEngine() {
        this(new EvidenceValidationSelector());
    }

    SearchV3EvidenceSelectionAblationEngine(EvidenceValidationSelector selector) {
        this.selector = Objects.requireNonNull(selector, "selector");
    }

    ExperimentReport evaluate(
            String suite,
            SearchV3DenseAblationEngine.PassageDenseRun denseRun,
            List<TypedConstraintStressDataset.DatasetSlice> strictTypedSlices) {
        Objects.requireNonNull(suite, "suite");
        Objects.requireNonNull(denseRun, "denseRun");
        Objects.requireNonNull(strictTypedSlices, "strictTypedSlices");
        Map<String, TypedConstraintStressDataset.DatasetSlice> typedBySplit = strictTypedSlices.stream()
                .collect(Collectors.toMap(
                        value -> value.split().name(),
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalArgumentException("duplicate strict typed split");
                        },
                        LinkedHashMap::new));
        if (!typedBySplit.isEmpty()) {
            Set<String> denseSplits = denseRun.slices().stream()
                    .map(value -> value.dataset().split().manifestName())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!denseSplits.equals(typedBySplit.keySet())) {
                throw new IllegalArgumentException("strict typed split coverage differs from B3");
            }
        }

        List<PendingSlice> pendingSlices = new ArrayList<>();
        for (SearchV3DenseAblationEngine.PassageDenseSliceRun denseSlice : denseRun.slices()) {
            TypedConstraintStressDataset.DatasetSlice strict = typedBySplit.get(
                    denseSlice.dataset().split().manifestName());
            if (strict != null && !strict.datasetVersion().equals(denseSlice.dataset().datasetVersion())) {
                throw new IllegalArgumentException("strict typed and B3 dataset version differ");
            }
            if (strict != null) {
                validateStrictRuntimeIdentity(denseSlice, strict);
            }
            Projection projection = projectSourceOnly(denseSlice);
            PreparedCorpus prepared = selector.prepare(projection.sourceCandidates());
            List<PendingQuery> pending = new ArrayList<>();
            for (SearchV3DenseAblationEngine.PassageDenseQueryRanking denseQuery : denseSlice.queries()) {
                ParsedQuery parsed = selector.parse(denseQuery.query().queryId(), denseQuery.query().text());
                if (strict != null) {
                    requiredRuntimeQuestion(strict, denseQuery.query());
                }
                List<DenseCandidate> denseCandidates = denseQuery.fullRanking().stream()
                        .map(value -> new DenseCandidate(
                                value.rank(), value.candidateId(), value.cosineScore()))
                        .toList();
                SelectionResult selected = selector.select(
                        prepared,
                        parsed,
                        denseQuery.query().userBundleId(),
                        strict != null,
                        denseCandidates);
                pending.add(new PendingQuery(denseQuery, parsed, selected));
            }

            pendingSlices.add(new PendingSlice(denseSlice, strict, prepared, List.copyOf(pending)));
        }

        // Global two-pass boundary: DEV and CALIBRATION runtime selections both finish before any
        // evaluation Gold is read, validated, or attached.
        List<SliceReport> slices = new ArrayList<>();
        for (PendingSlice pendingSlice : pendingSlices) {
            SearchV3DenseAblationEngine.PassageDenseSliceRun denseSlice = pendingSlice.dense();
            TypedConstraintStressDataset.DatasetSlice strict = pendingSlice.strict();
            if (strict != null) validateStrictGoldIdentity(denseSlice, strict);
            List<QueryReport> queries = new ArrayList<>();
            for (PendingQuery value : pendingSlice.queries()) {
                TypedConstraintStressDataset.TypedQueryAnnotation annotation = strict == null ? null
                        : requiredAnnotation(strict, value.dense().query());
                QueryGold gold = attachGold(denseSlice, value.dense().query(), strict, annotation);
                queries.add(scoreQuery(
                        denseSlice, value.dense(), value.parsed(), value.selected(), gold, annotation));
            }
            slices.add(new SliceReport(
                    denseSlice.dataset().split().manifestName(),
                    pendingSlice.prepared().observationExtractionLatencyMs(),
                    pendingSlice.prepared().observationCount(),
                    pendingSlice.prepared().sourcePayloadUtf8Bytes(),
                    List.copyOf(queries)));
        }
        List<QueryReport> queries = slices.stream().flatMap(value -> value.queries().stream()).toList();
        return new ExperimentReport(
                1,
                suite,
                denseRun.datasetVersion(),
                denseRun.model(),
                E0_PROFILE,
                E1_PROFILE,
                aggregate(queries),
                grouped(queries, QueryReport::typedKind),
                grouped(queries, QueryReport::primaryFamily),
                latency(queries.stream().map(QueryReport::queryParseLatencyMs).toList()),
                latency(queries.stream().map(QueryReport::validationLatencyMs).toList()),
                latency(queries.stream().flatMap(value -> value.validationTrace().stream())
                        .map(CandidateValidationTrace::validationLatencyMs).toList()),
                latency(queries.stream().map(QueryReport::selectionLatencyMs).toList()),
                latency(queries.stream().map(QueryReport::addedLatencyMs).toList()),
                List.copyOf(slices));
    }

    private Projection projectSourceOnly(SearchV3DenseAblationEngine.PassageDenseSliceRun denseSlice) {
        Map<String, String> ownerByVersion = denseSlice.dataset().activeDocumentsByVersion().values().stream()
                .collect(Collectors.toMap(
                        SearchV3DenseAblationDataset.SourceDocument::versionId,
                        SearchV3DenseAblationDataset.SourceDocument::userBundleId));
        List<SourceCandidate> candidates = new ArrayList<>();
        denseSlice.passageById().forEach((candidateId, passage) -> {
            if (!candidateId.equals(passage.passageId())) {
                throw new IllegalStateException("B3 passage identity mismatch");
            }
            String owner = ownerByVersion.get(passage.versionId());
            if (owner == null) {
                throw new IllegalStateException("B3 passage is not from an ACTIVE owner version");
            }
            List<AtomicEvidence> children = passage.evidenceChildren().stream()
                    .map(child -> new AtomicEvidence(
                            child.childId(), child.sourceText(), child.provenance()))
                    .toList();
            candidates.add(new SourceCandidate(
                    owner,
                    passage.passageId(),
                    passage.documentId(),
                    passage.versionId(),
                    passage.parentAnnotationCandidateId(),
                    children));
        });
        return new Projection(List.copyOf(candidates));
    }

    private void validateStrictRuntimeIdentity(
            SearchV3DenseAblationEngine.PassageDenseSliceRun denseSlice,
            TypedConstraintStressDataset.DatasetSlice strict) {
        Set<String> denseQueryIds = denseSlice.queries().stream()
                .map(value -> value.query().queryId())
                .collect(Collectors.toSet());
        Set<String> strictQueryIds = strict.runtimeInputs().questions().stream()
                .map(TypedConstraintStressDataset.RuntimeQuestion::queryId)
                .collect(Collectors.toSet());
        if (!denseQueryIds.equals(strictQueryIds)) {
            throw new IllegalStateException("strict typed runtime query inventory differs from B3");
        }

        Map<String, SearchV3DenseAblationDataset.SourceDocument> denseDocuments =
                denseSlice.dataset().activeDocumentsByVersion();
        if (denseDocuments.size() != strict.runtimeInputs().documents().size()) {
            throw new IllegalStateException("strict typed runtime document inventory differs from B3");
        }
        for (TypedConstraintStressDataset.SourceDocument strictDocument
                : strict.runtimeInputs().documents()) {
            SearchV3DenseAblationDataset.SourceDocument dense =
                    denseDocuments.get(strictDocument.versionId());
            if (dense == null
                    || !dense.userBundleId().equals(strictDocument.userBundleId())
                    || !dense.documentId().equals(strictDocument.documentId())
                    || !dense.structuralDocument().sourcePath().equals(strictDocument.contentPath())
                    || !dense.structuralDocument().sourceText().equals(strictDocument.sourceText())
                    || !dense.structuralDocument().sourceSha256().equals(strictDocument.contentSha256())
                    || !dense.documentType().equals(strictDocument.documentType())
                    || !dense.language().equals(strictDocument.language())) {
                throw new IllegalStateException(
                        "strict typed runtime document identity differs from B3: "
                                + strictDocument.versionId());
            }
        }
    }

    private void validateStrictGoldIdentity(
            SearchV3DenseAblationEngine.PassageDenseSliceRun denseSlice,
            TypedConstraintStressDataset.DatasetSlice strict) {
        Set<String> denseQueryIds = denseSlice.queries().stream()
                .map(value -> value.query().queryId())
                .collect(Collectors.toSet());
        if (!denseQueryIds.equals(strict.evaluationGold().queryAnnotations().keySet())
                || !denseQueryIds.equals(strict.evaluationGold().queryTruth().keySet())) {
            throw new IllegalStateException("strict typed Gold query inventory differs from B3");
        }
        Map<String, SearchV3DenseAblationDataset.GoldUnit> denseUnits = denseSlice.dataset().units();
        if (!denseUnits.keySet().equals(strict.evaluationGold().units().keySet())) {
            throw new IllegalStateException("strict typed Gold unit inventory differs from B3");
        }
        strict.evaluationGold().units().forEach((unitId, strictUnit) -> {
            SearchV3DenseAblationDataset.GoldUnit dense = denseUnits.get(unitId);
            boolean spansEqual = dense.sourceSpans().size() == strictUnit.sourceSpans().size();
            if (spansEqual) {
                for (int index = 0; index < dense.sourceSpans().size(); index++) {
                    SearchV3DenseAblationDataset.GoldSpan left = dense.sourceSpans().get(index);
                    TypedConstraintStressDataset.SourceSpan right = strictUnit.sourceSpans().get(index);
                    if (!left.spanId().equals(right.spanId())
                            || !left.documentId().equals(right.documentId())
                            || !left.versionId().equals(right.versionId())
                            || !Objects.equals(left.page(), right.page())
                            || left.codePointStart() != right.codePointStart()
                            || left.codePointEnd() != right.codePointEnd()
                            || left.lineStart() != right.lineStart()
                            || left.lineEnd() != right.lineEnd()
                            || !left.text().equals(right.text())
                            || !left.textSha256().equals(right.textSha256())) {
                        spansEqual = false;
                        break;
                    }
                }
            }
            if (!dense.userBundleId().equals(strictUnit.userBundleId())
                    || !dense.parentId().equals(strictUnit.parentId())
                    || !dense.groupId().equals(strictUnit.groupId())
                    || !dense.documentId().equals(strictUnit.documentId())
                    || !dense.versionId().equals(strictUnit.versionId())
                    || !dense.sourceFactId().equals(strictUnit.sourceFactId())
                    || !spansEqual) {
                throw new IllegalStateException(
                        "strict typed Gold unit identity differs from B3: " + unitId);
            }
        });
        for (SearchV3DenseAblationEngine.PassageDenseQueryRanking denseQuery : denseSlice.queries()) {
            TypedConstraintStressDataset.QueryTruth truth =
                    strict.evaluationGold().queryTruth().get(denseQuery.query().queryId());
            Set<String> strictDirect = truth.expectedEvidence().stream()
                    .filter(value -> value.supportRelation().equals("DIRECT_SUPPORT"))
                    .map(TypedConstraintStressDataset.ExpectedEvidence::evidenceUnitId)
                    .collect(Collectors.toSet());
            Set<String> denseDirect = denseQuery.query().allExpectedEvidence().stream()
                    .filter(value -> value.supportRelation().equals("DIRECT_SUPPORT"))
                    .map(SearchV3DenseAblationDataset.ExpectedEvidence::evidenceUnitId)
                    .collect(Collectors.toSet());
            if (!truth.userBundleId().equals(denseQuery.query().userBundleId())
                    || !truth.language().equals(denseQuery.query().language())
                    || !strictDirect.equals(denseDirect)) {
                throw new IllegalStateException(
                        "strict typed Gold query identity differs from B3: " + truth.queryId());
            }
        }
    }

    private TypedConstraintStressDataset.RuntimeQuestion requiredRuntimeQuestion(
            TypedConstraintStressDataset.DatasetSlice strict,
            SearchV3DenseAblationDataset.Query query) {
        TypedConstraintStressDataset.RuntimeQuestion runtime = strict.runtimeInputs().questions().stream()
                .filter(value -> value.queryId().equals(query.queryId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("strict typed query is missing"));
        if (!runtime.userBundleId().equals(query.userBundleId())
                || !runtime.text().equals(query.text())
                || !runtime.language().equals(query.language())) {
            throw new IllegalStateException("strict typed/B3 runtime query mismatch: " + query.queryId());
        }
        return runtime;
    }

    private TypedConstraintStressDataset.TypedQueryAnnotation requiredAnnotation(
            TypedConstraintStressDataset.DatasetSlice strict,
            SearchV3DenseAblationDataset.Query query) {
        TypedConstraintStressDataset.TypedQueryAnnotation annotation =
                strict.evaluationGold().queryAnnotations().get(query.queryId());
        if (annotation == null || !annotation.userBundleId().equals(query.userBundleId())) {
            throw new IllegalStateException("strict typed annotation is missing: " + query.queryId());
        }
        return annotation;
    }

    private QueryGold attachGold(
            SearchV3DenseAblationEngine.PassageDenseSliceRun denseSlice,
            SearchV3DenseAblationDataset.Query query,
            TypedConstraintStressDataset.DatasetSlice strict,
            TypedConstraintStressDataset.TypedQueryAnnotation annotation) {
        Map<String, SearchV3DenseAblationDataset.GoldUnit> units = denseSlice.dataset().units();
        Set<String> directUnits = query.allExpectedEvidence().stream()
                .filter(value -> value.supportRelation().equals("DIRECT_SUPPORT"))
                .map(SearchV3DenseAblationDataset.ExpectedEvidence::evidenceUnitId)
                .collect(Collectors.toSet());
        if (strict == null) {
            return new QueryGold(null, Map.of(), directUnits, units);
        }
        Map<String, String> expectedStateByUnit = annotation.expectedEvidenceStates().stream()
                .collect(Collectors.toMap(
                        TypedConstraintStressDataset.ExpectedEvidenceState::evidenceUnitId,
                        TypedConstraintStressDataset.ExpectedEvidenceState::state,
                        (left, right) -> {
                            throw new IllegalStateException("duplicate typed unit state");
                        },
                        LinkedHashMap::new));
        TypedEvidenceState expectedState = expectedTypedState(expectedStateByUnit.values());
        return new QueryGold(expectedState, Map.copyOf(expectedStateByUnit), directUnits, units);
    }

    private QueryReport scoreQuery(
            SearchV3DenseAblationEngine.PassageDenseSliceRun denseSlice,
            SearchV3DenseAblationEngine.PassageDenseQueryRanking dense,
            ParsedQuery parsed,
            SelectionResult result,
            QueryGold gold,
            TypedConstraintStressDataset.TypedQueryAnnotation annotation) {
        boolean e0Recall20 = candidateRecall20(dense.fullRanking(), gold.directUnitIds());
        boolean e1Recall20 = e0Recall20;
        boolean semanticParity = !result.typedApplicabilityVerified()
                && result.selectedEvidence().equals(result.baselineEvidence())
                && result.originalCandidateIds().equals(dense.fullRanking().stream()
                        .map(SearchV3DenseAblationEngine.RankedCandidate::candidateId).toList());
        boolean e0DirectAt1 = selectedDirectAt1(result.baselineEvidence(), gold);
        boolean e1DirectAt1 = selectedDirectAt1(result.selectedEvidence(), gold);
        boolean correctState = gold.expectedState() == null || result.state() == gold.expectedState();
        SelectionCorrectness correctness = gold.expectedState() == null
                ? SelectionCorrectness.unassessed()
                : selectedMatchesExpectedTier(result.selectedEvidence(), gold);
        long contradictedSelected = result.selectedEvidence().stream()
                .filter(this::containsContradictedConstraint)
                .count();
        long supportContradictedSelected = result.state() == TypedEvidenceState.FOUND
                        || result.state() == TypedEvidenceState.PARTIAL
                ? contradictedSelected : 0L;
        boolean unknownFallback = result.state() == TypedEvidenceState.PARTIAL
                && result.selectedEvidence().stream()
                        .allMatch(value -> value.matchState() == TypedValueModel.MatchState.UNKNOWN);
        long duplicateSelected = duplicateSelected(result.selectedEvidence());
        long crossParentViolations = crossParentMergeViolations(result);
        long provenanceCorrect = result.selectedEvidence().stream()
                .filter(value -> validSelectedProvenance(
                        value, dense.query().userBundleId(), denseSlice))
                .count();
        return new QueryReport(
                dense.query().queryId(),
                dense.query().userBundleId(),
                dense.query().split().manifestName(),
                dense.professionGroup(),
                dense.query().language(),
                annotation == null ? "" : annotation.constraint().kind(),
                annotation == null ? "" : annotation.primaryFamily(),
                annotation == null || exactConstraintConformance(parsed, annotation),
                !gold.directUnitIds().isEmpty(),
                result.typedApplicabilityVerified(),
                gold.expectedState(),
                result.state(),
                correctState,
                correctness.queryCorrect(),
                correctness.tierCoverage(),
                correctness.correctSelectedCount(),
                correctness.incorrectSelectedCount(),
                correctness.selectedPrecision(),
                e0Recall20,
                e1Recall20,
                semanticParity,
                e0DirectAt1,
                e1DirectAt1,
                e0DirectAt1 && !e1DirectAt1,
                !e0DirectAt1 && e1DirectAt1,
                result.originalCandidateIds().size(),
                result.selectedEvidence().size(),
                contradictedSelected,
                supportContradictedSelected,
                unknownFallback,
                duplicateSelected,
                crossParentViolations,
                provenanceCorrect,
                result.queryParseLatencyMs(),
                result.validationLatencyMs(),
                result.validationTrace().isEmpty() ? 0.0d
                        : result.validationLatencyMs() / result.validationTrace().size(),
                result.selectionLatencyMs(),
                result.queryParseLatencyMs() + result.validationLatencyMs() + result.selectionLatencyMs(),
                result.baselineEvidence().stream().map(SelectedEvidence::evidenceChildId).toList(),
                result.selectedEvidence().stream().map(this::selectedTrace).toList(),
                validationTrace(result));
    }

    private SelectedEvidenceTrace selectedTrace(SelectedEvidence value) {
        return new SelectedEvidenceTrace(
                value.selectedRank(),
                value.denseRank(),
                value.candidateId(),
                value.cosineScore(),
                value.evidenceChildId(),
                value.sourceText(),
                value.provenance(),
                value.matchState(),
                value.reasons(),
                value.constraintTrace().stream().map(this::constraintTrace).toList());
    }

    private List<CandidateValidationTrace> validationTrace(SelectionResult result) {
        return result.validationTrace().stream().map(candidate -> new CandidateValidationTrace(
                candidate.dense().denseRank(),
                candidate.dense().candidateId(),
                candidate.dense().cosineScore(),
                candidate.result().state(),
                candidate.result().reasons(),
                candidate.validationLatencyMs(),
                candidate.constraints().stream().map(this::constraintTrace).toList(),
                candidate.children().stream().map(child -> new ChildValidationTrace(
                        child.source().childId(),
                        child.source().sourceText(),
                        child.source().provenance(),
                        child.result().state(),
                        child.result().reasons(),
                        child.constraints().stream().map(this::constraintTrace).toList()))
                        .toList())).toList();
    }

    private ConstraintResultTrace constraintTrace(
            EvidenceValidationSelector.ConstraintValidation value) {
        return new ConstraintResultTrace(
                value.constraint().kind().name(),
                value.constraint().span().surface(),
                value.result().state(),
                value.result().reasons());
    }

    private boolean candidateRecall20(
            List<SearchV3DenseAblationEngine.RankedCandidate> ranking,
            Set<String> directUnitIds) {
        if (directUnitIds.isEmpty()) return false;
        return ranking.stream().limit(EvidenceValidationSelector.VALIDATION_CANDIDATE_K)
                .flatMap(value -> value.coveredUnitIds().stream())
                .anyMatch(directUnitIds::contains);
    }

    private boolean selectedDirectAt1(List<SelectedEvidence> selected, QueryGold gold) {
        if (selected.isEmpty() || gold.directUnitIds().isEmpty()) return false;
        Set<String> units = mappedUnitIds(selected.get(0), gold.denseUnits());
        return units.stream().anyMatch(gold.directUnitIds()::contains);
    }

    private SelectionCorrectness selectedMatchesExpectedTier(
            List<SelectedEvidence> selected,
            QueryGold gold) {
        if (selected.isEmpty()) return new SelectionCorrectness(false, false, 0, 0, 0.0d);
        String expectedUnitState = switch (gold.expectedState()) {
            case FOUND -> "SATISFIED";
            case PARTIAL -> "UNKNOWN";
            case NONE -> "CONTRADICTED";
            case UNASSESSED -> throw new IllegalStateException("Gold cannot be UNASSESSED");
        };
        Set<String> acceptable = gold.expectedStateByUnit().entrySet().stream()
                .filter(entry -> entry.getValue().equals(expectedUnitState))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        long correct = selected.stream()
                .filter(value -> mappedUnitIds(value, gold.denseUnits()).stream()
                        .anyMatch(acceptable::contains))
                .count();
        boolean coverage = correct > 0;
        long incorrect = selected.size() - correct;
        return new SelectionCorrectness(
                coverage && incorrect == 0,
                coverage,
                correct,
                incorrect,
                correct / (double) selected.size());
    }

    private Set<String> mappedUnitIds(
            SelectedEvidence evidence,
            Map<String, SearchV3DenseAblationDataset.GoldUnit> units) {
        SourceProvenance source = evidence.provenance();
        return units.values().stream()
                .filter(unit -> unit.documentId().equals(source.documentId()))
                .filter(unit -> unit.versionId().equals(source.versionId()))
                .filter(unit -> unit.sourceSpans().stream().allMatch(span ->
                        Objects.equals(span.page(), source.page())
                                && source.codePointStart() <= span.codePointStart()
                                && source.codePointEnd() >= span.codePointEnd()))
                .map(SearchV3DenseAblationDataset.GoldUnit::evidenceUnitId)
                .collect(Collectors.toSet());
    }

    private long duplicateSelected(List<SelectedEvidence> selected) {
        Set<String> childIds = new HashSet<>();
        Set<String> spans = new HashSet<>();
        long duplicates = 0L;
        for (SelectedEvidence evidence : selected) {
            SourceProvenance source = evidence.provenance();
            String span = source.documentId() + "|" + source.versionId() + "|" + source.page()
                    + "|" + source.codePointStart() + "|" + source.codePointEnd();
            if (!childIds.add(evidence.evidenceChildId()) || !spans.add(span)) duplicates++;
        }
        return duplicates;
    }

    private boolean containsContradictedConstraint(SelectedEvidence evidence) {
        return evidence.constraintTrace().stream()
                .anyMatch(value -> value.result().state() == TypedValueModel.MatchState.CONTRADICTED);
    }

    private long crossParentMergeViolations(SelectionResult result) {
        Map<String, Set<String>> parentByCandidate = result.validationTrace().stream()
                .collect(Collectors.toMap(
                        value -> value.dense().candidateId(),
                        value -> value.children().stream()
                                .map(child -> child.source().provenance().parentAnnotationCandidateId())
                                .collect(Collectors.toSet())));
        return result.selectedEvidence().stream().filter(value -> {
            Set<String> parents = parentByCandidate.get(value.candidateId());
            return parents != null && (parents.size() != 1
                    || !parents.contains(value.provenance().parentAnnotationCandidateId()));
        }).count();
    }

    private boolean validSelectedProvenance(
            SelectedEvidence evidence,
            String expectedOwner,
            SearchV3DenseAblationEngine.PassageDenseSliceRun denseSlice) {
        SourceProvenance source = evidence.provenance();
        RetrievalPassage passage = denseSlice.passageById().get(evidence.candidateId());
        if (passage == null) return false;
        EvidenceChild exactChild = passage.evidenceChildren().stream()
                .filter(child -> child.childId().equals(evidence.evidenceChildId()))
                .findFirst().orElse(null);
        if (exactChild == null || !exactChild.sourceText().equals(evidence.sourceText())
                || !exactChild.provenance().equals(source)) {
            return false;
        }
        SearchV3DenseAblationDataset.SourceDocument document =
                denseSlice.dataset().activeDocumentsByVersion().get(source.versionId());
        if (document == null || !document.userBundleId().equals(expectedOwner)
                || !document.documentId().equals(source.documentId())) {
            return false;
        }
        StructuralDocument structural = document.structuralDocument();
        if (!structural.versionId().equals(source.versionId())
                || !structural.sourcePath().equals(source.sourcePath())
                || !Objects.equals(structural.page(), source.page())
                || !structural.sourceSha256().equals(source.documentSourceSha256())) {
            return false;
        }
        String exactSlice;
        try {
            int utf16Start = structural.sourceText().offsetByCodePoints(0, source.codePointStart());
            int utf16End = structural.sourceText().offsetByCodePoints(0, source.codePointEnd());
            exactSlice = structural.sourceText().substring(utf16Start, utf16End);
        }
        catch (IndexOutOfBoundsException exception) {
            return false;
        }
        int lineStart = 1 + newlineCountBefore(structural.sourceText(), source.codePointStart());
        int lineEnd = lineStart + newlineCount(exactSlice);
        return exactSlice.equals(evidence.sourceText())
                && source.codePointEnd() - source.codePointStart()
                        == evidence.sourceText().codePointCount(0, evidence.sourceText().length())
                && source.lineStart() == lineStart
                && source.lineEnd() == lineEnd
                && source.exactTextSha256().equals(sha256(evidence.sourceText()))
                && source.sourceBlockId() != null
                && !source.sourceBlockId().isBlank()
                && source.parentAnnotationCandidateId().equals(passage.parentAnnotationCandidateId());
    }

    private int newlineCountBefore(String source, int codePointEnd) {
        int utf16End = source.offsetByCodePoints(0, codePointEnd);
        return newlineCount(source.substring(0, utf16End));
    }

    private int newlineCount(String value) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == '\n') count++;
        }
        return count;
    }

    private TypedEvidenceState expectedTypedState(java.util.Collection<String> states) {
        if (states.contains("SATISFIED")) return TypedEvidenceState.FOUND;
        if (states.contains("CONTRADICTED")) return TypedEvidenceState.NONE;
        return TypedEvidenceState.PARTIAL;
    }

    private AggregateMetrics aggregate(List<QueryReport> queries) {
        long typed = queries.stream().filter(QueryReport::typedApplicabilityVerified).count();
        long semantic = queries.size() - typed;
        long direct = queries.stream().filter(QueryReport::directSupport).count();
        long constraintConformance = queries.stream().filter(QueryReport::typedApplicabilityVerified)
                .filter(QueryReport::constraintConformance).count();
        long stateCorrect = queries.stream().filter(QueryReport::typedApplicabilityVerified)
                .filter(QueryReport::stateCorrect).count();
        long selectionCorrect = queries.stream().filter(QueryReport::typedApplicabilityVerified)
                .filter(QueryReport::evidenceSelectionCorrect).count();
        Map<String, Map<String, Long>> confusion = confusion(queries);
        double macroF1 = macroF1(confusion);
        long selected = queries.stream().mapToLong(QueryReport::selectedEvidenceCount).sum();
        long correctSelected = queries.stream().mapToLong(QueryReport::correctSelectedEvidenceCount).sum();
        long incorrectSelected = queries.stream().mapToLong(QueryReport::incorrectSelectedEvidenceCount).sum();
        long contradicted = queries.stream().mapToLong(QueryReport::contradictedSelectedCount).sum();
        long supportSelected = queries.stream()
                .filter(value -> value.predictedState() == TypedEvidenceState.FOUND
                        || value.predictedState() == TypedEvidenceState.PARTIAL)
                .mapToLong(QueryReport::selectedEvidenceCount).sum();
        long supportContradicted = queries.stream()
                .mapToLong(QueryReport::supportContradictedSelectedCount).sum();
        long partial = queries.stream()
                .filter(value -> value.predictedState() == TypedEvidenceState.PARTIAL).count();
        long unknownFallback = queries.stream().filter(QueryReport::unknownFallback).count();
        long provenanceCorrect = queries.stream().mapToLong(QueryReport::provenanceCorrectCount).sum();
        long semanticParity = queries.stream().filter(value -> !value.typedApplicabilityVerified())
                .filter(QueryReport::semanticExactParity).count();
        return new AggregateMetrics(
                queries.size(),
                typed,
                semantic,
                direct,
                constraintConformance,
                typed == 0 ? 1.0d : constraintConformance / (double) typed,
                semanticParity,
                semantic == 0 ? 1.0d : semanticParity / (double) semantic,
                queries.stream().filter(QueryReport::e0CandidateRecall20).count(),
                queries.stream().filter(QueryReport::e1CandidateRecall20).count(),
                direct == 0 ? 1.0d : queries.stream().filter(QueryReport::e0CandidateRecall20).count()
                        / (double) direct,
                direct == 0 ? 1.0d : queries.stream().filter(QueryReport::e1CandidateRecall20).count()
                        / (double) direct,
                queries.stream().filter(QueryReport::directRank1Loss).count(),
                queries.stream().filter(QueryReport::directRank1Gain).count(),
                stateCorrect,
                typed == 0 ? 1.0d : stateCorrect / (double) typed,
                macroF1,
                confusion,
                selectionCorrect,
                typed == 0 ? 1.0d : selectionCorrect / (double) typed,
                selected,
                correctSelected,
                incorrectSelected,
                typed == 0 || correctSelected + incorrectSelected == 0
                        ? 1.0d : correctSelected / (double) (correctSelected + incorrectSelected),
                contradicted,
                selected == 0 ? 0.0d : contradicted / (double) selected,
                supportContradicted,
                supportSelected == 0 ? 0.0d : supportContradicted / (double) supportSelected,
                unknownFallback,
                partial == 0 ? 1.0d : unknownFallback / (double) partial,
                queries.stream().mapToLong(QueryReport::duplicateSelectedCount).sum(),
                queries.stream().mapToLong(QueryReport::crossParentMergeViolationCount).sum(),
                provenanceCorrect,
                selected == 0 ? 1.0d : provenanceCorrect / (double) selected);
    }

    private Map<String, AggregateMetrics> grouped(
            List<QueryReport> queries,
            Function<QueryReport, String> classifier) {
        return queries.stream().filter(value -> !classifier.apply(value).isBlank())
                .collect(Collectors.groupingBy(
                        classifier,
                        LinkedHashMap::new,
                        Collectors.collectingAndThen(Collectors.toList(), this::aggregate)));
    }

    private Map<String, Map<String, Long>> confusion(List<QueryReport> queries) {
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        for (QueryReport query : queries) {
            if (!query.typedApplicabilityVerified()) continue;
            result.computeIfAbsent(query.expectedState().name(), ignored -> new LinkedHashMap<>())
                    .merge(query.predictedState().name(), 1L, Long::sum);
        }
        Map<String, Map<String, Long>> frozen = new LinkedHashMap<>();
        result.forEach((key, value) -> frozen.put(key, Map.copyOf(value)));
        return Map.copyOf(frozen);
    }

    private double macroF1(Map<String, Map<String, Long>> confusion) {
        if (confusion.isEmpty()) return 1.0d;
        List<TypedEvidenceState> states = List.of(
                TypedEvidenceState.FOUND, TypedEvidenceState.PARTIAL, TypedEvidenceState.NONE);
        return states.stream().mapToDouble(state -> {
            String key = state.name();
            long tp = confusion.getOrDefault(key, Map.of()).getOrDefault(key, 0L);
            long actual = confusion.getOrDefault(key, Map.of()).values().stream()
                    .mapToLong(Long::longValue).sum();
            long predicted = confusion.values().stream()
                    .mapToLong(row -> row.getOrDefault(key, 0L)).sum();
            double precision = predicted == 0 ? 0.0d : tp / (double) predicted;
            double recall = actual == 0 ? 0.0d : tp / (double) actual;
            return precision + recall == 0.0d ? 0.0d
                    : 2.0d * precision * recall / (precision + recall);
        }).average().orElse(0.0d);
    }

    private boolean exactConstraintConformance(
            ParsedQuery query,
            TypedConstraintStressDataset.TypedQueryAnnotation annotation) {
        return query.constraints().size() == 1
                && canonicalConstraint(query.constraints().get(0))
                        .equals(canonicalConstraint(query.text(), annotation.constraint()));
    }

    private String canonicalConstraint(QueryConstraint value) {
        String base = value.kind() + "|" + canonicalSpan(value.span());
        if (value instanceof QuantityConstraint quantity) {
            return base + "|" + quantity.operator() + "|" + decimal(quantity.value()) + "|"
                    + decimal(quantity.upperValue()) + "|" + quantity.normalizedUnit() + "|"
                    + canonicalQualifier(quantity.qualifier()) + "|" + canonicalDirection(quantity.direction());
        }
        if (value instanceof DateConstraint date) {
            return base + "|" + date.operator() + "|" + canonicalDate(date.interval()) + "|"
                    + canonicalQualifier(date.qualifier());
        }
        if (value instanceof IdentifierNumberConstraint identifier) {
            return base + "|" + identifier.normalizedIdentifier() + "|" + identifier.numberSurface() + "|"
                    + segments(identifier.normalizedSegments());
        }
        LiteralIdentifierConstraint literal = (LiteralIdentifierConstraint) value;
        return base + "|" + literal.normalizedLiteral();
    }

    private String canonicalConstraint(
            String query,
            TypedConstraintStressDataset.ConstraintAnnotation value) {
        String base = value.kind() + "|" + value.sourceSurface() + "@" + value.queryCharStart()
                + ":" + value.queryCharEnd();
        String qualifier = canonicalExpectedQualifier(
                query, value.qualifier(), value.qualifierCharStart(), value.qualifierCharEnd());
        return switch (value.kind()) {
            case "QUANTITY" -> base + "|" + value.operator() + "|" + decimal(value.value()) + "|"
                    + decimal(value.upperValue()) + "|" + value.normalizedUnit() + "|" + qualifier + "|"
                    + canonicalExpectedDirection(query, value);
            case "DATE" -> base + "|" + value.operator() + "|" + canonicalExpectedDate(value) + "|" + qualifier;
            case "IDENTIFIER_NUMBER" -> base + "|" + normalizeCaptured(value.identifier()) + "|"
                    + value.numberSurface() + "|" + value.normalizedSegments().stream()
                            .map(String::valueOf).collect(Collectors.joining("."));
            case "LITERAL_IDENTIFIER" -> base + "|" + value.normalizedLiteral();
            default -> throw new IllegalArgumentException("unknown expected constraint kind: " + value.kind());
        };
    }

    private String canonicalSpan(TypedValueModel.CodePointSpan span) {
        return span.surface() + "@" + span.startInclusive() + ":" + span.endExclusive();
    }

    private String canonicalQualifier(TypedValueModel.Qualifier qualifier) {
        if (qualifier.normalized().isBlank()) return "-";
        return qualifier.normalized() + "[" + String.join(",", qualifier.orderedTokens()) + "]|"
                + canonicalSpan(qualifier.span());
    }

    private String canonicalDirection(TypedValueModel.DirectionMark direction) {
        return direction.direction() + (direction.span() == null ? "" : "|" + canonicalSpan(direction.span()));
    }

    private String canonicalDate(TypedValueModel.DateInterval interval) {
        return interval.startInclusive() + ".." + interval.endInclusive() + ":" + interval.precision();
    }

    private String canonicalExpectedQualifier(String source, String qualifier, Integer start, Integer end) {
        if (qualifier == null) return "-";
        String normalized = normalize(qualifier);
        return normalized + "[" + normalized.replace(' ', ',') + "]|" + codePointSlice(source, start, end)
                + "@" + start + ":" + end;
    }

    private String canonicalExpectedDirection(
            String query,
            TypedConstraintStressDataset.ConstraintAnnotation value) {
        String direction = value.direction() == null ? "NONE" : value.direction();
        if ("NONE".equals(direction)) return direction;
        return direction + "|" + codePointSlice(
                query, value.directionCharStart(), value.directionCharEnd()) + "@"
                + value.directionCharStart() + ":" + value.directionCharEnd();
    }

    private String canonicalExpectedDate(TypedConstraintStressDataset.ConstraintAnnotation value) {
        LocalDate start;
        LocalDate end;
        if (value.dateStart() != null) {
            start = value.dateStart();
            end = value.dateEnd();
        }
        else {
            start = value.dateValue();
            end = switch (value.precision()) {
                case "YEAR" -> LocalDate.of(start.getYear(), 12, 31);
                case "YEAR_MONTH" -> YearMonth.from(start).atEndOfMonth();
                default -> start;
            };
        }
        return start + ".." + end + ":" + value.precision();
    }

    private String decimal(BigDecimal value) {
        return value == null ? "-" : value.stripTrailingZeros().toPlainString();
    }

    private String decimal(Double value) {
        return value == null ? "-" : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    private String segments(List<BigInteger> values) {
        return values.stream().map(BigInteger::toString).collect(Collectors.joining("."));
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}]+", " ").replaceAll("\\s+", " ").trim();
    }

    private String normalizeCaptured(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).trim();
    }

    private String codePointSlice(String value, int start, int end) {
        int utf16Start = value.offsetByCodePoints(0, start);
        int utf16End = value.offsetByCodePoints(0, end);
        return value.substring(utf16Start, utf16End);
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private LatencyMetrics latency(List<Double> values) {
        if (values.isEmpty()) return new LatencyMetrics(0, 0.0d, 0.0d, 0.0d, 0.0d);
        List<Double> sorted = values.stream().sorted().toList();
        return new LatencyMetrics(
                values.size(),
                values.stream().mapToDouble(Double::doubleValue).sum(),
                values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d),
                percentile(sorted, 0.50d),
                percentile(sorted, 0.95d));
    }

    private double percentile(List<Double> sorted, double percentile) {
        int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
        return sorted.get(index);
    }

    private record Projection(List<SourceCandidate> sourceCandidates) {
    }

    private record QueryGold(
            TypedEvidenceState expectedState,
            Map<String, String> expectedStateByUnit,
            Set<String> directUnitIds,
            Map<String, SearchV3DenseAblationDataset.GoldUnit> denseUnits) {
    }

    private record PendingQuery(
            SearchV3DenseAblationEngine.PassageDenseQueryRanking dense,
            ParsedQuery parsed,
            SelectionResult selected) {
    }

    private record PendingSlice(
            SearchV3DenseAblationEngine.PassageDenseSliceRun dense,
            TypedConstraintStressDataset.DatasetSlice strict,
            PreparedCorpus prepared,
            List<PendingQuery> queries) {
        PendingSlice {
            queries = List.copyOf(queries);
        }
    }

    private record SelectionCorrectness(
            boolean queryCorrect,
            boolean tierCoverage,
            long correctSelectedCount,
            long incorrectSelectedCount,
            double selectedPrecision) {

        static SelectionCorrectness unassessed() {
            return new SelectionCorrectness(true, true, 0, 0, 1.0d);
        }
    }

    record ConstraintResultTrace(
            String kind,
            String sourceSurface,
            TypedValueModel.MatchState state,
            List<TypedValueModel.DiagnosticReason> reasons) {
        ConstraintResultTrace {
            reasons = List.copyOf(reasons);
        }
    }

    record ChildValidationTrace(
            String evidenceChildId,
            String sourceText,
            SourceProvenance provenance,
            TypedValueModel.MatchState state,
            List<TypedValueModel.DiagnosticReason> reasons,
            List<ConstraintResultTrace> constraints) {
        ChildValidationTrace {
            reasons = List.copyOf(reasons);
            constraints = List.copyOf(constraints);
        }
    }

    record CandidateValidationTrace(
            int denseRank,
            String candidateId,
            double cosineScore,
            TypedValueModel.MatchState state,
            List<TypedValueModel.DiagnosticReason> reasons,
            double validationLatencyMs,
            List<ConstraintResultTrace> constraints,
            List<ChildValidationTrace> children) {
        CandidateValidationTrace {
            reasons = List.copyOf(reasons);
            constraints = List.copyOf(constraints);
            children = List.copyOf(children);
        }
    }

    record SelectedEvidenceTrace(
            int selectedRank,
            int denseRank,
            String candidateId,
            double cosineScore,
            String evidenceChildId,
            String sourceText,
            SourceProvenance provenance,
            TypedValueModel.MatchState state,
            List<TypedValueModel.DiagnosticReason> reasons,
            List<ConstraintResultTrace> constraints) {
        SelectedEvidenceTrace {
            reasons = List.copyOf(reasons);
            constraints = List.copyOf(constraints);
        }
    }

    record QueryReport(
            String queryId,
            String userBundleId,
            String split,
            String professionGroup,
            String language,
            String typedKind,
            String primaryFamily,
            boolean constraintConformance,
            boolean directSupport,
            boolean typedApplicabilityVerified,
            TypedEvidenceState expectedState,
            TypedEvidenceState predictedState,
            boolean stateCorrect,
            boolean evidenceSelectionCorrect,
            boolean expectedTierCoverage,
            long correctSelectedEvidenceCount,
            long incorrectSelectedEvidenceCount,
            double selectedEvidencePrecision,
            boolean e0CandidateRecall20,
            boolean e1CandidateRecall20,
            boolean semanticExactParity,
            boolean e0DirectAt1,
            boolean e1DirectAt1,
            boolean directRank1Loss,
            boolean directRank1Gain,
            int originalCandidateCount,
            int selectedEvidenceCount,
            long contradictedSelectedCount,
            long supportContradictedSelectedCount,
            boolean unknownFallback,
            long duplicateSelectedCount,
            long crossParentMergeViolationCount,
            long provenanceCorrectCount,
            double queryParseLatencyMs,
            double validationLatencyMs,
            double validationPerCandidateLatencyMs,
            double selectionLatencyMs,
            double addedLatencyMs,
            List<String> e0EvidenceChildIds,
            List<SelectedEvidenceTrace> e1SelectedEvidence,
            List<CandidateValidationTrace> validationTrace) {
        QueryReport {
            e0EvidenceChildIds = List.copyOf(e0EvidenceChildIds);
            e1SelectedEvidence = List.copyOf(e1SelectedEvidence);
            validationTrace = List.copyOf(validationTrace);
        }

        List<String> e1EvidenceChildIds() {
            return e1SelectedEvidence.stream().map(SelectedEvidenceTrace::evidenceChildId).toList();
        }
    }

    record AggregateMetrics(
            long queryCount,
            long typedQueryCount,
            long semanticQueryCount,
            long directQueryCount,
            long constraintConformanceCount,
            double constraintConformanceRate,
            long semanticExactParityCount,
            double semanticExactParityRate,
            long e0CandidateRecall20Count,
            long e1CandidateRecall20Count,
            double e0CandidateRecall20,
            double e1CandidateRecall20,
            long directRank1LossCount,
            long directRank1GainCount,
            long typedStateCorrectCount,
            double typedStateAccuracy,
            double typedStateMacroF1,
            Map<String, Map<String, Long>> typedStateConfusion,
            long correctEvidenceSelectionCount,
            double correctEvidenceSelectionRate,
            long selectedEvidenceCount,
            long correctSelectedEvidenceCount,
            long incorrectSelectedEvidenceCount,
            double selectedEvidencePrecision,
            long contradictedSelectedCount,
            double contradictedSelectedRate,
            long supportContradictedSelectedCount,
            double supportContradictedSelectedRate,
            long unknownFallbackQueryCount,
            double unknownFallbackRate,
            long duplicateSelectedCount,
            long crossParentMergeViolationCount,
            long provenanceCorrectCount,
            double provenanceAccuracy) {
    }

    record LatencyMetrics(long samples, double totalMs, double averageMs, double p50Ms, double p95Ms) {
    }

    record SliceReport(
            String split,
            double observationExtractionLatencyMs,
            long observationCount,
            long sourcePayloadUtf8Bytes,
            List<QueryReport> queries) {
        SliceReport {
            queries = List.copyOf(queries);
        }
    }

    record ExperimentReport(
            int schemaVersion,
            String suite,
            String datasetVersion,
            OllamaBgeM3EmbeddingClient.ModelMetadata model,
            String e0Profile,
            String e1Profile,
            AggregateMetrics aggregate,
            Map<String, AggregateMetrics> typedKindSlices,
            Map<String, AggregateMetrics> primaryFamilySlices,
            LatencyMetrics queryParseLatency,
            LatencyMetrics validationLatency,
            LatencyMetrics validationPerCandidateLatency,
            LatencyMetrics selectionLatency,
            LatencyMetrics addedLatency,
            List<SliceReport> slices) {
        ExperimentReport {
            slices = List.copyOf(slices);
        }
    }
}
