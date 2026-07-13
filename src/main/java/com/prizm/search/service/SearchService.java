package com.prizm.search.service;

import com.prizm.embedding.service.EmbeddingService;
import com.prizm.search.dto.response.SearchResponse;
import com.prizm.search.exception.InvalidSearchQueryException;
import com.prizm.search.exception.SearchResultNotFoundException;
import com.prizm.search.repository.VectorSearchRepository;
import org.springframework.stereotype.Service;

/**
 * 질문을 임베딩하고 pgvector exact cosine 검색 결과를 API 응답으로 변환한다.
 *
 * <p>현재 업로드 문서는 QUARANTINED 상태이고 자동 청크·임베딩이 아직 없으므로 검색 행이 생성되지 않는다.
 * 따라서 이 단계에서는 격리 문서가 검색 결과에 나오지 않지만, 상태 필터 자체는 아직 이 서비스의 책임이 아니다.</p>
 */
@Service
public class SearchService {

    public static final int MAX_QUERY_LENGTH = 500;

    private final EmbeddingService embeddingService;
    private final VectorSearchRepository vectorSearchRepository;

    public SearchService(EmbeddingService embeddingService, VectorSearchRepository vectorSearchRepository) {
        this.embeddingService = embeddingService;
        this.vectorSearchRepository = vectorSearchRepository;
    }

    /**
     * 질문 길이를 검증한 뒤 동일 임베딩 모델로 가장 가까운 청크를 조회한다.
     *
     * @param query 사용자가 입력한 자연어 질문
     * @return 검색 내용과 cosine distance 기반 점수
     */
    public SearchResponse search(String query) {
        validateQuery(query);
        float[] queryEmbedding = embeddingService.embed(query);
        return vectorSearchRepository.findNearest(queryEmbedding)
                .map(result -> new SearchResponse(result.content(), result.distance(), result.score()))
                .orElseThrow(SearchResultNotFoundException::new);
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
