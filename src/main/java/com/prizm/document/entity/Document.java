package com.prizm.document.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사용자가 관리하는 논리 문서와 현재 검색 대상 버전의 포인터를 보관한다.
 *
 * <p>새 원본은 별도 {@link DocumentVersion}으로 등록되며, 색인이 끝나기 전에는
 * {@code activeVersionId}를 바꾸지 않는다. 그러면 새 버전 처리가 실패해도 이전 ACTIVE 버전의
 * 검색 결과를 계속 제공할 수 있다.</p>
 */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private Long ownerUserId;

    @Column(nullable = false, length = 200)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private DocumentType documentType;

    @Column(name = "active_version_id")
    private Long activeVersionId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Document() {
    }

    private Document(Long ownerUserId, String title, DocumentType documentType) {
        this.ownerUserId = ownerUserId;
        this.title = title;
        this.documentType = documentType;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static Document create(Long ownerUserId, String title) {
        return create(ownerUserId, title, DocumentType.OTHER);
    }

    public static Document create(Long ownerUserId, String title, DocumentType documentType) {
        return new Document(ownerUserId, title, documentType);
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }

    /** 색인 완료 트랜잭션에서 ACTIVE가 된 버전을 현재 검색 대상으로 연결한다. */
    public void activateVersion(Long versionId) {
        if (versionId == null) {
            throw new IllegalArgumentException("versionId must not be null");
        }
        this.activeVersionId = versionId;
    }

    public void updateMetadata(String title, DocumentType documentType) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (documentType == null) {
            throw new IllegalArgumentException("documentType must not be null");
        }
        this.title = title;
        this.documentType = documentType;
    }

    /** 새 버전이 추가된 시각만 반영하며, 기존 ACTIVE 버전 포인터는 유지한다. */
    public void markVersionAdded() {
        this.updatedAt = Instant.now();
    }

    public void clearActiveVersion() {
        this.activeVersionId = null;
    }

    public Long getId() {
        return id;
    }

    public Long getOwnerUserId() {
        return ownerUserId;
    }

    public String getTitle() {
        return title;
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public Long getActiveVersionId() {
        return activeVersionId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
