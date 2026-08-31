package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.AspectRequirement;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.DatasetSlice;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.ExpectedEvidence;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.GoldUnit;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.Query;
import com.prizm.search.evaluation.searchv3.typed.DeterministicTypedObservationExtractor;
import com.prizm.search.evaluation.searchv3.typed.DeterministicTypedQueryParser;
import com.prizm.search.evaluation.searchv3.typed.TypedConstraintEvaluator;
import com.prizm.search.evaluation.searchv3.typed.TypedConstraintStressDataset;
import com.prizm.search.evaluation.searchv3.typed.TypedStablePartitioner;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.CandidateObservation;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DateConstraint;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DateObservation;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DiagnosticReason;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.EvaluationResult;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.IdentifierNumberConstraint;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.IdentifierNumberObservation;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.LiteralIdentifierConstraint;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.LiteralIdentifierObservation;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.MatchState;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QuantityConstraint;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QuantityObservation;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QueryConstraint;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.SourceSlice;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Evaluation-only T0 Dense versus T1 typed stable-partition measurement.
 *
 * <p>The parser, extractor, evaluator, and partitioner see only {@link RuntimeQuery} and
 * {@link RuntimeCandidate}. Gold attachments stay in a separate map until ranking is complete.
 */
final class SearchV3TypedConstraintAblationEngine {

    static final String T0_PROFILE = "T0_B3_BGE_M3_RAW_DENSE";
    static final String T1_PROFILE = "T1_B3_TYPED_STABLE_PARTITION";
    private static final List<Integer> CUTOFFS = List.of(5, 10, 20);

    private final DeterministicTypedQueryParser queryParser;
    private final DeterministicTypedObservationExtractor observationExtractor;
    private final TypedConstraintEvaluator evaluator;
    private final TypedStablePartitioner partitioner;

    SearchV3TypedConstraintAblationEngine() {
        this(
                new DeterministicTypedQueryParser(),
                new DeterministicTypedObservationExtractor(),
                new TypedConstraintEvaluator(),
                new TypedStablePartitioner());
    }

    SearchV3TypedConstraintAblationEngine(
            DeterministicTypedQueryParser queryParser,
            DeterministicTypedObservationExtractor observationExtractor,
            TypedConstraintEvaluator evaluator,
            TypedStablePartitioner partitioner) {
        this.queryParser = Objects.requireNonNull(queryParser, "queryParser");
        this.observationExtractor = Objects.requireNonNull(observationExtractor, "observationExtractor");
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.partitioner = Objects.requireNonNull(partitioner, "partitioner");
    }

    ExperimentReport evaluate(SearchV3DenseAblationEngine.PassageDenseRun denseRun) {
        return evaluate(denseRun, List.of());
    }

    ExperimentReport evaluate(
            SearchV3DenseAblationEngine.PassageDenseRun denseRun,
            List<TypedConstraintStressDataset.DatasetSlice> stressSlices) {
        Objects.requireNonNull(denseRun, "denseRun");
        Objects.requireNonNull(stressSlices, "stressSlices");
        if (denseRun.slices().isEmpty()) {
            throw new IllegalArgumentException("typed ablation requires at least one B3 slice");
        }

        Map<String, TypedConstraintStressDataset.DatasetSlice> stressBySplit = indexStressSlices(
                denseRun.datasetVersion(), stressSlices);
        if (!stressBySplit.isEmpty()) {
            Set<String> denseSplits = denseRun.slices().stream()
                    .map(slice -> slice.dataset().split().manifestName())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!denseSplits.equals(stressBySplit.keySet())) {
                throw new IllegalArgumentException(
                        "typed stress and B3 splits must have exact one-to-one coverage: dense="
                                + denseSplits + ", stress=" + stressBySplit.keySet());
            }
        }
        List<SliceReport> sliceReports = new ArrayList<>();
        ExtractionAccumulator extraction = new ExtractionAccumulator();
        StateAccumulator states = new StateAccumulator();
        HardNegativeAccumulator hardNegatives = new HardNegativeAccumulator();
        List<Double> queryParseLatencies = new ArrayList<>();
        List<Double> observationParseLatencies = new ArrayList<>();
        List<Double> matchPartitionLatencies = new ArrayList<>();
        List<Double> onlineAddedLatencies = new ArrayList<>();
        List<Double> sharedEmbeddingLatencies = new ArrayList<>();
        List<Double> sharedDenseRankingLatencies = new ArrayList<>();
        List<Double> t0EndToEndLatencies = new ArrayList<>();
        List<Double> t1EndToEndLatencies = new ArrayList<>();
        long atomicSourceCount = 0L;
        long extractedObservationCount = 0L;
        long observationCacheCandidateCount = 0L;
        long observationCacheCanonicalPayloadUtf8Bytes = 0L;
        long parsedConstraintCount = 0L;
        long candidateParityQueryCount = 0L;
        long semanticQueryCount = 0L;
        long semanticExactParityCount = 0L;

