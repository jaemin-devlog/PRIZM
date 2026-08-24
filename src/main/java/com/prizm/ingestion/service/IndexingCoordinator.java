package com.prizm.ingestion.service;

import com.prizm.ingestion.exception.StaleProcessingJobClaimException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 작업 선점, 외부 문서 처리, 실패 기록의 경계를 잇는 색인 Worker 진입점이다.
 *
 * <p>선점 트랜잭션을 먼저 끝낸 뒤 파일 I/O와 임베딩을 수행하고, 실패하면 다시 짧은 트랜잭션으로 상태를
 * 기록한다. 이미 임대를 잃은 Worker의 완료나 실패는 현재 처리 시도를 덮어쓰지 않도록 무시한다.</p>
 */
@Service
public class IndexingCoordinator {

    private static final Logger log = LoggerFactory.getLogger(IndexingCoordinator.class);

    private final ProcessingJobClaimService claimService;
    private final DocumentIndexingProcessor processor;
    private final IndexingFailureClassifier failureClassifier;
    private final IndexingFailureService failureService;

    public IndexingCoordinator(
            ProcessingJobClaimService claimService,
            DocumentIndexingProcessor processor,
            IndexingFailureClassifier failureClassifier,
            IndexingFailureService failureService) {
        this.claimService = claimService;
        this.processor = processor;
        this.failureClassifier = failureClassifier;
        this.failureService = failureService;
    }

    /** 대기 중인 작업을 최대 한 건 선점해 완료 또는 실패 기록까지 진행한다. */
    public boolean processNext() {
        Optional<ClaimedProcessingJob> claimed = claimService.claimNext();
        if (claimed.isEmpty()) {
            return false;
        }

        ClaimedProcessingJob job = claimed.orElseThrow();
        try {
            processor.process(job);
        }
        catch (StaleProcessingJobClaimException exception) {
            log.info("Ignored stale indexing claim for job {}.", job.processingJobId());
        }
        catch (RuntimeException exception) {
            try {
                failureService.handleFailure(
                        job,
                        failureClassifier.isRetryable(exception),
                        failureClassifier.failureCode(exception),
                        exception.getMessage());
                log.warn("Indexing job {} failed with {}.",
                        job.processingJobId(), exception.getClass().getSimpleName(), exception);
            }
            catch (StaleProcessingJobClaimException staleClaim) {
                log.info("Ignored failure from stale indexing claim for job {}.", job.processingJobId());
            }
        }
        return true;
    }
}
