package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.evaluation.searchv3.typed.TypedConstraintStressDataset;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Executes the frozen PRZ-029 E0/E1 comparison on DEV/CAL inputs only. */
class Prz029EvidenceValidationSelectionBenchmarkTest {

    private static final Path OUTPUT = Path.of(
            "local/search-v3-evaluation/prz029/evidence-validation-selection.json");
    private static final Path SEALED_MANIFEST = Path.of(
            "src/test/resources/search-v3-evaluation/sealed-final/manifest.json");
    private static final String SEALED_COMBINED_SHA256 =
            "e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383";
    private static final String EXPECTED_MODEL_NAME = "bge-m3:latest";
    private static final String EXPECTED_MODEL_DIGEST =
            "7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab";
    private static final Pattern COMMIT_SHA = Pattern.compile("^[0-9a-f]{40}$");

    @Test
    void validatesBeforeSelectingEvidenceOnFrozenDevCalibrationOnly() throws Exception {
        String codeFreeze = System.getProperty("prizm.prz029.code-freeze-commit", "");
        assertThat(codeFreeze).matches(COMMIT_SHA);
        assertThat(git("rev-parse", "HEAD")).isEqualTo(codeFreeze);
        assertThat(git("status", "--porcelain", "--untracked-files=all")).isBlank();
        assertThat(Files.exists(OUTPUT)).as("PRZ-029 report must be CREATE_NEW").isFalse();

        SealedMetadata sealedBefore = sealedMetadataOnly();
        String sealedManifestShaBefore = sha256(SEALED_MANIFEST);
        assertThat(sealedBefore).isEqualTo(new SealedMetadata(
                SEALED_COMBINED_SHA256, false, false, false));

        SearchV3DenseAblationDataset loader = new SearchV3DenseAblationDataset();
        List<SearchV3DenseAblationDataset.DatasetSlice> original = List.of(
                loader.load(SearchV3DenseAblationDataset.Split.DEV),
                loader.load(SearchV3DenseAblationDataset.Split.CALIBRATION));
        List<SearchV3DenseAblationDataset.DatasetSlice> longForm = List.of(
                loader.loadLongForm(SearchV3DenseAblationDataset.Split.DEV),
                loader.loadLongForm(SearchV3DenseAblationDataset.Split.CALIBRATION));
        List<SearchV3DenseAblationDataset.DatasetSlice> robustness = List.of(
                loader.loadRobustness(SearchV3DenseAblationDataset.Split.DEV),
                loader.loadRobustness(SearchV3DenseAblationDataset.Split.CALIBRATION));
        List<SearchV3DenseAblationDataset.DatasetSlice> typed = List.of(
                loader.loadTypedStressOfficial(SearchV3DenseAblationDataset.Split.DEV),
                loader.loadTypedStressOfficial(SearchV3DenseAblationDataset.Split.CALIBRATION));

        TypedConstraintStressDataset strictLoader = new TypedConstraintStressDataset();
        List<TypedConstraintStressDataset.DatasetSlice> strictTyped = List.of(
                strictLoader.load(TypedConstraintStressDataset.OFFICIAL_1_1_0,
                        TypedConstraintStressDataset.Split.DEV),
                strictLoader.load(TypedConstraintStressDataset.OFFICIAL_1_1_0,
                        TypedConstraintStressDataset.Split.CALIBRATION));

        OllamaBgeM3EmbeddingClient client = new OllamaBgeM3EmbeddingClient();
        OllamaBgeM3EmbeddingClient.ModelMetadata model = client.inspectModel();
        assertThat(model.resolvedName()).isEqualTo(EXPECTED_MODEL_NAME);
        assertThat(model.digest()).isEqualTo(EXPECTED_MODEL_DIGEST);
        assertThat(model.dimensions()).isEqualTo(1024);
        assertThat(model.embeddingCapable()).isTrue();
        assertThat(OllamaBgeM3EmbeddingClient.SIMILARITY).isEqualTo("COSINE");

        MemoryUsage heapBefore = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
        SearchV3DenseAblationEngine denseEngine = new SearchV3DenseAblationEngine();
        SearchV3EvidenceSelectionAblationEngine selectionEngine =
                new SearchV3EvidenceSelectionAblationEngine();
        List<SuiteReport> suites = List.of(
                runSuite("ORIGINAL_SEED", original, List.of(), denseEngine, selectionEngine, client, model),
                runSuite("LONG_FORM", longForm, List.of(), denseEngine, selectionEngine, client, model),
                runSuite("ROBUSTNESS", robustness, List.of(), denseEngine, selectionEngine, client, model),
                runSuite("TYPED_STRESS_1_1_0", typed, strictTyped,
                        denseEngine, selectionEngine, client, model));
        MemoryUsage heapAfter = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();

        assertThat(git("rev-parse", "HEAD")).isEqualTo(codeFreeze);
        assertThat(git("status", "--porcelain", "--untracked-files=all")).isBlank();
        assertThat(sealedMetadataOnly()).isEqualTo(sealedBefore);
        assertThat(sha256(SEALED_MANIFEST)).isEqualTo(sealedManifestShaBefore);

        GateAssessment gate = assessGate(suites);

        BenchmarkReport report = new BenchmarkReport(
                1,
                "PRZ-029_EVIDENCE_VALIDATION_SELECTION",
                codeFreeze,
                model,
                EvidenceValidationSelector.VALIDATION_CANDIDATE_K,
                EvidenceValidationSelector.MAX_SELECTED_EVIDENCE,
                "B3 full owner-scoped candidate identity/order retained; only top-20 is typed-validated",
                "FOUND/PARTIAL support selects no CONTRADICTED evidence; NONE may select labeled exclusion evidence",
                suites,
                gate,
                new MemoryObservation(
                        "JVM_HEAP_POINT_OBSERVATION_NOT_ISOLATED",
                        heapBefore.getUsed(),
                        heapAfter.getUsed(),
                        heapAfter.getUsed() - heapBefore.getUsed(),
                        0,
                        0),
                sealedBefore,
                "NOT_RUN");
        writeCreateNew(report);

        System.out.println("PRZ029_OUTPUT=" + OUTPUT.toAbsolutePath().normalize());
        System.out.println("PRZ029_OUTPUT_SHA256=" + sha256(OUTPUT));
        System.out.println("PRZ029_MODEL=" + model.resolvedName());
        System.out.println("PRZ029_MODEL_DIGEST=" + model.digest());
        System.out.println("PRZ029_SEALED_HASH=" + sealedBefore.combinedSha256());
        System.out.println("PRZ029_SEALED_SEARCH_EXECUTED=" + sealedBefore.searchExecuted());
        System.out.println("PRZ029_GATE_FINDINGS=" + gate.findings());

        // Preserve the immutable raw report even when a quality Gate fails.
        assertThat(gate.findings()).as("PRZ-029 quality Gate findings").isEmpty();
    }

