package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.changelog.entity.ChangeLogDispatchStatus;
import com.prizm.changelog.entity.DocumentChangeLog;
import com.prizm.changelog.repository.DocumentChangeLogRepository;
import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.exception.DocumentManagementErrorCode;
import com.prizm.document.exception.DocumentManagementException;
import com.prizm.document.exception.DocumentNotFoundException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.document.service.DocumentManagementService;
import com.prizm.document.service.DocumentUploadService;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** P5의 Version 상태 1차 guard와 ProcessingJob 2차 guard를 PostgreSQL에서 검증한다. */
@ActiveProfiles("integration-test")
@SpringBootTest
@Testcontainers
class DocumentVersionGuardDatabaseIntegrationTest {

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
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentVersionRepository documentVersionRepository;
    @Autowired DocumentChangeLogRepository documentChangeLogRepository;
    @Autowired ProcessingJobRepository processingJobRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
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
    void rejectsV3AndDeletionWhileV2IsQuarantinedWithPendingChangeLogAndNoJob() {
        UserAccount owner = createUser("quarantined-owner@prizm.local");
        VersionFixture fixture = createActiveV1ThenV2(owner, "quarantined");

        assertThat(documentChangeLogRepository.findAll())
                .filteredOn(changeLog -> changeLog.getDocumentVersionId().equals(fixture.versionTwoId()))
                .singleElement()
                .satisfies(changeLog -> assertThat(changeLog.getDispatchStatus())
                        .isEqualTo(ChangeLogDispatchStatus.PENDING));
        assertThat(processingJobRepository.count()).isZero();

        assertProcessing(() -> documentUploadService.uploadVersion(
                owner.getId(), fixture.documentId(), textFile("v3.txt", "V3 evidence")));
        assertProcessing(() -> documentManagementService.delete(owner.getId(), fixture.documentId()));

        assertThat(rowCount("file_cleanup_jobs")).isZero();
        assertThat(rowCount("documents")).isEqualTo(1L);
        assertThat(documentVersionRepository
                .findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(owner.getId(), fixture.documentId()))
                .hasSize(2);
    }

    @Test
    void rejectsV3AndDeletionWhileV2IsProcessing() {
        UserAccount owner = createUser("processing-owner@prizm.local");
        VersionFixture fixture = createActiveV1ThenV2(owner, "processing");
        jdbcTemplate.update("UPDATE document_versions SET status = 'PROCESSING' WHERE id = ?", fixture.versionTwoId());

        assertProcessing(() -> documentUploadService.uploadVersion(
                owner.getId(), fixture.documentId(), textFile("v3.txt", "V3 evidence")));
        assertProcessing(() -> documentManagementService.delete(owner.getId(), fixture.documentId()));

        assertThat(rowCount("file_cleanup_jobs")).isZero();
        assertThat(rowCount("documents")).isEqualTo(1L);
    }

    @Test
    void allowsV3WhenNewestV2IsActiveOrFailed() {
        UserAccount owner = createUser("terminal-owner@prizm.local");
        VersionFixture activeFixture = createActiveV1ThenV2(owner, "active");
        jdbcTemplate.update("UPDATE document_versions SET status = 'ACTIVE' WHERE id = ?", activeFixture.versionTwoId());

        var afterActive = documentUploadService.uploadVersion(
                owner.getId(), activeFixture.documentId(), textFile("active-v3.txt", "V3 active evidence"));

        VersionFixture failedFixture = createActiveV1ThenV2(owner, "failed");
        jdbcTemplate.update("UPDATE document_versions SET status = 'FAILED' WHERE id = ?", failedFixture.versionTwoId());

        var afterFailed = documentUploadService.uploadVersion(
                owner.getId(), failedFixture.documentId(), textFile("failed-v3.txt", "V3 failed evidence"));

        assertThat(afterActive.documentId()).isEqualTo(activeFixture.documentId());
        assertThat(afterFailed.documentId()).isEqualTo(failedFixture.documentId());
        assertThat(documentVersionRepository
                .findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(owner.getId(), activeFixture.documentId()))
                .extracting(DocumentVersion::getVersionNo)
                .containsExactly(3, 2, 1);
        assertThat(documentVersionRepository
                .findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(owner.getId(), failedFixture.documentId()))
                .extracting(DocumentVersion::getVersionNo)
                .containsExactly(3, 2, 1);
    }

