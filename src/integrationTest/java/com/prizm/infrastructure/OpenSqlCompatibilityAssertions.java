package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.cleanup.repository.FileCleanupJobRepository;
import com.prizm.cleanup.service.ClaimedFileCleanupJob;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.ingestion.repository.ProcessingJobClaimRepository;
import com.prizm.ingestion.service.ClaimedProcessingJob;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.repository.VectorSearchResult;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Assertions shared by the PostgreSQL reference test and the opt-in external OpenSQL test.
 *
 * <p>The suite uses the production repository SQL for search and job claims. It never starts schedulers,
 * file deletion, Ollama, or the Spring application context.</p>
 */
final class OpenSqlCompatibilityAssertions {

    private static final int EXPECTED_MIGRATION_COUNT = 16;
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);
    private static final List<String> DOMAIN_TABLES = List.of(
            "users", "documents", "document_versions", "document_chunks",
            "processing_jobs", "file_cleanup_jobs", "document_change_logs", "document_tags");
    private static final Pattern MIGRATION_FILE_PATTERN = Pattern.compile("(?i)\\bV(1[0-6]|[1-9])(?:__|\\b)");
    private static final Pattern MIGRATION_VERSION_PATTERN = Pattern.compile("(?i)\\bversion\\s+['\"]?(1[0-6]|[1-9])\\b");

    private OpenSqlCompatibilityAssertions() {
    }

    static void verify(DataSource runtimeDataSource, DataSource flywayDataSource) {
        verify(runtimeDataSource, flywayDataSource, () -> { });
    }

    static void verifyWithOpenSqlRuntimeGrants(DataSource runtimeDataSource, DataSource flywayDataSource) {
        verify(runtimeDataSource, flywayDataSource, () ->
                OpenSqlRuntimePrivilegePreparation.prepare(runtimeDataSource, flywayDataSource));
    }

    private static void verify(
            DataSource runtimeDataSource,
            DataSource flywayDataSource,
            Runnable postMigrationPreparation) {
        JdbcTemplate runtimeJdbc = new JdbcTemplate(runtimeDataSource);
        JdbcTemplate flywayJdbc = new JdbcTemplate(flywayDataSource);

        verifyPreMigrationTargets(flywayJdbc, runtimeJdbc);
        verifyMigrations(flywayDataSource, flywayJdbc);
        verifyPhase("runtime-privileges", "OpenSQL test-only runtime grants", postMigrationPreparation);
        VerificationMarker marker = verifySharedTarget(flywayJdbc, runtimeJdbc);
        String runToken = UUID.randomUUID().toString();

        try {
            verifyPhase("pgvector", "vector(1024), CAST, vector_dims and <=>", () ->
                    inTransaction(runtimeDataSource,
                            () -> PgVectorSmokeAssertions.verifyExactCosineSearch(runtimeJdbc)));
            verifyPhase("search", "VectorSearchRepository exact cosine SQL and row mapping", () ->
                    verifyVectorSearch(runtimeJdbc, flywayJdbc, runToken));
            verifyPhase("indexing-worker", "ProcessingJobClaimRepository claim, lease and SKIP LOCKED SQL", () ->
                    verifyProcessingJobSql(runtimeDataSource, runtimeJdbc, flywayJdbc, runToken));
            verifyPhase("change-log", "V14 constraints, owner isolation, SKIP LOCKED and ProcessingJob ON CONFLICT", () ->
                    verifyChangeLogSql(runtimeDataSource, runtimeJdbc, flywayJdbc, runToken));
            verifyPhase("cleanup-worker", "FileCleanupJobRepository registration, claim, recovery and fencing SQL", () ->
                    verifyCleanupJobSql(runtimeDataSource, runtimeJdbc, flywayJdbc, runToken));
        }
        finally {
            verifyPhase("target-cleanup", "run-scoped verification marker cleanup", () -> {
                deleteMarker(flywayJdbc, marker);
                assertDomainRowsEmpty(flywayJdbc);
                assertDomainRowsEmpty(runtimeJdbc);
            });
        }
    }

    private static void verifyPreMigrationTargets(JdbcTemplate flywayJdbc, JdbcTemplate runtimeJdbc) {
        verifyPhase("target-preflight", "independent empty runtime and Flyway schemas", () -> {
            assertNoBaseTables(flywayJdbc);
            assertNoBaseTables(runtimeJdbc);
        });
    }

    private static void assertNoBaseTables(JdbcTemplate jdbcTemplate) {
        String schema = jdbcTemplate.queryForObject("SELECT current_schema()", String.class);
        assertThat(schema).isNotBlank();
        Long existingTables = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM pg_catalog.pg_class relation
                JOIN pg_catalog.pg_namespace namespace ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = current_schema()
                  AND relation.relkind IN ('r', 'p')
                """,
                Long.class);
        assertThat(existingTables).isZero();
    }

    private static void verifyMigrations(DataSource flywayDataSource, JdbcTemplate flywayJdbc) {
        PreV14ProcessingFixture preV14Fixture = null;
        try {
            Flyway v13Flyway = Flyway.configure()
                    .dataSource(flywayDataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .cleanDisabled(true)
                    .target("13")
                    .load();

            MigrateResult v13Migration = v13Flyway.migrate();
            assertThat(v13Migration.migrationsExecuted).isEqualTo(13);
            assertSuccessfulMigrationVersions(flywayJdbc, 13);
            assertThat(v13Flyway.info().current()).isNotNull();
            assertThat(v13Flyway.info().current().getVersion().getVersion()).isEqualTo("13");

            preV14Fixture = createPreV14ProcessingFixture(flywayJdbc);

            Flyway latestFlyway = Flyway.configure()
                    .dataSource(flywayDataSource)
                    .locations("classpath:db/migration")
                    .baselineOnMigrate(false)
                    .cleanDisabled(true)
                    .load();
            MigrateResult latestMigration = latestFlyway.migrate();
            assertThat(latestMigration.migrationsExecuted).isEqualTo(3);
            assertSuccessfulMigrationVersions(flywayJdbc, EXPECTED_MIGRATION_COUNT);
            assertThat(latestFlyway.info().current()).isNotNull();
            assertThat(latestFlyway.info().current().getVersion().getVersion()).isEqualTo("16");
            assertPreV14ProcessingFixturePreserved(flywayJdbc, preV14Fixture);

            MigrateResult secondMigration = latestFlyway.migrate();
            assertThat(secondMigration.migrationsExecuted).isZero();
            assertThat(latestFlyway.info().pending()).isEmpty();
            assertSchema(flywayJdbc);
        }
        catch (AssertionError failure) {
            throw migrationFailure(failure, flywayJdbc);
        }
        catch (RuntimeException failure) {
            throw migrationFailure(failure, flywayJdbc);
        }
        finally {
            if (preV14Fixture != null) {
                deletePreV14ProcessingFixture(flywayJdbc, preV14Fixture);
            }
        }
    }

    private static void assertSuccessfulMigrationVersions(JdbcTemplate flywayJdbc, int expectedMigrationCount) {
        List<String> successfulVersions = flywayJdbc.queryForList(
                """
                SELECT version
                FROM flyway_schema_history
                WHERE success AND version IS NOT NULL
                ORDER BY installed_rank
                """,
                String.class);
        assertThat(successfulVersions)
                .containsExactlyElementsOf(IntStream.rangeClosed(1, expectedMigrationCount)
                        .mapToObj(Integer::toString)
                        .toList());
    }

    private static PreV14ProcessingFixture createPreV14ProcessingFixture(JdbcTemplate jdbcTemplate) {
        String token = UUID.randomUUID().toString();
        Long ownerUserId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users(email, password_hash, role, enabled)
                VALUES (?, repeat('v', 60), 'USER', TRUE)
                RETURNING id
                """,
                Long.class,
                "pre-v14-" + token + "@compatibility.invalid");
        Long documentId = jdbcTemplate.queryForObject(
                """
                INSERT INTO documents(owner_user_id, title, document_type)
                VALUES (?, ?, 'OTHER')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                "Pre-V14 ProcessingJob " + token);
        Long versionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path,
                    file_type, content_hash, status
                )
                VALUES (?, ?, 1, 'pre-v14.txt', ?, 'TXT', repeat('v', 64), 'QUARANTINED')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                documentId,
                "compatibility/pre-v14/" + token + ".txt");
        Long processingJobId = jdbcTemplate.queryForObject(
                """
                INSERT INTO processing_jobs(owner_user_id, document_version_id, job_type, status)
                VALUES (?, ?, 'INDEXING', 'PENDING')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                versionId);
        return new PreV14ProcessingFixture(ownerUserId, documentId, versionId, processingJobId);
    }

    private static void assertPreV14ProcessingFixturePreserved(
            JdbcTemplate jdbcTemplate, PreV14ProcessingFixture fixture) {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processing_jobs WHERE id = ? AND owner_user_id = ? AND document_version_id = ? "
                        + "AND job_type = 'INDEXING' AND status = 'PENDING'",
                Long.class,
                fixture.processingJobId(),
                fixture.ownerUserId(),
                fixture.documentVersionId())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_change_logs WHERE owner_user_id = ? AND document_version_id = ?",
                Long.class,
                fixture.ownerUserId(),
                fixture.documentVersionId())).isZero();
    }

    private static void deletePreV14ProcessingFixture(JdbcTemplate jdbcTemplate, PreV14ProcessingFixture fixture) {
        jdbcTemplate.update("DELETE FROM processing_jobs WHERE id = ?", fixture.processingJobId());
        jdbcTemplate.update("DELETE FROM document_versions WHERE id = ?", fixture.documentVersionId());
        jdbcTemplate.update("DELETE FROM documents WHERE id = ?", fixture.documentId());
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", fixture.ownerUserId());
    }

    private static VerificationMarker verifySharedTarget(
            JdbcTemplate flywayJdbc, JdbcTemplate runtimeJdbc) {
        VerificationMarker marker = null;
        try {
            assertDomainRowsEmpty(flywayJdbc);
            marker = createMarker(flywayJdbc);
            assertMarkerVisible(runtimeJdbc, marker);

            String flywaySchema = flywayJdbc.queryForObject("SELECT current_schema()", String.class);
            String runtimeSchema = runtimeJdbc.queryForObject("SELECT current_schema()", String.class);
            assertThat(runtimeSchema).isEqualTo(flywaySchema);
            assertOnlyMarkerExists(flywayJdbc, marker);
            assertOnlyMarkerExists(runtimeJdbc, marker);
            assertSchema(runtimeJdbc);
            return marker;
        }
        catch (AssertionError failure) {
            throw targetValidationFailure(failure, flywayJdbc, marker);
        }
        catch (RuntimeException failure) {
            throw targetValidationFailure(failure, flywayJdbc, marker);
        }
    }

    private static VerificationMarker createMarker(JdbcTemplate flywayJdbc) {
        String markerToken = UUID.randomUUID().toString();
        String markerEmail = "opensql-marker-" + markerToken + "@compatibility.invalid";
        Long markerId = flywayJdbc.queryForObject(
                """
                INSERT INTO users(email, password_hash, role, enabled)
                VALUES (?, repeat('m', 60), 'USER', FALSE)
                RETURNING id
                """,
                Long.class,
                markerEmail);
        return new VerificationMarker(markerId, markerEmail);
    }

    private static void assertMarkerVisible(JdbcTemplate runtimeJdbc, VerificationMarker marker) {
        assertThat(runtimeJdbc.queryForObject(
                "SELECT COUNT(*) FROM users WHERE id = ? AND email = ? AND NOT enabled",
                Long.class,
                marker.userId(),
                marker.email())).isEqualTo(1L);
    }

    private static void assertOnlyMarkerExists(JdbcTemplate jdbcTemplate, VerificationMarker marker) {
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(1L);
        assertMarkerVisible(jdbcTemplate, marker);
        for (String table : DOMAIN_TABLES) {
            if (!"users".equals(table)) {
                assertThat(countRows(jdbcTemplate, table)).isZero();
            }
        }
    }

    private static void assertDomainRowsEmpty(JdbcTemplate jdbcTemplate) {
        for (String table : DOMAIN_TABLES) {
            assertThat(countRows(jdbcTemplate, table)).isZero();
        }
    }

    private static long countRows(JdbcTemplate jdbcTemplate, String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private static void deleteMarker(JdbcTemplate flywayJdbc, VerificationMarker marker) {
        assertThat(flywayJdbc.update(
                "DELETE FROM users WHERE id = ? AND email = ?",
                marker.userId(),
                marker.email())).isEqualTo(1);
    }

    private static CompatibilityVerificationFailure targetValidationFailure(
            Throwable failure,
            JdbcTemplate flywayJdbc,
            VerificationMarker marker) {
        CompatibilityVerificationFailure reported = safeFailure(
                "target-identity", "not-applicable", "runtime/Flyway UUID marker visibility", failure);
        if (marker == null) {
            return reported;
        }
        try {
            deleteMarker(flywayJdbc, marker);
        }
        catch (AssertionError cleanupFailure) {
            reported.addSuppressed(safeFailure(
                    "target-cleanup", "not-applicable", "run-scoped verification marker cleanup", cleanupFailure));
        }
        catch (RuntimeException cleanupFailure) {
            reported.addSuppressed(safeFailure(
                    "target-cleanup", "not-applicable", "run-scoped verification marker cleanup", cleanupFailure));
        }
        return reported;
    }

    private static void assertSchema(JdbcTemplate jdbcTemplate) {
        for (String table : List.of(
                "users", "documents", "document_versions", "document_chunks",
                "processing_jobs", "file_cleanup_jobs", "document_change_logs", "tags", "document_tags")) {
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.tables
                    WHERE table_schema = current_schema() AND table_name = ?
                    """,
                    Long.class,
                    table)).isEqualTo(1L);
        }

        for (String column : List.of(
                "lease_expires_at", "claim_version", "completed_at", "last_error_code")) {
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'file_cleanup_jobs'
                      AND column_name = ?
                    """,
                    Long.class,
                    column)).isEqualTo(1L);
        }

        for (String column : List.of(
                "progress_stage", "completed_chunks", "total_chunks", "failure_code")) {
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM information_schema.columns
                    WHERE table_schema = current_schema()
                      AND table_name = 'processing_jobs'
                      AND column_name = ?
                    """,
                    Long.class,
                    column)).isEqualTo(1L);
        }

        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT format_type(attribute.atttypid, attribute.atttypmod)
                FROM pg_attribute attribute
                JOIN pg_class relation ON relation.oid = attribute.attrelid
                JOIN pg_namespace namespace ON namespace.oid = relation.relnamespace
                WHERE namespace.nspname = current_schema()
                  AND relation.relname = 'document_chunks'
                  AND attribute.attname = 'embedding'
                  AND NOT attribute.attisdropped
                """,
                String.class)).isEqualTo("vector(1024)");

        assertConstraints(jdbcTemplate, "f", List.of(
                "fk_documents_owner_user",
                "fk_document_versions_document_owner",
                "fk_document_chunks_version_owner",
                "fk_processing_jobs_version_owner",
                "fk_documents_active_version_owner",
                "fk_document_change_logs_version_owner",
                "fk_document_change_logs_processing_job_owner_version",
                "fk_tags_owner_user",
                "fk_document_tags_document_owner",
                "fk_document_tags_tag",
                "fk_document_tags_owner_user"));
        assertConstraints(jdbcTemplate, "u", List.of(
                "uq_processing_jobs_id_owner_version",
                "uq_document_change_logs_event_key",
                "uq_document_change_logs_version_event",
                "uq_document_change_logs_processing_job"));
        assertConstraints(jdbcTemplate, "c", List.of(
                "ck_document_versions_status",
                "ck_processing_jobs_status",
                "ck_processing_jobs_claim_version",
                "ck_processing_jobs_progress_stage",
                "ck_processing_jobs_chunk_progress",
                "ck_processing_jobs_failure_code",
                "ck_documents_document_type",
                "ck_document_chunks_source_type",
                "ck_document_chunks_source_index",
                "ck_file_cleanup_jobs_status",
                "ck_file_cleanup_jobs_attempts",
                "ck_document_change_logs_event_type",
                "ck_document_change_logs_dispatch_status",
                "ck_document_change_logs_retry_count",
                "ck_tags_source",
                "ck_tags_owner_scope"));

        for (String index : List.of(
                "ix_processing_jobs_claim",
                "ix_processing_jobs_status_retry_lease",
                "idx_processing_jobs_owner_status_available",
                "ix_file_cleanup_jobs_pending_available",
                "ix_file_cleanup_jobs_processing_lease",
                "ix_document_change_logs_dispatch_claim",
                "ix_document_change_logs_owner_version",
                "uq_tags_system_normalized_name",
                "uq_tags_user_owner_normalized_name",
                "ix_tags_owner_normalized_name",
                "ix_document_tags_owner_tag")) {
            assertThat(jdbcTemplate.queryForObject(
                    """
                    SELECT COUNT(*)
                    FROM pg_indexes
                    WHERE schemaname = current_schema() AND indexname = ?
                    """,
                    Long.class,
                    index)).isEqualTo(1L);
        }

        assertThat(constraintDefinition(jdbcTemplate, "ck_file_cleanup_jobs_status"))
                .contains("PENDING", "PROCESSING", "RETRY_WAIT", "COMPLETED", "FAILED");
        assertThat(constraintDefinition(jdbcTemplate, "ck_document_change_logs_event_type"))
                .contains("DOCUMENT_VERSION_CREATED");
        assertThat(constraintDefinition(jdbcTemplate, "ck_document_change_logs_dispatch_status"))
                .contains("PENDING", "RETRY_WAIT", "DISPATCHED", "FAILED");
        assertThat(constraintDefinition(jdbcTemplate, "ck_document_change_logs_retry_count"))
                .contains("0", "3");
        assertThat(jdbcTemplate.queryForObject(
                """
                SELECT is_nullable
                FROM information_schema.columns
                WHERE table_schema = current_schema()
                  AND table_name = 'document_change_logs'
                  AND column_name = 'processing_job_id'
                """,
                String.class)).isEqualTo("YES");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM tags WHERE source = 'SYSTEM' AND owner_user_id IS NULL",
                Long.class)).isEqualTo(11L);
    }

    private static void assertConstraints(JdbcTemplate jdbcTemplate, String type, List<String> names) {
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

    private static String constraintDefinition(JdbcTemplate jdbcTemplate, String constraintName) {
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

    private static void verifyVectorSearch(
            JdbcTemplate jdbcTemplate, JdbcTemplate cleanupJdbc, String runToken) {
        try (FixtureScope fixtures = new FixtureScope(cleanupJdbc, runToken + "-search")) {
            VectorSearchRepository repository = new VectorSearchRepository(jdbcTemplate);
            long owner = createUser(jdbcTemplate, fixtures);
            long otherOwner = createUser(jdbcTemplate, fixtures);

            DocumentFixture current = createDocumentVersion(
                    jdbcTemplate, fixtures, owner, "Current evidence", "ACTIVE", 1);
            List<float[]> orderedVectors = List.of(
                    vector(1.0f, 0.0f),
                    vector(4.0f, 3.0f),
                    vector(3.0f, 4.0f),
                    vector(2.0f, 4.6f),
                    vector(1.0f, 4.9f),
                    vector(0.0f, 1.0f));
            List<String> expectedHits = new ArrayList<>();
            for (int index = 0; index < orderedVectors.size(); index++) {
                boolean page = index == 0;
                String content = fixtures.tag("search-hit-" + (index + 1));
                expectedHits.add(content);
                insertChunk(
                        jdbcTemplate,
                        fixtures,
                        owner,
                        current.versionId(),
                        index + 1,
                        content,
                        orderedVectors.get(index),
                        page ? "PAGE" : "TEXT_CHUNK",
                        page ? 7 : index + 1,
                        page ? "7페이지" : "텍스트 구간 " + (index + 1),
                        page ? 7 : null);
            }

            String foreignContent = fixtures.tag("foreign-owner");
            DocumentFixture foreign = createDocumentVersion(
                    jdbcTemplate, fixtures, otherOwner, "Foreign evidence", "ACTIVE", 1);
            insertChunk(jdbcTemplate, fixtures, otherOwner, foreign.versionId(), 1, foreignContent, vector(1, 0),
                    "TEXT_CHUNK", 1, "텍스트 구간 1", null);

            String oldContent = fixtures.tag("old-version");
            DocumentFixture old = createDocumentVersion(
                    jdbcTemplate, fixtures, owner, "Old version evidence", "ACTIVE", 1);
            insertChunk(jdbcTemplate, fixtures, owner, old.versionId(), 1, oldContent, vector(1, 0),
                    "TEXT_CHUNK", 1, "텍스트 구간 1", null);
            long replacementVersion = insertVersion(
                    jdbcTemplate, fixtures, owner, old.documentId(), 2, "ACTIVE");
            jdbcTemplate.update(
                    "UPDATE documents SET active_version_id = ? WHERE id = ?",
                    replacementVersion,
                    old.documentId());

            String processingContent = fixtures.tag("processing-version");
            DocumentFixture processing = createDocumentVersion(
                    jdbcTemplate, fixtures, owner, "Processing evidence", "PROCESSING", 1);
            insertChunk(jdbcTemplate, fixtures, owner, processing.versionId(), 1, processingContent, vector(1, 0),
                    "TEXT_CHUNK", 1, "텍스트 구간 1", null);

            float[] query = vector(1.0f, 0.0f);
            VectorSearchResult nearest = repository.findNearest(owner, query).orElseThrow();
            List<VectorSearchResult> evidence = repository.findCareerEvidence(owner, query);

            assertThat(nearest.documentId()).isEqualTo(current.documentId());
            assertThat(nearest.documentVersionId()).isEqualTo(current.versionId());
            assertThat(nearest.content()).isEqualTo(expectedHits.get(0));
            assertThat(nearest.sourceType()).isEqualTo(ChunkSourceType.PAGE);
            assertThat(nearest.sourceIndex()).isEqualTo(7);
            assertThat(nearest.sourceLabel()).isEqualTo("7페이지");
            assertThat(nearest.pageNo()).isEqualTo(7);
            assertThat(nearest.distance()).isZero();

            assertThat(evidence).hasSize(5);
            assertThat(evidence).extracting(VectorSearchResult::documentId).containsOnly(current.documentId());
            assertThat(evidence).extracting(VectorSearchResult::documentVersionId).containsOnly(current.versionId());
            assertThat(evidence).extracting(VectorSearchResult::content)
                    .containsExactlyElementsOf(expectedHits.subList(0, 5));
            assertThat(evidence).extracting(VectorSearchResult::distance).isSorted();
            assertThat(evidence.get(1).sourceType()).isEqualTo(ChunkSourceType.TEXT_CHUNK);
            assertThat(evidence.get(1).sourceIndex()).isEqualTo(2);
            assertThat(evidence.get(1).sourceLabel()).isEqualTo("텍스트 구간 2");
            assertThat(evidence).noneMatch(result ->
                    List.of(foreignContent, oldContent, processingContent).contains(result.content()));
        }
    }

    private static void verifyProcessingJobSql(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            JdbcTemplate cleanupJdbc,
            String runToken) {
        ProcessingJobClaimRepository repository = new ProcessingJobClaimRepository(jdbcTemplate);
        try (FixtureScope fixtures = new FixtureScope(cleanupJdbc, runToken + "-indexing-claim")) {
            long owner = createUser(jdbcTemplate, fixtures);
            Instant now = databaseNow(jdbcTemplate);
            ProcessingFixture pending = createProcessingJob(
                    jdbcTemplate, fixtures, owner, "PENDING", null, now.minusSeconds(20));
            ProcessingFixture retry = createProcessingJob(
                    jdbcTemplate, fixtures, owner, "RETRY_WAIT", now.minusSeconds(10), now.minusSeconds(10));

            Instant beforeClaim = databaseNow(jdbcTemplate);
            ClaimedProcessingJob pendingClaim = repository.claimNext(LEASE_DURATION).orElseThrow();
            assertThat(pendingClaim.processingJobId()).isEqualTo(pending.jobId());
            assertThat(pendingClaim.claimVersion()).isEqualTo(1L);
            assertThat(pendingClaim.leaseExpiresAt()).isAfter(beforeClaim);
            assertThat(jobStatus(jdbcTemplate, pending.jobId())).isEqualTo("PROCESSING");

            Instant renewedLease = repository.renewLease(
                    pendingClaim.processingJobId(), pendingClaim.claimVersion(), Duration.ofSeconds(60)).orElseThrow();
            assertThat(renewedLease).isAfter(pendingClaim.leaseExpiresAt());
            ProcessingState renewedState = processingState(jdbcTemplate, pending.jobId());
            assertThat(repository.renewLease(
                    pendingClaim.processingJobId(), pendingClaim.claimVersion() - 1,
                    Duration.ofSeconds(90))).isEmpty();
            assertThat(processingState(jdbcTemplate, pending.jobId())).isEqualTo(renewedState);

            ClaimedProcessingJob retryClaim = repository.claimNext(LEASE_DURATION).orElseThrow();
            assertThat(retryClaim.processingJobId()).isEqualTo(retry.jobId());
            assertThat(retryClaim.claimVersion()).isEqualTo(1L);
            jdbcTemplate.update(
                    "UPDATE processing_jobs SET lease_expires_at = now() - INTERVAL '1 second' WHERE id = ?",
                    retry.jobId());
            assertThat(repository.lockNextExpiredId()).contains(retry.jobId());
        }

        try (FixtureScope fixtures = new FixtureScope(cleanupJdbc, runToken + "-indexing-skip-locked")) {
            long owner = createUser(jdbcTemplate, fixtures);
            Instant now = databaseNow(jdbcTemplate);
            ProcessingFixture first = createProcessingJob(
                    jdbcTemplate, fixtures, owner, "PENDING", null, now.minusSeconds(20));
            ProcessingFixture second = createProcessingJob(
                    jdbcTemplate, fixtures, owner, "PENDING", null, now.minusSeconds(10));

            ClaimedProcessingJob skipLockedClaim = claimProcessingWhileLocked(dataSource, first.jobId());
            assertThat(skipLockedClaim.processingJobId()).isEqualTo(second.jobId());
            assertThat(jobStatus(jdbcTemplate, first.jobId())).isEqualTo("PENDING");
            assertThat(jobStatus(jdbcTemplate, second.jobId())).isEqualTo("PROCESSING");

            ProcessingState workerState = processingState(jdbcTemplate, second.jobId());
            assertThat(repository.renewLease(
                    second.jobId(), skipLockedClaim.claimVersion() - 1,
                    Duration.ofSeconds(45))).isEmpty();
            assertThat(processingState(jdbcTemplate, second.jobId())).isEqualTo(workerState);
        }
    }

    private static void verifyChangeLogSql(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            JdbcTemplate cleanupJdbc,
            String runToken) {
        try (FixtureScope fixtures = new FixtureScope(cleanupJdbc, runToken + "-change-log")) {
            long ownerOne = createUser(jdbcTemplate, fixtures);
            long ownerTwo = createUser(jdbcTemplate, fixtures);
            DocumentFixture ownerOneFirst = createDocumentVersion(
                    jdbcTemplate, fixtures, ownerOne, "ChangeLog first", "QUARANTINED", 1);
            DocumentFixture ownerOneSecond = createDocumentVersion(
                    jdbcTemplate, fixtures, ownerOne, "ChangeLog second", "QUARANTINED", 1);
            DocumentFixture ownerOneThird = createDocumentVersion(
                    jdbcTemplate, fixtures, ownerOne, "ChangeLog third", "QUARANTINED", 1);
            DocumentFixture ownerTwoFirst = createDocumentVersion(
                    jdbcTemplate, fixtures, ownerTwo, "ChangeLog foreign", "QUARANTINED", 1);

            long firstChangeLogId = insertPendingChangeLog(
                    jdbcTemplate, fixtures, ownerOne, ownerOneFirst.versionId(), "first");
            long secondChangeLogId = insertPendingChangeLog(
                    jdbcTemplate, fixtures, ownerOne, ownerOneSecond.versionId(), "second");
            long foreignChangeLogId = insertPendingChangeLog(
                    jdbcTemplate, fixtures, ownerTwo, ownerTwoFirst.versionId(), "foreign");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM document_change_logs WHERE processing_job_id IS NULL", Long.class))
                    .isEqualTo(3L);

            assertThatThrownBy(() -> jdbcTemplate.update(
                    """
                    INSERT INTO document_change_logs(
                        owner_user_id, document_version_id, event_type, event_key, dispatch_status)
                    VALUES (?, ?, 'DOCUMENT_VERSION_CREATED', ?, 'PENDING')
                    """,
                    ownerOne,
                    ownerOneSecond.versionId(),
                    fixtures.tag("first")))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    """
                    INSERT INTO document_change_logs(
                        owner_user_id, document_version_id, event_type, event_key, dispatch_status)
                    VALUES (?, ?, 'DOCUMENT_VERSION_CREATED', ?, 'PENDING')
                    """,
                    ownerOne,
                    ownerOneFirst.versionId(),
                    fixtures.tag("duplicate-version-event")))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

            assertThat(insertIndexingIfAbsent(jdbcTemplate, ownerOne, ownerOneFirst.versionId())).isEqualTo(1);
            assertThat(insertIndexingIfAbsent(jdbcTemplate, ownerOne, ownerOneFirst.versionId())).isZero();
            long firstJobId = processingJobId(jdbcTemplate, ownerOne, ownerOneFirst.versionId());
            fixtures.trackProcessingJob(firstJobId);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM processing_jobs WHERE owner_user_id = ? AND document_version_id = ? "
                            + "AND job_type = 'INDEXING'",
                    Long.class,
                    ownerOne,
                    ownerOneFirst.versionId())).isEqualTo(1L);

            long sameOwnerWrongVersionJobId = createIndexingJob(
                    jdbcTemplate, fixtures, ownerOne, ownerOneThird.versionId());
            long foreignOwnerJobId = createIndexingJob(
                    jdbcTemplate, fixtures, ownerTwo, ownerTwoFirst.versionId());

            assertThat(jdbcTemplate.update(
                    "UPDATE document_change_logs SET processing_job_id = ? WHERE id = ?",
                    firstJobId,
                    firstChangeLogId)).isEqualTo(1);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE document_change_logs SET processing_job_id = ? WHERE id = ?",
                    firstJobId,
                    secondChangeLogId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE document_change_logs SET processing_job_id = ? WHERE id = ?",
                    sameOwnerWrongVersionJobId,
                    secondChangeLogId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThatThrownBy(() -> jdbcTemplate.update(
                    "UPDATE document_change_logs SET processing_job_id = ? WHERE id = ?",
                    foreignOwnerJobId,
                    secondChangeLogId))
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM document_change_logs WHERE id = ? AND owner_user_id = ?",
                    Long.class,
                    firstChangeLogId,
                    ownerOne)).isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM document_change_logs WHERE id = ? AND owner_user_id = ?",
                    Long.class,
                    firstChangeLogId,
                    ownerTwo)).isZero();

            assertThat(jdbcTemplate.update(
                    "UPDATE document_change_logs SET dispatch_status = 'DISPATCHED' WHERE id = ?",
                    firstChangeLogId)).isEqualTo(1);
            long skipLockedClaimId = claimChangeLogWhileLocked(dataSource, secondChangeLogId);
            assertThat(skipLockedClaimId).isEqualTo(foreignChangeLogId);
        }
    }

    private static void verifyCleanupJobSql(
            DataSource dataSource,
            JdbcTemplate jdbcTemplate,
            JdbcTemplate cleanupJdbc,
            String runToken) {
        FileCleanupJobRepository repository = new FileCleanupJobRepository(jdbcTemplate);
        try (FixtureScope fixtures = new FixtureScope(cleanupJdbc, runToken + "-cleanup-lifecycle")) {
            String completedKey = fixtures.storageKey("cleanup-complete.txt");
            registerCleanupJob(repository, jdbcTemplate, fixtures, completedKey);
            repository.registerPending(completedKey);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM file_cleanup_jobs WHERE storage_key = ?", Long.class, completedKey))
                    .isEqualTo(1L);
            ClaimedFileCleanupJob completedClaim = repository.claimNext(LEASE_DURATION).orElseThrow();
            assertThat(completedClaim.claimVersion()).isEqualTo(1L);
            assertThat(repository.complete(
                    completedClaim.fileCleanupJobId(), completedClaim.claimVersion())).isTrue();
            assertThat(cleanupStatus(jdbcTemplate, completedKey)).isEqualTo("COMPLETED");

            String retryKey = fixtures.storageKey("cleanup-retry-fail.txt");
            registerCleanupJob(repository, jdbcTemplate, fixtures, retryKey);
            ClaimedFileCleanupJob workerA = repository.claimNext(LEASE_DURATION).orElseThrow();
            Instant available = databaseNow(jdbcTemplate).minusSeconds(1);
            assertThat(repository.scheduleRetry(
                    workerA.fileCleanupJobId(), workerA.claimVersion(), available, "COMPAT_RETRY")).isTrue();
            assertThat(cleanupStatus(jdbcTemplate, retryKey)).isEqualTo("RETRY_WAIT");
            ClaimedFileCleanupJob workerB = repository.claimNext(LEASE_DURATION).orElseThrow();
            assertThat(workerB.claimVersion()).isEqualTo(workerA.claimVersion() + 1);
            CleanupState workerBState = cleanupState(jdbcTemplate, retryKey);
            assertThat(repository.complete(workerA.fileCleanupJobId(), workerA.claimVersion())).isFalse();
            assertThat(repository.scheduleRetry(
                    workerA.fileCleanupJobId(), workerA.claimVersion(), available, "STALE_RETRY")).isFalse();
            assertThat(repository.fail(
                    workerA.fileCleanupJobId(), workerA.claimVersion(), "STALE_FAIL")).isFalse();
            assertThat(cleanupState(jdbcTemplate, retryKey)).isEqualTo(workerBState);
            assertThat(repository.fail(
                    workerB.fileCleanupJobId(), workerB.claimVersion(), "COMPAT_FAILED")).isTrue();
            assertThat(cleanupStatus(jdbcTemplate, retryKey)).isEqualTo("FAILED");

            String recoveryKey = fixtures.storageKey("cleanup-recovery.txt");
            registerCleanupJob(repository, jdbcTemplate, fixtures, recoveryKey);
            ClaimedFileCleanupJob expiredClaim = repository.claimNext(LEASE_DURATION).orElseThrow();
            jdbcTemplate.update(
                    "UPDATE file_cleanup_jobs SET lease_expires_at = now() - INTERVAL '1 second' WHERE id = ?",
                    expiredClaim.fileCleanupJobId());
            ClaimedFileCleanupJob lockedExpired = repository.lockNextExpired().orElseThrow();
            assertThat(lockedExpired.fileCleanupJobId()).isEqualTo(expiredClaim.fileCleanupJobId());
            Instant retryAt = databaseNow(jdbcTemplate).minusSeconds(1);
            assertThat(repository.recoverForRetry(
                    expiredClaim.fileCleanupJobId(), expiredClaim.claimVersion(), retryAt,
                    "LEASE_EXPIRED")).isTrue();
            ClaimedFileCleanupJob recoveredClaim = repository.claimNext(LEASE_DURATION).orElseThrow();
            assertThat(recoveredClaim.claimVersion()).isEqualTo(expiredClaim.claimVersion() + 2);
            CleanupState recoveredState = cleanupState(jdbcTemplate, recoveryKey);
            assertThat(repository.complete(
                    expiredClaim.fileCleanupJobId(), expiredClaim.claimVersion())).isFalse();
            assertThat(repository.scheduleRetry(
                    expiredClaim.fileCleanupJobId(), expiredClaim.claimVersion(), retryAt,
                    "STALE_RETRY")).isFalse();
            assertThat(repository.fail(
                    expiredClaim.fileCleanupJobId(), expiredClaim.claimVersion(), "STALE_FAIL")).isFalse();
            assertThat(cleanupState(jdbcTemplate, recoveryKey)).isEqualTo(recoveredState);
            assertThat(repository.complete(
                    recoveredClaim.fileCleanupJobId(), recoveredClaim.claimVersion())).isTrue();
        }

        try (FixtureScope fixtures = new FixtureScope(cleanupJdbc, runToken + "-cleanup-skip-locked")) {
            String firstKey = fixtures.storageKey("cleanup-skip-locked-first.txt");
            String secondKey = fixtures.storageKey("cleanup-skip-locked-second.txt");
            long firstId = registerCleanupJob(repository, jdbcTemplate, fixtures, firstKey);
            long secondId = registerCleanupJob(repository, jdbcTemplate, fixtures, secondKey);
            jdbcTemplate.update(
                    "UPDATE file_cleanup_jobs SET available_at = now() - INTERVAL '2 seconds' WHERE id = ?",
                    firstId);
            jdbcTemplate.update(
                    "UPDATE file_cleanup_jobs SET available_at = now() - INTERVAL '1 second' WHERE id = ?",
                    secondId);

            ClaimedFileCleanupJob skipLocked = claimCleanupWhileLocked(dataSource, firstId);
            assertThat(skipLocked.fileCleanupJobId()).isEqualTo(secondId);
            assertThat(cleanupStatus(jdbcTemplate, firstKey)).isEqualTo("PENDING");
            assertThat(cleanupStatus(jdbcTemplate, secondKey)).isEqualTo("PROCESSING");
        }
    }

    private static ClaimedProcessingJob claimProcessingWhileLocked(DataSource dataSource, long lockedJobId) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Optional<ClaimedProcessingJob>> future = null;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            lockRow(connection, "processing_jobs", lockedJobId);
            future = executor.submit(() -> new ProcessingJobClaimRepository(new JdbcTemplate(dataSource))
                    .claimNext(LEASE_DURATION));
            Optional<ClaimedProcessingJob> claim = future.get(5, TimeUnit.SECONDS);
            connection.rollback();
            return claim.orElseThrow();
        }
        catch (Exception failure) {
            if (future != null) {
                future.cancel(true);
            }
            throw new IllegalStateException("Processing SKIP LOCKED verification failed.", failure);
        }
        finally {
            executor.shutdownNow();
        }
    }

    private static ClaimedFileCleanupJob claimCleanupWhileLocked(DataSource dataSource, long lockedJobId) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Optional<ClaimedFileCleanupJob>> future = null;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            lockRow(connection, "file_cleanup_jobs", lockedJobId);
            future = executor.submit(() -> new FileCleanupJobRepository(new JdbcTemplate(dataSource))
                    .claimNext(LEASE_DURATION));
            Optional<ClaimedFileCleanupJob> claim = future.get(5, TimeUnit.SECONDS);
            connection.rollback();
            return claim.orElseThrow();
        }
        catch (Exception failure) {
            if (future != null) {
                future.cancel(true);
            }
            throw new IllegalStateException("Cleanup SKIP LOCKED verification failed.", failure);
        }
        finally {
            executor.shutdownNow();
        }
    }

    private static long claimChangeLogWhileLocked(DataSource dataSource, long lockedChangeLogId) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Long> future = null;
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            lockRow(connection, "document_change_logs", lockedChangeLogId);
            future = executor.submit(() -> new JdbcTemplate(dataSource).query(
                    """
                    SELECT id
                    FROM document_change_logs
                    WHERE dispatch_status = 'PENDING'
                       OR (dispatch_status = 'RETRY_WAIT' AND next_retry_at <= now())
                    ORDER BY created_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                    """,
                    resultSet -> resultSet.next() ? resultSet.getLong("id") : null));
            Long claimId = future.get(5, TimeUnit.SECONDS);
            connection.rollback();
            if (claimId == null) {
                throw new IllegalStateException("ChangeLog SKIP LOCKED verification did not find a claimable row.");
            }
            return claimId;
        }
        catch (Exception failure) {
            if (future != null) {
                future.cancel(true);
            }
            throw new IllegalStateException("ChangeLog SKIP LOCKED verification failed.", failure);
        }
        finally {
            executor.shutdownNow();
        }
    }

    private static void lockRow(Connection connection, String table, long id) throws SQLException {
        String sql = switch (table) {
            case "processing_jobs" -> "SELECT id FROM processing_jobs WHERE id = ? FOR UPDATE";
            case "file_cleanup_jobs" -> "SELECT id FROM file_cleanup_jobs WHERE id = ? FOR UPDATE";
            case "document_change_logs" -> "SELECT id FROM document_change_logs WHERE id = ? FOR UPDATE";
            default -> throw new IllegalArgumentException("Unsupported compatibility-test table.");
        };
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
            }
        }
    }

    private static long registerCleanupJob(
            FileCleanupJobRepository repository,
            JdbcTemplate jdbcTemplate,
            FixtureScope fixtures,
            String storageKey) {
        fixtures.trackCleanupKey(storageKey);
        repository.registerPending(storageKey);
        return cleanupId(jdbcTemplate, storageKey);
    }

    private static ProcessingFixture createProcessingJob(
            JdbcTemplate jdbcTemplate,
            FixtureScope fixtures,
            long ownerUserId,
            String status,
            Instant nextRetryAt,
            Instant createdAt) {
        DocumentFixture document = createDocumentVersion(
                jdbcTemplate, fixtures, ownerUserId, "Worker compatibility", "QUARANTINED", 1);
        Long jobId = jdbcTemplate.queryForObject(
                """
                INSERT INTO processing_jobs(
                    owner_user_id, document_version_id, job_type, status, next_retry_at, created_at
                )
                VALUES (?, ?, 'INDEXING', ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                document.versionId(),
                status,
                nextRetryAt == null ? null : Timestamp.from(nextRetryAt),
                Timestamp.from(createdAt));
        fixtures.trackProcessingJob(jobId);
        return new ProcessingFixture(document.documentId(), document.versionId(), jobId);
    }

    private static long insertPendingChangeLog(
            JdbcTemplate jdbcTemplate,
            FixtureScope fixtures,
            long ownerUserId,
            long documentVersionId,
            String eventKeySuffix) {
        Long changeLogId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_change_logs(
                    owner_user_id, document_version_id, event_type, event_key, dispatch_status)
                VALUES (?, ?, 'DOCUMENT_VERSION_CREATED', ?, 'PENDING')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                documentVersionId,
                fixtures.tag(eventKeySuffix));
        fixtures.trackChangeLog(changeLogId);
        return changeLogId;
    }

    private static int insertIndexingIfAbsent(
            JdbcTemplate jdbcTemplate, long ownerUserId, long documentVersionId) {
        return jdbcTemplate.update(
                """
                INSERT INTO processing_jobs(owner_user_id, document_version_id, job_type, status)
                VALUES (?, ?, 'INDEXING', 'PENDING')
                ON CONFLICT (document_version_id, job_type) DO NOTHING
                """,
                ownerUserId,
                documentVersionId);
    }

    private static long processingJobId(JdbcTemplate jdbcTemplate, long ownerUserId, long documentVersionId) {
        Long processingJobId = jdbcTemplate.queryForObject(
                """
                SELECT id
                FROM processing_jobs
                WHERE owner_user_id = ? AND document_version_id = ? AND job_type = 'INDEXING'
                """,
                Long.class,
                ownerUserId,
                documentVersionId);
        return processingJobId;
    }

    private static long createIndexingJob(
            JdbcTemplate jdbcTemplate, FixtureScope fixtures, long ownerUserId, long documentVersionId) {
        Long processingJobId = jdbcTemplate.queryForObject(
                """
                INSERT INTO processing_jobs(owner_user_id, document_version_id, job_type, status)
                VALUES (?, ?, 'INDEXING', 'PENDING')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                documentVersionId);
        fixtures.trackProcessingJob(processingJobId);
        return processingJobId;
    }

    private static DocumentFixture createDocumentVersion(
            JdbcTemplate jdbcTemplate,
            FixtureScope fixtures,
            long ownerUserId,
            String title,
            String status,
            int versionNo) {
        Long documentId = jdbcTemplate.queryForObject(
                "INSERT INTO documents(owner_user_id, title, document_type) VALUES (?, ?, 'OTHER') RETURNING id",
                Long.class,
                ownerUserId,
                fixtures.tag(title));
        fixtures.trackDocument(documentId);
        long versionId = insertVersion(
                jdbcTemplate, fixtures, ownerUserId, documentId, versionNo, status);
        jdbcTemplate.update("UPDATE documents SET active_version_id = ? WHERE id = ?", versionId, documentId);
        return new DocumentFixture(documentId, versionId);
    }

    private static long insertVersion(
            JdbcTemplate jdbcTemplate,
            FixtureScope fixtures,
            long ownerUserId,
            long documentId,
            int versionNo,
            String status) {
        Long versionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path,
                    file_type, content_hash, status
                )
                VALUES (?, ?, ?, 'compatibility.txt', ?, 'TXT', repeat('a', 64), ?)
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                documentId,
                versionNo,
                fixtures.storageKey("document-" + UUID.randomUUID() + ".txt"),
                status);
        fixtures.trackVersion(versionId);
        return versionId;
    }

    private static void insertChunk(
            JdbcTemplate jdbcTemplate,
            FixtureScope fixtures,
            long ownerUserId,
            long versionId,
            int chunkNo,
            String content,
            float[] embedding,
            String sourceType,
            int sourceIndex,
            String sourceLabel,
            Integer pageNo) {
        Long chunkId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_chunks(
                    owner_user_id, content, embedding, document_version_id, chunk_no, page_no,
                    source_type, source_index, source_label
                )
                VALUES (?, ?, CAST(? AS vector), ?, ?, ?, ?, ?, ?)
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                content,
                vectorLiteral(embedding),
                versionId,
                chunkNo,
                pageNo,
                sourceType,
                sourceIndex,
                sourceLabel);
        fixtures.trackChunk(chunkId);
    }

    private static long createUser(JdbcTemplate jdbcTemplate, FixtureScope fixtures) {
        Long userId = jdbcTemplate.queryForObject(
                """
                INSERT INTO users(email, password_hash, role, enabled)
                VALUES (?, repeat('x', 60), 'USER', TRUE)
                RETURNING id
                """,
                Long.class,
                fixtures.email());
        fixtures.trackUser(userId);
        return userId;
    }

    private static float[] vector(float first, float second) {
        float[] vector = new float[PgVectorSmokeAssertions.DIMENSIONS];
        vector[0] = first;
        vector[1] = second;
        return vector;
    }

    private static String vectorLiteral(float[] vector) {
        List<String> values = new ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(Float.toString(value));
        }
        return "[" + String.join(",", values) + "]";
    }

    private static Instant databaseNow(JdbcTemplate jdbcTemplate) {
        return jdbcTemplate.queryForObject(
                "SELECT now()", (resultSet, rowNum) -> resultSet.getTimestamp(1).toInstant());
    }

    private static String jobStatus(JdbcTemplate jdbcTemplate, long jobId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM processing_jobs WHERE id = ?", String.class, jobId);
    }

    private static ProcessingState processingState(JdbcTemplate jdbcTemplate, long jobId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status, retry_count, claim_version, next_retry_at, lease_expires_at, completed_at
                FROM processing_jobs
                WHERE id = ?
                """,
                (resultSet, rowNum) -> new ProcessingState(
                        resultSet.getString("status"),
                        resultSet.getInt("retry_count"),
                        resultSet.getLong("claim_version"),
                        resultSet.getTimestamp("next_retry_at"),
                        resultSet.getTimestamp("lease_expires_at"),
                        resultSet.getTimestamp("completed_at")),
                jobId);
    }

    private static long cleanupId(JdbcTemplate jdbcTemplate, String storageKey) {
        Long id = jdbcTemplate.queryForObject(
                "SELECT id FROM file_cleanup_jobs WHERE storage_key = ?", Long.class, storageKey);
        return id;
    }

    private static String cleanupStatus(JdbcTemplate jdbcTemplate, String storageKey) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM file_cleanup_jobs WHERE storage_key = ?", String.class, storageKey);
    }

    private static CleanupState cleanupState(JdbcTemplate jdbcTemplate, String storageKey) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status, attempts, claim_version, lease_expires_at, available_at,
                       last_error_code, completed_at, updated_at
                FROM file_cleanup_jobs
                WHERE storage_key = ?
                """,
                (resultSet, rowNum) -> new CleanupState(
                        resultSet.getString("status"),
                        resultSet.getInt("attempts"),
                        resultSet.getLong("claim_version"),
                        resultSet.getTimestamp("lease_expires_at"),
                        resultSet.getTimestamp("available_at"),
                        resultSet.getString("last_error_code"),
                        resultSet.getTimestamp("completed_at"),
                        resultSet.getTimestamp("updated_at")),
                storageKey);
    }

    private static void inTransaction(DataSource dataSource, Runnable assertion) {
        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        transaction.executeWithoutResult(status -> assertion.run());
    }

    private static void verifyPhase(String phase, String feature, Runnable assertion) {
        try {
            assertion.run();
        }
        catch (CompatibilityVerificationFailure failure) {
            throw failure;
        }
        catch (AssertionError failure) {
            throw safeFailure(phase, "not-applicable", feature, failure);
        }
        catch (RuntimeException failure) {
            throw safeFailure(phase, "not-applicable", feature, failure);
        }
    }

    private static CompatibilityVerificationFailure migrationFailure(
            Throwable failure, JdbcTemplate flywayJdbc) {
        String version = findFailedMigration(failure, flywayJdbc).orElse("unknown");
        return safeFailure("migration", version, migrationFeature(version), failure);
    }

    private static CompatibilityVerificationFailure safeFailure(
            String phase, String migration, String feature, Throwable failure) {
        String sqlState = findSqlState(failure).orElse("not-available");
        String failureType = failure.getClass().getSimpleName();
        return new CompatibilityVerificationFailure(
                "OpenSQL compatibility verification failed: phase=" + phase
                        + ", migration=" + migration
                        + ", sqlState=" + sqlState
                        + ", sqlFeature=" + feature
                        + ", failureType=" + failureType + ".");
    }

    private static Optional<String> findFailedMigration(Throwable failure, JdbcTemplate flywayJdbc) {
        try {
            String failed = flywayJdbc.query(
                    """
                    SELECT version
                    FROM flyway_schema_history
                    WHERE NOT success AND version IS NOT NULL
                    ORDER BY installed_rank DESC
                    LIMIT 1
                    """,
                    resultSet -> resultSet.next() ? resultSet.getString(1) : null);
            if (failed != null && !failed.isBlank()) {
                return Optional.of(failed);
            }
        }
        catch (RuntimeException ignored) {
            // The history table may not exist when the first migration fails.
        }

        for (Throwable current = failure; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message == null) {
                continue;
            }
            Matcher fileMatcher = MIGRATION_FILE_PATTERN.matcher(message);
            if (fileMatcher.find()) {
                return Optional.of(fileMatcher.group(1));
            }
            Matcher versionMatcher = MIGRATION_VERSION_PATTERN.matcher(message);
            if (versionMatcher.find()) {
                return Optional.of(versionMatcher.group(1));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> findSqlState(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                return Optional.of(sqlException.getSQLState());
            }
        }
        return Optional.empty();
    }

    private static String migrationFeature(String version) {
        return switch (version) {
            case "1" -> "CREATE EXTENSION vector";
            case "2" -> "vector(1024) column";
            case "3" -> "tables, foreign keys and ALTER TABLE";
            case "4" -> "CHECK constraints and claim index";
            case "5" -> "lease columns and partial recovery update";
            case "6" -> "users table and role CHECK";
            case "7" -> "status transition updates and ON CONFLICT";
            case "8" -> "DO block, composite foreign keys and owner indexes";
            case "9" -> "DocumentType CHECK constraint";
            case "10" -> "CTE, row_number and UPDATE FROM";
            case "11" -> "TXT/PDF and source CHECK replacement";
            case "12" -> "cleanup table, unique constraint and index";
            case "13" -> "cleanup lease/fencing columns and recovery index";
            case "14" -> "ChangeLog table, CHECK/unique/composite foreign keys and claim indexes";
            case "15" -> "processing progress columns and CHECK constraints";
            case "16" -> "document tags, owner-scoped user tags and SYSTEM seed data";
            default -> "V1-V16 Flyway SQL";
        };
    }

    private static final class FixtureScope implements AutoCloseable {

        private final JdbcTemplate cleanupJdbc;
        private final String runToken;
        private final Set<String> cleanupKeys = new LinkedHashSet<>();
        private final Set<Long> changeLogIds = new LinkedHashSet<>();
        private final Set<Long> processingJobIds = new LinkedHashSet<>();
        private final Set<Long> chunkIds = new LinkedHashSet<>();
        private final Set<Long> versionIds = new LinkedHashSet<>();
        private final Set<Long> documentIds = new LinkedHashSet<>();
        private final Set<Long> userIds = new LinkedHashSet<>();
        private int emailSequence;
        private boolean closed;

        private FixtureScope(JdbcTemplate cleanupJdbc, String runToken) {
            this.cleanupJdbc = cleanupJdbc;
            this.runToken = runToken;
        }

        private String tag(String value) {
            return runToken + ":" + value;
        }

        private String email() {
            emailSequence++;
            return runToken + "-user-" + emailSequence + "@compatibility.invalid";
        }

        private String storageKey(String fileName) {
            return "compatibility/" + runToken + "/" + fileName;
        }

        private void trackCleanupKey(String storageKey) {
            cleanupKeys.add(storageKey);
        }

        private void trackChangeLog(long changeLogId) {
            changeLogIds.add(changeLogId);
        }

        private void trackProcessingJob(long processingJobId) {
            processingJobIds.add(processingJobId);
        }

        private void trackChunk(long chunkId) {
            chunkIds.add(chunkId);
        }

        private void trackVersion(long versionId) {
            versionIds.add(versionId);
        }

        private void trackDocument(long documentId) {
            documentIds.add(documentId);
        }

        private void trackUser(long userId) {
            userIds.add(userId);
        }

        @Override
        public void close() {
            if (closed) {
                return;
            }
            closed = true;

            deleteCleanupJobs();
            deleteById("document_change_logs", changeLogIds);
            deleteById("processing_jobs", processingJobIds);
            deleteById("document_chunks", chunkIds);
            forEachReversed(documentIds, documentId -> assertThat(cleanupJdbc.update(
                    "UPDATE documents SET active_version_id = NULL WHERE id = ?",
                    documentId)).isEqualTo(1));
            deleteById("document_versions", versionIds);
            deleteById("documents", documentIds);
            deleteById("users", userIds);
        }

        private void deleteCleanupJobs() {
            List<String> keys = new ArrayList<>(cleanupKeys);
            for (int index = keys.size() - 1; index >= 0; index--) {
                assertThat(cleanupJdbc.update(
                        "DELETE FROM file_cleanup_jobs WHERE storage_key = ?",
                        keys.get(index))).isEqualTo(1);
            }
        }

        private void deleteById(String table, Set<Long> ids) {
            forEachReversed(ids, id -> assertThat(cleanupJdbc.update(
                    "DELETE FROM " + table + " WHERE id = ?",
                    id)).isEqualTo(1));
        }

        private void forEachReversed(Set<Long> ids, java.util.function.LongConsumer action) {
            List<Long> orderedIds = new ArrayList<>(ids);
            for (int index = orderedIds.size() - 1; index >= 0; index--) {
                action.accept(orderedIds.get(index));
            }
        }
    }

    private record VerificationMarker(long userId, String email) {
    }

    private record DocumentFixture(long documentId, long versionId) {
    }

    private record ProcessingFixture(long documentId, long versionId, long jobId) {
    }

    private record PreV14ProcessingFixture(
            long ownerUserId,
            long documentId,
            long documentVersionId,
            long processingJobId) {
    }

    private record ProcessingState(
            String status,
            int retryCount,
            long claimVersion,
            Timestamp nextRetryAt,
            Timestamp leaseExpiresAt,
            Timestamp completedAt) {
    }

    private record CleanupState(
            String status,
            int attempts,
            long claimVersion,
            Timestamp leaseExpiresAt,
            Timestamp availableAt,
            String lastErrorCode,
            Timestamp completedAt,
            Timestamp updatedAt) {
    }

    private static final class CompatibilityVerificationFailure extends AssertionError {

        private CompatibilityVerificationFailure(String message) {
            super(message);
        }
    }
}
