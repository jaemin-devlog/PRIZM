package com.prizm.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.search.v3.indexing.service.SearchV3EmbeddingModelContractProvider;
import com.prizm.search.v3.indexing.service.SearchV3IndexingCoordinator;
import com.prizm.search.v3.indexing.service.SearchV3JobDispatchService;
import com.prizm.search.v3.query.service.SearchV3ShadowQueryService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
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

/** 로컬 Ollama BGE-M3와 PostgreSQL을 잇는 Search V3 비봉인 shadow smoke다. */
@ActiveProfiles("integration-test")
@SpringBootTest
@Testcontainers
class SearchV3RealBgeM3RuntimeIntegrationTest {

    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");
    private static final Path STORAGE_ROOT = createStorageRoot();

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm_search_v3_real_bge")
            .withUsername("prizm")
            .withPassword("prizm-test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("prizm.storage.root", STORAGE_ROOT::toString);
        registry.add("prizm.storage.temp", () -> STORAGE_ROOT.resolve("temp").toString());
        registry.add("prizm.search-v3.worker-enabled", () -> false);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired FileStorage fileStorage;
    @Autowired SearchV3EmbeddingModelContractProvider modelProvider;
    @Autowired EmbeddingService embeddingService;
    @Autowired SearchV3JobDispatchService dispatchService;
    @Autowired SearchV3IndexingCoordinator coordinator;
    @Autowired SearchV3ShadowQueryService queryService;

    @AfterAll
    static void cleanStorage() throws IOException {
        if (!Files.exists(STORAGE_ROOT)) return;
        try (var paths = Files.walk(STORAGE_ROOT)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void indexesAndQueriesShadowInventoryWithTheConfiguredRealBgeM3Model() {
        var model = modelProvider.resolve();
        assertThat(model.modelId()).isEqualTo("bge-m3");
        assertThat(model.resolvedModelDigest()).matches("[0-9a-f]{64}");
        assertThat(model.dimension()).isEqualTo(1024);
        assertThat(embeddingService.embed("Search V3 실제 BGE-M3 차원 확인")).hasSize(1024);

        long owner = insertOwner();
        long document = jdbc.queryForObject(
                "INSERT INTO documents(owner_user_id, title, document_type) VALUES (?, ?, 'OTHER') RETURNING id",
                Long.class,
                owner,
                "PRZ-041 실제 BGE-M3 smoke");
        byte[] content = """
                운영 자동화
                장애 발생 후 대응 단계를 자동화해 평균 복구 시간을 줄였다.

                고객 조사
                인터뷰 의견을 분류해 다음 분기 제품 계획에 반영했다.
                """.getBytes(StandardCharsets.UTF_8);
        long version = jdbc.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name, stored_file_path,
                    file_type, content_hash, status
                ) VALUES (?, ?, 1, 'runtime-smoke.txt', 'pending', ?, ?, 'ACTIVE') RETURNING id
                """,
                Long.class,
                owner,
                document,
                DocumentFileType.TXT.name(),
                sha256(content));
        String storageKey = fileStorage.store(document, version, "runtime-smoke.txt", content);
        jdbc.update("UPDATE document_versions SET stored_file_path = ? WHERE id = ?", storageKey, version);
        jdbc.update("UPDATE documents SET active_version_id = ? WHERE id = ?", version, document);

        var dispatched = dispatchService.dispatchNext().orElseThrow();
        assertThat(coordinator.processNext()).isTrue();

        assertThat(jdbc.queryForObject(
                "SELECT status FROM search_v3_index_generations WHERE id = ?",
                String.class,
                dispatched.generationId())).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM search_v3_indexing_jobs WHERE id = ?",
                String.class,
                dispatched.jobId())).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*) FROM search_v3_passage_embeddings
                WHERE generation_id = ? AND resolved_model_digest = ?
                  AND embedding_dimension = 1024 AND vector_dims(embedding) = 1024
                """,
                Long.class,
                dispatched.generationId(),
                model.resolvedModelDigest())).isPositive();
        assertThat(jdbc.queryForObject(
                """
                SELECT count(*) FROM search_v3_child_embeddings
                WHERE generation_id = ? AND resolved_model_digest = ?
                  AND embedding_dimension = 1024 AND vector_dims(embedding) = 1024
                """,
                Long.class,
                dispatched.generationId(),
                model.resolvedModelDigest())).isPositive();

        var result = queryService.search(owner, "운영 장애 복구 시간을 줄인 경험");
        var otherOwner = queryService.search(owner + 1, "운영 장애 복구 시간을 줄인 경험");
        assertThat(result.evidence()).isNotEmpty();
        assertThat(result.evidence().get(0).sourceText()).contains("평균 복구 시간을 줄였다");
        assertThat(result.evidence()).allSatisfy(value -> {
            assertThat(value.generationId()).isEqualTo(dispatched.generationId());
            assertThat(value.documentVersionId()).isEqualTo(version);
            assertThat(value.sourcePath()).isEqualTo(storageKey);
        });
        assertThat(otherOwner.evidence()).isEmpty();
    }

    private long insertOwner() {
        return jdbc.queryForObject(
                """
                INSERT INTO users(email, password_hash, role, enabled)
                VALUES (?, 'not-used', 'USER', TRUE) RETURNING id
                """,
                Long.class,
                "prz041-real-bge-" + UUID.randomUUID() + "@example.com");
    }

    private static Path createStorageRoot() {
        try {
            return Files.createTempDirectory("prizm-prz041-real-bge-");
        }
        catch (IOException exception) {
            throw new IllegalStateException("Could not create PRZ-041 smoke storage.", exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }
}
