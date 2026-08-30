package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Executes the pre-registered B3 versus C1 comparison on existing DEV/CAL datasets only. */
class Prz026ParentContextDenseBenchmarkTest {

    private static final Path DEFAULT_OUTPUT = Path.of(
            "local/search-v3-evaluation/prz026/parent-context-c1.json");
    private static final Pattern COMMIT_SHA = Pattern.compile("^[0-9a-f]{40}$");
    private static final String FROZEN_EVIDENCE_CHILD_BUILDER_SHA256 =
            "6ff76f49df332319fac987a59be4ead11d7ecda90b44f0d11e0cb538acd6cb83";
    private static final String FROZEN_RETRIEVAL_PASSAGE_BUILDER_SHA256 =
            "64c93a0ba50ec2785209a85abd339fa0e4d6de0dc6a99ac29dedfa3a93dc2c39";
    private static final Path EVIDENCE_CHILD_BUILDER = Path.of(
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralEvidenceChildBuilder.java");
    private static final Path RETRIEVAL_PASSAGE_BUILDER = Path.of(
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralRetrievalPassageBuilder.java");
    private static final List<String> EXECUTION_SOURCE_FILES = List.of(
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralBlockParser.java",
            EVIDENCE_CHILD_BUILDER.toString().replace('\\', '/'),
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/RetrievalPassage.java",
            RETRIEVAL_PASSAGE_BUILDER.toString().replace('\\', '/'),
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/ContextualRetrievalPassage.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralHeadingPathContextBuilder.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/SearchV3DenseAblationDataset.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/SearchV3DenseAblationEngine.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/ParentContextAblationGate.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/OllamaBgeM3EmbeddingClient.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/Prz026ParentContextDenseBenchmarkTest.java");

    @Test
    void comparesFrozenB3AndC1OnExistingDevCalibrationSuitesOnly() throws Exception {
        String inputFreezeCommit = System.getProperty("prizm.prz026.parent-context-input-freeze-commit", "");
        assertThat(inputFreezeCommit).matches(COMMIT_SHA);
        assertThat(sha256(EVIDENCE_CHILD_BUILDER)).isEqualTo(FROZEN_EVIDENCE_CHILD_BUILDER_SHA256);
        assertThat(sha256(RETRIEVAL_PASSAGE_BUILDER)).isEqualTo(FROZEN_RETRIEVAL_PASSAGE_BUILDER_SHA256);

        SearchV3DenseAblationDataset loader = new SearchV3DenseAblationDataset();
        SearchV3DenseAblationDataset.SealedManifestMetadata sealedBefore =
                loader.readSealedManifestMetadata();
        SearchV3DenseAblationDataset.LongFormManifestMetadata longFormBefore =
                loader.readLongFormManifestMetadata();
        SearchV3DenseAblationDataset.RobustnessManifestMetadata robustnessBefore =
                loader.readRobustnessManifestMetadata();
        List<SearchV3DenseAblationDataset.DatasetSlice> original = List.of(
                loader.load(SearchV3DenseAblationDataset.Split.DEV),
                loader.load(SearchV3DenseAblationDataset.Split.CALIBRATION));
        List<SearchV3DenseAblationDataset.DatasetSlice> longForm = List.of(
                loader.loadLongForm(SearchV3DenseAblationDataset.Split.DEV),
                loader.loadLongForm(SearchV3DenseAblationDataset.Split.CALIBRATION));
        List<SearchV3DenseAblationDataset.DatasetSlice> robustness = List.of(
                loader.loadRobustness(SearchV3DenseAblationDataset.Split.DEV),
                loader.loadRobustness(SearchV3DenseAblationDataset.Split.CALIBRATION));

        OllamaBgeM3EmbeddingClient client = new OllamaBgeM3EmbeddingClient();
        OllamaBgeM3EmbeddingClient.ModelMetadata model = client.inspectModel();
        assertThat(model.resolvedName()).startsWith("bge-m3");
        assertThat(model.dimensions()).isEqualTo(1024);
        assertThat(model.embeddingCapable()).isTrue();

        SearchV3DenseAblationEngine engine = new SearchV3DenseAblationEngine();
        SearchV3DenseAblationEngine.ParentContextExperimentReport originalReport = engine.runParentContext(
                original, client, model, "PRZ-026-C1-ORIGINAL-SEED-DEV-CAL", inputFreezeCommit);
        SearchV3DenseAblationEngine.ParentContextExperimentReport longFormReport = engine.runParentContext(
                longForm, client, model, "PRZ-026-C1-LONG-FORM-DEV-CAL", inputFreezeCommit);
        SearchV3DenseAblationEngine.ParentContextExperimentReport robustnessReport = engine.runParentContext(
                robustness, client, model, "PRZ-026-C1-ROBUSTNESS-DEV-CAL", inputFreezeCommit);
        List<SearchV3DenseAblationEngine.ParentContextExperimentReport> reports =
                List.of(originalReport, longFormReport, robustnessReport);
        ParentContextAblationGate.Assessment assessment = new ParentContextAblationGate().assess(reports);

        SearchV3DenseAblationDataset.SealedManifestMetadata sealedAfter =
                loader.readSealedManifestMetadata();
        SearchV3DenseAblationDataset.LongFormManifestMetadata longFormAfter =
                loader.readLongFormManifestMetadata();
        SearchV3DenseAblationDataset.RobustnessManifestMetadata robustnessAfter =
                loader.readRobustnessManifestMetadata();
        assertThat(sealedAfter).isEqualTo(sealedBefore);
        assertThat(longFormAfter).isEqualTo(longFormBefore);
        assertThat(robustnessAfter).isEqualTo(robustnessBefore);
        assertThat(reports).allSatisfy(report -> {
            assertThat(report.sealedFinalOpened()).isFalse();
            assertThat(report.sealedFinalSearchExecuted()).isFalse();
            assertThat(report.currentFreshBaseline()).isEqualTo("NOT_RUN");
            assertThat(report.passageCorpus().candidateCount()).isEqualTo(report.contextCorpus().candidateCount());
            assertThat(report.passageCorpus().embeddingCount()).isEqualTo(report.contextCorpus().embeddingCount());
            assertThat(report.passageStats().directGoldEvidenceChildPreservationRate()).isEqualTo(1.0d);
            assertThat(report.contextStats().crossParentContextViolationCount()).isZero();
            assertThat(report.contextStats().sourceParityViolationCount()).isZero();
            assertThat(report.contextStats().evidenceChildParityViolationCount()).isZero();
        });

        Path output = Path.of(System.getProperty(
                        "prizm.prz026.parent-context-output", DEFAULT_OUTPUT.toString()))
                .toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        ParentContextReport report = new ParentContextReport(
                1,
                "PRZ-026-PHASE-1-C1-STRUCTURAL-HEADING-PATH-CONTEXT",
                inputFreezeCommit,
                executionSourceSnapshot(),
                originalReport,
                longFormReport,
                robustnessReport,
                assessment,
                longFormAfter,
                robustnessAfter,
                sealedAfter,
                "NOT_RUN");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n";
        Files.writeString(output, json, StandardCharsets.UTF_8);
        String reportSha256 = sha256(json.getBytes(StandardCharsets.UTF_8));

        System.out.println("PRZ026_C1_REPORT=" + output);
        System.out.println("PRZ026_C1_REPORT_SHA256=" + reportSha256);
        System.out.println("PRZ026_C1_MODEL=" + model.resolvedName());
        System.out.println("PRZ026_C1_MODEL_DIGEST=" + model.digest());
        printSummary("ORIGINAL", originalReport);
        printSummary("LONG_FORM", longFormReport);
        printSummary("ROBUSTNESS", robustnessReport);
        System.out.println("PRZ026_C1_CONTEXT_FALSE_HITS=" + assessment.contextOnlyFalseHitCount());
        System.out.println("PRZ026_C1_DECISION=" + assessment.decision());
        System.out.println("PRZ026_SEALED_FINAL_SEARCH_EXECUTED=false");
    }

    private void printSummary(
            String name,
            SearchV3DenseAblationEngine.ParentContextExperimentReport report) {
        System.out.println("PRZ026_C1_" + name + "_CANDIDATES=" + report.contextCorpus().candidateCount());
        System.out.println("PRZ026_C1_" + name + "_B3_TOP1=" + report.queryMicro().passage().top1());
        System.out.println("PRZ026_C1_" + name + "_C1_TOP1=" + report.queryMicro().context().top1());
        System.out.println("PRZ026_C1_" + name + "_B3_MRR=" + report.queryMicro().passage().mrr());
        System.out.println("PRZ026_C1_" + name + "_C1_MRR=" + report.queryMicro().context().mrr());
    }

    private ExecutionSourceSnapshot executionSourceSnapshot() throws Exception {
        Map<String, String> fileHashes = new LinkedHashMap<>();
        for (String sourceFile : EXECUTION_SOURCE_FILES) {
            fileHashes.put(sourceFile, sha256(Path.of(sourceFile)));
        }
        String hashInput = fileHashes.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
        return new ExecutionSourceSnapshot(
                "CONTENT_ADDRESSED_WORKTREE_SNAPSHOT",
                Map.copyOf(fileHashes),
                sha256(hashInput.getBytes(StandardCharsets.UTF_8)),
                FROZEN_EVIDENCE_CHILD_BUILDER_SHA256,
                FROZEN_RETRIEVAL_PASSAGE_BUILDER_SHA256);
    }

    private String sha256(Path path) throws Exception {
        return sha256(Files.readAllBytes(path));
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    record ParentContextReport(
            int schemaVersion,
            String phase,
            String inputFreezeCommit,
            ExecutionSourceSnapshot executionSource,
            SearchV3DenseAblationEngine.ParentContextExperimentReport originalSeed,
            SearchV3DenseAblationEngine.ParentContextExperimentReport longForm,
            SearchV3DenseAblationEngine.ParentContextExperimentReport robustness,
            ParentContextAblationGate.Assessment assessment,
            SearchV3DenseAblationDataset.LongFormManifestMetadata longFormManifest,
            SearchV3DenseAblationDataset.RobustnessManifestMetadata robustnessManifest,
            SearchV3DenseAblationDataset.SealedManifestMetadata sealedFinal,
            String parentDenseStatus) {
    }

    record ExecutionSourceSnapshot(
            String kind,
            Map<String, String> fileSha256,
            String combinedSha256,
            String frozenEvidenceChildBuilderSha256,
            String frozenRetrievalPassageBuilderSha256) {
    }
}
