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
            CeilingState ceilingState = ceilingState(gold, s0);
            CeilingState expectedState = expectedState(gold.answerability());
            FailureStage failureStage = failureStage(
                    gold.answerability(), directPositive, directCandidate, relatedCandidate,
                    ceilingState, expectedState, s0);
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
                    rankingMetrics(s0, gold),
                    rankingMetrics(o1, gold),
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
        validateAspectGold(gold);
        Set<String> candidateIds = query.rankedCandidates().stream()
                .map(CandidateProjection::candidateId)
                .collect(Collectors.toSet());
        Set<String> expectedUnitIds = gold.aspects().stream()
                .flatMap(aspect -> aspect.expectedEvidence().stream())
                .map(ExpectedGoldEvidence::evidenceUnitId)
                .collect(Collectors.toSet());
        gold.coveredEvidenceUnitIdsByCandidateId().forEach((candidateId, unitIds) -> {
            requireNonBlank(candidateId, "Gold candidate ID");
            if (!candidateIds.contains(candidateId)) {
                throw new IllegalArgumentException(
                        "Oracle coverage references a candidate outside frozen candidates: " + candidateId);
            }
            if (unitIds.isEmpty() || !expectedUnitIds.containsAll(unitIds)) {
                throw new IllegalArgumentException(
                        "Oracle coverage references a non-expected Gold unit: " + candidateId);
            }
        });
        boolean directPositive = gold.goldDirectPositive();
        boolean nonDirectRequiredAspect = gold.aspects().stream()
                .filter(GoldAspect::required)
                .anyMatch(aspect -> aspect.expectedEvidence().stream()
                        .noneMatch(value -> value.relation() == OracleRelation.DIRECT_SUPPORT));
        if (gold.answerability() == GoldAnswerability.SUPPORTED
                && (!directPositive || nonDirectRequiredAspect)) {
            throw new IllegalArgumentException(
                    "SUPPORTED Gold requires DIRECT_SUPPORT for every required aspect");
        }
        if (gold.answerability() == GoldAnswerability.PARTIALLY_SUPPORTED
                && (!directPositive || !nonDirectRequiredAspect)) {
            throw new IllegalArgumentException(
                    "PARTIALLY_SUPPORTED Gold requires direct and unresolved required aspects");
        }
        if (gold.answerability() == GoldAnswerability.NOT_SUPPORTED && directPositive) {
            throw new IllegalArgumentException("NOT_SUPPORTED Gold cannot expect DIRECT_SUPPORT");
        }
    }

    private void validateAspectGold(QueryGold gold) {
        GoldAspectExpression expression = Objects.requireNonNull(
                gold.aspectExpression(), "Gold aspectExpression");
        if (!("ALL".equals(expression.operator()) || "ANY".equals(expression.operator()))) {
            throw new IllegalArgumentException("Gold aspect operator must be ALL or ANY");
        }
        Map<String, GoldAspect> aspectById = gold.aspects().stream().collect(Collectors.toMap(
                GoldAspect::aspectId,
                Function.identity(),
                (left, right) -> {
                    throw new IllegalArgumentException("duplicate Gold aspect: " + left.aspectId());
                },
                LinkedHashMap::new));
        Set<String> expressionRequiredIds = new LinkedHashSet<>(expression.requiredAspectIds());
        Set<String> declaredRequiredIds = gold.aspects().stream()
                .filter(GoldAspect::required)
                .map(GoldAspect::aspectId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (aspectById.isEmpty()
                || expression.requiredAspectIds().isEmpty()
                || !aspectById.keySet().containsAll(expression.requiredAspectIds())
                || expressionRequiredIds.size() != expression.requiredAspectIds().size()
                || !expressionRequiredIds.equals(declaredRequiredIds)
                || expression.minShouldMatch() < 1
                || expression.minShouldMatch() > expression.requiredAspectIds().size()) {
            throw new IllegalArgumentException("Gold aspect expression is invalid");
        }
        Set<String> unitIds = new LinkedHashSet<>();
        for (GoldAspect aspect : gold.aspects()) {
            requireNonBlank(aspect.aspectId(), "Gold aspectId");
            if (aspect.minEvidenceGroups() < 0) {
                throw new IllegalArgumentException("Gold aspect evidence requirement is invalid");
            }
            Set<String> directGroups = aspect.expectedEvidence().stream()
                    .filter(expected -> expected.relation() == OracleRelation.DIRECT_SUPPORT)
                    .map(ExpectedGoldEvidence::evidenceGroupId)
                    .collect(Collectors.toSet());
            if (directGroups.isEmpty()) {
                if (aspect.minEvidenceGroups() != 0 || !aspect.requiredEvidenceGroupIds().isEmpty()) {
                    throw new IllegalArgumentException(
                            "Gold non-direct aspect cannot require direct evidence groups");
                }
            }
            else if (aspect.minEvidenceGroups() < 1
                    || aspect.minEvidenceGroups() > directGroups.size()
                    || aspect.requiredEvidenceGroupIds().isEmpty()
                    || !directGroups.containsAll(aspect.requiredEvidenceGroupIds())) {
                throw new IllegalArgumentException("Gold direct aspect requirement is invalid");
            }
            for (ExpectedGoldEvidence expected : aspect.expectedEvidence()) {
                requireNonBlank(expected.evidenceUnitId(), "Gold Evidence Unit ID");
                requireNonBlank(expected.evidenceGroupId(), "Gold Evidence Group ID");
                Objects.requireNonNull(expected.relation(), "Gold expected relation");
                if (!unitIds.add(expected.evidenceUnitId())) {
                    throw new IllegalArgumentException(
                            "Gold Evidence Unit cannot cross aspects: " + expected.evidenceUnitId());
                }
            }
        }
    }

    private List<OracleCandidate> sourceRanking(QueryProjection query, QueryGold gold) {
        List<OracleCandidate> result = new ArrayList<>();
        for (CandidateProjection candidate : query.rankedCandidates()) {
            OracleRelation relation = relationForCandidate(gold, candidate.candidateId());
            result.add(new OracleCandidate(candidate.rank(), candidate.rank(), candidate, relation));
        }
        return List.copyOf(result);
    }

    private OracleRelation relationForCandidate(QueryGold gold, String candidateId) {
        Set<String> covered = Set.copyOf(
                gold.coveredEvidenceUnitIdsByCandidateId().getOrDefault(candidateId, List.of()));
        return gold.aspects().stream()
                .flatMap(aspect -> aspect.expectedEvidence().stream())
                .filter(expected -> covered.contains(expected.evidenceUnitId()))
                .map(ExpectedGoldEvidence::relation)
                .min(Enum::compareTo)
                .orElse(OracleRelation.UNJUDGED);
    }

    private List<OracleCandidate> stableOraclePartition(List<OracleCandidate> source) {
        return stablePartitionPrefix(source, source.size());
    }

    /**
     * Applies the frozen Gold relation order to a bounded prefix while preserving the untouched
     * tail. This is package-private so a real validator can be compared with an addressable
     * Oracle without redefining PRZ-030 relevance or aspect completeness.
     */
    List<OracleCandidate> stablePartitionPrefix(List<OracleCandidate> source, int cutoff) {
        Objects.requireNonNull(source, "source");
        if (cutoff < 0 || cutoff > source.size()) {
            throw new IllegalArgumentException("Oracle prefix cutoff is outside candidate ranking");
        }
        List<OracleCandidate> result = new ArrayList<>();
        for (OracleRelation relation : OracleRelation.values()) {
            for (OracleCandidate candidate : source.subList(0, cutoff)) {
                if (candidate.relation() == relation) {
                    result.add(new OracleCandidate(
                            candidate.sourceRank(), result.size() + 1, candidate.candidate(), relation));
                }
            }
        }
        for (OracleCandidate candidate : source.subList(cutoff, source.size())) {
            result.add(new OracleCandidate(
                    candidate.sourceRank(), result.size() + 1, candidate.candidate(), candidate.relation()));
        }
        List<OracleCandidate> immutable = List.copyOf(result);
        validateParity(source, immutable);
        return immutable;
    }

    /** Scores an exact candidate permutation with the existing Gold/aspect-aware metric logic. */
    ScoredRanking scoreCandidateOrder(
            QueryGold gold,
            List<OracleCandidate> canonicalS0,
            List<String> orderedCandidateIds) {
        Objects.requireNonNull(gold, "gold");
        Objects.requireNonNull(canonicalS0, "canonicalS0");
        Objects.requireNonNull(orderedCandidateIds, "orderedCandidateIds");
        Map<String, OracleCandidate> byId = canonicalS0.stream().collect(Collectors.toMap(
                value -> value.candidate().candidateId(),
                Function.identity(),
                (left, right) -> {
                    throw new IllegalArgumentException(
                            "duplicate canonical candidate: " + left.candidate().candidateId());
                },
                LinkedHashMap::new));
        Set<String> orderedIds = new LinkedHashSet<>(orderedCandidateIds);
        if (orderedCandidateIds.size() != canonicalS0.size()
                || orderedIds.size() != orderedCandidateIds.size()
                || !orderedIds.equals(byId.keySet())) {
            throw new IllegalArgumentException("scored candidate order must be an exact S0 permutation");
        }
        List<OracleCandidate> ranking = new ArrayList<>();
        for (String candidateId : orderedCandidateIds) {
            OracleCandidate candidate = byId.get(candidateId);
            ranking.add(new OracleCandidate(
                    candidate.sourceRank(),
                    ranking.size() + 1,
                    candidate.candidate(),
                    candidate.relation()));
        }
        List<OracleCandidate> immutable = List.copyOf(ranking);
        validateIdentityParity(canonicalS0, immutable);
        return new ScoredRanking(
                immutable,
                rankingMetrics(immutable, gold),
                ceilingState(gold, immutable));
    }

    private void validateIdentityParity(List<OracleCandidate> source, List<OracleCandidate> result) {
        if (source.size() != result.size()) {
            throw new IllegalStateException("candidate count parity failed");
        }
        Map<String, CandidateProjection> sourceById = source.stream().collect(Collectors.toMap(
                value -> value.candidate().candidateId(), OracleCandidate::candidate));
        Map<String, CandidateProjection> resultById = result.stream().collect(Collectors.toMap(
                value -> value.candidate().candidateId(), OracleCandidate::candidate));
        if (!sourceById.equals(resultById)) {
            throw new IllegalStateException("candidate identity/cosine/source parity failed");
        }
    }

    private void validateParity(List<OracleCandidate> s0, List<OracleCandidate> o1) {
        validateIdentityParity(s0, o1);
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

    private CeilingState ceilingState(QueryGold gold, List<OracleCandidate> ranking) {
        if (gold.answerability() == GoldAnswerability.NOT_SUPPORTED) {
            return CeilingState.NONE;
        }
        Set<String> hitDirectUnits = hitDirectUnits(gold, ranking, ranking.size());
        if (gold.answerability() == GoldAnswerability.PARTIALLY_SUPPORTED) {
            return directRequirementsMet(gold, hitDirectUnits)
                    ? CeilingState.PARTIAL : CeilingState.UNRESOLVED;
        }
        return requirementsMet(gold, hitDirectUnits) ? CeilingState.FOUND : CeilingState.UNRESOLVED;
    }

    private Set<String> hitDirectUnits(
            QueryGold gold,
            List<OracleCandidate> ranking,
            int cutoff) {
        return ranking.stream()
                .limit(cutoff)
                .flatMap(candidate -> gold.coveredEvidenceUnitIdsByCandidateId()
                        .getOrDefault(candidate.candidate().candidateId(), List.of()).stream())
                .filter(unitId -> expectedRelation(gold, unitId) == OracleRelation.DIRECT_SUPPORT)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean directRequirementsMet(QueryGold gold, Set<String> hitDirectUnits) {
        if (gold.answerability() != GoldAnswerability.PARTIALLY_SUPPORTED) {
            return requirementsMet(gold, hitDirectUnits);
        }
        List<GoldAspect> directRequiredAspects = gold.aspects().stream()
                .filter(GoldAspect::required)
                .filter(aspect -> aspect.expectedEvidence().stream()
                        .anyMatch(expected -> expected.relation() == OracleRelation.DIRECT_SUPPORT))
                .toList();
        return !directRequiredAspects.isEmpty()
                && directRequiredAspects.stream()
                        .allMatch(aspect -> aspectRequirementsMet(aspect, hitDirectUnits));
    }

    private boolean aspectRequirementsMet(GoldAspect aspect, Set<String> hitDirectUnits) {
        Set<String> directGroups = aspect.expectedEvidence().stream()
                .filter(expected -> expected.relation() == OracleRelation.DIRECT_SUPPORT)
                .filter(expected -> hitDirectUnits.contains(expected.evidenceUnitId()))
                .map(ExpectedGoldEvidence::evidenceGroupId)
                .collect(Collectors.toSet());
        return directGroups.containsAll(aspect.requiredEvidenceGroupIds())
                && directGroups.size() >= aspect.minEvidenceGroups();
    }

    private OracleRelation expectedRelation(QueryGold gold, String evidenceUnitId) {
        return gold.aspects().stream()
                .flatMap(aspect -> aspect.expectedEvidence().stream())
                .filter(expected -> expected.evidenceUnitId().equals(evidenceUnitId))
                .map(ExpectedGoldEvidence::relation)
                .findFirst()
                .orElse(OracleRelation.INSUFFICIENT);
    }

    private boolean requirementsMet(QueryGold gold, Set<String> hitDirectUnits) {
        Map<String, Boolean> satisfied = new LinkedHashMap<>();
        for (GoldAspect aspect : gold.aspects()) {
            satisfied.put(aspect.aspectId(), aspectRequirementsMet(aspect, hitDirectUnits));
        }
        long hit = gold.aspectExpression().requiredAspectIds().stream()
                .filter(id -> Boolean.TRUE.equals(satisfied.get(id)))
                .count();
        return "ALL".equals(gold.aspectExpression().operator())
                ? hit == gold.aspectExpression().requiredAspectIds().size()
                : hit >= gold.aspectExpression().minShouldMatch();
    }

    private FailureStage failureStage(
            GoldAnswerability answerability,
            boolean directPositive,
            boolean directCandidate,
            boolean relatedCandidate,
            CeilingState ceilingState,
            CeilingState expectedState,
            List<OracleCandidate> s0) {
        if (directPositive) {
            if (ceilingState != expectedState) {
                return FailureStage.RETRIEVAL_MISS;
            }
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

    private RankingMetrics rankingMetrics(List<OracleCandidate> ranking, QueryGold gold) {
        boolean directPositive = gold.goldDirectPositive();
        Integer firstDirect = ranking.stream()
                .filter(value -> value.relation() == OracleRelation.DIRECT_SUPPORT)
                .map(OracleCandidate::oracleRank)
                .findFirst()
                .orElse(null);
        return new RankingMetrics(
                directPositive && directRequirementsMet(gold, hitDirectUnits(gold, ranking, 5)),
                directPositive && directRequirementsMet(gold, hitDirectUnits(gold, ranking, 20)),
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
        INSUFFICIENT(0),
        UNJUDGED(0);

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

    record GoldAspectExpression(
            String operator,
            List<String> requiredAspectIds,
            int minShouldMatch) {

        GoldAspectExpression {
            requireNonBlank(operator, "Gold aspect operator");
            requiredAspectIds = requiredAspectIds == null ? List.of() : List.copyOf(requiredAspectIds);
        }
    }

    record ExpectedGoldEvidence(
            String evidenceUnitId,
            String evidenceGroupId,
            OracleRelation relation) {
    }

    record GoldAspect(
            String aspectId,
            boolean required,
            int minEvidenceGroups,
            List<String> requiredEvidenceGroupIds,
            List<ExpectedGoldEvidence> expectedEvidence) {

        GoldAspect {
            requiredEvidenceGroupIds = requiredEvidenceGroupIds == null
                    ? List.of() : List.copyOf(requiredEvidenceGroupIds);
            expectedEvidence = expectedEvidence == null ? List.of() : List.copyOf(expectedEvidence);
        }
    }

    record QueryGold(
            String queryId,
            String userBundleId,
            String professionGroup,
            String language,
            List<String> categories,
            GoldAnswerability answerability,
            GoldAspectExpression aspectExpression,
            List<GoldAspect> aspects,
            Map<String, List<String>> coveredEvidenceUnitIdsByCandidateId) {

        QueryGold {
            requireNonBlank(queryId, "Gold queryId");
            requireNonBlank(userBundleId, "Gold userBundleId");
            categories = categories == null ? List.of() : List.copyOf(categories);
            aspects = aspects == null ? List.of() : List.copyOf(aspects);
            if (coveredEvidenceUnitIdsByCandidateId == null) {
                coveredEvidenceUnitIdsByCandidateId = Map.of();
            }
            else {
                Map<String, List<String>> immutable = new LinkedHashMap<>();
                coveredEvidenceUnitIdsByCandidateId.forEach(
                        (key, value) -> immutable.put(key, List.copyOf(value)));
                coveredEvidenceUnitIdsByCandidateId = Map.copyOf(immutable);
            }
        }

        boolean goldDirectPositive() {
            return aspects.stream()
                    .flatMap(aspect -> aspect.expectedEvidence().stream())
                    .anyMatch(expected -> expected.relation() == OracleRelation.DIRECT_SUPPORT);
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

    /** Recall requires Gold aspect/group completeness; Top1 and MRR rank the first DIRECT evidence. */
    record RankingMetrics(
            boolean directRecallAt5,
            boolean directRecallAt20,
            boolean top1,
            double mrr,
            double ndcgAt5) {
    }

    record ScoredRanking(
            List<OracleCandidate> ranking,
            RankingMetrics metrics,
            CeilingState ceilingState) {

        ScoredRanking {
            ranking = List.copyOf(ranking);
            Objects.requireNonNull(metrics, "metrics");
            Objects.requireNonNull(ceilingState, "ceilingState");
        }
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
