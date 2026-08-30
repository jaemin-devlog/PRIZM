package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

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
    void modelAndRankingConstantsCannotDriftBetweenProfiles() {
        assertThat(OllamaBgeM3EmbeddingClient.MODEL).isEqualTo("bge-m3");
        assertThat(OllamaBgeM3EmbeddingClient.DIMENSIONS).isEqualTo(1024);
        assertThat(OllamaBgeM3EmbeddingClient.SIMILARITY).isEqualTo("COSINE");
        assertThat(SearchV3DenseAblationEngine.FIXED_PROFILE).contains("BGE_M3_DENSE");
        assertThat(SearchV3DenseAblationEngine.STRUCTURAL_PROFILE).contains("BGE_M3_DENSE");
    }
}
