package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.evaluation.searchv3.structural.Prz030SemanticEvidenceValidationCeilingBenchmarkTest.Decision;
import com.prizm.search.evaluation.searchv3.structural.Prz030SemanticEvidenceValidationCeilingBenchmarkTest.DecisionAssessment;
import com.prizm.search.evaluation.searchv3.structural.Prz030SemanticEvidenceValidationCeilingBenchmarkTest.DecisionInputs;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class Prz030SemanticEvidenceValidationCeilingBenchmarkPolicyTest {

    @Test
    void retrievalBlockerTakesPrecedenceOverCapabilityGate() {
        DecisionAssessment lowRecall = assess(0.899999d, 0, 0.50d, 20, 20, 20);
        DecisionAssessment threeMissBundles = assess(1.0d, 3, 0.50d, 20, 20, 20);

        assertThat(lowRecall.decision()).isEqualTo(Decision.RETRIEVAL_FIRST);
        assertThat(threeMissBundles.decision()).isEqualTo(Decision.RETRIEVAL_FIRST);
        assertThat(lowRecall.retrievalAugmentation()).isEqualTo("RETRIEVAL_AUGMENTATION_NEEDED");
        assertThat(lowRecall.parentDense()).isEqualTo("DEFER");
    }

    @Test
    void eachFrozenCapabilityClauseCanJustifyAValidator() {
        assertThat(assess(0.90d, 2, 0.05d, 0, 0, 0).decision())
                .isEqualTo(Decision.BUILD_SEMANTIC_VALIDATOR);
        assertThat(assess(0.90d, 2, 0.70d - 0.65d, 0, 0, 0).decision())
                .isEqualTo(Decision.BUILD_SEMANTIC_VALIDATOR);
        assertThat(assess(1.0d, 0, 0.0d, 3, 0, 0).decision())
                .isEqualTo(Decision.BUILD_SEMANTIC_VALIDATOR);
        assertThat(assess(1.0d, 0, 0.0d, 0, 2, 2).decision())
                .isEqualTo(Decision.BUILD_SEMANTIC_VALIDATOR);
    }

    @Test
    void falsePositiveRiskClauseRequiresBothQueriesAndBundles() {
        assertThat(assess(1.0d, 0, 0.0d, 0, 2, 1).decision())
                .isEqualTo(Decision.VALIDATOR_NOT_JUSTIFIED);
        assertThat(assess(1.0d, 0, 0.0d, 0, 1, 2).decision())
                .isEqualTo(Decision.VALIDATOR_NOT_JUSTIFIED);
    }

    @Test
    void officialRunIsOptInAndWritesOnlyUnderIgnoredLocalBoundary() {
        assertThat(Prz030SemanticEvidenceValidationCeilingBenchmarkTest.CODE_FREEZE_PROPERTY)
                .isEqualTo("prizm.prz030.code-freeze-commit");
        Path output = Prz030SemanticEvidenceValidationCeilingBenchmarkTest.OUTPUT.normalize();
        assertThat(output.startsWith(Path.of("local", "search-v3-evaluation", "prz030"))).isTrue();
        assertThat(output.toString().replace('\\', '/')).doesNotContain("sealed-final", "prz029");
    }

    @Test
    void freezesParserEmptySemanticCoreBeforeOfficialCandidateExecution() {
        SearchV3DenseAblationDataset datasets = new SearchV3DenseAblationDataset();
        SearchV3SemanticOracleDataset semantic = new SearchV3SemanticOracleDataset();
        List<Prz030SemanticEvidenceValidationCeilingBenchmarkTest.QueryText> queries = new ArrayList<>();
        // Offline contract audit: only queryId/query text are projected into the parser input.
        for (SearchV3DenseAblationDataset.DatasetSlice slice : List.of(
                datasets.load(SearchV3DenseAblationDataset.Split.DEV),
                datasets.load(SearchV3DenseAblationDataset.Split.CALIBRATION),
                datasets.loadLongForm(SearchV3DenseAblationDataset.Split.DEV),
                datasets.loadLongForm(SearchV3DenseAblationDataset.Split.CALIBRATION),
                datasets.loadRobustness(SearchV3DenseAblationDataset.Split.DEV),
                datasets.loadRobustness(SearchV3DenseAblationDataset.Split.CALIBRATION))) {
            slice.queries().forEach(query -> queries.add(
                    new Prz030SemanticEvidenceValidationCeilingBenchmarkTest.QueryText(
                            query.queryId(), query.text())));
        }
        for (SearchV3DenseAblationDataset.Split split : SearchV3DenseAblationDataset.Split.values()) {
            semantic.loadStressRuntime(split).questions().forEach(query -> queries.add(
                    new Prz030SemanticEvidenceValidationCeilingBenchmarkTest.QueryText(
                            query.queryId(), query.text())));
        }
        var inventory = Prz030SemanticEvidenceValidationCeilingBenchmarkTest
                .freezeQueryTrackInventory(queries);

        assertThat(inventory.orderedRows()).hasSize(93);
        assertThat(inventory.semanticCoreQueryIds()).hasSize(79);
        assertThat(inventory.typedOverlapQueryIds())
                .containsExactlyInAnyOrderElementsOf(
                        Prz030SemanticEvidenceValidationCeilingBenchmarkTest.FROZEN_TYPED_OVERLAP_QUERY_IDS);
        assertThat(inventory.canonicalSha256())
                .isEqualTo(Prz030SemanticEvidenceValidationCeilingBenchmarkTest.QUERY_TRACK_INVENTORY_SHA256);
    }

    private DecisionAssessment assess(
            double directRecall20,
            int missBundles,
            double userMacroTop1Gain,
            int recoverableBundles,
            int falsePositiveRiskQueries,
            int falsePositiveRiskBundles) {
        return Prz030SemanticEvidenceValidationCeilingBenchmarkTest.assessDecision(new DecisionInputs(
                directRecall20,
                missBundles,
                userMacroTop1Gain,
                recoverableBundles,
                falsePositiveRiskQueries,
                falsePositiveRiskBundles));
    }
}
