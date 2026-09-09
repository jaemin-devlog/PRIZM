package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.service.IndexingRetryPolicy;
import com.prizm.search.v3.indexing.exception.StaleSearchV3IndexingJobClaimException;
import com.prizm.search.v3.indexing.exception.StaleSearchV3RecoveryLockException;
import com.prizm.search.v3.indexing.model.SearchV3IndexGenerationStatus;
import com.prizm.search.v3.indexing.model.SearchV3IndexingFailureStage;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobStatus;
import com.prizm.search.v3.indexing.model.SearchV3RecoveryLock;
import com.prizm.search.v3.indexing.repository.SearchV3IndexingJobRepository;
import com.prizm.search.v3.indexing.service.SearchV3IndexingJobService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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

/** V18 Search V3 indexing job의 실제 PostgreSQL ownership·lease·recovery fencing을 검증한다. */
@Testcontainers
class SearchV3IndexingJobRuntimeTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");
    private static final String MODEL_DIGEST = "a".repeat(64);
    private static final String MANIFEST_HASH = "b".repeat(64);
    private static final Duration LEASE_DURATION = Duration.ofMinutes(10);

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm_search_v3_job_runtime")
            .withUsername("prizm")
            .withPassword("prizm-test");

    @Test
    void claimsPendingAndOnlyDueRetryWithFullLineageAndCounters() {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createFixture(database.jdbc(), "claim-owner");
        long generationId = insertBuildingGeneration(database.jdbc(), fixture);
        long jobId = insertPendingJob(database.jdbc(), fixture, generationId);
        SearchV3IndexingJobService service = database.service();

        SearchV3IndexingJobClaim first = service.claimNext().orElseThrow();

        assertThat(first.jobId()).isEqualTo(jobId);
        assertThat(first.generationId()).isEqualTo(generationId);
        assertThat(first.ownerUserId()).isEqualTo(fixture.ownerUserId());
        assertThat(first.documentId()).isEqualTo(fixture.documentId());
        assertThat(first.documentVersionId()).isEqualTo(fixture.documentVersionId());
        assertThat(first.claimVersion()).isEqualTo(1);
        assertThat(first.attemptCount()).isEqualTo(1);
        assertThat(service.claimNext()).isEmpty();

        assertThat(service.handleFailure(first, true, SearchV3IndexingFailureStage.STORAGE, "retry"))
                .isEqualTo(SearchV3IndexingJobStatus.RETRY_WAIT);
        assertThat(service.claimNext()).isEmpty();
        assertThat(database.jdbc().queryForObject(
                "SELECT attempt_count FROM search_v3_indexing_jobs WHERE id = ?",
                Integer.class,
                jobId)).isEqualTo(1);

        database.jdbc().update(
                "UPDATE search_v3_indexing_jobs SET next_retry_at = now() - interval '1 second' WHERE id = ?",
                jobId);
        SearchV3IndexingJobClaim second = service.claimNext().orElseThrow();
        assertThat(second.claimVersion()).isEqualTo(2);
        assertThat(second.attemptCount()).isEqualTo(2);
        assertThat(second.generationId()).isEqualTo(generationId);
    }

    @Test
    void pendingJobDoesNotClaimAReadyGeneration() {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createFixture(database.jdbc(), "pending-ready-owner");
        long generationId = insertBuildingGeneration(database.jdbc(), fixture);
        long jobId = insertPendingJob(database.jdbc(), fixture, generationId);
        markGenerationReady(database.jdbc(), generationId);

        assertThat(database.service().claimNext()).isEmpty();
        assertThat(database.jdbc().queryForMap(
                        "SELECT status, claim_version, attempt_count FROM search_v3_indexing_jobs WHERE id = ?",
                        jobId))
                .containsEntry("status", "PENDING")
                .containsEntry("claim_version", 0L)
                .containsEntry("attempt_count", 0);
    }

    @Test
    void readyActivationDeferralResumesOnlyWhenDueWithoutUsingFailureRetryBudget() {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createFixture(database.jdbc(), "ready-resume-owner");
        long generationId = insertBuildingGeneration(database.jdbc(), fixture);
        insertPendingJob(database.jdbc(), fixture, generationId);
        SearchV3IndexingJobService service = database.service();
        SearchV3IndexingJobClaim current = service.claimNext().orElseThrow();

        assertThat(service.currentGenerationStatus(current))
                .isEqualTo(SearchV3IndexGenerationStatus.BUILDING);
        markGenerationReady(database.jdbc(), generationId);
        assertThat(service.currentGenerationStatus(current))
                .isEqualTo(SearchV3IndexGenerationStatus.READY);

        for (int deferral = 1; deferral <= 5; deferral++) {
            SearchV3IndexingJobClaim deferredClaim = current;
            Instant nextRetryAt = service.deferActivation(
                    deferredClaim, "Production version is not active yet.");
            assertThat(database.jdbc().queryForObject(
                    "SELECT next_retry_at FROM search_v3_indexing_jobs WHERE id = ?",
                    java.sql.Timestamp.class,
                    deferredClaim.jobId()).toInstant()).isEqualTo(nextRetryAt);
            assertThat(database.jdbc().queryForObject(
                    "SELECT status FROM search_v3_indexing_jobs WHERE id = ?",
                    String.class,
                    deferredClaim.jobId())).isEqualTo("RETRY_WAIT");
            assertThat(database.jdbc().queryForObject(
                    "SELECT status FROM search_v3_index_generations WHERE id = ?",
                    String.class,
                    generationId)).isEqualTo("READY");
            assertThat(service.claimNext()).isEmpty();
            assertThatThrownBy(() -> service.currentGenerationStatus(deferredClaim))
                    .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);

            database.jdbc().update(
                    "UPDATE search_v3_indexing_jobs SET next_retry_at = now() - interval '1 second' WHERE id = ?",
                    deferredClaim.jobId());
            current = service.claimNext().orElseThrow();
            assertThat(current.attemptCount()).isEqualTo(deferral + 1);
            assertThat(current.claimVersion()).isEqualTo(deferral + 1L);
            assertThat(service.currentGenerationStatus(current))
                    .isEqualTo(SearchV3IndexGenerationStatus.READY);
        }

        assertThat(database.jdbc().queryForObject(
                "SELECT status FROM search_v3_index_generations WHERE id = ?",
                String.class,
                generationId)).isEqualTo("READY");
        assertThat(database.jdbc().queryForObject(
                "SELECT status FROM search_v3_indexing_jobs WHERE id = ?",
                String.class,
                current.jobId())).isEqualTo("PROCESSING");
    }

    @Test
    void activationDeferralRequiresReadyCurrentFullLineageWithoutRecoveryLock() {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createFixture(database.jdbc(), "deferral-fencing-owner");
        long generationId = insertBuildingGeneration(database.jdbc(), fixture);
        insertPendingJob(database.jdbc(), fixture, generationId);
        SearchV3IndexingJobService service = database.service();
        SearchV3IndexingJobClaim claim = service.claimNext().orElseThrow();

        assertThatThrownBy(() -> service.deferActivation(claim, "not ready"))
                .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);
        markGenerationReady(database.jdbc(), generationId);

        Fixture foreign = createFixture(database.jdbc(), "foreign-deferral-owner");
        long foreignGeneration = insertBuildingGeneration(database.jdbc(), foreign);
        SearchV3IndexingJobClaim[] invalidClaims = {
                withGeneration(claim, foreignGeneration),
                withOwner(claim, foreign.ownerUserId()),
                withDocument(claim, foreign.documentId()),
                withVersion(claim, foreign.documentVersionId()),
                withClaimVersion(claim, claim.claimVersion() + 1)
        };
        for (SearchV3IndexingJobClaim invalid : invalidClaims) {
            assertThatThrownBy(() -> service.deferActivation(invalid, "foreign"))
                    .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);
            assertThatThrownBy(() -> service.currentGenerationStatus(invalid))
                    .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);
        }

        database.jdbc().update(
                """
                UPDATE search_v3_indexing_jobs
                SET recovery_lock_token = ?, recovery_locked_at = now()
                WHERE id = ?
                """,
                UUID.randomUUID(),
                claim.jobId());
        assertThatThrownBy(() -> service.deferActivation(claim, "recovery locked"))
                .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);
        assertThatThrownBy(() -> service.currentGenerationStatus(claim))
                .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);

        database.jdbc().update(
                """
                UPDATE search_v3_indexing_jobs
                SET recovery_lock_token = NULL, recovery_locked_at = NULL
                WHERE id = ?
                """,
                claim.jobId());
        assertThat(service.deferActivation(claim, "safe deferral")).isNotNull();
        assertThat(database.jdbc().queryForObject(
                "SELECT status FROM search_v3_indexing_jobs WHERE id = ?",
                String.class,
                claim.jobId())).isEqualTo("RETRY_WAIT");
        assertThat(database.jdbc().queryForObject(
                "SELECT status FROM search_v3_index_generations WHERE id = ?",
                String.class,
                generationId)).isEqualTo("READY");
    }

    @Test
    void concurrentClaimersProduceExactlyOneOwner() throws Exception {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createFixture(database.jdbc(), "concurrent-claim-owner");
        long generationId = insertBuildingGeneration(database.jdbc(), fixture);
        insertPendingJob(database.jdbc(), fixture, generationId);
        SearchV3IndexingJobRepository repository = database.repository();
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Optional<SearchV3IndexingJobClaim>> winner = executor.submit(() -> transaction(database, () -> {
                Optional<SearchV3IndexingJobClaim> claim = repository.claimNext(LEASE_DURATION);
                firstClaimed.countDown();
                await(releaseFirst);
                return claim;
            }));
            firstClaimed.await();
            Future<Optional<SearchV3IndexingJobClaim>> loser = executor.submit(
                    () -> transaction(database, () -> repository.claimNext(LEASE_DURATION)));

            assertThat(loser.get()).isEmpty();
            releaseFirst.countDown();
            assertThat(winner.get()).isPresent();
        }
        finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }

        assertThat(database.jdbc().queryForObject(
                "SELECT COUNT(*) FROM search_v3_indexing_jobs WHERE status = 'PROCESSING'",
                Long.class)).isEqualTo(1L);
        assertThat(database.jdbc().queryForObject(
                "SELECT claim_version FROM search_v3_indexing_jobs WHERE generation_id = ?",
                Long.class,
                generationId)).isEqualTo(1L);
    }

    @Test
    void renewRequiresCurrentUnexpiredFullIdentityAndProcessingState() {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createFixture(database.jdbc(), "renew-owner");
        long generationId = insertBuildingGeneration(database.jdbc(), fixture);
        insertPendingJob(database.jdbc(), fixture, generationId);
        SearchV3IndexingJobService service = database.service();
        SearchV3IndexingJobClaim claim = service.claimNext().orElseThrow();

        assertThat(service.renewLease(claim)).isAfter(claim.leaseExpiresAt());
        assertStaleRenew(service, withGeneration(claim, claim.generationId() + 1000));
        assertStaleRenew(service, withOwner(claim, claim.ownerUserId() + 1000));
        assertStaleRenew(service, withDocument(claim, claim.documentId() + 1000));
        assertStaleRenew(service, withVersion(claim, claim.documentVersionId() + 1000));
        assertStaleRenew(service, withClaimVersion(claim, claim.claimVersion() + 1));

        database.jdbc().update(
                "UPDATE search_v3_indexing_jobs SET lease_expires_at = now() - interval '1 second' WHERE id = ?",
                claim.jobId());
        assertStaleRenew(service, claim);

        RuntimeDatabase retryDatabase = createRuntimeDatabase();
        Fixture retryFixture = createFixture(retryDatabase.jdbc(), "non-processing-renew-owner");
        long retryGeneration = insertBuildingGeneration(retryDatabase.jdbc(), retryFixture);
        insertPendingJob(retryDatabase.jdbc(), retryFixture, retryGeneration);
        SearchV3IndexingJobClaim retryClaim = retryDatabase.service().claimNext().orElseThrow();
        retryDatabase.service().handleFailure(
                retryClaim, true, SearchV3IndexingFailureStage.CHILD_EMBEDDING, "retry");
        assertStaleRenew(retryDatabase.service(), retryClaim);
    }

    @Test
    void recoveryLockIsExclusiveAndExactTokenReclaimFencesOldWorker() throws Exception {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createFixture(database.jdbc(), "recovery-owner");
        long generationId = insertBuildingGeneration(database.jdbc(), fixture);
        insertPendingJob(database.jdbc(), fixture, generationId);
        SearchV3IndexingJobService service = database.service();
        SearchV3IndexingJobClaim expiredClaim = service.claimNext().orElseThrow();

        assertThat(service.acquireNextRecoveryLock()).isEmpty();
        database.jdbc().update(
                "UPDATE search_v3_indexing_jobs SET lease_expires_at = now() - interval '1 second' WHERE id = ?",
                expiredClaim.jobId());

        SearchV3IndexingJobRepository repository = database.repository();
        CountDownLatch firstLocked = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        UUID winningToken = UUID.randomUUID();
        SearchV3RecoveryLock recoveryLock;
        try {
            Future<Optional<SearchV3RecoveryLock>> winner = executor.submit(() -> transaction(database, () -> {
                Optional<SearchV3RecoveryLock> lock = repository.acquireNextRecoveryLock(winningToken);
                firstLocked.countDown();
                await(releaseFirst);
                return lock;
            }));
            firstLocked.await();
            Future<Optional<SearchV3RecoveryLock>> loser = executor.submit(() -> transaction(
                    database,
                    () -> repository.acquireNextRecoveryLock(UUID.randomUUID())));

            assertThat(loser.get()).isEmpty();
            releaseFirst.countDown();
            recoveryLock = winner.get().orElseThrow();
        }
        finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }

        assertThat(recoveryLock.recoveryToken()).isEqualTo(winningToken);
        assertStaleRenew(service, expiredClaim);
        assertThatThrownBy(() -> service.handleFailure(
                expiredClaim, true, SearchV3IndexingFailureStage.STORAGE, "stale retry"))
                .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);

        SearchV3RecoveryLock wrongToken = new SearchV3RecoveryLock(
                recoveryLock.expiredClaim(), UUID.randomUUID(), recoveryLock.recoveryLockedAt());
        assertThatThrownBy(() -> service.reclaim(wrongToken))
                .isInstanceOf(StaleSearchV3RecoveryLockException.class);

        SearchV3IndexingJobClaim reclaimed = service.reclaim(recoveryLock);
        assertThat(reclaimed.claimVersion()).isEqualTo(expiredClaim.claimVersion() + 1);
        assertThat(reclaimed.attemptCount()).isEqualTo(expiredClaim.attemptCount() + 1);
        assertThat(service.renewLease(reclaimed)).isAfter(reclaimed.leaseExpiresAt());
        assertStaleRenew(service, expiredClaim);
        assertThatThrownBy(() -> service.handleFailure(
                expiredClaim, false, SearchV3IndexingFailureStage.STORAGE, "stale failure"))
                .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);
        assertThatThrownBy(() -> service.reclaim(recoveryLock))
                .isInstanceOf(StaleSearchV3RecoveryLockException.class);
        assertThat(service.acquireNextRecoveryLock()).isEmpty();
    }

    @Test
    void retryBudgetEndsWithAtomicJobAndGenerationFailure() {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture fixture = createFixture(database.jdbc(), "failure-owner");
        long generationId = insertBuildingGeneration(database.jdbc(), fixture);
        insertPendingJob(database.jdbc(), fixture, generationId);
        SearchV3IndexingJobService service = database.service();

        SearchV3IndexingJobClaim current = service.claimNext().orElseThrow();
        for (int attempt = 1; attempt <= 3; attempt++) {
            assertThat(current.attemptCount()).isEqualTo(attempt);
            assertThat(service.handleFailure(
                    current, true, SearchV3IndexingFailureStage.PASSAGE_EMBEDDING, "retry-" + attempt))
                    .isEqualTo(SearchV3IndexingJobStatus.RETRY_WAIT);
            database.jdbc().update(
                    "UPDATE search_v3_indexing_jobs SET next_retry_at = now() - interval '1 second' WHERE id = ?",
                    current.jobId());
            current = service.claimNext().orElseThrow();
        }

        assertThat(current.attemptCount()).isEqualTo(4);
        SearchV3IndexingJobClaim finalClaim = current;
        assertThat(service.handleFailure(
                finalClaim, true, SearchV3IndexingFailureStage.PASSAGE_EMBEDDING, "terminal"))
                .isEqualTo(SearchV3IndexingJobStatus.FAILED);
        assertThat(database.jdbc().queryForObject(
                "SELECT status FROM search_v3_indexing_jobs WHERE id = ?",
                String.class,
                finalClaim.jobId())).isEqualTo("FAILED");
        assertThat(database.jdbc().queryForObject(
                "SELECT status FROM search_v3_index_generations WHERE id = ?",
                String.class,
                generationId)).isEqualTo("FAILED");
        assertThatThrownBy(() -> service.handleFailure(
                finalClaim, false, SearchV3IndexingFailureStage.STORAGE, "stale"))
                .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);
    }

    @Test
    void allMutationsRejectCrossOwnerDocumentVersionAndGenerationIdentity() {
        RuntimeDatabase database = createRuntimeDatabase();
        Fixture first = createFixture(database.jdbc(), "first-fencing-owner");
        Fixture second = createFixture(database.jdbc(), "second-fencing-owner");
        long firstGeneration = insertBuildingGeneration(database.jdbc(), first);
        long secondGeneration = insertBuildingGeneration(database.jdbc(), second);
        insertPendingJob(database.jdbc(), first, firstGeneration);
        insertPendingJob(database.jdbc(), second, secondGeneration);
        SearchV3IndexingJobService service = database.service();
        SearchV3IndexingJobClaim claim = service.claimNext().orElseThrow();

        SearchV3IndexingJobClaim[] foreignClaims = {
                withGeneration(claim, secondGeneration),
                withOwner(claim, second.ownerUserId()),
                withDocument(claim, second.documentId()),
                withVersion(claim, second.documentVersionId())
        };
        for (SearchV3IndexingJobClaim foreign : foreignClaims) {
            assertStaleRenew(service, foreign);
            assertThatThrownBy(() -> service.handleFailure(
                    foreign, true, SearchV3IndexingFailureStage.STORAGE, "foreign"))
                    .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);
        }

        database.jdbc().update(
                "UPDATE search_v3_indexing_jobs SET lease_expires_at = now() - interval '1 second' WHERE id = ?",
                claim.jobId());
        SearchV3RecoveryLock lock = service.acquireNextRecoveryLock().orElseThrow();
        for (SearchV3IndexingJobClaim foreign : foreignClaims) {
            SearchV3RecoveryLock foreignLock = new SearchV3RecoveryLock(
                    foreign, lock.recoveryToken(), lock.recoveryLockedAt());
            assertThatThrownBy(() -> service.reclaim(foreignLock))
                    .isInstanceOf(StaleSearchV3RecoveryLockException.class);
        }

        assertThat(database.jdbc().queryForObject(
                "SELECT status FROM search_v3_indexing_jobs WHERE id = ?",
                String.class,
                claim.jobId())).isEqualTo("PROCESSING");
        assertThat(database.jdbc().queryForObject(
                "SELECT claim_version FROM search_v3_indexing_jobs WHERE id = ?",
                Long.class,
                claim.jobId())).isEqualTo(claim.claimVersion());
    }

    private RuntimeDatabase createRuntimeDatabase() {
        String databaseName = "prizm_s3_job_" + UUID.randomUUID().toString().replace("-", "");
        JdbcTemplate admin = new JdbcTemplate(new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword()));
        admin.execute("CREATE DATABASE " + databaseName);
        String url = postgres.getJdbcUrl().replace("/" + postgres.getDatabaseName(), "/" + databaseName);
        DataSource dataSource = new DriverManagerDataSource(url, postgres.getUsername(), postgres.getPassword());
        Flyway.configure().dataSource(dataSource).load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        SearchV3IndexingJobRepository repository = new SearchV3IndexingJobRepository(jdbc);
        IngestionProperties properties = new IngestionProperties();
        properties.setLeaseDuration(LEASE_DURATION);
        SearchV3IndexingJobService service = new SearchV3IndexingJobService(
                repository, properties, new IndexingRetryPolicy());
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
                "f".repeat(64));
        return new Fixture(owner, document, version);
    }

    private long insertBuildingGeneration(JdbcTemplate jdbc, Fixture fixture) {
        return jdbc.queryForObject(
                """
                INSERT INTO search_v3_index_generations(
                    owner_user_id, document_id, document_version_id, status,
                    structure_policy_version, passage_policy_version, child_policy_version,
                    embedding_model_id, resolved_model_digest, embedding_dimension,
                    passage_input_policy_version, child_input_policy_version,
                    expected_passage_count, expected_child_count, expected_manifest_sha256
                ) VALUES (?, ?, ?, 'BUILDING', 'struct-v1', 'passage-v1', 'child-v1',
                    'bge-m3', ?, 1024, 'passage-source-v1', 'child-source-v1', 1, 1, ?)
                RETURNING id
                """,
                Long.class,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId(),
                MODEL_DIGEST,
                MANIFEST_HASH);
    }

    private long insertPendingJob(JdbcTemplate jdbc, Fixture fixture, long generationId) {
        return jdbc.queryForObject(
                """
                INSERT INTO search_v3_indexing_jobs(
                    generation_id, owner_user_id, document_id, document_version_id
                ) VALUES (?, ?, ?, ?) RETURNING id
                """,
                Long.class,
                generationId,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId());
    }

    private void markGenerationReady(JdbcTemplate jdbc, long generationId) {
        jdbc.update(
                """
                UPDATE search_v3_index_generations
                SET status = 'READY',
                    build_completed_at = now(),
                    verified_inventory_sha256 = ?
                WHERE id = ?
                """,
                "c".repeat(64),
                generationId);
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

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent PostgreSQL test was interrupted.", exception);
        }
    }

    private void assertStaleRenew(SearchV3IndexingJobService service, SearchV3IndexingJobClaim claim) {
        assertThatThrownBy(() -> service.renewLease(claim))
                .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);
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

    private record RuntimeDatabase(
            DataSource dataSource,
            JdbcTemplate jdbc,
            SearchV3IndexingJobRepository repository,
            SearchV3IndexingJobService service) {}
}
