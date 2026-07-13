package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.document.dto.response.DocumentDetailResponse;
import com.prizm.document.dto.response.DocumentUploadResponse;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.document.service.DocumentQueryService;
import com.prizm.document.service.DocumentUploadService;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.exception.StaleProcessingJobClaimException;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import com.prizm.ingestion.service.ClaimedProcessingJob;
import com.prizm.ingestion.service.DocumentIndexingProcessor;
import com.prizm.ingestion.service.IndexedChunk;
import com.prizm.ingestion.service.IndexingCompletionService;
import com.prizm.ingestion.service.ProcessingJobRecoveryService;
import com.prizm.ingestion.service.ProcessingJobClaimService;
import com.prizm.search.dto.response.SearchResponse;
import com.prizm.search.exception.SearchResultNotFoundException;
import com.prizm.search.service.SearchService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Docker PostgreSQL, Flyway, 실제 Ollama를 연결한 통합 검증이다.
 * Docker나 Ollama가 없으면 테스트를 건너뛰지 않고 컨텍스트 초기화 실패로 드러나게 한다.
 */
@ActiveProfiles("integration-test")
@SpringBootTest
@Testcontainers
class PgVectorInfrastructureTest {

    private static final Path STORAGE_ROOT = createStorageRoot();

    private static final List<String> SEARCH_TEST_SENTENCES = List.of(
            "연차 신청은 인사 시스템에서 진행합니다.",
            "서버 장애가 발생하면 운영 담당자에게 보고합니다.",
            "프로젝트 회고에는 장애 원인과 해결 과정이 기록되어 있습니다.");

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
        registry.add("prizm.storage.root", STORAGE_ROOT::toString);
    }

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    EmbeddingService embeddingService;

    @Autowired
    SearchService searchService;

    @Autowired
    DocumentUploadService documentUploadService;

    @Autowired
    DocumentQueryService documentQueryService;

    @Autowired
    DocumentVersionRepository documentVersionRepository;

    @Autowired
    DocumentRepository documentRepository;

    @Autowired
    ProcessingJobRepository processingJobRepository;

    @Autowired
    ProcessingJobClaimService processingJobClaimService;

    @Autowired
    DocumentIndexingProcessor documentIndexingProcessor;

    @Autowired
    IndexingCompletionService indexingCompletionService;

    @Autowired
    ProcessingJobRecoveryService processingJobRecoveryService;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Autowired
    FileStorage fileStorage;

    @Test
    @Transactional
    // 새 DB에 migration만 적용했을 때 문서 데이터가 비어 있고 pgvector가 동작하는지 확인한다.
    void appliesFlywayAndStores1024DimensionVectorsForExactCosineSearch() {
        Integer serverVersion = jdbcTemplate.queryForObject(
                "SELECT current_setting('server_version_num')::integer", Integer.class);

        Long successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Long.class);
        Long documentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM documents", Long.class);
        Long versionCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_versions", Long.class);
        Long chunkCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_chunks", Long.class);
        Long jobCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM processing_jobs", Long.class);
        Long userCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        Long uniqueJobConstraints = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'uq_processing_jobs_version_type'",
                Long.class);
        PgVectorSmokeAssertions.SmokeResult result =
                PgVectorSmokeAssertions.verifyExactCosineSearch(jdbcTemplate);

        assertThat(serverVersion).isBetween(160000, 169999);
        assertThat(successfulMigrations).isEqualTo(8);
        assertThat(result.extensionVersion()).isEqualTo("0.8.2");
        assertThat(documentCount).isZero();
        assertThat(versionCount).isZero();
        assertThat(chunkCount).isZero();
        assertThat(jobCount).isZero();
        assertThat(userCount).isZero();
        assertThat(uniqueJobConstraints).isEqualTo(1);
    }

    @Test
    @Transactional
    // 검색 경로가 실제 임베딩과 저장된 active 청크를 사용해 의미상 가까운 문장을 찾는지 확인한다.
    void storesBgeM3EmbeddingsAndFindsAnnualLeaveSentenceFirst() {
        Long ownerUserId = createUser();
        long documentVersionId = createActiveVectorDocumentVersion(ownerUserId);
        for (int index = 0; index < SEARCH_TEST_SENTENCES.size(); index++) {
            String sentence = SEARCH_TEST_SENTENCES.get(index);
            float[] embedding = embeddingService.embed(sentence);
            jdbcTemplate.update(
                    """
                    INSERT INTO document_chunks(
                        owner_user_id, content, embedding, document_version_id, chunk_no, page_no
                    )
                    VALUES (?, ?, CAST(? AS vector), ?, ?, ?)
                    """,
                    ownerUserId,
                    sentence,
                    toVectorLiteral(embedding),
                    documentVersionId,
                    index + 1,
                    1);
        }

        SearchResponse result = searchService.search(ownerUserId, "휴가는 어디에서 신청하나요?");

        assertThat(result.content()).isEqualTo(SEARCH_TEST_SENTENCES.get(0));
        assertThat(result.documentTitle()).isEqualTo("Vector search verification");
        assertThat(result.documentVersionId()).isEqualTo(documentVersionId);
        assertThat(result.chunkNo()).isEqualTo(1);
        assertThat(result.distance()).isBetween(0.0d, 2.0d);
        assertThat(result.score()).isEqualTo(1.0d - result.distance());
    }

    @Test
    @Transactional
    // 업로드 직후 파일·메타데이터·처리 작업을 만들되 버전은 QUARANTINED로 남아야 한다.
    void uploadsTxtAsQuarantinedDocumentAndStoresFile() throws IOException {
        Long ownerUserId = createUser();
        byte[] content = "연차 신청은 인사 시스템에서 진행합니다.".getBytes(StandardCharsets.UTF_8);
        DocumentUploadResponse response = documentUploadService.upload(
                ownerUserId,
                "휴가 안내",
                new MockMultipartFile("file", "leave-guide.txt", "text/plain", content));

        DocumentDetailResponse detail = documentQueryService.get(ownerUserId, response.documentId());

        assertThat(response.status()).isEqualTo(DocumentVersionStatus.QUARANTINED);
        assertThat(detail.activeVersionId()).isNull();
        assertThat(detail.versions()).hasSize(1);
        assertThat(detail.versions().get(0).status()).isEqualTo(DocumentVersionStatus.QUARANTINED);
        assertThat(documentVersionRepository.findById(response.versionId()).orElseThrow().getContentHash())
                .hasSize(64)
                .matches("[0-9a-f]{64}");
        Path storedFile = STORAGE_ROOT.resolve("documents")
                .resolve(response.documentId().toString())
                .resolve(response.versionId().toString())
                .resolve("leave-guide.txt");
        assertThat(storedFile).exists().hasContent(new String(content, StandardCharsets.UTF_8));
    }

    @Test
    // 실제 원본 파일이 자동 색인된 뒤에만 출처 정보와 함께 검색되는 전체 세로 흐름을 확인한다.
    void uploadsIndexesAndSearchesActiveTxtDocumentAutomatically() {
        Long ownerUserId = createUser();
        byte[] content = ("연차 신청은 인사 시스템에서 진행합니다. "
                        + "휴가 신청 절차는 사내 인사 시스템의 휴가 메뉴를 사용합니다.")
                .getBytes(StandardCharsets.UTF_8);
        DocumentUploadResponse uploaded = documentUploadService.upload(
                ownerUserId,
                "휴가 신청 안내",
                new MockMultipartFile("file", "leave-search.txt", "text/plain", content));
        String storedFilePath = documentVersionRepository.findById(uploaded.versionId())
                .orElseThrow()
                .getStoredFilePath();

        try {
            assertThat(uploaded.status()).isEqualTo(DocumentVersionStatus.QUARANTINED);
            assertThatThrownBy(() -> searchService.search(ownerUserId, "휴가는 어디에서 신청하나요?"))
                    .isInstanceOf(SearchResultNotFoundException.class);

            assertThat(processingJobRepository.count()).isEqualTo(1);
            assertThat(processingJobRepository.findAll().get(0).getStatus())
                    .isEqualTo(ProcessingJobStatus.PENDING);
            assertThat(processingJobRepository.findAll().get(0).getDocumentVersionId())
                    .isEqualTo(uploaded.versionId());
            assertThat(documentRepository.findById(uploaded.documentId()).orElseThrow().getOwnerUserId())
                    .isEqualTo(ownerUserId);
            assertThat(documentVersionRepository.findById(uploaded.versionId()).orElseThrow().getOwnerUserId())
                    .isEqualTo(ownerUserId);
            assertThat(processingJobRepository.findAll().get(0).getOwnerUserId()).isEqualTo(ownerUserId);

            ClaimedProcessingJob claimed = processingJobClaimService.claimNext().orElseThrow();
            assertThat(processingJobClaimService.claimNext()).isEmpty();
            assertThat(processingJobRepository.findById(claimed.processingJobId()).orElseThrow().getStatus())
                    .isEqualTo(ProcessingJobStatus.PROCESSING);
            assertThat(documentVersionRepository.findById(uploaded.versionId()).orElseThrow().getStatus())
                    .isEqualTo(DocumentVersionStatus.PROCESSING);

            documentIndexingProcessor.process(claimed);

            assertThat(processingJobRepository.findById(claimed.processingJobId()).orElseThrow().getStatus())
                    .isEqualTo(ProcessingJobStatus.COMPLETED);
            assertThat(documentVersionRepository.findById(uploaded.versionId()).orElseThrow().getStatus())
                    .isEqualTo(DocumentVersionStatus.ACTIVE);
            assertThat(documentRepository.findById(uploaded.documentId()).orElseThrow().getActiveVersionId())
                    .isEqualTo(uploaded.versionId());
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM document_chunks WHERE document_version_id = ?",
                    Long.class,
                    uploaded.versionId())).isPositive();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT MIN(vector_dims(embedding)) FROM document_chunks WHERE document_version_id = ?",
                    Integer.class,
                    uploaded.versionId())).isEqualTo(1024);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM document_chunks WHERE document_version_id = ? AND page_no IS NOT NULL",
                    Long.class,
                    uploaded.versionId())).isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM document_chunks WHERE document_version_id = ? AND owner_user_id = ?",
                    Long.class,
                    uploaded.versionId(),
                    ownerUserId)).isPositive();

            SearchResponse result = searchService.search(ownerUserId, "휴가는 어디에서 신청하나요?");
            assertThat(result.documentId()).isEqualTo(uploaded.documentId());
            assertThat(result.documentVersionId()).isEqualTo(uploaded.versionId());
            assertThat(result.documentTitle()).isEqualTo("휴가 신청 안내");
            assertThat(result.versionNo()).isEqualTo(1);
            assertThat(result.chunkNo()).isEqualTo(1);
            assertThat(result.pageNo()).isNull();
            assertThat(result.content()).contains("인사 시스템");
            assertThat(result.score()).isEqualTo(1.0d - result.distance());
        }
        finally {
            fileStorage.delete(storedFilePath);
            deleteCommittedDocumentData();
        }
    }

    @Test
    @Transactional
    // 부분 청크가 있어도 ACTIVE 상태와 활성 버전 연결을 모두 만족하지 않으면 검색하지 않는다.
    void excludesPartialChunksFromIndexingDocumentVersion() {
        Long ownerUserId = createUser();
        String content = "색인 중인 문서는 검색 결과에 노출되면 안 됩니다.";
        float[] embedding = embeddingService.embed(content);
        Long documentId = jdbcTemplate.queryForObject(
                "INSERT INTO documents(owner_user_id, title) VALUES (?, '부분 색인 문서') RETURNING id",
                Long.class,
                ownerUserId);
        Long versionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path, file_type,
                    content_hash, status
                )
                VALUES (?, ?, 1, 'partial.txt', 'test/partial.txt', 'TXT', repeat('b', 64), 'PROCESSING')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                documentId);
        jdbcTemplate.update("UPDATE documents SET active_version_id = ? WHERE id = ?", versionId, documentId);
        jdbcTemplate.update(
                """
                INSERT INTO document_chunks(
                    owner_user_id, content, embedding, document_version_id, chunk_no, page_no
                )
                VALUES (?, ?, CAST(? AS vector), ?, 1, NULL)
                """,
                ownerUserId,
                content,
                toVectorLiteral(embedding),
                versionId);

        assertThatThrownBy(() -> searchService.search(ownerUserId, content))
                .isInstanceOf(SearchResultNotFoundException.class);
    }

    @Test
    void allowsOnlyOneIndependentTransactionToClaimTheSamePendingJob() throws Exception {
        PendingJobFixture fixture = createPendingIndexingJob("동시 선점 문서");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstClaimed = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        try {
            Future<Optional<ClaimedProcessingJob>> first = executor.submit(() -> transactionTemplate.execute(status -> {
                Optional<ClaimedProcessingJob> claimed = processingJobClaimService.claimNext();
                firstClaimed.countDown();
                await(releaseFirst);
                return claimed;
            }));
            assertThat(firstClaimed.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Optional<ClaimedProcessingJob>> second = executor.submit(
                    () -> transactionTemplate.execute(status -> processingJobClaimService.claimNext()));
            Optional<ClaimedProcessingJob> secondClaim = second.get(5, TimeUnit.SECONDS);
            releaseFirst.countDown();
            Optional<ClaimedProcessingJob> firstClaim = first.get(5, TimeUnit.SECONDS);

            List<ClaimedProcessingJob> claims = java.util.stream.Stream.of(firstClaim, secondClaim)
                    .flatMap(Optional::stream)
                    .toList();
            assertThat(claims).hasSize(1);
            assertThat(claims.get(0).processingJobId()).isEqualTo(fixture.processingJobId());
            assertThat(claims.get(0).claimVersion()).isEqualTo(1L);
            assertThat(claims.get(0).leaseExpiresAt()).isNotNull();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM processing_jobs WHERE id = ? AND status = 'PROCESSING'",
                    Long.class,
                    fixture.processingJobId())).isEqualTo(1L);

            indexingCompletionService.complete(claims.get(0), List.of(indexedChunk("동시 선점 결과")));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM document_chunks WHERE document_version_id = ?",
                    Long.class,
                    fixture.documentVersionId())).isEqualTo(1L);
        }
        finally {
            releaseFirst.countDown();
            executor.shutdownNow();
            deleteCommittedDocumentData();
        }
    }

    @Test
    void recoversExpiredLeaseAndRejectsCompletionFromTheOldWorker() {
        PendingJobFixture fixture = createPendingIndexingJob("중단 복구 문서");

        try {
            ClaimedProcessingJob workerA = processingJobClaimService.claimNext().orElseThrow();
            jdbcTemplate.update(
                    "UPDATE processing_jobs SET lease_expires_at = now() - INTERVAL '1 second' WHERE id = ?",
                    fixture.processingJobId());
            Instant beforeRecovery = databaseNow();

            assertThat(processingJobRecoveryService.recoverNext()).isTrue();

            Long recoveredClaimVersion = jdbcTemplate.queryForObject(
                    "SELECT claim_version FROM processing_jobs WHERE id = ?",
                    Long.class,
                    fixture.processingJobId());
            Integer retryCount = jdbcTemplate.queryForObject(
                    "SELECT retry_count FROM processing_jobs WHERE id = ?",
                    Integer.class,
                    fixture.processingJobId());
            String recoveredStatus = jdbcTemplate.queryForObject(
                    "SELECT status FROM processing_jobs WHERE id = ?",
                    String.class,
                    fixture.processingJobId());
            Instant nextRetryAt = jdbcTemplate.queryForObject(
                    "SELECT next_retry_at FROM processing_jobs WHERE id = ?",
                    (resultSet, rowNum) -> resultSet.getTimestamp(1).toInstant(),
                    fixture.processingJobId());
            assertThat(recoveredStatus).isEqualTo("RETRY_WAIT");
            assertThat(retryCount).isEqualTo(1);
            assertThat(recoveredClaimVersion).isEqualTo(workerA.claimVersion() + 1);
            assertThat(nextRetryAt).isBetween(beforeRecovery.plusSeconds(60), databaseNow().plusSeconds(60));
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT lease_expires_at IS NULL AND started_at IS NULL FROM processing_jobs WHERE id = ?",
                    Boolean.class,
                    fixture.processingJobId())).isTrue();
            assertThat(documentVersionRepository.findById(fixture.documentVersionId()).orElseThrow().getStatus())
                    .isEqualTo(DocumentVersionStatus.PROCESSING);

            jdbcTemplate.update(
                    "UPDATE processing_jobs SET next_retry_at = now() - INTERVAL '1 second' WHERE id = ?",
                    fixture.processingJobId());
            ClaimedProcessingJob workerB = processingJobClaimService.claimNext().orElseThrow();
            assertThat(workerB.claimVersion()).isGreaterThan(workerA.claimVersion());

            List<IndexedChunk> chunks = List.of(indexedChunk("복구된 Worker 결과"));
            assertThatThrownBy(() -> indexingCompletionService.complete(workerA, chunks))
                    .isInstanceOf(StaleProcessingJobClaimException.class);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM document_chunks WHERE document_version_id = ?",
                    Long.class,
                    fixture.documentVersionId())).isZero();

            indexingCompletionService.complete(workerB, chunks);

            assertThat(documentVersionRepository.findById(fixture.documentVersionId()).orElseThrow().getStatus())
                    .isEqualTo(DocumentVersionStatus.ACTIVE);
            assertThat(documentRepository.findById(fixture.documentId()).orElseThrow().getActiveVersionId())
                    .isEqualTo(fixture.documentVersionId());
            assertThat(processingJobRepository.findById(fixture.processingJobId()).orElseThrow().getStatus())
                    .isEqualTo(ProcessingJobStatus.COMPLETED);
            assertThat(processingJobRepository.findById(fixture.processingJobId()).orElseThrow().getLeaseExpiresAt())
                    .isNull();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM document_chunks WHERE document_version_id = ?",
                    Long.class,
                    fixture.documentVersionId())).isEqualTo(1L);
        }
        finally {
            deleteCommittedDocumentData();
        }
    }

    @Test
    void keepsExistingActiveVersionSearchableWhenReplacementVersionFails() {
        Long ownerUserId = createUser();
        String activeContent = "Implemented a retry policy with explicit backoff intervals.";
        Long documentId = jdbcTemplate.queryForObject(
                "INSERT INTO documents(owner_user_id, title) VALUES (?, ?) RETURNING id",
                Long.class,
                ownerUserId,
                "Career project report");
        Long activeVersionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path, file_type,
                    content_hash, status
                )
                VALUES (?, ?, 1, 'project-v1.txt', 'test/project-v1.txt', 'TXT', repeat('d', 64), 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                documentId);
        jdbcTemplate.update("UPDATE documents SET active_version_id = ? WHERE id = ?", activeVersionId, documentId);
        jdbcTemplate.update(
                """
                INSERT INTO document_chunks(
                    owner_user_id, content, embedding, document_version_id, chunk_no, page_no
                )
                VALUES (?, ?, CAST(? AS vector), ?, 1, NULL)
                """,
                ownerUserId,
                activeContent,
                toVectorLiteral(embeddingService.embed(activeContent)),
                activeVersionId);

        Long replacementVersionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path, file_type,
                    content_hash, status
                )
                VALUES (?, ?, 2, 'project-v2.txt', 'test/project-v2.txt', 'TXT', repeat('e', 64), 'QUARANTINED')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                documentId);
        Long jobId = jdbcTemplate.queryForObject(
                """
                INSERT INTO processing_jobs(owner_user_id, document_version_id, job_type, status)
                VALUES (?, ?, 'INDEXING', 'PENDING')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                replacementVersionId);

        try {
            ClaimedProcessingJob claim = processingJobClaimService.claimNext().orElseThrow();
            assertThat(claim.processingJobId()).isEqualTo(jobId);
            assertThat(documentVersionRepository.findById(replacementVersionId).orElseThrow().getStatus())
                    .isEqualTo(DocumentVersionStatus.PROCESSING);
            jdbcTemplate.update(
                    """
                    UPDATE processing_jobs
                    SET retry_count = 3,
                        lease_expires_at = now() - INTERVAL '1 second'
                    WHERE id = ?
                    """,
                    jobId);

            assertThat(processingJobRecoveryService.recoverNext()).isTrue();

            assertThat(documentVersionRepository.findById(replacementVersionId).orElseThrow().getStatus())
                    .isEqualTo(DocumentVersionStatus.FAILED);
            assertThat(documentRepository.findById(documentId).orElseThrow().getActiveVersionId())
                    .isEqualTo(activeVersionId);
            SearchResponse result = searchService.search(ownerUserId, activeContent);
            assertThat(result.documentVersionId()).isEqualTo(activeVersionId);
            assertThat(result.content()).isEqualTo(activeContent);
        }
        finally {
            deleteCommittedDocumentData();
        }
    }

    private void deleteCommittedDocumentData() {
        jdbcTemplate.update("DELETE FROM processing_jobs");
        jdbcTemplate.update("DELETE FROM document_chunks");
        jdbcTemplate.update("UPDATE documents SET active_version_id = NULL");
        jdbcTemplate.update("DELETE FROM document_versions");
        jdbcTemplate.update("DELETE FROM documents");
        jdbcTemplate.update("DELETE FROM users");
    }

    private long createActiveVectorDocumentVersion(Long ownerUserId) {
        Long documentId = jdbcTemplate.queryForObject(
                "INSERT INTO documents(owner_user_id, title) VALUES (?, ?) RETURNING id",
                Long.class,
                ownerUserId,
                "Vector search verification");
        Long versionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path, file_type,
                    content_hash, status
                )
                VALUES (?, ?, 1, 'vector-search.txt', 'test/vector-search.txt', 'TXT', repeat('a', 64), 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                documentId);
        jdbcTemplate.update("UPDATE documents SET active_version_id = ? WHERE id = ?", versionId, documentId);
        return versionId;
    }

    private PendingJobFixture createPendingIndexingJob(String title) {
        Long ownerUserId = createUser();
        Long documentId = jdbcTemplate.queryForObject(
                "INSERT INTO documents(owner_user_id, title) VALUES (?, ?) RETURNING id",
                Long.class,
                ownerUserId,
                title);
        Long versionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path, file_type,
                    content_hash, status
                )
                VALUES (?, ?, 1, 'worker.txt', 'test/worker.txt', 'TXT', repeat('c', 64), 'QUARANTINED')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                documentId);
        Long jobId = jdbcTemplate.queryForObject(
                """
                INSERT INTO processing_jobs(owner_user_id, document_version_id, job_type, status)
                VALUES (?, ?, 'INDEXING', 'PENDING')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                versionId);
        return new PendingJobFixture(ownerUserId, documentId, versionId, jobId);
    }

    private Long createUser() {
        return jdbcTemplate.queryForObject(
                """
                INSERT INTO users(email, password_hash, role, enabled)
                VALUES (?, 'not-used-by-infrastructure-test', 'USER', TRUE)
                RETURNING id
                """,
                Long.class,
                UUID.randomUUID() + "@example.com");
    }

    private IndexedChunk indexedChunk(String content) {
        float[] embedding = new float[1024];
        embedding[0] = 1.0f;
        return new IndexedChunk(1, content, embedding);
    }

    private Instant databaseNow() {
        return jdbcTemplate.queryForObject(
                "SELECT now()",
                (resultSet, rowNum) -> resultSet.getTimestamp(1).toInstant());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out while coordinating concurrent claims.");
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent claim test was interrupted.", exception);
        }
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder literal = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                literal.append(',');
            }
            literal.append(embedding[index]);
        }
        return literal.append(']').toString();
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("prizm-integration-storage-");
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record PendingJobFixture(
            Long ownerUserId,
            Long documentId,
            Long documentVersionId,
            Long processingJobId) {
    }
}
