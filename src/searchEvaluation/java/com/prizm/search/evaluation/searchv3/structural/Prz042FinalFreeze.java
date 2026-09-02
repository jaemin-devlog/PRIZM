package com.prizm.search.evaluation.searchv3.structural;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * PRZ-042 input, one-shot attempt, Gold-free predictions, and execution receipt boundary.
 *
 * <p>The immutable SEALED manifest remains byte-identical after execution. The append-only
 * completion receipt is the authoritative record that the sealed input was opened and searched.
 */
final class Prz042FinalFreeze {

    static final String CONTRACT_TYPE = "PRZ042_FINAL_EVALUATION_CONTRACT";
    static final String OUTPUT_TYPE = "PRZ042_FINAL_RUNTIME_PREDICTIONS";
    static final String PROTOCOL_VERSION = "PRZ042_FINAL_PROTOCOL_V1";

    private final ObjectMapper mapper = new ObjectMapper();

    VerifiedInput verifyInput(Path contractPath) {
        Path contract = normalizedFile(contractPath, "PRZ-042 execution contract");
        JsonNode root = read(contract);
        require(CONTRACT_TYPE.equals(text(root, "artifactType")), "contract artifact type changed");
        require(PROTOCOL_VERSION.equals(text(root, "protocolVersion")), "protocol version changed");
        require("INPUT_FROZEN".equals(text(root, "status")), "contract is not INPUT_FROZEN");
        require(root.path("attempt").asInt(-1) == 1, "official attempt must be exactly 1");

        JsonNode dataset = root.path("dataset");
        Path splitRoot = normalizedDirectory(Path.of(text(dataset, "splitRoot")), "SEALED split root");
        Path manifestPath = normalizedFile(splitRoot.resolve("manifest.json"), "SEALED manifest");
        String expectedManifestSha = text(dataset, "manifestSha256");
        require(expectedManifestSha.equals(sha256(manifestPath)), "SEALED manifest SHA changed");
        JsonNode manifest = read(manifestPath);
        require("SEALED".equals(text(manifest, "status")), "SEALED status changed");
        require(!manifest.path("mutable").asBoolean(true), "SEALED became mutable");
        require(!manifest.path("opened").asBoolean(true), "SEALED was already opened");
        require(!manifest.path("searchExecuted").asBoolean(true), "SEALED search was already executed");
        require(text(dataset, "combinedSha256").equals(text(manifest, "combinedSha256")),
                "SEALED combined SHA changed");
        require(dataset.path("userBundles").asInt(-1) == manifest.path("counts").path("userBundles").asInt(-2),
                "SEALED user count changed");
        require(dataset.path("queries").asInt(-1) == manifest.path("counts").path("queries").asInt(-2),
                "SEALED query count changed");
        require(dataset.path("notSupportedQueries").asInt(-1)
                        == manifest.path("distributions").path("answerability")
                                .path("NOT_SUPPORTED").asInt(-2),
                "SEALED NOT_SUPPORTED count changed");
        require(dataset.path("directPositiveQueries").asInt(-1)
                        == manifest.path("distributions").path("answerability")
                                .path("SUPPORTED").asInt(-2),
                "SEALED direct-positive count changed");

        Map<String, String> boundaryHashes = new LinkedHashMap<>();
        for (JsonNode boundary : root.path("sourceBoundaries")) {
            String name = text(boundary, "name");
            List<Path> files = new ArrayList<>();
            for (JsonNode file : boundary.path("files")) {
                files.add(normalizedFile(Path.of(file.asText()), "source boundary file"));
            }
            for (JsonNode directory : boundary.path("directories")) {
                Path rootDirectory = normalizedDirectory(Path.of(directory.asText()), "source boundary directory");
                try (var paths = Files.walk(rootDirectory)) {
                    paths.filter(Files::isRegularFile)
                            .filter(path -> !Files.isSymbolicLink(path))
                            .forEach(files::add);
                }
                catch (IOException exception) {
                    throw new IllegalStateException("Cannot walk source boundary: " + rootDirectory, exception);
                }
            }
            String actual = canonicalFileSetSha256(files);
            require(text(boundary, "sha256").equals(actual), "source boundary changed: " + name);
            require(boundaryHashes.putIfAbsent(name, actual) == null, "duplicate source boundary: " + name);
        }
        require(boundaryHashes.keySet().containsAll(List.of("V2", "V3", "SHARED", "EVALUATOR")),
                "required source boundary is missing");

        JsonNode model = root.path("model");
        require(model.path("dimension").asInt(-1) == 1024, "model dimension contract changed");
        require("COSINE".equals(text(model, "similarity")), "model similarity contract changed");
        require(root.path("gate").path("frozenBeforeExecution").asBoolean(false),
                "numeric Gate is not frozen");
        require(!root.path("gate").path("releaseAdequacyMet").asBoolean(true),
                "small protocol seed cannot claim release adequacy");

        return new VerifiedInput(
                contract,
                sha256(contract),
                splitRoot,
                manifestPath,
                expectedManifestSha,
                text(dataset, "combinedSha256"),
                text(dataset, "datasetVersion"),
                text(dataset, "split"),
                text(dataset, "goldSchemaSha256"),
                text(dataset, "gitTree"),
                text(root, "baseCommit"),
                text(model, "modelId"),
                text(model, "resolvedDigest"),
                model.path("dimension").asInt(),
                Map.copyOf(boundaryHashes),
                dataset.path("userBundles").asInt(),
                dataset.path("queries").asInt(),
                dataset.path("directPositiveQueries").asInt(),
                dataset.path("notSupportedQueries").asInt());
    }

