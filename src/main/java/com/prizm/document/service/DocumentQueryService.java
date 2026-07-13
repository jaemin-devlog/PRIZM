package com.prizm.document.service;

import com.prizm.document.dto.response.DocumentDetailResponse;
import com.prizm.document.dto.response.DocumentSummaryResponse;
import com.prizm.document.dto.response.DocumentVersionResponse;
import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.exception.DocumentNotFoundException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 문서 목록과 문서별 버전 메타데이터를 조회 응답으로 변환한다. */
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

    /** 저장된 문서를 최신 생성 순서로 요약 조회한다. */
    public List<DocumentSummaryResponse> list() {
        return documentRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(this::toSummary)
                .toList();
    }

    /** 문서와 버전 목록을 조회한다. */
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
                version.getFileType(),
                version.getStatus(),
                version.getCreatedAt());
    }
}
