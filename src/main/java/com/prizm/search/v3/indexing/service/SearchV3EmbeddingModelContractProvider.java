package com.prizm.search.v3.indexing.service;

import com.prizm.search.v3.indexing.model.SearchV3EmbeddingModelContract;

/** Search V3 embedding generation에 사용할 실제 local model identity를 해석한다. */
public interface SearchV3EmbeddingModelContractProvider {

    SearchV3EmbeddingModelContract resolve();
}
