package com.prizm.mcp;

import com.prizm.auth.security.CurrentUserProvider;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.dto.response.CareerEvidenceSearchV2Response;
import com.prizm.search.service.SearchService;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * 기존 Career Evidence V2 검색을 MCP의 읽기 전용 도구로 노출한다.
 * 인증 컨텍스트의 사용자 ID를 그대로 {@link SearchService}에 넘기므로 REST와 같은 소유자 범위와 ACTIVE
 * 버전 제한, 질의 검증, Evidence Retrieval과 Localization 결과를 재사용한다. 원문 전체와 내부 점수는
 * 내보내지 않으며, 경력의 진위나 요구사항 충족 여부를 따로 판정하지 않는다.
 */
@Component
public class CareerEvidenceMcpTool {

    private final SearchService searchService;
    private final CurrentUserProvider currentUserProvider;

    public CareerEvidenceMcpTool(SearchService searchService, CurrentUserProvider currentUserProvider) {
        this.searchService = searchService;
        this.currentUserProvider = currentUserProvider;
    }

    /** MCP 전용 검색 규칙을 더하지 않고 인증된 사용자의 V2 결과를 응답 스키마로만 옮긴다. */
    @McpTool(
            name = "search_career_evidence",
            description = "Search the authenticated user's active career documents for direct evidence.",
            generateOutputSchema = true,
            annotations = @McpTool.McpAnnotations(
                    title = "Search career evidence",
                    readOnlyHint = true,
                    destructiveHint = false,
                    openWorldHint = false))
    public CareerEvidenceMcpResponse search(
            @McpToolParam(
                    description = "Natural-language career evidence question (1-500 characters).",
                    required = true)
            String query) {
        CareerEvidenceSearchV2Response response =
                searchService.searchCareerEvidenceV2(currentUserProvider.userId(), query);
        return CareerEvidenceMcpResponse.from(response);
    }

    public record CareerEvidenceMcpResponse(
            String state,
            List<CareerEvidenceMcpResult> results) {

        static CareerEvidenceMcpResponse from(CareerEvidenceSearchV2Response response) {
            return new CareerEvidenceMcpResponse(
                    response.state().name(),
                    response.results().stream().map(CareerEvidenceMcpResult::from).toList());
        }

        public CareerEvidenceMcpResponse {
            results = List.copyOf(results);
        }
    }

    public record CareerEvidenceMcpResult(
            String evidence,
            String documentTitle,
            int versionNo,
            String sourceType,
            int sourceIndex,
            String sourceLabel,
            String evidenceSourceType,
            int evidenceSourceIndex,
            String evidenceSourceLabel,
            Long documentId,
            Long documentVersionId,
            Long chunkId,
            Long evidenceChunkId) {

        static CareerEvidenceMcpResult from(CareerEvidenceSearchResponse response) {
            return new CareerEvidenceMcpResult(
                    response.snippet(),
                    response.documentTitle(),
                    response.versionNo(),
                    response.sourceType().name(),
                    response.sourceIndex(),
                    response.sourceLabel(),
                    response.evidenceSourceType().name(),
                    response.evidenceSourceIndex(),
                    response.evidenceSourceLabel(),
                    response.documentId(),
                    response.documentVersionId(),
                    response.chunkId(),
                    response.evidenceChunkId());
        }
    }
}
