package com.prizm.ingestion.service;

import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.exception.DocumentNotFoundException;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.repository.ProcessingJobClaimRepository;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 색인 작업 한 건을 선점하고 해당 문서 버전을 {@code PROCESSING}으로 전환한다.
 *
 * <p>작업·버전·문서를 같은 짧은 트랜잭션에서 잠그고 세 단계의 소유자가 모두 같은지 확인한다.
 * 파일 읽기와 임베딩은 이 트랜잭션이 끝난 뒤 실행해 장시간 DB 락을 잡지 않는다.</p>
 */
@Service
public class ProcessingJobClaimService {

    private final ProcessingJobClaimRepository claimRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;
    private final IngestionProperties properties;

    public ProcessingJobClaimService(
            ProcessingJobClaimRepository claimRepository,
            ProcessingJobRepository processingJobRepository,
            DocumentVersionRepository documentVersionRepository,
            DocumentRepository documentRepository,
            IngestionProperties properties) {
        this.claimRepository = claimRepository;
        this.processingJobRepository = processingJobRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentRepository = documentRepository;
        this.properties = properties;
    }

    /** 다음 작업을 선점하고 문서 계층과 fencing 값이 일치하는 처리 시도를 반환한다. */
    @Transactional
    public Optional<ClaimedProcessingJob> claimNext() {
        Optional<ClaimedProcessingJob> claimed = claimRepository.claimNext(properties.getLeaseDuration());
        if (claimed.isEmpty()) {
            return Optional.empty();
        }

        ClaimedProcessingJob claimedJob = claimed.orElseThrow();
        ProcessingJob job = processingJobRepository.findByIdForUpdate(claimedJob.processingJobId())
                .orElseThrow(() -> new IllegalStateException("Claimed processing job disappeared."));
        job.requireClaim(claimedJob.claimVersion());
        if (!job.getDocumentVersionId().equals(claimedJob.documentVersionId())) {
            throw new IllegalStateException("Claimed job and document version do not match.");
        }
        requireSameOwner(claimedJob.ownerUserId(), job.getOwnerUserId());
        DocumentVersion version = documentVersionRepository.findByIdForUpdate(job.getDocumentVersionId())
                .orElseThrow(() -> new DocumentVersionNotFoundException(job.getDocumentVersionId()));
        requireSameOwner(claimedJob.ownerUserId(), version.getOwnerUserId());
        Document document = documentRepository.findByIdForUpdate(version.getDocumentId())
                .orElseThrow(() -> new DocumentNotFoundException(version.getDocumentId()));
        requireSameOwner(claimedJob.ownerUserId(), document.getOwnerUserId());
        if (version.getStatus() == DocumentVersionStatus.QUARANTINED) {
            version.startProcessing();
        }
        else if (version.getStatus() != DocumentVersionStatus.PROCESSING) {
            throw new IllegalStateException("Only QUARANTINED or PROCESSING document versions can be claimed.");
        }
        return Optional.of(claimedJob);
    }

    private void requireSameOwner(Long claimedOwnerUserId, Long actualOwnerUserId) {
        if (!claimedOwnerUserId.equals(actualOwnerUserId)) {
            throw new IllegalStateException("Processing job ownership does not match its document hierarchy.");
        }
    }
}
