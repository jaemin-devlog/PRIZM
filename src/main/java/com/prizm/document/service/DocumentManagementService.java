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

/** Owns metadata changes and safe, asynchronous-file-backed document deletion. */
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
     * Deletes only terminal document metadata. File removal is queued in the same transaction and is
     * completed later by the Cleanup Worker outside this transaction.
     *
     * @return {@code true} when a document was removed; {@code false} for an already absent owner-scoped document
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
