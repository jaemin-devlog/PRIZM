package com.prizm.search.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.repository.VectorSearchResult;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositeSearchProfileSourceConsolidationTest {

    private final CompositeSearchProfile profile = new CompositeSearchProfile();

    @Test
    void preservesDifferentEvidenceChunksOnTheSamePdfPage() {
        VectorSearchResult alpha = candidate(
                1L,
                20L,
                2,
                4,
                ChunkSourceType.PAGE,
                "Project Alpha에서는 대기 작업을 분리해 처리 지연을 줄였다.",
                0.90d);
        VectorSearchResult beta = candidate(
                2L,
                20L,
                2,
                5,
                ChunkSourceType.PAGE,
                "Project Beta에서는 저장소 잠금으로 동시 갱신 충돌을 막았다.",
                0.80d);

        CompositeSearchProfile.Decision decision = profile.apply(
                "프로젝트 기술 기록", List.of(alpha, beta));

        assertThat(decision.results())
                .extracting(VectorSearchResult::chunkId)
                .containsExactly(1L, 2L);
    }

    @Test
    void consolidatesMeaningfullyOverlappingChunksOnTheSamePdfPage() {
        String overlap = "경계에서 반복되는 합성 프로젝트 처리 근거를 그대로 보존한다. ".repeat(4);
        VectorSearchResult first = candidate(
                1L,
                20L,
                2,
                4,
                ChunkSourceType.PAGE,
                "앞쪽 설명. " + overlap,
                0.90d);
        VectorSearchResult second = candidate(
                2L,
                20L,
                2,
                5,
                ChunkSourceType.PAGE,
                overlap + "뒤쪽 설명.",
                0.80d);

        CompositeSearchProfile.Decision decision = profile.apply(
                "합성 프로젝트 처리 기록", List.of(first, second));

        assertThat(decision.results()).hasSize(1);
    }

    @Test
    void consolidatesExactDuplicateChunksOnTheSamePdfPage() {
        String duplicate = "동일한 PDF 추출 결과가 반복 저장된 합성 근거 문장이다.".repeat(3);
        VectorSearchResult first = candidate(
                1L, 20L, 2, 4, ChunkSourceType.PAGE, duplicate, 0.90d);
        VectorSearchResult second = candidate(
                2L, 20L, 2, 5, ChunkSourceType.PAGE, duplicate, 0.80d);

        CompositeSearchProfile.Decision decision = profile.apply(
                "합성 PDF 기록", List.of(first, second));

        assertThat(decision.results()).hasSize(1);
    }

    @Test
    void preservesIdenticalContentWhenItComesFromDifferentPdfPages() {
        String content = "반복되는 요약 문장이지만 서로 다른 페이지에 기록된 합성 근거다.";
        VectorSearchResult firstPage = candidate(
                1L, 20L, 1, 1, ChunkSourceType.PAGE, content, 0.90d);
        VectorSearchResult secondPage = candidate(
                2L, 20L, 2, 2, ChunkSourceType.PAGE, content, 0.80d);

        CompositeSearchProfile.Decision decision = profile.apply(
                "합성 기록", List.of(firstPage, secondPage));

        assertThat(decision.results())
                .extracting(VectorSearchResult::chunkId)
                .containsExactly(1L, 2L);
    }

    @Test
    void keepsExistingTxtBoundaryOverlapConsolidation() {
        String overlap = "고정 길이 텍스트 청크 경계에서 반복되는 합성 근거다. ".repeat(4);
        VectorSearchResult first = candidate(
                1L,
                20L,
                1,
                1,
                ChunkSourceType.TEXT_CHUNK,
                "앞쪽 설명. " + overlap,
                0.90d);
        VectorSearchResult second = candidate(
                2L,
                20L,
                2,
                2,
                ChunkSourceType.TEXT_CHUNK,
                overlap + "뒤쪽 설명.",
                0.80d);

        CompositeSearchProfile.Decision decision = profile.apply(
                "합성 텍스트 기록", List.of(first, second));

        assertThat(decision.results()).hasSize(1);
    }

    @Test
    void doesNotConsolidateAcrossDocumentVersions() {
        String duplicate = "동일한 페이지 내용이어도 ACTIVE version 경계 밖과 합치지 않는다.".repeat(3);
        VectorSearchResult current = candidate(
                1L, 20L, 1, 1, ChunkSourceType.PAGE, duplicate, 0.90d);
        VectorSearchResult differentVersion = candidate(
                2L, 21L, 1, 1, ChunkSourceType.PAGE, duplicate, 0.80d);

        CompositeSearchProfile.Decision decision = profile.apply(
                "문서 버전 기록", List.of(current, differentVersion));

        assertThat(decision.results())
                .extracting(VectorSearchResult::chunkId)
                .containsExactly(1L, 2L);
    }

    @Test
    void consolidatesRepeatedEvidenceSpansWithoutRestoringPageLevelSourceDeduplication() {
        String repeated = "장애 원인을 재현하기 위해 요청 식별자와 배포 버전을 함께 남기고 재시도 순서를 검증했다. ".repeat(7);
        VectorSearchResult direct = candidate(
                1L,
                20L,
                2,
                4,
                ChunkSourceType.PAGE,
                "프로젝트 Alpha에서 Redis 장애 복구를 직접 구현했다. "
                        + repeated + "Alpha의 검증 결과를 별도로 기록했다.",
                0.90d);
        VectorSearchResult repeatedChunk = candidate(
                2L,
                20L,
                2,
                5,
                ChunkSourceType.PAGE,
                "후속 운영 기록. " + repeated + "Redis 관찰 항목을 정리했다.",
                0.80d);

        CompositeSearchProfile.Decision decision = profile.apply(
                "Redis 장애 복구 기록", List.of(direct, repeatedChunk));

        assertThat(decision.results()).extracting(VectorSearchResult::chunkId).containsExactly(1L);
    }

    @Test
    void preservesDifferentProjectsThatUseTheSameTechnology() {
        VectorSearchResult alpha = candidate(
                1L,
                20L,
                1,
                4,
                ChunkSourceType.PAGE,
                "프로젝트 Alpha에서는 Redis 지연을 줄이기 위해 읽기 경로를 분리했다.",
                0.90d);
        VectorSearchResult beta = candidate(
                2L,
                20L,
                2,
                5,
                ChunkSourceType.PAGE,
                "프로젝트 Beta에서는 Redis 장애에 대비해 재동기화 작업을 직접 구현했다.",
                0.80d);

        CompositeSearchProfile.Decision decision = profile.apply(
                "Redis 사용 경험", List.of(alpha, beta));

        assertThat(decision.results())
                .extracting(VectorSearchResult::chunkId)
                .containsExactly(1L, 2L);
    }

    private static VectorSearchResult candidate(
            long chunkId,
            long versionId,
            int sourceIndex,
            int chunkNo,
            ChunkSourceType sourceType,
            String content,
            double score) {
        return new VectorSearchResult(
                chunkId,
                10L,
                versionId,
                "합성 검색 문서",
                1,
                chunkNo,
                sourceType == ChunkSourceType.PAGE ? sourceIndex : null,
                sourceType,
                sourceIndex,
                sourceType == ChunkSourceType.PAGE
                        ? sourceIndex + "페이지"
                        : "텍스트 구간 " + sourceIndex,
                content,
                1.0d - score,
                score);
    }
}
