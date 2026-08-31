package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.CandidateProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvaluationTrack;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.FreezeInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.GoldJoined;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.QueryProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.VerifiedCandidates;
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

/** Gold Oracle ceiling over an already verified, immutable semantic B3 candidate freeze. */
final class SearchV3OracleCeilingEvaluator {

    OracleRun evaluate(GoldJoined<List<QueryGold>> joined) {
        Objects.requireNonNull(joined, "joined");
        VerifiedCandidates verified = SearchV3CandidateFreeze.verify(joined.verified().frozen());
        FreezeInput input = verified.frozen().input();
        if (input.track() != EvaluationTrack.SEMANTIC) {
            throw new IllegalArgumentException("semantic Oracle rejects typed candidate freezes");
        }

        Map<String, QueryGold> goldByQuery = joined.gold().stream().collect(Collectors.toMap(
                QueryGold::queryId,
                Function.identity(),
                (left, right) -> {
                    throw new IllegalArgumentException("duplicate Oracle Gold query: " + left.queryId());
                },
                LinkedHashMap::new));
        Set<String> candidateQueryIds = input.queries().stream()
                .map(QueryProjection::queryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!goldByQuery.keySet().equals(candidateQueryIds)) {
            throw new IllegalArgumentException("Oracle Gold and candidate query inventory differ");
        }

        List<QueryResult> results = new ArrayList<>();
        for (QueryProjection query : input.queries()) {
            QueryGold gold = goldByQuery.get(query.queryId());
            validateGold(query, gold);
            List<OracleCandidate> s0 = sourceRanking(query, gold);
            List<OracleCandidate> o1 = stableOraclePartition(s0);
            validateParity(s0, o1);
            boolean directPositive = gold.goldDirectPositive();
            boolean directCandidate = s0.stream()
                    .anyMatch(value -> value.relation() == OracleRelation.DIRECT_SUPPORT);
            boolean relatedCandidate = s0.stream()
                    .anyMatch(value -> value.relation() == OracleRelation.RELATED);
            CeilingState ceilingState = ceilingState(gold.answerability(), directCandidate, relatedCandidate);
            FailureStage failureStage = failureStage(
                    gold.answerability(), directPositive, directCandidate, relatedCandidate, s0);
            CeilingState expectedState = expectedState(gold.answerability());
            results.add(new QueryResult(
                    query.queryId(),
                    query.userBundleId(),
                    query.split(),
                    gold.professionGroup(),
                    gold.language(),
                    gold.categories(),
                    directPositive,
                    ceilingState,
                    expectedState,
                    ceilingState == expectedState,
                    failureStage,
                    rankingMetrics(s0, directPositive),
                    rankingMetrics(o1, directPositive),
                    s0,
                    o1));
        }
        List<QueryResult> immutable = List.copyOf(results);
        return new OracleRun(
                verified,
                immutable,
                aggregate(immutable),
                aggregateByUser(immutable),
                aggregateByProfession(immutable),
                aggregateByLanguage(immutable),
                aggregateByCategory(immutable));
    }

    static Map<String, AggregateMetrics> aggregateByUser(List<QueryResult> results) {
        return grouped(results, QueryResult::userBundleId);
    }

    static Map<String, AggregateMetrics> aggregateByProfession(List<QueryResult> results) {
        return grouped(results, QueryResult::professionGroup);
    }

    static Map<String, AggregateMetrics> aggregateByLanguage(List<QueryResult> results) {
        return grouped(results, QueryResult::language);
    }

    static Map<String, AggregateMetrics> aggregateByCategory(List<QueryResult> results) {
        Map<String, List<QueryResult>> grouped = new LinkedHashMap<>();
        for (QueryResult result : results) {
            for (String category : result.categories()) {
                grouped.computeIfAbsent(category, ignored -> new ArrayList<>()).add(result);
            }
        }
        return aggregateGroups(grouped);
    }

