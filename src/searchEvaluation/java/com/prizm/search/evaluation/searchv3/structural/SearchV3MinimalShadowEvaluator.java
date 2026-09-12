package com.prizm.search.evaluation.searchv3.structural;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Gold-after-output evaluator for the PRZ-032 Production V2 / Minimal V3 shadow comparison.
 *
 * <p>Every relevance decision is made from owner-scoped, half-open Unicode code-point source
 * spans. A multi-span Evidence Unit is DIRECT only when one atomic output span contains every
 * constituent Gold span; spans from different V3 children are deliberately never unioned.
 */
final class SearchV3MinimalShadowEvaluator {

    static final int EXPECTED_QUERY_COUNT = 117;
    static final int EXPECTED_USER_COUNT = 23;
    static final int EXPECTED_DIRECT_POSITIVE_COUNT = 85;
    static final int EXPECTED_NOT_SUPPORTED_COUNT = 32;
    static final int CANDIDATE_LIMIT = 20;
    static final int FINAL_LIMIT = 5;

    private static final Map<String, Long> EXPECTED_SUITE_COUNTS = Map.of(
            "ORIGINAL", 21L,
            "LONG_FORM", 24L,
            "ROBUSTNESS", 24L,
            "TYPED_STRESS", 24L,
            "SEMANTIC_STRESS", 24L);

    EvaluationReport evaluate(
            SearchV3MinimalShadowFreeze.OutputArtifact output,
            SearchV3MinimalShadowGold.GoldSnapshot gold) {
        return evaluate(output, gold, InventoryContract.prz032());
    }

    EvaluationReport evaluate(
            SearchV3MinimalShadowFreeze.OutputArtifact output,
            SearchV3MinimalShadowGold.GoldSnapshot gold,
            InventoryContract inventory) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(gold, "gold");
        Objects.requireNonNull(inventory, "inventory");
        DedupAudit dedup = validateInventory(output, gold, inventory);

        List<QueryEvaluation> queries = new ArrayList<>();
        Set<String> seenQueryIds = new LinkedHashSet<>();
        for (SearchV3MinimalShadowFreeze.QueryOutput value : output.queries()) {
            if (!seenQueryIds.add(value.queryId())) {
                throw new IllegalStateException("duplicate frozen queryId: " + value.queryId());
            }
            SearchV3MinimalShadowGold.GoldQuery queryGold = gold.queriesById().get(value.queryId());
            if (queryGold == null) {
                throw new IllegalStateException("frozen output has no Gold query: " + value.queryId());
            }
            validateQueryIdentity(value, queryGold);
            GoldContext context = context(queryGold, gold);
            PathEvaluation v2 = evaluateV2(value, context, gold.parents());
            PathEvaluation v3 = evaluateV3(value, context, gold.parents());
            boolean directPositive = queryGold.hasDirectSupport();
            PrimaryClassification classification = directPositive
                    ? classify(v2.finalRanking().top1(), v3.finalRanking().top1())
                    : PrimaryClassification.NOT_APPLICABLE;
            RankComparison rankComparison = directPositive
                    ? compareRanks(v2.finalRanking().firstDirectRank(), v3.finalRanking().firstDirectRank())
                    : RankComparison.NOT_APPLICABLE;
            TypedQueryDiagnostic typed = typedDiagnostic(context, value.v3(), v2, v3);
            SemanticNegativeDiagnostic semantic = semanticNegativeDiagnostic(queryGold, value.v3(), v2, v3);
            boolean sameGoldDifferentRank = directPositive
                    && v2.finalRanking().firstDirectRank() != null
                    && v3.finalRanking().firstDirectRank() != null
                    && !v2.finalRanking().firstDirectRank().equals(v3.finalRanking().firstDirectRank());
            queries.add(new QueryEvaluation(
                    value.suite(), value.datasetVersion(), value.split(), value.queryId(),
                    value.userBundleId(), value.professionGroup(), value.language(),
                    queryGold.answerability(), queryGold.categories(), directPositive, value.v3().state(),
                    v2, v3, classification, rankComparison,
                    sameGoldDifferentRank,
                    v2.finalDiagnostics().crossParentContaminatedResultCount() > 0,
                    queryGold.typedExpectedState() != null
                            && v3.finalRanking().top1() && !v2.finalRanking().top1(),
                    typed, semantic));
        }
        if (seenQueryIds.size() != gold.queriesById().size()) {
            Set<String> missing = new LinkedHashSet<>(gold.queriesById().keySet());
            missing.removeAll(seenQueryIds);
            throw new IllegalStateException("Gold queries missing frozen output: " + missing);
        }

