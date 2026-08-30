package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.BaselineBundle;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.DatasetRun;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.PreparedInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.ScoreOutput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Strict import and evaluation of the one frozen PRZ-027 Cross Encoder run. */
class Prz027CrossEncoderBenchmarkTest {

    private static final Path DEFAULT_INPUT =
            Path.of("local/search-v3-evaluation/prz027/reranker-input.json");
    private static final Path DEFAULT_BASELINE =
            Path.of("local/search-v3-evaluation/prz027/b3-baseline.json");
    private static final Path DEFAULT_SCORES =
            Path.of("local/search-v3-evaluation/prz027/reranker-scores.json");
    private static final Path DEFAULT_RESULT =
            Path.of("local/search-v3-evaluation/prz027/cross-encoder-result.json");
    private static final Pattern COMMIT_SHA = Pattern.compile("^[0-9a-f]{40}$");

    @Test
    void importsExactScoresAndEvaluatesR0R1WithoutSealedFinal() throws IOException {
        String inputFreezeCommit = System.getProperty("prizm.prz027.input-freeze-commit", "");
        assertThat(inputFreezeCommit).matches(COMMIT_SHA);
        Path inputPath = propertyPath("prizm.prz027.input", DEFAULT_INPUT);
        Path baselinePath = propertyPath("prizm.prz027.baseline", DEFAULT_BASELINE);
        Path scoresPath = propertyPath("prizm.prz027.scores", DEFAULT_SCORES);
        Path resultPath = propertyPath("prizm.prz027.result", DEFAULT_RESULT);

        SearchV3RerankerPairArtifacts artifacts = new SearchV3RerankerPairArtifacts();
        PreparedInput prepared = artifacts.readPrepared(inputPath);
        BaselineBundle baseline = artifacts.readBaseline(baselinePath);
        assertThat(baseline.inputFreezeCommit()).isEqualTo(inputFreezeCommit);
        ScoreOutput scores = artifacts.readScores(scoresPath, inputPath, prepared);

        SearchV3DenseAblationDataset loader = new SearchV3DenseAblationDataset();
        SearchV3DenseAblationDataset.SealedManifestMetadata sealedBefore =
                loader.readSealedManifestMetadata();
        Map<String, List<SearchV3DenseAblationDataset.DatasetSlice>> slices = Map.of(
                "ORIGINAL", List.of(loader.load(SearchV3DenseAblationDataset.Split.DEV),
                        loader.load(SearchV3DenseAblationDataset.Split.CALIBRATION)),
                "LONG_FORM", List.of(loader.loadLongForm(SearchV3DenseAblationDataset.Split.DEV),
                        loader.loadLongForm(SearchV3DenseAblationDataset.Split.CALIBRATION)),
                "ROBUSTNESS", List.of(loader.loadRobustness(SearchV3DenseAblationDataset.Split.DEV),
                        loader.loadRobustness(SearchV3DenseAblationDataset.Split.CALIBRATION)));
        Map<String, SearchV3DenseAblationEngine.ExperimentReport> reports = baseline.datasets().stream()
                .collect(Collectors.toMap(value -> value.label(), value -> value.report()));
        List<DatasetRun> runs = List.of("ORIGINAL", "LONG_FORM", "ROBUSTNESS").stream()
                .map(label -> new DatasetRun(label, slices.get(label), reports.get(label)))
                .toList();

        SearchV3CrossEncoderEvaluator.EvaluationReport evaluation =
                new SearchV3CrossEncoderEvaluator().evaluate(prepared, baseline, scores, runs);
        SearchV3DenseAblationDataset.SealedManifestMetadata sealedAfter =
                loader.readSealedManifestMetadata();
        assertThat(sealedAfter).isEqualTo(sealedBefore);
        assertThat(evaluation.safety().all()).isTrue();
        assertThat(evaluation.candidateIdentityParity()).isTrue();
        assertThat(evaluation.provenanceParity()).isTrue();
        assertThat(evaluation.sealedFinalOpened()).isFalse();
        assertThat(evaluation.sealedFinalSearchExecuted()).isFalse();
        assertThat(evaluation.currentFreshBaseline()).isEqualTo("NOT_RUN");

        ModelExecution model = new ModelExecution(
                scores.model(), scores.modelRevision(), scores.codeRepository(), scores.codeRevision(),
                scores.license(), scores.transformersVersion(), scores.torchVersion(), scores.psutilVersion(),
                scores.pythonVersion(),
                scores.device(), scores.dtype(), scores.modelParameterCount(), scores.modelWeightBytes(),
                scores.modelCacheBytes(),
                scores.modelWeightSha256(), scores.configSha256(), scores.remoteConfigurationSha256(),
                scores.remoteModelingSha256());
        ResultArtifact result = new ResultArtifact(
                1,
                inputFreezeCommit,
                SearchV3RerankerPairArtifacts.sha256File(inputPath),
                SearchV3RerankerPairArtifacts.sha256File(baselinePath),
                SearchV3RerankerPairArtifacts.sha256File(scoresPath),
                model,
                evaluation,
                sealedAfter);
        Files.createDirectories(resultPath.getParent());
        String json = new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(result) + "\n";
        Files.writeString(resultPath, json, StandardCharsets.UTF_8);

        System.out.println("PRZ027_RESULT=" + resultPath);
        System.out.println("PRZ027_RESULT_SHA256=" + SearchV3RerankerPairArtifacts.sha256File(resultPath));
        System.out.println("PRZ027_CANDIDATE_PARITY=" + evaluation.candidateIdentityParity());
        System.out.println("PRZ027_R0_TOP1=" + evaluation.combinedQueryMicro().r0Top1());
        System.out.println("PRZ027_R1_TOP1=" + evaluation.combinedQueryMicro().r1Top1());
        System.out.println("PRZ027_R0_MRR=" + evaluation.combinedQueryMicro().r0Mrr());
        System.out.println("PRZ027_R1_MRR=" + evaluation.combinedQueryMicro().r1Mrr());
        System.out.println("PRZ027_WIN_LOSS_TIE=" + evaluation.outcomes().wins() + "/"
                + evaluation.outcomes().losses() + "/" + evaluation.outcomes().ties());
        System.out.println("PRZ027_GATE=" + evaluation.gate().decision());
        System.out.println("PRZ027_QUERY_PLANNER_ALLOWED=" + evaluation.gate().queryPlannerAllowed());
        System.out.println("PRZ027_SEALED_FINAL_SEARCH_EXECUTED=false");
    }

    private Path propertyPath(String name, Path fallback) {
        return Path.of(System.getProperty(name, fallback.toString())).toAbsolutePath().normalize();
    }

    record ModelExecution(
            String model,
            String modelRevision,
            String codeRepository,
            String codeRevision,
            String license,
            String transformersVersion,
            String torchVersion,
            String psutilVersion,
            String pythonVersion,
            String device,
            String dtype,
            long parameters,
            long modelWeightBytes,
            long modelCacheBytes,
            String modelWeightSha256,
            String configSha256,
            String remoteConfigurationSha256,
            String remoteModelingSha256) {
    }

    record ResultArtifact(
            int schemaVersion,
            String inputFreezeCommit,
            String inputSha256,
            String baselineSha256,
            String scoresSha256,
            ModelExecution model,
            SearchV3CrossEncoderEvaluator.EvaluationReport evaluation,
            SearchV3DenseAblationDataset.SealedManifestMetadata sealedFinal) {
    }
}
