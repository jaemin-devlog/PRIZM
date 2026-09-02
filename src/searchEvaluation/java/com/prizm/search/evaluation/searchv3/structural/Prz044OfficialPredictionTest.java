package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.service.DocumentTextExtractor;
import com.prizm.ingestion.service.IndexingCoordinator;
import com.prizm.ingestion.service.TextChunker;
import com.prizm.search.service.SearchService;
import com.prizm.search.v3.indexing.service.SearchV3EmbeddingModelContractProvider;
import com.prizm.search.v3.indexing.service.SearchV3IndexingCoordinator;
import com.prizm.search.v3.indexing.service.SearchV3JobDispatchService;
import com.prizm.search.v3.query.service.SearchV3ShadowQueryService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
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

/** One-shot Gold-free PRZ-044 prediction orchestration. Never enable from a generic test task. */
@ActiveProfiles("search-evaluation")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@EnabledIfSystemProperty(named = "prizm.prz044.official", matches = "true")
class Prz044OfficialPredictionTest {

    private static final DockerImageName PGVECTOR = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path STORAGE = Path.of(
                    "build", "prz044-official-storage-" + UUID.randomUUID())
            .toAbsolutePath().normalize();

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR)
            .withDatabaseName("prz044_official")
            .withUsername("prizm")
            .withPassword("prz044-official");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("prizm.storage.root", STORAGE::toString);
        registry.add("prizm.storage.temp", () -> STORAGE.resolve("temp").toString());
        registry.add("prizm.search.profile", () -> "source-dedup-evidence-signals-v1");
        registry.add("prizm.ingestion.max-chunk-length", () -> 800);
        registry.add("prizm.ingestion.overlap", () -> 120);
        registry.add("prizm.document.pdf.max-pages", () -> 300);
        registry.add("prizm.document.pdf.max-extracted-characters", () -> 2_000_000);
        registry.add("prizm.embedding.dimensions", () -> 1024);
        registry.add("prizm.change-log.scheduler.enabled", () -> false);
        registry.add("prizm.ingestion.worker-enabled", () -> false);
        registry.add("prizm.search-v3.worker-enabled", () -> false);
        registry.add("prizm.cleanup.worker-enabled", () -> false);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired FileStorage fileStorage;
    @Autowired DocumentTextExtractor textExtractor;
    @Autowired TextChunker textChunker;
    @Autowired IngestionProperties ingestionProperties;
    @Autowired EmbeddingService embeddingService;
    @Autowired EmbeddingValidator embeddingValidator;
    @Autowired IndexingCoordinator v2Coordinator;
    @Autowired SearchService v2SearchService;
    @Autowired SearchV3JobDispatchService v3Dispatch;
    @Autowired SearchV3IndexingCoordinator v3Coordinator;
    @Autowired SearchV3ShadowQueryService v3QueryService;
    @Autowired SearchV3EmbeddingModelContractProvider modelProvider;

    @Test
    void freezesAllV2ThenAllV3PredictionsExactlyOnceWithoutGold() {
        Prz044PredictionFreeze freeze = new Prz044PredictionFreeze();
        Prz044PredictionFreeze.OfficialAttempt attempt = null;
        AtomicReference<String> stage = new AtomicReference<>("LOCATE_INPUT");
        try {
            Path inputZip = inputZip();
            stage.set("VERIFY_CONTRACT");
            Prz044PredictionFreeze.VerifiedContract contract = freeze.verifyContract(PROJECT_ROOT);
            stage.set("PREFLIGHT_INPUT");
            Prz044PredictionDataset.VerifiedInputPackage input = new Prz044PredictionDataset().preflight(
                    inputZip, contract.expectedInput(), textExtractor);
            assertThat(input.goldPresent()).isFalse();
            Prz044DocumentTypeMapping mapping = new Prz044DocumentTypeMapping();
            var verifiedMapping = mapping.verifyContract(PROJECT_ROOT);
            var mappingAudit = mapping.audit(input.documents());
            assertThat(mappingAudit.mappedCount()).isEqualTo(90);
            assertThat(mappingAudit.unmappedCount()).isZero();
            assertThat(mappingAudit.ambiguousCount()).isZero();
            stage.set("VERIFY_ATTEMPT_3_PREFLIGHT_RECEIPT");
            var preflight = freeze.verifyPreflightPass(contract);
            assertThat(preflight.receiptSha256()).matches("[0-9a-f]{64}");

            Prz044PredictionRuntime runtime = new Prz044PredictionRuntime(
                    jdbc,
                    fileStorage,
                    textExtractor,
                    textChunker,
                    ingestionProperties,
                    embeddingService,
                    embeddingValidator,
                    v2Coordinator,
                    v2SearchService,
                    v3Dispatch,
                    v3Coordinator,
                    v3QueryService,
                    modelProvider,
                    mapping);
            Prz044PredictionRuntime.RunContract runContract =
                    Prz044PredictionRuntime.RunContract.official(contract, input);

            // The real model resolve, neutral warm-up, and re-resolve all happen before claim.
            stage.set("MODEL_PRECHECK_AND_WARMUP");
            Prz044PredictionRuntime.ModelPrecheck precheck = runtime.precheckAndWarmUp(runContract);
            stage.set("CLAIM_OFFICIAL_ATTEMPT");
            attempt = freeze.claimOfficialAttempt(contract, input, precheck.model());

            Prz044PredictionFreeze.OfficialAttempt claimed = attempt;
            stage.set("RUNTIME_V2");
            Prz044PredictionRuntime.CompletedRun<Prz044PredictionFreeze.FrozenV2> completed =
                    runtime.execute(input, runContract, precheck, prediction -> {
                        stage.set("FREEZE_AND_RELOAD_V2");
                        Prz044PredictionFreeze.FrozenV2 frozen = freeze.freezeV2(claimed, prediction);
                        stage.set("RUNTIME_V3");
                        return frozen;
                    });

            assertThat(completed.v2().queries()).hasSize(contract.expectedInput().queryCount());
            assertThat(completed.v3().queries()).hasSize(contract.expectedInput().queryCount());
            assertThat(completed.v2().engine()).isEqualTo(Prz044PredictionArtifact.Engine.V2);
            assertThat(completed.v3().engine()).isEqualTo(Prz044PredictionArtifact.Engine.V3);

            stage.set("FREEZE_AND_RELOAD_V3");
            Prz044PredictionFreeze.FrozenV3 frozenV3 = freeze.freezeV3(
                    completed.frozenV2(), completed.v3());
            stage.set("COMPLETE_PREDICTIONS");
            Prz044PredictionFreeze.PredictionCompletion completion = freeze.complete(frozenV3);

            assertThat(completion.v2().predictions().queries()).hasSize(600);
            assertThat(completion.v3().predictions().queries()).hasSize(600);
            assertThat(completion.v2().canonicalSha256())
                    .isNotEqualTo(completion.v3().canonicalSha256());
            assertThat(completion.receiptPath()).isRegularFile();
            assertThat(Files.readString(completion.receiptPath()))
                    .contains("\"goldPresent\":false", "\"goldAccessed\":false");
        }
        catch (RuntimeException | Error failure) {
            if (attempt != null && !terminalReceiptExists(attempt)) {
                try {
                    freeze.recordFailure(attempt, stage.get(), failure);
                }
                catch (RuntimeException receiptFailure) {
                    failure.addSuppressed(receiptFailure);
                }
            }
            throw failure;
        }
        catch (Exception failure) {
            throw new IllegalStateException("cannot inspect PRZ-044 completion receipt", failure);
        }
    }

    private static Path inputZip() {
        String value = System.getProperty("prizm.prz044.input-zip");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("PRZ-044 INPUT ZIP locator is required");
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static boolean terminalReceiptExists(Prz044PredictionFreeze.OfficialAttempt attempt) {
        return Files.isRegularFile(attempt.runDirectory().resolve("prediction-completion-receipt.json"))
                || Files.isRegularFile(attempt.runDirectory().resolve("failure-receipt.json"));
    }
}
