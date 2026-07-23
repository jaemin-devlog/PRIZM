package com.prizm.document.entity;

import com.prizm.document.exception.InvalidDocumentVersionStateException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 문서 원본 한 개의 버전과 현재 처리 상태를 표현하는 JPA 엔티티다. */
@Entity
@Table(name = "document_versions")
public class DocumentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private Long ownerUserId;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    @Column(name = "original_file_name", nullable = false, length = 255)
    private String originalFileName;

    @Column(name = "stored_file_path", nullable = false, length = 500)
    private String storedFilePath;

    @Enumerated(EnumType.STRING)
    @Column(name = "file_type", nullable = false, length = 10)
    private DocumentFileType fileType;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private DocumentVersionStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentVersion() {
    }

    private DocumentVersion(
            Long ownerUserId,
            Long documentId,
            int versionNo,
            String originalFileName,
            DocumentFileType fileType,
            String contentHash) {
        if (versionNo < 1) {
            throw new IllegalArgumentException("versionNo must be positive");
        }
        this.ownerUserId = ownerUserId;
        this.documentId = documentId;
        this.versionNo = versionNo;
        this.originalFileName = originalFileName;
        this.storedFilePath = "pending";
        this.fileType = fileType;
        this.contentHash = contentHash;
        this.status = DocumentVersionStatus.QUARANTINED;
        this.createdAt = Instant.now();
    }

    /** 업로드 직후 검색에 사용하지 않는 QUARANTINED 버전을 만든다. */
    public static DocumentVersion quarantined(
            Long ownerUserId,
            Long documentId,
            String originalFileName,
            String contentHash) {
        return quarantined(ownerUserId, documentId, originalFileName, DocumentFileType.TXT, contentHash);
    }

    public static DocumentVersion quarantined(
            Long ownerUserId,
            Long documentId,
            String originalFileName,
            DocumentFileType fileType,
            String contentHash) {
        return quarantined(ownerUserId, documentId, 1, originalFileName, fileType, contentHash);
    }

    public static DocumentVersion quarantined(
            Long ownerUserId,
            Long documentId,
            int versionNo,
            String originalFileName,
            DocumentFileType fileType,
            String contentHash) {
        return new DocumentVersion(ownerUserId, documentId, versionNo, originalFileName, fileType, contentHash);
    }

    /** 파일 저장이 성공한 뒤 임시 경로를 실제 서버 저장 경로로 교체한다. */
    public void updateStoredFilePath(String storedFilePath) {
        this.storedFilePath = storedFilePath;
    }

    /** 검증과 원본 저장이 끝난 격리 버전을 Worker 처리 상태로 전환한다. */
    public void startProcessing() {
        transition(DocumentVersionStatus.QUARANTINED, DocumentVersionStatus.PROCESSING);
    }

    /** 모든 청크 저장이 검증된 색인 작업만 활성화한다. */
    public void activate() {
        transition(DocumentVersionStatus.PROCESSING, DocumentVersionStatus.ACTIVE);
    }

    /** 색인 중 최종 실패한 버전을 검색 불가능 상태로 전환한다. */
    public void failProcessing() {
        transition(DocumentVersionStatus.PROCESSING, DocumentVersionStatus.FAILED);
    }

    private void transition(DocumentVersionStatus expected, DocumentVersionStatus next) {
        if (status != expected) {
            throw new InvalidDocumentVersionStateException(id, status, next);
        }
        status = next;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public int getVersionNo() {
        return versionNo;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public String getStoredFilePath() {
        return storedFilePath;
    }

    public DocumentFileType getFileType() {
        return fileType;
    }

    public String getContentHash() {
        return contentHash;
    }

    public DocumentVersionStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
