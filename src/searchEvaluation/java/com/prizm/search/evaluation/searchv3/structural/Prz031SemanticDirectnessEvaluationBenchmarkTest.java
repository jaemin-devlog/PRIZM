package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.CandidateProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvaluationTrack;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvidenceChildProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.FrozenCandidates;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.QueryProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.VerifiedCandidates;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.QueryGold;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessOfficialEvaluationAdapter.ArtifactHashes;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessOfficialEvaluationAdapter.Decision;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessOfficialEvaluationAdapter.EvaluationReport;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessOfficialEvaluationAdapter.OfficialCost;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessOfficialEvaluationAdapter.ResourceSnapshot;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessOfficialEvaluationAdapter.SealedState;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessOfficialEvaluationAdapter.SuiteGold;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.FrozenInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.FrozenOutput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.Input;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.Output;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.Prediction;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.QueryInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.Relation;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.RunContract;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.SourceSuite;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Opt-in official PRZ-031 Gold-after-output evaluation shell plus its offline policy tests. */
class Prz031SemanticDirectnessEvaluationBenchmarkTest {

    static final String RUN_PROPERTY = "prizm.prz031.d2-official-evaluation";
    static final String CODE_FREEZE_PROPERTY =
            Prz031SemanticDirectnessInputBenchmarkTest.V2_CODE_FREEZE_PROPERTY;
    static final String CONTRACT_FILE_SHA_PROPERTY =
            Prz031SemanticDirectnessInputBenchmarkTest.V2_CONTRACT_SHA_PROPERTY;
    static final String CANDIDATE_FILE_SHA_PROPERTY = "prizm.prz031.candidate-file-sha256";
    static final String INPUT_FILE_SHA_PROPERTY = "prizm.prz031.input-file-sha256";
    static final String OUTPUT_FILE_SHA_PROPERTY = "prizm.prz031.output-file-sha256";
    static final Path OFFICIAL_OUTPUT = Path.of(
            "local/search-v3-evaluation/prz031/semantic-directness-output-protocol-v2.json");
    static final Path OFFICIAL_RUN_MARKER = Path.of(
            "local/search-v3-evaluation/prz031/semantic-directness-output-protocol-v2.json.official-run-started.json");

