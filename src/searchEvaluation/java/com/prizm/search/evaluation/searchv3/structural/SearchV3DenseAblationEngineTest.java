package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SearchV3DenseAblationEngineTest {

    private final SearchV3DenseAblationDataset loader = new SearchV3DenseAblationDataset();
    private final SearchV3DenseAblationEngine engine = new SearchV3DenseAblationEngine();

    @Test
    void fixedAndStructuralUseExactlyTheSameActiveCorpusVersions() {
        SearchV3DenseAblationDataset.DatasetSlice dev =
                loader.load(SearchV3DenseAblationDataset.Split.DEV);

        SearchV3DenseAblationEngine.CandidateBuild fixed = engine.buildFixedCandidates(dev);
        SearchV3DenseAblationEngine.CandidateBuild structural = engine.buildStructuralCandidates(dev);
        Set<String> active = dev.activeDocumentsByVersion().keySet();

        assertThat(fixed.candidates().stream()
                .map(SearchV3DenseAblationEngine.CandidateSpec::versionId)
                .collect(Collectors.toSet())).isEqualTo(active);
        assertThat(structural.candidates().stream()
                .map(SearchV3DenseAblationEngine.CandidateSpec::versionId)
                .collect(Collectors.toSet())).isEqualTo(active);
        assertThat(fixed.slice().queries()).containsExactlyElementsOf(structural.slice().queries());
    }

    @Test
    void fixedBaselineCallsProductionEightHundredCharacterChunkingContract() {
        SearchV3DenseAblationDataset.DatasetSlice calibration =
                loader.load(SearchV3DenseAblationDataset.Split.CALIBRATION);

        SearchV3DenseAblationEngine.CandidateBuild fixed = engine.buildFixedCandidates(calibration);

        assertThat(fixed.profile()).isEqualTo(SearchV3DenseAblationEngine.FIXED_PROFILE);
        assertThat(fixed.candidates()).hasSize(calibration.activeDocumentsByVersion().size());
        assertThat(fixed.candidates()).allSatisfy(candidate -> {
            String original = calibration.activeDocumentsByVersion().get(candidate.versionId())
                    .structuralDocument().sourceText().strip();
            assertThat(candidate.sourceText()).isEqualTo(original);
            assertThat(candidate.retrievalText()).isEqualTo(candidate.sourceText());
            assertThat(candidate.ranges()).hasSize(1);
        });
    }

    @Test
    void fixedSourceMappingDoesNotAssumeChunkNumberEqualsRawWindowNumber() {
        String source = "A".repeat(40) + " ".repeat(1_700) + "B".repeat(40);

        List<SearchV3DenseAblationEngine.MappedTextChunk> mapped = engine.mappedProductionChunks(source);

        assertThat(mapped).hasSize(2);
        assertThat(mapped).extracting(value -> value.chunk().chunkNo()).containsExactly(1, 2);
        assertThat(mapped.get(1).exactStart()).isGreaterThan(1_700);
        assertThat(source.substring(mapped.get(1).exactStart(), mapped.get(1).exactEnd()))
                .isEqualTo(mapped.get(1).chunk().content());
    }

    @Test
    void structuralConstructionReducesCrossParentContaminationWithoutFragmentingSeedGold() {
        SearchV3DenseAblationDataset.DatasetSlice dev =
                loader.load(SearchV3DenseAblationDataset.Split.DEV);

        SearchV3DenseAblationEngine.CandidateBuild fixed = engine.buildFixedCandidates(dev);
        SearchV3DenseAblationEngine.CandidateBuild structural = engine.buildStructuralCandidates(dev);

        assertThat(fixed.corpusStats().fragmentedGoldUnitCount()).isZero();
        assertThat(structural.corpusStats().fragmentedGoldUnitCount()).isZero();
        assertThat(structural.corpusStats().contaminationRate())
                .isLessThan(fixed.corpusStats().contaminationRate());
        assertThat(structural.candidates()).hasSizeGreaterThan(fixed.candidates().size());
    }

    @Test
    void structuralV2ExcludesHeadingCandidatesAndTracksTheirContextBoundaryRole() {
        SearchV3DenseAblationDataset.DatasetSlice dev =
                loader.load(SearchV3DenseAblationDataset.Split.DEV);

        SearchV3DenseAblationEngine.CandidateBuild structural = engine.buildStructuralCandidates(dev);

        assertThat(structural.candidates())
                .noneMatch(candidate -> StructuralBlockType.HEADING.name().equals(candidate.sourceBlockType()));
        assertThat(structural.corpusStats().headingOnlyCandidateCount()).isZero();
        assertThat(structural.corpusStats().contextOnlyHeadingCount()).isPositive();
        assertThat(structural.candidates()).allSatisfy(candidate -> {
            if (!"TABLE_ROW".equals(candidate.sourceBlockType()) || candidate.contextBlockIds().isEmpty()) {
                assertThat(candidate.retrievalText()).isEqualTo(candidate.sourceText());
            }
        });
    }

    @Test
    void longFormRemovesFixedDocumentCeilingAndKeepsStructuralParentsSeparated() {
        SearchV3DenseAblationDataset.DatasetSlice dev =
                loader.loadLongForm(SearchV3DenseAblationDataset.Split.DEV);

        SearchV3DenseAblationEngine.CandidateBuild fixed = engine.buildFixedCandidates(dev);
        SearchV3DenseAblationEngine.CandidateBuild structural = engine.buildStructuralCandidates(dev);

        assertThat(dev.parents().values())
                .anySatisfy(parent -> assertThat(
                        parent.sourceSpan().codePointEnd() - parent.sourceSpan().codePointStart())
                        .isGreaterThan(800));
        assertThat(fixed.candidates().size()).isGreaterThan(dev.activeDocumentsByVersion().size());
        assertThat(fixed.corpusStats().contaminatedCandidateCount()).isPositive();
        assertThat(fixed.corpusStats().goldParentCountPerCandidateDistribution())
                .anySatisfy((parentCount, candidates) -> {
                    assertThat(Integer.parseInt(parentCount)).isGreaterThan(1);
                    assertThat(candidates).isPositive();
                });
        assertThat(structural.corpusStats().contaminatedCandidateCount()).isZero();
        assertThat(structural.corpusStats().fragmentedGoldUnitCount()).isZero();
    }

    @Test
    void modelAndRankingConstantsCannotDriftBetweenProfiles() {
        assertThat(OllamaBgeM3EmbeddingClient.MODEL).isEqualTo("bge-m3");
        assertThat(OllamaBgeM3EmbeddingClient.DIMENSIONS).isEqualTo(1024);
        assertThat(OllamaBgeM3EmbeddingClient.SIMILARITY).isEqualTo("COSINE");
        assertThat(SearchV3DenseAblationEngine.FIXED_PROFILE).contains("BGE_M3_DENSE");
        assertThat(SearchV3DenseAblationEngine.STRUCTURAL_PROFILE).contains("BGE_M3_DENSE");
    }

    @Test
    void recallRequirementsHonorExplicitGroupsAndAllVersusAnyExpressions() {
        SearchV3DenseAblationDataset.GoldUnit unitA = goldUnit("U-A", "G-A");
        SearchV3DenseAblationDataset.GoldUnit unitB = goldUnit("U-B", "G-B");
        SearchV3DenseAblationDataset.DatasetSlice slice = new SearchV3DenseAblationDataset.DatasetSlice(
                "test", SearchV3DenseAblationDataset.Split.DEV, "hash", List.of(), List.of(), Map.of(),
                Map.of(unitA.evidenceUnitId(), unitA, unitB.evidenceUnitId(), unitB), Map.of(), Map.of());
        SearchV3DenseAblationDataset.AspectRequirement bothGroups =
                new SearchV3DenseAblationDataset.AspectRequirement(
                        "A", true, 2, List.of("G-A"), List.of(
                                new SearchV3DenseAblationDataset.ExpectedEvidence("U-A", "DIRECT_SUPPORT"),
                                new SearchV3DenseAblationDataset.ExpectedEvidence("U-B", "DIRECT_SUPPORT")));
        SearchV3DenseAblationDataset.Query groupQuery = query(
                new SearchV3DenseAblationDataset.AspectExpression("ALL", List.of("A"), 1),
                List.of(bothGroups));

        assertThat(engine.requirementsMet(groupQuery, Set.of("U-A"), slice)).isFalse();
        assertThat(engine.requirementsMet(groupQuery, Set.of("U-A", "U-B"), slice)).isTrue();

        SearchV3DenseAblationDataset.AspectRequirement aspectA = singleAspect("A", "U-A", "G-A");
        SearchV3DenseAblationDataset.AspectRequirement aspectB = singleAspect("B", "U-B", "G-B");
        assertThat(engine.requirementsMet(
                query(new SearchV3DenseAblationDataset.AspectExpression("ANY", List.of("A", "B"), 1),
                        List.of(aspectA, aspectB)),
                Set.of("U-A"), slice)).isTrue();
        assertThat(engine.requirementsMet(
                query(new SearchV3DenseAblationDataset.AspectExpression("ALL", List.of("A", "B"), 2),
                        List.of(aspectA, aspectB)),
                Set.of("U-A"), slice)).isFalse();
    }

    @Test
    void combinedDuplicateRatioKeepsZeroDuplicateSplitDenominator() {
        SearchV3DenseAblationEngine.ProfileCorpusStats combined = engine.combineCorpusStats(
                "profile", List.of(corpusStats(0, 10), corpusStats(1, 2)));

        assertThat(combined.duplicateGroupMappingCount()).isEqualTo(1);
        assertThat(combined.goldGroupMappingCount()).isEqualTo(12);
        assertThat(combined.duplicateRatio()).isEqualTo(1.0d / 12.0d);
    }

    @Test
    void userMacroKeepsQueryCountsSeparateFromMacroGroupDenominator() {
        Map<Integer, Double> perfect = Map.of(5, 1.0d, 10, 1.0d, 20, 1.0d, 50, 1.0d);
        SearchV3DenseAblationEngine.AggregateMetrics combined = engine.averageAggregates(List.of(
                new SearchV3DenseAblationEngine.AggregateMetrics(
                        2, 1, 2, 1.0d, 1.0d, perfect, perfect, perfect, perfect),
                new SearchV3DenseAblationEngine.AggregateMetrics(
                        3, 0, 3, 0.5d, 0.75d, perfect, perfect, perfect, perfect)));

        assertThat(combined.directQueryCount()).isEqualTo(5);
        assertThat(combined.diagnosticOnlyQueryCount()).isEqualTo(1);
        assertThat(combined.aggregationUnitCount()).isEqualTo(2);
        assertThat(combined.top1()).isEqualTo(0.75d);
    }

    private SearchV3DenseAblationEngine.ProfileCorpusStats corpusStats(long duplicates, long mappings) {
        return new SearchV3DenseAblationEngine.ProfileCorpusStats(
                "profile", 1, 1, 10, 10.0d, 10, 0, 0, 0.0d, 0, 0.0d,
                duplicates, mappings, mappings == 0 ? 0.0d : (double) duplicates / mappings,
                0, 0, 0, 0, Map.of(), Map.of(), 0.0d);
    }

    private SearchV3DenseAblationDataset.GoldUnit goldUnit(String unitId, String groupId) {
        return new SearchV3DenseAblationDataset.GoldUnit(
                unitId, "USER", "PARENT", groupId, "DOC", "VERSION", "FACT-" + unitId, List.of());
    }

    private SearchV3DenseAblationDataset.AspectRequirement singleAspect(
            String aspectId, String unitId, String groupId) {
        return new SearchV3DenseAblationDataset.AspectRequirement(
                aspectId, true, 1, List.of(groupId), List.of(
                        new SearchV3DenseAblationDataset.ExpectedEvidence(unitId, "DIRECT_SUPPORT")));
    }

    private SearchV3DenseAblationDataset.Query query(
            SearchV3DenseAblationDataset.AspectExpression expression,
            List<SearchV3DenseAblationDataset.AspectRequirement> aspects) {
        List<SearchV3DenseAblationDataset.ExpectedEvidence> expected = aspects.stream()
                .flatMap(aspect -> aspect.expectedEvidence().stream())
                .toList();
        return new SearchV3DenseAblationDataset.Query(
                "QUERY", "USER", SearchV3DenseAblationDataset.Split.DEV, "query", "SUPPORTED", "EN",
                List.of("semantic_paraphrase"), expression, aspects, expected);
    }
}
