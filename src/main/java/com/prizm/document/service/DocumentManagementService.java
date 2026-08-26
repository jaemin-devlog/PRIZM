package com.prizm.document.service;

import com.prizm.cleanup.service.FileCleanupJobService;
import com.prizm.changelog.repository.DocumentChangeLogRepository;
import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.exception.DocumentManagementErrorCode;
import com.prizm.document.exception.DocumentManagementException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 문서 메타데이터를 수정하고 종료된 문서 또는 과거 버전을 소유자 범위에서 삭제한다.
 *
 * <p>DB 메타데이터 삭제와 cleanup 작업 등록은 같은 트랜잭션으로 묶지만 실제 파일 삭제는
 * 커밋 뒤 Worker에 맡긴다. 처리 중인 문서는 삭제하지 않으며, 개별 버전을 지울 때는 현재
 * ACTIVE 버전도 보호해 검색 가능한 원본을 실수로 잃지 않는다.</p>
 */
@Service
public class DocumentManagementService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentChangeLogRepository documentChangeLogRepository;
    private final ProcessingJobRepository processingJobRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final FileCleanupJobService fileCleanupJobService;

    public DocumentManagementService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            DocumentChangeLogRepository documentChangeLogRepository,
            ProcessingJobRepository processingJobRepository,
            DocumentChunkRepository documentChunkRepository,
            FileCleanupJobService fileCleanupJobService) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.documentChangeLogRepository = documentChangeLogRepository;
        this.processingJobRepository = processingJobRepository;
        this.documentChunkRepository = documentChunkRepository;
        this.fileCleanupJobService = fileCleanupJobService;
    }

    @Transactional
    public void updateMetadata(Long ownerUserId, Long documentId, String title, DocumentType documentType) {
        String normalizedTitle = normalizeTitle(title);
        DocumentType safeDocumentType = requireDocumentType(documentType);
        Document document = documentRepository.findByIdAndOwnerUserIdForUpdate(documentId, ownerUserId)
                .orElseThrow(() -> new com.prizm.document.exception.DocumentNotFoundException(documentId));
        document.updateMetadata(normalizedTitle, safeDocumentType);
    }

    /**
     * 종료된 문서의 메타데이터와 모든 버전을 지우고 원본별 cleanup 작업을 함께 등록한다.
     * 실제 파일은 트랜잭션 밖에서 삭제해 DB 롤백과 파일 유실이 엇갈리지 않게 한다.
     *
     * @return 문서를 삭제했으면 {@code true}, 소유자 범위에서 이미 없으면 {@code false}
     */
    @Transactional
    public boolean delete(Long ownerUserId, Long documentId) {
        Optional<Document> foundDocument = documentRepository.findByIdAndOwnerUserIdForUpdate(documentId, ownerUserId);
        if (foundDocument.isEmpty()) {
            return false;
        }

        Document document = foundDocument.orElseThrow();
        List<DocumentVersion> versions = documentVersionRepository
                .findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(ownerUserId, documentId);
        if (!versions.isEmpty() && isInFlight(versions.get(0).getStatus())) {
            throw new DocumentManagementException(
                    DocumentManagementErrorCode.DOCUMENT_PROCESSING,
                    "Document processing must finish before deletion.");
        }
        List<Long> versionIds = versions.stream().map(DocumentVersion::getId).toList();
        List<ProcessingJob> jobs = versionIds.isEmpty()
                ? List.of()
                : processingJobRepository.findByOwnerUserIdAndDocumentVersionIdIn(ownerUserId, versionIds);
        if (jobs.stream().anyMatch(job -> isNonTerminal(job.getStatus()))) {
            throw new DocumentManagementException(
                    DocumentManagementErrorCode.DOCUMENT_PROCESSING,
                    "Document processing must finish before deletion.");
        }

        for (DocumentVersion version : versions) {
            fileCleanupJobService.registerPendingCleanupInCurrentTransaction(version.getStoredFilePath());
        }

        if (!versionIds.isEmpty()) {
            documentChangeLogRepository.deleteByOwnerUserIdAndDocumentVersionIdIn(ownerUserId, versionIds);
            documentChangeLogRepository.flush();
        }
        processingJobRepository.deleteAll(jobs);
        processingJobRepository.flush();
        for (DocumentVersion version : versions) {
            documentChunkRepository.deleteByOwnerUserIdAndDocumentVersionId(ownerUserId, version.getId());
        }
        document.clearActiveVersion();
        documentRepository.saveAndFlush(document);
        documentVersionRepository.deleteAll(versions);
        documentVersionRepository.flush();
        documentRepository.delete(document);
        documentRepository.flush();
        return true;
    }

    /**
     * 현재 검색 포인터를 건드리지 않고 종료된 과거 버전 하나를 삭제한다.
     * ACTIVE 버전과 처리 중 버전은 거부하고, 원본 삭제는 커밋 뒤 cleanup Worker에 맡긴다.
     */
    @Transactional
    public boolean deleteVersion(Long ownerUserId, Long documentId, Long versionId) {
        Optional<Document> foundDocument = documentRepository.findByIdAndOwnerUserIdForUpdate(documentId, ownerUserId);
        if (foundDocument.isEmpty()) {
            return false;
        }

        Document document = foundDocument.orElseThrow();
        Optional<DocumentVersion> foundVersion = documentVersionRepository
                .findByIdAndOwnerUserIdAndDocumentIdForUpdate(versionId, ownerUserId, documentId);
        if (foundVersion.isEmpty()) {
            return false;
        }

        DocumentVersion version = foundVersion.orElseThrow();
        if (versionId.equals(document.getActiveVersionId())) {
            throw new DocumentManagementException(
                    DocumentManagementErrorCode.DOCUMENT_VERSION_ACTIVE,
                    "The active document version cannot be deleted.");
        }
        if (isInFlight(version.getStatus())) {
            throw new DocumentManagementException(
                    DocumentManagementErrorCode.DOCUMENT_PROCESSING,
                    "Document processing must finish before deletion.");
        }

        Optional<ProcessingJob> foundJob = processingJobRepository
                .findByOwnerUserIdAndDocumentVersionId(ownerUserId, versionId);
        if (foundJob.isPresent() && isNonTerminal(foundJob.orElseThrow().getStatus())) {
            throw new DocumentManagementException(
                    DocumentManagementErrorCode.DOCUMENT_PROCESSING,
                    "Document processing must finish before deletion.");
        }

        fileCleanupJobService.registerPendingCleanupInCurrentTransaction(version.getStoredFilePath());
        documentChangeLogRepository.deleteByOwnerUserIdAndDocumentVersionIdIn(ownerUserId, List.of(versionId));
        documentChangeLogRepository.flush();
        foundJob.ifPresent(processingJobRepository::delete);
        processingJobRepository.flush();
        documentChunkRepository.deleteByOwnerUserIdAndDocumentVersionId(ownerUserId, versionId);
        documentVersionRepository.delete(version);
        documentVersionRepository.flush();
        return true;
    }

    private boolean isNonTerminal(ProcessingJobStatus status) {
        return status == ProcessingJobStatus.PENDING
                || status == ProcessingJobStatus.RETRY_WAIT
                || status == ProcessingJobStatus.PROCESSING;
    }

    private boolean isInFlight(DocumentVersionStatus status) {
        return status == DocumentVersionStatus.QUARANTINED
                || status == DocumentVersionStatus.PROCESSING;
    }

    private String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new DocumentManagementException(
                    DocumentManagementErrorCode.INVALID_TITLE,
                    "title must not be blank");
        }
        String normalized = title.trim();
        if (normalized.length() > 200) {
            throw new DocumentManagementException(
                    DocumentManagementErrorCode.INVALID_TITLE,
                    "title must be at most 200 characters");
        }
        return normalized;
    }

    private DocumentType requireDocumentType(DocumentType documentType) {
        if (documentType == null) {
            throw new DocumentManagementException(
                    DocumentManagementErrorCode.INVALID_DOCUMENT_TYPE,
                    "documentType is required");
        }
        return documentType;
    }
}
