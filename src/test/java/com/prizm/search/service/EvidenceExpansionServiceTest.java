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
    void keepsDirectTechnicalEvidenceInTheSelectedChunkInsteadOfExpandingAwayItsAnchor() {
        VectorSearchResult result = result(
                52L,
                "Project Delta의 기술 스택은 MessageBridge와 RelationalDB입니다.",
                4,
                "4페이지");

        EvidencePresentation presentation = service.select(7L, "MessageBridge를 사용한 경험이 있나요?", result);

        assertThat(presentation.evidenceChunkId()).isEqualTo(52L);
        assertThat(presentation.snippet()).contains("MessageBridge");
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

    @Test
    void prefersDetailedDuplicateStorageEvidenceOverOutboxAndSummaryEvidence() {
        VectorSearchResult result = result(
                99L,
                "Outbox는 알림 ID와 디바이스 ID 조합에 unique constraint를 적용해 "
                        + "같은 알림의 중복 발송 이벤트를 차단했습니다.",
                3,
                "3페이지");
        EvidenceChunk summary = chunk(
                93L,
                1,
                "1페이지",
                "이 포트폴리오는 대표 문제 해결 사례를 요약했습니다.\n"
                        + "4,400회 재현 테스트 / 중복 저장 0건");
        EvidenceChunk detail = chunk(
                96L,
                2,
                "2페이지",
                "03 동시성 정합성 테스트 결과\n"
                        + "MySQL 합계 4방식 × 4조건 / 4,400회 성공 800 / 예상 차단 3,600 / 중복 저장 0건");
        when(repository.findActiveVersionChunks(7L, 10L, 20L))
                .thenReturn(List.of(summary, detail));

        EvidencePresentation presentation = service.select(
                7L,
                "데이터가 중복 저장되는 문제 해결한 적 있어?",
                result);

        assertThat(presentation.evidenceChunkId()).isEqualTo(96L);
        assertThat(presentation.evidenceSourceLabel()).isEqualTo("2페이지");
        assertThat(presentation.snippet()).contains("중복 저장 0건");
    }

    @Test
    void expandsFromGenericServerSummaryToDeploymentAction() {
        VectorSearchResult result = result(
                87L,
                "기능 구현에서 끝나지 않고 외부 서비스 실패 상황에서도 사용자 흐름이 "
                        + "끊기지 않는 서버 구조를 설계하는 백엔드",
                1,
                "1페이지");
        EvidenceChunk deployment = chunk(
                91L,
                2,
                "2페이지",
                "서버 재시작이나 배포 시 Spring Boot / MySQL / Redis를 각각 관리해야 하는 운영 부담 확인.\n"
                        + "배포 환경 구축을 담당해 GCP Ubuntu 서버에서 Docker Compose로 세 서비스를 함께 실행하도록 구성.");
        when(repository.findActiveVersionChunks(7L, 10L, 20L)).thenReturn(List.of(deployment));

        EvidencePresentation presentation = service.select(
                7L,
                "서버에 서비스를 올려본 적 있어?",
                result);

        assertThat(presentation.evidenceChunkId()).isEqualTo(91L);
        assertThat(presentation.evidenceSourceLabel()).isEqualTo("2페이지");
        assertThat(presentation.snippet()).contains("GCP Ubuntu 서버").contains("Docker Compose");
    }

    @Test
    void localizesStalledWorkQuestionToNumericRecoveryEvidenceRow() {
        VectorSearchResult result = result(
                103L,
                "별도였던 소개 정보 동기화를 상세 동기화 흐름에 통합해 같은 관광지를 "
                        + "다시 순회하는 단계를 제거했습니다.",
                5,
                "5페이지");
        EvidenceChunk recovery = chunk(
                100L,
                3,
                "3페이지",
                "오래 멈춘 작업 복구 재처리 대상 복구 만료 3건 복구 / 최근 1건 유지");
        when(repository.findActiveVersionChunks(7L, 10L, 20L)).thenReturn(List.of(recovery));

        EvidencePresentation presentation = service.select(
                7L,
                "오래 멈춘 작업을 다시 처리할 수 있게 한 경험은?",
                result);

        assertThat(presentation.evidenceChunkId()).isEqualTo(100L);
        assertThat(presentation.evidenceSourceLabel()).isEqualTo("3페이지");
        assertThat(presentation.snippet()).isEqualTo(
                "오래 멈춘 작업 복구 재처리 대상 복구 만료 3건 복구 / 최근 1건 유지");
    }

    @Test
    void replacesNumericSummaryWithStructuredDetailEvidence() {
        VectorSearchResult result = result(
                94L,
                "검증 기준\n1,252건 기준 / 19분 22초 → 11초",
                1,
                "1페이지");
        EvidenceChunk detail = chunk(
                104L,
                5,
                "5페이지",
                "03 결과\n동일 1,252건 기준 TourAPI 호출 시간을 약 19분 22초에서 11초로 줄였습니다.\n"
                        + "성능 측정 결과");
        when(repository.findActiveVersionChunks(7L, 10L, 20L)).thenReturn(List.of(detail));

        EvidencePresentation presentation = service.select(7L, "19분 22초에서 11초", result);

        assertThat(presentation.evidenceChunkId()).isEqualTo(104L);
        assertThat(presentation.evidenceSourceLabel()).isEqualTo("5페이지");
        assertThat(presentation.snippet()).contains("19분 22초에서 11초");
    }

    @Test
    void keepsDetailedLocalEvidenceAheadOfNearbySummaryEvidence() {
        VectorSearchResult result = result(
                96L,
                "후보 확정 직전에 DB row lock으로 팀방을 선점했습니다.\n"
                        + "두 팀 ID를 정렬해 저장하고 unique constraint를 적용해 중복 매칭을 차단했습니다.",
                2,
                "2페이지");
        EvidencePresentation presentation = service.select(
                7L,
                "row lock과 unique constraint로 중복 매칭을 차단한 결과는?",
                result);

        assertThat(presentation.evidenceChunkId()).isEqualTo(96L);
        assertThat(presentation.evidenceSourceLabel()).isEqualTo("2페이지");
        assertThat(presentation.snippet()).contains("unique constraint");
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
