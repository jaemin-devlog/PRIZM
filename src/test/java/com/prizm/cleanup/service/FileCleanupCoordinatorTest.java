package com.prizm.cleanup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prizm.cleanup.exception.StaleFileCleanupJobClaimException;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.infrastructure.storage.PermanentFileStorageException;
import com.prizm.infrastructure.storage.TransientFileStorageException;
import java.io.IOException;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith({MockitoExtension.class, OutputCaptureExtension.class})
class FileCleanupCoordinatorTest {

    @Mock FileCleanupJobClaimService claimService;
    @Mock FileStorage fileStorage;
    @Mock FileCleanupCompletionService completionService;
    @Mock FileCleanupFailureClassifier failureClassifier;
    @Mock FileCleanupFailureService failureService;
    @InjectMocks FileCleanupCoordinator coordinator;

    @Test
    void deletesClaimedFileAndCompletesJob() {
        ClaimedFileCleanupJob job = job();
        when(claimService.claimNext()).thenReturn(Optional.of(job));

        assertThat(coordinator.processNext()).isTrue();

        verify(fileStorage).delete(job.storageKey());
        verify(completionService).complete(job);
    }

    @Test
    void leavesClaimProcessingWhenCompletionUpdateFailsAndContinuesBatch() {
        ClaimedFileCleanupJob job = job();
        when(claimService.claimNext()).thenReturn(Optional.of(job)).thenReturn(Optional.empty());
        doThrow(new DataAccessResourceFailureException("database unavailable"))
                .when(completionService).complete(job);

        assertThat(coordinator.processBatch(2)).isEqualTo(1);

        verify(fileStorage).delete(job.storageKey());
        verify(completionService).complete(job);
        verifyNoInteractions(failureClassifier, failureService);
    }

    @Test
    void ignoresStaleCompletionWithoutRecordingDeleteFailure() {
        ClaimedFileCleanupJob job = job();
        when(claimService.claimNext()).thenReturn(Optional.of(job));
        doThrow(new StaleFileCleanupJobClaimException(job.fileCleanupJobId(), job.claimVersion()))
                .when(completionService).complete(job);

        assertThat(coordinator.processNext()).isTrue();

        verify(fileStorage).delete(job.storageKey());
        verifyNoInteractions(failureClassifier, failureService);
    }

    @Test
    void recordsPermanentDeleteFailureWithoutExposingStorageKey() {
        ClaimedFileCleanupJob job = job();
        CleanupFailure failure = new CleanupFailure(false, "PERMANENT_STORAGE_ERROR");
        when(claimService.claimNext()).thenReturn(Optional.of(job));
        org.mockito.Mockito.doThrow(new PermanentFileStorageException("invalid"))
                .when(fileStorage).delete(job.storageKey());
        when(failureClassifier.classify(org.mockito.ArgumentMatchers.any())).thenReturn(failure);

        assertThat(coordinator.processNext()).isTrue();

        verify(failureService).handleFailure(job, failure);
        verify(completionService, never()).complete(job);
    }

    @Test
    void recordsTransientDeleteFailureWithoutCallingCompletion() {
        ClaimedFileCleanupJob job = job();
        CleanupFailure failure = new CleanupFailure(true, "TRANSIENT_STORAGE_ERROR");
        when(claimService.claimNext()).thenReturn(Optional.of(job));
        doThrow(new TransientFileStorageException("temporary", new IOException("disk unavailable")))
                .when(fileStorage).delete(job.storageKey());
        when(failureClassifier.classify(org.mockito.ArgumentMatchers.any())).thenReturn(failure);

        assertThat(coordinator.processNext()).isTrue();

        verify(failureService).handleFailure(job, failure);
        verify(completionService, never()).complete(job);
    }

    @Test
    void retriesUnexpectedRuntimeFailureWithoutLoggingSensitiveExceptionDetails(CapturedOutput output) {
        ClaimedFileCleanupJob job = job();
        String sensitiveMessage = "provider failure at /private/career/source.pdf";
        when(claimService.claimNext()).thenReturn(Optional.of(job));
        doThrow(new IllegalStateException(sensitiveMessage)).when(fileStorage).delete(job.storageKey());
        FileCleanupCoordinator coordinatorWithActualClassifier = new FileCleanupCoordinator(
                claimService,
                fileStorage,
                completionService,
                new FileCleanupFailureClassifier(),
                failureService);

        assertThat(coordinatorWithActualClassifier.processNext()).isTrue();

        verify(failureService).handleFailure(job, new CleanupFailure(true, "UNEXPECTED_CLEANUP_ERROR"));
        verify(completionService, never()).complete(job);
        assertThat(output.getAll())
                .contains("UNEXPECTED_CLEANUP_ERROR")
                .doesNotContain(sensitiveMessage)
                .doesNotContain(job.storageKey());
    }

    @Test
    void doesNotCatchErrorsFromFileDeletion() {
        ClaimedFileCleanupJob job = job();
        AssertionError error = new AssertionError("fatal virtual machine condition");
        when(claimService.claimNext()).thenReturn(Optional.of(job));
        doThrow(error).when(fileStorage).delete(job.storageKey());

        assertThatThrownBy(() -> coordinator.processNext()).isSameAs(error);

        verifyNoInteractions(failureClassifier, failureService);
        verify(completionService, never()).complete(job);
    }

    @Test
    void returnsFalseWhenNoClaimableJobExists() {
        when(claimService.claimNext()).thenReturn(Optional.empty());

        assertThat(coordinator.processNext()).isFalse();
    }

    private ClaimedFileCleanupJob job() {
        return new ClaimedFileCleanupJob(3L, "documents/cleanup/file.txt", 0, 1L, Instant.now());
    }
}
