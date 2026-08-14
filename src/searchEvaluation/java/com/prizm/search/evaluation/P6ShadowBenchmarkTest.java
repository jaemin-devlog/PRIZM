package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.dto.response.CareerEvidenceSearchV2Response;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.profile.NaturalLanguageQueryFallback;
import com.prizm.search.profile.NumericAnchorRescueProfile;
import com.prizm.search.profile.NumericQueryAnchors;
import com.prizm.search.profile.SearchIntent;
import com.prizm.search.profile.ShortGeneralExactTokenRescueProfile;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.repository.VectorSearchResult;
import com.prizm.search.service.EvidenceExpansionService;
import com.prizm.search.service.EvidencePresentation;
import com.prizm.search.service.SearchService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * PRZ-013 P6 evaluation-only runner against the already-running external PostgreSQL corpus.
 * It never seeds, updates, migrates, or deletes application data.
 */
@ActiveProfiles("local")
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
            "spring.flyway.enabled=false",
            "prizm.change-log.scheduler.enabled=false",
            "prizm.ingestion.worker-enabled=false",
            "prizm.cleanup.worker-enabled=false"
        })
class P6ShadowBenchmarkTest {

    private static final Long OWNER_ID = 1L;
    private static final int TOP_20 = 20;
    private static final int FINAL_LIMIT = 5;
    private static final String EXPECTED_PRODUCTION_SEARCH_HASH =
            "32d8e31d7f5eeb3bf64033ce2b9db7c58347cbfe3d6f540b792e44c04951df31";
    private static final int EXPECTED_PRODUCTION_SEARCH_FILES = 30;
    private static final Path DEVELOPMENT_DATASET = Path.of(
            "specs/PRZ-013-search-performance-v2/p0-benchmark/evaluation-dataset.json");
    private static final Path P5_DATASET = Path.of(
            "specs/PRZ-013-search-performance-v2/p5-final-holdout/holdout-dataset.json");
    private static final Path P5_GROUND_TRUTH = Path.of(
            "specs/PRZ-013-search-performance-v2/p5-final-holdout/holdout-ground-truth.json");
    private static final Path STRESS_DATASET = Path.of(
            "specs/PRZ-013-search-performance-v2/p6-retrieval-shadow/identifier-stress-dataset.json");
    private static final Path STRESS_GROUND_TRUTH = Path.of(
            "specs/PRZ-013-search-performance-v2/p6-retrieval-shadow/identifier-stress-ground-truth.json");
    private static final Path REGRESSION_GUARDS = Path.of(
            "specs/PRZ-013-search-performance-v2/p6-retrieval-shadow/regression-guards.json");
    private static final String FROZEN_STRESS_DATASET_HASH =
            "0dfdd5aa5d51fb8f5116904ef4f998b5f4ce73e35a5657159f35d80fc15859f5";
    private static final String FROZEN_STRESS_GROUND_TRUTH_HASH =
            "f356a3ce914343caf49ed8edc4131cc29c933599708cc390c0eec3d9f8b218d8";
    private static final Path PRODUCTION_SEARCH_ROOT = Path.of(
            "src/main/java/com/prizm/search");

    @Autowired
    JdbcTemplate jdbcTemplate;

    @Autowired
    SearchService searchService;

    @Autowired
    EmbeddingService embeddingService;

    @Autowired
    EmbeddingValidator embeddingValidator;

    @Autowired
    VectorSearchRepository vectorSearchRepository;

    @Autowired
    CompositeSearchProfile compositeSearchProfile;

    @Autowired
    EvidenceExpansionService evidenceExpansionService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<Long> denseQ0Nanos = new ArrayList<>();
    private final List<Long> lexicalQ0Nanos = new ArrayList<>();
    private final List<Long> hybridQ0Nanos = new ArrayList<>();
    private final Map<Mode, List<Long>> endToEndNanos = new LinkedHashMap<>();
    private final List<Double> h2GateQ0Millis = new ArrayList<>();
    private SearchEvaluationLiteralEvidenceGate literalEvidenceGate;

