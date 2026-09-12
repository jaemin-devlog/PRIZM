package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.service.TextChunker;
import com.prizm.search.evaluation.trace.ProductionSearchDecisionTracer;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.service.EvidenceExpansionService;
import com.prizm.search.service.SearchService;
import com.prizm.search.v3.indexing.service.SearchV3EmbeddingModelContractProvider;
import com.prizm.search.v3.indexing.service.SearchV3IndexingCoordinator;
import com.prizm.search.v3.indexing.service.SearchV3JobDispatchService;
import com.prizm.search.v3.query.repository.SearchV3ShadowQueryRepository;
import com.prizm.search.v3.query.service.SearchV3ShadowQueryService;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
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

@ActiveProfiles("search-evaluation")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
class Prz042RuntimeComparisonIntegrationTest {

    private static final String EXPECTED_MODEL_ID = "bge-m3";
    private static final String EXPECTED_MODEL_DIGEST =
            "7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab";
    private static final int EXPECTED_DIMENSION = 1024;
    private static final DockerImageName PGVECTOR = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");
    private static final Path STORAGE = Path.of(
                    "build", "prz042-runtime-smoke-storage-" + UUID.randomUUID())
            .toAbsolutePath().normalize();

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR)
            .withDatabaseName("prz042_runtime_smoke")
            .withUsername("prizm")
            .withPassword("prz042-smoke");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("prizm.storage.root", STORAGE::toString);
        registry.add("prizm.storage.temp", () -> STORAGE.resolve("temp").toString());
        registry.add("prizm.change-log.scheduler.enabled", () -> false);
        registry.add("prizm.ingestion.worker-enabled", () -> false);
        registry.add("prizm.search-v3.worker-enabled", () -> false);
        registry.add("prizm.cleanup.worker-enabled", () -> false);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired FileStorage fileStorage;
    @Autowired TextChunker textChunker;
    @Autowired EmbeddingService embeddingService;
    @Autowired EmbeddingValidator embeddingValidator;
    @Autowired DocumentChunkRepository chunkRepository;
    @Autowired VectorSearchRepository vectorRepository;
    @Autowired CompositeSearchProfile profile;
    @Autowired EvidenceExpansionService expansionService;
    @Autowired SearchService searchService;
    @Autowired SearchV3JobDispatchService dispatch;
    @Autowired SearchV3IndexingCoordinator coordinator;
    @Autowired SearchV3ShadowQueryRepository v3Repository;
    @Autowired SearchV3ShadowQueryService v3QueryService;
    @Autowired SearchV3EmbeddingModelContractProvider modelProvider;

    @Test
    void comparesActualV2AndV3WithoutGoldOrSealedInput() {
        var model = modelProvider.resolve();
        assertThat(model.modelId()).isEqualTo(EXPECTED_MODEL_ID);
        assertThat(model.resolvedModelDigest()).isEqualTo(EXPECTED_MODEL_DIGEST);
        assertThat(model.dimension()).isEqualTo(EXPECTED_DIMENSION);
        String firstText = """
                운영 개선
                배포 점검 절차를 자동화해 반복 오류를 줄였다.

                고객 대응
                문의 유형을 분류해 응답 흐름을 정리했다.
                """;
        String secondText = """
                행사 기획
                지역 행사 참가자 동선을 분석해 안내 표지를 다시 배치했다.

                교육 이수
                2026년 3월 안전 교육을 수료했다.
                """;
        var runtime = new Prz042FinalDataset.RuntimeInput(
                Path.of("build/prz042-non-sealed-runtime"),
                "NON_SEALED_RUNTIME",
                "DEV",
                "a".repeat(64),
                "b".repeat(64),
                "c".repeat(64),
                "d".repeat(64),
                List.of(
                        document("U1", "D1", "V1", "synthetic/u1.txt", firstText),
                        document("U2", "D2", "V2", "synthetic/u2.txt", secondText)),
                List.of(
                        new Prz042FinalDataset.RuntimeQuery(
                                "Q1", "U1", "배포 점검 오류를 줄인 경험", "KO", "GENERAL", "operations"),
                        new Prz042FinalDataset.RuntimeQuery(
                                "Q2", "U2", "안전 교육 수료 시기", "KO", "GENERAL", "planning")),
                List.of(),
                "e".repeat(64),
                1,
                null);
        var sourceFreeze = new SearchV3MinimalShadowFreeze.SourceFreeze(
                "v2", "v3", "policy", runtime.canonicalSha256(), "schema", "contract");
        var sealedState = new SearchV3MinimalShadowFreeze.SealedState(
                "not-sealed", "not-sealed", "not-sealed", false, false, "NOT_RUN");
        var tracer = new ProductionSearchDecisionTracer(
                embeddingService, embeddingValidator, vectorRepository, profile,
                expansionService, searchService);
        var adapter = new Prz042RuntimeComparison(
                jdbc, fileStorage, textChunker, embeddingService, embeddingValidator,
                chunkRepository, vectorRepository, tracer, searchService, dispatch, coordinator,
                v3Repository, v3QueryService, modelProvider);

        var result = adapter.compare(runtime, new Prz042RuntimeComparison.RunMetadata(
                "NON_SEALED", sourceFreeze, sealedState,
                model.modelId(), model.resolvedModelDigest(), model.dimension(), true));
        Prz042FinalFreeze.requirePredictionStructure(runtime, result.comparison().queries());

        assertThat(result.comparison().queries()).hasSize(2);
        assertThat(result.comparison().sourceFreeze()).isEqualTo(sourceFreeze);
        assertThat(result.comparison().sealedState()).isEqualTo(sealedState);
        assertThat(result.comparison().sealedState().opened()).isFalse();
        assertThat(result.comparison().sealedState().searchExecuted()).isFalse();
        assertThat(result.comparison().sealedState().currentFreshBaseline()).isEqualTo("NOT_RUN");
        assertThat(result.comparison().queries()).allSatisfy(query -> {
            assertThat(query.v2().candidates()).isNotEmpty();
            assertThat(query.v3().candidates()).isNotEmpty();
            assertThat(query.v3().finalResults()).isNotEmpty();
        });
        assertThat(result.runtimeAudit().ownerLeakageCount()).isZero();
        assertThat(result.runtimeAudit().inactiveVersionLeakageCount()).isZero();
        assertThat(result.runtimeAudit().lifecycleViolationCount()).isZero();
        assertThat(result.runtimeAudit().duplicateArtifactCount()).isZero();
        assertThat(result.runtimeAudit().mixedArtifactCount()).isZero();
        assertThat(result.runtimeAudit().realBgeM3()).isTrue();
        assertThat(result.runtimeAudit().v2QueryExecutions()).isEqualTo(2);
        assertThat(result.runtimeAudit().v3QueryExecutions()).isEqualTo(2);
        assertThat(result.runtimeAudit().v3PassageVectorCount())
                .isEqualTo(result.runtimeAudit().v3PassageCount());
        assertThat(result.runtimeAudit().v3ChildVectorCount())
                .isEqualTo(result.runtimeAudit().v3ChildCount());
        assertThat(result.runtimeAudit().additionalModelCount()).isZero();
        assertThat(result.runtimeAudit().additionalServiceCount()).isZero();
        assertThat(result.runtimeAudit().gpuRequired()).isFalse();
        assertThat(result.runtimeAudit().modelDigest()).isEqualTo(model.resolvedModelDigest());
    }

    private static Prz042FinalDataset.RuntimeDocument document(
            String owner,
            String document,
            String version,
            String path,
            String source) {
        return new Prz042FinalDataset.RuntimeDocument(
                owner, "GENERAL", "general", document, document, document + "-LINEAGE",
                version, 1, true, document, "OTHER", "KO", path, source,
                Prz042FinalDataset.sha256(source));
    }
}
