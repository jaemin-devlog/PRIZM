package com.prizm.search.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.repository.VectorSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class NumericAnchorRescueProfileTest {

    private final NumericAnchorRescueProfile profile =
            new NumericAnchorRescueProfile(new CompositeSearchProfile());

    @Test
    void rescuesExactNumberAndUnitWhileKeepingTheOriginalScore() {
        VectorSearchResult candidate = candidate(
                10L, "동시 요청 재현 통합 테스트 4,400회에서 중복 저장 0건을 확인했다.", 0.31d);

        assertThat(profile.apply("4,400회 테스트", List.of(candidate)))
                .extracting(
                        VectorSearchResult::chunkId,
                        VectorSearchResult::score,
                        VectorSearchResult::distance)
                .containsExactly(tuple(10L, 0.31d, 0.69d));
    }

    @Test
    void doesNotRescueANearMissNumber() {
        VectorSearchResult candidate = candidate(
                10L, "동시 요청 재현 통합 테스트 4,400회에서 중복 저장 0건을 확인했다.", 0.31d);

        assertThat(profile.apply("4,401회 테스트", List.of(candidate))).isEmpty();
    }

    private VectorSearchResult candidate(long chunkId, String content, double score) {
        return new VectorSearchResult(
                chunkId,
                1L,
                2L,
                "포트폴리오",
                1,
                1,
                2,
                ChunkSourceType.PAGE,
                2,
                "2페이지",
                content,
                1.0d - score,
                score);
    }
}
