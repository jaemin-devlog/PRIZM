package com.prizm.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.document.dto.response.DocumentApprovalResponse;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.exception.InvalidDocumentVersionStateException;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.entity.ProcessingJobType;
import com.prizm.ingestion.exception.DuplicateProcessingJobException;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class DocumentApprovalServiceTest {

    @Mock
    DocumentVersionRepository documentVersionRepository;

    @Mock
    ProcessingJobRepository processingJobRepository;

    DocumentApprovalService service;

    @BeforeEach
    void setUp() {
        service = new DocumentApprovalService(documentVersionRepository, processingJobRepository);
    }

    @Test
    void approvesQuarantinedVersionAndCreatesOnePendingJob() {
        DocumentVersion version = version(10L);
        when(documentVersionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(version));
        when(processingJobRepository.existsByDocumentVersionIdAndJobType(10L, ProcessingJobType.INDEXING))
                .thenReturn(false);
        when(processingJobRepository.saveAndFlush(any(ProcessingJob.class))).thenAnswer(invocation -> {
            ProcessingJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "id", 20L);
            return job;
        });

        DocumentApprovalResponse response = service.approve(10L);

        assertThat(response.status()).isEqualTo(DocumentVersionStatus.APPROVED);
        assertThat(response.jobStatus()).isEqualTo(ProcessingJobStatus.PENDING);
        assertThat(response.jobId()).isEqualTo(20L);
    }

    @Test
    void rejectsMissingVersionWithoutCreatingJob() {
        when(documentVersionRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.approve(99L))
                .isInstanceOf(DocumentVersionNotFoundException.class);
        verify(processingJobRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsDuplicateIndexingJob() {
        DocumentVersion version = version(10L);
        when(documentVersionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(version));
        when(processingJobRepository.existsByDocumentVersionIdAndJobType(10L, ProcessingJobType.INDEXING))
                .thenReturn(true);

        assertThatThrownBy(() -> service.approve(10L))
                .isInstanceOf(DuplicateProcessingJobException.class);
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.QUARANTINED);
    }

    @Test
    void rejectsApprovalWhenVersionIsNotQuarantined() {
        DocumentVersion version = version(10L);
        version.approve();
        when(documentVersionRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(version));
        when(processingJobRepository.existsByDocumentVersionIdAndJobType(10L, ProcessingJobType.INDEXING))
                .thenReturn(false);

        assertThatThrownBy(() -> service.approve(10L))
                .isInstanceOf(InvalidDocumentVersionStateException.class);
        verify(processingJobRepository, never()).saveAndFlush(any());
    }

    private DocumentVersion version(Long id) {
        DocumentVersion version = DocumentVersion.quarantined(1L, "guide.txt", "a".repeat(64));
        ReflectionTestUtils.setField(version, "id", id);
        return version;
    }
}
