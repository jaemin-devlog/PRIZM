package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Runs the frozen B3 policy on the independent robustness DEV/CAL suite. */
class Prz026RetrievalPassageRobustnessBenchmarkTest {

    private static final Path DEFAULT_OUTPUT = Path.of(
            "local/search-v3-evaluation/prz026/retrieval-passage-b3-robustness.json");
    private static final Pattern COMMIT_SHA = Pattern.compile("^[0-9a-f]{40}$");
    private static final List<String> EXECUTION_SOURCE_FILES = List.of(
            "scripts/evaluation/search-v3/materialize-prz026-robustness-devcal.mjs",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralBlockParser.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralEvidenceChildBuilder.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/RetrievalPassage.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralRetrievalPassageBuilder.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/SearchV3DenseAblationDataset.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/SearchV3DenseAblationEngine.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/RetrievalPassageRobustnessGate.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/OllamaBgeM3EmbeddingClient.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/Prz026RetrievalPassageRobustnessBenchmarkTest.java");

    @Test
    void runsFrozenB3AgainstIndependentRobustnessDevCalibrationOnly() throws Exception {
        String inputFreezeCommit = System.getProperty("prizm.prz026.input-freeze-commit", "");
        assertThat(inputFreezeCommit).matches(COMMIT_SHA);

        SearchV3DenseAblationDataset loader = new SearchV3DenseAblationDataset();
        SearchV3DenseAblationDataset.SealedManifestMetadata sealedBefore =
                loader.readSealedManifestMetadata();
        SearchV3DenseAblationDataset.RobustnessManifestMetadata robustnessBefore =
                loader.readRobustnessManifestMetadata();
        List<SearchV3DenseAblationDataset.DatasetSlice> longFormSlices = List.of(
                loader.loadLongForm(SearchV3DenseAblationDataset.Split.DEV),
                loader.loadLongForm(SearchV3DenseAblationDataset.Split.CALIBRATION));
        List<SearchV3DenseAblationDataset.DatasetSlice> robustnessSlices = List.of(
                loader.loadRobustness(SearchV3DenseAblationDataset.Split.DEV),
                loader.loadRobustness(SearchV3DenseAblationDataset.Split.CALIBRATION));
        assertThat(robustnessSlices.stream().flatMap(slice -> slice.queries().stream()))
                .hasSize(24)
                .allMatch(SearchV3DenseAblationDataset.Query::hasDirectSupport);

        OllamaBgeM3EmbeddingClient client = new OllamaBgeM3EmbeddingClient();
        OllamaBgeM3EmbeddingClient.ModelMetadata model = client.inspectModel();
        assertThat(model.resolvedName()).startsWith("bge-m3");
        assertThat(model.dimensions()).isEqualTo(1024);
        assertThat(model.embeddingCapable()).isTrue();

        SearchV3DenseAblationEngine engine = new SearchV3DenseAblationEngine();
        SearchV3DenseAblationEngine.ExperimentReport historicalLongForm = engine.run(
                longFormSlices,
                client,
                model,
                "PRZ-026-B3-ROBUSTNESS-HISTORICAL-LONG-FORM");
        SearchV3DenseAblationEngine.ExperimentReport robustness = engine.run(
                robustnessSlices,
                client,
                model,
                "PRZ-026-B3-ROBUSTNESS-INDEPENDENT-DEV-CAL");
        List<SearchV3DenseAblationEngine.QueryResult> cumulative = new ArrayList<>();
        cumulative.addAll(historicalLongForm.queries());
        cumulative.addAll(robustness.queries());
        RetrievalPassageRobustnessGate.RobustnessAssessment assessment =
                new RetrievalPassageRobustnessGate().assess(robustness, cumulative);

        SearchV3DenseAblationDataset.SealedManifestMetadata sealedAfter =
                loader.readSealedManifestMetadata();
        SearchV3DenseAblationDataset.RobustnessManifestMetadata robustnessAfter =
                loader.readRobustnessManifestMetadata();
        assertThat(sealedAfter).isEqualTo(sealedBefore);
        assertThat(robustnessAfter).isEqualTo(robustnessBefore);
        assertThat(robustness.sealedFinalOpened()).isFalse();
        assertThat(robustness.sealedFinalSearchExecuted()).isFalse();
        assertThat(robustness.currentFreshBaseline()).isEqualTo("NOT_RUN");
        assertThat(robustness.passageStats().crossParentPassageViolationCount()).isZero();
        assertThat(robustness.passageStats().directGoldEvidenceChildPreservationRate()).isEqualTo(1.0d);

        Path output = Path.of(System.getProperty("prizm.prz026.robustness-output", DEFAULT_OUTPUT.toString()))
                .toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        RobustnessReport report = new RobustnessReport(
                1,
                "PRZ-026-PHASE-1-RETRIEVAL-PASSAGE-ROBUSTNESS",
                inputFreezeCommit,
                executionSourceSnapshot(),
                robustnessAfter,
                historicalLongForm,
                robustness,
                assessment,
                sealedAfter,
                "NOT_RUN",
                "NOT_RUN");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n";
        Files.writeString(output, json, StandardCharsets.UTF_8);
        String reportSha256 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));

