package com.prizm.search.evaluation.searchv3.structural;

import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.GoldAnswerability.NOT_SUPPORTED;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.GoldAnswerability.PARTIALLY_SUPPORTED;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.GoldAnswerability.SUPPORTED;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleRelation.CONTRADICTS;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleRelation.DIRECT_SUPPORT;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleRelation.RELATED;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.DirectnessRelation.DIRECT_MATCH;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.DirectnessRelation.INSUFFICIENT;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.DirectnessRelation.QUERY_CONFLICT;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.DirectnessRelation.RELATED_CONTEXT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.CandidateProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvaluationTrack;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvidenceChildProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.FreezeInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.GoldJoined;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.PhaseGuard;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.QueryProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.ExpectedGoldEvidence;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.GoldAspect;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.GoldAspectExpression;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleCandidate;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleRelation;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.QueryGold;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.AggregateMetrics;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.ByteMeasurement;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.CandidatePrediction;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.CaptureMetric;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.CaptureMetrics;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.ComparatorStatus;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.CostMetrics;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.DirectnessRelation;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.DirectnessRun;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.GateAssessment;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.GateStatus;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.InferenceCostObservation;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.MetricStatus;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.QueryMetrics;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.QueryPredictions;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.ResourceCost;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.SafetyInputs;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class SearchV3SemanticDirectnessEvaluatorTest {

    private final SearchV3SemanticDirectnessEvaluator evaluator =
            new SearchV3SemanticDirectnessEvaluator();

    @Test
    void evaluatesD0D2AndO10WithJudgedMetricsSlicesRetentionRecoveryAndNoSupportDiagnostics() {
        List<QueryProjection> queries = List.of(
                query("Q1", "U1", candidates("Q1", "U1", 4)),
                query("Q2", "U2", candidates("Q2", "U2", 2)),
                query("Q3", "U3", candidates("Q3", "U3", 2)),
                query("Q4", "U4", candidates("Q4", "U4", 2)),
                query("Q5", "U5", candidates("Q5", "U5", 2)));
        List<QueryGold> gold = List.of(
                gold("Q1", "U1", "BACKEND", "KO", List.of("completion_state", "other_actor"),
                        SUPPORTED, Map.of(
                                "Q1-C1", RELATED,
                                "Q1-C2", DIRECT_SUPPORT,
                                "Q1-C3", CONTRADICTS,
                                "Q1-C4", OracleRelation.INSUFFICIENT)),
                gold("Q2", "U2", "DESIGN_PRODUCT", "EN", List.of("abstract_competency"),
                        SUPPORTED, Map.of("Q2-C1", DIRECT_SUPPORT)),
                gold("Q3", "U3", "MARKETING_SALES", "KO_EN_MIXED", List.of("negation"),
                        NOT_SUPPORTED, Map.of("Q3-C1", CONTRADICTS)),
                gold("Q4", "U4", "PLANNING", "KO", List.of("semantic_paraphrase"),
                        PARTIALLY_SUPPORTED, Map.of("Q4-C1", RELATED, "Q4-C2", DIRECT_SUPPORT)),
                gold("Q5", "U5", "NON_DEVELOPMENT_GENERAL", "EN", List.of("semantic_paraphrase"),
                        SUPPORTED, Map.of("Q5-C2", DIRECT_SUPPORT)));
        List<QueryPredictions> predictions = List.of(
                predictions("Q1", RELATED_CONTEXT, DIRECT_MATCH, QUERY_CONFLICT, INSUFFICIENT),
                predictions("Q2", DIRECT_MATCH, INSUFFICIENT),
                predictions("Q3", QUERY_CONFLICT, DIRECT_MATCH),
                predictions("Q4", RELATED_CONTEXT, DIRECT_MATCH),
                predictions("Q5", RELATED_CONTEXT, DIRECT_MATCH));

        DirectnessRun run = evaluator.evaluate(
                joined(queries, gold),
                Set.of("Q1", "Q2", "Q3", "Q4", "Q5"),
                predictions);
        AggregateMetrics metrics = run.summary().aggregate();

        assertThat(metrics.queryCount()).isEqualTo(5);
        assertThat(metrics.directPositiveQueryCount()).isEqualTo(4);
        assertThat(metrics.d0().top1()).isEqualTo(0.25d);
        assertThat(metrics.d2().top1()).isEqualTo(1.0d);
        assertThat(metrics.o10().top1()).isEqualTo(1.0d);
        assertThat(metrics.d0().directRecallAt20()).isEqualTo(1.0d);
        assertThat(metrics.d2().directRecallAt20()).isEqualTo(1.0d);
        assertThat(metrics.winCount()).isEqualTo(3);
        assertThat(metrics.lossCount()).isZero();
        assertThat(metrics.tieCount()).isEqualTo(1);
        assertThat(metrics.rank1Retention().numerator()).isEqualTo(1);
        assertThat(metrics.rank1Retention().denominator()).isEqualTo(1);
        assertThat(metrics.rank1Retention().value()).isEqualTo(1.0d);
        assertThat(metrics.recoveredBundleCount()).isEqualTo(3);
        assertThat(metrics.userMacro().d0().top1()).isEqualTo(0.25d);
        assertThat(metrics.userMacro().d2().top1()).isEqualTo(1.0d);
        assertThat(metrics.captures().userMacroTop1().ratio()).isEqualTo(1.0d);

        assertThat(metrics.relations().predictedPairCount()).isEqualTo(12);
        assertThat(metrics.relations().judgedPairCount()).isEqualTo(9);
        assertThat(metrics.relations().unjudgedPairCount()).isEqualTo(3);
        assertThat(metrics.relations().judgedAccuracy()).isEqualTo(1.0d);
        assertThat(metrics.relations().macroF1()).isEqualTo(1.0d);
        assertThat(metrics.relations().direct().precision()).isEqualTo(1.0d);
        assertThat(metrics.relations().confusion().get(DirectnessRelation.QUERY_CONFLICT)
                .get(DirectnessRelation.QUERY_CONFLICT)).isEqualTo(2);

        assertThat(metrics.noSupport().queryCount()).isEqualTo(1);
        assertThat(metrics.noSupport().denseTop1PredictedDirectCount()).isZero();
        assertThat(metrics.noSupport().finalTop1PredictedDirectCount()).isEqualTo(1);
        assertThat(metrics.noSupport().changeComparator()).isEqualTo(ComparatorStatus.NOT_APPLICABLE);
        assertThat(run.summary().professionSlices()).containsKeys(
                "BACKEND", "DESIGN_PRODUCT", "MARKETING_SALES", "PLANNING");
        assertThat(run.summary().languageSlices()).containsKeys("KO", "EN", "KO_EN_MIXED");
        assertThat(run.summary().categorySlices().get("semantic_paraphrase").queryCount()).isEqualTo(2);
        assertThat(run.summary().focusSlices().get("COMPLETION").queryCount()).isEqualTo(1);
        assertThat(run.summary().focusSlices().get("NEGATION").queryCount()).isEqualTo(1);

        GateAssessment gate = SearchV3SemanticDirectnessEvaluator.assessGate(
                metrics, new SafetyInputs(true, true, true, true, true));
        assertThat(gate.status()).isEqualTo(GateStatus.PASS);
        assertThat(gate.conditionAOracleCapture()).isTrue();
        assertThat(gate.conditionBRecoveredBundles()).isTrue();
        assertThat(gate.conditionCNoSupportComparator()).isEqualTo(ComparatorStatus.NOT_APPLICABLE);
    }

    @Test
    void distinguishesWinLossTieAndRetrievalMissWithoutChangingTheCandidateSet() {
        List<QueryProjection> queries = List.of(
                query("Q1", "U1", candidates("Q1", "U1", 2)),
                query("Q2", "U2", candidates("Q2", "U2", 2)),
                query("Q3", "U3", candidates("Q3", "U3", 2)),
                query("Q4", "U4", candidates("Q4", "U4", 2)));
        List<QueryGold> gold = List.of(
                gold("Q1", "U1", "BACKEND", "KO", List.of(), SUPPORTED,
                        Map.of("Q1-C2", DIRECT_SUPPORT)),
                gold("Q2", "U2", "DESIGN_PRODUCT", "EN", List.of(), SUPPORTED,
                        Map.of("Q2-C1", DIRECT_SUPPORT)),
                gold("Q3", "U3", "PLANNING", "KO", List.of(), SUPPORTED,
                        Map.of("Q3-C1", DIRECT_SUPPORT)),
                gold("Q4", "U4", "MARKETING_SALES", "EN", List.of(), SUPPORTED,
                        Map.of("Q4-MISSING", DIRECT_SUPPORT)));
        List<QueryPredictions> predictions = List.of(
                predictions("Q1", RELATED_CONTEXT, DIRECT_MATCH),
                predictions("Q2", RELATED_CONTEXT, DIRECT_MATCH),
                predictions("Q3", DIRECT_MATCH, RELATED_CONTEXT),
                predictions("Q4", INSUFFICIENT, RELATED_CONTEXT));

        AggregateMetrics metrics = evaluator.evaluate(
                        joined(queries, gold), Set.of("Q1", "Q2", "Q3", "Q4"), predictions)
                .summary().aggregate();

        assertThat(metrics.winCount()).isEqualTo(1);
        assertThat(metrics.lossCount()).isEqualTo(1);
        assertThat(metrics.tieCount()).isEqualTo(1);
        assertThat(metrics.retrievalMissCount()).isEqualTo(1);
    }

    @Test
    void D2UsesFrozenRelationPriorityStableWithinBucketsAndPreservesDenseTail() {
        QueryProjection query = query("Q1", "U1", candidates("Q1", "U1", 12));
        QueryGold gold = gold(
                "Q1", "U1", "BACKEND", "KO", List.of("semantic_paraphrase"),
                SUPPORTED, Map.of("Q1-C2", DIRECT_SUPPORT));
        QueryPredictions predictions = predictions(
                "Q1",
                RELATED_CONTEXT,
                DIRECT_MATCH,
                DIRECT_MATCH,
                QUERY_CONFLICT,
                RELATED_CONTEXT,
                INSUFFICIENT,
                QUERY_CONFLICT,
                RELATED_CONTEXT,
                INSUFFICIENT,
                DIRECT_MATCH);

        QueryMetrics result = evaluator.evaluate(
                        joined(List.of(query), List.of(gold)), Set.of("Q1"), List.of(predictions))
                .queries().get(0);

        assertThat(result.d2Ranking()).extracting(value -> value.candidate().candidateId())
                .containsExactly(
                        "Q1-P2", "Q1-P3", "Q1-P10",
                        "Q1-P1", "Q1-P5", "Q1-P8",
                        "Q1-P4", "Q1-P7",
                        "Q1-P6", "Q1-P9",
                        "Q1-P11", "Q1-P12");
    }

    @Test
    void O10OnlyPartitionsTop10AndLeavesGoldDirectAtRank11InTheDenseTail() {
        QueryProjection query = query("Q1", "U1", candidates("Q1", "U1", 12));
        QueryGold gold = gold(
                "Q1", "U1", "BACKEND", "KO", List.of("semantic_paraphrase"),
                SUPPORTED, Map.of("Q1-C11", DIRECT_SUPPORT));
        DirectnessRelation[] labels = IntStream.range(0, 10)
                .mapToObj(ignored -> INSUFFICIENT)
                .toArray(DirectnessRelation[]::new);

        DirectnessRun run = evaluator.evaluate(
                joined(List.of(query), List.of(gold)),
                Set.of("Q1"),
                List.of(predictions("Q1", labels)));
        QueryMetrics result = run.queries().get(0);

        assertThat(result.d0FirstDirectRank()).isEqualTo(11);
        assertThat(result.d2FirstDirectRank()).isEqualTo(11);
        assertThat(result.o10FirstDirectRank()).isEqualTo(11);
        assertThat(result.o10().top1()).isFalse();
        assertThat(result.o10().mrr()).isEqualTo(1.0d / 11.0d);
        assertThat(result.o10Ranking()).extracting(value -> value.candidate().candidateId())
                .containsExactlyElementsOf(IntStream.rangeClosed(1, 12)
                        .mapToObj(index -> "Q1-P" + index).toList());
        assertThat(result.o10Ranking().subList(10, 12))
                .extracting(value -> value.candidate().candidateId())
                .containsExactly("Q1-P11", "Q1-P12");
    }

    @Test
    void predictionsMustExactlyCoverTheFrozenDensePrefix() {
        QueryProjection query = query("Q1", "U1", candidates("Q1", "U1", 2));
        QueryGold gold = gold(
                "Q1", "U1", "BACKEND", "KO", List.of("semantic_paraphrase"),
                SUPPORTED, Map.of("Q1-C1", DIRECT_SUPPORT));
        QueryPredictions missing = new QueryPredictions(
                "Q1", List.of(new CandidatePrediction(
                        1, "Q1-P1", DIRECT_MATCH)));

        assertThatThrownBy(() -> evaluator.evaluate(
                joined(List.of(query), List.of(gold)), Set.of("Q1"), List.of(missing)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prediction count");
    }

    @Test
    void captureIsNotApplicableWithoutHeadroomAndNeverClampsRegressionsOrOvershoot() {
        CaptureMetric noHeadroom = SearchV3SemanticDirectnessEvaluator.capture(1.0d, 0.5d, 1.0d);
        CaptureMetric regression = SearchV3SemanticDirectnessEvaluator.capture(0.5d, 0.25d, 1.0d);
        CaptureMetric overshoot = SearchV3SemanticDirectnessEvaluator.capture(0.5d, 1.25d, 1.0d);

        assertThat(noHeadroom.status()).isEqualTo(MetricStatus.NOT_APPLICABLE);
        assertThat(noHeadroom.ratio()).isNull();
        assertThat(regression.ratio()).isEqualTo(-0.5d);
        assertThat(overshoot.ratio()).isEqualTo(1.5d);
    }

    @Test
    void conditionCNotApplicableCannotMakeTheGatePass() {
        DirectnessRun run = passingRun();
        AggregateMetrics base = run.summary().aggregate();
        CaptureMetric insufficientA = new CaptureMetric(
                0.5d, 0.6d, 1.0d, MetricStatus.APPLICABLE, 0.2d);
        CaptureMetrics captures = new CaptureMetrics(
                base.captures().queryMicroTop1(),
                base.captures().queryMicroMrr(),
                base.captures().queryMicroNdcgAt5(),
                insufficientA,
                base.captures().userMacroMrr());
        AggregateMetrics withoutAOrB = new AggregateMetrics(
                base.queryCount(), base.directPositiveQueryCount(), base.d0(), base.d2(), base.o10(),
                base.userMacro(), base.winCount(), base.lossCount(), base.tieCount(),
                base.retrievalMissCount(), base.rank1Retention(), base.recoveredQueryCount(), 0,
                captures, base.relations(), base.noSupport());

        GateAssessment gate = SearchV3SemanticDirectnessEvaluator.assessGate(
                withoutAOrB, new SafetyInputs(true, true, true, true, true));

        assertThat(gate.status()).isEqualTo(GateStatus.FAIL);
        assertThat(gate.conditionAOracleCapture()).isFalse();
        assertThat(gate.conditionBRecoveredBundles()).isFalse();
        assertThat(gate.conditionCNoSupportComparator()).isEqualTo(ComparatorStatus.NOT_APPLICABLE);
    }

    @Test
    void summarizesQueryPairLatencyBytesAndResourceMeasurements() {
        ByteMeasurement unavailable = new ByteMeasurement(false, 0, "NOT_AVAILABLE");
        ResourceCost resources = new ResourceCost(
                1024,
                new ByteMeasurement(true, 100, "PROCESS_RSS"),
                new ByteMeasurement(true, 140, "PROCESS_RSS"),
                new ByteMeasurement(true, 110, "PROCESS_RSS"),
                unavailable, unavailable, unavailable);
        CostMetrics metrics = SearchV3SemanticDirectnessEvaluator.summarizeCost(
                List.of(
                        new InferenceCostObservation(
                                "Q1", 2, 1, 100, 20, 12.0d, List.of(4.0d, 6.0d)),
                        new InferenceCostObservation(
                                "Q2", 1, 1, 50, 10, 20.0d, List.of(8.0d))),
                resources);

        assertThat(metrics.queryCount()).isEqualTo(2);
        assertThat(metrics.pairCount()).isEqualTo(3);
        assertThat(metrics.requestCount()).isEqualTo(2);
        assertThat(metrics.inputUtf8Bytes()).isEqualTo(150);
        assertThat(metrics.outputUtf8Bytes()).isEqualTo(30);
        assertThat(metrics.queryLatency().p50Ms()).isEqualTo(12.0d);
        assertThat(metrics.queryLatency().p95Ms()).isEqualTo(20.0d);
        assertThat(metrics.pairLatency().p50Ms()).isEqualTo(6.0d);
        assertThat(metrics.resources()).isEqualTo(resources);
    }

    private DirectnessRun passingRun() {
        List<QueryProjection> queries = List.of(
                query("Q1", "U1", candidates("Q1", "U1", 4)),
                query("Q2", "U2", candidates("Q2", "U2", 2)),
                query("Q3", "U3", candidates("Q3", "U3", 2)),
                query("Q4", "U4", candidates("Q4", "U4", 2)));
        List<QueryGold> gold = List.of(
                gold("Q1", "U1", "BACKEND", "KO", List.of("other_actor"), SUPPORTED,
                        Map.of("Q1-C1", RELATED, "Q1-C2", DIRECT_SUPPORT,
                                "Q1-C3", CONTRADICTS, "Q1-C4", OracleRelation.INSUFFICIENT)),
                gold("Q2", "U2", "DESIGN_PRODUCT", "EN", List.of("abstract_competency"), SUPPORTED,
                        Map.of("Q2-C1", DIRECT_SUPPORT)),
                gold("Q3", "U3", "MARKETING_SALES", "KO", List.of("semantic_paraphrase"), SUPPORTED,
                        Map.of("Q3-C2", DIRECT_SUPPORT)),
                gold("Q4", "U4", "PLANNING", "KO", List.of("semantic_paraphrase"), SUPPORTED,
                        Map.of("Q4-C2", DIRECT_SUPPORT)));
        return evaluator.evaluate(
                joined(queries, gold),
                Set.of("Q1", "Q2", "Q3", "Q4"),
                List.of(
                        predictions("Q1", RELATED_CONTEXT, DIRECT_MATCH, QUERY_CONFLICT, INSUFFICIENT),
                        predictions("Q2", DIRECT_MATCH, INSUFFICIENT),
                        predictions("Q3", RELATED_CONTEXT, DIRECT_MATCH),
                        predictions("Q4", RELATED_CONTEXT, DIRECT_MATCH)));
    }

    private GoldJoined<List<QueryGold>> joined(
            List<QueryProjection> queries,
            List<QueryGold> gold) {
        PhaseGuard guard = new PhaseGuard();
        guard.freezeCandidates(new FreezeInput(
                SearchV3CandidateFreeze.SCHEMA_VERSION,
                "SUITE",
                "DATASET-1",
                "b".repeat(64),
                EvaluationTrack.SEMANTIC,
                queries));
        guard.verifyFreeze();
        Map<String, QueryProjection> byId = queries.stream().collect(Collectors.toMap(
                QueryProjection::queryId, Function.identity()));
        List<QueryGold> bound = gold.stream().map(value -> bindCoverage(value, byId.get(value.queryId())))
                .toList();
        return guard.joinGold(() -> bound);
    }

    private QueryGold bindCoverage(QueryGold gold, QueryProjection query) {
        Set<String> expected = gold.aspects().stream()
                .flatMap(aspect -> aspect.expectedEvidence().stream())
                .map(ExpectedGoldEvidence::evidenceUnitId)
                .collect(Collectors.toSet());
        Map<String, List<String>> coverage = new LinkedHashMap<>();
        for (CandidateProjection candidate : query.rankedCandidates()) {
            List<String> covered = candidate.evidenceChildren().stream()
                    .map(EvidenceChildProjection::evidenceChildId)
                    .filter(expected::contains)
                    .toList();
            if (!covered.isEmpty()) coverage.put(candidate.candidateId(), covered);
        }
        return new QueryGold(
                gold.queryId(), gold.userBundleId(), gold.professionGroup(), gold.language(),
                gold.categories(), gold.answerability(), gold.aspectExpression(), gold.aspects(), coverage);
    }

    private QueryProjection query(String queryId, String user, List<CandidateProjection> candidates) {
        return new QueryProjection(queryId, user, "DEV", EvaluationTrack.SEMANTIC, candidates);
    }

    private List<CandidateProjection> candidates(String queryId, String user, int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(rank -> candidate(rank, queryId + "-P" + rank, user, queryId + "-C" + rank))
                .toList();
    }

    private CandidateProjection candidate(int rank, String candidateId, String user, String childId) {
        String document = candidateId + "-DOC";
        String version = candidateId + "-VERSION";
        String text = "source " + childId;
        String hash = SearchV3CandidateFreeze.sha256(text);
        EvidenceChildProjection child = new EvidenceChildProjection(
                childId, document, version, 1, 0,
                text.codePointCount(0, text.length()), text, hash);
        return new CandidateProjection(
                rank,
                candidateId,
                1.0d - rank * 0.01d,
                user,
                document,
                version,
                candidateId + "-PARENT",
                text,
                text,
                hash,
                hash,
                List.of(child));
    }

    private QueryPredictions predictions(String queryId, DirectnessRelation... relations) {
        List<CandidatePrediction> candidates = IntStream.range(0, relations.length)
                .mapToObj(index -> new CandidatePrediction(
                        index + 1,
                        queryId + "-P" + (index + 1),
                        relations[index]))
                .toList();
        return new QueryPredictions(queryId, candidates);
    }

    private QueryGold gold(
            String queryId,
            String user,
            String profession,
            String language,
            List<String> categories,
            SearchV3OracleCeilingEvaluator.GoldAnswerability answerability,
            Map<String, OracleRelation> relations) {
        List<ExpectedGoldEvidence> direct = new ArrayList<>();
        List<ExpectedGoldEvidence> nonDirect = new ArrayList<>();
        relations.forEach((unitId, relation) -> {
            ExpectedGoldEvidence value = new ExpectedGoldEvidence(unitId, "G-" + unitId, relation);
            (relation == DIRECT_SUPPORT ? direct : nonDirect).add(value);
        });
        List<GoldAspect> aspects = new ArrayList<>();
        List<String> required = new ArrayList<>();
        if (!direct.isEmpty()) {
            aspects.add(new GoldAspect(
                    "direct", true, 1,
                    direct.stream().map(ExpectedGoldEvidence::evidenceGroupId).toList(), direct));
            required.add("direct");
        }
        if (!nonDirect.isEmpty()) {
            boolean requiredAspect = answerability != SUPPORTED;
            aspects.add(new GoldAspect("non_direct", requiredAspect, 0, List.of(), nonDirect));
            if (requiredAspect) required.add("non_direct");
        }
        if (aspects.isEmpty()) {
            aspects.add(new GoldAspect("no_evidence", true, 0, List.of(), List.of()));
            required.add("no_evidence");
        }
        return new QueryGold(
                queryId, user, profession, language, categories, answerability,
                new GoldAspectExpression("ALL", required, required.size()), aspects, Map.of());
    }
}
