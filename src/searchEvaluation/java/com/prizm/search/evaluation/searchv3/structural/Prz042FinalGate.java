package com.prizm.search.evaluation.searchv3.structural;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Applies the immutable pre-result PRZ-042 Gate without changing frozen search output. */
final class Prz042FinalGate {

    static final int BOOTSTRAP_SAMPLES = 10_000;
    static final long BOOTSTRAP_SEED = 42_042L;
    static final int RELEASE_MIN_USERS = 50;
    static final int ADEQUATE_SLICE_MIN_USERS = 3;
    static final int ADEQUATE_SLICE_MIN_DIRECT_QUERIES = 5;

    GateReport evaluate(
            SearchV3MinimalShadowEvaluator.EvaluationReport evaluation,
            Prz042FinalFreeze.RuntimeAudit runtimeAudit,
            Prz042FinalFreeze.VerifiedInput input) {
        Comparison micro = comparison(evaluation.queryMicro());
        Comparison macro = comparison(evaluation.userMacro());
        ConfidenceInterval primaryCi = pairedUserBootstrap(evaluation.queries());

        List<String> adequacyFindings = new ArrayList<>();
        if (input.userBundleCount() < RELEASE_MIN_USERS) {
            adequacyFindings.add("user bundles " + input.userBundleCount() + " < " + RELEASE_MIN_USERS);
        }
        adequacyFindings.add("profession/language/PDF release distribution and independent adjudication are absent");
        Status releaseAdequacy = adequacyFindings.isEmpty() ? Status.PASS : Status.FAIL;

        Status safety = status(runtimeAudit.ownerLeakageCount() == 0
                && runtimeAudit.inactiveVersionLeakageCount() == 0
                && runtimeAudit.lifecycleViolationCount() == 0
                && runtimeAudit.duplicateArtifactCount() == 0
                && runtimeAudit.mixedArtifactCount() == 0
                && evaluation.queryMicro().v2().ownerLeakageQueryRate() == 0.0d
                && evaluation.queryMicro().v2().inactiveVersionLeakageQueryRate() == 0.0d
                && evaluation.queryMicro().v3().ownerLeakageQueryRate() == 0.0d
                && evaluation.queryMicro().v3().inactiveVersionLeakageQueryRate() == 0.0d
                && evaluation.queryMicro().v3().finalStructure().crossParentContaminationRate() == 0.0d);
        Status primary = releaseAdequacy == Status.PASS
                ? status(macro.top1Delta() >= 0.03d && primaryCi.lower() > 0.0d)
                : Status.NOT_ASSESSED;
        Status secondary = status(micro.top1Delta() >= -0.01d
                && micro.mrrDelta() >= -0.01d
                && micro.ndcgAt5Delta() >= -0.01d
                && micro.recallAt5Delta() >= -0.01d
                && macro.mrrDelta() >= -0.01d);
        Status localization = status(evaluation.queryMicro().v3().finalStructure().localizationPrecision()
                        >= evaluation.queryMicro().v2().finalStructure().localizationPrecision() - 0.01d
                && evaluation.queryMicro().v3().finalStructure().localizationRecall()
                        >= evaluation.queryMicro().v2().finalStructure().localizationRecall() - 0.01d
                && evaluation.queryMicro().v3().finalStructure().localizationPrecision() >= 0.95d);
        Status queryLatency = status(withinQueryBudget(evaluation.operation()));
        // V2 indexing is an evaluation adapter over real TextChunker/repository components, not
        // DocumentIndexingProcessor. Preserve the numbers, but do not certify this Gate.
        Status indexingAndStorage = Status.NOT_ASSESSED;
        Status actualRuntime = status(runtimeAudit.realBgeM3()
                && input.modelId().equals(runtimeAudit.modelId())
                && input.modelDigest().equals(runtimeAudit.modelDigest())
                && input.modelDimension() == runtimeAudit.modelDimension()
                && runtimeAudit.v2QueryExecutions() == evaluation.queries().size()
                && runtimeAudit.v3QueryExecutions() == evaluation.queries().size());
        Status resources = status(runtimeAudit.additionalModelCount() == 0
                && runtimeAudit.additionalServiceCount() == 0
                && !runtimeAudit.gpuRequired());
        Status typed = typedStatus(evaluation.typed());
        // Semantic FOUND/NONE is intentionally absent, so no-answer quality is not certified.
        Status noAnswer = Status.NOT_ASSESSED;

        Map<String, SliceGate> professions = sliceGates(
                evaluation.professionSlices(), evaluation.queries(),
                SearchV3MinimalShadowEvaluator.QueryEvaluation::professionGroup);
        Map<String, SliceGate> languages = sliceGates(
                evaluation.languageSlices(), evaluation.queries(),
                SearchV3MinimalShadowEvaluator.QueryEvaluation::language);
        Status sliceRegression = combinedSliceStatus(professions, languages);

        boolean materialQualityRegression = micro.top1Delta() < -0.01d
                || micro.mrrDelta() < -0.01d
                || micro.recallAt5Delta() < -0.01d
                || secondary == Status.FAIL
                || sliceRegression == Status.FAIL;
        Verdict verdict;
        if (safety == Status.FAIL || actualRuntime == Status.FAIL || resources == Status.FAIL
                || materialQualityRegression) {
            verdict = Verdict.V3_NO_GO;
        }
        else if (releaseAdequacy == Status.PASS
                && primary == Status.PASS
                && secondary == Status.PASS
                && localization == Status.PASS
                && queryLatency == Status.PASS
                && indexingAndStorage == Status.PASS
                && typed != Status.FAIL
                && noAnswer != Status.NOT_ASSESSED
                && sliceRegression == Status.PASS) {
            verdict = Verdict.V3_ADOPT;
        }
        else {
            verdict = Verdict.V3_NEEDS_ADJUSTMENT;
        }

        return new GateReport(
                verdict,
                "SEED_FINAL_PROTOCOL_RESULT",
                releaseAdequacy,
                List.copyOf(adequacyFindings),
                safety,
                primary,
                secondary,
                localization,
                queryLatency,
                indexingAndStorage,
                actualRuntime,
                resources,
                typed,
                noAnswer,
                sliceRegression,
                micro,
                macro,
                primaryCi,
                professions,
                languages,
                evaluation.semantic().notSupportedQueryCount(),
                negativeDirectTop1(evaluation.queries(), true),
                negativeDirectTop1(evaluation.queries(), false),
                evaluation.semantic().v3AssessedStateViolationCount(),
                evaluation.typed(),
                evaluation.operation());
    }

