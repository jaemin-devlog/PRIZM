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

    private static final String DATABASE = "prizm_integration_test";
    private static final String OWNER = "prizm_owner";
    private static final String RUNTIME = "prizm_app";
    private static final List<String> DOMAIN_TABLES = List.of(
            "users", "documents", "document_versions", "document_chunks",
            "processing_jobs", "file_cleanup_jobs");
    private static final List<String> DOMAIN_SEQUENCES = List.of(
            "users_id_seq", "documents_id_seq", "document_versions_id_seq",
            "document_chunks_id_seq", "processing_jobs_id_seq", "file_cleanup_jobs_id_seq");
    private static final Map<String, Set<String>> EXPECTED_TABLE_PRIVILEGES = Map.of(
            "users", Set.of("SELECT", "INSERT"),
            "documents", Set.of("SELECT", "INSERT", "UPDATE", "DELETE"),
            "document_versions", Set.of("SELECT", "INSERT", "UPDATE", "DELETE"),
            "document_chunks", Set.of("SELECT", "INSERT", "DELETE"),
            "processing_jobs", Set.of("SELECT", "INSERT", "UPDATE", "DELETE"),
            "file_cleanup_jobs", Set.of("SELECT", "INSERT", "UPDATE"));

    private OpenSqlRuntimePrivilegePreparation() {
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
        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("13");
        assertThat(flyway.info().pending()).isEmpty();

        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history "
                        + "WHERE success AND version IS NOT NULL ORDER BY installed_rank",
                String.class);
        assertThat(versions).containsExactly("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12", "13");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE NOT success", Long.class)).isZero();

        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema='public' AND table_type='BASE TABLE' ORDER BY table_name",
                String.class);
        assertThat(tables).containsExactlyInAnyOrderElementsOf(List.of(
                "users", "documents", "document_versions", "document_chunks",
                "processing_jobs", "file_cleanup_jobs", "flyway_schema_history"));
        List<String> sequences = jdbc.queryForList(
                "SELECT sequence_name FROM information_schema.sequences "
                        + "WHERE sequence_schema='public' ORDER BY sequence_name",
                String.class);
        assertThat(sequences).containsExactlyInAnyOrderElementsOf(DOMAIN_SEQUENCES);

        assertThat(badOwnerCount(jdbc, "r", List.of(
                "users", "documents", "document_versions", "document_chunks",
                "processing_jobs", "file_cleanup_jobs", "flyway_schema_history"))).isZero();
        assertThat(badOwnerCount(jdbc, "S", DOMAIN_SEQUENCES)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT e.extversion || '|' || r.rolname FROM pg_catalog.pg_extension e "
                        + "JOIN pg_catalog.pg_roles r ON r.oid=e.extowner WHERE e.extname='vector'",
                String.class)).isEqualTo("0.8.1|postgres");
    }

    private static long badOwnerCount(JdbcTemplate jdbc, String relationKind, List<String> names) {
        String placeholders = String.join(",", java.util.Collections.nCopies(names.size(), "?"));
        Object[] parameters = new Object[names.size() + 1];
        parameters[0] = relationKind;
        for (int index = 0; index < names.size(); index++) {
            parameters[index + 1] = names.get(index);
        }
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_catalog.pg_class c "
                        + "JOIN pg_catalog.pg_namespace n ON n.oid=c.relnamespace "
                        + "JOIN pg_catalog.pg_roles r ON r.oid=c.relowner "
                        + "WHERE n.nspname='public' AND c.relkind::text=? AND c.relname IN ("
                        + placeholders + ") AND r.rolname<> 'prizm_owner'",
                Long.class,
                parameters);
    }

    private static void applyExplicitGrants(JdbcTemplate jdbc) {
        jdbc.execute("GRANT CONNECT ON DATABASE prizm_integration_test TO prizm_app");
        jdbc.execute("GRANT USAGE ON SCHEMA public TO prizm_app");
        jdbc.execute("GRANT SELECT, INSERT ON TABLE users TO prizm_app");
        jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE documents TO prizm_app");
        jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE document_versions TO prizm_app");
        jdbc.execute("GRANT SELECT, INSERT, DELETE ON TABLE document_chunks TO prizm_app");
        jdbc.execute("GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE processing_jobs TO prizm_app");
        jdbc.execute("GRANT SELECT, INSERT, UPDATE ON TABLE file_cleanup_jobs TO prizm_app");
        jdbc.execute("GRANT USAGE ON SEQUENCE users_id_seq, documents_id_seq, "
                + "document_versions_id_seq, document_chunks_id_seq, processing_jobs_id_seq, "
                + "file_cleanup_jobs_id_seq TO prizm_app");
    }

    private static void verifyRuntimePrivileges(JdbcTemplate jdbc) {
        assertThat(jdbc.queryForObject(
                "SELECT has_database_privilege('prizm_app', 'prizm_integration_test', 'CONNECT')",
                Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT has_schema_privilege('prizm_app', 'public', 'USAGE')", Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT has_schema_privilege('prizm_app', 'public', 'CREATE')", Boolean.class)).isFalse();

        Map<String, Set<String>> actual = new TreeMap<>();
        jdbc.query(
                "SELECT table_name, privilege_type FROM information_schema.table_privileges "
                        + "WHERE table_schema='public' AND grantee='prizm_app' ORDER BY table_name, privilege_type",
                resultSet -> {
                    actual.computeIfAbsent(resultSet.getString(1), ignored -> new java.util.TreeSet<>())
                            .add(resultSet.getString(2));
                });
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(EXPECTED_TABLE_PRIVILEGES);

        for (String sequence : DOMAIN_SEQUENCES) {
            assertThat(hasSequencePrivilege(jdbc, sequence, "USAGE")).isTrue();
            assertThat(hasSequencePrivilege(jdbc, sequence, "SELECT")).isFalse();
            assertThat(hasSequencePrivilege(jdbc, sequence, "UPDATE")).isFalse();
        }
        for (String privilege : List.of(
                "SELECT", "INSERT", "UPDATE", "DELETE", "TRUNCATE", "REFERENCES", "TRIGGER")) {
            assertThat(jdbc.queryForObject(
                    "SELECT has_table_privilege('prizm_app', 'public.flyway_schema_history', ?)",
                    Boolean.class,
                    privilege)).isFalse();
        }
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.table_privileges "
                        + "WHERE table_schema='public' AND table_name='flyway_schema_history' "
                        + "AND grantee IN ('prizm_app', 'PUBLIC')",
                Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication "
                        + "FROM pg_catalog.pg_roles WHERE rolname='prizm_app'",
                Boolean.class)).isFalse();
    }

    private static boolean hasSequencePrivilege(JdbcTemplate jdbc, String sequence, String privilege) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT has_sequence_privilege('prizm_app', 'public." + sequence + "', ?)",
                Boolean.class,
                privilege));
    }
}
