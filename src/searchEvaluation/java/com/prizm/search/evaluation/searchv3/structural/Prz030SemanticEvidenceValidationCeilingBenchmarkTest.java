package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.prizm.search.evaluation.searchv3.structural.SearchV3B3CandidateReplay.Replay;
import com.prizm.search.evaluation.searchv3.structural.SearchV3B3CandidateReplay.ReplayCandidate;
import com.prizm.search.evaluation.searchv3.structural.SearchV3B3CandidateReplay.Suite;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.CandidateProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvaluationTrack;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvidenceChildProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.FreezeInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.GoldJoined;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.PhaseGuard;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.QueryProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.VerifiedCandidates;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.AspectExpression;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.DatasetSlice;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.Query;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.Split;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationEngine.PassageDenseRun;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationEngine.PassageDenseSliceRun;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationEngine.RankedCandidate;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.AggregateMetrics;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.FailureStage;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleRun;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.QueryGold;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.QueryResult;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticOracleDataset.RuntimeSlice;
import com.prizm.search.evaluation.searchv3.typed.DeterministicTypedQueryParser;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Official, opt-in PRZ-030 semantic Oracle ceiling benchmark. */
class Prz030SemanticEvidenceValidationCeilingBenchmarkTest {

    static final String CODE_FREEZE_PROPERTY = "prizm.prz030.code-freeze-commit";
    static final Path OUTPUT = Path.of(
            "local/search-v3-evaluation/prz030/semantic-evidence-validation-ceiling.json");
    static final double MIN_DIRECT_RECALL_20 = 0.90d;
    static final double MIN_USER_MACRO_TOP1_GAIN = 0.05d;
    static final int MIN_RECOVERABLE_BUNDLES = 3;
    static final int MIN_FALSE_POSITIVE_RISK_QUERIES = 2;
    static final int MIN_FALSE_POSITIVE_RISK_BUNDLES = 2;
    static final double DECISION_EPSILON = 1.0e-12d;
    static final int QUERY_TRACK_TOTAL = 93;
    static final int SEMANTIC_CORE_QUERY_COUNT = 79;
    static final int TYPED_OVERLAP_QUERY_COUNT = 14;
    static final String QUERY_TRACK_INVENTORY_SHA256 =
            "6eb8db7e2cbbb4c4821e857dc3c72f70526c0014abf99fcc596951588cdd02c4";
    static final Set<String> FROZEN_TYPED_OVERLAP_QUERY_IDS = Set.of(
            "SV3-U01-Q01",
            "SV3-U01-Q02",
            "SV3-U01-Q05",
            "SV3-U06-Q01",
            "SV3-U06-Q04",
            "SV3-U02-Q01",
            "SV3-U03-Q01",
            "SV3-LF-U101-Q02",
            "SV3-LF-U103-Q01",
            "SV3-LF-U103-Q02",
            "SV3-LF-U105-Q01",
            "SV3-LF-U105-Q04",
            "SV3-LF-U106-Q01",
            "SV3-RB-U205-Q04");

