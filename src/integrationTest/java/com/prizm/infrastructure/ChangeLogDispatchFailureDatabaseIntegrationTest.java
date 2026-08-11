package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.changelog.entity.ChangeLogDispatchStatus;
import com.prizm.changelog.entity.DocumentChangeLog;
import com.prizm.changelog.repository.DocumentChangeLogRepository;
import com.prizm.changelog.service.ChangeLogDispatchFailureDisposition;
import com.prizm.changelog.service.ChangeLogDispatchFailureException;
import com.prizm.changelog.service.ChangeLogDispatchFailureRecorder;
import com.prizm.changelog.service.ChangeLogDispatchTransaction;
import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import java.time.Instant;
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

/** P4 Transaction B의 retry/final-failure 계약을 PostgreSQL에서 검증한다. */
@ActiveProfiles("integration-test")
@SpringBootTest
@Testcontainers
class ChangeLogDispatchFailureDatabaseIntegrationTest {

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
    @Autowired ChangeLogDispatchFailureRecorder failureRecorder;
    @Autowired DocumentChangeLogRepository documentChangeLogRepository;
    @Autowired ProcessingJobRepository processingJobRepository;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentVersionRepository documentVersionRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_dispatch_update ON document_change_logs");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_dispatch_update()");
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_failure_record_update ON document_change_logs");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_failure_record_update()");
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
    void recordsRetryWaitOnlyAfterTransactionARollsBack() {
        UserAccount owner = createUser("retryable-a@prizm.local");
        ChangeLogFixture fixture = createPendingChangeLog(owner, "retryable-a");
        installDispatchFailureTrigger();

        ChangeLogDispatchFailureException failure = catchThrowableOfType(
                () -> dispatchTransaction.dispatchNext(), ChangeLogDispatchFailureException.class);
        assertThat(failure).isNotNull();

        failureRecorder.record(
                fixture.changeLogId(),
                ChangeLogDispatchFailureDisposition.RETRYABLE,
                failure.getCause());

        DocumentChangeLog retried = documentChangeLogRepository.findById(fixture.changeLogId()).orElseThrow();
        assertThat(retried.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.RETRY_WAIT);
        assertThat(retried.getRetryCount()).isEqualTo(1);
        assertThat(retried.getNextRetryAt()).isAfter(Instant.now().plusSeconds(55));
        assertThat(processingJobRepository.count()).isZero();
    }

    @Test
    void permanentlyFailsTheQuarantinedVersionWithoutChangingPreviousActiveVersion() {
        UserAccount owner = createUser("permanent@prizm.local");
        ActiveVersionFixture fixture = createActiveV1AndPendingV2(owner);

        failureRecorder.record(
                fixture.changeLogId(),
                ChangeLogDispatchFailureDisposition.PERMANENT,
                new IllegalStateException("owner/version mismatch"));

        DocumentChangeLog failed = documentChangeLogRepository.findById(fixture.changeLogId()).orElseThrow();
        DocumentVersion versionTwo = documentVersionRepository.findById(fixture.versionTwoId()).orElseThrow();
        Document reloadedDocument = documentRepository.findById(fixture.documentId()).orElseThrow();
        assertThat(failed.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.FAILED);
        assertThat(failed.getRetryCount()).isZero();
        assertThat(failed.getNextRetryAt()).isNull();
        assertThat(versionTwo.getStatus()).isEqualTo(DocumentVersionStatus.FAILED);
        assertThat(reloadedDocument.getActiveVersionId()).isEqualTo(fixture.versionOneId());
    }

    @Test
    void doesNotRegressWhenAnotherDispatcherCommitsBeforeFailureRecording() {
        UserAccount owner = createUser("non-regression@prizm.local");
        ChangeLogFixture fixture = createPendingChangeLog(owner, "non-regression");
        installDispatchFailureTrigger();

        ChangeLogDispatchFailureException failure = catchThrowableOfType(
                () -> dispatchTransaction.dispatchNext(), ChangeLogDispatchFailureException.class);
        assertThat(failure).isNotNull();
        jdbcTemplate.execute("DROP TRIGGER fail_dispatch_update ON document_change_logs");
        jdbcTemplate.execute("DROP FUNCTION fail_dispatch_update()");

        assertThat(dispatchTransaction.dispatchNext()).isTrue();
        failureRecorder.record(
                fixture.changeLogId(),
                ChangeLogDispatchFailureDisposition.RETRYABLE,
                failure.getCause());

        DocumentChangeLog dispatched = documentChangeLogRepository.findById(fixture.changeLogId()).orElseThrow();
        assertThat(dispatched.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.DISPATCHED);
        assertThat(dispatched.getRetryCount()).isZero();
        assertThat(dispatched.getProcessingJobId()).isNotNull();
    }

    @Test
    void leavesLastCommittedStateWhenFailureRecorderTransactionCannotCommit() {
        UserAccount owner = createUser("failure-recorder-db@prizm.local");
        ChangeLogFixture fixture = createPendingChangeLog(owner, "failure-recorder-db");
        installFailureRecorderTrigger();

        assertThatThrownBy(() -> failureRecorder.record(
                fixture.changeLogId(),
                ChangeLogDispatchFailureDisposition.RETRYABLE,
                new IllegalStateException("temporary database failure")))
                .isInstanceOf(RuntimeException.class);

        DocumentChangeLog pending = documentChangeLogRepository.findById(fixture.changeLogId()).orElseThrow();
        assertThat(pending.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.PENDING);
        assertThat(pending.getRetryCount()).isZero();
        assertThat(pending.getNextRetryAt()).isNull();
    }

    @Test
    void keepsTheChangeLogDispatchedWhenTheExistingIndexingJobRetries() {
        UserAccount owner = createUser("indexing-retry@prizm.local");
        ChangeLogFixture fixture = createPendingChangeLog(owner, "indexing-retry");

        assertThat(dispatchTransaction.dispatchNext()).isTrue();
        DocumentChangeLog dispatched = documentChangeLogRepository.findById(fixture.changeLogId()).orElseThrow();
        jdbcTemplate.update(
                "UPDATE processing_jobs SET status = 'RETRY_WAIT', next_retry_at = now() + INTERVAL '1 minute' WHERE id = ?",
                dispatched.getProcessingJobId());

        ProcessingJob retriedJob = processingJobRepository.findById(dispatched.getProcessingJobId()).orElseThrow();
        DocumentChangeLog unchanged = documentChangeLogRepository.findById(fixture.changeLogId()).orElseThrow();
        assertThat(retriedJob.getStatus()).isEqualTo(ProcessingJobStatus.RETRY_WAIT);
        assertThat(unchanged.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.DISPATCHED);
    }

    private void installDispatchFailureTrigger() {
        jdbcTemplate.execute("""
                CREATE FUNCTION fail_dispatch_update() RETURNS trigger AS $$
                BEGIN
                    IF NEW.dispatch_status = 'DISPATCHED' THEN
                        RAISE EXCEPTION 'forced Transaction A failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_dispatch_update
                BEFORE UPDATE ON document_change_logs
                FOR EACH ROW EXECUTE FUNCTION fail_dispatch_update()
                """);
    }

    private void installFailureRecorderTrigger() {
        jdbcTemplate.execute("""
                CREATE FUNCTION fail_failure_record_update() RETURNS trigger AS $$
                BEGIN
                    IF NEW.dispatch_status = 'RETRY_WAIT' THEN
                        RAISE EXCEPTION 'forced Transaction B failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_failure_record_update
                BEFORE UPDATE ON document_change_logs
                FOR EACH ROW EXECUTE FUNCTION fail_failure_record_update()
                """);
    }

    private ChangeLogFixture createPendingChangeLog(UserAccount owner, String suffix) {
        Document document = documentRepository.saveAndFlush(
                Document.create(owner.getId(), "Failure " + suffix, DocumentType.OTHER));
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

    private ActiveVersionFixture createActiveV1AndPendingV2(UserAccount owner) {
        Document document = documentRepository.saveAndFlush(
                Document.create(owner.getId(), "Active then failed", DocumentType.OTHER));
        DocumentVersion versionOne = documentVersionRepository.saveAndFlush(DocumentVersion.quarantined(
                owner.getId(), document.getId(), 1, "v1.txt", DocumentFileType.TXT, "b".repeat(64)));
        versionOne.startProcessing();
        versionOne.activate();
        documentVersionRepository.saveAndFlush(versionOne);
        document.activateVersion(versionOne.getId());
        documentRepository.saveAndFlush(document);

        DocumentVersion versionTwo = documentVersionRepository.saveAndFlush(DocumentVersion.quarantined(
                owner.getId(), document.getId(), 2, "v2.txt", DocumentFileType.TXT, "c".repeat(64)));
        DocumentChangeLog changeLog = documentChangeLogRepository.saveAndFlush(
                DocumentChangeLog.pendingDocumentVersionCreated(owner.getId(), versionTwo.getId()));
        return new ActiveVersionFixture(document.getId(), versionOne.getId(), versionTwo.getId(), changeLog.getId());
    }

    private UserAccount createUser(String email) {
        return userAccountRepository.saveAndFlush(UserAccount.create(email, "test-password-hash", UserRole.USER));
    }

    private record ChangeLogFixture(Long changeLogId, Long versionId) {
    }

    private record ActiveVersionFixture(Long documentId, Long versionOneId, Long versionTwoId, Long changeLogId) {
    }
}
