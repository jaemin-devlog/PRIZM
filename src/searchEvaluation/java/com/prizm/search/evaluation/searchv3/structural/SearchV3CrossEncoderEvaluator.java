package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.DatasetSlice;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.ExpectedEvidence;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.Query;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.BaselineBundle;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.BaselineDataset;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.DatasetRun;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.PreparedInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.PreparedQuestion;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.ScoreOutput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.ScorePair;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.ScoreQuestion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Strict R0/R1 comparison. Gold is joined only after the Gold-free score artifact is validated. */
final class SearchV3CrossEncoderEvaluator {

    private static final double EPSILON = 1.0e-12d;
    private static final long GIB = 1024L * 1024L * 1024L;
    private final SearchV3DenseAblationEngine engine = new SearchV3DenseAblationEngine();

    EvaluationReport evaluate(
            PreparedInput prepared,
            BaselineBundle baseline,
            ScoreOutput scores,
            List<DatasetRun> runs) {
        Map<String, BaselineDataset> baselineByVersion = baseline.datasets().stream()
                .collect(Collectors.toMap(
                        value -> value.report().datasetVersion(),
                        value -> value,
                        (left, right) -> {
                            throw new IllegalArgumentException("Duplicate baseline dataset version");
                        },
                        LinkedHashMap::new));
        Map<String, DatasetRun> runsByVersion = runs.stream()
                .collect(Collectors.toMap(
                        value -> value.report().datasetVersion(),
                        value -> value,
                        (left, right) -> {
                            throw new IllegalArgumentException("Duplicate frozen dataset run");
                        },
                        LinkedHashMap::new));
        Map<String, PreparedQuestion> preparedQuestions = prepared.datasets().stream()
                .flatMap(dataset -> dataset.questions().stream()
                        .map(question -> Map.entry(key(dataset.datasetVersion(), question.questionId()), question)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<String, ScoreQuestion> scoredQuestions = scores.questions().stream()
                .collect(Collectors.toMap(
                        question -> key(question.datasetVersion(), question.questionId()),
                        Function.identity()));

        if (!baselineByVersion.keySet().equals(runsByVersion.keySet())) {
            throw new IllegalArgumentException("Baseline and frozen dataset versions differ");
        }
        List<DatasetComparison> datasets = new ArrayList<>();
        List<QueryComparison> combinedQueries = new ArrayList<>();
        boolean candidateParity = true;
        boolean provenanceParity = true;
        for (BaselineDataset baselineDataset : baseline.datasets()) {
            String version = baselineDataset.report().datasetVersion();
            DatasetRun run = runsByVersion.get(version);
            Map<String, DatasetSlice> slices = run.slices().stream()
                    .collect(Collectors.toMap(slice -> slice.split().manifestName(), Function.identity()));
            Map<String, Query> queries = run.slices().stream()
                    .flatMap(slice -> slice.queries().stream())
                    .collect(Collectors.toMap(Query::queryId, Function.identity()));
            List<QueryComparison> queryComparisons = new ArrayList<>();
            for (SearchV3DenseAblationEngine.QueryResult baselineQuery : baselineDataset.report().queries()) {
                String questionKey = key(version, baselineQuery.queryId());
                PreparedQuestion preparedQuestion = preparedQuestions.get(questionKey);
                ScoreQuestion scoredQuestion = scoredQuestions.get(questionKey);
                Query query = queries.get(baselineQuery.queryId());
                DatasetSlice slice = slices.get(baselineQuery.split().manifestName());
                if (preparedQuestion == null || scoredQuestion == null || query == null || slice == null) {
                    throw new IllegalArgumentException("R0/R1 join identity is incomplete: " + questionKey);
                }
                List<SearchV3DenseAblationEngine.RankedCandidate> r0 =
                        baselineQuery.passage().rawDenseRanking();
                List<SearchV3DenseAblationEngine.RankedCandidate> r1 = rerank(r0, scoredQuestion);
                candidateParity &= candidateIds(r0).equals(candidateIds(r1));
                provenanceParity &= candidatePayloads(r0).equals(candidatePayloads(r1));
                if (!candidateParity || !provenanceParity) {
                    throw new IllegalArgumentException("R1 changed B3 candidate identity or provenance");
                }
                long rerankNanos = Math.round(scoredQuestion.rerankMillis() * 1_000_000.0d);
                SearchV3DenseAblationEngine.QueryProfileResult r1Profile =
                        engine.queryResult(query, r1, slice, 0L, rerankNanos);
                double r0Ndcg = ndcgAt5(query, r0);
                double r1Ndcg = ndcgAt5(query, r1);
                String outcome = outcome(baselineQuery.passage().firstDirectRank(), r1Profile.firstDirectRank());
                double topScore = scoredQuestion.pairs().stream()
                        .mapToDouble(ScorePair::score)
                        .max()
                        .orElseThrow();
                queryComparisons.add(new QueryComparison(
                        baselineDataset.label(),
                        version,
                        baselineQuery.queryId(),
                        baselineQuery.userBundleId(),
                        baselineQuery.split().manifestName(),
                        baselineQuery.professionGroup(),
                        baselineQuery.language(),
                        baselineQuery.categories(),
                        baselineQuery.directSupport(),
                        baselineQuery.passage().firstDirectRank(),
                        r1Profile.firstDirectRank(),
                        outcome,
                        metrics(baselineQuery.passage(), r0Ndcg),
                        metrics(r1Profile, r1Ndcg),
                        topScore,
                        scoredQuestion.rerankMillis()));
            }
            DatasetComparison comparison = new DatasetComparison(
                    baselineDataset.label(),
                    version,
                    aggregate(queryComparisons, false),
                    aggregate(queryComparisons, true),
                    slices(queryComparisons, QueryComparison::professionGroup),
                    slices(queryComparisons, QueryComparison::language),
                    categorySlices(queryComparisons),
                    List.copyOf(queryComparisons));
            datasets.add(comparison);
            combinedQueries.addAll(queryComparisons);
        }
        if (!preparedQuestions.keySet().equals(scoredQuestions.keySet())) {
            throw new IllegalArgumentException("Validated score questions differ from prepared questions");
        }

        Safety safety = safety(candidateParity, provenanceParity, baseline);
        Aggregate combinedMicro = aggregate(combinedQueries, false);
        Aggregate combinedUserMacro = aggregate(combinedQueries, true);
        Map<String, SliceComparison> profession = slices(combinedQueries, QueryComparison::professionGroup);
        Map<String, SliceComparison> language = slices(combinedQueries, QueryComparison::language);
        Map<String, SliceComparison> category = categorySlices(combinedQueries);
        OutcomeCounts outcomes = outcomes(combinedQueries);
        Operation operation = operation(scores);
        Gate gate = gate(safety, datasets, combinedMicro, combinedUserMacro, profession, language, outcomes, operation);
        return new EvaluationReport(
                1,
                "PRZ-027-R0-B3-DENSE-VS-R1-GTE-CROSS-ENCODER",
                prepared.inputDigest(),
                candidateParity,
                provenanceParity,
                safety,
                List.copyOf(datasets),
                combinedMicro,
                combinedUserMacro,
                profession,
                language,
                category,
                outcomes,
                negativeScores(combinedQueries),
                operation,
                gate,
                false,
                false,
                "NOT_RUN");
    }

    List<SearchV3DenseAblationEngine.RankedCandidate> rerank(
            List<SearchV3DenseAblationEngine.RankedCandidate> dense,
            ScoreQuestion scores) {
        Map<String, SearchV3DenseAblationEngine.RankedCandidate> byId = dense.stream()
                .collect(Collectors.toMap(
                        SearchV3DenseAblationEngine.RankedCandidate::candidateId,
                        Function.identity()));
        List<SearchV3DenseAblationEngine.RankedCandidate> ordered = new ArrayList<>();
        for (ScorePair score : scores.pairs()) {
            SearchV3DenseAblationEngine.RankedCandidate candidate = byId.get(score.candidateId());
            if (candidate == null || candidate.rank() != score.denseRank()) {
                throw new IllegalArgumentException("R1 score refers to an unknown or moved B3 candidate");
            }
            ordered.add(candidate);
        }
        Set<String> rerankedIds = ordered.stream()
                .map(SearchV3DenseAblationEngine.RankedCandidate::candidateId)
                .collect(Collectors.toSet());
        dense.stream().filter(candidate -> !rerankedIds.contains(candidate.candidateId())).forEach(ordered::add);
        List<SearchV3DenseAblationEngine.RankedCandidate> result = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            result.add(withRank(ordered.get(index), index + 1));
        }
        return List.copyOf(result);
    }

    private SearchV3DenseAblationEngine.RankedCandidate withRank(
            SearchV3DenseAblationEngine.RankedCandidate value,
            int rank) {
        return new SearchV3DenseAblationEngine.RankedCandidate(
                rank,
                value.candidateId(),
                value.cosineScore(),
                value.documentId(),
                value.versionId(),
                value.sourceBlockType(),
                value.sourceText(),
                value.contextText(),
                value.retrievalText(),
                value.parentAnnotationCandidateId(),
                value.sourceCodePointLength(),
                value.evidenceChildIds(),
                value.contextSourceBlockIds(),
                value.coveredUnitIds(),
                value.coveredGroupIds(),
                value.coveredParentIds());
    }

    private Set<String> candidateIds(List<SearchV3DenseAblationEngine.RankedCandidate> candidates) {
        return candidates.stream().map(SearchV3DenseAblationEngine.RankedCandidate::candidateId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Map<String, CandidatePayload> candidatePayloads(
            List<SearchV3DenseAblationEngine.RankedCandidate> candidates) {
        return candidates.stream().collect(Collectors.toMap(
                SearchV3DenseAblationEngine.RankedCandidate::candidateId,
                value -> new CandidatePayload(
                        value.cosineScore(), value.documentId(), value.versionId(), value.sourceBlockType(),
                        value.sourceText(), value.contextText(), value.retrievalText(),
                        value.parentAnnotationCandidateId(), value.sourceCodePointLength(),
                        value.evidenceChildIds(), value.contextSourceBlockIds(), value.coveredUnitIds(),
                        value.coveredGroupIds(), value.coveredParentIds())));
    }

    private double ndcgAt5(Query query, List<SearchV3DenseAblationEngine.RankedCandidate> ranking) {
        Set<String> directUnits = query.allExpectedEvidence().stream()
                .filter(expected -> "DIRECT_SUPPORT".equals(expected.supportRelation()))
                .map(ExpectedEvidence::evidenceUnitId)
                .collect(Collectors.toSet());
        if (directUnits.isEmpty()) {
            return 0.0d;
        }
        int relevantCandidates = (int) ranking.stream()
                .filter(candidate -> candidate.coveredUnitIds().stream().anyMatch(directUnits::contains))
                .count();
        double dcg = 0.0d;
        for (int index = 0; index < Math.min(5, ranking.size()); index++) {
            boolean relevant = ranking.get(index).coveredUnitIds().stream().anyMatch(directUnits::contains);
            if (relevant) {
                dcg += 1.0d / log2(index + 2.0d);
            }
        }
        double ideal = 0.0d;
        for (int index = 0; index < Math.min(5, relevantCandidates); index++) {
            ideal += 1.0d / log2(index + 2.0d);
        }
        return ideal == 0.0d ? 0.0d : dcg / ideal;
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2.0d);
    }

    private QueryMetrics metrics(SearchV3DenseAblationEngine.QueryProfileResult value, double ndcgAt5) {
        return new QueryMetrics(
                value.top1(),
                value.reciprocalRank(),
                ndcgAt5,
                Boolean.TRUE.equals(value.recallAtK().get(5)),
                Boolean.TRUE.equals(value.recallAtK().get(20)));
    }

    private String outcome(Integer before, Integer after) {
        if (before == null && after == null) {
            return "NOT_APPLICABLE";
        }
        if (before == null || after == null) {
            return after == null ? "LOSS" : "WIN";
        }
        return after < before ? "WIN" : after > before ? "LOSS" : "TIE";
    }

    private Aggregate aggregate(List<QueryComparison> queries, boolean userMacro) {
        List<QueryComparison> direct = queries.stream().filter(QueryComparison::directSupport).toList();
        if (!userMacro) {
            return aggregateDirect(direct, direct.stream().map(QueryComparison::userBundleId).distinct().count());
        }
        Map<String, List<QueryComparison>> byUser = direct.stream()
                .collect(Collectors.groupingBy(QueryComparison::userBundleId, LinkedHashMap::new, Collectors.toList()));
        List<Aggregate> users = byUser.values().stream()
                .map(values -> aggregateDirect(values, 1L))
                .toList();
        return new Aggregate(
                direct.size(),
                byUser.size(),
                average(users, Aggregate::r0Top1),
                average(users, Aggregate::r0Mrr),
                average(users, Aggregate::r0NdcgAt5),
                average(users, Aggregate::r0RecallAt5),
                average(users, Aggregate::r0RecallAt20),
                average(users, Aggregate::r1Top1),
                average(users, Aggregate::r1Mrr),
                average(users, Aggregate::r1NdcgAt5),
                average(users, Aggregate::r1RecallAt5),
                average(users, Aggregate::r1RecallAt20));
    }

    private Aggregate aggregateDirect(List<QueryComparison> direct, long userCount) {
        return new Aggregate(
                direct.size(),
                userCount,
                averageQueries(direct, value -> value.r0().top1() ? 1.0d : 0.0d),
                averageQueries(direct, value -> value.r0().mrr()),
                averageQueries(direct, value -> value.r0().ndcgAt5()),
                averageQueries(direct, value -> value.r0().recallAt5() ? 1.0d : 0.0d),
                averageQueries(direct, value -> value.r0().recallAt20() ? 1.0d : 0.0d),
                averageQueries(direct, value -> value.r1().top1() ? 1.0d : 0.0d),
                averageQueries(direct, value -> value.r1().mrr()),
                averageQueries(direct, value -> value.r1().ndcgAt5()),
                averageQueries(direct, value -> value.r1().recallAt5() ? 1.0d : 0.0d),
                averageQueries(direct, value -> value.r1().recallAt20() ? 1.0d : 0.0d));
    }

    private double averageQueries(List<QueryComparison> values, Function<QueryComparison, Double> getter) {
        return values.stream().mapToDouble(getter::apply).average().orElse(0.0d);
    }

    private double average(List<Aggregate> values, Function<Aggregate, Double> getter) {
        return values.stream().mapToDouble(getter::apply).average().orElse(0.0d);
    }

    private Map<String, SliceComparison> slices(
            List<QueryComparison> queries,
            Function<QueryComparison, String> classifier) {
        return queries.stream()
                .filter(QueryComparison::directSupport)
                .collect(Collectors.groupingBy(classifier, LinkedHashMap::new, Collectors.toList()))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new SliceComparison(
                                entry.getKey(),
                                entry.getValue().size(),
                                (int) entry.getValue().stream().map(QueryComparison::userBundleId).distinct().count(),
                                aggregate(entry.getValue(), false)),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private Map<String, SliceComparison> categorySlices(List<QueryComparison> queries) {
        Map<String, List<QueryComparison>> grouped = new LinkedHashMap<>();
        queries.stream().filter(QueryComparison::directSupport).forEach(query -> query.categories()
                .forEach(category -> grouped.computeIfAbsent(category, ignored -> new ArrayList<>()).add(query)));
        return grouped.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> new SliceComparison(
                        entry.getKey(),
                        entry.getValue().size(),
                        (int) entry.getValue().stream().map(QueryComparison::userBundleId).distinct().count(),
                        aggregate(entry.getValue(), false)),
                (left, right) -> left,
                LinkedHashMap::new));
    }

    private OutcomeCounts outcomes(List<QueryComparison> queries) {
        List<QueryComparison> direct = queries.stream().filter(QueryComparison::directSupport).toList();
        int rankOneBaseline = (int) direct.stream().filter(value -> Integer.valueOf(1).equals(value.r0DirectRank())).count();
        int rankOneLosses = (int) direct.stream()
                .filter(value -> Integer.valueOf(1).equals(value.r0DirectRank()))
                .filter(value -> !Integer.valueOf(1).equals(value.r1DirectRank()))
                .count();
        return new OutcomeCounts(
                countOutcome(direct, "WIN"),
                countOutcome(direct, "LOSS"),
                countOutcome(direct, "TIE"),
                (int) direct.stream().filter(value -> "WIN".equals(value.outcome()))
                        .map(QueryComparison::userBundleId).distinct().count(),
                (int) direct.stream()
                        .filter(value -> value.r0DirectRank() != null
                                && value.r0DirectRank() >= 2
                                && value.r0DirectRank() <= 5
                                && Integer.valueOf(1).equals(value.r1DirectRank()))
                        .count(),
                rankOneBaseline,
                rankOneLosses,
                rankOneBaseline == 0 ? 0.0d : (double) rankOneLosses / rankOneBaseline);
    }

    private int countOutcome(List<QueryComparison> queries, String outcome) {
        return (int) queries.stream().filter(value -> outcome.equals(value.outcome())).count();
    }

    private ScoreDistribution negativeScores(List<QueryComparison> queries) {
        List<Double> values = queries.stream()
                .filter(value -> !value.directSupport())
                .map(QueryComparison::rerankerTopScore)
                .sorted()
                .toList();
        if (values.isEmpty()) {
            return new ScoreDistribution(0, 0.0d, 0.0d, 0.0d, 0.0d);
        }
        return new ScoreDistribution(
                values.size(), values.get(0), percentile(values, 0.50d), percentile(values, 0.95d),
                values.get(values.size() - 1));
    }

    private Operation operation(ScoreOutput scores) {
        List<Double> queryMillis = scores.questions().stream().map(ScoreQuestion::rerankMillis).toList();
        int pairs = scores.questions().stream().mapToInt(ScoreQuestion::pairCount).sum();
        double totalMillis = queryMillis.stream().mapToDouble(Double::doubleValue).sum();
        long rssDelta = Math.max(0L, scores.processRssPeakBytes() - scores.processRssBeforeLoadBytes());
        return new Operation(
                scores.questions().size(),
                pairs,
                pairs == 0 ? 0.0d : totalMillis / pairs,
                percentile(queryMillis, 0.50d),
                percentile(queryMillis, 0.95d),
                scores.modelLoadMillis(),
                scores.warmupMillis(),
                scores.processRssBeforeLoadBytes(),
                scores.processRssAfterLoadBytes(),
                scores.processRssPeakBytes(),
                rssDelta,
                scores.modelWeightBytes(),
                scores.modelCacheBytes(),
                scores.gpuUsed(),
                scores.gpuPeakAllocatedBytes(),
                scores.gpuPeakReservedBytes());
    }

    private double percentile(List<Double> values, double percentile) {
        if (values.isEmpty()) {
            return 0.0d;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int rank = (int) Math.ceil(percentile * sorted.size());
        return sorted.get(Math.max(0, rank - 1));
    }

    private Safety safety(boolean candidateParity, boolean provenanceParity, BaselineBundle baseline) {
        boolean contamination = baseline.datasets().stream()
                .allMatch(value -> value.report().passageCorpus().contaminationRate() == 0.0d);
        boolean fragmentation = baseline.datasets().stream()
                .allMatch(value -> value.report().passageCorpus().fragmentationRate() == 0.0d);
        boolean crossParent = baseline.datasets().stream()
                .allMatch(value -> value.report().passageStats().crossParentPassageViolationCount() == 0L);
        boolean goldPreserved = baseline.datasets().stream()
                .allMatch(value -> value.report().passageStats().directGoldEvidenceChildPreservationRate() == 1.0d);
        boolean sealed = baseline.datasets().stream().allMatch(value ->
                !value.report().sealedFinalOpened()
                        && !value.report().sealedFinalSearchExecuted()
                        && "NOT_RUN".equals(value.report().currentFreshBaseline()));
        return new Safety(candidateParity, provenanceParity, contamination, fragmentation, crossParent, goldPreserved, sealed);
    }

    private Gate gate(
            Safety safety,
            List<DatasetComparison> datasets,
            Aggregate micro,
            Aggregate macro,
            Map<String, SliceComparison> profession,
            Map<String, SliceComparison> language,
            OutcomeCounts outcomes,
            Operation operation) {
        List<String> findings = new ArrayList<>();
        if (!safety.all()) {
            findings.add("candidate/provenance/B3/SEALED safety invariant failed");
            return new Gate("INVALID", false, List.copyOf(findings));
        }
        boolean recall20 = datasets.stream().allMatch(value ->
                value.queryMicro().r1RecallAt20() + EPSILON >= value.queryMicro().r0RecallAt20());
        boolean aggregateGain = micro.r1Top1() > micro.r0Top1() + EPSILON
                || micro.r1Mrr() > micro.r0Mrr() + EPSILON;
        boolean macroNoRegression = macro.r1Top1() + EPSILON >= macro.r0Top1()
                && macro.r1Mrr() + EPSILON >= macro.r0Mrr();
        boolean wins = outcomes.wins() > outcomes.losses() && outcomes.distinctWinningUsers() >= 2;
        boolean sliceNoRegression = sufficientSlicesNoRegression(profession)
                && sufficientSlicesNoRegression(language);
        boolean operationPromising = operation.queryP95Millis() <= 3_000.0d
                && operation.peakRssIncreaseBytes() <= 4L * GIB
                && operation.modelCacheBytes() <= GIB
                && !operation.gpuUsed();
        if (!recall20) findings.add("Recall@20 regressed");
        if (!aggregateGain) findings.add("combined Top1/MRR did not improve");
        if (!macroNoRegression) findings.add("user-macro Top1/MRR regressed");
        if (!wins) findings.add("wins did not exceed losses across at least two users");
        if (outcomes.rankOneLossRate() > 0.05d + EPSILON) findings.add("direct rank1 loss rate exceeded 5%");
        if (!sliceNoRegression) findings.add("sufficient profession/language slice regressed");
        if (!operationPromising) findings.add("frozen CPU operational target was not met");
        if (recall20 && aggregateGain && macroNoRegression && wins
                && outcomes.rankOneLossRate() <= 0.05d + EPSILON && sliceNoRegression && operationPromising) {
            return new Gate("PROMISING", true, List.of());
        }
        boolean noGo = !aggregateGain
                || outcomes.losses() > outcomes.wins()
                || outcomes.rankOneLossRate() > 0.10d + EPSILON
                || operation.queryP95Millis() > 10_000.0d
                || operation.peakRssIncreaseBytes() > 8L * GIB;
        return new Gate(noGo ? "NO_GO" : "NEEDS_ADJUSTMENT", false, List.copyOf(findings));
    }

    private boolean sufficientSlicesNoRegression(Map<String, SliceComparison> slices) {
        return slices.values().stream()
                .filter(value -> value.userCount() >= 3 && value.directQueryCount() >= 10)
                .allMatch(value -> value.metrics().r1Top1() + EPSILON >= value.metrics().r0Top1()
                        && value.metrics().r1Mrr() + EPSILON >= value.metrics().r0Mrr());
    }

    private String key(String datasetVersion, String questionId) {
        return SearchV3RerankerPairArtifacts.questionKey(datasetVersion, questionId);
    }

    record CandidatePayload(
            double cosineScore,
            String documentId,
            String versionId,
            String sourceBlockType,
            String sourceText,
            String contextText,
            String retrievalText,
            String parentAnnotationCandidateId,
            int sourceCodePointLength,
            List<String> evidenceChildIds,
            List<String> contextSourceBlockIds,
            List<String> coveredUnitIds,
            List<String> coveredGroupIds,
            List<String> coveredParentIds) {
    }

    record QueryMetrics(boolean top1, double mrr, double ndcgAt5, boolean recallAt5, boolean recallAt20) {
    }

    record QueryComparison(
            String dataset,
            String datasetVersion,
            String queryId,
            String userBundleId,
            String split,
            String professionGroup,
            String language,
            List<String> categories,
            boolean directSupport,
            Integer r0DirectRank,
            Integer r1DirectRank,
            String outcome,
            QueryMetrics r0,
            QueryMetrics r1,
            double rerankerTopScore,
            double rerankMillis) {
    }

    record Aggregate(
            int directQueryCount,
            long userCount,
            double r0Top1,
            double r0Mrr,
            double r0NdcgAt5,
            double r0RecallAt5,
            double r0RecallAt20,
            double r1Top1,
            double r1Mrr,
            double r1NdcgAt5,
            double r1RecallAt5,
            double r1RecallAt20) {
    }

    record SliceComparison(String slice, int directQueryCount, int userCount, Aggregate metrics) {
    }

    record DatasetComparison(
            String label,
            String datasetVersion,
            Aggregate queryMicro,
            Aggregate userMacro,
            Map<String, SliceComparison> profession,
            Map<String, SliceComparison> language,
            Map<String, SliceComparison> category,
            List<QueryComparison> queries) {
    }

    record OutcomeCounts(
            int wins,
            int losses,
            int ties,
            int distinctWinningUsers,
            int directRankTwoToFivePromotedToOne,
            int baselineDirectRankOneCount,
            int directRankOneLossCount,
            double rankOneLossRate) {
    }

    record ScoreDistribution(int count, double minimum, double median, double p95, double maximum) {
    }

    record Operation(
            int queryCount,
            int pairCount,
            double averagePairMillis,
            double queryP50Millis,
            double queryP95Millis,
            double modelLoadMillis,
            double warmupMillis,
            long processRssBeforeLoadBytes,
            long processRssAfterLoadBytes,
            long processRssPeakBytes,
            long peakRssIncreaseBytes,
            long modelWeightBytes,
            long modelCacheBytes,
            boolean gpuUsed,
            long gpuPeakAllocatedBytes,
            long gpuPeakReservedBytes) {
    }

    record Safety(
            boolean candidateParity,
            boolean provenanceParity,
            boolean contaminationZero,
            boolean fragmentationZero,
            boolean crossParentViolationZero,
            boolean goldEvidenceChildPreserved,
            boolean sealedFinalUnopened) {

        boolean all() {
            return candidateParity && provenanceParity && contaminationZero && fragmentationZero
                    && crossParentViolationZero && goldEvidenceChildPreserved && sealedFinalUnopened;
        }
    }

    record Gate(String decision, boolean queryPlannerAllowed, List<String> findings) {
    }

    record EvaluationReport(
            int schemaVersion,
            String phase,
            String inputDigest,
            boolean candidateIdentityParity,
            boolean provenanceParity,
            Safety safety,
            List<DatasetComparison> datasets,
            Aggregate combinedQueryMicro,
            Aggregate combinedUserMacro,
            Map<String, SliceComparison> profession,
            Map<String, SliceComparison> language,
            Map<String, SliceComparison> category,
            OutcomeCounts outcomes,
            ScoreDistribution negativeTopScores,
            Operation operation,
            Gate gate,
            boolean sealedFinalOpened,
            boolean sealedFinalSearchExecuted,
            String currentFreshBaseline) {
    }
}
