package com.prizm.cleanup.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.prizm.cleanup.exception.StaleFileCleanupJobClaimException;
import com.prizm.cleanup.repository.FileCleanupJobRepository;
import com.prizm.ingestion.service.IndexingRetryPolicy;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileCleanupFailureServiceTest {

    @Mock
    FileCleanupJobRepository repository;

    @Spy
    IndexingRetryPolicy retryPolicy = new IndexingRetryPolicy();

    @InjectMocks
    FileCleanupFailureService service;

    @Test
    void schedulesFirstTransientFailureWithTheExistingOneMinuteBackoff() {
        ClaimedFileCleanupJob job = jobWithAttempts(0);
        Instant databaseNow = Instant.parse("2026-07-15T00:00:00Z");
        when(repository.currentDatabaseTime()).thenReturn(databaseNow);
        when(repository.scheduleRetry(eq(1L), eq(7L), any(), eq("TRANSIENT_STORAGE_ERROR"))).thenReturn(true);

        service.handleFailure(job, new CleanupFailure(true, "TRANSIENT_STORAGE_ERROR"));

        verify(repository).scheduleRetry(1L, 7L, databaseNow.plusSeconds(60), "TRANSIENT_STORAGE_ERROR");
    }

    @Test
    void marksPermanentFailureWithoutRetry() {
        ClaimedFileCleanupJob job = jobWithAttempts(0);
        when(repository.fail(1L, 7L, "PERMANENT_STORAGE_ERROR")).thenReturn(true);

        service.handleFailure(job, new CleanupFailure(false, "PERMANENT_STORAGE_ERROR"));

        verify(repository).fail(1L, 7L, "PERMANENT_STORAGE_ERROR");
    }

    @Test
    void marksTransientFailureAsFailedAfterThreeRetries() {
        ClaimedFileCleanupJob job = jobWithAttempts(IndexingRetryPolicy.MAX_RETRIES);
        when(repository.fail(1L, 7L, "TRANSIENT_STORAGE_ERROR")).thenReturn(true);

        service.handleFailure(job, new CleanupFailure(true, "TRANSIENT_STORAGE_ERROR"));

        verify(repository).fail(1L, 7L, "TRANSIENT_STORAGE_ERROR");
    }

    @ParameterizedTest
    @CsvSource({"0, 60", "1, 300", "2, 900"})
    void schedulesUnexpectedRuntimeFailureWithExistingBackoff(int attempts, long delaySeconds) {
        ClaimedFileCleanupJob job = jobWithAttempts(attempts);
        Instant databaseNow = Instant.parse("2026-07-15T00:00:00Z");
        when(repository.currentDatabaseTime()).thenReturn(databaseNow);
        when(repository.scheduleRetry(1L, 7L, databaseNow.plusSeconds(delaySeconds), "UNEXPECTED_CLEANUP_ERROR"))
                .thenReturn(true);

        service.handleFailure(job, new CleanupFailure(true, "UNEXPECTED_CLEANUP_ERROR"));

        verify(repository).currentDatabaseTime();
        verify(repository).scheduleRetry(
                1L,
                7L,
                databaseNow.plusSeconds(delaySeconds),
                "UNEXPECTED_CLEANUP_ERROR");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void marksUnexpectedRuntimeFailureAsFailedAfterThreeRetries() {
        ClaimedFileCleanupJob job = jobWithAttempts(IndexingRetryPolicy.MAX_RETRIES);
        when(repository.fail(1L, 7L, "UNEXPECTED_CLEANUP_ERROR")).thenReturn(true);

        service.handleFailure(job, new CleanupFailure(true, "UNEXPECTED_CLEANUP_ERROR"));

        verify(repository).fail(1L, 7L, "UNEXPECTED_CLEANUP_ERROR");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void throwsStaleClaimWhenRetryUpdateDoesNotMatchAndMakesNoAdditionalStateChange() {
        ClaimedFileCleanupJob job = jobWithAttempts(0);
        Instant databaseNow = Instant.parse("2026-07-15T00:00:00Z");
        when(repository.currentDatabaseTime()).thenReturn(databaseNow);
        when(repository.scheduleRetry(1L, 7L, databaseNow.plusSeconds(60), "TRANSIENT_STORAGE_ERROR"))
                .thenReturn(false);

        assertThatThrownBy(() -> service.handleFailure(job, new CleanupFailure(true, "TRANSIENT_STORAGE_ERROR")))
                .isInstanceOf(StaleFileCleanupJobClaimException.class);

        verify(repository).currentDatabaseTime();
        verify(repository).scheduleRetry(1L, 7L, databaseNow.plusSeconds(60), "TRANSIENT_STORAGE_ERROR");
        verifyNoMoreInteractions(repository);
    }

    @Test
    void throwsStaleClaimWhenFailUpdateDoesNotMatchAndMakesNoAdditionalStateChange() {
        ClaimedFileCleanupJob job = jobWithAttempts(0);
        when(repository.fail(1L, 7L, "PERMANENT_STORAGE_ERROR")).thenReturn(false);

        assertThatThrownBy(() -> service.handleFailure(job, new CleanupFailure(false, "PERMANENT_STORAGE_ERROR")))
                .isInstanceOf(StaleFileCleanupJobClaimException.class);

        verify(repository).fail(1L, 7L, "PERMANENT_STORAGE_ERROR");
        verifyNoMoreInteractions(repository);
    }

    private ClaimedFileCleanupJob jobWithAttempts(int attempts) {
        return new ClaimedFileCleanupJob(1L, "documents/cleanup/file.txt", attempts, 7L, Instant.now());
    }
}
