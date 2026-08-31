package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.RecordComponent;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SearchV3SemanticOracleDatasetTest {

    private final SearchV3SemanticOracleDataset loader = new SearchV3SemanticOracleDataset();

    @Test
    void loadsFrozenStressRuntimeWithoutGoldFieldsOrCopiedDocuments() {
        SearchV3SemanticOracleDataset.ManifestMetadata manifest =
                loader.readStressRuntimeManifestMetadata(true);
        SearchV3SemanticOracleDataset.RuntimeSlice dev =
                loader.loadStressRuntime(SearchV3DenseAblationDataset.Split.DEV);
        SearchV3SemanticOracleDataset.RuntimeSlice calibration =
                loader.loadStressRuntime(SearchV3DenseAblationDataset.Split.CALIBRATION);

        assertThat(manifest.combinedSha256())
                .isEqualTo("4e6c6f719f32e11b9039a2f6679c91ff19f1b130675a8afe6d20e024d3748907");
        assertThat(dev.questions()).hasSize(12);
        assertThat(calibration.questions()).hasSize(12);
        assertThat(dev.bundles()).hasSize(3);
        assertThat(calibration.bundles()).hasSize(3);
        assertThat(dev.activeDocumentsByVersion()).hasSize(3);
        assertThat(calibration.activeDocumentsByVersion()).hasSize(3);
        assertThat(List.of(SearchV3SemanticOracleDataset.RuntimeQuestion.class.getRecordComponents()).stream()
                .map(RecordComponent::getName))
                .containsExactly("queryId", "userBundleId", "text", "language")
                .noneMatch(name -> name.toLowerCase().contains("gold")
                        || name.toLowerCase().contains("answer")
                        || name.toLowerCase().contains("relation")
                        || name.toLowerCase().contains("categor"));
    }

    @Test
    void validatesEveryFrozenPayloadAndSourceGroundedStressGold() {
        SearchV3CandidateFreeze.VerifiedCandidates verified = verifiedStressFreeze();
        SearchV3SemanticOracleDataset.StressGoldSlice dev =
                loader.loadStressGold(SearchV3DenseAblationDataset.Split.DEV, verified);
        SearchV3SemanticOracleDataset.StressGoldSlice calibration =
                loader.loadStressGold(SearchV3DenseAblationDataset.Split.CALIBRATION, verified);

        List<SearchV3SemanticOracleDataset.StressGoldQuery> queries =
                java.util.stream.Stream.concat(dev.questions().stream(), calibration.questions().stream())
                        .toList();
        List<SearchV3SemanticOracleDataset.ExpectedRelation> relations = queries.stream()
                .flatMap(value -> value.expectedRelations().stream())
                .toList();
        Map<String, Long> answers = queries.stream().collect(Collectors.groupingBy(
                SearchV3SemanticOracleDataset.StressGoldQuery::answerability,
                Collectors.counting()));
        Map<String, Long> relationCounts = relations.stream().collect(Collectors.groupingBy(
                SearchV3SemanticOracleDataset.ExpectedRelation::relation,
                Collectors.counting()));

        assertThat(queries).hasSize(24);
        assertThat(dev.units()).hasSize(12);
        assertThat(calibration.units()).hasSize(12);
        assertThat(answers).containsExactlyInAnyOrderEntriesOf(Map.of(
                "SUPPORTED", 8L,
                "PARTIALLY_SUPPORTED", 8L,
                "NOT_SUPPORTED", 8L));
        assertThat(relationCounts).containsExactlyInAnyOrderEntriesOf(Map.of(
                "DIRECT_SUPPORT", 8L,
                "RELATED", 4L,
                "INSUFFICIENT", 4L,
                "CONTRADICTS", 8L));
    }

    @Test
    void stressGoldRequiresVerifiedStressCandidateFreeze() {
        assertThatThrownBy(() -> loader.loadStressGold(
                SearchV3DenseAblationDataset.Split.DEV, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("verifiedCandidates");

        SearchV3CandidateFreeze.FreezeInput wrongDataset = freezeInput("OTHER-DATASET");
        SearchV3CandidateFreeze.VerifiedCandidates verifiedWrong = SearchV3CandidateFreeze.verify(
                SearchV3CandidateFreeze.freeze(wrongDataset));
        assertThatThrownBy(() -> loader.loadStressGold(
                SearchV3DenseAblationDataset.Split.DEV, verifiedWrong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("verified semantic candidate freeze");
    }

    @Test
    void loadsOnlyOriginalLongFormAndRobustnessDevCalibrationRuntimeCorpora() {
        List<SearchV3SemanticOracleDataset.RuntimeSlice> slices = List.of(
                loader.loadOriginalRuntime(SearchV3DenseAblationDataset.Split.DEV),
                loader.loadOriginalRuntime(SearchV3DenseAblationDataset.Split.CALIBRATION),
                loader.loadLongFormRuntime(SearchV3DenseAblationDataset.Split.DEV),
                loader.loadLongFormRuntime(SearchV3DenseAblationDataset.Split.CALIBRATION),
                loader.loadRobustnessRuntime(SearchV3DenseAblationDataset.Split.DEV),
                loader.loadRobustnessRuntime(SearchV3DenseAblationDataset.Split.CALIBRATION));

        assertThat(slices).allSatisfy(slice -> {
            assertThat(slice.split())
                    .isIn(SearchV3DenseAblationDataset.Split.DEV,
                            SearchV3DenseAblationDataset.Split.CALIBRATION);
            assertThat(slice.bundles()).isNotEmpty();
            assertThat(slice.activeDocumentsByVersion()).isNotEmpty();
            assertThat(slice.questions()).isEmpty();
            assertThat(slice.activeDocumentsByVersion().values())
                    .allSatisfy(document -> assertThat(document.structuralDocument().page()).isNull());
        });
    }

    private SearchV3CandidateFreeze.VerifiedCandidates verifiedStressFreeze() {
        return SearchV3CandidateFreeze.verify(SearchV3CandidateFreeze.freeze(
                freezeInput(SearchV3SemanticOracleDataset.STRESS_VERSION)));
    }

    private SearchV3CandidateFreeze.FreezeInput freezeInput(String datasetVersion) {
        List<SearchV3CandidateFreeze.QueryProjection> queries = new java.util.ArrayList<>();
        for (SearchV3DenseAblationDataset.Split split : SearchV3DenseAblationDataset.Split.values()) {
            for (SearchV3SemanticOracleDataset.RuntimeQuestion runtimeQuestion
                    : loader.loadStressRuntime(split).questions()) {
                String source = "source-grounded " + runtimeQuestion.queryId();
                String hash = SearchV3CandidateFreeze.sha256(source);
                String suffix = runtimeQuestion.queryId();
                SearchV3CandidateFreeze.EvidenceChildProjection child =
                        new SearchV3CandidateFreeze.EvidenceChildProjection(
                                suffix + "-E1", suffix + "-D1", suffix + "-V1", null, 0,
                                source.codePointCount(0, source.length()), source, hash);
                SearchV3CandidateFreeze.CandidateProjection candidate =
                        new SearchV3CandidateFreeze.CandidateProjection(
                                1, suffix + "-P1", 0.5d, runtimeQuestion.userBundleId(),
                                suffix + "-D1", suffix + "-V1", suffix + "-PARENT",
                                source, source, hash, hash, List.of(child));
                queries.add(new SearchV3CandidateFreeze.QueryProjection(
                        runtimeQuestion.queryId(), runtimeQuestion.userBundleId(), split.manifestName(),
                        SearchV3CandidateFreeze.EvaluationTrack.SEMANTIC, List.of(candidate)));
            }
        }
        return new SearchV3CandidateFreeze.FreezeInput(
                SearchV3CandidateFreeze.SCHEMA_VERSION,
                SearchV3SemanticOracleDataset.STRESS_SUITE,
                datasetVersion,
                SearchV3SemanticOracleDataset.STRESS_RUNTIME_SHA256,
                SearchV3CandidateFreeze.EvaluationTrack.SEMANTIC,
                List.copyOf(queries));
    }
}
