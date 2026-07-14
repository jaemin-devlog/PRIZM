ALTER TABLE document_versions
    DROP CONSTRAINT document_versions_file_type_check,
    ADD CONSTRAINT ck_document_versions_file_type
        CHECK (file_type IN ('TXT', 'PDF'));

ALTER TABLE document_chunks
    DROP CONSTRAINT ck_document_chunks_source_type,
    ADD CONSTRAINT ck_document_chunks_source_type
        CHECK (source_type IN ('TEXT_CHUNK', 'PAGE'));
