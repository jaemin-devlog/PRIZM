package com.prizm.search.v3.indexing.service;

import com.prizm.search.v3.indexing.model.SearchV3DispatchedJob;
import com.prizm.search.v3.indexing.model.SearchV3EmbeddingModelContract;
import com.prizm.search.v3.indexing.repository.SearchV3JobDispatchRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 현재 BGE-M3 계약에 아직 색인되지 않은 Production ACTIVE version을 한 건 dispatch한다. */
@Service
public class SearchV3JobDispatchService {

    private final SearchV3JobDispatchRepository repository;
    private final SearchV3EmbeddingModelContractProvider modelContractProvider;

    public SearchV3JobDispatchService(
            SearchV3JobDispatchRepository repository,
            SearchV3EmbeddingModelContractProvider modelContractProvider) {
        this.repository = repository;
        this.modelContractProvider = modelContractProvider;
    }

    @Transactional
    public Optional<SearchV3DispatchedJob> dispatchNext() {
        SearchV3EmbeddingModelContract model = modelContractProvider.resolve();
        return repository.dispatchNext(model);
    }
}