        for (SearchV3DenseAblationEngine.PassageDenseSliceRun denseSlice : denseRun.slices()) {
            RuntimeProjection runtime = projectRuntime(denseSlice);
            GoldAttachments gold = attachGold(denseSlice);
            TypedConstraintStressDataset.DatasetSlice stress = stressBySplit.get(
                    denseSlice.dataset().split().manifestName());
            StressAttachment stressAttachment = stress == null
                    ? StressAttachment.empty()
                    : attachStress(runtime, denseSlice, stress);

            ObservationExtraction extractionRun = extractOnce(runtime.candidatesById());
            Map<String, List<CandidateObservation>> observations = extractionRun.observationsByCandidate();
            double observationLatency = extractionRun.candidateLatenciesMs().stream()
                    .mapToDouble(Double::doubleValue).sum();
            observationParseLatencies.addAll(extractionRun.candidateLatenciesMs());
            atomicSourceCount += runtime.candidatesById().values().stream()
                    .mapToLong(candidate -> candidate.atomicSources().size()).sum();
            extractedObservationCount += observations.values().stream().mapToLong(List::size).sum();
            observationCacheCandidateCount += observations.size();
            observationCacheCanonicalPayloadUtf8Bytes += observations.values().stream()
                    .flatMap(List::stream)
                    .map(this::canonicalObservation)
                    .mapToLong(value -> value.getBytes(StandardCharsets.UTF_8).length)
                    .sum();
            if (stress != null) {
                extraction.addObservationSets(
                        canonicalPredictedObservations(observations),
                        canonicalExpectedObservations(stress));
            }

            List<QueryReport> queryReports = new ArrayList<>();
            long semanticQueries = 0L;
            for (RuntimeRanking runtimeRanking : runtime.rankings()) {
                RuntimeQuery runtimeQuery = runtimeRanking.query();
                GoldQuery goldQuery = gold.queriesById().get(runtimeQuery.queryId());
                if (goldQuery == null) {
                    throw new IllegalStateException("Missing query Gold attachment: " + runtimeQuery.queryId());
                }

                long parseStarted = System.nanoTime();
                List<QueryConstraint> constraints = queryParser.parse(runtimeQuery.text());
                double parseLatency = nanosToMillis(System.nanoTime() - parseStarted);
                queryParseLatencies.add(parseLatency);
                parsedConstraintCount += constraints.size();
                sharedEmbeddingLatencies.add(runtimeRanking.queryEmbeddingLatencyMs());
                sharedDenseRankingLatencies.add(runtimeRanking.denseRankingLatencyMs());
                if (stress != null) {
                    extraction.addQuerySets(
                            runtimeQuery,
                            constraints,
                            stress.evaluationGold().queryAnnotations().get(runtimeQuery.queryId()));
                }

                long partitionStarted = System.nanoTime();
                Map<String, EvaluationResult> evaluationByCandidate = new LinkedHashMap<>();
                Map<String, MatchState> stateByCandidate = new LinkedHashMap<>();
                for (RuntimeCandidate candidate : runtimeRanking.t0Candidates()) {
                    EvaluationResult evaluation = evaluator.evaluateAllDetailed(
                            constraints, observations.get(candidate.candidateId()));
                    evaluationByCandidate.put(candidate.candidateId(), evaluation);
                    stateByCandidate.put(candidate.candidateId(), evaluation.state());
                }
                List<RuntimeCandidate> t1Candidates = partitioner.partitionEvaluated(
                        runtimeRanking.t0Candidates(),
                        !constraints.isEmpty(),
                        candidate -> stateByCandidate.get(candidate.candidateId()));
                double partitionLatency = nanosToMillis(System.nanoTime() - partitionStarted);
                matchPartitionLatencies.add(partitionLatency);
                onlineAddedLatencies.add(parseLatency + partitionLatency);
                double sharedLatency = runtimeRanking.queryEmbeddingLatencyMs()
                        + runtimeRanking.denseRankingLatencyMs();
                t0EndToEndLatencies.add(sharedLatency);
                t1EndToEndLatencies.add(sharedLatency + parseLatency + partitionLatency);
                assertCandidateParity(runtimeRanking.t0Candidates(), t1Candidates);
                candidateParityQueryCount++;

                List<String> t0Ids = ids(runtimeRanking.t0Candidates());
                List<String> t1Ids = ids(t1Candidates);
                boolean semanticOrderParity = constraints.isEmpty() && t0Ids.equals(t1Ids);
                if (constraints.isEmpty()) {
                    semanticQueries++;
                    semanticQueryCount++;
                    if (!semanticOrderParity) {
                        throw new IllegalStateException("semantic/no-constraint T0/T1 order changed: "
                                + runtimeQuery.queryId());
                    }
                    semanticExactParityCount++;
                }
                assertStableStateOrder(runtimeRanking.t0Candidates(), t1Candidates, stateByCandidate, constraints);
                Map<String, Integer> denseRankByCandidate = listPositions(runtimeRanking.t0Candidates());

                ProfileResult t0 = score(
                        runtimeRanking.t0Candidates(), goldQuery.query(), gold.dataset(), gold.candidatesById(),
                        stateByCandidate);
                ProfileResult t1 = score(
                        t1Candidates, goldQuery.query(), gold.dataset(), gold.candidatesById(), stateByCandidate);
                String directOutcome = directOutcome(
                        goldQuery.query().hasDirectSupport(), t0.firstDirectRank(), t1.firstDirectRank());
                TypedConstraintStressDataset.TypedQueryAnnotation annotation = stress == null
                        ? null
                        : stress.evaluationGold().queryAnnotations().get(runtimeQuery.queryId());
                MatchState t0ExpectedRank1State = annotation == null ? null : expectedCandidateState(
                        annotation, requiredGold(runtimeRanking.t0Candidates().get(0), gold.candidatesById()));
                MatchState t1ExpectedRank1State = annotation == null ? null : expectedCandidateState(
                        annotation, requiredGold(t1Candidates.get(0), gold.candidatesById()));
                String typedKind = annotation == null ? "" : annotation.constraint().kind();
                String primaryFamily = annotation == null ? "" : annotation.primaryFamily();
                List<String> typedFamilies = annotation == null ? List.of() : annotation.stressFamilies();
                QueryReport report = new QueryReport(
                        denseRun.datasetVersion(),
                        denseSlice.dataset().split().manifestName(),
                        runtimeQuery.queryId(),
                        runtimeQuery.userBundleId(),
                        runtimeRanking.professionGroup(),
                        runtimeQuery.language(),
                        goldQuery.query().hasDirectSupport(),
                        constraints.size(),
                        semanticOrderParity,
                        typedKind,
                        primaryFamily,
                        typedFamilies,
                        runtimeRanking.queryEmbeddingLatencyMs(),
                        runtimeRanking.denseRankingLatencyMs(),
                        parseLatency,
                        partitionLatency,
                        t0,
                        t1,
                        directOutcome,
                        t0ExpectedRank1State,
                        t1ExpectedRank1State,
                        rankedCandidates(runtimeRanking.t0Candidates(), evaluationByCandidate,
                                denseRankByCandidate, runtimeRanking.denseScoreByCandidate()),
                        rankedCandidates(t1Candidates, evaluationByCandidate,
                                denseRankByCandidate, runtimeRanking.denseScoreByCandidate()));
                queryReports.add(report);
                if (annotation != null) {
                    states.add(
                            annotation,
                            constraints,
                            observations,
                            stress.evaluationGold().units(),
                            evaluator);
                }
                if (isHardNegative(goldQuery.query(), annotation)) {
                    String t0CandidateId = t0Ids.get(0);
                    String t1CandidateId = t1Ids.get(0);
                    hardNegatives.add(
                            stateByCandidate.get(t0CandidateId),
                            stateByCandidate.get(t1CandidateId),
                            t0ExpectedRank1State,
                            t1ExpectedRank1State);
                }
            }
            sliceReports.add(new SliceReport(
                    denseSlice.dataset().split().manifestName(),
                    observationLatency,
                    semanticQueries,
                    queryReports.size(),
                    List.copyOf(queryReports)));
            if (stress != null) {
                assertExpectedObservationsHaveOneAtomicChild(stressAttachment);
            }
        }

