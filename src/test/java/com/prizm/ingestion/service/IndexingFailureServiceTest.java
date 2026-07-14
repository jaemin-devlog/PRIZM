package com.prizm.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.exception.StaleProcessingJobClaimException;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.repository.ProcessingJobClaimRepository;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IndexingFailureServiceTest {

    @Mock
    ProcessingJobRepository processingJobRepository;
    @Mock
    DocumentVersionRepository documentVersionRepository;
    @Mock
    DocumentRepository documentRepository;
    @Mock
    DocumentChunkRepository documentChunkRepository;
    @Mock
    ProcessingJobClaimRepository claimRepository;

    IndexingFailureService service;

    @BeforeEach
    void setUp() {
        service = new IndexingFailureService(
                processingJobRepository,
                documentVersionRepository,
                documentRepository,
                documentChunkRepository,
                claimRepository,
                new IndexingRetryPolicy());
    }

    @Test
    void schedulesRetryAndIncrementsCountForTransientFailure() {
        ProcessingJob job = processingJob(0);
        DocumentVersion version = processingVersion();
        stub(job, version);

        ProcessingJobStatus status = service.handleFailure(claimed(job), true, "Ollama unavailable");

        assertThat(status).isEqualTo(ProcessingJobStatus.RETRY_WAIT);
        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(job.getNextRetryAt()).isEqualTo(Instant.parse("2026-07-13T00:01:00Z"));
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.PROCESSING);
    }

    @Test
    void keepsExistingActiveVersionWhenInvalidEmbeddingSchedulesRetry() {
        ProcessingJob job = processingJob(0);
        DocumentVersion version = processingVersion();
        Document document = document();
        document.activateVersion(5L);
        when(processingJobRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(job));
        when(documentVersionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(version));
        when(documentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(document));
        when(claimRepository.currentDatabaseTime()).thenReturn(Instant.parse("2026-07-13T00:00:00Z"));

        ProcessingJobStatus status = service.handleFailure(
                claimed(job), true, "Embedding service returned an invalid response.");

        assertThat(status).isEqualTo(ProcessingJobStatus.RETRY_WAIT);
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.PROCESSING);
        assertThat(document.getActiveVersionId()).isEqualTo(5L);
        verify(documentChunkRepository).deleteByOwnerUserIdAndDocumentVersionId(7L, 10L);
    }

    @Test
    void marksJobAndVersionFailedAfterMaximumRetries() {
        ProcessingJob job = processingJob(3);
        DocumentVersion version = processingVersion();
        stub(job, version);

        ProcessingJobStatus status = service.handleFailure(claimed(job), true, "still unavailable");

        assertThat(status).isEqualTo(ProcessingJobStatus.FAILED);
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.FAILED);
    }

    @Test
    void marksPermanentFileFailureImmediately() {
        ProcessingJob job = processingJob(0);
        DocumentVersion version = processingVersion();
        stub(job, version);

        ProcessingJobStatus status = service.handleFailure(claimed(job), false, "invalid UTF-8");

        assertThat(status).isEqualTo(ProcessingJobStatus.FAILED);
        assertThat(job.getRetryCount()).isZero();
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.FAILED);
    }

    @Test
    void rejectsFailureFromStaleWorkerBeforeDeletingChunks() {
        ProcessingJob job = processingJob(0);
        when(processingJobRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(job));
        ClaimedProcessingJob staleClaim = new ClaimedProcessingJob(
                20L,
                10L,
                7L,
                job.getClaimVersion() - 1,
                Instant.parse("2026-07-13T00:10:00Z"));

        assertThatThrownBy(() -> service.handleFailure(staleClaim, true, "late failure"))
                .isInstanceOf(StaleProcessingJobClaimException.class);

        assertThat(job.getStatus()).isEqualTo(ProcessingJobStatus.PROCESSING);
        verifyNoInteractions(documentVersionRepository, documentRepository, documentChunkRepository, claimRepository);
    }

    private void stub(ProcessingJob job, DocumentVersion version) {
        when(processingJobRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(job));
        when(documentVersionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(version));
        when(documentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(document()));
        when(claimRepository.currentDatabaseTime()).thenReturn(Instant.parse("2026-07-13T00:00:00Z"));
    }

    private ProcessingJob processingJob(int retryCount) {
        ProcessingJob job = ProcessingJob.pendingIndexing(7L, 10L);
        ReflectionTestUtils.setField(job, "id", 20L);
        ReflectionTestUtils.setField(job, "status", ProcessingJobStatus.PROCESSING);
        ReflectionTestUtils.setField(job, "retryCount", retryCount);
        ReflectionTestUtils.setField(job, "claimVersion", 4L);
        return job;
    }

    private DocumentVersion processingVersion() {
        DocumentVersion version = DocumentVersion.quarantined(7L, 1L, "guide.txt", "a".repeat(64));
        ReflectionTestUtils.setField(version, "id", 10L);
        version.startProcessing();
        return version;
    }

    private Document document() {
        Document document = Document.create(7L, "Guide");
        ReflectionTestUtils.setField(document, "id", 1L);
        return document;
    }

    private ClaimedProcessingJob claimed(ProcessingJob job) {
        return new ClaimedProcessingJob(
                job.getId(),
                job.getDocumentVersionId(),
                job.getOwnerUserId(),
                job.getClaimVersion(),
                Instant.parse("2026-07-13T00:10:00Z"));
    }
}
