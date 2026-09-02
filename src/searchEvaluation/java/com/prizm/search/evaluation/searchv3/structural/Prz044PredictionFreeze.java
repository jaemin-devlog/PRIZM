package com.prizm.search.evaluation.searchv3.structural;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Contract-bound, one-shot prediction freeze for PRZ-044.
 *
 * <p>This boundary intentionally has no Gold path, Gold-open permit, metric, or report API.</p>
 */
final class Prz044PredictionFreeze {

    static final String CONTRACT_TYPE = "PRZ044_PREDICTION_FREEZE_CONTRACT";
    static final String PROTOCOL_VERSION = "PRZ044_PREDICTION_FREEZE_V1";
    static final String CONTRACT_RELATIVE =
            "specs/PRZ-044-search-v3-release-grade-evaluation/execution-contract.json";
    static final String OFFICIAL_RUN_DIRECTORY =
            "local/search-v3-evaluation/prz044/official/"
                    + "6a7eca9b327b59ec5d0c5448cb08d1738298739747dd9509ec5a335a467f68ec/attempt-1";
    static final String PREFLIGHT_RECEIPT_RELATIVE =
            "local/search-v3-evaluation/prz044/preflight/source-freeze-v2/"
                    + "preflight-pass-receipt.json";
    private static final String PREFLIGHT_HASH_RECEIPT_RELATIVE =
            "local/search-v3-evaluation/prz044/preflight/source-freeze-v2/"
                    + "preflight-pass-receipt-hash.json";
    private static final String PREFLIGHT_PROBE_DIRECTORY =
            "build/prz044-preflight-source-freeze-v2-prediction-probes";

    private static final String DATASET_ID = "prizm-release-eval-v1.0.3";
    private static final String DATASET_VERSION = "1.0.3";
    private static final String EVALUATION_SPLIT = "FINAL_SEALED";
    private static final String INPUT_ZIP_SHA256 =
            "8293ba115b74967b137d2ddd5f21dee98b8bbdb4822958808e6d117552bfb8c0";
    private static final String MANIFEST_SHA256 =
            "1c6a363f06765c4715a03e70d2cb70e3f045259d651e6be621b5ddb92b9dede1";
    private static final String MANIFEST_CANONICAL_SHA256 =
            "762b520be8618657f4f57e6829c60b68857c87c86b142d7003a7c2f9156d890a";
    private static final String PHYSICAL_PAYLOAD_SHA256 =
            "8413cf153302754c0625fb2d594bea4e10df8ac73f35259b7f7fe4695dad63b0";
    private static final String COMMITMENT_SHA256 =
            "6a7eca9b327b59ec5d0c5448cb08d1738298739747dd9509ec5a335a467f68ec";
    private static final String SEALED_COMMITMENT_SHA256 =
            "d0a507764449315645fabac06d785c1ef8598b1f9ab131674b6e20ad58dda696";
    private static final String MODEL_ID = "bge-m3";
    private static final String SEARCH_SOURCE_BASE_COMMIT =
            "0e95472bb68f72accf0d6b2171c22f0719fe6941";
    private static final String MODEL_DIGEST =
            "7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab";

    private final ObjectMapper mapper = new ObjectMapper();

    VerifiedContract verifyContract(Path projectRoot) {
        Path rootDirectory = normalizedDirectory(projectRoot, "PRZ-044 project root");
        Path contractPath = regularProjectFile(
                rootDirectory, resolvePortable(rootDirectory, CONTRACT_RELATIVE), "PRZ-044 execution contract");
        JsonNode root = read(contractPath);
        requireExactFields(root, "contract", Set.of(
                "artifactType", "protocolVersion", "status", "attempt", "baseCommit",
                "dataset", "model", "profiles", "sourceBoundaries", "execution", "goldPolicy"));
        require(CONTRACT_TYPE.equals(text(root, "artifactType")), "contract artifact type changed");
        require(PROTOCOL_VERSION.equals(text(root, "protocolVersion")), "contract protocol changed");
        require("INPUT_FROZEN".equals(text(root, "status")), "contract is not INPUT_FROZEN");
        require(root.path("attempt").asInt(-1) == 1, "official attempt must be exactly one");
        require(SEARCH_SOURCE_BASE_COMMIT.equals(text(root, "baseCommit")),
                "Search source base commit changed");

        JsonNode dataset = root.path("dataset");
        requireExactFields(dataset, "dataset", Set.of(
                "datasetId", "datasetVersion", "evaluationSplit", "inputZipSha256",
                "manifestSha256", "manifestCanonicalSha256", "physicalPayloadCombinedSha256",
                "manifestCombinedCommitmentSha256", "sealedCommitmentSha256",
                "physicalPayloadCount", "userCount", "documentCount", "queryCount",
                "txtCount", "pdfCount", "professionCount"));
        require(DATASET_ID.equals(text(dataset, "datasetId")), "dataset ID changed");
        require(DATASET_VERSION.equals(text(dataset, "datasetVersion")), "dataset version changed");
        require(EVALUATION_SPLIT.equals(text(dataset, "evaluationSplit")), "dataset split changed");
        require(INPUT_ZIP_SHA256.equals(text(dataset, "inputZipSha256")), "INPUT ZIP SHA changed");
        require(MANIFEST_SHA256.equals(text(dataset, "manifestSha256")), "manifest raw SHA changed");
        require(MANIFEST_CANONICAL_SHA256.equals(text(dataset, "manifestCanonicalSha256")),
                "manifest canonical SHA changed");
        require(PHYSICAL_PAYLOAD_SHA256.equals(text(dataset, "physicalPayloadCombinedSha256")),
                "physical payload combined SHA changed");
        require(COMMITMENT_SHA256.equals(text(dataset, "manifestCombinedCommitmentSha256")),
                "manifest combined commitment changed");
        require(SEALED_COMMITMENT_SHA256.equals(text(dataset, "sealedCommitmentSha256")),
                "sealed commitment changed");

        Prz044PredictionDataset.ExpectedInput expectedInput = new Prz044PredictionDataset.ExpectedInput(
                DATASET_ID,
                DATASET_VERSION,
                EVALUATION_SPLIT,
                INPUT_ZIP_SHA256,
                MANIFEST_SHA256,
                MANIFEST_CANONICAL_SHA256,
                PHYSICAL_PAYLOAD_SHA256,
                COMMITMENT_SHA256,
                SEALED_COMMITMENT_SHA256,
                requiredInt(dataset, "physicalPayloadCount", 1),
                requiredInt(dataset, "userCount", 1),
                requiredInt(dataset, "documentCount", 1),
                requiredInt(dataset, "queryCount", 1),
                requiredInt(dataset, "txtCount", 1),
                requiredInt(dataset, "pdfCount", 1),
                requiredInt(dataset, "professionCount", 1));
        require(expectedInput.physicalPayloadCount() == 92, "physical payload count changed");
        require(expectedInput.userCount() == 75, "user count changed");
        require(expectedInput.documentCount() == 90, "document count changed");
        require(expectedInput.queryCount() == 600, "query count changed");
        require(expectedInput.txtCount() == 45 && expectedInput.pdfCount() == 45,
                "TXT/PDF distribution changed");
        require(expectedInput.professionCount() == 15, "profession count changed");

        JsonNode model = root.path("model");
        requireExactFields(model, "model", Set.of(
                "modelId", "resolvedDigest", "dimension", "similarity"));
        Prz044PredictionArtifact.ModelIdentity expectedModel = new Prz044PredictionArtifact.ModelIdentity(
                text(model, "modelId"),
                text(model, "resolvedDigest"),
                requiredInt(model, "dimension", 1),
                text(model, "similarity"));
        require(MODEL_ID.equals(expectedModel.modelId()), "model ID changed");
        require(MODEL_DIGEST.equals(expectedModel.resolvedDigest()), "model digest changed");
        require(expectedModel.dimension() == 1024, "model dimension changed");
        require("COSINE".equals(expectedModel.similarity()), "model similarity changed");

        Map<String, String> sourceHashes = verifySourceBoundaries(rootDirectory, root.path("sourceBoundaries"));
        require(sourceHashes.keySet().equals(Set.of("V2", "V3", "SHARED", "EVALUATOR")),
                "source boundary inventory changed");

        JsonNode profiles = root.path("profiles");
        requireExactFields(profiles, "profiles", Set.of("v2", "v3"));
        Map<Prz044PredictionArtifact.Engine, String> expectedProfiles = new EnumMap<>(
                Prz044PredictionArtifact.Engine.class);
        expectedProfiles.put(Prz044PredictionArtifact.Engine.V2, text(profiles, "v2"));
        expectedProfiles.put(Prz044PredictionArtifact.Engine.V3, text(profiles, "v3"));

        JsonNode execution = root.path("execution");
        requireExactFields(execution, "execution", Set.of("runDirectory", "officialRunsAllowed"));
        require(execution.path("officialRunsAllowed").asInt(-1) == 1,
                "officialRunsAllowed must be exactly one");
        require(OFFICIAL_RUN_DIRECTORY.equals(text(execution, "runDirectory")),
                "official run directory changed");
        resolvePortable(rootDirectory, text(execution, "runDirectory"));

        JsonNode goldPolicy = root.path("goldPolicy");
        requireExactFields(goldPolicy, "goldPolicy", Set.of(
                "physicalGoldAllowed", "goldLoaderAllowed", "metricAllowed"));
        require(!goldPolicy.path("physicalGoldAllowed").asBoolean(true),
                "physical Gold must be forbidden");
        require(!goldPolicy.path("goldLoaderAllowed").asBoolean(true),
                "Gold loader must be forbidden");
        require(!goldPolicy.path("metricAllowed").asBoolean(true),
                "metric execution must be forbidden");

        return new VerifiedContract(
                rootDirectory,
                contractPath,
                sha256(contractPath),
                text(root, "baseCommit"),
                expectedInput,
                expectedModel,
                Map.copyOf(sourceHashes),
                Map.copyOf(expectedProfiles),
                OFFICIAL_RUN_DIRECTORY,
                1);
    }