    private static Status typedStatus(SearchV3MinimalShadowEvaluator.TypedAggregate typed) {
        if (typed.queryCount() == 0) return Status.NOT_ASSESSED;
        return status(typed.stateAccuracy() == 1.0d
                && typed.falseNoneCount() == 0
                && typed.wrongValueCount() == 0
                && typed.wrongDateCount() == 0
                && typed.wrongVersionCount() == 0
                && typed.qualifierMismatchCount() == 0
                && typed.constraintCorrectEvidencePrecision() >= 0.95d);
    }

    private static long negativeDirectTop1(
            List<SearchV3MinimalShadowEvaluator.QueryEvaluation> queries,
            boolean v2) {
        return queries.stream()
                .filter(query -> "NOT_SUPPORTED".equals(query.answerability()))
                .filter(query -> (v2 ? query.v2() : query.v3()).finalRanking().top1Relation()
                        == SearchV3MinimalShadowEvaluator.Relation.DIRECT_SUPPORT)
                .count();
    }

    private static Status combinedSliceStatus(
            Map<String, SliceGate> professions,
            Map<String, SliceGate> languages) {
        List<Status> statuses = java.util.stream.Stream.concat(
                        professions.values().stream(), languages.values().stream())
                .map(SliceGate::status).toList();
        if (statuses.contains(Status.FAIL)) return Status.FAIL;
        if (statuses.contains(Status.PASS)) return Status.PASS;
        return Status.NOT_ASSESSED;
    }

