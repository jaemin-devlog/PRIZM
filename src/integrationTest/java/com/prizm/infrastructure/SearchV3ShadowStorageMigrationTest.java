package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** V18 Search V3 shadow schema의 PostgreSQL·pgvector 제약을 검증한다. */
@Testcontainers
class SearchV3ShadowStorageMigrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");
    private static final String MODEL_DIGEST = "a".repeat(64);
    private static final String MANIFEST_HASH = "b".repeat(64);
    private static final String SOURCE_HASH = "c".repeat(64);
    private static final String INPUT_HASH = "d".repeat(64);

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm_search_v3_shadow")
            .withUsername("prizm")
            .withPassword("prizm-test");

    @Test
    void migratesAdditivelyAndKeepsSearchV2Chunks() {
        MigrationDatabase database = createMigratedDatabase();
        JdbcTemplate jdbc = database.jdbcTemplate();

        assertThat(successfulMigrationCount(jdbc)).isEqualTo(18L);
        assertThat(existingTables(jdbc)).contains(
                "documents",
                "document_versions",
                "document_chunks",
                "processing_jobs",
                "search_v3_index_generations",
                "search_v3_indexing_jobs",
                "search_v3_retrieval_passages",
                "search_v3_evidence_children",
                "search_v3_passage_embeddings",
                "search_v3_child_embeddings");
        assertConstraintsExist(jdbc);
        assertThat(jdbc.queryForObject(
                "SELECT atttypmod FROM pg_attribute WHERE attrelid = 'document_chunks'::regclass AND attname = 'embedding'",
                Integer.class)).isEqualTo(1024);

        Fixture fixture = createFixture(jdbc, "migration-owner");
        jdbc.update(
                """
                INSERT INTO document_chunks(
                    owner_user_id, document_version_id, chunk_no, content, embedding,
                    source_type, source_index, source_label
                ) VALUES (?, ?, 1, 'Search V2 remains available',
                    array_prepend(1::real, array_fill(0::real, ARRAY[1023]))::vector,
                    'TEXT_CHUNK', 1, '텍스트 구간 1')
                """,
                fixture.ownerUserId(), fixture.documentVersionId());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM document_chunks", Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject(
                "SELECT active_search_v3_generation_id FROM documents WHERE id = ?",
                Long.class,
                fixture.documentId())).isNull();
    }

    @Test
    void supportsFirstUploadAndMultipleGenerationsForOneVersion() {
        JdbcTemplate jdbc = createMigratedDatabase().jdbcTemplate();
        Fixture fixture = createFixture(jdbc, "first-upload-owner");

        Long firstGeneration = insertBuildingGeneration(jdbc, fixture);
        Long secondGeneration = insertBuildingGeneration(jdbc, fixture);
        insertPendingJob(jdbc, fixture, firstGeneration);
        insertPendingJob(jdbc, fixture, secondGeneration);

        assertThat(firstGeneration).isNotEqualTo(secondGeneration);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM search_v3_index_generations WHERE document_version_id = ?",
                Long.class,
                fixture.documentVersionId())).isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "SELECT active_version_id FROM documents WHERE id = ?",
                Long.class,
                fixture.documentId())).isNull();
        assertThat(jdbc.queryForObject(
                "SELECT active_search_v3_generation_id FROM documents WHERE id = ?",
                Long.class,
                fixture.documentId())).isNull();

        jdbc.update(
                """
                UPDATE search_v3_index_generations
                SET status = 'FAILED', failed_at = now(), failure_stage = 'STORAGE', updated_at = now()
                WHERE id = ?
                """,
                firstGeneration);
        assertThat(jdbc.queryForObject(
                "SELECT active_search_v3_generation_id FROM documents WHERE id = ?",
                Long.class,
                fixture.documentId())).isNull();
    }

    @Test
    void rejectsCrossOwnerAndCrossDocumentGenerationLineage() {
        JdbcTemplate jdbc = createMigratedDatabase().jdbcTemplate();
        Fixture first = createFixture(jdbc, "first-lineage-owner");
        Fixture second = createFixture(jdbc, "second-lineage-owner");
        Long sameOwnerOtherDocumentId = createDocument(jdbc, first.ownerUserId(), "same-owner-other-document");

        assertThatThrownBy(() -> insertGeneration(
                jdbc,
                first.ownerUserId(),
                first.documentId(),
                second.documentVersionId(),
                "BUILDING"))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThatThrownBy(() -> insertGeneration(
                jdbc,
                first.ownerUserId(),
                sameOwnerOtherDocumentId,
                first.documentVersionId(),
                "BUILDING"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsChildFromAnotherGenerationPassage() {
        JdbcTemplate jdbc = createMigratedDatabase().jdbcTemplate();
        Fixture fixture = createFixture(jdbc, "child-lineage-owner");
        Long firstGeneration = insertBuildingGeneration(jdbc, fixture);
        Long secondGeneration = insertBuildingGeneration(jdbc, fixture);
        Long firstPassage = insertPassage(jdbc, fixture, firstGeneration, "P1", 0, INPUT_HASH);

        assertThatThrownBy(() -> insertChild(
                jdbc,
                fixture,
                secondGeneration,
                firstPassage,
                "C1",
                0,
                INPUT_HASH))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesArtifactVectorIdentityInputAndContract() {
        JdbcTemplate jdbc = createMigratedDatabase().jdbcTemplate();
        Fixture fixture = createFixture(jdbc, "vector-owner");
        Long generation = insertBuildingGeneration(jdbc, fixture);
        Long passage = insertPassage(jdbc, fixture, generation, "P1", 0, INPUT_HASH);
        Long child = insertChild(jdbc, fixture, generation, passage, "C1", 0, INPUT_HASH);

        insertPassageVector(jdbc, fixture, generation, passage, INPUT_HASH);
        insertChildVector(jdbc, fixture, generation, child, INPUT_HASH);

        assertThat(jdbc.queryForObject("SELECT vector_dims(embedding) FROM search_v3_passage_embeddings", Integer.class))
                .isEqualTo(1024);
        assertThat(jdbc.queryForObject("SELECT vector_dims(embedding) FROM search_v3_child_embeddings", Integer.class))
                .isEqualTo(1024);
        assertThatThrownBy(() -> insertPassageVector(jdbc, fixture, generation, passage, INPUT_HASH))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPassageVector(jdbc, fixture, generation, passage, "e".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChildVector(jdbc, fixture, generation, child, INPUT_HASH))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChildVector(jdbc, fixture, generation, child + 999, INPUT_HASH))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChildVector(jdbc, fixture, generation, child, "e".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);

        Long secondPassage = insertPassage(jdbc, fixture, generation, "P2", 1, "e".repeat(64));
        Long secondChild = insertChild(
                jdbc,
                fixture,
                generation,
                secondPassage,
                "C2",
                1,
                "e".repeat(64));
        Long otherGeneration = insertBuildingGeneration(jdbc, fixture);
        assertThatThrownBy(() -> insertPassageVector(
                jdbc,
                fixture,
                otherGeneration,
                secondPassage,
                "e".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertChildVector(
                jdbc,
                fixture,
                otherGeneration,
                secondChild,
                "e".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO search_v3_child_embeddings(
                    child_id, generation_id, owner_user_id, document_id, document_version_id,
                    input_sha256, embedding_model_id, resolved_model_digest,
                    embedding_dimension, input_policy_version, embedding
                ) VALUES (?, ?, ?, ?, ?, ?, 'bge-m3', ?, 1024, 'child-source-v1',
                    array_fill(0::real, ARRAY[1024])::vector)
                """,
                secondChild,
                generation,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                "e".repeat(64),
                MODEL_DIGEST))
                .isInstanceOf(DataIntegrityViolationException.class);

        Fixture other = createFixture(jdbc, "cross-owner-vector");
        assertThatThrownBy(() -> insertChildVector(
                jdbc,
                other,
                generation,
                secondChild,
                "e".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesGenerationJobIdentityLeaseAndRecoveryTokenShape() {
        JdbcTemplate jdbc = createMigratedDatabase().jdbcTemplate();
        Fixture fixture = createFixture(jdbc, "job-owner");
        Long generation = insertBuildingGeneration(jdbc, fixture);
        insertPendingJob(jdbc, fixture, generation);

        assertThatThrownBy(() -> insertPendingJob(jdbc, fixture, generation))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                """
                UPDATE search_v3_indexing_jobs
                SET status = 'PROCESSING', claim_version = 1, attempt_count = 1,
                    started_at = now(), lease_expires_at = now() + interval '5 minutes',
                    recovery_lock_token = ?
                WHERE generation_id = ?
                """,
                UUID.randomUUID(),
                generation))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void protectsActivePointerAndSupportsSameVersionReindex() {
        MigrationDatabase database = createMigratedDatabase();
        JdbcTemplate jdbc = database.jdbcTemplate();
        Fixture fixture = createFixture(jdbc, "active-owner");
        jdbc.update(
                "UPDATE document_versions SET status = 'ACTIVE' WHERE id = ?",
                fixture.documentVersionId());
        jdbc.update(
                "UPDATE documents SET active_version_id = ? WHERE id = ?",
                fixture.documentVersionId(),
                fixture.documentId());

        Long oldGeneration = insertGeneration(
                jdbc,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                "ACTIVE");
        Long newGeneration = insertGeneration(
                jdbc,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                "READY");
        insertCompletedJob(jdbc, fixture, oldGeneration);
        insertProcessingJob(jdbc, fixture, newGeneration);
        jdbc.update(
                "UPDATE documents SET active_search_v3_generation_id = ? WHERE id = ?",
                oldGeneration,
                fixture.documentId());

        Fixture other = createFixture(jdbc, "other-active-owner");
        jdbc.update("UPDATE document_versions SET status = 'ACTIVE' WHERE id = ?", other.documentVersionId());
        Long otherGeneration = insertGeneration(
                jdbc,
                other.ownerUserId(),
                other.documentId(),
                other.documentVersionId(),
                "ACTIVE");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE documents SET active_search_v3_generation_id = ? WHERE id = ?",
                otherGeneration,
                fixture.documentId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        Long sameOwnerDocument = createDocument(jdbc, fixture.ownerUserId(), "same-owner-active-document");
        Long sameOwnerVersion = createVersion(
                jdbc,
                fixture.ownerUserId(),
                sameOwnerDocument);
        jdbc.update("UPDATE document_versions SET status = 'ACTIVE' WHERE id = ?", sameOwnerVersion);
        jdbc.update(
                "UPDATE documents SET active_version_id = ? WHERE id = ?",
                sameOwnerVersion,
                sameOwnerDocument);
        Long sameOwnerGeneration = insertGeneration(
                jdbc,
                fixture.ownerUserId(),
                sameOwnerDocument,
                sameOwnerVersion,
                "ACTIVE");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE documents SET active_search_v3_generation_id = ? WHERE id = ?",
                sameOwnerGeneration,
                fixture.documentId()))
                .isInstanceOf(DataIntegrityViolationException.class);

        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(database.dataSource()));
        transaction.executeWithoutResult(status -> {
            jdbc.update(
                    """
                    UPDATE search_v3_index_generations
                    SET status = 'SUPERSEDED', superseded_at = now(), updated_at = now()
                    WHERE id = ?
                    """,
                    oldGeneration);
            jdbc.update(
                    """
                    UPDATE search_v3_index_generations
                    SET status = 'ACTIVE', activated_at = now(), updated_at = now()
                    WHERE id = ?
                    """,
                    newGeneration);
            jdbc.update(
                    """
                    UPDATE search_v3_indexing_jobs
                    SET status = 'COMPLETED', lease_expires_at = NULL, completed_at = now(), updated_at = now()
                    WHERE generation_id = ?
                    """,
                    newGeneration);
            jdbc.update(
                    "UPDATE documents SET active_search_v3_generation_id = ? WHERE id = ?",
                    newGeneration,
                    fixture.documentId());
        });

        assertThat(jdbc.queryForObject(
                "SELECT active_version_id FROM documents WHERE id = ?",
                Long.class,
                fixture.documentId())).isEqualTo(fixture.documentVersionId());
        assertThat(jdbc.queryForObject(
                "SELECT active_search_v3_generation_id FROM documents WHERE id = ?",
                Long.class,
                fixture.documentId())).isEqualTo(newGeneration);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM search_v3_index_generations WHERE id = ?",
                String.class,
                oldGeneration)).isEqualTo("SUPERSEDED");
        assertThat(jdbc.update(
                "DELETE FROM search_v3_index_generations WHERE id = ?",
                oldGeneration)).isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update(
                "DELETE FROM search_v3_index_generations WHERE id = ?",
                newGeneration))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private MigrationDatabase createMigratedDatabase() {
        String databaseName = "prizm_s3_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        admin.execute("CREATE DATABASE " + databaseName);
        String url = postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + databaseName);
        DataSource dataSource = new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        return new MigrationDatabase(dataSource, new JdbcTemplate(dataSource));
    }

    private Fixture createFixture(JdbcTemplate jdbc, String label) {
        Long owner = jdbc.queryForObject(
                """
                INSERT INTO users(email, password_hash, role, enabled)
                VALUES (?, 'not-used', 'USER', TRUE) RETURNING id
                """,
                Long.class,
                label + "-" + UUID.randomUUID() + "@example.com");
        Long document = createDocument(jdbc, owner, label);
        Long version = createVersion(jdbc, owner, document);
        return new Fixture(owner, document, version);
    }

    private Long createVersion(JdbcTemplate jdbc, Long owner, Long document) {
        return jdbc.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path,
                    file_type, content_hash, status
                ) VALUES (?, ?, 1, 'fixture.txt', 'test/fixture.txt', 'TXT', ?, 'PROCESSING')
                RETURNING id
                """,
                Long.class,
                owner,
                document,
                "f".repeat(64));
    }

    private Long createDocument(JdbcTemplate jdbc, Long owner, String title) {
        return jdbc.queryForObject(
                "INSERT INTO documents(owner_user_id, title, document_type) VALUES (?, ?, 'OTHER') RETURNING id",
                Long.class,
                owner,
                title);
    }

    private Long insertBuildingGeneration(JdbcTemplate jdbc, Fixture fixture) {
        return insertGeneration(
                jdbc,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                "BUILDING");
    }

    private Long insertGeneration(
            JdbcTemplate jdbc,
            Long owner,
            Long document,
            Long version,
            String status) {
        String statusColumns = switch (status) {
            case "READY" -> ", build_completed_at";
            case "ACTIVE" -> ", build_completed_at, activated_at";
            default -> "";
        };
        String statusValues = switch (status) {
            case "READY" -> ", now()";
            case "ACTIVE" -> ", now(), now()";
            default -> "";
        };
        return jdbc.queryForObject(
                """
                INSERT INTO search_v3_index_generations(
                    owner_user_id, document_id, document_version_id, status,
                    structure_policy_version, passage_policy_version, child_policy_version,
                    embedding_model_id, resolved_model_digest, embedding_dimension,
                    passage_input_policy_version, child_input_policy_version,
                    expected_passage_count, expected_child_count, expected_manifest_sha256
                """ + statusColumns + """
                ) VALUES (?, ?, ?, ?, 'struct-v1', 'passage-v1', 'child-v1',
                    'bge-m3', ?, 1024, 'passage-source-v1', 'child-source-v1', 1, 1, ?
                """ + statusValues + ") RETURNING id",
                Long.class,
                owner,
                document,
                version,
                status,
                MODEL_DIGEST,
                MANIFEST_HASH);
    }

    private void insertPendingJob(JdbcTemplate jdbc, Fixture fixture, Long generation) {
        jdbc.update(
                """
                INSERT INTO search_v3_indexing_jobs(
                    generation_id, owner_user_id, document_id, document_version_id
                ) VALUES (?, ?, ?, ?)
                """,
                generation,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId());
    }

    private void insertProcessingJob(JdbcTemplate jdbc, Fixture fixture, Long generation) {
        jdbc.update(
                """
                INSERT INTO search_v3_indexing_jobs(
                    generation_id, owner_user_id, document_id, document_version_id,
                    status, claim_version, attempt_count, lease_expires_at, started_at
                ) VALUES (?, ?, ?, ?, 'PROCESSING', 1, 1, now() + interval '5 minutes', now())
                """,
                generation,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId());
    }

    private void insertCompletedJob(JdbcTemplate jdbc, Fixture fixture, Long generation) {
        jdbc.update(
                """
                INSERT INTO search_v3_indexing_jobs(
                    generation_id, owner_user_id, document_id, document_version_id,
                    status, claim_version, attempt_count, started_at, completed_at
                ) VALUES (?, ?, ?, ?, 'COMPLETED', 1, 1, now(), now())
                """,
                generation,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId());
    }

    private Long insertPassage(
            JdbcTemplate jdbc,
            Fixture fixture,
            Long generation,
            String key,
            int order,
            String inputHash) {
        return jdbc.queryForObject(
                """
                INSERT INTO search_v3_retrieval_passages(
                    generation_id, owner_user_id, document_id, document_version_id,
                    passage_key, passage_order, source_text, retrieval_text, retrieval_text_sha256,
                    source_path, page_no, line_start, line_end, code_point_start, code_point_end,
                    parent_annotation_candidate_id, document_source_sha256, source_block_ids
                ) VALUES (?, ?, ?, ?, ?, ?, 'source text', 'retrieval text', ?,
                    'test/fixture.txt', NULL, 1, 1, 0, 11, 'parent-1', ?, '["block-1"]'::jsonb)
                RETURNING id
                """,
                Long.class,
                generation,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                key,
                order,
                inputHash,
                SOURCE_HASH);
    }

    private Long insertChild(
            JdbcTemplate jdbc,
            Fixture fixture,
            Long generation,
            Long passage,
            String key,
            int order,
            String inputHash) {
        return jdbc.queryForObject(
                """
                INSERT INTO search_v3_evidence_children(
                    generation_id, owner_user_id, document_id, document_version_id, passage_id,
                    child_key, child_order, passage_child_order, source_block_type,
                    source_text, source_text_sha256, source_path, page_no,
                    line_start, line_end, code_point_start, code_point_end,
                    source_block_id, parent_annotation_candidate_id,
                    document_source_sha256, source_block_ids
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 'PARAGRAPH',
                    'source text', ?, 'test/fixture.txt', NULL,
                    1, 1, 0, 11, 'block-1', 'parent-1', ?, '["block-1"]'::jsonb)
                RETURNING id
                """,
                Long.class,
                generation,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                passage,
                key,
                order,
                inputHash,
                SOURCE_HASH);
    }

    private void insertPassageVector(
            JdbcTemplate jdbc,
            Fixture fixture,
            Long generation,
            Long passage,
            String inputHash) {
        jdbc.update(
                """
                INSERT INTO search_v3_passage_embeddings(
                    passage_id, generation_id, owner_user_id, document_id, document_version_id,
                    input_sha256, embedding_model_id, resolved_model_digest,
                    embedding_dimension, input_policy_version, embedding
                ) VALUES (?, ?, ?, ?, ?, ?, 'bge-m3', ?, 1024, 'passage-source-v1',
                    array_prepend(1::real, array_fill(0::real, ARRAY[1023]))::vector)
                """,
                passage,
                generation,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                inputHash,
                MODEL_DIGEST);
    }

    private void insertChildVector(
            JdbcTemplate jdbc,
            Fixture fixture,
            Long generation,
            Long child,
            String inputHash) {
        jdbc.update(
                """
                INSERT INTO search_v3_child_embeddings(
                    child_id, generation_id, owner_user_id, document_id, document_version_id,
                    input_sha256, embedding_model_id, resolved_model_digest,
                    embedding_dimension, input_policy_version, embedding
                ) VALUES (?, ?, ?, ?, ?, ?, 'bge-m3', ?, 1024, 'child-source-v1',
                    array_prepend(1::real, array_fill(0::real, ARRAY[1023]))::vector)
                """,
                child,
                generation,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                inputHash,
                MODEL_DIGEST);
    }

    private List<String> existingTables(JdbcTemplate jdbc) {
        return jdbc.queryForList(
                "SELECT tablename FROM pg_tables WHERE schemaname = 'public'",
                String.class);
    }

    private void assertConstraintsExist(JdbcTemplate jdbc) {
        for (String name : List.of(
                "fk_s3_generations_version_lineage",
                "uq_s3_jobs_generation",
                "fk_s3_jobs_generation_lineage",
                "fk_s3_passages_generation_lineage",
                "fk_s3_children_passage_lineage",
                "fk_s3_passage_vectors_artifact_input",
                "fk_s3_passage_vectors_generation_contract",
                "fk_s3_child_vectors_artifact_input",
                "fk_s3_child_vectors_generation_contract",
                "fk_documents_active_s3_generation_lineage")) {
            assertThat(jdbc.queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint WHERE conname = ?",
                    Long.class,
                    name)).isEqualTo(1L);
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE indexname = 'uq_s3_generations_one_active_document'",
                Long.class)).isEqualTo(1L);
    }

    private long successfulMigrationCount(JdbcTemplate jdbc) {
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success",
                Long.class);
        return count == null ? 0 : count;
    }

    private record MigrationDatabase(DataSource dataSource, JdbcTemplate jdbcTemplate) {
    }

    private record Fixture(Long ownerUserId, Long documentId, Long documentVersionId) {
    }
}
