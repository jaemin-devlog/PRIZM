package com.prizm.changelog.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 한 immutable document version 생성 사실을 나타내는 owner-scoped 영속 변경 로그다. */
@Entity
@Table(name = "document_change_logs")
public class DocumentChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private Long ownerUserId;

    @Column(name = "document_version_id", nullable = false, updatable = false)
    private Long documentVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, updatable = false, length = 50)
    private ChangeLogEventType eventType;

    @Column(name = "event_key", nullable = false, updatable = false, length = 255)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "dispatch_status", nullable = false, length = 30)
    private ChangeLogDispatchStatus dispatchStatus;

    @Column(name = "processing_job_id")
    private Long processingJobId;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "failed_at")
    private Instant failedAt;

    @Column(name = "last_error_message", length = 1000)
    private String lastErrorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected DocumentChangeLog() {
    }

    private DocumentChangeLog(Long ownerUserId, Long documentVersionId) {
        if (ownerUserId == null || documentVersionId == null) {
            throw new IllegalArgumentException("ownerUserId and documentVersionId are required");
        }
        this.ownerUserId = ownerUserId;
        this.documentVersionId = documentVersionId;
        this.eventType = ChangeLogEventType.DOCUMENT_VERSION_CREATED;
        this.eventKey = ChangeLogEventType.DOCUMENT_VERSION_CREATED + ":" + documentVersionId;
        this.dispatchStatus = ChangeLogDispatchStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = Instant.now();
    }

    /** Upload transaction에서 새 version의 단일 변경 사실을 만든다. */
    public static DocumentChangeLog pendingDocumentVersionCreated(Long ownerUserId, Long documentVersionId) {
        return new DocumentChangeLog(ownerUserId, documentVersionId);
    }

    /** Transaction A에서 확보한 INDEXING Job으로 전달을 확정한다. */
    public void markDispatched(Long processingJobId, Instant dispatchedAt) {
        if (dispatchStatus != ChangeLogDispatchStatus.PENDING
                && dispatchStatus != ChangeLogDispatchStatus.RETRY_WAIT) {
            throw new IllegalStateException("ChangeLog %s cannot be dispatched from %s."
                    .formatted(id, dispatchStatus));
        }
        if (processingJobId == null || dispatchedAt == null) {
            throw new IllegalArgumentException("processingJobId and dispatchedAt are required");
        }
        this.processingJobId = processingJobId;
        this.dispatchStatus = ChangeLogDispatchStatus.DISPATCHED;
        this.dispatchedAt = dispatchedAt;
        this.nextRetryAt = null;
    }

    /** Transaction B가 commit할 dispatch 재시도만 retry budget을 소비한다. */
    public void scheduleDispatchRetry(Instant nextRetryAt, String errorMessage) {
        requireDispatchable();
        if (retryCount >= 3) {
            throw new IllegalStateException("ChangeLog %s has exhausted its dispatch retry budget.".formatted(id));
        }
        if (nextRetryAt == null) {
            throw new IllegalArgumentException("nextRetryAt is required");
        }
        this.dispatchStatus = ChangeLogDispatchStatus.RETRY_WAIT;
        this.retryCount++;
        this.nextRetryAt = nextRetryAt;
        this.lastErrorMessage = errorMessage;
        this.failedAt = null;
    }

    /** Transaction B가 더 이상 dispatch하지 않을 ChangeLog를 종료한다. */
    public void markDispatchFailed(Instant failedAt, String errorMessage) {
        requireDispatchable();
        if (failedAt == null) {
            throw new IllegalArgumentException("failedAt is required");
        }
        this.dispatchStatus = ChangeLogDispatchStatus.FAILED;
        this.nextRetryAt = null;
        this.failedAt = failedAt;
        this.lastErrorMessage = errorMessage;
    }

    private void requireDispatchable() {
        if (dispatchStatus != ChangeLogDispatchStatus.PENDING
                && dispatchStatus != ChangeLogDispatchStatus.RETRY_WAIT) {
            throw new IllegalStateException("ChangeLog %s cannot change dispatch failure state from %s."
                    .formatted(id, dispatchStatus));
        }
    }

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public Long getDocumentVersionId() { return documentVersionId; }
    public ChangeLogEventType getEventType() { return eventType; }
    public String getEventKey() { return eventKey; }
    public ChangeLogDispatchStatus getDispatchStatus() { return dispatchStatus; }
    public Long getProcessingJobId() { return processingJobId; }
    public int getRetryCount() { return retryCount; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public Instant getDispatchedAt() { return dispatchedAt; }
    public Instant getFailedAt() { return failedAt; }
    public String getLastErrorMessage() { return lastErrorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
