package com.prizm.search.evaluation.searchv3.typed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.CandidateObservation;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.MatchState;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QueryConstraint;
import com.prizm.search.evaluation.searchv3.typed.TypedValueModel.SourceSlice;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TypedStablePartitionerTest {

    private final DeterministicTypedQueryParser queryParser = new DeterministicTypedQueryParser();
    private final DeterministicTypedObservationExtractor extractor = new DeterministicTypedObservationExtractor();
    private final TypedStablePartitioner partitioner = new TypedStablePartitioner();

    @Test
    void stablePartitionsWithoutChangingCandidateIdentityOrStateLocalOrder() {
        List<String> ranking = List.of("unknown-1", "contradicted-1", "satisfied-1", "unknown-2",
                "satisfied-2", "contradicted-2");
        Map<String, List<CandidateObservation>> observations = new HashMap<>();
        observations.put("unknown-1", observations("데이터 1,300건"));
        observations.put("contradicted-1", observations("사용자 300명"));
        observations.put("satisfied-1", observations("사용자 1,300명"));
        observations.put("unknown-2", observations("참가자 1,300명"));
        observations.put("satisfied-2", observations("사용자 2,000명"));
        observations.put("contradicted-2", observations("사용자 999명"));

        List<String> result = partitioner.partition(
                ranking, queryParser.parse("사용자 1,000명 이상"), observations::get);

        assertThat(result).containsExactly(
                "satisfied-1", "satisfied-2",
                "unknown-1", "unknown-2",
                "contradicted-1", "contradicted-2");
        assertThat(result).containsExactlyInAnyOrderElementsOf(ranking);
    }

    @Test
    void noConstraintReturnsTheExactOriginalRankingWithoutCallingProvider() {
        List<String> ranking = List.of("C1", "C2", "C3");

        List<String> result = partitioner.partition(ranking, List.of(), ignored -> {
            throw new AssertionError("semantic query must not parse candidate observations");
        });

        assertThat(result).isSameAs(ranking);
        assertThat(result).containsExactly("C1", "C2", "C3");
    }

    @Test
    void stablePartitionsAlreadyEvaluatedStatesWithoutReevaluatingObservations() {
        List<String> ranking = List.of("unknown-1", "contradicted", "satisfied", "unknown-2");
        Map<String, MatchState> states = Map.of(
                "unknown-1", MatchState.UNKNOWN,
                "contradicted", MatchState.CONTRADICTED,
                "satisfied", MatchState.SATISFIED,
                "unknown-2", MatchState.UNKNOWN);

        assertThat(partitioner.partitionEvaluated(ranking, true, states::get))
                .containsExactly("satisfied", "unknown-1", "unknown-2", "contradicted");
        assertThat(partitioner.partitionEvaluated(ranking, false, ignored -> {
            throw new AssertionError("semantic query must not request a state");
        })).isSameAs(ranking);
    }

    @Test
    void runtimePublicApisExposeNoGoldOrEvaluationDatasetTypes() {
        List<Class<?>> runtimeTypes = List.of(
                DeterministicTypedQueryParser.class,
                DeterministicTypedObservationExtractor.class,
                TypedConstraintEvaluator.class,
                TypedStablePartitioner.class);

        assertThatNoException().isThrownBy(() -> runtimeTypes.stream()
                .flatMap(type -> List.of(type.getDeclaredMethods()).stream())
                .filter(method -> java.lang.reflect.Modifier.isPublic(method.getModifiers()))
                .forEach(this::assertGoldFree));
    }

    private void assertGoldFree(Method method) {
        String signature = method.toGenericString();
        assertThat(signature)
                .doesNotContain("DatasetSlice", "GoldUnit", "ExpectedEvidence", "Answerability", "Category");
    }

    private List<CandidateObservation> observations(String text) {
        return extractor.extract(new SourceSlice("D", "V", text, null, 0, text));
    }
}
