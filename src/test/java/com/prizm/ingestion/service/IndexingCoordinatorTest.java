package com.prizm.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
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
        ClaimedProcessingJob job = new ClaimedProcessingJob(20L, 10L, 0);
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
    void doesNothingWhenAnotherWorkerAlreadyClaimedTheJob() {
        when(claimService.claimNext()).thenReturn(Optional.empty());
        IndexingCoordinator coordinator = new IndexingCoordinator(
                claimService, processor, new IndexingFailureClassifier(), failureService);

        assertThat(coordinator.processNext()).isFalse();
        verify(processor, never()).process(org.mockito.ArgumentMatchers.any());
    }
}
