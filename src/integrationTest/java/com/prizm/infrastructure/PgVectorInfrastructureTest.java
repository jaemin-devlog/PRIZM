package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.prizm.document.dto.response.DocumentDetailResponse;
import com.prizm.document.dto.response.DocumentUploadResponse;
import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.repository.DocumentRepository;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.document.service.DocumentQueryService;
import com.prizm.document.service.DocumentUploadService;
import com.prizm.cleanup.exception.StaleFileCleanupJobClaimException;
import com.prizm.cleanup.repository.FileCleanupJobRepository;
import com.prizm.cleanup.service.ClaimedFileCleanupJob;
import com.prizm.cleanup.service.CleanupFailure;
import com.prizm.cleanup.service.FileCleanupCoordinator;
import com.prizm.cleanup.service.FileCleanupCompletionService;
import com.prizm.cleanup.service.FileCleanupFailureClassifier;
import com.prizm.cleanup.service.FileCleanupFailureService;
import com.prizm.cleanup.service.FileCleanupJobClaimService;
import com.prizm.cleanup.service.FileCleanupJobRecoveryService;
import com.prizm.cleanup.service.FileCleanupJobService;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.ingestion.exception.DocumentIndexingException;
import com.prizm.ingestion.exception.StaleProcessingJobClaimException;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import com.prizm.ingestion.service.ClaimedProcessingJob;
import com.prizm.ingestion.service.DocumentIndexingProcessor;
import com.prizm.ingestion.service.IndexedChunk;
import com.prizm.ingestion.service.IndexingCompletionService;
import com.prizm.ingestion.service.IndexingFailureClassifier;
import com.prizm.ingestion.service.IndexingFailureService;
import com.prizm.ingestion.service.ProcessingJobRecoveryService;
import com.prizm.ingestion.service.ProcessingJobClaimService;
import com.prizm.ingestion.service.ProcessingJobLeaseService;
import com.prizm.search.dto.response.SearchResponse;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.exception.SearchResultNotFoundException;
import com.prizm.search.service.SearchService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SecureDirectoryStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.lifecycle.Startable;
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
    private static final String EXTERNAL_DATABASE_URL = System.getenv("PRIZM_INTEGRATION_TEST_DATABASE_URL");
    private static final String EXTERNAL_DATABASE_USERNAME = System.getenv("PRIZM_INTEGRATION_TEST_DATABASE_USERNAME");
    private static final String EXTERNAL_DATABASE_PASSWORD = System.getenv("PRIZM_INTEGRATION_TEST_DATABASE_PASSWORD");
    private static final boolean USE_EXTERNAL_DATABASE = EXTERNAL_DATABASE_URL != null
            && !EXTERNAL_DATABASE_URL.isBlank();

    private static final List<String> SEARCH_TEST_SENTENCES = List.of(
            "연차 신청은 인사 시스템에서 진행합니다.",
            "서버 장애가 발생하면 운영 담당자에게 보고합니다.",
            "프로젝트 회고에는 장애 원인과 해결 과정이 기록되어 있습니다.");

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");

    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm")
            .withUsername("prizm")
            .withPassword("prizm-test");

    @Container
    static final Startable database = USE_EXTERNAL_DATABASE ? new ExternalDatabaseLifecycle() : postgres;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        if (USE_EXTERNAL_DATABASE) {
            registry.add("spring.datasource.url", () -> EXTERNAL_DATABASE_URL);
            registry.add("spring.datasource.username", () -> EXTERNAL_DATABASE_USERNAME);
            registry.add("spring.datasource.password", () -> EXTERNAL_DATABASE_PASSWORD);
        }
        else {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
        }
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
    FileCleanupJobService fileCleanupJobService;

    @Autowired
    FileCleanupJobRepository fileCleanupJobRepository;

    @Autowired
    FileCleanupCoordinator fileCleanupCoordinator;

    @Autowired
    FileCleanupCompletionService fileCleanupCompletionService;

    @Autowired
    FileCleanupJobClaimService fileCleanupJobClaimService;

    @Autowired
    FileCleanupJobRecoveryService fileCleanupJobRecoveryService;

    @Autowired
    FileCleanupFailureClassifier fileCleanupFailureClassifier;

    @Autowired
    FileCleanupFailureService fileCleanupFailureService;

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
    ProcessingJobLeaseService processingJobLeaseService;

    @Autowired
    DocumentIndexingProcessor documentIndexingProcessor;

    @Autowired
    IndexingCompletionService indexingCompletionService;

    @Autowired
    ProcessingJobRecoveryService processingJobRecoveryService;

    @Autowired
    IndexingFailureService indexingFailureService;

    @Autowired
    IndexingFailureClassifier indexingFailureClassifier;

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
        Long fileCleanupJobCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM file_cleanup_jobs", Long.class);
        Long uniqueJobConstraints = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'uq_processing_jobs_version_type'",
                Long.class);
        PgVectorSmokeAssertions.SmokeResult result =
                PgVectorSmokeAssertions.verifyExactCosineSearch(jdbcTemplate);

        assertThat(serverVersion).isBetween(160000, 169999);
        assertThat(successfulMigrations).isEqualTo(13L);
        assertThat(result.extensionVersion()).isEqualTo("0.8.2");
        assertThat(documentCount).isZero();
        assertThat(versionCount).isZero();
        assertThat(chunkCount).isZero();
        assertThat(jobCount).isZero();
        assertThat(userCount).isZero();
        assertThat(fileCleanupJobCount).isZero();
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
                        owner_user_id, content, embedding, document_version_id, chunk_no, page_no,
                        source_type, source_index, source_label
                    )
                    VALUES (?, ?, CAST(? AS vector), ?, ?, ?, 'TEXT_CHUNK', ?, ?)
                    """,
                    ownerUserId,
                    sentence,
                    toVectorLiteral(embedding),
                    documentVersionId,
                    index + 1,
                    1,
                    index + 1,
                    "텍스트 구간 " + (index + 1));
        }

        SearchResponse result = searchService.search(ownerUserId, "휴가는 어디에서 신청하나요?");

        assertThat(result.content()).isEqualTo(SEARCH_TEST_SENTENCES.get(0));
        assertThat(result.documentTitle()).isEqualTo("Vector search verification");
        assertThat(result.documentVersionId()).isEqualTo(documentVersionId);
        assertThat(result.chunkNo()).isEqualTo(1);
        assertThat(result.sourceType()).isEqualTo(ChunkSourceType.TEXT_CHUNK);
        assertThat(result.sourceIndex()).isEqualTo(1);
        assertThat(result.sourceLabel()).isEqualTo("텍스트 구간 1");
        assertThat(result.distance()).isBetween(0.0d, 2.0d);
        assertThat(result.score()).isEqualTo(1.0d - result.distance());
    }

    @Test
    @Transactional
    void returnsAtMostFiveOrderedCareerEvidenceChunksOnlyForTheCurrentActiveOwnerVersion() {
        Long ownerA = createUser();
        Long ownerB = createUser();
        String query = "Spring Boot and Redis experience";
        ActiveVectorDocument ownerEvidence = createActiveVectorDocument(ownerA, "Owner A career evidence");
        List<String> ownerContents = List.of(
                query,
                "Built a Spring Boot API with Redis caching.",
                "Implemented Spring Boot background processing.",
                "Used Redis to coordinate cache invalidation.",
                "Created backend integration tests for a Spring service.",
                "Maintained Java services and REST APIs.");
        for (int index = 0; index < ownerContents.size(); index++) {
            insertVectorChunk(ownerA, ownerEvidence.versionId(), ownerContents.get(index), index + 1);
        }

        ActiveVectorDocument otherOwnerEvidence = createActiveVectorDocument(ownerB, "Owner B career evidence");
        insertVectorChunk(ownerB, otherOwnerEvidence.versionId(), query, 1);

        ActiveVectorDocument supersededEvidence = createActiveVectorDocument(ownerA, "Superseded evidence");
        insertVectorChunk(ownerA, supersededEvidence.versionId(), query, 1);
        Long replacementVersionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path, file_type,
                    content_hash, status
                )
                VALUES (?, ?, 2, 'replacement.txt', 'test/replacement.txt', 'TXT', repeat('d', 64), 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                ownerA,
                supersededEvidence.documentId());
        jdbcTemplate.update(
                "UPDATE documents SET active_version_id = ? WHERE id = ?",
                replacementVersionId,
                supersededEvidence.documentId());

        ActiveVectorDocument processingEvidence = createActiveVectorDocument(ownerA, "Processing evidence");
        jdbcTemplate.update(
                "UPDATE document_versions SET status = 'PROCESSING' WHERE id = ?",
                processingEvidence.versionId());
        insertVectorChunk(ownerA, processingEvidence.versionId(), query, 1);

        ActiveVectorDocument failedEvidence = createActiveVectorDocument(ownerA, "Failed evidence");
        jdbcTemplate.update(
                "UPDATE document_versions SET status = 'FAILED' WHERE id = ?",
                failedEvidence.versionId());
        insertVectorChunk(ownerA, failedEvidence.versionId(), query, 1);

        List<CareerEvidenceSearchResponse> results = searchService.searchCareerEvidence(ownerA, query);

        assertThat(results).hasSize(5);
        assertThat(results).extracting(CareerEvidenceSearchResponse::documentId)
                .containsOnly(ownerEvidence.documentId());
        assertThat(results).extracting(CareerEvidenceSearchResponse::distance).isSorted();
        assertThat(results.get(0).content()).isEqualTo(query);
        assertThat(results.get(0).chunkId()).isPositive();
        assertThat(results.get(0).documentVersionId()).isEqualTo(ownerEvidence.versionId());
        assertThat(results.get(0).sourceType()).isEqualTo(ChunkSourceType.TEXT_CHUNK);
        assertThat(results.get(0).sourceIndex()).isPositive();
        assertThat(results.get(0).sourceLabel()).isEqualTo("텍스트 구간 " + results.get(0).sourceIndex());
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
        assertThat(response.documentType()).isEqualTo(DocumentType.OTHER);
        assertThat(detail.activeVersionId()).isNull();
        assertThat(detail.documentType()).isEqualTo(DocumentType.OTHER);
        assertThat(documentQueryService.list(ownerUserId, null).get(0).documentType()).isEqualTo(DocumentType.OTHER);
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
                        + "휴가 신청 절차는 사내 인사 시스템의 휴가 메뉴를 사용합니다. ")
                .repeat(40)
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
            List<StoredChunkSource> storedSources = jdbcTemplate.query(
                    """
                    SELECT chunk_no, source_type, source_index, source_label
                    FROM document_chunks
                    WHERE document_version_id = ?
                    ORDER BY source_index
                    """,
                    (resultSet, rowNum) -> new StoredChunkSource(
                            resultSet.getInt("chunk_no"),
                            resultSet.getString("source_type"),
                            resultSet.getInt("source_index"),
                            resultSet.getString("source_label")),
                    uploaded.versionId());
            assertThat(storedSources).hasSizeGreaterThan(1);
            for (int index = 0; index < storedSources.size(); index++) {
                StoredChunkSource source = storedSources.get(index);
                assertThat(source.chunkNo()).isEqualTo(index + 1);
                assertThat(source.sourceType()).isEqualTo("TEXT_CHUNK");
                assertThat(source.sourceIndex()).isEqualTo(index + 1);
                assertThat(source.sourceLabel()).isEqualTo("텍스트 구간 " + (index + 1));
            }

            SearchResponse result = searchService.search(ownerUserId, "휴가는 어디에서 신청하나요?");
            assertThat(result.documentId()).isEqualTo(uploaded.documentId());
            assertThat(result.documentVersionId()).isEqualTo(uploaded.versionId());
            assertThat(result.documentTitle()).isEqualTo("휴가 신청 안내");
            assertThat(result.versionNo()).isEqualTo(1);
            assertThat(result.pageNo()).isNull();
            assertThat(result.sourceType()).isEqualTo(ChunkSourceType.TEXT_CHUNK);
            assertThat(result.sourceIndex()).isBetween(1, storedSources.size());
            assertThat(result.sourceLabel()).isEqualTo("텍스트 구간 " + result.sourceIndex());
            assertThat(result.content()).contains("인사 시스템");
            assertThat(result.score()).isEqualTo(1.0d - result.distance());
        }
        finally {
            try {
                deleteTestStoredFile(storedFilePath);
            }
            finally {
                deleteCommittedDocumentData();
            }
        }
    }

    @Test
    void uploadsIndexesAndSearchesPdfPagesWithPageSources() {
        Long ownerUserId = createUser();
        String indexedPageText = ("PDF page source indexing evidence for Spring Boot. ").repeat(50);
        DocumentUploadResponse uploaded = documentUploadService.upload(
                ownerUserId,
                "PDF evidence",
                new MockMultipartFile(
                        "file",
                        "evidence.pdf",
                        "application/pdf",
                        textPdf(List.of("First PDF page", "", indexedPageText))));
        String storedFilePath = documentVersionRepository.findById(uploaded.versionId())
                .orElseThrow()
                .getStoredFilePath();

        try {
            assertThat(uploaded.status()).isEqualTo(DocumentVersionStatus.QUARANTINED);
            assertThat(documentVersionRepository.findById(uploaded.versionId()).orElseThrow().getFileType())
                    .isEqualTo(DocumentFileType.PDF);

            ClaimedProcessingJob claimed = processingJobClaimService.claimNext().orElseThrow();
            documentIndexingProcessor.process(claimed);

            assertThat(documentVersionRepository.findById(uploaded.versionId()).orElseThrow().getStatus())
                    .isEqualTo(DocumentVersionStatus.ACTIVE);
            List<StoredChunkSource> storedSources = jdbcTemplate.query(
                    """
                    SELECT chunk_no, source_type, source_index, source_label
                    FROM document_chunks
                    WHERE document_version_id = ?
                    ORDER BY chunk_no
                    """,
                    (resultSet, rowNum) -> new StoredChunkSource(
                            resultSet.getInt("chunk_no"),
                            resultSet.getString("source_type"),
                            resultSet.getInt("source_index"),
                            resultSet.getString("source_label")),
                    uploaded.versionId());
            assertThat(storedSources).hasSizeGreaterThan(2);
            assertThat(storedSources).extracting(StoredChunkSource::chunkNo)
                    .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, storedSources.size())
                            .boxed()
                            .toList());
            assertThat(storedSources.get(0))
                    .isEqualTo(new StoredChunkSource(1, "PAGE", 1, "1페이지"));
            assertThat(storedSources.subList(1, storedSources.size())).allSatisfy(source -> {
                assertThat(source.sourceType()).isEqualTo("PAGE");
                assertThat(source.sourceIndex()).isEqualTo(3);
                assertThat(source.sourceLabel()).isEqualTo("3페이지");
            });

            SearchResponse result = searchService.search(ownerUserId, "PDF page source indexing evidence");
            assertThat(result.documentId()).isEqualTo(uploaded.documentId());
            assertThat(result.documentVersionId()).isEqualTo(uploaded.versionId());
            assertThat(result.sourceType()).isEqualTo(ChunkSourceType.PAGE);
            assertThat(result.sourceIndex()).isEqualTo(3);
            assertThat(result.sourceLabel()).isEqualTo("3페이지");
        }
        finally {
            try {
                deleteTestStoredFile(storedFilePath);
            }
            finally {
                deleteCommittedDocumentData();
            }
        }
    }

    @Test
    @Transactional
    void keepsPdfPageSearchResultsIsolatedByOwner() {
        Long firstOwnerUserId = createUser();
        Long secondOwnerUserId = createUser();
        Long firstDocumentId = createActivePdfPageDocument(
                firstOwnerUserId, "First owner PDF career evidence");
        createActivePdfPageDocument(secondOwnerUserId, "Second owner PDF career evidence");

        SearchResponse result = searchService.search(firstOwnerUserId, "First owner PDF career evidence");

        assertThat(result.documentId()).isEqualTo(firstDocumentId);
        assertThat(result.sourceType()).isEqualTo(ChunkSourceType.PAGE);
        assertThat(result.sourceIndex()).isEqualTo(1);
        assertThat(result.sourceLabel()).isEqualTo("1페이지");
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
                    owner_user_id, content, embedding, document_version_id, chunk_no, page_no,
                    source_type, source_index, source_label
                )
                VALUES (?, ?, CAST(? AS vector), ?, 1, NULL, 'TEXT_CHUNK', 1, '텍스트 구간 1')
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
    void commitsCleanupRegistrationIndependentlyAndKeepsItIdempotent() {
        String storageKey = "documents/cleanup/rollback.txt";
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);

        try {
            transactionTemplate.execute(status -> {
                fileCleanupJobService.registerPendingCleanup(storageKey);
                status.setRollbackOnly();
                return null;
            });

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM file_cleanup_jobs WHERE storage_key = ?", Long.class, storageKey))
                    .isEqualTo(1L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM file_cleanup_jobs WHERE storage_key = ?", String.class, storageKey))
                    .isEqualTo("PENDING");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT attempts FROM file_cleanup_jobs WHERE storage_key = ?", Integer.class, storageKey))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT available_at <= now() AND created_at IS NOT NULL AND updated_at IS NOT NULL "
                            + "FROM file_cleanup_jobs WHERE storage_key = ?",
                    Boolean.class,
                    storageKey)).isTrue();

            fileCleanupJobService.registerPendingCleanup(storageKey);

            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM file_cleanup_jobs WHERE storage_key = ?", Long.class, storageKey))
                    .isEqualTo(1L);
        }
        finally {
            jdbcTemplate.update("DELETE FROM file_cleanup_jobs WHERE storage_key = ?", storageKey);
        }
    }

    @Test
    void cleanupWorkerDeletesPendingFilesTreatsMissingFilesAsCompletedAndDoesNotReclaimCompletedJobs() throws Exception {
        assumeSecureFileDeletionSupported();
        String existingKey = "documents/cleanup/worker-existing.txt";
        String missingKey = "documents/cleanup/worker-missing.txt";
        Path existingFile = STORAGE_ROOT.resolve(existingKey);
        Files.createDirectories(existingFile.getParent());
        Files.writeString(existingFile, "orphan", StandardCharsets.UTF_8);

        try {
            fileCleanupJobService.registerPendingCleanup(existingKey);
            assertThat(fileCleanupCoordinator.processNext()).isTrue();
            assertThat(Files.exists(existingFile)).isFalse();
            assertThat(cleanupStatus(existingKey)).isEqualTo("COMPLETED");
            assertThat(fileCleanupCoordinator.processNext()).isFalse();

            fileCleanupJobService.registerPendingCleanup(missingKey);
            assertThat(fileCleanupCoordinator.processNext()).isTrue();
            assertThat(cleanupStatus(missingKey)).isEqualTo("COMPLETED");
        }
        finally {
            jdbcTemplate.update("DELETE FROM file_cleanup_jobs WHERE storage_key IN (?, ?)", existingKey, missingKey);
            Files.deleteIfExists(existingFile);
        }
    }

    @Test
    void cleanupClaimSkipsLockedFirstJobAndClaimsNextJobBeforeLockRelease() throws Exception {
        String firstStorageKey = "documents/cleanup/skip-locked-first.txt";
        String secondStorageKey = "documents/cleanup/skip-locked-second.txt";
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstRowLocked = new CountDownLatch(1);
        CountDownLatch releaseFirstLock = new CountDownLatch(1);
        Future<Void> firstLockTransaction = null;

        try {
            fileCleanupJobService.registerPendingCleanup(firstStorageKey);
            fileCleanupJobService.registerPendingCleanup(secondStorageKey);
            jdbcTemplate.update(
                    "UPDATE file_cleanup_jobs SET available_at = now() - INTERVAL '2 seconds' WHERE storage_key = ?",
                    firstStorageKey);
            jdbcTemplate.update(
                    "UPDATE file_cleanup_jobs SET available_at = now() - INTERVAL '1 second' WHERE storage_key = ?",
                    secondStorageKey);
            long firstJobId = jdbcTemplate.queryForObject(
                    "SELECT id FROM file_cleanup_jobs WHERE storage_key = ?", Long.class, firstStorageKey);
            long secondJobId = jdbcTemplate.queryForObject(
                    "SELECT id FROM file_cleanup_jobs WHERE storage_key = ?", Long.class, secondStorageKey);

            firstLockTransaction = executor.submit(() -> {
                TransactionTemplate lockTransaction = new TransactionTemplate(transactionManager);
                lockTransaction.executeWithoutResult(status -> {
                    Long lockedJobId = jdbcTemplate.queryForObject(
                            "SELECT id FROM file_cleanup_jobs WHERE id = ? FOR UPDATE", Long.class, firstJobId);
                    assertThat(lockedJobId).isEqualTo(firstJobId);
                    firstRowLocked.countDown();
                    awaitLatch(releaseFirstLock);
                });
                return null;
            });
            assertThat(firstRowLocked.await(5, TimeUnit.SECONDS)).isTrue();

            Future<Optional<ClaimedFileCleanupJob>> secondClaimTransaction = executor.submit(() -> {
                TransactionTemplate claimTransaction = new TransactionTemplate(transactionManager);
                return claimTransaction.execute(status -> {
                    jdbcTemplate.execute("SET LOCAL lock_timeout = '2s'");
                    return fileCleanupJobClaimService.claimNext();
                });
            });
            ClaimedFileCleanupJob secondClaim = secondClaimTransaction.get(5, TimeUnit.SECONDS).orElseThrow();

            assertThat(releaseFirstLock.getCount()).isOne();
            assertThat(secondClaim.fileCleanupJobId()).isEqualTo(secondJobId);
            assertThat(secondClaim.fileCleanupJobId()).isNotEqualTo(firstJobId);
            assertThat(cleanupStatus(firstStorageKey)).isEqualTo("PENDING");
            assertThat(cleanupStatus(secondStorageKey)).isEqualTo("PROCESSING");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT claim_version FROM file_cleanup_jobs WHERE storage_key = ?", Long.class, secondStorageKey))
                    .isGreaterThan(0L);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT lease_expires_at IS NOT NULL FROM file_cleanup_jobs WHERE storage_key = ?",
                    Boolean.class,
                    secondStorageKey)).isTrue();

            releaseFirstLock.countDown();
            firstLockTransaction.get(5, TimeUnit.SECONDS);

            ClaimedFileCleanupJob firstClaim = fileCleanupJobClaimService.claimNext().orElseThrow();
            assertThat(firstClaim.fileCleanupJobId()).isEqualTo(firstJobId);
            assertThat(firstClaim.fileCleanupJobId()).isNotEqualTo(secondClaim.fileCleanupJobId());
        }
        finally {
            releaseFirstLock.countDown();
            if (firstLockTransaction != null) {
                firstLockTransaction.get(5, TimeUnit.SECONDS);
            }
            executor.shutdownNow();
            assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            jdbcTemplate.update(
                    "DELETE FROM file_cleanup_jobs WHERE storage_key IN (?, ?)", firstStorageKey, secondStorageKey);
        }
    }

    @Test
    void cleanupWorkerConvergesToCompletedAfterCompletionUpdateFailureAndLeaseRecovery() throws Exception {
        assumeSecureFileDeletionSupported();
        String storageKey = "documents/cleanup/completion-update-failure.txt";
        Path file = STORAGE_ROOT.resolve(storageKey);
        Files.createDirectories(file.getParent());
        Files.writeString(file, "orphan", StandardCharsets.UTF_8);
        FileCleanupCompletionService failingCompletionService = new FileCleanupCompletionService(fileCleanupJobRepository) {
            @Override
            public void complete(ClaimedFileCleanupJob job) {
                throw new DataAccessResourceFailureException("simulated completion update failure");
            }
        };
        FileCleanupCoordinator coordinatorWithFailingCompletion = new FileCleanupCoordinator(
                fileCleanupJobClaimService,
                fileStorage,
                failingCompletionService,
                fileCleanupFailureClassifier,
                fileCleanupFailureService);

        try {
            fileCleanupJobService.registerPendingCleanup(storageKey);
            assertThat(coordinatorWithFailingCompletion.processNext()).isTrue();
            assertThat(Files.exists(file)).isFalse();
            assertThat(cleanupStatus(storageKey)).isEqualTo("PROCESSING");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT attempts FROM file_cleanup_jobs WHERE storage_key = ?", Integer.class, storageKey))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT lease_expires_at IS NOT NULL FROM file_cleanup_jobs WHERE storage_key = ?",
                    Boolean.class,
                    storageKey)).isTrue();

            long jobId = jdbcTemplate.queryForObject(
                    "SELECT id FROM file_cleanup_jobs WHERE storage_key = ?", Long.class, storageKey);
            long initialClaimVersion = jdbcTemplate.queryForObject(
                    "SELECT claim_version FROM file_cleanup_jobs WHERE storage_key = ?", Long.class, storageKey);
            ClaimedFileCleanupJob staleClaim = new ClaimedFileCleanupJob(
                    jobId, storageKey, 0, initialClaimVersion, Instant.EPOCH);

            jdbcTemplate.update(
                    "UPDATE file_cleanup_jobs SET lease_expires_at = now() - INTERVAL '1 second' WHERE storage_key = ?",
                    storageKey);
            assertThat(fileCleanupJobRecoveryService.recoverNext()).isTrue();
            assertThat(cleanupStatus(storageKey)).isEqualTo("RETRY_WAIT");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT attempts FROM file_cleanup_jobs WHERE storage_key = ?", Integer.class, storageKey))
                    .isEqualTo(1);
            assertThatThrownBy(() -> fileCleanupCompletionService.complete(staleClaim))
                    .isInstanceOf(StaleFileCleanupJobClaimException.class);

            jdbcTemplate.update(
                    "UPDATE file_cleanup_jobs SET available_at = now() - INTERVAL '1 second' WHERE storage_key = ?",
                    storageKey);
            assertThat(fileCleanupCoordinator.processNext()).isTrue();
            assertThat(cleanupStatus(storageKey)).isEqualTo("COMPLETED");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT claim_version FROM file_cleanup_jobs WHERE storage_key = ?", Long.class, storageKey))
                    .isGreaterThan(initialClaimVersion);
            assertThat(Files.exists(file)).isFalse();
        }
        finally {
            jdbcTemplate.update("DELETE FROM file_cleanup_jobs WHERE storage_key = ?", storageKey);
            Files.deleteIfExists(file);
        }
    }

    @Test
    void cleanupWorkerClaimsOnceWithSkipLockedAndRecoversAnExpiredClaim() throws Exception {
        String storageKey = "documents/cleanup/worker-recovery.txt";
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            fileCleanupJobService.registerPendingCleanup(storageKey);
            List<Future<Optional<com.prizm.cleanup.service.ClaimedFileCleanupJob>>> futures = List.of(
                    executor.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        return fileCleanupJobClaimService.claimNext();
                    }),
                    executor.submit(() -> {
                        start.await(5, TimeUnit.SECONDS);
                        return fileCleanupJobClaimService.claimNext();
                    }));
            start.countDown();
            List<com.prizm.cleanup.service.ClaimedFileCleanupJob> claims = futures.stream()
                    .map(future -> {
                        try {
                            return future.get(5, TimeUnit.SECONDS);
                        }
                        catch (Exception exception) {
                            throw new IllegalStateException(exception);
                        }
                    })
                    .flatMap(Optional::stream)
                    .toList();
            assertThat(claims).hasSize(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT status FROM file_cleanup_jobs WHERE storage_key = ?", String.class, storageKey))
                    .isEqualTo("PROCESSING");

            jdbcTemplate.update(
                    "UPDATE file_cleanup_jobs SET lease_expires_at = now() - INTERVAL '1 second' WHERE storage_key = ?",
                    storageKey);
            assertThat(fileCleanupJobRecoveryService.recoverNext()).isTrue();
            assertThat(cleanupStatus(storageKey)).isEqualTo("RETRY_WAIT");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT attempts FROM file_cleanup_jobs WHERE storage_key = ?", Integer.class, storageKey))
                    .isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT last_error_code FROM file_cleanup_jobs WHERE storage_key = ?", String.class, storageKey))
                    .isEqualTo("LEASE_EXPIRED");
            assertThatThrownBy(() -> fileCleanupCompletionService.complete(claims.get(0)))
                    .isInstanceOf(com.prizm.cleanup.exception.StaleFileCleanupJobClaimException.class);
        }
        finally {
            executor.shutdownNow();
            jdbcTemplate.update("DELETE FROM file_cleanup_jobs WHERE storage_key = ?", storageKey);
        }
    }

    @Test
    void staleCleanupClaimCannotOverwriteRetryOrFailureStateOwnedByNewWorker() {
        String storageKey = "documents/cleanup/stale-failure-fencing.txt";
        try {
            fileCleanupJobService.registerPendingCleanup(storageKey);
            ClaimedFileCleanupJob workerA = fileCleanupJobClaimService.claimNext().orElseThrow();
            jdbcTemplate.update(
                    "UPDATE file_cleanup_jobs SET lease_expires_at = now() - INTERVAL '1 second' WHERE storage_key = ?",
                    storageKey);

            assertThat(fileCleanupJobRecoveryService.recoverNext()).isTrue();
            assertThat(cleanupStatus(storageKey)).isEqualTo("RETRY_WAIT");
            jdbcTemplate.update(
                    "UPDATE file_cleanup_jobs SET available_at = now() - INTERVAL '1 second' WHERE storage_key = ?",
                    storageKey);
            ClaimedFileCleanupJob workerB = fileCleanupJobClaimService.claimNext().orElseThrow();
            assertThat(workerB.fileCleanupJobId()).isEqualTo(workerA.fileCleanupJobId());
            assertThat(workerB.claimVersion()).isEqualTo(workerA.claimVersion() + 2);

            CleanupJobState workerBState = cleanupJobState(storageKey);
            assertThat(workerBState.status()).isEqualTo("PROCESSING");
            assertThat(workerBState.attempts()).isEqualTo(1);
            assertThat(workerBState.claimVersion()).isEqualTo(workerB.claimVersion());
            assertThat(workerBState.leaseExpiresAt()).isNotNull();
            assertThat(workerBState.lastErrorCode()).isEqualTo("LEASE_EXPIRED");
            assertThat(workerBState.completedAt()).isNull();

            assertThatThrownBy(() -> fileCleanupFailureService.handleFailure(
                    workerA,
                    new CleanupFailure(true, "STALE_WORKER_RETRY")))
                    .isInstanceOf(StaleFileCleanupJobClaimException.class);
            assertThat(cleanupJobState(storageKey)).isEqualTo(workerBState);

            assertThatThrownBy(() -> fileCleanupFailureService.handleFailure(
                    workerA,
                    new CleanupFailure(false, "STALE_WORKER_FAIL")))
                    .isInstanceOf(StaleFileCleanupJobClaimException.class);
            assertThat(cleanupJobState(storageKey)).isEqualTo(workerBState);
        }
        finally {
            jdbcTemplate.update("DELETE FROM file_cleanup_jobs WHERE storage_key = ?", storageKey);
        }
    }

    @Test
    void cleanupWorkerMarksInvalidStorageKeyAsFailedWithoutRetry() {
        String storageKey = "/not-a-relative-storage-key";
        try {
            jdbcTemplate.update(
                    "INSERT INTO file_cleanup_jobs(storage_key, status, attempts, available_at, created_at, updated_at) "
                            + "VALUES (?, 'PENDING', 0, now(), now(), now())",
                    storageKey);

            assertThat(fileCleanupCoordinator.processNext()).isTrue();
            assertThat(cleanupStatus(storageKey)).isEqualTo("FAILED");
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT attempts FROM file_cleanup_jobs WHERE storage_key = ?", Integer.class, storageKey))
                    .isZero();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT last_error_code FROM file_cleanup_jobs WHERE storage_key = ?", String.class, storageKey))
                    .isEqualTo("PERMANENT_STORAGE_ERROR");
        }
        finally {
            jdbcTemplate.update("DELETE FROM file_cleanup_jobs WHERE storage_key = ?", storageKey);
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
    void renewsHeartbeatLeaseBeforeRecoveryWithoutChangingTheClaimVersion() {
        PendingJobFixture fixture = createPendingIndexingJob("heartbeat lease renewal document");

        try {
            ClaimedProcessingJob worker = processingJobClaimService.claimNext().orElseThrow();
            jdbcTemplate.update(
                    "UPDATE processing_jobs SET lease_expires_at = now() - INTERVAL '1 second' WHERE id = ?",
                    fixture.processingJobId());

            processingJobLeaseService.renew(worker);

            Long currentClaimVersion = jdbcTemplate.queryForObject(
                    "SELECT claim_version FROM processing_jobs WHERE id = ?",
                    Long.class,
                    fixture.processingJobId());
            Boolean leaseIsCurrent = jdbcTemplate.queryForObject(
                    "SELECT lease_expires_at > now() FROM processing_jobs WHERE id = ?",
                    Boolean.class,
                    fixture.processingJobId());
            assertThat(currentClaimVersion).isEqualTo(worker.claimVersion());
            assertThat(leaseIsCurrent).isTrue();
            assertThat(processingJobRecoveryService.recoverNext()).isFalse();
            assertThat(processingJobRepository.findById(fixture.processingJobId()).orElseThrow().getStatus())
                    .isEqualTo(ProcessingJobStatus.PROCESSING);
        }
        finally {
            deleteCommittedDocumentData();
        }
    }

    @Test
    void rejectsHeartbeatRenewalFromAStaleClaimVersion() {
        PendingJobFixture fixture = createPendingIndexingJob("stale heartbeat claim document");

        try {
            ClaimedProcessingJob worker = processingJobClaimService.claimNext().orElseThrow();
            jdbcTemplate.update(
                    "UPDATE processing_jobs SET claim_version = claim_version + 1 WHERE id = ?",
                    fixture.processingJobId());

            assertThatThrownBy(() -> processingJobLeaseService.renew(worker))
                    .isInstanceOf(StaleProcessingJobClaimException.class);
        }
        finally {
            deleteCommittedDocumentData();
        }
    }

    @Test
    void rejectsHeartbeatRenewalWhenTheJobIsNoLongerProcessing() {
        PendingJobFixture fixture = createPendingIndexingJob("non-processing heartbeat document");

        try {
            ClaimedProcessingJob worker = processingJobClaimService.claimNext().orElseThrow();
            jdbcTemplate.update(
                    "UPDATE processing_jobs SET status = 'RETRY_WAIT', lease_expires_at = NULL WHERE id = ?",
                    fixture.processingJobId());

            assertThatThrownBy(() -> processingJobLeaseService.renew(worker))
                    .isInstanceOf(StaleProcessingJobClaimException.class);
        }
        finally {
            deleteCommittedDocumentData();
        }
    }

    @Test
    void failsMissingOriginalFileWithoutStoringChunksOrActivatingTheVersion() {
        PendingJobFixture fixture = createPendingIndexingJob("missing source file document");

        try {
            ClaimedProcessingJob claim = processingJobClaimService.claimNext().orElseThrow();

            DocumentIndexingException exception = catchThrowableOfType(
                    () -> documentIndexingProcessor.process(claim), DocumentIndexingException.class);
            assertThat(exception).isNotNull();
            assertThat(exception.isRetryable()).isFalse();

            ProcessingJobStatus status = indexingFailureService.handleFailure(
                    claim,
                    indexingFailureClassifier.isRetryable(exception),
                    exception.getMessage());

            assertThat(status).isEqualTo(ProcessingJobStatus.FAILED);
            assertThat(processingJobRepository.findById(fixture.processingJobId()).orElseThrow().getRetryCount())
                    .isZero();
            assertThat(documentVersionRepository.findById(fixture.documentVersionId()).orElseThrow().getStatus())
                    .isEqualTo(DocumentVersionStatus.FAILED);
            assertThat(documentRepository.findById(fixture.documentId()).orElseThrow().getActiveVersionId()).isNull();
            assertThat(jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM document_chunks WHERE document_version_id = ?",
                    Long.class,
                    fixture.documentVersionId())).isZero();
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
                    owner_user_id, content, embedding, document_version_id, chunk_no, page_no,
                    source_type, source_index, source_label
                )
                VALUES (?, ?, CAST(? AS vector), ?, 1, NULL, 'TEXT_CHUNK', 1, '텍스트 구간 1')
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
        jdbcTemplate.update("DELETE FROM file_cleanup_jobs");
        jdbcTemplate.update("DELETE FROM processing_jobs");
        jdbcTemplate.update("DELETE FROM document_chunks");
        jdbcTemplate.update("UPDATE documents SET active_version_id = NULL");
        jdbcTemplate.update("DELETE FROM document_versions");
        jdbcTemplate.update("DELETE FROM documents");
        jdbcTemplate.update("DELETE FROM users");
    }

    private void deleteTestStoredFile(String storedFilePath) {
        Path target = STORAGE_ROOT.resolve(storedFilePath).normalize();
        if (!target.startsWith(STORAGE_ROOT)) {
            throw new IllegalArgumentException("Test storage path escapes the isolated test root.");
        }
        try {
            Files.deleteIfExists(target);
        }
        catch (IOException exception) {
            throw new UncheckedIOException("Failed to remove an isolated test storage file.", exception);
        }
    }

    private String cleanupStatus(String storageKey) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM file_cleanup_jobs WHERE storage_key = ?", String.class, storageKey);
    }

    private CleanupJobState cleanupJobState(String storageKey) {
        return jdbcTemplate.queryForObject(
                """
                SELECT status, attempts, claim_version, lease_expires_at, available_at,
                       last_error_code, completed_at, updated_at
                FROM file_cleanup_jobs
                WHERE storage_key = ?
                """,
                (resultSet, rowNum) -> new CleanupJobState(
                        resultSet.getString("status"),
                        resultSet.getInt("attempts"),
                        resultSet.getLong("claim_version"),
                        resultSet.getTimestamp("lease_expires_at"),
                        resultSet.getTimestamp("available_at"),
                        resultSet.getString("last_error_code"),
                        resultSet.getTimestamp("completed_at"),
                        resultSet.getTimestamp("updated_at")),
                storageKey);
    }

    private void assumeSecureFileDeletionSupported() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(STORAGE_ROOT.getRoot())) {
            Assumptions.assumeTrue(
                    stream instanceof SecureDirectoryStream<?>,
                    "SecureDirectoryStream is not available in this test environment.");
        }
    }

    private void awaitLatch(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for the test transaction latch.");
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for the test transaction latch.", exception);
        }
    }

    private long createActiveVectorDocumentVersion(Long ownerUserId) {
        return createActiveVectorDocument(ownerUserId, "Vector search verification").versionId();
    }

    private ActiveVectorDocument createActiveVectorDocument(Long ownerUserId, String title) {
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
                VALUES (?, ?, 1, 'vector-search.txt', 'test/vector-search.txt', 'TXT', repeat('a', 64), 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                documentId);
        jdbcTemplate.update("UPDATE documents SET active_version_id = ? WHERE id = ?", versionId, documentId);
        return new ActiveVectorDocument(documentId, versionId);
    }

    private void insertVectorChunk(Long ownerUserId, Long documentVersionId, String content, int chunkNo) {
        jdbcTemplate.update(
                """
                INSERT INTO document_chunks(
                    owner_user_id, content, embedding, document_version_id, chunk_no, page_no,
                    source_type, source_index, source_label
                )
                VALUES (?, ?, CAST(? AS vector), ?, ?, NULL, 'TEXT_CHUNK', ?, ?)
                """,
                ownerUserId,
                content,
                toVectorLiteral(embeddingService.embed(content)),
                documentVersionId,
                chunkNo,
                chunkNo,
                "텍스트 구간 " + chunkNo);
    }

    private Long createActivePdfPageDocument(Long ownerUserId, String content) {
        Long documentId = jdbcTemplate.queryForObject(
                "INSERT INTO documents(owner_user_id, title) VALUES (?, ?) RETURNING id",
                Long.class,
                ownerUserId,
                "PDF ownership verification");
        Long versionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path, file_type,
                    content_hash, status
                )
                VALUES (?, ?, 1, 'ownership.pdf', 'test/ownership.pdf', 'PDF', repeat('f', 64), 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                ownerUserId,
                documentId);
        jdbcTemplate.update("UPDATE documents SET active_version_id = ? WHERE id = ?", versionId, documentId);
        jdbcTemplate.update(
                """
                INSERT INTO document_chunks(
                    owner_user_id, content, embedding, document_version_id, chunk_no, page_no,
                    source_type, source_index, source_label
                )
                VALUES (?, ?, CAST(? AS vector), ?, 1, NULL, 'PAGE', 1, '1페이지')
                """,
                ownerUserId,
                content,
                toVectorLiteral(embeddingService.embed(content)),
                versionId);
        return documentId;
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
        return new IndexedChunk(
                1, ChunkSourceType.TEXT_CHUNK, 1, "텍스트 구간 1", content, embedding);
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

    private byte[] textPdf(List<String> pageTexts) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (String pageText : pageTexts) {
                PDPage page = new PDPage();
                document.addPage(page);
                if (!pageText.isBlank()) {
                    try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                        stream.beginText();
                        stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                        stream.newLineAtOffset(72, 720);
                        stream.showText(pageText);
                        stream.endText();
                    }
                }
            }
            document.save(output);
            return output.toByteArray();
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("prizm-integration-storage-");
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static final class ExternalDatabaseLifecycle implements Startable {

        @Override
        public void start() {
            // The caller owns the lifecycle of the explicitly configured integration-test database.
        }

        @Override
        public void stop() {
            // The caller owns the lifecycle of the explicitly configured integration-test database.
        }
    }

    private record PendingJobFixture(
            Long ownerUserId,
            Long documentId,
            Long documentVersionId,
            Long processingJobId) {
    }

    private record CleanupJobState(
            String status,
            int attempts,
            long claimVersion,
            java.sql.Timestamp leaseExpiresAt,
            java.sql.Timestamp availableAt,
            String lastErrorCode,
            java.sql.Timestamp completedAt,
            java.sql.Timestamp updatedAt) {
    }

    private record ActiveVectorDocument(Long documentId, Long versionId) {
    }

    private record StoredChunkSource(int chunkNo, String sourceType, int sourceIndex, String sourceLabel) {
    }
}
