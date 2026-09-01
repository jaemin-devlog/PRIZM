package com.prizm.search.v3.indexing.service;

import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.service.IndexingRetryPolicy;
import com.prizm.search.v3.indexing.exception.StaleSearchV3IndexingJobClaimException;
import com.prizm.search.v3.indexing.exception.StaleSearchV3RecoveryLockException;
import com.prizm.search.v3.indexing.model.SearchV3IndexingFailureStage;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobStatus;
import com.prizm.search.v3.indexing.model.SearchV3RecoveryLock;
import com.prizm.search.v3.indexing.repository.SearchV3IndexingJobRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Search V3 indexing job의 claim, lease, recovery와 failure 정책을 조정한다. */
@Service
public class SearchV3IndexingJobService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final SearchV3IndexingJobRepository repository;
    private final IngestionProperties ingestionProperties;
    private final IndexingRetryPolicy retryPolicy;

    public SearchV3IndexingJobService(
            SearchV3IndexingJobRepository repository,
            IngestionProperties ingestionProperties,
            IndexingRetryPolicy retryPolicy) {
        this.repository = repository;
        this.ingestionProperties = ingestionProperties;
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    public Optional<SearchV3IndexingJobClaim> claimNext() {
        return repository.claimNext(ingestionProperties.getLeaseDuration());
    }

    @Transactional
    public Instant renewLease(SearchV3IndexingJobClaim claim) {
        return repository.renewLease(claim, ingestionProperties.getLeaseDuration())
                .orElseThrow(() -> new StaleSearchV3IndexingJobClaimException(claim));
    }

    @Transactional
    public Optional<SearchV3RecoveryLock> acquireNextRecoveryLock() {
        return repository.acquireNextRecoveryLock(UUID.randomUUID());
    }

    @Transactional
    public SearchV3IndexingJobClaim reclaim(SearchV3RecoveryLock recoveryLock) {
        return repository.reclaim(recoveryLock, ingestionProperties.getLeaseDuration())
                .orElseThrow(() -> new StaleSearchV3RecoveryLockException(recoveryLock));
    }

    @Transactional
    public SearchV3IndexingJobStatus handleFailure(
            SearchV3IndexingJobClaim claim,
            boolean retryable,
            SearchV3IndexingFailureStage failureStage,
            String errorMessage) {
        int completedRetryCount = claim.attemptCount() - 1;
        String safeMessage = truncate(errorMessage == null ? "Search V3 indexing failed." : errorMessage);
        if (retryable && retryPolicy.canRetry(completedRetryCount)) {
            Instant databaseNow = repository.currentDatabaseTime();
            Instant nextRetryAt = retryPolicy.nextRetryAt(completedRetryCount, databaseNow);
            if (!repository.scheduleRetry(claim, nextRetryAt, safeMessage)) {
                throw new StaleSearchV3IndexingJobClaimException(claim);
            }
            return SearchV3IndexingJobStatus.RETRY_WAIT;
        }

        if (!repository.fail(claim, failureStage, safeMessage)) {
            throw new StaleSearchV3IndexingJobClaimException(claim);
        }
        return SearchV3IndexingJobStatus.FAILED;
    }

    private String truncate(String message) {
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
