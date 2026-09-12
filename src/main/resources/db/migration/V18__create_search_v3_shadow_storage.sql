ALTER TABLE document_versions
    ADD CONSTRAINT uq_document_versions_id_document_owner
        UNIQUE (id, document_id, owner_user_id);

CREATE TABLE search_v3_index_generations (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    document_version_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    structure_policy_version VARCHAR(100) NOT NULL,
    passage_policy_version VARCHAR(100) NOT NULL,
    child_policy_version VARCHAR(100) NOT NULL,
    embedding_model_id VARCHAR(200) NOT NULL,
    resolved_model_digest VARCHAR(64) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    passage_input_policy_version VARCHAR(100) NOT NULL,
    child_input_policy_version VARCHAR(100) NOT NULL,
    expected_passage_count INTEGER NOT NULL,
    expected_child_count INTEGER NOT NULL,
    expected_manifest_sha256 VARCHAR(64) NOT NULL,
    build_completed_at TIMESTAMPTZ NULL,
    activated_at TIMESTAMPTZ NULL,
    superseded_at TIMESTAMPTZ NULL,
    failed_at TIMESTAMPTZ NULL,
    failure_stage VARCHAR(30) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_s3_generations_status
        CHECK (status IN ('BUILDING', 'READY', 'ACTIVE', 'FAILED', 'SUPERSEDED')),
    CONSTRAINT ck_s3_generations_policy_text
        CHECK (
            char_length(trim(structure_policy_version)) > 0
            AND char_length(trim(passage_policy_version)) > 0
            AND char_length(trim(child_policy_version)) > 0
            AND char_length(trim(embedding_model_id)) > 0
            AND char_length(trim(passage_input_policy_version)) > 0
            AND char_length(trim(child_input_policy_version)) > 0
        ),
    CONSTRAINT ck_s3_generations_digest
        CHECK (resolved_model_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_s3_generations_dimension
        CHECK (embedding_dimension = 1024),
    CONSTRAINT ck_s3_generations_manifest
        CHECK (
            expected_passage_count > 0
            AND expected_child_count > 0
            AND expected_manifest_sha256 ~ '^[0-9a-f]{64}$'
        ),
    CONSTRAINT ck_s3_generations_failure_stage
        CHECK (failure_stage IS NULL OR failure_stage IN (
            'PASSAGE_GENERATION',
            'PASSAGE_EMBEDDING',
            'CHILD_GENERATION',
            'CHILD_EMBEDDING',
            'STORAGE',
            'ACTIVATION'
        )),
    CONSTRAINT ck_s3_generations_status_metadata
        CHECK (
            (status = 'BUILDING'
                AND build_completed_at IS NULL
                AND activated_at IS NULL
                AND superseded_at IS NULL
                AND failed_at IS NULL
                AND failure_stage IS NULL)
            OR (status = 'READY'
                AND build_completed_at IS NOT NULL
                AND activated_at IS NULL
                AND superseded_at IS NULL
                AND failed_at IS NULL
                AND failure_stage IS NULL)
            OR (status = 'ACTIVE'
                AND build_completed_at IS NOT NULL
                AND activated_at IS NOT NULL
                AND superseded_at IS NULL
                AND failed_at IS NULL
                AND failure_stage IS NULL)
            OR (status = 'SUPERSEDED'
                AND build_completed_at IS NOT NULL
                AND activated_at IS NOT NULL
                AND superseded_at IS NOT NULL
                AND failed_at IS NULL
                AND failure_stage IS NULL)
            OR (status = 'FAILED'
                AND activated_at IS NULL
                AND superseded_at IS NULL
                AND failed_at IS NOT NULL
                AND failure_stage IS NOT NULL)
        ),
    CONSTRAINT fk_s3_generations_version_lineage
        FOREIGN KEY (document_version_id, document_id, owner_user_id)
        REFERENCES document_versions(id, document_id, owner_user_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_s3_generations_id_lineage
        UNIQUE (id, owner_user_id, document_id, document_version_id),
    CONSTRAINT uq_s3_generations_passage_contract
        UNIQUE (
            id, owner_user_id, document_id, document_version_id,
            embedding_model_id, resolved_model_digest, embedding_dimension,
            passage_input_policy_version
        ),
    CONSTRAINT uq_s3_generations_child_contract
        UNIQUE (
            id, owner_user_id, document_id, document_version_id,
            embedding_model_id, resolved_model_digest, embedding_dimension,
            child_input_policy_version
        )
);

CREATE UNIQUE INDEX uq_s3_generations_one_active_document
    ON search_v3_index_generations(owner_user_id, document_id)
    WHERE status = 'ACTIVE';

CREATE INDEX ix_s3_generations_owner_document_status
    ON search_v3_index_generations(owner_user_id, document_id, status, created_at DESC, id DESC);

CREATE INDEX ix_s3_generations_version
    ON search_v3_index_generations(owner_user_id, document_version_id, created_at DESC, id DESC);

CREATE TABLE search_v3_indexing_jobs (
    id BIGSERIAL PRIMARY KEY,
    generation_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    document_version_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    claim_version BIGINT NOT NULL DEFAULT 0,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_retry_at TIMESTAMPTZ NULL,
    lease_expires_at TIMESTAMPTZ NULL,
    recovery_lock_token UUID NULL,
    recovery_locked_at TIMESTAMPTZ NULL,
    started_at TIMESTAMPTZ NULL,
    completed_at TIMESTAMPTZ NULL,
    failed_at TIMESTAMPTZ NULL,
    failure_stage VARCHAR(30) NULL,
    error_message VARCHAR(2000) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_s3_jobs_status
        CHECK (status IN ('PENDING', 'RETRY_WAIT', 'PROCESSING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_s3_jobs_counters
        CHECK (claim_version >= 0 AND attempt_count >= 0),
    CONSTRAINT ck_s3_jobs_failure_stage
        CHECK (failure_stage IS NULL OR failure_stage IN (
            'PASSAGE_GENERATION',
            'PASSAGE_EMBEDDING',
            'CHILD_GENERATION',
            'CHILD_EMBEDDING',
            'STORAGE',
            'ACTIVATION'
        )),
    CONSTRAINT ck_s3_jobs_recovery_lock
        CHECK (
            (recovery_lock_token IS NULL AND recovery_locked_at IS NULL)
            OR (status = 'PROCESSING'
                AND recovery_lock_token IS NOT NULL
                AND recovery_locked_at IS NOT NULL)
        ),
    CONSTRAINT ck_s3_jobs_status_metadata
        CHECK (
            (status = 'PENDING'
                AND claim_version = 0
                AND attempt_count = 0
                AND next_retry_at IS NULL
                AND lease_expires_at IS NULL
                AND started_at IS NULL
                AND completed_at IS NULL
                AND failed_at IS NULL
                AND failure_stage IS NULL)
            OR (status = 'RETRY_WAIT'
                AND next_retry_at IS NOT NULL
                AND lease_expires_at IS NULL
                AND started_at IS NULL
                AND completed_at IS NULL
                AND failed_at IS NULL
                AND failure_stage IS NULL)
            OR (status = 'PROCESSING'
                AND claim_version > 0
                AND attempt_count > 0
                AND next_retry_at IS NULL
                AND lease_expires_at IS NOT NULL
                AND started_at IS NOT NULL
                AND completed_at IS NULL
                AND failed_at IS NULL
                AND failure_stage IS NULL)
            OR (status = 'COMPLETED'
                AND claim_version > 0
                AND attempt_count > 0
                AND next_retry_at IS NULL
                AND lease_expires_at IS NULL
                AND completed_at IS NOT NULL
                AND failed_at IS NULL
                AND failure_stage IS NULL)
            OR (status = 'FAILED'
                AND next_retry_at IS NULL
                AND lease_expires_at IS NULL
                AND completed_at IS NULL
                AND failed_at IS NOT NULL
                AND failure_stage IS NOT NULL)
        ),
    CONSTRAINT fk_s3_jobs_generation_lineage
        FOREIGN KEY (generation_id, owner_user_id, document_id, document_version_id)
        REFERENCES search_v3_index_generations(id, owner_user_id, document_id, document_version_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_s3_jobs_generation UNIQUE (generation_id),
    CONSTRAINT uq_s3_jobs_id_lineage
        UNIQUE (id, owner_user_id, document_id, document_version_id, generation_id)
);

CREATE INDEX ix_s3_jobs_claim
    ON search_v3_indexing_jobs(status, next_retry_at, lease_expires_at, created_at, id);

CREATE TABLE search_v3_retrieval_passages (
    id BIGSERIAL PRIMARY KEY,
    generation_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    document_version_id BIGINT NOT NULL,
    passage_key VARCHAR(200) NOT NULL,
    passage_order INTEGER NOT NULL,
    source_text TEXT NOT NULL,
    retrieval_text TEXT NOT NULL,
    retrieval_text_sha256 VARCHAR(64) NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    page_no INTEGER NULL,
    line_start INTEGER NOT NULL,
    line_end INTEGER NOT NULL,
    code_point_start INTEGER NOT NULL,
    code_point_end INTEGER NOT NULL,
    parent_annotation_candidate_id VARCHAR(200) NOT NULL,
    document_source_sha256 VARCHAR(64) NOT NULL,
    source_block_ids JSONB NOT NULL,
    context_source_block_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_s3_passages_identity
        CHECK (char_length(trim(passage_key)) > 0 AND passage_order >= 0),
    CONSTRAINT ck_s3_passages_text
        CHECK (char_length(trim(source_text)) > 0 AND char_length(trim(retrieval_text)) > 0),
    CONSTRAINT ck_s3_passages_hashes
        CHECK (
            retrieval_text_sha256 ~ '^[0-9a-f]{64}$'
            AND document_source_sha256 ~ '^[0-9a-f]{64}$'
        ),
    CONSTRAINT ck_s3_passages_provenance
        CHECK (
            char_length(trim(source_path)) > 0
            AND (page_no IS NULL OR page_no >= 1)
            AND line_start >= 1
            AND line_end >= line_start
            AND code_point_start >= 0
            AND code_point_end > code_point_start
            AND char_length(trim(parent_annotation_candidate_id)) > 0
            AND jsonb_typeof(source_block_ids) = 'array'
            AND jsonb_array_length(source_block_ids) > 0
            AND jsonb_typeof(context_source_block_ids) = 'array'
        ),
    CONSTRAINT fk_s3_passages_generation_lineage
        FOREIGN KEY (generation_id, owner_user_id, document_id, document_version_id)
        REFERENCES search_v3_index_generations(id, owner_user_id, document_id, document_version_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_s3_passages_generation_key UNIQUE (generation_id, passage_key),
    CONSTRAINT uq_s3_passages_generation_order UNIQUE (generation_id, passage_order),
    CONSTRAINT uq_s3_passages_id_lineage
        UNIQUE (id, owner_user_id, document_id, document_version_id, generation_id),
    CONSTRAINT uq_s3_passages_id_input_lineage
        UNIQUE (
            id, owner_user_id, document_id, document_version_id, generation_id,
            retrieval_text_sha256
        )
);

CREATE INDEX ix_s3_passages_generation_order
    ON search_v3_retrieval_passages(generation_id, passage_order);

CREATE TABLE search_v3_evidence_children (
    id BIGSERIAL PRIMARY KEY,
    generation_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    document_version_id BIGINT NOT NULL,
    passage_id BIGINT NOT NULL,
    child_key VARCHAR(200) NOT NULL,
    child_order INTEGER NOT NULL,
    passage_child_order INTEGER NOT NULL,
    source_block_type VARCHAR(20) NOT NULL,
    source_text TEXT NOT NULL,
    source_text_sha256 VARCHAR(64) NOT NULL,
    source_path VARCHAR(500) NOT NULL,
    page_no INTEGER NULL,
    line_start INTEGER NOT NULL,
    line_end INTEGER NOT NULL,
    code_point_start INTEGER NOT NULL,
    code_point_end INTEGER NOT NULL,
    source_block_id VARCHAR(200) NOT NULL,
    parent_annotation_candidate_id VARCHAR(200) NOT NULL,
    document_source_sha256 VARCHAR(64) NOT NULL,
    source_block_ids JSONB NOT NULL,
    context_source_block_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_s3_children_identity
        CHECK (
            char_length(trim(child_key)) > 0
            AND child_order >= 0
            AND passage_child_order >= 0
        ),
    CONSTRAINT ck_s3_children_block_type
        CHECK (source_block_type IN ('HEADING', 'PARAGRAPH', 'LIST_ITEM', 'TABLE_ROW', 'KEY_VALUE', 'OTHER')),
    CONSTRAINT ck_s3_children_text_hash
        CHECK (
            char_length(trim(source_text)) > 0
            AND source_text_sha256 ~ '^[0-9a-f]{64}$'
            AND document_source_sha256 ~ '^[0-9a-f]{64}$'
        ),
    CONSTRAINT ck_s3_children_provenance
        CHECK (
            char_length(trim(source_path)) > 0
            AND (page_no IS NULL OR page_no >= 1)
            AND line_start >= 1
            AND line_end >= line_start
            AND code_point_start >= 0
            AND code_point_end > code_point_start
            AND char_length(trim(source_block_id)) > 0
            AND char_length(trim(parent_annotation_candidate_id)) > 0
            AND jsonb_typeof(source_block_ids) = 'array'
            AND jsonb_array_length(source_block_ids) > 0
            AND jsonb_typeof(context_source_block_ids) = 'array'
        ),
    CONSTRAINT fk_s3_children_passage_lineage
        FOREIGN KEY (passage_id, owner_user_id, document_id, document_version_id, generation_id)
        REFERENCES search_v3_retrieval_passages(id, owner_user_id, document_id, document_version_id, generation_id)
        ON DELETE CASCADE,
    CONSTRAINT uq_s3_children_generation_key UNIQUE (generation_id, child_key),
    CONSTRAINT uq_s3_children_generation_order UNIQUE (generation_id, child_order),
    CONSTRAINT uq_s3_children_passage_order UNIQUE (passage_id, passage_child_order),
    CONSTRAINT uq_s3_children_id_lineage
        UNIQUE (id, owner_user_id, document_id, document_version_id, generation_id),
    CONSTRAINT uq_s3_children_id_input_lineage
        UNIQUE (
            id, owner_user_id, document_id, document_version_id, generation_id,
            source_text_sha256
        )
);

CREATE INDEX ix_s3_children_generation_order
    ON search_v3_evidence_children(generation_id, child_order);

CREATE INDEX ix_s3_children_passage_order
    ON search_v3_evidence_children(passage_id, passage_child_order);

CREATE TABLE search_v3_passage_embeddings (
    passage_id BIGINT PRIMARY KEY,
    generation_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    document_version_id BIGINT NOT NULL,
    input_sha256 VARCHAR(64) NOT NULL,
    embedding_model_id VARCHAR(200) NOT NULL,
    resolved_model_digest VARCHAR(64) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    input_policy_version VARCHAR(100) NOT NULL,
    embedding vector(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_s3_passage_vectors_dimension
        CHECK (embedding_dimension = 1024 AND vector_dims(embedding) = embedding_dimension),
    CONSTRAINT ck_s3_passage_vectors_norm
        CHECK (vector_norm(embedding) > 0),
    CONSTRAINT fk_s3_passage_vectors_artifact_input
        FOREIGN KEY (
            passage_id, owner_user_id, document_id, document_version_id, generation_id,
            input_sha256
        ) REFERENCES search_v3_retrieval_passages(
            id, owner_user_id, document_id, document_version_id, generation_id,
            retrieval_text_sha256
        ) ON DELETE CASCADE,
    CONSTRAINT fk_s3_passage_vectors_generation_contract
        FOREIGN KEY (
            generation_id, owner_user_id, document_id, document_version_id,
            embedding_model_id, resolved_model_digest, embedding_dimension,
            input_policy_version
        ) REFERENCES search_v3_index_generations(
            id, owner_user_id, document_id, document_version_id,
            embedding_model_id, resolved_model_digest, embedding_dimension,
            passage_input_policy_version
        )
);

CREATE TABLE search_v3_child_embeddings (
    child_id BIGINT PRIMARY KEY,
    generation_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    document_id BIGINT NOT NULL,
    document_version_id BIGINT NOT NULL,
    input_sha256 VARCHAR(64) NOT NULL,
    embedding_model_id VARCHAR(200) NOT NULL,
    resolved_model_digest VARCHAR(64) NOT NULL,
    embedding_dimension INTEGER NOT NULL,
    input_policy_version VARCHAR(100) NOT NULL,
    embedding vector(1024) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_s3_child_vectors_dimension
        CHECK (embedding_dimension = 1024 AND vector_dims(embedding) = embedding_dimension),
    CONSTRAINT ck_s3_child_vectors_norm
        CHECK (vector_norm(embedding) > 0),
    CONSTRAINT fk_s3_child_vectors_artifact_input
        FOREIGN KEY (
            child_id, owner_user_id, document_id, document_version_id, generation_id,
            input_sha256
        ) REFERENCES search_v3_evidence_children(
            id, owner_user_id, document_id, document_version_id, generation_id,
            source_text_sha256
        ) ON DELETE CASCADE,
    CONSTRAINT fk_s3_child_vectors_generation_contract
        FOREIGN KEY (
            generation_id, owner_user_id, document_id, document_version_id,
            embedding_model_id, resolved_model_digest, embedding_dimension,
            input_policy_version
        ) REFERENCES search_v3_index_generations(
            id, owner_user_id, document_id, document_version_id,
            embedding_model_id, resolved_model_digest, embedding_dimension,
            child_input_policy_version
        )
);

CREATE INDEX ix_s3_child_vectors_reuse
    ON search_v3_child_embeddings(
        owner_user_id, input_sha256, embedding_model_id,
        resolved_model_digest, embedding_dimension, input_policy_version
    );

ALTER TABLE documents
    ADD COLUMN active_search_v3_generation_id BIGINT NULL,
    ADD CONSTRAINT ck_documents_s3_pointer_requires_active_version
        CHECK (active_search_v3_generation_id IS NULL OR active_version_id IS NOT NULL),
    ADD CONSTRAINT fk_documents_active_s3_generation_lineage
        FOREIGN KEY (active_search_v3_generation_id, owner_user_id, id, active_version_id)
        REFERENCES search_v3_index_generations(id, owner_user_id, document_id, document_version_id);
