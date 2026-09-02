package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.service.DocumentTextExtractor;
import com.prizm.ingestion.service.IndexingCoordinator;
import com.prizm.ingestion.service.PageText;
import com.prizm.ingestion.service.TextChunker;
import com.prizm.search.service.SearchService;
import com.prizm.search.v3.indexing.service.SearchV3EmbeddingModelContractProvider;
import com.prizm.search.v3.indexing.service.SearchV3IndexingCoordinator;
import com.prizm.search.v3.indexing.service.SearchV3JobDispatchService;
import com.prizm.search.v3.query.service.SearchV3ShadowQueryService;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
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

/** Opt-in, Gold-free real PostgreSQL/pgvector and local Ollama BGE-M3 runtime preflight. */
@ActiveProfiles("search-evaluation")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@EnabledIfSystemProperty(named = "prizm.prz044.preflight", matches = "true")
class Prz044PredictionPreflightIntegrationTest {

    private static final DockerImageName PGVECTOR = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");
    private static final Path STORAGE = Path.of(
                    "build", "prz044-preflight-storage-" + UUID.randomUUID())
            .toAbsolutePath().normalize();
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR)
            .withDatabaseName("prz044_preflight")
            .withUsername("prizm")
            .withPassword("prz044-preflight");

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
    void runsMixedTxtAndPdfThroughActualV2ThenActualV3() throws IOException {
        Prz044PredictionFreeze freeze = new Prz044PredictionFreeze();
        Prz044PredictionFreeze.VerifiedContract verifiedContract = freeze.verifyContract(PROJECT_ROOT);
        byte[] txt = """
                Release operations
                Automated deployment checks reduced repeated release errors.
                """.getBytes(StandardCharsets.UTF_8);
        byte[] pdf = pdf(
                "Incident review - Reduced response delay by 30 percent.",
                "Training record - Completed safety training in March 2026.");
        List<PageText> txtPages = textExtractor.extract(DocumentFileType.TXT, txt);
        List<PageText> pdfPages = textExtractor.extract(DocumentFileType.PDF, pdf);

        var users = List.of(
                new Prz044PredictionDataset.RuntimeUser(
                        "U1", "operations", "Operations", List.of("Release automation")),
                new Prz044PredictionDataset.RuntimeUser(
                        "U2", "support", "Support", List.of("Incident review")));
        var documents = List.of(
                document(
                        users.get(0), "D1", "V1", DocumentFileType.TXT, "release.txt",
                        "evaluation/users/U1/documents/D1/release.txt", txt, txtPages),
                document(
                        users.get(1), "D2", "V2", DocumentFileType.PDF, "incident.pdf",
                        "evaluation/users/U2/documents/D2/incident.pdf", pdf, pdfPages));
        var queries = List.of(
                query(users.get(0), "Q1", "automated deployment checks reduced release errors"),
                query(users.get(1), "Q2", "response delay reduced by 30 percent"));
        var input = new Prz044PredictionDataset.VerifiedInputPackage(
                Path.of("build/prz044-synthetic-input.zip"),
                "1".repeat(64),
                "2".repeat(64),
                "3".repeat(64),
                "4".repeat(64),
                "5".repeat(64),
                new Prz044PredictionDataset.SealedCommitment(
                        "sealed/gold.json", 1L, "6".repeat(64)),
                List.of(),
                List.of(),
                users,
                documents,
                queries);
        var contract = new Prz044PredictionRuntime.RunContract(
                Prz044PredictionRuntime.RunMode.PREFLIGHT,
                verifiedContract.expectedProfiles().get(Prz044PredictionArtifact.Engine.V2),
                verifiedContract.expectedProfiles().get(Prz044PredictionArtifact.Engine.V3),
                verifiedContract.contractSha256(),
                input.zipSha256(),
                input.manifestCanonicalSha256(),
                input.physicalCombinedSha256(),
                input.commitmentCombinedSha256(),
                verifiedContract.sourceBoundaryHashes(),
                Prz044PredictionFreeze.queryInventorySha256(queries),
                verifiedContract.expectedModel(),
                users.size(),
                documents.size(),
                queries.size(),
                true);
        var runtime = new Prz044PredictionRuntime(
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
                modelProvider);
        var precheck = runtime.precheckAndWarmUp(contract);
        var preflightRun = freeze.beginPreflight(verifiedContract, precheck);
        AtomicBoolean v2Reloaded = new AtomicBoolean();

        var completed = runtime.execute(input, contract, precheck, v2 -> {
            assertThat(v2.engine()).isEqualTo(Prz044PredictionArtifact.Engine.V2);
            assertThat(v2.queries()).extracting(Prz044PredictionArtifact.QueryPrediction::queryId)
                    .containsExactly("Q1", "Q2");
            Prz044PredictionFreeze.FrozenPreflightV2 frozen =
                    freeze.freezePreflightV2(preflightRun, v2);
            v2Reloaded.set(true);
            return frozen;
        });

        assertThat(v2Reloaded).isTrue();
        assertThat(completed.frozenV2().prediction().engine())
                .isEqualTo(Prz044PredictionArtifact.Engine.V2);
        assertThat(completed.v2().queries()).hasSize(2);
        assertThat(completed.v3().queries()).hasSize(2);
        assertThat(completed.v2().runtimeAudit().ownerLeakageCount()).isZero();
        assertThat(completed.v3().runtimeAudit().ownerLeakageCount()).isZero();
        assertThat(completed.v2().runtimeAudit().lifecycleViolationCount()).isZero();
        assertThat(completed.v3().runtimeAudit().lifecycleViolationCount()).isZero();
        assertThat(completed.v2().indexingStats().documentCount()).isEqualTo(2);
        assertThat(completed.v3().indexingStats().documentCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM processing_jobs WHERE status = 'COMPLETED'", Long.class))
                .isEqualTo(2L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM search_v3_index_generations WHERE status = 'ACTIVE'", Long.class))
                .isEqualTo(2L);
        assertThat(completed.v3().queries().get(1).finalResults()).isNotEmpty();
        assertThat(completed.v3().queries().get(1).finalResults())
                .allSatisfy(result -> {
                    assertThat(result.selectedSpans()).hasSize(1);
                    assertThat(result.displaySpans()).hasSize(1);
                    assertThat(result.selectedSpans().get(0).fileType())
                            .isEqualTo(DocumentFileType.PDF);
                    assertThat(result.selectedSpans().get(0).pageNumber()).isPositive();
                });
        boolean pdfProvenanceVerified = completed.v3().queries().get(1).finalResults().stream()
                .flatMap(result -> result.selectedSpans().stream())
                .allMatch(span -> span.fileType() == DocumentFileType.PDF
                        && span.pageNumber() != null && span.pageNumber() > 0);
        String postgresqlVersion = jdbc.queryForObject("SELECT version()", String.class);
        String pgvectorVersion = jdbc.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
        Prz044PredictionFreeze.PreflightReceipt receipt = freeze.completePreflight(
                completed.frozenV2(),
                completed.v3(),
                new Prz044PredictionFreeze.PreflightEvidence(
                        postgresqlVersion,
                        pgvectorVersion,
                        !txtPages.isEmpty(),
                        pdfPages.size() == 2,
                        pdfProvenanceVerified));
        assertThat(receipt.receiptPath()).isRegularFile();
        assertThat(receipt.receiptSha256()).matches("[0-9a-f]{64}");
        assertThat(freeze.verifyPreflightPass(verifiedContract).receiptSha256())
                .isEqualTo(receipt.receiptSha256());
        assertThat(Files.exists(Prz044PredictionFreeze.resolvePortable(
                PROJECT_ROOT, Prz044PredictionFreeze.OFFICIAL_RUN_DIRECTORY))).isFalse();
    }

    private static Prz044PredictionDataset.RuntimeDocument document(
            Prz044PredictionDataset.RuntimeUser user,
            String documentId,
            String versionId,
            DocumentFileType fileType,
            String filename,
            String relativePath,
            byte[] bytes,
            List<PageText> pages) {
        return new Prz044PredictionDataset.RuntimeDocument(
                user.userId(),
                user.professionId(),
                user.professionLabel(),
                documentId,
                versionId,
                "OTHER",
                fileType,
                filename,
                relativePath,
                sha256(bytes),
                bytes,
                pages,
                user.projectNames());
    }

    private static Prz044PredictionDataset.RuntimeQuery query(
            Prz044PredictionDataset.RuntimeUser user,
            String queryId,
            String text) {
        return new Prz044PredictionDataset.RuntimeQuery(
                queryId,
                user.userId(),
                user.professionId(),
                user.professionLabel(),
                "EN",
                text,
                sha256(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] pdf(String... pages) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            for (String text : pages) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(font, 12);
                    stream.newLineAtOffset(72, 720);
                    stream.showText(text);
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
