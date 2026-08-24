package com.prizm.cleanup.service;

import com.prizm.cleanup.exception.StaleFileCleanupJobClaimException;
import com.prizm.infrastructure.storage.FileStorage;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * cleanup 작업을 짧게 claim한 뒤 파일을 지우고, 결과를 별도 DB 트랜잭션에 기록한다.
 *
 * <p>파일시스템과 DB는 함께 커밋할 수 없으므로 삭제하는 동안 행 잠금을 유지하지 않는다.
 * 파일은 지워졌지만 완료 기록이 실패한 경우 PROCESSING lease를 남겨 recovery가 같은 삭제를
 * 다시 시도하게 하며, 늦게 도착한 이전 claim의 결과는 claim version으로 무시한다.</p>
 */
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

    /** claim 가능한 작업 하나를 처리하며, 작업이 없을 때만 {@code false}를 반환한다. */
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
            // 파일은 이미 없어졌을 수 있으므로 PROCESSING을 유지해 lease recovery가 멱등 삭제를 다시 시도한다.
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
