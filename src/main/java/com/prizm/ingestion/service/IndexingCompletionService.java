package com.prizm.ingestion.service;

import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.exception.DocumentNotFoundException;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.repository.ProcessingJobClaimRepository;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 완성된 청크 저장, 문서 버전 활성화, 작업 완료를 한 DB 트랜잭션으로 확정한다.
 *
 * <p>현재 claim과 소유자 계층을 다시 검증한 뒤 청크를 전부 교체하고 저장 개수까지 확인한다. 이 검증이
 * 끝나야 새 버전과 문서의 활성 버전 포인터를 함께 바꾸므로, 일부 청크만 저장된 버전은 검색 대상이 되지
 * 않는다. 어느 단계든 실패하면 전체 트랜잭션이 롤백돼 이전 ACTIVE 버전이 유지된다.</p>
 */
@Service
public class IndexingCompletionService {

    private final ProcessingJobRepository processingJobRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final ProcessingJobClaimRepository claimRepository;
    private final EmbeddingValidator embeddingValidator;

    public IndexingCompletionService(
            ProcessingJobRepository processingJobRepository,
            DocumentVersionRepository documentVersionRepository,
            DocumentRepository documentRepository,
            DocumentChunkRepository documentChunkRepository,
            ProcessingJobClaimRepository claimRepository,
            EmbeddingValidator embeddingValidator) {
        this.processingJobRepository = processingJobRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentRepository = documentRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.claimRepository = claimRepository;
        this.embeddingValidator = embeddingValidator;
    }

    /** 현재 처리 시도가 만든 청크를 저장하고 새 문서 버전을 원자적으로 활성화한다. */
    @Transactional
    public void complete(ClaimedProcessingJob claimedJob, List<IndexedChunk> chunks) {
        if (chunks.isEmpty()) {
            throw new IllegalArgumentException("At least one indexed chunk is required.");
        }
        chunks.forEach(chunk -> embeddingValidator.validate(chunk.embedding()));

        ProcessingJob job = processingJobRepository.findByIdForUpdate(claimedJob.processingJobId())
                .orElseThrow(() -> new IllegalStateException("Processing job was not found."));
        job.requireClaim(claimedJob.claimVersion());
        if (!job.getDocumentVersionId().equals(claimedJob.documentVersionId())) {
            throw new IllegalStateException("Claimed job and document version do not match.");
        }
        requireSameOwner(claimedJob.ownerUserId(), job.getOwnerUserId());
        DocumentVersion version = documentVersionRepository.findByIdForUpdate(claimedJob.documentVersionId())
                .orElseThrow(() -> new DocumentVersionNotFoundException(claimedJob.documentVersionId()));
        if (version.getStatus() != DocumentVersionStatus.PROCESSING) {
            throw new IllegalStateException("Document version must be PROCESSING before completion.");
        }
        requireSameOwner(claimedJob.ownerUserId(), version.getOwnerUserId());
        Document document = documentRepository.findByIdForUpdate(version.getDocumentId())
                .orElseThrow(() -> new DocumentNotFoundException(version.getDocumentId()));
        requireSameOwner(claimedJob.ownerUserId(), document.getOwnerUserId());

        // 이전 처리 시도의 일부 청크가 남아 있어도 새 결과와 섞이지 않도록 같은 트랜잭션에서 전부 교체한다.
        documentChunkRepository.replaceAll(claimedJob.ownerUserId(), version.getId(), chunks);
        long storedCount = documentChunkRepository.countByOwnerUserIdAndDocumentVersionId(
                claimedJob.ownerUserId(), version.getId());
        if (storedCount != chunks.size()) {
            throw new IllegalStateException("Stored chunk count does not match generated chunk count.");
        }

        // 생성 개수와 저장 개수가 일치한 뒤에만 검색이 참조하는 ACTIVE 버전 포인터를 바꾼다.
        version.activate();
        document.activateVersion(version.getId());
        job.complete(claimRepository.currentDatabaseTime());
    }

    private void requireSameOwner(Long claimedOwnerUserId, Long actualOwnerUserId) {
        if (!claimedOwnerUserId.equals(actualOwnerUserId)) {
            throw new IllegalStateException("Processing job ownership does not match its document hierarchy.");
        }
    }
}
