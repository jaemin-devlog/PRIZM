package com.prizm.ingestion.service;

import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.exception.DocumentNotFoundException;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.entity.ProcessingFailureCode;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.repository.ProcessingJobClaimRepository;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실패한 처리 시도의 부분 청크를 제거하고 재시도 또는 최종 실패를 기록한다.
 *
 * <p>청크를 지우기 전에 claim과 작업·버전·문서의 소유자를 다시 확인해 임대를 잃은 Worker가 현재 작업을
 * 훼손하지 못하게 한다. 재시도할 때는 버전을 {@code PROCESSING}으로 유지하고, 재시도할 수 없을 때만
 * 버전을 실패 처리한다. 문서의 활성 버전 포인터는 바꾸지 않으므로 기존 ACTIVE 버전은 그대로 남는다.</p>
 */
@Service
public class IndexingFailureService {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 2000;

    private final ProcessingJobRepository processingJobRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ProcessingJobClaimRepository claimRepository;
    private final IndexingRetryPolicy retryPolicy;

    public IndexingFailureService(
            ProcessingJobRepository processingJobRepository,
            DocumentVersionRepository documentVersionRepository,
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            ProcessingJobClaimRepository claimRepository,
            IndexingRetryPolicy retryPolicy) {
        this.processingJobRepository = processingJobRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.claimRepository = claimRepository;
        this.retryPolicy = retryPolicy;
    }

    /** 현재 claim의 실패를 재시도 정책에 따라 {@code RETRY_WAIT} 또는 {@code FAILED}로 전환한다. */
    @Transactional
    public ProcessingJobStatus handleFailure(
            ClaimedProcessingJob claimedJob,
            boolean retryable,
            ProcessingFailureCode failureCode,
            String errorMessage) {
        ProcessingJob job = processingJobRepository.findByIdForUpdate(claimedJob.processingJobId())
                .orElseThrow(() -> new IllegalStateException("Processing job was not found."));
        job.requireClaim(claimedJob.claimVersion());
        if (!job.getDocumentVersionId().equals(claimedJob.documentVersionId())) {
            throw new IllegalStateException("Claimed job and document version do not match.");
        }
        requireSameOwner(claimedJob.ownerUserId(), job.getOwnerUserId());
        DocumentVersion version = documentVersionRepository.findByIdForUpdate(claimedJob.documentVersionId())
                .orElseThrow(() -> new DocumentVersionNotFoundException(claimedJob.documentVersionId()));
        requireSameOwner(claimedJob.ownerUserId(), version.getOwnerUserId());
        Document document = documentRepository.findByIdForUpdate(version.getDocumentId())
                .orElseThrow(() -> new DocumentNotFoundException(version.getDocumentId()));
        requireSameOwner(claimedJob.ownerUserId(), document.getOwnerUserId());
        documentChunkRepository.deleteByOwnerUserIdAndDocumentVersionId(
                claimedJob.ownerUserId(), version.getId());

        String safeMessage = truncate(errorMessage == null ? "Indexing failed." : errorMessage);
        Instant now = claimRepository.currentDatabaseTime();
        if (retryable && retryPolicy.canRetry(job.getRetryCount())) {
            job.scheduleRetry(retryPolicy.nextRetryAt(job.getRetryCount(), now), safeMessage, failureCode);
        }
        else {
            job.fail(now, safeMessage, failureCode);
            if (version.getStatus() == DocumentVersionStatus.PROCESSING) {
                version.failProcessing();
            }
        }
        return job.getStatus();
    }

    private void requireSameOwner(Long claimedOwnerUserId, Long actualOwnerUserId) {
        if (!claimedOwnerUserId.equals(actualOwnerUserId)) {
            throw new IllegalStateException("Processing job ownership does not match its document hierarchy.");
        }
    }

    private String truncate(String message) {
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