    Attempt claimAttempt(VerifiedInput input, Path runDirectory) {
        Objects.requireNonNull(input, "verified input");
        Path directory = runDirectory.toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
            Path attemptPath = directory.resolve("attempt.json");
            ObjectNode attempt = mapper.createObjectNode();
            attempt.put("artifactType", "PRZ042_OFFICIAL_ATTEMPT");
            attempt.put("protocolVersion", PROTOCOL_VERSION);
            attempt.put("attempt", 1);
            attempt.put("contractSha256", input.contractSha256());
            attempt.put("sealedCombinedSha256", input.sealedCombinedSha256());
            attempt.put("startedAt", Instant.now().toString());
            Files.writeString(
                    attemptPath,
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(attempt) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
            return new Attempt(input, directory, attemptPath, sha256(attemptPath));
        }
        catch (IOException exception) {
            throw new IllegalStateException(
                    "PRZ-042 official attempt already exists or cannot be claimed: " + directory,
                    exception);
        }
    }

    OpenedAttempt recordInputOpened(Attempt attempt) {
        Objects.requireNonNull(attempt, "attempt");
        verifyAttemptFile(attempt);
        Path receiptPath = attempt.runDirectory().resolve("input-opened-receipt.json");
        ObjectNode receipt = mapper.createObjectNode();
        receipt.put("artifactType", "PRZ042_INPUT_OPENED_RECEIPT");
        receipt.put("protocolVersion", PROTOCOL_VERSION);
        receipt.put("attempt", 1);
        receipt.put("status", "INPUT_OPENED");
        receipt.put("opened", true);
        receipt.put("searchExecuted", false);
        receipt.put("recordedAt", Instant.now().toString());
        receipt.put("contractSha256", attempt.input().contractSha256());
        receipt.put("attemptSha256", attempt.attemptSha256());
        receipt.put("sealedCombinedSha256", attempt.input().sealedCombinedSha256());
        writeCreateNew(receiptPath, receipt);
        return new OpenedAttempt(attempt, receiptPath, sha256(receiptPath));
    }