    PreflightRun beginPreflight(
            VerifiedContract contract,
            Prz044PredictionRuntime.ModelPrecheck precheck) {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(precheck, "precheck");
        verifyContractFile(contract);
        require(contract.expectedModel().equals(precheck.model()),
                "preflight model differs from the frozen contract");
        Path probeRoot = resolvePortable(contract.projectRoot(), PREFLIGHT_PROBE_DIRECTORY);
        createSafeDirectories(contract.projectRoot(), probeRoot);
        try {
            Path probeDirectory = Files.createTempDirectory(probeRoot, "run-")
                    .toAbsolutePath().normalize();
            require(!Files.isSymbolicLink(probeDirectory), "preflight probe directory is symbolic");
            return new PreflightRun(contract, precheck, probeDirectory);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot create PRZ-044 preflight prediction probe", exception);
        }
    }

    FrozenPreflightV2 freezePreflightV2(
            PreflightRun run,
            Prz044PredictionArtifact.PredictionSet predictions) {
        Objects.requireNonNull(run, "run");
        verifyContractFile(run.contract());
        validatePreflightPrediction(run, predictions, Prz044PredictionArtifact.Engine.V2);
        PreflightReloadedPrediction written = writeAndReloadPreflightPrediction(run, predictions);
        return new FrozenPreflightV2(run, written);
    }

    PreflightReceipt completePreflight(
            FrozenPreflightV2 frozenV2,
            Prz044PredictionArtifact.PredictionSet v3Predictions,
            PreflightEvidence evidence) {
        Objects.requireNonNull(frozenV2, "frozenV2");
        Objects.requireNonNull(evidence, "evidence");
        PreflightRun run = frozenV2.run();
        verifyContractFile(run.contract());
        PreflightReloadedPrediction v2 = reloadPreflightPrediction(
                run, Prz044PredictionArtifact.Engine.V2);
        requirePreflightReloadedParity(frozenV2.prediction(), v2);
        validatePreflightPrediction(run, v3Predictions, Prz044PredictionArtifact.Engine.V3);
        requireSameQueryInventory(v2.predictions(), v3Predictions);
        PreflightReloadedPrediction v3 = writeAndReloadPreflightPrediction(run, v3Predictions);
        requireSameQueryInventory(v2.predictions(), v3.predictions());
        require(!v2.canonicalSha256().equals(v3.canonicalSha256()),
                "preflight V2/V3 canonical predictions unexpectedly match");
        validatePreflightEvidence(evidence);

        Path receiptPath = resolvePortable(run.contract().projectRoot(), PREFLIGHT_RECEIPT_RELATIVE);
        Path hashPath = resolvePortable(run.contract().projectRoot(), PREFLIGHT_HASH_RECEIPT_RELATIVE);
        if (Files.exists(receiptPath, LinkOption.NOFOLLOW_LINKS)
                || Files.exists(hashPath, LinkOption.NOFOLLOW_LINKS)) {
            require(Files.isRegularFile(receiptPath, LinkOption.NOFOLLOW_LINKS)
                            && Files.isRegularFile(hashPath, LinkOption.NOFOLLOW_LINKS),
                    "preflight receipt/hash pair is incomplete");
            return verifyPreflightPass(run.contract());
        }

        ObjectNode receipt = mapper.createObjectNode();
        receipt.put("artifactType", "PRZ044_PREFLIGHT_PASS_RECEIPT");
        receipt.put("protocolVersion", PROTOCOL_VERSION);
        receipt.put("status", "PASS");
        receipt.put("contractSha256", run.contract().contractSha256());
        receipt.put("baseCommit", run.contract().baseCommit());
        receipt.set("model", mapper.valueToTree(run.contract().expectedModel()));
        receipt.set("sourceBoundaryHashes", mapper.valueToTree(run.contract().sourceBoundaryHashes()));
        receipt.put("evaluatorSourceSha256", run.contract().sourceBoundaryHashes().get("EVALUATOR"));
        ObjectNode profiles = receipt.putObject("profiles");
        profiles.put("v2", run.contract().expectedProfiles().get(Prz044PredictionArtifact.Engine.V2));
        profiles.put("v3", run.contract().expectedProfiles().get(Prz044PredictionArtifact.Engine.V3));
        receipt.put("postgresqlVersion", evidence.postgresqlVersion());
        receipt.put("pgvectorVersion", evidence.pgvectorVersion());
        receipt.put("fixtureKind", "SYNTHETIC_TXT_PDF");
        receipt.put("v2CanonicalSha256", v2.canonicalSha256());
        receipt.put("v2FileSha256", v2.fileSha256());
        receipt.put("v3CanonicalSha256", v3.canonicalSha256());
        receipt.put("v3FileSha256", v3.fileSha256());
        ObjectNode coverage = receipt.putObject("coverage");
        coverage.put("actualPostgreSqlPgvector", true);
        coverage.put("actualBgeM3", true);
        coverage.put("txtExtraction", evidence.txtExtractionVerified());
        coverage.put("pdfExtraction", evidence.pdfExtractionVerified());
        coverage.put("pdfPageProvenance", evidence.pdfPageProvenanceVerified());
        coverage.put("v2Runtime", true);
        coverage.put("v3Runtime", true);
        coverage.put("predictionWriter", true);
        coverage.put("canonicalHash", true);
        coverage.put("diskReload", true);
        coverage.put("createNewOneShot", true);
        receipt.put("officialDatasetAccessed", false);
        receipt.put("goldPresent", false);
        receipt.put("goldAccessed", false);

        createSafeDirectories(run.contract().projectRoot(), receiptPath.getParent());
        writeCreateNewOrVerifyExact(receiptPath, receipt);
        String receiptSha = sha256(receiptPath);
        ObjectNode hashReceipt = mapper.createObjectNode();
        hashReceipt.put("artifactType", "PRZ044_PREFLIGHT_PASS_RECEIPT_HASH");
        hashReceipt.put("protocolVersion", PROTOCOL_VERSION);
        hashReceipt.put("receiptPath", PREFLIGHT_RECEIPT_RELATIVE);
        hashReceipt.put("receiptSha256", receiptSha);
        writeCreateNewOrVerifyExact(hashPath, hashReceipt);
        return verifyPreflightPass(run.contract());
    }

