package com.prizm.mcp;

import com.prizm.auth.security.CurrentUserProvider;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.dto.response.CareerEvidenceSearchV2Response;
import com.prizm.search.service.SearchService;
import java.util.List;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/** Read-only MCP adapter for the existing owner-scoped Career Evidence V2 search. */
@Component
public class CareerEvidenceMcpTool {

    private final SearchService searchService;
    private final CurrentUserProvider currentUserProvider;

    public CareerEvidenceMcpTool(SearchService searchService, CurrentUserProvider currentUserProvider) {
        this.searchService = searchService;
        this.currentUserProvider = currentUserProvider;
    }

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
