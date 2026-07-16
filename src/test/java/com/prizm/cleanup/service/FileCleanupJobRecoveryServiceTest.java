package com.prizm.cleanup.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.cleanup.repository.FileCleanupJobRepository;
import com.prizm.ingestion.service.IndexingRetryPolicy;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileCleanupJobRecoveryServiceTest {

    @Mock FileCleanupJobRepository repository;
    @Spy IndexingRetryPolicy retryPolicy = new IndexingRetryPolicy();
    @InjectMocks FileCleanupJobRecoveryService service;

    @Test
    void recoversExpiredClaimUsingTheExistingBackoff() {
        ClaimedFileCleanupJob job = job(0);
        Instant databaseNow = Instant.parse("2026-07-15T00:00:00Z");
        when(repository.lockNextExpired()).thenReturn(Optional.of(job));
        when(repository.currentDatabaseTime()).thenReturn(databaseNow);
        when(repository.recoverForRetry(eq(1L), eq(4L), any(), eq("LEASE_EXPIRED"))).thenReturn(true);

        service.recoverNext();

        verify(repository).recoverForRetry(1L, 4L, databaseNow.plusSeconds(60), "LEASE_EXPIRED");
    }

    @Test
    void failsExpiredClaimAfterMaximumRetries() {
        ClaimedFileCleanupJob job = job(IndexingRetryPolicy.MAX_RETRIES);
        when(repository.lockNextExpired()).thenReturn(Optional.of(job));
        when(repository.recoverAsFailed(1L, 4L, "LEASE_EXPIRED")).thenReturn(true);

        service.recoverNext();

        verify(repository).recoverAsFailed(1L, 4L, "LEASE_EXPIRED");
    }

    private ClaimedFileCleanupJob job(int attempts) {
        return new ClaimedFileCleanupJob(1L, "documents/cleanup/file.txt", attempts, 4L, Instant.now());
    }
}
