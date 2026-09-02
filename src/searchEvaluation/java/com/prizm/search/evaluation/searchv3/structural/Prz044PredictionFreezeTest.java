package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.ingestion.service.PageText;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class Prz044PredictionFreezeTest {

    @TempDir Path temporaryDirectory;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void rejectsWindowsLinuxAndTraversalAlternatesBeforeClaim() throws Exception {
        Path project = project("alternate", "local/prz044/elsewhere/attempt-1", 1);
        Prz044PredictionFreeze freeze = new Prz044PredictionFreeze();

        assertThatThrownBy(() -> freeze.verifyContract(project))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("official run directory changed");
        assertThat(Files.exists(Prz044PredictionFreeze.resolvePortable(
                project, Prz044PredictionFreeze.OFFICIAL_RUN_DIRECTORY))).isFalse();

        for (String unsafe : List.of(
                "local\\search-v3-evaluation\\prz044\\attempt-1",
                "C:/local/search-v3-evaluation/prz044/attempt-1",
                "C:\\local\\search-v3-evaluation\\prz044\\attempt-1",
                "/local/search-v3-evaluation/prz044/attempt-1",
                "//server/share/attempt-1",
                "local//search-v3-evaluation/attempt-1",
                "local/./search-v3-evaluation/attempt-1",
                "local/../search-v3-evaluation/attempt-1")) {
            assertThatThrownBy(() -> Prz044PredictionFreeze.resolvePortable(project, unsafe))
                    .as(unsafe)
                    .isInstanceOf(IllegalStateException.class);
        }

        Path strict = project("strict-fields", Prz044PredictionFreeze.OFFICIAL_RUN_DIRECTORY, 1);
        Path strictContract = Prz044PredictionFreeze.resolvePortable(
                strict, Prz044PredictionFreeze.CONTRACT_RELATIVE);
        ObjectNode withGoldPath = (ObjectNode) mapper.readTree(Files.readAllBytes(strictContract));
        withGoldPath.put("goldPath", "sealed/gold.json");
        Files.writeString(strictContract, mapper.writeValueAsString(withGoldPath), StandardCharsets.UTF_8);
        assertThatThrownBy(() -> freeze.verifyContract(strict))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("contract fields changed");
    }

    @Test
    void bindsExactOfficialDirectoryAndConsumesTheOnlyAttempt() throws Exception {
        Path project = project("claim", Prz044PredictionFreeze.OFFICIAL_RUN_DIRECTORY, 1);
        Prz044PredictionFreeze freeze = new Prz044PredictionFreeze();
        var contract = freeze.verifyContract(project);
        var input = input(project);
        var model = contract.expectedModel();

        assertThat(freeze.verifyPreflightPass(contract).receiptSha256()).matches("[0-9a-f]{64}");

        var attempt = freeze.claimOfficialAttempt(contract, input, model);

        assertThat(attempt.runDirectory()).isEqualTo(
                Prz044PredictionFreeze.resolvePortable(project, Prz044PredictionFreeze.OFFICIAL_RUN_DIRECTORY));
        assertThat(Files.isRegularFile(attempt.attemptPath())).isTrue();
        assertThatThrownBy(() -> freeze.claimOfficialAttempt(contract, input, model))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already contains");

        Path wrongRuns = project("wrong-runs", Prz044PredictionFreeze.OFFICIAL_RUN_DIRECTORY, 2);
        assertThatThrownBy(() -> freeze.verifyContract(wrongRuns))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("officialRunsAllowed");

        Path missingPreflight = project(
                "missing-preflight", Prz044PredictionFreeze.OFFICIAL_RUN_DIRECTORY, 1);
        var missingContract = freeze.verifyContract(missingPreflight);
        Files.delete(Prz044PredictionFreeze.resolvePortable(
                missingPreflight, Prz044Attempt2PreflightReceipt.RECEIPT_RELATIVE));
        assertThatThrownBy(() -> freeze.claimOfficialAttempt(
                missingContract, input(missingPreflight), missingContract.expectedModel()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("attempt-2 preflight receipt");
        assertThat(Files.exists(Prz044PredictionFreeze.resolvePortable(
                missingPreflight, Prz044PredictionFreeze.OFFICIAL_RUN_DIRECTORY))).isFalse();
    }

    @Test
    void writesReloadsHashesAndVerifiesDatasetIndependentPreflightReceipt() throws Exception {
        Path project = project("preflight-receipt", Prz044PredictionFreeze.OFFICIAL_RUN_DIRECTORY, 1);
        Path receiptPath = Prz044PredictionFreeze.resolvePortable(
                project, Prz044PredictionFreeze.PREFLIGHT_RECEIPT_RELATIVE);
        Path hashPath = project.resolve(
                "local/search-v3-evaluation/prz044/preflight/source-freeze-v2/"
                        + "preflight-pass-receipt-hash.json");
        Files.delete(receiptPath);
        Files.delete(hashPath);

        Prz044PredictionFreeze freeze = new Prz044PredictionFreeze();
        var contract = freeze.verifyContract(project);
        var precheck = new Prz044PredictionRuntime.ModelPrecheck(
                contract.expectedModel(), Prz044PredictionFreeze.sha256("neutral warm-up"),
                "2026-09-03T00:00:00Z");
        var run = freeze.beginPreflight(contract, precheck);
        var v2 = freeze.freezePreflightV2(
                run, preflightPredictions(run, Prz044PredictionArtifact.Engine.V2));
        var receipt = freeze.completePreflight(
                v2,
                preflightPredictions(run, Prz044PredictionArtifact.Engine.V3),
                new Prz044PredictionFreeze.PreflightEvidence(
                        "PostgreSQL 16.10 unit fixture", "0.8.2", true, true, true));

        assertThat(receipt.receiptPath()).isEqualTo(receiptPath).isRegularFile();
        assertThat(receipt.receiptSha256()).matches("[0-9a-f]{64}");
        assertThat(freeze.verifyPreflightPass(contract).receiptSha256())
                .isEqualTo(receipt.receiptSha256());
        JsonNode json = mapper.readTree(Files.readAllBytes(receiptPath));
        assertThat(json.path("status").asText()).isEqualTo("PASS");
        assertThat(json.path("coverage").path("predictionWriter").asBoolean()).isTrue();
        assertThat(json.path("coverage").path("diskReload").asBoolean()).isTrue();
        assertThat(json.path("coverage").path("createNewOneShot").asBoolean()).isTrue();
        assertThat(json.has("inputZipSha256") || json.has("dataset")).isFalse();
        assertThat(Files.exists(Prz044PredictionFreeze.resolvePortable(
                project, Prz044PredictionFreeze.OFFICIAL_RUN_DIRECTORY))).isFalse();
    }

    @Test
    void freezesV2ThenV3SeparatelyReloadsFromDiskAndCompletesWithoutGold() throws Exception {
        Path project = project("freeze", Prz044PredictionFreeze.OFFICIAL_RUN_DIRECTORY, 1);
        Prz044PredictionFreeze freeze = new Prz044PredictionFreeze();
        var contract = freeze.verifyContract(project);
        var input = input(project);
        var attempt = freeze.claimOfficialAttempt(contract, input, contract.expectedModel());

        var v2 = freeze.freezeV2(attempt, predictions(attempt, Prz044PredictionArtifact.Engine.V2));
        var v2Reloaded = freeze.reload(attempt, Prz044PredictionArtifact.Engine.V2);
        var v3 = freeze.freezeV3(v2, predictions(attempt, Prz044PredictionArtifact.Engine.V3));
        var v3Reloaded = freeze.reload(attempt, Prz044PredictionArtifact.Engine.V3);
        var completion = freeze.complete(v3);

        assertThat(v2Reloaded.canonicalSha256()).isEqualTo(v2.prediction().canonicalSha256());
        assertThat(v2Reloaded.fileSha256()).isEqualTo(v2.prediction().fileSha256());
        assertThat(v3Reloaded.canonicalSha256()).isEqualTo(v3.prediction().canonicalSha256());
        assertThat(v3Reloaded.fileSha256()).isEqualTo(v3.prediction().fileSha256());
        assertThat(v2Reloaded.canonicalSha256()).isNotEqualTo(v3Reloaded.canonicalSha256());
        assertThat(completion.receiptSha256()).matches("[0-9a-f]{64}");
        String receipt = Files.readString(completion.receiptPath());
        assertThat(receipt).contains("\"v2Rows\":600", "\"v3Rows\":600",
                "\"goldPresent\":false", "\"goldAccessed\":false");
        assertThat(attempt.runDirectory().resolve("v2-predictions.json")).isRegularFile();
        assertThat(attempt.runDirectory().resolve("v3-predictions.json")).isRegularFile();
        try (var files = Files.list(attempt.runDirectory())) {
            assertThat(files.map(path -> path.getFileName().toString()).toList())
                    .allMatch(name -> !name.toLowerCase().contains("gold")
                            && !name.toLowerCase().contains("metric"));
        }
        assertThatThrownBy(() -> freeze.complete(v3))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("terminal");
    }

    @Test
    void rejectsSecondFreezeDiskTamperingQueryOwnerMismatchAndLeakage() throws Exception {
        Path project = project("tamper", Prz044PredictionFreeze.OFFICIAL_RUN_DIRECTORY, 1);
        Prz044PredictionFreeze freeze = new Prz044PredictionFreeze();
        var contract = freeze.verifyContract(project);
        var attempt = freeze.claimOfficialAttempt(contract, input(project), contract.expectedModel());
        var v2Set = predictions(attempt, Prz044PredictionArtifact.Engine.V2);
        var v2 = freeze.freezeV2(attempt, v2Set);

        assertThatThrownBy(() -> freeze.freezeV2(attempt, v2Set))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already frozen");

        Files.writeString(v2.prediction().outputPath(), "{}", StandardCharsets.UTF_8);
        assertThatThrownBy(() -> freeze.freezeV3(
                v2, predictions(attempt, Prz044PredictionArtifact.Engine.V3)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("file changed");

        Path ownerProject = project("owner", Prz044PredictionFreeze.OFFICIAL_RUN_DIRECTORY, 1);
        var ownerContract = freeze.verifyContract(ownerProject);
        var ownerAttempt = freeze.claimOfficialAttempt(
                ownerContract, input(ownerProject), ownerContract.expectedModel());
        var invalidOwner = predictionsWithFirstResult(ownerAttempt, "other-user", false);
        assertThatThrownBy(() -> freeze.freezeV2(ownerAttempt, invalidOwner))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner differs");

        Path leakageProject = project("leakage", Prz044PredictionFreeze.OFFICIAL_RUN_DIRECTORY, 1);
        var leakageContract = freeze.verifyContract(leakageProject);
        var leakageAttempt = freeze.claimOfficialAttempt(
                leakageContract, input(leakageProject), leakageContract.expectedModel());
        assertThatThrownBy(() -> freeze.freezeV2(
                leakageAttempt, predictionsWithFirstResult(leakageAttempt, "user-1", true)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("owner leakage");
    }

    private Path project(String name, String runDirectory, int allowedRuns) throws Exception {
        Path project = temporaryDirectory.resolve(name);
        Files.createDirectories(project);
        Path source = project.resolve("source.txt");
        Files.writeString(source, "frozen source", StandardCharsets.UTF_8);
        String sourceSha = Prz044PredictionFreeze.canonicalFileSetSha256(project, List.of(source));

        ObjectNode root = mapper.createObjectNode();
        root.put("artifactType", Prz044PredictionFreeze.CONTRACT_TYPE);
        root.put("protocolVersion", Prz044PredictionFreeze.PROTOCOL_VERSION);
        root.put("status", "INPUT_FROZEN");
        root.put("attempt", 2);
        root.put("baseCommit", "0e95472bb68f72accf0d6b2171c22f0719fe6941");
        ObjectNode dataset = root.putObject("dataset");
        dataset.put("datasetId", "prizm-release-eval-v1.0.3");
        dataset.put("datasetVersion", "1.0.3");
        dataset.put("evaluationSplit", "FINAL_SEALED");
        dataset.put("inputZipSha256",
                "8293ba115b74967b137d2ddd5f21dee98b8bbdb4822958808e6d117552bfb8c0");
        dataset.put("manifestSha256",
                "1c6a363f06765c4715a03e70d2cb70e3f045259d651e6be621b5ddb92b9dede1");
        dataset.put("manifestCanonicalSha256",
                "762b520be8618657f4f57e6829c60b68857c87c86b142d7003a7c2f9156d890a");
        dataset.put("physicalPayloadCombinedSha256",
                "8413cf153302754c0625fb2d594bea4e10df8ac73f35259b7f7fe4695dad63b0");
        dataset.put("manifestCombinedCommitmentSha256",
                "6a7eca9b327b59ec5d0c5448cb08d1738298739747dd9509ec5a335a467f68ec");
        dataset.put("sealedCommitmentSha256",
                "d0a507764449315645fabac06d785c1ef8598b1f9ab131674b6e20ad58dda696");
        dataset.put("physicalPayloadCount", 92);
        dataset.put("userCount", 75);
        dataset.put("documentCount", 90);
        dataset.put("queryCount", 600);
        dataset.put("txtCount", 45);
        dataset.put("pdfCount", 45);
        dataset.put("professionCount", 15);
        ObjectNode model = root.putObject("model");
        model.put("modelId", "bge-m3");
        model.put("resolvedDigest",
                "7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab");
        model.put("dimension", 1024);
        model.put("similarity", "COSINE");
        root.putObject("profiles").put("v2", "V2_PROFILE").put("v3", "V3_PROFILE");
        writeMappingContract(project);
        String mappingSha = Prz044PredictionFreeze.sha256(Prz044PredictionFreeze.resolvePortable(
                project, Prz044DocumentTypeMapping.CONTRACT_RELATIVE));
        root.putObject("documentTypeMapping")
                .put("path", Prz044DocumentTypeMapping.CONTRACT_RELATIVE)
                .put("version", Prz044DocumentTypeMapping.VERSION)
                .put("sha256", mappingSha);
        var boundaries = root.putArray("sourceBoundaries");
        for (String boundaryName : List.of("V2", "V3", "SHARED", "EVALUATOR")) {
            ObjectNode boundary = boundaries.addObject();
            boundary.put("name", boundaryName);
            boundary.putArray("files").add("source.txt");
            boundary.putArray("directories");
            boundary.put("sha256", sourceSha);
        }
        root.putObject("execution")
                .put("runDirectory", runDirectory)
                .put("officialRunsAllowed", allowedRuns)
                .put("attemptIdentity", Prz044PredictionFreeze.ATTEMPT_IDENTITY);
        root.putObject("goldPolicy")
                .put("physicalGoldAllowed", false)
                .put("goldLoaderAllowed", false)
                .put("metricAllowed", false);
        Path contract = Prz044PredictionFreeze.resolvePortable(project, Prz044PredictionFreeze.CONTRACT_RELATIVE);
        Files.createDirectories(contract.getParent());
        Files.writeString(contract, mapper.writeValueAsString(root), StandardCharsets.UTF_8);
        writeSyntheticPreflightReceipt(project, contract, sourceSha);
        writeAttempt1Artifacts(project);
        var input = input(project);
        var mapping = new Prz044DocumentTypeMapping();
        new Prz044Attempt2PreflightReceipt().write(
                project,
                mapping.verifyContract(project),
                input,
                mapping.audit(input.documents()),
                Prz044PredictionFreeze.officialModelIdentity(),
                "PostgreSQL 16.10 synthetic unit fixture",
                "0.8.2",
                1,
                1);
        return project.toAbsolutePath().normalize();
    }

    private void writeMappingContract(Path project) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("artifactType", Prz044DocumentTypeMapping.ARTIFACT_TYPE);
        root.put("version", Prz044DocumentTypeMapping.VERSION);
        root.putArray("datasetTypes").add("CAREER_DESCRIPTION").add("PORTFOLIO").add("RESUME");
        var production = root.putArray("productionTypes");
        for (var type : com.prizm.document.entity.DocumentType.values()) production.add(type.name());
        var mappings = root.putArray("mappings");
        mappings.addObject().put("source", "CAREER_DESCRIPTION").put("target", "RESUME");
        mappings.addObject().put("source", "PORTFOLIO").put("target", "PORTFOLIO");
        mappings.addObject().put("source", "RESUME").put("target", "RESUME");
        root.put("unknownPolicy", "FAIL_CLOSED");
        root.put("fallbackAllowed", false);
        Path path = Prz044PredictionFreeze.resolvePortable(
                project, Prz044DocumentTypeMapping.CONTRACT_RELATIVE);
        Files.createDirectories(path.getParent());
        Files.writeString(path, mapper.writeValueAsString(root) + "\n", StandardCharsets.UTF_8);
    }

    private void writeAttempt1Artifacts(Path project) throws Exception {
        Path directory = Prz044PredictionFreeze.resolvePortable(project,
                "local/search-v3-evaluation/prz044/official/"
                        + "6a7eca9b327b59ec5d0c5448cb08d1738298739747dd9509ec5a335a467f68ec/attempt-1");
        Files.createDirectories(directory);
        String attempt = "{\"artifactType\":\"PRZ044_OFFICIAL_PREDICTION_ATTEMPT\","
                + "\"attempt\":1,\"contractSha256\":\"be03a7edb6d836478b7daaa406b52bf023e67e222be37d020a89f1700bb51913\","
                + "\"goldAccessed\":false,\"goldPresent\":false,"
                + "\"inputZipSha256\":\"8293ba115b74967b137d2ddd5f21dee98b8bbdb4822958808e6d117552bfb8c0\","
                + "\"manifestCombinedCommitmentSha256\":\"6a7eca9b327b59ec5d0c5448cb08d1738298739747dd9509ec5a335a467f68ec\","
                + "\"modelDigest\":\"7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab\","
                + "\"modelId\":\"bge-m3\","
                + "\"physicalPayloadCombinedSha256\":\"8413cf153302754c0625fb2d594bea4e10df8ac73f35259b7f7fe4695dad63b0\","
                + "\"protocolVersion\":\"PRZ044_PREDICTION_FREEZE_V1\","
                + "\"startedAt\":\"2026-09-02T18:36:17.849961800Z\"}\n";
        String failure = "{\"artifactType\":\"PRZ044_PREDICTION_FAILURE_RECEIPT\","
                + "\"attempt\":1,\"attemptSha256\":\"5630c6d6d2028076b862abdb3e2fa60b2c80e81196cdb71e596cc8e033c7bb74\","
                + "\"contractSha256\":\"be03a7edb6d836478b7daaa406b52bf023e67e222be37d020a89f1700bb51913\","
                + "\"failureMessageSha256\":\"6b34f994a9fbcc9ac0fd09328b82d82c50ca515b964b3c193acdd2d859bedd7f\","
                + "\"failureType\":\"java.lang.IllegalStateException\","
                + "\"goldAccessed\":false,\"goldPresent\":false,"
                + "\"protocolVersion\":\"PRZ044_PREDICTION_FREEZE_V1\","
                + "\"recordedAt\":\"2026-09-02T18:36:17.940027400Z\","
                + "\"stage\":\"RUNTIME_V2\",\"status\":\"FAILED_ATTEMPT_CONSUMED\","
                + "\"v2Frozen\":false,\"v3Frozen\":false}\n";
        Files.writeString(directory.resolve("attempt.json"), attempt, StandardCharsets.UTF_8);
        Files.writeString(directory.resolve("failure-receipt.json"), failure, StandardCharsets.UTF_8);
    }

    private void writeSyntheticPreflightReceipt(Path project, Path contract, String sourceSha) throws Exception {
        ObjectNode receipt = mapper.createObjectNode();
        receipt.put("artifactType", "PRZ044_PREFLIGHT_PASS_RECEIPT");
        receipt.put("protocolVersion", Prz044PredictionFreeze.PROTOCOL_VERSION);
        receipt.put("status", "PASS");
        receipt.put("contractSha256", Prz044PredictionFreeze.sha256(contract));
        receipt.put("baseCommit", "0e95472bb68f72accf0d6b2171c22f0719fe6941");
        receipt.set("model", mapper.valueToTree(new Prz044PredictionArtifact.ModelIdentity(
                "bge-m3",
                "7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab",
                1024,
                "COSINE")));
        ObjectNode sources = receipt.putObject("sourceBoundaryHashes");
        for (String name : List.of("V2", "V3", "SHARED", "EVALUATOR")) {
            sources.put(name, sourceSha);
        }
        receipt.put("evaluatorSourceSha256", sourceSha);
        receipt.putObject("profiles").put("v2", "V2_PROFILE").put("v3", "V3_PROFILE");
        receipt.put("postgresqlVersion", "PostgreSQL 16.10 synthetic unit fixture");
        receipt.put("pgvectorVersion", "0.8.2");
        receipt.put("fixtureKind", "SYNTHETIC_TXT_PDF");
        receipt.put("v2CanonicalSha256", "a".repeat(64));
        receipt.put("v2FileSha256", "b".repeat(64));
        receipt.put("v3CanonicalSha256", "c".repeat(64));
        receipt.put("v3FileSha256", "d".repeat(64));
        ObjectNode coverage = receipt.putObject("coverage");
        for (String field : List.of(
                "actualPostgreSqlPgvector", "actualBgeM3", "txtExtraction", "pdfExtraction",
                "pdfPageProvenance", "v2Runtime", "v3Runtime", "predictionWriter",
                "canonicalHash", "diskReload", "createNewOneShot")) {
            coverage.put(field, true);
        }
        receipt.put("officialDatasetAccessed", false);
        receipt.put("goldPresent", false);
        receipt.put("goldAccessed", false);
        Path receiptPath = Prz044PredictionFreeze.resolvePortable(
                project, Prz044PredictionFreeze.PREFLIGHT_RECEIPT_RELATIVE);
        Files.createDirectories(receiptPath.getParent());
        Files.writeString(receiptPath, mapper.writeValueAsString(receipt), StandardCharsets.UTF_8);

        ObjectNode receiptHash = mapper.createObjectNode();
        receiptHash.put("artifactType", "PRZ044_PREFLIGHT_PASS_RECEIPT_HASH");
        receiptHash.put("protocolVersion", Prz044PredictionFreeze.PROTOCOL_VERSION);
        receiptHash.put("receiptPath", Prz044PredictionFreeze.PREFLIGHT_RECEIPT_RELATIVE);
        receiptHash.put("receiptSha256", Prz044PredictionFreeze.sha256(receiptPath));
        Path hashPath = project.resolve(
                "local/search-v3-evaluation/prz044/preflight/source-freeze-v2/"
                        + "preflight-pass-receipt-hash.json");
        Files.writeString(hashPath, mapper.writeValueAsString(receiptHash), StandardCharsets.UTF_8);
    }

    private Prz044PredictionDataset.VerifiedInputPackage input(Path project) {
        List<Prz044PredictionDataset.RuntimeUser> users = IntStream.rangeClosed(1, 75)
                .mapToObj(index -> new Prz044PredictionDataset.RuntimeUser(
                        "user-" + index, "PROFESSION-" + (((index - 1) / 5) + 1),
                        "직군 " + (((index - 1) / 5) + 1), List.of()))
                .toList();
        byte[] content = "문서".getBytes(StandardCharsets.UTF_8);
        List<Prz044PredictionDataset.RuntimeDocument> documents = IntStream.rangeClosed(1, 90)
                .mapToObj(index -> {
                    int ownerIndex = ((index - 1) % 75) + 1;
                    int professionIndex = ((ownerIndex - 1) / 5) + 1;
                    DocumentFileType fileType = index <= 45 ? DocumentFileType.TXT : DocumentFileType.PDF;
                    String extension = fileType == DocumentFileType.TXT ? ".txt" : ".pdf";
                    String sourceType = index <= 15
                            ? "CAREER_DESCRIPTION"
                            : index <= 30 ? "PORTFOLIO" : "RESUME";
                    return new Prz044PredictionDataset.RuntimeDocument(
                            "user-" + ownerIndex, "PROFESSION-" + professionIndex,
                            "직군 " + professionIndex, "document-" + index, "version-" + index,
                            sourceType, fileType, "document-" + index + extension,
                            "corpus/document-" + index + extension,
                            Prz044PredictionFreeze.sha256(content), content,
                            List.of(new PageText(1, "문서")), List.of());
                })
                .toList();
        List<Prz044PredictionDataset.RuntimeQuery> queries = IntStream.rangeClosed(1, 600)
                .mapToObj(index -> {
                    String query = "질문 " + index;
                    int ownerIndex = ((index - 1) % 75) + 1;
                    int professionIndex = ((ownerIndex - 1) / 5) + 1;
                    return new Prz044PredictionDataset.RuntimeQuery(
                            "query-" + index, "user-" + ownerIndex,
                            "PROFESSION-" + professionIndex, "직군 " + professionIndex, "KO", query,
                            Prz044PredictionFreeze.sha256(query));
                })
                .toList();
        List<Prz044PredictionDataset.InputFile> inputFiles = IntStream.rangeClosed(1, 92)
                .mapToObj(index -> new Prz044PredictionDataset.InputFile(
                        "payload-" + index, 1L, Prz044PredictionFreeze.sha256("payload-" + index)))
                .toList();
        return new Prz044PredictionDataset.VerifiedInputPackage(
                project.resolve("input.zip"),
                "8293ba115b74967b137d2ddd5f21dee98b8bbdb4822958808e6d117552bfb8c0",
                "1c6a363f06765c4715a03e70d2cb70e3f045259d651e6be621b5ddb92b9dede1",
                "762b520be8618657f4f57e6829c60b68857c87c86b142d7003a7c2f9156d890a",
                "8413cf153302754c0625fb2d594bea4e10df8ac73f35259b7f7fe4695dad63b0",
                "6a7eca9b327b59ec5d0c5448cb08d1738298739747dd9509ec5a335a467f68ec",
                new Prz044PredictionDataset.SealedCommitment(
                        "sealed/gold.json", 1_529_904,
                        "d0a507764449315645fabac06d785c1ef8598b1f9ab131674b6e20ad58dda696"),
                List.of(), inputFiles, users, documents, queries);
    }

    private Prz044PredictionArtifact.PredictionSet predictions(
            Prz044PredictionFreeze.OfficialAttempt attempt,
            Prz044PredictionArtifact.Engine engine) {
        List<Prz044PredictionArtifact.QueryPrediction> queries = attempt.input().queries().stream()
                .map(query -> new Prz044PredictionArtifact.QueryPrediction(
                        query.queryId(), query.userId(), query.professionId(), query.professionLabel(),
                        query.language(), query.querySha256(), "NO_RESULTS", 1.0d, List.of()))
                .toList();
        return prediction(attempt, engine, queries, 0L);
    }

    private Prz044PredictionArtifact.PredictionSet preflightPredictions(
            Prz044PredictionFreeze.PreflightRun run,
            Prz044PredictionArtifact.Engine engine) {
        List<Prz044PredictionArtifact.QueryPrediction> queries = IntStream.rangeClosed(1, 2)
                .mapToObj(index -> new Prz044PredictionArtifact.QueryPrediction(
                        "synthetic-query-" + index,
                        "synthetic-user-" + index,
                        "synthetic-profession-" + index,
                        "Synthetic profession " + index,
                        "EN",
                        Prz044PredictionFreeze.sha256("synthetic query " + index),
                        "NO_RESULTS",
                        1.0d,
                        List.of()))
                .toList();
        var model = run.contract().expectedModel();
        return new Prz044PredictionArtifact.PredictionSet(
                Prz044PredictionArtifact.ARTIFACT_TYPE,
                Prz044PredictionArtifact.SCHEMA_VERSION,
                engine,
                run.contract().expectedProfiles().get(engine),
                run.contract().contractSha256(),
                "1".repeat(64),
                "2".repeat(64),
                "3".repeat(64),
                "4".repeat(64),
                run.contract().sourceBoundaryHashes(),
                model,
                "5".repeat(64),
                "2026-09-03T00:00:00Z",
                "2026-09-03T00:01:00Z",
                new Prz044PredictionArtifact.IndexingStats(2, 2, 2, 8_192, 1.0d, "UNIT"),
                new Prz044PredictionArtifact.RuntimeAudit(
                        2, 2, 2, 0, 0, 0, 0, 0, 0, true,
                        model.modelId(), model.resolvedDigest(), model.dimension(), 0, 0, false),
                queries);
    }

    private Prz044PredictionArtifact.PredictionSet predictionsWithFirstResult(
            Prz044PredictionFreeze.OfficialAttempt attempt,
            String spanOwner,
            boolean ownerLeakage) {
        List<Prz044PredictionArtifact.QueryPrediction> queries = new ArrayList<>();
        for (int index = 0; index < attempt.input().queries().size(); index++) {
            var input = attempt.input().queries().get(index);
            List<Prz044PredictionArtifact.Result> results = index == 0
                    ? List.of(new Prz044PredictionArtifact.Result(
                            1, "stable-1", "parent-1", 0.5d, "RESULT",
                            List.of(span(spanOwner)), List.of(span(spanOwner))))
                    : List.of();
            queries.add(new Prz044PredictionArtifact.QueryPrediction(
                    input.queryId(), input.userId(), input.professionId(), input.professionLabel(),
                    input.language(), input.querySha256(), results.isEmpty() ? "NO_RESULTS" : "RESULTS",
                    1.0d, results));
        }
        return prediction(attempt, Prz044PredictionArtifact.Engine.V2, queries, ownerLeakage ? 1L : 0L);
    }

    private Prz044PredictionArtifact.PredictionSet prediction(
            Prz044PredictionFreeze.OfficialAttempt attempt,
            Prz044PredictionArtifact.Engine engine,
            List<Prz044PredictionArtifact.QueryPrediction> queries,
            long ownerLeakage) {
        return new Prz044PredictionArtifact.PredictionSet(
                Prz044PredictionArtifact.ARTIFACT_TYPE,
                Prz044PredictionArtifact.SCHEMA_VERSION,
                engine,
                attempt.contract().expectedProfiles().get(engine),
                attempt.contract().contractSha256(),
                attempt.input().zipSha256(),
                attempt.input().manifestCanonicalSha256(),
                attempt.input().physicalCombinedSha256(),
                attempt.input().commitmentCombinedSha256(),
                attempt.contract().sourceBoundaryHashes(),
                attempt.model(),
                Prz044PredictionFreeze.queryInventorySha256(attempt.input().queries()),
                "2026-09-03T00:00:00Z",
                "2026-09-03T00:01:00Z",
                new Prz044PredictionArtifact.IndexingStats(90, 90, 90, 368_640, 1.0d, "UNIT"),
                new Prz044PredictionArtifact.RuntimeAudit(
                        75, 90, 600, ownerLeakage, 0, 0, 0, 0, 0, true,
                        attempt.model().modelId(), attempt.model().resolvedDigest(),
                        attempt.model().dimension(), 0, 0, false),
                queries);
    }

    private Prz044PredictionArtifact.SourceSpan span(String owner) {
        return new Prz044PredictionArtifact.SourceSpan(
                owner, "document-1", "version-1", "RESUME", DocumentFileType.TXT,
                "corpus/document-1.txt", 1, 0, 2, Prz044PredictionFreeze.sha256("문서"));
    }
}