    private static Map<String, SliceGate> sliceGates(
            Map<String, SearchV3MinimalShadowEvaluator.ComparisonAggregate> aggregates,
            List<SearchV3MinimalShadowEvaluator.QueryEvaluation> queries,
            Function<SearchV3MinimalShadowEvaluator.QueryEvaluation, String> key) {
        Map<String, SliceGate> result = new LinkedHashMap<>();
        aggregates.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            List<SearchV3MinimalShadowEvaluator.QueryEvaluation> values = queries.stream()
                    .filter(query -> entry.getKey().equals(key.apply(query))).toList();
            int users = new LinkedHashSet<>(values.stream()
                    .map(SearchV3MinimalShadowEvaluator.QueryEvaluation::userBundleId).toList()).size();
            int direct = (int) values.stream()
                    .filter(SearchV3MinimalShadowEvaluator.QueryEvaluation::directPositive).count();
            Comparison metrics = comparison(entry.getValue());
            Status valueStatus = users >= ADEQUATE_SLICE_MIN_USERS
                            && direct >= ADEQUATE_SLICE_MIN_DIRECT_QUERIES
                    ? status(metrics.top1Delta() >= -0.05d && metrics.mrrDelta() >= -0.05d)
                    : Status.NOT_ASSESSED;
            result.put(entry.getKey(), new SliceGate(users, direct, valueStatus, metrics));
        });
        return Map.copyOf(result);
    }

    private static boolean withinQueryBudget(SearchV3MinimalShadowEvaluator.OperationAggregate operation) {
        double budget = Math.min(operation.v2QueryP95Ms() * 1.5d, operation.v2QueryP95Ms() + 100.0d);
        return operation.v3QueryP95Ms() <= budget;
    }

    private static Comparison comparison(SearchV3MinimalShadowEvaluator.ComparisonAggregate value) {
        SearchV3MinimalShadowEvaluator.RankingAggregate v2 = value.v2().finalRanking();
        SearchV3MinimalShadowEvaluator.RankingAggregate v3 = value.v3().finalRanking();
        return new Comparison(
                value.aggregationUnitCount(), value.queryCount(), value.directPositiveQueryCount(),
                v2.top1(), v3.top1(), v3.top1() - v2.top1(),
                v2.mrr(), v3.mrr(), v3.mrr() - v2.mrr(),
                v2.ndcgAt5(), v3.ndcgAt5(), v3.ndcgAt5() - v2.ndcgAt5(),
                v2.directRecallAt5(), v3.directRecallAt5(),
                v3.directRecallAt5() - v2.directRecallAt5());
    }

    private static ConfidenceInterval pairedUserBootstrap(
            List<SearchV3MinimalShadowEvaluator.QueryEvaluation> queries) {
        Map<String, List<SearchV3MinimalShadowEvaluator.QueryEvaluation>> byUser = queries.stream()
                .filter(SearchV3MinimalShadowEvaluator.QueryEvaluation::directPositive)
                .collect(Collectors.groupingBy(
                        SearchV3MinimalShadowEvaluator.QueryEvaluation::userBundleId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<Double> deltas = byUser.values().stream().map(values -> {
            double v2 = values.stream().filter(value -> value.v2().finalRanking().top1()).count()
                    / (double) values.size();
            double v3 = values.stream().filter(value -> value.v3().finalRanking().top1()).count()
                    / (double) values.size();
            return v3 - v2;
        }).toList();
        if (deltas.isEmpty()) {
            return new ConfidenceInterval(BOOTSTRAP_SAMPLES, BOOTSTRAP_SEED, 0, 0.0d, 0.0d, 0.0d);
        }
        Random random = new Random(BOOTSTRAP_SEED);
        List<Double> sampled = new ArrayList<>(BOOTSTRAP_SAMPLES);
        for (int sample = 0; sample < BOOTSTRAP_SAMPLES; sample++) {
            double sum = 0.0d;
            for (int index = 0; index < deltas.size(); index++) {
                sum += deltas.get(random.nextInt(deltas.size()));
            }
            sampled.add(sum / deltas.size());
        }
        sampled.sort(Comparator.naturalOrder());
        return new ConfidenceInterval(
                BOOTSTRAP_SAMPLES, BOOTSTRAP_SEED, deltas.size(),
                deltas.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d),
                percentile(sampled, 0.025d), percentile(sampled, 0.975d));
    }

    private static double percentile(List<Double> sorted, double percentile) {
        int index = (int) Math.floor(percentile * (sorted.size() - 1));
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static Status status(boolean passed) {
        return passed ? Status.PASS : Status.FAIL;
    }

    enum Verdict { V3_ADOPT, V3_NEEDS_ADJUSTMENT, V3_NO_GO }
    enum Status { PASS, FAIL, NOT_ASSESSED }

    record GateReport(
            Verdict verdict,
            String resultScope,
            Status releaseAdequacy,
            List<String> adequacyFindings,
            Status safety,
            Status primaryQuality,
            Status secondaryQuality,
            Status localization,
            Status queryLatency,
            Status indexingAndStorage,
            Status actualRuntime,
            Status resources,
            Status typedContract,
            Status noAnswerContract,
            Status sliceRegression,
            Comparison queryMicro,
            Comparison userMacro,
            ConfidenceInterval primaryConfidenceInterval,
            Map<String, SliceGate> professionSlices,
            Map<String, SliceGate> languageSlices,
            int notSupportedQueryCount,
            long v2NotSupportedDirectTop1,
            long v3NotSupportedDirectTop1,
            long v3AssessedStateViolationCount,
            SearchV3MinimalShadowEvaluator.TypedAggregate typed,
            SearchV3MinimalShadowEvaluator.OperationAggregate operation) {
    }

    record SliceGate(int userCount, int directPositiveQueryCount, Status status, Comparison metrics) {
    }

    record Comparison(
            int aggregationUnitCount,
            int queryCount,
            int directPositiveQueryCount,
            double v2Top1,
            double v3Top1,
            double top1Delta,
            double v2Mrr,
            double v3Mrr,
            double mrrDelta,
            double v2NdcgAt5,
            double v3NdcgAt5,
            double ndcgAt5Delta,
            double v2RecallAt5,
            double v3RecallAt5,
            double recallAt5Delta) {
    }

    record ConfidenceInterval(
            int samples,
            long seed,
            int userCount,
            double pointEstimate,
            double lower,
            double upper) {
    }
}
