package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.search.evaluation.SearchEvaluationData.Dataset;
import com.prizm.search.evaluation.SearchEvaluationData.Split;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SearchEvaluationDatasetSelectorTest {

    private final SearchEvaluationDatasetSelector selector = new SearchEvaluationDatasetSelector();
    private final Dataset dataset = new SearchEvaluationDatasetLoader(new ObjectMapper())
            .load(Path.of("src/test/resources/search-evaluation/v2"));
    private final Dataset frozenTestDataset = new SearchEvaluationDatasetLoader(new ObjectMapper())
            .load(Path.of("src/test/resources/search-evaluation/v2-3"));
    private final Dataset prizmCareerEvidenceDataset = new SearchEvaluationDatasetLoader(new ObjectMapper())
            .load(Path.of("src/test/resources/search-evaluation/prizm-v1"));

    @Test
    void selectsOnlyTuningQuestionsAndDocuments() {
        Dataset selected = selector.select(dataset, Split.TUNING);

        assertThat(selected.questions()).hasSize(15).allMatch(question -> question.split() == Split.TUNING);
        assertThat(selected.corpus().documents()).hasSize(8)
                .allMatch(document -> document.split() == Split.TUNING);
    }

    @Test
    void excludesASelectedSplitFixtureThatNoSelectedQuestionReferences() {
        Dataset selected = selector.select(frozenTestDataset, Split.TEST);

        assertThat(selected.questions()).hasSize(10).allMatch(question -> question.split() == Split.TEST);
        assertThat(selected.corpus().documents())
                .extracting(document -> document.fixtureId())
                .containsExactlyInAnyOrder(
                        "test-orchid-api",
                        "test-cedar-release",
                        "test-study-note",
                        "test-other-owner-only")
                .doesNotContain("test-past-version-only");
    }

    @Test
    void requiresAnExplicitKnownSplit() {
        assertThatThrownBy(() -> selector.parseRequiredSplit(null))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessageContaining("PRIZM_SEARCH_EVALUATION_SPLIT");
        assertThatThrownBy(() -> selector.parseRequiredSplit("all"))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessageContaining("TUNING or TEST");
    }

    @Test
    void permitsTestOnlyForAnExplicitlyAllowedFrozenDataset() {
        assertThat(SearchEvaluationBaselineTest.allowsFrozenTestRun(
                        dataset, Split.TEST, "true"))
                .isFalse();
        assertThat(SearchEvaluationBaselineTest.allowsFrozenTestRun(
                        frozenTestDataset, Split.TEST, null))
                .isFalse();
        assertThat(SearchEvaluationBaselineTest.allowsFrozenTestRun(
                        frozenTestDataset, Split.TUNING, "true"))
                .isFalse();
        assertThat(SearchEvaluationBaselineTest.allowsFrozenTestRun(
                        frozenTestDataset, Split.TEST, "true"))
                .isTrue();
        assertThat(SearchEvaluationBaselineTest.allowsFrozenTestRun(
                        prizmCareerEvidenceDataset, Split.TEST, null))
                .isFalse();
        assertThat(SearchEvaluationBaselineTest.allowsFrozenTestRun(
                        prizmCareerEvidenceDataset, Split.TUNING, "true"))
                .isFalse();
        assertThat(SearchEvaluationBaselineTest.allowsFrozenTestRun(
                        prizmCareerEvidenceDataset, Split.TEST, "TRUE"))
                .isFalse();
        assertThat(SearchEvaluationBaselineTest.allowsFrozenTestRun(
                        prizmCareerEvidenceDataset, Split.TEST, "true"))
                .isTrue();
    }
}
