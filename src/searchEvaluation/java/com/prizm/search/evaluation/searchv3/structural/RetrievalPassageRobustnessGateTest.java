package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RetrievalPassageRobustnessGateTest {

    private final RetrievalPassageRobustnessGate gate = new RetrievalPassageRobustnessGate();

    @Test
    void marksSmallSliceInsufficientInsteadOfTreatingPercentageAsBlockingProof() {
        List<SearchV3DenseAblationEngine.QueryResult> values = queries(2, 4, 0);

        RetrievalPassageRobustnessGate.PairedSlice result = gate.summarize("FRONTEND_MOBILE", values);

        assertThat(result.userBundleCount()).isEqualTo(2);
        assertThat(result.directQueryCount()).isEqualTo(8);
        assertThat(result.sufficient()).isFalse();
        assertThat(result.status()).isEqualTo("INSUFFICIENT_SAMPLE");
    }

    @Test
    void classifiesConsistentPairedLossAsBlockingOnlyWhenSampleIsSufficient() {
        List<SearchV3DenseAblationEngine.QueryResult> values = queries(3, 4, 4);

        RetrievalPassageRobustnessGate.PairedSlice result = gate.summarize("FRONTEND_MOBILE", values);

        assertThat(result.sufficient()).isTrue();
        assertThat(result.top1Delta()).isEqualTo(-1.0d);
        assertThat(result.mrrDelta()).isEqualTo(-0.5d);
        assertThat(result.top1ConfidenceInterval().upper()).isLessThan(0.0d);
        assertThat(result.status()).isEqualTo("BLOCKING_REGRESSION");
    }

    @Test
    void keepsOneClusterLossInconclusiveAndBootstrapIsDeterministic() {
        List<SearchV3DenseAblationEngine.QueryResult> values = new ArrayList<>();
        values.addAll(queriesForUser("U1", 4, 1));
        values.addAll(queriesForUser("U2", 4, 0));
        values.addAll(queriesForUser("U3", 4, 0));

        RetrievalPassageRobustnessGate.PairedSlice first = gate.summarize("FRONTEND_MOBILE", values);
        RetrievalPassageRobustnessGate.PairedSlice second = gate.summarize("FRONTEND_MOBILE", values);

        assertThat(first).isEqualTo(second);
        assertThat(first.sufficient()).isTrue();
        assertThat(first.top1Losses()).isEqualTo(1);
        assertThat(first.top1ConfidenceInterval().lower()).isLessThan(0.0d);
        assertThat(first.top1ConfidenceInterval().upper()).isZero();
        assertThat(first.status()).isEqualTo("INCONCLUSIVE");
    }

    @Test
    void assessReturnsPromisingForFrozenBoundariesCostReductionAndPairedTies() {
        List<SearchV3DenseAblationEngine.QueryResult> fresh = queries(6, 4, 0);
        SearchV3DenseAblationEngine.ExperimentReport report = report(fresh, 120, 80, 0.0d, 0.0d);

        RetrievalPassageRobustnessGate.RobustnessAssessment result = gate.assess(report, fresh);

        assertThat(result.boundaryInvariants()).isTrue();
        assertThat(result.candidateReduction()).isGreaterThan(0.33d);
        assertThat(result.freshPointNonRegression()).isTrue();
        assertThat(result.blockingSliceRegression()).isFalse();
        assertThat(result.decision()).isEqualTo("PROMISING");
    }

    @Test
    void decisionRejectsBoundaryViolationAsItsOnlyFailure() {
        assertThat(gate.decide(false, 0.40d, true, false)).isEqualTo("NEEDS_ADJUSTMENT");
    }

    @Test
    void decisionRejectsSubThresholdCandidateReductionAsItsOnlyFailure() {
        assertThat(gate.decide(true, 0.24d, true, false)).isEqualTo("NEEDS_ADJUSTMENT");
    }

    @Test
    void decisionRejectsFreshPointRegressionAsItsOnlyFailure() {
        assertThat(gate.decide(true, 0.40d, false, false)).isEqualTo("NEEDS_ADJUSTMENT");
    }

    @Test
    void decisionRejectsBlockingCumulativeSliceAsItsOnlyFailure() {
        assertThat(gate.decide(true, 0.40d, true, true)).isEqualTo("NEEDS_ADJUSTMENT");
    }

    private List<SearchV3DenseAblationEngine.QueryResult> queries(int users, int perUser, int lossesPerUser) {
        List<SearchV3DenseAblationEngine.QueryResult> values = new ArrayList<>();
        for (int user = 1; user <= users; user++) {
            values.addAll(queriesForUser("U" + user, perUser, lossesPerUser));
        }
        return values;
    }

    private List<SearchV3DenseAblationEngine.QueryResult> queriesForUser(
            String user,
            int count,
            int losses) {
        List<SearchV3DenseAblationEngine.QueryResult> values = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            boolean loss = index < losses;
            values.add(query(user + "-Q" + index, user, loss));
        }
        return values;
    }

    private SearchV3DenseAblationEngine.QueryResult query(String queryId, String user, boolean loss) {
        SearchV3DenseAblationEngine.QueryProfileResult structural = profile(true, 1.0d);
        SearchV3DenseAblationEngine.QueryProfileResult passage = loss
                ? profile(false, 0.5d)
                : profile(true, 1.0d);
        return new SearchV3DenseAblationEngine.QueryResult(
                queryId,
                user,
                SearchV3DenseAblationDataset.Split.DEV,
                "FRONTEND_MOBILE",
                "EN",
                "SUPPORTED",
                List.of("semantic_paraphrase"),
                true,
                0.0d,
                structural,
                structural,
                passage);
    }

    private SearchV3DenseAblationEngine.QueryProfileResult profile(boolean top1, double reciprocalRank) {
        return new SearchV3DenseAblationEngine.QueryProfileResult(
                1,
                top1 ? 1 : 2,
                top1 ? 1 : 2,
                top1,
                reciprocalRank,
                Map.of(5, true, 10, true, 20, true, 50, true),
                Map.of(),
                Map.of(),
                Map.of(),
                0.0d,
                0.0d,
                List.of());
    }

    private SearchV3DenseAblationEngine.ExperimentReport report(
            List<SearchV3DenseAblationEngine.QueryResult> queries,
            long structuralCandidates,
            long passageCandidates,
            double fragmentation,
            double contamination) {
        Map<Integer, Double> structuralRecall = Map.of(5, 0.9d, 10, 1.0d, 20, 1.0d, 50, 1.0d);
        Map<Integer, Double> passageRecall = Map.of(5, 1.0d, 10, 1.0d, 20, 1.0d, 50, 1.0d);
        SearchV3DenseAblationEngine.AggregateMetrics fixed = metrics(structuralRecall);
        SearchV3DenseAblationEngine.AggregateMetrics structural = metrics(structuralRecall);
        SearchV3DenseAblationEngine.AggregateMetrics passage = metrics(passageRecall);
        SearchV3DenseAblationEngine.AggregateComparison comparison =
                new SearchV3DenseAblationEngine.AggregateComparison(fixed, structural, passage);
        return new SearchV3DenseAblationEngine.ExperimentReport(
                1,
                "TEST",
                "",
                "TEST",
                "",
                "",
                Map.of(),
                null,
                null,
                corpus("FIXED", 20, 0.0d, 0.0d),
                corpus("STRUCTURAL", structuralCandidates, 0.0d, 0.0d),
                corpus("PASSAGE", passageCandidates, fragmentation, contamination),
                passageStats(passageCandidates),
                null,
                null,
                null,
                Map.of(),
                comparison,
                comparison,
                Map.of(),
                Map.of(),
                Map.of(),
                Map.of(),
                0L,
                0L,
                queries,
                false,
                false,
                "NOT_RUN",
                "PROMISING");
    }

    private SearchV3DenseAblationEngine.AggregateMetrics metrics(Map<Integer, Double> recall) {
        return new SearchV3DenseAblationEngine.AggregateMetrics(
                24, 0, 24, 1.0d, 1.0d, recall, Map.of(), Map.of(), Map.of());
    }

    private SearchV3DenseAblationEngine.ProfileCorpusStats corpus(
            String profile,
            long candidates,
            double fragmentation,
            double contamination) {
        return new SearchV3DenseAblationEngine.ProfileCorpusStats(
                profile,
                candidates,
                candidates,
                10,
                100.0d,
                200,
                24,
                fragmentation == 0.0d ? 0 : 1,
                fragmentation,
                contamination == 0.0d ? 0 : 1,
                contamination,
                0,
                24,
                0.0d,
                0,
                0,
                0,
                0,
                Map.of(),
                Map.of(),
                0.0d);
    }

    private SearchV3DenseAblationEngine.PassageCorpusStats passageStats(long passages) {
        return new SearchV3DenseAblationEngine.PassageCorpusStats(
                "PASSAGE",
                passages,
                120,
                1,
                1.5d,
                3,
                10,
                150.0d,
                300,
                passages / 2,
                0.5d,
                passages - (passages / 2),
                0.5d,
                0,
                24,
                24,
                1.0d);
    }
}