    private static final String PRZ025_DEPENDENCY =
            "5f8229f88251938dc5b34588676cc69edf409c99";
    private static final String PRZ026_B3_DEPENDENCY =
            "1bbc1d761bd314a17e8f3ed4e2bcceb23a2fc96a";
    private static final String PRZ028_DEPENDENCY =
            "33c702aa0bff86502f7f70a343b60c59c13eb80f";
    private static final String PRZ029_DEPENDENCY =
            "f7e4a7adffd5574526d6c00c76ece9113a68d69f";
    private static final String EXPECTED_MODEL_NAME = "bge-m3:latest";
    private static final String EXPECTED_MODEL_DIGEST = SearchV3B3CandidateReplay.BGE_M3_DIGEST;
    private static final Path SEALED_MANIFEST = Path.of(
            "src/test/resources/search-v3-evaluation/sealed-final/manifest.json");
    private static final String SEALED_GIT_PATH =
            "src/test/resources/search-v3-evaluation/sealed-final";
    private static final String STRESS_GIT_PATH =
            SearchV3SemanticOracleDataset.STRESS_ROOT.toString().replace('\\', '/');
    private static final String SEALED_TREE = "a129080861d7dafd32a9b3b3357b61aebb237e59";
    private static final String SEALED_MANIFEST_SHA256 =
            "d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa";
    private static final String SEALED_COMBINED_SHA256 =
            "e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383";
    private static final Pattern COMMIT_SHA = Pattern.compile("^[0-9a-f]{40}$");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void computesSemanticOracleCeilingOnlyAfterEveryCandidateSuiteIsFrozen() throws Exception {
        String codeFreeze = System.getProperty(CODE_FREEZE_PROPERTY, "");
        assumeTrue(!codeFreeze.isBlank(), "PRZ-030 official benchmark is opt-in");
        assertThat(codeFreeze).matches(COMMIT_SHA);
        verifyRepositoryBeforeRun(codeFreeze);
        assertThat(Files.exists(OUTPUT)).as("raw report must be CREATE_NEW").isFalse();

        SealedMetadata sealedBefore = sealedMetadataOnly();
        assertThat(sealedBefore).isEqualTo(expectedSealedMetadata());
        String sealedManifestHashBefore = sha256(SEALED_MANIFEST);
        assertThat(sealedManifestHashBefore).isEqualTo(SEALED_MANIFEST_SHA256);
        assertThat(git("rev-parse", "HEAD:" + SEALED_GIT_PATH)).isEqualTo(SEALED_TREE);

        SearchV3SemanticOracleDataset dataset = new SearchV3SemanticOracleDataset();
        SearchV3SemanticOracleDataset.ManifestMetadata runtimeManifestBefore =
                dataset.readStressRuntimeManifestMetadata(true);
        assertThat(runtimeManifestBefore.combinedSha256())
                .isEqualTo(SearchV3SemanticOracleDataset.STRESS_RUNTIME_SHA256);
        String stressTreeBefore = git("rev-parse", "HEAD:" + STRESS_GIT_PATH);

        // Gold-free query-track classification is frozen before any candidate construction or BGE call.
        PreRetrievalInventory preRetrieval = preRetrievalInventory(dataset);
        QueryTrackInventory queryTracks = preRetrieval.inventory();

        SearchV3DenseAblationEngine denseEngine = new SearchV3DenseAblationEngine();
        SearchV3B3CandidateReplay replayLoader = new SearchV3B3CandidateReplay();
        List<FrozenSuite> frozenSuites = new ArrayList<>();

        // Historical suites are exact frozen B3 replays. No model endpoint is touched.
        frozenSuites.add(freezeHistorical(
                Suite.ORIGINAL_SEED, dataset, denseEngine, replayLoader));
        frozenSuites.add(freezeHistorical(
                Suite.LONG_FORM_EXPANSION, dataset, denseEngine, replayLoader));
        frozenSuites.add(freezeHistorical(
                Suite.INDEPENDENT_ROBUSTNESS, dataset, denseEngine, replayLoader));

        // Stress is the only suite that performs fixed BGE-M3 B3 candidate export.
        List<RuntimeSlice> stressRuntime = preRetrieval.stressRuntime();
        OllamaBgeM3EmbeddingClient embeddingClient = new OllamaBgeM3EmbeddingClient();
        OllamaBgeM3EmbeddingClient.ModelMetadata model = embeddingClient.inspectModel();
        verifyModel(model);
        frozenSuites.add(freezeStress(stressRuntime, denseEngine, embeddingClient, model));

        // Global boundary: no suite may load Gold until all four candidate freezes verify.
        assertThat(frozenSuites).hasSize(4);
        assertThat(frozenSuites)
                .allSatisfy(value -> {
                    assertThat(value.guard().phase()).isEqualTo(SearchV3CandidateFreeze.Phase.VERIFIED);
                    assertThat(value.verified()).isNotNull();
                });

        SearchV3SemanticOracleGoldJoiner goldJoiner = new SearchV3SemanticOracleGoldJoiner();
        SearchV3OracleCeilingEvaluator evaluator = new SearchV3OracleCeilingEvaluator();
        List<SuiteReport> suites = new ArrayList<>();
        for (FrozenSuite frozen : frozenSuites) {
            GoldJoined<List<QueryGold>> joined = frozen.guard().joinGold(() -> frozen.replaySuite() == null
                    ? goldJoiner.loadStressGold(frozen.verified())
                    : goldJoiner.loadHistoricalGold(frozen.verified(), frozen.replaySuite()));
            OracleRun run = evaluator.evaluate(joined);
            suites.add(toReport(frozen, run, queryTracks));
        }

        List<QueryResult> combinedQueries = suites.stream()
                .flatMap(value -> value.queries().stream())
                .toList();
        assertThat(combinedQueries).hasSize(93);
        assertThat(combinedQueries.stream().map(QueryResult::queryId)).doesNotHaveDuplicates();
        assertThat(combinedQueries.stream().map(QueryResult::queryId).collect(Collectors.toSet()))
                .isEqualTo(queryTracks.allQueryIds());
        List<QueryResult> semanticCore = combinedQueries.stream()
                .filter(value -> queryTracks.semanticCoreQueryIds().contains(value.queryId()))
                .toList();
        List<QueryResult> typedOverlap = combinedQueries.stream()
                .filter(value -> queryTracks.typedOverlapQueryIds().contains(value.queryId()))
                .toList();
        assertThat(semanticCore).hasSize(SEMANTIC_CORE_QUERY_COUNT);
        assertThat(typedOverlap).hasSize(TYPED_OVERLAP_QUERY_COUNT);
        CombinedReport combined = combinedReport(combinedQueries, semanticCore, typedOverlap, queryTracks);
        DecisionAssessment decision = assessDecision(decisionInputs(semanticCore, combined));

        verifyRepositoryAfterRun(
                codeFreeze, sealedBefore, sealedManifestHashBefore,
                stressTreeBefore, runtimeManifestBefore, replayLoader);

        BenchmarkReport report = new BenchmarkReport(
                1,
                "PRZ-030_SEMANTIC_EVIDENCE_VALIDATION_ORACLE_CEILING",
                codeFreeze,
                List.of(PRZ025_DEPENDENCY, PRZ026_B3_DEPENDENCY, PRZ028_DEPENDENCY, PRZ029_DEPENDENCY),
                model,
                "B3 RetrievalPassage + BGE-M3 raw Dense Top20",
                "DIRECT_SUPPORT > RELATED > CONTRADICTS > INSUFFICIENT > UNJUDGED stable partition; "
                        + "graded relevance and false-positive removal are lower-bound ceilings",
                "All suite candidate exports were frozen and verified before any Gold load",
                SearchV3SemanticOracleDataset.STRESS_SHA256,
                stressTreeBefore,
                suites,
                combined,
                decision,
                sealedBefore,
                "NOT_RUN");
        writeCreateNew(report);

        System.out.println("PRZ030_OUTPUT=" + OUTPUT.toAbsolutePath().normalize());
        System.out.println("PRZ030_OUTPUT_SHA256=" + sha256(OUTPUT));
        System.out.println("PRZ030_STRESS_INPUT_SHA256=" + SearchV3SemanticOracleDataset.STRESS_SHA256);
        System.out.println("PRZ030_SEALED_TREE=" + SEALED_TREE);
        System.out.println("PRZ030_DECISION=" + decision.decision());
    }

