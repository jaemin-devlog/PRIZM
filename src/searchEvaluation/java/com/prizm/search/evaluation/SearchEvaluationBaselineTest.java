package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.service.IndexedChunk;
import com.prizm.ingestion.service.TextChunk;
import com.prizm.ingestion.service.TextChunker;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.PreparedInput;
import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.RawChunk;
import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.RawQuestion;
import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.SparseQuestion;
import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.SparseRank;
import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.SparseRun;
import com.prizm.search.evaluation.SearchEvaluationBgeRerankerArtifacts.RerankerQuestion;
import com.prizm.search.evaluation.SearchEvaluationBgeRerankerArtifacts.RerankerRank;
import com.prizm.search.evaluation.SearchEvaluationBgeRerankerArtifacts.RerankerRun;
import com.prizm.search.evaluation.SearchEvaluationDenseSparseRerankerProfile.RerankerCandidate;
import com.prizm.search.evaluation.SearchEvaluationDenseSparseRerankerProfile.RerankerOutcome;
import com.prizm.search.evaluation.SearchEvaluationDenseSparseRrfProfile.SparseCandidate;
import com.prizm.search.evaluation.SearchEvaluationHybridRrfProfile.Outcome;
import com.prizm.search.evaluation.SearchEvaluationLexicalCandidateRepository.LexicalCandidate;
import com.prizm.search.evaluation.SearchEvaluationData.CandidateResult;
import com.prizm.search.evaluation.SearchEvaluationData.Category;
import com.prizm.search.evaluation.SearchEvaluationData.Breakdown;
import com.prizm.search.evaluation.SearchEvaluationData.ChunkDescriptor;
import com.prizm.search.evaluation.SearchEvaluationData.Dataset;
import com.prizm.search.evaluation.SearchEvaluationData.EvidenceAnchor;
import com.prizm.search.evaluation.SearchEvaluationData.ExpectedEvidence;
import com.prizm.search.evaluation.SearchEvaluationData.EvaluationProfile;
import com.prizm.search.evaluation.SearchEvaluationData.EvaluationProfileKind;
import com.prizm.search.evaluation.SearchEvaluationData.FixtureDocument;
import com.prizm.search.evaluation.SearchEvaluationData.FixturePage;
import com.prizm.search.evaluation.SearchEvaluationData.OwnerScenario;
import com.prizm.search.evaluation.SearchEvaluationData.Question;
import com.prizm.search.evaluation.SearchEvaluationData.QuestionResult;
import com.prizm.search.evaluation.SearchEvaluationData.Report;
import com.prizm.search.evaluation.SearchEvaluationData.ReportFiles;
import com.prizm.search.evaluation.SearchEvaluationData.ScoreDistribution;
import com.prizm.search.evaluation.SearchEvaluationData.SearchState;
import com.prizm.search.evaluation.SearchEvaluationData.Split;
import com.prizm.search.evaluation.SearchEvaluationData.Summary;
import com.prizm.search.evaluation.SearchEvaluationData.VersionScenario;
import com.prizm.search.repository.VectorSearchResult;
import com.prizm.search.service.SearchService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.IntSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.ObjectMapper;

/** 실제 PostgreSQL·pgvector와 Ollama를 이용해 현재 Dense 검색 기준선을 측정한다. */
@ActiveProfiles("search-evaluation")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
class SearchEvaluationBaselineTest {

    private static final int PRODUCTION_CANDIDATE_LIMIT = 20;
    private static final int CANDIDATE_LIMIT = 30;
    private static final List<Integer> CANDIDATE_LIMITS = List.of(5, 10, PRODUCTION_CANDIDATE_LIMIT, CANDIDATE_LIMIT);
    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");
    private static final Set<String> FROZEN_TEST_DATASET_IDS = Set.of(
            "prizm-search-evidence-synthetic-v2.3",
            "prizm-career-evidence-synthetic-v1.0");
    private static final String FROZEN_TEST_ALLOW_ENVIRONMENT_VARIABLE =
            "PRIZM_SEARCH_EVALUATION_ALLOW_FROZEN_TEST";
    private static final String CHUNKING_ENVIRONMENT_VARIABLE =
            "PRIZM_SEARCH_EVALUATION_CHUNKING";
    private static final String PRODUCTION_CHUNKING_PROFILE = "production";
    private static final Path STORAGE_ROOT = createTemporaryStorageRoot();

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm_search_evaluation")
            .withUsername("prizm")
            .withPassword("prizm-search-evaluation");

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
    SearchService searchService;

    @Autowired
    EmbeddingService embeddingService;

    @Autowired
    EmbeddingValidator embeddingValidator;

    @Autowired
    TextChunker textChunker;

    @Autowired
    DocumentChunkRepository documentChunkRepository;

    @Autowired
    PlatformTransactionManager transactionManager;

    private final Map<Integer, List<Long>> candidateDbTimes = new LinkedHashMap<>();
    private final List<Long> lexicalDbNanos = new ArrayList<>();
    private final List<Long> fusionNanos = new ArrayList<>();
    private final List<Long> sparseQueryNanos = new ArrayList<>();
    private final List<Long> sparseFusionNanos = new ArrayList<>();
    private final List<Long> rerankerInferenceNanos = new ArrayList<>();
    private final List<Long> rerankerFusionNanos = new ArrayList<>();
    private final List<Integer> preparedChunkLengths = new ArrayList<>();
    private SparseRun sparseRun;
    private PreparedInput sparsePreparedInput;
    private RerankerRun rerankerRun;
    private final SearchEvaluationSectionChunker sectionChunker =
            new SearchEvaluationSectionChunker();
    private final SearchEvaluationSectionParagraphV2Chunker sectionParagraphV2Chunker =
            new SearchEvaluationSectionParagraphV2Chunker();

    @Test
    void measuresCurrentDenseSearchBaseline() {
        Path datasetPath = Path.of(System.getProperty("prizm.search-evaluation.dataset-dir"))
                .toAbsolutePath()
                .normalize();
        Path outputPath = Path.of(System.getProperty("prizm.search-evaluation.output-dir"))
                .toAbsolutePath()
                .normalize();

        ObjectMapper objectMapper = new ObjectMapper();
        Dataset loadedDataset = new SearchEvaluationDatasetLoader(objectMapper).load(datasetPath);
        Dataset dataset = selectDatasetForRun(loadedDataset);
        RunProfile runProfile = runProfile();
        candidateDbTimes.clear();
        lexicalDbNanos.clear();
        fusionNanos.clear();
        sparseQueryNanos.clear();
        sparseFusionNanos.clear();
        rerankerInferenceNanos.clear();
        rerankerFusionNanos.clear();
        preparedChunkLengths.clear();
        sparseRun = null;
        sparsePreparedInput = null;
        rerankerRun = null;
        SeededCorpus seededCorpus = seedCorpus(dataset);
        printChunkingStatistics();
        validateExpectedEvidence(dataset, seededCorpus.fixtureEvidenceIds());
        if (usesDenseSparseCandidates(runProfile)) {
            sparseRun = prepareSparseRun(
                    objectMapper,
                    outputPath,
                    dataset,
                    seededCorpus);
        }
        if (isBgeReranker(runProfile)) {
            rerankerRun = prepareRerankerRun(objectMapper, sparsePreparedInput);
        }

        List<QuestionResult> questionResults = evaluate(
                dataset,
                seededCorpus,
                runProfile,
                sparseRun,
                rerankerRun);
        Breakdown breakdown = new SearchEvaluationMetrics().calculateBreakdown(questionResults);
        Report report = new Report(
                Instant.now().toString(),
                dataset.corpus().datasetId(),
                runProfile.profile(),
                breakdown,
                questionResults);
        ReportFiles files = new SearchEvaluationReportWriter(objectMapper).write(outputPath, report);

        printReport(breakdown, questionResults, files);
        printHybridCost();
        printSparseCost();
        printRerankerCost();
        if (runProfile.currentProduct()) {
            printCandidateLimitAnalysis(questionResults);
        }
        assertThat(questionResults).hasSize(dataset.questions().size());
    }

