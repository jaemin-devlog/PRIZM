package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.changelog.entity.ChangeLogDispatchStatus;
import com.prizm.changelog.entity.DocumentChangeLog;
import com.prizm.changelog.repository.DocumentChangeLogRepository;
import com.prizm.changelog.service.ChangeLogDispatchTransaction;
import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** P3 Transaction A를 명시 호출해 dispatch 원자성과 idempotency를 검증한다. */
@ActiveProfiles("integration-test")
@SpringBootTest
@Testcontainers
class ChangeLogDispatchDatabaseIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm")
            .withUsername("prizm")
            .withPassword("prizm-test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired ChangeLogDispatchTransaction dispatchTransaction;
    @Autowired DocumentChangeLogRepository documentChangeLogRepository;
    @Autowired ProcessingJobRepository processingJobRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentVersionRepository documentVersionRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_dispatch_change_log_update ON document_change_logs");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_dispatch_change_log_update()");
        jdbcTemplate.update("DELETE FROM document_change_logs");
        jdbcTemplate.update("DELETE FROM file_cleanup_jobs");
        jdbcTemplate.update("DELETE FROM processing_jobs");
        jdbcTemplate.update("DELETE FROM document_chunks");
        jdbcTemplate.update("UPDATE documents SET active_version_id = NULL");
        jdbcTemplate.update("DELETE FROM document_versions");
        jdbcTemplate.update("DELETE FROM documents");
        userAccountRepository.deleteAll();
    }

    @Test
    void dispatchesTheOldestPendingChangeLogWithOneJobAndTimestamps() {
        UserAccount owner = createUser("oldest-owner@prizm.local");
        ChangeLogFixture oldest = createPendingChangeLog(owner, "oldest");
        jdbcTemplate.update("UPDATE document_change_logs SET created_at = now() - INTERVAL '1 minute' WHERE id = ?",
                oldest.changeLogId());
        ChangeLogFixture newer = createPendingChangeLog(owner, "newer");

        assertThat(dispatchTransaction.dispatchNext()).isTrue();

        DocumentChangeLog dispatched = documentChangeLogRepository.findById(oldest.changeLogId()).orElseThrow();
        DocumentChangeLog pending = documentChangeLogRepository.findById(newer.changeLogId()).orElseThrow();
        assertThat(dispatched.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.DISPATCHED);
        assertThat(dispatched.getProcessingJobId()).isNotNull();
        assertThat(dispatched.getDispatchedAt()).isNotNull();
        assertThat(pending.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.PENDING);
        assertThat(processingJobRepository.count()).isEqualTo(1L);
        ProcessingJob job = processingJobRepository.findById(dispatched.getProcessingJobId()).orElseThrow();
        assertThat(job.getOwnerUserId()).isEqualTo(owner.getId());
        assertThat(job.getDocumentVersionId()).isEqualTo(oldest.versionId());
    }

    @Test
    void dispatchesAnAvailableRetryWaitChangeLog() {
        UserAccount owner = createUser("retry-owner@prizm.local");
        ChangeLogFixture fixture = createPendingChangeLog(owner, "retry");
        jdbcTemplate.update(
                "UPDATE document_change_logs SET dispatch_status = 'RETRY_WAIT', next_retry_at = now() - INTERVAL '1 second' WHERE id = ?",
                fixture.changeLogId());

        assertThat(dispatchTransaction.dispatchNext()).isTrue();

        DocumentChangeLog dispatched = documentChangeLogRepository.findById(fixture.changeLogId()).orElseThrow();
        assertThat(dispatched.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.DISPATCHED);
        assertThat(dispatched.getNextRetryAt()).isNull();
        assertThat(processingJobRepository.count()).isEqualTo(1L);
    }

    @Test
    void reusesAnExistingOwnerVersionJobWithoutCreatingAnother() {
        UserAccount owner = createUser("reuse-owner@prizm.local");
        ChangeLogFixture fixture = createPendingChangeLog(owner, "reuse");
        ProcessingJob existing = processingJobRepository.saveAndFlush(
                ProcessingJob.pendingIndexing(owner.getId(), fixture.versionId()));

        assertThat(dispatchTransaction.dispatchNext()).isTrue();

        DocumentChangeLog dispatched = documentChangeLogRepository.findById(fixture.changeLogId()).orElseThrow();
        assertThat(dispatched.getProcessingJobId()).isEqualTo(existing.getId());
        assertThat(processingJobRepository.count()).isEqualTo(1L);
    }

    @Test
    void allowsOnlyOneConcurrentDispatcherToCommitTheSameChangeLog() throws Exception {
        UserAccount owner = createUser("concurrent-owner@prizm.local");
        ChangeLogFixture fixture = createPendingChangeLog(owner, "concurrent");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> dispatchAfterStart(start));
            Future<Boolean> second = executor.submit(() -> dispatchAfterStart(start));
            start.countDown();

            List<Boolean> results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder(true, false);
            assertThat(processingJobRepository.count()).isEqualTo(1L);
            assertThat(documentChangeLogRepository.findById(fixture.changeLogId()).orElseThrow().getDispatchStatus())
                    .isEqualTo(ChangeLogDispatchStatus.DISPATCHED);
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rollsBackTheInsertedJobWhenChangeLogDispatchUpdateFails() {
        UserAccount owner = createUser("rollback-owner@prizm.local");
        ChangeLogFixture fixture = createPendingChangeLog(owner, "rollback");
        jdbcTemplate.execute("""
                CREATE FUNCTION fail_dispatch_change_log_update() RETURNS trigger AS $$
                BEGIN
                    IF NEW.dispatch_status = 'DISPATCHED' THEN
                        RAISE EXCEPTION 'forced ChangeLog dispatch update failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_dispatch_change_log_update
                BEFORE UPDATE ON document_change_logs
                FOR EACH ROW EXECUTE FUNCTION fail_dispatch_change_log_update()
                """);

        assertThatThrownBy(() -> dispatchTransaction.dispatchNext()).isInstanceOf(RuntimeException.class);

        DocumentChangeLog pending = documentChangeLogRepository.findById(fixture.changeLogId()).orElseThrow();
        assertThat(pending.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.PENDING);
        assertThat(pending.getProcessingJobId()).isNull();
        assertThat(processingJobRepository.count()).isZero();
    }

    @Test
    void leavesAnEmptyQueueUnchanged() {
        assertThat(dispatchTransaction.dispatchNext()).isFalse();
        assertThat(processingJobRepository.count()).isZero();
    }

    private boolean dispatchAfterStart(CountDownLatch start) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out before concurrent dispatch start.");
            }
            return dispatchTransaction.dispatchNext();
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted before concurrent dispatch.", exception);
        }
    }

    private ChangeLogFixture createPendingChangeLog(UserAccount owner, String suffix) {
        Document document = documentRepository.saveAndFlush(
                Document.create(owner.getId(), "Dispatch " + suffix, DocumentType.OTHER));
        DocumentVersion version = documentVersionRepository.saveAndFlush(DocumentVersion.quarantined(
                owner.getId(),
                document.getId(),
                suffix + ".txt",
                DocumentFileType.TXT,
                "a".repeat(64)));
        DocumentChangeLog changeLog = documentChangeLogRepository.saveAndFlush(
                DocumentChangeLog.pendingDocumentVersionCreated(owner.getId(), version.getId()));
        return new ChangeLogFixture(changeLog.getId(), version.getId());
    }

    private UserAccount createUser(String email) {
        return userAccountRepository.saveAndFlush(UserAccount.create(email, "test-password-hash", UserRole.USER));
    }

    private record ChangeLogFixture(Long changeLogId, Long versionId) {
    }
}
