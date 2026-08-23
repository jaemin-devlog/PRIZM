package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** V8 소유권 migration이 기존 문서 데이터를 추측해 귀속하지 않는지 실제 PostgreSQL에서 검증한다. */
@Testcontainers
class CareerPlatformMigrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");
    private static final String LEGACY_DATA_ERROR = "기존 문서 데이터의 소유자를 확인할 수 없으므로 V8 migration을 적용할 수 없습니다.";
    private static final List<String> DOCUMENT_TABLES = List.of(
            "documents", "document_versions", "document_chunks", "processing_jobs");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm_migration")
            .withUsername("prizm")
            .withPassword("prizm-test");

    @Test
    void migratesEmptyDocumentTablesWithoutUsersAndCreatesOwnershipSchema() {
        MigrationDatabase database = createMigrationDatabase();

        migrateTo(database, "7");
        migrateLatest(database);

        assertOwnershipSchema(database.jdbcTemplate());
        assertDocumentTypeSchema(database.jdbcTemplate());
        assertChunkSourceSchema(database.jdbcTemplate());
        assertPdfFileTypeSchema(database.jdbcTemplate());
        assertFileCleanupJobSchema(database.jdbcTemplate());
        assertProcessingProgressSchema(database.jdbcTemplate());
        assertThat(successfulMigrationCount(database.jdbcTemplate())).isEqualTo(16L);
        for (String table : DOCUMENT_TABLES) {
            assertThat(rowCount(database.jdbcTemplate(), table)).isZero();
        }
        assertThat(rowCount(database.jdbcTemplate(), "file_cleanup_jobs")).isZero();
    }

    @Test
    void migratesEmptyDocumentTablesWhenUserAndSystemAdminExist() {
        MigrationDatabase database = createMigrationDatabase();

        migrateTo(database, "7");
        createUser(database.jdbcTemplate(), "owner@prizm.local", "USER", true);
        createUser(database.jdbcTemplate(), "operator@prizm.local", "SYSTEM_ADMIN", true);

        migrateLatest(database);

        assertOwnershipSchema(database.jdbcTemplate());
        assertDocumentTypeSchema(database.jdbcTemplate());
        assertFileCleanupJobSchema(database.jdbcTemplate());
        assertProcessingProgressSchema(database.jdbcTemplate());
        for (String table : DOCUMENT_TABLES) {
            assertThat(rowCount(database.jdbcTemplate(), table)).isZero();
        }
    }

    @Test
    void migratesExistingV8DocumentToOtherDocumentType() {
        MigrationDatabase database = createMigrationDatabase();

        migrateTo(database, "8");
        Long ownerUserId = createUser(database.jdbcTemplate(), "owner@prizm.local", "USER", true);
        Long documentId = database.jdbcTemplate().queryForObject(
                "INSERT INTO documents(owner_user_id, title) VALUES (?, 'existing-document') RETURNING id",
                Long.class,
                ownerUserId);

        migrateLatest(database);

        assertThat(database.jdbcTemplate().queryForObject(
                "SELECT document_type FROM documents WHERE id = ?", String.class, documentId))
                .isEqualTo("OTHER");
        assertDocumentTypeSchema(database.jdbcTemplate());
        assertChunkSourceSchema(database.jdbcTemplate());
        assertPdfFileTypeSchema(database.jdbcTemplate());
        assertFileCleanupJobSchema(database.jdbcTemplate());
        assertProcessingProgressSchema(database.jdbcTemplate());
        assertThat(successfulMigrationCount(database.jdbcTemplate())).isEqualTo(16L);
    }

    @Test
    void backfillsV9ChunksWithTextSourceWithoutDeletingContentOrEmbeddings() {
        MigrationDatabase database = createMigrationDatabase();

        migrateTo(database, "9");
        Long ownerUserId = createUser(database.jdbcTemplate(), "owner@prizm.local", "USER", true);
        Long documentId = database.jdbcTemplate().queryForObject(
                "INSERT INTO documents(owner_user_id, title, document_type) VALUES (?, 'existing-document', 'OTHER') RETURNING id",
                Long.class,
                ownerUserId);
        Long versionId = database.jdbcTemplate().queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path, file_type,
                    content_hash, status
                )
                VALUES (?, ?, 1, 'existing.txt', 'documents/existing.txt', 'TXT', repeat('a', 64), 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                documentId);
        database.jdbcTemplate().update(
                """
                INSERT INTO document_chunks(
                    owner_user_id, content, embedding, document_version_id, chunk_no, page_no
                )
                VALUES (?, ?, array_fill(0::real, ARRAY[1024])::vector, ?, ?, NULL)
                """,
                ownerUserId,
                "first existing chunk",
                versionId,
                5);
        database.jdbcTemplate().update(
                """
                INSERT INTO document_chunks(
                    owner_user_id, content, embedding, document_version_id, chunk_no, page_no
                )
                VALUES (?, ?, array_fill(0::real, ARRAY[1024])::vector, ?, ?, NULL)
                """,
                ownerUserId,
                "second existing chunk",
                versionId,
                9);

        migrateLatest(database);

        List<StoredChunk> chunks = database.jdbcTemplate().query(
                """
                SELECT content, source_type, source_index, source_label, vector_dims(embedding) AS embedding_dimensions
                FROM document_chunks
                WHERE document_version_id = ?
                ORDER BY chunk_no
                """,
                (resultSet, rowNum) -> new StoredChunk(
                        resultSet.getString("content"),
                        resultSet.getString("source_type"),
                        resultSet.getInt("source_index"),
                        resultSet.getString("source_label"),
                        resultSet.getInt("embedding_dimensions")),
                versionId);

        assertThat(chunks).containsExactly(
                new StoredChunk("first existing chunk", "TEXT_CHUNK", 1, "텍스트 구간 1", 1024),
                new StoredChunk("second existing chunk", "TEXT_CHUNK", 2, "텍스트 구간 2", 1024));
        assertChunkSourceSchema(database.jdbcTemplate());
        assertPdfFileTypeSchema(database.jdbcTemplate());
        assertFileCleanupJobSchema(database.jdbcTemplate());
        assertProcessingProgressSchema(database.jdbcTemplate());
        assertThat(successfulMigrationCount(database.jdbcTemplate())).isEqualTo(16L);
    }

    @Test
    void rejectsLegacyDocumentWithExactlyOneActiveUserWithoutChangingData() {
        MigrationDatabase database = createMigrationDatabase();

        migrateTo(database, "7");
        LegacyDocument legacyDocument = createLegacyDocument(database.jdbcTemplate());
        Long userId = createUser(database.jdbcTemplate(), "only-user@prizm.local", "USER", true);

        assertV8RejectedAndRolledBack(database, legacyDocument);
        assertThat(database.jdbcTemplate().queryForObject(
                "SELECT role FROM users WHERE id = ?", String.class, userId)).isEqualTo("USER");
        assertThat(database.jdbcTemplate().queryForObject(
                "SELECT enabled FROM users WHERE id = ?", Boolean.class, userId)).isTrue();
    }

    @Test
    void rejectsLegacyDocumentWithUserAndSystemAdminWithoutChangingData() {
        MigrationDatabase database = createMigrationDatabase();

        migrateTo(database, "7");
        LegacyDocument legacyDocument = createLegacyDocument(database.jdbcTemplate());
        Long userId = createUser(database.jdbcTemplate(), "user@prizm.local", "USER", true);
        Long systemAdminId = createUser(database.jdbcTemplate(), "system-admin@prizm.local", "SYSTEM_ADMIN", true);

        assertV8RejectedAndRolledBack(database, legacyDocument);
        assertThat(database.jdbcTemplate().queryForObject(
                "SELECT role FROM users WHERE id = ?", String.class, userId)).isEqualTo("USER");
        assertThat(database.jdbcTemplate().queryForObject(
                "SELECT role FROM users WHERE id = ?", String.class, systemAdminId)).isEqualTo("SYSTEM_ADMIN");
    }

    @Test
    void rejectsLegacyDocumentWithMultipleActiveUsers() {
        MigrationDatabase database = createMigrationDatabase();

        migrateTo(database, "7");
        LegacyDocument legacyDocument = createLegacyDocument(database.jdbcTemplate());
        createUser(database.jdbcTemplate(), "first-user@prizm.local", "USER", true);
        createUser(database.jdbcTemplate(), "second-user@prizm.local", "USER", true);

        assertV8RejectedAndRolledBack(database, legacyDocument);
    }

    @Test
    void rejectsLegacyDocumentWithoutUsers() {
        MigrationDatabase database = createMigrationDatabase();

        migrateTo(database, "7");
        LegacyDocument legacyDocument = createLegacyDocument(database.jdbcTemplate());

        assertV8RejectedAndRolledBack(database, legacyDocument);
    }

    private MigrationDatabase createMigrationDatabase() {
        String databaseName = "prizm_migration_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate adminJdbcTemplate = jdbcTemplate(postgres.getJdbcUrl());
        adminJdbcTemplate.execute("CREATE DATABASE " + databaseName);

        String databaseUrl = postgres.getJdbcUrl().replace(
                "/" + postgres.getDatabaseName(), "/" + databaseName);
        DataSource dataSource = new DriverManagerDataSource(
                databaseUrl, postgres.getUsername(), postgres.getPassword());
        return new MigrationDatabase(dataSource, new JdbcTemplate(dataSource));
    }

    private JdbcTemplate jdbcTemplate(String jdbcUrl) {
        return new JdbcTemplate(new DriverManagerDataSource(
                jdbcUrl, postgres.getUsername(), postgres.getPassword()));
    }

    private void migrateTo(MigrationDatabase database, String version) {
        Flyway.configure()
                .dataSource(database.dataSource())
                .target(MigrationVersion.fromVersion(version))
                .load()
                .migrate();
    }

    private void migrateLatest(MigrationDatabase database) {
        Flyway.configure()
                .dataSource(database.dataSource())
                .load()
                .migrate();
    }

    private Long createUser(JdbcTemplate jdbcTemplate, String email, String role, boolean enabled) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO users(email, password_hash, role, enabled)
                VALUES (?, repeat('x', 60), ?, ?)
                RETURNING id
                """,
                Long.class,
                email,
                role,
                enabled);
    }

    private LegacyDocument createLegacyDocument(JdbcTemplate jdbcTemplate) {
        Long documentId = jdbcTemplate.queryForObject(
                "INSERT INTO documents(title) VALUES ('legacy-document') RETURNING id",
                Long.class);
        Long versionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_versions(
                    document_id, version_no, original_file_name, stored_file_path, file_type, content_hash, status
                )
                VALUES (?, 1, 'legacy.txt', 'documents/legacy/legacy.txt', 'TXT', repeat('a', 64), 'QUARANTINED')
                RETURNING id
                """,
                Long.class,
                documentId);
        Long chunkId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_chunks(content, embedding, document_version_id, chunk_no)
                VALUES ('legacy chunk', array_fill(0::real, ARRAY[1024])::vector, ?, 0)
                RETURNING id
                """,
                Long.class,
                versionId);
        Long processingJobId = jdbcTemplate.queryForObject(
                """
                INSERT INTO processing_jobs(document_version_id, job_type, status)
                VALUES (?, 'INDEXING', 'PENDING')
                RETURNING id
                """,
                Long.class,
                versionId);
        return new LegacyDocument(documentId, versionId, chunkId, processingJobId);
    }

    private void assertV8RejectedAndRolledBack(MigrationDatabase database, LegacyDocument legacyDocument) {
        assertThatThrownBy(() -> migrateLatest(database))
                .hasStackTraceContaining(LEGACY_DATA_ERROR);

        JdbcTemplate jdbcTemplate = database.jdbcTemplate();
        assertThat(successfulMigrationCount(jdbcTemplate)).isEqualTo(7L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '8'", Long.class)).isZero();
        for (String table : DOCUMENT_TABLES) {
            assertThat(ownerColumnCount(jdbcTemplate, table)).isZero();
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT title FROM documents WHERE id = ?", String.class, legacyDocument.documentId()))
                .isEqualTo("legacy-document");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM document_versions WHERE id = ?", String.class, legacyDocument.versionId()))
                .isEqualTo("QUARANTINED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT content FROM document_chunks WHERE id = ?", String.class, legacyDocument.chunkId()))
                .isEqualTo("legacy chunk");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM processing_jobs WHERE id = ?", String.class, legacyDocument.processingJobId()))
                .isEqualTo("PENDING");
    }

    private void assertOwnershipSchema(JdbcTemplate jdbcTemplate) {
        for (String table : DOCUMENT_TABLES) {
            assertThat(ownerColumnCount(jdbcTemplate, table)).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT is_nullable
                    FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = ? AND column_name = 'owner_user_id'
                    """,
                    String.class,
                    table)).isEqualTo("NO");
        }
        for (String constraintName : List.of(
                "fk_documents_owner_user",
                "fk_document_versions_owner_user",
                "fk_document_chunks_owner_user",
                "fk_processing_jobs_owner_user",
                "uq_documents_id_owner",
                "uq_document_versions_id_owner",
                "fk_document_versions_document_owner",
                "fk_document_chunks_version_owner",
                "fk_processing_jobs_version_owner",
                "fk_documents_active_version_owner")) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint WHERE conname = ?", Long.class, constraintName))
                    .isEqualTo(1L);
        }
        for (String indexName : List.of(
                "idx_documents_owner_created",
                "idx_document_versions_owner_document",
                "idx_document_chunks_owner_version",
                "idx_processing_jobs_owner_status_available")) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                    Long.class,
                    indexName)).isEqualTo(1L);
        }
    }

    private void assertDocumentTypeSchema(JdbcTemplate jdbcTemplate) {
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = 'documents' AND column_name = 'document_type'
                """,
                String.class)).isEqualTo("NO");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'ck_documents_document_type'", Long.class))
                .isEqualTo(1L);
    }

    private void assertChunkSourceSchema(JdbcTemplate jdbcTemplate) {
        for (String columnName : List.of("source_type", "source_index", "source_label")) {
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT is_nullable
                    FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'document_chunks' AND column_name = ?
                    """,
                    String.class,
                    columnName)).isEqualTo("NO");
        }
        for (String constraintName : List.of(
                "ck_document_chunks_source_type", "ck_document_chunks_source_index")) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint WHERE conname = ?", Long.class, constraintName))
                    .isEqualTo(1L);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_document_chunks_source_type'",
                String.class)).contains("TEXT_CHUNK", "PAGE");
    }

    private void assertPdfFileTypeSchema(JdbcTemplate jdbcTemplate) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_document_versions_file_type'",
                String.class)).contains("TXT", "PDF");
    }

    private void assertFileCleanupJobSchema(JdbcTemplate jdbcTemplate) {
        for (String columnName : List.of(
                "storage_key", "status", "attempts", "available_at", "claim_version", "created_at", "updated_at")) {
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT is_nullable
                    FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'file_cleanup_jobs' AND column_name = ?
                    """,
                    String.class,
                    columnName)).isEqualTo("NO");
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_file_cleanup_jobs_status'",
                String.class)).contains("PENDING", "PROCESSING", "RETRY_WAIT", "COMPLETED", "FAILED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'uq_file_cleanup_jobs_storage_key'", Long.class))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'ix_file_cleanup_jobs_pending_available'",
                Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = 'public' AND indexname = 'ix_file_cleanup_jobs_processing_lease'",
                Long.class)).isEqualTo(1L);
    }

    private void assertProcessingProgressSchema(JdbcTemplate jdbcTemplate) {
        for (String columnName : List.of(
                "progress_stage", "completed_chunks", "total_chunks", "failure_code")) {
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT is_nullable
                    FROM information_schema.columns
                    WHERE table_schema = 'public' AND table_name = 'processing_jobs' AND column_name = ?
                    """,
                    String.class,
                    columnName)).isEqualTo("YES");
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_processing_jobs_progress_stage'",
                String.class)).contains(
                        "FILE_READING", "TEXT_EXTRACTION", "CHUNK_CREATION", "EMBEDDING", "SAVING", "COMPLETED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_processing_jobs_failure_code'",
                String.class)).contains(
                        "OLLAMA_UNAVAILABLE", "OLLAMA_MODEL_NOT_INSTALLED",
                        "OLLAMA_RUNTIME_FAILURE", "DOCUMENT_PROCESSING_FAILED");
    }

    private long ownerColumnCount(JdbcTemplate jdbcTemplate, String tableName) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ? AND column_name = 'owner_user_id'
                """,
                Long.class,
                tableName);
    }

    private long rowCount(JdbcTemplate jdbcTemplate, String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }

    private long successfulMigrationCount(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Long.class);
    }

    private record MigrationDatabase(DataSource dataSource, JdbcTemplate jdbcTemplate) {
    }

    private record LegacyDocument(Long documentId, Long versionId, Long chunkId, Long processingJobId) {
    }

    private record StoredChunk(
            String content,
            String sourceType,
            int sourceIndex,
            String sourceLabel,
            int embeddingDimensions) {
    }
}
