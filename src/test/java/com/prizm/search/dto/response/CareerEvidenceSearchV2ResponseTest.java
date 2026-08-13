package com.prizm.search.dto.response;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.ingestion.entity.ChunkSourceType;
import java.util.List;
import org.junit.jupiter.api.Test;

class CareerEvidenceSearchV2ResponseTest {

    private static final CareerEvidenceSearchResponse EVIDENCE = new CareerEvidenceSearchResponse(
            1L,
            2L,
            3L,
            "합성 문서",
            1,
            "합성 근거",
            "합성 근거",
            ChunkSourceType.TEXT_CHUNK,
            1,
            "텍스트 구간 1",
            1L,
            ChunkSourceType.TEXT_CHUNK,
            1,
            "텍스트 구간 1",
            0.2d,
            0.8d);

    @Test
    void rejectsInconsistentStateAndResultCombinations() {
        assertThatThrownBy(() -> new CareerEvidenceSearchV2Response(
                        CareerEvidenceSearchState.EVIDENCE_FOUND, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CareerEvidenceSearchV2Response(
                        CareerEvidenceSearchState.NO_EVIDENCE, List.of(EVIDENCE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CareerEvidenceSearchV2Response(
                        CareerEvidenceSearchState.NO_RELEVANT_RESULTS, List.of(EVIDENCE)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new CareerEvidenceSearchV2Response(
                        CareerEvidenceSearchState.NO_SEARCHABLE_DOCUMENTS, List.of(EVIDENCE)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullStateAndResults() {
        assertThatThrownBy(() -> new CareerEvidenceSearchV2Response(null, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("state must not be null");
        assertThatThrownBy(() -> new CareerEvidenceSearchV2Response(CareerEvidenceSearchState.NO_EVIDENCE, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("results must not be null");
    }
}
