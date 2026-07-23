package com.prizm.document.service;

import com.prizm.document.dto.response.DocumentDetailResponse;
import com.prizm.document.dto.response.DocumentSummaryResponse;
import com.prizm.document.dto.response.DocumentVersionResponse;
import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.exception.DocumentNotFoundException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 문서 목록과 문서별 버전 메타데이터를 조회 응답으로 변환한다. */
@Service
@Transactional(readOnly = true)
public class DocumentQueryService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final ProcessingJobRepository processingJobRepository;

    public DocumentQueryService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository,
            ProcessingJobRepository processingJobRepository) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.processingJobRepository = processingJobRepository;
    }

    /** 저장된 문서를 최신 생성 순서로 요약 조회한다. */
    public List<DocumentSummaryResponse> list(Long ownerUserId, DocumentType documentType) {
        return list(ownerUserId, documentType, null, null);
    }

    /** Lists only the current user's documents and applies optional user-visible filters. */
    public List<DocumentSummaryResponse> list(
            Long ownerUserId,
            DocumentType documentType,
            String title,
            ProcessingJobStatus processingStatus) {
        List<Document> documents = documentType == null
                ? documentRepository.findAllByOwnerUserIdOrderByCreatedAtDesc(ownerUserId)
                : documentRepository.findAllByOwnerUserIdAndDocumentTypeOrderByCreatedAtDesc(ownerUserId, documentType);
        String normalizedTitle = normalizeTitleFilter(title);
        return documents.stream()
                .filter(document -> normalizedTitle == null
                        || document.getTitle().toLowerCase(java.util.Locale.ROOT)
                                .contains(normalizedTitle.toLowerCase(java.util.Locale.ROOT)))
                .map(document -> toSummary(ownerUserId, document))
                .filter(summary -> processingStatus == null || processingStatus == summary.latestProcessingStatus())
                .toList();
    }

    /** 문서와 버전 목록을 조회한다. */
    public DocumentDetailResponse get(Long ownerUserId, Long documentId) {
        Document document = documentRepository.findByIdAndOwnerUserId(documentId, ownerUserId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        List<DocumentVersionResponse> versions = documentVersionRepository
                .findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(ownerUserId, documentId)
                .stream()
                .map(version -> toVersionResponse(ownerUserId, version))
                .toList();
        return new DocumentDetailResponse(
                document.getId(),
                document.getTitle(),
                document.getDocumentType(),
                true,
                document.getActiveVersionId(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                versions);
    }

    private DocumentSummaryResponse toSummary(Long ownerUserId, Document document) {
        List<DocumentVersion> versions = documentVersionRepository
                .findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(ownerUserId, document.getId());
        DocumentVersion latestVersion = versions.stream().findFirst().orElse(null);
        DocumentVersion activeVersion = versions.stream()
                .filter(version -> version.getId().equals(document.getActiveVersionId()))
                .findFirst()
                .orElse(null);
        Optional<ProcessingJob> latestProcessingJob = latestVersion == null
                ? Optional.empty()
                : processingJobRepository.findByOwnerUserIdAndDocumentVersionId(ownerUserId, latestVersion.getId());
        return new DocumentSummaryResponse(
                document.getId(),
                document.getTitle(),
                document.getDocumentType(),
                document.getActiveVersionId(),
                latestVersion == null ? null : latestVersion.getId(),
                latestVersion == null ? null : latestVersion.getStatus(),
                latestVersion == null ? null : latestVersion.getOriginalFileName(),
                latestVersion == null ? null : latestVersion.getFileType(),
                latestProcessingJob.map(ProcessingJob::getStatus).orElse(null),
                safeErrorCode(latestProcessingJob),
                activeVersion == null ? null : activeVersion.getStatus(),
                versions.size(),
                document.getCreatedAt(),
                document.getUpdatedAt());
    }

    private DocumentVersionResponse toVersionResponse(Long ownerUserId, DocumentVersion version) {
        Optional<ProcessingJob> processingJob = processingJobRepository
                .findByOwnerUserIdAndDocumentVersionId(ownerUserId, version.getId());
        return new DocumentVersionResponse(
                version.getId(),
                version.getVersionNo(),
                version.getOriginalFileName(),
                version.getFileType(),
                version.getStatus(),
                processingJob.map(ProcessingJob::getStatus).orElse(null),
                safeErrorCode(processingJob),
                processingJob.map(job -> job.getStatus() == ProcessingJobStatus.RETRY_WAIT).orElse(false),
                version.getCreatedAt());
    }

    private String normalizeTitleFilter(String title) {
        if (title == null || title.isBlank()) {
            return null;
        }
        return title.trim();
    }

    private String safeErrorCode(Optional<ProcessingJob> processingJob) {
        return processingJob.map(ProcessingJob::getStatus)
                .map(status -> switch (status) {
                    case RETRY_WAIT -> "PROCESSING_RETRY_SCHEDULED";
                    case FAILED -> "PROCESSING_FAILED";
                    default -> null;
                })
                .orElse(null);
    }
}
