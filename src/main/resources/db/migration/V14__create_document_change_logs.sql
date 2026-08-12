ALTER TABLE processing_jobs
    ADD CONSTRAINT uq_processing_jobs_id_owner_version
        UNIQUE (id, owner_user_id, document_version_id);

CREATE TABLE document_change_logs (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    document_version_id BIGINT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    event_key VARCHAR(255) NOT NULL,
    dispatch_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    processing_job_id BIGINT NULL,
    retry_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ NULL,
    dispatched_at TIMESTAMPTZ NULL,
    failed_at TIMESTAMPTZ NULL,
    last_error_message VARCHAR(1000) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_document_change_logs_event_type
        CHECK (event_type = 'DOCUMENT_VERSION_CREATED'),
    CONSTRAINT ck_document_change_logs_dispatch_status
        CHECK (dispatch_status IN ('PENDING', 'RETRY_WAIT', 'DISPATCHED', 'FAILED')),
    CONSTRAINT ck_document_change_logs_retry_count
        CHECK (retry_count BETWEEN 0 AND 3),
    CONSTRAINT uq_document_change_logs_event_key UNIQUE (event_key),
    CONSTRAINT uq_document_change_logs_version_event UNIQUE (document_version_id, event_type),
    CONSTRAINT uq_document_change_logs_processing_job UNIQUE (processing_job_id),
    CONSTRAINT fk_document_change_logs_version_owner
        FOREIGN KEY (document_version_id, owner_user_id)
        REFERENCES document_versions(id, owner_user_id),
    CONSTRAINT fk_document_change_logs_processing_job_owner_version
        FOREIGN KEY (processing_job_id, owner_user_id, document_version_id)
        REFERENCES processing_jobs(id, owner_user_id, document_version_id)
);

CREATE INDEX ix_document_change_logs_dispatch_claim
    ON document_change_logs(dispatch_status, next_retry_at, created_at, id);

CREATE INDEX ix_document_change_logs_owner_version
    ON document_change_logs(owner_user_id, document_version_id);
