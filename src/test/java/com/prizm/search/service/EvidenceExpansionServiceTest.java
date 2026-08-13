package com.prizm.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.repository.EvidenceChunk;
import com.prizm.search.repository.EvidenceExpansionRepository;
import com.prizm.search.repository.VectorSearchResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EvidenceExpansionServiceTest {

    @Mock
    EvidenceExpansionRepository repository;

    EvidenceExpansionService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceExpansionService(repository, new SearchSnippetGenerator());
    }

    @Test
    void keepsCurrentChunkWithoutExpansionWhenItAlreadyContainsDirectEvidence() {
        VectorSearchResult result = result(
                51L,
                "Worker는 처리 대상을 FOR UPDATE SKIP LOCKED로 조회해 중복 처리를 방지했습니다.",
                4,
                "4페이지");

        EvidencePresentation presentation = service.select(7L, "FOR UPDATE SKIP LOCKED", result);

        assertThat(presentation.evidenceChunkId()).isEqualTo(51L);
        assertThat(presentation.evidenceSourceLabel()).isEqualTo("4페이지");
        assertThat(presentation.snippet()).contains("FOR UPDATE SKIP LOCKED");
        verify(repository, never()).findActiveVersionChunks(7L, 10L, 20L);
    }

    @Test
    void expandsOnlyWithinTheSelectedOwnersDocumentAndActiveVersion() {
        VectorSearchResult result = result(
                58L,
                "교내 매칭 서비스를 운영하며 알림 시스템을 개선했습니다.",
                2,
                "2페이지");
        EvidenceChunk unrelated = chunk(
                59L,
                3,
                "3페이지",
                "동시 매칭 요청에서 중복 확정 문제를 해결했습니다.");
        EvidenceChunk direct = chunk(
                60L,
                5,
                "5페이지",
                "이메일 로그인과 Kakao 로그인이 분리되어 계정 관리 기준이 달라지는 문제를 확인했습니다.\n"
                        + "Spring Security를 공통 인증 진입점으로 두고 OAuth2/JWT 흐름을 통합했습니다.");
        when(repository.findActiveVersionChunks(7L, 10L, 20L))
                .thenReturn(List.of(unrelated, direct));

        EvidencePresentation presentation = service.select(
                7L,
                "이메일 로그인과 카카오 로그인을 통합한 경험",
                result);

        assertThat(presentation.evidenceChunkId()).isEqualTo(60L);
        assertThat(presentation.evidenceSourceLabel()).isEqualTo("5페이지");
        assertThat(presentation.snippet())
                .contains("이메일 로그인과 Kakao 로그인이 분리되어")
                .contains("OAuth2/JWT 흐름을 통합했습니다.")
                .doesNotContain("동시 매칭 요청");
        verify(repository).findActiveVersionChunks(7L, 10L, 20L);
    }

    @Test
    void expandedAnchorIncludesOneCompleteFollowingSourceSentence() {
        VectorSearchResult result = result(
                57L,
                "모바일 앱 경험을 개선했습니다.",
                1,
                "1페이지");
        EvidenceChunk direct = chunk(
                61L,
                2,
                "2페이지",
                "서버 재시작이나 배포 시 Spring Boot / MySQL / Redis를 각각 관리해야 하는 운영 부담 확인.\n"
                        + "배포 환경 구축을 담당해 GCP Ubuntu 서버에서 Docker Compose로 세 서비스를 함께 실행하도록 구성.\n"
                        + "도메인 연결과 HTTPS 설정도 적용했습니다.");
        when(repository.findActiveVersionChunks(7L, 10L, 20L)).thenReturn(List.of(direct));

        EvidencePresentation presentation = service.select(7L, "Springboot 활용 경험", result);

        assertThat(presentation.snippet())
                .isEqualTo("서버 재시작이나 배포 시 Spring Boot / MySQL / Redis를 각각 관리해야 하는 운영 부담 확인.\n"
                        + "배포 환경 구축을 담당해 GCP Ubuntu 서버에서 Docker Compose로 세 서비스를 함께 실행하도록 구성.");
        assertThat(presentation.snippet()).doesNotContain("HTTPS 설정");
    }

    @Test
    void preservesSelectedResultPresentationWhenNoDirectEvidenceExistsElsewhere() {
        VectorSearchResult result = result(
                57L,
                "모바일 경험을 제공하기 위해 앱 출시와 알림 시스템 개선을 이끌었습니다.",
                2,
                "2페이지");
        when(repository.findActiveVersionChunks(7L, 10L, 20L))
                .thenReturn(List.of(chunk(59L, 3, "3페이지", "운영 절차를 문서화했습니다.")));

        EvidencePresentation presentation = service.select(7L, "Springboot 활용 경험", result);

        assertThat(presentation.evidenceChunkId()).isEqualTo(57L);
        assertThat(presentation.evidenceSourceLabel()).isEqualTo("2페이지");
        assertThat(presentation.snippet()).isEqualTo(result.content());
    }

    @Test
    void numericEvidenceInTheCurrentChunkDoesNotTriggerExpansion() {
        String content = "엑셀 업로드 2,329행 기존 관광지만 갱신 1~3초대 성공 675 / 제외 1,654";
        VectorSearchResult result = result(56L, content, 5, "5페이지");

        EvidencePresentation presentation = service.select(7L, "2,329행 중 675건 갱신", result);

        assertThat(presentation.evidenceChunkId()).isEqualTo(56L);
        assertThat(presentation.snippet()).isEqualTo(content);
        verify(repository, never()).findActiveVersionChunks(7L, 10L, 20L);
    }

    private static VectorSearchResult result(
            long chunkId,
            String content,
            int sourceIndex,
            String sourceLabel) {
        return new VectorSearchResult(
                chunkId,
                10L,
                20L,
                "백엔드 이력서",
                1,
                Math.toIntExact(chunkId),
                sourceIndex,
                ChunkSourceType.PAGE,
                sourceIndex,
                sourceLabel,
                content,
                0.2d,
                0.8d);
    }

    private static EvidenceChunk chunk(
            long chunkId,
            int sourceIndex,
            String sourceLabel,
            String content) {
        return new EvidenceChunk(
                chunkId,
                Math.toIntExact(chunkId),
                ChunkSourceType.PAGE,
                sourceIndex,
                sourceLabel,
                content);
    }
}