    SearchStarted recordSearchStarted(
            OpenedAttempt opened,
            Prz042FinalDataset.RuntimeInput runtime) {
        Objects.requireNonNull(opened, "opened attempt");
        Objects.requireNonNull(runtime, "runtime input");
        verifyAttemptFile(opened.attempt());
        require(opened.receiptSha256().equals(sha256(opened.receiptPath())),
                "input-opened receipt changed before search");
        require(runtime.contractSha256().equals(opened.attempt().input().contractSha256()),
                "runtime contract differs from the official attempt");
        require(runtime.attemptSha256().equals(opened.attempt().attemptSha256()),
                "runtime attempt differs from the official attempt");
        require(runtime.manifestSha256().equals(opened.attempt().input().manifestSha256()),
                "runtime manifest differs from the official attempt");
        require(runtime.combinedSha256().equals(opened.attempt().input().sealedCombinedSha256()),
                "runtime SEALED identity differs from the official attempt");

        Path receiptPath = opened.attempt().runDirectory().resolve("search-started-receipt.json");
        ObjectNode receipt = mapper.createObjectNode();
        receipt.put("artifactType", "PRZ042_SEARCH_STARTED_RECEIPT");
        receipt.put("protocolVersion", PROTOCOL_VERSION);
        receipt.put("attempt", 1);
        receipt.put("status", "SEARCH_STARTED");
        receipt.put("opened", true);
        receipt.put("searchExecuted", true);
        receipt.put("currentFreshBaseline", "RUNNING");
        receipt.put("recordedAt", Instant.now().toString());
        receipt.put("contractSha256", opened.attempt().input().contractSha256());
        receipt.put("attemptSha256", opened.attempt().attemptSha256());
        receipt.put("runtimeInputCanonicalSha256", runtime.canonicalSha256());
        receipt.put("sealedCombinedSha256", runtime.combinedSha256());
        writeCreateNew(receiptPath, receipt);
        return new SearchStarted(opened, runtime, receiptPath, sha256(receiptPath));
    }

    FailureReceipt recordFailure(Attempt attempt, String stage, Throwable failure) {
        Objects.requireNonNull(attempt, "attempt");
        Objects.requireNonNull(stage, "stage");
        Objects.requireNonNull(failure, "failure");
        verifyAttemptFile(attempt);
        Path receiptPath = attempt.runDirectory().resolve("failure-receipt.json");
        ObjectNode receipt = mapper.createObjectNode();
        receipt.put("artifactType", "PRZ042_FINAL_FAILURE_RECEIPT");
        receipt.put("protocolVersion", PROTOCOL_VERSION);
        receipt.put("attempt", 1);
        receipt.put("status", "FAILED");
        receipt.put("stage", stage);
        receipt.put("opened", Files.isRegularFile(attempt.runDirectory().resolve("input-opened-receipt.json")));
        receipt.put("searchExecuted", Files.isRegularFile(
                attempt.runDirectory().resolve("search-started-receipt.json")));
        receipt.put("recordedAt", Instant.now().toString());
        receipt.put("contractSha256", attempt.input().contractSha256());
        receipt.put("attemptSha256", attempt.attemptSha256());
        receipt.put("failureType", failure.getClass().getName());
        receipt.put("failureMessageSha256", sha256(Objects.toString(failure.getMessage(), "")));
        writeCreateNew(receiptPath, receipt);
        return new FailureReceipt(receiptPath, sha256(receiptPath));
    }