    PreflightReceipt verifyPreflightPass(VerifiedContract contract) {
        Objects.requireNonNull(contract, "contract");
        verifyContractFile(contract);
        Path receiptPath = regularProjectFile(contract.projectRoot(),
                resolvePortable(contract.projectRoot(), PREFLIGHT_RECEIPT_RELATIVE),
                "PRZ-044 preflight PASS receipt");
        Path hashPath = regularProjectFile(contract.projectRoot(),
                resolvePortable(contract.projectRoot(), PREFLIGHT_HASH_RECEIPT_RELATIVE),
                "PRZ-044 preflight PASS receipt hash");
        JsonNode hashReceipt = read(hashPath);
        requireExactFields(hashReceipt, "preflight receipt hash", Set.of(
                "artifactType", "protocolVersion", "receiptPath", "receiptSha256"));
        require("PRZ044_PREFLIGHT_PASS_RECEIPT_HASH".equals(text(hashReceipt, "artifactType")),
                "preflight receipt hash type changed");
        require(PROTOCOL_VERSION.equals(text(hashReceipt, "protocolVersion")),
                "preflight receipt hash protocol changed");
        require(PREFLIGHT_RECEIPT_RELATIVE.equals(text(hashReceipt, "receiptPath")),
                "preflight receipt path changed");
        String receiptSha = text(hashReceipt, "receiptSha256");
        requireLowerSha256(receiptSha, "preflight receipt SHA");
        require(receiptSha.equals(sha256(receiptPath)), "preflight PASS receipt hash changed");

        JsonNode receipt = read(receiptPath);
        requireExactFields(receipt, "preflight receipt", Set.of(
                "artifactType", "protocolVersion", "status", "contractSha256", "baseCommit",
                "model", "sourceBoundaryHashes", "evaluatorSourceSha256", "profiles",
                "postgresqlVersion", "pgvectorVersion", "fixtureKind", "v2CanonicalSha256",
                "v2FileSha256", "v3CanonicalSha256", "v3FileSha256", "coverage",
                "officialDatasetAccessed", "goldPresent", "goldAccessed"));
        require("PRZ044_PREFLIGHT_PASS_RECEIPT".equals(text(receipt, "artifactType")),
                "preflight receipt type changed");
        require(PROTOCOL_VERSION.equals(text(receipt, "protocolVersion"))
                        && "PASS".equals(text(receipt, "status")),
                "preflight receipt status changed");
        require(contract.contractSha256().equals(text(receipt, "contractSha256"))
                        && contract.baseCommit().equals(text(receipt, "baseCommit")),
                "preflight receipt contract identity changed");
        require(mapper.valueToTree(contract.expectedModel()).equals(receipt.path("model")),
                "preflight receipt model identity changed");
        require(mapper.valueToTree(contract.sourceBoundaryHashes()).equals(
                        receipt.path("sourceBoundaryHashes")),
                "preflight receipt source identity changed");
        require(contract.sourceBoundaryHashes().get("EVALUATOR").equals(
                        text(receipt, "evaluatorSourceSha256")),
                "preflight evaluator identity changed");
        JsonNode profiles = receipt.path("profiles");
        requireExactFields(profiles, "preflight profiles", Set.of("v2", "v3"));
        require(contract.expectedProfiles().get(Prz044PredictionArtifact.Engine.V2)
                        .equals(text(profiles, "v2"))
                        && contract.expectedProfiles().get(Prz044PredictionArtifact.Engine.V3)
                        .equals(text(profiles, "v3")),
                "preflight profiles changed");
        require(text(receipt, "postgresqlVersion").contains("PostgreSQL 16")
                        && "0.8.2".equals(text(receipt, "pgvectorVersion")),
                "preflight PostgreSQL/pgvector identity changed");
        require("SYNTHETIC_TXT_PDF".equals(text(receipt, "fixtureKind")),
                "preflight fixture identity changed");
        for (String hashField : List.of(
                "v2CanonicalSha256", "v2FileSha256", "v3CanonicalSha256", "v3FileSha256")) {
            requireLowerSha256(text(receipt, hashField), hashField);
        }
        require(!text(receipt, "v2CanonicalSha256").equals(text(receipt, "v3CanonicalSha256")),
                "preflight engine prediction hashes unexpectedly match");
        JsonNode coverage = receipt.path("coverage");
        Set<String> coverageFields = Set.of(
                "actualPostgreSqlPgvector", "actualBgeM3", "txtExtraction", "pdfExtraction",
                "pdfPageProvenance", "v2Runtime", "v3Runtime", "predictionWriter",
                "canonicalHash", "diskReload", "createNewOneShot");
        requireExactFields(coverage, "preflight coverage", coverageFields);
        for (String field : coverageFields) {
            require(coverage.path(field).asBoolean(false), "preflight coverage failed: " + field);
        }
        require(!receipt.path("officialDatasetAccessed").asBoolean(true)
                        && !receipt.path("goldPresent").asBoolean(true)
                        && !receipt.path("goldAccessed").asBoolean(true),
                "preflight receipt claims official dataset or Gold access");
        return new PreflightReceipt(contract, receiptPath, receiptSha, hashPath, sha256(hashPath));
    }

    OfficialAttempt claimOfficialAttempt(
            VerifiedContract contract,
            Prz044PredictionDataset.VerifiedInputPackage input,
            Prz044PredictionArtifact.ModelIdentity actualModel) {
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(actualModel, "actualModel");
        verifyContractFile(contract);
        require(contract.officialRunsAllowed() == 1, "officialRunsAllowed changed after verification");
        verifyPreflightPass(contract);
        require(contract.expectedModel().equals(actualModel), "actual model differs from the frozen contract");
        requireInputIdentity(contract.expectedInput(), input);

        Path runDirectory = resolvePortable(contract.projectRoot(), contract.runDirectory());
        Path officialRoot = runDirectory.getParent();
        require(officialRoot != null, "official root is missing");
        createSafeDirectories(contract.projectRoot(), officialRoot);
        requireEmptyOfficialRoot(officialRoot);

        try {
            Files.createDirectory(runDirectory);
        }
        catch (IOException exception) {
            throw new IllegalStateException("PRZ-044 official attempt already exists or cannot be claimed", exception);
        }
        Path attemptPath = runDirectory.resolve("attempt.json");
        ObjectNode marker = mapper.createObjectNode();
        marker.put("artifactType", "PRZ044_OFFICIAL_PREDICTION_ATTEMPT");
        marker.put("protocolVersion", PROTOCOL_VERSION);
        marker.put("attempt", 1);
        marker.put("contractSha256", contract.contractSha256());
        marker.put("inputZipSha256", input.zipSha256());
        marker.put("physicalPayloadCombinedSha256", input.physicalCombinedSha256());
        marker.put("manifestCombinedCommitmentSha256", input.commitmentCombinedSha256());
        marker.put("modelId", actualModel.modelId());
        marker.put("modelDigest", actualModel.resolvedDigest());
        marker.put("goldPresent", false);
        marker.put("goldAccessed", false);
        marker.put("startedAt", Instant.now().toString());
        writeCreateNew(attemptPath, marker);
        return new OfficialAttempt(
                contract,
                input,
                actualModel,
                runDirectory,
                attemptPath,
                sha256(attemptPath));
    }

    FrozenV2 freezeV2(
            OfficialAttempt attempt,
            Prz044PredictionArtifact.PredictionSet predictions) {
        Objects.requireNonNull(attempt, "attempt");
        verifyAttempt(attempt);
        require(!terminalArtifactExists(attempt), "official attempt is already terminal");
        require(!predictionArtifactExists(attempt, Prz044PredictionArtifact.Engine.V2),
                "V2 prediction was already frozen");
        require(!predictionArtifactExists(attempt, Prz044PredictionArtifact.Engine.V3),
                "V3 artifact exists before V2 freeze");
        validatePredictions(attempt, predictions, Prz044PredictionArtifact.Engine.V2);
        FrozenPrediction written = writePrediction(attempt, predictions);
        ReloadedPrediction reloaded = reload(attempt, Prz044PredictionArtifact.Engine.V2);
        requireFrozenParity(written, reloaded);
        return new FrozenV2(attempt, reloaded);
    }

