package com.prizm.ingestion.service;

import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.repository.ProcessingJobClaimRepository;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 작업 한 건을 선점하고 문서 버전을 INDEXING으로 바꾸는 짧은 트랜잭션이다. */
@Service
public class ProcessingJobClaimService {

    private final ProcessingJobClaimRepository claimRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final IngestionProperties properties;

    public ProcessingJobClaimService(
            ProcessingJobClaimRepository claimRepository,
            ProcessingJobRepository processingJobRepository,
            DocumentVersionRepository documentVersionRepository,
            IngestionProperties properties) {
        this.claimRepository = claimRepository;
        this.processingJobRepository = processingJobRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.properties = properties;
    }

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
        DocumentVersion version = documentVersionRepository.findByIdForUpdate(job.getDocumentVersionId())
                .orElseThrow(() -> new DocumentVersionNotFoundException(job.getDocumentVersionId()));
        if (version.getStatus() == DocumentVersionStatus.APPROVED) {
            version.startIndexing();
        }
        else if (version.getStatus() != DocumentVersionStatus.INDEXING) {
            throw new IllegalStateException("Only APPROVED or INDEXING document versions can be claimed.");
        }
        return Optional.of(claimedJob);
    }
}
