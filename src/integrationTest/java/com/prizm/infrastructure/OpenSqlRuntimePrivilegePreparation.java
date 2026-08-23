package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Prepares only the explicit runtime privileges required by the isolated OpenSQL integration test. */
final class OpenSqlRuntimePrivilegePreparation {

    private static final String DATABASE = configured("PRIZM_OPENSQL_EXPECTED_DATABASE", "prizm_integration_test");
    private static final String OWNER = configured("PRIZM_OPENSQL_EXPECTED_FLYWAY_USER", "prizm_owner");
    private static final String RUNTIME = configured("PRIZM_OPENSQL_EXPECTED_RUNTIME_USER", "prizm_app");
    private static final List<String> DOMAIN_TABLES = List.of(
            "users", "documents", "document_versions", "document_chunks",
            "processing_jobs", "file_cleanup_jobs", "document_change_logs", "tags", "document_tags");
    private static final List<String> DOMAIN_SEQUENCES = List.of(
            "users_id_seq", "documents_id_seq", "document_versions_id_seq",
            "document_chunks_id_seq", "processing_jobs_id_seq", "file_cleanup_jobs_id_seq",
            "document_change_logs_id_seq", "tags_id_seq");
    private static final Map<String, Set<String>> EXPECTED_TABLE_PRIVILEGES = Map.of(
            "users", Set.of("SELECT", "INSERT"),
            "documents", Set.of("SELECT", "INSERT", "UPDATE", "DELETE"),
            "document_versions", Set.of("SELECT", "INSERT", "UPDATE", "DELETE"),
            "document_chunks", Set.of("SELECT", "INSERT", "DELETE"),
            "processing_jobs", Set.of("SELECT", "INSERT", "UPDATE", "DELETE"),
            "file_cleanup_jobs", Set.of("SELECT", "INSERT", "UPDATE"),
            "document_change_logs", Set.of("SELECT", "INSERT", "UPDATE", "DELETE"),
            "tags", Set.of("SELECT", "INSERT"),
            "document_tags", Set.of("SELECT", "INSERT", "DELETE"));

    private OpenSqlRuntimePrivilegePreparation() {
    }

    private static String configured(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String sqlIdentifier(String value) {
        if (!value.matches("[a-z][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("OpenSQL verification identifier is not a safe SQL identifier.");
        }
        return value;
    }

    static void prepare(DataSource runtimeDataSource, DataSource ownerDataSource) {
        JdbcTemplate runtimeJdbc = new JdbcTemplate(runtimeDataSource);
        JdbcTemplate ownerJdbc = new JdbcTemplate(ownerDataSource);
        verifyConnectionIdentity(ownerJdbc, OWNER);
        verifyConnectionIdentity(runtimeJdbc, RUNTIME);
        verifyMigrationAndObjectGate(ownerDataSource, ownerJdbc);

        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(ownerDataSource));
        transaction.executeWithoutResult(status -> {
            applyExplicitGrants(ownerJdbc);
            verifyRuntimePrivileges(ownerJdbc);
        });
        verifyConnectionIdentity(runtimeJdbc, RUNTIME);
    }

    private static void verifyConnectionIdentity(JdbcTemplate jdbc, String expectedUser) {
        assertThat(jdbc.queryForObject("SELECT current_database()", String.class)).isEqualTo(DATABASE);
        assertThat(jdbc.queryForObject("SELECT current_schema()", String.class)).isEqualTo("public");
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo(expectedUser);
    }

    private static void verifyMigrationAndObjectGate(DataSource ownerDataSource, JdbcTemplate jdbc) {
        Flyway flyway = Flyway.configure()
                .dataSource(ownerDataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .cleanDisabled(true)
                .outOfOrder(false)
                .load();
        assertThat(flyway.info().current()).isNotNull();
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("16");
        assertThat(flyway.info().pending()).isEmpty();

        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history "
                        + "WHERE success AND version IS NOT NULL ORDER BY installed_rank",
                String.class);
        assertThat(versions).containsExactly(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13", "14", "15", "16");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success", Long.class)).isZero();

        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name",
                String.class);
        assertThat(tables).containsExactlyInAnyOrderElementsOf(List.of(
                "users", "documents", "document_versions", "document_chunks",
                "processing_jobs", "file_cleanup_jobs", "document_change_logs", "tags", "document_tags",
                "flyway_schema_history"));
        List<String> sequences = jdbc.queryForList(
                "SELECT sequence_name FROM information_schema.sequences "
                        + "WHERE sequence_schema='public' ORDER BY sequence_name",
                String.class);
        assertThat(sequences).containsExactlyInAnyOrderElementsOf(DOMAIN_SEQUENCES);