    private static final String EXPECTED_OUTPUT_ARTIFACT =
            "PRZ031_SEMANTIC_DIRECTNESS_OUTPUT_PROTOCOL_V2";
    private static final String EXPECTED_PROTOCOL = "SEMANTIC_DIRECTNESS_PROTOCOL_V2";
    private static final String D1_CODE_FREEZE_COMMIT =
            "3d1f57b969d97d1b73a2531ba990cd9beaed57db";
    private static final String D1_CONTRACT_SHA256 =
            "aa683f4cecb21c90d91d43c7b77bb31cb2f98fe0cd8c7a2c916962eef620d77e";
    private static final String SHA_PATTERN = "^[0-9a-f]{64}$";
    private static final String COMMIT_PATTERN = "^[0-9a-f]{40}$";
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void evaluatesOfficialFrozenOutputOnlyWhenExplicitlyEnabled() throws Exception {
        assumeTrue(Boolean.getBoolean(RUN_PROPERTY), "PRZ-031 official evaluation is opt-in");
        String codeFreeze = requiredProperty(CODE_FREEZE_PROPERTY, COMMIT_PATTERN);
        String contractFileSha = requiredProperty(CONTRACT_FILE_SHA_PROPERTY, SHA_PATTERN);
        String candidateFileSha = requiredProperty(CANDIDATE_FILE_SHA_PROPERTY, SHA_PATTERN);
        String inputFileSha = requiredProperty(INPUT_FILE_SHA_PROPERTY, SHA_PATTERN);
        String outputFileSha = requiredProperty(OUTPUT_FILE_SHA_PROPERTY, SHA_PATTERN);
        verifyOfficialPreconditions(codeFreeze, contractFileSha);
        assertThat(SearchV3SemanticDirectnessOfficialEvaluationAdapter.DEFAULT_REPORT).doesNotExist();

        CandidateArtifact candidateArtifact = readCandidateArtifact(
                Prz031SemanticDirectnessInputBenchmarkTest.CANDIDATE_OUTPUT, candidateFileSha);
        ModelInputArtifact inputArtifact = readInputArtifact(
                Prz031SemanticDirectnessInputBenchmarkTest.MODEL_INPUT_V2_OUTPUT, inputFileSha);
        if (!candidateArtifact.codeFreezeCommit().equals(D1_CODE_FREEZE_COMMIT)
                || !inputArtifact.codeFreezeCommit().equals(codeFreeze)
                || !candidateArtifact.contractSha256().equals(D1_CONTRACT_SHA256)
                || !inputArtifact.contractSha256().equals(contractFileSha)
                || !inputArtifact.candidateFreezeFileSha256().equals(candidateFileSha)
                || !inputArtifact.sourceD1InputFileSha256().equals(
                        Prz031SemanticDirectnessInputBenchmarkTest.D1_INPUT_FILE_SHA256)
                || !inputArtifact.sourceD1InputCanonicalSha256().equals(
                        Prz031SemanticDirectnessInputBenchmarkTest.D1_INPUT_CANONICAL_SHA256)
                || !inputArtifact.sourceD1GuardContractSha256().equals(
                        Prz031SemanticDirectnessInputBenchmarkTest.D1_GUARD_CONTRACT_SHA256)
                || !inputArtifact.candidatePayloadSha256().equals(
                        Prz031SemanticDirectnessInputBenchmarkTest.D1_CANDIDATE_PAYLOAD_SHA256)) {
            throw new IllegalStateException("candidate/input artifact lineage differs from official properties");
        }
        FrozenInput frozenInput = inputFreeze(inputArtifact);
        ParsedOutput parsedOutput = outputFreeze(
                OFFICIAL_OUTPUT, OFFICIAL_RUN_MARKER, outputFileSha, inputFileSha,
                contractFileSha, frozenInput);
        SealedState sealed = sealedState();

        ArtifactHashes hashes = new ArtifactHashes(
                candidateFileSha,
                parsedOutput.markerFileSha256(),
                inputFileSha,
                frozenInput.canonicalSha256(),
                contractFileSha,
                frozenInput.contractSha256(),
                outputFileSha,
                parsedOutput.artifactCanonicalSha256(),
                parsedOutput.frozen().canonicalSha256());
        SearchV3SemanticDirectnessOfficialEvaluationAdapter adapter =
                new SearchV3SemanticDirectnessOfficialEvaluationAdapter();
        EvaluationReport report = adapter.evaluate(
                frozenInput,
                parsedOutput.frozen(),
                hashes,
                parsedOutput.cost(),
                sealed,
                ignored -> officialGold(candidateArtifact, frozenInput.input()));
        Path reportPath = SearchV3SemanticDirectnessOfficialEvaluationAdapter.writeCreateNew(
                Path.of("."),
                SearchV3SemanticDirectnessOfficialEvaluationAdapter.DEFAULT_REPORT,
                report,
                mapper);

        assertThat(report.queries()).hasSize(SearchV3SemanticDirectnessPredictionFreeze.SEMANTIC_QUERY_COUNT);
        assertThat(report.suites()).hasSize(4);
        assertThat(report.goldAccessState()).isEqualTo("GOLD_JOINED_AFTER_OUTPUT_VERIFIED");
        assertThat(git("rev-parse", "HEAD")).isEqualTo(codeFreeze);
        assertThat(git("status", "--porcelain")).isEmpty();
        assertThat(sealedState()).isEqualTo(sealed);
        System.out.println("PRZ031_EVALUATION_REPORT=" + reportPath);
        System.out.println("PRZ031_EVALUATION_REPORT_SHA256=" + sha256(reportPath));
    }

