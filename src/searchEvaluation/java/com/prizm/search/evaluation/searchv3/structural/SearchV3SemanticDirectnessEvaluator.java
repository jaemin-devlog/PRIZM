package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.GoldJoined;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.VerifiedCandidates;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.GoldAnswerability;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleCandidate;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleRelation;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleRun;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.QueryGold;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.QueryResult;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.RankingMetrics;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.ScoredRanking;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Evaluation-only D0/D2/O10 comparison over an already Gold-joined candidate freeze. */
final class SearchV3SemanticDirectnessEvaluator {

    static final int VALIDATION_TOP_K = 10;
    static final double MIN_RANK1_RETENTION = 0.98d;
    static final double MIN_RELATION_MACRO_F1 = 0.85d;
    static final double MIN_USER_MACRO_TOP1_CAPTURE = 0.25d;
    static final int MIN_RECOVERED_BUNDLES = 3;
    static final double GATE_EPSILON = 1.0e-12d;

    private static final Set<String> COMPLETION_CATEGORIES = Set.of(
            "completion_state", "completed_production", "attempted_prototype", "planned");
    private static final List<DirectnessRelation> RELATION_PRIORITY = List.of(
            DirectnessRelation.DIRECT_MATCH,
            DirectnessRelation.RELATED_CONTEXT,
            DirectnessRelation.QUERY_CONFLICT,
            DirectnessRelation.INSUFFICIENT);
    private final SearchV3OracleCeilingEvaluator oracle;

    SearchV3SemanticDirectnessEvaluator() {
        this(new SearchV3OracleCeilingEvaluator());
    }

    SearchV3SemanticDirectnessEvaluator(SearchV3OracleCeilingEvaluator oracle) {
        this.oracle = Objects.requireNonNull(oracle, "oracle");
    }

