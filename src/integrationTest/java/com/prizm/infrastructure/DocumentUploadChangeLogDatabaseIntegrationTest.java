package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.changelog.entity.ChangeLogDispatchStatus;
import com.prizm.changelog.entity.ChangeLogEventType;
import com.prizm.changelog.entity.DocumentChangeLog;
import com.prizm.changelog.repository.DocumentChangeLogRepository;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.exception.DocumentNotFoundException;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.document.service.DocumentUploadService;
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

/** P2 upload transaction contract for Version, ChangeLog, and file rollback compensation. */
@ActiveProfiles("integration-test")
@SpringBootTest
@Testcontainers
class DocumentUploadChangeLogDatabaseIntegrationTest {

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
    @Autowired DocumentVersionRepository documentVersionRepository;
    @Autowired DocumentChangeLogRepository documentChangeLogRepository;
    @Autowired ProcessingJobRepository processingJobRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS fail_change_log_insert ON document_change_logs");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_change_log_insert()");
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
    void commitsNewV1WithExactlyOnePendingChangeLogAndNoProcessingJob() {
        UserAccount owner = createUser("v1-owner@prizm.local");

        var response = documentUploadService.upload(
                owner.getId(), "V1 document", textFile("v1.txt", "V1 immutable evidence"));

        DocumentVersion version = documentVersionRepository.findById(response.versionId()).orElseThrow();
        List<DocumentChangeLog> changeLogs = documentChangeLogRepository.findAll();
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.QUARANTINED);
        assertThat(changeLogs).hasSize(1);
        assertPendingVersionCreated(changeLogs.get(0), owner.getId(), response.versionId());
        assertThat(processingJobRepository.count()).isZero();
    }

    @Test
    void commitsV2WithItsOwnPendingChangeLogAndNoProcessingJob() {
        UserAccount owner = createUser("v2-owner@prizm.local");
        var v1 = documentUploadService.upload(owner.getId(), "Versioned document", textFile("v1.txt", "V1 evidence"));
        jdbcTemplate.update("UPDATE document_versions SET status = 'ACTIVE' WHERE id = ?", v1.versionId());

        var v2 = documentUploadService.uploadVersion(
                owner.getId(), v1.documentId(), textFile("v2.txt", "V2 replacement evidence"));

        List<DocumentVersion> versions = documentVersionRepository
                .findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(owner.getId(), v1.documentId());
        List<DocumentChangeLog> changeLogs = documentChangeLogRepository.findAll();
        assertThat(versions).extracting(DocumentVersion::getVersionNo).containsExactly(2, 1);
        assertThat(versions).extracting(DocumentVersion::getStatus)
                .containsExactly(DocumentVersionStatus.QUARANTINED, DocumentVersionStatus.ACTIVE);
        assertThat(changeLogs).hasSize(2);
        assertThat(changeLogs).allSatisfy(changeLog ->
                assertPendingVersionCreated(changeLog, owner.getId(), changeLog.getDocumentVersionId()));
        assertThat(changeLogs).extracting(DocumentChangeLog::getDocumentVersionId)
                .containsExactlyInAnyOrder(v1.versionId(), v2.versionId());
        assertThat(processingJobRepository.count()).isZero();
    }

    @Test
    void rollsBackDocumentVersionAndStoredFileWhenChangeLogInsertFails() {
        UserAccount owner = createUser("rollback-owner@prizm.local");
        jdbcTemplate.execute("""
                CREATE FUNCTION fail_change_log_insert() RETURNS trigger AS $$
                BEGIN
                    RAISE EXCEPTION 'forced ChangeLog insert failure';
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER fail_change_log_insert
                BEFORE INSERT ON document_change_logs
                FOR EACH ROW EXECUTE FUNCTION fail_change_log_insert()
                """);

        assertThatThrownBy(() -> documentUploadService.upload(
                owner.getId(), "Rollback document", textFile("rollback.txt", "stored before DB failure")))
                .isInstanceOf(RuntimeException.class);

        assertThat(rowCount("documents")).isZero();
        assertThat(rowCount("document_versions")).isZero();
        assertThat(rowCount("document_change_logs")).isZero();
        assertThat(rowCount("processing_jobs")).isZero();
        if (hasStoredOriginal()) {
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM file_cleanup_jobs", String.class)).isEqualTo("PENDING");
        }
        else {
            assertThat(rowCount("file_cleanup_jobs")).isZero();
        }
    }

    @Test
    void rejectsAnotherOwnerBeforeCreatingAVersionOrChangeLog() {
        UserAccount owner = createUser("owner@prizm.local");
        UserAccount other = createUser("other@prizm.local");
        var upload = documentUploadService.upload(owner.getId(), "Owner document", textFile("owner.txt", "owner evidence"));

        assertThatThrownBy(() -> documentUploadService.uploadVersion(
                other.getId(), upload.documentId(), textFile("other.txt", "other evidence")))
                .isInstanceOf(DocumentNotFoundException.class);

        assertThat(documentVersionRepository
                .findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(owner.getId(), upload.documentId()))
                .hasSize(1);
        assertThat(documentChangeLogRepository.findAll()).hasSize(1);
        assertThat(processingJobRepository.count()).isZero();
    }

    private UserAccount createUser(String email) {
        return userAccountRepository.saveAndFlush(UserAccount.create(email, "test-password-hash", UserRole.USER));
    }

    private MockMultipartFile textFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes());
    }

    private void assertPendingVersionCreated(DocumentChangeLog changeLog, Long ownerUserId, Long versionId) {
        assertThat(changeLog.getOwnerUserId()).isEqualTo(ownerUserId);
        assertThat(changeLog.getDocumentVersionId()).isEqualTo(versionId);
        assertThat(changeLog.getEventType()).isEqualTo(ChangeLogEventType.DOCUMENT_VERSION_CREATED);
        assertThat(changeLog.getEventKey()).isEqualTo("DOCUMENT_VERSION_CREATED:" + versionId);
        assertThat(changeLog.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.PENDING);
        assertThat(changeLog.getProcessingJobId()).isNull();
    }

    private long rowCount(String tableName) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
    }

    private boolean hasStoredOriginal() {
        Path storage = STORAGE_ROOT.resolve("storage");
        if (!Files.exists(storage)) {
            return false;
        }
        try (var paths = Files.walk(storage)) {
            return paths.anyMatch(Files::isRegularFile);
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("prizm-upload-change-log-");
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
}
