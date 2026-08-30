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
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Executes Original Seed and the separately frozen long-form DEV/CAL against A/B raw Dense. */
class Prz026StructuralChildDenseBenchmarkTest {

    private static final Path DEFAULT_OUTPUT = Path.of(
            "local/search-v3-evaluation/prz026/structural-child-dense-v2-adjustment.json");
    private static final String ADJUSTMENT_START_COMMIT =
            "a9d093dd48e99a8d19675b3a8caa09c794d2888b";
    private static final List<String> EXECUTION_SOURCE_FILES = List.of(
            "scripts/evaluation/search-v3/materialize-prz026-devcal.mjs",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralBlockParser.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralEvidenceChildBuilder.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/SearchV3DenseAblationDataset.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/SearchV3DenseAblationEngine.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/OllamaBgeM3EmbeddingClient.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/Prz026StructuralChildDenseBenchmarkTest.java");

    @Test
    void runsFixedVsStructuralV2OnOriginalAndLongFormDevCalibrationOnly() throws Exception {
        SearchV3DenseAblationDataset loader = new SearchV3DenseAblationDataset();
        SearchV3DenseAblationDataset.SealedManifestMetadata sealedBefore =
                loader.readSealedManifestMetadata();
        SearchV3DenseAblationDataset.LongFormManifestMetadata longFormBefore =
                loader.readLongFormManifestMetadata();
        List<SearchV3DenseAblationDataset.DatasetSlice> originalSlices = List.of(
                loader.load(SearchV3DenseAblationDataset.Split.DEV),
                loader.load(SearchV3DenseAblationDataset.Split.CALIBRATION));
        List<SearchV3DenseAblationDataset.DatasetSlice> longFormSlices = List.of(
                loader.loadLongForm(SearchV3DenseAblationDataset.Split.DEV),
                loader.loadLongForm(SearchV3DenseAblationDataset.Split.CALIBRATION));
        assertThat(originalSlices).allSatisfy(slice ->
                assertThat(slice.split()).isIn(
                        SearchV3DenseAblationDataset.Split.DEV,
                        SearchV3DenseAblationDataset.Split.CALIBRATION));
        assertThat(longFormSlices).allSatisfy(slice ->
                assertThat(slice.split()).isIn(
                        SearchV3DenseAblationDataset.Split.DEV,
                        SearchV3DenseAblationDataset.Split.CALIBRATION));
        assertThat(originalSlices.stream().flatMap(slice -> slice.queries().stream())).hasSize(21);
        assertThat(originalSlices.stream().flatMap(slice -> slice.queries().stream())
                .filter(SearchV3DenseAblationDataset.Query::hasDirectSupport)).hasSize(14);
        assertThat(longFormSlices.stream().flatMap(slice -> slice.queries().stream())).hasSize(24);
        assertThat(longFormSlices.stream().flatMap(slice -> slice.queries().stream())
                .filter(SearchV3DenseAblationDataset.Query::hasDirectSupport)).hasSize(15);

        OllamaBgeM3EmbeddingClient client = new OllamaBgeM3EmbeddingClient();
        OllamaBgeM3EmbeddingClient.ModelMetadata model = client.inspectModel();
        assertThat(model.resolvedName()).startsWith("bge-m3");
        assertThat(model.dimensions()).isEqualTo(1024);
        assertThat(model.embeddingCapable()).isTrue();

        SearchV3DenseAblationEngine engine = new SearchV3DenseAblationEngine();
        SearchV3DenseAblationEngine.ExperimentReport original = engine.run(
                originalSlices,
                client,
                model,
                "PRZ-026-PHASE-1-ADJUSTMENT-ORIGINAL-SEED");
        SearchV3DenseAblationEngine.ExperimentReport longForm = engine.run(
                longFormSlices,
                client,
                model,
                "PRZ-026-PHASE-1-ADJUSTMENT-LONG-FORM");
        SearchV3DenseAblationDataset.SealedManifestMetadata sealedAfter =
                loader.readSealedManifestMetadata();
        SearchV3DenseAblationDataset.LongFormManifestMetadata longFormAfter =
                loader.readLongFormManifestMetadata();

        assertThat(sealedAfter).isEqualTo(sealedBefore);
        assertThat(longFormAfter).isEqualTo(longFormBefore);
        assertThat(List.of(original, longForm)).allSatisfy(report -> {
            assertThat(report.sealedFinalOpened()).isFalse();
            assertThat(report.sealedFinalSearchExecuted()).isFalse();
            assertThat(report.currentFreshBaseline()).isEqualTo("NOT_RUN");
            assertThat(report.fixedCorpus().embeddingCount()).isEqualTo(report.fixedCorpus().candidateCount());
            assertThat(report.structuralCorpus().embeddingCount())
                    .isEqualTo(report.structuralCorpus().candidateCount());
            assertThat(report.structuralCorpus().headingOnlyCandidateCount()).isZero();
            assertThat(report.structuralHeadingOnlyRank1Count()).isZero();
        });
        assertThat(original.queryMicro().fixed().directQueryCount()).isEqualTo(14);
        assertThat(original.queryMicro().structural().directQueryCount()).isEqualTo(14);
        assertThat(longForm.queryMicro().fixed().directQueryCount()).isEqualTo(15);
        assertThat(longForm.queryMicro().structural().directQueryCount()).isEqualTo(15);
        assertThat(original.queries())
                .filteredOn(result -> List.of(
                                "SV3-U01-Q04", "SV3-U04-Q01", "SV3-U04-Q03", "SV3-U02-Q04")
                        .contains(result.queryId()))
                .allSatisfy(result -> assertThat(result.structural().rawDenseRanking().get(0).sourceBlockType())
                        .isNotEqualTo(StructuralBlockType.HEADING.name()));

        Path output = Path.of(System.getProperty("prizm.prz026.output", DEFAULT_OUTPUT.toString()))
                .toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        ObjectMapper mapper = new ObjectMapper();
        AdjustmentReport report = new AdjustmentReport(
                2,
                "PRZ-026-PHASE-1-ADJUSTMENT",
                "NEEDS_ADJUSTMENT",
                "STANDALONE_HEADING",
                "NEEDS_ADJUSTMENT",
                executionSourceSnapshot(),
                original,
                longForm,
                sealedAfter,
                longFormAfter,
                "BLOCKED_FOR_LATER_LAYOUT_PHASE",
                "EVIDENCE_PARENT_AND_HEADING_CONTEXT_NOT_RUN_TABLE_HEADER_EXCEPTION_ACTIVE");
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n";
        Files.writeString(output, json, StandardCharsets.UTF_8);
        String reportSha256 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));

