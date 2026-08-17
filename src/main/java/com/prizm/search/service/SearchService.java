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
import com.prizm.search.profile.NaturalLanguageQueryFallback;
import com.prizm.search.profile.NumericAnchorRescueProfile;
import com.prizm.search.profile.NumericQueryAnchors;
import com.prizm.search.profile.SearchIntent;
import com.prizm.search.profile.ShortGeneralExactTokenRescueProfile;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.repository.VectorSearchResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(SearchService.class);

    private final EmbeddingService embeddingService;
    private final EmbeddingValidator embeddingValidator;
    private final VectorSearchRepository vectorSearchRepository;
    private final SearchProperties searchProperties;
    private final CompositeSearchProfile compositeSearchProfile;
    private final ShortGeneralExactTokenRescueProfile shortGeneralExactTokenRescueProfile;
    private final NumericAnchorRescueProfile numericAnchorRescueProfile;
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
        this.numericAnchorRescueProfile = new NumericAnchorRescueProfile(compositeSearchProfile);
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

        Set<String> guardedIdentifiers = selectedProfile == SearchProfile.LEGACY_DENSE_V1
                ? Set.of()
                : compositeSearchProfile.strongIdentifiersForEvidenceGuard(query);
        if (selectedProfile != SearchProfile.LEGACY_DENSE_V1) {
            if (!guardedIdentifiers.isEmpty()
                    && !vectorSearchRepository.hasAllActiveIdentifiers(
                            ownerUserId, guardedIdentifiers)) {
                return emptyOutcome(emptyStateFor(query));
            }
        }

        List<VectorSearchResult> selected;
        if (selectedProfile == SearchProfile.LEGACY_DENSE_V1) {
            selected = candidates;
        } else {
            selected = selectCompositeResults(
                    query,
                    List.of(query),
                    candidates,
                    NaturalLanguageQueryFallback.requiresDirectAnchor(query));
            selected = keepExactContextualNumericEvidence(query, selected);
            boolean fallbackAllowed = compositeSearchProfile.resolveIntent(query) == SearchIntent.GENERAL
                    || NaturalLanguageQueryFallback.isExperienceRequest(query);
            if (selected.isEmpty() && fallbackAllowed) {
                List<String> variants = NaturalLanguageQueryFallback.variants(query).stream()
                        .filter(variant -> NaturalLanguageQueryFallback.preservesRequiredAnchors(
                                query, variant, guardedIdentifiers))
                        .toList();
                List<VectorSearchResult> mergedCandidates = candidates;
                List<String> anchorQueries = new ArrayList<>();
                anchorQueries.add(query);
                int executedVariants = 0;
                for (String fallbackQuery : variants) {
                    float[] fallbackEmbedding = embeddingService.embed(fallbackQuery);
                    embeddingValidator.validate(fallbackEmbedding);
                    List<VectorSearchResult> fallbackCandidates =
                            vectorSearchRepository.findCareerEvidenceCandidates(
                                    ownerUserId,
                                    fallbackEmbedding);
                    executedVariants++;
                    anchorQueries.add(fallbackQuery);
                    mergedCandidates = mergeCandidates(mergedCandidates, fallbackCandidates);
                    selected = selectCompositeResults(
                            fallbackQuery,
                            anchorQueries,
                            mergedCandidates,
                            true);
                    selected = keepExactContextualNumericEvidence(query, selected);
                    if (!selected.isEmpty()) {
                        break;
                    }
                }
                if (executedVariants > 0) {
                    LOGGER.info("P3_VARIANT_FALLBACK variants={}", executedVariants);
                }
            }
            if (selected.isEmpty()) {
                Set<String> normalizedNumbers = NumericQueryAnchors.extract(query).stream()
                        .filter(NumericQueryAnchors.NumericAnchor::hasUnit)
                        .map(NumericQueryAnchors.NumericAnchor::number)
                        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
                if (!normalizedNumbers.isEmpty()) {
                    List<VectorSearchResult> numericCandidates =
                            vectorSearchRepository.findNumericAnchorCandidates(
                                    ownerUserId,
                                    queryEmbedding,
                                    normalizedNumbers);
                    selected = numericAnchorRescueProfile.apply(query, numericCandidates);
                }
            }
        }
        selected = deduplicateExactPresentationContent(selected);
        if (selected.isEmpty()) {
            return emptyOutcome(emptyStateFor(query));
        }
        return new CareerEvidenceSearchV2Response(
                CareerEvidenceSearchState.EVIDENCE_FOUND,
                selected.stream()
                        .map(result -> toCareerEvidenceResponse(ownerUserId, query, result))
                        .toList());
    }

    private static List<VectorSearchResult> keepExactContextualNumericEvidence(
            String query,
            List<VectorSearchResult> selected) {
        boolean hasContextualNumericAnchor = NumericQueryAnchors.extract(query).stream()
                .anyMatch(NumericQueryAnchors.NumericAnchor::hasUnit);
        if (!hasContextualNumericAnchor) {
            return selected;
        }
        return selected.stream()
                .filter(candidate -> NumericQueryAnchors.hasContextualMatch(query, candidate.content()))
                .toList();
    }

    private List<VectorSearchResult> selectCompositeResults(
            String searchQuery,
            List<String> anchorQueries,
            List<VectorSearchResult> candidates,
            boolean requireDirectAnchor) {
        List<VectorSearchResult> selected =
                shortGeneralExactTokenRescueProfile.apply(searchQuery, candidates).results();
        if (!requireDirectAnchor) {
            return selected;
        }
        return selected.stream()
                .filter(candidate -> anchorQueries.stream().anyMatch(anchorQuery ->
                        NaturalLanguageQueryFallback.hasDirectAnchor(
                                anchorQuery,
                                candidate.content())))
                .toList();
    }

    static List<VectorSearchResult> mergeCandidates(
            List<VectorSearchResult> existing,
            List<VectorSearchResult> incoming) {
        Map<Long, VectorSearchResult> byChunkId = new LinkedHashMap<>();
        existing.forEach(candidate -> byChunkId.put(candidate.chunkId(), candidate));
        incoming.forEach(candidate -> byChunkId.merge(
                candidate.chunkId(),
                candidate,
                (current, replacement) -> replacement.score() > current.score()
                        ? replacement
                        : current));
        return byChunkId.values().stream()
                .sorted(Comparator.comparingDouble(VectorSearchResult::score)
                        .reversed()
                        .thenComparing(VectorSearchResult::chunkId))
                .toList();
    }

    private static CareerEvidenceSearchV2Response emptyOutcome(CareerEvidenceSearchState state) {
        return new CareerEvidenceSearchV2Response(state, List.of());
    }

    private CareerEvidenceSearchState emptyStateFor(String query) {
        return switch (compositeSearchProfile.resolveIntent(query)) {
            case GENERAL -> CareerEvidenceSearchState.NO_RELEVANT_RESULTS;
            case COMPLETED_RELEASE_EVIDENCE -> CareerEvidenceSearchState.NO_EVIDENCE;
        };
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
