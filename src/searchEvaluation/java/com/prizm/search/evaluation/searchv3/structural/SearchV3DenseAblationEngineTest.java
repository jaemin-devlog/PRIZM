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
    void fixedStructuralAndPassageUseExactlyTheSameActiveCorpusVersions() {
        SearchV3DenseAblationDataset.DatasetSlice dev =
                loader.load(SearchV3DenseAblationDataset.Split.DEV);

        SearchV3DenseAblationEngine.CandidateBuild fixed = engine.buildFixedCandidates(dev);
        SearchV3DenseAblationEngine.CandidateBuild structural = engine.buildStructuralCandidates(dev);
        SearchV3DenseAblationEngine.PassageCandidateBuild passage =
                engine.buildPassageCandidates(dev, structural);
        Set<String> active = dev.activeDocumentsByVersion().keySet();

        assertThat(fixed.candidates().stream()
                .map(SearchV3DenseAblationEngine.CandidateSpec::versionId)
                .collect(Collectors.toSet())).isEqualTo(active);
        assertThat(structural.candidates().stream()
                .map(SearchV3DenseAblationEngine.CandidateSpec::versionId)
                .collect(Collectors.toSet())).isEqualTo(active);
        assertThat(passage.candidateBuild().candidates().stream()
                .map(SearchV3DenseAblationEngine.CandidateSpec::versionId)
                .collect(Collectors.toSet())).isEqualTo(active);
        assertThat(fixed.slice().queries()).containsExactlyElementsOf(structural.slice().queries());
        assertThat(fixed.slice().queries()).containsExactlyElementsOf(passage.candidateBuild().slice().queries());
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
        SearchV3DenseAblationEngine.PassageCandidateBuild passage =
                engine.buildPassageCandidates(dev, structural);

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
        assertThat(passage.candidateBuild().corpusStats().contaminatedCandidateCount()).isZero();
        assertThat(passage.candidateBuild().corpusStats().fragmentedGoldUnitCount()).isZero();
        assertThat(passage.candidateBuild().candidates()).hasSizeLessThan(structural.candidates().size());
        assertThat(passage.passageStats().crossParentPassageViolationCount()).isZero();
        assertThat(passage.passageStats().directGoldEvidenceChildPreservationRate()).isEqualTo(1.0d);
        assertThat(passage.passageStats().maximumPassageCodePointLength())
                .isLessThanOrEqualTo(StructuralRetrievalPassageBuilder.DEFAULT_ABSOLUTE_MAX_CODE_POINTS);
    }

    @Test
    void passageCandidatesRetainEveryAtomicEvidenceChildExactlyOnce() {
        SearchV3DenseAblationDataset.DatasetSlice dev =
                loader.loadLongForm(SearchV3DenseAblationDataset.Split.DEV);
        SearchV3DenseAblationEngine.CandidateBuild structural = engine.buildStructuralCandidates(dev);

        SearchV3DenseAblationEngine.PassageCandidateBuild passage =
                engine.buildPassageCandidates(dev, structural);

        List<String> atomicIds = structural.candidates().stream()
                .flatMap(candidate -> candidate.evidenceChildren().stream())
                .map(SearchV3DenseAblationEngine.EvidenceChildRange::evidenceChildId)
                .toList();
        List<String> passageIds = passage.candidateBuild().candidates().stream()
                .flatMap(candidate -> candidate.evidenceChildren().stream())
                .map(SearchV3DenseAblationEngine.EvidenceChildRange::evidenceChildId)
                .toList();
        assertThat(passageIds).containsExactlyElementsOf(atomicIds).doesNotHaveDuplicates();
        assertThat(passage.candidateBuild().candidates()).allSatisfy(candidate -> {
            assertThat(candidate.sourceBlockType()).isEqualTo("RETRIEVAL_PASSAGE");
            assertThat(candidate.parentAnnotationCandidateId()).isNotBlank();
            assertThat(candidate.evidenceChildren()).isNotEmpty();
        });
    }

    @Test
    void parentContextChangesOnlyRetrievalTextAcrossEveryFrozenDevCalibrationSuite() {
        List<SearchV3DenseAblationDataset.DatasetSlice> slices = List.of(
                loader.load(SearchV3DenseAblationDataset.Split.DEV),
                loader.load(SearchV3DenseAblationDataset.Split.CALIBRATION),
                loader.loadLongForm(SearchV3DenseAblationDataset.Split.DEV),
                loader.loadLongForm(SearchV3DenseAblationDataset.Split.CALIBRATION),
                loader.loadRobustness(SearchV3DenseAblationDataset.Split.DEV),
                loader.loadRobustness(SearchV3DenseAblationDataset.Split.CALIBRATION));

        for (SearchV3DenseAblationDataset.DatasetSlice slice : slices) {
            SearchV3DenseAblationEngine.CandidateBuild structural = engine.buildStructuralCandidates(slice);
            SearchV3DenseAblationEngine.PassageCandidateBuild passage =
                    engine.buildPassageCandidates(slice, structural);
            SearchV3DenseAblationEngine.ContextCandidateBuild context =
                    engine.buildParentContextCandidates(slice, passage);

            assertThat(context.candidateBuild().candidates())
                    .hasSameSizeAs(passage.candidateBuild().candidates());
            for (int index = 0; index < passage.candidateBuild().candidates().size(); index++) {
                SearchV3DenseAblationEngine.CandidateSpec b3 =
                        passage.candidateBuild().candidates().get(index);
                SearchV3DenseAblationEngine.CandidateSpec c1 =
                        context.candidateBuild().candidates().get(index);
                assertThat(c1.candidateId()).isEqualTo(b3.candidateId());
                assertThat(c1.sourceText()).isEqualTo(b3.sourceText());
                assertThat(c1.ranges()).isEqualTo(b3.ranges());
                assertThat(c1.sourceBlockIds()).isEqualTo(b3.sourceBlockIds());
                assertThat(c1.contextBlockIds()).isEqualTo(b3.contextBlockIds());
                assertThat(c1.evidenceChildren()).isEqualTo(b3.evidenceChildren());
                assertThat(c1.retrievalText()).isEqualTo(c1.contextText().isBlank()
                        ? b3.retrievalText()
                        : c1.contextText() + "\n" + b3.retrievalText());
            }
            assertThat(context.contextStats().sourceParityViolationCount()).isZero();
            assertThat(context.contextStats().evidenceChildParityViolationCount()).isZero();
            assertThat(context.contextStats().crossParentContextViolationCount()).isZero();
        }
    }

    @Test
    void contextOnlyFalseHitUsesSourceMappedGoldAndReportsOnlyContextPromotedNoise() {
        SearchV3DenseAblationDataset.Query query = query(
                new SearchV3DenseAblationDataset.AspectExpression("ALL", List.of("A"), 1),
                List.of(singleAspect("A", "U-A", "G-A")));
        SearchV3DenseAblationEngine.RankedCandidate directB3 = ranked(
                1, "DIRECT", "", List.of("U-A"), List.of("E-DIRECT"));
        SearchV3DenseAblationEngine.RankedCandidate noiseB3 = ranked(
                2, "NOISE", "", List.of(), List.of("E-NOISE"));
        SearchV3DenseAblationEngine.RankedCandidate noiseC1 = ranked(
                1, "NOISE", "Relevant heading", List.of(), List.of("E-NOISE"));
        SearchV3DenseAblationEngine.RankedCandidate directC1 = ranked(
                2, "DIRECT", "Other heading", List.of("U-A"), List.of("E-DIRECT"));

        List<SearchV3DenseAblationEngine.ContextOnlyFalseHit> falseHits =
                engine.contextOnlyFalseHits(query, List.of(directB3, noiseB3), List.of(noiseC1, directC1));

        assertThat(falseHits).singleElement().satisfies(finding -> {
            assertThat(finding.candidateId()).isEqualTo("NOISE");
            assertThat(finding.passageRank()).isEqualTo(2);
            assertThat(finding.contextRank()).isEqualTo(1);
            assertThat(finding.contextText()).isEqualTo("Relevant heading");
            assertThat(finding.evidenceChildIds()).containsExactly("E-NOISE");
        });
    }

    @Test
    void modelAndRankingConstantsCannotDriftBetweenProfiles() {
        assertThat(OllamaBgeM3EmbeddingClient.MODEL).isEqualTo("bge-m3");
        assertThat(OllamaBgeM3EmbeddingClient.DIMENSIONS).isEqualTo(1024);
        assertThat(OllamaBgeM3EmbeddingClient.SIMILARITY).isEqualTo("COSINE");
        assertThat(SearchV3DenseAblationEngine.FIXED_PROFILE).contains("BGE_M3_DENSE");
        assertThat(SearchV3DenseAblationEngine.STRUCTURAL_PROFILE).contains("BGE_M3_DENSE");
        assertThat(SearchV3DenseAblationEngine.PASSAGE_PROFILE).contains("BGE_M3_DENSE");
        assertThat(SearchV3DenseAblationEngine.PARENT_CONTEXT_PROFILE).contains("BGE_M3_DENSE");
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

    private SearchV3DenseAblationEngine.RankedCandidate ranked(
            int rank,
            String candidateId,
            String contextText,
            List<String> coveredUnitIds,
            List<String> evidenceChildIds) {
        return new SearchV3DenseAblationEngine.RankedCandidate(
                rank,
                candidateId,
                1.0d / rank,
                "DOC",
                "VERSION",
                "RETRIEVAL_PASSAGE",
                "source",
                contextText,
                contextText.isBlank() ? "source" : contextText + "\nsource",
                "PARENT",
                6,
                evidenceChildIds,
                contextText.isBlank() ? List.of() : List.of("HEADING"),
                coveredUnitIds,
                coveredUnitIds.isEmpty() ? List.of() : List.of("G-A"),
                coveredUnitIds.isEmpty() ? List.of() : List.of("PARENT"));
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
