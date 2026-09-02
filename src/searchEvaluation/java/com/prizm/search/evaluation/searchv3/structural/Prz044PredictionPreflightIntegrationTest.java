package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
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
import java.util.Map;
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

/** Attempt-3 mapping inventory and small actual PostgreSQL/BGE-M3 indexing preflight. */
@ActiveProfiles("search-evaluation")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
@EnabledIfSystemProperty(named = "prizm.prz044.attempt3-preflight", matches = "true")
class Prz044PredictionPreflightIntegrationTest {

    private static final DockerImageName PGVECTOR = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path STORAGE = Path.of(
                    "build", "prz044-attempt2-preflight-storage-" + UUID.randomUUID())
            .toAbsolutePath().normalize();
    private static final String V2_PROFILE = "PRODUCTION_SEARCH_V2_SOURCE_DEDUP_EVIDENCE_SIGNALS_V1";
    private static final String V3_PROFILE = "MINIMAL_V3_B3_TYPED_CHILD_DENSE_V1";

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR)
            .withDatabaseName("prz044_attempt3_preflight")
            .withUsername("prizm")
            .withPassword("prz044-attempt3-preflight");

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
    void validatesAllOfficialMappingsThenExercisesActualIndexingWithoutOfficialPredictions() throws IOException {
        Prz044DocumentTypeMapping mapping = new Prz044DocumentTypeMapping();
        var verifiedMapping = mapping.verifyContract(PROJECT_ROOT);
        var attempt1 = new Prz044Attempt2PreflightReceipt().verifyAttempt1(PROJECT_ROOT);
        assertThat(attempt1.attemptSha256())
                .isEqualTo("5630c6d6d2028076b862abdb3e2fa60b2c80e81196cdb71e596cc8e033c7bb74");

        Prz044PredictionFreeze freeze = new Prz044PredictionFreeze();
        var verifiedContract = freeze.verifyContract(PROJECT_ROOT);
        var officialInput = new Prz044PredictionDataset().preflight(
                inputZip(), verifiedContract.expectedInput(), textExtractor);
        var mappingAudit = mapping.audit(officialInput.documents());
        assertThat(mappingAudit.documentCount()).isEqualTo(90);
        assertThat(mappingAudit.mappedCount()).isEqualTo(90);
        assertThat(mappingAudit.unmappedCount()).isZero();
        assertThat(mappingAudit.ambiguousCount()).isZero();
        assertThat(mappingAudit.sourceCounts()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "CAREER_DESCRIPTION", 15,
                "PORTFOLIO", 15,
                "RESUME", 60));
        assertThat(mappingAudit.targetCounts()).containsExactlyInAnyOrderEntriesOf(Map.of(
                DocumentType.RESUME, 75,
                DocumentType.PORTFOLIO, 15));

        var synthetic = syntheticInput();
        var model = verifiedContract.expectedModel();
        var runContract = new Prz044PredictionRuntime.RunContract(
                Prz044PredictionRuntime.RunMode.PREFLIGHT,
                V2_PROFILE,
                V3_PROFILE,
                verifiedMapping.sha256(),
                synthetic.zipSha256(),
                synthetic.manifestCanonicalSha256(),
                synthetic.physicalCombinedSha256(),
                synthetic.commitmentCombinedSha256(),
                Map.of("DOCUMENT_TYPE_MAPPING", verifiedMapping.sha256()),
                Prz044PredictionFreeze.queryInventorySha256(synthetic.queries()),
                model,
                synthetic.users().size(),
                synthetic.documents().size(),
                synthetic.queries().size(),
                true);
        var runtime = new Prz044PredictionRuntime(
                jdbc, fileStorage, textExtractor, textChunker, ingestionProperties,
                embeddingService, embeddingValidator, v2Coordinator, v2SearchService,
                v3Dispatch, v3Coordinator, v3QueryService, modelProvider, mapping);
        var precheck = runtime.precheckAndWarmUp(runContract);
        AtomicBoolean v2Boundary = new AtomicBoolean();
        var preflightRun = freeze.beginPreflight(verifiedContract, precheck);
        var completed = runtime.execute(synthetic, runContract, precheck, v2 -> {
            v2Boundary.set(true);
            return freeze.freezePreflightV2(preflightRun, v2);
        });

        assertThat(v2Boundary).isTrue();
        assertThat(completed.v2().queries()).hasSize(3);
        assertThat(completed.v3().queries()).hasSize(3);
        assertThat(completed.v2().indexingStats().documentCount()).isEqualTo(3);
        assertThat(completed.v3().indexingStats().documentCount()).isEqualTo(3);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM processing_jobs WHERE status = 'COMPLETED'", Long.class))
                .isEqualTo(3L);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM search_v3_index_generations WHERE status = 'ACTIVE'", Long.class))
                .isEqualTo(3L);

        String postgresqlVersion = jdbc.queryForObject("SELECT version()", String.class);
        String pgvectorVersion = jdbc.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'vector'", String.class);
        var receipt = freeze.completePreflight(
                completed.frozenV2(),
                completed.v3(),
                new Prz044PredictionFreeze.PreflightEvidence(
                        postgresqlVersion, pgvectorVersion, true, true, true));
        assertThat(receipt.receiptPath()).isRegularFile();
        assertThat(receipt.receiptSha256()).matches("[0-9a-f]{64}");
        assertThat(Files.exists(PROJECT_ROOT.resolve(
                "local/search-v3-evaluation/prz044/official/"
                        + "6a7eca9b327b59ec5d0c5448cb08d1738298739747dd9509ec5a335a467f68ec/"
                        + "contract-v3/attempt-3"))).isFalse();
    }

    private Prz044PredictionDataset.VerifiedInputPackage syntheticInput() throws IOException {
        byte[] career = "Career summary\nReduced deployment errors through release checks."
                .getBytes(StandardCharsets.UTF_8);
        byte[] resume = "Resume\nCoordinated incident response and documented recovery steps."
                .getBytes(StandardCharsets.UTF_8);
        byte[] portfolio = pdf("Portfolio - Improved onboarding completion by 20 percent.");
        var users = List.of(
                new Prz044PredictionDataset.RuntimeUser("U1", "backend", "Backend", List.of()),
                new Prz044PredictionDataset.RuntimeUser("U2", "design", "Design", List.of()),
                new Prz044PredictionDataset.RuntimeUser("U3", "operations", "Operations", List.of()));
        var documents = List.of(
                document(users.get(0), "D1", "V1", "CAREER_DESCRIPTION",
                        DocumentFileType.TXT, "career.txt", career,
                        textExtractor.extract(DocumentFileType.TXT, career)),
                document(users.get(1), "D2", "V2", "PORTFOLIO",
                        DocumentFileType.PDF, "portfolio.pdf", portfolio,
                        textExtractor.extract(DocumentFileType.PDF, portfolio)),
                document(users.get(2), "D3", "V3", "RESUME",
                        DocumentFileType.TXT, "resume.txt", resume,
                        textExtractor.extract(DocumentFileType.TXT, resume)));
        var queries = List.of(
                query(users.get(0), "Q1", "release checks reduced deployment errors"),
                query(users.get(1), "Q2", "onboarding completion improvement"),
                query(users.get(2), "Q3", "incident recovery documentation"));
        return new Prz044PredictionDataset.VerifiedInputPackage(
                Path.of("build/prz044-attempt2-synthetic.zip"),
                "1".repeat(64), "2".repeat(64), "3".repeat(64), "4".repeat(64),
                "5".repeat(64),
                new Prz044PredictionDataset.SealedCommitment("sealed/gold.json", 1, "6".repeat(64)),
                List.of(), List.of(), users, documents, queries);
    }

    private static Prz044PredictionDataset.RuntimeDocument document(
            Prz044PredictionDataset.RuntimeUser user,
            String documentId,
            String versionId,
            String sourceType,
            DocumentFileType fileType,
            String filename,
            byte[] bytes,
            List<PageText> pages) {
        return new Prz044PredictionDataset.RuntimeDocument(
                user.userId(), user.professionId(), user.professionLabel(), documentId, versionId,
                sourceType, fileType, filename, "synthetic/" + filename, sha256(bytes), bytes, pages,
                user.projectNames());
    }

    private static Prz044PredictionDataset.RuntimeQuery query(
            Prz044PredictionDataset.RuntimeUser user, String queryId, String text) {
        return new Prz044PredictionDataset.RuntimeQuery(
                queryId, user.userId(), user.professionId(), user.professionLabel(),
                "EN", text, sha256(text.getBytes(StandardCharsets.UTF_8)));
    }

    private static Path inputZip() {
        String value = System.getProperty("prizm.prz044.input-zip");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("attempt-3 mapping preflight requires INPUT ZIP");
        }
        return Path.of(value).toAbsolutePath().normalize();
    }

    private static byte[] pdf(String text) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(72, 720);
                stream.showText(text);
                stream.endText();
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
