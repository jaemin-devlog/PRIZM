package com.prizm.ingestion.service;

import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.exception.StaleProcessingJobClaimException;
import com.prizm.ingestion.repository.ProcessingJobClaimRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 현재 Worker가 소유한 작업의 임대 시간만 조건부로 연장한다. */
@Service
public class ProcessingJobLeaseService {

    private final ProcessingJobClaimRepository claimRepository;
    private final IngestionProperties properties;

    public ProcessingJobLeaseService(
            ProcessingJobClaimRepository claimRepository,
            IngestionProperties properties) {
        this.claimRepository = claimRepository;
        this.properties = properties;
    }

    @Transactional
    public Instant renew(ClaimedProcessingJob claimedJob) {
        return claimRepository.renewLease(
                        claimedJob.processingJobId(),
                        claimedJob.claimVersion(),
                        properties.getLeaseDuration())
                .orElseThrow(() -> new StaleProcessingJobClaimException(
                        claimedJob.processingJobId(), claimedJob.claimVersion()));
    }
}
