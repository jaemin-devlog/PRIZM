package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.ingestion.service.TextChunk;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchEvaluationSectionParagraphV2ChunkerTest {

    private final SearchEvaluationSectionParagraphV2Chunker chunker =
            new SearchEvaluationSectionParagraphV2Chunker();

    @Test
    void carriesShortParentContextIntoEachCaseWithoutMergingDifferentCases() {
        String parentContext = "04 대표 문제 해결 사례\n3개 대표 사례를 간단히 요약했습니다.";
        String text = parentContext + "\n"
                + "Case 01\n"
                + "AtlasBoard - 그룹 매칭 동시성 정합성 개선\n"
                + "DB row lock과 unique constraint로 중복 확정을 방지했습니다.\n"
                + "Case 02\n"
                + "AtlasBoard - Outbox 기반 알림 처리\n"
                + "알림 저장과 FCM 발송을 분리했습니다.";

        assertThat(chunker.split(text))
                .hasSize(2)
                .allSatisfy(chunk -> assertThat(chunk.content())
                        .startsWith(parentContext + "\nCase"));
        assertThat(chunker.split(text).get(0).content())
                .contains("Case 01\nAtlasBoard - 그룹 매칭 동시성 정합성 개선")
                .doesNotContain("Case 02");
        assertThat(chunker.split(text).get(1).content())
                .contains("Case 02\nAtlasBoard - Outbox 기반 알림 처리")
                .doesNotContain("Case 01");
    }

    @Test
    void doesNotMergeCaseParagraphsAcrossDifferentTopLevelSections() {
        String text = "03 GCP 기반 Docker 배포 환경 구축\n"
                + "Nginx와 HTTPS를 적용했습니다.\n"
                + "04 대표 문제 해결 사례\n"
                + "Case 01\n"
                + "AtlasBoard - 그룹 매칭 동시성 정합성 개선\n"
                + "DB row lock으로 중복 확정을 방지했습니다.\n"
                + "05 수상\n"
                + "관광데이터 활용 공모전 우수상을 수상했습니다.";

        List<TextChunk> chunks = chunker.split(text);

        assertThat(chunks).noneMatch(chunk ->
                chunk.content().contains("Nginx") && chunk.content().contains("Case 01"));
        assertThat(chunks).noneMatch(chunk ->
                chunk.content().contains("Case 01") && chunk.content().contains("공모전"));
    }

    @Test
    void preservesAllLinesAndTheExistingSixHundredCharacterMaximum() {
        String text = "04 대표 문제 해결 사례\n3개 대표 사례를 간단히 요약했습니다.\n"
                + "Case 01\n"
                + "AtlasBoard - 그룹 매칭 동시성 정합성 개선\n"
                + "DB row lock으로 상태를 확인했습니다. ".repeat(6) + "\n"
                + "Case 02\n"
                + "LedgerLab - TourAPI 동기화 병목 개선\n"
                + "관광지별 호출을 병렬 처리했습니다. ".repeat(6);

        List<TextChunk> chunks = chunker.split(text);

        assertThat(chunks)
                .allMatch(chunk -> chunk.content().length()
                        <= SearchEvaluationSectionChunker.MAX_REBALANCED_CHUNK_LENGTH);
        List<String> renderedLines = chunks.stream()
                .flatMap(chunk -> chunk.content().lines())
                .toList();
        assertThat(text.lines()
                        .map(String::strip)
                        .filter(line -> !line.isBlank())
                        .toList())
                .allMatch(renderedLines::contains);
    }
}