    FrozenV3 freezeV3(
            FrozenV2 frozenV2,
            Prz044PredictionArtifact.PredictionSet predictions) {
        Objects.requireNonNull(frozenV2, "frozenV2");
        OfficialAttempt attempt = frozenV2.attempt();
        verifyAttempt(attempt);
        require(!terminalArtifactExists(attempt), "official attempt is already terminal");
        ReloadedPrediction v2 = reload(attempt, Prz044PredictionArtifact.Engine.V2);
        requireReloadedParity(frozenV2.prediction(), v2);
        require(!predictionArtifactExists(attempt, Prz044PredictionArtifact.Engine.V3),
                "V3 prediction was already frozen");
        validatePredictions(attempt, predictions, Prz044PredictionArtifact.Engine.V3);
        requireSameQueryInventory(v2.predictions(), predictions);
        FrozenPrediction written = writePrediction(attempt, predictions);
        ReloadedPrediction reloaded = reload(attempt, Prz044PredictionArtifact.Engine.V3);
        requireFrozenParity(written, reloaded);
        requireSameQueryInventory(v2.predictions(), reloaded.predictions());
        return new FrozenV3(frozenV2, reloaded);
    }

    ReloadedPrediction reload(OfficialAttempt attempt, Prz044PredictionArtifact.Engine engine) {
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(engine, "engine");
        verifyAttempt(attempt);
        Path outputPath = predictionPath(attempt, engine);
        Path receiptPath = predictionReceiptPath(attempt, engine);
        byte[] outputBytes = readBytes(normalizedFile(outputPath, engine + " frozen predictions"));
        JsonNode receipt = read(normalizedFile(receiptPath, engine + " prediction receipt"));
        require("PRZ044_PREDICTION_FROZEN_RECEIPT".equals(text(receipt, "artifactType")),
                "prediction receipt type changed");
        require(PROTOCOL_VERSION.equals(text(receipt, "protocolVersion")),
                "prediction receipt protocol changed");
        require(engine.name().equals(text(receipt, "engine")), "prediction receipt engine changed");
        require(attempt.contract().contractSha256().equals(text(receipt, "contractSha256")),
                "prediction receipt contract changed");
        require(attempt.attemptSha256().equals(text(receipt, "attemptSha256")),
                "prediction receipt attempt changed");
        require(sha256(outputBytes).equals(text(receipt, "fileSha256")),
                "frozen prediction file changed");

        JsonNode wrapper = read(outputPath);
        require("PRZ044_FROZEN_PREDICTION_WRAPPER".equals(text(wrapper, "artifactType")),
                "prediction wrapper type changed");
        require(engine.name().equals(text(wrapper, "engine")), "prediction wrapper engine changed");
        String canonicalSha = text(wrapper, "canonicalSha256");
        require(canonicalSha.equals(text(receipt, "canonicalSha256")),
                "prediction canonical receipt changed");
        byte[] canonical = canonicalBytes(wrapper.path("prediction"));
        require(canonical.length == wrapper.path("canonicalBytes").asLong(-1),
                "prediction canonical length changed");
        require(canonicalSha.equals(sha256(canonical)), "prediction canonical content changed");
        try {
            Prz044PredictionArtifact.PredictionSet predictions = mapper.treeToValue(
                    wrapper.path("prediction"), Prz044PredictionArtifact.PredictionSet.class);
            validatePredictions(attempt, predictions, engine);
            require(canonicalSha.equals(sha256(canonicalBytes(mapper.valueToTree(predictions)))),
                    "prediction DTO round-trip changed");
            return new ReloadedPrediction(
                    attempt,
                    engine,
                    predictions,
                    outputPath,
                    canonicalSha,
                    sha256(outputBytes),
                    outputBytes.length,
                    receiptPath,
                    sha256(receiptPath));
        }
        catch (RuntimeException exception) {
            throw new IllegalStateException("cannot reload frozen " + engine + " predictions", exception);
        }
    }

    PredictionCompletion complete(FrozenV3 frozenV3) {
        Objects.requireNonNull(frozenV3, "frozenV3");
        OfficialAttempt attempt = frozenV3.attempt();
        verifyAttempt(attempt);
        require(!terminalArtifactExists(attempt), "official attempt is already terminal");
        ReloadedPrediction v2 = reload(attempt, Prz044PredictionArtifact.Engine.V2);
        ReloadedPrediction v3 = reload(attempt, Prz044PredictionArtifact.Engine.V3);
        requireReloadedParity(frozenV3.v2().prediction(), v2);
        requireReloadedParity(frozenV3.prediction(), v3);
        requireSameQueryInventory(v2.predictions(), v3.predictions());

        Path receiptPath = attempt.runDirectory().resolve("prediction-completion-receipt.json");
        ObjectNode receipt = mapper.createObjectNode();
        receipt.put("artifactType", "PRZ044_PREDICTION_COMPLETION_RECEIPT");
        receipt.put("protocolVersion", PROTOCOL_VERSION);
        receipt.put("status", "PREDICTIONS_FROZEN");
        receipt.put("attempt", 1);
        receipt.put("contractSha256", attempt.contract().contractSha256());
        receipt.put("attemptSha256", attempt.attemptSha256());
        receipt.put("inputZipSha256", attempt.input().zipSha256());
        receipt.put("manifestCombinedCommitmentSha256", attempt.input().commitmentCombinedSha256());
        receipt.put("v2Rows", v2.predictions().queries().size());
        receipt.put("v3Rows", v3.predictions().queries().size());
        receipt.put("v2CanonicalSha256", v2.canonicalSha256());
        receipt.put("v2FileSha256", v2.fileSha256());
        receipt.put("v2ReceiptSha256", v2.receiptSha256());
        receipt.put("v3CanonicalSha256", v3.canonicalSha256());
        receipt.put("v3FileSha256", v3.fileSha256());
        receipt.put("v3ReceiptSha256", v3.receiptSha256());
        receipt.put("goldPresent", false);
        receipt.put("goldAccessed", false);
        receipt.put("completedAt", Instant.now().toString());
        writeCreateNew(receiptPath, receipt);
        return new PredictionCompletion(attempt, v2, v3, receiptPath, sha256(receiptPath));
    }

    FailureReceipt recordFailure(OfficialAttempt attempt, String stage, Throwable failure) {
        Objects.requireNonNull(attempt, "attempt");
        requireText(stage, "stage");
        Objects.requireNonNull(failure, "failure");
        verifyAttempt(attempt);
        require(!Files.exists(attempt.runDirectory().resolve("prediction-completion-receipt.json"),
                LinkOption.NOFOLLOW_LINKS), "completed attempt cannot be failed");
        Path receiptPath = attempt.runDirectory().resolve("failure-receipt.json");
        ObjectNode receipt = mapper.createObjectNode();
        receipt.put("artifactType", "PRZ044_PREDICTION_FAILURE_RECEIPT");
        receipt.put("protocolVersion", PROTOCOL_VERSION);
        receipt.put("status", "FAILED_ATTEMPT_CONSUMED");
        receipt.put("attempt", 1);
        receipt.put("stage", stage);
        receipt.put("contractSha256", attempt.contract().contractSha256());
        receipt.put("attemptSha256", attempt.attemptSha256());
        receipt.put("v2Frozen", predictionArtifactExists(attempt, Prz044PredictionArtifact.Engine.V2));
        receipt.put("v3Frozen", predictionArtifactExists(attempt, Prz044PredictionArtifact.Engine.V3));
        receipt.put("goldPresent", false);
        receipt.put("goldAccessed", false);
        receipt.put("failureType", failure.getClass().getName());
        receipt.put("failureMessageSha256", sha256(Objects.toString(failure.getMessage(), "")));
        receipt.put("recordedAt", Instant.now().toString());
        writeCreateNew(receiptPath, receipt);
        return new FailureReceipt(attempt, receiptPath, sha256(receiptPath));
    }

    private PreflightReloadedPrediction writeAndReloadPreflightPrediction(
            PreflightRun run,
            Prz044PredictionArtifact.PredictionSet predictions) {
        Path path = run.probeDirectory().resolve(
                predictions.engine().name().toLowerCase() + "-predictions.json");
        require(!Files.exists(path, LinkOption.NOFOLLOW_LINKS),
                "preflight prediction was already written: " + predictions.engine());
        JsonNode predictionTree = mapper.valueToTree(predictions);
        byte[] canonical = canonicalBytes(predictionTree);
        ObjectNode wrapper = mapper.createObjectNode();
        wrapper.put("artifactType", "PRZ044_PREFLIGHT_PREDICTION_PROBE");
        wrapper.put("protocolVersion", PROTOCOL_VERSION);
        wrapper.put("engine", predictions.engine().name());
        wrapper.put("canonicalSha256", sha256(canonical));
        wrapper.put("canonicalBytes", canonical.length);
        wrapper.set("prediction", predictionTree);
        writeCreateNew(path, wrapper);
        requireSecondCreateNewRejected(path, wrapper);
        return reloadPreflightPrediction(run, predictions.engine());
    }

