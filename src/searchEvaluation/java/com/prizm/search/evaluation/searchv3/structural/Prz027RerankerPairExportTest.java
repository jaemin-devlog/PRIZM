package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.BaselineBundle;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.BaselineDataset;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.DatasetRun;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.PreparedInput;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** One-shot export of the frozen B3 R0 report and Gold-free Top20 Cross Encoder pairs. */
class Prz027RerankerPairExportTest {

    private static final Path DEFAULT_INPUT =
            Path.of("local/search-v3-evaluation/prz027/reranker-input.json");
    private static final Path DEFAULT_BASELINE =
            Path.of("local/search-v3-evaluation/prz027/b3-baseline.json");
    private static final Pattern COMMIT_SHA = Pattern.compile("^[0-9a-f]{40}$");
    private static final Map<String, String> FROZEN_PRZ026_B3_HASHES = Map.of(
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralBlockParser.java",
            "974bee756bff26248690b068fe80253d362b4ae13ddac3379d541cde616dffda",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralEvidenceChildBuilder.java",
            "6ff76f49df332319fac987a59be4ead11d7ecda90b44f0d11e0cb538acd6cb83",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/RetrievalPassage.java",
            "2be2a8138133025f438d49f26a61d5edb6565e71d5a78806a5d59eea5c7e7a71",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralRetrievalPassageBuilder.java",
            "64c93a0ba50ec2785209a85abd339fa0e4d6de0dc6a99ac29dedfa3a93dc2c39");
    private static final List<String> SOURCE_SNAPSHOT = List.of(
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralBlockParser.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralEvidenceChildBuilder.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/RetrievalPassage.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralRetrievalPassageBuilder.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/SearchV3DenseAblationEngine.java");

    @Test
    void exportsFrozenB3AndGoldFreeTop20PairsFromDevCalibrationOnly() {
        String inputFreezeCommit = freezeCommit();
        assertThat(inputFreezeCommit).matches(COMMIT_SHA);
        SearchV3DenseAblationDataset loader = new SearchV3DenseAblationDataset();
        SearchV3DenseAblationDataset.SealedManifestMetadata sealedBefore =
                loader.readSealedManifestMetadata();
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
        List<DatasetRun> runs = List.of(
                run("ORIGINAL", original, engine, client, model),
                run("LONG_FORM", longForm, engine, client, model),
                run("ROBUSTNESS", robustness, engine, client, model));
        SearchV3RerankerPairArtifacts artifacts = new SearchV3RerankerPairArtifacts();
        PreparedInput prepared = artifacts.prepare(runs);
        Map<String, String> sourceHashes = sourceSnapshot();
        FROZEN_PRZ026_B3_HASHES.forEach((path, expected) -> assertThat(sourceHashes.get(path))
                .as("PRZ-026 B3 source must remain frozen: %s", path)
                .isEqualTo(expected));
        BaselineBundle baseline = new BaselineBundle(
                SearchV3RerankerPairArtifacts.SCHEMA_VERSION,
                inputFreezeCommit,
                sourceHashes,
                runs.stream().map(run -> new BaselineDataset(run.label(), run.report())).toList());

        Path input = Path.of(System.getProperty("prizm.prz027.input", DEFAULT_INPUT.toString()))
                .toAbsolutePath().normalize();
        Path baselinePath = Path.of(System.getProperty("prizm.prz027.baseline", DEFAULT_BASELINE.toString()))
                .toAbsolutePath().normalize();
        artifacts.writePrepared(input, prepared);
        artifacts.writeBaseline(baselinePath, baseline);

        SearchV3DenseAblationDataset.SealedManifestMetadata sealedAfter =
                loader.readSealedManifestMetadata();
        assertThat(sealedAfter).isEqualTo(sealedBefore);
        runs.forEach(run -> {
            assertThat(run.report().sealedFinalOpened()).isFalse();
            assertThat(run.report().sealedFinalSearchExecuted()).isFalse();
            assertThat(run.report().currentFreshBaseline()).isEqualTo("NOT_RUN");
            assertThat(run.report().passageCorpus().contaminationRate()).isZero();
            assertThat(run.report().passageCorpus().fragmentationRate()).isZero();
            assertThat(run.report().passageStats().crossParentPassageViolationCount()).isZero();
            assertThat(run.report().passageStats().directGoldEvidenceChildPreservationRate()).isEqualTo(1.0d);
        });

        int questionCount = prepared.datasets().stream().mapToInt(value -> value.questions().size()).sum();
        int pairCount = prepared.datasets().stream()
                .flatMap(value -> value.questions().stream())
                .mapToInt(value -> value.pairs().size())
                .sum();
        System.out.println("PRZ027_INPUT=" + input);
        System.out.println("PRZ027_INPUT_SHA256=" + SearchV3RerankerPairArtifacts.sha256File(input));
        System.out.println("PRZ027_BASELINE=" + baselinePath);
        System.out.println("PRZ027_BASELINE_SHA256=" + SearchV3RerankerPairArtifacts.sha256File(baselinePath));
        System.out.println("PRZ027_INPUT_DIGEST=" + prepared.inputDigest());
        System.out.println("PRZ027_QUESTION_COUNT=" + questionCount);
        System.out.println("PRZ027_PAIR_COUNT=" + pairCount);
        System.out.println("PRZ027_SEALED_FINAL_SEARCH_EXECUTED=false");
    }

    private DatasetRun run(
            String label,
            List<SearchV3DenseAblationDataset.DatasetSlice> slices,
            SearchV3DenseAblationEngine engine,
            OllamaBgeM3EmbeddingClient client,
            OllamaBgeM3EmbeddingClient.ModelMetadata model) {
        return new DatasetRun(
                label,
                slices,
                engine.run(slices, client, model, "PRZ-027-R0-B3-DENSE-FROZEN-" + label));
    }

    private String freezeCommit() {
        return System.getProperty(
                "prizm.prz027.input-freeze-commit",
                System.getenv().getOrDefault("PRIZM_PRZ027_INPUT_FREEZE_COMMIT", ""));
    }

    private Map<String, String> sourceSnapshot() {
        Map<String, String> hashes = new LinkedHashMap<>();
        SOURCE_SNAPSHOT.forEach(path -> hashes.put(path, SearchV3RerankerPairArtifacts.sha256File(Path.of(path))));
        return Map.copyOf(hashes);
    }
}
