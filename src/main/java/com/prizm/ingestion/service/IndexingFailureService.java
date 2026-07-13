package com.prizm.ingestion.service;

import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.repository.ProcessingJobClaimRepository;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 실패한 작업의 부분 청크를 제거하고 재시도 또는 최종 실패 상태를 기록한다. */
@Service
public class IndexingFailureService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final ProcessingJobRepository processingJobRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ProcessingJobClaimRepository claimRepository;
    private final IndexingRetryPolicy retryPolicy;

    public IndexingFailureService(
            ProcessingJobRepository processingJobRepository,
            DocumentVersionRepository documentVersionRepository,
            DocumentChunkRepository documentChunkRepository,
            ProcessingJobClaimRepository claimRepository,
            IndexingRetryPolicy retryPolicy) {
        this.processingJobRepository = processingJobRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.claimRepository = claimRepository;
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    public ProcessingJobStatus handleFailure(
            ClaimedProcessingJob claimedJob,
            boolean retryable,
            String errorMessage) {
        ProcessingJob job = processingJobRepository.findByIdForUpdate(claimedJob.processingJobId())
                .orElseThrow(() -> new IllegalStateException("Processing job was not found."));
        job.requireClaim(claimedJob.claimVersion());
        if (!job.getDocumentVersionId().equals(claimedJob.documentVersionId())) {
            throw new IllegalStateException("Claimed job and document version do not match.");
        }
        DocumentVersion version = documentVersionRepository.findByIdForUpdate(claimedJob.documentVersionId())
                .orElseThrow(() -> new DocumentVersionNotFoundException(claimedJob.documentVersionId()));
        documentChunkRepository.deleteByDocumentVersionId(version.getId());

        String safeMessage = truncate(errorMessage == null ? "Indexing failed." : errorMessage);
        Instant now = claimRepository.currentDatabaseTime();
        if (retryable && retryPolicy.canRetry(job.getRetryCount())) {
            job.scheduleRetry(retryPolicy.nextRetryAt(job.getRetryCount(), now), safeMessage);
        }
        else {
            job.fail(now, safeMessage);
            if (version.getStatus() == DocumentVersionStatus.INDEXING) {
                version.failIndexing();
            }
        }
        return job.getStatus();
    }

    private String truncate(String message) {
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
