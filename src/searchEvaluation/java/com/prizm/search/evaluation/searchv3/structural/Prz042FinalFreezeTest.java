package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class Prz042FinalFreezeTest {

    @TempDir Path temporaryDirectory;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void verifiesTheTrackedContractWithoutClaimingOrOpeningAnAttempt() {
        var input = new Prz042FinalFreeze().verifyInput(Path.of(
                "specs/PRZ-042-search-v3-final-evaluation/execution-contract.json"));

        assertThat(input.datasetVersion()).isEqualTo("search-v3-fresh-seed-1.0.1");
        assertThat(input.userBundleCount()).isEqualTo(2);
        assertThat(input.queryCount()).isEqualTo(8);
        assertThat(input.sourceBoundaryHashes()).containsKeys("V2", "V3", "SHARED", "EVALUATOR");
    }

    @Test
    void claimsOneAttemptAndFreezesPredictionsWithoutMutatingTheSeal() throws Exception {
        Path split = temporaryDirectory.resolve("sealed-final");
        Files.createDirectories(split);
        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("artifactType", "MANIFEST");
        manifest.put("datasetVersion", "unit-sealed-1");
        manifest.put("split", "SEALED_FINAL_TEST");
        manifest.put("status", "SEALED");
        manifest.put("mutable", false);
        manifest.put("opened", false);
        manifest.put("searchExecuted", false);
        manifest.put("combinedSha256", "a".repeat(64));
        manifest.putObject("counts").put("userBundles", 1).put("queries", 1);
        manifest.putObject("distributions").putObject("answerability")
                .put("SUPPORTED", 1).put("NOT_SUPPORTED", 0);
        Path manifestPath = split.resolve("manifest.json");
        Files.writeString(manifestPath, mapper.writeValueAsString(manifest), StandardCharsets.UTF_8);
        byte[] manifestBefore = Files.readAllBytes(manifestPath);

        Path frozenSource = Path.of("AGENTS.md").toAbsolutePath().normalize();
        String boundarySha = Prz042FinalFreeze.canonicalFileSetSha256(List.of(frozenSource));
        ObjectNode contract = mapper.createObjectNode();
        contract.put("artifactType", Prz042FinalFreeze.CONTRACT_TYPE);
        contract.put("protocolVersion", Prz042FinalFreeze.PROTOCOL_VERSION);
        contract.put("status", "INPUT_FROZEN");
        contract.put("attempt", 1);
        contract.put("baseCommit", "b".repeat(40));
        ObjectNode dataset = contract.putObject("dataset");
        dataset.put("datasetVersion", "unit-sealed-1");
        dataset.put("split", "SEALED_FINAL_TEST");
        dataset.put("goldSchemaSha256", "e".repeat(64));
        dataset.put("gitTree", "f".repeat(40));
        dataset.put("splitRoot", split.toAbsolutePath().toString());
        dataset.put("manifestSha256", Prz042FinalFreeze.sha256(manifestPath));
        dataset.put("combinedSha256", "a".repeat(64));
        dataset.put("userBundles", 1);
        dataset.put("queries", 1);
        dataset.put("directPositiveQueries", 1);
        dataset.put("notSupportedQueries", 0);
        var boundaries = contract.putArray("sourceBoundaries");
        for (String name : List.of("V2", "V3", "SHARED", "EVALUATOR")) {
            ObjectNode boundary = boundaries.addObject();
            boundary.put("name", name);
            boundary.putArray("files").add(frozenSource.toString());
            boundary.putArray("directories");
            boundary.put("sha256", boundarySha);
        }
        ObjectNode model = contract.putObject("model");
        model.put("modelId", "bge-m3");
        model.put("resolvedDigest", "c".repeat(64));
        model.put("dimension", 1024);
        model.put("similarity", "COSINE");
        contract.putObject("gate").put("frozenBeforeExecution", true)
                .put("releaseAdequacyMet", false);
        Path contractPath = temporaryDirectory.resolve("contract.json");
        Files.writeString(contractPath, mapper.writeValueAsString(contract), StandardCharsets.UTF_8);

        Prz042FinalFreeze freeze = new Prz042FinalFreeze();
        var input = freeze.verifyInput(contractPath);
        var attempt = freeze.claimAttempt(input, temporaryDirectory.resolve("attempt-1"));
        var opened = freeze.recordInputOpened(attempt);
        String source = "근거";
        var runtime = new Prz042FinalDataset.RuntimeInput(
                split, "unit-sealed-1", "SEALED_FINAL_TEST", input.contractSha256(),
                attempt.attemptSha256(), input.manifestSha256(), input.sealedCombinedSha256(),
                List.of(new Prz042FinalDataset.RuntimeDocument(
                        "U1", "GENERAL", "general", "D1", "D1", "L1", "V1", 1,
                        true, "문서", "OTHER", "KO", "sealed-final/documents/d1.txt", source,
                        Prz042FinalDataset.sha256(source))),
                List.of(new Prz042FinalDataset.RuntimeQuery(
                        "Q1", "U1", "질문", "KO", "GENERAL", "general")),
                List.of(), "d".repeat(64), 1, opened);
        var started = freeze.recordSearchStarted(opened, runtime);
        var predictions = freeze.freezeAndVerifyPredictions(started, bundle(input, runtime));
        var resumed = freeze.reloadVerifiedPredictions(started);
        var receipt = freeze.complete(predictions, Map.of("metric", 1), Map.of("verdict", "UNIT"));

        assertThat(predictions.canonicalSha256()).matches("[0-9a-f]{64}");
        assertThat(resumed.canonicalSha256()).isEqualTo(predictions.canonicalSha256());
        assertThat(resumed.fileSha256()).isEqualTo(predictions.fileSha256());
        assertThat(receipt.receiptSha256()).matches("[0-9a-f]{64}");
        assertThat(Files.readAllBytes(manifestPath)).isEqualTo(manifestBefore);
        assertThatThrownBy(() -> freeze.claimAttempt(input, temporaryDirectory.resolve("attempt-1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already exists");
    }

    @Test
    void recordsOpenedAndSearchExecutedEvenWhenTheOfficialRunFails() throws Exception {
        Path runDirectory = temporaryDirectory.resolve("failed-attempt");
        Files.createDirectories(runDirectory);
        Path attemptPath = runDirectory.resolve("attempt.json");
        String contractSha = "1".repeat(64);
        String combinedSha = "2".repeat(64);
        ObjectNode value = mapper.createObjectNode();
        value.put("artifactType", "PRZ042_OFFICIAL_ATTEMPT");
        value.put("protocolVersion", Prz042FinalFreeze.PROTOCOL_VERSION);
        value.put("attempt", 1);
        value.put("contractSha256", contractSha);
        value.put("sealedCombinedSha256", combinedSha);
        value.put("startedAt", "2026-09-02T00:00:00Z");
        Files.writeString(attemptPath, mapper.writeValueAsString(value), StandardCharsets.UTF_8);
        var input = new Prz042FinalFreeze.VerifiedInput(
                temporaryDirectory.resolve("contract.json"), contractSha,
                temporaryDirectory, temporaryDirectory.resolve("manifest.json"), "3".repeat(64),
                combinedSha, "unit", "SEALED_FINAL_TEST", "4".repeat(64), "5".repeat(40),
                "6".repeat(40), "bge-m3", "7".repeat(64), 1024, Map.of(), 1, 1, 1, 0);
        var attempt = new Prz042FinalFreeze.Attempt(
                input, runDirectory, attemptPath, Prz042FinalFreeze.sha256(attemptPath));
        Prz042FinalFreeze freeze = new Prz042FinalFreeze();
        var opened = freeze.recordInputOpened(attempt);
        var runtime = new Prz042FinalDataset.RuntimeInput(
                temporaryDirectory, "unit", "SEALED_FINAL_TEST", contractSha,
                attempt.attemptSha256(), input.manifestSha256(), combinedSha,
                List.of(), List.of(), List.of(), "8".repeat(64), 1, opened);
        freeze.recordSearchStarted(opened, runtime);
        var failure = freeze.recordFailure(attempt, "RUNTIME_SEARCH", new IllegalStateException("boom"));
        JsonNode receipt = mapper.readTree(failure.receiptPath().toFile());

        assertThat(receipt.path("opened").asBoolean()).isTrue();
        assertThat(receipt.path("searchExecuted").asBoolean()).isTrue();
        assertThat(receipt.path("stage").asText()).isEqualTo("RUNTIME_SEARCH");
        assertThatThrownBy(() -> freeze.recordFailure(
                attempt, "RUNTIME_SEARCH", new IllegalStateException("again")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CREATE_NEW");
    }

    private Prz042FinalFreeze.PredictionBundle bundle(
            Prz042FinalFreeze.VerifiedInput input,
            Prz042FinalDataset.RuntimeInput runtime) {
        var v2 = new ProductionV2ShadowAdapter.QueryRun(
                "NO_RELEVANT_RESULTS", 1, 1, 1, 1, 1, List.of(), List.of(), false, false);
        var v3 = new MinimalV3ShadowAdapter.QueryRun(
                "UNASSESSED", false, 0, 1, 1, 1, 1, 1, List.of(), List.of(), false, 0);
        var stats = new SearchV3MinimalShadowFreeze.IndexingStats(1, 1, 1, 1, 1, 4096);
        var output = new SearchV3MinimalShadowFreeze.OutputArtifact(
                1,
                Prz042FinalFreeze.OUTPUT_TYPE,
                input.baseCommit(),
                new SearchV3MinimalShadowFreeze.SourceFreeze(
                        input.sourceBoundaryHashes().get("V2"),
                        input.sourceBoundaryHashes().get("V3"),
                        input.sourceBoundaryHashes().get("EVALUATOR"),
                        runtime.canonicalSha256(), input.goldSchemaSha256(), input.contractSha256()),
                new SearchV3MinimalShadowFreeze.ModelIdentity(
                        input.modelId(), input.modelDigest(), input.modelDimension(), "COSINE"),
                "CURRENT", "MINIMAL_V3", "POSTGRESQL", 1, 1, 1,
                stats, stats, List.of(), List.of(),
                List.of(new SearchV3MinimalShadowFreeze.QueryOutput(
                        "FRESH_FINAL", "unit-sealed-1", "SEALED_FINAL_TEST", "Q1", "U1",
                        "GENERAL", "KO", Prz042FinalDataset.sha256("질문"), false, v2, v3)),
                new SearchV3MinimalShadowFreeze.SealedState(
                        input.sealedCombinedSha256(), input.manifestSha256(), input.sealedGitTree(), false, false,
                        "NOT_RUN"));
        var audit = new Prz042FinalFreeze.RuntimeAudit(
                1, 1, 1, 1, 0, 1, 1, 1, 1,
                0, 0, 0, 0, 0, true,
                input.modelId(), input.modelDigest(), input.modelDimension(), 1, 1,
                0, 0, false);
        return new Prz042FinalFreeze.PredictionBundle(output, audit);
    }
}
