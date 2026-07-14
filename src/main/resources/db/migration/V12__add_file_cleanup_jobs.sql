CREATE TABLE file_cleanup_jobs (
    id BIGSERIAL PRIMARY KEY,
    storage_key VARCHAR(1024) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    attempts INTEGER NOT NULL DEFAULT 0,
    available_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_file_cleanup_jobs_status CHECK (status = 'PENDING'),
    CONSTRAINT ck_file_cleanup_jobs_attempts CHECK (attempts >= 0),
    CONSTRAINT uq_file_cleanup_jobs_storage_key UNIQUE (storage_key)
);

CREATE INDEX ix_file_cleanup_jobs_pending_available
    ON file_cleanup_jobs(status, available_at, created_at, id);