    DirectnessRun evaluate(
            GoldJoined<List<QueryGold>> joined,
            Set<String> semanticCoreQueryIds,
            List<QueryPredictions> predictions) {
        Objects.requireNonNull(joined, "joined");
        Set<String> scope = Set.copyOf(Objects.requireNonNull(semanticCoreQueryIds, "semanticCoreQueryIds"));
        if (scope.isEmpty()) {
            throw new IllegalArgumentException("semantic directness scope must not be empty");
        }

        OracleRun oracleRun = oracle.evaluate(joined);
        Map<String, QueryGold> goldByQuery = uniqueMap(joined.gold(), QueryGold::queryId, "Gold query");
        Map<String, QueryPredictions> predictionByQuery = uniqueMap(
                predictions, QueryPredictions::queryId, "prediction query");
        Set<String> candidateQueryIds = oracleRun.queries().stream()
                .map(QueryResult::queryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!candidateQueryIds.containsAll(scope)) {
            throw new IllegalArgumentException("semantic scope references a query outside candidate freeze");
        }
        if (!predictionByQuery.keySet().equals(scope)) {
            throw new IllegalArgumentException("prediction query inventory differs from semantic scope");
        }

        List<QueryMetrics> queries = new ArrayList<>();
        for (QueryResult source : oracleRun.queries()) {
            if (!scope.contains(source.queryId())) {
                continue;
            }
            QueryGold gold = goldByQuery.get(source.queryId());
            QueryPredictions queryPredictions = predictionByQuery.get(source.queryId());
            validatePredictions(source, queryPredictions);

            List<String> d0Order = candidateIds(source.s0Ranking());
            List<String> d2Order = d2Order(source.s0Ranking(), queryPredictions.candidates());
            List<OracleCandidate> o10Ranking = oracle.stablePartitionPrefix(
                    source.s0Ranking(), Math.min(VALIDATION_TOP_K, source.s0Ranking().size()));
            List<String> o10Order = candidateIds(o10Ranking);
            ScoredRanking d0 = oracle.scoreCandidateOrder(gold, source.s0Ranking(), d0Order);
            ScoredRanking d2 = oracle.scoreCandidateOrder(gold, source.s0Ranking(), d2Order);
            ScoredRanking o10 = oracle.scoreCandidateOrder(gold, source.s0Ranking(), o10Order);
            if (d0.metrics().directRecallAt20() != d2.metrics().directRecallAt20()) {
                throw new IllegalStateException("D0/D2 Direct Recall@20 candidate parity failed");
            }

            Integer d0DirectRank = firstDirectRank(d0.ranking());
            Integer d2DirectRank = firstDirectRank(d2.ranking());
            Integer o10DirectRank = firstDirectRank(o10.ranking());
            RankOutcome outcome = rankOutcome(source.directPositive(), d0DirectRank, d2DirectRank);
            boolean retentionEligible = Integer.valueOf(1).equals(d0DirectRank);
            boolean retained = retentionEligible && Integer.valueOf(1).equals(d2DirectRank);
            boolean recovered = d0DirectRank != null
                    && d0DirectRank >= 2
                    && d0DirectRank <= VALIDATION_TOP_K
                    && Integer.valueOf(1).equals(d2DirectRank);

            Map<String, CandidatePrediction> predictionByCandidate = queryPredictions.candidates().stream()
                    .collect(Collectors.toMap(
                            CandidatePrediction::candidateId,
                            Function.identity(),
                            (left, right) -> {
                                throw new IllegalArgumentException(
                                        "duplicate prediction candidate: " + left.candidateId());
                            },
                            LinkedHashMap::new));
            CandidatePrediction denseTop1 = predictionByCandidate.get(
                    source.s0Ranking().get(0).candidate().candidateId());
            CandidatePrediction finalTop1 = predictionByCandidate.get(
                    d2.ranking().get(0).candidate().candidateId());
            if (denseTop1 == null || finalTop1 == null) {
                throw new IllegalStateException("D0/D2 Top1 must remain inside predicted Top10");
            }

            List<RelationJudgment> judgments = queryPredictions.candidates().stream()
                    .map(prediction -> new RelationJudgment(
                            source.queryId(),
                            source.userBundleId(),
                            goldRelation(source.s0Ranking(), prediction.candidateId()),
                            prediction.relation()))
                    .toList();
            queries.add(new QueryMetrics(
                    source.queryId(),
                    source.userBundleId(),
                    source.split(),
                    oracleRun.verifiedCandidates().frozen().input().suite(),
                    source.professionGroup(),
                    source.language(),
                    source.categories(),
                    gold.answerability(),
                    source.directPositive(),
                    d0.metrics(),
                    d2.metrics(),
                    o10.metrics(),
                    d0.ranking(),
                    d2.ranking(),
                    o10.ranking(),
                    d0DirectRank,
                    d2DirectRank,
                    o10DirectRank,
                    outcome,
                    retentionEligible,
                    retained,
                    recovered,
                    gold.answerability() == GoldAnswerability.NOT_SUPPORTED
                            && denseTop1.relation() == DirectnessRelation.DIRECT_MATCH,
                    gold.answerability() == GoldAnswerability.NOT_SUPPORTED
                            && finalTop1.relation() == DirectnessRelation.DIRECT_MATCH,
                    judgments));
        }
        List<QueryMetrics> immutable = List.copyOf(queries);
        return new DirectnessRun(
                oracleRun.verifiedCandidates(),
                immutable,
                summarize(immutable));
    }

    private void validatePredictions(QueryResult source, QueryPredictions predictions) {
        if (!source.queryId().equals(predictions.queryId())) {
            throw new IllegalArgumentException("prediction query identity mismatch");
        }
        int expected = Math.min(VALIDATION_TOP_K, source.s0Ranking().size());
        if (predictions.candidates().size() != expected) {
            throw new IllegalArgumentException("prediction count must equal frozen Dense Top10 prefix");
        }
        for (int index = 0; index < expected; index++) {
            CandidatePrediction actual = predictions.candidates().get(index);
            OracleCandidate candidate = source.s0Ranking().get(index);
            if (actual.denseRank() != index + 1
                    || !actual.candidateId().equals(candidate.candidate().candidateId())) {
                throw new IllegalArgumentException("prediction identity/order differs from frozen Dense prefix");
            }
        }
    }

    private List<String> d2Order(
            List<OracleCandidate> s0,
            List<CandidatePrediction> predictions) {
        List<String> result = new ArrayList<>();
        for (DirectnessRelation relation : RELATION_PRIORITY) {
            predictions.stream()
                    .filter(value -> value.relation() == relation)
                    .map(CandidatePrediction::candidateId)
                    .forEach(result::add);
        }
        s0.stream().skip(predictions.size())
                .map(value -> value.candidate().candidateId())
                .forEach(result::add);
        return List.copyOf(result);
    }

