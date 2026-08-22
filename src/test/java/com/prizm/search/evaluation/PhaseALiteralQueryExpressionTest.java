package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.repository.VectorSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class PhaseALiteralQueryExpressionTest {

    @Test
    void matchesCaseInsensitivelyWithExactIdentifierBoundaries() {
        PhaseALiteralQueryExpression expression = PhaseALiteralQueryExpression.from("fooEngine")
                .orElseThrow();

        assertThat(expression.matches("Built FOOENGINE for the synthetic workflow.")).isTrue();
        assertThat(expression.matches("Built FooEngineX for the synthetic workflow.")).isFalse();
    }

    @Test
    void treatsWhitespaceSeparatedWordsAsOneGenericLiteralPhrase() {
        PhaseALiteralQueryExpression expression = PhaseALiteralQueryExpression.from("Quartz Harbor Mesh")
                .orElseThrow();

        assertThat(expression.matches("Used quartz\t harbor   mesh in a synthetic project.")).isTrue();
        assertThat(expression.matches("Used Quartz Harbored Mesh in a synthetic project.")).isFalse();
    }

    @Test
    void rejectsSentenceLikeOrUnsafeExpressions() {
        assertThat(PhaseALiteralQueryExpression.from(
                "동시 요청으로 중복 데이터가 생기는 문제를 어떻게 해결했나요")).isEmpty();
        assertThat(PhaseALiteralQueryExpression.from("Redis|.*")).isEmpty();
    }

    @Test
    void unionsByChunkIdentityWithoutChangingScores() {
        VectorSearchResult dense = candidate(1L, 0.90d);
        VectorSearchResult duplicateLiteral = candidate(1L, 0.10d);
        VectorSearchResult literalOnly = candidate(2L, 0.40d);

        List<VectorSearchResult> union = PhaseALiteralRetrievalEvaluator.unionByChunkIdentity(
                List.of(dense), List.of(duplicateLiteral, literalOnly));

        assertThat(union).extracting(VectorSearchResult::chunkId).containsExactly(1L, 2L);
        assertThat(union.get(0).score()).isEqualTo(0.90d);
        assertThat(union.get(1).score()).isEqualTo(0.40d);
    }

    private static VectorSearchResult candidate(Long chunkId, double score) {
        return new VectorSearchResult(
                chunkId,
                10L,
                20L,
                "Synthetic",
                1,
                chunkId.intValue(),
                null,
                ChunkSourceType.TEXT_CHUNK,
                chunkId.intValue(),
                "텍스트 구간 " + chunkId,
                "Synthetic content " + chunkId,
                1.0d - score,
                score);
    }
}
