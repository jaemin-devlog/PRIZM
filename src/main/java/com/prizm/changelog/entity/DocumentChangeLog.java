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

/**
 * 문서 버전 생성 사실과 색인 작업으로의 전달 상태를 소유자별로 보존한다.
 *
 * <p>업로드 트랜잭션이 문서 버전과 함께 {@code PENDING} 로그를 남기므로, 업로드가 끝난 뒤에도
 * 색인 작업 생성 여부를 다시 확인할 수 있다. 이벤트 유형과 버전 ID로 만든 {@code eventKey}에는 DB 고유
 * 조건이 있어 같은 버전 생성 이벤트가 중복 저장되지 않는다. 전달에 실패하면 같은 행에 재시도 시각과
 * 오류를 기록하고, 실제로 커밋된 재시도만 {@code retryCount}에 반영한다.</p>
 */
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

    /** 업로드 트랜잭션에서 새 문서 버전과 함께 저장할 변경 사실을 만든다. */
    public static DocumentChangeLog pendingDocumentVersionCreated(Long ownerUserId, Long documentVersionId) {
        return new DocumentChangeLog(ownerUserId, documentVersionId);
    }

    /** 같은 트랜잭션에서 확보한 {@code INDEXING} 작업과 연결해 전달을 확정한다. */
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

    /** 별도 실패 기록 트랜잭션이 커밋할 재시도만 재시도 횟수에 반영한다. */
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

    /** 더 이상 전달하지 않을 ChangeLog를 최종 실패로 닫는다. */
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
