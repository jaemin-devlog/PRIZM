package com.prizm.search.evaluation.searchv3.typed;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.CandidateObservation;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.MatchState;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QueryConstraint;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.SourceSlice;
import java.util.List;
import org.junit.jupiter.api.Test;

class TypedConstraintEvaluatorTest {

    private final DeterministicTypedQueryParser queryParser = new DeterministicTypedQueryParser();
    private final DeterministicTypedObservationExtractor observationExtractor =
            new DeterministicTypedObservationExtractor();
    private final TypedConstraintEvaluator evaluator = new TypedConstraintEvaluator();

    @Test
    void quantityUsesExactQualifierAndUnitBeforeComparingValues() {
        QueryConstraint constraint = firstConstraint("사용자 1,000명 이상");

        assertThat(evaluate(constraint, "사용자 1,300명")).isEqualTo(MatchState.SATISFIED);
        assertThat(evaluate(constraint, "사용자 300명")).isEqualTo(MatchState.CONTRADICTED);
        assertThat(evaluate(constraint, "데이터 1,300건")).isEqualTo(MatchState.UNKNOWN);
        assertThat(evaluate(constraint, "참가자 1,300명")).isEqualTo(MatchState.UNKNOWN);
    }

    @Test
    void twoTrulyUnqualifiedQuantitiesRemainComparable() {
        QueryConstraint constraint = firstConstraint("2,329건");

        assertThat(evaluate(constraint, "2,329건")).isEqualTo(MatchState.SATISFIED);
        assertThat(evaluate(constraint, "300건")).isEqualTo(MatchState.CONTRADICTED);
    }

    @Test
    void directionalPercentageRejectsOppositeDirectionAndPreservesMissingAsUnknown() {
        QueryConstraint constraint = firstConstraint("미응답 비율을 50% 이상 감소");

        assertThat(evaluate(constraint, "미응답 비율이 65% 감소")).isEqualTo(MatchState.SATISFIED);
        assertThat(evaluate(constraint, "미응답 비율이 65% 증가")).isEqualTo(MatchState.CONTRADICTED);
        assertThat(evaluate(constraint, "미응답 비율은 65%")).isEqualTo(MatchState.UNKNOWN);
    }

    @Test
    void quantityRangeIsInclusiveAndWrongValuesContradict() {
        QueryConstraint constraint = firstConstraint("교육 키트 50~100건");

        assertThat(evaluate(constraint, "교육 키트 50건")).isEqualTo(MatchState.SATISFIED);
        assertThat(evaluate(constraint, "교육 키트 100건")).isEqualTo(MatchState.SATISFIED);
        assertThat(evaluate(constraint, "교육 키트 120건")).isEqualTo(MatchState.CONTRADICTED);
    }

    @Test
    void candidateQuantityRangeRemainsUnknownInExactObservationV1() {
        QueryConstraint constraint = firstConstraint("교육 키트 50~100건");
        List<CandidateObservation> candidateRange = observations("교육 키트 50~100건");

        assertThat(candidateRange).noneMatch(TypedValueModel.QuantityObservation.class::isInstance);
        assertThat(evaluator.evaluate(constraint, candidateRange)).isEqualTo(MatchState.UNKNOWN);
    }

    @Test
    void dateComparisonRespectsStrictEnglishAndInclusiveKoreanBoundaries() {
        QueryConstraint english = firstConstraint("approved service launch date after 2025-06-30");
        QueryConstraint korean = firstConstraint("전국 rollout 시작일이 2025-06-30 이후");

        assertThat(evaluate(english, "approved service launch date was 2025-06-30"))
                .isEqualTo(MatchState.CONTRADICTED);
        assertThat(evaluate(english, "approved service launch date was 2025-07-01"))
                .isEqualTo(MatchState.SATISFIED);
        assertThat(evaluate(korean, "전국 rollout 시작일은 2025-06-30이었다"))
                .isEqualTo(MatchState.SATISFIED);
        assertThat(evaluate(korean, "전국 rollout 시작일은 2024-12-31이었다"))
                .isEqualTo(MatchState.CONTRADICTED);
    }

    @Test
    void identifierNumberContradictsOnlyTheSameIdentifier() {
        QueryConstraint constraint = firstConstraint("Java 17");

        assertThat(evaluate(constraint, "Java 17")).isEqualTo(MatchState.SATISFIED);
        assertThat(evaluate(constraint, "Java 11")).isEqualTo(MatchState.CONTRADICTED);
        assertThat(evaluate(constraint, "HTTP/17")).isEqualTo(MatchState.UNKNOWN);
    }

    @Test
    void literalRequiresWholeNormalizedEqualityAndNearMatchIsUnknown() {
        QueryConstraint constraint = firstConstraint("ZephyrDB");

        assertThat(evaluate(constraint, "ZephyrDB")).isEqualTo(MatchState.SATISFIED);
        assertThat(evaluate(constraint, "ZephyrDBX")).isEqualTo(MatchState.UNKNOWN);
        assertThat(evaluate(firstConstraint("\"North Star\""), "North-Star"))
                .isEqualTo(MatchState.SATISFIED);
    }

    @Test
    void observationReductionPrefersSatisfiedThenContradictedThenUnknown() {
        QueryConstraint constraint = firstConstraint("사용자 1,000명 이상");
        List<CandidateObservation> observations = observations("사용자 300명. 사용자 1,300명.");

        assertThat(evaluator.evaluate(constraint, observations)).isEqualTo(MatchState.SATISFIED);
        assertThat(evaluator.evaluate(constraint, observations("사용자 300명. 데이터 1,300건.")))
                .isEqualTo(MatchState.CONTRADICTED);
        assertThat(evaluator.evaluate(constraint, observations("데이터 1,300건.")))
                .isEqualTo(MatchState.UNKNOWN);
    }

    @Test
    void multipleConstraintReductionRequiresAllSatisfiedAndLetsContradictionWin() {
        List<QueryConstraint> constraints = queryParser.parse("Java 17에서 사용자 1,000명 이상");

        assertThat(evaluator.evaluateAll(constraints, observations("Java 17에서 사용자 1,300명")))
                .isEqualTo(MatchState.SATISFIED);
        assertThat(evaluator.evaluateAll(constraints, observations("Java 17에서 데이터 1,300건")))
                .isEqualTo(MatchState.UNKNOWN);
        assertThat(evaluator.evaluateAll(constraints, observations("Java 11에서 사용자 1,300명")))
                .isEqualTo(MatchState.CONTRADICTED);
        assertThat(evaluator.evaluateAll(List.of(), observations("Java 17")))
                .isEqualTo(MatchState.UNKNOWN);
    }

    private QueryConstraint firstConstraint(String query) {
        return queryParser.parse(query).get(0);
    }

    private MatchState evaluate(QueryConstraint constraint, String sourceText) {
        return evaluator.evaluate(constraint, observations(sourceText));
    }

    private List<CandidateObservation> observations(String sourceText) {
        return observationExtractor.extract(new SourceSlice("D", "V", "C", null, 0, sourceText));
    }
}
