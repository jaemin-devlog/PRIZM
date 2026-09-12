package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.prizm.search.v3.indexing.exception.SearchV3InventoryActivationException;
import com.prizm.search.v3.indexing.exception.StaleSearchV3IndexingJobClaimException;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.ChildRow;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.PassageRow;
import com.prizm.search.v3.indexing.service.SearchV3InventoryActivationService;
import com.prizm.search.v3.indexing.service.SearchV3InventoryVerifier;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Search V3 exact inventory READY와 같은-version 원자 활성화를 실제 PostgreSQL에서 검증한다. */
@Testcontainers
class SearchV3InventoryActivationRuntimeTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");
    private static final String MODEL_DIGEST = "a".repeat(64);
    private static final String DOCUMENT_SOURCE_HASH = "f".repeat(64);
    private static final String EXPECTED_LOGICAL_MANIFEST_SHA256 =
            "fe4b2c577c38d6b76bf01d485133043625f2344bb0da79e95076f13758f019f1";
    private static final String PASSAGE_ONE_TEXT = "Alpha summary.\nReduced queue latency.";
    private static final String PASSAGE_TWO_TEXT = "Beta summary.\nImproved support workflow.";
    private static final String CHILD_ONE_TEXT = "Reduced queue latency.";
    private static final String CHILD_TWO_TEXT = "Improved support workflow.";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm_search_v3_inventory_activation")
            .withUsername("prizm")
            .withPassword("prizm-test");

    private final List<HikariDataSource> runtimeDataSources = new ArrayList<>();

    @AfterEach
    void closeRuntimeDataSources() {
        runtimeDataSources.forEach(HikariDataSource::close);
        runtimeDataSources.clear();
    }

    @Test
    void independentlyFrozenManifestMarksReadyAndPerformsFirstShadowActivation() {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createActiveFixture(database.jdbc(), "first-activation-owner");
        assertFrozenExpectedManifest(database.verifier());
        Generation generation = insertGenerationWithInventory(database, fixture);

        SearchV3InventoryActivationService.ReadyResult ready = transaction(
                database, () -> database.service().markReady(generation.claim()));

        assertThat(ready.logicalManifestSha256()).isEqualTo(EXPECTED_LOGICAL_MANIFEST_SHA256);
        assertThat(ready.verifiedInventorySha256()).hasSize(64);
        assertThat(ready.passageCount()).isEqualTo(2);
        assertThat(ready.childCount()).isEqualTo(2);
        assertThat(status(database.jdbc(), "search_v3_index_generations", generation.generationId()))
                .isEqualTo("READY");
        assertThat(status(database.jdbc(), "search_v3_indexing_jobs", generation.claim().jobId()))
                .isEqualTo("PROCESSING");

        long activeVersionBefore = activeVersion(database.jdbc(), fixture.documentId());
        SearchV3InventoryActivationService.ActivationResult activated = transaction(
                database, () -> database.service().activate(generation.claim()));

        assertThat(activated.generationId()).isEqualTo(generation.generationId());
        assertThat(activated.supersededGenerationId()).isNull();
        assertThat(status(database.jdbc(), "search_v3_index_generations", generation.generationId()))
                .isEqualTo("ACTIVE");
        assertThat(status(database.jdbc(), "search_v3_indexing_jobs", generation.claim().jobId()))
                .isEqualTo("COMPLETED");
        assertThat(activeSearchV3Generation(database.jdbc(), fixture.documentId()))
                .isEqualTo(generation.generationId());
        assertThat(activeVersion(database.jdbc(), fixture.documentId())).isEqualTo(activeVersionBefore);
    }

    @Test
    void rejectsSameCountLogicalMismatchAndMissingOrMismatchedVectors() {
        RuntimeDatabase passageDatabase = createRuntimeDatabase();
        Fixture passageFixture = createActiveFixture(passageDatabase.jdbc(), "passage-mismatch-owner");
        Generation passageGeneration = insertGenerationWithInventory(passageDatabase, passageFixture);
        passageDatabase.jdbc().update(
                "UPDATE search_v3_retrieval_passages SET source_path = 'changed.txt' WHERE id = ?",
                passageGeneration.inventory().firstPassageId());
        assertInventoryRejected(passageDatabase, passageGeneration.claim());

        RuntimeDatabase childDatabase = createRuntimeDatabase();
        Fixture childFixture = createActiveFixture(childDatabase.jdbc(), "child-mismatch-owner");
        Generation childGeneration = insertGenerationWithInventory(childDatabase, childFixture);
        childDatabase.jdbc().update(
                """
                UPDATE search_v3_evidence_children
                SET passage_id = ?, passage_child_order = 1
                WHERE id = ?
                """,
                childGeneration.inventory().secondPassageId(),
                childGeneration.inventory().firstChildId());
        assertInventoryRejected(childDatabase, childGeneration.claim());

        RuntimeDatabase missingVectorDatabase = createRuntimeDatabase();
        Fixture missingVectorFixture = createActiveFixture(
                missingVectorDatabase.jdbc(), "missing-vector-owner");
        Generation missingVectorGeneration = insertGenerationWithInventory(
                missingVectorDatabase, missingVectorFixture);
        missingVectorDatabase.jdbc().update(
                "DELETE FROM search_v3_child_embeddings WHERE child_id = ?",
                missingVectorGeneration.inventory().firstChildId());
        assertInventoryRejected(missingVectorDatabase, missingVectorGeneration.claim());

        RuntimeDatabase mismatchDatabase = createRuntimeDatabase();
        Fixture mismatchFixture = createActiveFixture(mismatchDatabase.jdbc(), "vector-mismatch-owner");
        Generation mismatchGeneration = insertGenerationWithInventory(mismatchDatabase, mismatchFixture);
        assertThatThrownBy(() -> mismatchDatabase.jdbc().update(
                "UPDATE search_v3_child_embeddings SET input_sha256 = ? WHERE child_id = ?",
                "e".repeat(64),
                mismatchGeneration.inventory().firstChildId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void readyRejectsStaleClaimAndRecoveryLockedClaim() {
        RuntimeDatabase staleDatabase = createRuntimeDatabase();
        Fixture staleFixture = createActiveFixture(staleDatabase.jdbc(), "stale-ready-owner");
        Generation staleGeneration = insertGenerationWithInventory(staleDatabase, staleFixture);
        SearchV3IndexingJobClaim stale = withClaimVersion(
                staleGeneration.claim(), staleGeneration.claim().claimVersion() + 1);

        assertThatThrownBy(() -> transaction(staleDatabase, () -> staleDatabase.service().markReady(stale)))
                .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);

        RuntimeDatabase recoveryDatabase = createRuntimeDatabase();
        Fixture recoveryFixture = createActiveFixture(recoveryDatabase.jdbc(), "recovery-ready-owner");
        Generation recoveryGeneration = insertGenerationWithInventory(recoveryDatabase, recoveryFixture);
        lockRecovery(recoveryDatabase.jdbc(), recoveryGeneration.claim().jobId());

        assertThatThrownBy(() -> transaction(
                recoveryDatabase,
                () -> recoveryDatabase.service().markReady(recoveryGeneration.claim())))
                .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);
    }

    @Test
    void activationRejectsPostReadyMutationAndProductionVersionBoundaryViolations() {
        RuntimeDatabase changedDatabase = createRuntimeDatabase();
        Fixture changedFixture = createActiveFixture(changedDatabase.jdbc(), "changed-ready-owner");
        Generation changedGeneration = insertGenerationWithInventory(changedDatabase, changedFixture);
        markReady(changedDatabase, changedGeneration);
        changedDatabase.jdbc().update(
                "UPDATE search_v3_child_embeddings SET embedding = CAST(? AS vector) WHERE child_id = ?",
                unitVector(1),
                changedGeneration.inventory().firstChildId());

        assertThatThrownBy(() -> transaction(
                changedDatabase,
                () -> changedDatabase.service().activate(changedGeneration.claim())))
                .isInstanceOf(SearchV3InventoryActivationException.class)
                .hasMessageContaining("changed after verification");
        assertThat(status(changedDatabase.jdbc(), "search_v3_index_generations", changedGeneration.generationId()))
                .isEqualTo("READY");

        RuntimeDatabase otherVersionDatabase = createRuntimeDatabase();
        Fixture otherVersionFixture = createActiveFixture(otherVersionDatabase.jdbc(), "other-version-owner");
        Generation otherVersionGeneration = insertGenerationWithInventory(
                otherVersionDatabase, otherVersionFixture);
        markReady(otherVersionDatabase, otherVersionGeneration);
        long otherVersion = insertActiveVersion(
                otherVersionDatabase.jdbc(), otherVersionFixture, 2, "other-version.txt");
        otherVersionDatabase.jdbc().update(
                "UPDATE documents SET active_version_id = ? WHERE id = ?",
                otherVersion,
                otherVersionFixture.documentId());

        assertActivationRejected(otherVersionDatabase, otherVersionGeneration.claim());
        assertThat(activeVersion(otherVersionDatabase.jdbc(), otherVersionFixture.documentId()))
                .isEqualTo(otherVersion);

        RuntimeDatabase nullVersionDatabase = createRuntimeDatabase();
        Fixture nullVersionFixture = createActiveFixture(nullVersionDatabase.jdbc(), "null-version-owner");
        Generation nullVersionGeneration = insertGenerationWithInventory(nullVersionDatabase, nullVersionFixture);
        markReady(nullVersionDatabase, nullVersionGeneration);
        nullVersionDatabase.jdbc().update(
                "UPDATE documents SET active_version_id = NULL WHERE id = ?",
                nullVersionFixture.documentId());

        assertActivationRejected(nullVersionDatabase, nullVersionGeneration.claim());
        assertThat(nullVersionDatabase.jdbc().queryForObject(
                "SELECT active_version_id FROM documents WHERE id = ?",
                Long.class,
                nullVersionFixture.documentId())).isNull();

        RuntimeDatabase wrongPointerDatabase = createRuntimeDatabase();
        Fixture wrongPointerFixture = createActiveFixture(wrongPointerDatabase.jdbc(), "wrong-pointer-owner");
        Generation currentGeneration = insertGenerationWithInventory(wrongPointerDatabase, wrongPointerFixture);
        markReady(wrongPointerDatabase, currentGeneration);
        transaction(wrongPointerDatabase, () -> wrongPointerDatabase.service().activate(currentGeneration.claim()));
        wrongPointerDatabase.jdbc().update(
                "UPDATE documents SET active_search_v3_generation_id = NULL WHERE id = ?",
                wrongPointerFixture.documentId());
        Generation replacementGeneration = insertGenerationWithInventory(
                wrongPointerDatabase, wrongPointerFixture);
        markReady(wrongPointerDatabase, replacementGeneration);

        assertActivationRejected(wrongPointerDatabase, replacementGeneration.claim());
        assertThat(status(
                        wrongPointerDatabase.jdbc(),
                        "search_v3_index_generations",
                        currentGeneration.generationId()))
                .isEqualTo("ACTIVE");
    }

    @Test
    void sameVersionReindexSupersedesPreviousGenerationWithoutChangingActiveVersion() {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createActiveFixture(database.jdbc(), "same-version-owner");
        Generation first = insertGenerationWithInventory(database, fixture);
        markReady(database, first);
        transaction(database, () -> database.service().activate(first.claim()));

        Generation second = insertGenerationWithInventory(database, fixture);
        markReady(database, second);
        long activeVersionBefore = activeVersion(database.jdbc(), fixture.documentId());
        SearchV3InventoryActivationService.ActivationResult result = transaction(
                database, () -> database.service().activate(second.claim()));

        assertThat(result.supersededGenerationId()).isEqualTo(first.generationId());
        assertThat(status(database.jdbc(), "search_v3_index_generations", first.generationId()))
                .isEqualTo("SUPERSEDED");
        assertThat(status(database.jdbc(), "search_v3_index_generations", second.generationId()))
                .isEqualTo("ACTIVE");
        assertThat(status(database.jdbc(), "search_v3_indexing_jobs", second.claim().jobId()))
                .isEqualTo("COMPLETED");
        assertThat(activeSearchV3Generation(database.jdbc(), fixture.documentId()))
                .isEqualTo(second.generationId());
        assertThat(activeVersion(database.jdbc(), fixture.documentId())).isEqualTo(activeVersionBefore);
        assertThat(database.jdbc().queryForObject(
                """
                SELECT COUNT(*) FROM search_v3_index_generations
                WHERE owner_user_id = ? AND document_id = ? AND status = 'ACTIVE'
                """,
                Long.class,
                fixture.ownerUserId(),
                fixture.documentId())).isEqualTo(1L);
    }

    @Test
    void productionVersionSwitchOrDeletionSupersedesOldV3AndClearsPointerWithoutBeingBlocked() {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createActiveFixture(database.jdbc(), "production-version-switch-owner");
        Generation generation = insertGenerationWithInventory(database, fixture);
        markReady(database, generation);
        transaction(database, () -> database.service().activate(generation.claim()));
        long replacementVersion = insertActiveVersion(database.jdbc(), fixture, 2, "replacement.txt");

        assertThat(database.jdbc().update(
                "UPDATE documents SET active_version_id = ? WHERE id = ?",
                replacementVersion,
                fixture.documentId())).isEqualTo(1);

        assertThat(activeVersion(database.jdbc(), fixture.documentId())).isEqualTo(replacementVersion);
        assertThat(activeSearchV3Generation(database.jdbc(), fixture.documentId())).isNull();
        assertThat(status(database.jdbc(), "search_v3_index_generations", generation.generationId()))
                .isEqualTo("SUPERSEDED");
        assertThat(database.jdbc().queryForObject(
                """
                SELECT COUNT(*) FROM search_v3_index_generations
                WHERE owner_user_id = ? AND document_id = ? AND status = 'ACTIVE'
                """,
                Long.class,
                fixture.ownerUserId(),
                fixture.documentId())).isZero();

        Fixture replacementFixture = new Fixture(
                fixture.ownerUserId(), fixture.documentId(), replacementVersion);
        Generation replacementGeneration = insertGenerationWithInventory(database, replacementFixture);
        markReady(database, replacementGeneration);
        transaction(database, () -> database.service().activate(replacementGeneration.claim()));

        assertThat(database.jdbc().update(
                "UPDATE documents SET active_version_id = NULL WHERE id = ?",
                fixture.documentId())).isEqualTo(1);

        assertThat(database.jdbc().queryForObject(
                "SELECT active_version_id FROM documents WHERE id = ?",
                Long.class,
                fixture.documentId())).isNull();
        assertThat(activeSearchV3Generation(database.jdbc(), fixture.documentId())).isNull();
        assertThat(status(
                        database.jdbc(),
                        "search_v3_index_generations",
                        replacementGeneration.generationId()))
                .isEqualTo("SUPERSEDED");
    }

    @Test
    void activationRejectsStaleAndRecoveryLockedClaims() {
        RuntimeDatabase staleDatabase = createRuntimeDatabase();
        Fixture staleFixture = createActiveFixture(staleDatabase.jdbc(), "stale-activation-owner");
        Generation staleGeneration = insertGenerationWithInventory(staleDatabase, staleFixture);
        markReady(staleDatabase, staleGeneration);
        SearchV3IndexingJobClaim stale = withClaimVersion(
                staleGeneration.claim(), staleGeneration.claim().claimVersion() + 1);

        assertThatThrownBy(() -> transaction(staleDatabase, () -> staleDatabase.service().activate(stale)))
                .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);

        RuntimeDatabase recoveryDatabase = createRuntimeDatabase();
        Fixture recoveryFixture = createActiveFixture(recoveryDatabase.jdbc(), "recovery-activation-owner");
        Generation recoveryGeneration = insertGenerationWithInventory(recoveryDatabase, recoveryFixture);
        markReady(recoveryDatabase, recoveryGeneration);
        lockRecovery(recoveryDatabase.jdbc(), recoveryGeneration.claim().jobId());

        assertThatThrownBy(() -> transaction(
                recoveryDatabase,
                () -> recoveryDatabase.service().activate(recoveryGeneration.claim())))
                .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);
    }

    @Test
    void activationFailureRollsBackGenerationJobAndPointer() {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createActiveFixture(database.jdbc(), "rollback-owner");
        Generation current = insertGenerationWithInventory(database, fixture);
        markReady(database, current);
        transaction(database, () -> database.service().activate(current.claim()));
        Generation candidate = insertGenerationWithInventory(database, fixture);
        markReady(database, candidate);
        long activeVersionBefore = activeVersion(database.jdbc(), fixture.documentId());
        installPointerFailureTrigger(database.jdbc());

        assertThatThrownBy(() -> transaction(
                database, () -> database.service().activate(candidate.claim())))
                .isInstanceOf(RuntimeException.class);

        assertThat(status(database.jdbc(), "search_v3_index_generations", current.generationId()))
                .isEqualTo("ACTIVE");
        assertThat(status(database.jdbc(), "search_v3_index_generations", candidate.generationId()))
                .isEqualTo("READY");
        assertThat(status(database.jdbc(), "search_v3_indexing_jobs", current.claim().jobId()))
                .isEqualTo("COMPLETED");
        assertThat(status(database.jdbc(), "search_v3_indexing_jobs", candidate.claim().jobId()))
                .isEqualTo("PROCESSING");
        assertThat(activeSearchV3Generation(database.jdbc(), fixture.documentId()))
                .isEqualTo(current.generationId());
        assertThat(activeVersion(database.jdbc(), fixture.documentId())).isEqualTo(activeVersionBefore);
        assertThat(database.jdbc().queryForObject(
                """
                SELECT COUNT(*) FROM search_v3_index_generations
                WHERE owner_user_id = ? AND document_id = ? AND status = 'ACTIVE'
                """,
                Long.class,
                fixture.ownerUserId(),
                fixture.documentId())).isEqualTo(1L);
    }

    @Test
    void concurrentActivationOfOneReadyGenerationHasOneWinnerAndNoPartialState() throws Exception {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createActiveFixture(database.jdbc(), "concurrent-activation-owner");
        Generation generation = insertGenerationWithInventory(database, fixture);
        markReady(database, generation);
        long activeVersionBefore = activeVersion(database.jdbc(), fixture.documentId());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(
                    () -> attemptActivation(database, generation.claim(), start));
            Future<Boolean> second = executor.submit(
                    () -> attemptActivation(database, generation.claim(), start));
            start.countDown();

            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(true, false);
        }
        finally {
            executor.shutdownNow();
        }

        assertThat(database.jdbc().queryForObject(
                """
                SELECT COUNT(*) FROM search_v3_index_generations
                WHERE owner_user_id = ? AND document_id = ? AND status = 'ACTIVE'
                """,
                Long.class,
                fixture.ownerUserId(),
                fixture.documentId())).isEqualTo(1L);
        assertThat(status(database.jdbc(), "search_v3_index_generations", generation.generationId()))
                .isEqualTo("ACTIVE");
        assertThat(status(database.jdbc(), "search_v3_indexing_jobs", generation.claim().jobId()))
                .isEqualTo("COMPLETED");
        assertThat(activeSearchV3Generation(database.jdbc(), fixture.documentId()))
                .isEqualTo(generation.generationId());
        assertThat(activeVersion(database.jdbc(), fixture.documentId())).isEqualTo(activeVersionBefore);
    }

    @Test
    void concurrentDistinctReadyGenerationsSerializeWithoutPartialState() throws Exception {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createActiveFixture(database.jdbc(), "distinct-activation-owner");
        Generation first = insertGenerationWithInventory(database, fixture);
        Generation second = insertGenerationWithInventory(database, fixture);
        markReady(database, first);
        markReady(database, second);
        long activeVersionBefore = activeVersion(database.jdbc(), fixture.documentId());
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Long> firstActivation = executor.submit(() -> {
                await(start);
                return transaction(
                                database,
                                () -> database.service().activate(first.claim()))
                        .generationId();
            });
            Future<Long> secondActivation = executor.submit(() -> {
                await(start);
                return transaction(
                                database,
                                () -> database.service().activate(second.claim()))
                        .generationId();
            });
            start.countDown();

            assertThat(List.of(
                            firstActivation.get(10, TimeUnit.SECONDS),
                            secondActivation.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(first.generationId(), second.generationId());
        }
        finally {
            executor.shutdownNow();
        }

        long activeGeneration = activeSearchV3Generation(database.jdbc(), fixture.documentId());
        long supersededGeneration = activeGeneration == first.generationId()
                ? second.generationId()
                : first.generationId();
        assertThat(activeGeneration).isIn(first.generationId(), second.generationId());
        assertThat(status(database.jdbc(), "search_v3_index_generations", activeGeneration))
                .isEqualTo("ACTIVE");
        assertThat(status(database.jdbc(), "search_v3_index_generations", supersededGeneration))
                .isEqualTo("SUPERSEDED");
        assertThat(status(database.jdbc(), "search_v3_indexing_jobs", first.claim().jobId()))
                .isEqualTo("COMPLETED");
        assertThat(status(database.jdbc(), "search_v3_indexing_jobs", second.claim().jobId()))
                .isEqualTo("COMPLETED");
        assertThat(database.jdbc().queryForObject(
                """
                SELECT COUNT(*) FROM search_v3_index_generations
                WHERE owner_user_id = ? AND document_id = ? AND status = 'ACTIVE'
                """,
                Long.class,
                fixture.ownerUserId(),
                fixture.documentId())).isEqualTo(1L);
        assertThat(activeVersion(database.jdbc(), fixture.documentId())).isEqualTo(activeVersionBefore);
    }

    @Test
    void lifecycleVersionClearLockWinsAndV3ActivationFailsFastWithoutDeadlockOrPartialState()
            throws Exception {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createActiveFixture(database.jdbc(), "delete-activation-race-owner");
        Generation generation = insertGenerationWithInventory(database, fixture);
        markReady(database, generation);
        CountDownLatch documentLocked = new CountDownLatch(1);
        CountDownLatch activationFinished = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> deletion = executor.submit(() -> transaction(database, () -> {
                long lockedDocument = database.jdbc().queryForObject(
                        "SELECT id FROM documents WHERE id = ? FOR UPDATE",
                        Long.class,
                        fixture.documentId());
                assertThat(lockedDocument).isEqualTo(fixture.documentId());
                documentLocked.countDown();
                await(activationFinished);
                return database.jdbc().update(
                        "UPDATE documents SET active_version_id = NULL WHERE id = ?",
                        fixture.documentId()) == 1;
            }));
            Future<SearchV3InventoryActivationException> activationRejected = executor.submit(() -> {
                await(documentLocked);
                try {
                    transaction(database, () -> database.service().activate(generation.claim()));
                    return null;
                }
                catch (SearchV3InventoryActivationException exception) {
                    return exception;
                }
                finally {
                    activationFinished.countDown();
                }
            });

            assertThat(activationRejected.get(10, TimeUnit.SECONDS))
                    .isNotNull()
                    .hasMessage("Document is locked by another lifecycle transaction.");
            assertThat(deletion.get(10, TimeUnit.SECONDS)).isTrue();
        }
        finally {
            activationFinished.countDown();
            executor.shutdownNow();
        }

        assertThat(status(database.jdbc(), "search_v3_index_generations", generation.generationId()))
                .isEqualTo("READY");
        assertThat(status(database.jdbc(), "search_v3_indexing_jobs", generation.claim().jobId()))
                .isEqualTo("PROCESSING");
        assertThat(database.jdbc().queryForObject(
                "SELECT active_version_id FROM documents WHERE id = ?",
                Long.class,
                fixture.documentId())).isNull();
        assertThat(activeSearchV3Generation(database.jdbc(), fixture.documentId())).isNull();
        assertThat(database.jdbc().queryForObject(
                """
                SELECT COUNT(*) FROM search_v3_index_generations
                WHERE owner_user_id = ? AND document_id = ? AND status = 'ACTIVE'
                """,
                Long.class,
                fixture.ownerUserId(),
                fixture.documentId())).isZero();
    }

    private RuntimeDatabase createRuntimeDatabase() {
        String databaseName = "prizm_s3_activation_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        admin.execute("CREATE DATABASE " + databaseName);
        String url = postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + databaseName);
        HikariConfig poolConfig = new HikariConfig();
        poolConfig.setJdbcUrl(url);
        poolConfig.setUsername(postgres.getUsername());
        poolConfig.setPassword(postgres.getPassword());
        poolConfig.setMaximumPoolSize(4);
        poolConfig.setMinimumIdle(0);
        poolConfig.setPoolName("prz039-" + databaseName);
        HikariDataSource dataSource = new HikariDataSource(poolConfig);
        runtimeDataSources.add(dataSource);
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        SearchV3InventoryActivationRepository repository = new SearchV3InventoryActivationRepository(jdbc);
        SearchV3InventoryVerifier verifier = new SearchV3InventoryVerifier();
        SearchV3InventoryActivationService service = new SearchV3InventoryActivationService(repository, verifier);
        return new RuntimeDatabase(dataSource, jdbc, verifier, service);
    }

    private Fixture createActiveFixture(JdbcTemplate jdbc, String label) {
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
                ) VALUES (?, ?, 1, 'fixture.txt', 'test/fixture.txt', 'TXT', ?, 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                owner,
                document,
                DOCUMENT_SOURCE_HASH);
        jdbc.update("UPDATE documents SET active_version_id = ? WHERE id = ?", version, document);
        return new Fixture(owner, document, version);
    }

    private long insertActiveVersion(JdbcTemplate jdbc, Fixture fixture, int versionNo, String fileName) {
        return jdbc.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path,
                    file_type, content_hash, status
                ) VALUES (?, ?, ?, ?, ?, 'TXT', ?, 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                fixture.ownerUserId(),
                fixture.documentId(),
                versionNo,
                fileName,
                "test/" + fileName,
                "e".repeat(64));
    }

    private Generation insertGenerationWithInventory(RuntimeDatabase database, Fixture fixture) {
        long generationId = database.jdbc().queryForObject(
                """
                INSERT INTO search_v3_index_generations(
                    owner_user_id, document_id, document_version_id, status,
                    structure_policy_version, passage_policy_version, child_policy_version,
                    embedding_model_id, resolved_model_digest, embedding_dimension,
                    passage_input_policy_version, child_input_policy_version,
                    expected_passage_count, expected_child_count, expected_manifest_sha256
                ) VALUES (?, ?, ?, 'BUILDING', 'struct-v1', 'passage-v1', 'child-v1',
                    'bge-m3', ?, 1024, 'passage-source-v1', 'child-source-v1', 2, 2, ?)
                RETURNING id
                """,
                Long.class,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                MODEL_DIGEST,
                EXPECTED_LOGICAL_MANIFEST_SHA256);
        SearchV3IndexingJobClaim claim = insertProcessingJob(database.jdbc(), fixture, generationId);
        InventoryIds inventory = insertInventory(database.jdbc(), fixture, generationId);
        return new Generation(generationId, claim, inventory);
    }

    private SearchV3IndexingJobClaim insertProcessingJob(
            JdbcTemplate jdbc, Fixture fixture, long generationId) {
        long jobId = jdbc.queryForObject(
                """
                INSERT INTO search_v3_indexing_jobs(
                    generation_id, owner_user_id, document_id, document_version_id,
                    status, claim_version, attempt_count, lease_expires_at, started_at
                ) VALUES (?, ?, ?, ?, 'PROCESSING', 1, 1, now() + interval '10 minutes', now())
                RETURNING id
                """,
                Long.class,
                generationId,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId());
        Instant leaseExpiresAt = jdbc.queryForObject(
                        "SELECT lease_expires_at FROM search_v3_indexing_jobs WHERE id = ?",
                        Timestamp.class,
                        jobId)
                .toInstant();
        return new SearchV3IndexingJobClaim(
                jobId,
                generationId,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                1,
                1,
                leaseExpiresAt);
    }

    private InventoryIds insertInventory(JdbcTemplate jdbc, Fixture fixture, long generationId) {
        long firstPassage = insertPassage(
                jdbc,
                fixture,
                generationId,
                "passage-001",
                0,
                PASSAGE_ONE_TEXT,
                1,
                2,
                0,
                37,
                "parent-a",
                "[\"block-a1\",\"block-a2\"]");
        long secondPassage = insertPassage(
                jdbc,
                fixture,
                generationId,
                "passage-002",
                1,
                PASSAGE_TWO_TEXT,
                3,
                4,
                40,
                80,
                "parent-b",
                "[\"block-b1\",\"block-b2\"]");
        long firstChild = insertChild(
                jdbc,
                fixture,
                generationId,
                firstPassage,
                "child-001",
                0,
                CHILD_ONE_TEXT,
                2,
                15,
                37,
                "block-a2",
                "parent-a");
        long secondChild = insertChild(
                jdbc,
                fixture,
                generationId,
                secondPassage,
                "child-002",
                1,
                CHILD_TWO_TEXT,
                4,
                54,
                80,
                "block-b2",
                "parent-b");

        insertPassageEmbedding(jdbc, fixture, generationId, firstPassage, sha256(PASSAGE_ONE_TEXT), 0);
        insertPassageEmbedding(jdbc, fixture, generationId, secondPassage, sha256(PASSAGE_TWO_TEXT), 1);
        insertChildEmbedding(jdbc, fixture, generationId, firstChild, sha256(CHILD_ONE_TEXT), 0);
        insertChildEmbedding(jdbc, fixture, generationId, secondChild, sha256(CHILD_TWO_TEXT), 1);
        return new InventoryIds(firstPassage, secondPassage, firstChild, secondChild);
    }

    private long insertPassage(
            JdbcTemplate jdbc,
            Fixture fixture,
            long generationId,
            String key,
            int order,
            String text,
            int lineStart,
            int lineEnd,
            int codePointStart,
            int codePointEnd,
            String parent,
            String sourceBlockIds) {
        return jdbc.queryForObject(
                """
                INSERT INTO search_v3_retrieval_passages(
                    generation_id, owner_user_id, document_id, document_version_id,
                    passage_key, passage_order, source_text, retrieval_text, retrieval_text_sha256,
                    source_path, page_no, line_start, line_end, code_point_start, code_point_end,
                    parent_annotation_candidate_id, document_source_sha256,
                    source_block_ids, context_source_block_ids
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'fixture.txt', NULL, ?, ?, ?, ?, ?, ?,
                    CAST(? AS jsonb), '[]'::jsonb)
                RETURNING id
                """,
                Long.class,
                generationId,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                key,
                order,
                text,
                text,
                sha256(text),
                lineStart,
                lineEnd,
                codePointStart,
                codePointEnd,
                parent,
                DOCUMENT_SOURCE_HASH,
                sourceBlockIds);
    }

    private long insertChild(
            JdbcTemplate jdbc,
            Fixture fixture,
            long generationId,
            long passageId,
            String key,
            int order,
            String text,
            int line,
            int codePointStart,
            int codePointEnd,
            String sourceBlockId,
            String parent) {
        return jdbc.queryForObject(
                """
                INSERT INTO search_v3_evidence_children(
                    generation_id, owner_user_id, document_id, document_version_id, passage_id,
                    child_key, child_order, passage_child_order, source_block_type,
                    source_text, source_text_sha256, source_path, page_no,
                    line_start, line_end, code_point_start, code_point_end,
                    source_block_id, parent_annotation_candidate_id, document_source_sha256,
                    source_block_ids, context_source_block_ids
                ) VALUES (?, ?, ?, ?, ?, ?, ?, 0, 'PARAGRAPH', ?, ?, 'fixture.txt', NULL,
                    ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), '[]'::jsonb)
                RETURNING id
                """,
                Long.class,
                generationId,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                passageId,
                key,
                order,
                text,
                sha256(text),
                line,
                line,
                codePointStart,
                codePointEnd,
                sourceBlockId,
                parent,
                DOCUMENT_SOURCE_HASH,
                "[\"" + sourceBlockId + "\"]");
    }

    private void insertPassageEmbedding(
            JdbcTemplate jdbc,
            Fixture fixture,
            long generationId,
            long passageId,
            String inputHash,
            int vectorIndex) {
        jdbc.update(
                """
                INSERT INTO search_v3_passage_embeddings(
                    passage_id, generation_id, owner_user_id, document_id, document_version_id,
                    input_sha256, embedding_model_id, resolved_model_digest, embedding_dimension,
                    input_policy_version, embedding
                ) VALUES (?, ?, ?, ?, ?, ?, 'bge-m3', ?, 1024, 'passage-source-v1', CAST(? AS vector))
                """,
                passageId,
                generationId,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                inputHash,
                MODEL_DIGEST,
                unitVector(vectorIndex));
    }

    private void insertChildEmbedding(
            JdbcTemplate jdbc,
            Fixture fixture,
            long generationId,
            long childId,
            String inputHash,
            int vectorIndex) {
        jdbc.update(
                """
                INSERT INTO search_v3_child_embeddings(
                    child_id, generation_id, owner_user_id, document_id, document_version_id,
                    input_sha256, embedding_model_id, resolved_model_digest, embedding_dimension,
                    input_policy_version, embedding
                ) VALUES (?, ?, ?, ?, ?, ?, 'bge-m3', ?, 1024, 'child-source-v1', CAST(? AS vector))
                """,
                childId,
                generationId,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                inputHash,
                MODEL_DIGEST,
                unitVector(vectorIndex));
    }

    private List<PassageRow> expectedPassages() {
        return List.of(
                new PassageRow(
                        0,
                        "passage-001",
                        0,
                        PASSAGE_ONE_TEXT,
                        PASSAGE_ONE_TEXT,
                        sha256(PASSAGE_ONE_TEXT),
                        "fixture.txt",
                        null,
                        1,
                        2,
                        0,
                        37,
                        "parent-a",
                        DOCUMENT_SOURCE_HASH,
                        List.of("block-a1", "block-a2"),
                        List.of()),
                new PassageRow(
                        0,
                        "passage-002",
                        1,
                        PASSAGE_TWO_TEXT,
                        PASSAGE_TWO_TEXT,
                        sha256(PASSAGE_TWO_TEXT),
                        "fixture.txt",
                        null,
                        3,
                        4,
                        40,
                        80,
                        "parent-b",
                        DOCUMENT_SOURCE_HASH,
                        List.of("block-b1", "block-b2"),
                        List.of()));
    }

    private List<ChildRow> expectedChildren() {
        return List.of(
                new ChildRow(
                        0,
                        "child-001",
                        0,
                        0,
                        "passage-001",
                        "PARAGRAPH",
                        CHILD_ONE_TEXT,
                        sha256(CHILD_ONE_TEXT),
                        "fixture.txt",
                        null,
                        2,
                        2,
                        15,
                        37,
                        "block-a2",
                        "parent-a",
                        DOCUMENT_SOURCE_HASH,
                        List.of("block-a2"),
                        List.of()),
                new ChildRow(
                        0,
                        "child-002",
                        1,
                        0,
                        "passage-002",
                        "PARAGRAPH",
                        CHILD_TWO_TEXT,
                        sha256(CHILD_TWO_TEXT),
                        "fixture.txt",
                        null,
                        4,
                        4,
                        54,
                        80,
                        "block-b2",
                        "parent-b",
                        DOCUMENT_SOURCE_HASH,
                        List.of("block-b2"),
                        List.of()));
    }

    private void assertFrozenExpectedManifest(SearchV3InventoryVerifier verifier) {
        String frozenBeforeDatabaseInsert = verifier.logicalManifestSha256(
                expectedPassages(), expectedChildren());
        assertThat(frozenBeforeDatabaseInsert).isEqualTo(EXPECTED_LOGICAL_MANIFEST_SHA256);
    }

    private void markReady(RuntimeDatabase database, Generation generation) {
        transaction(database, () -> database.service().markReady(generation.claim()));
    }

    private void assertInventoryRejected(RuntimeDatabase database, SearchV3IndexingJobClaim claim) {
        assertThatThrownBy(() -> transaction(database, () -> database.service().markReady(claim)))
                .isInstanceOf(SearchV3InventoryActivationException.class);
        assertThat(status(database.jdbc(), "search_v3_index_generations", claim.generationId()))
                .isEqualTo("BUILDING");
    }

    private void assertActivationRejected(RuntimeDatabase database, SearchV3IndexingJobClaim claim) {
        assertThatThrownBy(() -> transaction(database, () -> database.service().activate(claim)))
                .isInstanceOf(SearchV3InventoryActivationException.class);
        assertThat(status(database.jdbc(), "search_v3_index_generations", claim.generationId()))
                .isEqualTo("READY");
        assertThat(status(database.jdbc(), "search_v3_indexing_jobs", claim.jobId()))
                .isEqualTo("PROCESSING");
    }

    private boolean attemptActivation(
            RuntimeDatabase database,
            SearchV3IndexingJobClaim claim,
            CountDownLatch start) {
        await(start);
        try {
            transaction(database, () -> database.service().activate(claim));
            return true;
        }
        catch (StaleSearchV3IndexingJobClaimException exception) {
            return false;
        }
    }

    private <T> T transaction(RuntimeDatabase database, java.util.concurrent.Callable<T> operation) {
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

    private void lockRecovery(JdbcTemplate jdbc, long jobId) {
        jdbc.update(
                """
                UPDATE search_v3_indexing_jobs
                SET recovery_lock_token = ?, recovery_locked_at = now()
                WHERE id = ?
                """,
                UUID.randomUUID(),
                jobId);
    }

    private void installPointerFailureTrigger(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE FUNCTION fail_search_v3_pointer_update() RETURNS trigger
                LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.active_search_v3_generation_id IS DISTINCT FROM OLD.active_search_v3_generation_id THEN
                        RAISE EXCEPTION 'forced Search V3 pointer failure';
                    END IF;
                    RETURN NEW;
                END;
                $$
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_search_v3_pointer_update_trigger
                BEFORE UPDATE ON documents
                FOR EACH ROW EXECUTE FUNCTION fail_search_v3_pointer_update()
                """);
    }

    private SearchV3IndexingJobClaim withClaimVersion(SearchV3IndexingJobClaim claim, long claimVersion) {
        return new SearchV3IndexingJobClaim(
                claim.jobId(),
                claim.generationId(),
                claim.ownerUserId(),
                claim.documentId(),
                claim.documentVersionId(),
                claimVersion,
                claim.attemptCount(),
                claim.leaseExpiresAt());
    }

    private String status(JdbcTemplate jdbc, String table, long id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id);
    }

    private long activeVersion(JdbcTemplate jdbc, long documentId) {
        return jdbc.queryForObject(
                "SELECT active_version_id FROM documents WHERE id = ?",
                Long.class,
                documentId);
    }

    private Long activeSearchV3Generation(JdbcTemplate jdbc, long documentId) {
        return jdbc.queryForObject(
                "SELECT active_search_v3_generation_id FROM documents WHERE id = ?",
                Long.class,
                documentId);
    }

    private static String unitVector(int nonZeroIndex) {
        StringBuilder vector = new StringBuilder(3 + (1024 * 2));
        vector.append('[');
        for (int index = 0; index < 1024; index++) {
            if (index > 0) {
                vector.append(',');
            }
            vector.append(index == nonZeroIndex ? '1' : '0');
        }
        return vector.append(']').toString();
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent PostgreSQL test was interrupted.", exception);
        }
    }

    private record Fixture(long ownerUserId, long documentId, long documentVersionId) {
    }

    private record InventoryIds(
            long firstPassageId,
            long secondPassageId,
            long firstChildId,
            long secondChildId) {
    }

    private record Generation(
            long generationId,
            SearchV3IndexingJobClaim claim,
            InventoryIds inventory) {
    }

    private record RuntimeDatabase(
            DataSource dataSource,
            JdbcTemplate jdbc,
            SearchV3InventoryVerifier verifier,
            SearchV3InventoryActivationService service) {
    }
}
