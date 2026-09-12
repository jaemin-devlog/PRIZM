package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.prizm.search.evaluation.searchv3.structural.Prz030SemanticEvidenceValidationCeilingBenchmarkTest.FrozenSuite;
import com.prizm.search.evaluation.searchv3.structural.Prz030SemanticEvidenceValidationCeilingBenchmarkTest.GoldFreeCandidateInventory;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.CandidateProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvaluationTrack;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.FrozenCandidates;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.QueryProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.FrozenInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.Input;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.InputVerification;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.QueryInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.RunContract;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.SourceSuite;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Official, opt-in Gold-free input materialization for PRZ-031. */
class Prz031SemanticDirectnessInputBenchmarkTest {

    static final String CODE_FREEZE_PROPERTY = "prizm.prz031.code-freeze-commit";
    static final String CONTRACT_SHA_PROPERTY = "prizm.prz031.contract-sha256";
    static final Path CONTRACT = Path.of(
            "specs/PRZ-031-semantic-evidence-directness/execution-contract.json");
    static final Path CANDIDATE_OUTPUT = Path.of(
            "local/search-v3-evaluation/prz031/b3-candidate-freeze.json");
    static final Path MODEL_INPUT_OUTPUT = Path.of(
            "local/search-v3-evaluation/prz031/semantic-directness-input.json");
    static final String V2_CODE_FREEZE_PROPERTY = "prizm.prz031.d2-code-freeze-commit";
    static final String V2_CONTRACT_SHA_PROPERTY = "prizm.prz031.d2-contract-sha256";
    static final Path CONTRACT_V2 = Path.of(
            "specs/PRZ-031-semantic-evidence-directness/execution-contract-v2.json");
    static final Path MODEL_INPUT_V2_OUTPUT = Path.of(
            "local/search-v3-evaluation/prz031/semantic-directness-v2-input.json");
    static final Path SEALED_MANIFEST = Path.of(
            "src/test/resources/search-v3-evaluation/sealed-final/manifest.json");
    static final String SEALED_GIT_PATH =
            "src/test/resources/search-v3-evaluation/sealed-final";
    static final String SEALED_TREE = "a129080861d7dafd32a9b3b3357b61aebb237e59";
    static final String SEALED_MANIFEST_SHA256 =
            "d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa";
    static final String SEALED_COMBINED_SHA256 =
            "e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383";
    static final String CURRENT_FRESH_BASELINE = "NOT_RUN";
    static final int FULL_QUERY_COUNT = 93;
    static final int FULL_CANDIDATE_COUNT = 751;
    static final String D1_CANDIDATE_FILE_SHA256 =
            "708f8f647a57a3b42a55a9c11ac76d925646491d5bee1997e052f6690e77107a";
    static final String D1_INPUT_FILE_SHA256 =
            "b91c6864f809560ee486cd00cad2a21ec7aae02844fa51a902a842e909943671";
    static final String D1_INPUT_CANONICAL_SHA256 =
            "4242e751831cb59d1a2c9849a1063f6a6044bae87f2a6cbdbce168acedfd6359";
    static final String D1_GUARD_CONTRACT_SHA256 =
            "237537ffb08179e10f579203b0681cf9c4040791b059cb9152b5ced1e6442d20";
    static final String D1_CANDIDATE_PAYLOAD_SHA256 =
            "5e4863f245f258dcdc96eed755bf17159ae55c5711ec2b967b6169ee000b885f";
    private static final String D1_CODE_FREEZE_COMMIT =
            "3d1f57b969d97d1b73a2531ba990cd9beaed57db";
    private static final String D1_CONTRACT_SHA256 =
            "aa683f4cecb21c90d91d43c7b77bb31cb2f98fe0cd8c7a2c916962eef620d77e";
    private static final String D2_ARTIFACT_TYPE =
            "PRZ031_SEMANTIC_DIRECTNESS_PROTOCOL_V2_INPUT";
    private static final String D2_PROTOCOL = "SEMANTIC_DIRECTNESS_PROTOCOL_V2";
    private static final Pattern COMMIT_SHA = Pattern.compile("^[0-9a-f]{40}$");
    private static final Map<String, String> EXPECTED_SUITE_FREEZES = Map.of(
            "originalSeed", "fe69d2cbbc3d679b49e449d5d2b7a4c7387069d3d0b29b43df8772dc76be6d79",
            "longFormExpansion", "0935f6eeaad188005011d25374f012b66e843f34b7653a1ec981645a4e182570",
            "independentRobustness", "20346aea334c7cb662dd459b7ca5b8e44a3a4dffa4382006f892c0c99fd0fba9",
            SearchV3SemanticOracleDataset.STRESS_SUITE,
            "ee3142abfe2097799f03998cb6b7acfd35ebc0c70a58618c43c33cd8ab709da8");
    private static final List<Path> SOURCE_ONLY_QUERY_FILES = List.of(
            Path.of("src/test/resources/search-v3-evaluation/dev/questions.json"),
            Path.of("src/test/resources/search-v3-evaluation/calibration/questions.json"),
            Path.of("src/searchEvaluation/resources/search-v3-evaluation/devcal-1.1.0/dev/questions.json"),
            Path.of("src/searchEvaluation/resources/search-v3-evaluation/devcal-1.1.0/calibration/questions.json"),
            Path.of("src/searchEvaluation/resources/search-v3-evaluation/devcal-robustness-1.0.0/dev/questions.json"),
            Path.of("src/searchEvaluation/resources/search-v3-evaluation/devcal-robustness-1.0.0/calibration/questions.json"),
            Path.of("src/searchEvaluation/resources/search-v3-evaluation/semantic-support-stress-1.0.1/dev/runtime-questions.json"),
            Path.of("src/searchEvaluation/resources/search-v3-evaluation/semantic-support-stress-1.0.1/calibration/runtime-questions.json"));
    private static final Set<String> FORBIDDEN_GOLD_FIELDS = Set.of(
            "answerability",
            "aspectExpression",
            "aspects",
            "categories",
            "category",
            "expectedAnswer",
            "expectedEvidence",
            "gold",
            "goldParent",
            "goldRelation",
            "oracle",
            "questionGroupId",
            "relation",
            "safetyExclusions",
            "supportRelation");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void freezesGoldFreeCandidateAndSemanticModelInput() throws Exception {
        String codeFreeze = System.getProperty(CODE_FREEZE_PROPERTY, "");
        String expectedContractSha = System.getProperty(CONTRACT_SHA_PROPERTY, "");
        assumeTrue(!codeFreeze.isBlank(), "PRZ-031 input materialization is opt-in");
        assertThat(codeFreeze).matches(COMMIT_SHA);
        assertThat(expectedContractSha).matches("^[0-9a-f]{64}$");
        verifyRepositoryBeforeRun(codeFreeze, expectedContractSha);
        assertThat(CANDIDATE_OUTPUT).doesNotExist();
        assertThat(MODEL_INPUT_OUTPUT).doesNotExist();

        SealedMetadata sealedBefore = sealedMetadata();
        assertThat(sealedBefore).isEqualTo(expectedSealedMetadata());
        String sealedFileHash = sha256(SEALED_MANIFEST);
        String sealedTree = git("rev-parse", "HEAD:" + SEALED_GIT_PATH);
        assertThat(sealedFileHash).isEqualTo(SEALED_MANIFEST_SHA256);
        assertThat(sealedTree).isEqualTo(SEALED_TREE);

        GoldFreeCandidateInventory inventory =
                new Prz030SemanticEvidenceValidationCeilingBenchmarkTest()
                        .freezeGoldFreeCandidatesForDownstream();
        List<SuiteSnapshot> snapshots = snapshots(inventory.suites());
        CandidateArtifact candidateArtifact = new CandidateArtifact(
                1,
                "PRZ031_B3_CANDIDATE_FREEZE",
                codeFreeze,
                expectedContractSha,
                SearchV3B3CandidateReplay.BGE_M3_DIGEST,
                FULL_QUERY_COUNT,
                FULL_CANDIDATE_COUNT,
                snapshots);
        byte[] candidateBytes = artifactBytes(candidateArtifact, "PRZ031_B3_CANDIDATE_FREEZE");
        String candidateFileSha = sha256(candidateBytes);

        Map<String, SourceOnlyQuery> queryText = sourceOnlyQueryProjection();
        assertThat(queryText).hasSize(FULL_QUERY_COUNT);
        Set<String> semanticIds = inventory.queryTracks().semanticCoreQueryIds();
        Set<String> typedIds = inventory.queryTracks().typedOverlapQueryIds();
        assertThat(semanticIds).hasSize(SearchV3SemanticDirectnessPredictionFreeze.SEMANTIC_QUERY_COUNT);
        assertThat(typedIds).hasSize(Prz030SemanticEvidenceValidationCeilingBenchmarkTest.TYPED_OVERLAP_QUERY_COUNT);

        ExecutionContract execution = executionContract(expectedContractSha);
        List<QueryInput> semanticQueries = semanticQueries(snapshots, semanticIds, queryText);
        Input input = new Input(
                SearchV3SemanticDirectnessPredictionFreeze.SCHEMA_VERSION,
                execution.runContract(),
                SearchV3SemanticDirectnessPredictionFreeze.expectedSourceSuites(),
                semanticQueries);
        SearchV3SemanticDirectnessPredictionFreeze.PhaseGuard guard =
                new SearchV3SemanticDirectnessPredictionFreeze.PhaseGuard();
        FrozenInput frozen = guard.freezeInput(input);
        InputVerification verified = guard.verifyInput();
        assertThat(verified.inferencePairCount())
                .isEqualTo(SearchV3SemanticDirectnessPredictionFreeze.INFERENCE_PAIR_COUNT);

        List<ModelInputQuery> modelQueries = frozen.input().queries().stream()
                .map(ModelInputQuery::from)
                .toList();
        ModelInputArtifact modelInput = new ModelInputArtifact(
                1,
                "PRZ031_SEMANTIC_DIRECTNESS_INPUT",
                codeFreeze,
                expectedContractSha,
                candidateFileSha,
                SearchV3B3CandidateReplay.BGE_M3_DIGEST,
                frozen.canonicalSha256(),
                frozen.canonicalByteLength(),
                frozen.contractSha256(),
                verified.semanticQueryCount(),
                verified.candidateCount(),
                verified.inferencePairCount(),
                verified.typedQueryCount(),
                CURRENT_FRESH_BASELINE,
                frozen.input().contract(),
                frozen.input().sourceSuites(),
                modelQueries);
        byte[] modelInputBytes = artifactBytes(modelInput, "PRZ031_SEMANTIC_DIRECTNESS_INPUT");
        writePairCreateNew(CANDIDATE_OUTPUT, candidateBytes, MODEL_INPUT_OUTPUT, modelInputBytes);

        assertThat(sha256(SEALED_MANIFEST)).isEqualTo(sealedFileHash);
        assertThat(git("rev-parse", "HEAD:" + SEALED_GIT_PATH)).isEqualTo(sealedTree);
        assertThat(sealedMetadata()).isEqualTo(sealedBefore);
        assertThat(git("rev-parse", "HEAD")).isEqualTo(codeFreeze);
        assertThat(git("status", "--porcelain")).isEmpty();

        System.out.println("PRZ031_CANDIDATE_FREEZE=" + CANDIDATE_OUTPUT.toAbsolutePath().normalize());
        System.out.println("PRZ031_CANDIDATE_FREEZE_FILE_SHA256=" + candidateFileSha);
        System.out.println("PRZ031_MODEL_INPUT=" + MODEL_INPUT_OUTPUT.toAbsolutePath().normalize());
        System.out.println("PRZ031_MODEL_INPUT_FILE_SHA256=" + sha256(MODEL_INPUT_OUTPUT));
        System.out.println("PRZ031_MODEL_INPUT_CANONICAL_SHA256=" + frozen.canonicalSha256());
        System.out.println("PRZ031_CURRENT_FRESH_BASELINE=" + CURRENT_FRESH_BASELINE);
    }

