package com.prizm.changelog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prizm.changelog.entity.ChangeLogDispatchStatus;
import com.prizm.changelog.entity.DocumentChangeLog;
import com.prizm.changelog.repository.DocumentChangeLogRepository;
import com.prizm.ingestion.entity.ProcessingJob;
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
class ChangeLogDispatchTransactionTest {

    @Mock
    DocumentChangeLogRepository documentChangeLogRepository;

    @Mock
    ProcessingJobRepository processingJobRepository;

    ChangeLogDispatchTransaction dispatchTransaction;

    @BeforeEach
    void setUp() {
        dispatchTransaction = new ChangeLogDispatchTransaction(documentChangeLogRepository, processingJobRepository);
    }

    @Test
    void leavesAnEmptyQueueUnchanged() {
        when(documentChangeLogRepository.claimNextDispatchable(any(Instant.class))).thenReturn(Optional.empty());

        assertThat(dispatchTransaction.dispatchNext()).isFalse();

        verifyNoInteractions(processingJobRepository);
    }

    @Test
    void atomicallyDispatchesTheOwnerMatchingIndexingJob() {
        DocumentChangeLog changeLog = changeLog(10L, 7L, 22L);
        ProcessingJob job = processingJob(30L, 7L, 22L);
        when(documentChangeLogRepository.claimNextDispatchable(any(Instant.class))).thenReturn(Optional.of(changeLog));
        when(processingJobRepository.findByDocumentVersionId(22L)).thenReturn(Optional.of(job));

        assertThat(dispatchTransaction.dispatchNext()).isTrue();

        verify(processingJobRepository).insertIndexingIfAbsent(7L, 22L);
        assertThat(changeLog.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.DISPATCHED);
        assertThat(changeLog.getProcessingJobId()).isEqualTo(30L);
        assertThat(changeLog.getDispatchedAt()).isNotNull();
    }

    @Test
    void rejectsAnOwnerOrVersionMismatchBeforeConnectingTheJob() {
        DocumentChangeLog changeLog = changeLog(10L, 7L, 22L);
        ProcessingJob mismatchedJob = processingJob(30L, 8L, 22L);
        when(documentChangeLogRepository.claimNextDispatchable(any(Instant.class))).thenReturn(Optional.of(changeLog));
        when(processingJobRepository.findByDocumentVersionId(22L)).thenReturn(Optional.of(mismatchedJob));

        assertThatThrownBy(() -> dispatchTransaction.dispatchNext())
                .isInstanceOf(ChangeLogDispatchFailureException.class)
                .hasCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("INDEXING job owner or version did not match ChangeLog 10.");

        assertThat(changeLog.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.PENDING);
        assertThat(changeLog.getProcessingJobId()).isNull();
    }

    private DocumentChangeLog changeLog(long id, long ownerUserId, long documentVersionId) {
        DocumentChangeLog changeLog = DocumentChangeLog.pendingDocumentVersionCreated(ownerUserId, documentVersionId);
        ReflectionTestUtils.setField(changeLog, "id", id);
        return changeLog;
    }

    private ProcessingJob processingJob(long id, long ownerUserId, long documentVersionId) {
        ProcessingJob job = ProcessingJob.pendingIndexing(ownerUserId, documentVersionId);
        ReflectionTestUtils.setField(job, "id", id);
        return job;
    }
}
