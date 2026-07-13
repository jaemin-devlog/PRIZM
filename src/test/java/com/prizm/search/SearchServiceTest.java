package com.prizm.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.embedding.EmbeddingService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock
    EmbeddingService embeddingService;

    @Mock
    VectorSearchRepository vectorSearchRepository;

    @Test
    void returnsNearestChunkFromExactSearchRepository() {
        float[] embedding = new float[1024];
        SearchResponse expected = new SearchResponse("연차 신청은 인사 시스템에서 진행합니다.", 0.2d, 0.8d);
        when(embeddingService.embed("휴가는 어디에서 신청하나요?")).thenReturn(embedding);
        when(vectorSearchRepository.findNearest(embedding)).thenReturn(Optional.of(expected));

        SearchResponse result = new SearchService(embeddingService, vectorSearchRepository)
                .search("휴가는 어디에서 신청하나요?");

        assertThat(result).isEqualTo(expected);
        verify(vectorSearchRepository).findNearest(embedding);
    }

    @Test
    void rejectsBlankQueryBeforeEmbedding() {
        assertThatThrownBy(() -> new SearchService(embeddingService, vectorSearchRepository).search(" "))
                .isInstanceOf(InvalidSearchQueryException.class)
                .hasMessage("query must not be blank");
    }

    @Test
    void signalsWhenNoChunkExists() {
        float[] embedding = new float[1024];
        when(embeddingService.embed("검색어")).thenReturn(embedding);
        when(vectorSearchRepository.findNearest(embedding)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new SearchService(embeddingService, vectorSearchRepository).search("검색어"))
                .isInstanceOf(SearchResultNotFoundException.class);
    }
}
