package com.prizm.ingestion.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.ingestion.repository.ProcessingJobClaimRepository;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IndexingCompletionOwnershipTest {

    @Mock ProcessingJobRepository processingJobRepository;
    @Mock DocumentVersionRepository documentVersionRepository;
    @Mock DocumentRepository documentRepository;
    @Mock DocumentChunkRepository documentChunkRepository;
    @Mock ProcessingJobClaimRepository claimRepository;

    @Test
    void rejectsMismatchedOwnerBeforeWritingChunksOrActivatingVersion() {
        ProcessingJob job = ProcessingJob.pendingIndexing(8L, 10L);
        ReflectionTestUtils.setField(job, "id", 20L);
        ReflectionTestUtils.setField(job, "status", ProcessingJobStatus.PROCESSING);
        ReflectionTestUtils.setField(job, "claimVersion", 1L);
        when(processingJobRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(job));
        ClaimedProcessingJob claim = new ClaimedProcessingJob(
                20L, 10L, 7L, 1L, Instant.parse("2026-07-13T00:10:00Z"));
        IndexedChunk chunk = new IndexedChunk(
                1, ChunkSourceType.TEXT_CHUNK, 1, "텍스트 구간 1", "content", nonZeroEmbedding());
        IndexingCompletionService service = new IndexingCompletionService(
                processingJobRepository,
                documentVersionRepository,
                documentRepository,
                documentChunkRepository,
                claimRepository,
                new EmbeddingValidator(1024));

        assertThatThrownBy(() -> service.complete(claim, List.of(chunk)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ownership");

        verifyNoInteractions(documentVersionRepository, documentRepository, documentChunkRepository, claimRepository);
    }

    @Test
    void rejectsZeroNormEmbeddingBeforeLockingOrWriting() {
        ClaimedProcessingJob claim = new ClaimedProcessingJob(
                20L, 10L, 7L, 1L, Instant.parse("2026-07-13T00:10:00Z"));
        IndexedChunk chunk = new IndexedChunk(
                1, ChunkSourceType.TEXT_CHUNK, 1, "텍스트 구간 1", "content", new float[1024]);
        IndexingCompletionService service = new IndexingCompletionService(
                processingJobRepository,
                documentVersionRepository,
                documentRepository,
                documentChunkRepository,
                claimRepository,
                new EmbeddingValidator(1024));

        assertThatThrownBy(() -> service.complete(claim, List.of(chunk)))
                .isInstanceOf(EmbeddingException.class)
                .extracting(exception -> ((EmbeddingException) exception).code())
                .isEqualTo(EmbeddingErrorCode.EMBEDDING_INVALID_RESPONSE);

        verifyNoInteractions(
                processingJobRepository,
                documentVersionRepository,
                documentRepository,
                documentChunkRepository,
                claimRepository);
    }

    private float[] nonZeroEmbedding() {
        float[] embedding = new float[1024];
        embedding[0] = 1.0f;
        return embedding;
    }
}
