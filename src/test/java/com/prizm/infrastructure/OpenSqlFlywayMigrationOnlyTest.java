package com.prizm.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;

/** Applies only PRIZM Flyway migrations to an explicitly approved, empty OpenSQL target. */
@EnabledIfEnvironmentVariable(named = "PRIZM_OPENSQL_MIGRATION_EXECUTION", matches = "APPROVED")
class OpenSqlFlywayMigrationOnlyTest {

    private static final int EXPECTED_MIGRATION_COUNT = 19;
    private static final List<String> EXPECTED_MIGRATION_VERSIONS = IntStream
            .rangeClosed(1, EXPECTED_MIGRATION_COUNT)
            .mapToObj(Integer::toString)
            .toList();
    private static final List<String> DOMAIN_TABLES = List.of(
            "users",
            "documents",
            "document_versions",
            "document_chunks",
            "processing_jobs",
            "file_cleanup_jobs",
            "document_change_logs",
            "tags",
            "document_tags",
            "search_v3_index_generations",
            "search_v3_indexing_jobs",
            "search_v3_retrieval_passages",
            "search_v3_evidence_children",
            "search_v3_passage_embeddings",
            "search_v3_child_embeddings");

    @BeforeAll
    static void suppressConnectionDetailsFromLibraryLogs() {
        LoggingSystem loggingSystem = LoggingSystem.get(OpenSqlFlywayMigrationOnlyTest.class.getClassLoader());
        loggingSystem.setLogLevel("org.flywaydb", LogLevel.OFF);
        loggingSystem.setLogLevel("org.postgresql", LogLevel.OFF);
    }

    @Test
    void appliesOnlyFlywayMigrationsToApprovedOpenSqlTarget() {
        String url = requiredEnvironment("PRIZM_FLYWAY_URL");
        String username = requiredEnvironment("PRIZM_FLYWAY_USERNAME");
        String password = requiredEnvironment("PRIZM_FLYWAY_PASSWORD");

        try {
            verifyEmptyApprovedTarget(url, username, password);

            Flyway flyway = Flyway.configure()
                    .dataSource(url, username, password)
                    .locations("classpath:db/migration")
                    .cleanDisabled(true)
                    .baselineOnMigrate(false)
                    .outOfOrder(false)
                    .load();

            MigrationInfo[] pendingBeforeMigration = flyway.info().pending();
            assertEquals(EXPECTED_MIGRATION_COUNT, pendingBeforeMigration.length);
            assertEquals(EXPECTED_MIGRATION_VERSIONS, migrationVersions(pendingBeforeMigration));
            assertEquals(0, flyway.info().applied().length);
            assertNull(flyway.info().current());

            MigrateResult firstMigration = flyway.migrate();
            assertEquals(EXPECTED_MIGRATION_COUNT, firstMigration.migrationsExecuted);

            flyway.validate();

            MigrationInfo[] applied = flyway.info().applied();
            assertEquals(EXPECTED_MIGRATION_COUNT, applied.length);
            assertEquals(EXPECTED_MIGRATION_VERSIONS, migrationVersions(applied));
            assertNotNull(flyway.info().current());
            assertEquals("19", flyway.info().current().getVersion().getVersion());
            assertEquals(0, flyway.info().pending().length);
            verifyNoFailedHistory(url, username, password);

            MigrateResult secondMigration = flyway.migrate();
            assertEquals(0, secondMigration.migrationsExecuted);
            assertEquals("19", flyway.info().current().getVersion().getVersion());
            assertEquals(0, flyway.info().pending().length);
        }
        catch (AssertionError failure) {
            throw failure;
        }
        catch (Exception failure) {
            throw new AssertionError(
                    "OpenSQL Flyway migration-only execution failed with "
                            + failure.getClass().getSimpleName()
                            + "; connection details are redacted.");
        }
    }

    private List<String> migrationVersions(MigrationInfo[] migrations) {
        return Arrays.stream(migrations)
                .map(info -> {
                    assertNotNull(info.getVersion());
                    return info.getVersion().getVersion();
                })
                .toList();
    }

    private void verifyNoFailedHistory(String url, String username, String password) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals(
                    "0",
                    querySingleValue(
                            connection,
                            "SELECT count(*)::text FROM public.flyway_schema_history WHERE NOT success"));
        }
    }

    private void verifyEmptyApprovedTarget(String url, String username, String password) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, username, password)) {
            assertEquals("prizm", querySingleValue(connection, "SELECT current_database()"));
            assertEquals("public", querySingleValue(connection, "SELECT current_schema()"));
            assertEquals("prizm_owner", querySingleValue(connection, "SELECT current_user"));

            assertFalse(relationExists(connection, "public.flyway_schema_history"));
            for (String table : DOMAIN_TABLES) {
                assertFalse(relationExists(connection, "public." + table));
            }

            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    SELECT extension.extversion, owner.rolname
                    FROM pg_catalog.pg_extension extension
                    JOIN pg_catalog.pg_roles owner ON owner.oid = extension.extowner
                    WHERE extension.extname = 'vector'
                    """)) {
                try (ResultSet result = statement.executeQuery()) {
                    assertEquals(true, result.next());
                    assertEquals("0.8.1", result.getString(1));
                    assertEquals("postgres", result.getString(2));
                    assertFalse(result.next());
                }
            }
        }
    }

    private boolean relationExists(Connection connection, String qualifiedName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_catalog.to_regclass(?) IS NOT NULL")) {
            statement.setString(1, qualifiedName);
            try (ResultSet result = statement.executeQuery()) {
                assertEquals(true, result.next());
                return result.getBoolean(1);
            }
        }
    }

    private String querySingleValue(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
                ResultSet result = statement.executeQuery()) {
            assertEquals(true, result.next());
            String value = result.getString(1);
            assertFalse(result.next());
            return value;
        }
    }

    private String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new AssertionError("OpenSQL migration configuration is missing " + name + ".");
        }
        return value;
    }
}