    private SuiteReport runSuite(
            String suite,
            List<SearchV3DenseAblationDataset.DatasetSlice> datasets,
            List<TypedConstraintStressDataset.DatasetSlice> strictTyped,
            SearchV3DenseAblationEngine denseEngine,
            SearchV3EvidenceSelectionAblationEngine selectionEngine,
            OllamaBgeM3EmbeddingClient client,
            OllamaBgeM3EmbeddingClient.ModelMetadata model) {
        SearchV3DenseAblationEngine.PassageDenseRun dense = denseEngine.runPassageDenseOnly(
                datasets,
                client,
                model,
                "PRZ029_E0_E1_SHARED_B3_DENSE");
        SearchV3EvidenceSelectionAblationEngine.ExperimentReport result =
                selectionEngine.evaluate(suite, dense, strictTyped);
        return new SuiteReport(
                suite,
                datasets.stream().map(value -> new SplitInput(
                        value.split().manifestName(),
                        value.manifestCombinedSha256(),
                        value.bundles().size(),
                        value.activeDocumentsByVersion().size(),
                        value.queries().size())).toList(),
                result);
    }

    private GateAssessment assessGate(List<SuiteReport> suites) {
        List<String> findings = new ArrayList<>();
        for (SuiteReport suite : suites) {
            SearchV3EvidenceSelectionAblationEngine.AggregateMetrics metrics = suite.result().aggregate();
            finding(findings, metrics.e1CandidateRecall20Count() == metrics.e0CandidateRecall20Count(),
                    suite.suite() + ":CANDIDATE_RECALL_CHANGED");
            finding(findings, metrics.directRank1LossCount() == 0,
                    suite.suite() + ":DIRECT_RANK1_LOSS");
            finding(findings, metrics.semanticExactParityCount() == metrics.semanticQueryCount(),
                    suite.suite() + ":SEMANTIC_PARITY_FAILED");
            finding(findings, metrics.duplicateSelectedCount() == 0,
                    suite.suite() + ":DUPLICATE_SELECTION");
            finding(findings, metrics.crossParentMergeViolationCount() == 0,
                    suite.suite() + ":CROSS_PARENT_MERGE");
            finding(findings, metrics.provenanceAccuracy() == 1.0d,
                    suite.suite() + ":PROVENANCE_FAILED");
        }
        SearchV3EvidenceSelectionAblationEngine.AggregateMetrics typed = suites.get(3).result().aggregate();
        finding(findings, typed.typedQueryCount() == 24, "TYPED_QUERY_COUNT");
        finding(findings, typed.constraintConformanceCount() == 24, "TYPED_CONSTRAINT_CONFORMANCE");
        finding(findings, typed.typedStateCorrectCount() == 24, "TYPED_STATE_ERROR");
        finding(findings, typed.correctEvidenceSelectionCount() == 24, "TYPED_SELECTION_ERROR");
        finding(findings, typed.incorrectSelectedEvidenceCount() == 0, "TYPED_SELECTION_POLLUTION");
        finding(findings, typed.supportContradictedSelectedCount() == 0,
                "SUPPORT_CONTRADICTED_SELECTION");
        finding(findings, typed.unknownFallbackQueryCount() == 2, "UNKNOWN_FALLBACK_COUNT");
        finding(findings, typed.directRank1GainCount() > 0, "NO_SATISFIED_DIRECT_SELECTION_GAIN");
        finding(findings, typed.typedStateConfusion().getOrDefault("FOUND", Map.of())
                .getOrDefault("FOUND", 0L) == 16L, "FOUND_CONFUSION");
        finding(findings, typed.typedStateConfusion().getOrDefault("NONE", Map.of())
                .getOrDefault("NONE", 0L) == 6L, "NONE_CONFUSION");
        finding(findings, typed.typedStateConfusion().getOrDefault("PARTIAL", Map.of())
                .getOrDefault("PARTIAL", 0L) == 2L, "PARTIAL_CONFUSION");
        return new GateAssessment(findings.isEmpty(), List.copyOf(findings));
    }