    private PreRetrievalInventory preRetrievalInventory(SearchV3SemanticOracleDataset dataset) {
        List<QueryTrackRow> rows = new ArrayList<>();
        for (Suite suite : List.of(
                Suite.ORIGINAL_SEED, Suite.LONG_FORM_EXPANSION, Suite.INDEPENDENT_ROBUSTNESS)) {
            for (SearchV3B3CandidateReplay.QueryIdentity query : suite.queryInventory()) {
                rows.add(new QueryTrackRow(
                        query.queryId(),
                        FROZEN_TYPED_OVERLAP_QUERY_IDS.contains(query.queryId())
                                ? QueryTrack.TYPED_OVERLAP : QueryTrack.SEMANTIC_CORE));
            }
        }
        DeterministicTypedQueryParser parser = new DeterministicTypedQueryParser();
        List<RuntimeSlice> stressRuntime = List.of(
                dataset.loadStressRuntime(Split.DEV),
                dataset.loadStressRuntime(Split.CALIBRATION));
        for (RuntimeSlice runtime : stressRuntime) {
            for (SearchV3SemanticOracleDataset.RuntimeQuestion query : runtime.questions()) {
                rows.add(new QueryTrackRow(
                        query.queryId(),
                        parser.parse(query.text()).isEmpty()
                                ? QueryTrack.SEMANTIC_CORE : QueryTrack.TYPED_OVERLAP));
            }
        }
        return new PreRetrievalInventory(verifyQueryTrackRows(rows), stressRuntime);
    }

    static QueryTrackInventory freezeQueryTrackInventory(List<QueryText> queries) {
        DeterministicTypedQueryParser parser = new DeterministicTypedQueryParser();
        List<QueryTrackRow> rows = queries.stream()
                .map(query -> new QueryTrackRow(
                        query.queryId(),
                        parser.parse(query.queryText()).isEmpty()
                                ? QueryTrack.SEMANTIC_CORE : QueryTrack.TYPED_OVERLAP))
                .toList();
        return verifyQueryTrackRows(rows);
    }

