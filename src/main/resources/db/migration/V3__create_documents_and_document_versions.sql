CREATE TABLE documents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    active_version_id BIGINT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE document_versions (
    id BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL REFERENCES documents(id),
    version_no INTEGER NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    stored_file_path VARCHAR(500) NOT NULL,
    file_type VARCHAR(10) NOT NULL CHECK (file_type = 'TXT'),
    content_hash VARCHAR(64) NOT NULL CHECK (char_length(content_hash) = 64),
    status VARCHAR(30) NOT NULL CHECK (status IN ('QUARANTINED', 'ACTIVE', 'FAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_document_versions_document_version UNIQUE (document_id, version_no)
);

ALTER TABLE document_chunks
    ADD COLUMN document_version_id BIGINT,
    ADD COLUMN chunk_no INTEGER,
    ADD COLUMN page_no INTEGER;

ALTER TABLE documents
    ADD CONSTRAINT fk_documents_active_version
    FOREIGN KEY (active_version_id) REFERENCES document_versions(id);

ALTER TABLE document_chunks
    ALTER COLUMN document_version_id SET NOT NULL,
    ALTER COLUMN chunk_no SET NOT NULL,
    ADD CONSTRAINT fk_document_chunks_document_version
        FOREIGN KEY (document_version_id) REFERENCES document_versions(id),
    ADD CONSTRAINT uq_document_chunks_version_chunk
        UNIQUE (document_version_id, chunk_no);