    static DirectnessSummary summarize(List<QueryMetrics> queries) {
        List<QueryMetrics> values = List.copyOf(queries);
        return new DirectnessSummary(
                aggregate(values),
                grouped(values, QueryMetrics::suite),
                grouped(values, QueryMetrics::userBundleId),
                grouped(values, QueryMetrics::professionGroup),
                grouped(values, QueryMetrics::language),
                groupedCategories(values),
                focusSlices(values));
    }

    private static AggregateMetrics aggregate(List<QueryMetrics> queries) {
        List<QueryMetrics> values = List.copyOf(queries);
        long directCount = values.stream().filter(QueryMetrics::directPositive).count();
        PathAggregate d0 = pathAggregate(values, QueryMetrics::d0, directCount);
        PathAggregate d2 = pathAggregate(values, QueryMetrics::d2, directCount);
        PathAggregate o10 = pathAggregate(values, QueryMetrics::o10, directCount);
        UserMacroMetrics userMacro = userMacro(values);
        long wins = countOutcome(values, RankOutcome.WIN);
        long losses = countOutcome(values, RankOutcome.LOSS);
        long ties = countOutcome(values, RankOutcome.TIE);
        long misses = countOutcome(values, RankOutcome.RETRIEVAL_MISS);
        long retentionEligible = values.stream().filter(QueryMetrics::retentionEligible).count();
        long retained = values.stream().filter(QueryMetrics::retained).count();
        long recovered = values.stream().filter(QueryMetrics::recoveredToRank1).count();
        long recoveredBundles = values.stream().filter(QueryMetrics::recoveredToRank1)
                .map(QueryMetrics::userBundleId).distinct().count();
        RelationMetrics relations = relationMetrics(values.stream()
                .flatMap(value -> value.relationJudgments().stream()).toList());
        NoSupportDiagnostics noSupport = noSupport(values);
        CaptureMetrics captures = new CaptureMetrics(
                capture(d0.top1(), d2.top1(), o10.top1()),
                capture(d0.mrr(), d2.mrr(), o10.mrr()),
                capture(d0.ndcgAt5(), d2.ndcgAt5(), o10.ndcgAt5()),
                capture(userMacro.d0().top1(), userMacro.d2().top1(), userMacro.o10().top1()),
                capture(userMacro.d0().mrr(), userMacro.d2().mrr(), userMacro.o10().mrr()));
        return new AggregateMetrics(
                values.size(),
                directCount,
                d0,
                d2,
                o10,
                userMacro,
                wins,
                losses,
                ties,
                misses,
                ratioMetric(retained, retentionEligible),
                recovered,
                recoveredBundles,
                captures,
                relations,
                noSupport);
    }

    private static PathAggregate pathAggregate(
            List<QueryMetrics> values,
            Function<QueryMetrics, RankingMetrics> metric,
            long directCount) {
        List<QueryMetrics> direct = values.stream().filter(QueryMetrics::directPositive).toList();
        List<QueryMetrics> relevanceBearing = values.stream()
                .filter(QueryMetrics::relevanceBearing).toList();
        return new PathAggregate(
                ratio(direct.stream().filter(value -> metric.apply(value).directRecallAt5()).count(), directCount),
                ratio(direct.stream().filter(value -> metric.apply(value).directRecallAt20()).count(), directCount),
                ratio(direct.stream().filter(value -> metric.apply(value).top1()).count(), directCount),
                average(direct.stream().map(value -> metric.apply(value).mrr()).toList()),
                average(values.stream().map(value -> metric.apply(value).ndcgAt5()).toList()),
                relevanceBearing.size(),
                average(relevanceBearing.stream().map(value -> metric.apply(value).ndcgAt5()).toList()));
    }

    private static UserMacroMetrics userMacro(List<QueryMetrics> values) {
        Map<String, List<QueryMetrics>> users = values.stream().collect(Collectors.groupingBy(
                QueryMetrics::userBundleId, LinkedHashMap::new, Collectors.toList()));
        List<List<QueryMetrics>> directUsers = users.values().stream()
                .map(group -> group.stream().filter(QueryMetrics::directPositive).toList())
                .filter(group -> !group.isEmpty())
                .toList();
        return new UserMacroMetrics(
                directUsers.size(),
                pathMacro(directUsers, QueryMetrics::d0),
                pathMacro(directUsers, QueryMetrics::d2),
                pathMacro(directUsers, QueryMetrics::o10));
    }