    @Test
    void keepsNonTerminalProcessingJobsAsTheSecondGuardForUploadAndDeletion() {
        UserAccount owner = createUser("job-guard-owner@prizm.local");
        VersionFixture fixture = createActiveV1ThenV2(owner, "job-guard");
        jdbcTemplate.update("UPDATE document_versions SET status = 'ACTIVE' WHERE id = ?", fixture.versionTwoId());
        processingJobRepository.saveAndFlush(ProcessingJob.pendingIndexing(owner.getId(), fixture.versionTwoId()));

        assertProcessing(() -> documentUploadService.uploadVersion(
                owner.getId(), fixture.documentId(), textFile("v3.txt", "V3 evidence")));
        assertProcessing(() -> documentManagementService.delete(owner.getId(), fixture.documentId()));

        assertThat(rowCount("file_cleanup_jobs")).isZero();
        assertThat(rowCount("documents")).isEqualTo(1L);
    }

    @Test
    void keepsOtherOwnersFromInferringOrChangingGuardedDocuments() {
        UserAccount owner = createUser("owner@prizm.local");
        UserAccount other = createUser("other@prizm.local");
        VersionFixture fixture = createActiveV1ThenV2(owner, "owner-isolation");

        assertThatThrownBy(() -> documentUploadService.uploadVersion(
                other.getId(), fixture.documentId(), textFile("other-v3.txt", "other evidence")))
                .isInstanceOf(DocumentNotFoundException.class);
        assertThat(documentManagementService.delete(other.getId(), fixture.documentId())).isFalse();

        assertThat(rowCount("documents")).isEqualTo(1L);
        assertThat(rowCount("file_cleanup_jobs")).isZero();
        assertThat(documentChangeLogRepository.findAll()).hasSize(1);
    }

    @Test
    void terminalDeletionRemovesChangeLogsBeforeJobsVersionsAndDocument() {
        UserAccount owner = createUser("delete-owner@prizm.local");
        TerminalFixture fixture = createTerminalDocumentWithDispatchedChangeLog(owner);

        assertThat(documentManagementService.delete(owner.getId(), fixture.documentId())).isTrue();

        assertThat(rowCount("document_change_logs")).isZero();
        assertThat(rowCount("processing_jobs")).isZero();
        assertThat(rowCount("document_versions")).isZero();
        assertThat(rowCount("documents")).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM file_cleanup_jobs WHERE storage_key = ?", String.class, fixture.storageKey()))
                .isEqualTo("PENDING");
    }

    private VersionFixture createActiveV1ThenV2(UserAccount owner, String suffix) {
        Document document = documentRepository.saveAndFlush(
                Document.create(owner.getId(), "Guard " + suffix, DocumentType.OTHER));
        DocumentVersion versionOne = documentVersionRepository.saveAndFlush(DocumentVersion.quarantined(
                owner.getId(), document.getId(), 1, "v1-" + suffix + ".txt", DocumentFileType.TXT, "a".repeat(64)));
        versionOne.startProcessing();
        versionOne.activate();
        documentVersionRepository.saveAndFlush(versionOne);
        document.activateVersion(versionOne.getId());
        documentRepository.saveAndFlush(document);

        var versionTwo = documentUploadService.uploadVersion(
                owner.getId(), document.getId(), textFile("v2-" + suffix + ".txt", "V2 evidence"));
        return new VersionFixture(document.getId(), versionOne.getId(), versionTwo.versionId());
    }

    private TerminalFixture createTerminalDocumentWithDispatchedChangeLog(UserAccount owner) {
        Document document = documentRepository.saveAndFlush(
                Document.create(owner.getId(), "Terminal delete", DocumentType.OTHER));
        DocumentVersion version = DocumentVersion.quarantined(
                owner.getId(), document.getId(), "terminal.txt", DocumentFileType.TXT, "b".repeat(64));
        String storageKey = "documents/%d/terminal.txt".formatted(document.getId());
        version.updateStoredFilePath(storageKey);
        version = documentVersionRepository.saveAndFlush(version);
        version.startProcessing();
        version.activate();
        documentVersionRepository.saveAndFlush(version);
        document.activateVersion(version.getId());
        documentRepository.saveAndFlush(document);

        ProcessingJob job = processingJobRepository.saveAndFlush(
                ProcessingJob.pendingIndexing(owner.getId(), version.getId()));
        jdbcTemplate.update(
                "UPDATE processing_jobs SET status = 'COMPLETED', completed_at = now() WHERE id = ?", job.getId());
        DocumentChangeLog changeLog = documentChangeLogRepository.saveAndFlush(
                DocumentChangeLog.pendingDocumentVersionCreated(owner.getId(), version.getId()));
        changeLog.markDispatched(job.getId(), Instant.now());
        documentChangeLogRepository.saveAndFlush(changeLog);
        return new TerminalFixture(document.getId(), storageKey);
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
            return Files.createTempDirectory("prizm-version-guard-");
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

    private record VersionFixture(Long documentId, Long versionOneId, Long versionTwoId) {
    }

    private record TerminalFixture(Long documentId, String storageKey) {
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run();
    }
}
