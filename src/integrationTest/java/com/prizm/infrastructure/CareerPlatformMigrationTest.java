package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** V6까지 저장된 상태와 역할이 V7에서 데이터 손실 없이 중립 모델로 전환되는지 검증한다. */
@Testcontainers
class CareerPlatformMigrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm_migration")
            .withUsername("prizm")
            .withPassword("prizm-test");

    @Test
    void migratesV6StatesAndRoleWithoutLosingDocuments() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .target(MigrationVersion.fromVersion("6"))
                .load()
                .migrate();

        JdbcTemplate jdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        Long documentId = jdbcTemplate.queryForObject(
                "INSERT INTO documents(title) VALUES ('기존 프로젝트 보고서') RETURNING id",
                Long.class);
        Long legacyVersionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_versions(
                    document_id, version_no, original_file_name, stored_file_path, file_type, content_hash, status
                )
                VALUES (?, 1, 'project-v1.txt', 'documents/1/1/project-v1.txt', 'TXT', repeat('a', 64), 'APPROVED')
                RETURNING id
                """,
                Long.class,
                documentId);
        Long quarantinedVersionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_versions(
                    document_id, version_no, original_file_name, stored_file_path, file_type, content_hash, status
                )
                VALUES (?, 2, 'project-v2.txt', 'documents/1/2/project-v2.txt', 'TXT', repeat('b', 64), 'QUARANTINED')
                RETURNING id
                """,
                Long.class,
                documentId);
        Long retryJobId = jdbcTemplate.queryForObject(
                """
                INSERT INTO processing_jobs(
                    document_version_id, job_type, status, retry_count, next_retry_at
                )
                VALUES (?, 'INDEXING', 'PENDING', 1, now())
                RETURNING id
                """,
                Long.class,
                legacyVersionId);
        Long userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users(email, password_hash, role, enabled)
                VALUES ('legacy-operator@prizm.local', repeat('x', 60), 'ADMIN', true)
                RETURNING id
                """,
                Long.class);

        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .load()
                .migrate();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM document_versions WHERE id = ?", String.class, legacyVersionId))
                .isEqualTo("QUARANTINED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM document_versions WHERE id = ?", String.class, quarantinedVersionId))
                .isEqualTo("QUARANTINED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM processing_jobs WHERE id = ?", String.class, retryJobId))
                .isEqualTo("RETRY_WAIT");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT role FROM users WHERE id = ?", String.class, userId))
                .isEqualTo("SYSTEM_ADMIN");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processing_jobs WHERE document_version_id = ? AND status = 'PENDING'",
                Long.class,
                quarantinedVersionId))
                .isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM documents", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_versions", Long.class)).isEqualTo(2L);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Long.class))
                .isEqualTo(7L);
    }
}