    private static PathMacro pathMacro(
            List<List<QueryMetrics>> users,
            Function<QueryMetrics, RankingMetrics> metric) {
        return new PathMacro(
                users.stream().mapToDouble(group -> ratio(
                        group.stream().filter(value -> metric.apply(value).top1()).count(), group.size()))
                        .average().orElse(0.0d),
                users.stream().mapToDouble(group -> average(
                        group.stream().map(value -> metric.apply(value).mrr()).toList()))
                        .average().orElse(0.0d));
    }

    private static RelationMetrics relationMetrics(List<RelationJudgment> judgments) {
        List<RelationJudgment> judged = judgments.stream()
                .filter(value -> value.goldRelation() != OracleRelation.UNJUDGED)
                .toList();
        Map<DirectnessRelation, Map<DirectnessRelation, Long>> confusion = new EnumMap<>(
                DirectnessRelation.class);
        for (DirectnessRelation actual : DirectnessRelation.values()) {
            Map<DirectnessRelation, Long> row = new EnumMap<>(DirectnessRelation.class);
            for (DirectnessRelation predicted : DirectnessRelation.values()) {
                row.put(predicted, 0L);
            }
            confusion.put(actual, row);
        }
        for (RelationJudgment judgment : judged) {
            DirectnessRelation actual = fromGold(judgment.goldRelation());
            confusion.get(actual).merge(judgment.predictedRelation(), 1L, Long::sum);
        }
        long correct = judged.stream()
                .filter(value -> fromGold(value.goldRelation()) == value.predictedRelation())
                .count();
        Map<DirectnessRelation, ClassMetrics> classes = new EnumMap<>(DirectnessRelation.class);
        for (DirectnessRelation relation : DirectnessRelation.values()) {
            long truePositive = confusion.get(relation).get(relation);
            long actual = confusion.get(relation).values().stream().mapToLong(Long::longValue).sum();
            long predicted = confusion.values().stream()
                    .mapToLong(row -> row.get(relation)).sum();
            double precision = ratio(truePositive, predicted);
            double recall = ratio(truePositive, actual);
            double f1 = precision + recall == 0.0d
                    ? 0.0d : 2.0d * precision * recall / (precision + recall);
            classes.put(relation, new ClassMetrics(actual, predicted, truePositive, precision, recall, f1));
        }
        Map<DirectnessRelation, Map<DirectnessRelation, Long>> immutableConfusion = new EnumMap<>(
                DirectnessRelation.class);
        confusion.forEach((key, value) -> immutableConfusion.put(key, Map.copyOf(value)));
        return new RelationMetrics(
                judgments.size(),
                judged.size(),
                judgments.size() - judged.size(),
                ratio(judged.size(), judgments.size()),
                ratio(correct, judged.size()),
                Map.copyOf(immutableConfusion),
                Map.copyOf(classes),
                classes.values().stream().mapToDouble(ClassMetrics::f1).average().orElse(0.0d),
                classes.get(DirectnessRelation.DIRECT_MATCH));
    }

    private static NoSupportDiagnostics noSupport(List<QueryMetrics> values) {
        List<QueryMetrics> noSupport = values.stream()
                .filter(value -> value.answerability() == GoldAnswerability.NOT_SUPPORTED)
                .toList();
        long dense = noSupport.stream().filter(QueryMetrics::denseTop1PredictedDirect).count();
        long ranked = noSupport.stream().filter(QueryMetrics::finalTop1PredictedDirect).count();
        return new NoSupportDiagnostics(
                noSupport.size(),
                dense,
                ratioMetric(dense, noSupport.size()),
                noSupport.stream().filter(QueryMetrics::denseTop1PredictedDirect)
                        .map(QueryMetrics::userBundleId).distinct().count(),
                ranked,
                ratioMetric(ranked, noSupport.size()),
                noSupport.stream().filter(QueryMetrics::finalTop1PredictedDirect)
                        .map(QueryMetrics::userBundleId).distinct().count(),
                ComparatorStatus.NOT_APPLICABLE);
    }

    static CaptureMetric capture(double d0, double d2, double o10) {
        double denominator = o10 - d0;
        if (Math.abs(denominator) <= GATE_EPSILON) {
            return new CaptureMetric(d0, d2, o10, MetricStatus.NOT_APPLICABLE, null);
        }
        return new CaptureMetric(d0, d2, o10, MetricStatus.APPLICABLE, (d2 - d0) / denominator);
    }

