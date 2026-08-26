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

/**
 * 한 번 업로드한 TXT/PDF 원본을 독립된 버전으로 보관한다.
 *
 * <p>원본의 파일명·형식·해시와 저장소 키는 버전마다 분리된다. 상태는
 * {@code QUARANTINED -> PROCESSING -> ACTIVE|FAILED}로 바뀌며, dispatch가 최종 실패하면
 * QUARANTINED에서 바로 FAILED가 된다. 검색 가능 여부는 {@link Document}의 ACTIVE 버전
 * 포인터도 함께 확인한다.</p>
 */
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

    /** 업로드 직후 검색 후보에서 제외되는 QUARANTINED 버전을 만든다. */
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

    /** 파일 저장이 끝난 뒤 생성 시 사용한 임시 값을 실제 저장소 키로 바꾼다. */
    public void updateStoredFilePath(String storedFilePath) {
        this.storedFilePath = storedFilePath;
    }

    /** 검증과 원본 저장이 끝난 격리 버전을 색인 처리 상태로 전환한다. */
    public void startProcessing() {
        transition(DocumentVersionStatus.QUARANTINED, DocumentVersionStatus.PROCESSING);
    }

    /** 모든 청크를 검증해 저장한 색인 완료 트랜잭션에서만 ACTIVE로 전환한다. */
    public void activate() {
        transition(DocumentVersionStatus.PROCESSING, DocumentVersionStatus.ACTIVE);
    }

    /** 색인 중 최종 실패한 버전을 검색 불가능 상태로 전환한다. */
    public void failProcessing() {
        transition(DocumentVersionStatus.PROCESSING, DocumentVersionStatus.FAILED);
    }

    /** ChangeLog 전달이 영구 실패로 분류되거나 재시도 한도를 넘긴 격리 버전은 색인 전에 종료한다. */
    public void failDispatch() {
        transition(DocumentVersionStatus.QUARANTINED, DocumentVersionStatus.FAILED);
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
