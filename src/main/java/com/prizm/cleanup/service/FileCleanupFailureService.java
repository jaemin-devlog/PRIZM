package com.prizm.cleanup.service;

import com.prizm.cleanup.exception.StaleFileCleanupJobClaimException;
import com.prizm.cleanup.repository.FileCleanupJobRepository;
import com.prizm.ingestion.service.IndexingRetryPolicy;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileCleanupFailureService {

    private final FileCleanupJobRepository repository;
    private final IndexingRetryPolicy retryPolicy;

    public FileCleanupFailureService(FileCleanupJobRepository repository, IndexingRetryPolicy retryPolicy) {
        this.repository = repository;
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    public void handleFailure(ClaimedFileCleanupJob job, CleanupFailure failure) {
        boolean changed;
        if (failure.retryable() && retryPolicy.canRetry(job.attempts())) {
            Instant databaseNow = repository.currentDatabaseTime();
            changed = repository.scheduleRetry(
                    job.fileCleanupJobId(),
                    job.claimVersion(),
                    retryPolicy.nextRetryAt(job.attempts(), databaseNow),
                    failure.errorCode());
        }
        else {
            changed = repository.fail(job.fileCleanupJobId(), job.claimVersion(), failure.errorCode());
        }
        if (!changed) {
            throw new StaleFileCleanupJobClaimException(job.fileCleanupJobId(), job.claimVersion());
        }
    }
}
