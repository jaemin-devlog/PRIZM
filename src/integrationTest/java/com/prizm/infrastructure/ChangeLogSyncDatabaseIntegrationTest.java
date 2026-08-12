package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.prizm.changelog.entity.ChangeLogDispatchStatus;
import com.prizm.changelog.entity.DocumentChangeLog;
import com.prizm.changelog.repository.DocumentChangeLogRepository;
import com.prizm.changelog.service.ChangeLogDispatchFailureDisposition;
import com.prizm.changelog.service.ChangeLogDispatchFailureException;
import com.prizm.changelog.service.ChangeLogDispatchFailureRecorder;
import com.prizm.changelog.service.ChangeLogDispatchTransaction;
import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.exception.DocumentManagementErrorCode;
import com.prizm.document.exception.DocumentManagementException;
import com.prizm.document.exception.DocumentNotFoundException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.document.service.DocumentManagementService;
import com.prizm.document.service.DocumentUploadService;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** PRZ-010 P1~P6 ChangeLog 동기화 계약을 하나의 PostgreSQL 흐름으로 검증한다. */
@ActiveProfiles("integration-test")
@SpringBootTest
@Testcontainers
class ChangeLogSyncDatabaseIntegrationTest {

    private static final Path STORAGE_ROOT = createStorageRoot();

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
        registry.add("prizm.storage.root", () -> STORAGE_ROOT.resolve("storage").toString());
        registry.add("prizm.storage.temp", () -> STORAGE_ROOT.resolve("temp").toString());
    }

    @Autowired DocumentUploadService documentUploadService;
    @Autowired DocumentManagementService documentManagementService;
    @Autowired ChangeLogDispatchTransaction dispatchTransaction;
    @Autowired ChangeLogDispatchFailureRecorder failureRecorder;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentVersionRepository documentVersionRepository;
    @Autowired DocumentChangeLogRepository documentChangeLogRepository;
    @Autowired ProcessingJobRepository processingJobRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS p7_fail_dispatch_update ON document_change_logs");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS p7_fail_dispatch_update()");
        jdbcTemplate.update("DELETE FROM document_change_logs");
        jdbcTemplate.update("DELETE FROM file_cleanup_jobs");
        jdbcTemplate.update("DELETE FROM processing_jobs");
        jdbcTemplate.update("DELETE FROM document_chunks");
        jdbcTemplate.update("UPDATE documents SET active_version_id = NULL");
        jdbcTemplate.update("DELETE FROM document_versions");
        jdbcTemplate.update("DELETE FROM documents");
        userAccountRepository.deleteAll();
        clearStorageRoot();
    }

    @Test
    void commitsVersionAndPendingChangeLogThenDispatchesOnlyOneIndexingJobOnReplay() {
        UserAccount owner = createUser("atomic-owner@prizm.local");

        var upload = documentUploadService.upload(owner.getId(), "Atomic document", textFile("v1.txt", "V1 evidence"));

        DocumentVersion version = documentVersionRepository.findById(upload.versionId()).orElseThrow();
        DocumentChangeLog changeLog = changeLogForVersion(upload.versionId());
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.QUARANTINED);
        assertThat(changeLog.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.PENDING);
        assertThat(changeLog.getEventKey()).isEqualTo("DOCUMENT_VERSION_CREATED:" + upload.versionId());
        assertThat(processingJobRepository.count()).isZero();

        assertThat(dispatchTransaction.dispatchNext()).isTrue();

        DocumentChangeLog dispatched = changeLogForVersion(upload.versionId());
        assertThat(dispatched.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.DISPATCHED);
        assertThat(dispatched.getProcessingJobId()).isNotNull();
        assertThat(dispatched.getDispatchedAt()).isNotNull();
        assertThat(processingJobRepository.count()).isEqualTo(1L);
        assertThat(dispatchTransaction.dispatchNext()).isFalse();
        assertThat(processingJobRepository.count()).isEqualTo(1L);
    }

    @Test
    void allowsOnlyOneConcurrentDispatcherToCommitTheSameChangeLog() throws Exception {
        UserAccount owner = createUser("concurrent-owner@prizm.local");
        var upload = documentUploadService.upload(owner.getId(), "Concurrent document", textFile("v1.txt", "V1 evidence"));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = executor.submit(() -> dispatchAfterStart(start));
            Future<Boolean> second = executor.submit(() -> dispatchAfterStart(start));
            start.countDown();

            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            assertThat(changeLogForVersion(upload.versionId()).getDispatchStatus())
                    .isEqualTo(ChangeLogDispatchStatus.DISPATCHED);
            assertThat(processingJobRepository.count()).isEqualTo(1L);
        }
        finally {
            executor.shutdownNow();
        }
    }

    @Test
    void keepsVersionChangeLogAndJobOwnerScopedDuringDispatch() {
        UserAccount firstOwner = createUser("first-owner@prizm.local");
        UserAccount secondOwner = createUser("second-owner@prizm.local");
        var firstUpload = documentUploadService.upload(
                firstOwner.getId(), "First document", textFile("first.txt", "first evidence"));
        var secondUpload = documentUploadService.upload(
                secondOwner.getId(), "Second document", textFile("second.txt", "second evidence"));

        assertThatThrownBy(() -> jdbcTemplate.update(
                """
                INSERT INTO document_change_logs(
                    owner_user_id, document_version_id, event_type, event_key, dispatch_status, retry_count)
                VALUES (?, ?, 'DOCUMENT_VERSION_CREATED', ?, 'PENDING', 0)
                """,
                secondOwner.getId(), firstUpload.versionId(), "cross-owner:" + firstUpload.versionId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> documentUploadService.uploadVersion(
                secondOwner.getId(), firstUpload.documentId(), textFile("cross-owner.txt", "other evidence")))
                .isInstanceOf(DocumentNotFoundException.class);
        assertThat(documentManagementService.delete(secondOwner.getId(), firstUpload.documentId())).isFalse();

        assertThat(dispatchTransaction.dispatchNext()).isTrue();
        assertThat(dispatchTransaction.dispatchNext()).isTrue();

        assertOwnerAndVersionMatch(changeLogForVersion(firstUpload.versionId()));
        assertOwnerAndVersionMatch(changeLogForVersion(secondUpload.versionId()));
    }

    @Test
    void rollsBackTransactionAAndPersistsRetryWaitOnlyThroughFailureRecorder() {
        UserAccount owner = createUser("retry-owner@prizm.local");
        var upload = documentUploadService.upload(owner.getId(), "Retry document", textFile("v1.txt", "V1 evidence"));
        installDispatchFailureTrigger();

        ChangeLogDispatchFailureException failure = catchThrowableOfType(
                dispatchTransaction::dispatchNext, ChangeLogDispatchFailureException.class);
        assertThat(failure).isNotNull();
        assertThat(changeLogForVersion(upload.versionId()).getDispatchStatus())
                .isEqualTo(ChangeLogDispatchStatus.PENDING);
        assertThat(processingJobRepository.count()).isZero();

        failureRecorder.record(
                changeLogForVersion(upload.versionId()).getId(),
                ChangeLogDispatchFailureDisposition.RETRYABLE,
                failure.getCause());

        DocumentChangeLog retried = changeLogForVersion(upload.versionId());
        assertThat(retried.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.RETRY_WAIT);
        assertThat(retried.getRetryCount()).isEqualTo(1);
        assertThat(retried.getNextRetryAt()).isNotNull();
        assertThat(processingJobRepository.count()).isZero();
    }

    @Test
    void marksOnlyTheNewQuarantinedVersionFailedAndPreservesPreviousActiveVersion() {
        UserAccount owner = createUser("final-failure-owner@prizm.local");
        ActiveVersionFixture versionOne = createActiveV1(owner, "final-failure");
        var versionTwo = documentUploadService.uploadVersion(
                owner.getId(), versionOne.documentId(), textFile("v2.txt", "V2 evidence"));

        failureRecorder.record(
                changeLogForVersion(versionTwo.versionId()).getId(),
                ChangeLogDispatchFailureDisposition.PERMANENT,
                new IllegalStateException("owner/version mismatch"));

        Document document = documentRepository.findById(versionOne.documentId()).orElseThrow();
        assertThat(changeLogForVersion(versionTwo.versionId()).getDispatchStatus())
                .isEqualTo(ChangeLogDispatchStatus.FAILED);
        assertThat(documentVersionRepository.findById(versionTwo.versionId()).orElseThrow().getStatus())
                .isEqualTo(DocumentVersionStatus.FAILED);
        assertThat(document.getActiveVersionId()).isEqualTo(versionOne.versionId());
        assertThat(documentVersionRepository.findById(versionOne.versionId()).orElseThrow().getStatus())
                .isEqualTo(DocumentVersionStatus.ACTIVE);
    }

    @Test
    void blocksV3UploadAndDeletionForQuarantinedOrProcessingLatestVersion() {
        UserAccount owner = createUser("guard-owner@prizm.local");
        ActiveVersionFixture versionOne = createActiveV1(owner, "guard");
        var versionTwo = documentUploadService.uploadVersion(
                owner.getId(), versionOne.documentId(), textFile("v2.txt", "V2 evidence"));

        assertThat(changeLogForVersion(versionTwo.versionId()).getDispatchStatus())
                .isEqualTo(ChangeLogDispatchStatus.PENDING);
        assertThat(processingJobRepository.findByOwnerUserIdAndDocumentVersionId(owner.getId(), versionTwo.versionId()))
                .isEmpty();
        assertProcessing(() -> documentUploadService.uploadVersion(
                owner.getId(), versionOne.documentId(), textFile("v3.txt", "V3 evidence")));
        assertProcessing(() -> documentManagementService.delete(owner.getId(), versionOne.documentId()));
        assertThat(rowCount("file_cleanup_jobs")).isZero();

        jdbcTemplate.update("UPDATE document_versions SET status = 'PROCESSING' WHERE id = ?", versionTwo.versionId());
        assertProcessing(() -> documentUploadService.uploadVersion(
                owner.getId(), versionOne.documentId(), textFile("v3-processing.txt", "V3 evidence")));
        assertProcessing(() -> documentManagementService.delete(owner.getId(), versionOne.documentId()));
        assertThat(rowCount("file_cleanup_jobs")).isZero();
    }

    @Test
    void terminalDeletionRemovesChangeLogBeforeJobVersionAndDocument() {
        UserAccount owner = createUser("delete-owner@prizm.local");
        ActiveVersionFixture version = createActiveV1(owner, "delete");

        assertThat(documentManagementService.delete(owner.getId(), version.documentId())).isTrue();

        assertThat(rowCount("document_change_logs")).isZero();
        assertThat(rowCount("processing_jobs")).isZero();
        assertThat(rowCount("document_versions")).isZero();
        assertThat(rowCount("documents")).isZero();
    }

    private ActiveVersionFixture createActiveV1(UserAccount owner, String suffix) {
        var upload = documentUploadService.upload(
                owner.getId(), "Document " + suffix, textFile("v1-" + suffix + ".txt", "V1 evidence"));
        assertThat(dispatchTransaction.dispatchNext()).isTrue();
        ProcessingJob job = processingJobRepository
                .findByOwnerUserIdAndDocumentVersionId(owner.getId(), upload.versionId())
                .orElseThrow();
        jdbcTemplate.update(
                "UPDATE processing_jobs SET status = 'COMPLETED', completed_at = now() WHERE id = ?", job.getId());

        DocumentVersion version = documentVersionRepository.findById(upload.versionId()).orElseThrow();
        version.startProcessing();
        version.activate();
        documentVersionRepository.saveAndFlush(version);
        Document document = documentRepository.findById(upload.documentId()).orElseThrow();
        document.activateVersion(version.getId());
        documentRepository.saveAndFlush(document);
        return new ActiveVersionFixture(document.getId(), version.getId());
    }

    private void installDispatchFailureTrigger() {
        jdbcTemplate.execute("""
                CREATE FUNCTION p7_fail_dispatch_update() RETURNS trigger AS $$
                BEGIN
                    IF NEW.dispatch_status = 'DISPATCHED' THEN
                        RAISE EXCEPTION 'forced Transaction A failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER p7_fail_dispatch_update
                BEFORE UPDATE ON document_change_logs
                FOR EACH ROW EXECUTE FUNCTION p7_fail_dispatch_update()
                """);
    }

    private void assertOwnerAndVersionMatch(DocumentChangeLog changeLog) {
        ProcessingJob job = processingJobRepository.findById(changeLog.getProcessingJobId()).orElseThrow();
        assertThat(job.getOwnerUserId()).isEqualTo(changeLog.getOwnerUserId());
        assertThat(job.getDocumentVersionId()).isEqualTo(changeLog.getDocumentVersionId());
    }

    private DocumentChangeLog changeLogForVersion(Long versionId) {
        return documentChangeLogRepository.findAll().stream()
                .filter(changeLog -> changeLog.getDocumentVersionId().equals(versionId))
                .findFirst()
                .orElseThrow();
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

    private void assertProcessing(ThrowingRunnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DocumentManagementException.class)
                .extracting(exception -> ((DocumentManagementException) exception).code())
                .isEqualTo(DocumentManagementErrorCode.DOCUMENT_PROCESSING);
    }

    private UserAccount createUser(String email) {
        return userAccountRepository.saveAndFlush(UserAccount.create(email, "test-password-hash", UserRole.USER));
    }

    private MockMultipartFile textFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes());
    }

    private long rowCount(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("prizm-change-log-sync-");
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void clearStorageRoot() {
        try {
            if (!Files.exists(STORAGE_ROOT)) {
                return;
            }
            try (var paths = Files.walk(STORAGE_ROOT)) {
                paths.filter(path -> !path.equals(STORAGE_ROOT))
                        .sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.deleteIfExists(path);
                            }
                            catch (IOException exception) {
                                throw new UncheckedIOException(exception);
                            }
                        });
            }
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record ActiveVersionFixture(Long documentId, Long versionId) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