    static GateAssessment assessGate(AggregateMetrics metrics, SafetyInputs safety) {
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(safety, "safety");
        boolean safetyPassed = safety.allPassed();
        boolean retentionPassed = metrics.rank1Retention().status() == MetricStatus.APPLICABLE
                && metrics.rank1Retention().value() + GATE_EPSILON >= MIN_RANK1_RETENTION;
        boolean winLossPassed = metrics.winCount() > metrics.lossCount();
        boolean userMacroImproved = metrics.userMacro().d2().top1()
                > metrics.userMacro().d0().top1() + GATE_EPSILON;
        boolean relationPassed = metrics.relations().macroF1() + GATE_EPSILON
                >= MIN_RELATION_MACRO_F1;
        boolean qualityPassed = retentionPassed && winLossPassed && userMacroImproved && relationPassed;
        CaptureMetric userCapture = metrics.captures().userMacroTop1();
        boolean conditionA = userCapture.status() == MetricStatus.APPLICABLE
                && userCapture.ratio() + GATE_EPSILON >= MIN_USER_MACRO_TOP1_CAPTURE;
        boolean conditionB = metrics.recoveredBundleCount() >= MIN_RECOVERED_BUNDLES;
        boolean additionalPassed = conditionA || conditionB;
        return new GateAssessment(
                safetyPassed && qualityPassed && additionalPassed ? GateStatus.PASS : GateStatus.FAIL,
                safetyPassed,
                retentionPassed,
                winLossPassed,
                userMacroImproved,
                relationPassed,
                conditionA,
                conditionB,
                ComparatorStatus.NOT_APPLICABLE,
                metrics,
                safety);
    }

    static CostMetrics summarizeCost(
            List<InferenceCostObservation> observations,
            ResourceCost resources) {
        List<InferenceCostObservation> values = List.copyOf(observations);
        Set<String> queryIds = new LinkedHashSet<>();
        for (InferenceCostObservation value : values) {
            if (!queryIds.add(value.queryId())) {
                throw new IllegalArgumentException("duplicate inference cost query: " + value.queryId());
            }
        }
        return new CostMetrics(
                values.size(),
                values.stream().mapToLong(InferenceCostObservation::pairCount).sum(),
                values.stream().mapToLong(InferenceCostObservation::requestCount).sum(),
                values.stream().mapToLong(InferenceCostObservation::inputUtf8Bytes).sum(),
                values.stream().mapToLong(InferenceCostObservation::outputUtf8Bytes).sum(),
                latency(values.stream().map(InferenceCostObservation::queryLatencyMs).toList()),
                latency(values.stream().flatMap(value -> value.pairLatencyMs().stream()).toList()),
                Objects.requireNonNull(resources, "resources"));
    }

