package com.prizm.ingestion.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.infrastructure.storage.FileStorageException;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.exception.DocumentIndexingException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentIndexingProcessorTest {

    @Mock
    DocumentVersionRepository documentVersionRepository;

    @Mock
    FileStorage fileStorage;

    @Mock
    EmbeddingService embeddingService;

    @Mock
    IndexingCompletionService completionService;

    @Mock
    ProcessingJobLeaseService leaseService;

    DocumentIndexingProcessor processor;
    ClaimedProcessingJob claimedJob;

    @BeforeEach
    void setUp() {
        IngestionProperties properties = new IngestionProperties();
        properties.setMaxChunkLength(8);
        properties.setOverlap(2);
        properties.setLeaseRefreshChunkInterval(2);
        processor = new DocumentIndexingProcessor(
                documentVersionRepository,
                fileStorage,
                new TextChunker(properties),
                embeddingService,
                completionService,
                leaseService,
                properties,
                4);
        DocumentVersion version = DocumentVersion.quarantined(7L, 1L, "guide.txt", "a".repeat(64));
        ReflectionTestUtils.setField(version, "id", 10L);
        version.startProcessing();
        version.updateStoredFilePath("documents/1/10/guide.txt");
        when(documentVersionRepository.findById(10L)).thenReturn(Optional.of(version));
        claimedJob = new ClaimedProcessingJob(
                20L, 10L, 7L, 1L, java.time.Instant.parse("2026-07-13T00:10:00Z"));
    }

    @Test
    void createsEmbeddingsAndCompletesWithNonEmptyChunks() {
        when(fileStorage.read("documents/1/10/guide.txt"))
                .thenReturn("abcdefghijk".getBytes(StandardCharsets.UTF_8));
        when(embeddingService.embed(anyString())).thenReturn(new float[4]);

        processor.process(claimedJob);

        verify(leaseService, times(3)).renew(claimedJob);
        verify(completionService).complete(any(ClaimedProcessingJob.class), any(List.class));
    }

    @Test
    void treatsMissingStoredFileAsPermanentFailure() {
        when(fileStorage.read("documents/1/10/guide.txt"))
                .thenThrow(new FileStorageException("missing"));

        assertThatThrownBy(() -> processor.process(claimedJob))
                .isInstanceOf(DocumentIndexingException.class)
                .extracting(exception -> ((DocumentIndexingException) exception).isRetryable())
                .isEqualTo(false);
        verify(embeddingService, never()).embed(anyString());
    }

    @Test
    void rejectsInvalidUtf8AndBlankTxt() {
        when(fileStorage.read("documents/1/10/guide.txt")).thenReturn(new byte[] {(byte) 0xC3, 0x28});
        assertThatThrownBy(() -> processor.process(claimedJob))
                .isInstanceOf(DocumentIndexingException.class)
                .hasMessageContaining("UTF-8");

        when(fileStorage.read("documents/1/10/guide.txt"))
                .thenReturn("   \n".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> processor.process(claimedJob))
                .isInstanceOf(DocumentIndexingException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void rejectsEmbeddingDimensionMismatch() {
        when(fileStorage.read("documents/1/10/guide.txt")).thenReturn("text".getBytes(StandardCharsets.UTF_8));
        when(embeddingService.embed("text")).thenReturn(new float[3]);

        assertThatThrownBy(() -> processor.process(claimedJob))
                .isInstanceOf(DocumentIndexingException.class)
                .hasMessageContaining("4-dimensional");
        verify(completionService, never()).complete(any(), any());
    }
}