    private PreflightReloadedPrediction reloadPreflightPrediction(
            PreflightRun run,
            Prz044PredictionArtifact.Engine engine) {
        Path path = normalizedFile(run.probeDirectory().resolve(
                engine.name().toLowerCase() + "-predictions.json"),
                "preflight " + engine + " prediction probe");
        byte[] fileBytes = readBytes(path);
        JsonNode wrapper = read(path);
        requireExactFields(wrapper, "preflight prediction probe", Set.of(
                "artifactType", "protocolVersion", "engine", "canonicalSha256",
                "canonicalBytes", "prediction"));
        require("PRZ044_PREFLIGHT_PREDICTION_PROBE".equals(text(wrapper, "artifactType"))
                        && PROTOCOL_VERSION.equals(text(wrapper, "protocolVersion"))
                        && engine.name().equals(text(wrapper, "engine")),
                "preflight prediction probe identity changed");
        byte[] canonical = canonicalBytes(wrapper.path("prediction"));
        String canonicalSha = text(wrapper, "canonicalSha256");
        requireLowerSha256(canonicalSha, "preflight canonical SHA");
        require(canonical.length == wrapper.path("canonicalBytes").asLong(-1)
                        && canonicalSha.equals(sha256(canonical)),
                "preflight prediction canonical hash/reload changed");
        try {
            Prz044PredictionArtifact.PredictionSet predictions = mapper.treeToValue(
                    wrapper.path("prediction"), Prz044PredictionArtifact.PredictionSet.class);
            validatePreflightPrediction(run, predictions, engine);
            require(canonicalSha.equals(sha256(canonicalBytes(mapper.valueToTree(predictions)))),
                    "preflight prediction DTO round-trip changed");
            return new PreflightReloadedPrediction(
                    run, engine, predictions, path, canonicalSha, sha256(fileBytes), fileBytes.length);
        }
        catch (RuntimeException exception) {
            throw new IllegalStateException("cannot reload preflight " + engine + " prediction", exception);
        }
    }

    private static void validatePreflightPrediction(
            PreflightRun run,
            Prz044PredictionArtifact.PredictionSet predictions,
            Prz044PredictionArtifact.Engine engine) {
        Objects.requireNonNull(predictions, "predictions");
        require(predictions.engine() == engine, "preflight prediction engine changed");
        require(run.contract().expectedProfiles().get(engine).equals(predictions.profile()),
                "preflight prediction profile changed");
        require(run.contract().contractSha256().equals(predictions.contractSha256()),
                "preflight prediction contract changed");
        require(run.contract().sourceBoundaryHashes().equals(predictions.sourceBoundaryHashes()),
                "preflight prediction source boundary changed");
        require(run.contract().expectedModel().equals(predictions.model()),
                "preflight prediction model changed");
        require(predictions.queries().size() == 2
                        && predictions.indexingStats().documentCount() == 2
                        && predictions.runtimeAudit().ownerCount() == 2
                        && predictions.runtimeAudit().documentCount() == 2
                        && predictions.runtimeAudit().queryExecutions() == 2,
                "preflight synthetic TXT/PDF runtime scale changed");
        Prz044PredictionArtifact.RuntimeAudit audit = predictions.runtimeAudit();
        require(audit.ownerLeakageCount() == 0
                        && audit.inactiveVersionLeakageCount() == 0
                        && audit.lifecycleViolationCount() == 0
                        && audit.duplicateArtifactCount() == 0
                        && audit.mixedArtifactCount() == 0
                        && audit.crossParentMergeCount() == 0,
                "preflight runtime audit is not clean");
        require(audit.realBgeM3()
                        && audit.modelId().equals(run.contract().expectedModel().modelId())
                        && audit.modelDigest().equals(run.contract().expectedModel().resolvedDigest())
                        && audit.modelDimension() == run.contract().expectedModel().dimension()
                        && audit.additionalModelCount() == 0
                        && audit.additionalServiceCount() == 0
                        && !audit.gpuRequired(),
                "preflight actual BGE-M3 audit changed");
    }

    private static void validatePreflightEvidence(PreflightEvidence evidence) {
        requireText(evidence.postgresqlVersion(), "preflight PostgreSQL version");
        require(evidence.postgresqlVersion().contains("PostgreSQL 16"),
                "preflight did not use actual PostgreSQL 16");
        require("0.8.2".equals(evidence.pgvectorVersion()),
                "preflight did not use pgvector 0.8.2");
        require(evidence.txtExtractionVerified(), "preflight TXT extraction was not verified");
        require(evidence.pdfExtractionVerified(), "preflight PDF extraction was not verified");
        require(evidence.pdfPageProvenanceVerified(),
                "preflight PDF page provenance was not verified");
    }

    private static void requirePreflightReloadedParity(
            PreflightReloadedPrediction expected,
            PreflightReloadedPrediction actual) {
        require(expected.engine() == actual.engine()
                        && expected.canonicalSha256().equals(actual.canonicalSha256())
                        && expected.fileSha256().equals(actual.fileSha256())
                        && expected.fileBytes() == actual.fileBytes(),
                "preflight frozen prediction disk parity changed");
    }