    static AggregateMetrics aggregate(List<QueryResult> results) {
        List<QueryResult> values = List.copyOf(results);
        long directCount = values.stream().filter(QueryResult::directPositive).count();
        Map<FailureStage, Long> failures = new EnumMap<>(FailureStage.class);
        for (FailureStage stage : FailureStage.values()) {
            failures.put(stage, values.stream().filter(value -> value.failureStage() == stage).count());
        }
        return new AggregateMetrics(
                values.size(),
                directCount,
                ratio(values.stream().filter(value -> value.directPositive()
                        && value.s0().directRecallAt5()).count(), directCount),
                ratio(values.stream().filter(value -> value.directPositive()
                        && value.o1().directRecallAt5()).count(), directCount),
                ratio(values.stream().filter(value -> value.directPositive()
                        && value.s0().directRecallAt20()).count(), directCount),
                ratio(values.stream().filter(value -> value.directPositive()
                        && value.o1().directRecallAt20()).count(), directCount),
                ratio(values.stream().filter(value -> value.directPositive() && value.s0().top1()).count(),
                        directCount),
                ratio(values.stream().filter(value -> value.directPositive() && value.o1().top1()).count(),
                        directCount),
                average(values.stream().filter(QueryResult::directPositive)
                        .map(value -> value.s0().mrr()).toList()),
                average(values.stream().filter(QueryResult::directPositive)
                        .map(value -> value.o1().mrr()).toList()),
                average(values.stream().map(value -> value.s0().ndcgAt5()).toList()),
                average(values.stream().map(value -> value.o1().ndcgAt5()).toList()),
                values.stream().filter(QueryResult::ceilingStateCorrect).count(),
                ratio(values.stream().filter(QueryResult::ceilingStateCorrect).count(), values.size()),
                Map.copyOf(failures));
    }

    private static Map<String, AggregateMetrics> grouped(
            List<QueryResult> results,
            Function<QueryResult, String> key) {
        Map<String, List<QueryResult>> grouped = results.stream().collect(Collectors.groupingBy(
                key, LinkedHashMap::new, Collectors.toList()));
        return aggregateGroups(grouped);
    }

    private static Map<String, AggregateMetrics> aggregateGroups(Map<String, List<QueryResult>> grouped) {
        Map<String, AggregateMetrics> result = new LinkedHashMap<>();
        grouped.forEach((key, values) -> result.put(key, aggregate(values)));
        return Map.copyOf(result);
    }

    private void validateGold(QueryProjection query, QueryGold gold) {
        Objects.requireNonNull(gold, "query Gold");
        if (!query.userBundleId().equals(gold.userBundleId())) {
            throw new IllegalArgumentException("Oracle Gold crosses query owner: " + query.queryId());
        }
        requireNonBlank(gold.professionGroup(), "professionGroup");
        requireNonBlank(gold.language(), "language");
        Objects.requireNonNull(gold.answerability(), "answerability");
        gold.categories().forEach(value -> requireNonBlank(value, "category"));
        Set<String> candidateChildIds = query.rankedCandidates().stream()
                .flatMap(candidate -> candidate.evidenceChildren().stream())
                .map(value -> value.evidenceChildId())
                .collect(Collectors.toSet());
        gold.relationByEvidenceChildId().forEach((childId, relation) -> {
            requireNonBlank(childId, "Gold EvidenceChild ID");
            Objects.requireNonNull(relation, "Oracle relation");
            if (!candidateChildIds.contains(childId)) {
                throw new IllegalArgumentException(
                        "Oracle relation references a child outside frozen candidates: " + childId);
            }
        });
        boolean directPositive = gold.goldDirectPositive();
        if (gold.answerability() == GoldAnswerability.SUPPORTED && !directPositive) {
            throw new IllegalArgumentException("SUPPORTED Gold requires expected DIRECT_SUPPORT");
        }
        if (gold.answerability() == GoldAnswerability.NOT_SUPPORTED && directPositive) {
            throw new IllegalArgumentException("NOT_SUPPORTED Gold cannot expect DIRECT_SUPPORT");
        }
        if (!directPositive && gold.relationByEvidenceChildId().containsValue(
                OracleRelation.DIRECT_SUPPORT)) {
            throw new IllegalArgumentException("DIRECT candidate requires goldDirectPositive");
        }
    }

    private List<OracleCandidate> sourceRanking(QueryProjection query, QueryGold gold) {
        List<OracleCandidate> result = new ArrayList<>();
        for (CandidateProjection candidate : query.rankedCandidates()) {
            OracleRelation relation = candidate.evidenceChildren().stream()
                    .map(value -> gold.relationByEvidenceChildId().getOrDefault(
                            value.evidenceChildId(), OracleRelation.INSUFFICIENT))
                    .min(Enum::compareTo)
                    .orElse(OracleRelation.INSUFFICIENT);
            result.add(new OracleCandidate(candidate.rank(), candidate.rank(), candidate, relation));
        }
        return List.copyOf(result);
    }

    private List<OracleCandidate> stableOraclePartition(List<OracleCandidate> source) {
        List<OracleCandidate> result = new ArrayList<>();
        for (OracleRelation relation : OracleRelation.values()) {
            for (OracleCandidate candidate : source) {
                if (candidate.relation() == relation) {
                    result.add(new OracleCandidate(
                            candidate.sourceRank(), result.size() + 1, candidate.candidate(), relation));
                }
            }
        }
        return List.copyOf(result);
    }

