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

    private static final int CANDIDATE_LIMIT = 20;
    private static final List<Integer> CANDIDATE_LIMITS = List.of(5, 10, CANDIDATE_LIMIT);
    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");
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
        SeededCorpus seededCorpus = seedCorpus(dataset);
        validateExpectedEvidence(dataset, seededCorpus.fixtureEvidenceIds());

        List<QuestionResult> questionResults = evaluate(dataset, seededCorpus, runProfile);
        Breakdown breakdown = new SearchEvaluationMetrics().calculateBreakdown(questionResults);
        Report report = new Report(
                Instant.now().toString(),
                dataset.corpus().datasetId(),
                runProfile.profile(),
                breakdown,
                questionResults);
        ReportFiles files = new SearchEvaluationReportWriter(objectMapper).write(outputPath, report);

        printReport(breakdown, questionResults, files);
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
        if (versionTwo && selectedSplit != Split.TUNING) {
            throw new SearchEvaluationDataException("This Batch permits Dataset v2 TUNING execution only.");
        }
        return selector.select(loadedDataset, selectedSplit);
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
            List<TextChunk> pageChunks = textChunker.split(page.text());
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
            RunProfile runProfile) {
        SearchEvaluationCandidateRepository candidateRepository =
                new SearchEvaluationCandidateRepository(jdbcTemplate);
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
            long embeddingElapsedMillis = elapsedMillisSince(embeddingStartedAt);
            long dbStartedAt = System.nanoTime();
            List<VectorSearchResult> top20 = candidateRepository.findCandidates(
                    queryOwnerUserId,
                    queryEmbedding,
                    CANDIDATE_LIMIT);
            long dbElapsedMillis = elapsedMillisSince(dbStartedAt);
            long totalElapsedMillis = elapsedMillisSince(totalStartedAt);
            candidateDbTimes.computeIfAbsent(CANDIDATE_LIMIT, ignored -> new ArrayList<>())
                    .add(dbElapsedMillis);

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
            List<Long> candidatePrefix = top20.stream()
                    .limit(productionIds.size())
                    .map(VectorSearchResult::chunkId)
                    .toList();
            if (!productionIds.equals(candidatePrefix)) {
                throw new IllegalStateException("Evaluation candidate SQL diverged from the production top-five order.");
            }

            List<VectorSearchResult> evaluatedCandidates = top20;
            List<VectorSearchResult> returnedResults = top20.stream()
                    .limit(productionIds.size())
                    .toList();
            if (!runProfile.currentProduct()) {
                SearchEvaluationCompositeProfile.Decision decision =
                        new SearchEvaluationCompositeProfile().apply(question.query(), top20);
                evaluatedCandidates = decision.candidates();
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
        return Math.round((System.nanoTime() - startedAt) / 1_000_000.0d);
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
