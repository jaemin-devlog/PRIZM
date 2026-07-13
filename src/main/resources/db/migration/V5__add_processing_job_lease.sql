ALTER TABLE processing_jobs
    ADD COLUMN claim_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN lease_expires_at TIMESTAMPTZ NULL,
    ADD CONSTRAINT ck_processing_jobs_claim_version CHECK (claim_version >= 0);

-- V5 적용 전에 실행 중이던 작업도 새 복구 Worker가 즉시 회수할 수 있게 만료 상태로 둔다.
UPDATE processing_jobs
SET lease_expires_at = now()
WHERE status = 'PROCESSING';

CREATE INDEX ix_processing_jobs_status_retry_lease
    ON processing_jobs (status, next_retry_at, lease_expires_at);
