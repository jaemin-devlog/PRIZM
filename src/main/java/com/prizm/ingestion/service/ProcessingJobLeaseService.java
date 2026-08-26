package com.prizm.ingestion.service;

import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.exception.StaleProcessingJobClaimException;
import com.prizm.ingestion.repository.ProcessingJobClaimRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 현재 Worker가 소유한 처리 시도의 임대만 조건부로 연장한다.
 *
 * <p>작업이 {@code PROCESSING}이고 {@code claimVersion}이 같을 때만 DB 시간이 새 만료 시각을 계산한다.
 * 갱신된 행이 없으면 임대를 잃은 처리 시도로 간주해 이전 Worker가 계속 진행하지 못하게 한다.</p>
 */
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

    /** 현재 claim의 임대를 연장하고 DB가 계산한 새 만료 시각을 반환한다. */
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