    private static LatencyMetrics latency(List<Double> values) {
        if (values.isEmpty()) {
            return new LatencyMetrics(0, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d);
        }
        if (values.stream().anyMatch(value -> value == null || !Double.isFinite(value) || value < 0.0d)) {
            throw new IllegalArgumentException("latency must be finite and non-negative");
        }
        List<Double> sorted = values.stream().sorted().toList();
        return new LatencyMetrics(
                values.size(),
                values.stream().mapToDouble(Double::doubleValue).sum(),
                values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d),
                percentile(sorted, 0.50d),
                percentile(sorted, 0.95d),
                sorted.get(sorted.size() - 1));
    }

    private static double percentile(List<Double> sorted, double percentile) {
        int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
        return sorted.get(index);
    }

    private static Map<String, AggregateMetrics> grouped(
            List<QueryMetrics> values,
            Function<QueryMetrics, String> key) {
        Map<String, List<QueryMetrics>> grouped = values.stream().collect(Collectors.groupingBy(
                key, LinkedHashMap::new, Collectors.toList()));
        Map<String, AggregateMetrics> result = new LinkedHashMap<>();
        grouped.forEach((name, queries) -> result.put(name, aggregate(queries)));
        return Map.copyOf(result);
    }

    private static Map<String, AggregateMetrics> groupedCategories(List<QueryMetrics> values) {
        Map<String, List<QueryMetrics>> grouped = new LinkedHashMap<>();
        for (QueryMetrics value : values) {
            for (String category : value.categories()) {
                grouped.computeIfAbsent(category, ignored -> new ArrayList<>()).add(value);
            }
        }
        Map<String, AggregateMetrics> result = new LinkedHashMap<>();
        grouped.forEach((name, queries) -> result.put(name, aggregate(queries)));
        return Map.copyOf(result);
    }

    private static Map<String, AggregateMetrics> focusSlices(List<QueryMetrics> values) {
        Map<String, AggregateMetrics> result = new LinkedHashMap<>();
        result.put("OTHER_ACTOR", aggregateCategories(values, Set.of("other_actor")));
        result.put("NEGATION", aggregateCategories(values, Set.of("negation")));
        result.put("COMPLETION", aggregateCategories(values, COMPLETION_CATEGORIES));
        result.put("ABSTRACT", aggregateCategories(values, Set.of("abstract_competency")));
        result.put("PARAPHRASE", aggregateCategories(values, Set.of("semantic_paraphrase")));
        return Map.copyOf(result);
    }

    private static AggregateMetrics aggregateCategories(List<QueryMetrics> values, Set<String> categories) {
        return aggregate(values.stream()
                .filter(value -> value.categories().stream().anyMatch(categories::contains))
                .toList());
    }

    private static List<String> candidateIds(List<OracleCandidate> ranking) {
        return ranking.stream().map(value -> value.candidate().candidateId()).toList();
    }

    private static Integer firstDirectRank(List<OracleCandidate> ranking) {
        return ranking.stream()
                .filter(value -> value.relation() == OracleRelation.DIRECT_SUPPORT)
                .map(OracleCandidate::oracleRank)
                .findFirst()
                .orElse(null);
    }

    private static RankOutcome rankOutcome(boolean directPositive, Integer d0, Integer d2) {
        if (!directPositive) return RankOutcome.NOT_APPLICABLE;
        if (d0 == null) return RankOutcome.RETRIEVAL_MISS;
        if (d2 == null) {
            throw new IllegalStateException("D2 lost a DIRECT candidate from an identical candidate set");
        }
        int compared = Integer.compare(d2, d0);
        if (compared < 0) return RankOutcome.WIN;
        if (compared > 0) return RankOutcome.LOSS;
        return RankOutcome.TIE;
    }

    private static OracleRelation goldRelation(List<OracleCandidate> ranking, String candidateId) {
        return ranking.stream()
                .filter(value -> value.candidate().candidateId().equals(candidateId))
                .map(OracleCandidate::relation)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "prediction candidate is outside Gold-ranked candidates: " + candidateId));
    }

    private static DirectnessRelation fromGold(OracleRelation relation) {
        return switch (relation) {
            case DIRECT_SUPPORT -> DirectnessRelation.DIRECT_MATCH;
            case RELATED -> DirectnessRelation.RELATED_CONTEXT;
            case CONTRADICTS -> DirectnessRelation.QUERY_CONFLICT;
            case INSUFFICIENT -> DirectnessRelation.INSUFFICIENT;
            case UNJUDGED -> throw new IllegalArgumentException("UNJUDGED is excluded from relation metrics");
        };
    }

    private static <T> Map<String, T> uniqueMap(
            List<T> values,
            Function<T, String> key,
            String label) {
        return values.stream().collect(Collectors.toMap(
                key,
                Function.identity(),
                (left, right) -> {
                    throw new IllegalArgumentException("duplicate " + label + ": " + key.apply(left));
                },
                LinkedHashMap::new));
    }

    private static long countOutcome(List<QueryMetrics> values, RankOutcome outcome) {
        return values.stream().filter(value -> value.rankOutcome() == outcome).count();
    }

    private static RatioMetric ratioMetric(long numerator, long denominator) {
        return denominator == 0
                ? new RatioMetric(numerator, denominator, MetricStatus.NOT_APPLICABLE, null)
                : new RatioMetric(numerator, denominator, MetricStatus.APPLICABLE,
                        numerator / (double) denominator);
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0d : numerator / (double) denominator;
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
    }

    enum DirectnessRelation {
        DIRECT_MATCH,
        RELATED_CONTEXT,
        QUERY_CONFLICT,
        INSUFFICIENT
    }

    enum RankOutcome {
        WIN,
        LOSS,
        TIE,
        RETRIEVAL_MISS,
        NOT_APPLICABLE
    }

    enum MetricStatus {
        APPLICABLE,
        NOT_APPLICABLE
    }

    enum ComparatorStatus {
        NOT_APPLICABLE
    }

    enum GateStatus {
        PASS,
        FAIL
    }

    record CandidatePrediction(
            int denseRank,
            String candidateId,
            DirectnessRelation relation) {

        CandidatePrediction {
            if (denseRank < 1 || candidateId == null || candidateId.isBlank()
                    || relation == null) {
                throw new IllegalArgumentException("semantic directness prediction is invalid");
            }
        }
    }

    record QueryPredictions(String queryId, List<CandidatePrediction> candidates) {

        QueryPredictions {
            if (queryId == null || queryId.isBlank()) {
                throw new IllegalArgumentException("prediction queryId must be non-blank");
            }
            candidates = List.copyOf(candidates);
        }
    }

    record RelationJudgment(
            String queryId,
            String userBundleId,
            OracleRelation goldRelation,
            DirectnessRelation predictedRelation) {
    }

    record QueryMetrics(
            String queryId,
            String userBundleId,
            String split,
            String suite,
            String professionGroup,
            String language,
            List<String> categories,
            GoldAnswerability answerability,
            boolean directPositive,
            RankingMetrics d0,
            RankingMetrics d2,
            RankingMetrics o10,
            List<OracleCandidate> d0Ranking,
            List<OracleCandidate> d2Ranking,
            List<OracleCandidate> o10Ranking,
            Integer d0FirstDirectRank,
            Integer d2FirstDirectRank,
            Integer o10FirstDirectRank,
            RankOutcome rankOutcome,
            boolean retentionEligible,
            boolean retained,
            boolean recoveredToRank1,
            boolean denseTop1PredictedDirect,
            boolean finalTop1PredictedDirect,
            List<RelationJudgment> relationJudgments) {

        QueryMetrics {
            categories = List.copyOf(categories);
            d0Ranking = List.copyOf(d0Ranking);
            d2Ranking = List.copyOf(d2Ranking);
            o10Ranking = List.copyOf(o10Ranking);
            relationJudgments = List.copyOf(relationJudgments);
        }

        boolean relevanceBearing() {
            return d0Ranking.stream().anyMatch(value -> value.relation().gain() > 0);
        }
    }

    record PathAggregate(
            double directRecallAt5,
            double directRecallAt20,
            double top1,
            double mrr,
            double ndcgAt5,
            long relevanceBearingQueryCount,
            double relevanceBearingNdcgAt5) {
    }

    record PathMacro(double top1, double mrr) {
    }

    record UserMacroMetrics(
            long directPositiveUserCount,
            PathMacro d0,
            PathMacro d2,
            PathMacro o10) {
    }

    record RatioMetric(long numerator, long denominator, MetricStatus status, Double value) {
    }

    record CaptureMetric(double d0, double d2, double o10, MetricStatus status, Double ratio) {
    }

    record CaptureMetrics(
            CaptureMetric queryMicroTop1,
            CaptureMetric queryMicroMrr,
            CaptureMetric queryMicroNdcgAt5,
            CaptureMetric userMacroTop1,
            CaptureMetric userMacroMrr) {
    }

    record ClassMetrics(
            long actualCount,
            long predictedCount,
            long truePositiveCount,
            double precision,
            double recall,
            double f1) {
    }

    record RelationMetrics(
            long predictedPairCount,
            long judgedPairCount,
            long unjudgedPairCount,
            double judgedCoverage,
            double judgedAccuracy,
            Map<DirectnessRelation, Map<DirectnessRelation, Long>> confusion,
            Map<DirectnessRelation, ClassMetrics> classes,
            double macroF1,
            ClassMetrics direct) {

        RelationMetrics {
            confusion = Map.copyOf(confusion);
            classes = Map.copyOf(classes);
        }
    }

    record NoSupportDiagnostics(
            long queryCount,
            long denseTop1PredictedDirectCount,
            RatioMetric denseTop1PredictedDirectRate,
            long denseTop1PredictedDirectBundleCount,
            long finalTop1PredictedDirectCount,
            RatioMetric finalTop1PredictedDirectRate,
            long finalTop1PredictedDirectBundleCount,
            ComparatorStatus changeComparator) {
    }

    record AggregateMetrics(
            long queryCount,
            long directPositiveQueryCount,
            PathAggregate d0,
            PathAggregate d2,
            PathAggregate o10,
            UserMacroMetrics userMacro,
            long winCount,
            long lossCount,
            long tieCount,
            long retrievalMissCount,
            RatioMetric rank1Retention,
            long recoveredQueryCount,
            long recoveredBundleCount,
            CaptureMetrics captures,
            RelationMetrics relations,
            NoSupportDiagnostics noSupport) {
    }

    record DirectnessSummary(
            AggregateMetrics aggregate,
            Map<String, AggregateMetrics> suiteSlices,
            Map<String, AggregateMetrics> userSlices,
            Map<String, AggregateMetrics> professionSlices,
            Map<String, AggregateMetrics> languageSlices,
            Map<String, AggregateMetrics> categorySlices,
            Map<String, AggregateMetrics> focusSlices) {

        DirectnessSummary {
            suiteSlices = Map.copyOf(suiteSlices);
            userSlices = Map.copyOf(userSlices);
            professionSlices = Map.copyOf(professionSlices);
            languageSlices = Map.copyOf(languageSlices);
            categorySlices = Map.copyOf(categorySlices);
            focusSlices = Map.copyOf(focusSlices);
        }
    }

    record DirectnessRun(
            VerifiedCandidates verifiedCandidates,
            List<QueryMetrics> queries,
            DirectnessSummary summary) {

        DirectnessRun {
            queries = List.copyOf(queries);
        }
    }

    record SafetyInputs(
            boolean candidateIdentityParity,
            boolean sourceProvenanceUnchanged,
            boolean crossParentMergeFree,
            boolean goldBeforeOutputAccessFree,
            boolean sealedFinalUntouched) {

        boolean allPassed() {
            return candidateIdentityParity
                    && sourceProvenanceUnchanged
                    && crossParentMergeFree
                    && goldBeforeOutputAccessFree
                    && sealedFinalUntouched;
        }
    }

    record GateAssessment(
            GateStatus status,
            boolean safetyPassed,
            boolean retentionPassed,
            boolean winLossPassed,
            boolean userMacroImproved,
            boolean relationMacroF1Passed,
            boolean conditionAOracleCapture,
            boolean conditionBRecoveredBundles,
            ComparatorStatus conditionCNoSupportComparator,
            AggregateMetrics metrics,
            SafetyInputs safety) {
    }

    record InferenceCostObservation(
            String queryId,
            int pairCount,
            int requestCount,
            long inputUtf8Bytes,
            long outputUtf8Bytes,
            double queryLatencyMs,
            List<Double> pairLatencyMs) {

        InferenceCostObservation {
            if (queryId == null || queryId.isBlank() || pairCount < 0 || requestCount < 0
                    || inputUtf8Bytes < 0 || outputUtf8Bytes < 0
                    || !Double.isFinite(queryLatencyMs) || queryLatencyMs < 0.0d) {
                throw new IllegalArgumentException("inference cost observation is invalid");
            }
            pairLatencyMs = List.copyOf(pairLatencyMs);
            if (pairLatencyMs.size() != pairCount) {
                throw new IllegalArgumentException("pair latency count differs from pairCount");
            }
        }
    }

    record ByteMeasurement(boolean available, long bytes, String source) {

        ByteMeasurement {
            if (bytes < 0 || source == null || source.isBlank()) {
                throw new IllegalArgumentException("resource byte measurement is invalid");
            }
            if (!available && bytes != 0) {
                throw new IllegalArgumentException("unavailable resource measurement must use zero bytes");
            }
        }
    }

    record ResourceCost(
            long modelArtifactBytes,
            ByteMeasurement rssBefore,
            ByteMeasurement rssPeak,
            ByteMeasurement rssAfter,
            ByteMeasurement gpuVramBefore,
            ByteMeasurement gpuVramPeak,
            ByteMeasurement gpuVramAfter) {

        ResourceCost {
            if (modelArtifactBytes < 0) {
                throw new IllegalArgumentException("model artifact bytes must be non-negative");
            }
            Objects.requireNonNull(rssBefore, "rssBefore");
            Objects.requireNonNull(rssPeak, "rssPeak");
            Objects.requireNonNull(rssAfter, "rssAfter");
            Objects.requireNonNull(gpuVramBefore, "gpuVramBefore");
            Objects.requireNonNull(gpuVramPeak, "gpuVramPeak");
            Objects.requireNonNull(gpuVramAfter, "gpuVramAfter");
        }
    }

    record LatencyMetrics(
            long samples,
            double totalMs,
            double averageMs,
            double p50Ms,
            double p95Ms,
            double maxMs) {
    }

    record CostMetrics(
            long queryCount,
            long pairCount,
            long requestCount,
            long inputUtf8Bytes,
            long outputUtf8Bytes,
            LatencyMetrics queryLatency,
            LatencyMetrics pairLatency,
            ResourceCost resources) {
    }
}