    private static QueryTrackInventory verifyQueryTrackRows(List<QueryTrackRow> rows) {
        if (rows.size() != QUERY_TRACK_TOTAL) {
            throw new IllegalStateException("PRZ-030 query-track inventory count changed");
        }
        Set<String> allIds = rows.stream()
                .map(QueryTrackRow::queryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (allIds.size() != rows.size()) {
            throw new IllegalStateException("PRZ-030 query-track inventory contains duplicate IDs");
        }
        Set<String> semanticIds = rows.stream()
                .filter(value -> value.track() == QueryTrack.SEMANTIC_CORE)
                .map(QueryTrackRow::queryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> typedIds = rows.stream()
                .filter(value -> value.track() == QueryTrack.TYPED_OVERLAP)
                .map(QueryTrackRow::queryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String canonical = rows.stream()
                .map(value -> value.queryId() + "="
                        + (value.track() == QueryTrack.SEMANTIC_CORE ? "SEMANTIC" : "TYPED"))
                .collect(Collectors.joining("\n", "", "\n"));
        String sha256 = SearchV3CandidateFreeze.sha256(canonical);
        if (semanticIds.size() != SEMANTIC_CORE_QUERY_COUNT
                || typedIds.size() != TYPED_OVERLAP_QUERY_COUNT
                || !typedIds.equals(FROZEN_TYPED_OVERLAP_QUERY_IDS)
                || !QUERY_TRACK_INVENTORY_SHA256.equals(sha256)) {
            throw new IllegalStateException("PRZ-030 query-track inventory drifted");
        }
        return new QueryTrackInventory(
                List.copyOf(rows), Set.copyOf(allIds), Set.copyOf(semanticIds), Set.copyOf(typedIds), sha256);
    }

    private FrozenSuite freezeHistorical(
            Suite suite,
            SearchV3SemanticOracleDataset dataset,
            SearchV3DenseAblationEngine denseEngine,
            SearchV3B3CandidateReplay replayLoader) {
        Replay replay = replayLoader.load(suite);
        List<RuntimeSlice> runtimes = switch (suite) {
            case ORIGINAL_SEED -> List.of(
                    dataset.loadOriginalRuntime(Split.DEV),
                    dataset.loadOriginalRuntime(Split.CALIBRATION));
            case LONG_FORM_EXPANSION -> List.of(
                    dataset.loadLongFormRuntime(Split.DEV),
                    dataset.loadLongFormRuntime(Split.CALIBRATION));
            case INDEPENDENT_ROBUSTNESS -> List.of(
                    dataset.loadRobustnessRuntime(Split.DEV),
                    dataset.loadRobustnessRuntime(Split.CALIBRATION));
            case HISTORICAL_LONG_FORM -> throw new IllegalArgumentException(
                    "duplicate historical-long-form suite is excluded from PRZ-030");
        };
        Map<String, PassageOwner> passages = reconstructPassages(runtimes, denseEngine);
        Set<String> replayPassageIds = replay.candidates().stream()
                .map(ReplayCandidate::candidateId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!replayPassageIds.equals(passages.keySet())) {
            throw new IllegalStateException("historical replay/reconstructed passage inventory drifted");
        }
        List<QueryProjection> queries = historicalProjection(replay, passages);
        FreezeInput input = new FreezeInput(
                SearchV3CandidateFreeze.SCHEMA_VERSION,
                suite.rootNode(),
                suite.datasetVersion(),
                replay.canonicalSha256(),
                EvaluationTrack.SEMANTIC,
                queries);
        PhaseGuard guard = new PhaseGuard();
        guard.freezeCandidates(input);
        VerifiedCandidates verified = guard.verifyFreeze();
        return new FrozenSuite(suite.rootNode(), suite.datasetVersion(), suite, guard, verified,
                queries.size(), candidateCount(queries));
    }

    private FrozenSuite freezeStress(
            List<RuntimeSlice> runtimes,
            SearchV3DenseAblationEngine denseEngine,
            OllamaBgeM3EmbeddingClient embeddingClient,
            OllamaBgeM3EmbeddingClient.ModelMetadata model) {
        List<DatasetSlice> adapters = runtimes.stream().map(this::stressAdapter).toList();
        PassageDenseRun run = denseEngine.runPassageDenseOnly(
                adapters, embeddingClient, model, "PRZ-030-SEMANTIC-STRESS-B3-CANDIDATE-EXPORT");
        List<QueryProjection> queries = stressProjection(run);
        FreezeInput input = new FreezeInput(
                SearchV3CandidateFreeze.SCHEMA_VERSION,
                SearchV3SemanticOracleDataset.STRESS_SUITE,
                SearchV3SemanticOracleDataset.STRESS_VERSION,
                SearchV3SemanticOracleDataset.STRESS_RUNTIME_SHA256,
                EvaluationTrack.SEMANTIC,
                queries);
        PhaseGuard guard = new PhaseGuard();
        guard.freezeCandidates(input);
        VerifiedCandidates verified = guard.verifyFreeze();
        return new FrozenSuite(
                SearchV3SemanticOracleDataset.STRESS_SUITE,
                SearchV3SemanticOracleDataset.STRESS_VERSION,
                null,
                guard,
                verified,
                queries.size(),
                candidateCount(queries));
    }

    private DatasetSlice stressAdapter(RuntimeSlice runtime) {
        List<Query> queries = runtime.questions().stream().map(value -> new Query(
                value.queryId(),
                value.userBundleId(),
                runtime.split(),
                value.text(),
                "SOURCE_ONLY_NOT_LOADED",
                value.language(),
                List.of(),
                new AspectExpression("SOURCE_ONLY", List.of(), 0),
                List.of(),
                List.of())).toList();
        return new DatasetSlice(
                runtime.datasetVersion(), runtime.split(), runtime.manifestCombinedSha256(),
                runtime.bundles(), queries, runtime.activeDocumentsByVersion(), Map.of(), Map.of(), Map.of());
    }

    private Map<String, PassageOwner> reconstructPassages(
            List<RuntimeSlice> runtimes,
            SearchV3DenseAblationEngine denseEngine) {
        Map<String, PassageOwner> result = new LinkedHashMap<>();
        for (RuntimeSlice runtime : runtimes) {
            DatasetSlice slice = runtime.goldFreeAdapter();
            SearchV3DenseAblationEngine.CandidateBuild children = denseEngine.buildStructuralCandidates(slice);
            SearchV3DenseAblationEngine.PassageCandidateBuild passages =
                    denseEngine.buildPassageCandidates(slice, children);
            Map<String, String> ownerByVersion = runtime.activeDocumentsByVersion().values().stream()
                    .collect(Collectors.toMap(value -> value.versionId(), value -> value.userBundleId()));
            for (RetrievalPassage passage : passages.passages()) {
                PassageOwner value = new PassageOwner(ownerByVersion.get(passage.versionId()), passage);
                if (value.owner() == null || result.put(passage.passageId(), value) != null) {
                    throw new IllegalStateException("duplicate or ownerless reconstructed passage");
                }
            }
        }
        return Map.copyOf(result);
    }

    private List<QueryProjection> historicalProjection(
            Replay replay,
            Map<String, PassageOwner> passages) {
        Map<String, List<ReplayCandidate>> byQuery = replay.candidates().stream().collect(
                Collectors.groupingBy(ReplayCandidate::query, LinkedHashMap::new, Collectors.toList()));
        List<QueryProjection> result = new ArrayList<>();
        for (List<ReplayCandidate> ranking : byQuery.values()) {
            ReplayCandidate identity = ranking.get(0);
            Set<String> ownerPassageIds = passages.entrySet().stream()
                    .filter(value -> identity.owner().equals(value.getValue().owner()))
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> replayRankingIds = ranking.stream()
                    .map(ReplayCandidate::candidateId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (ranking.size() > SearchV3CandidateFreeze.MAX_CANDIDATES_PER_QUERY
                    || !replayRankingIds.equals(ownerPassageIds)) {
                throw new IllegalStateException(
                        "historical replay is not the exact full owner B3 ranking: " + identity.query());
            }
            List<CandidateProjection> candidates = ranking.stream()
                    .map(row -> historicalCandidate(row, passages.get(row.candidateId())))
                    .toList();
            result.add(new QueryProjection(
                    identity.query(), identity.owner(), identity.split(), EvaluationTrack.SEMANTIC, candidates));
        }
        return List.copyOf(result);
    }

    private CandidateProjection historicalCandidate(ReplayCandidate row, PassageOwner located) {
        if (located == null || !row.owner().equals(located.owner())) {
            throw new IllegalStateException("historical replay candidate cannot be reconstructed: "
                    + row.candidateId());
        }
        RetrievalPassage passage = located.passage();
        List<String> childIds = passage.evidenceChildren().stream().map(EvidenceChild::childId).toList();
        if (!row.doc().equals(passage.documentId())
                || !row.version().equals(passage.versionId())
                || !row.parent().equals(passage.parentAnnotationCandidateId())
                || !row.evidenceChildIds().equals(childIds)
                || !row.sourceSha256().equals(SearchV3CandidateFreeze.sha256(passage.passageSourceText()))
                || !row.retrievalSha256().equals(SearchV3CandidateFreeze.sha256(passage.retrievalText()))) {
            throw new IllegalStateException("historical replay source identity drifted: " + row.candidateId());
        }
        return candidateProjection(
                row.rank(), row.cosine(), row.owner(), passage);
    }

    private List<QueryProjection> stressProjection(PassageDenseRun run) {
        List<QueryProjection> result = new ArrayList<>();
        for (PassageDenseSliceRun slice : run.slices()) {
            for (SearchV3DenseAblationEngine.PassageDenseQueryRanking query : slice.queries()) {
                List<CandidateProjection> ranking = query.fullRanking().stream()
                        .limit(SearchV3CandidateFreeze.MAX_CANDIDATES_PER_QUERY)
                        .map(candidate -> stressCandidate(
                                candidate,
                                query.query().userBundleId(),
                                slice.passageById().get(candidate.candidateId())))
                        .toList();
                result.add(new QueryProjection(
                        query.query().queryId(), query.query().userBundleId(),
                        query.query().split().manifestName(), EvaluationTrack.SEMANTIC, ranking));
            }
        }
        return List.copyOf(result);
    }

    private CandidateProjection stressCandidate(
            RankedCandidate ranked,
            String owner,
            RetrievalPassage passage) {
        if (passage == null
                || !ranked.documentId().equals(passage.documentId())
                || !ranked.versionId().equals(passage.versionId())
                || !ranked.parentAnnotationCandidateId().equals(passage.parentAnnotationCandidateId())
                || !ranked.evidenceChildIds().equals(passage.evidenceChildIds())
                || !ranked.sourceText().equals(passage.passageSourceText())
                || !ranked.retrievalText().equals(passage.retrievalText())) {
            throw new IllegalStateException("stress B3 source identity drifted: " + ranked.candidateId());
        }
        return candidateProjection(ranked.rank(), ranked.cosineScore(), owner, passage);
    }

    private CandidateProjection candidateProjection(
            int rank,
            double cosine,
            String owner,
            RetrievalPassage passage) {
        List<EvidenceChildProjection> children = passage.evidenceChildren().stream()
                .map(child -> {
                    SourceProvenance source = child.provenance();
                    return new EvidenceChildProjection(
                            child.childId(), source.documentId(), source.versionId(), source.page(),
                            source.codePointStart(), source.codePointEnd(), child.sourceText(),
                            SearchV3CandidateFreeze.sha256(child.sourceText()));
                })
                .toList();
        return new CandidateProjection(
                rank,
                passage.passageId(),
                cosine,
                owner,
                passage.documentId(),
                passage.versionId(),
                passage.parentAnnotationCandidateId(),
                passage.passageSourceText(),
                passage.retrievalText(),
                SearchV3CandidateFreeze.sha256(passage.passageSourceText()),
                SearchV3CandidateFreeze.sha256(passage.retrievalText()),
                children);
    }

    private SuiteReport toReport(
            FrozenSuite frozen,
            OracleRun run,
            QueryTrackInventory queryTracks) {
        List<QueryResult> semantic = run.queries().stream()
                .filter(value -> queryTracks.semanticCoreQueryIds().contains(value.queryId()))
                .toList();
        List<QueryResult> typed = run.queries().stream()
                .filter(value -> queryTracks.typedOverlapQueryIds().contains(value.queryId()))
                .toList();
        return new SuiteReport(
                frozen.suite(),
                frozen.datasetVersion(),
                frozen.verified().frozen().input().sourceArtifactSha256(),
                frozen.verified().frozen().canonicalSha256(),
                frozen.queryCount(),
                frozen.candidateCount(),
                run.aggregate(),
                SearchV3OracleCeilingEvaluator.aggregate(semantic),
                SearchV3OracleCeilingEvaluator.aggregate(typed),
                directMacro(SearchV3OracleCeilingEvaluator.aggregateByUser(semantic)),
                SearchV3OracleCeilingEvaluator.aggregateByProfession(semantic),
                SearchV3OracleCeilingEvaluator.aggregateByLanguage(semantic),
                SearchV3OracleCeilingEvaluator.aggregateByCategory(semantic),
                run.queries());
    }

    private CombinedReport combinedReport(
            List<QueryResult> allQueries,
            List<QueryResult> semanticQueries,
            List<QueryResult> typedQueries,
            QueryTrackInventory queryTracks) {
        Map<String, AggregateMetrics> users = SearchV3OracleCeilingEvaluator.aggregateByUser(semanticQueries);
        Map<String, AggregateMetrics> focusSlices = new LinkedHashMap<>();
        focusSlices.put("OTHER_ACTOR", aggregateCategories(semanticQueries, Set.of("other_actor")));
        focusSlices.put("NEGATION", aggregateCategories(semanticQueries, Set.of("negation")));
        focusSlices.put("COMPLETION", aggregateCategories(
                semanticQueries,
                Set.of("completion_state", "completed_production", "attempted_prototype", "planned")));
        focusSlices.put("ABSTRACT", aggregateCategories(semanticQueries, Set.of("abstract_competency")));
        focusSlices.put("PARAPHRASE", aggregateCategories(semanticQueries, Set.of("semantic_paraphrase")));
        Map<String, Long> answerability = semanticQueries.stream().collect(Collectors.groupingBy(
                value -> switch (value.expectedState()) {
                    case FOUND -> "SUPPORTED";
                    case PARTIAL -> "PARTIALLY_SUPPORTED";
                    case NONE -> "NOT_SUPPORTED";
                    case UNRESOLVED -> "INVALID_EXPECTED_STATE";
                },
                LinkedHashMap::new,
                Collectors.counting()));
        return new CombinedReport(
                queryTracks.canonicalSha256(),
                allQueries.size(),
                SearchV3OracleCeilingEvaluator.aggregate(allQueries),
                SearchV3OracleCeilingEvaluator.aggregate(semanticQueries),
                directMacro(users),
                users,
                SearchV3OracleCeilingEvaluator.aggregateByProfession(semanticQueries),
                SearchV3OracleCeilingEvaluator.aggregateByLanguage(semanticQueries),
                SearchV3OracleCeilingEvaluator.aggregateByCategory(semanticQueries),
                Map.copyOf(focusSlices),
                Map.copyOf(answerability),
                new TypedOverlapDiagnostic(
                        typedQueries.size(),
                        typedQueries.stream().map(QueryResult::queryId).toList(),
                        SearchV3OracleCeilingEvaluator.aggregate(typedQueries),
                        directMacro(SearchV3OracleCeilingEvaluator.aggregateByUser(typedQueries)),
                        SearchV3OracleCeilingEvaluator.aggregateByProfession(typedQueries),
                        SearchV3OracleCeilingEvaluator.aggregateByLanguage(typedQueries),
                        "DIAGNOSTIC_ONLY_DECISION_WEIGHT_0"));
    }

    private AggregateMetrics aggregateCategories(List<QueryResult> queries, Set<String> categories) {
        return SearchV3OracleCeilingEvaluator.aggregate(queries.stream()
                .filter(value -> value.categories().stream().anyMatch(categories::contains))
                .toList());
    }

    private MacroMetrics directMacro(Map<String, AggregateMetrics> users) {
        List<AggregateMetrics> directUsers = users.values().stream()
                .filter(value -> value.directPositiveQueryCount() > 0)
                .toList();
        return new MacroMetrics(
                directUsers.size(),
                average(directUsers, AggregateMetrics::s0Top1),
                average(directUsers, AggregateMetrics::o1Top1),
                average(directUsers, AggregateMetrics::s0Mrr),
                average(directUsers, AggregateMetrics::o1Mrr));
    }

    private double average(List<AggregateMetrics> values, Function<AggregateMetrics, Double> field) {
        return values.stream().mapToDouble(field::apply).average().orElse(0.0d);
    }

    private DecisionInputs decisionInputs(List<QueryResult> queries, CombinedReport combined) {
        Set<String> missBundles = bundlesAt(queries, FailureStage.RETRIEVAL_MISS);
        Set<String> recoverableBundles = bundlesAt(queries, FailureStage.RANKING_RECOVERABLE);
        List<QueryResult> falsePositiveRisks = queries.stream()
                .filter(value -> value.failureStage() == FailureStage.FALSE_POSITIVE_RISK)
                .toList();
        long removableFalsePositiveRisks = falsePositiveRisks.stream()
                .filter(value -> value.ceilingState() == SearchV3OracleCeilingEvaluator.CeilingState.NONE)
                .count();
        long falsePositiveBundles = falsePositiveRisks.stream()
                .filter(value -> value.ceilingState() == SearchV3OracleCeilingEvaluator.CeilingState.NONE)
                .map(QueryResult::userBundleId)
                .distinct()
                .count();
        return new DecisionInputs(
                combined.semanticCoreAggregate().s0DirectRecallAt20(),
                missBundles.size(),
                combined.directUserMacro().o1Top1() - combined.directUserMacro().s0Top1(),
                recoverableBundles.size(),
                Math.toIntExact(removableFalsePositiveRisks),
                Math.toIntExact(falsePositiveBundles));
    }

    static DecisionAssessment assessDecision(DecisionInputs input) {
        boolean top1Gate = input.userMacroTop1Gain() + DECISION_EPSILON >= MIN_USER_MACRO_TOP1_GAIN;
        boolean recoverableGate = input.recoverableBundleCount() >= MIN_RECOVERABLE_BUNDLES;
        boolean falsePositiveGate = input.falsePositiveRiskQueryCount() >= MIN_FALSE_POSITIVE_RISK_QUERIES
                && input.falsePositiveRiskBundleCount() >= MIN_FALSE_POSITIVE_RISK_BUNDLES;
        boolean capabilityGate = top1Gate || recoverableGate || falsePositiveGate;
        Decision decision;
        if (input.s0DirectRecallAt20() + DECISION_EPSILON < MIN_DIRECT_RECALL_20
                || input.retrievalMissBundleCount() >= 3) {
            decision = Decision.RETRIEVAL_FIRST;
        }
        else if (capabilityGate) {
            decision = Decision.BUILD_SEMANTIC_VALIDATOR;
        }
        else {
            decision = Decision.VALIDATOR_NOT_JUSTIFIED;
        }
        return new DecisionAssessment(
                decision,
                capabilityGate,
                top1Gate,
                recoverableGate,
                falsePositiveGate,
                decision == Decision.RETRIEVAL_FIRST
                        ? "RETRIEVAL_AUGMENTATION_NEEDED" : "DEFER",
                "DEFER",
                input);
    }

    private Set<String> bundlesAt(List<QueryResult> queries, FailureStage stage) {
        return queries.stream()
                .filter(value -> value.failureStage() == stage)
                .map(QueryResult::userBundleId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private long candidateCount(List<QueryProjection> queries) {
        return queries.stream().mapToLong(value -> value.rankedCandidates().size()).sum();
    }

    private void verifyRepositoryBeforeRun(String codeFreeze) throws Exception {
        assertThat(git("rev-parse", "HEAD")).isEqualTo(codeFreeze);
        assertThat(git("status", "--porcelain", "--untracked-files=all")).isBlank();
        for (String dependency : List.of(
                PRZ025_DEPENDENCY, PRZ026_B3_DEPENDENCY, PRZ028_DEPENDENCY, PRZ029_DEPENDENCY)) {
            assertThat(gitExit("merge-base", "--is-ancestor", dependency, "HEAD")).isZero();
        }
    }

    private void verifyRepositoryAfterRun(
            String codeFreeze,
            SealedMetadata sealedBefore,
            String sealedManifestHashBefore,
            String stressTreeBefore,
            SearchV3SemanticOracleDataset.ManifestMetadata runtimeManifestBefore,
            SearchV3B3CandidateReplay replayLoader) throws Exception {
        assertThat(git("rev-parse", "HEAD")).isEqualTo(codeFreeze);
        assertThat(git("status", "--porcelain", "--untracked-files=all")).isBlank();
        assertThat(sealedMetadataOnly()).isEqualTo(sealedBefore);
        assertThat(sha256(SEALED_MANIFEST)).isEqualTo(sealedManifestHashBefore);
        assertThat(git("rev-parse", "HEAD:" + SEALED_GIT_PATH)).isEqualTo(SEALED_TREE);
        assertThat(git("rev-parse", "HEAD:" + STRESS_GIT_PATH)).isEqualTo(stressTreeBefore);
        SearchV3SemanticOracleDataset dataset = new SearchV3SemanticOracleDataset();
        assertThat(dataset.readStressRuntimeManifestMetadata(true)).isEqualTo(runtimeManifestBefore);
        replayLoader.load(Suite.ORIGINAL_SEED);
        replayLoader.load(Suite.LONG_FORM_EXPANSION);
        replayLoader.load(Suite.INDEPENDENT_ROBUSTNESS);
    }

    private void verifyModel(OllamaBgeM3EmbeddingClient.ModelMetadata model) {
        assertThat(model.resolvedName()).isEqualTo(EXPECTED_MODEL_NAME);
        assertThat(model.digest()).isEqualTo(EXPECTED_MODEL_DIGEST);
        assertThat(model.dimensions()).isEqualTo(1024);
        assertThat(model.embeddingCapable()).isTrue();
        assertThat(OllamaBgeM3EmbeddingClient.SIMILARITY).isEqualTo("COSINE");
    }

    private SealedMetadata expectedSealedMetadata() {
        return new SealedMetadata(SEALED_COMBINED_SHA256, false, false, false);
    }

    private SealedMetadata sealedMetadataOnly() throws Exception {
        JsonNode manifest;
        try (InputStream input = Files.newInputStream(SEALED_MANIFEST)) {
            manifest = mapper.readTree(input);
        }
        return new SealedMetadata(
                manifest.path("combinedSha256").asText(),
                manifest.path("opened").asBoolean(true),
                manifest.path("searchExecuted").asBoolean(true),
                manifest.path("mutable").asBoolean(true));
    }

    private void writeCreateNew(BenchmarkReport report) throws Exception {
        Files.createDirectories(OUTPUT.getParent());
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n";
        Path temporary = OUTPUT.resolveSibling(OUTPUT.getFileName() + ".tmp-" + UUID.randomUUID());
        Files.writeString(
                temporary, json, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        try {
            Files.move(temporary, OUTPUT, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, OUTPUT);
        }
    }

    private String git(String... arguments) throws Exception {
        ProcessResult result = gitResult(arguments);
        if (result.exitCode() != 0) {
            throw new IllegalStateException("git command failed: " + result.output());
        }
        return result.output();
    }

    private int gitExit(String... arguments) throws Exception {
        return gitResult(arguments).exitCode();
    }

    private ProcessResult gitResult(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        return new ProcessResult(process.waitFor(), output);
    }

    private String sha256(Path path) throws Exception {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    enum Decision {
        BUILD_SEMANTIC_VALIDATOR,
        RETRIEVAL_FIRST,
        VALIDATOR_NOT_JUSTIFIED
    }

    record DecisionInputs(
            double s0DirectRecallAt20,
            int retrievalMissBundleCount,
            double userMacroTop1Gain,
            int recoverableBundleCount,
            int falsePositiveRiskQueryCount,
            int falsePositiveRiskBundleCount) {
    }

    record DecisionAssessment(
            Decision decision,
            boolean capabilityGatePassed,
            boolean userMacroTop1Gate,
            boolean recoverableBundleGate,
            boolean falsePositiveRiskGate,
            String retrievalAugmentation,
            String parentDense,
            DecisionInputs inputs) {
    }

    record MacroMetrics(
            int directPositiveUserCount,
            double s0Top1,
            double o1Top1,
            double s0Mrr,
            double o1Mrr) {
    }

    record FrozenSuite(
            String suite,
            String datasetVersion,
            Suite replaySuite,
            PhaseGuard guard,
            VerifiedCandidates verified,
            int queryCount,
            long candidateCount) {
    }

    record PassageOwner(String owner, RetrievalPassage passage) {
    }

    enum QueryTrack {
        SEMANTIC_CORE,
        TYPED_OVERLAP
    }

    record QueryText(String queryId, String queryText) {
    }

    record QueryTrackRow(String queryId, QueryTrack track) {
    }

    record QueryTrackInventory(
            List<QueryTrackRow> orderedRows,
            Set<String> allQueryIds,
            Set<String> semanticCoreQueryIds,
            Set<String> typedOverlapQueryIds,
            String canonicalSha256) {

        QueryTrackInventory {
            orderedRows = List.copyOf(orderedRows);
            allQueryIds = Set.copyOf(allQueryIds);
            semanticCoreQueryIds = Set.copyOf(semanticCoreQueryIds);
            typedOverlapQueryIds = Set.copyOf(typedOverlapQueryIds);
        }
    }

    record PreRetrievalInventory(
            QueryTrackInventory inventory,
            List<RuntimeSlice> stressRuntime) {

        PreRetrievalInventory {
            stressRuntime = List.copyOf(stressRuntime);
        }
    }

    record SuiteReport(
            String suite,
            String datasetVersion,
            String sourceArtifactSha256,
            String candidateFreezeSha256,
            int queryCount,
            long candidateCount,
            AggregateMetrics allQueryDiagnosticAggregate,
            AggregateMetrics semanticCoreAggregate,
            AggregateMetrics typedOverlapDiagnosticAggregate,
            MacroMetrics directUserMacro,
            Map<String, AggregateMetrics> professionSlices,
            Map<String, AggregateMetrics> languageSlices,
            Map<String, AggregateMetrics> categorySlices,
            List<QueryResult> queries) {

        SuiteReport {
            professionSlices = Map.copyOf(professionSlices);
            languageSlices = Map.copyOf(languageSlices);
            categorySlices = Map.copyOf(categorySlices);
            queries = List.copyOf(queries);
        }
    }

    record CombinedReport(
            String queryTrackInventorySha256,
            int allQueryCount,
            AggregateMetrics allQueryDiagnosticAggregate,
            AggregateMetrics semanticCoreAggregate,
            MacroMetrics directUserMacro,
            Map<String, AggregateMetrics> userSlices,
            Map<String, AggregateMetrics> professionSlices,
            Map<String, AggregateMetrics> languageSlices,
            Map<String, AggregateMetrics> categorySlices,
            Map<String, AggregateMetrics> semanticFocusSlices,
            Map<String, Long> answerabilityCounts,
            TypedOverlapDiagnostic typedOverlap) {

        CombinedReport {
            userSlices = Map.copyOf(userSlices);
            professionSlices = Map.copyOf(professionSlices);
            languageSlices = Map.copyOf(languageSlices);
            categorySlices = Map.copyOf(categorySlices);
            semanticFocusSlices = Map.copyOf(semanticFocusSlices);
            answerabilityCounts = Map.copyOf(answerabilityCounts);
        }
    }

    record TypedOverlapDiagnostic(
            int queryCount,
            List<String> queryIds,
            AggregateMetrics aggregate,
            MacroMetrics directUserMacro,
            Map<String, AggregateMetrics> professionSlices,
            Map<String, AggregateMetrics> languageSlices,
            String decisionPolicy) {

        TypedOverlapDiagnostic {
            queryIds = List.copyOf(queryIds);
            professionSlices = Map.copyOf(professionSlices);
            languageSlices = Map.copyOf(languageSlices);
        }
    }

    record SealedMetadata(
            String combinedSha256,
            boolean opened,
            boolean searchExecuted,
            boolean mutable) {
    }

    record ProcessResult(int exitCode, String output) {
    }

    record BenchmarkReport(
            int schemaVersion,
            String phase,
            String codeFreezeCommit,
            List<String> dependencyCommits,
            OllamaBgeM3EmbeddingClient.ModelMetadata model,
            String baseline,
            String oraclePolicy,
            String goldBoundary,
            String stressInputSha256,
            String stressInputGitTree,
            List<SuiteReport> suites,
            CombinedReport combined,
            DecisionAssessment decision,
            SealedMetadata sealedFinal,
            String currentFreshBaseline) {

        BenchmarkReport {
            dependencyCommits = List.copyOf(dependencyCommits);
            suites = List.copyOf(suites);
        }
    }
}
