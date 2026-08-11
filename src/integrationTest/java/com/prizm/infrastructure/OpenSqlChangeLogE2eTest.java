package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.prizm.changelog.entity.ChangeLogDispatchStatus;
import com.prizm.changelog.entity.DocumentChangeLog;
import com.prizm.changelog.repository.DocumentChangeLogRepository;
import com.prizm.changelog.service.ChangeLogDispatchFailureDisposition;
import com.prizm.changelog.service.ChangeLogDispatchFailureException;
import com.prizm.changelog.service.ChangeLogDispatchFailureRecorder;
import com.prizm.changelog.service.ChangeLogDispatchTransaction;
import com.prizm.document.dto.response.DocumentUploadResponse;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.document.service.DocumentUploadService;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import com.prizm.ingestion.service.IndexingCoordinator;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.dto.response.SearchResponse;
import com.prizm.search.service.SearchService;
import com.prizm.user.entity.UserAccount;
import com.prizm.user.entity.UserRole;
import com.prizm.user.repository.UserAccountRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Actual OpenSQL direct-5432 and Ollama P9 V1-to-V2 ChangeLog E2E verification. */
@ActiveProfiles("integration-test")
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_OPENSQL_P9_TESTS", matches = "(?i:true|1)")
class OpenSqlChangeLogE2eTest {

    private static final Path STORAGE_ROOT = createStorageRoot();
    private static final String V1_QUERY = "legacy payroll continuity procedure";
    private static final String V2_QUERY = "kubernetes incident orchestration ledger";

