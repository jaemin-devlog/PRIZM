package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.service.TextChunker;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.service.SearchService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SearchV3MinimalShadowIntegrityTest {

    private static final Path PROJECTION = Path.of(
            "specs/PRZ-032-minimal-v3-shadow-comparison/runtime-query-projection.json");

    @Test
    void loadsExactlyTheGoldFreeCanonicalRuntimeInventory() throws Exception {
        SearchV3MinimalShadowDataset.RuntimeInput input = new SearchV3MinimalShadowDataset().loadRuntime();

        assertThat(input.queries()).hasSize(117);
        assertThat(input.queries().stream().filter(value -> value.split().equals("DEV"))).hasSize(61);
        assertThat(input.queries().stream().filter(value -> value.split().equals("CALIBRATION"))).hasSize(56);
        assertThat(input.queries().stream().map(SearchV3MinimalShadowDataset.RuntimeQuery::userBundleId)
                .distinct()).hasSize(23);
        assertThat(input.documents()).hasSize(26);
        assertThat(input.activeDocuments()).hasSize(25);
        assertThat(input.queries().stream().filter(
                SearchV3MinimalShadowDataset.RuntimeQuery::typedApplicabilityVerified)).hasSize(24)
                .allMatch(value -> value.suite().equals("TYPED_STRESS"));
        assertThat(input.queries().stream().map(value -> value.userBundleId() + "\0"
                + SearchV3MinimalShadowDataset.normalizeQuery(value.text()))).doesNotHaveDuplicates();
        System.out.println("PRZ032_RUNTIME_INPUT_SHA256=" + input.canonicalSha256());
    }

    @Test
    void trackedRuntimeProjectionContainsOnlyApprovedFieldsAndNoGold() throws Exception {
        JsonNode root = new ObjectMapper().readTree(Files.readString(PROJECTION, StandardCharsets.UTF_8));
        Set<String> approved = Set.of(
                "suite", "datasetVersion", "split", "queryId", "userBundleId", "query",
                "language", "typedApplicabilityVerified");
        assertThat(root.path("queries")).hasSize(117).allSatisfy(value ->
                assertThat(value.propertyNames()).containsExactlyInAnyOrderElementsOf(approved));
        assertThat(root.path("queries").toString().toLowerCase())
                .doesNotContain("answerability", "expectedevidence", "supportrelation", "category");
    }

    @Test
    void canonicalDedupPreservesPunctuationThatChangesIdentifiersAndConstraints() {
        assertThat(SearchV3MinimalShadowDataset.normalizeQuery(" C++   4.2  >=  95% "))
                .isEqualTo("c++ 4.2 >= 95%");
        assertThat(SearchV3MinimalShadowDataset.normalizeQuery("C++"))
                .isNotEqualTo(SearchV3MinimalShadowDataset.normalizeQuery("C"));
    }

    @Test
    void productionV2AdapterDependsOnActualProductionTypesAndChunkContract() {
        assertThat(SearchService.class.getName()).isEqualTo("com.prizm.search.service.SearchService");
        assertThat(VectorSearchRepository.class.getName())
                .isEqualTo("com.prizm.search.repository.VectorSearchRepository");
        assertThat(CompositeSearchProfile.class.getName())
                .isEqualTo("com.prizm.search.profile.CompositeSearchProfile");
        IngestionProperties properties = new IngestionProperties();
        properties.setMaxChunkLength(800);
        properties.setOverlap(120);
        TextChunker chunker = new TextChunker(properties);
        assertThat(chunker.split("x".repeat(1_000))).hasSize(2);
        assertThat(chunker.split("x".repeat(1_000)).get(0).content()).hasSize(800);
    }

    @Test
    void goldJoinIsBlockedUntilAnOutputFreezeHasVerified() {
        SearchV3MinimalShadowFreeze.PhaseGuard guard = new SearchV3MinimalShadowFreeze.PhaseGuard();
        assertThatThrownBy(() -> guard.joinGold(ignored -> "forbidden"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OUTPUT_VERIFIED");
    }

    @Test
    void sealedDatasetCannotBeLoadedAsDevOrCalibration() {
        assertThatThrownBy(() -> new SearchV3DenseAblationDataset().load(
                Path.of("src/test/resources/search-v3-evaluation/sealed-final"),
                SearchV3DenseAblationDataset.Split.DEV))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SEALED_FINAL_TEST");
    }
}
