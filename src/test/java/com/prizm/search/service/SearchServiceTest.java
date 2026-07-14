package com.prizm.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.dto.response.SearchResponse;
import com.prizm.search.exception.InvalidSearchQueryException;
import com.prizm.search.exception.SearchResultNotFoundException;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.repository.VectorSearchResult;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
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

    SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(
                embeddingService, new EmbeddingValidator(1024), vectorSearchRepository);
    }

    @Test
    void returnsNearestChunkFromExactSearchRepository() {
        float[] embedding = nonZeroEmbedding();
        VectorSearchResult repositoryResult =
                new VectorSearchResult(
                        30L,
                        10L,
                        20L,
                        "휴가 안내",
                        1,
                        1,
                        null,
                        ChunkSourceType.TEXT_CHUNK,
                        1,
                        "텍스트 구간 1",
                        "연차 신청은 인사 시스템에서 진행합니다.",
                        0.2d,
                        0.8d);
        when(embeddingService.embed("휴가는 어디에서 신청하나요?")).thenReturn(embedding);
        when(vectorSearchRepository.findNearest(7L, embedding)).thenReturn(Optional.of(repositoryResult));

        SearchResponse result = searchService.search(7L, "휴가는 어디에서 신청하나요?");

        assertThat(result).isEqualTo(
                new SearchResponse(
                        10L,
                        20L,
                        "휴가 안내",
                        1,
                        1,
                        null,
                        ChunkSourceType.TEXT_CHUNK,
                        1,
                        "텍스트 구간 1",
                        "연차 신청은 인사 시스템에서 진행합니다.",
                        0.2d,
                        0.8d));
        verify(vectorSearchRepository).findNearest(7L, embedding);
    }

    @Test
    void rejectsBlankQueryBeforeEmbedding() {
        assertThatThrownBy(() -> searchService.search(7L, " "))
                .isInstanceOf(InvalidSearchQueryException.class)
                .hasMessage("query must not be blank");
    }

    @Test
    void signalsWhenNoChunkExists() {
        float[] embedding = nonZeroEmbedding();
        when(embeddingService.embed("검색어")).thenReturn(embedding);
        when(vectorSearchRepository.findNearest(7L, embedding)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> searchService.search(7L, "검색어"))
                .isInstanceOf(SearchResultNotFoundException.class);
    }

    @Test
    void rejectsZeroNormEmbeddingBeforeSingleSearchRepositoryCall() {
        when(embeddingService.embed("zero vector")).thenReturn(new float[1024]);

        assertThatThrownBy(() -> searchService.search(7L, "zero vector"))
                .isInstanceOf(EmbeddingException.class)
                .extracting(exception -> ((EmbeddingException) exception).code())
                .isEqualTo(EmbeddingErrorCode.EMBEDDING_INVALID_RESPONSE);
        verify(vectorSearchRepository, never()).findNearest(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(float[].class));
    }

    @Test
    void returnsUpToFiveCareerEvidenceChunksWithoutChangingSingleSearchContract() {
        float[] embedding = nonZeroEmbedding();
        VectorSearchResult first = new VectorSearchResult(
                31L,
                10L,
                20L,
                "Career record",
                2,
                1,
                null,
                ChunkSourceType.TEXT_CHUNK,
                1,
                "텍스트 구간 1",
                "Spring Boot and Redis experience",
                0.1d,
                0.9d);
        VectorSearchResult second = new VectorSearchResult(
                32L,
                10L,
                20L,
                "Career record",
                2,
                2,
                null,
                ChunkSourceType.TEXT_CHUNK,
                2,
                "텍스트 구간 2",
                "Related backend evidence",
                0.2d,
                0.8d);
        when(embeddingService.embed("Spring Boot experience")).thenReturn(embedding);
        when(vectorSearchRepository.findCareerEvidence(7L, embedding)).thenReturn(List.of(first, second));

        List<CareerEvidenceSearchResponse> results = searchService.searchCareerEvidence(7L, "Spring Boot experience");

        assertThat(results).containsExactly(
                new CareerEvidenceSearchResponse(
                        31L,
                        10L,
                        20L,
                        "Career record",
                        2,
                        "Spring Boot and Redis experience",
                        ChunkSourceType.TEXT_CHUNK,
                        1,
                        "텍스트 구간 1",
                        0.1d,
                        0.9d),
                new CareerEvidenceSearchResponse(
                        32L,
                        10L,
                        20L,
                        "Career record",
                        2,
                        "Related backend evidence",
                        ChunkSourceType.TEXT_CHUNK,
                        2,
                        "텍스트 구간 2",
                        0.2d,
                        0.8d));
        verify(vectorSearchRepository).findCareerEvidence(7L, embedding);
    }

    @Test
    void returnsEmptyCareerEvidenceWhenNoActiveChunkExists() {
        float[] embedding = nonZeroEmbedding();
        when(embeddingService.embed("no matching evidence")).thenReturn(embedding);
        when(vectorSearchRepository.findCareerEvidence(7L, embedding)).thenReturn(List.of());

        List<CareerEvidenceSearchResponse> results = searchService.searchCareerEvidence(7L, "no matching evidence");

        assertThat(results).isEmpty();
    }

    @Test
    void rejectsOverlongCareerEvidenceQueryBeforeEmbedding() {
        String query = "x".repeat(SearchService.MAX_QUERY_LENGTH + 1);

        assertThatThrownBy(() -> searchService.searchCareerEvidence(7L, query))
                .isInstanceOf(InvalidSearchQueryException.class)
                .hasMessage("query must be at most 500 characters");
    }

    @Test
    void rejectsZeroNormEmbeddingBeforeCareerEvidenceRepositoryCall() {
        when(embeddingService.embed("zero evidence vector")).thenReturn(new float[1024]);

        assertThatThrownBy(() -> searchService.searchCareerEvidence(7L, "zero evidence vector"))
                .isInstanceOf(EmbeddingException.class)
                .extracting(exception -> ((EmbeddingException) exception).code())
                .isEqualTo(EmbeddingErrorCode.EMBEDDING_INVALID_RESPONSE);
        verify(vectorSearchRepository, never()).findCareerEvidence(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(float[].class));
    }

    private float[] nonZeroEmbedding() {
        float[] embedding = new float[1024];
        embedding[0] = 1.0f;
        return embedding;
    }
}
