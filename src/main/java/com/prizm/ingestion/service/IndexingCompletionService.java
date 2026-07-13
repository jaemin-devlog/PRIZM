package com.prizm.ingestion.service;

import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.exception.DocumentNotFoundException;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.repository.ProcessingJobClaimRepository;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 완성된 청크 저장과 문서 활성화·작업 완료를 한 DB 트랜잭션으로 확정한다. */
@Service
public class IndexingCompletionService {

    private final ProcessingJobRepository processingJobRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ProcessingJobClaimRepository claimRepository;

    public IndexingCompletionService(
            ProcessingJobRepository processingJobRepository,
            DocumentVersionRepository documentVersionRepository,
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            ProcessingJobClaimRepository claimRepository) {
        this.processingJobRepository = processingJobRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.claimRepository = claimRepository;
    }

    @Transactional
    public void complete(ClaimedProcessingJob claimedJob, List<IndexedChunk> chunks) {
        ProcessingJob job = processingJobRepository.findByIdForUpdate(claimedJob.processingJobId())
                .orElseThrow(() -> new IllegalStateException("Processing job was not found."));
        job.requireClaim(claimedJob.claimVersion());
        if (!job.getDocumentVersionId().equals(claimedJob.documentVersionId())) {
            throw new IllegalStateException("Claimed job and document version do not match.");
        }
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("At least one indexed chunk is required.");
        }
        DocumentVersion version = documentVersionRepository.findByIdForUpdate(claimedJob.documentVersionId())
                .orElseThrow(() -> new DocumentVersionNotFoundException(claimedJob.documentVersionId()));
        if (version.getStatus() != DocumentVersionStatus.INDEXING) {
            throw new IllegalStateException("Document version must be INDEXING before completion.");
        }
        Document document = documentRepository.findByIdForUpdate(version.getDocumentId())
                .orElseThrow(() -> new DocumentNotFoundException(version.getDocumentId()));

        // 재시도 전에 남은 미완성 청크가 있어도 같은 트랜잭션에서 전부 교체한다.
        documentChunkRepository.replaceAll(version.getId(), chunks);
        long storedCount = documentChunkRepository.countByDocumentVersionId(version.getId());
        if (storedCount != chunks.size()) {
            throw new IllegalStateException("Stored chunk count does not match generated chunk count.");
        }

        version.activate();
        document.activateVersion(version.getId());
        job.complete(claimRepository.currentDatabaseTime());
    }
}