    @Test
    void verifiesExactFrozenD1ReplayWithoutBgeGoldOrDatasetAccess() throws Exception {
        assumeTrue(
                Files.isRegularFile(CANDIDATE_OUTPUT) && Files.isRegularFile(MODEL_INPUT_OUTPUT),
                "frozen D1 local artifacts are unavailable");
        SealedMetadata sealedBefore = sealedMetadata();
        String sealedFileHash = sha256(SEALED_MANIFEST);
        String candidateFileHash = sha256(CANDIDATE_OUTPUT);
        String inputFileHash = sha256(MODEL_INPUT_OUTPUT);
        boolean d2Existed = Files.exists(MODEL_INPUT_V2_OUTPUT);
        String d2FileHash = d2Existed ? sha256(MODEL_INPUT_V2_OUTPUT) : null;

        D1Replay replay = verifiedD1Replay();

        assertThat(replay.candidatePayloadSha256()).isEqualTo(D1_CANDIDATE_PAYLOAD_SHA256);
        assertThat(replay.input().semanticQueryCount())
                .isEqualTo(SearchV3SemanticDirectnessPredictionFreeze.SEMANTIC_QUERY_COUNT);
        assertThat(replay.input().candidateCount())
                .isEqualTo(SearchV3SemanticDirectnessPredictionFreeze.SEMANTIC_CANDIDATE_COUNT);
        assertThat(replay.input().inferencePairCount())
                .isEqualTo(SearchV3SemanticDirectnessPredictionFreeze.INFERENCE_PAIR_COUNT);
        assertThat(replay.input().typedQueryCount())
                .isEqualTo(SearchV3SemanticDirectnessPredictionFreeze.TYPED_QUERY_COUNT);
        assertThat(sha256(CANDIDATE_OUTPUT)).isEqualTo(candidateFileHash);
        assertThat(sha256(MODEL_INPUT_OUTPUT)).isEqualTo(inputFileHash);
        assertThat(Files.exists(MODEL_INPUT_V2_OUTPUT)).isEqualTo(d2Existed);
        if (d2Existed) assertThat(sha256(MODEL_INPUT_V2_OUTPUT)).isEqualTo(d2FileHash);
        assertThat(sha256(SEALED_MANIFEST)).isEqualTo(sealedFileHash);
        assertThat(sealedMetadata()).isEqualTo(sealedBefore).isEqualTo(expectedSealedMetadata());
    }