        assertThat(badOwnerCount(jdbc, "r", List.of(
                "users", "documents", "document_versions", "document_chunks",
                "processing_jobs", "file_cleanup_jobs", "document_change_logs", "tags", "document_tags",
                "flyway_schema_history"), OWNER)).isZero();
        assertThat(badOwnerCount(jdbc, "S", DOMAIN_SEQUENCES, OWNER)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT e.extversion || '|' || r.rolname FROM pg_catalog.pg_extension e "
                        + "JOIN pg_catalog.pg_roles r ON r.oid=e.extowner WHERE e.extname='vector'",
                String.class)).isEqualTo("0.8.1|postgres");
    }

    private static long badOwnerCount(
            JdbcTemplate jdbc, String relationKind, List<String> names, String expectedOwner) {
        String placeholders = String.join(",", java.util.Collections.nCopies(names.size(), "?"));
        Object[] parameters = new Object[names.size() + 2];
        parameters[0] = relationKind;
        for (int index = 0; index < names.size(); index++) {
            parameters[index + 1] = names.get(index);
        }
        parameters[parameters.length - 1] = expectedOwner;
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_catalog.pg_class c "
                        + "JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace "
                        + "JOIN pg_catalog.pg_roles r ON r.oid=c.relowner "
                        + "WHERE n.nspname='public' AND c.relkind::text=? AND c.relname IN ("
                        + placeholders + ") AND r.rolname<> ?",
                Long.class,
                parameters);
    }

    private static void applyExplicitGrants(JdbcTemplate jdbc) {
        String runtimeRole = sqlIdentifier(RUNTIME);
        String database = sqlIdentifier(DATABASE);
        jdbc.execute("GRANT CONNECT ON DATABASE " + database + " TO " + runtimeRole);
        jdbc.execute("GRANT USAGE ON SCHEMA public TO " + runtimeRole);
        jdbc.execute("GRANT SELECT, INSERT ON TABLE users TO " + runtimeRole);
        jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE documents TO " + runtimeRole);
        jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE document_versions TO " + runtimeRole);
        jdbc.execute("GRANT SELECT, INSERT, DELETE ON TABLE document_chunks TO " + runtimeRole);
        jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE processing_jobs TO " + runtimeRole);
        jdbc.execute("GRANT SELECT, INSERT, UPDATE ON TABLE file_cleanup_jobs TO " + runtimeRole);
        jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE document_change_logs TO " + runtimeRole);
        jdbc.execute("GRANT SELECT, INSERT ON TABLE tags TO " + runtimeRole);
        jdbc.execute("GRANT SELECT, INSERT, DELETE ON TABLE document_tags TO " + runtimeRole);
        jdbc.execute("GRANT USAGE ON SEQUENCE users_id_seq, documents_id_seq, "
                + "document_versions_id_seq, document_chunks_id_seq, processing_jobs_id_seq, "
                + "file_cleanup_jobs_id_seq, document_change_logs_id_seq, tags_id_seq TO " + runtimeRole);
    }

    private static void verifyRuntimePrivileges(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject(
                "SELECT has_database_privilege(?, ?, 'CONNECT')", Boolean.class, RUNTIME, DATABASE)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT has_schema_privilege(?, 'public', 'USAGE')", Boolean.class, RUNTIME)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT has_schema_privilege(?, 'public', 'CREATE')", Boolean.class, RUNTIME)).isFalse();

        Map<String, Set<String>> actual = new TreeMap<>();
        jdbc.query(
                "SELECT table_name, privilege_type FROM information_schema.table_privileges "
                        + "WHERE table_schema='public' AND grantee=? ORDER BY table_name, privilege_type",
                resultSet -> {
                    actual.computeIfAbsent(resultSet.getString(1), ignored -> new java.util.TreeSet<>())
                            .add(resultSet.getString(2));
                }, RUNTIME);
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(EXPECTED_TABLE_PRIVILEGES);

        for (String sequence : DOMAIN_SEQUENCES) {
            assertThat(hasSequencePrivilege(jdbc, sequence, "USAGE")).isTrue();
            assertThat(hasSequencePrivilege(jdbc, sequence, "SELECT")).isFalse();
            assertThat(hasSequencePrivilege(jdbc, sequence, "UPDATE")).isFalse();
        }
        for (String privilege : List.of(
                "SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER")) {
            assertThat(jdbc.queryForObject(
                    "SELECT has_table_privilege(?, 'public.flyway_schema_history', ?)",
                    Boolean.class,
                    RUNTIME,
                    privilege)).isFalse();
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_privileges "
                        + "WHERE table_schema='public' AND table_name='flyway_schema_history' "
                        + "AND grantee IN (?, 'PUBLIC')",
                Long.class,
                RUNTIME)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication "
                        + "FROM pg_catalog.pg_roles WHERE rolname=?",
                Boolean.class,
                RUNTIME)).isFalse();
    }

    private static boolean hasSequencePrivilege(JdbcTemplate jdbc, String sequence, String privilege) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT has_sequence_privilege(?, 'public." + sequence + "', ?)",
                Boolean.class,
                RUNTIME,
                privilege));
    }
}