    @DynamicPropertySource
    static void p9Properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> required("PRIZM_P9_DB_URL"));
        registry.add("spring.datasource.username", () -> required("PRIZM_P9_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> required("PRIZM_P9_DB_PASSWORD"));
        registry.add("spring.flyway.url", () -> required("PRIZM_P9_DB_URL"));
        registry.add("spring.flyway.user", () -> required("PRIZM_P9_DB_USERNAME"));
        registry.add("spring.flyway.password", () -> required("PRIZM_P9_DB_PASSWORD"));
        registry.add("spring.ai.ollama.base-url", () -> required("PRIZM_P9_OLLAMA_URL"));
        registry.add("spring.ai.ollama.embedding.model", () -> "bge-m3");
        registry.add("prizm.embedding.dimensions", () -> 1024);
        registry.add("prizm.storage.root", STORAGE_ROOT::toString);
        registry.add("prizm.storage.temp", () -> STORAGE_ROOT.resolve("temp").toString());
        registry.add("prizm.change-log.scheduler.enabled", () -> false);
        registry.add("prizm.ingestion.worker-enabled", () -> false);
        registry.add("prizm.cleanup.worker-enabled", () -> false);
    }

    @Autowired private DocumentUploadService documentUploadService;
    @Autowired private ChangeLogDispatchTransaction dispatchTransaction;
    @Autowired private ChangeLogDispatchFailureRecorder failureRecorder;
    @Autowired private IndexingCoordinator indexingCoordinator;
    @Autowired private SearchService searchService;
    @Autowired private EmbeddingService embeddingService;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentVersionRepository documentVersionRepository;
    @Autowired private DocumentChangeLogRepository documentChangeLogRepository;
    @Autowired private ProcessingJobRepository processingJobRepository;
    @Autowired private UserAccountRepository userAccountRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Value("${spring.ai.ollama.embedding.model}")
    private String embeddingModel;

    @Value("${prizm.embedding.dimensions}")
    private int embeddingDimensions;

    @AfterEach
    void cleanUp() {
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS p9_fail_dispatch_update ON document_change_logs");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS p9_fail_dispatch_update()");
        jdbcTemplate.update("DELETE FROM document_change_logs");
        jdbcTemplate.update("DELETE FROM file_cleanup_jobs");
        jdbcTemplate.update("DELETE FROM processing_jobs");
        jdbcTemplate.update("DELETE FROM document_chunks");
        jdbcTemplate.update("UPDATE documents SET active_version_id = NULL");
        jdbcTemplate.update("DELETE FROM document_versions");
        jdbcTemplate.update("DELETE FROM documents");
        jdbcTemplate.update("DELETE FROM users");
        clearStorageRoot();
    }

    @AfterAll
    static void removeStorageRoot() {
        clearStorageRoot();
        try {
            Files.deleteIfExists(STORAGE_ROOT);
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    @Test
    void switchesSearchFromActiveV1ToActiveV2ThroughActualChangeLogWorkerAndOllama() {
        assertThat(embeddingModel).isEqualTo("bge-m3");
        assertThat(embeddingDimensions).isEqualTo(1024);

        UserAccount owner = createUser("v1-v2");
        ActiveVersion v1 = createActiveVersion(owner, "P9 V1 resume", v1Content());
        assertSearchesVersion(owner.getId(), V1_QUERY, v1.versionId());

        DocumentUploadResponse v2Upload = documentUploadService.uploadVersion(
                owner.getId(), v1.documentId(), textFile("p9-v2.txt", v2Content()));
        assertThat(v2Upload.status()).isEqualTo(DocumentVersionStatus.QUARANTINED);
        assertThat(changeLogForVersion(v2Upload.versionId()).getDispatchStatus())
                .isEqualTo(ChangeLogDispatchStatus.PENDING);
        assertThat(processingJobRepository.findByOwnerUserIdAndDocumentVersionId(owner.getId(), v2Upload.versionId()))
                .isEmpty();
        assertSearchesVersion(owner.getId(), V1_QUERY, v1.versionId());

        assertThat(dispatchTransaction.dispatchNext()).isTrue();
        DocumentChangeLog dispatched = changeLogForVersion(v2Upload.versionId());
        ProcessingJob v2Job = processingJobRepository
                .findByOwnerUserIdAndDocumentVersionId(owner.getId(), v2Upload.versionId())
                .orElseThrow();
        assertThat(dispatched.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.DISPATCHED);
        assertThat(dispatched.getProcessingJobId()).isEqualTo(v2Job.getId());
        assertThat(v2Job.getStatus()).isEqualTo(ProcessingJobStatus.PENDING);

        assertThat(indexingCoordinator.processNext()).isTrue();
        assertThat(processingJobRepository.findById(v2Job.getId()).orElseThrow().getStatus())
                .isEqualTo(ProcessingJobStatus.COMPLETED);
        assertThat(documentVersionRepository.findById(v2Upload.versionId()).orElseThrow().getStatus())
                .isEqualTo(DocumentVersionStatus.ACTIVE);
        assertThat(documentRepository.findById(v1.documentId()).orElseThrow().getActiveVersionId())
                .isEqualTo(v2Upload.versionId());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM document_chunks WHERE document_version_id = ?", Long.class, v2Upload.versionId()))
                .isPositive();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT MIN(vector_dims(embedding)) FROM document_chunks WHERE document_version_id = ?",
                Integer.class,
                v2Upload.versionId())).isEqualTo(1024);
        assertThat(embeddingService.embed(V2_QUERY)).hasSize(1024);

        assertSearchesVersion(owner.getId(), V2_QUERY, v2Upload.versionId());
        List<CareerEvidenceSearchResponse> evidence = searchService.searchCareerEvidence(owner.getId(), V2_QUERY);
        assertThat(evidence).isNotEmpty();
        assertThat(evidence).extracting(CareerEvidenceSearchResponse::documentVersionId)
                .containsOnly(v2Upload.versionId())
                .doesNotContain(v1.versionId());
    }

    @Test
    void keepsV1ActiveAndSearchableWhenDispatchFailsPermanently() {
        UserAccount owner = createUser("dispatch-failure");
        ActiveVersion v1 = createActiveVersion(owner, "P9 dispatch V1", v1Content());
        DocumentUploadResponse v2Upload = documentUploadService.uploadVersion(
                owner.getId(), v1.documentId(), textFile("p9-dispatch-failure-v2.txt", v2Content()));
        installDispatchFailureTrigger();

        ChangeLogDispatchFailureException failure = catchThrowableOfType(
                dispatchTransaction::dispatchNext, ChangeLogDispatchFailureException.class);
        assertThat(failure).isNotNull();
        failureRecorder.record(
                changeLogForVersion(v2Upload.versionId()).getId(),
                ChangeLogDispatchFailureDisposition.PERMANENT,
                failure.getCause());

        assertThat(changeLogForVersion(v2Upload.versionId()).getDispatchStatus())
                .isEqualTo(ChangeLogDispatchStatus.FAILED);
        assertThat(documentVersionRepository.findById(v2Upload.versionId()).orElseThrow().getStatus())
                .isEqualTo(DocumentVersionStatus.FAILED);
        assertThat(documentRepository.findById(v1.documentId()).orElseThrow().getActiveVersionId())
                .isEqualTo(v1.versionId());
        assertSearchesVersion(owner.getId(), V1_QUERY, v1.versionId());
    }

    @Test
    void keepsV1ActiveAndSearchableWhenExistingIndexingWorkerFailsPermanently() {
        UserAccount owner = createUser("indexing-failure");
        ActiveVersion v1 = createActiveVersion(owner, "P9 indexing V1", v1Content());
        DocumentUploadResponse v2Upload = documentUploadService.uploadVersion(
                owner.getId(), v1.documentId(), textFile("p9-indexing-failure-v2.txt", v2Content()));
        assertThat(dispatchTransaction.dispatchNext()).isTrue();

        DocumentVersion v2 = documentVersionRepository.findById(v2Upload.versionId()).orElseThrow();
        deleteStoredFile(v2.getStoredFilePath());
        assertThat(indexingCoordinator.processNext()).isTrue();

        ProcessingJob v2Job = processingJobRepository
                .findByOwnerUserIdAndDocumentVersionId(owner.getId(), v2Upload.versionId())
                .orElseThrow();
        assertThat(v2Job.getStatus()).isEqualTo(ProcessingJobStatus.FAILED);
        assertThat(documentVersionRepository.findById(v2Upload.versionId()).orElseThrow().getStatus())
                .isEqualTo(DocumentVersionStatus.FAILED);
        assertThat(documentRepository.findById(v1.documentId()).orElseThrow().getActiveVersionId())
                .isEqualTo(v1.versionId());
        assertSearchesVersion(owner.getId(), V1_QUERY, v1.versionId());
    }

    private ActiveVersion createActiveVersion(UserAccount owner, String title, String content) {
        DocumentUploadResponse upload = documentUploadService.upload(
                owner.getId(), title, textFile("p9-v1.txt", content));
        assertThat(upload.status()).isEqualTo(DocumentVersionStatus.QUARANTINED);
        assertThat(changeLogForVersion(upload.versionId()).getDispatchStatus())
                .isEqualTo(ChangeLogDispatchStatus.PENDING);
        assertThat(processingJobRepository.findByOwnerUserIdAndDocumentVersionId(owner.getId(), upload.versionId()))
                .isEmpty();
        assertThat(dispatchTransaction.dispatchNext()).isTrue();
        assertThat(indexingCoordinator.processNext()).isTrue();
        assertThat(documentVersionRepository.findById(upload.versionId()).orElseThrow().getStatus())
                .isEqualTo(DocumentVersionStatus.ACTIVE);
        assertThat(documentRepository.findById(upload.documentId()).orElseThrow().getActiveVersionId())
                .isEqualTo(upload.versionId());
        return new ActiveVersion(upload.documentId(), upload.versionId());
    }

    private void assertSearchesVersion(Long ownerUserId, String query, Long expectedVersionId) {
        SearchResponse result = searchService.search(ownerUserId, query);
        assertThat(result.documentVersionId()).isEqualTo(expectedVersionId);
    }

    private DocumentChangeLog changeLogForVersion(Long versionId) {
        return documentChangeLogRepository.findAll().stream()
                .filter(changeLog -> changeLog.getDocumentVersionId().equals(versionId))
                .findFirst()
                .orElseThrow();
    }

    private UserAccount createUser(String scenario) {
        return userAccountRepository.saveAndFlush(UserAccount.create(
                "p9-" + scenario + "-" + UUID.randomUUID() + "@compatibility.invalid",
                "p9-test-password-hash",
                UserRole.USER));
    }

    private MockMultipartFile textFile(String name, String content) {
        return new MockMultipartFile("file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    private void installDispatchFailureTrigger() {
        jdbcTemplate.execute("""
                CREATE FUNCTION p9_fail_dispatch_update() RETURNS trigger AS $$
                BEGIN
                    IF NEW.dispatch_status = 'DISPATCHED' THEN
                        RAISE EXCEPTION 'forced P9 dispatch failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbcTemplate.execute("""
                CREATE TRIGGER p9_fail_dispatch_update
                BEFORE UPDATE ON document_change_logs
                FOR EACH ROW EXECUTE FUNCTION p9_fail_dispatch_update()
                """);
    }

    private void deleteStoredFile(String storedFilePath) {
        Path target = STORAGE_ROOT.resolve(storedFilePath).normalize();
        if (!target.startsWith(STORAGE_ROOT)) {
            throw new IllegalArgumentException("P9 storage path escaped its dedicated test root.");
        }
        try {
            Files.deleteIfExists(target);
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static String v1Content() {
        return ("P9 V1 legacy payroll continuity procedure preserves annual leave evidence. ").repeat(40);
    }

    private static String v2Content() {
        return ("P9 V2 kubernetes incident orchestration ledger documents the production response. ").repeat(40);
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("P9 OpenSQL configuration is missing " + name + ".");
        }
        return value;
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("prizm-opensql-p9-");
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

    private record ActiveVersion(Long documentId, Long versionId) {
    }
}
