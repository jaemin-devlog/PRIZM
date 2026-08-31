package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SearchV3DenseAblationDatasetTest {

    @TempDir
    Path temporaryDirectory;

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
        assertThat(metadata.verifiedFileCount()).isPositive();
    }

    @Test
    void loadsSeparateLongFormDevAndCalibrationWithoutReplacingOriginalSeed() {
        SearchV3DenseAblationDataset.DatasetSlice dev =
                loader.loadLongForm(SearchV3DenseAblationDataset.Split.DEV);
        SearchV3DenseAblationDataset.DatasetSlice calibration =
                loader.loadLongForm(SearchV3DenseAblationDataset.Split.CALIBRATION);

        assertThat(dev.datasetVersion()).isEqualTo(SearchV3DenseAblationDataset.LONG_FORM_DATASET_VERSION);
        assertThat(calibration.datasetVersion()).isEqualTo(SearchV3DenseAblationDataset.LONG_FORM_DATASET_VERSION);
        assertThat(dev.bundles()).hasSize(3);
        assertThat(calibration.bundles()).hasSize(3);
        assertThat(dev.activeDocumentsByVersion()).hasSize(3);
        assertThat(calibration.activeDocumentsByVersion()).hasSize(3);
        assertThat(dev.queries()).hasSize(12);
        assertThat(calibration.queries()).hasSize(12);
        assertThat(dev.bundles()).allSatisfy(bundle -> assertThat(bundle.activeDocuments())
                .allSatisfy(document -> assertThat(document.structuralDocument().sourceText()
                        .codePointCount(0, document.structuralDocument().sourceText().length()))
                        .isGreaterThanOrEqualTo(1_500)));
        assertThat(calibration.bundles()).allSatisfy(bundle -> assertThat(bundle.activeDocuments())
                .allSatisfy(document -> assertThat(document.structuralDocument().sourceText()
                        .codePointCount(0, document.structuralDocument().sourceText().length()))
                        .isGreaterThanOrEqualTo(1_500)));
    }

    @Test
    void longFormManifestIsFrozenDevCalOnlyAndAllowsEvaluationWithoutClaimingRunState() {
        SearchV3DenseAblationDataset.LongFormManifestMetadata metadata =
                loader.readLongFormManifestMetadata();

        assertThat(metadata.datasetVersion())
                .isEqualTo(SearchV3DenseAblationDataset.LONG_FORM_DATASET_VERSION);
        assertThat(metadata.previousVersion())
                .isEqualTo(SearchV3DenseAblationDataset.ORIGINAL_DATASET_VERSION);
        assertThat(metadata.documentCount()).isEqualTo(6);
        assertThat(metadata.queryCount()).isEqualTo(24);
        assertThat(metadata.executionPolicy()).isEqualTo("DEV_CAL_EVALUATION_ALLOWED");
    }

    @Test
    void loadsIndependentRobustnessDevCalWithFrozenB3Policy() {
        SearchV3DenseAblationDataset.DatasetSlice dev =
                loader.loadRobustness(SearchV3DenseAblationDataset.Split.DEV);
        SearchV3DenseAblationDataset.DatasetSlice calibration =
                loader.loadRobustness(SearchV3DenseAblationDataset.Split.CALIBRATION);
        SearchV3DenseAblationDataset.RobustnessManifestMetadata metadata =
                loader.readRobustnessManifestMetadata();

        assertThat(List.of(dev, calibration)).allSatisfy(slice -> {
            assertThat(slice.datasetVersion())
                    .isEqualTo(SearchV3DenseAblationDataset.ROBUSTNESS_DATASET_VERSION);
            assertThat(slice.bundles()).hasSize(3);
            assertThat(slice.activeDocumentsByVersion()).hasSize(3);
            assertThat(slice.queries()).hasSize(12);
            assertThat(slice.queries()).allMatch(SearchV3DenseAblationDataset.Query::hasDirectSupport);
            assertThat(slice.bundles()).allSatisfy(bundle -> assertThat(bundle.activeDocuments())
                    .allSatisfy(document -> assertThat(document.structuralDocument().sourceText()
                            .codePointCount(0, document.structuralDocument().sourceText().length()))
                            .isGreaterThanOrEqualTo(1_200)));
        });
        assertThat(metadata.userBundleCount()).isEqualTo(6);
        assertThat(metadata.documentCount()).isEqualTo(6);
        assertThat(metadata.queryCount()).isEqualTo(24);
        assertThat(metadata.directQueryCount()).isEqualTo(24);
        assertThat(metadata.previousVersion())
                .isEqualTo(SearchV3DenseAblationDataset.LONG_FORM_DATASET_VERSION);
        assertThat(metadata.executionPolicy()).isEqualTo("DEV_CAL_EVALUATION_ALLOWED");
        assertThat(metadata.b3PolicyRevision())
                .isEqualTo("01d9ae2f90eff691d96041579e42a02aa04a3486");
        assertThat(metadata.combinedSha256()).isEqualTo(SearchV3DenseAblationDataset.ROBUSTNESS_SHA256);
        assertThat(metadata.verifiedFileCount()).isEqualTo(16);
    }

    @Test
    void loadsFrozenTypedStressThroughTheSameB3CorpusBoundary() {
        SearchV3DenseAblationDataset.DatasetSlice dev =
                loader.loadTypedStress(SearchV3DenseAblationDataset.Split.DEV);
        SearchV3DenseAblationDataset.DatasetSlice calibration =
                loader.loadTypedStress(SearchV3DenseAblationDataset.Split.CALIBRATION);

        assertThat(List.of(dev, calibration)).allSatisfy(slice -> {
            assertThat(slice.datasetVersion())
                    .isEqualTo(SearchV3DenseAblationDataset.TYPED_STRESS_DATASET_VERSION);
            assertThat(slice.bundles()).hasSize(3);
            assertThat(slice.activeDocumentsByVersion()).hasSize(3);
            assertThat(slice.queries()).hasSize(12);
            assertThat(slice.activeDocumentsByVersion().keySet())
                    .noneMatch(id -> id.toLowerCase().contains("chunk"));
        });
        assertThat(dev.manifestCombinedSha256())
                .isEqualTo("35c6e84b85302aad5f1499bc5f8a96fdeeb3a635a3d2da3595f4473654e17350");
        assertThat(calibration.manifestCombinedSha256())
                .isEqualTo("b754d92e49246aec955c3bef252eeb09a6978272b7b7ba869059bf5a536e606e");
    }

    @Test
    void rejectsMutatedRobustnessFixtureThroughFrozenManifestHash() throws Exception {
        Path copiedRoot = temporaryDirectory.resolve("robustness");
        try (var paths = Files.walk(SearchV3DenseAblationDataset.ROBUSTNESS_BENCHMARK_ROOT)) {
            for (Path source : paths.toList()) {
                Path target = copiedRoot.resolve(
                        SearchV3DenseAblationDataset.ROBUSTNESS_BENCHMARK_ROOT.relativize(source));
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                }
                else {
                    Files.copy(source, target);
                }
            }
        }
        Path document = copiedRoot.resolve(
                "dev/documents/sv3-rb-u201-mobile-field-portfolio-v01.txt");
        Files.writeString(document, Files.readString(document) + "mutated\n");

        assertThatThrownBy(() -> loader.readRobustnessManifestMetadata(
                        copiedRoot,
                        SearchV3DenseAblationDataset.ROBUSTNESS_SHA256))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("file hash/size mismatch");
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