        List<QueryEvaluation> immutable = List.copyOf(queries);
        ComparisonAggregate queryMicro = aggregate(immutable, Aggregation.QUERY_MICRO);
        ComparisonAggregate userMacro = aggregate(immutable, Aggregation.USER_MACRO);
        TypedAggregate typed = typedAggregate(immutable);
        SemanticAggregate semantic = semanticAggregate(immutable);
        DecisionResult decision = decision(output, immutable, queryMicro, userMacro, typed, semantic);
        return new EvaluationReport(
                decision.decision(), decision.biggestBottleneck(), dedup, immutable,
                indexStructure(output.v2IndexUnits(), gold),
                indexStructure(output.v3IndexUnits(), gold),
                queryMicro, userMacro,
                sliced(immutable, QueryEvaluation::professionGroup),
                sliced(immutable, QueryEvaluation::language),
                typed, semantic, operation(output));
    }

    private OperationAggregate operation(SearchV3MinimalShadowFreeze.OutputArtifact output) {
        List<Double> v2 = output.queries().stream().map(value -> value.v2().finalTotalMs()).sorted().toList();
        List<Double> v3 = output.queries().stream().map(value -> value.v3().totalMs()).sorted().toList();
        return new OperationAggregate(
                output.v2Indexing(), output.v3Indexing(),
                percentile(v2, 0.50d), percentile(v2, 0.95d),
                percentile(v3, 0.50d), percentile(v3, 0.95d));
    }

    private double percentile(List<Double> sorted, double percentile) {
        if (sorted.isEmpty()) return 0.0d;
        int index = Math.max(0, Math.min(sorted.size() - 1,
                (int) Math.ceil(percentile * sorted.size()) - 1));
        return sorted.get(index);
    }

    private DedupAudit validateInventory(
            SearchV3MinimalShadowFreeze.OutputArtifact output,
            SearchV3MinimalShadowGold.GoldSnapshot gold,
            InventoryContract inventory) {
        int expectedQueries = inventory.expectedQueryCount();
        if (output.queryCount() != expectedQueries
                || output.queries().size() != expectedQueries
                || gold.queriesById().size() != expectedQueries) {
            throw new IllegalStateException(
                    inventory.label() + " requires exactly " + expectedQueries + " canonical queries");
        }
        Set<String> outputUsers = output.queries().stream()
                .map(SearchV3MinimalShadowFreeze.QueryOutput::userBundleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> goldUsers = gold.queriesById().values().stream()
                .map(SearchV3MinimalShadowGold.GoldQuery::userBundleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        int expectedUsers = inventory.expectedUserCount();
        if (output.userCount() != expectedUsers
                || outputUsers.size() != expectedUsers
                || goldUsers.size() != expectedUsers
                || !outputUsers.equals(goldUsers)) {
            throw new IllegalStateException(
                    inventory.label() + " user inventory changed: declared=" + output.userCount()
                            + " output=" + outputUsers.size() + " Gold=" + goldUsers.size());
        }
        long direct = gold.queriesById().values().stream()
                .filter(SearchV3MinimalShadowGold.GoldQuery::hasDirectSupport).count();
        long negatives = gold.queriesById().values().stream()
                .filter(value -> "NOT_SUPPORTED".equals(value.answerability())).count();
        if (direct != inventory.expectedDirectPositiveCount()
                || negatives != inventory.expectedNotSupportedCount()) {
            throw new IllegalStateException(
                    inventory.label() + " Gold inventory changed: direct=" + direct
                            + " negatives=" + negatives);
        }
        Map<String, Long> suites = output.queries().stream().collect(Collectors.groupingBy(
                SearchV3MinimalShadowFreeze.QueryOutput::suite,
                LinkedHashMap::new,
                Collectors.counting()));
        if (!inventory.expectedSuiteCounts().equals(suites)) {
            throw new IllegalStateException(inventory.label() + " suite inventory changed: " + suites);
        }

        Map<String, List<String>> canonicalIds = new LinkedHashMap<>();
        for (SearchV3MinimalShadowGold.GoldQuery query : gold.queriesById().values()) {
            String key = query.userBundleId() + "\u0000"
                    + SearchV3MinimalShadowDataset.normalizeQuery(query.text());
            canonicalIds.computeIfAbsent(key, ignored -> new ArrayList<>()).add(query.queryId());
        }
        Map<String, List<String>> collisions = canonicalIds.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> List.copyOf(entry.getValue()),
                        (left, right) -> left,
                        LinkedHashMap::new));
        if (canonicalIds.size() != expectedQueries || !collisions.isEmpty()) {
            throw new IllegalStateException(
                    "canonical query dedup changed: unique=" + canonicalIds.size()
                            + " collisions=" + collisions);
        }
        long frozenExecutionKeys = output.queries().stream()
                .map(value -> value.userBundleId() + "\u0000" + value.queryTextSha256())
                .distinct().count();
        if (frozenExecutionKeys != expectedQueries) {
            throw new IllegalStateException("frozen query execution identity is not unique");
        }
        return new DedupAudit(
                expectedQueries, canonicalIds.size(), (int) frozenExecutionKeys,
                Map.copyOf(collisions), Map.copyOf(suites));
    }

    private IndexStructureAudit indexStructure(
            List<SearchV3MinimalShadowFreeze.IndexUnit> indexUnits,
            SearchV3MinimalShadowGold.GoldSnapshot gold) {
        Set<String> directUnitIds = gold.queriesById().values().stream()
                .flatMap(value -> value.relationByUnitId().entrySet().stream())
                .filter(value -> "DIRECT_SUPPORT".equals(value.getValue()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<SearchV3MinimalShadowGold.GoldUnit> directUnits = directUnitIds.stream()
                .map(gold.units()::get).filter(Objects::nonNull).toList();
        long touched = directUnits.stream().filter(unit -> indexUnits.stream()
                .flatMap(value -> value.spans().stream()).anyMatch(span -> overlaps(span, unit))).count();
        long fragmented = directUnits.stream().filter(unit -> indexUnits.stream()
                        .flatMap(value -> value.spans().stream()).anyMatch(span -> overlaps(span, unit)))
                .filter(unit -> indexUnits.stream().flatMap(value -> value.spans().stream())
                        .noneMatch(span -> covers(span, unit))).count();
        long contaminated = indexUnits.stream().filter(index -> gold.parents().values().stream()
                .filter(parent -> index.spans().stream().anyMatch(span -> overlaps(span, parent.span())))
                .map(SearchV3MinimalShadowGold.GoldParent::parentId).distinct().count() > 1).count();
        long duplicate = indexUnits.stream().flatMap(value -> value.spans().stream())
                .collect(Collectors.groupingBy(SpanKey::from, Collectors.counting())).values().stream()
                .mapToLong(count -> Math.max(0L, count - 1L)).sum();
        long atomicSpans = indexUnits.stream().mapToLong(value -> value.spans().size()).sum();
        return new IndexStructureAudit(
                indexUnits.size(), atomicSpans, directUnits.size(), touched, fragmented,
                ratio(fragmented, directUnits.size()), contaminated,
                ratio(contaminated, indexUnits.size()), duplicate, ratio(duplicate, atomicSpans));
    }

    private boolean overlaps(
            ProductionV2ShadowAdapter.SourceSpan span,
            SearchV3MinimalShadowGold.GoldUnit unit) {
        return unit.spans().stream().anyMatch(value -> overlaps(span, value));
    }

    private boolean overlaps(
            ProductionV2ShadowAdapter.SourceSpan span,
            SearchV3MinimalShadowGold.GoldSpan gold) {
        return span.documentId().equals(gold.documentId())
                && span.versionId().equals(gold.versionId())
                && Objects.equals(span.page(), gold.page())
                && span.codePointStart() < gold.codePointEnd()
                && span.codePointEnd() > gold.codePointStart();
    }

    private void validateQueryIdentity(
            SearchV3MinimalShadowFreeze.QueryOutput output,
            SearchV3MinimalShadowGold.GoldQuery gold) {
        if (!output.suite().equals(gold.suite())
                || !output.datasetVersion().equals(gold.datasetVersion())
                || !output.split().equals(gold.split())
                || !output.queryId().equals(gold.queryId())
                || !output.userBundleId().equals(gold.userBundleId())
                || !output.language().equals(gold.language())
                || !output.queryTextSha256().equals(SearchV3MinimalShadowDataset.sha256(gold.text()))) {
            throw new IllegalStateException("frozen output / Gold identity mismatch: " + output.queryId());
        }
        boolean typed = gold.typedExpectedState() != null;
        if (output.typedApplicabilityVerified() != typed
                || output.v3().typedApplicabilityVerified() != typed) {
            throw new IllegalStateException("typed applicability mismatch: " + output.queryId());
        }
        validateRanks(output.v2().candidates(), ProductionV2ShadowAdapter.CandidateResult::rank,
                CANDIDATE_LIMIT, "V2 candidates", output.queryId());
        validateRanks(output.v2().finalResults(), ProductionV2ShadowAdapter.FinalResult::rank,
                FINAL_LIMIT, "V2 final", output.queryId());
        validateRanks(output.v3().candidates(), MinimalV3ShadowAdapter.CandidateResult::rank,
                CANDIDATE_LIMIT, "V3 candidates", output.queryId());
        validateRanks(output.v3().finalResults(), MinimalV3ShadowAdapter.FinalResult::rank,
                FINAL_LIMIT, "V3 final", output.queryId());
    }

    private <T> void validateRanks(
            List<T> values,
            Function<T, Integer> rank,
            int limit,
            String label,
            String queryId) {
        if (values.size() > limit) {
            throw new IllegalStateException(label + " exceeds " + limit + ": " + queryId);
        }
        List<Integer> ranks = values.stream().map(rank).sorted().toList();
        for (int index = 0; index < ranks.size(); index++) {
            if (ranks.get(index) != index + 1) {
                throw new IllegalStateException(label + " ranks are not contiguous: " + queryId);
            }
        }
    }

    private GoldContext context(
            SearchV3MinimalShadowGold.GoldQuery query,
            SearchV3MinimalShadowGold.GoldSnapshot snapshot) {
        List<UnitRelation> units = new ArrayList<>();
        for (Map.Entry<String, String> entry : query.relationByUnitId().entrySet()) {
            SearchV3MinimalShadowGold.GoldUnit unit = snapshot.units().get(entry.getKey());
            if (unit == null || !query.userBundleId().equals(unit.userBundleId())) {
                throw new IllegalStateException("query has missing/cross-owner Gold unit: " + entry.getKey());
            }
            validateGoldUnit(unit);
            units.add(new UnitRelation(unit, Relation.parse(entry.getValue())));
        }
        for (SearchV3MinimalShadowGold.Aspect aspect : query.aspects()) {
            for (String unitId : aspect.expectedRelations().keySet()) {
                if (!query.relationByUnitId().containsKey(unitId)) {
                    throw new IllegalStateException("aspect unit is absent from query relation map: " + unitId);
                }
            }
        }
        return new GoldContext(query, List.copyOf(units));
    }

    private void validateGoldUnit(SearchV3MinimalShadowGold.GoldUnit unit) {
        if (unit.spans() == null || unit.spans().isEmpty()) {
            throw new IllegalStateException("Gold unit has no source span: " + unit.evidenceUnitId());
        }
        for (SearchV3MinimalShadowGold.GoldSpan span : unit.spans()) {
            if (!unit.documentId().equals(span.documentId())
                    || !unit.versionId().equals(span.versionId())
                    || span.codePointStart() < 0
                    || span.codePointEnd() <= span.codePointStart()) {
                throw new IllegalStateException("invalid Gold source span: " + unit.evidenceUnitId());
            }
        }
    }

    private PathEvaluation evaluateV2(
            SearchV3MinimalShadowFreeze.QueryOutput query,
            GoldContext gold,
            Map<String, SearchV3MinimalShadowGold.GoldParent> parents) {
        List<RankedEvidence> candidates = query.v2().candidates().stream()
                .map(value -> new RankedEvidence(
                        value.rank(), value.candidateId(), List.of(value.span()), null, null, null))
                .toList();
        List<RankedEvidence> finals = query.v2().finalResults().stream()
                .map(value -> new RankedEvidence(
                        value.rank(), value.selectedCandidateId(), List.of(value.selectedSpan()),
                        value.displaySpan(), value.snippet(), value.evidenceChunkSpan()))
                .toList();
        RankingMetrics candidateRanking = ranking(candidates, gold);
        RankingMetrics finalRanking = ranking(finals, gold);
        return new PathEvaluation(
                candidateRanking,
                finalRanking,
                finalDiagnostics(finals, gold, parents),
                stageOutcome(candidateRanking, finalRanking, gold.query().hasDirectSupport()),
                query.v2().ownerLeakage() || derivedOwnerLeakage(query.userBundleId(), candidates, finals),
                query.v2().inactiveVersionLeakage());
    }

    private PathEvaluation evaluateV3(
            SearchV3MinimalShadowFreeze.QueryOutput query,
            GoldContext gold,
            Map<String, SearchV3MinimalShadowGold.GoldParent> parents) {
        List<RankedEvidence> candidates = query.v3().candidates().stream()
                .map(value -> new RankedEvidence(
                        value.rank(), value.candidateId(), value.spans(), null, null, null))
                .toList();
        List<RankedEvidence> finals = query.v3().finalResults().stream()
                .map(value -> new RankedEvidence(
                        value.rank(), value.evidenceChildId(), List.of(value.span()),
                        value.span(), null, null))
                .toList();
        RankingMetrics candidateRanking = ranking(candidates, gold);
        RankingMetrics finalRanking = ranking(finals, gold);
        return new PathEvaluation(
                candidateRanking,
                finalRanking,
                finalDiagnostics(finals, gold, parents),
                stageOutcome(candidateRanking, finalRanking, gold.query().hasDirectSupport()),
                query.v3().ownerLeakage() || derivedOwnerLeakage(query.userBundleId(), candidates, finals),
                false);
    }

    private boolean derivedOwnerLeakage(
            String owner,
            List<RankedEvidence> candidates,
            List<RankedEvidence> finals) {
        return java.util.stream.Stream.concat(candidates.stream(), finals.stream())
                .flatMap(value -> value.atomicSpans().stream())
                .anyMatch(span -> !owner.equals(span.userBundleId()));
    }

    private RankingMetrics ranking(List<RankedEvidence> ranking, GoldContext gold) {
        List<ScoredRank> scored = ranking.stream()
                .sorted(Comparator.comparingInt(RankedEvidence::rank))
                .map(value -> score(value, gold))
                .toList();
        boolean applicable = gold.query().hasDirectSupport();
        Integer firstDirect = scored.stream()
                .filter(value -> value.relation() == Relation.DIRECT_SUPPORT)
                .map(value -> value.evidence().rank())
                .findFirst().orElse(null);
        Set<String> directAt5 = hitDirectUnits(scored, 5);
        Set<String> directAt20 = hitDirectUnits(scored, 20);
        Coverage coverage5 = coverage(gold, directAt5);
        Coverage coverage20 = coverage(gold, directAt20);
        Ndcg ndcg = ndcgAt5(scored, gold);
        long judgedAt5 = scored.stream().limit(5)
                .filter(value -> value.relation() != Relation.UNJUDGED).count();
        int sizeAt5 = Math.min(5, scored.size());
        Relation top1Relation = scored.isEmpty() ? Relation.UNJUDGED : scored.get(0).relation();
        return new RankingMetrics(
                applicable,
                applicable && directRequirementsMet(gold, directAt5),
                applicable && directRequirementsMet(gold, directAt20),
                applicable && firstDirect != null && firstDirect == 1,
                applicable && firstDirect != null ? 1.0d / firstDirect : 0.0d,
                ndcg.applicable(), ndcg.value(),
                coverage5.group(), coverage20.group(),
                coverage5.parent(), coverage20.parent(),
                firstDirect,
                applicable && !directRequirementsMet(gold, directAt20),
                (int) judgedAt5,
                sizeAt5 - (int) judgedAt5,
                top1Relation);
    }

    private ScoredRank score(RankedEvidence evidence, GoldContext gold) {
        List<UnitRelation> covered = gold.units().stream()
                .filter(unit -> evidence.atomicSpans().stream()
                        .anyMatch(span -> covers(span, unit.unit())))
                .toList();
        Relation strongest = covered.stream().map(UnitRelation::relation)
                .max(Comparator.comparingInt(Relation::gain)).orElse(Relation.UNJUDGED);
        return new ScoredRank(evidence, strongest, covered);
    }

    private Set<String> hitDirectUnits(List<ScoredRank> ranking, int cutoff) {
        return ranking.stream().limit(cutoff)
                .flatMap(value -> value.covered().stream())
                .filter(value -> value.relation() == Relation.DIRECT_SUPPORT)
                .map(value -> value.unit().evidenceUnitId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean directRequirementsMet(GoldContext gold, Set<String> hitDirectUnits) {
        if (!gold.query().hasDirectSupport()) {
            return false;
        }
        if ("PARTIALLY_SUPPORTED".equals(gold.query().answerability())) {
            List<SearchV3MinimalShadowGold.Aspect> directRequired = gold.query().aspects().stream()
                    .filter(SearchV3MinimalShadowGold.Aspect::required)
                    .filter(aspect -> aspect.expectedRelations().entrySet().stream()
                            .anyMatch(value -> Relation.parse(value.getValue()) == Relation.DIRECT_SUPPORT))
                    .toList();
            return !directRequired.isEmpty()
                    && directRequired.stream().allMatch(value -> aspectMet(value, hitDirectUnits, gold));
        }
        Map<String, Boolean> met = gold.query().aspects().stream().collect(Collectors.toMap(
                SearchV3MinimalShadowGold.Aspect::aspectId,
                value -> aspectMet(value, hitDirectUnits, gold),
                (left, right) -> left,
                LinkedHashMap::new));
        SearchV3MinimalShadowGold.AspectExpression expression = gold.query().aspectExpression();
        long count = expression.requiredAspectIds().stream()
                .filter(value -> Boolean.TRUE.equals(met.get(value))).count();
        return "ALL".equals(expression.operator())
                ? count == expression.requiredAspectIds().size()
                : count >= expression.minShouldMatch();
    }

    private boolean aspectMet(
            SearchV3MinimalShadowGold.Aspect aspect,
            Set<String> hitDirectUnits,
            GoldContext gold) {
        Set<String> hitGroups = aspect.expectedRelations().entrySet().stream()
                .filter(value -> Relation.parse(value.getValue()) == Relation.DIRECT_SUPPORT)
                .filter(value -> hitDirectUnits.contains(value.getKey()))
                .map(value -> requiredUnit(gold, value.getKey()).groupId())
                .collect(Collectors.toSet());
        return hitGroups.containsAll(aspect.requiredEvidenceGroupIds())
                && hitGroups.size() >= aspect.minEvidenceGroups();
    }

    private SearchV3MinimalShadowGold.GoldUnit requiredUnit(GoldContext gold, String unitId) {
        return gold.units().stream().map(UnitRelation::unit)
                .filter(value -> value.evidenceUnitId().equals(unitId)).findFirst()
                .orElseThrow(() -> new IllegalStateException("missing query Gold unit: " + unitId));
    }

    private Coverage coverage(GoldContext gold, Set<String> directHitIds) {
        Set<String> allGroups = gold.units().stream()
                .filter(value -> value.relation() == Relation.DIRECT_SUPPORT)
                .map(value -> value.unit().groupId()).collect(Collectors.toSet());
        Set<String> hitGroups = gold.units().stream()
                .filter(value -> directHitIds.contains(value.unit().evidenceUnitId()))
                .map(value -> value.unit().groupId()).collect(Collectors.toSet());
        Set<String> allParents = gold.units().stream()
                .filter(value -> value.relation() == Relation.DIRECT_SUPPORT)
                .map(value -> value.unit().parentId()).collect(Collectors.toSet());
        Set<String> hitParents = gold.units().stream()
                .filter(value -> directHitIds.contains(value.unit().evidenceUnitId()))
                .map(value -> value.unit().parentId()).collect(Collectors.toSet());
        return new Coverage(ratio(hitGroups.size(), allGroups.size()), ratio(hitParents.size(), allParents.size()));
    }

    private Ndcg ndcgAt5(List<ScoredRank> ranking, GoldContext gold) {
        Set<String> creditedGroups = new LinkedHashSet<>();
        double actual = 0.0d;
        for (int index = 0; index < Math.min(5, ranking.size()); index++) {
            UnitRelation best = ranking.get(index).covered().stream()
                    .filter(value -> !creditedGroups.contains(value.unit().groupId()))
                    .max(Comparator.comparingInt(value -> value.relation().gain())).orElse(null);
            if (best != null) {
                creditedGroups.add(best.unit().groupId());
                actual += best.relation().gain() / log2(index + 2.0d);
            }
        }
        List<Integer> idealGains = gold.units().stream().collect(Collectors.toMap(
                        value -> value.unit().groupId(), value -> value.relation().gain(), Math::max))
                .values().stream().sorted(Comparator.reverseOrder()).limit(5).toList();
        double ideal = 0.0d;
        for (int index = 0; index < idealGains.size(); index++) {
            ideal += idealGains.get(index) / log2(index + 2.0d);
        }
        return ideal == 0.0d ? new Ndcg(false, 0.0d) : new Ndcg(true, actual / ideal);
    }

    private FinalDiagnostics finalDiagnostics(
            List<RankedEvidence> results,
            GoldContext gold,
            Map<String, SearchV3MinimalShadowGold.GoldParent> parents) {
        List<ScoredRank> scored = results.stream().map(value -> score(value, gold)).toList();
        Set<SpanKey> seenSpans = new LinkedHashSet<>();
        int duplicate = 0;
        for (RankedEvidence value : results) {
            for (ProductionV2ShadowAdapter.SourceSpan span : value.atomicSpans()) {
                if (!seenSpans.add(SpanKey.from(span))) duplicate++;
            }
        }
        Set<String> seenGroups = new LinkedHashSet<>();
        int repeatedGroups = 0;
        for (ScoredRank value : scored) {
            String group = strongestCovered(value).map(unit -> unit.unit().groupId()).orElse(null);
            if (group != null && !seenGroups.add(group)) repeatedGroups++;
        }

        long contaminated = results.stream()
                .filter(value -> value.atomicSpans().stream()
                        .anyMatch(span -> overlappingParents(span, gold.query().userBundleId(), parents) > 1))
                .count();
        List<UnitRelation> directUnits = gold.units().stream()
                .filter(value -> value.relation() == Relation.DIRECT_SUPPORT).toList();
        long touched = directUnits.stream().filter(unit -> results.stream()
                .flatMap(value -> value.atomicSpans().stream())
                .anyMatch(span -> overlapsAny(span, unit.unit()))).count();
        long fragmented = directUnits.stream().filter(unit -> results.stream()
                        .flatMap(value -> value.atomicSpans().stream())
                        .anyMatch(span -> overlapsAny(span, unit.unit())))
                .filter(unit -> results.stream().flatMap(value -> value.atomicSpans().stream())
                        .noneMatch(span -> covers(span, unit.unit()))).count();

        List<Localization> localizations = new ArrayList<>();
        long localized = 0;
        for (ScoredRank value : scored) {
            List<UnitRelation> directMatches = value.covered().stream()
                    .filter(unit -> unit.relation() == Relation.DIRECT_SUPPORT).toList();
            if (directMatches.isEmpty() || value.evidence().displaySpan() == null) continue;
            Localization best = directMatches.stream()
                    .map(unit -> localization(value.evidence().displaySpan(), unit.unit()))
                    .max(Comparator.comparingDouble(Localization::iou)).orElseThrow();
            localizations.add(best);
            if (best.fullyLocalized()) localized++;
        }
        long ambiguousSnippet = results.stream().filter(this::ambiguousSnippet).count();
        int atomicCount = results.stream().mapToInt(value -> value.atomicSpans().size()).sum();
        return new FinalDiagnostics(
                results.size(), atomicCount, duplicate,
                ratio(duplicate, atomicCount), repeatedGroups,
                (int) contaminated, ratio(contaminated, results.size()),
                (int) touched, (int) fragmented, ratio(fragmented, directUnits.size()),
                localizations.size(), (int) localized,
                average(localizations, Localization::precision),
                average(localizations, Localization::recall),
                average(localizations, Localization::iou),
                1.0d - average(localizations, Localization::precision),
                (int) ambiguousSnippet);
    }

    private java.util.Optional<UnitRelation> strongestCovered(ScoredRank value) {
        return value.covered().stream()
                .max(Comparator.comparingInt(unit -> unit.relation().gain()));
    }

    private int overlappingParents(
            ProductionV2ShadowAdapter.SourceSpan span,
            String owner,
            Map<String, SearchV3MinimalShadowGold.GoldParent> parents) {
        return (int) parents.values().stream()
                .filter(value -> owner.equals(value.userBundleId()))
                .filter(value -> sameLocation(span, value.span()))
                .filter(value -> positiveOverlap(
                        span.codePointStart(), span.codePointEnd(),
                        value.span().codePointStart(), value.span().codePointEnd()) > 0)
                .map(SearchV3MinimalShadowGold.GoldParent::parentId).distinct().count();
    }

    private boolean covers(
            ProductionV2ShadowAdapter.SourceSpan result,
            SearchV3MinimalShadowGold.GoldUnit unit) {
        if (!result.userBundleId().equals(unit.userBundleId())) return false;
        for (SearchV3MinimalShadowGold.GoldSpan span : unit.spans()) {
            if (!sameLocation(result, span)
                    || result.codePointStart() > span.codePointStart()
                    || result.codePointEnd() < span.codePointEnd()) {
                return false;
            }
            verifyContainedText(result, span);
        }
        return true;
    }

    private boolean overlapsAny(
            ProductionV2ShadowAdapter.SourceSpan result,
            SearchV3MinimalShadowGold.GoldUnit unit) {
        return result.userBundleId().equals(unit.userBundleId()) && unit.spans().stream()
                .anyMatch(span -> sameLocation(result, span)
                        && positiveOverlap(result.codePointStart(), result.codePointEnd(),
                                span.codePointStart(), span.codePointEnd()) > 0);
    }

    private boolean sameLocation(
            ProductionV2ShadowAdapter.SourceSpan result,
            SearchV3MinimalShadowGold.GoldSpan gold) {
        return result.documentId().equals(gold.documentId())
                && result.versionId().equals(gold.versionId())
                && Objects.equals(result.page(), gold.page());
    }

    private void verifyContainedText(
            ProductionV2ShadowAdapter.SourceSpan result,
            SearchV3MinimalShadowGold.GoldSpan gold) {
        if (gold.textSha256() == null || gold.textSha256().isBlank()
                || result.sourceText() == null) return;
        int expectedLength = result.codePointEnd() - result.codePointStart();
        if (result.sourceText().codePointCount(0, result.sourceText().length()) != expectedLength) {
            throw new IllegalStateException("output source text/code-point range mismatch");
        }
        int localStart = gold.codePointStart() - result.codePointStart();
        int localEnd = gold.codePointEnd() - result.codePointStart();
        int charStart = result.sourceText().offsetByCodePoints(0, localStart);
        int charEnd = result.sourceText().offsetByCodePoints(0, localEnd);
        String actual = SearchV3MinimalShadowDataset.sha256(result.sourceText().substring(charStart, charEnd));
        if (!gold.textSha256().equals(actual)) {
            throw new IllegalStateException("Gold/output contained text SHA mismatch");
        }
    }

    private Localization localization(
            ProductionV2ShadowAdapter.SourceSpan display,
            SearchV3MinimalShadowGold.GoldUnit unit) {
        if (!display.userBundleId().equals(unit.userBundleId())) {
            return Localization.ZERO;
        }
        List<Interval> goldIntervals = mergeIntervals(unit.spans().stream()
                .filter(span -> sameLocation(display, span))
                .map(span -> new Interval(span.codePointStart(), span.codePointEnd())).toList());
        long goldLength = goldIntervals.stream().mapToLong(Interval::length).sum();
        long displayLength = Math.max(0, display.codePointEnd() - display.codePointStart());
        long intersection = goldIntervals.stream().mapToLong(value -> positiveOverlap(
                display.codePointStart(), display.codePointEnd(), value.start(), value.end())).sum();
        long union = displayLength + goldLength - intersection;
        boolean full = !goldIntervals.isEmpty() && goldIntervals.stream().allMatch(value ->
                display.codePointStart() <= value.start() && display.codePointEnd() >= value.end());
        return new Localization(
                ratio(intersection, displayLength), ratio(intersection, goldLength),
                ratio(intersection, union), full);
    }

    private List<Interval> mergeIntervals(List<Interval> values) {
        List<Interval> sorted = values.stream().sorted(Comparator.comparingInt(Interval::start)).toList();
        List<Interval> merged = new ArrayList<>();
        for (Interval value : sorted) {
            if (merged.isEmpty() || value.start() > merged.get(merged.size() - 1).end()) {
                merged.add(value);
            }
            else {
                Interval previous = merged.remove(merged.size() - 1);
                merged.add(new Interval(previous.start(), Math.max(previous.end(), value.end())));
            }
        }
        return List.copyOf(merged);
    }

    private boolean ambiguousSnippet(RankedEvidence value) {
        if (value.snippet() == null || value.evidenceChunkSpan() == null
                || value.evidenceChunkSpan().sourceText() == null) return false;
        String source = value.evidenceChunkSpan().sourceText();
        String snippet = value.snippet();
        if (snippet.isEmpty()) return false;
        int occurrences = 0;
        int from = 0;
        while (from <= source.length() - snippet.length()) {
            int index = source.indexOf(snippet, from);
            if (index < 0) break;
            occurrences++;
            if (occurrences > 1) return true;
            from = index + Math.max(1, snippet.length());
        }
        return false;
    }

    private StageOutcome stageOutcome(
            RankingMetrics candidate,
            RankingMetrics finalRanking,
            boolean directPositive) {
        if (!directPositive) return StageOutcome.NOT_APPLICABLE;
        if (!candidate.directRecallAt20()) return StageOutcome.RETRIEVAL_MISS;
        if (!finalRanking.directRecallAt5()) return StageOutcome.FINAL_SELECTION_MISS;
        return StageOutcome.SUCCESS;
    }

    private PrimaryClassification classify(boolean v2Top1, boolean v3Top1) {
        if (v2Top1 && v3Top1) return PrimaryClassification.BOTH_CORRECT;
        if (v2Top1) return PrimaryClassification.V2_ONLY_CORRECT;
        if (v3Top1) return PrimaryClassification.V3_ONLY_CORRECT;
        return PrimaryClassification.BOTH_WRONG;
    }

    private RankComparison compareRanks(Integer v2, Integer v3) {
        if (v2 == null && v3 == null) return RankComparison.BOTH_MISS;
        if (v2 == null) return RankComparison.V3_RETRIEVAL_WIN;
        if (v3 == null) return RankComparison.V2_RETRIEVAL_WIN;
        if (v2.equals(v3)) return RankComparison.TIE;
        return v3 < v2 ? RankComparison.V3_BETTER : RankComparison.V2_BETTER;
    }

    private TypedQueryDiagnostic typedDiagnostic(
            GoldContext gold,
            MinimalV3ShadowAdapter.QueryRun run,
            PathEvaluation v2,
            PathEvaluation v3) {
        if (gold.query().typedExpectedState() == null) return null;
        String expected = gold.query().typedExpectedState();
        String predicted = run.state();
        String expectedMatchState = switch (expected) {
            case "FOUND" -> "SATISFIED";
            case "PARTIAL" -> "UNKNOWN";
            case "NONE" -> "CONTRADICTED";
            default -> throw new IllegalStateException("unknown typed Gold state: " + expected);
        };
        long assessed = run.finalResults().stream().filter(value -> value.matchState() != null).count();
        long correctEvidence = run.finalResults().stream()
                .filter(value -> expectedMatchState.equals(value.matchState()))
                .filter(value -> typedRelationCorrect(value, expected, gold)).count();
        List<MinimalV3ShadowAdapter.FinalResult> incorrect = run.finalResults().stream()
                .filter(value -> value.matchState() != null)
                .filter(value -> !expectedMatchState.equals(value.matchState())
                        || !typedRelationCorrect(value, expected, gold)).toList();
        long wrongValue = incorrect.stream().filter(value -> gold.query().categories().contains("numeric_quantity"))
                .filter(value -> !"UNKNOWN".equals(value.matchState())).count();
        long wrongDate = incorrect.stream().filter(value -> gold.query().categories().contains("date_range"))
                .filter(value -> !"UNKNOWN".equals(value.matchState())).count();
        long wrongVersion = incorrect.stream().filter(value -> gold.query().categories().contains("literal_identifier"))
                .filter(value -> !"UNKNOWN".equals(value.matchState())).count();
        long qualifierMismatch = incorrect.stream()
                .filter(value -> "UNKNOWN".equals(value.matchState())).count();
        return new TypedQueryDiagnostic(
                expected, predicted, expected.equals(predicted),
                "NONE".equals(predicted) && !"NONE".equals(expected),
                (int) assessed, (int) correctEvidence, ratio(correctEvidence, assessed),
                (int) wrongValue, (int) wrongDate, (int) wrongVersion, (int) qualifierMismatch,
                v2.finalRanking().top1(), v3.finalRanking().top1());
    }

    private boolean typedRelationCorrect(
            MinimalV3ShadowAdapter.FinalResult result,
            String expectedState,
            GoldContext gold) {
        Set<Relation> accepted = switch (expectedState) {
            case "FOUND" -> Set.of(Relation.DIRECT_SUPPORT);
            case "PARTIAL" -> Set.of(Relation.RELATED, Relation.INSUFFICIENT);
            case "NONE" -> Set.of(Relation.CONTRADICTS);
            default -> throw new IllegalStateException("unknown typed Gold state: " + expectedState);
        };
        return gold.units().stream()
                .filter(value -> accepted.contains(value.relation()))
                .anyMatch(value -> covers(result.span(), value.unit()));
    }

    private SemanticNegativeDiagnostic semanticNegativeDiagnostic(
            SearchV3MinimalShadowGold.GoldQuery gold,
            MinimalV3ShadowAdapter.QueryRun run,
            PathEvaluation v2,
            PathEvaluation v3) {
        if (gold.typedExpectedState() != null || !"NOT_SUPPORTED".equals(gold.answerability())) return null;
        return new SemanticNegativeDiagnostic(
                run.state(), "UNASSESSED".equals(run.state()),
                v2.finalDiagnostics().resultCount(), v3.finalDiagnostics().resultCount(),
                rankOneRelation(v2), rankOneRelation(v3));
    }

    private Relation rankOneRelation(PathEvaluation path) {
        return path.finalRanking().top1Relation();
    }

    private ComparisonAggregate aggregate(List<QueryEvaluation> values, Aggregation aggregation) {
        return new ComparisonAggregate(
                aggregation == Aggregation.USER_MACRO
                        ? (int) values.stream().map(QueryEvaluation::userBundleId).distinct().count()
                        : values.size(),
                values.size(),
                (int) values.stream().filter(QueryEvaluation::directPositive).count(),
                pathAggregate(values, QueryEvaluation::v2, aggregation),
                pathAggregate(values, QueryEvaluation::v3, aggregation),
                enumCounts(values, QueryEvaluation::classification, PrimaryClassification.class),
                enumCounts(values, QueryEvaluation::rankComparison, RankComparison.class));
    }

    private PathAggregate pathAggregate(
            List<QueryEvaluation> values,
            Function<QueryEvaluation, PathEvaluation> path,
            Aggregation aggregation) {
        return new PathAggregate(
                rankingAggregate(values, value -> path.apply(value).candidateRanking(), aggregation),
                rankingAggregate(values, value -> path.apply(value).finalRanking(), aggregation),
                new StructureAggregate(
                        mean(values, value -> path.apply(value).finalDiagnostics().duplicateRate(), value -> true,
                                aggregation),
                        mean(values, value -> path.apply(value).finalDiagnostics().crossParentContaminationRate(),
                                value -> true, aggregation),
                        mean(values, value -> path.apply(value).finalDiagnostics().fragmentationRate(),
                                QueryEvaluation::directPositive, aggregation),
                        mean(values, value -> path.apply(value).finalDiagnostics().localizationPrecision(),
                                value -> path.apply(value).finalDiagnostics().matchedDirectResultCount() > 0,
                                aggregation),
                        mean(values, value -> path.apply(value).finalDiagnostics().localizationRecall(),
                                value -> path.apply(value).finalDiagnostics().matchedDirectResultCount() > 0,
                                aggregation),
                        mean(values, value -> path.apply(value).finalDiagnostics().localizationIou(),
                                value -> path.apply(value).finalDiagnostics().matchedDirectResultCount() > 0,
                                aggregation),
                        mean(values, value -> path.apply(value).finalDiagnostics().localizationContamination(),
                                value -> path.apply(value).finalDiagnostics().matchedDirectResultCount() > 0,
                                aggregation)),
                mean(values, value -> path.apply(value).ownerLeakage() ? 1.0d : 0.0d,
                        value -> true, aggregation),
                mean(values, value -> path.apply(value).inactiveVersionLeakage() ? 1.0d : 0.0d,
                        value -> true, aggregation));
    }

    private RankingAggregate rankingAggregate(
            List<QueryEvaluation> values,
            Function<QueryEvaluation, RankingMetrics> metric,
            Aggregation aggregation) {
        Predicate<QueryEvaluation> applicable = value -> metric.apply(value).applicable();
        Predicate<QueryEvaluation> ndcgApplicable = value -> metric.apply(value).ndcgApplicable();
        return new RankingAggregate(
                (int) values.stream().filter(applicable).count(),
                (int) values.stream().filter(ndcgApplicable).count(),
                mean(values, value -> bool(metric.apply(value).top1()), applicable, aggregation),
                mean(values, value -> metric.apply(value).mrr(), applicable, aggregation),
                mean(values, value -> metric.apply(value).ndcgAt5(), ndcgApplicable, aggregation),
                mean(values, value -> bool(metric.apply(value).directRecallAt5()), applicable, aggregation),
                mean(values, value -> bool(metric.apply(value).directRecallAt20()), applicable, aggregation),
                mean(values, value -> metric.apply(value).directGroupCoverageAt5(), applicable, aggregation),
                mean(values, value -> metric.apply(value).directGroupCoverageAt20(), applicable, aggregation),
                mean(values, value -> metric.apply(value).directParentCoverageAt5(), applicable, aggregation),
                mean(values, value -> metric.apply(value).directParentCoverageAt20(), applicable, aggregation),
                mean(values, value -> bool(metric.apply(value).retrievalMiss()), applicable, aggregation));
    }

    private Map<String, ComparisonAggregate> sliced(
            List<QueryEvaluation> values,
            Function<QueryEvaluation, String> key) {
        Map<String, List<QueryEvaluation>> grouped = values.stream().collect(Collectors.groupingBy(
                key, LinkedHashMap::new, Collectors.toList()));
        Map<String, ComparisonAggregate> result = new LinkedHashMap<>();
        grouped.forEach((name, queries) -> result.put(name, aggregate(queries, Aggregation.QUERY_MICRO)));
        return Map.copyOf(result);
    }

    private double mean(
            List<QueryEvaluation> values,
            Function<QueryEvaluation, Double> metric,
            Predicate<QueryEvaluation> included,
            Aggregation aggregation) {
        if (aggregation == Aggregation.QUERY_MICRO) {
            return values.stream().filter(included).mapToDouble(value -> metric.apply(value)).average().orElse(0.0d);
        }
        Map<String, List<QueryEvaluation>> users = values.stream().filter(included)
                .collect(Collectors.groupingBy(QueryEvaluation::userBundleId));
        return users.values().stream()
                .mapToDouble(user -> user.stream().mapToDouble(value -> metric.apply(value)).average().orElse(0.0d))
                .average().orElse(0.0d);
    }

    private TypedAggregate typedAggregate(List<QueryEvaluation> values) {
        List<TypedQueryDiagnostic> typed = values.stream().map(QueryEvaluation::typed)
                .filter(Objects::nonNull).toList();
        Map<String, Map<String, Long>> confusion = new LinkedHashMap<>();
        typed.forEach(value -> confusion.computeIfAbsent(value.expectedState(), ignored -> new LinkedHashMap<>())
                .merge(value.predictedState(), 1L, Long::sum));
        Set<String> labels = Set.of("FOUND", "PARTIAL", "NONE");
        double macroF1 = labels.stream().mapToDouble(label -> f1(label, confusion)).average().orElse(0.0d);
        long assessed = typed.stream().mapToLong(TypedQueryDiagnostic::assessedEvidenceCount).sum();
        long correctEvidence = typed.stream().mapToLong(TypedQueryDiagnostic::correctEvidenceCount).sum();
        return new TypedAggregate(
                typed.size(),
                ratio(typed.stream().filter(TypedQueryDiagnostic::stateCorrect).count(), typed.size()),
                macroF1,
                immutableNested(confusion),
                typed.stream().filter(TypedQueryDiagnostic::falseNone).count(),
                ratio(correctEvidence, assessed),
                typed.stream().mapToLong(TypedQueryDiagnostic::wrongValueCount).sum(),
                typed.stream().mapToLong(TypedQueryDiagnostic::wrongDateCount).sum(),
                typed.stream().mapToLong(TypedQueryDiagnostic::wrongVersionCount).sum(),
                typed.stream().mapToLong(TypedQueryDiagnostic::qualifierMismatchCount).sum());
    }

    private double f1(String label, Map<String, Map<String, Long>> confusion) {
        long tp = confusion.getOrDefault(label, Map.of()).getOrDefault(label, 0L);
        long fp = confusion.entrySet().stream().filter(value -> !value.getKey().equals(label))
                .mapToLong(value -> value.getValue().getOrDefault(label, 0L)).sum();
        long fn = confusion.getOrDefault(label, Map.of()).entrySet().stream()
                .filter(value -> !value.getKey().equals(label)).mapToLong(Map.Entry::getValue).sum();
        return tp == 0 ? 0.0d : (2.0d * tp) / (2.0d * tp + fp + fn);
    }

    private SemanticAggregate semanticAggregate(List<QueryEvaluation> values) {
        List<QueryEvaluation> semanticQueries = values.stream()
                .filter(value -> value.typed() == null).toList();
        List<SemanticNegativeDiagnostic> negatives = values.stream().map(QueryEvaluation::semanticNegative)
                .filter(Objects::nonNull).toList();
        return new SemanticAggregate(
                semanticQueries.size(),
                negatives.size(),
                semanticQueries.stream().filter(value -> !"UNASSESSED".equals(value.v3State())).count(),
                negatives.stream().mapToInt(SemanticNegativeDiagnostic::v2ResultCount).average().orElse(0.0d),
                negatives.stream().mapToInt(SemanticNegativeDiagnostic::v3ResultCount).average().orElse(0.0d),
                enumCounts(negatives, SemanticNegativeDiagnostic::v2RankOneRelation, Relation.class),
                enumCounts(negatives, SemanticNegativeDiagnostic::v3RankOneRelation, Relation.class));
    }

    private DecisionResult decision(
            SearchV3MinimalShadowFreeze.OutputArtifact output,
            List<QueryEvaluation> queries,
            ComparisonAggregate queryMicro,
            ComparisonAggregate userMacro,
            TypedAggregate typed,
            SemanticAggregate semantic) {
        boolean invariantViolation = queries.stream().anyMatch(value ->
                value.v2().ownerLeakage() || value.v2().inactiveVersionLeakage()
                        || value.v3().ownerLeakage())
                || semantic.v3AssessedStateViolationCount() > 0;
        if (invariantViolation) {
            return new DecisionResult(Decision.INVALID_COMPARISON, "OWNER_VERSION_OR_STATE_INVARIANT");
        }

        double candidateDelta = queryMicro.v3().candidate().directRecallAt20()
                - queryMicro.v2().candidate().directRecallAt20();
        double top1Delta = userMacro.v3().finalRanking().top1()
                - userMacro.v2().finalRanking().top1();
        double mrrDelta = userMacro.v3().finalRanking().mrr()
                - userMacro.v2().finalRanking().mrr();
        double ndcgDelta = userMacro.v3().finalRanking().ndcgAt5()
                - userMacro.v2().finalRanking().ndcgAt5();
        double epsilon = 1.0e-12d;
        boolean candidateRegression = candidateDelta < -epsilon;
        boolean rankingRegression = Math.min(top1Delta, Math.min(mrrDelta, ndcgDelta)) < -epsilon;
        boolean structuralGain = userMacro.v3().finalStructure().crossParentContaminationRate()
                        < userMacro.v2().finalStructure().crossParentContaminationRate()
                || userMacro.v3().finalStructure().fragmentationRate()
                        < userMacro.v2().finalStructure().fragmentationRate()
                || userMacro.v3().finalStructure().localizationIou()
                        > userMacro.v2().finalStructure().localizationIou();
        long typedV3Wins = queries.stream().filter(value -> value.typed() != null)
                .filter(value -> value.classification() == PrimaryClassification.V3_ONLY_CORRECT).count();
        long typedV2Wins = queries.stream().filter(value -> value.typed() != null)
                .filter(value -> value.classification() == PrimaryClassification.V2_ONLY_CORRECT).count();
        boolean typedGain = typedV3Wins > typedV2Wins;
        boolean operationalRegression = output.v3Indexing().embeddingCount() > output.v2Indexing().embeddingCount()
                && output.v3Indexing().indexingWallMs() > output.v2Indexing().indexingWallMs();

        if (!candidateRegression && !rankingRegression
                && (structuralGain || typedGain)) {
            return new DecisionResult(
                    Decision.MINIMAL_V3_AHEAD,
                    biggestRemainingBottleneck(queries, queryMicro, typed));
        }
        if (structuralGain || typedGain) {
            return new DecisionResult(
                    Decision.MIXED_NEEDS_NEXT_CAPABILITY,
                    biggestRemainingBottleneck(queries, queryMicro, typed));
        }
        if (candidateRegression || rankingRegression || operationalRegression) {
            String bottleneck = candidateRegression
                    ? "V3_CANDIDATE_DIRECT_RECALL_AT_20"
                    : rankingRegression ? "V3_USER_MACRO_FINAL_RANKING" : "V3_OPERATIONAL_COST";
            return new DecisionResult(Decision.CURRENT_V2_AHEAD, bottleneck);
        }
        return new DecisionResult(
                Decision.MIXED_NEEDS_NEXT_CAPABILITY,
                biggestRemainingBottleneck(queries, queryMicro, typed));
    }

    private String biggestRemainingBottleneck(
            List<QueryEvaluation> queries,
            ComparisonAggregate queryMicro,
            TypedAggregate typed) {
        double retrievalMiss = queryMicro.v3().candidate().retrievalMissRate();
        double selectionMiss = Math.max(0.0d,
                queryMicro.v3().candidate().directRecallAt20()
                        - queryMicro.v3().finalRanking().directRecallAt5());
        double bothWrong = ratio(
                queries.stream().filter(value -> value.classification() == PrimaryClassification.BOTH_WRONG).count(),
                queries.stream().filter(QueryEvaluation::directPositive).count());
        double typedError = typed.queryCount() == 0 ? 0.0d : 1.0d - typed.stateAccuracy();
        Map<String, Double> candidates = new LinkedHashMap<>();
        candidates.put("V3_CANDIDATE_RETRIEVAL_MISS", retrievalMiss);
        candidates.put("V3_FINAL_SELECTION_MISS", selectionMiss);
        candidates.put("SHARED_SEMANTIC_RANK_ONE_MISS", bothWrong);
        candidates.put("V3_TYPED_STATE_ERROR", typedError);
        candidates.put("V3_CROSS_PARENT_CONTAMINATION",
                queryMicro.v3().finalStructure().crossParentContaminationRate());
        candidates.put("V3_RESULT_VISIBLE_FRAGMENTATION",
                queryMicro.v3().finalStructure().fragmentationRate());
        Map.Entry<String, Double> biggest = candidates.entrySet().stream()
                .max(Map.Entry.comparingByValue()).orElseThrow();
        return biggest.getValue() == 0.0d ? "NO_MATERIAL_BOTTLENECK_OBSERVED" : biggest.getKey();
    }

    private <T, E extends Enum<E>> Map<E, Long> enumCounts(
            List<T> values,
            Function<T, E> key,
            Class<E> enumType) {
        Map<E, Long> result = new EnumMap<>(enumType);
        for (E value : enumType.getEnumConstants()) result.put(value, 0L);
        values.forEach(value -> result.merge(key.apply(value), 1L, Long::sum));
        return Map.copyOf(result);
    }

    private Map<String, Map<String, Long>> immutableNested(Map<String, Map<String, Long>> values) {
        Map<String, Map<String, Long>> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(key, Map.copyOf(value)));
        return Map.copyOf(result);
    }

    private static double average(List<Localization> values, Function<Localization, Double> metric) {
        return values.stream().mapToDouble(value -> metric.apply(value)).average().orElse(0.0d);
    }

    private static long positiveOverlap(int leftStart, int leftEnd, int rightStart, int rightEnd) {
        return Math.max(0, Math.min(leftEnd, rightEnd) - Math.max(leftStart, rightStart));
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0d);
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0d : (double) numerator / denominator;
    }

    private static double bool(boolean value) {
        return value ? 1.0d : 0.0d;
    }

    enum Relation {
        DIRECT_SUPPORT(3),
        RELATED(2),
        CONTRADICTS(1),
        INSUFFICIENT(0),
        UNJUDGED(0);

        private final int gain;

        Relation(int gain) {
            this.gain = gain;
        }

        int gain() {
            return gain;
        }

        static Relation parse(String value) {
            if (value == null) return UNJUDGED;
            String normalized = value.trim().toUpperCase(Locale.ROOT);
            if ("DIRECT".equals(normalized)) return DIRECT_SUPPORT;
            try {
                return Relation.valueOf(normalized);
            }
            catch (IllegalArgumentException exception) {
                throw new IllegalStateException("unknown Gold relation: " + value, exception);
            }
        }
    }

    enum PrimaryClassification {
        BOTH_CORRECT,
        V2_ONLY_CORRECT,
        V3_ONLY_CORRECT,
        BOTH_WRONG,
        NOT_APPLICABLE
    }

    enum RankComparison {
        V3_BETTER,
        V2_BETTER,
        TIE,
        V3_RETRIEVAL_WIN,
        V2_RETRIEVAL_WIN,
        BOTH_MISS,
        NOT_APPLICABLE
    }

    enum StageOutcome {
        SUCCESS,
        RETRIEVAL_MISS,
        FINAL_SELECTION_MISS,
        NOT_APPLICABLE
    }

    enum Decision {
        MINIMAL_V3_AHEAD,
        MIXED_NEEDS_NEXT_CAPABILITY,
        CURRENT_V2_AHEAD,
        INVALID_COMPARISON
    }

    enum Aggregation {
        QUERY_MICRO,
        USER_MACRO
    }

    record EvaluationReport(
            Decision decision,
            String biggestBottleneck,
            DedupAudit dedup,
            List<QueryEvaluation> queries,
            IndexStructureAudit v2IndexStructure,
            IndexStructureAudit v3IndexStructure,
            ComparisonAggregate queryMicro,
            ComparisonAggregate userMacro,
            Map<String, ComparisonAggregate> professionSlices,
            Map<String, ComparisonAggregate> languageSlices,
            TypedAggregate typed,
            SemanticAggregate semantic,
            OperationAggregate operation) {
    }

    record OperationAggregate(
            SearchV3MinimalShadowFreeze.IndexingStats v2Indexing,
            SearchV3MinimalShadowFreeze.IndexingStats v3Indexing,
            double v2QueryP50Ms,
            double v2QueryP95Ms,
            double v3QueryP50Ms,
            double v3QueryP95Ms) {
    }

    record IndexStructureAudit(
            long indexUnitCount,
            long atomicSpanCount,
            long directGoldUnitCount,
            long touchedDirectGoldUnitCount,
            long fragmentedDirectGoldUnitCount,
            double fragmentationRate,
            long crossParentContaminatedUnitCount,
            double crossParentContaminationRate,
            long duplicateAtomicSpanCount,
            double duplicateAtomicSpanRate) {
    }

    private record DecisionResult(Decision decision, String biggestBottleneck) {
    }

    record InventoryContract(
            String label,
            int expectedQueryCount,
            int expectedUserCount,
            int expectedDirectPositiveCount,
            int expectedNotSupportedCount,
            Map<String, Long> expectedSuiteCounts) {

        InventoryContract {
            if (label == null || label.isBlank()) {
                throw new IllegalArgumentException("inventory label must not be blank");
            }
            if (expectedQueryCount <= 0 || expectedUserCount <= 0) {
                throw new IllegalArgumentException("query and user counts must be positive");
            }
            if (expectedDirectPositiveCount < 0
                    || expectedDirectPositiveCount > expectedQueryCount
                    || expectedNotSupportedCount < 0
                    || expectedNotSupportedCount > expectedQueryCount) {
                throw new IllegalArgumentException("Gold counts must be within the query inventory");
            }
            Objects.requireNonNull(expectedSuiteCounts, "expectedSuiteCounts");
            if (expectedSuiteCounts.isEmpty()
                    || expectedSuiteCounts.entrySet().stream().anyMatch(entry ->
                            entry.getKey() == null || entry.getKey().isBlank()
                                    || entry.getValue() == null || entry.getValue() < 0L)
                    || expectedSuiteCounts.values().stream().mapToLong(Long::longValue).sum()
                            != expectedQueryCount) {
                throw new IllegalArgumentException("suite counts must be non-negative and sum to query count");
            }
            expectedSuiteCounts = Map.copyOf(expectedSuiteCounts);
        }

        private static InventoryContract prz032() {
            return new InventoryContract(
                    "PRZ-032",
                    EXPECTED_QUERY_COUNT,
                    EXPECTED_USER_COUNT,
                    EXPECTED_DIRECT_POSITIVE_COUNT,
                    EXPECTED_NOT_SUPPORTED_COUNT,
                    EXPECTED_SUITE_COUNTS);
        }
    }

    /** Canonical key is owner + NUL + NFKC/lowercase/whitespace-collapsed query text. */
    record DedupAudit(
            int rawLineageCount,
            int canonicalQueryCount,
            int frozenExecutionKeyCount,
            Map<String, List<String>> collisions,
            Map<String, Long> suiteCounts) {
    }

    record QueryEvaluation(
            String suite,
            String datasetVersion,
            String split,
            String queryId,
            String userBundleId,
            String professionGroup,
            String language,
            String answerability,
            List<String> categories,
            boolean directPositive,
            String v3State,
            PathEvaluation v2,
            PathEvaluation v3,
            PrimaryClassification classification,
            RankComparison rankComparison,
            boolean sameGoldDifferentRank,
            boolean structuralContaminationV2,
            boolean typedAdvantageV3,
            TypedQueryDiagnostic typed,
            SemanticNegativeDiagnostic semanticNegative) {

        QueryEvaluation {
            categories = categories == null ? List.of() : List.copyOf(categories);
        }
    }

    record PathEvaluation(
            RankingMetrics candidateRanking,
            RankingMetrics finalRanking,
            FinalDiagnostics finalDiagnostics,
            StageOutcome stageOutcome,
            boolean ownerLeakage,
            boolean inactiveVersionLeakage) {
    }

    /** Recall is aspect/group completeness; Top1/MRR use the first DIRECT result. */
    record RankingMetrics(
            boolean applicable,
            boolean directRecallAt5,
            boolean directRecallAt20,
            boolean top1,
            double mrr,
            boolean ndcgApplicable,
            double ndcgAt5,
            double directGroupCoverageAt5,
            double directGroupCoverageAt20,
            double directParentCoverageAt5,
            double directParentCoverageAt20,
            Integer firstDirectRank,
            boolean retrievalMiss,
            int judgedAt5,
            int unjudgedAt5,
            Relation top1Relation) {
    }

    /**
     * Fragmentation is result-visible: a DIRECT unit is touched by final output but no one atomic
     * final span contains the complete unit. It is not an index-wide fragmentation measurement.
     */
    record FinalDiagnostics(
            int resultCount,
            int atomicSpanCount,
            int duplicateSpanCount,
            double duplicateRate,
            int repeatedGoldGroupCount,
            int crossParentContaminatedResultCount,
            double crossParentContaminationRate,
            int touchedDirectUnitCount,
            int fragmentedDirectUnitCount,
            double fragmentationRate,
            int matchedDirectResultCount,
            int fullyLocalizedDirectResultCount,
            double localizationPrecision,
            double localizationRecall,
            double localizationIou,
            double localizationContamination,
            int ambiguousSnippetProjectionCount) {
    }

    record TypedQueryDiagnostic(
            String expectedState,
            String predictedState,
            boolean stateCorrect,
            boolean falseNone,
            int assessedEvidenceCount,
            int correctEvidenceCount,
            double constraintCorrectEvidencePrecision,
            int wrongValueCount,
            int wrongDateCount,
            int wrongVersionCount,
            int qualifierMismatchCount,
            boolean v2DirectTop1,
            boolean v3DirectTop1) {
    }

    record SemanticNegativeDiagnostic(
            String v3State,
            boolean v3Unassessed,
            int v2ResultCount,
            int v3ResultCount,
            Relation v2RankOneRelation,
            Relation v3RankOneRelation) {
    }

    record ComparisonAggregate(
            int aggregationUnitCount,
            int queryCount,
            int directPositiveQueryCount,
            PathAggregate v2,
            PathAggregate v3,
            Map<PrimaryClassification, Long> classifications,
            Map<RankComparison, Long> rankComparisons) {
    }

    record PathAggregate(
            RankingAggregate candidate,
            RankingAggregate finalRanking,
            StructureAggregate finalStructure,
            double ownerLeakageQueryRate,
            double inactiveVersionLeakageQueryRate) {
    }

    record RankingAggregate(
            int applicableQueryCount,
            int ndcgApplicableQueryCount,
            double top1,
            double mrr,
            double ndcgAt5,
            double directRecallAt5,
            double directRecallAt20,
            double directGroupCoverageAt5,
            double directGroupCoverageAt20,
            double directParentCoverageAt5,
            double directParentCoverageAt20,
            double retrievalMissRate) {
    }

    record StructureAggregate(
            double duplicateRate,
            double crossParentContaminationRate,
            double fragmentationRate,
            double localizationPrecision,
            double localizationRecall,
            double localizationIou,
            double localizationContamination) {
    }

    record TypedAggregate(
            int queryCount,
            double stateAccuracy,
            double stateMacroF1,
            Map<String, Map<String, Long>> stateConfusion,
            long falseNoneCount,
            double constraintCorrectEvidencePrecision,
            long wrongValueCount,
            long wrongDateCount,
            long wrongVersionCount,
            long qualifierMismatchCount) {
    }

    record SemanticAggregate(
            int semanticQueryCount,
            int notSupportedQueryCount,
            long v3AssessedStateViolationCount,
            double v2AverageResultCount,
            double v3AverageResultCount,
            Map<Relation, Long> v2RankOneRelations,
            Map<Relation, Long> v3RankOneRelations) {
    }

    private record GoldContext(
            SearchV3MinimalShadowGold.GoldQuery query,
            List<UnitRelation> units) {
    }

    private record UnitRelation(SearchV3MinimalShadowGold.GoldUnit unit, Relation relation) {
    }

    private record RankedEvidence(
            int rank,
            String id,
            List<ProductionV2ShadowAdapter.SourceSpan> atomicSpans,
            ProductionV2ShadowAdapter.SourceSpan displaySpan,
            String snippet,
            ProductionV2ShadowAdapter.SourceSpan evidenceChunkSpan) {

        RankedEvidence {
            atomicSpans = List.copyOf(atomicSpans);
        }
    }

    private record ScoredRank(
            RankedEvidence evidence,
            Relation relation,
            List<UnitRelation> covered) {
    }

    private record Coverage(double group, double parent) {
    }

    private record Ndcg(boolean applicable, double value) {
    }

    private record SpanKey(
            String owner,
            String documentId,
            String versionId,
            Integer page,
            int start,
            int end) {

        static SpanKey from(ProductionV2ShadowAdapter.SourceSpan value) {
            return new SpanKey(
                    value.userBundleId(), value.documentId(), value.versionId(), value.page(),
                    value.codePointStart(), value.codePointEnd());
        }
    }

    private record Interval(int start, int end) {
        long length() {
            return end - start;
        }
    }

    private record Localization(double precision, double recall, double iou, boolean fullyLocalized) {
        private static final Localization ZERO = new Localization(0.0d, 0.0d, 0.0d, false);
    }
}