    @Test
    void runsP6AExternalCorpusShadowBenchmark() throws Exception {
        boolean h2Enabled = System.getProperty("prizm.p6.phase", "B").equalsIgnoreCase("B");
        if (h2Enabled) {
            assertFrozenStressInputs();
        }
        ProductionHash productionBefore = hashProductionSearch();
        assertThat(productionBefore.fileCount()).isEqualTo(EXPECTED_PRODUCTION_SEARCH_FILES);
        assertThat(productionBefore.aggregate()).isEqualTo(EXPECTED_PRODUCTION_SEARCH_HASH);
        CorpusSnapshot corpus = validateExternalCorpus();
        validateIsolationContracts(corpus);

        List<DatasetRun> datasetRuns = new ArrayList<>();
        datasetRuns.add(evaluateDataset(loadDevelopmentDataset(), "development-72", h2Enabled));
        datasetRuns.add(evaluateDataset(loadP5Dataset(), "p5-diagnostic-48", h2Enabled));
        if (h2Enabled) {
            datasetRuns.add(evaluateDataset(loadStressDataset(), "identifier-stress-28", true));
            datasetRuns.add(evaluateDataset(loadRegressionGuards(), "regression-guards-17", true));
        }

        ProductionHash productionAfter = hashProductionSearch();
        assertThat(productionAfter).isEqualTo(productionBefore);
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("phase", h2Enabled ? "PRZ-013-P6-B" : "PRZ-013-P6-A");
        report.put("executedAt", Instant.now().toString());
        report.put("environment", Map.of(
                "database", "external PostgreSQL+pgvector on configured local datasource",
                "embedding", "external Ollama bge-m3",
                "ownerId", OWNER_ID,
                "databaseMutation", 0,
                "productionSearchMutation", 0));
        report.put("productionSearchBefore", productionBefore);
        report.put("productionSearchAfter", productionAfter);
        report.put("corpus", corpus);
        report.put("latency", latencyReport());
        report.put("datasets", datasetRuns);

        Path output = outputDirectory().resolve(h2Enabled ? "p6-b-results.json" : "p6-a-results.json");
        Files.createDirectories(output.getParent());
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(output.toFile(), report);
        System.out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                datasetRuns.stream().collect(Collectors.toMap(
                        DatasetRun::dataset,
                        DatasetRun::summary,
                        (left, right) -> left,
                        LinkedHashMap::new))));
    }

    private DatasetRun evaluateDataset(
            List<EvaluationQuery> queries,
            String datasetName,
            boolean h2Enabled) {
        List<QueryRun> results = new ArrayList<>();
        int index = 0;
        for (EvaluationQuery query : queries) {
            QueryRun result = evaluateQuery(query, h2Enabled);
            results.add(result);
            index++;
            System.out.printf(
                    Locale.ROOT,
                    "%s %s %03d/%03d %s D0=%s L1=%s H1=%s H2=%s%n",
                    h2Enabled ? "P6-B" : "P6-A",
                    datasetName,
                    index,
                    queries.size(),
                    query.id(),
                    result.modeResults().get(Mode.D0).correctRank(),
                    result.modeResults().get(Mode.L1).correctRank(),
                    result.modeResults().get(Mode.H1).correctRank(),
                    h2Enabled ? result.modeResults().get(Mode.H2).correctRank() : "NOT_RUN");
        }
        return new DatasetRun(datasetName, summarize(results), List.copyOf(results));
    }

    private QueryRun evaluateQuery(EvaluationQuery query, boolean h2Enabled) {
        long embeddingStarted = System.nanoTime();
        float[] q0Embedding = embedding(query.query());
        long embeddingNanos = System.nanoTime() - embeddingStarted;

        long denseStarted = System.nanoTime();
        List<VectorSearchResult> dense = candidateRepository().findCandidates(
                OWNER_ID, q0Embedding, TOP_20);
        long denseNanos = System.nanoTime() - denseStarted;
        denseQ0Nanos.add(denseNanos);

        long lexicalStarted = System.nanoTime();
        List<SearchEvaluationLexicalCandidateRepository.LexicalCandidate> lexical =
                lexicalRepository().findCandidates(OWNER_ID, query.query(), q0Embedding, TOP_20);
        long lexicalNanos = System.nanoTime() - lexicalStarted;
        lexicalQ0Nanos.add(lexicalNanos);

        long hybridStarted = System.nanoTime();
        List<SearchEvaluationHybridRrfProfile.FusedCandidate> fused =
                SearchEvaluationHybridRrfProfile.fuse(dense, lexical);
        long hybridNanos = System.nanoTime() - hybridStarted;
        hybridQ0Nanos.add(hybridNanos);

        Set<Long> denseIds = ids(dense);
        Set<Long> lexicalIds = ids(lexical.stream()
                .map(SearchEvaluationLexicalCandidateRepository.LexicalCandidate::candidate)
                .toList());
        List<VectorSearchResult> hybridTop20 = fused.stream()
                .limit(TOP_20)
                .map(SearchEvaluationHybridRrfProfile.FusedCandidate::candidate)
                .toList();
        List<SearchEvaluationLiteralEvidenceGate.GateDecision> h2Q0Decisions = h2Enabled
                ? hybridTop20.stream().map(candidate -> gate().evaluate(OWNER_ID, query.query(), candidate)).toList()
                : List.of();
        h2Q0Decisions.stream().map(SearchEvaluationLiteralEvidenceGate.GateDecision::evaluationMs)
                .forEach(h2GateQ0Millis::add);
        List<VectorSearchResult> h2Top20 = new ArrayList<>();
        for (int candidateIndex = 0; candidateIndex < hybridTop20.size(); candidateIndex++) {
            if (!h2Enabled || h2Q0Decisions.get(candidateIndex).passed()) {
                h2Top20.add(hybridTop20.get(candidateIndex));
            }
        }
        ChannelMetrics channelMetrics = new ChannelMetrics(
                channelCandidates(dense),
                channelCandidates(lexical.stream()
                        .map(SearchEvaluationLexicalCandidateRepository.LexicalCandidate::candidate)
                        .toList()),
                channelCandidates(hybridTop20),
                channelCandidates(h2Top20),
                difference(denseIds, lexicalIds).size(),
                difference(lexicalIds, denseIds).size(),
                intersection(denseIds, lexicalIds).size(),
                candidateHit(query, dense),
                candidateHit(query, lexical.stream()
                        .map(SearchEvaluationLexicalCandidateRepository.LexicalCandidate::candidate)
                        .toList()),
                candidateHit(query, hybridTop20),
                h2Enabled && candidateHit(query, h2Top20),
                h2Q0Decisions,
                nanosToMillis(embeddingNanos),
                nanosToMillis(denseNanos),
                nanosToMillis(lexicalNanos),
                nanosToMillis(hybridNanos));

        Map<Mode, ModeResult> modeResults = new LinkedHashMap<>();
        for (Mode mode : modes(h2Enabled)) {
            ShadowOutcome shadow;
            List<PresentedCandidate> presented;
            String state;
            long elapsed;
            if (mode == Mode.D0) {
                shadow = runSequential(query.query(), mode, q0Embedding, dense, lexical);
                long started = System.nanoTime();
                CareerEvidenceSearchV2Response production = searchService.searchCareerEvidenceV2(
                        OWNER_ID, query.query());
                elapsed = System.nanoTime() - started;
                assertProductionParity(shadow.results(), production.results());
                presented = production.results().stream()
                        .map(PresentedCandidate::fromProduction)
                        .toList();
                state = production.state().name();
            } else {
                long started = System.nanoTime();
                shadow = runSequential(query.query(), mode, q0Embedding, dense, lexical);
                presented = shadow.results().stream()
                        .map(candidate -> present(query.query(), candidate))
                        .toList();
                elapsed = (System.nanoTime() - started) + embeddingNanos;
                state = presented.isEmpty() ? "NO_RELEVANT_RESULTS" : "EVIDENCE_FOUND";
            }
            endToEndNanos.computeIfAbsent(mode, ignored -> new ArrayList<>()).add(elapsed);
            modeResults.put(mode, modeResult(query, state, presented, shadow, elapsed));
        }
        return new QueryRun(query.id(), query.category(), query.query(), query.positive(),
                channelMetrics, Map.copyOf(modeResults));
    }

    private ShadowOutcome runSequential(
            String originalQuery,
            Mode mode,
            float[] q0Embedding,
            List<VectorSearchResult> q0Dense,
            List<SearchEvaluationLexicalCandidateRepository.LexicalCandidate> q0Lexical) {
        Set<String> guardedIdentifiers = compositeSearchProfile.strongIdentifiersForEvidenceGuard(originalQuery);
        if (!guardedIdentifiers.isEmpty()
                && !vectorSearchRepository.hasAllActiveIdentifiers(OWNER_ID, guardedIdentifiers)) {
            return new ShadowOutcome(List.of(), List.of(new VariantTrace("Q0", originalQuery, false, 0, 0, 0,
                    "GLOBAL_IDENTIFIER_REJECT")), false);
        }

        List<VectorSearchResult> mergedDense = q0Dense;
        List<SearchEvaluationLexicalCandidateRepository.LexicalCandidate> mergedLexical = q0Lexical;
        List<String> anchorQueries = new ArrayList<>(List.of(originalQuery));
        List<VariantTrace> traces = new ArrayList<>();
        List<VectorSearchResult> selected = select(
                mode, originalQuery, originalQuery, anchorQueries, mergedDense, mergedLexical);
        traces.add(trace("Q0", originalQuery, selected, q0Dense, q0Lexical));

        boolean fallbackAllowed = compositeSearchProfile.resolveIntent(originalQuery) == SearchIntent.GENERAL
                || NaturalLanguageQueryFallback.isExperienceRequest(originalQuery);
        if (selected.isEmpty() && fallbackAllowed) {
            List<String> variants = NaturalLanguageQueryFallback.variants(originalQuery).stream()
                    .filter(variant -> NaturalLanguageQueryFallback.preservesRequiredAnchors(
                            originalQuery, variant, guardedIdentifiers))
                    .toList();
            for (int index = 0; index < variants.size(); index++) {
                String variant = variants.get(index);
                float[] embedding = embedding(variant);
                List<VectorSearchResult> dense = candidateRepository().findCandidates(
                        OWNER_ID, embedding, TOP_20);
                List<SearchEvaluationLexicalCandidateRepository.LexicalCandidate> lexical =
                        lexicalRepository().findCandidates(OWNER_ID, variant, embedding, TOP_20);
                mergedDense = mergeDense(mergedDense, dense);
                mergedLexical = mergeLexical(mergedLexical, lexical);
                anchorQueries.add(variant);
                selected = select(
                        mode, originalQuery, variant, anchorQueries, mergedDense, mergedLexical);
                traces.add(trace("Q" + (index + 1), variant, selected, dense, lexical));
                if (!selected.isEmpty()) {
                    break;
                }
            }
        }
        boolean numericRescue = false;
        if (selected.isEmpty() && mode != Mode.L1) {
            Set<String> normalizedNumbers = NumericQueryAnchors.extract(originalQuery).stream()
                    .filter(NumericQueryAnchors.NumericAnchor::hasUnit)
                    .map(NumericQueryAnchors.NumericAnchor::number)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!normalizedNumbers.isEmpty()) {
                List<VectorSearchResult> numericCandidates = vectorSearchRepository.findNumericAnchorCandidates(
                        OWNER_ID, q0Embedding, normalizedNumbers);
                selected = new NumericAnchorRescueProfile(compositeSearchProfile)
                        .apply(originalQuery, numericCandidates);
                if (mode == Mode.H2) {
                    selected = selected.stream()
                            .filter(candidate -> gate().evaluate(OWNER_ID, originalQuery, candidate).passed())
                            .toList();
                }
                numericRescue = !selected.isEmpty();
            }
        }
        return new ShadowOutcome(deduplicate(selected), List.copyOf(traces), numericRescue);
    }

    private List<VectorSearchResult> select(
            Mode mode,
            String literalQuery,
            String searchQuery,
            List<String> anchorQueries,
            List<VectorSearchResult> dense,
            List<SearchEvaluationLexicalCandidateRepository.LexicalCandidate> lexical) {
        List<VectorSearchResult> selected;
        if (mode == Mode.D0) {
            selected = new ShortGeneralExactTokenRescueProfile(compositeSearchProfile)
                    .apply(searchQuery, dense.stream().limit(TOP_20).toList())
                    .results();
        } else if (mode == Mode.L1) {
            selected = new SearchEvaluationHybridRrfProfile()
                    .apply(searchQuery, List.of(), lexical)
                    .decision().results();
        } else {
            selected = new SearchEvaluationHybridRrfProfile()
                    .apply(searchQuery, dense, lexical)
                    .decision().results();
        }
        boolean requireDirectAnchor = NaturalLanguageQueryFallback.requiresDirectAnchor(searchQuery);
        if (requireDirectAnchor) {
            selected = selected.stream()
                    .filter(candidate -> anchorQueries.stream().anyMatch(anchor ->
                            NaturalLanguageQueryFallback.hasDirectAnchor(anchor, candidate.content())))
                    .toList();
        }
        boolean contextualNumeric = NumericQueryAnchors.extract(searchQuery).stream()
                .anyMatch(NumericQueryAnchors.NumericAnchor::hasUnit);
        if (contextualNumeric) {
            selected = selected.stream()
                    .filter(candidate -> NumericQueryAnchors.hasContextualMatch(searchQuery, candidate.content()))
                    .toList();
        }
        if (mode == Mode.H2) {
            selected = selected.stream()
                    .filter(candidate -> gate().evaluate(OWNER_ID, literalQuery, candidate).passed())
                    .toList();
        }
        return selected;
    }

    private VariantTrace trace(
            String variantId,
            String query,
            List<VectorSearchResult> selected,
            List<VectorSearchResult> dense,
            List<SearchEvaluationLexicalCandidateRepository.LexicalCandidate> lexical) {
        return new VariantTrace(
                variantId,
                query,
                !selected.isEmpty(),
                dense.size(),
                lexical.size(),
                selected.size(),
                selected.isEmpty() ? "CONTINUE" : "EARLY_STOP");
    }

    private ModeResult modeResult(
            EvaluationQuery query,
            String state,
            List<PresentedCandidate> results,
            ShadowOutcome shadow,
            long elapsedNanos) {
        Integer correctRank = null;
        if (query.positive()) {
            for (int index = 0; index < results.size(); index++) {
                if (finalHit(query, results.get(index))) {
                    correctRank = index + 1;
                    break;
                }
            }
        }
        boolean falsePositive = !query.positive() && !results.isEmpty();
        return new ModeResult(
                state,
                correctRank,
                falsePositive,
                results.stream().map(PresentedCandidate::diagnostic).toList(),
                shadow.traces(),
                shadow.numericRescue(),
                nanosToMillis(elapsedNanos));
    }

    private Map<String, Object> summarize(List<QueryRun> results) {
        List<QueryRun> positives = results.stream().filter(QueryRun::positive).toList();
        List<QueryRun> negatives = results.stream().filter(result -> !result.positive()).toList();
        Map<String, Object> summary = new LinkedHashMap<>();
        for (Mode mode : results.get(0).modeResults().keySet()) {
            List<ModeResult> modeResults = results.stream()
                    .map(result -> result.modeResults().get(mode))
                    .toList();
            summary.put(mode.name(), rankingMetrics(positives, negatives, modeResults, results, mode));
        }
        summary.put("candidateRecallAt20", Map.of(
                "D0", ratio(positives.stream().filter(result -> result.channelMetrics().denseGroundTruth()).count(), positives.size()),
                "L1", ratio(positives.stream().filter(result -> result.channelMetrics().lexicalGroundTruth()).count(), positives.size()),
                "H1", ratio(positives.stream().filter(result -> result.channelMetrics().hybridGroundTruth()).count(), positives.size()),
                "H2", ratio(positives.stream().filter(result -> result.channelMetrics().h2GroundTruth()).count(), positives.size())));
        summary.put("channelTotals", Map.of(
                "denseOnly", results.stream().mapToInt(result -> result.channelMetrics().denseOnly()).sum(),
                "lexicalOnly", results.stream().mapToInt(result -> result.channelMetrics().lexicalOnly()).sum(),
                "both", results.stream().mapToInt(result -> result.channelMetrics().both()).sum()));
        return Map.copyOf(summary);
    }

    private Map<String, Object> rankingMetrics(
            List<QueryRun> positives,
            List<QueryRun> negatives,
            List<ModeResult> ignored,
            List<QueryRun> all,
            Mode mode) {
        List<Integer> ranks = positives.stream()
                .map(result -> result.modeResults().get(mode).correctRank())
                .toList();
        long top1 = ranks.stream().filter(rank -> rank != null && rank == 1).count();
        long recall3 = ranks.stream().filter(rank -> rank != null && rank <= 3).count();
        long recall5 = ranks.stream().filter(rank -> rank != null && rank <= 5).count();
        double mrr = ranks.stream().mapToDouble(rank -> rank == null || rank > 5 ? 0.0d : 1.0d / rank).sum()
                / positives.size();
        long falsePositives = negatives.stream()
                .filter(result -> result.modeResults().get(mode).falsePositive())
                .count();
        long earlyStops = all.stream()
                .filter(result -> result.modeResults().get(mode).variants().stream()
                        .anyMatch(trace -> trace.selected() && trace.variantId().equals("Q0")))
                .count();
        return Map.of(
                "top1Accuracy", ratio(top1, positives.size()),
                "recallAt3", ratio(recall3, positives.size()),
                "recallAt5", ratio(recall5, positives.size()),
                "mrrAt5", mrr,
                "negativeFalsePositiveRate", ratio(falsePositives, negatives.size()),
                "falsePositiveCount", falsePositives,
                "q0EarlyStopCount", earlyStops);
    }

    private void assertProductionParity(
            List<VectorSearchResult> shadow,
            List<CareerEvidenceSearchResponse> production) {
        assertThat(shadow.stream().map(VectorSearchResult::chunkId).toList())
                .as("evaluation D0 orchestration must preserve production P3 selection")
                .containsExactlyElementsOf(production.stream()
                        .map(CareerEvidenceSearchResponse::chunkId)
                        .toList());
        for (int index = 0; index < production.size(); index++) {
            assertThat(shadow.get(index).score()).isEqualTo(production.get(index).score());
            assertThat(shadow.get(index).distance()).isEqualTo(production.get(index).distance());
            assertThat(production.get(index).score() + production.get(index).distance())
                    .isCloseTo(1.0d, org.assertj.core.data.Offset.offset(1.0e-12));
        }
    }

    private PresentedCandidate present(String query, VectorSearchResult candidate) {
        EvidencePresentation evidence = evidenceExpansionService.select(OWNER_ID, query, candidate);
        return new PresentedCandidate(
                candidate.chunkId(),
                candidate.documentId(),
                candidate.documentVersionId(),
                candidate.documentTitle(),
                candidate.sourceIndex(),
                candidate.content(),
                evidence.snippet(),
                evidence.evidenceChunkId(),
                evidence.evidenceSourceIndex(),
                candidate.score(),
                candidate.distance());
    }

    private boolean candidateHit(EvaluationQuery query, List<VectorSearchResult> candidates) {
        if (!query.positive()) {
            return false;
        }
        return candidates.stream().limit(TOP_20).anyMatch(candidate -> query.expected().stream()
                .anyMatch(expected -> expected.matchesCandidate(candidate)));
    }

    private boolean finalHit(EvaluationQuery query, PresentedCandidate candidate) {
        return query.expected().stream().anyMatch(expected -> expected.matchesPresented(candidate));
    }

    private CorpusSnapshot validateExternalCorpus() {
        List<CorpusDocument> documents = jdbcTemplate.query(
                """
                SELECT document.id, document.title, version.id, version.version_no, count(chunk.id)
                FROM documents document
                JOIN document_versions version ON version.id = document.active_version_id
                JOIN document_chunks chunk ON chunk.document_version_id = version.id
                WHERE document.owner_user_id = ?
                  AND version.owner_user_id = ?
                  AND chunk.owner_user_id = ?
                  AND version.status = 'ACTIVE'
                GROUP BY document.id, document.title, version.id, version.version_no
                ORDER BY document.id
                """,
                (rs, row) -> new CorpusDocument(
                        rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getInt(4), rs.getInt(5)),
                OWNER_ID, OWNER_ID, OWNER_ID);
        assertThat(documents).hasSize(2);
        assertThat(documents.stream().mapToInt(CorpusDocument::chunks).sum()).isEqualTo(18);
        return new CorpusSnapshot(OWNER_ID, documents.size(), 18, List.copyOf(documents));
    }

    private void validateIsolationContracts(CorpusSnapshot corpus) {
        float[] probe = embedding("Spring Boot");
        assertThat(candidateRepository().findCandidates(Long.MAX_VALUE, probe, TOP_20)).isEmpty();
        assertThat(lexicalRepository().findCandidates(Long.MAX_VALUE, "Spring Boot", probe, TOP_20)).isEmpty();
        Set<Long> inactiveChunkIds = new LinkedHashSet<>(jdbcTemplate.queryForList(
                """
                SELECT chunk.id
                FROM document_chunks chunk
                JOIN document_versions version ON version.id = chunk.document_version_id
                JOIN documents document ON document.id = version.document_id
                WHERE document.owner_user_id = ?
                  AND (version.status <> 'ACTIVE' OR document.active_version_id <> version.id)
                """,
                Long.class,
                corpus.ownerId()));
        Set<Long> returned = ids(candidateRepository().findCandidates(OWNER_ID, probe, TOP_20));
        assertThat(intersection(inactiveChunkIds, returned)).isEmpty();
    }

    private List<EvaluationQuery> loadDevelopmentDataset() throws IOException {
        JsonNode root = objectMapper.readTree(DEVELOPMENT_DATASET.toFile());
        List<EvaluationQuery> queries = new ArrayList<>();
        for (JsonNode node : root.get("queries")) {
            boolean positive = node.get("expected").asText().equals("EVIDENCE_EXISTS");
            List<ExpectedEvidence> expected = positive
                    ? List.of(new ExpectedEvidence(
                            node.get("expectedDocument").asText(),
                            null,
                            null,
                            integers(node.get("acceptablePages")),
                            Set.of(),
                            strings(node.get("anchorAny")),
                            false))
                    : List.of();
            queries.add(new EvaluationQuery(
                    node.get("id").asText(),
                    node.get("category").asText(),
                    node.get("query").asText(),
                    positive,
                    expected));
        }
        return List.copyOf(queries);
    }

    private List<EvaluationQuery> loadP5Dataset() throws IOException {
        JsonNode dataset = objectMapper.readTree(P5_DATASET.toFile());
        JsonNode groundTruth = objectMapper.readTree(P5_GROUND_TRUTH.toFile());
        Map<String, List<ExpectedEvidence>> positives = new LinkedHashMap<>();
        for (JsonNode positive : groundTruth.get("positives")) {
            List<ExpectedEvidence> expected = new ArrayList<>();
            for (JsonNode evidence : positive.get("acceptableEvidence")) {
                expected.add(new ExpectedEvidence(
                        null,
                        evidence.get("documentId").asLong(),
                        evidence.get("versionId").asLong(),
                        integers(evidence.get("pages")),
                        longs(evidence.get("chunkIds")),
                        strings(evidence.get("anchorsAny")),
                        false));
            }
            positives.put(positive.get("id").asText(), List.copyOf(expected));
        }
        List<EvaluationQuery> queries = new ArrayList<>();
        for (JsonNode node : dataset.get("queries")) {
            boolean positive = node.get("expected").asText().equals("EVIDENCE_EXISTS");
            queries.add(new EvaluationQuery(
                    node.get("id").asText(),
                    node.get("category").asText(),
                    node.get("query").asText(),
                    positive,
                    positives.getOrDefault(node.get("id").asText(), List.of())));
        }
        return List.copyOf(queries);
    }

    private List<EvaluationQuery> loadStressDataset() throws IOException {
        JsonNode dataset = objectMapper.readTree(STRESS_DATASET.toFile());
        JsonNode groundTruth = objectMapper.readTree(STRESS_GROUND_TRUTH.toFile());
        Map<String, List<ExpectedEvidence>> positives = new LinkedHashMap<>();
        for (JsonNode positive : groundTruth.get("positives")) {
            positives.put(positive.get("id").asText(), List.of(new ExpectedEvidence(
                    null,
                    positive.get("documentId").asLong(),
                    positive.get("versionId").asLong(),
                    Set.of(),
                    longs(positive.get("boundedChunkIds")),
                    strings(positive.get("anchorsAll")),
                    true)));
        }
        List<EvaluationQuery> queries = new ArrayList<>();
        for (JsonNode node : dataset.get("queries")) {
            boolean positive = node.get("polarity").asText().equals("POSITIVE");
            queries.add(new EvaluationQuery(
                    node.get("id").asText(),
                    node.get("type").asText(),
                    node.get("query").asText(),
                    positive,
                    positives.getOrDefault(node.get("id").asText(), List.of())));
        }
        return List.copyOf(queries);
    }

    private List<EvaluationQuery> loadRegressionGuards() throws IOException {
        JsonNode root = objectMapper.readTree(REGRESSION_GUARDS.toFile());
        List<EvaluationQuery> queries = new ArrayList<>();
        for (JsonNode node : root.get("queries")) {
            boolean positive = node.get("expected").asText().equals("EVIDENCE_EXISTS");
            List<ExpectedEvidence> expected = positive
                    ? List.of(new ExpectedEvidence(
                            node.get("expectedDocument").asText(),
                            null,
                            null,
                            integers(node.get("acceptablePages")),
                            Set.of(),
                            strings(node.get("anchorAny")),
                            false))
                    : List.of();
            queries.add(new EvaluationQuery(
                    node.get("id").asText(),
                    node.get("category").asText(),
                    node.get("query").asText(),
                    positive,
                    expected));
        }
        return List.copyOf(queries);
    }

    private void assertFrozenStressInputs() throws IOException {
        assertThat(sha256(Files.readAllBytes(STRESS_DATASET)))
                .isEqualTo(FROZEN_STRESS_DATASET_HASH);
        assertThat(sha256(Files.readAllBytes(STRESS_GROUND_TRUTH)))
                .isEqualTo(FROZEN_STRESS_GROUND_TRUTH_HASH);
        JsonNode dataset = objectMapper.readTree(STRESS_DATASET.toFile());
        assertThat(dataset.get("status").asText()).isEqualTo("FROZEN_PRE_H2");
        assertThat(dataset.get("h2ImplementedAtFreeze").asBoolean()).isFalse();
    }

    private Map<String, Object> latencyReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("D0Q0DenseDb", distribution(denseQ0Nanos));
        report.put("L1Q0LexicalDb", distribution(lexicalQ0Nanos));
        report.put("H1Q0Fusion", distribution(hybridQ0Nanos));
        report.put("H2GateQ0PerCandidate", distributionMillis(h2GateQ0Millis));
        for (Map.Entry<Mode, List<Long>> entry : endToEndNanos.entrySet()) {
            report.put(entry.getKey().name() + "EndToEnd", distribution(entry.getValue()));
        }
        return Map.copyOf(report);
    }

    private LatencyDistribution distribution(List<Long> nanos) {
        List<Double> millis = nanos.stream().map(P6ShadowBenchmarkTest::nanosToMillis).sorted().toList();
        return new LatencyDistribution(
                millis.size(),
                millis.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d),
                percentile(millis, 0.50d),
                percentile(millis, 0.95d));
    }

    private LatencyDistribution distributionMillis(List<Double> millisValues) {
        List<Double> millis = millisValues.stream().sorted().toList();
        return new LatencyDistribution(
                millis.size(),
                millis.stream().mapToDouble(Double::doubleValue).average().orElse(0.0d),
                percentile(millis, 0.50d),
                percentile(millis, 0.95d));
    }

    private ProductionHash hashProductionSearch() throws IOException {
        List<Path> files;
        try (var paths = Files.walk(PRODUCTION_SEARCH_ROOT)) {
            files = paths.filter(path -> path.toString().endsWith(".java"))
                    .sorted(Comparator.comparing(path -> path.toString().replace('\\', '/')))
                    .toList();
        }
        List<String> lines = new ArrayList<>();
        for (Path file : files) {
            lines.add(sha256(Files.readAllBytes(file)) + "  " + file.toString().replace('\\', '/'));
        }
        return new ProductionHash(files.size(), sha256(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private float[] embedding(String query) {
        float[] embedding = embeddingService.embed(query);
        embeddingValidator.validate(embedding);
        return embedding;
    }

    private SearchEvaluationCandidateRepository candidateRepository() {
        return new SearchEvaluationCandidateRepository(jdbcTemplate);
    }

    private SearchEvaluationLexicalCandidateRepository lexicalRepository() {
        return new SearchEvaluationLexicalCandidateRepository(jdbcTemplate);
    }

    private SearchEvaluationLiteralEvidenceGate gate() {
        if (literalEvidenceGate == null) {
            literalEvidenceGate = new SearchEvaluationLiteralEvidenceGate(
                    jdbcTemplate, evidenceExpansionService);
        }
        return literalEvidenceGate;
    }

    private static List<Mode> modes(boolean h2Enabled) {
        return h2Enabled ? List.of(Mode.D0, Mode.L1, Mode.H1, Mode.H2)
                : List.of(Mode.D0, Mode.L1, Mode.H1);
    }

    private static List<VectorSearchResult> mergeDense(
            List<VectorSearchResult> existing,
            List<VectorSearchResult> incoming) {
        Map<Long, VectorSearchResult> byId = new LinkedHashMap<>();
        existing.forEach(candidate -> byId.put(candidate.chunkId(), candidate));
        incoming.forEach(candidate -> byId.merge(candidate.chunkId(), candidate,
                (left, right) -> right.score() > left.score() ? right : left));
        return byId.values().stream()
                .sorted(Comparator.comparingDouble(VectorSearchResult::score).reversed()
                        .thenComparing(VectorSearchResult::chunkId))
                .toList();
    }

    private static List<SearchEvaluationLexicalCandidateRepository.LexicalCandidate> mergeLexical(
            List<SearchEvaluationLexicalCandidateRepository.LexicalCandidate> existing,
            List<SearchEvaluationLexicalCandidateRepository.LexicalCandidate> incoming) {
        Map<Long, SearchEvaluationLexicalCandidateRepository.LexicalCandidate> byId =
                new LinkedHashMap<>();
        existing.forEach(candidate -> byId.put(candidate.candidate().chunkId(), candidate));
        incoming.forEach(candidate -> byId.merge(candidate.candidate().chunkId(), candidate,
                (left, right) -> right.lexicalScore() > left.lexicalScore() ? right : left));
        return byId.values().stream()
                .sorted(Comparator.comparingDouble(
                                SearchEvaluationLexicalCandidateRepository.LexicalCandidate::lexicalScore)
                        .reversed()
                        .thenComparing(candidate -> candidate.candidate().chunkId()))
                .toList();
    }

    private static List<VectorSearchResult> deduplicate(List<VectorSearchResult> values) {
        Map<String, VectorSearchResult> byContent = new LinkedHashMap<>();
        for (VectorSearchResult value : values) {
            byContent.putIfAbsent(Objects.requireNonNullElse(value.content(), "")
                    .replace("\r\n", "\n").replace('\r', '\n').strip(), value);
        }
        return byContent.values().stream().limit(FINAL_LIMIT).toList();
    }

    private static List<CandidateDiagnostic> channelCandidates(List<VectorSearchResult> values) {
        return values.stream().limit(TOP_20)
                .map(candidate -> new CandidateDiagnostic(
                        candidate.chunkId(), candidate.documentId(), candidate.documentVersionId(),
                        candidate.documentTitle(), candidate.sourceIndex(), candidate.score(), candidate.distance()))
                .toList();
    }

    private static Set<Long> ids(List<VectorSearchResult> values) {
        return values.stream().limit(TOP_20).map(VectorSearchResult::chunkId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<Long> difference(Set<Long> left, Set<Long> right) {
        Set<Long> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static Set<Long> intersection(Set<Long> left, Set<Long> right) {
        Set<Long> result = new LinkedHashSet<>(left);
        result.retainAll(right);
        return result;
    }

    private static Set<Integer> integers(JsonNode node) {
        if (node == null) return Set.of();
        Set<Integer> values = new LinkedHashSet<>();
        node.forEach(value -> values.add(value.asInt()));
        return Set.copyOf(values);
    }

    private static Set<Long> longs(JsonNode node) {
        if (node == null) return Set.of();
        Set<Long> values = new LinkedHashSet<>();
        node.forEach(value -> values.add(value.asLong()));
        return Set.copyOf(values);
    }

    private static List<String> strings(JsonNode node) {
        if (node == null) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }

    private static String normalize(String value) {
        return java.text.Normalizer.normalize(Objects.requireNonNullElse(value, ""),
                        java.text.Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace(",", "")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0d : numerator / (double) denominator;
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    private static double percentile(List<Double> values, double ratio) {
        if (values.isEmpty()) return 0.0d;
        int index = Math.max(0, Math.min(values.size() - 1, (int) Math.ceil(values.size() * ratio) - 1));
        return values.get(index);
    }

    private Path outputDirectory() {
        return Path.of(System.getProperty("prizm.p6.output-dir")).toAbsolutePath().normalize();
    }

    private enum Mode {
        D0, L1, H1, H2
    }

    private record EvaluationQuery(
            String id,
            String category,
            String query,
            boolean positive,
            List<ExpectedEvidence> expected) {
    }

    private record ExpectedEvidence(
            String documentTitle,
            Long documentId,
            Long versionId,
            Set<Integer> pages,
            Set<Long> chunkIds,
            List<String> anchors,
            boolean requireAllAnchors) {

        boolean matchesCandidate(VectorSearchResult candidate) {
            if (!matchesDocument(candidate.documentTitle(), candidate.documentId(), candidate.documentVersionId())) {
                return false;
            }
            if (!chunkIds.isEmpty() && !chunkIds.contains(candidate.chunkId())) return false;
            if (!pages.isEmpty() && !pages.contains(candidate.sourceIndex())) return false;
            String searchable = normalize(candidate.content());
            return anchorsMatch(searchable);
        }

        boolean matchesPresented(PresentedCandidate candidate) {
            if (!matchesDocument(candidate.documentTitle(), candidate.documentId(), candidate.documentVersionId())) {
                return false;
            }
            // Match the final evidence location exactly as the P5 API benchmark does. A
            // candidate location must not mask an incorrect P4 evidence expansion.
            boolean location = (chunkIds.isEmpty() || chunkIds.contains(candidate.evidenceChunkId()))
                    && (pages.isEmpty() || pages.contains(candidate.evidenceSourceIndex()));
            if (!location) return false;
            String searchable = normalize(candidate.content() + "\n" + candidate.snippet());
            return anchorsMatch(searchable);
        }

        private boolean anchorsMatch(String searchable) {
            return requireAllAnchors
                    ? anchors.stream().allMatch(anchor -> searchable.contains(normalize(anchor)))
                    : anchors.stream().anyMatch(anchor -> searchable.contains(normalize(anchor)));
        }

        private boolean matchesDocument(String title, Long id, Long version) {
            return (documentTitle == null || documentTitle.equals(title))
                    && (documentId == null || documentId.equals(id))
                    && (versionId == null || versionId.equals(version));
        }
    }

    private record ShadowOutcome(
            List<VectorSearchResult> results,
            List<VariantTrace> traces,
            boolean numericRescue) {
    }

    private record VariantTrace(
            String variantId,
            String query,
            boolean selected,
            int denseCandidates,
            int lexicalCandidates,
            int selectedCount,
            String decision) {
    }

    private record CandidateDiagnostic(
            Long chunkId,
            Long documentId,
            Long documentVersionId,
            String documentTitle,
            int sourceIndex,
            double score,
            double distance) {
    }

    private record ChannelMetrics(
            List<CandidateDiagnostic> denseTop20,
            List<CandidateDiagnostic> lexicalTop20,
            List<CandidateDiagnostic> hybridTop20,
            List<CandidateDiagnostic> h2Top20,
            int denseOnly,
            int lexicalOnly,
            int both,
            boolean denseGroundTruth,
            boolean lexicalGroundTruth,
            boolean hybridGroundTruth,
            boolean h2GroundTruth,
            List<SearchEvaluationLiteralEvidenceGate.GateDecision> h2GateDiagnostics,
            double embeddingMs,
            double denseDbMs,
            double lexicalDbMs,
            double fusionMs) {
    }

    private record ModeResult(
            String state,
            Integer correctRank,
            boolean falsePositive,
            List<CandidateDiagnostic> results,
            List<VariantTrace> variants,
            boolean numericRescue,
            double latencyMs) {
    }

    private record QueryRun(
            String id,
            String category,
            String query,
            boolean positive,
            ChannelMetrics channelMetrics,
            Map<Mode, ModeResult> modeResults) {
    }

    private record DatasetRun(
            String dataset,
            Map<String, Object> summary,
            List<QueryRun> queries) {
    }

    private record CorpusDocument(
            Long documentId,
            String title,
            Long versionId,
            int versionNo,
            int chunks) {
    }

    private record CorpusSnapshot(
            Long ownerId,
            int activeDocuments,
            int activeChunks,
            List<CorpusDocument> documents) {
    }

    private record ProductionHash(int fileCount, String aggregate) {
    }

    private record LatencyDistribution(int samples, double average, double median, double p95) {
    }

    private record PresentedCandidate(
            Long chunkId,
            Long documentId,
            Long documentVersionId,
            String documentTitle,
            int sourceIndex,
            String content,
            String snippet,
            Long evidenceChunkId,
            int evidenceSourceIndex,
            double score,
            double distance) {

        static PresentedCandidate fromProduction(CareerEvidenceSearchResponse value) {
            return new PresentedCandidate(
                    value.chunkId(), value.documentId(), value.documentVersionId(), value.documentTitle(),
                    value.sourceIndex(), value.content(), value.snippet(), value.evidenceChunkId(),
                    value.evidenceSourceIndex(), value.score(), value.distance());
        }

        CandidateDiagnostic diagnostic() {
            return new CandidateDiagnostic(chunkId, documentId, documentVersionId, documentTitle,
                    evidenceSourceIndex, score, distance);
        }
    }
}
