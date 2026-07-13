package com.prizm.ingestion.service;

import com.prizm.ingestion.exception.StaleProcessingJobClaimException;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 작업 선점과 실제 처리 사이의 트랜잭션 경계를 유지하는 Worker 진입점이다. */
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

    /** 대기 중인 작업을 최대 한 건 처리한다. */
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
                failureService.handleFailure(job, failureClassifier.isRetryable(exception), exception.getMessage());
                log.warn("Indexing job {} failed with {}.",
                        job.processingJobId(), exception.getClass().getSimpleName());
            }
            catch (StaleProcessingJobClaimException staleClaim) {
                log.info("Ignored failure from stale indexing claim for job {}.", job.processingJobId());
            }
        }
        return true;
    }
}
