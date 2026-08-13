package com.prizm.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DocumentIndexingProgressCheckpointTest {

    @Test
    void limitsFifteenThousandChunksToOneHundredProgressWrites() {
        int totalChunks = 15_000;
        int progressWrites = 0;
        int lastPersistedPercent = 0;

        for (int completedChunks = 1; completedChunks <= totalChunks; completedChunks++) {
            if (DocumentIndexingProcessor.shouldPersistProgress(
                    completedChunks, totalChunks, lastPersistedPercent)) {
                progressWrites++;
                lastPersistedPercent = DocumentIndexingProcessor.progressPercent(completedChunks, totalChunks);
            }
        }

        assertThat(progressWrites).isEqualTo(100);
        assertThat(lastPersistedPercent).isEqualTo(100);
    }

    @Test
    void alwaysPersistsTheFinalChunkWhenIntegerPercentDidNotChange() {
        assertThat(DocumentIndexingProcessor.shouldPersistProgress(1, 2, 0)).isTrue();
        assertThat(DocumentIndexingProcessor.shouldPersistProgress(2, 2, 100)).isTrue();
    }
}
