package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** P1 migration, constraint, and SKIP LOCKED claim contract against a fresh PostgreSQL database. */
@Testcontainers
class DocumentChangeLogMigrationDatabaseIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm_changelog_admin")
            .withUsername("prizm")
            .withPassword("prizm-test");

    @Test
    void migratesChangeLogConstraintsAndSkipsLockedDispatchableRows() throws Exception {
        String databaseName = "prizm_changelog_" + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(dataSource("postgres")).execute("CREATE DATABASE " + databaseName);
        DataSource dataSource = dataSource(databaseName);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load();
        MigrateResult firstMigration = flyway.migrate();

        assertThat(firstMigration.migrationsExecuted).isEqualTo(14);
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("14");
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(tableCount(jdbcTemplate, "document_change_logs")).isEqualTo(1L);
        assertThat(nullableProcessingJobColumn(jdbcTemplate)).isEqualTo("YES");
        assertConstraints(jdbcTemplate, "u", "uq_document_change_logs_event_key",
                "uq_document_change_logs_version_event", "uq_document_change_logs_processing_job",
                "uq_processing_jobs_id_owner_version");
        assertConstraints(jdbcTemplate, "f", "fk_document_change_logs_version_owner",
                "fk_document_change_logs_processing_job_owner_version");
        assertThat(constraintDefinition(jdbcTemplate, "ck_document_change_logs_event_type"))
                .contains("DOCUMENT_VERSION_CREATED");
        assertThat(constraintDefinition(jdbcTemplate, "ck_document_change_logs_dispatch_status"))
                .contains("PENDING", "RETRY_WAIT", "DISPATCHED", "FAILED");
        assertThat(constraintDefinition(jdbcTemplate, "ck_document_change_logs_retry_count"))
                .contains("0", "3");
        assertThat(indexCount(jdbcTemplate, "ix_document_change_logs_dispatch_claim")).isEqualTo(1L);

        long ownerId = insertUser(jdbcTemplate, "owner");
        long versionOne = insertVersion(jdbcTemplate, ownerId, "one");
        long versionTwo = insertVersion(jdbcTemplate, ownerId, "two");
        long firstChangeLogId = insertPendingChangeLog(jdbcTemplate, ownerId, versionOne,
                "DOCUMENT_VERSION_CREATED:" + versionOne);
        long secondChangeLogId = insertPendingChangeLog(jdbcTemplate, ownerId, versionTwo,
                "DOCUMENT_VERSION_CREATED:" + versionTwo);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_change_logs WHERE processing_job_id IS NULL", Long.class))
                .isEqualTo(2L);

        assertThatThrownBy(() -> insertPendingChangeLog(jdbcTemplate, ownerId, versionTwo,
                "DOCUMENT_VERSION_CREATED:" + versionOne))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> insertPendingChangeLog(jdbcTemplate, ownerId, versionOne,
                "DOCUMENT_VERSION_CREATED:duplicate"))
                .isInstanceOf(DataIntegrityViolationException.class);

        long firstJobId = insertIndexingJob(jdbcTemplate, ownerId, versionOne);
        long secondJobId = insertIndexingJob(jdbcTemplate, ownerId, versionTwo);
        assertThatThrownBy(() -> insertIndexingJob(jdbcTemplate, ownerId, versionOne))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbcTemplate.update(
                "UPDATE document_change_logs SET processing_job_id = ? WHERE id = ?",
                firstJobId,
                firstChangeLogId)).isEqualTo(1);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE document_change_logs SET processing_job_id = ? WHERE id = ?",
                secondJobId,
                firstChangeLogId))
                .isInstanceOf(DataIntegrityViolationException.class);

        try (Connection firstConnection = dataSource.getConnection();
                Connection secondConnection = dataSource.getConnection()) {
            firstConnection.setAutoCommit(false);
            secondConnection.setAutoCommit(false);

            long firstClaim = claimNextDispatchable(firstConnection);
            long secondClaim = claimNextDispatchable(secondConnection);

            assertThat(firstClaim).isIn(firstChangeLogId, secondChangeLogId);
            assertThat(secondClaim).isIn(firstChangeLogId, secondChangeLogId);
            assertThat(secondClaim).isNotEqualTo(firstClaim);
            firstConnection.rollback();
            secondConnection.rollback();
        }
    }

    private DataSource dataSource(String databaseName) {
        String jdbcUrl = "jdbc:postgresql://" + postgres.getHost() + ":"
                + postgres.getMappedPort(5432) + "/" + databaseName;
        return new DriverManagerDataSource(jdbcUrl, postgres.getUsername(), postgres.getPassword());
    }

    private long tableCount(JdbcTemplate jdbcTemplate, String tableName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = current_schema() AND table_name = ?",
                Long.class,
                tableName);
    }

    private String nullableProcessingJobColumn(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject(
                """
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'document_change_logs'
                  AND column_name = 'processing_job_id'
                """,
                String.class);
    }

    private void assertConstraints(JdbcTemplate jdbcTemplate, String type, String... names) {
        for (String name : names) {
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM pg_constraint constraint_definition
                    JOIN pg_namespace namespace ON namespace.oid = constraint_definition.connamespace
                    WHERE namespace.nspname = current_schema()
                      AND constraint_definition.conname = ?
                      AND constraint_definition.contype::text = ?
                    """,
                    Long.class,
                    name,
                    type)).isEqualTo(1L);
        }
    }

    private String constraintDefinition(JdbcTemplate jdbcTemplate, String constraintName) {
        return jdbcTemplate.queryForObject(
                """
                SELECT pg_get_constraintdef(constraint_definition.oid)
                FROM pg_constraint constraint_definition
                JOIN pg_namespace namespace ON namespace.oid = constraint_definition.connamespace
                WHERE namespace.nspname = current_schema()
                  AND constraint_definition.conname = ?
                """,
                String.class,
                constraintName);
    }

    private long indexCount(JdbcTemplate jdbcTemplate, String indexName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = current_schema() AND indexname = ?",
                Long.class,
                indexName);
    }

    private long insertUser(JdbcTemplate jdbcTemplate, String suffix) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO users(email, password_hash, role, enabled)
                VALUES (?, repeat('p', 60), 'USER', TRUE)
                RETURNING id
                """,
                Long.class,
                suffix + "-" + UUID.randomUUID() + "@example.com");
    }

    private long insertVersion(JdbcTemplate jdbcTemplate, long ownerUserId, String suffix) {
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO documents(owner_user_id, title, document_type)
                VALUES (?, ?, 'OTHER')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                "ChangeLog " + suffix);
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path,
                    file_type, content_hash, status)
                VALUES (?, ?, 1, ?, ?, 'TXT', repeat('a', 64), 'QUARANTINED')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                documentId,
                suffix + ".txt",
                "test/" + suffix + ".txt");
    }

    private long insertPendingChangeLog(
            JdbcTemplate jdbcTemplate, long ownerUserId, long versionId, String eventKey) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO document_change_logs(
                    owner_user_id, document_version_id, event_type, event_key, dispatch_status)
                VALUES (?, ?, 'DOCUMENT_VERSION_CREATED', ?, 'PENDING')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                versionId,
                eventKey);
    }

    private long insertIndexingJob(JdbcTemplate jdbcTemplate, long ownerUserId, long versionId) {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO processing_jobs(owner_user_id, document_version_id, job_type, status)
                VALUES (?, ?, 'INDEXING', 'PENDING')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                versionId);
    }

    private long claimNextDispatchable(Connection connection) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT id
                FROM document_change_logs
                WHERE dispatch_status = 'PENDING'
                   OR (dispatch_status = 'RETRY_WAIT' AND next_retry_at <= now())
                ORDER BY created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
                """)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getLong("id");
            }
        }
    }
}