    private void requireSecondCreateNewRejected(Path path, JsonNode value) {
        byte[] bytes = (canonicalJson(value) + "\n").getBytes(StandardCharsets.UTF_8);
        try {
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        catch (FileAlreadyExistsException expected) {
            return;
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot verify CREATE_NEW one-shot semantics", exception);
        }
        throw new IllegalStateException("CREATE_NEW accepted a second preflight prediction write");
    }

    private void writeCreateNewOrVerifyExact(Path path, JsonNode value) {
        byte[] expected = (canonicalJson(value) + "\n").getBytes(StandardCharsets.UTF_8);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            byte[] actual = readBytes(normalizedFile(path, "existing PRZ-044 preflight receipt"));
            require(java.util.Arrays.equals(expected, actual),
                    "existing PRZ-044 preflight receipt differs from the verified PASS identity");
            return;
        }
        try {
            Files.write(path, expected, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot CREATE_NEW PRZ-044 preflight receipt: " + path, exception);
        }
    }

    private FrozenPrediction writePrediction(
            OfficialAttempt attempt,
            Prz044PredictionArtifact.PredictionSet predictions) {
        Prz044PredictionArtifact.Engine engine = predictions.engine();
        JsonNode predictionTree = mapper.valueToTree(predictions);
        byte[] canonical = canonicalBytes(predictionTree);
        String canonicalSha = sha256(canonical);
        Path outputPath = predictionPath(attempt, engine);
        ObjectNode wrapper = mapper.createObjectNode();
        wrapper.put("artifactType", "PRZ044_FROZEN_PREDICTION_WRAPPER");
        wrapper.put("protocolVersion", PROTOCOL_VERSION);
        wrapper.put("engine", engine.name());
        wrapper.put("canonicalSha256", canonicalSha);
        wrapper.put("canonicalBytes", canonical.length);
        wrapper.set("prediction", predictionTree);
        writeCreateNew(outputPath, wrapper);
        byte[] outputBytes = readBytes(outputPath);

        Path receiptPath = predictionReceiptPath(attempt, engine);
        ObjectNode receipt = mapper.createObjectNode();
        receipt.put("artifactType", "PRZ044_PREDICTION_FROZEN_RECEIPT");
        receipt.put("protocolVersion", PROTOCOL_VERSION);
        receipt.put("status", "PREDICTION_FROZEN");
        receipt.put("engine", engine.name());
        receipt.put("attempt", 1);
        receipt.put("contractSha256", attempt.contract().contractSha256());
        receipt.put("attemptSha256", attempt.attemptSha256());
        receipt.put("inputZipSha256", attempt.input().zipSha256());
        receipt.put("queryInventorySha256", predictions.queryInventorySha256());
        receipt.put("rowCount", predictions.queries().size());
        receipt.put("canonicalSha256", canonicalSha);
        receipt.put("canonicalBytes", canonical.length);
        receipt.put("fileSha256", sha256(outputBytes));
        receipt.put("fileBytes", outputBytes.length);
        receipt.put("goldPresent", false);
        receipt.put("goldAccessed", false);
        receipt.put("recordedAt", Instant.now().toString());
        writeCreateNew(receiptPath, receipt);
        return new FrozenPrediction(
                attempt,
                engine,
                outputPath,
                canonicalSha,
                sha256(outputBytes),
                outputBytes.length,
                receiptPath,
                sha256(receiptPath));
    }

    private void validatePredictions(
            OfficialAttempt attempt,
            Prz044PredictionArtifact.PredictionSet predictions,
            Prz044PredictionArtifact.Engine engine) {
        Objects.requireNonNull(predictions, "predictions");
        require(predictions.engine() == engine, "prediction engine changed");
        require(attempt.contract().expectedProfiles().get(engine).equals(predictions.profile()),
                "prediction profile differs from contract");
        require(attempt.contract().contractSha256().equals(predictions.contractSha256()),
                "prediction contract identity changed");
        require(attempt.input().zipSha256().equals(predictions.inputZipSha256()),
                "prediction INPUT ZIP identity changed");
        require(attempt.input().manifestCanonicalSha256().equals(predictions.manifestCanonicalSha256()),
                "prediction manifest identity changed");
        require(attempt.input().physicalCombinedSha256().equals(predictions.physicalPayloadCombinedSha256()),
                "prediction physical payload identity changed");
        require(attempt.input().commitmentCombinedSha256().equals(
                        predictions.manifestCombinedCommitmentSha256()),
                "prediction manifest commitment changed");
        require(attempt.contract().sourceBoundaryHashes().equals(predictions.sourceBoundaryHashes()),
                "prediction source boundary changed");
        require(attempt.model().equals(predictions.model()), "prediction model identity changed");
        require(queryInventorySha256(attempt.input().queries()).equals(predictions.queryInventorySha256()),
                "prediction query inventory SHA changed");
        require(predictions.queries().size() == attempt.contract().expectedInput().queryCount(),
                "prediction row count changed");
        require(predictions.runtimeAudit().queryExecutions() == predictions.queries().size(),
                "query execution count differs from frozen rows");
        require(predictions.runtimeAudit().ownerCount() == attempt.contract().expectedInput().userCount(),
                "runtime owner count changed");
        require(predictions.runtimeAudit().documentCount() == attempt.contract().expectedInput().documentCount(),
                "runtime document count changed");
        require(predictions.indexingStats().documentCount()
                        == attempt.contract().expectedInput().documentCount(),
                "indexing document count changed");
        require(predictions.runtimeAudit().ownerLeakageCount() == 0,
                "runtime owner leakage must be zero");
        require(predictions.runtimeAudit().inactiveVersionLeakageCount() == 0,
                "runtime inactive-version leakage must be zero");
        require(predictions.runtimeAudit().lifecycleViolationCount() == 0,
                "runtime lifecycle violations must be zero");
        require(predictions.runtimeAudit().duplicateArtifactCount() == 0,
                "runtime duplicate artifacts must be zero");
        require(predictions.runtimeAudit().mixedArtifactCount() == 0,
                "runtime mixed artifacts must be zero");
        require(predictions.runtimeAudit().crossParentMergeCount() == 0,
                "runtime cross-parent merges must be zero");
        require(predictions.runtimeAudit().realBgeM3(), "official prediction did not use real BGE-M3");
        require(predictions.runtimeAudit().modelId().equals(attempt.model().modelId())
                        && predictions.runtimeAudit().modelDigest().equals(attempt.model().resolvedDigest())
                        && predictions.runtimeAudit().modelDimension() == attempt.model().dimension(),
                "runtime model audit changed");
        require(predictions.runtimeAudit().additionalModelCount() == 0,
                "prediction introduced an additional model");
        require(predictions.runtimeAudit().additionalServiceCount() == 0,
                "prediction introduced an additional service");
        require(!predictions.runtimeAudit().gpuRequired(), "prediction unexpectedly requires a GPU");
        for (int queryIndex = 0; queryIndex < predictions.queries().size(); queryIndex++) {
            Prz044PredictionArtifact.QueryPrediction query = predictions.queries().get(queryIndex);
            Prz044PredictionDataset.RuntimeQuery inputQuery = attempt.input().queries().get(queryIndex);
            require(query.queryId().equals(inputQuery.queryId())
                            && query.userId().equals(inputQuery.userId())
                            && query.professionId().equals(inputQuery.professionId())
                            && query.professionLabel().equals(inputQuery.professionLabel())
                            && query.language().equals(inputQuery.language())
                            && query.queryTextSha256().equals(inputQuery.querySha256()),
                    "prediction query identity/order changed at row " + queryIndex);
            for (Prz044PredictionArtifact.Result result : query.finalResults()) {
                Prz044PredictionArtifact.SourceSpan identity = result.selectedSpans().get(0);
                require(query.userId().equals(identity.ownerUserId()),
                        "selected source span owner differs from query owner: " + query.queryId());
                for (Prz044PredictionArtifact.SourceSpan span : result.selectedSpans()) {
                    requireSameResultSource(attempt.input(), query, identity, span, "selected");
                }
                for (Prz044PredictionArtifact.SourceSpan span : result.displaySpans()) {
                    requireSameResultSource(attempt.input(), query, identity, span, "display");
                }
            }
        }
    }

    static String queryInventorySha256(List<Prz044PredictionDataset.RuntimeQuery> queries) {
        StringBuilder canonical = new StringBuilder();
        for (Prz044PredictionDataset.RuntimeQuery query : queries) {
            canonical.append(query.queryId()).append('\0')
                    .append(query.userId()).append('\0')
                    .append(query.professionId()).append('\0')
                    .append(query.professionLabel()).append('\0')
                    .append(query.language()).append('\0')
                    .append(query.querySha256()).append('\n');
        }
        return sha256(canonical.toString());
    }

    private static void requireSameResultSource(
            Prz044PredictionDataset.VerifiedInputPackage input,
            Prz044PredictionArtifact.QueryPrediction query,
            Prz044PredictionArtifact.SourceSpan expected,
            Prz044PredictionArtifact.SourceSpan actual,
            String label) {
        require(query.userId().equals(actual.ownerUserId()),
                label + " source span owner differs from query owner: " + query.queryId());
        require(expected.ownerUserId().equals(actual.ownerUserId())
                        && expected.documentId().equals(actual.documentId())
                        && expected.versionId().equals(actual.versionId())
                        && expected.sourceDocumentType().equals(actual.sourceDocumentType())
                        && expected.fileType() == actual.fileType()
                        && expected.relativePath().equals(actual.relativePath()),
                label + " source span identity differs within result: " + query.queryId());
        Prz044PredictionDataset.RuntimeDocument document = input.documents().stream()
                .filter(candidate -> candidate.userId().equals(actual.ownerUserId())
                        && candidate.documentId().equals(actual.documentId())
                        && candidate.versionId().equals(actual.versionId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        label + " source span references an unknown input document: " + query.queryId()));
        require(document.sourceDocumentType().equals(actual.sourceDocumentType())
                        && document.fileType() == actual.fileType()
                        && document.relativePath().equals(actual.relativePath()),
                label + " source span path/type differs from input: " + query.queryId());
        com.prizm.ingestion.service.PageText page = document.pages().stream()
                .filter(candidate -> actual.pageNumber() == null
                        ? document.fileType() == com.prizm.document.entity.DocumentFileType.TXT
                        : candidate.pageNumber() == actual.pageNumber())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        label + " source span references an unknown page: " + query.queryId()));
        int pageCodePoints = page.text().codePointCount(0, page.text().length());
        require(actual.codePointEnd() <= pageCodePoints,
                label + " source span exceeds the production-extracted page: " + query.queryId());
        int charStart = page.text().offsetByCodePoints(0, actual.codePointStart());
        int charEnd = page.text().offsetByCodePoints(0, actual.codePointEnd());
        require(sha256(page.text().substring(charStart, charEnd)).equals(actual.textSha256()),
                label + " source span text SHA differs from production extraction: " + query.queryId());
    }

    private static void requireInputIdentity(
            Prz044PredictionDataset.ExpectedInput expected,
            Prz044PredictionDataset.VerifiedInputPackage actual) {
        require(expected.zipSha256().equals(actual.zipSha256()), "preflight ZIP identity changed");
        require(expected.manifestSha256().equals(actual.manifestSha256()), "preflight manifest changed");
        require(expected.manifestCanonicalSha256().equals(actual.manifestCanonicalSha256()),
                "preflight canonical manifest changed");
        require(expected.physicalCombinedSha256().equals(actual.physicalCombinedSha256()),
                "preflight physical combined SHA changed");
        require(expected.commitmentCombinedSha256().equals(actual.commitmentCombinedSha256()),
                "preflight commitment combined SHA changed");
        require(!actual.goldPresent(), "preflight package unexpectedly contains physical Gold");
        require(actual.sealedCommitment() != null
                        && "sealed/gold.json".equals(actual.sealedCommitment().path())
                        && expected.sealedCommitmentSha256().equals(actual.sealedCommitment().sha256()),
                "sealed commitment metadata changed");
        require(actual.inputFiles().size() == expected.physicalPayloadCount(),
                "preflight physical payload count changed");
        require(actual.users().size() == expected.userCount(), "preflight user count changed");
        require(actual.documents().size() == expected.documentCount(), "preflight document count changed");
        require(actual.queries().size() == expected.queryCount(), "preflight query count changed");
        long txtCount = actual.documents().stream()
                .filter(document -> document.fileType() == com.prizm.document.entity.DocumentFileType.TXT)
                .count();
        long pdfCount = actual.documents().stream()
                .filter(document -> document.fileType() == com.prizm.document.entity.DocumentFileType.PDF)
                .count();
        require(txtCount == expected.txtCount() && pdfCount == expected.pdfCount(),
                "preflight TXT/PDF distribution changed");
        require(actual.users().stream().map(Prz044PredictionDataset.RuntimeUser::professionId)
                        .distinct().count() == expected.professionCount(),
                "preflight profession distribution changed");
    }

    private Map<String, String> verifySourceBoundaries(Path projectRoot, JsonNode boundaries) {
        require(boundaries.isArray(), "sourceBoundaries must be an array");
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode boundary : boundaries) {
            requireExactFields(boundary, "sourceBoundary", Set.of(
                    "name", "files", "directories", "sha256"));
            require(boundary.path("files").isArray(), "sourceBoundary files must be an array");
            require(boundary.path("directories").isArray(),
                    "sourceBoundary directories must be an array");
            String name = text(boundary, "name");
            List<Path> files = new ArrayList<>();
            for (JsonNode file : boundary.path("files")) {
                files.add(regularProjectFile(
                        projectRoot,
                        resolvePortable(projectRoot, file.asText()),
                        "source boundary file"));
            }
            for (JsonNode directory : boundary.path("directories")) {
                Path rootDirectory = normalizedProjectDirectory(
                        projectRoot,
                        resolvePortable(projectRoot, directory.asText()),
                        "source boundary directory");
                try (var paths = Files.walk(rootDirectory)) {
                    paths.filter(Files::isRegularFile)
                            .filter(path -> !Files.isSymbolicLink(path))
                            .forEach(files::add);
                }
                catch (IOException exception) {
                    throw new IllegalStateException("cannot walk source boundary: " + rootDirectory, exception);
                }
            }
            String actual = canonicalFileSetSha256(projectRoot, files);
            require(actual.equals(text(boundary, "sha256")), "source boundary changed: " + name);
            require(result.putIfAbsent(name, actual) == null, "duplicate source boundary: " + name);
        }
        return result;
    }

    static String canonicalFileSetSha256(Path projectRoot, List<Path> candidates) {
        Path root = projectRoot.toAbsolutePath().normalize();
        List<Path> files = candidates.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .sorted(Comparator.comparing(path -> portableProjectPath(root, path)))
                .toList();
        Set<String> normalizedPortablePaths = new java.util.HashSet<>();
        StringBuilder canonical = new StringBuilder();
        for (Path file : files) {
            if (!file.startsWith(root) || !Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                throw new IllegalStateException("frozen source is missing, symbolic, or outside project: " + file);
            }
            String portable = portableProjectPath(root, file);
            require(!portable.contains("|") && !portable.contains("\r") && !portable.contains("\n"),
                    "frozen source path is not canonical-safe: " + portable);
            String normalized = java.text.Normalizer.normalize(portable, java.text.Normalizer.Form.NFC)
                    .toLowerCase(java.util.Locale.ROOT);
            require(normalizedPortablePaths.add(normalized),
                    "frozen source path collides after NFC/casefold: " + portable);
            canonical.append(portable)
                    .append('|').append(file.toFile().length())
                    .append('|').append(sha256(file)).append('\n');
        }
        return sha256(canonical.toString());
    }

    private static void requireSameQueryInventory(
            Prz044PredictionArtifact.PredictionSet left,
            Prz044PredictionArtifact.PredictionSet right) {
        require(left.queryInventorySha256().equals(right.queryInventorySha256()),
                "V2/V3 query inventory SHA differs");
        require(left.queries().size() == right.queries().size(), "V2/V3 row count differs");
        for (int index = 0; index < left.queries().size(); index++) {
            var l = left.queries().get(index);
            var r = right.queries().get(index);
            require(l.queryId().equals(r.queryId())
                            && l.userId().equals(r.userId())
                            && l.professionId().equals(r.professionId())
                            && l.language().equals(r.language())
                            && l.queryTextSha256().equals(r.queryTextSha256()),
                    "V2/V3 query identity/order differs at row " + index);
        }
    }

    private static void requireFrozenParity(FrozenPrediction frozen, ReloadedPrediction reloaded) {
        require(frozen.engine() == reloaded.engine(), "frozen engine changed after reload");
        require(frozen.canonicalSha256().equals(reloaded.canonicalSha256()),
                "canonical SHA changed after reload");
        require(frozen.fileSha256().equals(reloaded.fileSha256()), "file SHA changed after reload");
        require(frozen.fileBytes() == reloaded.fileBytes(), "file size changed after reload");
        require(frozen.receiptSha256().equals(reloaded.receiptSha256()),
                "receipt SHA changed after reload");
    }

    private static void requireReloadedParity(ReloadedPrediction frozen, ReloadedPrediction reloaded) {
        require(frozen.engine() == reloaded.engine(), "reloaded engine changed");
        require(frozen.canonicalSha256().equals(reloaded.canonicalSha256()),
                "reloaded canonical SHA changed");
        require(frozen.fileSha256().equals(reloaded.fileSha256()), "reloaded file SHA changed");
        require(frozen.receiptSha256().equals(reloaded.receiptSha256()),
                "reloaded receipt SHA changed");
    }

    private void verifyContractFile(VerifiedContract contract) {
        require(contract.contractPath().equals(resolvePortable(contract.projectRoot(), CONTRACT_RELATIVE)),
                "verified contract path changed");
        require(contract.contractSha256().equals(sha256(normalizedFile(
                        contract.contractPath(), "PRZ-044 execution contract"))),
                "execution contract changed after verification");
        require(OFFICIAL_RUN_DIRECTORY.equals(contract.runDirectory()), "official run directory changed");
    }

    private void verifyAttempt(OfficialAttempt attempt) {
        verifyContractFile(attempt.contract());
        Path expectedRun = resolvePortable(attempt.contract().projectRoot(), OFFICIAL_RUN_DIRECTORY);
        require(expectedRun.equals(attempt.runDirectory()), "official run directory identity changed");
        require(attempt.attemptPath().equals(expectedRun.resolve("attempt.json")),
                "attempt marker path changed");
        Path marker = normalizedFile(attempt.attemptPath(), "PRZ-044 attempt marker");
        require(attempt.attemptSha256().equals(sha256(marker)), "attempt marker changed");
        JsonNode value = read(marker);
        require("PRZ044_OFFICIAL_PREDICTION_ATTEMPT".equals(text(value, "artifactType")),
                "attempt marker type changed");
        require(PROTOCOL_VERSION.equals(text(value, "protocolVersion")), "attempt protocol changed");
        require(value.path("attempt").asInt(-1) == 1, "attempt number changed");
        require(attempt.contract().contractSha256().equals(text(value, "contractSha256")),
                "attempt contract changed");
        require(attempt.input().zipSha256().equals(text(value, "inputZipSha256")),
                "attempt INPUT ZIP changed");
        require(!value.path("goldPresent").asBoolean(true) && !value.path("goldAccessed").asBoolean(true),
                "attempt marker claims Gold presence/access");
    }

    private static boolean terminalArtifactExists(OfficialAttempt attempt) {
        return Files.exists(attempt.runDirectory().resolve("failure-receipt.json"), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(
                        attempt.runDirectory().resolve("prediction-completion-receipt.json"),
                        LinkOption.NOFOLLOW_LINKS);
    }

    private static boolean predictionArtifactExists(
            OfficialAttempt attempt,
            Prz044PredictionArtifact.Engine engine) {
        return Files.exists(predictionPath(attempt, engine), LinkOption.NOFOLLOW_LINKS)
                || Files.exists(predictionReceiptPath(attempt, engine), LinkOption.NOFOLLOW_LINKS);
    }

    private static Path predictionPath(OfficialAttempt attempt, Prz044PredictionArtifact.Engine engine) {
        return attempt.runDirectory().resolve(engine.name().toLowerCase() + "-predictions.json");
    }

    private static Path predictionReceiptPath(
            OfficialAttempt attempt,
            Prz044PredictionArtifact.Engine engine) {
        return attempt.runDirectory().resolve(
                engine.name().toLowerCase() + "-predictions-frozen-receipt.json");
    }

    private static void requireEmptyOfficialRoot(Path officialRoot) {
        try (var entries = Files.list(officialRoot)) {
            require(entries.findAny().isEmpty(),
                    "PRZ-044 official root already contains an attempt or marker");
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot inspect PRZ-044 official root", exception);
        }
    }

    private static void createSafeDirectories(Path projectRoot, Path target) {
        Path root = projectRoot.toAbsolutePath().normalize();
        Path normalizedTarget = target.toAbsolutePath().normalize();
        require(normalizedTarget.startsWith(root) && !normalizedTarget.equals(root),
                "official directory escaped project root");
        Path current = root;
        for (Path segment : root.relativize(normalizedTarget)) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                require(!Files.isSymbolicLink(current) && Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS),
                        "official directory ancestor is unsafe: " + current);
            }
            else {
                try {
                    Files.createDirectory(current);
                }
                catch (IOException exception) {
                    throw new IllegalStateException("cannot create official directory ancestor: " + current, exception);
                }
            }
        }
    }

    static Path resolvePortable(Path projectRoot, String portable) {
        requireText(portable, "portable path");
        if (portable.contains("\\") || portable.startsWith("/") || portable.contains(":")
                || portable.startsWith("//")) {
            throw new IllegalStateException("path is not a portable project-relative path: " + portable);
        }
        Path result = projectRoot.toAbsolutePath().normalize();
        for (String segment : portable.split("/", -1)) {
            if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalStateException("portable path has an unsafe segment: " + portable);
            }
            result = result.resolve(segment);
        }
        result = result.normalize();
        require(result.startsWith(projectRoot.toAbsolutePath().normalize()), "portable path escaped project root");
        return result;
    }

    private byte[] canonicalBytes(JsonNode value) {
        return canonicalJson(value).getBytes(StandardCharsets.UTF_8);
    }

    private String canonicalJson(JsonNode value) {
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

    private void writeCreateNew(Path path, JsonNode value) {
        try {
            byte[] bytes = (canonicalJson(value) + "\n").getBytes(StandardCharsets.UTF_8);
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot CREATE_NEW PRZ-044 artifact: " + path, exception);
        }
    }

    private JsonNode read(Path path) {
        try {
            return mapper.readTree(Files.readAllBytes(path));
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot read JSON: " + path, exception);
        }
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot read frozen file: " + path, exception);
        }
    }

    static String sha256(Path path) {
        return sha256(readBytes(path));
    }

    static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String portableProjectPath(Path projectRoot, Path file) {
        Path normalized = file.toAbsolutePath().normalize();
        require(normalized.startsWith(projectRoot), "source file escaped project root");
        return projectRoot.relativize(normalized).toString().replace('\\', '/');
    }

    private static Path normalizedDirectory(Path path, String label) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized)) {
            throw new IllegalStateException(label + " is missing or symbolic: " + normalized);
        }
        return normalized;
    }

    private static Path normalizedProjectDirectory(
            Path projectRoot, Path path, String label) {
        Path normalized = normalizedDirectory(path, label);
        require(normalized.startsWith(projectRoot), label + " escaped project root");
        return normalized;
    }

    private static Path normalizedFile(Path path, String label) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized)) {
            throw new IllegalStateException(label + " is missing or symbolic: " + normalized);
        }
        return normalized;
    }

    private static Path regularProjectFile(Path projectRoot, Path path, String label) {
        Path normalized = normalizedFile(path, label);
        require(normalized.startsWith(projectRoot), label + " escaped project root");
        return normalized;
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText();
        requireText(value, field);
        return value;
    }

    private static int requiredInt(JsonNode node, String field, int minimum) {
        int value = node.path(field).asInt(Integer.MIN_VALUE);
        require(value >= minimum, field + " is below the required minimum");
        return value;
    }

    private static void requireExactFields(JsonNode node, String label, Set<String> expected) {
        require(node.isObject(), label + " must be an object");
        Set<String> actual = Set.copyOf(node.propertyNames());
        require(actual.equals(expected), label + " fields changed: " + actual);
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalStateException(label + " is required");
    }

    private static void requireLowerSha256(String value, String label) {
        requireText(value, label);
        require(value.matches("[0-9a-f]{64}"), label + " must be lowercase SHA-256");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    record VerifiedContract(
            Path projectRoot,
            Path contractPath,
            String contractSha256,
            String baseCommit,
            Prz044PredictionDataset.ExpectedInput expectedInput,
            Prz044PredictionArtifact.ModelIdentity expectedModel,
            Map<String, String> sourceBoundaryHashes,
            Map<Prz044PredictionArtifact.Engine, String> expectedProfiles,
            String runDirectory,
            int officialRunsAllowed) {
    }

    record PreflightRun(
            VerifiedContract contract,
            Prz044PredictionRuntime.ModelPrecheck precheck,
            Path probeDirectory) {
    }

    record PreflightReloadedPrediction(
            PreflightRun run,
            Prz044PredictionArtifact.Engine engine,
            Prz044PredictionArtifact.PredictionSet predictions,
            Path outputPath,
            String canonicalSha256,
            String fileSha256,
            long fileBytes) {
    }

    record FrozenPreflightV2(PreflightRun run, PreflightReloadedPrediction prediction) {
    }

    record PreflightEvidence(
            String postgresqlVersion,
            String pgvectorVersion,
            boolean txtExtractionVerified,
            boolean pdfExtractionVerified,
            boolean pdfPageProvenanceVerified) {
    }

    record PreflightReceipt(
            VerifiedContract contract,
            Path receiptPath,
            String receiptSha256,
            Path hashReceiptPath,
            String hashReceiptSha256) {
    }

    record OfficialAttempt(
            VerifiedContract contract,
            Prz044PredictionDataset.VerifiedInputPackage input,
            Prz044PredictionArtifact.ModelIdentity model,
            Path runDirectory,
            Path attemptPath,
            String attemptSha256) {
    }

    private record FrozenPrediction(
            OfficialAttempt attempt,
            Prz044PredictionArtifact.Engine engine,
            Path outputPath,
            String canonicalSha256,
            String fileSha256,
            long fileBytes,
            Path receiptPath,
            String receiptSha256) {
    }

    record ReloadedPrediction(
            OfficialAttempt attempt,
            Prz044PredictionArtifact.Engine engine,
            Prz044PredictionArtifact.PredictionSet predictions,
            Path outputPath,
            String canonicalSha256,
            String fileSha256,
            long fileBytes,
            Path receiptPath,
            String receiptSha256) {
    }

    record FrozenV2(OfficialAttempt attempt, ReloadedPrediction prediction) {
    }

    record FrozenV3(FrozenV2 v2, ReloadedPrediction prediction) {
        OfficialAttempt attempt() {
            return v2.attempt();
        }
    }

    record PredictionCompletion(
            OfficialAttempt attempt,
            ReloadedPrediction v2,
            ReloadedPrediction v3,
            Path receiptPath,
            String receiptSha256) {
    }

    record FailureReceipt(OfficialAttempt attempt, Path receiptPath, String receiptSha256) {
    }
}
