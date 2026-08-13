package com.prizm.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.prizm.document.dto.response.DocumentDetailResponse;
import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingFailureCode;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.entity.ProcessingProgressStage;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentQueryServiceTest {

    @Mock
    DocumentRepository documentRepository;
    @Mock
    DocumentVersionRepository documentVersionRepository;
    @Mock
    ProcessingJobRepository processingJobRepository;

    DocumentQueryService service;
    DocumentVersion version;

    @BeforeEach
    void setUp() {
        service = new DocumentQueryService(documentRepository, documentVersionRepository, processingJobRepository);
        Document document = Document.create(7L, "Guide");
        ReflectionTestUtils.setField(document, "id", 1L);
        version = DocumentVersion.quarantined(7L, 1L, "guide.txt", "a".repeat(64));
        ReflectionTestUtils.setField(version, "id", 10L);
        when(documentRepository.findByIdAndOwnerUserId(1L, 7L)).thenReturn(Optional.of(document));
        when(documentVersionRepository.findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(7L, 1L))
                .thenReturn(List.of(version));
    }

    @Test
    void returnsNullPercentBeforeTotalChunksAreKnown() {
        ProcessingJob job = processingJob(ProcessingJobStatus.PROCESSING);
        ReflectionTestUtils.setField(job, "progressStage", ProcessingProgressStage.TEXT_EXTRACTION);
        when(processingJobRepository.findByOwnerUserIdAndDocumentVersionId(7L, 10L))
                .thenReturn(Optional.of(job));

        DocumentDetailResponse response = service.get(7L, 1L);

        assertThat(response.versions().get(0).processingStage()).isEqualTo(ProcessingProgressStage.TEXT_EXTRACTION);
        assertThat(response.versions().get(0).progressPercent()).isNull();
    }

    @Test
    void calculatesOnlyActualCompletedOverTotalChunkPercent() {
        ProcessingJob job = processingJob(ProcessingJobStatus.PROCESSING);
        ReflectionTestUtils.setField(job, "progressStage", ProcessingProgressStage.EMBEDDING);
        ReflectionTestUtils.setField(job, "completedChunks", 2);
        ReflectionTestUtils.setField(job, "totalChunks", 5);
        when(processingJobRepository.findByOwnerUserIdAndDocumentVersionId(7L, 10L))
                .thenReturn(Optional.of(job));

        DocumentDetailResponse response = service.get(7L, 1L);

        assertThat(response.versions().get(0).completedChunks()).isEqualTo(2);
        assertThat(response.versions().get(0).totalChunks()).isEqualTo(5);
        assertThat(response.versions().get(0).progressPercent()).isEqualTo(40);
    }

    @Test
    void exposesStoredRetryEvidenceAndSafeFailureCodeWithoutInternalMessage() {
        ProcessingJob job = processingJob(ProcessingJobStatus.RETRY_WAIT);
        Instant nextRetryAt = Instant.parse("2026-08-13T01:02:03Z");
        ReflectionTestUtils.setField(job, "retryCount", 2);
        ReflectionTestUtils.setField(job, "nextRetryAt", nextRetryAt);
        ReflectionTestUtils.setField(job, "failureCode", ProcessingFailureCode.OLLAMA_RUNTIME_FAILURE);
        ReflectionTestUtils.setField(job, "errorMessage", "secret internal stack and URL");
        when(processingJobRepository.findByOwnerUserIdAndDocumentVersionId(7L, 10L))
                .thenReturn(Optional.of(job));

        DocumentDetailResponse response = service.get(7L, 1L);

        assertThat(response.versions().get(0).processingErrorCode()).isEqualTo("OLLAMA_RUNTIME_FAILURE");
        assertThat(response.versions().get(0).retryCount()).isEqualTo(2);
        assertThat(response.versions().get(0).maxRetries()).isEqualTo(3);
        assertThat(response.versions().get(0).nextRetryAt()).isEqualTo(nextRetryAt);
        assertThat(response.toString()).doesNotContain("secret internal stack and URL");
    }

    private ProcessingJob processingJob(ProcessingJobStatus status) {
        ProcessingJob job = ProcessingJob.pendingIndexing(7L, 10L);
        ReflectionTestUtils.setField(job, "id", 20L);
        ReflectionTestUtils.setField(job, "status", status);
        return job;
    }
}
