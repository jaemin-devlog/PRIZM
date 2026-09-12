package com.prizm.search.evaluation.searchv3.structural;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Pre-registered C1 gate comparing an unchanged B3 passage with heading-path context only. */
final class ParentContextAblationGate {

    private static final double EPSILON = 1.0e-12d;

    Assessment assess(List<SearchV3DenseAblationEngine.ParentContextExperimentReport> reports) {
        if (reports.isEmpty()) {
            throw new IllegalArgumentException("C1 assessment requires at least one frozen dataset report");
        }
        List<String> boundaryFailures = new ArrayList<>();
        List<String> qualityRegressions = new ArrayList<>();
        Set<String> winningBundles = new LinkedHashSet<>();
        Set<String> losingBundles = new LinkedHashSet<>();
        int wins = 0;
        int losses = 0;
        int ties = 0;
        long falseHitCount = 0L;
        boolean aggregateImprovement = false;

        for (SearchV3DenseAblationEngine.ParentContextExperimentReport report : reports) {
            String dataset = report.datasetVersion();
            validateBoundary(report, dataset, boundaryFailures);
            validateQuality(report, dataset, qualityRegressions);
            falseHitCount += report.queries().stream()
                    .mapToLong(query -> query.contextOnlyFalseHits().size())
                    .sum();
            for (SearchV3DenseAblationEngine.ParentContextQueryResult query : report.queries()) {
                switch (SearchV3DenseAblationEngine.RankOutcome.valueOf(query.directRankOutcome())) {
                    case WIN -> {
                        wins++;
                        winningBundles.add(query.userBundleId());
                    }
                    case LOSS -> {
                        losses++;
                        losingBundles.add(query.userBundleId());
                    }
                    case TIE -> ties++;
                    case NOT_APPLICABLE -> {
                        // Negative queries are diagnostic-only for the raw Dense ranking gate.
                    }
                }
            }
            aggregateImprovement |= greater(
                            report.queryMicro().context().top1(), report.queryMicro().passage().top1())
                    || greater(report.queryMicro().context().mrr(), report.queryMicro().passage().mrr());
        }
        if (falseHitCount > 0L) {
            qualityRegressions.add("context-only false hits=" + falseHitCount);
        }
        boolean boundaryInvariants = boundaryFailures.isEmpty();
        boolean qualityNonRegression = qualityRegressions.isEmpty();
        boolean meaningfulGain = aggregateImprovement || winningBundles.size() >= 2;
        String decision = decide(boundaryInvariants, qualityNonRegression, meaningfulGain);
        return new Assessment(
                reports.size(),
                boundaryInvariants,
                qualityNonRegression,
                meaningfulGain,
                aggregateImprovement,
                wins,
                losses,
                ties,
                Set.copyOf(winningBundles),
                Set.copyOf(losingBundles),
                falseHitCount,
                List.copyOf(boundaryFailures),
                List.copyOf(qualityRegressions),
                decision);
    }

    String decide(boolean boundaryInvariants, boolean qualityNonRegression, boolean meaningfulGain) {
        if (!boundaryInvariants) {
            return "NO_GO";
        }
        if (!qualityNonRegression) {
            return "NEEDS_ADJUSTMENT";
        }
        return meaningfulGain ? "PROMISING" : "NO_GO";
    }

    private void validateBoundary(
            SearchV3DenseAblationEngine.ParentContextExperimentReport report,
            String dataset,
            List<String> failures) {
        if (report.passageCorpus().candidateCount() != report.contextCorpus().candidateCount()) {
            failures.add(dataset + ": candidate count parity");
        }
        if (report.passageCorpus().embeddingCount() != report.contextCorpus().embeddingCount()) {
            failures.add(dataset + ": embedding count parity");
        }
        if (report.passageCorpus().contaminationRate() != 0.0d
                || report.contextCorpus().contaminationRate() != 0.0d) {
            failures.add(dataset + ": contamination must remain zero");
        }
        if (different(
                report.contextCorpus().fragmentationRate(), report.passageCorpus().fragmentationRate())) {
            failures.add(dataset + ": fragmentation parity");
        }
        if (report.passageStats().crossParentPassageViolationCount() != 0L
                || report.contextStats().crossParentContextViolationCount() != 0L) {
            failures.add(dataset + ": cross-parent violation");
        }
        if (different(report.passageStats().directGoldEvidenceChildPreservationRate(), 1.0d)) {
            failures.add(dataset + ": Gold EvidenceChild preservation");
        }
        if (report.contextStats().sourceParityViolationCount() != 0L
                || report.contextStats().evidenceChildParityViolationCount() != 0L) {
            failures.add(dataset + ": B3 source/evidence parity");
        }
        if (report.contextCorpus().headingOnlyCandidateCount() != 0L) {
            failures.add(dataset + ": heading-only candidate");
        }
        if (report.sealedFinalOpened()
                || report.sealedFinalSearchExecuted()
                || !"NOT_RUN".equals(report.currentFreshBaseline())) {
            failures.add(dataset + ": SEALED FINAL or fresh baseline guard");
        }
    }

    private void validateQuality(
            SearchV3DenseAblationEngine.ParentContextExperimentReport report,
            String dataset,
            List<String> regressions) {
        validateAggregate(dataset + ": query-micro", report.queryMicro(), regressions, true);
        validateAggregate(dataset + ": user-macro", report.userMacro(), regressions, true);
        validateGrouped(dataset + ": profession", report.professionMetrics(), regressions);
        validateGrouped(dataset + ": language", report.languageMetrics(), regressions);
    }

    private void validateGrouped(
            String label,
            Map<String, SearchV3DenseAblationEngine.ParentContextAggregateComparison> groups,
            List<String> regressions) {
        groups.forEach((group, comparison) -> validateAggregate(
                label + "/" + group, comparison, regressions, false));
    }

    private void validateAggregate(
            String label,
            SearchV3DenseAblationEngine.ParentContextAggregateComparison comparison,
            List<String> regressions,
            boolean includeRecall) {
        if (less(comparison.context().top1(), comparison.passage().top1())) {
            regressions.add(label + ": Top1 regression");
        }
        if (less(comparison.context().mrr(), comparison.passage().mrr())) {
            regressions.add(label + ": MRR regression");
        }
        if (includeRecall) {
            comparison.passage().recallAtK().forEach((cutoff, baseline) -> {
                if (less(comparison.context().recallAtK().getOrDefault(cutoff, -1.0d), baseline)) {
                    regressions.add(label + ": Recall@" + cutoff + " regression");
                }
            });
        }
    }

    private boolean greater(double left, double right) {
        return left - right > EPSILON;
    }

    private boolean less(double left, double right) {
        return right - left > EPSILON;
    }

    private boolean different(double left, double right) {
        return Math.abs(left - right) > EPSILON;
    }

    record Assessment(
            int datasetReportCount,
            boolean boundaryInvariants,
            boolean qualityNonRegression,
            boolean meaningfulGain,
            boolean aggregateImprovement,
            int directRankWins,
            int directRankLosses,
            int directRankTies,
            Set<String> winningBundles,
            Set<String> losingBundles,
            long contextOnlyFalseHitCount,
            List<String> boundaryFailures,
            List<String> qualityRegressions,
            String decision) {
    }
}