    private RunProfile runProfile() {
        String value = System.getenv("PRIZM_SEARCH_EVALUATION_PROFILE");
        if (value == null || value.isBlank() || value.equals("current-product")) {
            return new RunProfile(
                    new EvaluationProfile("current-product", EvaluationProfileKind.CURRENT_PRODUCT),
                    true);
        }
        if (value.equals(SearchEvaluationCompositeProfile.PROFILE_ID)) {
            return new RunProfile(
                    new EvaluationProfile(value, EvaluationProfileKind.EVALUATION_COMPOSITE),
                    false);
        }
        if (value.equals(SearchEvaluationDeferredPageDedupProfile.PROFILE_ID)) {
            return new RunProfile(
                    new EvaluationProfile(value, EvaluationProfileKind.EVALUATION_COMPOSITE),
                    false);
        }
        if (value.equals(SearchEvaluationShortQueryRescueProfile.PROFILE_ID)) {
            return new RunProfile(
                    new EvaluationProfile(value, EvaluationProfileKind.EVALUATION_COMPOSITE),
                    false);
        }
        if (value.equals(SearchEvaluationHybridRrfProfile.PROFILE_ID)) {
            return new RunProfile(
                    new EvaluationProfile(value, EvaluationProfileKind.EVALUATION_COMPOSITE),
                    false);
        }
        if (value.equals(SearchEvaluationDenseSparseRrfProfile.PROFILE_ID)) {
            return new RunProfile(
                    new EvaluationProfile(value, EvaluationProfileKind.EVALUATION_COMPOSITE),
                    false);
        }
        if (value.equals(SearchEvaluationDenseSparseRerankerProfile.PROFILE_ID)) {
            return new RunProfile(
                    new EvaluationProfile(value, EvaluationProfileKind.EVALUATION_COMPOSITE),
                    false);
        }
        throw new SearchEvaluationDataException("Unknown search evaluation profile.");
    }

    private Dataset selectDatasetForRun(Dataset loadedDataset) {
        String splitValue = System.getenv("PRIZM_SEARCH_EVALUATION_SPLIT");
        boolean versionTwo = loadedDataset.corpus().schemaVersion() != null
                && loadedDataset.corpus().schemaVersion() >= 2;
        if (!versionTwo && (splitValue == null || splitValue.isBlank())) {
            return loadedDataset;
        }

        SearchEvaluationDatasetSelector selector = new SearchEvaluationDatasetSelector();
        Split selectedSplit = selector.parseRequiredSplit(splitValue);
        if (versionTwo
                && selectedSplit != Split.TUNING
                && !allowsFrozenTestRun(
                        loadedDataset,
                        selectedSplit,
                        System.getenv(FROZEN_TEST_ALLOW_ENVIRONMENT_VARIABLE))) {
            throw new SearchEvaluationDataException(
                    "Dataset v2 TEST execution requires the explicit frozen-dataset allow flag.");
        }
        return selector.select(loadedDataset, selectedSplit);
    }

    static boolean allowsFrozenTestRun(Dataset dataset, Split split, String allowFlag) {
        return split == Split.TEST
                && FROZEN_TEST_DATASET_IDS.contains(dataset.corpus().datasetId())
                && "true".equals(allowFlag);
    }

    private SeededCorpus seedCorpus(Dataset dataset) {
        Long primaryOwnerUserId = createEvaluationUser("primary");
        Long otherOwnerUserId = createEvaluationUser("other-owner");
        Long noSearchableDocumentsUserId = createEvaluationUser("no-searchable-documents");

        Map<String, FixtureScenario> scenarioByFixture = fixtureScenarios(
                dataset,
                primaryOwnerUserId,
                otherOwnerUserId);
        Map<Long, ChunkDescriptor> descriptors = new HashMap<>();
        Set<String> fixtureEvidenceIds = new HashSet<>();
        for (FixtureDocument document : dataset.corpus().documents()) {
            FixtureScenario scenario = scenarioByFixture.get(document.fixtureId());
            if (scenario == null) {
                throw new SearchEvaluationDataException("Selected fixture is not referenced by a question.");
            }
            PreparedDocument prepared = prepareDocument(document);
            SeededDocument seeded = new TransactionTemplate(transactionManager).execute(status ->
                    storeDocument(
                            scenario.ownerUserId(),
                            document,
                            prepared.chunks(),
                            scenario.versionScenario() == VersionScenario.ACTIVE));
            if (seeded == null) {
                throw new IllegalStateException("Failed to store a synthetic evaluation document.");
            }

            Map<Integer, Long> chunkIds = jdbcTemplate.query(
                    """
                            SELECT id, chunk_no
                            FROM document_chunks
                            WHERE owner_user_id = ? AND document_version_id = ?
                            ORDER BY chunk_no
                            """,
                    resultSet -> {
                        Map<Integer, Long> result = new LinkedHashMap<>();
                        while (resultSet.next()) {
                            result.put(resultSet.getInt("chunk_no"), resultSet.getLong("id"));
                        }
                        return result;
                    },
                    scenario.ownerUserId(),
                    seeded.versionId());

            for (Map.Entry<Integer, Long> entry : chunkIds.entrySet()) {
                int chunkNo = entry.getKey();
                List<String> matchedEvidence = prepared.evidenceByChunkNo()
                        .getOrDefault(chunkNo, List.of());
                fixtureEvidenceIds.addAll(matchedEvidence);
                descriptors.put(
                        entry.getValue(),
                        new ChunkDescriptor(
                                entry.getValue(),
                                document.fixtureId() + ":chunk-" + chunkNo,
                                matchedEvidence));
            }
        }
        return new SeededCorpus(
                primaryOwnerUserId,
                noSearchableDocumentsUserId,
                Map.copyOf(descriptors),
                Set.copyOf(fixtureEvidenceIds));
    }