    private void validateParity(List<OracleCandidate> s0, List<OracleCandidate> o1) {
        if (s0.size() != o1.size()) {
            throw new IllegalStateException("S0/O1 candidate count parity failed");
        }
        Map<String, CandidateProjection> s0ById = s0.stream().collect(Collectors.toMap(
                value -> value.candidate().candidateId(), OracleCandidate::candidate));
        Map<String, CandidateProjection> o1ById = o1.stream().collect(Collectors.toMap(
                value -> value.candidate().candidateId(), OracleCandidate::candidate));
        if (!s0ById.equals(o1ById)) {
            throw new IllegalStateException("S0/O1 candidate identity/cosine/source parity failed");
        }
        for (OracleRelation relation : OracleRelation.values()) {
            List<String> s0LocalOrder = s0.stream().filter(value -> value.relation() == relation)
                    .map(value -> value.candidate().candidateId()).toList();
            List<String> o1LocalOrder = o1.stream().filter(value -> value.relation() == relation)
                    .map(value -> value.candidate().candidateId()).toList();
            if (!s0LocalOrder.equals(o1LocalOrder)) {
                throw new IllegalStateException("Oracle stable relation order failed: " + relation);
            }
        }
    }

    private CeilingState ceilingState(
            GoldAnswerability answerability,
            boolean directCandidate,
            boolean relatedCandidate) {
        if (directCandidate) {
            return CeilingState.FOUND;
        }
        if (answerability == GoldAnswerability.PARTIALLY_SUPPORTED && relatedCandidate) {
            return CeilingState.PARTIAL;
        }
        if (answerability == GoldAnswerability.NOT_SUPPORTED) {
            return CeilingState.NONE;
        }
        return CeilingState.UNRESOLVED;
    }

    private FailureStage failureStage(
            GoldAnswerability answerability,
            boolean directPositive,
            boolean directCandidate,
            boolean relatedCandidate,
            List<OracleCandidate> s0) {
        if (directPositive) {
            if (s0.get(0).relation() == OracleRelation.DIRECT_SUPPORT) {
                return FailureStage.ALREADY_CORRECT;
            }
            return directCandidate ? FailureStage.RANKING_RECOVERABLE : FailureStage.RETRIEVAL_MISS;
        }
        if (answerability == GoldAnswerability.PARTIALLY_SUPPORTED && relatedCandidate) {
            return FailureStage.PARTIAL_ONLY;
        }
        if (answerability == GoldAnswerability.NOT_SUPPORTED
                && (s0.get(0).relation() == OracleRelation.RELATED
                        || s0.get(0).relation() == OracleRelation.CONTRADICTS)) {
            return FailureStage.FALSE_POSITIVE_RISK;
        }
        return FailureStage.NO_SUPPORT;
    }

    private CeilingState expectedState(GoldAnswerability answerability) {
        return switch (answerability) {
            case SUPPORTED -> CeilingState.FOUND;
            case PARTIALLY_SUPPORTED -> CeilingState.PARTIAL;
            case NOT_SUPPORTED -> CeilingState.NONE;
        };
    }

    private RankingMetrics rankingMetrics(List<OracleCandidate> ranking, boolean directPositive) {
        Integer firstDirect = ranking.stream()
                .filter(value -> value.relation() == OracleRelation.DIRECT_SUPPORT)
                .map(OracleCandidate::oracleRank)
                .findFirst()
                .orElse(null);
        return new RankingMetrics(
                directPositive && firstDirect != null && firstDirect <= 5,
                directPositive && firstDirect != null && firstDirect <= 20,
                directPositive && firstDirect != null && firstDirect == 1,
                !directPositive || firstDirect == null ? 0.0d : 1.0d / firstDirect,
                ndcgAt5(ranking));
    }

    private double ndcgAt5(List<OracleCandidate> ranking) {
        double actual = dcgAt5(ranking);
        List<OracleCandidate> ideal = stableOraclePartition(ranking.stream()
                .sorted(java.util.Comparator.comparingInt(OracleCandidate::sourceRank))
                .toList());
        double idealValue = dcgAt5(ideal);
        return idealValue == 0.0d ? 0.0d : actual / idealValue;
    }

    private double dcgAt5(List<OracleCandidate> ranking) {
        double value = 0.0d;
        for (int index = 0; index < Math.min(5, ranking.size()); index++) {
            double discount = Math.log(index + 2.0d) / Math.log(2.0d);
            value += ranking.get(index).relation().gain() / discount;
        }
        return value;
    }

