package com.prizm.search.evaluation.searchv3.structural;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Pre-registered paired robustness gate for the frozen B3 retrieval-passage policy. */
final class RetrievalPassageRobustnessGate {

    static final int MINIMUM_BUNDLES_PER_SLICE = 3;
    static final int MINIMUM_DIRECT_QUERIES_PER_SLICE = 10;
    static final int BOOTSTRAP_SAMPLES = 10_000;
    static final long BOOTSTRAP_SEED = 260_830_026L;
    static final double MINIMUM_CANDIDATE_REDUCTION = 0.25d;

    RobustnessAssessment assess(
            SearchV3DenseAblationEngine.ExperimentReport fresh,
            List<SearchV3DenseAblationEngine.QueryResult> cumulativeLongFormAndFresh) {
        List<SearchV3DenseAblationEngine.QueryResult> freshDirect = direct(fresh.queries());
        List<SearchV3DenseAblationEngine.QueryResult> cumulativeDirect = direct(cumulativeLongFormAndFresh);
        requireUniqueQueryIds(freshDirect, "fresh robustness");
        requireUniqueQueryIds(cumulativeDirect, "cumulative long-form plus robustness");

        PairedSlice freshOverall = summarize("ALL", freshDirect);
        PairedSlice freshFrontend = summarize(
                "FRONTEND_MOBILE",
                freshDirect.stream()
                        .filter(query -> "FRONTEND_MOBILE".equals(query.professionGroup()))
                        .toList());
        Map<String, PairedSlice> profession = grouped(cumulativeDirect, QueryResultView::professionGroup);
        Map<String, PairedSlice> language = grouped(cumulativeDirect, QueryResultView::language);

        double candidateReduction = ratioReduction(
                fresh.structuralCorpus().candidateCount(),
                fresh.passageCorpus().candidateCount());
        boolean boundaryInvariants = boundaryInvariants(fresh);
        boolean freshPointNonRegression = nonNegative(freshOverall.top1Delta())
                && nonNegative(freshOverall.mrrDelta())
                && nonNegative(freshFrontend.top1Delta())
                && nonNegative(freshFrontend.mrrDelta());
        boolean blockingSliceRegression = profession.values().stream().anyMatch(PairedSlice::blockingRegression)
                || language.values().stream().anyMatch(PairedSlice::blockingRegression);
        String decision = decide(
                boundaryInvariants,
                candidateReduction,
                freshPointNonRegression,
                blockingSliceRegression);
        return new RobustnessAssessment(
                MINIMUM_BUNDLES_PER_SLICE,
                MINIMUM_DIRECT_QUERIES_PER_SLICE,
                BOOTSTRAP_SAMPLES,
                BOOTSTRAP_SEED,
                MINIMUM_CANDIDATE_REDUCTION,
                candidateReduction,
                boundaryInvariants,
                freshPointNonRegression,
                blockingSliceRegression,
                freshOverall,
                freshFrontend,
                Map.copyOf(profession),
                Map.copyOf(language),
                decision);
    }

    String decide(
            boolean boundaryInvariants,
            double candidateReduction,
            boolean freshPointNonRegression,
            boolean blockingSliceRegression) {
        return boundaryInvariants
                        && candidateReduction >= MINIMUM_CANDIDATE_REDUCTION
                        && freshPointNonRegression
                        && !blockingSliceRegression
                ? "PROMISING"
                : "NEEDS_ADJUSTMENT";
    }