    VerifiedPredictions freezeAndVerifyPredictions(
            SearchStarted searchStarted,
            PredictionBundle bundle) {
        Objects.requireNonNull(searchStarted, "search started");
        Objects.requireNonNull(bundle, "bundle");
        Attempt attempt = searchStarted.opened().attempt();
        Prz042FinalDataset.RuntimeInput runtime = searchStarted.runtime();
        verifyAttemptFile(attempt);
        require(searchStarted.receiptSha256().equals(sha256(searchStarted.receiptPath())),
                "search-started receipt changed before prediction freeze");
        SearchV3MinimalShadowFreeze.OutputArtifact output = bundle.comparison();
        require(OUTPUT_TYPE.equals(output.artifactType()), "PRZ-042 output artifact type changed");
        require(output.schemaVersion() == 1, "PRZ-042 output schema changed");
        require(attempt.input().baseCommit().equals(output.codeFreezeCommit()),
                "output code freeze commit differs from the contract");
        requireSourceFreeze(attempt.input(), runtime, output.sourceFreeze());
        requireSealedIdentity(attempt.input(), output.sealedState());
        require(attempt.input().modelId().equals(output.model().name())
                        && attempt.input().modelDigest().equals(output.model().digest())
                        && attempt.input().modelDimension() == output.model().dimensions()
                        && "COSINE".equals(output.model().similarity()),
                "prediction model identity differs from the contract");
        require(output.queryCount() == attempt.input().queryCount(), "prediction query count changed");
        require(output.userCount() == attempt.input().userBundleCount(), "prediction user count changed");
        require(output.documentVersionCount() == runtime.documents().size(),
                "prediction document inventory changed");
        requireQueryIdentity(runtime, output.queries());
        requirePredictionStructure(runtime, output.queries());
        require(bundle.runtimeAudit().realBgeM3(), "official run did not use real BGE-M3");
        require(attempt.input().modelId().equals(bundle.runtimeAudit().modelId()),
                "runtime model ID differs from the frozen contract");
        require(attempt.input().modelDigest().equals(bundle.runtimeAudit().modelDigest()),
                "runtime model digest differs from the frozen contract");
        require(attempt.input().modelDimension() == bundle.runtimeAudit().modelDimension(),
                "runtime model dimension differs from the frozen contract");
        require(bundle.runtimeAudit().v2QueryExecutions() == output.queryCount(), "V2 query execution count changed");
        require(bundle.runtimeAudit().v3QueryExecutions() == output.queryCount(), "V3 query execution count changed");
        require(bundle.runtimeAudit().additionalModelCount() == 0,
                "official comparison introduced an additional model");
        require(bundle.runtimeAudit().additionalServiceCount() == 0,
                "official comparison introduced an additional service");
        require(!bundle.runtimeAudit().gpuRequired(),
                "official comparison unexpectedly requires a GPU");
        byte[] canonical = mapper.writeValueAsBytes(bundle);
        String canonicalSha = sha256(canonical);
        Path outputPath = attempt.runDirectory().resolve("predictions.json");
        ObjectNode wrapper = mapper.createObjectNode();
        wrapper.put("artifactType", "PRZ042_FROZEN_PREDICTION_WRAPPER");
        wrapper.put("canonicalSha256", canonicalSha);
        wrapper.put("canonicalByteLength", canonical.length);
        wrapper.set("output", mapper.valueToTree(bundle));
        writeCreateNew(outputPath, wrapper);

        byte[] fileBytes = readBytes(outputPath);
        JsonNode verified = read(outputPath);
        byte[] verifiedCanonical = mapper.writeValueAsBytes(verified.path("output"));
        require(canonicalSha.equals(verified.path("canonicalSha256").asText()),
                "prediction wrapper canonical SHA changed");
        require(canonical.length == verified.path("canonicalByteLength").asInt(-1),
                "prediction wrapper canonical length changed");
        require(canonicalSha.equals(sha256(verifiedCanonical)), "prediction content changed after freeze");
        String fileSha = sha256(fileBytes);
        Path receiptPath = attempt.runDirectory().resolve("predictions-frozen-receipt.json");
        ObjectNode receipt = mapper.createObjectNode();
        receipt.put("artifactType", "PRZ042_PREDICTIONS_FROZEN_RECEIPT");
        receipt.put("protocolVersion", PROTOCOL_VERSION);
        receipt.put("attempt", 1);
        receipt.put("status", "OUTPUT_VERIFIED");
        receipt.put("recordedAt", Instant.now().toString());
        receipt.put("contractSha256", attempt.input().contractSha256());
        receipt.put("attemptSha256", attempt.attemptSha256());
        receipt.put("runtimeInputCanonicalSha256", runtime.canonicalSha256());
        receipt.put("predictionsCanonicalSha256", canonicalSha);
        receipt.put("predictionsFileSha256", fileSha);
        writeCreateNew(receiptPath, receipt);
        return new VerifiedPredictions(
                searchStarted,
                bundle,
                outputPath,
                canonicalSha,
                fileSha,
                fileBytes.length,
                receiptPath,
                sha256(receiptPath));
    }

