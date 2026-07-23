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

    /** 색인이 완전히 끝난 버전만 현재 검색 대상 버전으로 연결한다. */
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

    /** Records that a new immutable source version was attached to this document. */
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
