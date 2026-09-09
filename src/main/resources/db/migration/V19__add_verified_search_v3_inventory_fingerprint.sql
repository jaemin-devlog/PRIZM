ALTER TABLE search_v3_index_generations
    ADD COLUMN verified_inventory_sha256 VARCHAR(64) NULL,
    ADD CONSTRAINT ck_s3_generations_verified_inventory
        CHECK (
            verified_inventory_sha256 IS NULL
            OR verified_inventory_sha256 ~ '^[0-9a-f]{64}$'
        );

-- Production Search V2 owns active_version_id. When it activates a newer immutable
-- version, the old same-version V3 shadow pointer must not make the V2 transaction
-- violate V18's composite FK. The shadow generation is detached without changing
-- either document version status or the new Production pointer chosen by V2.
CREATE FUNCTION detach_search_v3_generation_on_version_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.active_version_id IS DISTINCT FROM OLD.active_version_id THEN
        IF OLD.active_search_v3_generation_id IS NOT NULL THEN
            UPDATE search_v3_index_generations
            SET status = 'SUPERSEDED',
                superseded_at = COALESCE(superseded_at, now()),
                updated_at = now()
            WHERE id = OLD.active_search_v3_generation_id
              AND owner_user_id = OLD.owner_user_id
              AND document_id = OLD.id
              AND document_version_id = OLD.active_version_id
              AND status = 'ACTIVE';
        END IF;
        NEW.active_search_v3_generation_id := NULL;
    END IF;
    RETURN NEW;
END
$$;

CREATE TRIGGER trg_documents_detach_search_v3_on_version_change
    BEFORE UPDATE OF active_version_id ON documents
    FOR EACH ROW
    EXECUTE FUNCTION detach_search_v3_generation_on_version_change();
