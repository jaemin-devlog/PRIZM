ALTER TABLE document_chunks
    ADD COLUMN source_type VARCHAR(30),
    ADD COLUMN source_index INTEGER,
    ADD COLUMN source_label VARCHAR(100);

WITH chunk_sources AS (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY document_version_id ORDER BY chunk_no) AS source_index
    FROM document_chunks
)
UPDATE document_chunks chunk
SET source_type = 'TEXT_CHUNK',
    source_index = chunk_sources.source_index,
    source_label = '텍스트 구간 ' || chunk_sources.source_index
FROM chunk_sources
WHERE chunk.id = chunk_sources.id;

ALTER TABLE document_chunks
    ALTER COLUMN source_type SET NOT NULL,
    ALTER COLUMN source_index SET NOT NULL,
    ALTER COLUMN source_label SET NOT NULL,
    ADD CONSTRAINT ck_document_chunks_source_type
        CHECK (source_type = 'TEXT_CHUNK'),
    ADD CONSTRAINT ck_document_chunks_source_index
        CHECK (source_index >= 1);
