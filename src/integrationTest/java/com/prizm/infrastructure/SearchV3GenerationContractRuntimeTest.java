package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.search.v3.indexing.exception.SearchV3InventoryActivationException;
import com.prizm.search.v3.indexing.exception.StaleSearchV3IndexingJobClaimException;
import com.prizm.search.v3.indexing.model.SearchV3GenerationBuildContract;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import com.prizm.search.v3.indexing.model.SearchV3IndexingPolicies;
import com.prizm.search.v3.indexing.repository.SearchV3GenerationContractRepository;
import com.prizm.search.v3.indexing.service.SearchV3GenerationContractService;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** V20 expected manifest의 current-claim 동결 경계를 실제 PostgreSQL에서 검증한다. */
@Testcontainers
class SearchV3GenerationContractRuntimeTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");
    private static final String MODEL_DIGEST = "a".repeat(64);
    private static final String MANIFEST_HASH = "b".repeat(64);
    private static final String DIFFERENT_MANIFEST_HASH = "c".repeat(64);
    private static final String PASSAGE_INPUT_HASH = "d".repeat(64);
    private static final String CHILD_INPUT_HASH = "e".repeat(64);
    private static final String SOURCE_HASH = "f".repeat(64);

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm_search_v3_generation_contract")
            .withUsername("prizm")
            .withPassword("prizm-test");

    @Test
    void freezesOnlyCurrentBuildingUnfrozenGenerationAndIsExactlyIdempotent() {
        RuntimeDatabase database = createRuntimeDatabase();
        Candidate candidate = createCandidate(database.jdbc(), createFixture(database.jdbc(), "freeze-owner"));

        assertThat(database.jdbc().queryForMap(
                """
                SELECT generation.status,
                       generation.expected_passage_count,
                       generation.expected_child_count,
                       generation.expected_manifest_sha256,
                       job.status AS job_status,
                       job.claim_version
                FROM search_v3_index_generations generation
                JOIN search_v3_indexing_jobs job ON job.generation_id = generation.id
                WHERE generation.id = ?
                """,
                candidate.generationId()))
                .containsEntry("status", "BUILDING")
                .containsEntry("expected_passage_count", null)
                .containsEntry("expected_child_count", null)
                .containsEntry("expected_manifest_sha256", null)
                .containsEntry("job_status", "PROCESSING")
                .containsEntry("claim_version", 1L);

        SearchV3GenerationBuildContract frozen = freeze(
                database, candidate.claim(), 2, 3, MANIFEST_HASH);

        assertThat(frozen.generationId()).isEqualTo(candidate.generationId());
        assertThat(frozen.status()).isEqualTo("BUILDING");
        assertThat(frozen.expectedPassageCount()).isEqualTo(2);
        assertThat(frozen.expectedChildCount()).isEqualTo(3);
        assertThat(frozen.expectedManifestSha256()).isEqualTo(MANIFEST_HASH);
        assertThat(frozen.manifestFrozen()).isTrue();

        database.jdbc().update(
                "UPDATE search_v3_index_generations SET updated_at = TIMESTAMPTZ '2000-01-01 00:00:00Z' WHERE id = ?",
                candidate.generationId());
        SearchV3GenerationBuildContract same = freeze(
                database, candidate.claim(), 2, 3, MANIFEST_HASH);
        assertThat(same).isEqualTo(frozen);
        assertThat(database.jdbc().queryForObject(
                """
                SELECT updated_at = TIMESTAMPTZ '2000-01-01 00:00:00Z'
                FROM search_v3_index_generations
                WHERE id = ?
                """,
                Boolean.class,
                candidate.generationId())).isTrue();

        ManifestValues[] differentValues = {
            new ManifestValues(4, 3, MANIFEST_HASH),
            new ManifestValues(2, 4, MANIFEST_HASH),
            new ManifestValues(2, 3, DIFFERENT_MANIFEST_HASH)
        };
        for (ManifestValues different : differentValues) {
            assertThatThrownBy(() -> freeze(
                    database,
                    candidate.claim(),
                    different.passageCount(),
                    different.childCount(),
                    different.sha256()))
                    .as("different frozen manifest %s", different)
                    .isInstanceOf(SearchV3InventoryActivationException.class)
                    .hasMessageContaining("already frozen with different values");
        }
        assertThat(database.jdbc().queryForMap(
                """
                SELECT expected_passage_count, expected_child_count, expected_manifest_sha256
                FROM search_v3_index_generations
                WHERE id = ?
                """,
                candidate.generationId()))
                .containsEntry("expected_passage_count", 2)
                .containsEntry("expected_child_count", 3)
                .containsEntry("expected_manifest_sha256", MANIFEST_HASH);
    }

    @Test
    void rejectsReadyAndDefensivelyRejectsPartialManifest() {
        RuntimeDatabase database = createRuntimeDatabase();
        Candidate ready = createCandidate(database.jdbc(), createFixture(database.jdbc(), "ready-owner"));
        freeze(database, ready.claim(), 1, 1, MANIFEST_HASH);
        database.jdbc().update(
                """
                UPDATE search_v3_index_generations
                SET status = 'READY', build_completed_at = now()
                WHERE id = ?
                """,
                ready.generationId());

        assertThatThrownBy(() -> freeze(database, ready.claim(), 1, 1, MANIFEST_HASH))
                .isInstanceOf(SearchV3InventoryActivationException.class)
                .hasMessageContaining("Only a BUILDING");

        Candidate partial = createCandidate(database.jdbc(), createFixture(database.jdbc(), "partial-owner"));
        database.jdbc().execute(
                "ALTER TABLE search_v3_index_generations DROP CONSTRAINT ck_s3_generations_manifest");
        database.jdbc().update(
                "UPDATE search_v3_index_generations SET expected_passage_count = 1 WHERE id = ?",
                partial.generationId());

        assertThatThrownBy(() -> freeze(database, partial.claim(), 1, 1, MANIFEST_HASH))
                .isInstanceOf(SearchV3InventoryActivationException.class)
                .hasMessageContaining("partially initialized");
        assertThat(database.jdbc().queryForMap(
                """
                SELECT expected_passage_count, expected_child_count, expected_manifest_sha256
                FROM search_v3_index_generations
                WHERE id = ?
                """,
                partial.generationId()))
                .containsEntry("expected_passage_count", 1)
                .containsEntry("expected_child_count", null)
                .containsEntry("expected_manifest_sha256", null);
    }

    @Test
    void rejectsStaleRecoveryLockedAndCrossLineageClaims() {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createFixture(database.jdbc(), "fenced-owner");
        Candidate candidate = createCandidate(database.jdbc(), fixture);
        Fixture foreign = createFixture(database.jdbc(), "foreign-owner");
        long otherGeneration = insertUnfrozenGeneration(database.jdbc(), fixture);

        SearchV3IndexingJobClaim[] invalidClaims = {
            withClaimVersion(candidate.claim(), candidate.claim().claimVersion() + 1),
            withOwner(candidate.claim(), foreign.ownerUserId()),
            withDocument(candidate.claim(), foreign.documentId()),
            withVersion(candidate.claim(), foreign.documentVersionId()),
            withGeneration(candidate.claim(), otherGeneration)
        };
        for (SearchV3IndexingJobClaim invalid : invalidClaims) {
            assertThatThrownBy(() -> freeze(database, invalid, 1, 1, MANIFEST_HASH))
                    .as("invalid full claim %s", invalid)
                    .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);
        }

        database.jdbc().update(
                """
                UPDATE search_v3_indexing_jobs
                SET recovery_lock_token = ?, recovery_locked_at = now()
                WHERE id = ?
                """,
                UUID.randomUUID(),
                candidate.jobId());
        assertThatThrownBy(() -> freeze(database, candidate.claim(), 1, 1, MANIFEST_HASH))
                .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);
        assertThat(database.jdbc().queryForObject(
                "SELECT expected_manifest_sha256 FROM search_v3_index_generations WHERE id = ?",
                String.class,
                candidate.generationId())).isNull();
    }

    @Test
    void requiresAllFourInventoryTablesToBeEmptyBeforeFreeze() {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createFixture(database.jdbc(), "inventory-owner");
        Candidate candidate = createCandidate(database.jdbc(), fixture);

        assertThat(database.repository().countInventory(candidate.claim())).isZero();

        long passage = insertPassage(database.jdbc(), fixture, candidate.generationId());
        assertInventoryBlocksFreeze(database, candidate, 1L);

        long child = insertChild(database.jdbc(), fixture, candidate.generationId(), passage);
        assertInventoryBlocksFreeze(database, candidate, 2L);

        insertPassageVector(database.jdbc(), fixture, candidate.generationId(), passage);
        assertInventoryBlocksFreeze(database, candidate, 3L);

        insertChildVector(database.jdbc(), fixture, candidate.generationId(), child);
        assertInventoryBlocksFreeze(database, candidate, 4L);

        assertThat(database.jdbc().queryForMap(
                """
                SELECT expected_passage_count, expected_child_count, expected_manifest_sha256
                FROM search_v3_index_generations
                WHERE id = ?
                """,
                candidate.generationId()))
                .containsEntry("expected_passage_count", null)
                .containsEntry("expected_child_count", null)
                .containsEntry("expected_manifest_sha256", null);
    }

    private RuntimeDatabase createRuntimeDatabase() {
        String databaseName = "prizm_s3_contract_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        admin.execute("CREATE DATABASE " + databaseName);
        String url = postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + databaseName);
        DataSource dataSource = new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        SearchV3GenerationContractRepository repository = new SearchV3GenerationContractRepository(jdbc);
        SearchV3GenerationContractService service = new SearchV3GenerationContractService(repository);
        return new RuntimeDatabase(dataSource, jdbc, repository, service);
    }

    private Fixture createFixture(JdbcTemplate jdbc, String label) {
        long owner = jdbc.queryForObject(
                """
                INSERT INTO users(email, password_hash, role, enabled)
                VALUES (?, 'not-used', 'USER', TRUE) RETURNING id
                """,
                Long.class,
                label + "-" + UUID.randomUUID() + "@example.com");
        long document = jdbc.queryForObject(
                "INSERT INTO documents(owner_user_id, title, document_type) VALUES (?, ?, 'OTHER') RETURNING id",
                Long.class,
                owner,
                label);
        long version = jdbc.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path,
                    file_type, content_hash, status
                ) VALUES (?, ?, 1, 'fixture.txt', 'test/fixture.txt', 'TXT', ?, 'PROCESSING')
                RETURNING id
                """,
                Long.class,
                owner,
                document,
                "0".repeat(64));
        return new Fixture(owner, document, version);
    }

    private Candidate createCandidate(JdbcTemplate jdbc, Fixture fixture) {
        long generation = insertUnfrozenGeneration(jdbc, fixture);
        long job = jdbc.queryForObject(
                """
                INSERT INTO search_v3_indexing_jobs(
                    generation_id, owner_user_id, document_id, document_version_id,
                    status, claim_version, attempt_count, lease_expires_at, started_at
                ) VALUES (?, ?, ?, ?, 'PROCESSING', 1, 1, now() + interval '10 minutes', now())
                RETURNING id
                """,
                Long.class,
                generation,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId());
        Instant leaseExpiresAt = jdbc.queryForObject(
                "SELECT lease_expires_at FROM search_v3_indexing_jobs WHERE id = ?",
                java.sql.Timestamp.class,
                job).toInstant();
        SearchV3IndexingJobClaim claim = new SearchV3IndexingJobClaim(
                job,
                generation,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                1,
                1,
                leaseExpiresAt);
        return new Candidate(generation, job, claim);
    }

    private long insertUnfrozenGeneration(JdbcTemplate jdbc, Fixture fixture) {
        return jdbc.queryForObject(
                """
                INSERT INTO search_v3_index_generations(
                    owner_user_id, document_id, document_version_id, status,
                    structure_policy_version, passage_policy_version, child_policy_version,
                    embedding_model_id, resolved_model_digest, embedding_dimension,
                    passage_input_policy_version, child_input_policy_version
                ) VALUES (?, ?, ?, 'BUILDING', ?, ?, ?, 'bge-m3', ?, 1024, ?, ?)
                RETURNING id
                """,
                Long.class,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                SearchV3IndexingPolicies.STRUCTURE,
                SearchV3IndexingPolicies.PASSAGE,
                SearchV3IndexingPolicies.CHILD,
                MODEL_DIGEST,
                SearchV3IndexingPolicies.PASSAGE_INPUT,
                SearchV3IndexingPolicies.CHILD_INPUT);
    }

    private long insertPassage(JdbcTemplate jdbc, Fixture fixture, long generation) {
        return jdbc.queryForObject(
                """
                INSERT INTO search_v3_retrieval_passages(
                    generation_id, owner_user_id, document_id, document_version_id,
                    passage_key, passage_order, source_text, retrieval_text, retrieval_text_sha256,
                    source_path, page_no, line_start, line_end, code_point_start, code_point_end,
                    parent_annotation_candidate_id, document_source_sha256, source_block_ids
                ) VALUES (?, ?, ?, ?, 'passage-1', 0, 'source text', 'retrieval text', ?,
                    'test/fixture.txt', NULL, 1, 1, 0, 11, 'parent-1', ?, '["block-1"]'::jsonb)
                RETURNING id
                """,
                Long.class,
                generation,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                PASSAGE_INPUT_HASH,
                SOURCE_HASH);
    }

    private long insertChild(JdbcTemplate jdbc, Fixture fixture, long generation, long passage) {
        return jdbc.queryForObject(
                """
                INSERT INTO search_v3_evidence_children(
                    generation_id, owner_user_id, document_id, document_version_id, passage_id,
                    child_key, child_order, passage_child_order, source_block_type,
                    source_text, source_text_sha256, source_path, page_no,
                    line_start, line_end, code_point_start, code_point_end,
                    source_block_id, parent_annotation_candidate_id,
                    document_source_sha256, source_block_ids
                ) VALUES (?, ?, ?, ?, ?, 'child-1', 0, 0, 'PARAGRAPH',
                    'source text', ?, 'test/fixture.txt', NULL,
                    1, 1, 0, 11, 'block-1', 'parent-1', ?, '["block-1"]'::jsonb)
                RETURNING id
                """,
                Long.class,
                generation,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                passage,
                CHILD_INPUT_HASH,
                SOURCE_HASH);
    }

    private void insertPassageVector(JdbcTemplate jdbc, Fixture fixture, long generation, long passage) {
        jdbc.update(
                """
                INSERT INTO search_v3_passage_embeddings(
                    passage_id, generation_id, owner_user_id, document_id, document_version_id,
                    input_sha256, embedding_model_id, resolved_model_digest,
                    embedding_dimension, input_policy_version, embedding
                ) VALUES (?, ?, ?, ?, ?, ?, 'bge-m3', ?, 1024, ?,
                    array_prepend(1::real, array_fill(0::real, ARRAY[1023]))::vector)
                """,
                passage,
                generation,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                PASSAGE_INPUT_HASH,
                MODEL_DIGEST,
                SearchV3IndexingPolicies.PASSAGE_INPUT);
    }

    private void insertChildVector(JdbcTemplate jdbc, Fixture fixture, long generation, long child) {
        jdbc.update(
                """
                INSERT INTO search_v3_child_embeddings(
                    child_id, generation_id, owner_user_id, document_id, document_version_id,
                    input_sha256, embedding_model_id, resolved_model_digest,
                    embedding_dimension, input_policy_version, embedding
                ) VALUES (?, ?, ?, ?, ?, ?, 'bge-m3', ?, 1024, ?,
                    array_prepend(1::real, array_fill(0::real, ARRAY[1023]))::vector)
                """,
                child,
                generation,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                CHILD_INPUT_HASH,
                MODEL_DIGEST,
                SearchV3IndexingPolicies.CHILD_INPUT);
    }

    private void assertInventoryBlocksFreeze(RuntimeDatabase database, Candidate candidate, long expectedCount) {
        assertThat(database.repository().countInventory(candidate.claim())).isEqualTo(expectedCount);
        assertThatThrownBy(() -> freeze(database, candidate.claim(), 1, 1, MANIFEST_HASH))
                .isInstanceOf(SearchV3InventoryActivationException.class)
                .hasMessageContaining("before artifact storage");
    }

    private SearchV3GenerationBuildContract freeze(
            RuntimeDatabase database,
            SearchV3IndexingJobClaim claim,
            int passageCount,
            int childCount,
            String manifestSha256) {
        return transaction(database, () -> database.service().freezeExpectedManifest(
                claim, passageCount, childCount, manifestSha256));
    }

    private <T> T transaction(RuntimeDatabase database, Callable<T> operation) {
        TransactionTemplate transaction = new TransactionTemplate(
                new DataSourceTransactionManager(database.dataSource()));
        return transaction.execute(status -> {
            try {
                return operation.call();
            }
            catch (RuntimeException exception) {
                throw exception;
            }
            catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private SearchV3IndexingJobClaim withGeneration(SearchV3IndexingJobClaim claim, long generationId) {
        return new SearchV3IndexingJobClaim(
                claim.jobId(), generationId, claim.ownerUserId(), claim.documentId(), claim.documentVersionId(),
                claim.claimVersion(), claim.attemptCount(), claim.leaseExpiresAt());
    }

    private SearchV3IndexingJobClaim withOwner(SearchV3IndexingJobClaim claim, long ownerUserId) {
        return new SearchV3IndexingJobClaim(
                claim.jobId(), claim.generationId(), ownerUserId, claim.documentId(), claim.documentVersionId(),
                claim.claimVersion(), claim.attemptCount(), claim.leaseExpiresAt());
    }

    private SearchV3IndexingJobClaim withDocument(SearchV3IndexingJobClaim claim, long documentId) {
        return new SearchV3IndexingJobClaim(
                claim.jobId(), claim.generationId(), claim.ownerUserId(), documentId, claim.documentVersionId(),
                claim.claimVersion(), claim.attemptCount(), claim.leaseExpiresAt());
    }

    private SearchV3IndexingJobClaim withVersion(SearchV3IndexingJobClaim claim, long versionId) {
        return new SearchV3IndexingJobClaim(
                claim.jobId(), claim.generationId(), claim.ownerUserId(), claim.documentId(), versionId,
                claim.claimVersion(), claim.attemptCount(), claim.leaseExpiresAt());
    }

    private SearchV3IndexingJobClaim withClaimVersion(SearchV3IndexingJobClaim claim, long claimVersion) {
        return new SearchV3IndexingJobClaim(
                claim.jobId(), claim.generationId(), claim.ownerUserId(), claim.documentId(), claim.documentVersionId(),
                claimVersion, claim.attemptCount(), claim.leaseExpiresAt());
    }

    private record Fixture(long ownerUserId, long documentId, long documentVersionId) {}

    private record Candidate(long generationId, long jobId, SearchV3IndexingJobClaim claim) {}

    private record ManifestValues(int passageCount, int childCount, String sha256) {}

    private record RuntimeDatabase(
            DataSource dataSource,
            JdbcTemplate jdbc,
            SearchV3GenerationContractRepository repository,
            SearchV3GenerationContractService service) {}
}
