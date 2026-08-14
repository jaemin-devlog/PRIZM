package com.prizm.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.auth.security.CurrentUserProvider;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.dto.response.CareerEvidenceSearchState;
import com.prizm.search.dto.response.CareerEvidenceSearchV2Response;
import com.prizm.search.exception.InvalidSearchQueryException;
import com.prizm.search.service.SearchService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CareerEvidenceMcpToolTest {

    @Mock
    SearchService searchService;

    @Mock
    CurrentUserProvider currentUserProvider;

    @Test
    void mapsEvidenceWithoutScoresOrRawContentAndUsesTheAuthenticatedUser() {
        CareerEvidenceMcpTool tool = new CareerEvidenceMcpTool(searchService, currentUserProvider);
        CareerEvidenceSearchV2Response serviceResponse = new CareerEvidenceSearchV2Response(
                CareerEvidenceSearchState.EVIDENCE_FOUND,
                List.of(evidence()));
        when(currentUserProvider.userId()).thenReturn(7L);
        when(searchService.searchCareerEvidenceV2(7L, "Spring Boot experience"))
                .thenReturn(serviceResponse);

        CareerEvidenceMcpTool.CareerEvidenceMcpResponse result =
                tool.search("Spring Boot experience");

        assertThat(result.state()).isEqualTo("EVIDENCE_FOUND");
        assertThat(result.results()).containsExactly(new CareerEvidenceMcpTool.CareerEvidenceMcpResult(
                "Spring Boot로 인증 API를 구현했다.",
                "Backend portfolio",
                2,
                "TEXT_CHUNK",
                3,
                "텍스트 구간 3",
                "PAGE",
                4,
                "4페이지",
                10L,
                20L,
                30L,
                31L));
        verify(searchService).searchCareerEvidenceV2(7L, "Spring Boot experience");
    }

    @Test
    void preservesNoEvidenceAndNoRelevantResultsStates() {
        CareerEvidenceMcpTool tool = new CareerEvidenceMcpTool(searchService, currentUserProvider);
        when(currentUserProvider.userId()).thenReturn(7L);
        when(searchService.searchCareerEvidenceV2(7L, "release evidence"))
                .thenReturn(empty(CareerEvidenceSearchState.NO_EVIDENCE));
        when(searchService.searchCareerEvidenceV2(7L, "general evidence"))
                .thenReturn(empty(CareerEvidenceSearchState.NO_RELEVANT_RESULTS));

        assertThat(tool.search("release evidence"))
                .isEqualTo(new CareerEvidenceMcpTool.CareerEvidenceMcpResponse("NO_EVIDENCE", List.of()));
        assertThat(tool.search("general evidence"))
                .isEqualTo(new CareerEvidenceMcpTool.CareerEvidenceMcpResponse("NO_RELEVANT_RESULTS", List.of()));
    }

    @Test
    void preservesSearchServiceValidationForBlankAndOverlongQueries() {
        CareerEvidenceMcpTool tool = new CareerEvidenceMcpTool(searchService, currentUserProvider);
        String overlong = "x".repeat(SearchService.MAX_QUERY_LENGTH + 1);
        when(currentUserProvider.userId()).thenReturn(7L);
        when(searchService.searchCareerEvidenceV2(7L, " "))
                .thenThrow(new InvalidSearchQueryException("query must not be blank"));
        when(searchService.searchCareerEvidenceV2(7L, overlong))
                .thenThrow(new InvalidSearchQueryException("query must be at most 500 characters"));

        assertThatThrownBy(() -> tool.search(" "))
                .isInstanceOf(InvalidSearchQueryException.class)
                .hasMessage("query must not be blank");
        assertThatThrownBy(() -> tool.search(overlong))
                .isInstanceOf(InvalidSearchQueryException.class)
                .hasMessage("query must be at most 500 characters");
    }

    private CareerEvidenceSearchV2Response empty(CareerEvidenceSearchState state) {
        return new CareerEvidenceSearchV2Response(state, List.of());
    }

    private CareerEvidenceSearchResponse evidence() {
        return new CareerEvidenceSearchResponse(
                30L,
                10L,
                20L,
                "Backend portfolio",
                2,
                "Full raw chunk content that is intentionally not exposed through MCP.",
                "Spring Boot로 인증 API를 구현했다.",
                ChunkSourceType.TEXT_CHUNK,
                3,
                "텍스트 구간 3",
                31L,
                ChunkSourceType.PAGE,
                4,
                "4페이지",
                0.12d,
                0.88d);
    }
}
