package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Runs the exact external OpenSQL Gate assertions first against the PostgreSQL reference environment. */
@Testcontainers
class PostgreSqlOpenSqlCompatibilityTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm_opensql_compatibility")
            .withUsername("prizm")
            .withPassword("prizm-test");

    @Test
    void validatesTheSharedOpenSqlCompatibilitySuiteOnPostgreSql() {
        String databaseName = createDatabase("normal");
        DataSource runtimeDataSource = dataSource(databaseName);
        DataSource flywayDataSource = dataSource(databaseName);

        assertThat(runtimeDataSource).isNotSameAs(flywayDataSource);
        OpenSqlCompatibilityAssertions.verify(runtimeDataSource, flywayDataSource);
    }

    @Test
    void rejectsExistingRuntimeDatabaseBeforeMigrationAndPreservesSentinelData() {
        String flywayDatabaseName = createDatabase("mismatch_flyway");
        String runtimeDatabaseName = createDatabase("mismatch_runtime");
        DataSource flywayDataSource = dataSource(flywayDatabaseName);
        DataSource runtimeDataSource = dataSource(runtimeDatabaseName);
        migrate(runtimeDataSource);

        JdbcTemplate runtimeJdbc = new JdbcTemplate(runtimeDataSource);
        SentinelFixture sentinel = createSentinel(runtimeJdbc);
        SentinelSnapshot before = snapshot(runtimeJdbc, sentinel);

        assertThatThrownBy(() -> OpenSqlCompatibilityAssertions.verify(runtimeDataSource, flywayDataSource))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("phase=target-preflight");

        assertThat(snapshot(runtimeJdbc, sentinel)).isEqualTo(before);
        assertThat(baseTableCount(new JdbcTemplate(flywayDataSource))).isZero();
    }

    @Test
    void rejectsDifferentEmptyRuntimeDatabaseWhenUuidMarkerIsNotVisible() {
        String flywayDatabaseName = createDatabase("marker_flyway");
        String runtimeDatabaseName = createDatabase("marker_runtime");
        DataSource flywayDataSource = dataSource(flywayDatabaseName);
        DataSource runtimeDataSource = dataSource(runtimeDatabaseName);

        assertThatThrownBy(() -> OpenSqlCompatibilityAssertions.verify(runtimeDataSource, flywayDataSource))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("phase=target-identity");

        assertThat(new JdbcTemplate(flywayDataSource).queryForObject(
                "SELECT COUNT(*) FROM users", Long.class)).isZero();
        assertThat(baseTableCount(new JdbcTemplate(runtimeDataSource))).isZero();
    }

    private static String createDatabase(String purpose) {
        String databaseName = "prizm_compat_" + purpose + "_"
                + UUID.randomUUID().toString().replace("-", "");
        new JdbcTemplate(dataSource("postgres")).execute("CREATE DATABASE " + databaseName);
        return databaseName;
    }

    private static DataSource dataSource(String databaseName) {
        String jdbcUrl = "jdbc:postgresql://" + postgres.getHost() + ":"
                + postgres.getMappedPort(5432) + "/" + databaseName;
        return new DriverManagerDataSource(jdbcUrl, postgres.getUsername(), postgres.getPassword());
    }

    private static void migrate(DataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .load()
                .migrate();
    }

    private static SentinelFixture createSentinel(JdbcTemplate jdbcTemplate) {
        String token = UUID.randomUUID().toString();
        String email = "sentinel-" + token + "@compatibility.invalid";
        Long userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users(email, password_hash, role, enabled)
                VALUES (?, repeat('s', 60), 'USER', TRUE)
                RETURNING id
                """,
                Long.class,
                email);
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO documents(owner_user_id, title, document_type)
                VALUES (?, ?, 'OTHER')
                RETURNING id
                """,
                Long.class,
                userId,
                "sentinel-" + token);
        Long versionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name,
                    stored_file_path, file_type, content_hash, status
                )
                VALUES (?, ?, 1, 'sentinel.txt', ?, 'TXT', repeat('s', 64), 'QUARANTINED')
                RETURNING id
                """,
                Long.class,
                userId,
                documentId,
                "sentinel/" + token + ".txt");
        jdbcTemplate.update(
                "UPDATE documents SET active_version_id = ? WHERE id = ?",
                versionId,
                documentId);
        Long processingJobId = jdbcTemplate.queryForObject(
                """
                INSERT INTO processing_jobs(owner_user_id, document_version_id, job_type, status)
                VALUES (?, ?, 'INDEXING', 'PENDING')
                RETURNING id
                """,
                Long.class,
                userId,
                versionId);
        Long cleanupJobId = jdbcTemplate.queryForObject(
                """
                INSERT INTO file_cleanup_jobs(storage_key, status)
                VALUES (?, 'PENDING')
                RETURNING id
                """,
                Long.class,
                "sentinel/" + token + ".txt");
        return new SentinelFixture(userId, documentId, versionId, processingJobId, cleanupJobId);
    }

    private static SentinelSnapshot snapshot(JdbcTemplate jdbcTemplate, SentinelFixture sentinel) {
        return new SentinelSnapshot(
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class),
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM documents", Long.class),
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_versions", Long.class),
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_chunks", Long.class),
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processing_jobs", Long.class),
                jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_cleanup_jobs", Long.class),
                jdbcTemplate.queryForObject("SELECT email FROM users WHERE id = ?", String.class, sentinel.userId()),
                jdbcTemplate.queryForObject(
                        "SELECT active_version_id FROM documents WHERE id = ?", Long.class, sentinel.documentId()),
                jdbcTemplate.queryForObject(
                        "SELECT status FROM document_versions WHERE id = ?", String.class, sentinel.versionId()),
                jdbcTemplate.queryForObject(
                        "SELECT status FROM processing_jobs WHERE id = ?", String.class, sentinel.processingJobId()),
                jdbcTemplate.queryForObject(
                        "SELECT status FROM file_cleanup_jobs WHERE id = ?", String.class, sentinel.cleanupJobId()));
    }

    private static long baseTableCount(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_catalog.pg_class relation
                JOIN pg_catalog.pg_namespace namespace ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = current_schema()
                  AND relation.relkind IN ('r', 'p')
                """,
                Long.class);
    }

    private record SentinelFixture(
            long userId,
            long documentId,
            long versionId,
            long processingJobId,
            long cleanupJobId) {
    }

    private record SentinelSnapshot(
            long users,
            long documents,
            long versions,
            long chunks,
            long processingJobs,
            long cleanupJobs,
            String email,
            long activeVersionId,
            String versionStatus,
            String processingStatus,
            String cleanupStatus) {
    }
}
