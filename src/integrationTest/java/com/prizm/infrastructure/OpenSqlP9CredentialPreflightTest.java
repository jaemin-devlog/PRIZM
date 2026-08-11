package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Credential-safe identity check that runs before the P9 Spring/Flyway E2E context. */
@EnabledIfEnvironmentVariable(named = "RUN_OPENSQL_P9_TESTS", matches = "(?i:true|1)")
class OpenSqlP9CredentialPreflightTest {

    private static final String EXPECTED_DATABASE = "prizm_p9_test";
    private static final String EXPECTED_ROLE = "prizm_p9_owner";

    @Test
    void connectsByDirectJdbcToTheExactDedicatedP9Target() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                        required("PRIZM_P9_DB_URL"),
                        required("PRIZM_P9_DB_USERNAME"),
                        required("PRIZM_P9_DB_PASSWORD"));
                Statement statement = connection.createStatement()) {
            try (ResultSet identity = statement.executeQuery("SELECT current_database(), current_user")) {
                assertThat(identity.next()).isTrue();
                assertThat(identity.getString(1)).isEqualTo(EXPECTED_DATABASE);
                assertThat(identity.getString(2)).isEqualTo(EXPECTED_ROLE);
            }

            assertThat(singleBoolean(statement,
                            "SELECT rolcanlogin AND NOT (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication) "
                                    + "FROM pg_catalog.pg_roles WHERE rolname=current_user"))
                    .isTrue();
            assertThat(singleString(statement,
                            "SELECT pg_catalog.pg_get_userbyid(datdba) FROM pg_catalog.pg_database "
                                    + "WHERE datname=current_database()"))
                    .isEqualTo(EXPECTED_ROLE);
            assertThat(singleBoolean(statement,
                            "SELECT has_database_privilege(current_user, current_database(), 'CONNECT')"))
                    .isTrue();
            assertThat(singleLong(statement,
                            "SELECT COUNT(*) FROM pg_catalog.pg_extension WHERE extname='vector'"))
                    .isOne();
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new AssertionError("P9 OpenSQL preflight configuration is missing " + name + ".");
        }
        return value;
    }

    private static boolean singleBoolean(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getBoolean(1);
        }
    }

    private static String singleString(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getString(1);
        }
    }

    private static long singleLong(Statement statement, String sql) throws Exception {
        try (ResultSet result = statement.executeQuery(sql)) {
            assertThat(result.next()).isTrue();
            return result.getLong(1);
        }
    }
}
