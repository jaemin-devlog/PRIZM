package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SearchV3DenseAblationDatasetTest {

    private final SearchV3DenseAblationDataset loader = new SearchV3DenseAblationDataset();

    @Test
    void loadsOnlyMaterializedDevAndCalibrationWithExpectedCounts() {
        SearchV3DenseAblationDataset.DatasetSlice dev =
                loader.load(SearchV3DenseAblationDataset.Split.DEV);
        SearchV3DenseAblationDataset.DatasetSlice calibration =
                loader.load(SearchV3DenseAblationDataset.Split.CALIBRATION);

        assertThat(dev.bundles()).hasSize(3);
        assertThat(dev.activeDocumentsByVersion()).hasSize(4);
        assertThat(dev.queries()).hasSize(13);
        assertThat(dev.queries()).filteredOn(SearchV3DenseAblationDataset.Query::hasDirectSupport)
                .hasSize(8);
        assertThat(calibration.bundles()).hasSize(2);
        assertThat(calibration.activeDocumentsByVersion()).hasSize(3);
        assertThat(calibration.queries()).hasSize(8);
        assertThat(calibration.queries()).filteredOn(SearchV3DenseAblationDataset.Query::hasDirectSupport)
                .hasSize(6);
    }

    @Test
    void rejectsSealedFinalBeforeReadingItsCorpusQuestionsOrGold() {
        Path sealedFinal = SearchV3DenseAblationDataset.BENCHMARK_ROOT.resolve("sealed-final");

        assertThatThrownBy(() -> loader.load(sealedFinal, SearchV3DenseAblationDataset.Split.DEV))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEALED_FINAL_TEST access is forbidden");
    }

    @Test
    void verifiesOnlySealedManifestMetadataAndKeepsSearchFlagsFalse() {
        SearchV3DenseAblationDataset.SealedManifestMetadata metadata = loader.readSealedManifestMetadata();

        assertThat(metadata.combinedSha256())
                .isEqualTo(SearchV3DenseAblationDataset.SEALED_FINAL_SHA256);
        assertThat(metadata.opened()).isFalse();
        assertThat(metadata.searchExecuted()).isFalse();
    }

    @Test
    void goldUsesAnnotationIdsRatherThanRuntimeChunkOrParentIds() {
        SearchV3DenseAblationDataset.DatasetSlice dev =
                loader.load(SearchV3DenseAblationDataset.Split.DEV);

        assertThat(dev.units().keySet()).allMatch(id -> id.startsWith("SV3-") && id.contains("-E"));
        assertThat(dev.parents().keySet()).allMatch(id -> id.startsWith("SV3-") && id.contains("-P"));
        assertThat(dev.units().keySet()).noneMatch(id -> id.toLowerCase().contains("chunk"));
    }
}
