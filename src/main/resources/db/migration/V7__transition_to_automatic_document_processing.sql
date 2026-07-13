ALTER TABLE document_versions
    DROP CONSTRAINT ck_document_versions_status;

UPDATE document_versions
SET status = CASE status
    WHEN 'APPROVED' THEN 'QUARANTINED'
    WHEN 'INDEXING' THEN 'PROCESSING'
    ELSE status
END
WHERE status IN ('APPROVED', 'INDEXING');

ALTER TABLE document_versions
    ADD CONSTRAINT ck_document_versions_status
        CHECK (status IN ('QUARANTINED', 'PROCESSING', 'ACTIVE', 'FAILED'));

ALTER TABLE processing_jobs
    DROP CONSTRAINT processing_jobs_status_check;

UPDATE processing_jobs
SET status = 'RETRY_WAIT'
WHERE status = 'PENDING'
  AND (retry_count > 0 OR next_retry_at IS NOT NULL);

ALTER TABLE processing_jobs
    ADD CONSTRAINT ck_processing_jobs_status
        CHECK (status IN ('PENDING', 'RETRY_WAIT', 'PROCESSING', 'COMPLETED', 'FAILED'));

ALTER TABLE users
    DROP CONSTRAINT ck_users_role;

UPDATE users
SET role = 'SYSTEM_ADMIN'
WHERE role = 'ADMIN';

ALTER TABLE users
    ADD CONSTRAINT ck_users_role CHECK (role IN ('SYSTEM_ADMIN', 'USER'));

-- 승인 API 제거 전에 업로드된 격리 버전도 자동 처리 흐름에 진입시킨다.
INSERT INTO processing_jobs (document_version_id, job_type, status)
SELECT version.id, 'INDEXING', 'PENDING'
FROM document_versions version
WHERE version.status = 'QUARANTINED'
ON CONFLICT (document_version_id, job_type) DO NOTHING;
