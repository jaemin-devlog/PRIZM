package com.prizm.ingestion.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.ingestion.entity.ProcessingProgressStage;
import com.prizm.ingestion.exception.StaleProcessingJobClaimException;
import com.prizm.ingestion.repository.ProcessingJobProgressRepository;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessingJobProgressServiceTest {

    @Mock
    ProcessingJobProgressRepository repository;

    ProcessingJobProgressService service;
    ClaimedProcessingJob job;

    @BeforeEach
    void setUp() {
        service = new ProcessingJobProgressService(repository);
        job = new ClaimedProcessingJob(20L, 10L, 7L, 4L, Instant.parse("2026-08-13T00:00:00Z"));
    }

    @Test
    void writesActualStageAndChunkProgressForCurrentOwnerAndClaim() {
        when(repository.updateStage(20L, 7L, 4L, ProcessingProgressStage.TEXT_EXTRACTION)).thenReturn(true);
        when(repository.startEmbedding(20L, 7L, 4L, 5)).thenReturn(true);
        when(repository.updateCompletedChunks(20L, 7L, 4L, 2)).thenReturn(true);

        service.updateStage(job, ProcessingProgressStage.TEXT_EXTRACTION);
        service.startEmbedding(job, 5);
        service.updateCompletedChunks(job, 2);

        verify(repository).updateStage(20L, 7L, 4L, ProcessingProgressStage.TEXT_EXTRACTION);
        verify(repository).startEmbedding(20L, 7L, 4L, 5);
        verify(repository).updateCompletedChunks(20L, 7L, 4L, 2);
    }

    @Test
    void rejectsStaleOrOwnerMismatchedProgressUpdate() {
        when(repository.updateStage(20L, 7L, 4L, ProcessingProgressStage.SAVING)).thenReturn(false);

        assertThatThrownBy(() -> service.updateStage(job, ProcessingProgressStage.SAVING))
                .isInstanceOf(StaleProcessingJobClaimException.class);
    }

    @Test
    void refusesUnknownTotalInsteadOfInventingProgress() {
        assertThatThrownBy(() -> service.startEmbedding(job, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Total chunks must be positive.");
    }
}
