package com.prizm.search.v3.query.service;

import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.search.exception.InvalidSearchQueryException;
import com.prizm.search.v3.indexing.model.SearchV3EmbeddingModelContract;
import com.prizm.search.v3.indexing.service.SearchV3EmbeddingModelContractProvider;
import com.prizm.search.v3.query.model.SearchV3QueryResult;
import java.util.Arrays;
import org.springframework.stereotype.Service;

/** 실제 Ollama query vector를 ACTIVE Search V3 shadow inventory에 적용하는 비공개 runtime 서비스다. */
@Service
public class SearchV3ShadowQueryService {

    private static final int MAX_QUERY_LENGTH = 500;

    private final EmbeddingService embeddingService;
    private final EmbeddingValidator embeddingValidator;
    private final SearchV3EmbeddingModelContractProvider modelContractProvider;
    private final SearchV3ShadowQueryTransaction transaction;

    public SearchV3ShadowQueryService(
            EmbeddingService embeddingService,
            EmbeddingValidator embeddingValidator,
            SearchV3EmbeddingModelContractProvider modelContractProvider,
            SearchV3ShadowQueryTransaction transaction) {
        this.embeddingService = embeddingService;
        this.embeddingValidator = embeddingValidator;
        this.modelContractProvider = modelContractProvider;
        this.transaction = transaction;
    }

    public SearchV3QueryResult search(long ownerUserId, String query) {
        validate(ownerUserId, query);
        SearchV3EmbeddingModelContract before = modelContractProvider.resolve();
        float[] queryVector = embeddingService.embed(query);
        embeddingValidator.validate(queryVector);
        SearchV3EmbeddingModelContract after = modelContractProvider.resolve();
        if (!before.equals(after)) {
            Arrays.fill(queryVector, 0.0f);
            throw new IllegalStateException("Search V3 embedding model contract changed during query embedding.");
        }
        return transaction.search(ownerUserId, query, queryVector, before);
    }

    private static void validate(long ownerUserId, String query) {
        if (ownerUserId < 1) {
            throw new IllegalArgumentException("ownerUserId must be positive");
        }
        if (query == null || query.isBlank()) {
            throw new InvalidSearchQueryException("query must not be blank");
        }
        if (query.length() > MAX_QUERY_LENGTH) {
            throw new InvalidSearchQueryException("query must be at most 500 characters");
        }
    }
}