    @Test
    void v2ContractPinsTheExactD1ReplaySourceAndKeepsArtifactsLocal() throws Exception {
        JsonNode contract = mapper.readTree(Files.readString(CONTRACT_V2, StandardCharsets.UTF_8));

        assertD2ContractSource(contract);
        assertThat(MODEL_INPUT_V2_OUTPUT.toString().replace('\\', '/'))
                .startsWith("local/search-v3-evaluation/prz031/");
        assertThat(sha256(SEALED_MANIFEST)).isEqualTo(SEALED_MANIFEST_SHA256);
        assertThat(sealedMetadata()).isEqualTo(expectedSealedMetadata());
    }

    @Test
    void materializesProtocolV2InputByReplayingOnlyTheVerifiedD1Payload() throws Exception {
        String codeFreeze = System.getProperty(V2_CODE_FREEZE_PROPERTY, "");
        String expectedContractSha = System.getProperty(V2_CONTRACT_SHA_PROPERTY, "");
        assumeTrue(!codeFreeze.isBlank(), "PRZ-031 protocol-v2 input materialization is opt-in");
        assertThat(codeFreeze).matches(COMMIT_SHA);
        assertThat(expectedContractSha).matches("^[0-9a-f]{64}$");
        verifyD2RepositoryBeforeRun(codeFreeze, expectedContractSha);
        assertThat(MODEL_INPUT_V2_OUTPUT).doesNotExist();

        SealedMetadata sealedBefore = sealedMetadata();
        String sealedFileHash = sha256(SEALED_MANIFEST);
        String sealedTree = git("rev-parse", "HEAD:" + SEALED_GIT_PATH);
        assertThat(sealedBefore).isEqualTo(expectedSealedMetadata());
        assertThat(sealedFileHash).isEqualTo(SEALED_MANIFEST_SHA256);
        assertThat(sealedTree).isEqualTo(SEALED_TREE);

        D1Replay replay = verifiedD1Replay();
        ExecutionContract execution = executionContract(CONTRACT_V2, expectedContractSha);
        JsonNode contractDocument = mapper.readTree(Files.readString(CONTRACT_V2, StandardCharsets.UTF_8));
        assertD2ContractSource(contractDocument);

        List<QueryInput> replayedQueries = replay.input().queries().stream()
                .map(value -> new QueryInput(
                        value.suite(),
                        value.datasetVersion(),
                        value.split(),
                        value.queryId(),
                        value.userBundleId(),
                        value.language(),
                        EvaluationTrack.SEMANTIC,
                        value.queryText(),
                        value.queryTextSha256(),
                        value.candidates()))
                .toList();
        Input input = new Input(
                SearchV3SemanticDirectnessPredictionFreeze.SCHEMA_VERSION,
                execution.runContract(),
                replay.input().sourceSuites(),
                replayedQueries);
        SearchV3SemanticDirectnessPredictionFreeze.PhaseGuard guard =
                new SearchV3SemanticDirectnessPredictionFreeze.PhaseGuard();
        FrozenInput frozen = guard.freezeInput(input);
        InputVerification verified = guard.verifyInput();
        List<ModelInputQuery> v2Queries = frozen.input().queries().stream()
                .map(ModelInputQuery::from)
                .toList();

        assertThat(v2Queries).isEqualTo(replay.input().queries());
        assertThat(candidatePayloadSha256(v2Queries)).isEqualTo(replay.candidatePayloadSha256());
        assertThat(verified.semanticQueryCount())
                .isEqualTo(SearchV3SemanticDirectnessPredictionFreeze.SEMANTIC_QUERY_COUNT);
        assertThat(verified.candidateCount())
                .isEqualTo(SearchV3SemanticDirectnessPredictionFreeze.SEMANTIC_CANDIDATE_COUNT);
        assertThat(verified.inferencePairCount())
                .isEqualTo(SearchV3SemanticDirectnessPredictionFreeze.INFERENCE_PAIR_COUNT);
        assertThat(verified.typedQueryCount())
                .isEqualTo(SearchV3SemanticDirectnessPredictionFreeze.TYPED_QUERY_COUNT);

        ProtocolV2InputArtifact artifact = new ProtocolV2InputArtifact(
                SearchV3SemanticDirectnessPredictionFreeze.SCHEMA_VERSION,
                D2_ARTIFACT_TYPE,
                D2_PROTOCOL,
                codeFreeze,
                execution.fileSha256(),
                D1_CANDIDATE_FILE_SHA256,
                D1_INPUT_FILE_SHA256,
                D1_INPUT_CANONICAL_SHA256,
                D1_GUARD_CONTRACT_SHA256,
                replay.candidatePayloadSha256(),
                frozen.canonicalSha256(),
                frozen.canonicalByteLength(),
                frozen.contractSha256(),
                verified.semanticQueryCount(),
                verified.candidateCount(),
                verified.inferencePairCount(),
                verified.typedQueryCount(),
                CURRENT_FRESH_BASELINE,
                frozen.input().contract(),
                frozen.input().sourceSuites(),
                v2Queries);
        byte[] artifactBytes = artifactBytes(artifact, D2_ARTIFACT_TYPE);
        Files.createDirectories(MODEL_INPUT_V2_OUTPUT.getParent());
        Files.write(
                MODEL_INPUT_V2_OUTPUT,
                artifactBytes,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);

        assertThat(sha256(CANDIDATE_OUTPUT)).isEqualTo(D1_CANDIDATE_FILE_SHA256);
        assertThat(sha256(MODEL_INPUT_OUTPUT)).isEqualTo(D1_INPUT_FILE_SHA256);
        assertThat(sha256(SEALED_MANIFEST)).isEqualTo(sealedFileHash);
        assertThat(git("rev-parse", "HEAD:" + SEALED_GIT_PATH)).isEqualTo(sealedTree);
        assertThat(sealedMetadata()).isEqualTo(sealedBefore);
        assertThat(git("rev-parse", "HEAD")).isEqualTo(codeFreeze);
        assertThat(git("status", "--porcelain")).isEmpty();

        System.out.println("PRZ031_PROTOCOL_V2_INPUT="
                + MODEL_INPUT_V2_OUTPUT.toAbsolutePath().normalize());
        System.out.println("PRZ031_PROTOCOL_V2_INPUT_FILE_SHA256="
                + sha256(MODEL_INPUT_V2_OUTPUT));
        System.out.println("PRZ031_PROTOCOL_V2_INPUT_CANONICAL_SHA256="
                + frozen.canonicalSha256());
        System.out.println("PRZ031_PROTOCOL_V2_CANDIDATE_PAYLOAD_SHA256="
                + replay.candidatePayloadSha256());
    }

