DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM documents)
            OR EXISTS (SELECT 1 FROM document_versions)
            OR EXISTS (SELECT 1 FROM document_chunks)
            OR EXISTS (SELECT 1 FROM processing_jobs) THEN
        RAISE EXCEPTION
            '기존 문서 데이터의 소유자를 확인할 수 없으므로 V8 migration을 적용할 수 없습니다. 기존 개발 데이터를 삭제하거나 검증된 문서-사용자 매핑을 먼저 제공해야 합니다.';
    END IF;
END
$$;

ALTER TABLE documents ADD COLUMN owner_user_id BIGINT;
ALTER TABLE document_versions ADD COLUMN owner_user_id BIGINT;
ALTER TABLE document_chunks ADD COLUMN owner_user_id BIGINT;
ALTER TABLE processing_jobs ADD COLUMN owner_user_id BIGINT;

ALTER TABLE documents ALTER COLUMN owner_user_id SET NOT NULL;
ALTER TABLE document_versions ALTER COLUMN owner_user_id SET NOT NULL;
ALTER TABLE document_chunks ALTER COLUMN owner_user_id SET NOT NULL;
ALTER TABLE processing_jobs ALTER COLUMN owner_user_id SET NOT NULL;

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES users(id),
    ADD CONSTRAINT uq_documents_id_owner UNIQUE (id, owner_user_id);

ALTER TABLE document_versions
    ADD CONSTRAINT fk_document_versions_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES users(id),
    ADD CONSTRAINT uq_document_versions_id_owner UNIQUE (id, owner_user_id),
    ADD CONSTRAINT fk_document_versions_document_owner
        FOREIGN KEY (document_id, owner_user_id)
        REFERENCES documents(id, owner_user_id);

ALTER TABLE document_chunks
    ADD CONSTRAINT fk_document_chunks_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES users(id),
    ADD CONSTRAINT fk_document_chunks_version_owner
        FOREIGN KEY (document_version_id, owner_user_id)
        REFERENCES document_versions(id, owner_user_id);

ALTER TABLE processing_jobs
    ADD CONSTRAINT fk_processing_jobs_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES users(id),
    ADD CONSTRAINT fk_processing_jobs_version_owner
        FOREIGN KEY (document_version_id, owner_user_id)
        REFERENCES document_versions(id, owner_user_id);

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_active_version_owner
        FOREIGN KEY (active_version_id, owner_user_id)
        REFERENCES document_versions(id, owner_user_id);

CREATE INDEX idx_documents_owner_created
    ON documents(owner_user_id, created_at DESC, id DESC);

CREATE INDEX idx_document_versions_owner_document
    ON document_versions(owner_user_id, document_id, version_no DESC);

CREATE INDEX idx_document_chunks_owner_version
    ON document_chunks(owner_user_id, document_version_id);

CREATE INDEX idx_processing_jobs_owner_status_available
    ON processing_jobs(owner_user_id, status, next_retry_at, created_at, id);