    VerifiedPredictions reloadVerifiedPredictions(SearchStarted searchStarted) {
        Objects.requireNonNull(searchStarted, "search started");
        Attempt attempt = searchStarted.opened().attempt();
        verifyAttemptFile(attempt);
        Path outputPath = attempt.runDirectory().resolve("predictions.json");
        Path receiptPath = attempt.runDirectory().resolve("predictions-frozen-receipt.json");
        JsonNode receipt = read(normalizedFile(receiptPath, "prediction freeze receipt"));
        require("PRZ042_PREDICTIONS_FROZEN_RECEIPT".equals(text(receipt, "artifactType")),
                "prediction freeze receipt type changed");
        require("OUTPUT_VERIFIED".equals(text(receipt, "status")),
                "prediction freeze receipt status changed");
        require(attempt.input().contractSha256().equals(text(receipt, "contractSha256")),
                "prediction freeze receipt contract changed");
        require(attempt.attemptSha256().equals(text(receipt, "attemptSha256")),
                "prediction freeze receipt attempt changed");
        require(searchStarted.runtime().canonicalSha256().equals(
                        text(receipt, "runtimeInputCanonicalSha256")),
                "prediction freeze receipt runtime changed");
        byte[] fileBytes = readBytes(normalizedFile(outputPath, "frozen predictions"));
        require(sha256(fileBytes).equals(text(receipt, "predictionsFileSha256")),
                "frozen prediction file changed");
        JsonNode wrapper = read(outputPath);
        String canonicalSha = text(receipt, "predictionsCanonicalSha256");
        require(canonicalSha.equals(wrapper.path("canonicalSha256").asText()),
                "frozen prediction canonical SHA changed");
        try {
            PredictionBundle bundle = mapper.treeToValue(wrapper.path("output"), PredictionBundle.class);
            require(canonicalSha.equals(sha256(mapper.writeValueAsBytes(bundle))),
                    "frozen prediction round-trip changed");
            return new VerifiedPredictions(
                    searchStarted,
                    bundle,
                    outputPath,
                    canonicalSha,
                    sha256(fileBytes),
                    fileBytes.length,
                    receiptPath,
                    sha256(receiptPath));
        }
        catch (RuntimeException exception) {
            throw new IllegalStateException("Cannot resume frozen PRZ-042 predictions", exception);
        }
    }

    CompletionReceipt complete(
            VerifiedPredictions verified,
            Object report,
            Map<String, Object> summary) {
        Objects.requireNonNull(verified, "verified predictions");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(summary, "summary");
        Path reportPath = verified.attempt().runDirectory().resolve("metrics.json");
        ObjectNode reportWrapper = mapper.createObjectNode();
        reportWrapper.put("artifactType", "PRZ042_FINAL_METRICS");
        reportWrapper.put("predictionCanonicalSha256", verified.canonicalSha256());
        reportWrapper.set("report", mapper.valueToTree(report));
        writeCreateNew(reportPath, reportWrapper);

        Path receiptPath = verified.attempt().runDirectory().resolve("completion-receipt.json");
        ObjectNode receipt = mapper.createObjectNode();
        receipt.put("artifactType", "PRZ042_FINAL_EXECUTION_RECEIPT");
        receipt.put("protocolVersion", PROTOCOL_VERSION);
        receipt.put("attempt", 1);
        receipt.put("status", "COMPLETED");
        receipt.put("opened", true);
        receipt.put("searchExecuted", true);
        receipt.put("currentFreshBaseline", "EXECUTED");
        receipt.put("completedAt", Instant.now().toString());
        receipt.put("contractSha256", verified.attempt().input().contractSha256());
        receipt.put("sealedCombinedSha256", verified.attempt().input().sealedCombinedSha256());
        receipt.put("predictionsCanonicalSha256", verified.canonicalSha256());
        receipt.put("predictionsFileSha256", verified.fileSha256());
        receipt.put("reportSha256", sha256(reportPath));
        receipt.set("summary", mapper.valueToTree(summary));
        writeCreateNew(receiptPath, receipt);
        return new CompletionReceipt(
                receiptPath,
                sha256(receiptPath),
                reportPath,
                sha256(reportPath),
                Map.copyOf(summary));
    }

    private void verifyAttemptFile(Attempt attempt) {
        Path expected = attempt.runDirectory().resolve("attempt.json").toAbsolutePath().normalize();
        require(expected.equals(attempt.attemptPath().toAbsolutePath().normalize()),
                "official attempt path changed");
        require(attempt.attemptSha256().equals(sha256(expected)), "official attempt file changed");
        JsonNode value = read(expected);
        require("PRZ042_OFFICIAL_ATTEMPT".equals(text(value, "artifactType")),
                "official attempt artifact type changed");
        require(PROTOCOL_VERSION.equals(text(value, "protocolVersion")),
                "official attempt protocol changed");
        require(value.path("attempt").asInt(-1) == 1, "official attempt number changed");
        require(attempt.input().contractSha256().equals(text(value, "contractSha256")),
                "official attempt contract changed");
        require(attempt.input().sealedCombinedSha256().equals(text(value, "sealedCombinedSha256")),
                "official attempt SEALED identity changed");
    }