    @Test
    void policyKeepsArtifactsLocalAndSealedFinalClosed() throws Exception {
        assertThat(CANDIDATE_OUTPUT.toString().replace('\\', '/'))
                .startsWith("local/search-v3-evaluation/prz031/");
        assertThat(MODEL_INPUT_OUTPUT.toString().replace('\\', '/'))
                .startsWith("local/search-v3-evaluation/prz031/");
        assertThat(SOURCE_ONLY_QUERY_FILES).allSatisfy(path -> {
            String portable = path.toString().replace('\\', '/').toLowerCase();
            assertThat(portable).doesNotContain("sealed-final", "sealed_final");
            assertThat(path).exists().isRegularFile();
        });
        assertThat(sha256(SEALED_MANIFEST)).isEqualTo(SEALED_MANIFEST_SHA256);
        assertThat(sealedMetadata()).isEqualTo(expectedSealedMetadata());
        JsonNode contract = mapper.readTree(Files.readString(CONTRACT, StandardCharsets.UTF_8));
        assertThat(contract.path("model").path("upstreamFileSha256").asText())
                .isEqualTo("7485fe6f11af29433bc51cab58009521f205840f5b4ae3a32fa7f92e8534fdf5");
        assertThat(contract.path("inferenceConfig").path("topKCandidatesPerQuery").asInt()).isEqualTo(10);
    }

