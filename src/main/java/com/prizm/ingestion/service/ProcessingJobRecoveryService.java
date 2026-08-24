package com.prizm.ingestion.service;

import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.exception.DocumentNotFoundException;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingFailureCode;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.repository.ProcessingJobClaimRepository;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 임대가 만료된 {@code PROCESSING} 작업을 재시도 대기 또는 최종 실패로 복구한다.
 *
 * <p>만료 작업을 {@code SKIP LOCKED}로 한 건씩 잠그고 소유자 계층을 확인한 뒤, 남은 부분 청크를
 * 제거한다. 복구하면서 {@code claimVersion}을 올려 이전 Worker의 권한을 끊고 DB 시간으로 다음 재시도를
 * 계산한다. 최종 실패해도 문서의 활성 버전 포인터는 건드리지 않는다.</p>
 */
@Service
public class ProcessingJobRecoveryService {

    private static final String LEASE_EXPIRED_MESSAGE = "Processing lease expired before completion.";

    private final ProcessingJobClaimRepository claimRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final IndexingRetryPolicy retryPolicy;

    public ProcessingJobRecoveryService(
            ProcessingJobClaimRepository claimRepository,
            ProcessingJobRepository processingJobRepository,
            DocumentVersionRepository documentVersionRepository,
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            IndexingRetryPolicy retryPolicy) {
        this.claimRepository = claimRepository;
        this.processingJobRepository = processingJobRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.retryPolicy = retryPolicy;
    }

    /** 가장 오래전에 만료된 작업 한 건을 잠가 현재 재시도 예산에 맞게 복구한다. */
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
        requireSameOwner(job.getOwnerUserId(), version.getOwnerUserId());
        Document document = documentRepository.findByIdForUpdate(version.getDocumentId())
                .orElseThrow(() -> new DocumentNotFoundException(version.getDocumentId()));
        requireSameOwner(job.getOwnerUserId(), document.getOwnerUserId());

        documentChunkRepository.deleteByOwnerUserIdAndDocumentVersionId(job.getOwnerUserId(), version.getId());
        Instant databaseNow = claimRepository.currentDatabaseTime();
        if (retryPolicy.canRetry(job.getRetryCount())) {
            job.recoverForRetry(
                    retryPolicy.nextRetryAt(job.getRetryCount(), databaseNow),
                    LEASE_EXPIRED_MESSAGE,
                    ProcessingFailureCode.DOCUMENT_PROCESSING_FAILED);
        }
        else {
            job.recoverAsFailed(
                    databaseNow,
                    LEASE_EXPIRED_MESSAGE,
                    ProcessingFailureCode.DOCUMENT_PROCESSING_FAILED);
            version.failProcessing();
        }
        return true;
    }

    private void requireSameOwner(Long jobOwnerUserId, Long actualOwnerUserId) {
        if (!jobOwnerUserId.equals(actualOwnerUserId)) {
            throw new IllegalStateException("Processing job ownership does not match its document hierarchy.");
        }
    }
}