    @Test
    void adapterInvokesGoldOnlyAfterVerifiedOutputThenRejectsMissingExactSuiteFreeze() {
        SyntheticRun fixture = syntheticRun();
        AtomicInteger goldLoads = new AtomicInteger();

        assertThatThrownBy(() -> new SearchV3SemanticDirectnessOfficialEvaluationAdapter().evaluate(
                fixture.input(), fixture.output(), fixture.hashes(), fixture.cost(), fixture.sealed(),
                ignored -> {
                    goldLoads.incrementAndGet();
                    return List.of();
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("omitted source suite");
        assertThat(goldLoads).hasValue(1);
    }

    @Test
    void outputHashMismatchFailsBeforeGoldLoaderIsCalled() {
        SyntheticRun fixture = syntheticRun();
        FrozenOutput corrupt = new FrozenOutput(
                fixture.output().output(), "f".repeat(64), fixture.output().canonicalByteLength());
        ArtifactHashes hashes = new ArtifactHashes(
                fixture.hashes().candidateArtifactFileSha256(),
                fixture.hashes().officialRunMarkerFileSha256(),
                fixture.hashes().inputArtifactFileSha256(),
                fixture.hashes().inputCanonicalSha256(),
                fixture.hashes().executionContractFileSha256(),
                fixture.hashes().guardContractSha256(),
                fixture.hashes().outputArtifactFileSha256(),
                fixture.hashes().outputArtifactCanonicalSha256(),
                "f".repeat(64));
        AtomicInteger goldLoads = new AtomicInteger();

        assertThatThrownBy(() -> new SearchV3SemanticDirectnessOfficialEvaluationAdapter().evaluate(
                fixture.input(), corrupt, hashes, fixture.cost(), fixture.sealed(),
                ignored -> {
                    goldLoads.incrementAndGet();
                    return List.of();
                }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("canonical freeze");
        assertThat(goldLoads).hasValue(0);
    }

    @Test
    void reportWriterIsCreateNewAndRestrictedToIgnoredLocalPrz031Path(@TempDir Path temporary)
            throws Exception {
        SyntheticRun fixture = syntheticRun();
        Path relative = Path.of("local/search-v3-evaluation/prz031/report.json");

        Path written = SearchV3SemanticDirectnessOfficialEvaluationAdapter.writeCreateNew(
                temporary, relative, Map.of("artifactType", "TEST_REPORT"), mapper);

        assertThat(written).exists().isRegularFile();
        assertThatThrownBy(() -> SearchV3SemanticDirectnessOfficialEvaluationAdapter.writeCreateNew(
                temporary, relative, Map.of("artifactType", "TEST_REPORT"), mapper))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(() -> SearchV3SemanticDirectnessOfficialEvaluationAdapter.requireLocalReportPath(
                Path.of("specs/PRZ-031/report.json")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void decisionPolicyIsFrozenBeforeOfficialResults() {
        assertThat(SearchV3SemanticDirectnessOfficialEvaluationAdapter.decision(
                SearchV3SemanticDirectnessEvaluator.GateStatus.PASS,
                0.0d, 100, 0, 1.0d, 0.0d,
                SearchV3SemanticDirectnessEvaluator.MetricStatus.NOT_APPLICABLE, null))
                .isEqualTo(Decision.PROMISING);
        assertThat(SearchV3SemanticDirectnessOfficialEvaluationAdapter.decision(
                SearchV3SemanticDirectnessEvaluator.GateStatus.FAIL,
                0.84d, 0, 1, 0.5d, 0.6d,
                SearchV3SemanticDirectnessEvaluator.MetricStatus.APPLICABLE, 0.98d))
                .isEqualTo(Decision.NO_GO);
        assertThat(SearchV3SemanticDirectnessOfficialEvaluationAdapter.decision(
                SearchV3SemanticDirectnessEvaluator.GateStatus.FAIL,
                0.85d, 0, 1, 0.5d, 0.6d,
                SearchV3SemanticDirectnessEvaluator.MetricStatus.APPLICABLE, 0.98d))
                .isEqualTo(Decision.NEEDS_ADJUSTMENT);
    }

    @Test
    void candidateAndModelInputArtifactDtosRoundTripWithoutHandCopyingRows() throws Exception {
        SyntheticRun fixture = syntheticRun();
        CandidateArtifact candidate = new CandidateArtifact(
                1, "PRZ031_B3_CANDIDATE_FREEZE", "a".repeat(40), "b".repeat(64),
                SearchV3B3CandidateReplay.BGE_M3_DIGEST, 79, 670,
                List.of(new CandidateSuite("combined", fixture.input().candidateFreeze())));
        ModelInputArtifact input = new ModelInputArtifact(
                2, "PRZ031_SEMANTIC_DIRECTNESS_PROTOCOL_V2_INPUT", EXPECTED_PROTOCOL,
                "a".repeat(40), "b".repeat(64),
                "c".repeat(64), "d".repeat(64), "e".repeat(64), "f".repeat(64),
                "1".repeat(64),
                fixture.input().canonicalSha256(), fixture.input().canonicalByteLength(),
                fixture.input().contractSha256(), 79, 670, 578, 0, "NOT_RUN",
                fixture.input().input().contract(), fixture.input().input().sourceSuites(),
                fixture.input().input().queries().stream().map(value -> new ModelInputQuery(
                        value.suite(), value.datasetVersion(), value.queryId(), value.userBundleId(),
                        value.split(), value.language(), value.originalQuery(),
                        value.originalQuerySha256(), value.rankedCandidates())).toList());

        CandidateArtifact candidateRoundTrip = mapper.readValue(
                mapper.writeValueAsString(candidate), CandidateArtifact.class);
        ModelInputArtifact inputRoundTrip = mapper.readValue(
                mapper.writeValueAsString(input), ModelInputArtifact.class);

        assertThat(candidateRoundTrip).isEqualTo(candidate);
        assertThat(inputRoundTrip).isEqualTo(input);
    }

    @Test
    void parsesPythonOutputIntoTheCanonicalPredictionFreeze(@TempDir Path temporary) throws Exception {
        SyntheticRun fixture = syntheticRun();
        String inputFileSha = "a".repeat(64);
        String contractFileSha = "b".repeat(64);
        String started = "2026-09-01T00:00:00Z";
        Path outputPath = temporary.resolve("output.json");
        Path markerPath = temporary.resolve("output.json.official-run-started.json");
        JsonNode executionContract = mapper.readTree(Files.readString(
                Prz031SemanticDirectnessInputBenchmarkTest.CONTRACT_V2, StandardCharsets.UTF_8));
        ObjectNode marker = mapper.createObjectNode();
        marker.put("artifactType", "PRZ031_OUTPUT_PROTOCOL_V2_OFFICIAL_RUN_STARTED");
        marker.put("protocol", EXPECTED_PROTOCOL);
        marker.put("startedAtUtc", started);
        marker.put("contractSha256", contractFileSha);
        marker.put("conformanceOutputFileSha256", "c".repeat(64));
        marker.put("candidateInputFileSha256", inputFileSha);
        marker.put("candidatePayloadSha256",
                Prz031SemanticDirectnessInputBenchmarkTest.D1_CANDIDATE_PAYLOAD_SHA256);
        marker.put("candidateFreezeFileSha256",
                Prz031SemanticDirectnessInputBenchmarkTest.D1_CANDIDATE_FILE_SHA256);
        marker.put("modelManifestDigest",
                required(executionContract.path("model"), "ollamaManifestDigest"));
        Files.writeString(markerPath, mapper.writeValueAsString(marker), StandardCharsets.UTF_8);

        ObjectNode output = mapper.createObjectNode();
        output.put("schemaVersion", 2);
        output.put("artifactType", EXPECTED_OUTPUT_ARTIFACT);
        output.put("protocol", EXPECTED_PROTOCOL);
        output.put("startedAtUtc", started);
        output.put("contractSha256", contractFileSha);
        output.put("conformanceOutputFileSha256", "c".repeat(64));
        output.put("runMarkerFileSha256", sha256(markerPath));
        output.put("candidateInputFileSha256", inputFileSha);
        output.put("candidatePayloadSha256",
                Prz031SemanticDirectnessInputBenchmarkTest.D1_CANDIDATE_PAYLOAD_SHA256);
        output.put("candidateFreezeFileSha256",
                Prz031SemanticDirectnessInputBenchmarkTest.D1_CANDIDATE_FILE_SHA256);
        output.put("queryCount", 79);
        output.put("pairCount", 578);
        var rows = output.putArray("rows");
        for (Prediction prediction : fixture.output().output().predictions()) {
            ObjectNode row = rows.addObject();
            row.put("pairId", prediction.queryId() + "::" + prediction.sourceRank());
            row.put("suite", "synthetic");
            row.put("queryId", prediction.queryId());
            row.put("userBundleId", "synthetic-user");
            row.put("candidateId", prediction.candidateId());
            row.put("denseRank", prediction.sourceRank());
            row.put("relation", prediction.relation().name());
            row.put("rawMessageContentSha256", "a".repeat(64));
            row.put("responseEnvelopeSha256", "b".repeat(64));
            row.put("latencyMs", 1.0d);
            row.putObject("ollama");
        }
        ObjectNode cost = output.putObject("cost");
        for (String field : List.of(
                "officialWallMs", "pairLatencyAverageMs", "pairLatencyP50Ms",
                "pairLatencyP95Ms", "pairLatencyMaxMs", "queryTop10LatencyP50Ms",
                "queryTop10LatencyP95Ms", "queryTop10LatencyMaxMs")) {
            cost.put(field, 1.0d);
        }
        for (String field : List.of("processRssBytes", "gpuUsedMiB")) {
            ObjectNode resource = cost.putObject(field);
            resource.putNull("before");
            resource.putNull("peak");
            resource.putNull("after");
        }
        output.put("outputCanonicalSha256",
                sha256(canonicalJson(output).getBytes(StandardCharsets.UTF_8)));
        Files.writeString(outputPath, mapper.writeValueAsString(output), StandardCharsets.UTF_8);

        ParsedOutput parsed = outputFreeze(
                outputPath, markerPath, sha256(outputPath), inputFileSha, contractFileSha,
                fixture.input());

        assertThat(parsed.frozen()).isEqualTo(fixture.output());
        assertThat(parsed.markerFileSha256()).isEqualTo(sha256(markerPath));

        ObjectNode extraField = output.deepCopy();
        ((ObjectNode) extraField.path("rows").get(0)).put("reasonCode", "DIRECT_ANSWER");
        rewriteCanonical(extraField);
        Path extraPath = temporary.resolve("output-extra.json");
        Files.writeString(extraPath, mapper.writeValueAsString(extraField), StandardCharsets.UTF_8);
        assertThatThrownBy(() -> outputFreeze(
                extraPath, markerPath, sha256(extraPath), inputFileSha, contractFileSha,
                fixture.input()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prediction row fields drifted");

        ObjectNode invalidEnum = output.deepCopy();
        ((ObjectNode) invalidEnum.path("rows").get(0)).put("relation", "UNKNOWN");
        rewriteCanonical(invalidEnum);
        Path invalidEnumPath = temporary.resolve("output-invalid-enum.json");
        Files.writeString(
                invalidEnumPath, mapper.writeValueAsString(invalidEnum), StandardCharsets.UTF_8);
        assertThatThrownBy(() -> outputFreeze(
                invalidEnumPath, markerPath, sha256(invalidEnumPath), inputFileSha,
                contractFileSha, fixture.input()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prediction relation is invalid");

        Path malformedPath = temporary.resolve("output-malformed.json");
        Files.writeString(malformedPath, "{", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> outputFreeze(
                malformedPath, markerPath, sha256(malformedPath), inputFileSha,
                contractFileSha, fixture.input()))
                .isInstanceOfAny(IOException.class, RuntimeException.class);
    }

    private void rewriteCanonical(ObjectNode output) throws IOException {
        output.remove("outputCanonicalSha256");
        output.put("outputCanonicalSha256",
                sha256(canonicalJson(output).getBytes(StandardCharsets.UTF_8)));
    }

    private CandidateArtifact readCandidateArtifact(Path path, String expectedFileSha) throws IOException {
        byte[] bytes = readVerified(path, expectedFileSha);
        CandidateArtifact artifact = mapper.readValue(new String(bytes, StandardCharsets.UTF_8),
                CandidateArtifact.class);
        if (artifact.schemaVersion() != 1
                || !"PRZ031_B3_CANDIDATE_FREEZE".equals(artifact.artifactType())
                || artifact.queryCount() != Prz031SemanticDirectnessInputBenchmarkTest.FULL_QUERY_COUNT
                || artifact.candidateCount() != Prz031SemanticDirectnessInputBenchmarkTest.FULL_CANDIDATE_COUNT
                || !SearchV3B3CandidateReplay.BGE_M3_DIGEST.equals(artifact.bgeM3Digest())) {
            throw new IllegalStateException("official candidate artifact contract drifted");
        }
        Map<String, CandidateSuite> suites = uniqueMap(
                artifact.suites(), CandidateSuite::suite, "candidate suite");
        for (SourceSuite expected : SearchV3SemanticDirectnessPredictionFreeze.expectedSourceSuites()) {
            CandidateSuite actual = suites.get(expected.suite());
            if (actual == null
                    || !SearchV3CandidateFreeze.verify(actual.frozen()).frozen().equals(actual.frozen())
                    || !expected.candidateFreezeSha256().equals(actual.frozen().canonicalSha256())) {
                throw new IllegalStateException("official source candidate freeze drifted: " + expected.suite());
            }
        }
        if (suites.size() != 4) {
            throw new IllegalStateException("official candidate artifact must contain four suites");
        }
        return artifact;
    }

    private ModelInputArtifact readInputArtifact(Path path, String expectedFileSha) throws IOException {
        byte[] bytes = readVerified(path, expectedFileSha);
        ModelInputArtifact artifact = mapper.readValue(new String(bytes, StandardCharsets.UTF_8),
                ModelInputArtifact.class);
        if (artifact.schemaVersion() != 2
                || !"PRZ031_SEMANTIC_DIRECTNESS_PROTOCOL_V2_INPUT".equals(artifact.artifactType())
                || !EXPECTED_PROTOCOL.equals(artifact.protocol())
                || artifact.semanticQueryCount() != 79
                || artifact.candidateCount() != 670
                || artifact.inferencePairCount() != 578
                || artifact.typedQueryCount() != 0
                || !"NOT_RUN".equals(artifact.currentFreshBaseline())) {
            throw new IllegalStateException("official model input artifact contract drifted");
        }
        return artifact;
    }

    private FrozenInput inputFreeze(ModelInputArtifact artifact) {
        List<QueryInput> queries = artifact.queries().stream().map(value -> new QueryInput(
                value.suite(), value.datasetVersion(), value.split(), value.queryId(),
                value.userBundleId(), value.language(), EvaluationTrack.SEMANTIC,
                value.queryText(), value.queryTextSha256(), value.candidates())).toList();
        FrozenInput frozen = SearchV3SemanticDirectnessPredictionFreeze.freezeInput(new Input(
                SearchV3SemanticDirectnessPredictionFreeze.SCHEMA_VERSION,
                artifact.runContract(), artifact.sourceSuites(), queries));
        if (!artifact.inputCanonicalSha256().equals(frozen.canonicalSha256())
                || artifact.inputCanonicalByteLength() != frozen.canonicalByteLength()
                || !artifact.guardContractSha256().equals(frozen.contractSha256())) {
            throw new IllegalStateException("official model input canonical freeze drifted");
        }
        return frozen;
    }

    private ParsedOutput outputFreeze(
            Path path,
            Path markerPath,
            String expectedFileSha,
            String inputFileSha,
            String contractFileSha,
            FrozenInput input) throws IOException {
        byte[] bytes = readVerified(path, expectedFileSha);
        ObjectNode root = (ObjectNode) mapper.readTree(bytes);
        if (root.path("schemaVersion").asInt(-1) != 2
                || !EXPECTED_OUTPUT_ARTIFACT.equals(root.path("artifactType").asText())
                || !EXPECTED_PROTOCOL.equals(root.path("protocol").asText())
                || !inputFileSha.equals(root.path("candidateInputFileSha256").asText())
                || !Prz031SemanticDirectnessInputBenchmarkTest.D1_CANDIDATE_FILE_SHA256.equals(
                        root.path("candidateFreezeFileSha256").asText())
                || !Prz031SemanticDirectnessInputBenchmarkTest.D1_CANDIDATE_PAYLOAD_SHA256.equals(
                        root.path("candidatePayloadSha256").asText())
                || !contractFileSha.equals(root.path("contractSha256").asText())
                || root.path("queryCount").asInt(-1) != 79
                || root.path("pairCount").asInt(-1) != 578
                || root.path("rows").size() != 578) {
            throw new IllegalStateException("official prediction artifact identity/inventory drifted");
        }
        String declaredCanonical = root.path("outputCanonicalSha256").asText();
        ObjectNode core = root.deepCopy();
        core.remove("outputCanonicalSha256");
        String actualCanonical = sha256(canonicalJson(core).getBytes(StandardCharsets.UTF_8));
        if (!actualCanonical.equals(declaredCanonical)) {
            throw new IllegalStateException("official prediction artifact canonical hash drifted");
        }
        String markerFileSha = verifyOfficialRunMarker(
                markerPath, inputFileSha, contractFileSha, input, root);
        List<Prediction> predictions = new ArrayList<>();
        for (JsonNode row : root.path("rows")) {
            assertExactPredictionRow(row);
            predictions.add(new Prediction(
                    required(row, "queryId"),
                    required(row, "candidateId"),
                    row.path("denseRank").asInt(-1),
                    Relation.valueOf(required(row, "relation"))));
        }
        FrozenOutput frozen = SearchV3SemanticDirectnessPredictionFreeze.freezeOutput(
                input,
                new Output(
                        SearchV3SemanticDirectnessPredictionFreeze.SCHEMA_VERSION,
                        input.canonicalSha256(), input.contractSha256(), predictions));
        return new ParsedOutput(
                frozen, declaredCanonical, markerFileSha, officialCost(root.path("cost"), input));
    }

    private void assertExactPredictionRow(JsonNode row) {
        Set<String> expected = Set.of(
                "pairId",
                "suite",
                "queryId",
                "userBundleId",
                "denseRank",
                "candidateId",
                "relation",
                "rawMessageContentSha256",
                "responseEnvelopeSha256",
                "latencyMs",
                "ollama");
        Set<String> actual = new LinkedHashSet<>();
        row.propertyNames().forEach(actual::add);
        if (!expected.equals(actual)) {
            throw new IllegalStateException("protocol-v2 prediction row fields drifted");
        }
        String relation = required(row, "relation");
        try {
            Relation.valueOf(relation);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalStateException("protocol-v2 prediction relation is invalid", exception);
        }
    }

    private String verifyOfficialRunMarker(
            Path markerPath,
            String inputFileSha,
            String contractFileSha,
            FrozenInput input,
            JsonNode output) throws IOException {
        byte[] markerBytes = Files.readAllBytes(markerPath);
        String markerFileSha = sha256(markerBytes);
        JsonNode marker = mapper.readTree(markerBytes);
        JsonNode executionContract = mapper.readTree(Files.readString(
                Prz031SemanticDirectnessInputBenchmarkTest.CONTRACT_V2, StandardCharsets.UTF_8));
        if (!"PRZ031_OUTPUT_PROTOCOL_V2_OFFICIAL_RUN_STARTED".equals(
                        marker.path("artifactType").asText())
                || !EXPECTED_PROTOCOL.equals(marker.path("protocol").asText())
                || !contractFileSha.equals(marker.path("contractSha256").asText())
                || !required(marker, "conformanceOutputFileSha256").equals(
                        output.path("conformanceOutputFileSha256").asText())
                || !inputFileSha.equals(marker.path("candidateInputFileSha256").asText())
                || !Prz031SemanticDirectnessInputBenchmarkTest.D1_CANDIDATE_FILE_SHA256.equals(
                        marker.path("candidateFreezeFileSha256").asText())
                || !Prz031SemanticDirectnessInputBenchmarkTest.D1_CANDIDATE_PAYLOAD_SHA256.equals(
                        marker.path("candidatePayloadSha256").asText())
                || !markerFileSha.equals(output.path("runMarkerFileSha256").asText())
                || !required(executionContract.path("model"), "ollamaManifestDigest").equals(
                        marker.path("modelManifestDigest").asText())
                || !required(marker, "startedAtUtc").equals(output.path("startedAtUtc").asText())) {
            throw new IllegalStateException("official-run marker/output identity chain drifted");
        }
        return markerFileSha;
    }

    private OfficialCost officialCost(JsonNode node, FrozenInput input) {
        return new OfficialCost(
                finite(node, "officialWallMs"),
                finite(node, "pairLatencyAverageMs"),
                finite(node, "pairLatencyP50Ms"),
                finite(node, "pairLatencyP95Ms"),
                finite(node, "pairLatencyMaxMs"),
                finite(node, "queryTop10LatencyP50Ms"),
                finite(node, "queryTop10LatencyP95Ms"),
                finite(node, "queryTop10LatencyMaxMs"),
                resource(node.path("processRssBytes")),
                resource(node.path("gpuUsedMiB")),
                input.input().contract().modelSizeBytes());
    }

    private List<SuiteGold> officialGold(CandidateArtifact artifact, Input input) {
        Map<String, CandidateSuite> suites = uniqueMap(
                artifact.suites(), CandidateSuite::suite, "candidate suite");
        SearchV3SemanticOracleGoldJoiner joiner = new SearchV3SemanticOracleGoldJoiner();
        List<SuiteGold> loaded = new ArrayList<>();
        for (SourceSuite source : input.sourceSuites()) {
            FrozenCandidates frozen = suites.get(source.suite()).frozen();
            VerifiedCandidates verified = SearchV3CandidateFreeze.verify(frozen);
            List<QueryGold> suiteGold = switch (source.suite()) {
                case "originalSeed" -> joiner.loadHistoricalGold(
                        verified, SearchV3B3CandidateReplay.Suite.ORIGINAL_SEED);
                case "longFormExpansion" -> joiner.loadHistoricalGold(
                        verified, SearchV3B3CandidateReplay.Suite.LONG_FORM_EXPANSION);
                case "independentRobustness" -> joiner.loadHistoricalGold(
                        verified, SearchV3B3CandidateReplay.Suite.INDEPENDENT_ROBUSTNESS);
                case SearchV3SemanticOracleDataset.STRESS_SUITE -> joiner.loadStressGold(verified);
                default -> throw new IllegalStateException("unapproved PRZ-031 Gold suite");
            };
            loaded.add(new SuiteGold(source.suite(), frozen, suiteGold));
        }
        return List.copyOf(loaded);
    }

    private SyntheticRun syntheticRun() {
        RunContract contract = contract();
        List<QueryInput> queries = new ArrayList<>();
        addQueries(queries, "originalSeed", "OS", fourteen(3));
        addQueries(queries, "longFormExpansion", "LF", concat(List.of(2), repeat(6, 20), repeat(11, 10)));
        addQueries(queries, "independentRobustness", "IR", concat(List.of(18, 18), repeat(13, 8), repeat(8, 7)));
        addQueries(queries, SearchV3SemanticOracleDataset.STRESS_SUITE, "SS",
                concat(List.of(18, 18), repeat(10, 8), repeat(12, 7)));
        FrozenInput input = SearchV3SemanticDirectnessPredictionFreeze.freezeInput(new Input(
                SearchV3SemanticDirectnessPredictionFreeze.SCHEMA_VERSION,
                contract,
                SearchV3SemanticDirectnessPredictionFreeze.expectedSourceSuites(),
                queries));
        List<Prediction> predictions = input.input().queries().stream().flatMap(query ->
                query.rankedCandidates().stream().limit(10).map(candidate -> new Prediction(
                        query.queryId(), candidate.candidateId(), candidate.rank(),
                        candidate.rank() == 1 ? Relation.DIRECT_MATCH : Relation.INSUFFICIENT)))
                .toList();
        FrozenOutput output = SearchV3SemanticDirectnessPredictionFreeze.freezeOutput(
                input,
                new Output(SearchV3SemanticDirectnessPredictionFreeze.SCHEMA_VERSION,
                        input.canonicalSha256(), input.contractSha256(), predictions));
        ArtifactHashes hashes = new ArtifactHashes(
                "1".repeat(64), "2".repeat(64), "3".repeat(64), input.canonicalSha256(),
                "4".repeat(64), input.contractSha256(), "5".repeat(64), "6".repeat(64),
                output.canonicalSha256());
        OfficialCost cost = new OfficialCost(
                1, 1, 1, 1, 1, 1, 1, 1,
                new ResourceSnapshot(null, null, null),
                new ResourceSnapshot(null, null, null),
                contract.modelSizeBytes());
        SealedState sealed = new SealedState(
                "7".repeat(64), "8".repeat(64), "9".repeat(40), false, false, "NOT_RUN");
        return new SyntheticRun(input, output, hashes, cost, sealed);
    }

    private void addQueries(List<QueryInput> target, String suite, String prefix, List<Integer> sizes) {
        SourceSuite source = SearchV3SemanticDirectnessPredictionFreeze.expectedSourceSuites().stream()
                .filter(value -> value.suite().equals(suite)).findFirst().orElseThrow();
        for (int index = 0; index < sizes.size(); index++) {
            String queryId = prefix + "-Q" + (index + 1);
            String owner = prefix + "-U" + (index + 1);
            String text = "query " + queryId;
            List<CandidateProjection> candidates = IntStream.rangeClosed(1, sizes.get(index))
                    .mapToObj(rank -> candidate(queryId, owner, rank)).toList();
            target.add(new QueryInput(
                    suite, source.datasetVersion(), index % 2 == 0 ? "DEV" : "CALIBRATION",
                    queryId, owner, index % 3 == 0 ? "KO" : index % 3 == 1 ? "EN" : "KO_EN_MIXED",
                    EvaluationTrack.SEMANTIC, text,
                    SearchV3SemanticDirectnessPredictionFreeze.sha256(text), candidates));
        }
    }

    private CandidateProjection candidate(String queryId, String owner, int rank) {
        String id = queryId + "-P" + rank;
        String childId = queryId + "-E" + rank;
        String source = "source " + childId;
        String hash = SearchV3CandidateFreeze.sha256(source);
        return new CandidateProjection(
                rank, id, 1.0d - rank * 0.001d, owner, queryId + "-DOC", queryId + "-V1",
                queryId + "-PARENT", source, source, hash, hash,
                List.of(new EvidenceChildProjection(
                        childId, queryId + "-DOC", queryId + "-V1", 1, 0,
                        source.codePointCount(0, source.length()), source, hash)));
    }

    private RunContract contract() {
        String instruction = "instruction";
        String schema = "schema";
        String config = "config";
        String policy = "policy";
        return new RunContract(
                "model", "revision", "Apache-2.0", 1, "a".repeat(64),
                instruction, SearchV3SemanticDirectnessPredictionFreeze.sha256(instruction),
                schema, SearchV3SemanticDirectnessPredictionFreeze.sha256(schema),
                config, SearchV3SemanticDirectnessPredictionFreeze.sha256(config),
                policy, SearchV3SemanticDirectnessPredictionFreeze.sha256(policy), 10);
    }

    private SealedState sealedState() throws Exception {
        Path manifestPath = Prz031SemanticDirectnessInputBenchmarkTest.SEALED_MANIFEST;
        JsonNode manifest = mapper.readTree(Files.readString(manifestPath, StandardCharsets.UTF_8));
        return new SealedState(
                required(manifest, "combinedSha256"),
                sha256(manifestPath),
                git("rev-parse", "HEAD:" + Prz031SemanticDirectnessInputBenchmarkTest.SEALED_GIT_PATH),
                manifest.path("opened").asBoolean(true),
                manifest.path("searchExecuted").asBoolean(true),
                "NOT_RUN");
    }

    private void verifyOfficialPreconditions(String codeFreeze, String contractFileSha) throws Exception {
        assertThat(git("rev-parse", "HEAD")).isEqualTo(codeFreeze);
        assertThat(git("status", "--porcelain")).isEmpty();
        assertThat(sha256(Prz031SemanticDirectnessInputBenchmarkTest.CONTRACT_V2))
                .isEqualTo(contractFileSha);
        assertThat(sealedState()).isEqualTo(new SealedState(
                Prz031SemanticDirectnessInputBenchmarkTest.SEALED_COMBINED_SHA256,
                Prz031SemanticDirectnessInputBenchmarkTest.SEALED_MANIFEST_SHA256,
                Prz031SemanticDirectnessInputBenchmarkTest.SEALED_TREE,
                false, false, "NOT_RUN"));
    }

    private byte[] readVerified(Path path, String expectedSha) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        if (!sha256(bytes).equals(expectedSha)) {
            throw new IllegalStateException("artifact file SHA-256 mismatch: " + path);
        }
        return bytes;
    }

    private ResourceSnapshot resource(JsonNode node) {
        return new ResourceSnapshot(nullableLong(node.get("before")), nullableLong(node.get("peak")),
                nullableLong(node.get("after")));
    }

    private Long nullableLong(JsonNode node) {
        return node == null || node.isNull() ? null : node.asLong();
    }

    private double finite(JsonNode node, String field) {
        double value = node.path(field).asDouble(Double.NaN);
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalStateException("invalid official cost field: " + field);
        }
        return value;
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

    private String requiredProperty(String name, String pattern) {
        String value = System.getProperty(name, "");
        if (!value.matches(pattern)) {
            throw new IllegalArgumentException("missing/invalid official property: " + name);
        }
        return value;
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing JSON field: " + field);
        }
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
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private <T> Map<String, T> uniqueMap(List<T> values, Function<T, String> key, String label) {
        return values.stream().collect(Collectors.toMap(
                key, Function.identity(), (left, right) -> {
                    throw new IllegalStateException("duplicate " + label + ": " + key.apply(left));
                }, LinkedHashMap::new));
    }

    private List<Integer> fourteen(int value) {
        return repeat(14, value);
    }

    private List<Integer> repeat(int count, int value) {
        return IntStream.range(0, count).mapToObj(ignored -> value).toList();
    }

    @SafeVarargs
    private final List<Integer> concat(List<Integer>... values) {
        return java.util.Arrays.stream(values).flatMap(List::stream).toList();
    }

    record CandidateArtifact(
            int schemaVersion,
            String artifactType,
            String codeFreezeCommit,
            String contractSha256,
            String bgeM3Digest,
            int queryCount,
            int candidateCount,
            List<CandidateSuite> suites) {

        CandidateArtifact {
            suites = List.copyOf(suites);
        }
    }

    record CandidateSuite(String suite, FrozenCandidates frozen) {
    }

    record ModelInputArtifact(
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

        ModelInputArtifact {
            sourceSuites = List.copyOf(sourceSuites);
            queries = List.copyOf(queries);
        }
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
    }

    record ParsedOutput(
            FrozenOutput frozen,
            String artifactCanonicalSha256,
            String markerFileSha256,
            OfficialCost cost) {
    }

    record SyntheticRun(
            FrozenInput input,
            FrozenOutput output,
            ArtifactHashes hashes,
            OfficialCost cost,
            SealedState sealed) {
    }
}
