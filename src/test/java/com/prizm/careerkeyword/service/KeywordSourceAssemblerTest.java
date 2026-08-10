package com.prizm.careerkeyword.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.careerkeyword.repository.KeywordSourceChunk;
import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.ingestion.entity.ChunkSourceType;
import java.util.List;
import org.junit.jupiter.api.Test;

class KeywordSourceAssemblerTest {

    private final KeywordSourceAssembler assembler = new KeywordSourceAssembler();

    @Test
    void assemblesAllTxtChunksAndRemovesTheirExactOverlap() {
        List<AssembledKeywordSource> sources = assembler.assemble(List.of(
                chunk(2, ChunkSourceType.TEXT_CHUNK, 2, "텍스트 구간 2", "Redis와 PostgreSQL을 사용했습니다."),
                chunk(1, ChunkSourceType.TEXT_CHUNK, 1, "텍스트 구간 1", "Spring Boot와 Redis")));

        assertThat(sources).singleElement().satisfies(source -> {
            assertThat(source.sourceType()).isEqualTo(ChunkSourceType.TEXT_CHUNK);
            assertThat(source.sourceIndex()).isEqualTo(1);
            assertThat(source.sourceLabel()).isEqualTo("텍스트 전체");
            assertThat(source.content()).isEqualTo("Spring Boot와 Redis와 PostgreSQL을 사용했습니다.");
        });
    }

    @Test
    void keepsPdfPagesAsSeparateSourceUnits() {
        List<AssembledKeywordSource> sources = assembler.assemble(List.of(
                chunk(1, ChunkSourceType.PAGE, 1, "1페이지", "Spring Boot"),
                chunk(2, ChunkSourceType.PAGE, 2, "2페이지", "Redis")));

        assertThat(sources).extracting(AssembledKeywordSource::sourceLabel)
                .containsExactly("1페이지", "2페이지");
        assertThat(sources).extracting(AssembledKeywordSource::content)
                .containsExactly("Spring Boot", "Redis");
    }

    private KeywordSourceChunk chunk(
            int chunkNo,
            ChunkSourceType sourceType,
            int sourceIndex,
            String sourceLabel,
            String content) {
        return new KeywordSourceChunk(
                10L,
                20L,
                "Career",
                DocumentType.RESUME,
                1,
                "career.txt",
                sourceType == ChunkSourceType.PAGE ? DocumentFileType.PDF : DocumentFileType.TXT,
                chunkNo,
                sourceType,
                sourceIndex,
                sourceLabel,
                content);
    }
}
