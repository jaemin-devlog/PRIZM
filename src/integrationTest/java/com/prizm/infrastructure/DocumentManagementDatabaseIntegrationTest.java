package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.document.entity.Document;
import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.exception.DocumentManagementException;
import com.prizm.document.exception.DocumentNotFoundException;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.document.service.DocumentManagementService;
import com.prizm.document.service.DocumentThumbnailService;
import com.prizm.document.service.DocumentUploadService;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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

@ActiveProfiles("integration-test")
@SpringBootTest
@Testcontainers
class DocumentManagementDatabaseIntegrationTest {

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

    @Autowired DocumentManagementService documentManagementService;
    @Autowired DocumentUploadService documentUploadService;
    @Autowired DocumentThumbnailService documentThumbnailService;
    @Autowired DocumentRepository documentRepository;
    @Autowired DocumentVersionRepository documentVersionRepository;
    @Autowired ProcessingJobRepository processingJobRepository;
    @Autowired UserAccountRepository userAccountRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
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
    void hardDeletesTerminalOwnerMetadataWithActiveVersionFkAndRegistersCleanup() {
        UserAccount owner = createUser("terminal-owner@prizm.local");
        DocumentFixture fixture = createDocument(owner.getId(), "terminal.txt");
        jdbcTemplate.update(
                "UPDATE processing_jobs SET status = 'FAILED', completed_at = now() WHERE id = ?",
                fixture.processingJobId());

        assertThat(documentManagementService.delete(owner.getId(), fixture.documentId())).isTrue();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE id = ?", Long.class, fixture.documentId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_versions WHERE id = ?", Long.class, fixture.versionId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processing_jobs WHERE id = ?", Long.class, fixture.processingJobId())).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM file_cleanup_jobs WHERE storage_key = ?", String.class, fixture.storageKey()))
                .isEqualTo("PENDING");
        assertThat(documentManagementService.delete(owner.getId(), fixture.documentId())).isFalse();
    }

    @Test
    void rejectsDeletionWhileIndexingIsPendingWithoutRegisteringCleanup() {
        UserAccount owner = createUser("processing-owner@prizm.local");
        DocumentFixture fixture = createDocument(owner.getId(), "processing.txt");

        assertThatThrownBy(() -> documentManagementService.delete(owner.getId(), fixture.documentId()))
                .isInstanceOf(DocumentManagementException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE id = ?", Long.class, fixture.documentId())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM file_cleanup_jobs WHERE storage_key = ?", Long.class, fixture.storageKey())).isZero();
    }

    @Test
    void leavesAnotherUsersDocumentUntouched() {
        UserAccount owner = createUser("owner@prizm.local");
        UserAccount otherUser = createUser("other@prizm.local");
        DocumentFixture fixture = createDocument(owner.getId(), "owner-only.txt");

        assertThat(documentManagementService.delete(otherUser.getId(), fixture.documentId())).isFalse();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM documents WHERE id = ?", Long.class, fixture.documentId())).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM processing_jobs WHERE id = ?", Long.class, fixture.processingJobId())).isEqualTo(1L);
    }

    @Test
    void addsASecondVersionWhileKeepingThePreviousActiveVersionUntilIndexingCompletes() {
        UserAccount owner = createUser("version-owner@prizm.local");
        DocumentFixture fixture = createDocument(owner.getId(), "guide-v1.txt");
        jdbcTemplate.update(
                "UPDATE processing_jobs SET status = 'COMPLETED', completed_at = now() WHERE id = ?",
                fixture.processingJobId());
        MockMultipartFile revisedFile = new MockMultipartFile(
                "file", "guide-v2.txt", "text/plain", "revised evidence".getBytes());

        var response = documentUploadService.uploadVersion(owner.getId(), fixture.documentId(), revisedFile);

        Document reloadedDocument = documentRepository.findById(fixture.documentId()).orElseThrow();
        assertThat(reloadedDocument.getActiveVersionId()).isEqualTo(fixture.versionId());
        assertThat(documentVersionRepository
                .findByOwnerUserIdAndDocumentIdOrderByVersionNoDesc(owner.getId(), fixture.documentId()))
                .extracting(DocumentVersion::getVersionNo)
                .containsExactly(2, 1);
        assertThat(processingJobRepository
                .findByOwnerUserIdAndDocumentVersionId(owner.getId(), response.versionId()))
                .get()
                .extracting(ProcessingJob::getStatus)
                .isEqualTo(com.prizm.ingestion.entity.ProcessingJobStatus.PENDING);
        assertThat(Files.exists(STORAGE_ROOT.resolve("storage").resolve(
                "documents/%d/%d/guide-v2.txt".formatted(fixture.documentId(), response.versionId())))).isTrue();
    }

    @Test
    void readsTheExactStoredPdfForItsOwnerAndRejectsAnotherUser() throws IOException {
        UserAccount owner = createUser("pdf-owner@prizm.local");
        UserAccount otherUser = createUser("pdf-other@prizm.local");
        byte[] pdfBytes = createTextPdf("Verified career evidence");
        MockMultipartFile pdf = new MockMultipartFile(
                "file", "career-evidence.pdf", "application/pdf", pdfBytes);
        var upload = documentUploadService.upload(owner.getId(), "Career evidence", DocumentType.OTHER, pdf);

        var original = documentThumbnailService.getOriginal(
                owner.getId(), upload.documentId(), upload.versionId());

        assertThat(original.bytes()).isEqualTo(pdfBytes);
        assertThat(original.originalFileName()).isEqualTo("career-evidence.pdf");
        assertThat(original.fileType()).isEqualTo(DocumentFileType.PDF);
        assertThatThrownBy(() -> documentThumbnailService.getOriginal(
                        otherUser.getId(), upload.documentId(), upload.versionId()))
                .isInstanceOf(DocumentNotFoundException.class);
    }

    private UserAccount createUser(String email) {
        return userAccountRepository.saveAndFlush(UserAccount.create(email, "test-password-hash", UserRole.USER));
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("prizm-document-management-");
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

    private byte[] createTextPdf(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText(text);
                stream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private DocumentFixture createDocument(Long ownerUserId, String fileName) {
        Document document = documentRepository.saveAndFlush(Document.create(ownerUserId, fileName, DocumentType.OTHER));
        DocumentVersion version = DocumentVersion.quarantined(
                ownerUserId, document.getId(), fileName, DocumentFileType.TXT, "a".repeat(64));
        version.updateStoredFilePath("documents/%d/%s".formatted(document.getId(), fileName));
        version = documentVersionRepository.saveAndFlush(version);
        document.activateVersion(version.getId());
        documentRepository.saveAndFlush(document);
        ProcessingJob processingJob = processingJobRepository.saveAndFlush(
                ProcessingJob.pendingIndexing(ownerUserId, version.getId()));
        return new DocumentFixture(document.getId(), version.getId(), processingJob.getId(), version.getStoredFilePath());
    }

    private record DocumentFixture(Long documentId, Long versionId, Long processingJobId, String storageKey) {
    }
}