    private Long createEvaluationUser(String localPart) {
        Long ownerUserId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO users(email, password_hash, role, enabled)
                        VALUES (?, ?, 'USER', TRUE)
                        RETURNING id
                """,
                Long.class,
                "search-evaluation-" + localPart + "@prizm.invalid",
                "{noop}search-evaluation-only");
        if (ownerUserId == null) {
            throw new IllegalStateException("Failed to create the synthetic evaluation user.");
        }
        return ownerUserId;
    }

    private Map<String, FixtureScenario> fixtureScenarios(
            Dataset dataset,
            Long primaryOwnerUserId,
            Long otherOwnerUserId) {
        Map<String, FixtureScenario> scenarios = new HashMap<>();
        for (FixtureDocument document : dataset.corpus().documents()) {
            List<Question> questions = dataset.questions().stream()
                    .filter(question -> question.fixtureIds().contains(document.fixtureId()))
                    .toList();
            Set<OwnerScenario> ownerScenarios = questions.stream()
                    .map(Question::ownerScenario)
                    .collect(java.util.stream.Collectors.toSet());
            Set<VersionScenario> versionScenarios = questions.stream()
                    .map(Question::versionScenario)
                    .collect(java.util.stream.Collectors.toSet());
            if (ownerScenarios.size() != 1 || versionScenarios.size() != 1) {
                throw new SearchEvaluationDataException(
                        "A fixture must have one owner and version scenario within the selected split.");
            }
            OwnerScenario ownerScenario = ownerScenarios.iterator().next();
            Long ownerUserId = ownerScenario == OwnerScenario.PRIMARY_OWNER
                    ? primaryOwnerUserId
                    : otherOwnerUserId;
            scenarios.put(
                    document.fixtureId(),
                    new FixtureScenario(ownerUserId, versionScenarios.iterator().next()));
        }
        return Map.copyOf(scenarios);
    }

    private PreparedDocument prepareDocument(FixtureDocument document) {
        List<IndexedChunk> chunks = new ArrayList<>();
        Map<Integer, List<String>> evidenceByChunkNo = new HashMap<>();
        int nextChunkNo = 1;

        List<FixturePage> pages = document.pages().stream()
                .sorted(Comparator.comparingInt(FixturePage::pageNumber))
                .toList();
        for (FixturePage page : pages) {
            List<TextChunk> pageChunks = splitEvaluationPage(page.text());
            for (TextChunk pageChunk : pageChunks) {
                int chunkNo = nextChunkNo++;
                ChunkSourceType sourceType = document.fileType() == DocumentFileType.PDF
                        ? ChunkSourceType.PAGE
                        : ChunkSourceType.TEXT_CHUNK;
                int sourceIndex = sourceType == ChunkSourceType.PAGE ? page.pageNumber() : chunkNo;
                String sourceLabel = sourceType == ChunkSourceType.PAGE
                        ? sourceIndex + "페이지"
                        : "텍스트 구간 " + sourceIndex;
                float[] embedding = embeddingService.embed(pageChunk.content());
                embeddingValidator.validate(embedding);
                chunks.add(new IndexedChunk(
                        chunkNo,
                        sourceType,
                        sourceIndex,
                        sourceLabel,
                        pageChunk.content(),
                        embedding));
                preparedChunkLengths.add(pageChunk.content().length());

                List<String> matchedEvidence = document.evidenceAnchors().stream()
                        .filter(anchor -> pageChunk.content().contains(anchor.anchorText()))
                        .map(EvidenceAnchor::fixtureEvidenceId)
                        .sorted()
                        .toList();
                evidenceByChunkNo.put(chunkNo, matchedEvidence);
            }
        }
        if (chunks.isEmpty()) {
            throw new SearchEvaluationDataException("Synthetic evaluation document produced no chunks.");
        }
        return new PreparedDocument(List.copyOf(chunks), Map.copyOf(evidenceByChunkNo));
    }

    private List<TextChunk> splitEvaluationPage(String text) {
        return switch (chunkingProfile()) {
            case PRODUCTION_CHUNKING_PROFILE -> textChunker.split(text);
            case SearchEvaluationSectionChunker.PROFILE_ID -> sectionChunker.split(text);
            case SearchEvaluationSectionParagraphV2Chunker.PROFILE_ID ->
                    sectionParagraphV2Chunker.split(text);
            default -> throw new SearchEvaluationDataException(
                    "Unknown search evaluation chunking profile.");
        };
    }

    private String chunkingProfile() {
        String value = System.getenv(CHUNKING_ENVIRONMENT_VARIABLE);
        return value == null || value.isBlank() ? PRODUCTION_CHUNKING_PROFILE : value.strip();
    }

    private void printChunkingStatistics() {
        IntSummaryStatistics statistics = preparedChunkLengths.stream()
                .mapToInt(Integer::intValue)
                .summaryStatistics();
        System.out.printf(
                Locale.ROOT,
                "Evaluation chunking | profile=%s | chunks=%d | min=%d | avg=%.2f | max=%d%n",
                chunkingProfile(),
                statistics.getCount(),
                statistics.getMin(),
                statistics.getAverage(),
                statistics.getMax());
    }

    private SparseRun prepareSparseRun(
            ObjectMapper objectMapper,
            Path outputPath,
            Dataset dataset,
            SeededCorpus seededCorpus) {
        List<RawChunk> chunks = jdbcTemplate.query(
                """
                        SELECT chunk.id, chunk.content
                        FROM document_chunks chunk
                        JOIN document_versions version
                          ON chunk.document_version_id = version.id
                        JOIN documents document
                          ON document.id = version.document_id
                         AND document.active_version_id = version.id
                        WHERE version.status = 'ACTIVE'
                          AND document.owner_user_id = ?
                          AND version.owner_user_id = ?
                          AND chunk.owner_user_id = ?
                        ORDER BY document.id, chunk.chunk_no
                        """,
                (resultSet, rowNum) -> {
                    long chunkId = resultSet.getLong("id");
                    ChunkDescriptor descriptor = seededCorpus.chunkDescriptors().get(chunkId);
                    if (descriptor == null) {
                        throw new SearchEvaluationDataException(
                                "P14 sparse input found a searchable chunk without a fixture descriptor.");
                    }
                    return new RawChunk(
                            descriptor.fixtureChunkId(),
                            resultSet.getString("content"));
                },
                seededCorpus.primaryOwnerUserId(),
                seededCorpus.primaryOwnerUserId(),
                seededCorpus.primaryOwnerUserId());
        List<RawQuestion> questions = dataset.questions().stream()
                .map(question -> new RawQuestion(question.questionId(), question.query()))
                .toList();

        SearchEvaluationBgeM3SparseArtifacts artifacts =
                new SearchEvaluationBgeM3SparseArtifacts(objectMapper);
        PreparedInput prepared = artifacts.writeInput(
                outputPath,
                dataset.corpus().datasetId(),
                chunkingProfile(),
                chunks,
                questions);
        sparsePreparedInput = prepared;

        String sparseOutputValue = System.getenv(
                SearchEvaluationBgeM3SparseArtifacts.OUTPUT_ENVIRONMENT_VARIABLE);
        if (sparseOutputValue == null || sparseOutputValue.isBlank()) {
            throw new SearchEvaluationDataException(
                    "P14 sparse input was written to "
                            + outputPath.resolve(SearchEvaluationBgeM3SparseArtifacts.INPUT_FILE)
                            + "; generate the official FlagEmbedding output and set "
                            + SearchEvaluationBgeM3SparseArtifacts.OUTPUT_ENVIRONMENT_VARIABLE
                            + ".");
        }
        return artifacts.loadOutput(
                Path.of(sparseOutputValue).toAbsolutePath().normalize(),
                prepared);
    }

    private RerankerRun prepareRerankerRun(
            ObjectMapper objectMapper,
            PreparedInput preparedInput) {
        if (preparedInput == null) {
            throw new SearchEvaluationDataException(
                    "P15 reranker requires the exact prepared P14 input.");
        }
        String rerankerOutputValue = System.getenv(
                SearchEvaluationBgeRerankerArtifacts.OUTPUT_ENVIRONMENT_VARIABLE);
        if (rerankerOutputValue == null || rerankerOutputValue.isBlank()) {
            throw new SearchEvaluationDataException(
                    "P15 requires an official bge-reranker-v2-m3 output; set "
                            + SearchEvaluationBgeRerankerArtifacts.OUTPUT_ENVIRONMENT_VARIABLE
                            + ".");
        }
        return new SearchEvaluationBgeRerankerArtifacts(objectMapper).loadOutput(
                Path.of(rerankerOutputValue).toAbsolutePath().normalize(),
                preparedInput);
    }

    private SeededDocument storeDocument(
            Long ownerUserId,
            FixtureDocument document,
            List<IndexedChunk> chunks,
            boolean activate) {
        Long documentId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO documents(title, owner_user_id, document_type)
                        VALUES (?, ?, ?)
                        RETURNING id
                        """,
                Long.class,
                document.title(),
                ownerUserId,
                document.documentType().name());
        if (documentId == null) {
            throw new IllegalStateException("Failed to create a synthetic evaluation document.");
        }

