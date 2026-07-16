package com.prizm.cleanup.service;

import com.prizm.cleanup.exception.StaleFileCleanupJobClaimException;
import com.prizm.infrastructure.storage.FileStorage;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Claims work briefly, deletes outside the database transaction, then records the outcome. */
@Service
public class FileCleanupCoordinator {

    private static final Logger log = LoggerFactory.getLogger(FileCleanupCoordinator.class);

    private final FileCleanupJobClaimService claimService;
    private final FileStorage fileStorage;
    private final FileCleanupCompletionService completionService;
    private final FileCleanupFailureClassifier failureClassifier;
    private final FileCleanupFailureService failureService;

    public FileCleanupCoordinator(
            FileCleanupJobClaimService claimService,
            FileStorage fileStorage,
            FileCleanupCompletionService completionService,
            FileCleanupFailureClassifier failureClassifier,
            FileCleanupFailureService failureService) {
        this.claimService = claimService;
        this.fileStorage = fileStorage;
        this.completionService = completionService;
        this.failureClassifier = failureClassifier;
        this.failureService = failureService;
    }

    public boolean processNext() {
        Optional<ClaimedFileCleanupJob> claimed = claimService.claimNext();
        if (claimed.isEmpty()) {
            return false;
        }

        ClaimedFileCleanupJob job = claimed.orElseThrow();
        try {
            fileStorage.delete(job.storageKey());
        }
        catch (RuntimeException exception) {
            try {
                CleanupFailure failure = failureClassifier.classify(exception);
                failureService.handleFailure(job, failure);
                log.warn("File cleanup job {} failed with {}.",
                        job.fileCleanupJobId(), failure.errorCode());
            }
            catch (StaleFileCleanupJobClaimException staleClaim) {
                log.info("Ignored failure from stale file cleanup claim for job {}.", job.fileCleanupJobId());
            }
            return true;
        }

        try {
            completionService.complete(job);
        }
        catch (StaleFileCleanupJobClaimException exception) {
            log.info("Ignored stale file cleanup completion for job {}.", job.fileCleanupJobId());
        }
        catch (RuntimeException exception) {
            // The file was already removed. Leave PROCESSING unchanged for lease recovery to retry idempotently.
            log.error("File cleanup completion update failed for job {} with code CLEANUP_COMPLETION_UPDATE_FAILED.",
                    job.fileCleanupJobId());
        }
        return true;
    }

    public int processBatch(int batchSize) {
        int processed = 0;
        while (processed < batchSize && processNext()) {
            processed++;
        }
        return processed;
    }
}
