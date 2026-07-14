package com.prizm.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import com.prizm.ingestion.exception.StaleProcessingJobClaimException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IndexingCoordinatorTest {

    @Mock
    ProcessingJobClaimService claimService;
    @Mock
    DocumentIndexingProcessor processor;
    @Mock
    IndexingFailureService failureService;

    @Test
    void recordsRetryableOllamaFailureWithoutHoldingClaimTransaction() {
        ClaimedProcessingJob job = claimedJob();
        when(claimService.claimNext()).thenReturn(Optional.of(job));
        org.mockito.Mockito.doThrow(new EmbeddingException(
                        EmbeddingErrorCode.OLLAMA_UNAVAILABLE, "unavailable"))
                .when(processor).process(job);
        IndexingCoordinator coordinator = new IndexingCoordinator(
                claimService, processor, new IndexingFailureClassifier(), failureService);

        assertThat(coordinator.processNext()).isTrue();

        verify(failureService).handleFailure(job, true, "unavailable");
    }

    @Test
    void recordsInvalidEmbeddingResponseAsRetryableFailure() {
        ClaimedProcessingJob job = claimedJob();
        when(claimService.claimNext()).thenReturn(Optional.of(job));
        org.mockito.Mockito.doThrow(new EmbeddingException(
                        EmbeddingErrorCode.EMBEDDING_INVALID_RESPONSE, "invalid response"))
                .when(processor).process(job);
        IndexingCoordinator coordinator = new IndexingCoordinator(
                claimService, processor, new IndexingFailureClassifier(), failureService);

        assertThat(coordinator.processNext()).isTrue();

        verify(failureService).handleFailure(job, true, "invalid response");
    }

    @Test
    void doesNothingWhenAnotherWorkerAlreadyClaimedTheJob() {
        when(claimService.claimNext()).thenReturn(Optional.empty());
        IndexingCoordinator coordinator = new IndexingCoordinator(
                claimService, processor, new IndexingFailureClassifier(), failureService);

        assertThat(coordinator.processNext()).isFalse();
        verify(processor, never()).process(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void ignoresCompletionFromAStaleWorkerWithoutChangingCurrentJob() {
        ClaimedProcessingJob job = claimedJob();
        when(claimService.claimNext()).thenReturn(Optional.of(job));
        org.mockito.Mockito.doThrow(new StaleProcessingJobClaimException(
                        job.processingJobId(), job.claimVersion()))
                .when(processor).process(job);
        IndexingCoordinator coordinator = new IndexingCoordinator(
                claimService, processor, new IndexingFailureClassifier(), failureService);

        assertThat(coordinator.processNext()).isTrue();

        verify(failureService, never()).handleFailure(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.any());
    }

    private ClaimedProcessingJob claimedJob() {
        return new ClaimedProcessingJob(20L, 10L, 7L, 1L, Instant.parse("2026-07-13T00:10:00Z"));
    }
}
