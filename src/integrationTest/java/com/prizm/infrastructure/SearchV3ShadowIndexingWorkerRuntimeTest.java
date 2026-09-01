package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.prizm.document.entity.DocumentFileType;
import com.prizm.embedding.exception.EmbeddingErrorCode;
import com.prizm.embedding.exception.EmbeddingException;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.infrastructure.storage.LocalFileStorage;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.config.PdfExtractionProperties;
import com.prizm.ingestion.service.DocumentTextExtractor;
import com.prizm.ingestion.service.IndexingRetryPolicy;
import com.prizm.search.v3.indexing.exception.StaleSearchV3IndexingJobClaimException;
import com.prizm.search.v3.indexing.model.SearchV3EmbeddingModelContract;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import com.prizm.search.v3.indexing.model.SearchV3IndexingPolicies;
import com.prizm.search.v3.indexing.model.SearchV3LogicalInventoryPlan;
import com.prizm.search.v3.indexing.model.SearchV3PreparedInventory;
import com.prizm.search.v3.indexing.model.SearchV3RecoveryLock;
import com.prizm.search.v3.indexing.repository.SearchV3ArtifactStorageRepository;
import com.prizm.search.v3.indexing.repository.SearchV3DocumentSourceRepository;
import com.prizm.search.v3.indexing.repository.SearchV3GenerationContractRepository;
import com.prizm.search.v3.indexing.repository.SearchV3IndexingJobRepository;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository;
import com.prizm.search.v3.indexing.service.SearchV3ArtifactStorageService;
import com.prizm.search.v3.indexing.service.SearchV3EmbeddingModelContractProvider;
import com.prizm.search.v3.indexing.service.SearchV3GenerationContractService;
import com.prizm.search.v3.indexing.service.SearchV3IndexingCoordinator;
import com.prizm.search.v3.indexing.service.SearchV3IndexingFailureClassifier;
import com.prizm.search.v3.indexing.service.SearchV3IndexingJobService;
import com.prizm.search.v3.indexing.service.SearchV3InventoryActivationService;
import com.prizm.search.v3.indexing.service.SearchV3InventoryVerifier;
import com.prizm.search.v3.indexing.service.SearchV3LogicalInventoryPlanner;
import com.prizm.search.v3.indexing.service.SearchV3ShadowIndexingProcessor;
import com.prizm.search.v3.indexing.service.SearchV3WorkerLeaseHeartbeat;
import com.prizm.search.v3.indexing.structure.ExtractedDocumentSource;
import com.prizm.search.v3.indexing.structure.SearchV3Structure;
import com.prizm.search.v3.indexing.structure.SearchV3StructureBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.NameMatchTransactionAttributeSource;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/** 실제 TXT/PDF 원문에서 Search V3 shadow activation까지의 Worker 경계를 PostgreSQL로 검증한다. */
@Testcontainers
class SearchV3ShadowIndexingWorkerRuntimeTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");
    private static final String MODEL_DIGEST = "b".repeat(64);

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm_search_v3_worker")
            .withUsername("prizm")
            .withPassword("prizm-test");

    private static HikariDataSource dataSource;
    private static JdbcTemplate jdbc;

    @TempDir
    Path storageRoot;

    @BeforeAll
    static void migrate() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(postgres.getJdbcUrl());
        config.setUsername(postgres.getUsername());
        config.setPassword(postgres.getPassword());
        config.setMaximumPoolSize(6);
        config.setMinimumIdle(0);
        config.setPoolName("prz040-worker");
        dataSource = new HikariDataSource(config);
        Flyway.configure().dataSource(dataSource).load().migrate();
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void closeDataSource() {
        if (dataSource != null) {
            dataSource.close();
        }
    }

    @BeforeEach
    void cleanDatabase() {
        jdbc.execute("TRUNCATE TABLE users RESTART IDENTITY CASCADE");
    }

    @Test
    void indexesTxtPrecomputesBothVectorKindsAndActivatesSameVersion() {
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());
        Runtime runtime = runtime(storage, deterministicEmbedding());
        Fixture fixture = createCurrentFixture(
                storage,
                DocumentFileType.TXT,
                "career.txt",
                """
                운영 자동화
                배포 전 검증 단계를 자동화해 반복 오류를 줄였다.
                장애 대응 절차를 문서화해 복구 시간을 단축했다.

                고객 조사
                - 사용자 인터뷰 결과를 정리했다.
                - 개선안을 제품 계획에 반영했다.
                """.getBytes(StandardCharsets.UTF_8));
        Job job = insertPendingGeneration(fixture);
        long activeVersionBefore = activeVersion(fixture.documentId());

        assertThat(runtime.coordinator().processNext()).isTrue();

        assertThat(status("search_v3_index_generations", job.generationId())).isEqualTo("ACTIVE");
        assertThat(status("search_v3_indexing_jobs", job.jobId())).isEqualTo("COMPLETED");
        assertThat(activeSearchV3Generation(fixture.documentId())).isEqualTo(job.generationId());
        assertThat(activeVersion(fixture.documentId())).isEqualTo(activeVersionBefore);
        assertThat(documentVersionStatus(fixture.documentVersionId())).isEqualTo("ACTIVE");
        assertExactInventory(job.generationId());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_chunks", Long.class)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM search_v3_evidence_children WHERE generation_id = ? AND page_no IS NULL",
                Long.class,
                job.generationId())).isEqualTo(childCount(job.generationId()));
    }

    @Test
    void indexesTextLayerPdfWithoutCrossPageGroupingAndDefersNonCurrentVersion() {
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());
        Runtime runtime = runtime(storage, deterministicEmbedding());
        Fixture active = createCurrentFixture(
                storage,
                DocumentFileType.TXT,
                "current.txt",
                "현재 버전의 원문 근거입니다.".getBytes(StandardCharsets.UTF_8));
        Fixture candidate = createAdditionalVersion(
                storage,
                active,
                2,
                DocumentFileType.PDF,
                "portfolio.pdf",
                textPdf(List.of(
                        List.of("Platform operations", "Reduced deployment failures through staged checks."),
                        List.of("Customer research", "Interviewed five participants and summarized findings."))));
        Job job = insertPendingGeneration(candidate);

        assertThat(runtime.coordinator().processNext()).isTrue();

        assertThat(status("search_v3_index_generations", job.generationId())).isEqualTo("READY");
        assertThat(status("search_v3_indexing_jobs", job.jobId())).isEqualTo("RETRY_WAIT");
        assertThat(activeVersion(active.documentId())).isEqualTo(active.documentVersionId());
        assertThat(activeSearchV3Generation(active.documentId())).isNull();
        assertThat(documentVersionStatus(active.documentVersionId())).isEqualTo("ACTIVE");
        assertThat(documentVersionStatus(candidate.documentVersionId())).isEqualTo("PROCESSING");
        assertExactInventory(job.generationId());
        assertThat(jdbc.queryForList(
                "SELECT DISTINCT page_no FROM search_v3_retrieval_passages WHERE generation_id = ? ORDER BY page_no",
                Integer.class,
                job.generationId())).containsExactly(1, 2);
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*)
                FROM search_v3_evidence_children child
                JOIN search_v3_retrieval_passages passage ON passage.id = child.passage_id
                WHERE child.generation_id = ? AND child.page_no IS DISTINCT FROM passage.page_no
                """,
                Long.class,
                job.generationId())).isZero();
    }

    @Test
    void safeReindexSupersedesPreviousAndEmbeddingFailurePreservesIt() {
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());
        Runtime good = runtime(storage, deterministicEmbedding());
        Fixture fixture = createCurrentFixture(
                storage,
                DocumentFileType.TXT,
                "reindex.txt",
                """
                서비스 안정화
                배포 확인 절차를 개선해 운영 오류를 줄였다.
                고객 요청을 분류해 처리 시간을 단축했다.
                """.getBytes(StandardCharsets.UTF_8));
        Job first = insertPendingGeneration(fixture);
        assertThat(good.coordinator().processNext()).isTrue();
        Job second = insertPendingGeneration(fixture);
        assertThat(good.coordinator().processNext()).isTrue();

        assertThat(status("search_v3_index_generations", first.generationId())).isEqualTo("SUPERSEDED");
        assertThat(status("search_v3_index_generations", second.generationId())).isEqualTo("ACTIVE");
        assertThat(activeSearchV3Generation(fixture.documentId())).isEqualTo(second.generationId());

        Runtime passageFailing = runtime(storage, failingEmbedding(0));
        Job passageFailed = insertPendingGeneration(fixture);
        assertThat(passageFailing.coordinator().processNext()).isTrue();

        assertThat(status("search_v3_index_generations", passageFailed.generationId())).isEqualTo("FAILED");
        assertThat(status("search_v3_indexing_jobs", passageFailed.jobId())).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject(
                "SELECT failure_stage FROM search_v3_indexing_jobs WHERE id = ?",
                String.class,
                passageFailed.jobId())).isEqualTo("PASSAGE_EMBEDDING");
        assertThat(activeSearchV3Generation(fixture.documentId())).isEqualTo(second.generationId());
        assertThat(passageCount(passageFailed.generationId())).isZero();

        Runtime childFailing = runtime(storage, failingEmbedding(1));
        Job childFailed = insertPendingGeneration(fixture);
        assertThat(childFailing.coordinator().processNext()).isTrue();

        assertThat(status("search_v3_index_generations", childFailed.generationId())).isEqualTo("FAILED");
        assertThat(status("search_v3_indexing_jobs", childFailed.jobId())).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject(
                "SELECT failure_stage FROM search_v3_indexing_jobs WHERE id = ?",
                String.class,
                childFailed.jobId())).isEqualTo("CHILD_EMBEDDING");
        assertThat(activeSearchV3Generation(fixture.documentId())).isEqualTo(second.generationId());
        assertThat(passageCount(childFailed.generationId())).isZero();

        jdbc.execute("""
                CREATE FUNCTION fail_prz040_worker_child_insert() RETURNS trigger
                LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced worker storage failure'; END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_prz040_worker_child_insert_trigger
                BEFORE INSERT ON search_v3_evidence_children
                FOR EACH ROW EXECUTE FUNCTION fail_prz040_worker_child_insert()
                """);
        Job storageFailed = insertPendingGeneration(fixture);
        try {
            assertThat(good.coordinator().processNext()).isTrue();
        }
        finally {
            jdbc.execute("DROP TRIGGER IF EXISTS fail_prz040_worker_child_insert_trigger "
                    + "ON search_v3_evidence_children");
            jdbc.execute("DROP FUNCTION IF EXISTS fail_prz040_worker_child_insert()");
        }
        assertFailedAt(storageFailed, "STORAGE");
        assertThat(activeSearchV3Generation(fixture.documentId())).isEqualTo(second.generationId());
        assertThat(passageCount(storageFailed.generationId())).isZero();
        assertThat(childCount(storageFailed.generationId())).isZero();

        Job cleanRetry = insertPendingGeneration(fixture);
        assertThat(good.coordinator().processNext()).isTrue();
        assertThat(status("search_v3_index_generations", second.generationId())).isEqualTo("SUPERSEDED");
        assertThat(status("search_v3_index_generations", cleanRetry.generationId())).isEqualTo("ACTIVE");
        assertThat(activeSearchV3Generation(fixture.documentId())).isEqualTo(cleanRetry.generationId());
        assertExactInventory(cleanRetry.generationId());
    }

    @Test
    void fileAndStructureFailuresDoNotReplaceExistingActiveGeneration() {
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());
        Runtime runtime = runtime(storage, deterministicEmbedding());
        Fixture fixture = createCurrentFixture(
                storage,
                DocumentFileType.TXT,
                "stable.txt",
                "안정화 기록\n서비스 운영 절차를 정리했다.".getBytes(StandardCharsets.UTF_8));
        Job active = insertPendingGeneration(fixture);
        assertThat(runtime.coordinator().processNext()).isTrue();

        Fixture missing = createAdditionalVersion(
                storage,
                fixture,
                2,
                DocumentFileType.TXT,
                "missing.txt",
                "삭제 예정 원문입니다.".getBytes(StandardCharsets.UTF_8));
        jdbc.update(
                "UPDATE document_versions SET stored_file_path = 'documents/missing/not-found.txt' WHERE id = ?",
                missing.documentVersionId());
        Job missingJob = insertPendingGeneration(missing);
        assertThat(runtime.coordinator().processNext()).isTrue();
        assertFailedAt(missingJob, "STORAGE");

        Fixture empty = createAdditionalVersion(
                storage,
                fixture,
                3,
                DocumentFileType.TXT,
                "empty.txt",
                "   \n\n".getBytes(StandardCharsets.UTF_8));
        Job emptyJob = insertPendingGeneration(empty);
        assertThat(runtime.coordinator().processNext()).isTrue();
        assertFailedAt(emptyJob, "PASSAGE_GENERATION");

        assertThat(activeSearchV3Generation(fixture.documentId())).isEqualTo(active.generationId());
        assertThat(status("search_v3_index_generations", active.generationId())).isEqualTo("ACTIVE");
        assertThat(documentVersionStatus(missing.documentVersionId())).isEqualTo("PROCESSING");
        assertThat(documentVersionStatus(empty.documentVersionId())).isEqualTo("PROCESSING");
    }

    @Test
    void retryableEmbeddingFailureRebuildsSameGenerationWithoutMixedArtifacts() {
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());
        Runtime good = runtime(storage, deterministicEmbedding());
        Fixture fixture = createCurrentFixture(
                storage,
                DocumentFileType.TXT,
                "retry.txt",
                "운영 자동화\n반복 점검을 자동화해 장애 대응 시간을 줄였다."
                        .getBytes(StandardCharsets.UTF_8));
        Job previous = insertPendingGeneration(fixture);
        assertThat(good.coordinator().processNext()).isTrue();

        Job retrying = insertPendingGeneration(fixture);
        Runtime transientFailure = runtime(storage, transientFailingEmbedding());
        assertThat(transientFailure.coordinator().processNext()).isTrue();

        assertThat(status("search_v3_index_generations", retrying.generationId())).isEqualTo("BUILDING");
        assertThat(status("search_v3_indexing_jobs", retrying.jobId())).isEqualTo("RETRY_WAIT");
        assertThat(activeSearchV3Generation(fixture.documentId())).isEqualTo(previous.generationId());
        assertThat(passageCount(retrying.generationId())).isZero();
        assertThat(childCount(retrying.generationId())).isZero();

        jdbc.update(
                "UPDATE search_v3_indexing_jobs SET next_retry_at = now() - interval '1 second' WHERE id = ?",
                retrying.jobId());
        assertThat(good.coordinator().processNext()).isTrue();

        assertThat(status("search_v3_index_generations", previous.generationId())).isEqualTo("SUPERSEDED");
        assertThat(status("search_v3_index_generations", retrying.generationId())).isEqualTo("ACTIVE");
        assertThat(status("search_v3_indexing_jobs", retrying.jobId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                "SELECT attempt_count FROM search_v3_indexing_jobs WHERE id = ?",
                Integer.class,
                retrying.jobId())).isEqualTo(2);
        assertThat(activeSearchV3Generation(fixture.documentId())).isEqualTo(retrying.generationId());
        assertExactInventory(retrying.generationId());
    }

    @Test
    void modelDigestChangeBeforeStorageFailsClosedAndPreservesActiveGeneration() {
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());
        Runtime good = runtime(storage, deterministicEmbedding());
        Fixture fixture = createCurrentFixture(
                storage,
                DocumentFileType.TXT,
                "model-drift.txt",
                "모델 계약\n동일 모델로 Passage와 Child 벡터를 생성했다."
                        .getBytes(StandardCharsets.UTF_8));
        Job previous = insertPendingGeneration(fixture);
        assertThat(good.coordinator().processNext()).isTrue();

        AtomicInteger resolutions = new AtomicInteger();
        SearchV3EmbeddingModelContractProvider changingModel = () -> new SearchV3EmbeddingModelContract(
                "bge-m3",
                resolutions.getAndIncrement() == 0 ? MODEL_DIGEST : "c".repeat(64),
                1024);
        Runtime drifting = runtime(storage, deterministicEmbedding(), changingModel);
        Job failed = insertPendingGeneration(fixture);
        assertThat(drifting.coordinator().processNext()).isTrue();

        assertFailedAt(failed, "CHILD_EMBEDDING");
        assertThat(activeSearchV3Generation(fixture.documentId())).isEqualTo(previous.generationId());
        assertThat(passageCount(failed.generationId())).isZero();
        assertThat(childCount(failed.generationId())).isZero();
    }

    @Test
    void reclaimedWorkerCannotStoreAndFailedReplacementRollsBackBeforeCleanRetry() {
        LocalFileStorage storage = new LocalFileStorage(storageRoot.toString());
        Runtime runtime = runtime(storage, deterministicEmbedding());
        Fixture fixture = createCurrentFixture(
                storage,
                DocumentFileType.TXT,
                "fencing.txt",
                """
                운영 품질
                점검 자동화를 도입해 반복 장애를 줄였다.
                지원 요청의 우선순위를 정리했다.
                """.getBytes(StandardCharsets.UTF_8));
        Job job = insertPendingGeneration(fixture);
        SearchV3IndexingJobClaim oldClaim = runtime.jobService().claimNext().orElseThrow();
        SearchV3PreparedInventory prepared = prepareInventory(runtime, storage, oldClaim);

        jdbc.update(
                "UPDATE search_v3_indexing_jobs SET lease_expires_at = now() - interval '1 second' WHERE id = ?",
                oldClaim.jobId());
        SearchV3RecoveryLock recovery = runtime.jobService().acquireNextRecoveryLock().orElseThrow();
        SearchV3IndexingJobClaim currentClaim = runtime.jobService().reclaim(recovery);

        assertThatThrownBy(() -> runtime.storageService().replaceAll(oldClaim, prepared))
                .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);
        runtime.storageService().replaceAll(currentClaim, prepared);
        long passageCountBefore = passageCount(job.generationId());
        long childCountBefore = childCount(job.generationId());

        jdbc.execute("""
                CREATE FUNCTION fail_prz040_child_insert() RETURNS trigger
                LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced child insert failure'; END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_prz040_child_insert_trigger
                BEFORE INSERT ON search_v3_evidence_children
                FOR EACH ROW EXECUTE FUNCTION fail_prz040_child_insert()
                """);
        assertThatThrownBy(() -> runtime.storageService().replaceAll(currentClaim, prepared))
                .isInstanceOf(RuntimeException.class);
        jdbc.execute("DROP TRIGGER fail_prz040_child_insert_trigger ON search_v3_evidence_children");
        jdbc.execute("DROP FUNCTION fail_prz040_child_insert()");

        assertThat(passageCount(job.generationId())).isEqualTo(passageCountBefore);
        assertThat(childCount(job.generationId())).isEqualTo(childCountBefore);
        runtime.storageService().replaceAll(currentClaim, prepared);
        assertThat(passageCount(job.generationId())).isEqualTo(passageCountBefore);
        assertThat(childCount(job.generationId())).isEqualTo(childCountBefore);
        runtime.activationService().markReady(currentClaim);
        assertExactInventory(job.generationId());
    }

    private Runtime runtime(LocalFileStorage storage, EmbeddingService embeddingService) {
        return runtime(
                storage,
                embeddingService,
                () -> new SearchV3EmbeddingModelContract("bge-m3", MODEL_DIGEST, 1024));
    }

    private Runtime runtime(
            LocalFileStorage storage,
            EmbeddingService embeddingService,
            SearchV3EmbeddingModelContractProvider modelProvider) {
        PlatformTransactionManager transactions = new DataSourceTransactionManager(dataSource);
        IngestionProperties ingestion = new IngestionProperties();
        ingestion.setLeaseDuration(Duration.ofMinutes(10));
        ingestion.setLeaseRefreshChunkInterval(2);
        ingestion.validate();

        SearchV3IndexingJobRepository jobRepository = new SearchV3IndexingJobRepository(jdbc);
        SearchV3IndexingJobService jobService = transactional(
                SearchV3IndexingJobService.class,
                new SearchV3IndexingJobService(jobRepository, ingestion, new IndexingRetryPolicy()),
                transactions);
        SearchV3GenerationContractRepository generationRepository =
                new SearchV3GenerationContractRepository(jdbc);
        SearchV3GenerationContractService generationService = transactional(
                SearchV3GenerationContractService.class,
                new SearchV3GenerationContractService(generationRepository),
                transactions);
        SearchV3InventoryVerifier verifier = new SearchV3InventoryVerifier();
        SearchV3InventoryActivationRepository inventoryRepository =
                new SearchV3InventoryActivationRepository(jdbc);
        SearchV3InventoryActivationService activationService = transactional(
                SearchV3InventoryActivationService.class,
                new SearchV3InventoryActivationService(inventoryRepository, verifier),
                transactions);
        SearchV3ArtifactStorageService storageService = transactional(
                SearchV3ArtifactStorageService.class,
                new SearchV3ArtifactStorageService(
                        generationService,
                        new SearchV3ArtifactStorageRepository(jdbc, new ObjectMapper()),
                        inventoryRepository,
                        verifier),
                transactions);
        SearchV3LogicalInventoryPlanner planner = new SearchV3LogicalInventoryPlanner(verifier);
        PdfExtractionProperties pdf = new PdfExtractionProperties();
        pdf.validate();
        EmbeddingValidator validator = new EmbeddingValidator(1024);
        SearchV3WorkerLeaseHeartbeat heartbeat = new SearchV3WorkerLeaseHeartbeat(jobService, ingestion);
        SearchV3ShadowIndexingProcessor processor = new SearchV3ShadowIndexingProcessor(
                new SearchV3DocumentSourceRepository(jdbc),
                storage,
                new DocumentTextExtractor(pdf),
                planner,
                generationService,
                modelProvider,
                embeddingService,
                validator,
                storageService,
                activationService,
                jobService,
                heartbeat,
                new SearchV3IndexingFailureClassifier(),
                ingestion);
        return new Runtime(
                new SearchV3IndexingCoordinator(jobService, processor),
                jobService,
                generationService,
                activationService,
                storageService,
                planner,
                validator,
                heartbeat);
    }

    private SearchV3PreparedInventory prepareInventory(
            Runtime runtime,
            LocalFileStorage storage,
            SearchV3IndexingJobClaim claim) {
        var source = new SearchV3DocumentSourceRepository(jdbc).find(claim).orElseThrow();
        List<com.prizm.ingestion.service.PageText> pages = new DocumentTextExtractor(pdfProperties())
                .extract(source.fileType(), storage.read(source.storedFilePath()));
        SearchV3Structure structure = new SearchV3StructureBuilder().build(ExtractedDocumentSource.from(
                claim.documentId(),
                claim.documentVersionId(),
                source.storedFilePath(),
                source.fileType(),
                pages));
        SearchV3LogicalInventoryPlan plan = runtime.planner().plan(structure);
        runtime.generationService().freezeExpectedManifest(
                claim,
                plan.passages().size(),
                plan.children().size(),
                plan.logicalManifestSha256());
        List<SearchV3PreparedInventory.EmbeddedPassage> passages = plan.passages().stream()
                .map(row -> new SearchV3PreparedInventory.EmbeddedPassage(
                        row, deterministicVector(row.retrievalText())))
                .toList();
        List<SearchV3PreparedInventory.EmbeddedChild> children = plan.children().stream()
                .map(row -> new SearchV3PreparedInventory.EmbeddedChild(
                        row, deterministicVector(row.sourceText())))
                .toList();
        return new SearchV3PreparedInventory(passages, children, plan.logicalManifestSha256());
    }

    private Fixture createCurrentFixture(
            LocalFileStorage storage,
            DocumentFileType fileType,
            String fileName,
            byte[] content) {
        long owner = insertOwner();
        long document = jdbc.queryForObject(
                "INSERT INTO documents(owner_user_id, title, document_type) VALUES (?, ?, 'OTHER') RETURNING id",
                Long.class,
                owner,
                "PRZ-040 " + fileName);
        Fixture fixture = insertVersion(storage, owner, document, 1, fileType, fileName, content);
        jdbc.update("UPDATE documents SET active_version_id = ? WHERE id = ?", fixture.documentVersionId(), document);
        return fixture;
    }

    private Fixture createAdditionalVersion(
            LocalFileStorage storage,
            Fixture existing,
            int versionNo,
            DocumentFileType fileType,
            String fileName,
            byte[] content) {
        Fixture fixture = insertVersion(
                storage,
                existing.ownerUserId(),
                existing.documentId(),
                versionNo,
                fileType,
                fileName,
                content);
        jdbc.update(
                "UPDATE document_versions SET status = 'PROCESSING' WHERE id = ?",
                fixture.documentVersionId());
        return fixture;
    }

    private Fixture insertVersion(
            LocalFileStorage storage,
            long owner,
            long document,
            int versionNo,
            DocumentFileType fileType,
            String fileName,
            byte[] content) {
        long version = jdbc.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path,
                    file_type, content_hash, status
                ) VALUES (?, ?, ?, ?, 'pending', ?, ?, 'ACTIVE') RETURNING id
                """,
                Long.class,
                owner,
                document,
                versionNo,
                fileName,
                fileType.name(),
                sha256(content));
        String storedPath = storage.store(document, version, fileName, content);
        jdbc.update("UPDATE document_versions SET stored_file_path = ? WHERE id = ?", storedPath, version);
        return new Fixture(owner, document, version);
    }

    private Job insertPendingGeneration(Fixture fixture) {
        long generation = jdbc.queryForObject(
                """
                INSERT INTO search_v3_index_generations(
                    owner_user_id, document_id, document_version_id, status,
                    structure_policy_version, passage_policy_version, child_policy_version,
                    embedding_model_id, resolved_model_digest, embedding_dimension,
                    passage_input_policy_version, child_input_policy_version
                ) VALUES (?, ?, ?, 'BUILDING', ?, ?, ?, 'bge-m3', ?, 1024, ?, ?) RETURNING id
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
        long job = jdbc.queryForObject(
                """
                INSERT INTO search_v3_indexing_jobs(
                    generation_id, owner_user_id, document_id, document_version_id, status
                ) VALUES (?, ?, ?, ?, 'PENDING') RETURNING id
                """,
                Long.class,
                generation,
                fixture.ownerUserId(),
                fixture.documentId(),
                fixture.documentVersionId());
        return new Job(generation, job);
    }

    private long insertOwner() {
        return jdbc.queryForObject(
                """
                INSERT INTO users(email, password_hash, role, enabled)
                VALUES (?, 'not-used', 'USER', TRUE) RETURNING id
                """,
                Long.class,
                "prz040-" + UUID.randomUUID() + "@example.com");
    }

    private void assertExactInventory(long generationId) {
        int expectedPassages = jdbc.queryForObject(
                "SELECT expected_passage_count FROM search_v3_index_generations WHERE id = ?",
                Integer.class,
                generationId);
        int expectedChildren = jdbc.queryForObject(
                "SELECT expected_child_count FROM search_v3_index_generations WHERE id = ?",
                Integer.class,
                generationId);
        assertThat(expectedPassages).isPositive();
        assertThat(expectedChildren).isPositive();
        assertThat(passageCount(generationId)).isEqualTo(expectedPassages);
        assertThat(childCount(generationId)).isEqualTo(expectedChildren);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM search_v3_passage_embeddings WHERE generation_id = ?",
                Long.class,
                generationId)).isEqualTo(expectedPassages);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM search_v3_child_embeddings WHERE generation_id = ?",
                Long.class,
                generationId)).isEqualTo(expectedChildren);
        String expectedManifest = jdbc.queryForObject(
                "SELECT expected_manifest_sha256 FROM search_v3_index_generations WHERE id = ?",
                String.class,
                generationId);
        String verifiedInventory = jdbc.queryForObject(
                "SELECT verified_inventory_sha256 FROM search_v3_index_generations WHERE id = ?",
                String.class,
                generationId);
        assertThat(expectedManifest).matches("[0-9a-f]{64}");
        assertThat(verifiedInventory).matches("[0-9a-f]{64}");
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*) FROM search_v3_passage_embeddings
                WHERE generation_id = ? AND embedding_model_id = 'bge-m3'
                  AND resolved_model_digest = ? AND embedding_dimension = 1024
                  AND input_policy_version = ?
                """,
                Long.class,
                generationId,
                MODEL_DIGEST,
                SearchV3IndexingPolicies.PASSAGE_INPUT)).isEqualTo(expectedPassages);
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*) FROM search_v3_child_embeddings
                WHERE generation_id = ? AND embedding_model_id = 'bge-m3'
                  AND resolved_model_digest = ? AND embedding_dimension = 1024
                  AND input_policy_version = ?
                """,
                Long.class,
                generationId,
                MODEL_DIGEST,
                SearchV3IndexingPolicies.CHILD_INPUT)).isEqualTo(expectedChildren);
    }

    private void assertFailedAt(Job job, String failureStage) {
        assertThat(status("search_v3_index_generations", job.generationId())).isEqualTo("FAILED");
        assertThat(status("search_v3_indexing_jobs", job.jobId())).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject(
                "SELECT failure_stage FROM search_v3_indexing_jobs WHERE id = ?",
                String.class,
                job.jobId())).isEqualTo(failureStage);
    }

    private long passageCount(long generationId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM search_v3_retrieval_passages WHERE generation_id = ?",
                Long.class,
                generationId);
    }

    private long childCount(long generationId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM search_v3_evidence_children WHERE generation_id = ?",
                Long.class,
                generationId);
    }

    private String status(String table, long id) {
        return jdbc.queryForObject("SELECT status FROM " + table + " WHERE id = ?", String.class, id);
    }

    private String documentVersionStatus(long documentVersionId) {
        return jdbc.queryForObject(
                "SELECT status FROM document_versions WHERE id = ?",
                String.class,
                documentVersionId);
    }

    private long activeVersion(long documentId) {
        return jdbc.queryForObject(
                "SELECT active_version_id FROM documents WHERE id = ?",
                Long.class,
                documentId);
    }

    private Long activeSearchV3Generation(long documentId) {
        return jdbc.queryForObject(
                "SELECT active_search_v3_generation_id FROM documents WHERE id = ?",
                Long.class,
                documentId);
    }

    private EmbeddingService deterministicEmbedding() {
        return SearchV3ShadowIndexingWorkerRuntimeTest::deterministicVector;
    }

    private EmbeddingService failingEmbedding(int successfulCallsBeforeFailure) {
        AtomicInteger calls = new AtomicInteger();
        return text -> {
            if (calls.getAndIncrement() >= successfulCallsBeforeFailure) {
                throw new EmbeddingException(
                        EmbeddingErrorCode.EMBEDDING_DIMENSION_MISMATCH,
                        "Forced PRZ-040 embedding failure.");
            }
            return deterministicVector(text);
        };
    }

    private EmbeddingService transientFailingEmbedding() {
        AtomicInteger calls = new AtomicInteger();
        return text -> {
            if (calls.getAndIncrement() == 0) {
                throw new EmbeddingException(
                        EmbeddingErrorCode.OLLAMA_UNAVAILABLE,
                        "Forced transient PRZ-040 embedding failure.");
            }
            return deterministicVector(text);
        };
    }

    private static float[] deterministicVector(String text) {
        float[] vector = new float[1024];
        int index = Math.floorMod(text.hashCode(), vector.length);
        vector[index] = 1.0f;
        return vector;
    }

    private byte[] textPdf(List<List<String>> pages) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (List<String> lines : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    stream.newLineAtOffset(72, 720);
                    for (int index = 0; index < lines.size(); index++) {
                        if (index > 0) {
                            stream.newLineAtOffset(0, -20);
                        }
                        stream.showText(lines.get(index));
                    }
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private PdfExtractionProperties pdfProperties() {
        PdfExtractionProperties properties = new PdfExtractionProperties();
        properties.validate();
        return properties;
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static <T> T transactional(Class<T> type, T target, PlatformTransactionManager transactions) {
        RuleBasedTransactionAttribute attribute = new RuleBasedTransactionAttribute();
        NameMatchTransactionAttributeSource source = new NameMatchTransactionAttributeSource();
        source.addTransactionalMethod("*", attribute);
        TransactionInterceptor interceptor = new TransactionInterceptor(transactions, source);
        ProxyFactory proxyFactory = new ProxyFactory(target);
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice(interceptor);
        return type.cast(proxyFactory.getProxy());
    }

    private record Fixture(long ownerUserId, long documentId, long documentVersionId) {
    }

    private record Job(long generationId, long jobId) {
    }

    private record Runtime(
            SearchV3IndexingCoordinator coordinator,
            SearchV3IndexingJobService jobService,
            SearchV3GenerationContractService generationService,
            SearchV3InventoryActivationService activationService,
            SearchV3ArtifactStorageService storageService,
            SearchV3LogicalInventoryPlanner planner,
            EmbeddingValidator validator,
            SearchV3WorkerLeaseHeartbeat heartbeat) {
    }
}
