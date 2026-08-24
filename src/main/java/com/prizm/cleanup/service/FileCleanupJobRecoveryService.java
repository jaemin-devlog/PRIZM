package com.prizm.cleanup.service;

import com.prizm.cleanup.exception.StaleFileCleanupJobClaimException;
import com.prizm.cleanup.repository.FileCleanupJobRepository;
import com.prizm.ingestion.service.IndexingRetryPolicy;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * lease가 만료된 PROCESSING 작업을 재시도 대기 또는 최종 실패로 회수한다.
 * 회수 갱신이 claim version도 올리므로 중단됐던 Worker가 나중에 완료 상태를 덮어쓸 수 없다.
 */
@Service
public class FileCleanupJobRecoveryService {

    private static final String LEASE_EXPIRED_CODE = "LEASE_EXPIRED";

    private final FileCleanupJobRepository repository;
    private final IndexingRetryPolicy retryPolicy;

    public FileCleanupJobRecoveryService(FileCleanupJobRepository repository, IndexingRetryPolicy retryPolicy) {
        this.repository = repository;
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    public boolean recoverNext() {
        Optional<ClaimedFileCleanupJob> expired = repository.lockNextExpired();
        if (expired.isEmpty()) {
            return false;
        }

        ClaimedFileCleanupJob job = expired.orElseThrow();
        boolean changed;
        if (retryPolicy.canRetry(job.attempts())) {
            Instant databaseNow = repository.currentDatabaseTime();
            changed = repository.recoverForRetry(
                    job.fileCleanupJobId(),
                    job.claimVersion(),
                    retryPolicy.nextRetryAt(job.attempts(), databaseNow),
                    LEASE_EXPIRED_CODE);
        }
        else {
            changed = repository.recoverAsFailed(job.fileCleanupJobId(), job.claimVersion(), LEASE_EXPIRED_CODE);
        }
        if (!changed) {
            throw new StaleFileCleanupJobClaimException(job.fileCleanupJobId(), job.claimVersion());
        }
        return true;
    }
}
