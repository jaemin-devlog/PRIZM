package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.evaluation.searchv3.typed.TypedConstraintStressDataset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SearchV3EvidenceSelectionAblationEngineTest {

    private final SearchV3DenseAblationDataset loader = new SearchV3DenseAblationDataset();
    private final SearchV3DenseAblationEngine denseEngine = new SearchV3DenseAblationEngine();
    private final SearchV3EvidenceSelectionAblationEngine engine =
            new SearchV3EvidenceSelectionAblationEngine();

    @Test
    void nonTypedSuitePreservesFullCandidatesAndSelectedEvidenceExactly() {
        List<SearchV3DenseAblationDataset.DatasetSlice> slices = List.of(
                loader.load(SearchV3DenseAblationDataset.Split.DEV),
                loader.load(SearchV3DenseAblationDataset.Split.CALIBRATION));
        SearchV3EvidenceSelectionAblationEngine.ExperimentReport report =
                engine.evaluate("ORIGINAL_UNIT", syntheticDenseRun(slices), List.of());

        assertThat(report.aggregate().queryCount()).isEqualTo(21);
        assertThat(report.aggregate().typedQueryCount()).isZero();
        assertThat(report.aggregate().semanticQueryCount()).isEqualTo(21);
        assertThat(report.aggregate().semanticExactParityCount()).isEqualTo(21);
        assertThat(report.aggregate().semanticExactParityRate()).isEqualTo(1.0d);
        assertThat(report.aggregate().directRank1LossCount()).isZero();
        assertThat(report.slices()).flatExtracting(value -> value.queries())
                .allSatisfy(query -> assertThat(query.e1EvidenceChildIds())
                        .isEqualTo(query.e0EvidenceChildIds()));
    }

    @Test
    void frozenTypedStressProducesExactStateAndWinningTierChildSelection() throws Exception {
        List<SearchV3DenseAblationDataset.DatasetSlice> denseSlices = List.of(
                loader.loadTypedStressOfficial(SearchV3DenseAblationDataset.Split.DEV),
                loader.loadTypedStressOfficial(SearchV3DenseAblationDataset.Split.CALIBRATION));
        TypedConstraintStressDataset strictLoader = new TypedConstraintStressDataset();
        List<TypedConstraintStressDataset.DatasetSlice> strictSlices = List.of(
                strictLoader.load(TypedConstraintStressDataset.OFFICIAL_1_1_0,
                        TypedConstraintStressDataset.Split.DEV),
                strictLoader.load(TypedConstraintStressDataset.OFFICIAL_1_1_0,
                        TypedConstraintStressDataset.Split.CALIBRATION));

        SearchV3EvidenceSelectionAblationEngine.ExperimentReport report =
                engine.evaluate("TYPED_UNIT", syntheticDenseRun(denseSlices), strictSlices);

        assertThat(report.aggregate().typedQueryCount()).isEqualTo(24);
        assertThat(report.aggregate().typedStateCorrectCount()).isEqualTo(24);
        assertThat(report.aggregate().typedStateAccuracy()).isEqualTo(1.0d);
        assertThat(report.aggregate().typedStateMacroF1()).isEqualTo(1.0d);
        assertThat(report.aggregate().typedStateConfusion()).containsOnlyKeys("FOUND", "NONE", "PARTIAL");
        assertThat(report.aggregate().typedStateConfusion().get("FOUND").get("FOUND")).isEqualTo(16L);
        assertThat(report.aggregate().typedStateConfusion().get("NONE").get("NONE")).isEqualTo(6L);
        assertThat(report.aggregate().typedStateConfusion().get("PARTIAL").get("PARTIAL")).isEqualTo(2L);
        List<String> incorrect = report.slices().stream().flatMap(value -> value.queries().stream())
                .filter(value -> !value.evidenceSelectionCorrect())
                .map(value -> value.queryId() + ":" + value.e1EvidenceChildIds())
                .toList();
        assertThat(report.aggregate().correctEvidenceSelectionCount())
                .withFailMessage("incorrect selections: %s", incorrect)
                .isEqualTo(24);
        assertThat(report.aggregate().correctEvidenceSelectionRate()).isEqualTo(1.0d);
        assertThat(report.aggregate().supportContradictedSelectedCount()).isZero();
        assertThat(report.aggregate().unknownFallbackQueryCount()).isEqualTo(2);
        assertThat(report.aggregate().duplicateSelectedCount()).isZero();
        assertThat(report.aggregate().crossParentMergeViolationCount()).isZero();
        assertThat(report.aggregate().provenanceAccuracy()).isEqualTo(1.0d);
        assertThat(report.aggregate().e0CandidateRecall20Count()).isEqualTo(16);
        assertThat(report.aggregate().e1CandidateRecall20Count()).isEqualTo(16);
        String serialized = new ObjectMapper().writeValueAsString(report);
        assertThat(serialized).contains("validationTrace", "e1SelectedEvidence", "cosineScore");
    }

    private SearchV3DenseAblationEngine.PassageDenseRun syntheticDenseRun(
            List<SearchV3DenseAblationDataset.DatasetSlice> datasets) {
        List<SearchV3DenseAblationEngine.PassageDenseSliceRun> slices = new ArrayList<>();
        for (SearchV3DenseAblationDataset.DatasetSlice dataset : datasets) {
            SearchV3DenseAblationEngine.CandidateBuild structural =
                    denseEngine.buildStructuralCandidates(dataset);
            SearchV3DenseAblationEngine.PassageCandidateBuild build =
                    denseEngine.buildPassageCandidates(dataset, structural);
            Map<String, RetrievalPassage> passages = build.passages().stream()
                    .collect(Collectors.toMap(
                            RetrievalPassage::passageId,
                            value -> value,
                            (left, right) -> {
                                throw new IllegalStateException("duplicate passage");
                            },
                            LinkedHashMap::new));
            Map<String, String> ownerByVersion = dataset.activeDocumentsByVersion().values().stream()
                    .collect(Collectors.toMap(
                            SearchV3DenseAblationDataset.SourceDocument::versionId,
                            SearchV3DenseAblationDataset.SourceDocument::userBundleId));
            Map<String, String> professionByOwner = dataset.bundles().stream()
                    .collect(Collectors.toMap(
                            SearchV3DenseAblationDataset.UserBundle::userBundleId,
                            SearchV3DenseAblationDataset.UserBundle::professionGroup));
            List<SearchV3DenseAblationEngine.PassageDenseQueryRanking> rankings = new ArrayList<>();
            for (SearchV3DenseAblationDataset.Query query : dataset.queries()) {
                List<RetrievalPassage> ownerPassages = build.passages().stream()
                        .filter(value -> query.userBundleId().equals(ownerByVersion.get(value.versionId())))
                        .sorted(Comparator.comparing(RetrievalPassage::passageId))
                        .toList();
                List<SearchV3DenseAblationEngine.RankedCandidate> candidates = new ArrayList<>();
                for (int index = 0; index < ownerPassages.size(); index++) {
                    RetrievalPassage passage = ownerPassages.get(index);
                    Set<String> coveredUnits = coveredUnits(passage, dataset.units());
                    Set<String> coveredGroups = coveredUnits.stream()
                            .map(id -> dataset.units().get(id).groupId())
                            .collect(Collectors.toSet());
                    Set<String> coveredParents = coveredUnits.stream()
                            .map(id -> dataset.units().get(id).parentId())
                            .collect(Collectors.toSet());
                    candidates.add(new SearchV3DenseAblationEngine.RankedCandidate(
                            index + 1,
                            passage.passageId(),
                            1.0d - index * 0.01d,
                            passage.documentId(),
                            passage.versionId(),
                            "RETRIEVAL_PASSAGE",
                            passage.passageSourceText(),
                            "",
                            passage.retrievalText(),
                            passage.parentAnnotationCandidateId(),
                            passage.passageSourceText().codePointCount(0, passage.passageSourceText().length()),
                            passage.evidenceChildIds(),
                            passage.contextSourceBlockIds(),
                            coveredUnits.stream().sorted().toList(),
                            coveredGroups.stream().sorted().toList(),
                            coveredParents.stream().sorted().toList()));
                }
                rankings.add(new SearchV3DenseAblationEngine.PassageDenseQueryRanking(
                        query,
                        professionByOwner.get(query.userBundleId()),
                        0.0d,
                        0.0d,
                        List.copyOf(candidates)));
            }
            SearchV3DenseAblationEngine.ProfileLatency latency =
                    new SearchV3DenseAblationEngine.ProfileLatency(
                            SearchV3DenseAblationEngine.PASSAGE_PROFILE,
                            0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d);
            slices.add(new SearchV3DenseAblationEngine.PassageDenseSliceRun(
                    dataset,
                    Map.copyOf(passages),
                    build.candidateBuild().corpusStats(),
                    latency,
                    List.copyOf(rankings)));
        }
        return new SearchV3DenseAblationEngine.PassageDenseRun(
                1,
                "PRZ029_SYNTHETIC_ORDER_NO_BGE",
                datasets.get(0).datasetVersion(),
                new OllamaBgeM3EmbeddingClient.ModelMetadata(
                        "bge-m3:latest", "unit-digest", 1024, true, "NO_NETWORK"),
                List.copyOf(slices));
    }

    private Set<String> coveredUnits(
            RetrievalPassage passage,
            Map<String, SearchV3DenseAblationDataset.GoldUnit> units) {
        return units.values().stream()
                .filter(unit -> unit.documentId().equals(passage.documentId()))
                .filter(unit -> unit.versionId().equals(passage.versionId()))
                .filter(unit -> unit.sourceSpans().stream().allMatch(span ->
                        passage.evidenceChildren().stream().anyMatch(child -> {
                            SourceProvenance source = child.provenance();
                            return Objects.equals(source.page(), span.page())
                                    && source.codePointStart() <= span.codePointStart()
                                    && source.codePointEnd() >= span.codePointEnd();
                        })))
                .map(SearchV3DenseAblationDataset.GoldUnit::evidenceUnitId)
                .collect(Collectors.toSet());
    }
}
