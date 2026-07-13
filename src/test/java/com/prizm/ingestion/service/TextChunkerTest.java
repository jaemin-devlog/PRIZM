package com.prizm.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.ingestion.config.IngestionProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class TextChunkerTest {

    @Test
    void splitsWithOverlapAndSequentialChunkNumbers() {
        IngestionProperties properties = properties(8, 2);

        List<TextChunk> chunks = new TextChunker(properties).split("abcdefghijk");

        assertThat(chunks).containsExactly(
                new TextChunk(1, "abcdefgh"),
                new TextChunk(2, "ghijk"));
    }

    @Test
    void excludesBlankChunksAndStopsAtLastChunk() {
        IngestionProperties properties = properties(10, 2);

        assertThat(new TextChunker(properties).split("   ")).isEmpty();
        assertThat(new TextChunker(properties).split("short"))
                .containsExactly(new TextChunk(1, "short"));
    }

    @Test
    void rejectsOverlapThatCannotAdvancePosition() {
        IngestionProperties properties = properties(8, 8);

        assertThatThrownBy(() -> new TextChunker(properties))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlap");
    }

    private IngestionProperties properties(int maxLength, int overlap) {
        IngestionProperties properties = new IngestionProperties();
        properties.setMaxChunkLength(maxLength);
        properties.setOverlap(overlap);
        return properties;
    }
}
