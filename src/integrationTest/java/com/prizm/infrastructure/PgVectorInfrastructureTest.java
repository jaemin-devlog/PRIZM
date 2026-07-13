package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.document.DocumentDetailResponse;
import com.prizm.document.DocumentQueryService;
import com.prizm.document.DocumentUploadResponse;
import com.prizm.document.DocumentUploadService;
import com.prizm.document.DocumentVersionRepository;
import com.prizm.document.DocumentVersionStatus;
import com.prizm.embedding.EmbeddingService;
import com.prizm.search.SearchResponse;
import com.prizm.search.SearchService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("integration-test")
@SpringBootTest
@Testcontainers
class PgVectorInfrastructureTest {

    private static final Path STORAGE_ROOT = createStorageRoot();

    private static final List<String> SEARCH_TEST_SENTENCES = List.of(
            "연차 신청은 인사 시스템에서 진행합니다.",
            "서버 장애가 발생하면 운영 담당자에게 보고합니다.",
            "기밀 문서는 외부 AI 서비스에 전송할 수 없습니다.");

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

    @Test
    @Transactional
    void appliesFlywayAndStores1024DimensionVectorsForExactCosineSearch() {
        Integer serverVersion = jdbcTemplate.queryForObject(
                "SELECT current_setting('server_version_num')::integer", Integer.class);

        Long successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Long.class);
        Long documentCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM documents", Long.class);
        Long versionCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_versions", Long.class);
        Long chunkCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM document_chunks", Long.class);
        PgVectorSmokeAssertions.SmokeResult result =
                PgVectorSmokeAssertions.verifyExactCosineSearch(jdbcTemplate);

        assertThat(serverVersion).isBetween(160000, 169999);
        assertThat(successfulMigrations).isEqualTo(3);
        assertThat(result.extensionVersion()).isEqualTo("0.8.2");
        assertThat(documentCount).isZero();
        assertThat(versionCount).isZero();
        assertThat(chunkCount).isZero();
    }

    @Test
    @Transactional
    void storesBgeM3EmbeddingsAndFindsAnnualLeaveSentenceFirst() {
        long documentVersionId = createActiveVectorDocumentVersion();
        for (int index = 0; index < SEARCH_TEST_SENTENCES.size(); index++) {
            String sentence = SEARCH_TEST_SENTENCES.get(index);
            float[] embedding = embeddingService.embed(sentence);
            jdbcTemplate.update(
                    """
                    INSERT INTO document_chunks(content, embedding, document_version_id, chunk_no, page_no)
                    VALUES (?, CAST(? AS vector), ?, ?, ?)
                    """,
                    sentence,
                    toVectorLiteral(embedding),
                    documentVersionId,
                    index + 1,
                    1);
        }

        SearchResponse result = searchService.search("휴가는 어디에서 신청하나요?");

        assertThat(result.content()).isEqualTo(SEARCH_TEST_SENTENCES.get(0));
        assertThat(result.distance()).isBetween(0.0d, 2.0d);
        assertThat(result.score()).isEqualTo(1.0d - result.distance());
    }

    @Test
    @Transactional
    void uploadsTxtAsQuarantinedDocumentAndStoresFile() throws IOException {
        byte[] content = "연차 신청은 인사 시스템에서 진행합니다.".getBytes(StandardCharsets.UTF_8);
        DocumentUploadResponse response = documentUploadService.upload(
                "휴가 안내",
                new MockMultipartFile("file", "leave-guide.txt", "text/plain", content));

        DocumentDetailResponse detail = documentQueryService.get(response.documentId());

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

    private long createActiveVectorDocumentVersion() {
        Long documentId = jdbcTemplate.queryForObject(
                "INSERT INTO documents(title) VALUES (?) RETURNING id",
                Long.class,
                "Vector search verification");
        Long versionId = jdbcTemplate.queryForObject(
                """
                INSERT INTO document_versions(
                    document_id, version_no, original_file_name, stored_file_path, file_type, content_hash, status
                )
                VALUES (?, 1, 'vector-search.txt', 'test/vector-search.txt', 'TXT', repeat('a', 64), 'ACTIVE')
                RETURNING id
                """,
                Long.class,
                documentId);
        jdbcTemplate.update("UPDATE documents SET active_version_id = ? WHERE id = ?", versionId, documentId);
        return versionId;
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
}
