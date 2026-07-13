package com.prizm.ingestion.entity;

import com.prizm.ingestion.exception.StaleProcessingJobClaimException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** 한 문서 버전의 비동기 색인 상태와 재시도 정보를 저장한다. */
@Entity
@Table(
        name = "processing_jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_processing_jobs_version_type",
                columnNames = {"document_version_id", "job_type"}))
public class ProcessingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private Long ownerUserId;

    @Column(name = "document_version_id", nullable = false)
    private Long documentVersionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 30)
    private ProcessingJobType jobType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private ProcessingJobStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "claim_version", nullable = false)
    private long claimVersion;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProcessingJob() {
    }

    private ProcessingJob(Long ownerUserId, Long documentVersionId) {
        this.ownerUserId = ownerUserId;
        this.documentVersionId = documentVersionId;
        this.jobType = ProcessingJobType.INDEXING;
        this.status = ProcessingJobStatus.PENDING;
        this.retryCount = 0;
        this.claimVersion = 0;
        this.createdAt = Instant.now();
    }

    public static ProcessingJob pendingIndexing(Long ownerUserId, Long documentVersionId) {
        return new ProcessingJob(ownerUserId, documentVersionId);
    }

    public void scheduleRetry(Instant nextRetryAt, String errorMessage) {
        requireStatus(ProcessingJobStatus.PROCESSING);
        this.status = ProcessingJobStatus.RETRY_WAIT;
        this.retryCount++;
        this.nextRetryAt = nextRetryAt;
        this.startedAt = null;
        this.completedAt = null;
        this.leaseExpiresAt = null;
        this.errorMessage = errorMessage;
    }

    public void complete(Instant completedAt) {
        requireStatus(ProcessingJobStatus.PROCESSING);
        this.status = ProcessingJobStatus.COMPLETED;
        this.completedAt = completedAt;
        this.nextRetryAt = null;
        this.leaseExpiresAt = null;
        this.errorMessage = null;
    }

    public void fail(Instant completedAt, String errorMessage) {
        requireStatus(ProcessingJobStatus.PROCESSING);
        this.status = ProcessingJobStatus.FAILED;
        this.completedAt = completedAt;
        this.nextRetryAt = null;
        this.leaseExpiresAt = null;
        this.errorMessage = errorMessage;
    }

    /** 만료된 처리 시도를 무효화하고 DB 시간 기준의 다음 재시도를 예약한다. */
    public void recoverForRetry(Instant nextRetryAt, String errorMessage) {
        requireStatus(ProcessingJobStatus.PROCESSING);
        this.claimVersion++;
        this.retryCount++;
        this.status = ProcessingJobStatus.RETRY_WAIT;
        this.nextRetryAt = nextRetryAt;
        this.startedAt = null;
        this.completedAt = null;
        this.leaseExpiresAt = null;
        this.errorMessage = errorMessage;
    }

    /** 최대 재시도를 사용한 만료 작업을 실패 처리하며 이전 Worker 소유권을 무효화한다. */
    public void recoverAsFailed(Instant completedAt, String errorMessage) {
        requireStatus(ProcessingJobStatus.PROCESSING);
        this.claimVersion++;
        this.status = ProcessingJobStatus.FAILED;
        this.completedAt = completedAt;
        this.nextRetryAt = null;
        this.leaseExpiresAt = null;
        this.errorMessage = errorMessage;
    }

    /** 상태와 fencing 값이 모두 현재 Worker의 처리 시도와 일치하는지 확인한다. */
    public void requireClaim(long expectedClaimVersion) {
        if (status != ProcessingJobStatus.PROCESSING || claimVersion != expectedClaimVersion) {
            throw new StaleProcessingJobClaimException(id, expectedClaimVersion);
        }
    }

    private void requireStatus(ProcessingJobStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("Processing job %s must be %s but was %s."
                    .formatted(id, expected, status));
        }
    }

    public Long getId() { return id; }
    public Long getOwnerUserId() { return ownerUserId; }
    public Long getDocumentVersionId() { return documentVersionId; }
    public ProcessingJobType getJobType() { return jobType; }
    public ProcessingJobStatus getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public Instant getNextRetryAt() { return nextRetryAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getClaimVersion() { return claimVersion; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
}
