ALTER TABLE search_v3_index_generations
    DROP CONSTRAINT ck_s3_generations_manifest;

ALTER TABLE search_v3_index_generations
    ALTER COLUMN expected_passage_count DROP NOT NULL,
    ALTER COLUMN expected_child_count DROP NOT NULL,
    ALTER COLUMN expected_manifest_sha256 DROP NOT NULL;

ALTER TABLE search_v3_index_generations
    ADD CONSTRAINT ck_s3_generations_manifest
        CHECK (
            (
                expected_passage_count IS NULL
                AND expected_child_count IS NULL
                AND expected_manifest_sha256 IS NULL
                AND status IN ('BUILDING', 'FAILED')
            )
            OR (
                expected_passage_count IS NOT NULL
                AND expected_child_count IS NOT NULL
                AND expected_manifest_sha256 IS NOT NULL
                AND expected_passage_count > 0
                AND expected_child_count > 0
                AND expected_manifest_sha256 ~ '^[0-9a-f]{64}$'
            )
        );