        System.out.println("PRZ026_ROBUSTNESS_REPORT=" + output);
        System.out.println("PRZ026_ROBUSTNESS_REPORT_SHA256=" + reportSha256);
        System.out.println("PRZ026_ROBUSTNESS_MANIFEST_SHA256=" + robustnessAfter.combinedSha256());
        System.out.println("PRZ026_ROBUSTNESS_B2_CANDIDATES=" + robustness.structuralCorpus().candidateCount());
        System.out.println("PRZ026_ROBUSTNESS_B3_CANDIDATES=" + robustness.passageCorpus().candidateCount());
        System.out.println("PRZ026_ROBUSTNESS_B2_TOP1=" + robustness.queryMicro().structural().top1());
        System.out.println("PRZ026_ROBUSTNESS_B3_TOP1=" + robustness.queryMicro().passage().top1());
        System.out.println("PRZ026_ROBUSTNESS_B2_MRR=" + robustness.queryMicro().structural().mrr());
        System.out.println("PRZ026_ROBUSTNESS_B3_MRR=" + robustness.queryMicro().passage().mrr());
        System.out.println("PRZ026_ROBUSTNESS_FRONTEND_STATUS=" +
                assessment.cumulativeProfession().get("FRONTEND_MOBILE").status());
        System.out.println("PRZ026_ROBUSTNESS_DECISION=" + assessment.decision());
        System.out.println("PRZ026_SEALED_FINAL_SEARCH_EXECUTED=false");
    }

    private ExecutionSourceSnapshot executionSourceSnapshot() throws Exception {
        Map<String, String> fileHashes = new LinkedHashMap<>();
        for (String sourceFile : EXECUTION_SOURCE_FILES) {
            byte[] bytes = Files.readAllBytes(Path.of(sourceFile));
            fileHashes.put(sourceFile, HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes)));
        }
        String hashInput = fileHashes.entrySet().stream()
                .map(entry -> entry.getKey() + ":" + entry.getValue())
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
        String combined = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(hashInput.getBytes(StandardCharsets.UTF_8)));
        return new ExecutionSourceSnapshot("CONTENT_ADDRESSED_WORKTREE_SNAPSHOT", Map.copyOf(fileHashes), combined);
    }

    record RobustnessReport(
            int schemaVersion,
            String phase,
            String inputFreezeCommit,
            ExecutionSourceSnapshot executionSource,
            SearchV3DenseAblationDataset.RobustnessManifestMetadata robustnessManifest,
            SearchV3DenseAblationEngine.ExperimentReport historicalLongForm,
            SearchV3DenseAblationEngine.ExperimentReport independentRobustness,
            RetrievalPassageRobustnessGate.RobustnessAssessment assessment,
            SearchV3DenseAblationDataset.SealedManifestMetadata sealedFinal,
            String parentContextStatus,
            String parentDenseStatus) {
    }

    record ExecutionSourceSnapshot(
            String kind,
            Map<String, String> fileSha256,
            String combinedSha256) {
    }
}
