package com.prizm.document;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DocumentQueryService {

    private final DocumentRepository documentRepository;
    private final DocumentVersionRepository documentVersionRepository;

    public DocumentQueryService(
            DocumentRepository documentRepository,
            DocumentVersionRepository documentVersionRepository) {
        this.documentRepository = documentRepository;
        this.documentVersionRepository = documentVersionRepository;
    }

    public List<DocumentSummaryResponse> list() {
        return documentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    public DocumentDetailResponse get(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));
        List<DocumentVersionResponse> versions = documentVersionRepository
                .findByDocumentIdOrderByVersionNoDesc(documentId)
                .stream()
                .map(this::toVersionResponse)
                .toList();
        return new DocumentDetailResponse(
                document.getId(),
                document.getTitle(),
                document.getActiveVersionId(),
                document.getCreatedAt(),
                document.getUpdatedAt(),
                versions);
    }

    private DocumentSummaryResponse toSummary(Document document) {
        DocumentVersion latestVersion = documentVersionRepository
                .findByDocumentIdOrderByVersionNoDesc(document.getId())
                .stream()
                .findFirst()
                .orElse(null);
        return new DocumentSummaryResponse(
                document.getId(),
                document.getTitle(),
                document.getActiveVersionId(),
                latestVersion == null ? null : latestVersion.getId(),
                latestVersion == null ? null : latestVersion.getStatus(),
                document.getCreatedAt());
    }

    private DocumentVersionResponse toVersionResponse(DocumentVersion version) {
        return new DocumentVersionResponse(
                version.getId(),
                version.getVersionNo(),
                version.getOriginalFileName(),
                version.getStoredFilePath(),
                version.getFileType(),
                version.getStatus(),
                version.getCreatedAt());
    }
}