    private static void requireSourceFreeze(
            VerifiedInput input,
            Prz042FinalDataset.RuntimeInput runtime,
            SearchV3MinimalShadowFreeze.SourceFreeze source) {
        require(input.sourceBoundaryHashes().get("V2").equals(source.v2SourceSha256()),
                "V2 source freeze differs from the contract");
        require(input.sourceBoundaryHashes().get("V3").equals(source.v3SourceSha256()),
                "V3 source freeze differs from the contract");
        require(input.sourceBoundaryHashes().get("EVALUATOR").equals(source.comparisonPolicySha256()),
                "evaluation source freeze differs from the contract");
        require(runtime.canonicalSha256().equals(source.inputCanonicalSha256()),
                "runtime input freeze differs from predictions");
        require(input.goldSchemaSha256().equals(source.goldSchemaSha256()),
                "Gold schema freeze differs from predictions");
        require(input.contractSha256().equals(source.contractFileSha256()),
                "contract freeze differs from predictions");
    }

    private static void requireSealedIdentity(
            VerifiedInput input,
            SearchV3MinimalShadowFreeze.SealedState sealed) {
        require(input.sealedCombinedSha256().equals(sealed.combinedSha256()),
                "prediction SEALED combined SHA changed");
        require(input.manifestSha256().equals(sealed.manifestSha256()),
                "prediction SEALED manifest SHA changed");
        require(input.sealedGitTree().equals(sealed.gitTree()),
                "prediction SEALED Git tree changed");
        require(!sealed.opened() && !sealed.searchExecuted()
                        && "NOT_RUN".equals(sealed.currentFreshBaseline()),
                "prediction did not preserve the pre-execution SEALED state");
    }

    private static void requireQueryIdentity(
            Prz042FinalDataset.RuntimeInput runtime,
            List<SearchV3MinimalShadowFreeze.QueryOutput> output) {
        require(output.size() == runtime.queries().size(), "prediction query inventory changed");
        Map<String, SearchV3MinimalShadowFreeze.QueryOutput> byId = new LinkedHashMap<>();
        for (SearchV3MinimalShadowFreeze.QueryOutput query : output) {
            require(byId.putIfAbsent(query.queryId(), query) == null,
                    "duplicate prediction query: " + query.queryId());
        }
        for (Prz042FinalDataset.RuntimeQuery expected : runtime.queries()) {
            SearchV3MinimalShadowFreeze.QueryOutput actual = byId.get(expected.queryId());
            require(actual != null
                            && "FRESH_FINAL".equals(actual.suite())
                            && runtime.datasetVersion().equals(actual.datasetVersion())
                            && runtime.split().equals(actual.split())
                            && expected.userBundleId().equals(actual.userBundleId())
                            && expected.professionGroup().equals(actual.professionGroup())
                            && expected.language().equals(actual.language())
                            && Prz042FinalDataset.sha256(expected.text()).equals(actual.queryTextSha256()),
                    "prediction query identity changed: " + expected.queryId());
        }
    }

