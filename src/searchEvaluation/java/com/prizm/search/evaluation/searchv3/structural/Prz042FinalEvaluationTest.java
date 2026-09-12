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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
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

/** One-shot official PRZ-042 execution. Never enable this test from a generic task. */
@ActiveProfiles("search-evaluation")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@EnabledIfSystemProperty(named = "prizm.prz042.official", matches = "true")
class Prz042FinalEvaluationTest {

    private static final DockerImageName PGVECTOR = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");
    private static final Path RUN_DIRECTORY = Path.of(System.getProperty(
                    "prizm.prz042.run-dir",
                    "local/search-v3-evaluation/prz042/final/attempt-1"))
            .toAbsolutePath().normalize();
    private static final Path CONTRACT = Path.of(System.getProperty(
                    "prizm.prz042.contract",
                    "specs/PRZ-042-search-v3-final-evaluation/execution-contract.json"))
            .toAbsolutePath().normalize();

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR)
            .withDatabaseName("prz042_final")
            .withUsername("prizm")
            .withPassword("prz042-final");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        Path storage = RUN_DIRECTORY.resolve("storage");
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("prizm.storage.root", storage::toString);
        registry.add("prizm.storage.temp", () -> storage.resolve("temp").toString());
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
    void executesTheFrozenFinalProtocolExactlyOnce() {
        Prz042FinalFreeze freeze = new Prz042FinalFreeze();
        Prz042FinalFreeze.Attempt attempt = null;
        String stage = "VERIFY_INPUT";
        try {
            Prz042FinalFreeze.VerifiedInput input = freeze.verifyInput(CONTRACT);
            stage = "CLAIM_ATTEMPT";
            attempt = freeze.claimAttempt(input, RUN_DIRECTORY);
            stage = "OPEN_INPUT";
            Prz042FinalDataset.RuntimeInput runtime = new Prz042FinalDataset().load(attempt);
            stage = "FREEZE_SEARCH_START";
            Prz042FinalFreeze.SearchStarted started = freeze.recordSearchStarted(
                    runtime.openedAttempt(), runtime);

            SearchV3MinimalShadowFreeze.SourceFreeze sourceFreeze =
                    new SearchV3MinimalShadowFreeze.SourceFreeze(
                            input.sourceBoundaryHashes().get("V2"),
                            input.sourceBoundaryHashes().get("V3"),
                            input.sourceBoundaryHashes().get("EVALUATOR"),
                            runtime.canonicalSha256(),
                            input.goldSchemaSha256(),
                            input.contractSha256());
            SearchV3MinimalShadowFreeze.SealedState sealed =
                    new SearchV3MinimalShadowFreeze.SealedState(
                            input.sealedCombinedSha256(), input.manifestSha256(),
                            input.sealedGitTree(), false, false, "NOT_RUN");
            ProductionSearchDecisionTracer tracer = new ProductionSearchDecisionTracer(
                    embeddingService, embeddingValidator, vectorRepository, profile,
                    expansionService, searchService);
            Prz042RuntimeComparison adapter = new Prz042RuntimeComparison(
                    jdbc, fileStorage, textChunker, embeddingService, embeddingValidator,
                    chunkRepository, vectorRepository, tracer, searchService, dispatch, coordinator,
                    v3Repository, v3QueryService, modelProvider);

            stage = "RUNTIME_SEARCH";
            Prz042FinalFreeze.PredictionBundle bundle = adapter.compare(
                    runtime,
                    new Prz042RuntimeComparison.RunMetadata(
                            input.baseCommit(), sourceFreeze, sealed,
                            input.modelId(), input.modelDigest(), input.modelDimension(), true));
            stage = "FREEZE_PREDICTIONS";
            Prz042FinalFreeze.VerifiedPredictions predictions =
                    freeze.freezeAndVerifyPredictions(started, bundle);

            stage = "GOLD_JOIN";
            SearchV3MinimalShadowGold.GoldSnapshot gold =
                    new Prz042FinalGold().load(predictions, runtime);
            stage = "EVALUATION";
            SearchV3MinimalShadowEvaluator.EvaluationReport evaluation =
                    new SearchV3MinimalShadowEvaluator().evaluate(
                            bundle.comparison(),
                            gold,
                            new SearchV3MinimalShadowEvaluator.InventoryContract(
                                    "PRZ-042_SEALED_SEED",
                                    input.queryCount(),
                                    input.userBundleCount(),
                                    input.directPositiveQueryCount(),
                                    input.notSupportedQueryCount(),
                                    Map.of("FRESH_FINAL", (long) input.queryCount())));
            Prz042FinalGate.GateReport gate =
                    new Prz042FinalGate().evaluate(evaluation, bundle.runtimeAudit(), input);

            stage = "COMPLETE";
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("verdict", gate.verdict().name());
            summary.put("resultScope", gate.resultScope());
            summary.put("v2Top1", gate.queryMicro().v2Top1());
            summary.put("v3Top1", gate.queryMicro().v3Top1());
            summary.put("v2Mrr", gate.queryMicro().v2Mrr());
            summary.put("v3Mrr", gate.queryMicro().v3Mrr());
            summary.put("v2RecallAt5", gate.queryMicro().v2RecallAt5());
            summary.put("v3RecallAt5", gate.queryMicro().v3RecallAt5());
            summary.put("releaseAdequacy", gate.releaseAdequacy().name());
            summary.put("sealedManifestMutated", false);
            freeze.complete(predictions, Map.of("evaluation", evaluation, "gate", gate), summary);

            assertThat(gate.verdict()).isNotNull();
            assertThat(gate.releaseAdequacy()).isEqualTo(Prz042FinalGate.Status.FAIL);
            assertThat(Files.isRegularFile(RUN_DIRECTORY.resolve("completion-receipt.json"))).isTrue();
        }
        catch (RuntimeException | Error failure) {
            if (attempt != null
                    && !Files.isRegularFile(RUN_DIRECTORY.resolve("completion-receipt.json"))
                    && !Files.isRegularFile(RUN_DIRECTORY.resolve("failure-receipt.json"))) {
                freeze.recordFailure(attempt, stage, failure);
            }
            throw failure;
        }
    }
}
