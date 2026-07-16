ALTER TABLE file_cleanup_jobs
    DROP CONSTRAINT ck_file_cleanup_jobs_status,
    ADD CONSTRAINT ck_file_cleanup_jobs_status
        CHECK (status IN ('PENDING', 'PROCESSING', 'RETRY_WAIT', 'COMPLETED', 'FAILED')),
    ADD COLUMN lease_expires_at TIMESTAMPTZ,
    ADD COLUMN claim_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN last_error_code VARCHAR(64);

CREATE INDEX ix_file_cleanup_jobs_processing_lease
    ON file_cleanup_jobs(status, lease_expires_at, id);
