package com.prizm.search.service;

import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.search.config.SearchProfile;
import com.prizm.search.config.SearchProperties;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.dto.response.CareerEvidenceSearchState;
import com.prizm.search.dto.response.CareerEvidenceSearchV2Response;
import com.prizm.search.dto.response.SearchResponse;
import com.prizm.search.exception.InvalidSearchQueryException;
import com.prizm.search.exception.SearchResultNotFoundException;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.profile.ShortGeneralExactTokenRescueProfile;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.repository.VectorSearchResult;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * 질문을 임베딩하고 pgvector exact cosine 검색 결과를 API 응답으로 변환한다.
 *
 * <p>Repository가 ACTIVE이면서 documents.active_version_id에 연결된 청크만 조회하므로
 * 승인 전이거나 처리에 실패한 문서는 결과에 포함되지 않는다.</p>
 */
@Service
public class SearchService {

    public static final int MAX_QUERY_LENGTH = 500;

    private final EmbeddingService embeddingService;
    private final EmbeddingValidator embeddingValidator;
    private final VectorSearchRepository vectorSearchRepository;
    private final SearchProperties searchProperties;
    private final CompositeSearchProfile compositeSearchProfile;
    private final ShortGeneralExactTokenRescueProfile shortGeneralExactTokenRescueProfile;
    private final EvidenceExpansionService evidenceExpansionService;

    public SearchService(
            EmbeddingService embeddingService,
            EmbeddingValidator embeddingValidator,
            VectorSearchRepository vectorSearchRepository,
            SearchProperties searchProperties,
            CompositeSearchProfile compositeSearchProfile,
            EvidenceExpansionService evidenceExpansionService) {
        this.embeddingService = embeddingService;
        this.embeddingValidator = embeddingValidator;
        this.vectorSearchRepository = vectorSearchRepository;
        this.searchProperties = searchProperties;
        this.compositeSearchProfile = compositeSearchProfile;
        this.shortGeneralExactTokenRescueProfile =
                new ShortGeneralExactTokenRescueProfile(compositeSearchProfile);
        this.evidenceExpansionService = evidenceExpansionService;
    }

    /**
     * 질문 길이를 검증한 뒤 동일 임베딩 모델로 가장 가까운 청크를 조회한다.
     *
     * @param query 사용자가 입력한 자연어 질문
     * @return 검색 내용과 cosine distance 기반 점수
     */
    public SearchResponse search(Long ownerUserId, String query) {
        validateQuery(query);
        float[] queryEmbedding = embeddingService.embed(query);
        embeddingValidator.validate(queryEmbedding);
        return vectorSearchRepository.findNearest(ownerUserId, queryEmbedding)
                .map(result -> new SearchResponse(
                        result.documentId(),
                        result.documentVersionId(),
                        result.documentTitle(),
                        result.versionNo(),
                        result.chunkNo(),
                        result.pageNo(),
                        result.sourceType(),
                        result.sourceIndex(),
                        result.sourceLabel(),
                        result.content(),
                        result.distance(),
                        result.score()))
                .orElseThrow(SearchResultNotFoundException::new);
    }

    /**
     * Finds up to five active source chunks for the authenticated user's query.
     * An empty result is a valid evidence-search response.
     */
    public List<CareerEvidenceSearchResponse> searchCareerEvidence(Long ownerUserId, String query) {
        return searchCareerEvidenceV2(ownerUserId, query).results();
    }

    /**
     * Searches the authenticated user's active evidence and reports a normal product state.
     */
    public CareerEvidenceSearchV2Response searchCareerEvidenceV2(Long ownerUserId, String query) {
        validateQuery(query);
        float[] queryEmbedding = embeddingService.embed(query);
        embeddingValidator.validate(queryEmbedding);

        SearchProfile selectedProfile = searchProperties.selectedProfile();
        List<VectorSearchResult> candidates = selectedProfile == SearchProfile.LEGACY_DENSE_V1
                ? vectorSearchRepository.findCareerEvidence(ownerUserId, queryEmbedding)
                : vectorSearchRepository.findCareerEvidenceCandidates(ownerUserId, queryEmbedding);
        if (candidates.isEmpty()) {
            return emptyOutcome(CareerEvidenceSearchState.NO_SEARCHABLE_DOCUMENTS);
        }

        List<VectorSearchResult> selected = selectedProfile == SearchProfile.LEGACY_DENSE_V1
                ? candidates
                : shortGeneralExactTokenRescueProfile.apply(query, candidates).results();
        selected = deduplicateExactPresentationContent(selected);
        if (selected.isEmpty()) {
            return emptyOutcome(switch (compositeSearchProfile.resolveIntent(query)) {
                case GENERAL -> CareerEvidenceSearchState.NO_RELEVANT_RESULTS;
                case COMPLETED_RELEASE_EVIDENCE -> CareerEvidenceSearchState.NO_EVIDENCE;
            });
        }
        return new CareerEvidenceSearchV2Response(
                CareerEvidenceSearchState.EVIDENCE_FOUND,
                selected.stream()
                        .map(result -> toCareerEvidenceResponse(ownerUserId, query, result))
                        .toList());
    }

    private static CareerEvidenceSearchV2Response emptyOutcome(CareerEvidenceSearchState state) {
        return new CareerEvidenceSearchV2Response(state, List.of());
    }

    private static List<VectorSearchResult> deduplicateExactPresentationContent(
            List<VectorSearchResult> selected) {
        Set<String> seenContent = new LinkedHashSet<>();
        return selected.stream()
                .filter(result -> seenContent.add(presentationContentKey(result.content())))
                .toList();
    }

    private static String presentationContentKey(String content) {
        return Objects.requireNonNullElse(content, "")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .strip();
    }

    private CareerEvidenceSearchResponse toCareerEvidenceResponse(
            Long ownerUserId,
            String query,
            VectorSearchResult result) {
        EvidencePresentation evidence = evidenceExpansionService.select(ownerUserId, query, result);
        return new CareerEvidenceSearchResponse(
                result.chunkId(),
                result.documentId(),
                result.documentVersionId(),
                result.documentTitle(),
                result.versionNo(),
                result.content(),
                evidence.snippet(),
                result.sourceType(),
                result.sourceIndex(),
                result.sourceLabel(),
                evidence.evidenceChunkId(),
                evidence.evidenceSourceType(),
                evidence.evidenceSourceIndex(),
                evidence.evidenceSourceLabel(),
                result.distance(),
                result.score());
    }

    private void validateQuery(String query) {
        if (query == null || query.isBlank()) {
            throw new InvalidSearchQueryException("query must not be blank");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new InvalidSearchQueryException("query must be at most 500 characters");
        }
    }
}
