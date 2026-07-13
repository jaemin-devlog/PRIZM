package com.prizm.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.repository.ProcessingJobRepository;
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
    DocumentChunkRepository documentChunkRepository;

    IndexingFailureService service;

    @BeforeEach
    void setUp() {
        service = new IndexingFailureService(
                processingJobRepository,
                documentVersionRepository,
                documentChunkRepository,
                new IndexingRetryPolicy());
    }

    @Test
    void schedulesRetryAndIncrementsCountForTransientFailure() {
        ProcessingJob job = processingJob(0);
        DocumentVersion version = indexingVersion();
        stub(job, version);

        ProcessingJobStatus status = service.handleFailure(claimed(job), true, "Ollama unavailable");

        assertThat(status).isEqualTo(ProcessingJobStatus.PENDING);
        assertThat(job.getRetryCount()).isEqualTo(1);
        assertThat(job.getNextRetryAt()).isNotNull();
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.INDEXING);
    }

    @Test
    void marksJobAndVersionFailedAfterMaximumRetries() {
        ProcessingJob job = processingJob(3);
        DocumentVersion version = indexingVersion();
        stub(job, version);

        ProcessingJobStatus status = service.handleFailure(claimed(job), true, "still unavailable");

        assertThat(status).isEqualTo(ProcessingJobStatus.FAILED);
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.FAILED);
    }

    @Test
    void marksPermanentFileFailureImmediately() {
        ProcessingJob job = processingJob(0);
        DocumentVersion version = indexingVersion();
        stub(job, version);

        ProcessingJobStatus status = service.handleFailure(claimed(job), false, "invalid UTF-8");

        assertThat(status).isEqualTo(ProcessingJobStatus.FAILED);
        assertThat(job.getRetryCount()).isZero();
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.FAILED);
    }

    private void stub(ProcessingJob job, DocumentVersion version) {
        when(processingJobRepository.findByIdForUpdate(20L)).thenReturn(Optional.of(job));
        when(documentVersionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(version));
    }

    private ProcessingJob processingJob(int retryCount) {
        ProcessingJob job = ProcessingJob.pendingIndexing(10L);
        ReflectionTestUtils.setField(job, "id", 20L);
        ReflectionTestUtils.setField(job, "status", ProcessingJobStatus.PROCESSING);
        ReflectionTestUtils.setField(job, "retryCount", retryCount);
        return job;
    }

    private DocumentVersion indexingVersion() {
        DocumentVersion version = DocumentVersion.quarantined(1L, "guide.txt", "a".repeat(64));
        ReflectionTestUtils.setField(version, "id", 10L);
        version.approve();
        version.startIndexing();
        return version;
    }

    private ClaimedProcessingJob claimed(ProcessingJob job) {
        return new ClaimedProcessingJob(job.getId(), job.getDocumentVersionId(), job.getRetryCount());
    }
}