        System.out.println("PRZ026_REPORT=" + output);
        System.out.println("PRZ026_REPORT_SHA256=" + reportSha256);
        System.out.println("PRZ026_ORIGINAL_FIXED_CANDIDATES=" + original.fixedCorpus().candidateCount());
        System.out.println("PRZ026_ORIGINAL_STRUCTURAL_CANDIDATES=" + original.structuralCorpus().candidateCount());
        System.out.println("PRZ026_ORIGINAL_FIXED_TOP1=" + original.queryMicro().fixed().top1());
        System.out.println("PRZ026_ORIGINAL_STRUCTURAL_TOP1=" + original.queryMicro().structural().top1());
        System.out.println("PRZ026_LONG_FIXED_CANDIDATES=" + longForm.fixedCorpus().candidateCount());
        System.out.println("PRZ026_LONG_STRUCTURAL_CANDIDATES=" + longForm.structuralCorpus().candidateCount());
        System.out.println("PRZ026_LONG_FIXED_TOP1=" + longForm.queryMicro().fixed().top1());
        System.out.println("PRZ026_LONG_STRUCTURAL_TOP1=" + longForm.queryMicro().structural().top1());
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
        return new ExecutionSourceSnapshot(
                ADJUSTMENT_START_COMMIT,
                "CONTENT_ADDRESSED_WORKTREE_SNAPSHOT",
                Map.copyOf(fileHashes),
                combined);
    }

    record AdjustmentReport(
            int schemaVersion,
            String phase,
            String previousDecision,
            String previousCause,
            String finalDecision,
            ExecutionSourceSnapshot executionSource,
            SearchV3DenseAblationEngine.ExperimentReport originalSeed,
            SearchV3DenseAblationEngine.ExperimentReport longFormExpansion,
            SearchV3DenseAblationDataset.SealedManifestMetadata sealedFinal,
            SearchV3DenseAblationDataset.LongFormManifestMetadata longFormManifest,
            String pdfStatus,
            String parentContextStatus) {
    }

    record ExecutionSourceSnapshot(
            String adjustmentStartCommit,
            String kind,
            Map<String, String> fileSha256,
            String combinedSha256) {
    }
}
