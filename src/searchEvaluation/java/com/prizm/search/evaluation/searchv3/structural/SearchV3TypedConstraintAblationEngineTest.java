package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.DatasetSlice;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.GoldSpan;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.GoldUnit;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.Query;
import com.prizm.search.evaluation.searchv3.typed.TypedConstraintStressDataset;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.MatchState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SearchV3TypedConstraintAblationEngineTest {

    private final SearchV3DenseAblationDataset denseDataset = new SearchV3DenseAblationDataset();
    private final SearchV3DenseAblationEngine denseEngine = new SearchV3DenseAblationEngine();
    private final SearchV3TypedConstraintAblationEngine engine =
            new SearchV3TypedConstraintAblationEngine();

    @Test
    void semanticQueriesPreserveExactOrderAndT1RanksComeFromNewListPositions() {
        DatasetSlice dev = denseDataset.load(SearchV3DenseAblationDataset.Split.DEV);
        SearchV3TypedConstraintAblationEngine.ExperimentReport report = engine.evaluate(fakeDenseRun(dev));

        List<SearchV3TypedConstraintAblationEngine.QueryReport> semantic = report.slices().get(0).queries()
                .stream().filter(query -> query.parsedConstraintCount() == 0).toList();
        assertThat(semantic).isNotEmpty();
        assertThat(semantic).allSatisfy(query -> {
            assertThat(query.semanticExactOrderParity()).isTrue();
            assertThat(query.t1Ranking()).extracting(
                    SearchV3TypedConstraintAblationEngine.RankedCandidateResult::candidateId)
                    .containsExactlyElementsOf(query.t0Ranking().stream()
                            .map(SearchV3TypedConstraintAblationEngine.RankedCandidateResult::candidateId).toList());
            assertThat(query.t1Ranking()).extracting(
                    SearchV3TypedConstraintAblationEngine.RankedCandidateResult::rank)
                    .containsExactlyElementsOf(sequence(query.t1Ranking().size()));
        });
        assertThat(report.queryMicro().t0().recallAtK()).containsKeys(5, 10, 20);
        assertThat(report.queryMicro().t1().ndcgAt5()).isBetween(0.0d, 1.0d);
        assertThat(report.userMacro().queryCount()).isPositive();
        assertThat(report.runtimeCost().candidateIdentityParityQueryCount())
                .isEqualTo(report.slices().get(0).queryCount());
        assertThat(report.runtimeCost().semanticExactOrderParityQueryCount())
                .isEqualTo(report.runtimeCost().semanticQueryCount());
        assertThat(report.runtimeCost().persistentIndexCount()).isZero();
        assertThat(report.runtimeCost().persistentStorageWriteCount()).isZero();
    }

    @Test
    void typedStressMeasuresStablePartitionExtractionStatesAndHardNegativesWithoutBge() {
        DatasetSlice dev = denseDataset.loadTypedStress(SearchV3DenseAblationDataset.Split.DEV);
        DatasetSlice calibration = denseDataset.loadTypedStress(SearchV3DenseAblationDataset.Split.CALIBRATION);
        TypedConstraintStressDataset strictLoader = new TypedConstraintStressDataset();
        TypedConstraintStressDataset.DatasetSlice devStress =
                strictLoader.load(TypedConstraintStressDataset.Split.DEV);
        TypedConstraintStressDataset.DatasetSlice calibrationStress =
                strictLoader.load(TypedConstraintStressDataset.Split.CALIBRATION);

        SearchV3TypedConstraintAblationEngine.ExperimentReport report =
                engine.evaluate(combine(fakeDenseRun(dev), fakeDenseRun(calibration)),
                        List.of(devStress, calibrationStress));

        assertThat(report.extraction().queryConstraints().expected()).isEqualTo(24);
        assertThat(report.extraction().queryConstraints().truePositive()).isEqualTo(24);
        assertThat(report.extraction().queryConstraints().falsePositive()).isZero();
        assertThat(report.extraction().queryConstraints().falseNegative()).isZero();
        assertThat(report.extraction().queryConstraints().truePositive()
                + report.extraction().queryConstraints().falseNegative()).isEqualTo(24);
        assertThat(report.extraction().candidateObservations().expected()).isEqualTo(25);
        assertThat(report.extraction().candidateObservations().truePositive()).isEqualTo(24);
        assertThat(report.extraction().candidateObservations().falsePositive()).isEqualTo(1);
        assertThat(report.extraction().candidateObservations().falseNegative()).isEqualTo(1);
        assertThat(report.extraction().candidateObservations().truePositive()
                + report.extraction().candidateObservations().falseNegative()).isEqualTo(25);
        assertThat(report.states().labeledUnitCount()).isEqualTo(104);
        assertThat(report.states().correct()).isEqualTo(104);
        assertThat(report.states().mismatches()).isEmpty();
        assertThat(report.states().confusion().values().stream()
                .flatMap(row -> row.values().stream()).mapToLong(Long::longValue).sum()).isEqualTo(104);
        assertThat(report.states().perState()).containsKeys(
                MatchState.SATISFIED.name(), MatchState.CONTRADICTED.name(), MatchState.UNKNOWN.name());
        assertThat(report.hardNegatives().queryCount()).isPositive();
        assertThat(report.hardNegatives().predictedRank1StateTransitions().values())
                .hasSizeGreaterThanOrEqualTo(1);
        assertThat(report.hardNegatives().predictedRank1StateTransitions().values().stream()
                .mapToLong(Long::longValue).sum()).isEqualTo(report.hardNegatives().queryCount());
        assertThat(report.hardNegatives().expectedRank1StateTransitions().values().stream()
                .mapToLong(Long::longValue).sum()).isEqualTo(report.hardNegatives().queryCount());
        assertThat(report.typedKindSlices()).containsKeys(
                "QUANTITY", "DATE", "LITERAL_IDENTIFIER");
        assertThat(report.typedFamilySlices()).containsKeys(
                "qualifier_mismatch", "percentage_direction", "duration",
                "not_supported_hard_negative");
        assertThat(report.runtimeCost().atomicSourceCount()).isPositive();
        assertThat(report.runtimeCost().extractedObservationCount()).isPositive();

        assertThat(report.slices().get(0).queries()).allSatisfy(query -> {
            assertThat(query.t0Ranking()).hasSameSizeAs(query.t1Ranking());
            assertThat(query.t1Ranking()).extracting(
                    SearchV3TypedConstraintAblationEngine.RankedCandidateResult::candidateId)
                    .containsExactlyInAnyOrderElementsOf(query.t0Ranking().stream()
                            .map(SearchV3TypedConstraintAblationEngine.RankedCandidateResult::candidateId).toList());
            assertThat(query.t0Ranking()).allSatisfy(candidate ->
                    assertThat(candidate.rank()).isEqualTo(candidate.denseRank()));
            if (query.parsedConstraintCount() > 0) {
                assertThat(query.t1Ranking().stream()
                        .map(SearchV3TypedConstraintAblationEngine.RankedCandidateResult::matchState)
                        .mapToInt(this::stateOrder).toArray()).isSorted();
            }
        });
    }

    @Test
    void officialCapabilityStressReportsPrimaryFamiliesReasonsAndDirectRank1LossWithoutBge() {
        DatasetSlice dev = denseDataset.loadTypedStressOfficial(SearchV3DenseAblationDataset.Split.DEV);
        DatasetSlice calibration = denseDataset.loadTypedStressOfficial(SearchV3DenseAblationDataset.Split.CALIBRATION);
        TypedConstraintStressDataset strictLoader = new TypedConstraintStressDataset();
        var devStress = strictLoader.load(
                TypedConstraintStressDataset.OFFICIAL_1_1_0, TypedConstraintStressDataset.Split.DEV);
        var calibrationStress = strictLoader.load(
                TypedConstraintStressDataset.OFFICIAL_1_1_0, TypedConstraintStressDataset.Split.CALIBRATION);

        SearchV3TypedConstraintAblationEngine.ExperimentReport report = engine.evaluate(
                combine(fakeDenseRun(dev), fakeDenseRun(calibration)),
                List.of(devStress, calibrationStress));

        assertThat(report.datasetVersion()).isEqualTo(TypedConstraintStressDataset.OFFICIAL_1_1_0.version());
        assertThat(report.primaryFamilySlices()).containsOnlyKeys(
                "quantity_wrong_value", "qualifier_mismatch", "date", "identifier_number",
                "percentage_direction", "range_boundary");
        assertThat(report.primaryFamilySlices()).allSatisfy((family, metrics) -> {
            assertThat(metrics.queryCount()).isEqualTo(4);
            assertThat(metrics.directWins() + metrics.directLosses() + metrics.directTies())
                    .isEqualTo(metrics.directQueryCount());
            assertThat(metrics.t0().top1()).isBetween(0.0d, 1.0d);
            assertThat(metrics.t1().mrr()).isBetween(0.0d, 1.0d);
            assertThat(metrics.t1().ndcgAt5()).isBetween(0.0d, 1.0d);
        });
        assertThat(report.states().diagnostics().qualifierMismatchCount()).isEqualTo(18);
        assertThat(report.states().diagnostics().qualifierMismatchSatisfiedFalsePositiveCount()).isZero();
        assertThat(report.states().diagnostics().sameQualifierWrongValueContradictedCount()).isEqualTo(24);
        assertThat(report.states().diagnostics().labeledReasonCount()).isEqualTo(96);
        assertThat(report.states().diagnostics().mismatches()).isEmpty();
        assertThat(report.states().diagnostics().correctReasonCount()).isEqualTo(96);
        assertThat(report.extraction().queryConstraints().f1()).isEqualTo(1.0d);
        assertThat(report.extraction().candidateObservations().f1())
                .as("official observation extraction: %s", report.extraction().candidateObservations())
                .isGreaterThanOrEqualTo(0.95d);
        assertThat(report.states().mismatches()).isEmpty();
        assertThat(report.states().diagnostics().sameQualifierWrongValueContradictedRecall()).isEqualTo(1.0d);
        assertThat(report.queryMicro().directRank1Losses()).isNotNegative();
        assertThat(report.slices()).flatExtracting(SearchV3TypedConstraintAblationEngine.SliceReport::queries)
                .flatExtracting(SearchV3TypedConstraintAblationEngine.QueryReport::t1Ranking)
                .allSatisfy(candidate -> assertThat(candidate.diagnosticReasons()).isNotNull());
        assertThat(report.slices()).flatExtracting(SearchV3TypedConstraintAblationEngine.SliceReport::queries)
                .allSatisfy(query -> assertThat(query.t1Ranking()).allSatisfy(t1 ->
                        assertThat(t1.cosineScore()).isEqualTo(query.t0Ranking().stream()
                                .filter(t0 -> t0.candidateId().equals(t1.candidateId()))
                                .findFirst().orElseThrow().cosineScore())));
        assertThat(report.runtimeCost().candidateIdentityParityQueryCount()).isEqualTo(24);
    }

    private SearchV3DenseAblationEngine.PassageDenseRun combine(
            SearchV3DenseAblationEngine.PassageDenseRun first,
            SearchV3DenseAblationEngine.PassageDenseRun second) {
        List<SearchV3DenseAblationEngine.PassageDenseSliceRun> slices = new ArrayList<>(first.slices());
        slices.addAll(second.slices());
        return new SearchV3DenseAblationEngine.PassageDenseRun(
                first.schemaVersion(), first.phase(), first.datasetVersion(), first.model(), List.copyOf(slices));
    }

    @Test
    void ndcgCreditsARequiredEvidenceGroupOnlyAtItsFirstRankedCandidate() {
        List<SearchV3TypedConstraintAblationEngine.CandidateCoverage> ranking = List.of(
                new SearchV3TypedConstraintAblationEngine.CandidateCoverage("C1", List.of("U1")),
                new SearchV3TypedConstraintAblationEngine.CandidateCoverage("C2", List.of("U2")),
                new SearchV3TypedConstraintAblationEngine.CandidateCoverage("C3", List.of("U3", "U4")));
        Set<String> direct = Set.of("U1", "U2", "U3", "U4");
        Map<String, String> groups = Map.of("U1", "G1", "U2", "G1", "U3", "G2", "U4", "G3");

        assertThat(SearchV3TypedConstraintAblationEngine.noveltyRelevance(
                ranking, direct, groups, false)).containsExactly(1, 0, 2);
        assertThat(SearchV3TypedConstraintAblationEngine.noveltyRelevance(
                ranking, direct, groups, true)).containsExactly(2, 1, 0);
    }

    @Test
    void ndcgIdealSearchFindsTheTrueTopFiveOptimumInsteadOfGreedyImmediateCoverage() {
        List<SearchV3TypedConstraintAblationEngine.CandidateCoverage> ranking = List.of(
                new SearchV3TypedConstraintAblationEngine.CandidateCoverage("A", List.of("U1", "U2")),
                new SearchV3TypedConstraintAblationEngine.CandidateCoverage("B", List.of("U1", "U3")),
                new SearchV3TypedConstraintAblationEngine.CandidateCoverage("C", List.of("U2", "U4")));
        Set<String> direct = Set.of("U1", "U2", "U3", "U4");
        Map<String, String> groups = Map.of("U1", "G1", "U2", "G2", "U3", "G3", "U4", "G4");

        assertThat(SearchV3TypedConstraintAblationEngine.noveltyRelevance(
                ranking, direct, groups, false)).containsExactly(2, 1, 1);
        assertThat(SearchV3TypedConstraintAblationEngine.noveltyRelevance(
                ranking, direct, groups, true)).containsExactly(2, 2, 0);
    }

    @Test
    void rejectsPartialStrictStressCoverageAcrossDenseSplits() {
        DatasetSlice dev = denseDataset.loadTypedStress(SearchV3DenseAblationDataset.Split.DEV);
        DatasetSlice calibration = denseDataset.loadTypedStress(SearchV3DenseAblationDataset.Split.CALIBRATION);
        TypedConstraintStressDataset.DatasetSlice devStress = new TypedConstraintStressDataset()
                .load(TypedConstraintStressDataset.Split.DEV);

        assertThatThrownBy(() -> engine.evaluate(
                combine(fakeDenseRun(dev), fakeDenseRun(calibration)), List.of(devStress)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exact one-to-one coverage");
    }

    private SearchV3DenseAblationEngine.PassageDenseRun fakeDenseRun(DatasetSlice slice) {
        SearchV3DenseAblationEngine.CandidateBuild structural = denseEngine.buildStructuralCandidates(slice);
        SearchV3DenseAblationEngine.PassageCandidateBuild passage =
                denseEngine.buildPassageCandidates(slice, structural);
        Map<String, RetrievalPassage> passageById = passage.passages().stream().collect(Collectors.toMap(
                RetrievalPassage::passageId,
                Function.identity(),
                (left, right) -> left,
                LinkedHashMap::new));
        Map<String, SearchV3DenseAblationEngine.CandidateSpec> candidates = passage.candidateBuild()
                .candidates().stream().collect(Collectors.toMap(
                        SearchV3DenseAblationEngine.CandidateSpec::candidateId,
                        Function.identity()));
        List<SearchV3DenseAblationEngine.PassageDenseQueryRanking> rankings = new ArrayList<>();
        for (Query query : slice.queries()) {
            String profession = slice.bundles().stream()
                    .filter(bundle -> bundle.userBundleId().equals(query.userBundleId()))
                    .findFirst().orElseThrow().professionGroup();
            List<SearchV3DenseAblationEngine.CandidateSpec> scoped = candidates.values().stream()
                    .filter(candidate -> candidate.userBundleId().equals(query.userBundleId()))
                    .sorted(Comparator.comparing(SearchV3DenseAblationEngine.CandidateSpec::candidateId))
                    .toList();
            List<SearchV3DenseAblationEngine.RankedCandidate> fakeRanking = new ArrayList<>();
            for (int index = 0; index < scoped.size(); index++) {
                SearchV3DenseAblationEngine.CandidateSpec candidate = scoped.get(index);
                Set<String> units = slice.units().values().stream()
                        .filter(unit -> covers(candidate, unit))
                        .map(GoldUnit::evidenceUnitId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                Set<String> groups = units.stream().map(slice.units()::get).map(GoldUnit::groupId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                Set<String> parents = units.stream().map(slice.units()::get).map(GoldUnit::parentId)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                fakeRanking.add(new SearchV3DenseAblationEngine.RankedCandidate(
                        index + 1,
                        candidate.candidateId(),
                        1.0d - index * 0.01d,
                        candidate.documentId(),
                        candidate.versionId(),
                        candidate.sourceBlockType(),
                        candidate.sourceText(),
                        candidate.contextText(),
                        candidate.retrievalText(),
                        candidate.parentAnnotationCandidateId(),
                        candidate.sourceText().codePointCount(0, candidate.sourceText().length()),
                        candidate.evidenceChildren().stream()
                                .map(SearchV3DenseAblationEngine.EvidenceChildRange::evidenceChildId).toList(),
                        candidate.addedContextBlockIds(),
                        List.copyOf(units),
                        List.copyOf(groups),
                        List.copyOf(parents)));
            }
            rankings.add(new SearchV3DenseAblationEngine.PassageDenseQueryRanking(
                    query, profession, 0.0d, 0.0d, List.copyOf(fakeRanking)));
        }
        SearchV3DenseAblationEngine.PassageDenseSliceRun run =
                new SearchV3DenseAblationEngine.PassageDenseSliceRun(
                        slice,
                        Map.copyOf(passageById),
                        passage.candidateBuild().corpusStats(),
                        null,
                        List.copyOf(rankings));
        return new SearchV3DenseAblationEngine.PassageDenseRun(
                1, "PRZ-028-PURE-TEST", slice.datasetVersion(), null, List.of(run));
    }

    private boolean covers(SearchV3DenseAblationEngine.CandidateSpec candidate, GoldUnit unit) {
        if (!candidate.documentId().equals(unit.documentId()) || !candidate.versionId().equals(unit.versionId())) {
            return false;
        }
        return candidate.evidenceChildren().stream().anyMatch(child -> unit.sourceSpans().stream()
                .allMatch(span -> covers(child.range(), span)));
    }

    private boolean covers(SearchV3DenseAblationEngine.CandidateRange range, GoldSpan span) {
        return (range.page() == null ? span.page() == null : range.page().equals(span.page()))
                && range.codePointStart() <= span.codePointStart()
                && range.codePointEnd() >= span.codePointEnd();
    }

    private List<Integer> sequence(int size) {
        List<Integer> values = new ArrayList<>();
        for (int value = 1; value <= size; value++) values.add(value);
        return values;
    }

    private int stateOrder(MatchState state) {
        return switch (state) {
            case SATISFIED -> 0;
            case UNKNOWN -> 1;
            case CONTRADICTED -> 2;
        };
    }
}
