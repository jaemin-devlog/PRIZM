package com.prizm.ingestion.service;

import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.repository.ProcessingJobClaimRepository;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 임대가 만료된 PROCESSING 작업을 DB 시간 기준으로 재시도하거나 최종 실패시킨다. */
@Service
public class ProcessingJobRecoveryService {

    private static final String LEASE_EXPIRED_MESSAGE = "Processing lease expired before completion.";

    private final ProcessingJobClaimRepository claimRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final IndexingRetryPolicy retryPolicy;

    public ProcessingJobRecoveryService(
            ProcessingJobClaimRepository claimRepository,
            ProcessingJobRepository processingJobRepository,
            DocumentVersionRepository documentVersionRepository,
            DocumentChunkRepository documentChunkRepository,
            IndexingRetryPolicy retryPolicy) {
        this.claimRepository = claimRepository;
        this.processingJobRepository = processingJobRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    public boolean recoverNext() {
        Optional<Long> expiredId = claimRepository.lockNextExpiredId();
        if (expiredId.isEmpty()) {
            return false;
        }

        ProcessingJob job = processingJobRepository.findByIdForUpdate(expiredId.orElseThrow())
                .orElseThrow(() -> new IllegalStateException("Expired processing job disappeared."));
        DocumentVersion version = documentVersionRepository.findByIdForUpdate(job.getDocumentVersionId())
                .orElseThrow(() -> new DocumentVersionNotFoundException(job.getDocumentVersionId()));
        if (version.getStatus() != DocumentVersionStatus.PROCESSING) {
            throw new IllegalStateException("Expired processing job must reference a PROCESSING version.");
        }

        documentChunkRepository.deleteByDocumentVersionId(version.getId());
        Instant databaseNow = claimRepository.currentDatabaseTime();
        if (retryPolicy.canRetry(job.getRetryCount())) {
            job.recoverForRetry(
                    retryPolicy.nextRetryAt(job.getRetryCount(), databaseNow),
                    LEASE_EXPIRED_MESSAGE);
        }
        else {
            job.recoverAsFailed(databaseNow, LEASE_EXPIRED_MESSAGE);
            version.failProcessing();
        }
        return true;
    }
}