    private void finding(List<String> findings, boolean condition, String code) {
        if (!condition) findings.add(code);
    }

    private SealedMetadata sealedMetadataOnly() throws Exception {
        JsonNode manifest;
        try (InputStream input = Files.newInputStream(SEALED_MANIFEST)) {
            manifest = new ObjectMapper().readTree(input);
        }
        return new SealedMetadata(
                manifest.path("combinedSha256").asText(),
                manifest.path("opened").asBoolean(),
                manifest.path("searchExecuted").asBoolean(),
                manifest.path("mutable").asBoolean());
    }

    private void writeCreateNew(BenchmarkReport report) throws Exception {
        Files.createDirectories(OUTPUT.getParent());
        String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n";
        Path temporary = OUTPUT.resolveSibling(OUTPUT.getFileName() + ".tmp-" + UUID.randomUUID());
        Files.writeString(
                temporary,
                json,
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        try {
            Files.move(temporary, OUTPUT, StandardCopyOption.ATOMIC_MOVE);
        }
        catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporary, OUTPUT);
        }
    }

    private String git(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int exit = process.waitFor();
        if (exit != 0) throw new IllegalStateException("git command failed: " + output);
        return output;
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

    record SplitInput(
            String split,
            String manifestCombinedSha256,
            int userBundleCount,
            int documentCount,
            int queryCount) {
    }

    record SuiteReport(
            String suite,
            List<SplitInput> inputs,
            SearchV3EvidenceSelectionAblationEngine.ExperimentReport result) {
        SuiteReport {
            inputs = List.copyOf(inputs);
        }
    }

    record MemoryObservation(
            String method,
            long heapBeforeBytes,
            long heapAfterBytes,
            long observedDeltaBytes,
            long persistentIndexCount,
            long persistentStorageWriteCount) {
    }

    record SealedMetadata(
            String combinedSha256,
            boolean opened,
            boolean searchExecuted,
            boolean mutable) {
    }

    record GateAssessment(boolean passed, List<String> findings) {
        GateAssessment {
            findings = List.copyOf(findings);
        }
    }

    record BenchmarkReport(
            int schemaVersion,
            String phase,
            String codeFreezeCommit,
            OllamaBgeM3EmbeddingClient.ModelMetadata model,
            int validationCandidateK,
            int maxSelectedEvidence,
            String candidatePolicy,
            String contradictedEvidencePolicy,
            List<SuiteReport> suites,
            GateAssessment gate,
            MemoryObservation memory,
            SealedMetadata sealedFinal,
            String currentFreshBaseline) {
        BenchmarkReport {
            suites = List.copyOf(suites);
        }
    }
}
