package com.prizm.search;

import com.prizm.embedding.EmbeddingService;
import org.springframework.stereotype.Service;

@Service
public class SearchService {

    public static final int MAX_QUERY_LENGTH = 500;

    private final EmbeddingService embeddingService;
    private final VectorSearchRepository vectorSearchRepository;

    public SearchService(EmbeddingService embeddingService, VectorSearchRepository vectorSearchRepository) {
        this.embeddingService = embeddingService;
        this.vectorSearchRepository = vectorSearchRepository;
    }

    public SearchResponse search(String query) {
        validateQuery(query);
        float[] queryEmbedding = embeddingService.embed(query);
        return vectorSearchRepository.findNearest(queryEmbedding)
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
