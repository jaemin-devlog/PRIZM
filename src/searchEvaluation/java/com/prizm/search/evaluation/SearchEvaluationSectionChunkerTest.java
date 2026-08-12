package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.ingestion.service.TextChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchEvaluationSectionChunkerTest {

    private final SearchEvaluationSectionChunker chunker = new SearchEvaluationSectionChunker();

    @Test
    void keepsDifferentNumberedSectionsOutOfTheSameChunk() {
        String authentication = "인증 흐름은 이메일 로그인과 Kakao 로그인을 단일 사용자 계정 기준으로 통합했습니다. "
                + "Spring Security를 공통 진입점으로 두고 OAuth2와 JWT 상태를 같은 규칙으로 검증했습니다. ";
        String deployment = "배포 환경은 GCP Ubuntu에서 Docker Compose로 구성했습니다. "
                + "Nginx 리버스 프록시와 HTTPS를 적용하고 업로드 파일 서빙을 분리했습니다. ";
        String text = "MoneyWay 프로젝트\n"
                + "02 인증 흐름 통합 및 계정 상태 검증\n"
                + authentication.repeat(2) + "\n"
                + "03 GCP 기반 Docker 배포 환경 구축\n"
                + deployment.repeat(2);

        List<TextChunk> chunks = chunker.split(text);

        assertThat(chunks).noneMatch(chunk ->
                chunk.content().contains("OAuth2") && chunk.content().contains("Nginx"));
    }

    @Test
    void preservesEveryNonBlankLineWithoutExceedingTheExperimentalMaximum() {
        String text = "AirConnect 프로젝트\n"
                + "01 매칭 중복 확정 방지\n"
                + "DB row lock으로 상태를 다시 확인했습니다. ".repeat(12) + "\n"
                + "02 알림 저장과 FCM 발송 실패 격리\n"
                + "Outbox 이벤트를 Worker가 비동기로 처리했습니다. ".repeat(12);

        List<TextChunk> chunks = chunker.split(text);

        assertThat(chunks)
                .allMatch(chunk -> chunk.content().length()
                        <= SearchEvaluationSectionChunker.MAX_REBALANCED_CHUNK_LENGTH);
        assertThat(String.join("\n", chunks.stream().map(TextChunk::content).toList()))
                .isEqualTo(String.join("\n", text.lines()
                        .map(String::strip)
                        .filter(line -> !line.isBlank())
                        .toList()));
    }

    @Test
    void carriesShortProjectTitleIntoTheFollowingSection() {
        String text = "Project Portfolio 01\n"
                + "AirConnect — 매칭 정합성 개선\n"
                + "01 문제 원인\n"
                + "같은 팀이 중복 확정될 수 있었습니다.";

        assertThat(chunker.split(text))
                .singleElement()
                .satisfies(chunk -> assertThat(chunk.content())
                        .contains("AirConnect — 매칭 정합성 개선", "같은 팀이 중복 확정"));
    }
}