    PairedSlice summarize(String slice, List<SearchV3DenseAblationEngine.QueryResult> values) {
        List<QueryResultView> direct = values.stream().filter(SearchV3DenseAblationEngine.QueryResult::directSupport)
                .map(QueryResultView::from)
                .toList();
        Map<String, List<QueryResultView>> byUser = direct.stream()
                .collect(Collectors.groupingBy(
                        QueryResultView::userBundleId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        double top1Delta = mean(direct, QueryResultView::top1Delta);
        double mrrDelta = mean(direct, QueryResultView::mrrDelta);
        double userMacroTop1Delta = mean(byUser.values().stream()
                .map(queries -> mean(queries, QueryResultView::top1Delta))
                .toList());
        double userMacroMrrDelta = mean(byUser.values().stream()
                .map(queries -> mean(queries, QueryResultView::mrrDelta))
                .toList());
        ConfidenceInterval top1Interval = bootstrap(byUser, QueryResultView::top1Delta);
        ConfidenceInterval mrrInterval = bootstrap(byUser, QueryResultView::mrrDelta);
        boolean sufficient = byUser.size() >= MINIMUM_BUNDLES_PER_SLICE
                && direct.size() >= MINIMUM_DIRECT_QUERIES_PER_SLICE;
        String status;
        if (!sufficient) {
            status = "INSUFFICIENT_SAMPLE";
        } else if (top1Interval.upper() < 0.0d || mrrInterval.upper() < 0.0d) {
            status = "BLOCKING_REGRESSION";
        } else if (top1Interval.lower() >= 0.0d && mrrInterval.lower() >= 0.0d) {
            status = "NON_INFERIOR";
        } else {
            status = "INCONCLUSIVE";
        }
        return new PairedSlice(
                slice,
                byUser.size(),
                direct.size(),
                sufficient,
                top1Delta,
                mrrDelta,
                userMacroTop1Delta,
                userMacroMrrDelta,
                wins(direct, QueryResultView::top1Delta),
                losses(direct, QueryResultView::top1Delta),
                ties(direct, QueryResultView::top1Delta),
                wins(direct, QueryResultView::mrrDelta),
                losses(direct, QueryResultView::mrrDelta),
                ties(direct, QueryResultView::mrrDelta),
                top1Interval,
                mrrInterval,
                status);
    }

    private Map<String, PairedSlice> grouped(
            List<SearchV3DenseAblationEngine.QueryResult> values,
            Function<QueryResultView, String> classifier) {
        return values.stream()
                .map(QueryResultView::from)
                .collect(Collectors.groupingBy(
                        classifier,
                        LinkedHashMap::new,
                        Collectors.toList()))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> summarizeViews(entry.getKey(), entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private PairedSlice summarizeViews(String slice, List<QueryResultView> values) {
        return summarize(slice, values.stream().map(QueryResultView::source).toList());
    }

    private ConfidenceInterval bootstrap(
            Map<String, List<QueryResultView>> byUser,
            Function<QueryResultView, Double> metric) {
        if (byUser.isEmpty()) {
            return new ConfidenceInterval(0.0d, 0.0d);
        }
        List<List<QueryResultView>> clusters = new ArrayList<>(byUser.values());
        SplittableRandom random = new SplittableRandom(BOOTSTRAP_SEED);
        double[] samples = new double[BOOTSTRAP_SAMPLES];
        for (int sample = 0; sample < BOOTSTRAP_SAMPLES; sample++) {
            double sum = 0.0d;
            int count = 0;
            for (int cluster = 0; cluster < clusters.size(); cluster++) {
                List<QueryResultView> selected = clusters.get(random.nextInt(clusters.size()));
                for (QueryResultView value : selected) {
                    sum += metric.apply(value);
                    count++;
                }
            }
            samples[sample] = count == 0 ? 0.0d : sum / count;
        }
        java.util.Arrays.sort(samples);
        return new ConfidenceInterval(
                percentile(samples, 0.025d),
                percentile(samples, 0.975d));
    }

    private boolean boundaryInvariants(SearchV3DenseAblationEngine.ExperimentReport report) {
        return report.passageCorpus().contaminationRate() == 0.0d
                && report.passageCorpus().fragmentationRate() == 0.0d
                && report.passageStats().crossParentPassageViolationCount() == 0L
                && report.passageStats().directGoldEvidenceChildPreservationRate() == 1.0d
                && report.passageCorpus().headingOnlyCandidateCount() == 0L
                && report.passageHeadingOnlyRank1Count() == 0L
                && report.queryMicro().structural().recallAtK().entrySet().stream()
                        .allMatch(entry -> report.queryMicro().passage().recallAtK()
                                .getOrDefault(entry.getKey(), -1.0d) >= entry.getValue());
    }

    private List<SearchV3DenseAblationEngine.QueryResult> direct(
            List<SearchV3DenseAblationEngine.QueryResult> values) {
        return values.stream().filter(SearchV3DenseAblationEngine.QueryResult::directSupport).toList();
    }

    private void requireUniqueQueryIds(
            List<SearchV3DenseAblationEngine.QueryResult> values,
            String label) {
        long unique = values.stream().map(SearchV3DenseAblationEngine.QueryResult::queryId).distinct().count();
        if (unique != values.size()) {
            throw new IllegalArgumentException("Duplicate query ID in " + label);
        }
    }

    private double ratioReduction(long baseline, long candidate) {
        return baseline == 0L ? 0.0d : 1.0d - ((double) candidate / baseline);
    }

    private boolean nonNegative(double value) {
        return value >= -1.0e-12d;
    }

    private double mean(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
    }

    private double mean(List<QueryResultView> values, Function<QueryResultView, Double> metric) {
        return values.stream().mapToDouble(value -> metric.apply(value)).average().orElse(0.0d);
    }

    private int wins(List<QueryResultView> values, Function<QueryResultView, Double> metric) {
        return (int) values.stream().filter(value -> metric.apply(value) > 1.0e-12d).count();
    }

    private int losses(List<QueryResultView> values, Function<QueryResultView, Double> metric) {
        return (int) values.stream().filter(value -> metric.apply(value) < -1.0e-12d).count();
    }

    private int ties(List<QueryResultView> values, Function<QueryResultView, Double> metric) {
        return values.size() - wins(values, metric) - losses(values, metric);
    }

    private double percentile(double[] sorted, double value) {
        int index = (int) Math.floor(value * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    record QueryResultView(
            SearchV3DenseAblationEngine.QueryResult source,
            String userBundleId,
            String professionGroup,
            String language,
            double top1Delta,
            double mrrDelta) {

        static QueryResultView from(SearchV3DenseAblationEngine.QueryResult source) {
            return new QueryResultView(
                    source,
                    source.userBundleId(),
                    source.professionGroup(),
                    source.language(),
                    (source.passage().top1() ? 1.0d : 0.0d)
                            - (source.structural().top1() ? 1.0d : 0.0d),
                    source.passage().reciprocalRank() - source.structural().reciprocalRank());
        }
    }

    record ConfidenceInterval(double lower, double upper) {
    }

    record PairedSlice(
            String slice,
            int userBundleCount,
            int directQueryCount,
            boolean sufficient,
            double top1Delta,
            double mrrDelta,
            double userMacroTop1Delta,
            double userMacroMrrDelta,
            int top1Wins,
            int top1Losses,
            int top1Ties,
            int mrrWins,
            int mrrLosses,
            int mrrTies,
            ConfidenceInterval top1ConfidenceInterval,
            ConfidenceInterval mrrConfidenceInterval,
            String status) {

        boolean blockingRegression() {
            return "BLOCKING_REGRESSION".equals(status);
        }
    }

    record RobustnessAssessment(
            int minimumBundlesPerSlice,
            int minimumDirectQueriesPerSlice,
            int bootstrapSamples,
            long bootstrapSeed,
            double minimumCandidateReduction,
            double candidateReduction,
            boolean boundaryInvariants,
            boolean freshPointNonRegression,
            boolean blockingSliceRegression,
            PairedSlice freshOverall,
            PairedSlice freshFrontend,
            Map<String, PairedSlice> cumulativeProfession,
            Map<String, PairedSlice> cumulativeLanguage,
            String decision) {
    }
}
