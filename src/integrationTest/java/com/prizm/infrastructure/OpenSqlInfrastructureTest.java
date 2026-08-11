package com.prizm.infrastructure;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Runs the SQL compatibility suite against a fresh, dedicated OpenSQL verification database or schema.
 *
 * <p>This test deliberately does not start the Spring application context, indexing scheduler, cleanup
 * scheduler, or Ollama. Runtime and Flyway credentials are read separately, and no supplied value is logged
 * or included in a failure message.</p>
 */
@EnabledIfEnvironmentVariable(named = "RUN_OPENSQL_TESTS", matches = "(?i:true|1)")
class OpenSqlInfrastructureTest {

    private static final String TARGET_CONFIRMATION = "PRIZM_OPENSQL_VERIFICATION_TARGET_CONFIRMED";
    private static final String EXPECTED_DATABASE = configured("PRIZM_OPENSQL_EXPECTED_DATABASE", "prizm_integration_test");
    private static final String EXPECTED_RUNTIME_USER = configured("PRIZM_OPENSQL_EXPECTED_RUNTIME_USER", "prizm_app");
    private static final String EXPECTED_FLYWAY_USER = configured("PRIZM_OPENSQL_EXPECTED_FLYWAY_USER", "prizm_owner");

    @BeforeAll
    static void suppressConnectionDetailsFromLibraryLogs() {
        LoggingSystem loggingSystem = LoggingSystem.get(OpenSqlInfrastructureTest.class.getClassLoader());
        loggingSystem.setLogLevel("org.flywaydb", LogLevel.OFF);
        loggingSystem.setLogLevel("org.postgresql", LogLevel.OFF);
        loggingSystem.setLogLevel("org.springframework.jdbc.datasource", LogLevel.OFF);
    }

    @Test
    void verifiesMigrationsVectorSearchAndWorkerSqlAgainstDedicatedOpenSqlTarget() {
        requireDedicatedVerificationTarget();

        DataSource runtimeDataSource = dataSource(
                required("PRIZM_DB_URL"),
                required("PRIZM_DB_USERNAME"),
                required("PRIZM_DB_PASSWORD"));
        DataSource flywayDataSource = dataSource(
                required("PRIZM_FLYWAY_URL"),
                required("PRIZM_FLYWAY_USERNAME"),
                required("PRIZM_FLYWAY_PASSWORD"));

        verifyConnectionIdentity(runtimeDataSource, EXPECTED_RUNTIME_USER);
        verifyConnectionIdentity(flywayDataSource, EXPECTED_FLYWAY_USER);
        OpenSqlCompatibilityAssertions.verifyWithOpenSqlRuntimeGrants(runtimeDataSource, flywayDataSource);
    }

    private void requireDedicatedVerificationTarget() {
        String confirmed = System.getenv(TARGET_CONFIRMATION);
        if (!"true".equalsIgnoreCase(confirmed)) {
            throw new AssertionError(
                    "OpenSQL verification requires " + TARGET_CONFIRMATION
                            + "=true for a fresh, dedicated database or schema.");
        }
    }

    private String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new AssertionError("OpenSQL verification configuration is missing " + name + ".");
        }
        return value;
    }

    private DataSource dataSource(String url, String username, String password) {
        return new DriverManagerDataSource(url, username, password);
    }

    private static String configured(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private void verifyConnectionIdentity(DataSource dataSource, String expectedUser) {
        org.springframework.jdbc.core.JdbcTemplate jdbc =
                new org.springframework.jdbc.core.JdbcTemplate(dataSource);
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        String user = jdbc.queryForObject("SELECT current_user", String.class);
        if (!EXPECTED_DATABASE.equals(database) || !expectedUser.equals(user)) {
            throw new AssertionError("OpenSQL verification target identity did not match the dedicated test target.");
        }
    }
}
