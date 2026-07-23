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

        OpenSqlCompatibilityAssertions.verify(runtimeDataSource, flywayDataSource);
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
}