    private static double average(List<Double> values) {
        return values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d);
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0d : (double) numerator / denominator;
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }

    /** Declaration order is the frozen Oracle stable-partition priority. */
    enum OracleRelation {
        DIRECT_SUPPORT(3),
        RELATED(2),
        CONTRADICTS(1),
        INSUFFICIENT(0);

        private final int gain;

        OracleRelation(int gain) {
            this.gain = gain;
        }

        int gain() {
            return gain;
        }
    }

    enum GoldAnswerability {
        SUPPORTED,
        PARTIALLY_SUPPORTED,
        NOT_SUPPORTED
    }

    enum CeilingState {
        FOUND,
        PARTIAL,
        NONE,
        UNRESOLVED
    }

    enum FailureStage {
        ALREADY_CORRECT,
        RANKING_RECOVERABLE,
        RETRIEVAL_MISS,
        PARTIAL_ONLY,
        FALSE_POSITIVE_RISK,
        NO_SUPPORT
    }

    record QueryGold(
            String queryId,
            String userBundleId,
            String professionGroup,
            String language,
            List<String> categories,
            GoldAnswerability answerability,
            List<OracleRelation> expectedGoldRelations,
            Map<String, OracleRelation> relationByEvidenceChildId) {

        QueryGold {
            requireNonBlank(queryId, "Gold queryId");
            requireNonBlank(userBundleId, "Gold userBundleId");
            categories = categories == null ? List.of() : List.copyOf(categories);
            expectedGoldRelations = expectedGoldRelations == null
                    ? List.of() : List.copyOf(expectedGoldRelations);
            expectedGoldRelations.forEach(value -> Objects.requireNonNull(value, "expected Gold relation"));
            relationByEvidenceChildId = relationByEvidenceChildId == null
                    ? Map.of() : Map.copyOf(relationByEvidenceChildId);
        }

        boolean goldDirectPositive() {
            return expectedGoldRelations.contains(OracleRelation.DIRECT_SUPPORT);
        }
    }

    record OracleCandidate(
            int sourceRank,
            int oracleRank,
            CandidateProjection candidate,
            OracleRelation relation) {

        OracleCandidate {
            if (sourceRank < 1 || oracleRank < 1) {
                throw new IllegalArgumentException("Oracle candidate ranks must be positive");
            }
            Objects.requireNonNull(candidate, "candidate");
            Objects.requireNonNull(relation, "relation");
        }
    }

    record RankingMetrics(
            boolean directRecallAt5,
            boolean directRecallAt20,
            boolean top1,
            double mrr,
            double ndcgAt5) {
    }

    record QueryResult(
            String queryId,
            String userBundleId,
            String split,
            String professionGroup,
            String language,
            List<String> categories,
            boolean directPositive,
            CeilingState ceilingState,
            CeilingState expectedState,
            boolean ceilingStateCorrect,
            FailureStage failureStage,
            RankingMetrics s0,
            RankingMetrics o1,
            List<OracleCandidate> s0Ranking,
            List<OracleCandidate> o1Ranking) {

        QueryResult {
            categories = List.copyOf(categories);
            s0Ranking = List.copyOf(s0Ranking);
            o1Ranking = List.copyOf(o1Ranking);
        }
    }

    record AggregateMetrics(
            long queryCount,
            long directPositiveQueryCount,
            double s0DirectRecallAt5,
            double o1DirectRecallAt5,
            double s0DirectRecallAt20,
            double o1DirectRecallAt20,
            double s0Top1,
            double o1Top1,
            double s0Mrr,
            double o1Mrr,
            double s0NdcgAt5,
            double o1NdcgAt5,
            long ceilingStateCorrectCount,
            double ceilingStateAccuracy,
            Map<FailureStage, Long> failureStageCounts) {

        AggregateMetrics {
            failureStageCounts = Map.copyOf(failureStageCounts);
        }
    }

    record OracleRun(
            VerifiedCandidates verifiedCandidates,
            List<QueryResult> queries,
            AggregateMetrics aggregate,
            Map<String, AggregateMetrics> userSlices,
            Map<String, AggregateMetrics> professionSlices,
            Map<String, AggregateMetrics> languageSlices,
            Map<String, AggregateMetrics> categorySlices) {

        OracleRun {
            queries = List.copyOf(queries);
            userSlices = Map.copyOf(userSlices);
            professionSlices = Map.copyOf(professionSlices);
            languageSlices = Map.copyOf(languageSlices);
            categorySlices = Map.copyOf(categorySlices);
        }
    }
}