        String extension = document.fileType().name().toLowerCase(Locale.ROOT);
        Long versionId = jdbcTemplate.queryForObject(
                """
                        INSERT INTO document_versions(
                            document_id, version_no, original_file_name, stored_file_path,
                            file_type, content_hash, status, owner_user_id
                        )
                        VALUES (?, 1, ?, ?, ?, ?, 'ACTIVE', ?)
                        RETURNING id
                        """,
                Long.class,
                documentId,
                document.fixtureId() + "." + extension,
                "search-evaluation/" + document.fixtureId() + "." + extension,
                document.fileType().name(),
                contentHash(document),
                ownerUserId);
        if (versionId == null) {
            throw new IllegalStateException("Failed to create a synthetic evaluation version.");
        }

        documentChunkRepository.replaceAll(ownerUserId, versionId, chunks);
        if (activate) {
            int activated = jdbcTemplate.update(
                    "UPDATE documents SET active_version_id = ?, updated_at = now() WHERE id = ? AND owner_user_id = ?",
                    versionId,
                    documentId,
                    ownerUserId);
            if (activated != 1) {
                throw new IllegalStateException("Failed to activate a synthetic evaluation version.");
            }
        }
        return new SeededDocument(versionId);
    }

    private List<QuestionResult> evaluate(
            Dataset dataset,
            SeededCorpus seededCorpus,
            RunProfile runProfile,
            SparseRun sparseRun,
            RerankerRun rerankerRun) {
        SearchEvaluationCandidateRepository candidateRepository =
                new SearchEvaluationCandidateRepository(jdbcTemplate);
        SearchEvaluationLexicalCandidateRepository lexicalCandidateRepository =
                new SearchEvaluationLexicalCandidateRepository(jdbcTemplate);
        SearchEvaluationHybridRrfProfile hybridRrfProfile =
                new SearchEvaluationHybridRrfProfile();
        SearchEvaluationDenseSparseRrfProfile denseSparseRrfProfile =
                new SearchEvaluationDenseSparseRrfProfile();
        SearchEvaluationDenseSparseRerankerProfile denseSparseRerankerProfile =
                new SearchEvaluationDenseSparseRerankerProfile();
        List<QuestionResult> results = new ArrayList<>();

        for (Question question : dataset.questions()) {
            Long queryOwnerUserId = question.ownerScenario() == OwnerScenario.NO_SEARCHABLE_DOCUMENTS
                    ? seededCorpus.noSearchableDocumentsUserId()
                    : seededCorpus.primaryOwnerUserId();
            List<CareerEvidenceSearchResponse> productionTop5 =
                    searchService.searchCareerEvidence(queryOwnerUserId, question.query());

            long totalStartedAt = System.nanoTime();
            long embeddingStartedAt = totalStartedAt;
            float[] queryEmbedding = embeddingService.embed(question.query());
            embeddingValidator.validate(queryEmbedding);
            long embeddingElapsedNanos = System.nanoTime() - embeddingStartedAt;
            long embeddingElapsedMillis = elapsedMillis(embeddingElapsedNanos);
            long dbStartedAt = System.nanoTime();
            List<VectorSearchResult> top20 = candidateRepository.findCandidates(
                    queryOwnerUserId,
                    queryEmbedding,
                    CANDIDATE_LIMIT);
            long denseDbElapsedNanos = System.nanoTime() - dbStartedAt;
            long baseTotalElapsedNanos = System.nanoTime() - totalStartedAt;
            long lexicalDbElapsedNanos = 0L;
            List<LexicalCandidate> lexicalCandidates = List.of();
            if (isHybridRrf(runProfile)) {
                long lexicalStartedAt = System.nanoTime();
                lexicalCandidates = lexicalCandidateRepository.findCandidates(
                        queryOwnerUserId,
                        question.query(),
                        queryEmbedding,
                        SearchEvaluationHybridRrfProfile.BRANCH_CANDIDATE_LIMIT);
                lexicalDbElapsedNanos = System.nanoTime() - lexicalStartedAt;
                lexicalDbNanos.add(lexicalDbElapsedNanos);
            }
            SparseQuestion sparseQuestion = null;
            long sparseQueryElapsedNanos = 0L;
            if (usesDenseSparseCandidates(runProfile)) {
                if (sparseRun == null) {
                    throw new SearchEvaluationDataException(
                            "P14 sparse profile requires a validated sparse run.");
                }
                sparseQuestion = sparseRun.question(question.questionId());
                sparseQueryElapsedNanos = Math.round(sparseQuestion.totalMillis() * 1_000_000.0d);
                sparseQueryNanos.add(sparseQueryElapsedNanos);
            }
            RerankerQuestion rerankerQuestion = null;
            long rerankerElapsedNanos = 0L;
            if (isBgeReranker(runProfile)) {
                if (rerankerRun == null) {
                    throw new SearchEvaluationDataException(
                            "P15 reranker profile requires a validated reranker run.");
                }
                rerankerQuestion = rerankerRun.question(question.questionId());
                if (denseSparseRerankerProfile.reranks(question.query())) {
                    rerankerElapsedNanos = Math.round(
                            rerankerQuestion.inferenceMillis() * 1_000_000.0d);
                    rerankerInferenceNanos.add(rerankerElapsedNanos);
                }
            }
            long totalElapsedNanos = baseTotalElapsedNanos
                    + lexicalDbElapsedNanos
                    + sparseQueryElapsedNanos
                    + rerankerElapsedNanos;
            long dbElapsedNanos = denseDbElapsedNanos + lexicalDbElapsedNanos;
            candidateDbTimes.computeIfAbsent(CANDIDATE_LIMIT, ignored -> new ArrayList<>())
                    .add(elapsedMillis(denseDbElapsedNanos));

            for (int candidateLimit : CANDIDATE_LIMITS) {
                if (candidateLimit == CANDIDATE_LIMIT) {
                    continue;
                }
                long experimentStartedAt = System.nanoTime();
                List<VectorSearchResult> experiment = candidateRepository.findCandidates(
                        queryOwnerUserId,
                        queryEmbedding,
                        candidateLimit);
                long experimentElapsedMillis = elapsedMillisSince(experimentStartedAt);
                candidateDbTimes.computeIfAbsent(candidateLimit, ignored -> new ArrayList<>())
                        .add(experimentElapsedMillis);
                List<Long> expectedPrefix = top20.stream()
                        .limit(candidateLimit)
                        .map(VectorSearchResult::chunkId)
                        .toList();
                List<Long> actualIds = experiment.stream().map(VectorSearchResult::chunkId).toList();
                if (!actualIds.equals(expectedPrefix)) {
                    throw new IllegalStateException("Candidate-limit experiment changed exact cosine order.");
                }
            }

            List<Long> productionIds = productionTop5.stream()
                    .map(CareerEvidenceSearchResponse::chunkId)
                    .toList();
            Set<Long> productCandidateIds = top20.stream()
                    .limit(PRODUCTION_CANDIDATE_LIMIT)
                    .map(VectorSearchResult::chunkId)
                    .collect(java.util.stream.Collectors.toSet());
            if (!productCandidateIds.containsAll(productionIds)) {
                throw new IllegalStateException("Production result is missing from the evaluated top-20 candidate set.");
            }

            List<VectorSearchResult> evaluatedCandidates = top20;
            Map<Long, VectorSearchResult> evaluatedCandidatesById = top20.stream()
                    .collect(java.util.stream.Collectors.toMap(
                            VectorSearchResult::chunkId,
                            candidate -> candidate));
            List<VectorSearchResult> returnedResults = productionIds.stream()
                    .map(evaluatedCandidatesById::get)
                    .toList();
            if (!runProfile.currentProduct()) {
                List<VectorSearchResult> productCandidates = top20.stream()
                        .limit(PRODUCTION_CANDIDATE_LIMIT)
                        .toList();
                long fusionStartedAt = System.nanoTime();
                CompositeSearchProfile.Decision decision;
                if (isHybridRrf(runProfile)) {
                    Outcome outcome = hybridRrfProfile.apply(
                            question.query(),
                            productCandidates,
                            lexicalCandidates);
                    decision = outcome.decision();
                    evaluatedCandidates = outcome.fusedCandidates().stream()
                            .map(candidate -> candidate.candidate())
                            .toList();
                    long fusionElapsedNanos = System.nanoTime() - fusionStartedAt;
                    fusionNanos.add(fusionElapsedNanos);
                    totalElapsedNanos += fusionElapsedNanos;
                    printHybridTrace(question, lexicalCandidates, outcome);
                }
                else if (usesDenseSparseCandidates(runProfile)) {
                    List<SparseCandidate> sparseCandidates = mapSparseCandidates(
                            sparseQuestion,
                            top20,
                            seededCorpus.chunkDescriptors());
                    if (isBgeReranker(runProfile)) {
                        List<RerankerCandidate> rerankerCandidates = mapRerankerCandidates(
                                rerankerQuestion,
                                top20,
                                seededCorpus.chunkDescriptors());
                        RerankerOutcome outcome = denseSparseRerankerProfile.apply(
                                question.query(),
                                productCandidates,
                                sparseCandidates,
                                rerankerCandidates);
                        decision = outcome.decision();
                        evaluatedCandidates = outcome.rerankedCandidates().isEmpty()
                                ? outcome.p14Candidates().stream()
                                        .map(candidate -> candidate.candidate())
                                        .toList()
                                : outcome.rerankedCandidates().stream()
                                        .map(candidate -> candidate.candidate())
                                        .toList();
                        long rerankerFusionElapsedNanos = System.nanoTime() - fusionStartedAt;
                        rerankerFusionNanos.add(rerankerFusionElapsedNanos);
                        totalElapsedNanos += rerankerFusionElapsedNanos;
                        printRerankerTrace(question, outcome);
                    }
                    else {
                        SearchEvaluationDenseSparseRrfProfile.Outcome outcome =
                                denseSparseRrfProfile.apply(
                                        question.query(),
                                        productCandidates,
                                        sparseCandidates);
                        decision = outcome.decision();
                        evaluatedCandidates = outcome.fusedCandidates().stream()
                                .map(candidate -> candidate.candidate())
                                .toList();
                        long sparseFusionElapsedNanos = System.nanoTime() - fusionStartedAt;
                        sparseFusionNanos.add(sparseFusionElapsedNanos);
                        totalElapsedNanos += sparseFusionElapsedNanos;
                        printSparseTrace(question, sparseCandidates, outcome);
                    }
                }
                else {
                    decision = applyEvaluationProfile(
                            runProfile, question.query(), productCandidates);
                }
                returnedResults = decision.results();
                if (decision.rejected()) {
                    System.out.printf(
                            Locale.ROOT,
                            "- profile rejection %s: %s%n",
                            question.questionId(),
                            decision.rejectionReasons());
                }
            }

            List<CandidateResult> candidates = labelCandidates(
                    question,
                    evaluatedCandidates,
                    seededCorpus.chunkDescriptors());
            List<Long> returnedIds = returnedResults.stream()
                    .map(VectorSearchResult::chunkId)
                    .toList();
            Map<Long, CandidateResult> candidatesById = candidates.stream()
                    .collect(java.util.stream.Collectors.toMap(CandidateResult::chunkId, candidate -> candidate));
            List<CandidateResult> returnedCandidates = returnedIds.stream()
                    .map(candidatesById::get)
                    .toList();
            Double top1Score = returnedResults.isEmpty() ? null : returnedResults.get(0).score();
            Double top1Distance = returnedResults.isEmpty() ? null : returnedResults.get(0).distance();
            long totalElapsedMillis = elapsedMillis(totalElapsedNanos);
            long dbElapsedMillis = elapsedMillis(dbElapsedNanos);
            results.add(new QuestionResult(
                    question.questionId(),
                    question.query(),
                    question.noEvidence(),
                    question.split(),
                    question.category(),
                    question.expectedEvidence(),
                    returnedIds,
                    returnedCandidates.stream().map(CandidateResult::relevance).toList(),
                    top1Score,
                    top1Distance,
                    hasDuplicateEvidence(returnedCandidates),
                    totalElapsedMillis,
                    embeddingElapsedMillis,
                    dbElapsedMillis,
                    searchState(question, returnedResults),
                    question.goldPage(),
                    candidates));
        }
        return List.copyOf(results);
    }

    private CompositeSearchProfile.Decision applyEvaluationProfile(
            RunProfile runProfile,
            String query,
            List<VectorSearchResult> productCandidates) {
        if (runProfile.profile().profileId().equals(SearchEvaluationCompositeProfile.PROFILE_ID)) {
            return new SearchEvaluationCompositeProfile().apply(query, productCandidates);
        }
        if (runProfile.profile().profileId().equals(SearchEvaluationDeferredPageDedupProfile.PROFILE_ID)) {
            return new SearchEvaluationDeferredPageDedupProfile().apply(query, productCandidates);
        }
        if (runProfile.profile().profileId().equals(SearchEvaluationShortQueryRescueProfile.PROFILE_ID)) {
            return new SearchEvaluationShortQueryRescueProfile().apply(query, productCandidates);
        }
        throw new SearchEvaluationDataException("Unsupported evaluation profile.");
    }

    private static boolean isHybridRrf(RunProfile runProfile) {
        return runProfile.profile().profileId().equals(SearchEvaluationHybridRrfProfile.PROFILE_ID);
    }

    private static boolean isDenseSparseRrf(RunProfile runProfile) {
        return runProfile.profile().profileId().equals(SearchEvaluationDenseSparseRrfProfile.PROFILE_ID);
    }

    private static boolean isBgeReranker(RunProfile runProfile) {
        return runProfile.profile().profileId().equals(
                SearchEvaluationDenseSparseRerankerProfile.PROFILE_ID);
    }

    private static boolean usesDenseSparseCandidates(RunProfile runProfile) {
        return isDenseSparseRrf(runProfile) || isBgeReranker(runProfile);
    }

    private List<SparseCandidate> mapSparseCandidates(
            SparseQuestion sparseQuestion,
            List<VectorSearchResult> allDenseCandidates,
            Map<Long, ChunkDescriptor> descriptors) {
        if (sparseQuestion == null) {
            throw new SearchEvaluationDataException("P14 sparse question is missing.");
        }
        Map<String, VectorSearchResult> candidatesByFixtureId = new HashMap<>();
        for (VectorSearchResult candidate : allDenseCandidates) {
            ChunkDescriptor descriptor = descriptors.get(candidate.chunkId());
            if (descriptor == null) {
                throw new SearchEvaluationDataException(
                        "P14 dense candidate is missing its fixture descriptor.");
            }
            candidatesByFixtureId.put(descriptor.fixtureChunkId(), candidate);
        }

        List<SparseCandidate> candidates = new ArrayList<>();
        for (SparseRank sparseRank : sparseQuestion.candidates()) {
            VectorSearchResult candidate = candidatesByFixtureId.get(sparseRank.fixtureChunkId());
            if (candidate == null) {
                throw new SearchEvaluationDataException(
                        "P14 sparse candidate is outside the evaluated dense raw corpus: "
                                + sparseRank.fixtureChunkId());
            }
            candidates.add(new SparseCandidate(candidate, sparseRank.sparseScore()));
        }
        return List.copyOf(candidates);
    }

    private List<RerankerCandidate> mapRerankerCandidates(
            RerankerQuestion rerankerQuestion,
            List<VectorSearchResult> allDenseCandidates,
            Map<Long, ChunkDescriptor> descriptors) {
        if (rerankerQuestion == null) {
            throw new SearchEvaluationDataException("P15 reranker question is missing.");
        }
        Map<String, VectorSearchResult> candidatesByFixtureId = new HashMap<>();
        for (VectorSearchResult candidate : allDenseCandidates) {
            ChunkDescriptor descriptor = descriptors.get(candidate.chunkId());
            if (descriptor == null) {
                throw new SearchEvaluationDataException(
                        "P15 dense candidate is missing its fixture descriptor.");
            }
            candidatesByFixtureId.put(descriptor.fixtureChunkId(), candidate);
        }

        List<RerankerCandidate> candidates = new ArrayList<>();
        for (RerankerRank rerankerRank : rerankerQuestion.candidates()) {
            VectorSearchResult candidate = candidatesByFixtureId.get(rerankerRank.fixtureChunkId());
            if (candidate == null) {
                throw new SearchEvaluationDataException(
                        "P15 reranker candidate is outside the evaluated P14 raw corpus: "
                                + rerankerRank.fixtureChunkId());
            }
            candidates.add(new RerankerCandidate(
                    candidate,
                    rerankerRank.p14Rank(),
                    rerankerRank.rerankerRank(),
                    rerankerRank.rerankerScore()));
        }
        return List.copyOf(candidates);
    }

    private void printHybridTrace(
            Question question,
            List<LexicalCandidate> lexicalCandidates,
            Outcome outcome) {
        List<String> lexicalTrace = new ArrayList<>();
        for (int index = 0; index < lexicalCandidates.size(); index++) {
            LexicalCandidate candidate = lexicalCandidates.get(index);
            lexicalTrace.add(String.format(
                    Locale.ROOT,
                    "%d:%d:%.6f",
                    index + 1,
                    candidate.candidate().chunkId(),
                    candidate.lexicalScore()));
        }
        List<String> fusionTrace = outcome.fusedCandidates().stream()
                .map(candidate -> String.format(
                        Locale.ROOT,
                        "%d:d%s:l%s:%.8f",
                        candidate.candidate().chunkId(),
                        candidate.denseRank(),
                        candidate.lexicalRank(),
                        candidate.rrfScore()))
                .toList();
        System.out.printf(
                Locale.ROOT,
                "- hybrid trace %s | lexical=%s | fused=%s%n",
                question.questionId(),
                lexicalTrace,
                fusionTrace);
    }

    private void printSparseTrace(
            Question question,
            List<SparseCandidate> sparseCandidates,
            SearchEvaluationDenseSparseRrfProfile.Outcome outcome) {
        List<String> sparseTrace = new ArrayList<>();
        for (int index = 0; index < sparseCandidates.size(); index++) {
            SparseCandidate candidate = sparseCandidates.get(index);
            sparseTrace.add(String.format(
                    Locale.ROOT,
                    "%d:%d:%.6f",
                    index + 1,
                    candidate.candidate().chunkId(),
                    candidate.sparseScore()));
        }
        List<String> fusionTrace = outcome.fusedCandidates().stream()
                .map(candidate -> String.format(
                        Locale.ROOT,
                        "%d:d%s:s%s:%.8f",
                        candidate.candidate().chunkId(),
                        candidate.denseRank(),
                        candidate.sparseRank(),
                        candidate.rrfScore()))
                .toList();
        System.out.printf(
                Locale.ROOT,
                "- sparse hybrid trace %s | sparse=%s | fused=%s%n",
                question.questionId(),
                sparseTrace,
                fusionTrace);
    }

    private void printRerankerTrace(
            Question question,
            RerankerOutcome outcome) {
        List<String> trace = outcome.rerankedCandidates().stream()
                .map(candidate -> String.format(
                        Locale.ROOT,
                        "%d:p14=%d:rerank=%d:%.6f",
                        candidate.candidate().chunkId(),
                        candidate.p14Rank(),
                        candidate.rerankerRank(),
                        candidate.rerankerScore()))
                .toList();
        System.out.printf(
                Locale.ROOT,
                "- BGE reranker trace %s | candidates=%s%n",
                question.questionId(),
                trace);
    }

    private List<CandidateResult> labelCandidates(
            Question question,
            List<VectorSearchResult> results,
            Map<Long, ChunkDescriptor> descriptors) {
        Map<String, ExpectedEvidence> expectedById = new HashMap<>();
        for (ExpectedEvidence evidence : question.expectedEvidence()) {
            expectedById.put(evidence.fixtureEvidenceId(), evidence);
        }

        List<CandidateResult> labelled = new ArrayList<>();
        for (int index = 0; index < results.size(); index++) {
            VectorSearchResult result = results.get(index);
            ChunkDescriptor descriptor = descriptors.get(result.chunkId());
            if (descriptor == null) {
                throw new IllegalStateException("Search returned a chunk outside the synthetic evaluation corpus.");
            }
            ExpectedEvidence label = descriptor.fixtureEvidenceIds().stream()
                    .map(expectedById::get)
                    .filter(java.util.Objects::nonNull)
                    .max(Comparator.comparingInt(ExpectedEvidence::relevance)
                            .thenComparing(ExpectedEvidence::fixtureEvidenceId))
                    .orElse(null);
            int relevance = label == null ? 0 : label.relevance();
            String group = label == null
                    ? "unlabelled:" + descriptor.fixtureChunkId()
                    : label.evidenceGroupId();
            labelled.add(new CandidateResult(
                    index + 1,
                    result.chunkId(),
                    descriptor.fixtureChunkId(),
                    descriptor.fixtureEvidenceIds(),
                    result.sourceType(),
                    result.sourceIndex(),
                    relevance,
                    group,
                    result.score(),
                    result.distance()));
        }
        return List.copyOf(labelled);
    }

    private boolean hasDuplicateEvidence(List<CandidateResult> candidates) {
        Set<String> groups = new HashSet<>();
        return candidates.stream().anyMatch(candidate -> !groups.add(candidate.evidenceGroupId()));
    }

    private SearchState searchState(
            Question question,
            List<?> returnedResults) {
        if (!returnedResults.isEmpty()) {
            return SearchState.EVIDENCE_FOUND;
        }
        return question.category() == Category.NO_SEARCHABLE_DOCUMENTS
                ? SearchState.NO_SEARCHABLE_DOCUMENTS
                : SearchState.NO_EVIDENCE;
    }

    private void validateExpectedEvidence(Dataset dataset, Set<String> fixtureEvidenceIds) {
        Set<String> declaredAnchorIds = dataset.corpus().documents().stream()
                .flatMap(document -> document.evidenceAnchors().stream())
                .map(EvidenceAnchor::fixtureEvidenceId)
                .collect(java.util.stream.Collectors.toSet());
        for (Question question : dataset.questions()) {
            for (ExpectedEvidence evidence : question.expectedEvidence()) {
                if (!declaredAnchorIds.contains(evidence.fixtureEvidenceId())) {
                    throw new SearchEvaluationDataException("Question references an unknown fixture evidence ID.");
                }
                if (!fixtureEvidenceIds.contains(evidence.fixtureEvidenceId())) {
                    throw new SearchEvaluationDataException(
                            "Fixture evidence anchor was not preserved by the current chunking configuration.");
                }
            }
        }
    }

    private String contentHash(FixtureDocument document) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (FixturePage page : document.pages()) {
                digest.update(page.text().getBytes(StandardCharsets.UTF_8));
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available.", exception);
        }
    }

    private void printReport(Breakdown breakdown, List<QuestionResult> questions, ReportFiles files) {
        printSummary("전체", breakdown.overall());
        breakdown.splits().forEach((split, summary) -> printSummary("split=" + split, summary));
        breakdown.categories().forEach((category, summary) -> printSummary("category=" + category, summary));
        for (QuestionResult question : questions) {
            System.out.printf(Locale.ROOT,
                    "- %s | %s/%s | chunks=%s | relevance=%s | top1Score=%s | top1Distance=%s | duplicate=%s | total=%dms | embedding=%dms | db=%dms%n",
                    question.questionId(),
                    question.split(),
                    question.category(),
                    question.returnedChunkIds(),
                    question.relevanceOrder(),
                    question.top1Score(),
                    question.top1Distance(),
                    question.duplicateEvidence(),
                    question.searchTimeMillis(),
                    question.embeddingTimeMillis(),
                    question.dbSearchTimeMillis());
        }
        System.out.println("결과 JSON 파일: " + files.report().getFileName());
        System.out.println("후보 CSV 파일: " + files.rawCandidates().getFileName());
    }

    private void printHybridCost() {
        if (lexicalDbNanos.isEmpty()) {
            return;
        }
        System.out.printf(
                Locale.ROOT,
                "Hybrid additional cost | lexical DB avg/p50/p95=%.3f/%.3f/%.3fms"
                        + " | fusion avg/p50/p95=%.3f/%.3f/%.3fms | extra DB lookups=%d%n",
                averageMillis(lexicalDbNanos),
                percentileMillis(lexicalDbNanos, 0.50d),
                percentileMillis(lexicalDbNanos, 0.95d),
                averageMillis(fusionNanos),
                percentileMillis(fusionNanos, 0.50d),
                percentileMillis(fusionNanos, 0.95d),
                lexicalDbNanos.size());
    }

    private void printSparseCost() {
        if (sparseRun == null || sparseQueryNanos.isEmpty()) {
            return;
        }
        if (sparseFusionNanos.isEmpty()) {
            System.out.printf(
                    Locale.ROOT,
                    "BGE-M3 sparse cost | model=%s@%s | FlagEmbedding=%s | device=%s"
                            + " | load=%.3fms | corpus=%.3fms | warmup=%.3fms"
                            + " | query+score avg/p50/p95=%.3f/%.3f/%.3fms"
                            + " | peakGPU=%d bytes%n",
                    sparseRun.model(),
                    sparseRun.modelRevision(),
                    sparseRun.flagEmbeddingVersion(),
                    sparseRun.device(),
                    sparseRun.modelLoadMillis(),
                    sparseRun.corpusEncodingMillis(),
                    sparseRun.warmupMillis(),
                    averageMillis(sparseQueryNanos),
                    percentileMillis(sparseQueryNanos, 0.50d),
                    percentileMillis(sparseQueryNanos, 0.95d),
                    sparseRun.gpuPeakMemoryBytes());
            return;
        }
        System.out.printf(
                Locale.ROOT,
                "BGE-M3 sparse cost | model=%s@%s | FlagEmbedding=%s | device=%s"
                        + " | load=%.3fms | corpus=%.3fms | warmup=%.3fms"
                        + " | query+score avg/p50/p95=%.3f/%.3f/%.3fms"
                        + " | fusion avg/p50/p95=%.3f/%.3f/%.3fms | peakGPU=%d bytes%n",
                sparseRun.model(),
                sparseRun.modelRevision(),
                sparseRun.flagEmbeddingVersion(),
                sparseRun.device(),
                sparseRun.modelLoadMillis(),
                sparseRun.corpusEncodingMillis(),
                sparseRun.warmupMillis(),
                averageMillis(sparseQueryNanos),
                percentileMillis(sparseQueryNanos, 0.50d),
                percentileMillis(sparseQueryNanos, 0.95d),
                averageMillis(sparseFusionNanos),
                percentileMillis(sparseFusionNanos, 0.50d),
                percentileMillis(sparseFusionNanos, 0.95d),
                sparseRun.gpuPeakMemoryBytes());
    }

    private void printRerankerCost() {
        if (rerankerRun == null || rerankerInferenceNanos.isEmpty()) {
            return;
        }
        System.out.printf(
                Locale.ROOT,
                "BGE reranker cost | model=%s@%s | runtime=%s@%s | device=%s"
                        + " | load=%.3fms | warmup=%.3fms | maxPairTokens=%d"
                        + " | inference avg/p50/p95=%.3f/%.3f/%.3fms"
                        + " | P15 profile avg/p50/p95=%.3f/%.3f/%.3fms"
                        + " | gpuModel=%d/%d bytes | gpuPeak=%d/%d bytes"
                        + " | rss before/after/peak=%d/%d/%d bytes%n",
                rerankerRun.model(),
                rerankerRun.modelRevision(),
                rerankerRun.inferenceLibrary(),
                rerankerRun.inferenceLibraryVersion(),
                rerankerRun.device(),
                rerankerRun.modelLoadMillis(),
                rerankerRun.warmupMillis(),
                rerankerRun.maximumPairTokens(),
                averageMillis(rerankerInferenceNanos),
                percentileMillis(rerankerInferenceNanos, 0.50d),
                percentileMillis(rerankerInferenceNanos, 0.95d),
                averageMillis(rerankerFusionNanos),
                percentileMillis(rerankerFusionNanos, 0.50d),
                percentileMillis(rerankerFusionNanos, 0.95d),
                rerankerRun.gpuModelAllocatedBytes(),
                rerankerRun.gpuModelReservedBytes(),
                rerankerRun.gpuPeakAllocatedBytes(),
                rerankerRun.gpuPeakReservedBytes(),
                rerankerRun.processRssBeforeLoadBytes(),
                rerankerRun.processRssAfterLoadBytes(),
                rerankerRun.processRssPeakBytes());
    }

    private void printCandidateLimitAnalysis(List<QuestionResult> questions) {
        SearchEvaluationMetrics metrics = new SearchEvaluationMetrics();
        for (int candidateLimit : CANDIDATE_LIMITS) {
            List<Long> dbTimes = candidateDbTimes.getOrDefault(candidateLimit, List.of());
            List<QuestionResult> projected = new ArrayList<>();
            for (int index = 0; index < questions.size(); index++) {
                QuestionResult question = questions.get(index);
                List<CandidateResult> candidates = question.candidates().stream()
                        .limit(candidateLimit)
                        .toList();
                List<CandidateResult> returned = candidates.stream().limit(5).toList();
                long dbMillis = index < dbTimes.size() ? dbTimes.get(index) : 0L;
                projected.add(new QuestionResult(
                        question.questionId(),
                        question.query(),
                        question.noEvidence(),
                        question.split(),
                        question.category(),
                        question.expectedEvidence(),
                        returned.stream().map(CandidateResult::chunkId).toList(),
                        returned.stream().map(CandidateResult::relevance).toList(),
                        returned.isEmpty() ? null : returned.get(0).score(),
                        returned.isEmpty() ? null : returned.get(0).distance(),
                        hasDuplicateEvidence(returned),
                        question.searchTimeMillis(),
                        question.embeddingTimeMillis(),
                        dbMillis,
                        returned.isEmpty()
                                ? question.category() == Category.NO_SEARCHABLE_DOCUMENTS
                                        ? SearchState.NO_SEARCHABLE_DOCUMENTS
                                        : SearchState.NO_EVIDENCE
                                : SearchState.EVIDENCE_FOUND,
                        question.goldPage(),
                        candidates));
            }
            Summary summary = metrics.calculate(projected);
            System.out.printf(Locale.ROOT,
                    "후보 수 %d | Recall@20=%.4f | Direct MRR@20=%.4f | nDCG@5=%.4f | duplicate=%.4f | DB p50/p95=%dms/%dms%n",
                    candidateLimit,
                    summary.recallAt20(),
                    summary.directMrrAt20(),
                    summary.ndcgAt5(),
                    summary.duplicateResultRatio(),
                    summary.dbSearchLatency().p50Millis(),
                    summary.dbSearchLatency().p95Millis());
        }
    }

    private void printSummary(String label, Summary summary) {
        System.out.printf(Locale.ROOT, "%nPRIZM Dense 검색 기준선 [%s] (%d개 질문)%n",
                label, summary.questionCount());
        System.out.printf(Locale.ROOT, "Recall@20: %.4f (direct %.4f)%n",
                summary.recallAt20(), summary.directRecallAt20());
        System.out.printf(Locale.ROOT, "Precision@5: %.4f (direct %.4f)%n",
                summary.precisionAt5(), summary.directPrecisionAt5());
        System.out.printf(Locale.ROOT, "Direct MRR@5/@20: %.4f/%.4f%n",
                summary.directMrrAt5(), summary.directMrrAt20());
        System.out.printf(Locale.ROOT, "nDCG@5: %.4f%n", summary.ndcgAt5());
        System.out.printf(Locale.ROOT, "중복 결과 비율: %.4f%n", summary.duplicateResultRatio());
        System.out.printf(Locale.ROOT, "검색 지연 total p50/p95: %dms/%dms%n",
                summary.totalLatency().p50Millis(), summary.totalLatency().p95Millis());
        System.out.printf(Locale.ROOT, "검색 지연 embedding p50/p95: %dms/%dms%n",
                summary.embeddingLatency().p50Millis(), summary.embeddingLatency().p95Millis());
        System.out.printf(Locale.ROOT, "검색 지연 DB p50/p95: %dms/%dms%n",
                summary.dbSearchLatency().p50Millis(), summary.dbSearchLatency().p95Millis());
        System.out.printf(Locale.ROOT, "무근거 거부/근거 오거부: %.4f/%.4f%n",
                summary.decisionMetrics().noEvidenceRejectionRate(),
                summary.decisionMetrics().falseRejectionRate());
        System.out.printf(Locale.ROOT, "근거/무근거 질문 수: %d/%d%n",
                summary.evidenceScoreDistribution().questionCount(),
                summary.noEvidenceScoreDistribution().questionCount());
        printScoreDistribution("근거", summary.evidenceScoreDistribution());
        printScoreDistribution("무근거", summary.noEvidenceScoreDistribution());
    }

    private void printScoreDistribution(String label, ScoreDistribution distribution) {
        if (distribution.questionCount() == 0) {
            return;
        }
        System.out.printf(Locale.ROOT, "%s top1 score min/avg/max: %.4f/%.4f/%.4f%n",
                label,
                distribution.minimumTop1Score(),
                distribution.averageTop1Score(),
                distribution.maximumTop1Score());
    }

    private long elapsedMillisSince(long startedAt) {
        return elapsedMillis(System.nanoTime() - startedAt);
    }

    private static long elapsedMillis(long elapsedNanos) {
        return Math.round(elapsedNanos / 1_000_000.0d);
    }

    private static double averageMillis(List<Long> elapsedNanos) {
        return elapsedNanos.stream()
                .mapToLong(Long::longValue)
                .average()
                .orElse(0.0d) / 1_000_000.0d;
    }

    private static double percentileMillis(List<Long> elapsedNanos, double percentile) {
        if (elapsedNanos.isEmpty()) {
            return 0.0d;
        }
        List<Long> sorted = elapsedNanos.stream().sorted().toList();
        int index = Math.max(
                0,
                Math.min(
                        sorted.size() - 1,
                        (int) Math.ceil(percentile * sorted.size()) - 1));
        return sorted.get(index) / 1_000_000.0d;
    }

    private static Path createTemporaryStorageRoot() {
        try {
            return Files.createTempDirectory("prizm-search-evaluation-storage-");
        }
        catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record PreparedDocument(
            List<IndexedChunk> chunks,
            Map<Integer, List<String>> evidenceByChunkNo) {
    }

    private record SeededDocument(Long versionId) {
    }

    private record SeededCorpus(
            Long primaryOwnerUserId,
            Long noSearchableDocumentsUserId,
            Map<Long, ChunkDescriptor> chunkDescriptors,
            Set<String> fixtureEvidenceIds) {
    }

    private record RunProfile(EvaluationProfile profile, boolean currentProduct) {
    }

    private record FixtureScenario(Long ownerUserId, VersionScenario versionScenario) {
    }
}
