package com.prizm.search.evaluation.searchv3.structural;

import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.CeilingState.FOUND;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.CeilingState.NONE;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.CeilingState.PARTIAL;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.CeilingState.UNRESOLVED;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.FailureStage.ALREADY_CORRECT;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.FailureStage.FALSE_POSITIVE_RISK;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.FailureStage.NO_SUPPORT;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.FailureStage.PARTIAL_ONLY;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.FailureStage.RANKING_RECOVERABLE;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.FailureStage.RETRIEVAL_MISS;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.GoldAnswerability.NOT_SUPPORTED;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.GoldAnswerability.PARTIALLY_SUPPORTED;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.GoldAnswerability.SUPPORTED;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleRelation.CONTRADICTS;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleRelation.DIRECT_SUPPORT;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleRelation.INSUFFICIENT;
import static com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleRelation.RELATED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.CandidateProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvaluationTrack;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvidenceChildProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.FreezeInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.GoldJoined;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.PhaseGuard;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.QueryProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleCandidate;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleRelation;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleRun;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.QueryGold;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.QueryResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SearchV3OracleCeilingEvaluatorTest {

    private final SearchV3OracleCeilingEvaluator evaluator = new SearchV3OracleCeilingEvaluator();

    @Test
    void O1UsesFrozenRelationOrderAndPreservesS0IdentityCosineAndLocalOrder() {
        QueryProjection query = query(
                "Q1", "U1", EvaluationTrack.SEMANTIC,
                candidate(1, "P1", "U1", 0.91d, "C1"),
                candidate(2, "P2", "U1", 0.82d, "C2"),
                candidate(3, "P3", "U1", 0.73d, "C3"),
                candidate(4, "P4", "U1", 0.64d, "C4"),
                candidate(5, "P5", "U1", 0.55d, "C5"));
        QueryGold gold = gold(
                "Q1", "U1", "backend", "ko", List.of("semantic"), SUPPORTED,
                true,
                Map.of("C1", RELATED, "C2", DIRECT_SUPPORT, "C3", RELATED, "C4", CONTRADICTS));

        QueryResult result = run(EvaluationTrack.SEMANTIC, List.of(query), List.of(gold)).queries().get(0);

        assertThat(result.s0Ranking()).extracting(value -> value.candidate().candidateId())
                .containsExactly("P1", "P2", "P3", "P4", "P5");
        assertThat(result.o1Ranking()).extracting(value -> value.candidate().candidateId())
                .containsExactly("P2", "P1", "P3", "P4", "P5");
        assertThat(result.o1Ranking()).extracting(OracleCandidate::relation)
                .containsExactly(DIRECT_SUPPORT, RELATED, RELATED, CONTRADICTS, INSUFFICIENT);
        assertThat(result.o1Ranking().stream()
                .filter(value -> value.relation() == RELATED)
                .map(value -> value.candidate().candidateId()))
                .containsExactly("P1", "P3");

        Map<String, CandidateProjection> s0ById = result.s0Ranking().stream().collect(Collectors.toMap(
                value -> value.candidate().candidateId(), OracleCandidate::candidate));
        Map<String, CandidateProjection> o1ById = result.o1Ranking().stream().collect(Collectors.toMap(
                value -> value.candidate().candidateId(), OracleCandidate::candidate));
        assertThat(o1ById).isEqualTo(s0ById);
        assertThat(result.s0().top1()).isFalse();
        assertThat(result.s0().mrr()).isEqualTo(0.5d);
        assertThat(result.o1().top1()).isTrue();
        assertThat(result.o1().mrr()).isEqualTo(1.0d);
        assertThat(result.o1().ndcgAt5()).isEqualTo(1.0d);
        assertThat(result.s0().ndcgAt5()).isLessThan(1.0d);
        assertThat(result.ceilingState()).isEqualTo(FOUND);
        assertThat(result.failureStage()).isEqualTo(RANKING_RECOVERABLE);
    }

    @Test
    void candidateUsesStrongestChildRelationWithoutChangingChildOrCandidateOrder() {
        CandidateProjection mixed = candidate(
                1, "P1", "U1", 0.9d, "RELATED-CHILD", "DIRECT-CHILD");
        CandidateProjection contradicted = candidate(
                2, "P2", "U1", 0.8d, "CONTRADICTED-CHILD");
        QueryGold gold = gold(
                "Q1", "U1", "design", "en", List.of("paraphrase"), SUPPORTED,
                true,
                Map.of(
                        "RELATED-CHILD", RELATED,
                        "DIRECT-CHILD", DIRECT_SUPPORT,
                        "CONTRADICTED-CHILD", CONTRADICTS));

        QueryResult result = run(
                EvaluationTrack.SEMANTIC,
                List.of(query("Q1", "U1", EvaluationTrack.SEMANTIC, mixed, contradicted)),
                List.of(gold)).queries().get(0);

        assertThat(result.s0Ranking()).extracting(OracleCandidate::relation)
                .containsExactly(DIRECT_SUPPORT, CONTRADICTS);
        assertThat(result.s0Ranking().get(0).candidate().evidenceChildren())
                .extracting(value -> value.evidenceChildId())
                .containsExactly("RELATED-CHILD", "DIRECT-CHILD");
        assertThat(OracleRelation.values())
                .containsExactly(DIRECT_SUPPORT, RELATED, CONTRADICTS, INSUFFICIENT);
    }

    @Test
    void failureStagesAndCeilingStatesAreClassifiedFromFrozenTop20Only() {
        List<QueryProjection> queries = List.of(
                query("Q1", "U1", EvaluationTrack.SEMANTIC,
                        candidate(1, "Q1-P1", "U1", 0.9d, "Q1-D")),
                query("Q2", "U1", EvaluationTrack.SEMANTIC,
                        candidate(1, "Q2-P1", "U1", 0.9d, "Q2-R"),
                        candidate(2, "Q2-P2", "U1", 0.8d, "Q2-D")),
                query("Q3", "U2", EvaluationTrack.SEMANTIC,
                        candidate(1, "Q3-P1", "U2", 0.9d, "Q3-I")),
                query("Q4", "U2", EvaluationTrack.SEMANTIC,
                        candidate(1, "Q4-P1", "U2", 0.9d, "Q4-R")),
                query("Q5", "U3", EvaluationTrack.SEMANTIC,
                        candidate(1, "Q5-P1", "U3", 0.9d, "Q5-C")),
                query("Q6", "U3", EvaluationTrack.SEMANTIC,
                        candidate(1, "Q6-P1", "U3", 0.9d, "Q6-I")));
        List<QueryGold> gold = List.of(
                gold("Q1", "U1", "backend", "ko", List.of("literal"), SUPPORTED,
                        true,
                        Map.of("Q1-D", DIRECT_SUPPORT)),
                gold("Q2", "U1", "backend", "ko", List.of("semantic"), SUPPORTED,
                        true,
                        Map.of("Q2-R", RELATED, "Q2-D", DIRECT_SUPPORT)),
                gold("Q3", "U2", "design", "en", List.of("abstract"), SUPPORTED,
                        true,
                        Map.of()),
                gold("Q4", "U2", "design", "en", List.of("multi_aspect"), PARTIALLY_SUPPORTED,
                        false,
                        Map.of("Q4-R", RELATED)),
                gold("Q5", "U3", "marketing", "ko", List.of("negation"), NOT_SUPPORTED,
                        false,
                        Map.of("Q5-C", CONTRADICTS)),
                gold("Q6", "U3", "marketing", "ko", List.of("no_answer"), NOT_SUPPORTED,
                        false,
                        Map.of()));

        OracleRun run = run(EvaluationTrack.SEMANTIC, queries, gold);
        Map<String, QueryResult> byId = run.queries().stream()
                .collect(Collectors.toMap(QueryResult::queryId, Function.identity()));

        assertThat(byId.get("Q1").failureStage()).isEqualTo(ALREADY_CORRECT);
        assertThat(byId.get("Q2").failureStage()).isEqualTo(RANKING_RECOVERABLE);
        assertThat(byId.get("Q3").failureStage()).isEqualTo(RETRIEVAL_MISS);
        assertThat(byId.get("Q3").directPositive()).isTrue();
        assertThat(byId.get("Q3").s0().directRecallAt20()).isFalse();
        assertThat(byId.get("Q3").o1().directRecallAt20()).isFalse();
        assertThat(byId.get("Q4").failureStage()).isEqualTo(PARTIAL_ONLY);
        assertThat(byId.get("Q5").failureStage()).isEqualTo(FALSE_POSITIVE_RISK);
        assertThat(byId.get("Q6").failureStage()).isEqualTo(NO_SUPPORT);
        assertThat(byId.get("Q1").ceilingState()).isEqualTo(FOUND);
        assertThat(byId.get("Q2").ceilingState()).isEqualTo(FOUND);
        assertThat(byId.get("Q3").ceilingState()).isEqualTo(UNRESOLVED);
        assertThat(byId.get("Q4").ceilingState()).isEqualTo(PARTIAL);
        assertThat(byId.get("Q5").ceilingState()).isEqualTo(NONE);
        assertThat(byId.get("Q6").ceilingState()).isEqualTo(NONE);
        assertThat(run.aggregate().directPositiveQueryCount()).isEqualTo(3);
        assertThat(run.aggregate().s0DirectRecallAt20()).isEqualTo(2.0d / 3.0d);
        assertThat(run.aggregate().o1Top1()).isEqualTo(2.0d / 3.0d);
    }

    @Test
    void groupingHelpersPreserveUserProfessionLanguageAndMultiCategoryMembership() {
        List<QueryProjection> queries = List.of(
                query("Q1", "U1", EvaluationTrack.SEMANTIC,
                        candidate(1, "Q1-P1", "U1", 0.9d, "Q1-D")),
                query("Q2", "U1", EvaluationTrack.SEMANTIC,
                        candidate(1, "Q2-P1", "U1", 0.8d, "Q2-R"),
                        candidate(2, "Q2-P2", "U1", 0.7d, "Q2-D")),
                query("Q3", "U2", EvaluationTrack.SEMANTIC,
                        candidate(1, "Q3-P1", "U2", 0.9d, "Q3-R")));
        List<QueryGold> gold = List.of(
                gold("Q1", "U1", "backend", "ko", List.of("semantic", "korean"), SUPPORTED,
                        true,
                        Map.of("Q1-D", DIRECT_SUPPORT)),
                gold("Q2", "U1", "backend", "ko", List.of("semantic"), SUPPORTED,
                        true,
                        Map.of("Q2-R", RELATED, "Q2-D", DIRECT_SUPPORT)),
                gold("Q3", "U2", "design", "en", List.of("abstract"), PARTIALLY_SUPPORTED,
                        false,
                        Map.of("Q3-R", RELATED)));

        OracleRun run = run(EvaluationTrack.SEMANTIC, queries, gold);

        assertThat(run.userSlices()).containsOnlyKeys("U1", "U2");
        assertThat(run.userSlices().get("U1").queryCount()).isEqualTo(2);
        assertThat(run.userSlices().get("U1").s0Top1()).isEqualTo(0.5d);
        assertThat(run.professionSlices()).containsOnlyKeys("backend", "design");
        assertThat(run.professionSlices().get("backend").queryCount()).isEqualTo(2);
        assertThat(run.languageSlices()).containsOnlyKeys("ko", "en");
        assertThat(run.categorySlices().get("semantic").queryCount()).isEqualTo(2);
        assertThat(run.categorySlices().get("korean").queryCount()).isEqualTo(1);
        assertThat(run.categorySlices().get("abstract").queryCount()).isEqualTo(1);
    }

    @Test
    void semanticOracleRejectsTypedFreezeAfterValidFreezeAndGoldJoin() {
        QueryProjection typed = query(
                "Q1", "U1", EvaluationTrack.TYPED,
                candidate(1, "P1", "U1", 0.9d, "C1"));
        QueryGold gold = gold(
                "Q1", "U1", "backend", "ko", List.of("numeric"), SUPPORTED,
                true,
                Map.of("C1", DIRECT_SUPPORT));
        GoldJoined<List<QueryGold>> joined = joined(EvaluationTrack.TYPED, List.of(typed), List.of(gold));

        assertThatThrownBy(() -> evaluator.evaluate(joined))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejects typed");
    }

    @Test
    void directPositiveIsDerivedFromExpectedGoldRelationsAndAnswerabilityFailsClosed() {
        QueryProjection query = query(
                "Q1", "U1", EvaluationTrack.SEMANTIC,
                candidate(1, "P1", "U1", 0.9d, "C1"));
        QueryGold malformed = new QueryGold(
                "Q1", "U1", "backend", "ko", List.of("semantic"), SUPPORTED,
                List.of(RELATED), Map.of("C1", RELATED));

        assertThatThrownBy(() -> run(
                EvaluationTrack.SEMANTIC, List.of(query), List.of(malformed)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SUPPORTED Gold requires");
    }

    private OracleRun run(
            EvaluationTrack track,
            List<QueryProjection> queries,
            List<QueryGold> gold) {
        return evaluator.evaluate(joined(track, queries, gold));
    }

    private GoldJoined<List<QueryGold>> joined(
            EvaluationTrack track,
            List<QueryProjection> queries,
            List<QueryGold> gold) {
        PhaseGuard guard = new PhaseGuard();
        guard.freezeCandidates(new FreezeInput(
                SearchV3CandidateFreeze.SCHEMA_VERSION,
                "SUITE",
                "DATASET-1",
                "b".repeat(64),
                track,
                queries));
        guard.verifyFreeze();
        return guard.joinGold(() -> gold);
    }

    private QueryProjection query(
            String queryId,
            String user,
            EvaluationTrack track,
            CandidateProjection... candidates) {
        return new QueryProjection(queryId, user, "DEV", track, List.of(candidates));
    }

    private QueryGold gold(
            String queryId,
            String user,
            String profession,
            String language,
            List<String> categories,
            SearchV3OracleCeilingEvaluator.GoldAnswerability answerability,
            boolean goldDirectPositive,
            Map<String, OracleRelation> relations) {
        return new QueryGold(
                queryId, user, profession, language, categories, answerability,
                goldDirectPositive ? List.of(DIRECT_SUPPORT) : relations.values().stream().distinct().toList(),
                relations);
    }

    private CandidateProjection candidate(
            int rank,
            String candidateId,
            String user,
            double score,
            String... childIds) {
        String document = candidateId + "-DOC";
        String version = candidateId + "-VERSION";
        List<EvidenceChildProjection> children = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        int cursor = 0;
        for (String childId : childIds) {
            String text = "source " + childId;
            String hash = SearchV3CandidateFreeze.sha256(text);
            children.add(new EvidenceChildProjection(
                    childId,
                    document,
                    version,
                    1,
                    cursor,
                    cursor + text.codePointCount(0, text.length()),
                    text,
                    hash));
            texts.add(text);
            cursor += text.codePointCount(0, text.length()) + 1;
        }
        String source = String.join("\n", texts);
        String hash = SearchV3CandidateFreeze.sha256(source);
        return new CandidateProjection(
                rank,
                candidateId,
                score,
                user,
                document,
                version,
                candidateId + "-PARENT",
                source,
                source,
                hash,
                hash,
                children);
    }
}