    @Test
    void inferenceArtifactRejectsGoldFieldsBeforeAnyWrite() {
        Map<String, Object> contaminated = Map.of(
                "artifactType", "PRZ031_SEMANTIC_DIRECTNESS_INPUT",
                "queries", List.of(Map.of(
                        "queryId", "Q1",
                        "answerability", "SUPPORTED")));

        assertThatThrownBy(() -> artifactBytes(contaminated, "PRZ031_SEMANTIC_DIRECTNESS_INPUT"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Gold field reached inference artifact: answerability");
    }

    @Test
    void pairedCreateNewDoesNotLeaveFirstArtifactWhenSecondAlreadyExists(@TempDir Path tempDir)
            throws IOException {
        Path first = tempDir.resolve("candidate.json");
        Path second = tempDir.resolve("model-input.json");
        Files.writeString(second, "existing", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);

        assertThatThrownBy(() -> writePairCreateNew(
                first,
                "candidate".getBytes(StandardCharsets.UTF_8),
                second,
                "model-input".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(FileAlreadyExistsException.class);
        assertThat(first).doesNotExist();
        assertThat(Files.readString(second, StandardCharsets.UTF_8)).isEqualTo("existing");
    }

    private List<SuiteSnapshot> snapshots(List<FrozenSuite> suites) {
        List<SuiteSnapshot> result = new ArrayList<>();
        int queries = 0;
        long candidates = 0;
        Set<String> names = new LinkedHashSet<>();
        for (FrozenSuite suite : suites) {
            FrozenCandidates frozen = suite.verified().frozen();
            assertThat(SearchV3CandidateFreeze.verify(frozen).frozen()).isEqualTo(frozen);
            assertThat(frozen.canonicalSha256())
                    .isEqualTo(EXPECTED_SUITE_FREEZES.get(suite.suite()));
            assertThat(names.add(suite.suite())).isTrue();
            result.add(new SuiteSnapshot(suite.suite(), frozen));
            queries += frozen.input().queries().size();
            candidates += frozen.input().queries().stream()
                    .mapToLong(value -> value.rankedCandidates().size()).sum();
        }
        assertThat(names).containsExactlyInAnyOrderElementsOf(EXPECTED_SUITE_FREEZES.keySet());
        assertThat(queries).isEqualTo(FULL_QUERY_COUNT);
        assertThat(candidates).isEqualTo(FULL_CANDIDATE_COUNT);
        return List.copyOf(result);
    }

    private List<QueryInput> semanticQueries(
            List<SuiteSnapshot> suites,
            Set<String> semanticIds,
            Map<String, SourceOnlyQuery> sourceOnly) {
        List<QueryInput> result = new ArrayList<>();
        for (SuiteSnapshot suite : suites) {
            for (QueryProjection query : suite.frozen().input().queries()) {
                if (!semanticIds.contains(query.queryId())) continue;
                SourceOnlyQuery text = sourceOnly.get(query.queryId());
                if (text == null
                        || !text.userBundleId().equals(query.userBundleId())
                        || !text.split().equals(query.split())) {
                    throw new IllegalStateException("source-only query identity drifted: " + query.queryId());
                }
                result.add(new QueryInput(
                        suite.suite(),
                        suite.frozen().input().datasetVersion(),
                        query.split(),
                        query.queryId(),
                        query.userBundleId(),
                        text.language(),
                        EvaluationTrack.SEMANTIC,
                        text.queryText(),
                        SearchV3SemanticDirectnessPredictionFreeze.sha256(text.queryText()),
                        query.rankedCandidates()));
            }
        }
        if (result.size() != semanticIds.size()
                || !result.stream().map(QueryInput::queryId).collect(Collectors.toSet()).equals(semanticIds)) {
            throw new IllegalStateException("semantic source-only query projection drifted");
        }
        return List.copyOf(result);
    }

    private Map<String, SourceOnlyQuery> sourceOnlyQueryProjection() throws IOException {
        Map<String, SourceOnlyQuery> result = new LinkedHashMap<>();
        for (Path path : SOURCE_ONLY_QUERY_FILES) {
            String portable = path.toString().replace('\\', '/').toLowerCase();
            if (portable.contains("sealed-final") || portable.contains("sealed_final")) {
                throw new IllegalArgumentException("SEALED FINAL query access is forbidden");
            }
            String split = portable.contains("/calibration/") ? "CALIBRATION" : "DEV";
            SourceOnlyQuestionFile source = mapper.readerFor(SourceOnlyQuestionFile.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(Files.readString(path, StandardCharsets.UTF_8));
            for (SourceOnlyQuestion node : source.queries()) {
                SourceOnlyQuery query = new SourceOnlyQuery(
                        required(node.queryId(), "queryId"),
                        required(node.userBundleId(), "userBundleId"),
                        split,
                        required(node.query(), "query"),
                        required(node.language(), "language"));
                if (result.put(query.queryId(), query) != null) {
                    throw new IllegalStateException("duplicate source-only query ID: " + query.queryId());
                }
            }
        }
        return Map.copyOf(result);
    }

    private D1Replay verifiedD1Replay() throws IOException {
        CandidateArtifact candidate = readVerifiedArtifact(
                CANDIDATE_OUTPUT, D1_CANDIDATE_FILE_SHA256, CandidateArtifact.class);
        ModelInputArtifact input = readVerifiedArtifact(
                MODEL_INPUT_OUTPUT, D1_INPUT_FILE_SHA256, ModelInputArtifact.class);
        if (candidate.schemaVersion() != 1
                || !"PRZ031_B3_CANDIDATE_FREEZE".equals(candidate.artifactType())
                || !D1_CODE_FREEZE_COMMIT.equals(candidate.codeFreezeCommit())
                || !D1_CONTRACT_SHA256.equals(candidate.contractSha256())
                || !SearchV3B3CandidateReplay.BGE_M3_DIGEST.equals(candidate.bgeM3Digest())
                || candidate.queryCount() != FULL_QUERY_COUNT
                || candidate.candidateCount() != FULL_CANDIDATE_COUNT) {
            throw new IllegalStateException("frozen D1 candidate envelope drifted");
        }
        if (input.schemaVersion() != 1
                || !"PRZ031_SEMANTIC_DIRECTNESS_INPUT".equals(input.artifactType())
                || !D1_CODE_FREEZE_COMMIT.equals(input.codeFreezeCommit())
                || !D1_CONTRACT_SHA256.equals(input.contractSha256())
                || !D1_CANDIDATE_FILE_SHA256.equals(input.candidateFreezeFileSha256())
                || !SearchV3B3CandidateReplay.BGE_M3_DIGEST.equals(input.bgeM3Digest())
                || !D1_INPUT_CANONICAL_SHA256.equals(input.inputCanonicalSha256())
                || !D1_GUARD_CONTRACT_SHA256.equals(input.guardContractSha256())
                || input.semanticQueryCount()
                        != SearchV3SemanticDirectnessPredictionFreeze.SEMANTIC_QUERY_COUNT
                || input.candidateCount()
                        != SearchV3SemanticDirectnessPredictionFreeze.SEMANTIC_CANDIDATE_COUNT
                || input.inferencePairCount()
                        != SearchV3SemanticDirectnessPredictionFreeze.INFERENCE_PAIR_COUNT
                || input.typedQueryCount()
                        != SearchV3SemanticDirectnessPredictionFreeze.TYPED_QUERY_COUNT
                || !CURRENT_FRESH_BASELINE.equals(input.currentFreshBaseline())) {
            throw new IllegalStateException("frozen D1 semantic input envelope drifted");
        }
        if (!input.sourceSuites()
                .equals(SearchV3SemanticDirectnessPredictionFreeze.expectedSourceSuites())) {
            throw new IllegalStateException("frozen D1 source-suite identity drifted");
        }

        Map<String, SuiteSnapshot> suiteByName = new LinkedHashMap<>();
        Map<String, QueryProjection> candidateQueryByIdentity = new LinkedHashMap<>();
        int fullQueryCount = 0;
        long fullCandidateCount = 0;
        for (SuiteSnapshot snapshot : candidate.suites()) {
            FrozenCandidates frozen = SearchV3CandidateFreeze.verify(snapshot.frozen()).frozen();
            if (!snapshot.suite().equals(frozen.input().suite())
                    || !EXPECTED_SUITE_FREEZES.containsKey(snapshot.suite())
                    || !EXPECTED_SUITE_FREEZES.get(snapshot.suite()).equals(frozen.canonicalSha256())
                    || suiteByName.put(snapshot.suite(), snapshot) != null) {
                throw new IllegalStateException("frozen D1 candidate suite identity drifted");
            }
            fullQueryCount += frozen.input().queries().size();
            fullCandidateCount += frozen.input().queries().stream()
                    .mapToLong(value -> value.rankedCandidates().size())
                    .sum();
            for (QueryProjection query : frozen.input().queries()) {
                String key = queryIdentity(snapshot.suite(), query.queryId());
                if (candidateQueryByIdentity.put(key, query) != null) {
                    throw new IllegalStateException("duplicate frozen D1 candidate query: " + key);
                }
            }
        }
        if (!suiteByName.keySet().equals(EXPECTED_SUITE_FREEZES.keySet())
                || fullQueryCount != FULL_QUERY_COUNT
                || fullCandidateCount != FULL_CANDIDATE_COUNT) {
            throw new IllegalStateException("frozen D1 candidate inventory drifted");
        }

        Set<String> queryIds = new LinkedHashSet<>();
        long semanticCandidateCount = 0;
        long inferencePairCount = 0;
        for (ModelInputQuery query : input.queries()) {
            if (!queryIds.add(query.queryId())
                    || !query.queryTextSha256().equals(
                            SearchV3SemanticDirectnessPredictionFreeze.sha256(query.queryText()))) {
                throw new IllegalStateException("frozen D1 semantic query identity drifted: "
                        + query.queryId());
            }
            SuiteSnapshot snapshot = suiteByName.get(query.suite());
            QueryProjection candidateQuery =
                    candidateQueryByIdentity.get(queryIdentity(query.suite(), query.queryId()));
            if (snapshot == null
                    || candidateQuery == null
                    || !query.datasetVersion().equals(snapshot.frozen().input().datasetVersion())
                    || !query.userBundleId().equals(candidateQuery.userBundleId())
                    || !query.split().equals(candidateQuery.split())
                    || candidateQuery.track() != EvaluationTrack.SEMANTIC
                    || !query.candidates().equals(candidateQuery.rankedCandidates())) {
                throw new IllegalStateException(
                        "D1 input/query/candidate projection parity failed: " + query.queryId());
            }
            semanticCandidateCount += query.candidates().size();
            inferencePairCount += Math.min(
                    SearchV3SemanticDirectnessPredictionFreeze.INFERENCE_TOP_K,
                    query.candidates().size());
        }
        if (queryIds.size() != SearchV3SemanticDirectnessPredictionFreeze.SEMANTIC_QUERY_COUNT
                || semanticCandidateCount
                        != SearchV3SemanticDirectnessPredictionFreeze.SEMANTIC_CANDIDATE_COUNT
                || inferencePairCount
                        != SearchV3SemanticDirectnessPredictionFreeze.INFERENCE_PAIR_COUNT) {
            throw new IllegalStateException("frozen D1 semantic replay inventory drifted");
        }
        String payloadSha256 = candidatePayloadSha256(input.queries());
        if (!D1_CANDIDATE_PAYLOAD_SHA256.equals(payloadSha256)) {
            throw new IllegalStateException("frozen D1 full candidate payload hash drifted");
        }
        return new D1Replay(candidate, input, payloadSha256);
    }

    private <T> T readVerifiedArtifact(Path path, String expectedSha256, Class<T> type)
            throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (!expectedSha256.equals(sha256(bytes))) {
            throw new IllegalStateException("frozen local artifact SHA-256 drifted: " + path);
        }
        return mapper.readValue(new String(bytes, StandardCharsets.UTF_8), type);
    }

    private String candidatePayloadSha256(List<ModelInputQuery> queries) throws IOException {
        return sha256(canonicalJson(mapper.valueToTree(queries)).getBytes(StandardCharsets.UTF_8));
    }

    private String queryIdentity(String suite, String queryId) {
        return suite + "\u0000" + queryId;
    }

    private void assertD2ContractSource(JsonNode contract) {
        assertThat(contract.path("schemaVersion").asInt()).isEqualTo(2);
        assertThat(required(contract, "artifactType"))
                .isEqualTo("PRZ031_SEMANTIC_DIRECTNESS_EXECUTION_CONTRACT_V2");
        assertThat(required(contract, "protocol")).isEqualTo(D2_PROTOCOL);
        JsonNode source = contract.path("sourceD1Input");
        assertThat(required(source, "artifactType")).isEqualTo("PRZ031_SEMANTIC_DIRECTNESS_INPUT");
        assertThat(required(source, "contractFileSha256")).isEqualTo(D1_CONTRACT_SHA256);
        assertThat(required(source, "candidateFreezeFileSha256"))
                .isEqualTo(D1_CANDIDATE_FILE_SHA256);
        assertThat(required(source, "inputFileSha256")).isEqualTo(D1_INPUT_FILE_SHA256);
        assertThat(required(source, "inputCanonicalSha256")).isEqualTo(D1_INPUT_CANONICAL_SHA256);
        assertThat(required(source, "guardContractSha256")).isEqualTo(D1_GUARD_CONTRACT_SHA256);
        assertThat(required(source, "candidatePayloadSha256"))
                .isEqualTo(D1_CANDIDATE_PAYLOAD_SHA256);
        assertThat(source.path("semanticQueryCount").asInt())
                .isEqualTo(SearchV3SemanticDirectnessPredictionFreeze.SEMANTIC_QUERY_COUNT);
        assertThat(source.path("candidateCount").asLong())
                .isEqualTo(SearchV3SemanticDirectnessPredictionFreeze.SEMANTIC_CANDIDATE_COUNT);
        assertThat(source.path("inferencePairCount").asLong())
                .isEqualTo(SearchV3SemanticDirectnessPredictionFreeze.INFERENCE_PAIR_COUNT);
        assertThat(source.path("typedQueryCount").asInt())
                .isEqualTo(SearchV3SemanticDirectnessPredictionFreeze.TYPED_QUERY_COUNT);
    }

    private ExecutionContract executionContract(String expectedFileSha) throws IOException {
        return executionContract(CONTRACT, expectedFileSha);
    }

    private ExecutionContract executionContract(Path contractPath, String expectedFileSha)
            throws IOException {
        if (!sha256(contractPath).equals(expectedFileSha)) {
            throw new IllegalStateException("execution contract file SHA drifted");
        }
        JsonNode root = mapper.readTree(Files.readString(contractPath, StandardCharsets.UTF_8));
        JsonNode model = root.path("model");
        JsonNode hashes = root.path("frozenHashes");
        String instruction = required(root, "instruction");
        String schema = canonicalJson(root.path("outputSchema"));
        String config = canonicalJson(root.path("inferenceConfig"));
        String policy = canonicalJson(root.path("rankingPolicy"));
        RunContract run = new RunContract(
                required(model, "upstreamModelId"),
                required(model, "upstreamRevision"),
                required(model, "license"),
                model.path("upstreamFileBytes").asLong(-1),
                required(model, "upstreamFileSha256"),
                instruction,
                required(hashes, "instructionSha256"),
                schema,
                required(hashes, "outputSchemaSha256"),
                config,
                required(hashes, "inferenceConfigSha256"),
                policy,
                required(hashes, "rankingPolicySha256"),
                root.path("inferenceConfig").path("topKCandidatesPerQuery").asInt(-1));
        return new ExecutionContract(run, expectedFileSha);
    }

    private String canonicalJson(JsonNode value) throws IOException {
        if (value.isObject()) {
            List<String> fields = new ArrayList<>();
            fields.addAll(value.propertyNames());
            fields.sort(Comparator.naturalOrder());
            List<String> entries = new ArrayList<>();
            for (String field : fields) {
                entries.add(mapper.writeValueAsString(field) + ":" + canonicalJson(value.path(field)));
            }
            return "{" + String.join(",", entries) + "}";
        }
        if (value.isArray()) {
            List<String> entries = new ArrayList<>();
            for (JsonNode item : value) entries.add(canonicalJson(item));
            return "[" + String.join(",", entries) + "]";
        }
        return mapper.writeValueAsString(value);
    }

    private void verifyRepositoryBeforeRun(String codeFreeze, String contractSha) throws Exception {
        assertThat(git("rev-parse", "HEAD")).isEqualTo(codeFreeze);
        assertThat(git("status", "--porcelain")).isEmpty();
        assertThat(sha256(CONTRACT)).isEqualTo(contractSha);
        assertThat(git("rev-parse", "HEAD:" + SEALED_GIT_PATH)).isEqualTo(SEALED_TREE);
    }

    private void verifyD2RepositoryBeforeRun(String codeFreeze, String contractSha) throws Exception {
        assertThat(git("rev-parse", "HEAD")).isEqualTo(codeFreeze);
        assertThat(git("status", "--porcelain")).isEmpty();
        assertThat(sha256(CONTRACT_V2)).isEqualTo(contractSha);
        assertThat(sha256(CANDIDATE_OUTPUT)).isEqualTo(D1_CANDIDATE_FILE_SHA256);
        assertThat(sha256(MODEL_INPUT_OUTPUT)).isEqualTo(D1_INPUT_FILE_SHA256);
        assertThat(git("rev-parse", "HEAD:" + SEALED_GIT_PATH)).isEqualTo(SEALED_TREE);
    }

    private SealedMetadata sealedMetadata() throws IOException {
        JsonNode manifest = mapper.readTree(Files.readString(SEALED_MANIFEST, StandardCharsets.UTF_8));
        return new SealedMetadata(
                required(manifest, "combinedSha256"),
                manifest.path("opened").asBoolean(true),
                manifest.path("searchExecuted").asBoolean(true),
                CURRENT_FRESH_BASELINE);
    }

    private SealedMetadata expectedSealedMetadata() {
        return new SealedMetadata(SEALED_COMBINED_SHA256, false, false, CURRENT_FRESH_BASELINE);
    }

    private byte[] artifactBytes(Object value, String expectedType) throws IOException {
        byte[] bytes = (mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n")
                .getBytes(StandardCharsets.UTF_8);
        JsonNode root = mapper.readTree(bytes);
        if (!expectedType.equals(required(root, "artifactType"))) {
            throw new IllegalStateException("materialized artifact type drifted");
        }
        rejectGoldFields(root);
        return bytes;
    }

    private void rejectGoldFields(JsonNode value) {
        if (value.isObject()) {
            for (String field : value.propertyNames()) {
                if (FORBIDDEN_GOLD_FIELDS.contains(field)) {
                    throw new IllegalStateException("Gold field reached inference artifact: " + field);
                }
                rejectGoldFields(value.path(field));
            }
        }
        else if (value.isArray()) {
            for (JsonNode item : value) rejectGoldFields(item);
        }
    }

    private void writePairCreateNew(
            Path first,
            byte[] firstBytes,
            Path second,
            byte[] secondBytes) throws IOException {
        Files.createDirectories(first.getParent());
        Files.createDirectories(second.getParent());
        if (Files.exists(first)) throw new FileAlreadyExistsException(first.toString());
        if (Files.exists(second)) throw new FileAlreadyExistsException(second.toString());
        String token = UUID.randomUUID().toString();
        Path firstPending = first.resolveSibling(first.getFileName() + "." + token + ".pending");
        Path secondPending = second.resolveSibling(second.getFileName() + "." + token + ".pending");
        boolean firstPublished = false;
        boolean secondPublished = false;
        try {
            Files.write(firstPending, firstBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.write(secondPending, secondBytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Files.move(firstPending, first);
            firstPublished = true;
            Files.move(secondPending, second);
            secondPublished = true;
        }
        catch (IOException | RuntimeException exception) {
            cleanupCreated(secondPending, exception);
            cleanupCreated(firstPending, exception);
            if (secondPublished) cleanupCreated(second, exception);
            if (firstPublished) cleanupCreated(first, exception);
            throw exception;
        }
    }

    private void cleanupCreated(Path path, Exception failure) {
        try {
            Files.deleteIfExists(path);
        }
        catch (IOException cleanup) {
            failure.addSuppressed(cleanup);
        }
    }

    private String required(JsonNode node, String field) {
        return required(node.path(field).asText(), field);
    }

    private String required(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalStateException("missing source-only field: " + field);
        return value;
    }

    private String git(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git failed: " + output);
        }
        return output;
    }

    private String sha256(Path path) throws IOException {
        return sha256(Files.readAllBytes(path));
    }

    private String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    record SuiteSnapshot(String suite, FrozenCandidates frozen) {
    }

    record CandidateArtifact(
            int schemaVersion,
            String artifactType,
            String codeFreezeCommit,
            String contractSha256,
            String bgeM3Digest,
            int queryCount,
            int candidateCount,
            List<SuiteSnapshot> suites) {

        CandidateArtifact {
            suites = List.copyOf(suites);
        }
    }

    record SourceOnlyQuery(
            String queryId,
            String userBundleId,
            String split,
            String queryText,
            String language) {
    }

    record SourceOnlyQuestionFile(List<SourceOnlyQuestion> queries) {

        SourceOnlyQuestionFile {
            queries = queries == null ? List.of() : List.copyOf(queries);
        }
    }

    record SourceOnlyQuestion(
            String queryId,
            String userBundleId,
            String query,
            String language) {
    }

    record ModelInputQuery(
            String suite,
            String datasetVersion,
            String queryId,
            String userBundleId,
            String split,
            String language,
            String queryText,
            String queryTextSha256,
            List<CandidateProjection> candidates) {

        ModelInputQuery {
            candidates = List.copyOf(candidates);
        }

        static ModelInputQuery from(QueryInput value) {
            return new ModelInputQuery(
                    value.suite(), value.datasetVersion(), value.queryId(), value.userBundleId(),
                    value.split(), value.language(), value.originalQuery(), value.originalQuerySha256(),
                    value.rankedCandidates());
        }
    }

    record ModelInputArtifact(
            int schemaVersion,
            String artifactType,
            String codeFreezeCommit,
            String contractSha256,
            String candidateFreezeFileSha256,
            String bgeM3Digest,
            String inputCanonicalSha256,
            int inputCanonicalByteLength,
            String guardContractSha256,
            int semanticQueryCount,
            long candidateCount,
            long inferencePairCount,
            int typedQueryCount,
            String currentFreshBaseline,
            RunContract runContract,
            List<SourceSuite> sourceSuites,
            List<ModelInputQuery> queries) {

        ModelInputArtifact {
            sourceSuites = List.copyOf(sourceSuites);
            queries = List.copyOf(queries);
        }
    }

    record ProtocolV2InputArtifact(
            int schemaVersion,
            String artifactType,
            String protocol,
            String codeFreezeCommit,
            String contractSha256,
            String candidateFreezeFileSha256,
            String sourceD1InputFileSha256,
            String sourceD1InputCanonicalSha256,
            String sourceD1GuardContractSha256,
            String candidatePayloadSha256,
            String inputCanonicalSha256,
            int inputCanonicalByteLength,
            String guardContractSha256,
            int semanticQueryCount,
            long candidateCount,
            long inferencePairCount,
            int typedQueryCount,
            String currentFreshBaseline,
            RunContract runContract,
            List<SourceSuite> sourceSuites,
            List<ModelInputQuery> queries) {

        ProtocolV2InputArtifact {
            sourceSuites = List.copyOf(sourceSuites);
            queries = List.copyOf(queries);
        }
    }

    record D1Replay(
            CandidateArtifact candidate,
            ModelInputArtifact input,
            String candidatePayloadSha256) {
    }

    record ExecutionContract(RunContract runContract, String fileSha256) {
    }

    record SealedMetadata(
            String combinedSha256,
            boolean opened,
            boolean searchExecuted,
            String currentFreshBaseline) {
    }
}
