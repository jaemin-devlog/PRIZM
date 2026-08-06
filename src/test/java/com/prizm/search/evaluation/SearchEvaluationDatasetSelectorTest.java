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

    @Test
    void selectsOnlyTuningQuestionsAndDocuments() {
        Dataset selected = selector.select(dataset, Split.TUNING);

        assertThat(selected.questions()).hasSize(15).allMatch(question -> question.split() == Split.TUNING);
        assertThat(selected.corpus().documents()).hasSize(8)
                .allMatch(document -> document.split() == Split.TUNING);
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
}