    static void requirePredictionStructure(
            Prz042FinalDataset.RuntimeInput runtime,
            List<SearchV3MinimalShadowFreeze.QueryOutput> output) {
        Map<String, Prz042FinalDataset.RuntimeDocument> documents = runtime.documents().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Prz042FinalDataset.RuntimeDocument::versionId,
                        java.util.function.Function.identity()));
        for (SearchV3MinimalShadowFreeze.QueryOutput query : output) {
            require(query.v2().candidates().size() <= 20 && query.v3().candidates().size() <= 20,
                    "candidate limit changed: " + query.queryId());
            require(query.v2().finalResults().size() <= 5 && query.v3().finalResults().size() <= 5,
                    "final result limit changed: " + query.queryId());
            requireSequentialRanks(query.v2().candidates().stream().map(
                    ProductionV2ShadowAdapter.CandidateResult::rank).toList(), "V2 candidates", query.queryId());
            requireSequentialRanks(query.v2().finalResults().stream().map(
                    ProductionV2ShadowAdapter.FinalResult::rank).toList(), "V2 final", query.queryId());
            requireSequentialRanks(query.v3().candidates().stream().map(
                    MinimalV3ShadowAdapter.CandidateResult::rank).toList(), "V3 candidates", query.queryId());
            requireSequentialRanks(query.v3().finalResults().stream().map(
                    MinimalV3ShadowAdapter.FinalResult::rank).toList(), "V3 final", query.queryId());
            requireDistinct(query.v2().candidates().stream().map(
                    ProductionV2ShadowAdapter.CandidateResult::candidateId).toList(), "V2 candidate");
            requireDistinct(query.v3().candidates().stream().map(
                    MinimalV3ShadowAdapter.CandidateResult::candidateId).toList(), "V3 candidate");
            requireDistinct(query.v3().finalResults().stream().map(
                    MinimalV3ShadowAdapter.FinalResult::evidenceChildId).toList(), "V3 evidence child");

            query.v2().candidates().forEach(value -> requireSpan(
                    query, value.span(), documents, "V2 candidate"));
            query.v2().finalResults().forEach(value -> {
                requireSpan(query, value.selectedSpan(), documents, "V2 selected");
                requireSpan(query, value.evidenceChunkSpan(), documents, "V2 evidence");
                requireSpan(query, value.displaySpan(), documents, "V2 display");
            });
            query.v3().candidates().forEach(value -> {
                require(!value.spans().isEmpty(), "V3 passage has no child provenance");
                value.spans().forEach(span -> requireSpan(query, span, documents, "V3 passage child"));
            });
            query.v3().finalResults().forEach(value -> {
                require(value.denseRank() >= 1 && value.denseRank() <= 5,
                        "V3 Child selector escaped Top5 Passage");
                requireSpan(query, value.span(), documents, "V3 final child");
            });
            requireFiniteNonNegative(query.v2().finalTotalMs(), "V2 latency");
            requireFiniteNonNegative(query.v3().totalMs(), "V3 latency");
        }
    }

    private static void requireSequentialRanks(List<Integer> ranks, String label, String queryId) {
        for (int index = 0; index < ranks.size(); index++) {
            require(ranks.get(index) == index + 1,
                    label + " ranks changed for " + queryId + ": " + ranks);
        }
    }

    private static void requireDistinct(List<String> values, String label) {
        require(values.stream().noneMatch(value -> value == null || value.isBlank())
                        && values.size() == new java.util.LinkedHashSet<>(values).size(),
                label + " identity is empty or duplicated");
    }

    private static void requireSpan(
            SearchV3MinimalShadowFreeze.QueryOutput query,
            ProductionV2ShadowAdapter.SourceSpan span,
            Map<String, Prz042FinalDataset.RuntimeDocument> documents,
            String label) {
        Prz042FinalDataset.RuntimeDocument document = documents.get(span.versionId());
        require(document != null
                        && document.active()
                        && query.userBundleId().equals(span.userBundleId())
                        && document.userBundleId().equals(span.userBundleId())
                        && document.documentId().equals(span.documentId())
                        && document.sourcePath().equals(span.sourcePath())
                        && span.codePointStart() >= 0
                        && span.codePointEnd() > span.codePointStart()
                        && span.codePointEnd() <= document.sourceText().codePointCount(
                                0, document.sourceText().length())
                        && span.sourceTextSha256().equals(Prz042FinalDataset.sha256(span.sourceText())),
                label + " provenance is invalid for " + query.queryId());
        int start = document.sourceText().offsetByCodePoints(0, span.codePointStart());
        int end = document.sourceText().offsetByCodePoints(0, span.codePointEnd());
        require(document.sourceText().substring(start, end).equals(span.sourceText()),
                label + " source span differs from frozen text for " + query.queryId());
    }

    private static void requireFiniteNonNegative(double value, String label) {
        require(Double.isFinite(value) && value >= 0.0d, label + " is invalid");
    }

    static String canonicalFileSetSha256(List<Path> candidates) {
        List<Path> files = candidates.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .distinct()
                .sorted(Comparator.comparing(Prz042FinalFreeze::portableProjectPath))
                .toList();
        StringBuilder canonical = new StringBuilder();
        for (Path file : files) {
            if (!Files.isRegularFile(file) || Files.isSymbolicLink(file)) {
                throw new IllegalStateException("Frozen source is missing or symbolic: " + file);
            }
            canonical.append(portableProjectPath(file))
                    .append('|').append(file.toFile().length())
                    .append('|').append(sha256(file)).append('\n');
        }
        return sha256(canonical.toString().getBytes(StandardCharsets.UTF_8));
    }

    static String sha256(Path path) {
        return sha256(readBytes(path));
    }

    static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Cannot read frozen file: " + path, exception);
        }
    }

    private JsonNode read(Path path) {
        try {
            return mapper.readTree(Files.readAllBytes(path));
        }
        catch (IOException exception) {
            throw new IllegalStateException("Cannot read JSON: " + path, exception);
        }
    }

    private void writeCreateNew(Path path, JsonNode value) {
        try {
            Files.createDirectories(path.toAbsolutePath().normalize().getParent());
            Files.writeString(
                    path,
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        }
        catch (IOException exception) {
            throw new IllegalStateException("Cannot CREATE_NEW PRZ-042 artifact: " + path, exception);
        }
    }

    private static Path normalizedFile(Path path, String label) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized) || Files.isSymbolicLink(normalized)) {
            throw new IllegalStateException(label + " is missing or symbolic: " + normalized);
        }
        return normalized;
    }

    private static Path normalizedDirectory(Path path, String label) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized) || Files.isSymbolicLink(normalized)) {
            throw new IllegalStateException(label + " is missing or symbolic: " + normalized);
        }
        return normalized;
    }

    private static String portableProjectPath(Path path) {
        Path root = Path.of("").toAbsolutePath().normalize();
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root)) {
            throw new IllegalStateException("Frozen source escaped project root: " + normalized);
        }
        return root.relativize(normalized).toString().replace('\\', '/');
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Required contract field is missing: " + field);
        }
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    record VerifiedInput(
            Path contractPath,
            String contractSha256,
            Path splitRoot,
            Path manifestPath,
            String manifestSha256,
            String sealedCombinedSha256,
            String datasetVersion,
            String split,
            String goldSchemaSha256,
            String sealedGitTree,
            String baseCommit,
            String modelId,
            String modelDigest,
            int modelDimension,
            Map<String, String> sourceBoundaryHashes,
            int userBundleCount,
            int queryCount,
            int directPositiveQueryCount,
            int notSupportedQueryCount) {
    }

    record Attempt(VerifiedInput input, Path runDirectory, Path attemptPath, String attemptSha256) {
    }

    record OpenedAttempt(Attempt attempt, Path receiptPath, String receiptSha256) {
    }

    record SearchStarted(
            OpenedAttempt opened,
            Prz042FinalDataset.RuntimeInput runtime,
            Path receiptPath,
            String receiptSha256) {
    }

    record FailureReceipt(Path receiptPath, String receiptSha256) {
    }

    record RuntimeAudit(
            int ownerCount,
            int documentCount,
            int activeDocumentCount,
            long v2ActiveChunkCount,
            long v2InactiveDecoyChunkCount,
            long v3PassageCount,
            long v3ChildCount,
            long v3PassageVectorCount,
            long v3ChildVectorCount,
            long ownerLeakageCount,
            long inactiveVersionLeakageCount,
            long lifecycleViolationCount,
            long duplicateArtifactCount,
            long mixedArtifactCount,
            boolean realBgeM3,
            String modelId,
            String modelDigest,
            int modelDimension,
            int v2QueryExecutions,
            int v3QueryExecutions,
            int additionalModelCount,
            int additionalServiceCount,
            boolean gpuRequired) {
    }

    record PredictionBundle(
            SearchV3MinimalShadowFreeze.OutputArtifact comparison,
            RuntimeAudit runtimeAudit) {

        PredictionBundle {
            Objects.requireNonNull(comparison, "comparison");
            Objects.requireNonNull(runtimeAudit, "runtimeAudit");
        }
    }

    record VerifiedPredictions(
            SearchStarted searchStarted,
            PredictionBundle bundle,
            Path outputPath,
            String canonicalSha256,
            String fileSha256,
            long fileBytes,
            Path receiptPath,
            String receiptSha256) {

        Attempt attempt() {
            return searchStarted.opened().attempt();
        }
    }

    record CompletionReceipt(
            Path receiptPath,
            String receiptSha256,
            Path reportPath,
            String reportSha256,
            Map<String, Object> summary) {
    }
}