        List<QueryReport> allQueries = sliceReports.stream()
                .flatMap(slice -> slice.queries().stream())
                .toList();
        return new ExperimentReport(
                1,
                denseRun.phase(),
                denseRun.datasetVersion(),
                denseRun.model(),
                T0_PROFILE,
                T1_PROFILE,
                aggregate(allQueries),
                macro(allQueries, query -> query.datasetVersion() + ":" + query.userBundleId()),
                grouped(allQueries, QueryReport::professionGroup),
                grouped(allQueries, QueryReport::language),
                grouped(allQueries, QueryReport::split),
                groupedNonBlank(allQueries, QueryReport::typedKind),
                groupedNonBlank(allQueries, QueryReport::primaryFamily),
                groupedFamilies(allQueries),
                extraction.metrics(),
                states.metrics(),
                hardNegatives.metrics(),
                new LatencyReport(
                        latency(queryParseLatencies),
                        latency(observationParseLatencies),
                        latency(matchPartitionLatencies),
                        latency(onlineAddedLatencies),
                        latency(sharedEmbeddingLatencies),
                        latency(sharedDenseRankingLatencies),
                        latency(t0EndToEndLatencies),
                        latency(t1EndToEndLatencies)),
                new RuntimeCost(
                        candidateParityQueryCount,
                        semanticQueryCount,
                        semanticExactParityCount,
                        atomicSourceCount,
                        extractedObservationCount,
                        parsedConstraintCount,
                        observationCacheCandidateCount,
                        observationCacheCanonicalPayloadUtf8Bytes,
                        "NOT_MEASURED",
                        0,
                        0),
                List.copyOf(sliceReports));
    }

    private RuntimeProjection projectRuntime(SearchV3DenseAblationEngine.PassageDenseSliceRun denseSlice) {
        Map<String, RuntimeCandidate> candidates = new LinkedHashMap<>();
        Set<String> atomicChildIds = new HashSet<>();
        denseSlice.passageById().forEach((candidateId, passage) -> {
            if (!candidateId.equals(passage.passageId())) {
                throw new IllegalStateException("B3 passage map key/identity mismatch: " + candidateId);
            }
            List<SourceSlice> sources = new ArrayList<>();
            for (EvidenceChild child : passage.evidenceChildren()) {
                SourceProvenance provenance = child.provenance();
                if (!atomicChildIds.add(child.childId())) {
                    throw new IllegalStateException("atomic child appears in multiple passages: " + child.childId());
                }
                sources.add(new SourceSlice(
                        provenance.documentId(),
                        provenance.versionId(),
                        child.childId(),
                        provenance.page(),
                        provenance.codePointStart(),
                        child.sourceText()));
            }
            RuntimeCandidate candidate = new RuntimeCandidate(
                    candidateId,
                    passage.documentId(),
                    passage.versionId(),
                    List.copyOf(sources));
            if (candidates.put(candidateId, candidate) != null) {
                throw new IllegalStateException("duplicate runtime candidate: " + candidateId);
            }
        });

        List<RuntimeRanking> rankings = new ArrayList<>();
        Set<String> queryIds = new HashSet<>();
        for (SearchV3DenseAblationEngine.PassageDenseQueryRanking ranking : denseSlice.queries()) {
            Query query = ranking.query();
            RuntimeQuery runtimeQuery = new RuntimeQuery(
                    query.queryId(), query.userBundleId(), query.text(), query.language());
            if (!queryIds.add(runtimeQuery.queryId())) {
                throw new IllegalStateException("duplicate runtime query: " + runtimeQuery.queryId());
            }
            for (int index = 0; index < ranking.fullRanking().size(); index++) {
                if (ranking.fullRanking().get(index).rank() != index + 1) {
                    throw new IllegalStateException("B3 dense rank does not match list position: "
                            + runtimeQuery.queryId());
                }
            }
            Map<String, Double> denseScoreByCandidate = ranking.fullRanking().stream()
                    .collect(Collectors.toMap(
                            SearchV3DenseAblationEngine.RankedCandidate::candidateId,
                            SearchV3DenseAblationEngine.RankedCandidate::cosineScore,
                            (left, right) -> {
                                throw new IllegalStateException("duplicate dense candidate score identity: "
                                        + runtimeQuery.queryId());
                            },
                            LinkedHashMap::new));
            if (denseScoreByCandidate.values().stream().anyMatch(score -> !Double.isFinite(score))) {
                throw new IllegalStateException("B3 dense ranking contains a non-finite score: "
                        + runtimeQuery.queryId());
            }
            List<RuntimeCandidate> ordered = ranking.fullRanking().stream()
                    .map(value -> candidates.get(value.candidateId()))
                    .toList();
            if (ordered.stream().anyMatch(Objects::isNull)
                    || ordered.size() != ranking.fullRanking().size()
                    || ordered.stream().map(RuntimeCandidate::candidateId).distinct().count() != ordered.size()) {
                throw new IllegalStateException("B3 runtime projection lost candidate identity: "
                        + runtimeQuery.queryId());
            }
            Set<String> activeVersions = denseSlice.dataset().bundles().stream()
                    .filter(bundle -> bundle.userBundleId().equals(runtimeQuery.userBundleId()))
                    .flatMap(bundle -> bundle.activeDocuments().stream())
                    .map(SearchV3DenseAblationDataset.SourceDocument::versionId)
                    .collect(Collectors.toSet());
            Set<String> expectedOwnerCandidates = denseSlice.passageById().values().stream()
                    .filter(passage -> activeVersions.contains(passage.versionId()))
                    .map(RetrievalPassage::passageId)
                    .collect(Collectors.toSet());
            if (!expectedOwnerCandidates.equals(new HashSet<>(ids(ordered)))) {
                throw new IllegalStateException("B3 ranking is not a full owner-scoped candidate set: "
                        + runtimeQuery.queryId());
            }
            rankings.add(new RuntimeRanking(
                    runtimeQuery,
                    ranking.professionGroup(),
                    ranking.queryEmbeddingLatencyMs(),
                    ranking.denseRankingLatencyMs(),
                    Map.copyOf(denseScoreByCandidate),
                    List.copyOf(ordered)));
        }
        return new RuntimeProjection(Map.copyOf(candidates), List.copyOf(rankings));
    }

    private GoldAttachments attachGold(SearchV3DenseAblationEngine.PassageDenseSliceRun denseSlice) {
        Map<String, GoldCandidate> candidates = new LinkedHashMap<>();
        for (SearchV3DenseAblationEngine.PassageDenseQueryRanking queryRanking : denseSlice.queries()) {
            for (SearchV3DenseAblationEngine.RankedCandidate candidate : queryRanking.fullRanking()) {
                GoldCandidate attachment = new GoldCandidate(
                        candidate.candidateId(),
                        List.copyOf(candidate.coveredUnitIds()),
                        List.copyOf(candidate.coveredGroupIds()),
                        List.copyOf(candidate.coveredParentIds()));
                GoldCandidate previous = candidates.putIfAbsent(candidate.candidateId(), attachment);
                if (previous != null && !previous.equals(attachment)) {
                    throw new IllegalStateException("candidate Gold attachment changed across queries: "
                            + candidate.candidateId());
                }
            }
        }
        Map<String, GoldQuery> queries = denseSlice.queries().stream()
                .collect(Collectors.toMap(
                        value -> value.query().queryId(),
                        value -> new GoldQuery(value.query()),
                        (left, right) -> {
                            throw new IllegalStateException("duplicate query Gold attachment");
                        },
                        LinkedHashMap::new));
        return new GoldAttachments(denseSlice.dataset(), Map.copyOf(candidates), Map.copyOf(queries));
    }

    private ObservationExtraction extractOnce(Map<String, RuntimeCandidate> candidates) {
        Map<String, List<CandidateObservation>> result = new LinkedHashMap<>();
        List<Double> candidateLatencies = new ArrayList<>();
        candidates.forEach((candidateId, candidate) -> {
            long started = System.nanoTime();
            result.put(candidateId, observationExtractor.extractAll(candidate.atomicSources()));
            candidateLatencies.add(nanosToMillis(System.nanoTime() - started));
        });
        return new ObservationExtraction(Map.copyOf(result), List.copyOf(candidateLatencies));
    }

    private StressAttachment attachStress(
            RuntimeProjection runtime,
            SearchV3DenseAblationEngine.PassageDenseSliceRun denseSlice,
            TypedConstraintStressDataset.DatasetSlice stress) {
        List<TypedConstraintStressDataset.EvaluationPassage> passages = denseSlice.passageById().values().stream()
                .map(passage -> new TypedConstraintStressDataset.EvaluationPassage(
                        passage.passageId(),
                        ownerForPassage(denseSlice, passage.passageId()),
                        passage.documentId(),
                        passage.versionId(),
                        passage.retrievalText(),
                        passage.evidenceChildren().stream().map(child -> {
                            SourceProvenance source = child.provenance();
                            return new TypedConstraintStressDataset.EvaluationChildSlice(
                                    child.childId(),
                                    source.documentId(),
                                    source.versionId(),
                                    source.sourcePath(),
                                    source.page(),
                                    source.codePointStart(),
                                    source.codePointEnd(),
                                    child.sourceText(),
                                    source.parentAnnotationCandidateId(),
                                    source.documentSourceSha256(),
                                    source.exactTextSha256());
                        }).toList()))
                .toList();
        TypedConstraintStressDataset.AttachedEvaluation attached = stress.attachPassages(passages);
        if (!runtime.candidatesById().keySet().equals(attached.passagesById().keySet())) {
            throw new IllegalStateException("stress attachment changed B3 candidate identities");
        }
        Map<String, TypedConstraintStressDataset.RuntimeQuestion> stressQueries = stress.runtimeInputs()
                .questions().stream().collect(Collectors.toMap(
                        TypedConstraintStressDataset.RuntimeQuestion::queryId, Function.identity()));
        if (!stressQueries.keySet().equals(runtime.rankings().stream()
                .map(ranking -> ranking.query().queryId()).collect(Collectors.toSet()))) {
            throw new IllegalStateException("stress and B3 query identities differ");
        }
        for (RuntimeRanking ranking : runtime.rankings()) {
            TypedConstraintStressDataset.RuntimeQuestion expected = stressQueries.get(ranking.query().queryId());
            if (!ranking.query().userBundleId().equals(expected.userBundleId())
                    || !ranking.query().text().equals(expected.text())
                    || !ranking.query().language().equals(expected.language())) {
                throw new IllegalStateException("stress and B3 runtime query differ: " + expected.queryId());
            }
        }
        return new StressAttachment(stress, attached);
    }

    private String ownerForPassage(
            SearchV3DenseAblationEngine.PassageDenseSliceRun denseSlice,
            String passageId) {
        return denseSlice.dataset().bundles().stream()
                .filter(bundle -> denseSlice.queries().stream()
                        .filter(query -> query.query().userBundleId().equals(bundle.userBundleId()))
                        .flatMap(query -> query.fullRanking().stream())
                        .anyMatch(candidate -> candidate.candidateId().equals(passageId)))
                .map(SearchV3DenseAblationDataset.UserBundle::userBundleId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("passage has no owner-scoped ranking: " + passageId));
    }

    private void assertExpectedObservationsHaveOneAtomicChild(StressAttachment attachment) {
        if (attachment.stress() == null) return;
        for (TypedConstraintStressDataset.ObservationAnnotation observation
                : attachment.stress().evaluationGold().observations().values()) {
            TypedConstraintStressDataset.EvidenceUnit unit = attachment.stress()
                    .evaluationGold().units().get(observation.evidenceUnitId());
            long matches = attachment.attached().passagesById().values().stream()
                    .flatMap(passage -> passage.evidenceChildren().stream())
                    .filter(child -> child.documentId().equals(unit.documentId()))
                    .filter(child -> child.versionId().equals(unit.versionId()))
                    .filter(child -> child.codePointStart() <= observation.charStart())
                    .filter(child -> child.codePointEnd() >= observation.charEnd())
                    .count();
            if (matches != 1L) {
                throw new IllegalStateException("expected observation must map to one atomic child: "
                        + observation.observationId() + " matches=" + matches);
            }
        }
    }

    private Map<String, TypedConstraintStressDataset.DatasetSlice> indexStressSlices(
            String denseDatasetVersion,
            List<TypedConstraintStressDataset.DatasetSlice> stressSlices) {
        Map<String, TypedConstraintStressDataset.DatasetSlice> result = new LinkedHashMap<>();
        for (TypedConstraintStressDataset.DatasetSlice slice : stressSlices) {
            if (!denseDatasetVersion.equals(slice.datasetVersion())) {
                throw new IllegalArgumentException("B3 and typed stress dataset versions differ");
            }
            if (result.put(slice.split().name(), slice) != null) {
                throw new IllegalArgumentException("duplicate typed stress split: " + slice.split());
            }
        }
        return Map.copyOf(result);
    }

    private ProfileResult score(
            List<RuntimeCandidate> ranking,
            Query query,
            DatasetSlice dataset,
            Map<String, GoldCandidate> goldByCandidate,
            Map<String, MatchState> stateByCandidate) {
        Set<String> directUnits = query.allExpectedEvidence().stream()
                .filter(value -> "DIRECT_SUPPORT".equals(value.supportRelation()))
                .map(ExpectedEvidence::evidenceUnitId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Integer first = null;
        for (int index = 0; index < ranking.size(); index++) {
            GoldCandidate gold = requiredGold(ranking.get(index), goldByCandidate);
            if (gold.coveredUnitIds().stream().anyMatch(directUnits::contains)) {
                first = index + 1;
                break;
            }
        }
        Map<Integer, Boolean> recall = new LinkedHashMap<>();
        for (int cutoff : CUTOFFS) {
            Set<String> hit = ranking.stream().limit(cutoff)
                    .map(candidate -> requiredGold(candidate, goldByCandidate))
                    .flatMap(candidate -> candidate.coveredUnitIds().stream())
                    .filter(directUnits::contains)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            recall.put(cutoff, !directUnits.isEmpty() && requirementsMet(query, hit, dataset));
        }
        return new ProfileResult(
                ranking.size(),
                first,
                first != null && first == 1,
                first == null ? 0.0d : 1.0d / first,
                Map.copyOf(recall),
                ndcgAt5(ranking, directUnits, dataset, goldByCandidate),
                ranking.isEmpty() ? null : stateByCandidate.get(ranking.get(0).candidateId()));
    }

    private boolean requirementsMet(Query query, Set<String> hitUnits, DatasetSlice dataset) {
        List<AspectRequirement> directAspects = query.aspects().stream()
                .filter(aspect -> aspect.expectedEvidence().stream()
                        .anyMatch(value -> "DIRECT_SUPPORT".equals(value.supportRelation())))
                .toList();
        if (directAspects.isEmpty()) return false;
        Map<String, Boolean> met = new LinkedHashMap<>();
        for (AspectRequirement aspect : directAspects) {
            Set<String> hitGroups = aspect.expectedEvidence().stream()
                    .filter(value -> "DIRECT_SUPPORT".equals(value.supportRelation()))
                    .map(ExpectedEvidence::evidenceUnitId)
                    .filter(hitUnits::contains)
                    .map(dataset.units()::get)
                    .map(GoldUnit::groupId)
                    .collect(Collectors.toSet());
            boolean explicit = hitGroups.containsAll(Set.copyOf(aspect.requiredEvidenceGroupIds()));
            met.put(aspect.aspectId(), explicit && hitGroups.size() >= Math.max(1, aspect.minEvidenceGroups()));
        }
        List<String> required = query.aspectExpression().requiredAspectIds().stream()
                .filter(met::containsKey).toList();
        if (required.isEmpty()) required = directAspects.stream().map(AspectRequirement::aspectId).toList();
        long count = required.stream().filter(id -> Boolean.TRUE.equals(met.get(id))).count();
        if ("ALL".equals(query.aspectExpression().operator())) return count == required.size();
        return count >= Math.min(query.aspectExpression().minShouldMatch(), required.size());
    }

    private double ndcgAt5(
            List<RuntimeCandidate> ranking,
            Set<String> directUnits,
            DatasetSlice dataset,
            Map<String, GoldCandidate> goldByCandidate) {
        List<Integer> relevance = noveltyRelevance(ranking, directUnits, dataset, goldByCandidate, false);
        List<Integer> idealRelevance = noveltyRelevance(ranking, directUnits, dataset, goldByCandidate, true);
        double dcg = dcg(relevance.stream().limit(5).toList());
        double ideal = dcg(idealRelevance.stream().limit(5).toList());
        return ideal == 0.0d ? 0.0d : dcg / ideal;
    }

    private List<Integer> noveltyRelevance(
            List<RuntimeCandidate> candidates,
            Set<String> directUnits,
            DatasetSlice dataset,
            Map<String, GoldCandidate> goldByCandidate,
            boolean greedyIdeal) {
        List<CandidateCoverage> coverages = candidates.stream()
                .map(candidate -> new CandidateCoverage(
                        candidate.candidateId(), requiredGold(candidate, goldByCandidate).coveredUnitIds()))
                .toList();
        Map<String, String> groups = directUnits.stream().collect(Collectors.toMap(
                Function.identity(), unit -> dataset.units().get(unit).groupId()));
        return noveltyRelevance(coverages, directUnits, groups, greedyIdeal);
    }

    static List<Integer> noveltyRelevance(
            List<CandidateCoverage> candidates,
            Set<String> directUnits,
            Map<String, String> groupByUnit,
            boolean greedyIdeal) {
        if (greedyIdeal) {
            IdealSequence ideal = idealSequence(
                    candidates.stream().sorted(Comparator.comparing(CandidateCoverage::candidateId)).toList(),
                    directUnits,
                    groupByUnit,
                    Math.min(5, candidates.size()),
                    0,
                    Set.of(),
                    new HashMap<>());
            List<Integer> padded = new ArrayList<>(ideal.relevances());
            while (padded.size() < candidates.size()) padded.add(0);
            return List.copyOf(padded);
        }
        List<CandidateCoverage> remaining = new ArrayList<>(candidates);
        List<Integer> result = new ArrayList<>();
        Set<String> creditedGroups = new HashSet<>();
        while (!remaining.isEmpty()) {
            CandidateCoverage selected = remaining.get(0);
            int relevance = novelUnitCount(selected, directUnits, creditedGroups, groupByUnit);
            result.add(relevance);
            directGroups(selected, directUnits, groupByUnit).forEach(creditedGroups::add);
            remaining.remove(selected);
        }
        return List.copyOf(result);
    }

    private static IdealSequence idealSequence(
            List<CandidateCoverage> candidates,
            Set<String> directUnits,
            Map<String, String> groupByUnit,
            int limit,
            int depth,
            Set<String> creditedGroups,
            Map<String, IdealSequence> memo) {
        if (depth >= limit) return new IdealSequence(0.0d, List.of());
        String key = depth + "|" + creditedGroups.stream().sorted().collect(Collectors.joining(","));
        IdealSequence cached = memo.get(key);
        if (cached != null) return cached;

        IdealSequence best = new IdealSequence(0.0d, List.of());
        for (CandidateCoverage candidate : candidates) {
            int relevance = novelUnitCount(candidate, directUnits, creditedGroups, groupByUnit);
            if (relevance == 0) continue;
            Set<String> nextGroups = new HashSet<>(creditedGroups);
            nextGroups.addAll(directGroups(candidate, directUnits, groupByUnit));
            IdealSequence suffix = idealSequence(
                    candidates, directUnits, groupByUnit, limit, depth + 1, Set.copyOf(nextGroups), memo);
            double score = discountedGain(relevance, depth) + suffix.score();
            if (score > best.score() + 1.0e-15d) {
                List<Integer> values = new ArrayList<>();
                values.add(relevance);
                values.addAll(suffix.relevances());
                best = new IdealSequence(score, List.copyOf(values));
            }
        }
        memo.put(key, best);
        return best;
    }

    private static int novelUnitCount(
            CandidateCoverage candidate,
            Set<String> directUnits,
            Set<String> creditedGroups,
            Map<String, String> groupByUnit) {
        return (int) candidate.coveredUnitIds().stream()
                .filter(directUnits::contains)
                .filter(unitId -> !creditedGroups.contains(groupByUnit.get(unitId)))
                .distinct()
                .count();
    }

    private static Set<String> directGroups(
            CandidateCoverage candidate,
            Set<String> directUnits,
            Map<String, String> groupByUnit) {
        return candidate.coveredUnitIds().stream()
                .filter(directUnits::contains)
                .map(groupByUnit::get)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private double dcg(List<Integer> relevance) {
        double value = 0.0d;
        for (int index = 0; index < relevance.size(); index++) {
            value += discountedGain(relevance.get(index), index);
        }
        return value;
    }

    private static double discountedGain(int relevance, int zeroBasedRank) {
        return (Math.pow(2.0d, relevance) - 1.0d)
                / (Math.log(zeroBasedRank + 2.0d) / Math.log(2.0d));
    }

    private GoldCandidate requiredGold(
            RuntimeCandidate candidate,
            Map<String, GoldCandidate> goldByCandidate) {
        GoldCandidate value = goldByCandidate.get(candidate.candidateId());
        if (value == null) throw new IllegalStateException("missing candidate Gold: " + candidate.candidateId());
        return value;
    }

    private ComparisonMetrics aggregate(List<QueryReport> queries) {
        List<QueryReport> direct = queries.stream()
                .filter(QueryReport::directSupport)
                .toList();
        return comparison(
                direct,
                queries.size(),
                direct.size(),
                queries.stream().filter(query -> "WIN".equals(query.directOutcome())).count(),
                queries.stream().filter(query -> "LOSS".equals(query.directOutcome())).count(),
                queries.stream().filter(query -> "TIE".equals(query.directOutcome())).count(),
                queries.stream().filter(query -> query.t0().top1() && !query.t1().top1()).count());
    }

    private ComparisonMetrics macro(
            List<QueryReport> queries,
            Function<QueryReport, String> groupKey) {
        Map<String, List<QueryReport>> groups = queries.stream().collect(Collectors.groupingBy(
                groupKey, LinkedHashMap::new, Collectors.toList()));
        List<ComparisonMetrics> values = groups.values().stream().map(this::aggregate).toList();
        List<ProfileMetrics> t0 = values.stream().filter(value -> value.directQueryCount() > 0)
                .map(ComparisonMetrics::t0).toList();
        List<ProfileMetrics> t1 = values.stream().filter(value -> value.directQueryCount() > 0)
                .map(ComparisonMetrics::t1).toList();
        return new ComparisonMetrics(
                groups.size(),
                values.stream().filter(value -> value.directQueryCount() > 0).count(),
                averageProfiles(t0),
                averageProfiles(t1),
                values.stream().mapToLong(ComparisonMetrics::directWins).sum(),
                values.stream().mapToLong(ComparisonMetrics::directLosses).sum(),
                values.stream().mapToLong(ComparisonMetrics::directTies).sum(),
                values.stream().mapToLong(ComparisonMetrics::directRank1Losses).sum());
    }

    private ComparisonMetrics comparison(
            List<QueryReport> direct,
            long queryCount,
            long directCount,
            long wins,
            long losses,
            long ties,
            long directRank1Losses) {
        return new ComparisonMetrics(
                queryCount,
                directCount,
                profile(direct, QueryReport::t0),
                profile(direct, QueryReport::t1),
                wins,
                losses,
                ties,
                directRank1Losses);
    }

    private ProfileMetrics profile(List<QueryReport> direct, Function<QueryReport, ProfileResult> getter) {
        Map<Integer, Double> recall = new LinkedHashMap<>();
        for (int cutoff : CUTOFFS) {
            recall.put(cutoff, direct.isEmpty() ? 0.0d : direct.stream()
                    .map(getter).filter(value -> value.recallAtK().get(cutoff)).count() / (double) direct.size());
        }
        return new ProfileMetrics(
                direct.isEmpty() ? 0.0d : direct.stream().map(getter).filter(ProfileResult::top1).count()
                        / (double) direct.size(),
                direct.stream().map(getter).mapToDouble(ProfileResult::mrr).average().orElse(0.0d),
                direct.stream().map(getter).mapToDouble(ProfileResult::ndcgAt5).average().orElse(0.0d),
                Map.copyOf(recall));
    }

    private ProfileMetrics averageProfiles(List<ProfileMetrics> values) {
        Map<Integer, Double> recall = new LinkedHashMap<>();
        for (int cutoff : CUTOFFS) {
            recall.put(cutoff, values.stream()
                    .mapToDouble(value -> value.recallAtK().get(cutoff)).average().orElse(0.0d));
        }
        return new ProfileMetrics(
                values.stream().mapToDouble(ProfileMetrics::top1).average().orElse(0.0d),
                values.stream().mapToDouble(ProfileMetrics::mrr).average().orElse(0.0d),
                values.stream().mapToDouble(ProfileMetrics::ndcgAt5).average().orElse(0.0d),
                Map.copyOf(recall));
    }

    private Map<String, ComparisonMetrics> grouped(
            List<QueryReport> queries,
            Function<QueryReport, String> key) {
        Map<String, List<QueryReport>> grouped = queries.stream().collect(Collectors.groupingBy(
                key, LinkedHashMap::new, Collectors.toList()));
        Map<String, ComparisonMetrics> result = new LinkedHashMap<>();
        grouped.forEach((name, values) -> result.put(name, aggregate(values)));
        return Map.copyOf(result);
    }

    private Map<String, ComparisonMetrics> groupedNonBlank(
            List<QueryReport> queries,
            Function<QueryReport, String> key) {
        return grouped(queries.stream().filter(query -> !key.apply(query).isBlank()).toList(), key);
    }

    private Map<String, ComparisonMetrics> groupedFamilies(List<QueryReport> queries) {
        Set<String> families = queries.stream().flatMap(query -> query.typedFamilies().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, ComparisonMetrics> result = new LinkedHashMap<>();
        for (String family : families) {
            result.put(family, aggregate(queries.stream()
                    .filter(query -> query.typedFamilies().contains(family)).toList()));
        }
        return Map.copyOf(result);
    }

    private String directOutcome(boolean directSupport, Integer t0, Integer t1) {
        if (!directSupport) return "NOT_APPLICABLE";
        if (t0 == null && t1 == null) return "TIE";
        if (t0 == null) return "WIN";
        if (t1 == null) return "LOSS";
        return t1 < t0 ? "WIN" : t1 > t0 ? "LOSS" : "TIE";
    }

    private boolean isHardNegative(
            Query query,
            TypedConstraintStressDataset.TypedQueryAnnotation annotation) {
        return annotation != null
                && "NOT_SUPPORTED".equals(query.answerability());
    }

    private MatchState expectedCandidateState(
            TypedConstraintStressDataset.TypedQueryAnnotation annotation,
            GoldCandidate candidate) {
        Map<String, MatchState> expectedByUnit = annotation.expectedEvidenceStates().stream()
                .collect(Collectors.toMap(
                        TypedConstraintStressDataset.ExpectedEvidenceState::evidenceUnitId,
                        value -> MatchState.valueOf(value.state())));
        List<MatchState> states = candidate.coveredUnitIds().stream()
                .map(expectedByUnit::get)
                .filter(Objects::nonNull)
                .toList();
        if (states.contains(MatchState.SATISFIED)) return MatchState.SATISFIED;
        if (states.contains(MatchState.CONTRADICTED)) return MatchState.CONTRADICTED;
        return MatchState.UNKNOWN;
    }

    private List<RankedCandidateResult> rankedCandidates(
            List<RuntimeCandidate> ranking,
            Map<String, EvaluationResult> evaluations,
            Map<String, Integer> denseRankByCandidate,
            Map<String, Double> denseScoreByCandidate) {
        List<RankedCandidateResult> result = new ArrayList<>();
        for (int index = 0; index < ranking.size(); index++) {
            RuntimeCandidate candidate = ranking.get(index);
            result.add(new RankedCandidateResult(
                    index + 1,
                    denseRankByCandidate.get(candidate.candidateId()),
                    candidate.candidateId(),
                    denseScoreByCandidate.get(candidate.candidateId()),
                    evaluations.get(candidate.candidateId()).state(),
                    evaluations.get(candidate.candidateId()).reasons()));
        }
        return List.copyOf(result);
    }

    private Map<String, Integer> listPositions(List<RuntimeCandidate> ranking) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < ranking.size(); index++) {
            result.put(ranking.get(index).candidateId(), index + 1);
        }
        return Map.copyOf(result);
    }

    private void assertCandidateParity(List<RuntimeCandidate> t0, List<RuntimeCandidate> t1) {
        List<String> left = ids(t0);
        List<String> right = ids(t1);
        if (left.size() != new HashSet<>(left).size()
                || right.size() != new HashSet<>(right).size()
                || !new HashSet<>(left).equals(new HashSet<>(right))) {
            throw new IllegalStateException("T0/T1 candidate identity parity failed");
        }
    }

    private void assertStableStateOrder(
            List<RuntimeCandidate> t0,
            List<RuntimeCandidate> t1,
            Map<String, MatchState> states,
            List<QueryConstraint> constraints) {
        if (constraints.isEmpty()) return;
        List<MatchState> order = List.of(MatchState.SATISFIED, MatchState.UNKNOWN, MatchState.CONTRADICTED);
        List<String> expected = order.stream()
                .flatMap(state -> t0.stream().filter(candidate -> states.get(candidate.candidateId()) == state))
                .map(RuntimeCandidate::candidateId).toList();
        if (!expected.equals(ids(t1))) {
            throw new IllegalStateException("typed result is not a stable SAT/UNKNOWN/CONTR partition");
        }
    }

    private List<String> ids(List<RuntimeCandidate> candidates) {
        return candidates.stream().map(RuntimeCandidate::candidateId).toList();
    }

    private Set<String> canonicalPredictedObservations(
            Map<String, List<CandidateObservation>> observations) {
        return observations.values().stream().flatMap(List::stream)
                .map(this::canonicalObservation).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<String> canonicalExpectedObservations(TypedConstraintStressDataset.DatasetSlice stress) {
        Set<String> result = new LinkedHashSet<>();
        for (TypedConstraintStressDataset.ObservationAnnotation observation
                : stress.evaluationGold().observations().values()) {
            TypedConstraintStressDataset.EvidenceUnit unit = stress.evaluationGold().units()
                    .get(observation.evidenceUnitId());
            result.add(canonicalObservation(observation, unit));
        }
        return result;
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
            RuntimeQuery query,
            TypedConstraintStressDataset.ConstraintAnnotation value) {
        String base = value.kind() + "|" + value.sourceSurface() + "@" + value.queryCharStart()
                + ":" + value.queryCharEnd();
        String qualifier = canonicalExpectedQualifier(
                query.text(), value.qualifier(), value.qualifierCharStart(), value.qualifierCharEnd());
        return switch (value.kind()) {
            case "QUANTITY" -> base + "|" + value.operator() + "|" + decimal(value.value()) + "|"
                    + decimal(value.upperValue()) + "|" + value.normalizedUnit() + "|" + qualifier + "|"
                    + canonicalExpectedDirection(query.text(), value);
            case "DATE" -> base + "|" + value.operator() + "|"
                    + canonicalExpectedDate(value) + "|" + qualifier;
            case "IDENTIFIER_NUMBER" -> base + "|" + normalizeCaptured(value.identifier()) + "|"
                    + value.numberSurface() + "|" + value.normalizedSegments().stream()
                            .map(String::valueOf).collect(Collectors.joining("."));
            case "LITERAL_IDENTIFIER" -> base + "|" + value.normalizedLiteral();
            default -> throw new IllegalArgumentException("unknown expected constraint kind: " + value.kind());
        };
    }

    private String canonicalObservation(CandidateObservation value) {
        String base = value.source().documentId() + "|" + value.source().versionId() + "|"
                + value.kind() + "|" + canonicalSpan(value.span());
        if (value instanceof QuantityObservation quantity) {
            return base + "|" + decimal(quantity.value()) + "|" + quantity.normalizedUnit() + "|"
                    + canonicalQualifier(quantity.qualifier()) + "|" + canonicalDirection(quantity.direction());
        }
        if (value instanceof DateObservation date) {
            return base + "|" + canonicalDate(date.interval()) + "|" + canonicalQualifier(date.qualifier());
        }
        if (value instanceof IdentifierNumberObservation identifier) {
            return base + "|" + identifier.normalizedIdentifier() + "|" + identifier.numberSurface() + "|"
                    + segments(identifier.normalizedSegments());
        }
        LiteralIdentifierObservation literal = (LiteralIdentifierObservation) value;
        return base + "|" + literal.normalizedLiteral();
    }

    private String canonicalObservation(
            TypedConstraintStressDataset.ObservationAnnotation value,
            TypedConstraintStressDataset.EvidenceUnit unit) {
        String base = unit.documentId() + "|" + unit.versionId() + "|" + value.kind() + "|"
                + value.sourceSurface() + "@" + value.charStart() + ":" + value.charEnd();
        String qualifier = value.qualifier() == null ? "-" : normalize(value.qualifier()) + "["
                + normalize(value.qualifier()).replace(' ', ',') + "]|" + value.qualifier() + "@"
                + value.qualifierCharStart() + ":" + value.qualifierCharEnd();
        return switch (value.kind()) {
            case "QUANTITY" -> base + "|" + decimal(value.value()) + "|" + value.normalizedUnit() + "|"
                    + qualifier + "|" + canonicalExpectedObservationDirection(value);
            case "DATE" -> base + "|" + value.dateStart() + ".." + value.dateEnd() + ":"
                    + value.precision() + "|" + qualifier;
            case "IDENTIFIER_NUMBER" -> base + "|" + normalizeCaptured(value.identifier()) + "|"
                    + value.numberSurface() + "|" + value.normalizedSegments().stream()
                            .map(String::valueOf).collect(Collectors.joining("."));
            case "LITERAL_IDENTIFIER" -> base + "|" + value.normalizedLiteral();
            default -> throw new IllegalArgumentException("unknown expected observation kind: " + value.kind());
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

    private String canonicalExpectedQualifier(
            String source,
            String qualifier,
            Integer start,
            Integer end) {
        if (qualifier == null) return "-";
        String tokens = normalize(qualifier).replace(' ', ',');
        return normalize(qualifier) + "[" + tokens + "]|" + codePointSlice(source, start, end) + "@"
                + start + ":" + end;
    }

    private String canonicalExpectedDirection(
            String query,
            TypedConstraintStressDataset.ConstraintAnnotation value) {
        String direction = value.direction() == null ? "NONE" : value.direction();
        if ("NONE".equals(direction)) return direction;
        if (value.directionSourceSurface() != null) {
            return direction + "|" + codePointSlice(
                    query, value.directionCharStart(), value.directionCharEnd()) + "@"
                    + value.directionCharStart() + ":" + value.directionCharEnd();
        }
        int start = value.queryCharStart();
        int end = value.queryCharEnd();
        String surface = codePointSlice(query, start, end);
        String[] words = "DECREASE".equals(direction)
                ? new String[] {"감소", "decrease", "decreased", "reduce", "reduced", "reduction"}
                : new String[] {"증가", "increase", "increased"};
        String lower = surface.toLowerCase(Locale.ROOT);
        for (String word : words) {
            int charIndex = lower.indexOf(word);
            if (charIndex >= 0) {
                int localStart = surface.codePointCount(0, charIndex);
                int localEnd = localStart + word.codePointCount(0, word.length());
                return direction + "|" + codePointSlice(query, start + localStart, start + localEnd) + "@"
                        + (start + localStart) + ":" + (start + localEnd);
            }
        }
        return direction + "|MISSING_SPAN";
    }

    private String canonicalExpectedObservationDirection(
            TypedConstraintStressDataset.ObservationAnnotation value) {
        String direction = value.direction() == null ? "NONE" : value.direction();
        if ("NONE".equals(direction)) return direction;
        return direction + "|" + value.directionSourceSurface() + "@" + value.directionCharStart()
                + ":" + value.directionCharEnd();
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

    private double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    record RuntimeQuery(String queryId, String userBundleId, String text, String language) {
        RuntimeQuery {
            Objects.requireNonNull(queryId, "queryId");
            Objects.requireNonNull(userBundleId, "userBundleId");
            Objects.requireNonNull(text, "text");
            Objects.requireNonNull(language, "language");
        }
    }

    record RuntimeCandidate(
            String candidateId,
            String documentId,
            String versionId,
            List<SourceSlice> atomicSources) {
        RuntimeCandidate {
            atomicSources = List.copyOf(atomicSources);
            if (atomicSources.isEmpty()) throw new IllegalArgumentException("runtime candidate has no source");
        }
    }

    record CandidateCoverage(String candidateId, List<String> coveredUnitIds) {
        CandidateCoverage {
            coveredUnitIds = List.copyOf(coveredUnitIds);
        }
    }

    private record IdealSequence(double score, List<Integer> relevances) {
    }

    private record RuntimeRanking(
            RuntimeQuery query,
            String professionGroup,
            double queryEmbeddingLatencyMs,
            double denseRankingLatencyMs,
            Map<String, Double> denseScoreByCandidate,
            List<RuntimeCandidate> t0Candidates) {
    }

    private record RuntimeProjection(
            Map<String, RuntimeCandidate> candidatesById,
            List<RuntimeRanking> rankings) {
    }

    private record ObservationExtraction(
            Map<String, List<CandidateObservation>> observationsByCandidate,
            List<Double> candidateLatenciesMs) {
    }

    private record GoldCandidate(
            String candidateId,
            List<String> coveredUnitIds,
            List<String> coveredGroupIds,
            List<String> coveredParentIds) {
    }

    private record GoldQuery(Query query) {
    }

    private record GoldAttachments(
            DatasetSlice dataset,
            Map<String, GoldCandidate> candidatesById,
            Map<String, GoldQuery> queriesById) {
    }

    private record StressAttachment(
            TypedConstraintStressDataset.DatasetSlice stress,
            TypedConstraintStressDataset.AttachedEvaluation attached) {
        static StressAttachment empty() {
            return new StressAttachment(null, null);
        }
    }

    record RankedCandidateResult(
            int rank,
            int denseRank,
            String candidateId,
            double cosineScore,
            MatchState matchState,
            List<DiagnosticReason> diagnosticReasons) {

        RankedCandidateResult {
            diagnosticReasons = List.copyOf(diagnosticReasons);
        }
    }

    record ProfileResult(
            int candidateCount,
            Integer firstDirectRank,
            boolean top1,
            double mrr,
            Map<Integer, Boolean> recallAtK,
            double ndcgAt5,
            MatchState rank1MatchState) {
    }

    record QueryReport(
            String datasetVersion,
            String split,
            String queryId,
            String userBundleId,
            String professionGroup,
            String language,
            boolean directSupport,
            int parsedConstraintCount,
            boolean semanticExactOrderParity,
            String typedKind,
            String primaryFamily,
            List<String> typedFamilies,
            double sharedQueryEmbeddingLatencyMs,
            double sharedDenseRankingLatencyMs,
            double queryParseLatencyMs,
            double matchPartitionLatencyMs,
            ProfileResult t0,
            ProfileResult t1,
            String directOutcome,
            MatchState t0ExpectedRank1State,
            MatchState t1ExpectedRank1State,
            List<RankedCandidateResult> t0Ranking,
            List<RankedCandidateResult> t1Ranking) {
        QueryReport {
            typedFamilies = List.copyOf(typedFamilies);
        }
    }

    record ProfileMetrics(
            double top1,
            double mrr,
            double ndcgAt5,
            Map<Integer, Double> recallAtK) {
    }

    record ComparisonMetrics(
            long queryCount,
            long directQueryCount,
            ProfileMetrics t0,
            ProfileMetrics t1,
            long directWins,
            long directLosses,
            long directTies,
            long directRank1Losses) {
    }

    record ExactSetMetrics(
            long predicted,
            long expected,
            long truePositive,
            long falsePositive,
            long falseNegative,
            double precision,
            double recall,
            double f1,
            List<String> falsePositiveItems,
            List<String> falseNegativeItems) {
        ExactSetMetrics {
            falsePositiveItems = List.copyOf(falsePositiveItems);
            falseNegativeItems = List.copyOf(falseNegativeItems);
        }

        static ExactSetMetrics empty() {
            return new ExactSetMetrics(0, 0, 0, 0, 0, 0.0d, 0.0d, 0.0d, List.of(), List.of());
        }
    }

    record ExtractionReport(ExactSetMetrics queryConstraints, ExactSetMetrics candidateObservations) {
    }

    record ClassMetrics(long support, long predicted, long truePositive, double precision, double recall) {
    }

    record StateReport(
            long labeledUnitCount,
            long correct,
            double accuracy,
            Map<String, Map<String, Long>> confusion,
            Map<String, ClassMetrics> perState,
            DiagnosticReport diagnostics,
            List<StateMismatch> mismatches) {
        static StateReport empty() {
            return new StateReport(0, 0, 0.0d, Map.of(), Map.of(), DiagnosticReport.empty(), List.of());
        }
    }

    record DiagnosticReport(
            long labeledReasonCount,
            long correctReasonCount,
            long qualifierMismatchCount,
            long qualifierMismatchSatisfiedFalsePositiveCount,
            long sameQualifierWrongValueContradictedCount,
            long sameQualifierWrongValueCorrectCount,
            double sameQualifierWrongValueContradictedRecall,
            List<ReasonMismatch> mismatches) {

        static DiagnosticReport empty() {
            return new DiagnosticReport(0, 0, 0, 0, 0, 0, 0.0d, List.of());
        }
    }

    record ReasonMismatch(
            String queryId,
            String evidenceUnitId,
            String expectedReason,
            List<DiagnosticReason> predictedReasons) {
        ReasonMismatch {
            predictedReasons = List.copyOf(predictedReasons);
        }
    }

    record StateMismatch(
            String queryId,
            String evidenceUnitId,
            String expectedState,
            String predictedState) {
    }

    record HardNegativeReport(
            long queryCount,
            long t0SatisfiedAt1,
            long t1SatisfiedAt1,
            double t0SatisfiedAt1Rate,
            double t1SatisfiedAt1Rate,
            long t0ContradictedAt1,
            long t1ContradictedAt1,
            long t0ExpectedContradictedAt1,
            long t1ExpectedContradictedAt1,
            Map<String, Long> predictedRank1StateTransitions,
            Map<String, Long> expectedRank1StateTransitions) {
    }

    record LatencyMetrics(long samples, double totalMs, double averageMs, double p50Ms, double p95Ms) {
    }

    record LatencyReport(
            LatencyMetrics queryParse,
            LatencyMetrics oneTimeObservationParse,
            LatencyMetrics matchAndPartition,
            LatencyMetrics onlineAdded,
            LatencyMetrics sharedQueryEmbedding,
            LatencyMetrics sharedDenseRanking,
            LatencyMetrics t0EndToEnd,
            LatencyMetrics t1EndToEnd) {
    }

    record RuntimeCost(
            long candidateIdentityParityQueryCount,
            long semanticQueryCount,
            long semanticExactOrderParityQueryCount,
            long atomicSourceCount,
            long extractedObservationCount,
            long parsedConstraintCount,
            long observationCacheCandidateCount,
            long observationCacheCanonicalPayloadUtf8Bytes,
            String exactAdditionalHeapBytes,
            long persistentIndexCount,
            long persistentStorageWriteCount) {
    }

    record SliceReport(
            String split,
            double oneTimeObservationParseLatencyMs,
            long semanticQueryCount,
            long queryCount,
            List<QueryReport> queries) {
    }

    record ExperimentReport(
            int schemaVersion,
            String phase,
            String datasetVersion,
            OllamaBgeM3EmbeddingClient.ModelMetadata model,
            String t0Profile,
            String t1Profile,
            ComparisonMetrics queryMicro,
            ComparisonMetrics userMacro,
            Map<String, ComparisonMetrics> professionSlices,
            Map<String, ComparisonMetrics> languageSlices,
            Map<String, ComparisonMetrics> splitSlices,
            Map<String, ComparisonMetrics> typedKindSlices,
            Map<String, ComparisonMetrics> primaryFamilySlices,
            Map<String, ComparisonMetrics> typedFamilySlices,
            ExtractionReport extraction,
            StateReport states,
            HardNegativeReport hardNegatives,
            LatencyReport latency,
            RuntimeCost runtimeCost,
            List<SliceReport> slices) {
    }

    private final class ExtractionAccumulator {
        private final Set<String> predictedQueries = new LinkedHashSet<>();
        private final Set<String> expectedQueries = new LinkedHashSet<>();
        private final Set<String> predictedObservations = new LinkedHashSet<>();
        private final Set<String> expectedObservations = new LinkedHashSet<>();

        void addQuerySets(
                RuntimeQuery query,
                List<QueryConstraint> predicted,
                TypedConstraintStressDataset.TypedQueryAnnotation expected) {
            if (expected == null) throw new IllegalStateException("stress query annotation is missing: " + query.queryId());
            predicted.stream().map(value -> query.queryId() + "|" + canonicalConstraint(value))
                    .forEach(predictedQueries::add);
            expectedQueries.add(query.queryId() + "|" + canonicalConstraint(query, expected.constraint()));
        }

        void addObservationSets(Set<String> predicted, Set<String> expected) {
            predictedObservations.addAll(predicted);
            expectedObservations.addAll(expected);
        }

        ExtractionReport metrics() {
            return new ExtractionReport(
                    exact(predictedQueries, expectedQueries),
                    exact(predictedObservations, expectedObservations));
        }

        private ExactSetMetrics exact(Set<String> predicted, Set<String> expected) {
            Set<String> intersection = new HashSet<>(predicted);
            intersection.retainAll(expected);
            List<String> falsePositiveItems = predicted.stream()
                    .filter(value -> !expected.contains(value)).sorted().toList();
            List<String> falseNegativeItems = expected.stream()
                    .filter(value -> !predicted.contains(value)).sorted().toList();
            long tp = intersection.size();
            long fp = falsePositiveItems.size();
            long fn = falseNegativeItems.size();
            double precision = predicted.isEmpty() ? 0.0d : tp / (double) predicted.size();
            double recall = expected.isEmpty() ? 0.0d : tp / (double) expected.size();
            double f1 = precision + recall == 0.0d ? 0.0d : 2.0d * precision * recall / (precision + recall);
            return new ExactSetMetrics(
                    predicted.size(), expected.size(), tp, fp, fn, precision, recall, f1,
                    falsePositiveItems, falseNegativeItems);
        }

    }

    private static final class StateAccumulator {
        private final Map<String, Map<String, Long>> confusion = new LinkedHashMap<>();
        private final List<StateMismatch> mismatches = new ArrayList<>();
        private long count;
        private long correct;
        private long labeledReasons;
        private long correctReasons;
        private long qualifierMismatches;
        private long qualifierMismatchSatisfiedFalsePositives;
        private long sameQualifierWrongValues;
        private long sameQualifierWrongValueCorrect;
        private final List<ReasonMismatch> reasonMismatches = new ArrayList<>();

        void add(
                TypedConstraintStressDataset.TypedQueryAnnotation annotation,
                List<QueryConstraint> constraints,
                Map<String, List<CandidateObservation>> observationsByCandidate,
                Map<String, TypedConstraintStressDataset.EvidenceUnit> units,
                TypedConstraintEvaluator evaluator) {
            List<CandidateObservation> allObservations = observationsByCandidate.values().stream()
                    .flatMap(List::stream).toList();
            for (TypedConstraintStressDataset.ExpectedEvidenceState expected : annotation.expectedEvidenceStates()) {
                TypedConstraintStressDataset.EvidenceUnit unit = units.get(expected.evidenceUnitId());
                if (unit == null) throw new IllegalStateException("typed state unit is missing");
                List<CandidateObservation> unitObservations = allObservations.stream()
                        .filter(observation -> observation.source().documentId().equals(unit.documentId()))
                        .filter(observation -> observation.source().versionId().equals(unit.versionId()))
                        .filter(observation -> unit.sourceSpans().stream().anyMatch(span ->
                                span.codePointStart() <= observation.span().startInclusive()
                                        && span.codePointEnd() >= observation.span().endExclusive()))
                        .toList();
                EvaluationResult evaluation = evaluator.evaluateAllDetailed(constraints, unitObservations);
                MatchState predicted = evaluation.state();
                String actual = expected.state();
                confusion.computeIfAbsent(actual, ignored -> new LinkedHashMap<>())
                        .merge(predicted.name(), 1L, Long::sum);
                count++;
                if (actual.equals(predicted.name())) {
                    correct++;
                }
                else {
                    mismatches.add(new StateMismatch(
                            annotation.queryId(), expected.evidenceUnitId(), actual, predicted.name()));
                }
                if (expected.reason() != null) {
                    DiagnosticReason expectedReason = DiagnosticReason.valueOf(expected.reason());
                    boolean exactReason = evaluation.reasons().equals(List.of(expectedReason));
                    labeledReasons++;
                    if (exactReason) correctReasons++;
                    else reasonMismatches.add(new ReasonMismatch(
                            annotation.queryId(),
                            expected.evidenceUnitId(),
                            expectedReason.name(),
                            evaluation.reasons()));
                    if (expectedReason == DiagnosticReason.QUALIFIER_MISMATCH) {
                        qualifierMismatches++;
                        if (predicted == MatchState.SATISFIED) qualifierMismatchSatisfiedFalsePositives++;
                    }
                    if (expectedReason == DiagnosticReason.VALUE_MISMATCH
                            && "CONTRADICTED".equals(expected.state())) {
                        sameQualifierWrongValues++;
                        if (predicted == MatchState.CONTRADICTED && exactReason) {
                            sameQualifierWrongValueCorrect++;
                        }
                    }
                }
            }
        }

        StateReport metrics() {
            if (count == 0) return StateReport.empty();
            Map<String, ClassMetrics> classes = new LinkedHashMap<>();
            for (MatchState state : MatchState.values()) {
                String name = state.name();
                long support = confusion.getOrDefault(name, Map.of()).values().stream()
                        .mapToLong(Long::longValue).sum();
                long predicted = confusion.values().stream()
                        .mapToLong(row -> row.getOrDefault(name, 0L)).sum();
                long tp = confusion.getOrDefault(name, Map.of()).getOrDefault(name, 0L);
                classes.put(name, new ClassMetrics(
                        support,
                        predicted,
                        tp,
                        predicted == 0 ? 0.0d : tp / (double) predicted,
                        support == 0 ? 0.0d : tp / (double) support));
            }
            Map<String, Map<String, Long>> frozen = new LinkedHashMap<>();
            confusion.forEach((key, row) -> frozen.put(key, Map.copyOf(row)));
            return new StateReport(
                    count,
                    correct,
                    correct / (double) count,
                    Map.copyOf(frozen),
                    Map.copyOf(classes),
                    new DiagnosticReport(
                            labeledReasons,
                            correctReasons,
                            qualifierMismatches,
                            qualifierMismatchSatisfiedFalsePositives,
                            sameQualifierWrongValues,
                            sameQualifierWrongValueCorrect,
                            sameQualifierWrongValues == 0 ? 0.0d
                                    : sameQualifierWrongValueCorrect / (double) sameQualifierWrongValues,
                            List.copyOf(reasonMismatches)),
                    List.copyOf(mismatches));
        }
    }

    private static final class HardNegativeAccumulator {
        private long count;
        private long t0Satisfied;
        private long t1Satisfied;
        private long t0Contradicted;
        private long t1Contradicted;
        private long t0ExpectedContradicted;
        private long t1ExpectedContradicted;
        private final Map<String, Long> predictedTransitions = new LinkedHashMap<>();
        private final Map<String, Long> expectedTransitions = new LinkedHashMap<>();

        void add(MatchState t0, MatchState t1, MatchState expectedT0, MatchState expectedT1) {
            count++;
            if (t0 == MatchState.SATISFIED) t0Satisfied++;
            if (t1 == MatchState.SATISFIED) t1Satisfied++;
            if (t0 == MatchState.CONTRADICTED) t0Contradicted++;
            if (t1 == MatchState.CONTRADICTED) t1Contradicted++;
            if (expectedT0 == MatchState.CONTRADICTED) t0ExpectedContradicted++;
            if (expectedT1 == MatchState.CONTRADICTED) t1ExpectedContradicted++;
            predictedTransitions.merge(t0 + "->" + t1, 1L, Long::sum);
            expectedTransitions.merge(expectedT0 + "->" + expectedT1, 1L, Long::sum);
        }

        HardNegativeReport metrics() {
            return new HardNegativeReport(
                    count,
                    t0Satisfied,
                    t1Satisfied,
                    count == 0 ? 0.0d : t0Satisfied / (double) count,
                    count == 0 ? 0.0d : t1Satisfied / (double) count,
                    t0Contradicted,
                    t1Contradicted,
                    t0ExpectedContradicted,
                    t1ExpectedContradicted,
                    Map.copyOf(predictedTransitions),
                    Map.copyOf(expectedTransitions));
        }
    }
}
