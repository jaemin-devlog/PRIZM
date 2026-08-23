CREATE TABLE tags (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    normalized_name VARCHAR(100) NOT NULL,
    source VARCHAR(10) NOT NULL,
    owner_user_id BIGINT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_tags_source CHECK (source IN ('SYSTEM', 'USER')),
    CONSTRAINT ck_tags_owner_scope CHECK (
        (source = 'SYSTEM' AND owner_user_id IS NULL)
        OR (source = 'USER' AND owner_user_id IS NOT NULL)
    ),
    CONSTRAINT fk_tags_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES users(id)
);

CREATE UNIQUE INDEX uq_tags_system_normalized_name
    ON tags(normalized_name)
    WHERE source = 'SYSTEM';

CREATE UNIQUE INDEX uq_tags_user_owner_normalized_name
    ON tags(owner_user_id, normalized_name)
    WHERE source = 'USER';

CREATE INDEX ix_tags_owner_normalized_name
    ON tags(owner_user_id, normalized_name, id);

CREATE TABLE document_tags (
    document_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (document_id, tag_id),
    CONSTRAINT fk_document_tags_document_owner
        FOREIGN KEY (document_id, owner_user_id)
        REFERENCES documents(id, owner_user_id)
        ON DELETE CASCADE,
    CONSTRAINT fk_document_tags_tag
        FOREIGN KEY (tag_id) REFERENCES tags(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_document_tags_owner_user
        FOREIGN KEY (owner_user_id) REFERENCES users(id)
);

CREATE INDEX ix_document_tags_owner_tag
    ON document_tags(owner_user_id, tag_id, document_id);

INSERT INTO tags(name, normalized_name, source, owner_user_id) VALUES
    ('Java', 'java', 'SYSTEM', NULL),
    ('Spring Boot', 'spring boot', 'SYSTEM', NULL),
    ('Spring Security', 'spring security', 'SYSTEM', NULL),
    ('Spring Batch', 'spring batch', 'SYSTEM', NULL),
    ('Redis', 'redis', 'SYSTEM', NULL),
    ('PostgreSQL', 'postgresql', 'SYSTEM', NULL),
    ('MySQL', 'mysql', 'SYSTEM', NULL),
    ('Docker', 'docker', 'SYSTEM', NULL),
    ('AWS', 'aws', 'SYSTEM', NULL),
    ('React', 'react', 'SYSTEM', NULL),
    ('TypeScript', 'typescript', 'SYSTEM', NULL);
