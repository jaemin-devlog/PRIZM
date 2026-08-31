package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.search.evaluation.searchv3.typed.TypedConstraintStressDataset;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class Prz029InputContractTest {

    private static final Path SEALED_MANIFEST = Path.of(
            "src/test/resources/search-v3-evaluation/sealed-final/manifest.json");
    private static final String SEALED_COMBINED_SHA256 =
            "e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383";

    @Test
    void frozenTypedEvidenceStatesDefineSixteenFoundSixNoneAndTwoPartial() {
        TypedConstraintStressDataset loader = new TypedConstraintStressDataset();
        List<TypedConstraintStressDataset.DatasetSlice> slices = List.of(
                loader.load(TypedConstraintStressDataset.OFFICIAL_1_1_0,
                        TypedConstraintStressDataset.Split.DEV),
                loader.load(TypedConstraintStressDataset.OFFICIAL_1_1_0,
                        TypedConstraintStressDataset.Split.CALIBRATION));

        Map<String, Long> distribution = new LinkedHashMap<>();
        Map<String, Long> answerabilityDistribution = new LinkedHashMap<>();
        List<String> partialQueries = new ArrayList<>();
        List<String> oracleDifferences = new ArrayList<>();
        for (TypedConstraintStressDataset.DatasetSlice slice : slices) {
            for (TypedConstraintStressDataset.TypedQueryAnnotation annotation
                    : slice.evaluationGold().queryAnnotations().values()) {
                String state = expectedTypedState(annotation);
                distribution.merge(state, 1L, Long::sum);
                if (state.equals("PARTIAL")) {
                    partialQueries.add(annotation.queryId());
                }
                TypedConstraintStressDataset.QueryTruth truth =
                        slice.evaluationGold().queryTruth().get(annotation.queryId());
                String answerabilityState = switch (truth.answerability()) {
                    case "SUPPORTED" -> "FOUND";
                    case "PARTIALLY_SUPPORTED" -> "PARTIAL";
                    case "NOT_SUPPORTED" -> "NONE";
                    default -> throw new IllegalStateException("unexpected answerability");
                };
                answerabilityDistribution.merge(answerabilityState, 1L, Long::sum);
                if (!state.equals(answerabilityState)) {
                    oracleDifferences.add(annotation.queryId());
                }

                List<String> satisfiedUnits = annotation.expectedEvidenceStates().stream()
                        .filter(value -> value.state().equals("SATISFIED"))
                        .map(TypedConstraintStressDataset.ExpectedEvidenceState::evidenceUnitId)
                        .sorted()
                        .toList();
                List<String> directUnits = truth.expectedEvidence().stream()
                        .filter(value -> value.supportRelation().equals("DIRECT_SUPPORT"))
                        .map(TypedConstraintStressDataset.ExpectedEvidence::evidenceUnitId)
                        .sorted()
                        .toList();
                assertThat(satisfiedUnits).as(annotation.queryId()).isEqualTo(directUnits);
            }
        }

        assertThat(distribution).containsExactlyInAnyOrderEntriesOf(Map.of(
                "FOUND", 16L,
                "NONE", 6L,
                "PARTIAL", 2L));
        assertThat(partialQueries).containsExactlyInAnyOrder("SV3-U42-Q04", "SV3-U45-Q04");
        assertThat(answerabilityDistribution).containsExactlyInAnyOrderEntriesOf(Map.of(
                "FOUND", 16L,
                "NONE", 8L));
        assertThat(oracleDifferences).containsExactlyInAnyOrder("SV3-U42-Q04", "SV3-U45-Q04");
    }

    @Test
    void sealedSemanticPathsAreRejectedAndOnlyManifestMetadataIsRead() throws Exception {
        SearchV3DenseAblationDataset dense = new SearchV3DenseAblationDataset();
        assertThatThrownBy(() -> dense.load(
                SEALED_MANIFEST.getParent(), SearchV3DenseAblationDataset.Split.DEV))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEALED_FINAL_TEST access is forbidden");

        TypedConstraintStressDataset typed = new TypedConstraintStressDataset();
        assertThatThrownBy(() -> typed.load(
                SEALED_MANIFEST.getParent(),
                TypedConstraintStressDataset.Split.DEV,
                SEALED_COMBINED_SHA256))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEALED_FINAL_TEST access is forbidden");

        JsonNode manifest;
        try (InputStream input = Files.newInputStream(SEALED_MANIFEST)) {
            manifest = new ObjectMapper().readTree(input);
        }
        assertThat(manifest.path("combinedSha256").asText()).isEqualTo(SEALED_COMBINED_SHA256);
        assertThat(manifest.path("opened").asBoolean()).isFalse();
        assertThat(manifest.path("searchExecuted").asBoolean()).isFalse();
    }

    private String expectedTypedState(TypedConstraintStressDataset.TypedQueryAnnotation annotation) {
        boolean satisfied = annotation.expectedEvidenceStates().stream()
                .anyMatch(value -> value.state().equals("SATISFIED"));
        if (satisfied) return "FOUND";
        boolean contradicted = annotation.expectedEvidenceStates().stream()
                .anyMatch(value -> value.state().equals("CONTRADICTED"));
        return contradicted ? "NONE" : "PARTIAL";
    }
}
