package com.prizm.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.repository.ProcessingJobClaimRepository;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProcessingJobRecoveryServiceTest {

    @Mock
    ProcessingJobClaimRepository claimRepository;
    @Mock
    ProcessingJobRepository processingJobRepository;
    @Mock
    DocumentVersionRepository documentVersionRepository;
    @Mock
    DocumentChunkRepository documentChunkRepository;

    @Test
    void marksExpiredJobAndVersionFailedAfterMaximumRetries() {
        ProcessingJob job = ProcessingJob.pendingIndexing(10L);
        ReflectionTestUtils.setField(job, "id", 20L);
        ReflectionTestUtils.setField(job, "status", ProcessingJobStatus.PROCESSING);
        ReflectionTestUtils.setField(job, "retryCount", 3);
        ReflectionTestUtils.setField(job, "claimVersion", 7L);
        DocumentVersion version = DocumentVersion.quarantined(1L, "guide.txt", "a".repeat(64));
        ReflectionTestUtils.setField(version, "id", 10L);
        version.approve();
        version.startIndexing();
        Instant databaseNow = Instant.parse("2026-07-13T00:00:00Z");

        when(claimRepository.lockNextExpiredId()).thenReturn(Optional.of(20L));
        when(processingJobRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(job));
        when(documentVersionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(version));
        when(claimRepository.currentDatabaseTime()).thenReturn(databaseNow);
        ProcessingJobRecoveryService service = new ProcessingJobRecoveryService(
                claimRepository,
                processingJobRepository,
                documentVersionRepository,
                documentChunkRepository,
                new IndexingRetryPolicy());

        assertThat(service.recoverNext()).isTrue();

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.FAILED);
        assertThat(job.getClaimVersion()).isEqualTo(8L);
        assertThat(job.getCompletedAt()).isEqualTo(databaseNow);
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.FAILED);
        verify(documentChunkRepository).deleteByDocumentVersionId(10L);
    }
}
