package com.prizm.search.v3.indexing.service;

import com.prizm.search.v3.indexing.exception.SearchV3IndexingWorkerException;
import com.prizm.search.v3.indexing.exception.StaleSearchV3IndexingJobClaimException;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Search V3 job claim과 shadow processor, fenced failure transition을 잇는 수동 Worker 진입점이다. */
@Service
public class SearchV3IndexingCoordinator {

    private static final Logger log = LoggerFactory.getLogger(SearchV3IndexingCoordinator.class);

    private final SearchV3IndexingJobService jobService;
    private final SearchV3ShadowIndexingProcessor processor;

    public SearchV3IndexingCoordinator(
            SearchV3IndexingJobService jobService,
            SearchV3ShadowIndexingProcessor processor) {
        this.jobService = jobService;
        this.processor = processor;
    }

    /** 대기 중인 Search V3 shadow job을 최대 한 건 처리한다. 자동 scheduler는 PRZ-040 범위가 아니다. */
    public boolean processNext() {
        Optional<SearchV3IndexingJobClaim> claimed = jobService.claimNext();
        if (claimed.isEmpty()) {
            return false;
        }

        SearchV3IndexingJobClaim claim = claimed.orElseThrow();
        try {
            processor.process(claim);
        }
        catch (StaleSearchV3IndexingJobClaimException exception) {
            log.info("Ignored stale Search V3 indexing claim for job {}.", claim.jobId());
        }
        catch (SearchV3IndexingWorkerException exception) {
            recordFailure(claim, exception);
        }
        catch (RuntimeException exception) {
            recordFailure(
                    claim,
                    new SearchV3IndexingWorkerException(
                            com.prizm.search.v3.indexing.model.SearchV3IndexingFailureStage.STORAGE,
                            false,
                            exception.getMessage() == null ? "Search V3 indexing failed." : exception.getMessage(),
                            exception));
        }
        return true;
    }

    private void recordFailure(
            SearchV3IndexingJobClaim claim,
            SearchV3IndexingWorkerException failure) {
        try {
            jobService.handleFailure(
                    claim,
                    failure.retryable(),
                    failure.failureStage(),
                    failure.getMessage());
            log.warn(
                    "Search V3 indexing job {} failed at {}.",
                    claim.jobId(),
                    failure.failureStage(),
                    failure);
        }
        catch (StaleSearchV3IndexingJobClaimException stale) {
            log.info("Ignored failure from stale Search V3 indexing claim for job {}.", claim.jobId());
        }
    }
}
