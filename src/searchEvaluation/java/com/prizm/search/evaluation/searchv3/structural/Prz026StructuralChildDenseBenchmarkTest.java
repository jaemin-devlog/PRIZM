package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Executes only PRZ-025 DEV/CAL against A/B raw Dense and writes an ignored local report. */
class Prz026StructuralChildDenseBenchmarkTest {

    private static final Path DEFAULT_OUTPUT = Path.of(
            "local/search-v3-evaluation/prz026/structural-child-dense-v1.json");

    @Test
    void runsFixedVsStructuralOnDevAndCalibrationOnly() throws Exception {
        SearchV3DenseAblationDataset loader = new SearchV3DenseAblationDataset();
        SearchV3DenseAblationDataset.SealedManifestMetadata sealedBefore =
                loader.readSealedManifestMetadata();
        List<SearchV3DenseAblationDataset.DatasetSlice> slices = List.of(
                loader.load(SearchV3DenseAblationDataset.Split.DEV),
                loader.load(SearchV3DenseAblationDataset.Split.CALIBRATION));
        assertThat(slices).allSatisfy(slice ->
                assertThat(slice.split()).isIn(
                        SearchV3DenseAblationDataset.Split.DEV,
                        SearchV3DenseAblationDataset.Split.CALIBRATION));
        assertThat(slices.stream().flatMap(slice -> slice.queries().stream())).hasSize(21);
        assertThat(slices.stream().flatMap(slice -> slice.queries().stream())
                .filter(SearchV3DenseAblationDataset.Query::hasDirectSupport)).hasSize(14);

        OllamaBgeM3EmbeddingClient client = new OllamaBgeM3EmbeddingClient();
        OllamaBgeM3EmbeddingClient.ModelMetadata model = client.inspectModel();
        assertThat(model.resolvedName()).startsWith("bge-m3");
        assertThat(model.dimensions()).isEqualTo(1024);
        assertThat(model.embeddingCapable()).isTrue();

        SearchV3DenseAblationEngine.ExperimentReport report =
                new SearchV3DenseAblationEngine().run(slices, client, model);
        SearchV3DenseAblationDataset.SealedManifestMetadata sealedAfter =
                loader.readSealedManifestMetadata();

        assertThat(sealedAfter).isEqualTo(sealedBefore);
        assertThat(report.sealedFinalOpened()).isFalse();
        assertThat(report.sealedFinalSearchExecuted()).isFalse();
        assertThat(report.currentFreshBaseline()).isEqualTo("NOT_RUN");
        assertThat(report.fixedCorpus().embeddingCount()).isEqualTo(report.fixedCorpus().candidateCount());
        assertThat(report.structuralCorpus().embeddingCount())
                .isEqualTo(report.structuralCorpus().candidateCount());
        assertThat(report.queryMicro().fixed().directQueryCount()).isEqualTo(14);
        assertThat(report.queryMicro().structural().directQueryCount()).isEqualTo(14);

        Path output = Path.of(System.getProperty("prizm.prz026.output", DEFAULT_OUTPUT.toString()))
                .toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n";
        Files.writeString(output, json, StandardCharsets.UTF_8);
        String reportSha256 = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(json.getBytes(StandardCharsets.UTF_8)));

        System.out.println("PRZ026_REPORT=" + output);
        System.out.println("PRZ026_REPORT_SHA256=" + reportSha256);
        System.out.println("PRZ026_FIXED_CANDIDATES=" + report.fixedCorpus().candidateCount());
        System.out.println("PRZ026_STRUCTURAL_CANDIDATES=" + report.structuralCorpus().candidateCount());
        System.out.println("PRZ026_FIXED_TOP1=" + report.queryMicro().fixed().top1());
        System.out.println("PRZ026_STRUCTURAL_TOP1=" + report.queryMicro().structural().top1());
        System.out.println("PRZ026_FIXED_MRR=" + report.queryMicro().fixed().mrr());
        System.out.println("PRZ026_STRUCTURAL_MRR=" + report.queryMicro().structural().mrr());
        System.out.println("PRZ026_SEALED_FINAL_SEARCH_EXECUTED=false");
    }
}
