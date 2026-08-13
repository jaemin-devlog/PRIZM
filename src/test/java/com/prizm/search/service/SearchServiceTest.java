package com.prizm.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.config.SearchProfile;
import com.prizm.search.config.SearchProperties;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.dto.response.CareerEvidenceSearchState;
import com.prizm.search.dto.response.CareerEvidenceSearchV2Response;
import com.prizm.search.dto.response.SearchResponse;
import com.prizm.search.exception.InvalidSearchQueryException;
import com.prizm.search.exception.SearchResultNotFoundException;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.repository.EvidenceExpansionRepository;
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

    @Mock
    EvidenceExpansionRepository evidenceExpansionRepository;

    SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(
                embeddingService,
                new EmbeddingValidator(1024),
                vectorSearchRepository,
                new SearchProperties(SearchProfile.LEGACY_DENSE_V1.propertyValue()),
                new CompositeSearchProfile(),
                evidenceExpansionService(new SearchSnippetGenerator()));
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
                        "Spring Boot and Redis experience",
                        ChunkSourceType.TEXT_CHUNK,
                        1,
                        "텍스트 구간 1",
                        31L,
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
                        "Related backend evidence",
                        ChunkSourceType.TEXT_CHUNK,
                        2,
                        "텍스트 구간 2",
                        32L,
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
    void reportsNoSearchableDocumentsWhenTheLegacyProfileFindsNoActiveChunk() {
        float[] embedding = nonZeroEmbedding();
        when(embeddingService.embed("no searchable documents")).thenReturn(embedding);
        when(vectorSearchRepository.findCareerEvidence(7L, embedding)).thenReturn(List.of());

        CareerEvidenceSearchV2Response result =
                searchService.searchCareerEvidenceV2(7L, "no searchable documents");

        assertThat(result.state()).isEqualTo(CareerEvidenceSearchState.NO_SEARCHABLE_DOCUMENTS);
        assertThat(result.results()).isEmpty();
        verify(vectorSearchRepository, never()).findCareerEvidenceCandidates(7L, embedding);
    }

    @Test
    void defaultProfileUsesTwentyCandidatePathAndReturnsEvidenceFound() {
        SearchService optInService = defaultSearchService();
        float[] embedding = nonZeroEmbedding();
        VectorSearchResult direct = new VectorSearchResult(
                31L,
                10L,
                20L,
                "MatchLedger 합성 포트폴리오",
                1,
                1,
                2,
                ChunkSourceType.PAGE,
                2,
                "2페이지",
                "MatchLedger는 DB 행 잠금과 상태 재확인으로 중복 확정을 방지했다.",
                0.30d,
                0.70d);
        when(embeddingService.embed("MatchLedger에서 중복 확정을 어떻게 방지했어?")).thenReturn(embedding);
        when(vectorSearchRepository.findCareerEvidenceCandidates(7L, embedding)).thenReturn(List.of(direct));

        CareerEvidenceSearchV2Response result = optInService.searchCareerEvidenceV2(
                7L, "MatchLedger에서 중복 확정을 어떻게 방지했어?");

        assertThat(result.state()).isEqualTo(CareerEvidenceSearchState.EVIDENCE_FOUND);
        assertThat(result.results()).extracting(CareerEvidenceSearchResponse::chunkId).containsExactly(31L);
        assertThat(optInService.searchCareerEvidence(
                        7L, "MatchLedger에서 중복 확정을 어떻게 방지했어?"))
                .isEqualTo(result.results());
        verify(vectorSearchRepository, never()).findCareerEvidence(7L, embedding);
    }

    @Test
    void defaultGeneralProfileReturnsDenseCandidateWithoutStringGateFiltering() {
        SearchService optInService = defaultSearchService();
        float[] embedding = nonZeroEmbedding();
        VectorSearchResult unrelated = new VectorSearchResult(
                32L,
                10L,
                20L,
                "합성 알림 문서",
                1,
                1,
                null,
                ChunkSourceType.TEXT_CHUNK,
                1,
                "텍스트 구간 1",
                "외부 푸시 장애에도 내부 알림 데이터를 보존했다.",
                0.20d,
                0.80d);
        when(embeddingService.embed("Kafka를 프로젝트에 적용한 근거가 있어?")).thenReturn(embedding);
        when(vectorSearchRepository.findCareerEvidenceCandidates(7L, embedding)).thenReturn(List.of(unrelated));

        CareerEvidenceSearchV2Response result =
                optInService.searchCareerEvidenceV2(7L, "Kafka를 프로젝트에 적용한 근거가 있어?");

        assertThat(result.state()).isEqualTo(CareerEvidenceSearchState.EVIDENCE_FOUND);
        assertThat(result.results())
                .extracting(CareerEvidenceSearchResponse::chunkId)
                .containsExactly(32L);
        assertThat(optInService.searchCareerEvidence(7L, "Kafka를 프로젝트에 적용한 근거가 있어?"))
                .isEqualTo(result.results());
    }

    @Test
    void presentationRemovesExactCrossDocumentDuplicatesWithoutChangingTheFirstResult() {
        SearchService optInService = defaultSearchService();
        float[] embedding = nonZeroEmbedding();
        String repeatedContent = "정재민 Java / Spring Boot Backend";
        VectorSearchResult first = careerEvidenceCandidate(
                71L, 10L, 20L, "정재민 신입 백엔드", repeatedContent, 0.82d);
        VectorSearchResult duplicate = careerEvidenceCandidate(
                72L, 11L, 21L, "정재민 신입 백엔드 이력서", repeatedContent, 0.81d);
        VectorSearchResult distinct = careerEvidenceCandidate(
                73L,
                12L,
                22L,
                "정재민 백엔드 포트폴리오",
                "Java / Spring Boot로 AirConnect 알림 시스템을 운영하고 개선했습니다.",
                0.80d);
        when(embeddingService.embed("Springboot 활용 경험")).thenReturn(embedding);
        when(vectorSearchRepository.findCareerEvidenceCandidates(7L, embedding))
                .thenReturn(List.of(first, duplicate, distinct));

        CareerEvidenceSearchV2Response result =
                optInService.searchCareerEvidenceV2(7L, "Springboot 활용 경험");

        assertThat(result.state()).isEqualTo(CareerEvidenceSearchState.EVIDENCE_FOUND);
        assertThat(result.results())
                .extracting(
                        CareerEvidenceSearchResponse::chunkId,
                        CareerEvidenceSearchResponse::documentId)
                .containsExactly(
                        tuple(71L, 10L),
                        tuple(73L, 12L));
        assertThat(result.results())
                .extracting(
                        CareerEvidenceSearchResponse::score,
                        CareerEvidenceSearchResponse::distance)
                .containsExactly(
                        tuple(first.score(), first.distance()),
                        tuple(distinct.score(), distinct.distance()));
    }

    @Test
    void defaultGeneralProfileReportsNoRelevantResultsWhenActiveCandidatesFailEligibility() {
        SearchService optInService = defaultSearchService();
        float[] embedding = nonZeroEmbedding();
        VectorSearchResult belowDenseFloor = careerEvidenceCandidate(
                33L, "Redis caching experience", 0.49d);
        when(embeddingService.embed("Redis experience")).thenReturn(embedding);
        when(vectorSearchRepository.findCareerEvidenceCandidates(7L, embedding))
                .thenReturn(List.of(belowDenseFloor));

        CareerEvidenceSearchV2Response result =
                optInService.searchCareerEvidenceV2(7L, "Redis experience");

        assertThat(result.state()).isEqualTo(CareerEvidenceSearchState.NO_RELEVANT_RESULTS);
        assertThat(result.results()).isEmpty();
    }

    @Test
    void defaultGeneralProfileRescuesValidatedShortExactTokensAndKeepsOriginalScores() {
        SearchService optInService = defaultSearchService();
        float[] embedding = nonZeroEmbedding();
        List<ShortRescueCase> scenarios = List.of(
                new ShortRescueCase("알림", "Outbox 기반 알림 처리 경험", 0.4946749800057917d),
                new ShortRescueCase("동시성", "동시성 제어 경험", 0.49050966744799074d),
                new ShortRescueCase("토큰", "인증 토큰 갱신 경험", 0.4999384432535814d));

        long chunkId = 100L;
        for (ShortRescueCase scenario : scenarios) {
            VectorSearchResult candidate = careerEvidenceCandidate(
                    chunkId,
                    scenario.content(),
                    scenario.score());
            when(embeddingService.embed(scenario.query())).thenReturn(embedding);
            when(vectorSearchRepository.findCareerEvidenceCandidates(7L, embedding))
                    .thenReturn(List.of(candidate));

            CareerEvidenceSearchV2Response result =
                    optInService.searchCareerEvidenceV2(7L, scenario.query());

            assertThat(result.state())
                    .as(scenario.query())
                    .isEqualTo(CareerEvidenceSearchState.EVIDENCE_FOUND);
            assertThat(result.results())
                    .as(scenario.query())
                    .extracting(
                            CareerEvidenceSearchResponse::chunkId,
                            CareerEvidenceSearchResponse::score,
                            CareerEvidenceSearchResponse::distance)
                    .containsExactly(tuple(chunkId, scenario.score(), 1.0d - scenario.score()));
            chunkId++;
        }
    }

    @Test
    void defaultGeneralProfileDoesNotRescueParticleOrSubstringOnlyMatches() {
        SearchService optInService = defaultSearchService();
        float[] embedding = nonZeroEmbedding();
        List<ShortRescueCase> scenarios = List.of(
                new ShortRescueCase("동시성은", "동시성 제어 경험", 0.494716643657d),
                new ShortRescueCase("알림톡", "FCM 알림 처리 경험", 0.499d));

        long chunkId = 110L;
        for (ShortRescueCase scenario : scenarios) {
            VectorSearchResult candidate = careerEvidenceCandidate(
                    chunkId,
                    scenario.content(),
                    scenario.score());
            when(embeddingService.embed(scenario.query())).thenReturn(embedding);
            when(vectorSearchRepository.findCareerEvidenceCandidates(7L, embedding))
                    .thenReturn(List.of(candidate));

            CareerEvidenceSearchV2Response result =
                    optInService.searchCareerEvidenceV2(7L, scenario.query());

            assertThat(result.state())
                    .as(scenario.query())
                    .isEqualTo(CareerEvidenceSearchState.NO_RELEVANT_RESULTS);
            assertThat(result.results()).as(scenario.query()).isEmpty();
            chunkId++;
        }
    }

    @Test
    void defaultCompletedReleaseProfileNeverUsesShortExactTokenRescue() {
        SearchService optInService = defaultSearchService();
        float[] embedding = nonZeroEmbedding();
        String query = "PRIZM 서비스를 배포했나요?";
        VectorSearchResult directClaim = careerEvidenceCandidate(
                120L,
                "PRIZM 서비스를 배포했습니다.",
                0.499d);
        when(embeddingService.embed(query)).thenReturn(embedding);
        when(vectorSearchRepository.findCareerEvidenceCandidates(7L, embedding))
                .thenReturn(List.of(directClaim));

        CareerEvidenceSearchV2Response result =
                optInService.searchCareerEvidenceV2(7L, query);

        assertThat(result.state()).isEqualTo(CareerEvidenceSearchState.NO_EVIDENCE);
        assertThat(result.results()).isEmpty();
    }

    @Test
    void defaultCompletedReleaseProfileReportsNoEvidenceWhenClaimGateRejectsActiveCandidate() {
        SearchService optInService = defaultSearchService();
        float[] embedding = nonZeroEmbedding();
        VectorSearchResult questionOnly = careerEvidenceCandidate(
                34L, "주문 API를 배포했나요?", 0.90d);
        when(embeddingService.embed("주문 API를 출시했나요?")).thenReturn(embedding);
        when(vectorSearchRepository.findCareerEvidenceCandidates(7L, embedding))
                .thenReturn(List.of(questionOnly));

        CareerEvidenceSearchV2Response result =
                optInService.searchCareerEvidenceV2(7L, "주문 API를 출시했나요?");

        assertThat(result.state()).isEqualTo(CareerEvidenceSearchState.NO_EVIDENCE);
        assertThat(result.results()).isEmpty();
    }

    @Test
    void defaultCompletedReleaseProfileReturnsEvidenceFoundForVerifiedClaim() {
        SearchService optInService = defaultSearchService();
        float[] embedding = nonZeroEmbedding();
        VectorSearchResult directClaim = careerEvidenceCandidate(
                35L, "2025년 3월 14일에 주문 API를 배포했습니다.", 0.90d);
        when(embeddingService.embed("주문 API를 출시했나요?")).thenReturn(embedding);
        when(vectorSearchRepository.findCareerEvidenceCandidates(7L, embedding))
                .thenReturn(List.of(directClaim));

        CareerEvidenceSearchV2Response result =
                optInService.searchCareerEvidenceV2(7L, "주문 API를 출시했나요?");

        assertThat(result.state()).isEqualTo(CareerEvidenceSearchState.EVIDENCE_FOUND);
        assertThat(result.results())
                .extracting(CareerEvidenceSearchResponse::chunkId, CareerEvidenceSearchResponse::content)
                .containsExactly(tuple(35L, "2025년 3월 14일에 주문 API를 배포했습니다."));
    }

    @Test
    void preservesSelectedResultAndUsesFullContentWhenSnippetGenerationFails() {
        SearchSnippetGenerator failingGenerator = mock(SearchSnippetGenerator.class);
        SearchService serviceWithFailingSnippet = new SearchService(
                embeddingService,
                new EmbeddingValidator(1024),
                vectorSearchRepository,
                new SearchProperties(SearchProperties.DEFAULT_PROFILE),
                new CompositeSearchProfile(),
                evidenceExpansionService(failingGenerator));
        float[] embedding = nonZeroEmbedding();
        String content = "Redis 캐시를 적용했다. 장애 상황에서도 결과 순서를 보존했다.";
        VectorSearchResult selected = careerEvidenceCandidate(36L, content, 0.82d);
        when(embeddingService.embed("Redis 캐싱 경험")).thenReturn(embedding);
        when(vectorSearchRepository.findCareerEvidenceCandidates(7L, embedding))
                .thenReturn(List.of(selected));
        when(failingGenerator.select("Redis 캐싱 경험", content))
                .thenThrow(new IllegalStateException("synthetic snippet failure"));

        CareerEvidenceSearchV2Response result =
                serviceWithFailingSnippet.searchCareerEvidenceV2(7L, "Redis 캐싱 경험");

        assertThat(result.state()).isEqualTo(CareerEvidenceSearchState.EVIDENCE_FOUND);
        assertThat(result.results())
                .extracting(
                        CareerEvidenceSearchResponse::chunkId,
                        CareerEvidenceSearchResponse::content,
                        CareerEvidenceSearchResponse::snippet,
                        CareerEvidenceSearchResponse::distance,
                        CareerEvidenceSearchResponse::score)
                .containsExactly(tuple(36L, content, content, selected.distance(), selected.score()));
    }

    @Test
    void presentationSnippetCannotChangeSelectedResultIdentityOrderOrScore() {
        SearchSnippetGenerator presentationGenerator = mock(SearchSnippetGenerator.class);
        SearchService serviceWithPresentation = new SearchService(
                embeddingService,
                new EmbeddingValidator(1024),
                vectorSearchRepository,
                new SearchProperties(SearchProperties.DEFAULT_PROFILE),
                new CompositeSearchProfile(),
                evidenceExpansionService(presentationGenerator));
        float[] embedding = nonZeroEmbedding();
        String query = "Springboot 활용 경험";
        VectorSearchResult first = careerEvidenceCandidate(
                201L,
                10L,
                20L,
                "백엔드 이력서",
                "Java / Spring Boot. Spring Boot로 인증 API를 구현했다.",
                0.83d);
        VectorSearchResult second = careerEvidenceCandidate(
                202L,
                11L,
                21L,
                "백엔드 포트폴리오",
                "Spring Boot 기반 서비스에서 인증 흐름을 통합했다.",
                0.79d);
        when(embeddingService.embed(query)).thenReturn(embedding);
        when(vectorSearchRepository.findCareerEvidenceCandidates(7L, embedding))
                .thenReturn(List.of(first, second));
        when(presentationGenerator.select(query, first.content()))
                .thenReturn(directSelection("Spring Boot로 인증 API를 구현했다."));
        when(presentationGenerator.select(query, second.content()))
                .thenReturn(directSelection("Spring Boot 기반 서비스에서 인증 흐름을 통합했다."));

        CareerEvidenceSearchV2Response result =
                serviceWithPresentation.searchCareerEvidenceV2(7L, query);

        assertThat(result.results())
                .extracting(
                        CareerEvidenceSearchResponse::chunkId,
                        CareerEvidenceSearchResponse::documentId,
                        CareerEvidenceSearchResponse::documentVersionId,
                        CareerEvidenceSearchResponse::score,
                        CareerEvidenceSearchResponse::distance)
                .containsExactly(
                        tuple(201L, 10L, 20L, first.score(), first.distance()),
                        tuple(202L, 11L, 21L, second.score(), second.distance()));
    }

    @Test
    void defaultProfileReportsNoSearchableDocumentsWhenCandidateSetIsEmpty() {
        SearchService optInService = defaultSearchService();
        float[] embedding = nonZeroEmbedding();
        when(embeddingService.embed("empty owner scope")).thenReturn(embedding);
        when(vectorSearchRepository.findCareerEvidenceCandidates(7L, embedding)).thenReturn(List.of());

        CareerEvidenceSearchV2Response result =
                optInService.searchCareerEvidenceV2(7L, "empty owner scope");

        assertThat(result.state()).isEqualTo(CareerEvidenceSearchState.NO_SEARCHABLE_DOCUMENTS);
        assertThat(result.results()).isEmpty();
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

    private VectorSearchResult careerEvidenceCandidate(long chunkId, String content, double score) {
        return careerEvidenceCandidate(
                chunkId, 10L, 20L, "Career record", content, score);
    }

    private VectorSearchResult careerEvidenceCandidate(
            long chunkId,
            long documentId,
            long documentVersionId,
            String documentTitle,
            String content,
            double score) {
        return new VectorSearchResult(
                chunkId,
                documentId,
                documentVersionId,
                documentTitle,
                1,
                Math.toIntExact(chunkId),
                null,
                ChunkSourceType.TEXT_CHUNK,
                Math.toIntExact(chunkId),
                "Text chunk " + chunkId,
                content,
                1.0d - score,
                score);
    }

    private SearchService defaultSearchService() {
        return new SearchService(
                embeddingService,
                new EmbeddingValidator(1024),
                vectorSearchRepository,
                new SearchProperties(SearchProperties.DEFAULT_PROFILE),
                new CompositeSearchProfile(),
                evidenceExpansionService(new SearchSnippetGenerator()));
    }

    private EvidenceExpansionService evidenceExpansionService(SearchSnippetGenerator generator) {
        return new EvidenceExpansionService(evidenceExpansionRepository, generator);
    }

    private SearchSnippetGenerator.SnippetSelection directSelection(String snippet) {
        return new SearchSnippetGenerator.SnippetSelection(
                snippet, false, 1, 0, true, false, false, 10_000);
    }

    private record ShortRescueCase(String query, String content, double score) {
    }
}
