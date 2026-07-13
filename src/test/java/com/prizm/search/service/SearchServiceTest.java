package com.prizm.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.embedding.service.EmbeddingService;
import com.prizm.search.dto.response.SearchResponse;
import com.prizm.search.exception.InvalidSearchQueryException;
import com.prizm.search.exception.SearchResultNotFoundException;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.repository.VectorSearchResult;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** 임베딩 호출과 검색 결과 변환·실패 조건을 단위 검증한다. */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    EmbeddingService embeddingService;

    @Mock
    VectorSearchRepository vectorSearchRepository;

    @Test
    void returnsNearestChunkFromExactSearchRepository() {
        float[] embedding = new float[1024];
        VectorSearchResult repositoryResult =
                new VectorSearchResult(
                        10L,
                        20L,
                        "휴가 안내",
                        1,
                        1,
                        null,
                        "연차 신청은 인사 시스템에서 진행합니다.",
                        0.2d,
                        0.8d);
        when(embeddingService.embed("휴가는 어디에서 신청하나요?")).thenReturn(embedding);
        when(vectorSearchRepository.findNearest(7L, embedding)).thenReturn(Optional.of(repositoryResult));

        SearchResponse result = new SearchService(embeddingService, vectorSearchRepository)
                .search(7L, "휴가는 어디에서 신청하나요?");

        assertThat(result).isEqualTo(
                new SearchResponse(
                        10L,
                        20L,
                        "휴가 안내",
                        1,
                        1,
                        null,
                        "연차 신청은 인사 시스템에서 진행합니다.",
                        0.2d,
                        0.8d));
        verify(vectorSearchRepository).findNearest(7L, embedding);
    }

    @Test
    void rejectsBlankQueryBeforeEmbedding() {
        assertThatThrownBy(() -> new SearchService(embeddingService, vectorSearchRepository).search(7L, " "))
                .isInstanceOf(InvalidSearchQueryException.class)
                .hasMessage("query must not be blank");
    }

    @Test
    void signalsWhenNoChunkExists() {
        float[] embedding = new float[1024];
        when(embeddingService.embed("검색어")).thenReturn(embedding);
        when(vectorSearchRepository.findNearest(7L, embedding)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new SearchService(embeddingService, vectorSearchRepository).search(7L, "검색어"))
                .isInstanceOf(SearchResultNotFoundException.class);
    }
}
