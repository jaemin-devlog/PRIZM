ALTER TABLE document_versions
    DROP CONSTRAINT document_versions_status_check;

ALTER TABLE document_versions
    ADD CONSTRAINT ck_document_versions_status
        CHECK (status IN ('QUARANTINED', 'APPROVED', 'INDEXING', 'ACTIVE', 'FAILED'));

CREATE TABLE processing_jobs (
    id BIGSERIAL PRIMARY KEY,
    document_version_id BIGINT NOT NULL REFERENCES document_versions(id),
    job_type VARCHAR(30) NOT NULL CHECK (job_type = 'INDEXING'),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
    retry_count INTEGER NOT NULL DEFAULT 0 CHECK (retry_count >= 0),
    next_retry_at TIMESTAMPTZ NULL,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    error_message TEXT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_processing_jobs_version_type UNIQUE (document_version_id, job_type)
);

CREATE INDEX ix_processing_jobs_claim
    ON processing_jobs (status, next_retry_at, created_at, id);
