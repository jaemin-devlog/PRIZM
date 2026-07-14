package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.evaluation.SearchEvaluationData.Category;
import com.prizm.search.evaluation.SearchEvaluationData.Dataset;
import com.prizm.search.evaluation.SearchEvaluationData.Split;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SearchEvaluationSampleDatasetTest {

    private static final Path SAMPLE_DATASET =
            Path.of("src/test/resources/search-evaluation/sample");

    @Test
    void hasThirtyQuestionsWithReviewedCategoryAndSplitCounts() {
        Dataset dataset = new SearchEvaluationDatasetLoader(new ObjectMapper()).load(SAMPLE_DATASET);

        Map<Split, Long> splitCounts = dataset.questions().stream()
                .collect(Collectors.groupingBy(SearchEvaluationData.Question::split, Collectors.counting()));
        Map<Category, Long> categoryCounts = dataset.questions().stream()
                .collect(Collectors.groupingBy(SearchEvaluationData.Question::category, Collectors.counting()));

        assertThat(dataset.questions()).hasSize(30);
        assertThat(splitCounts).containsEntry(Split.TUNING, 20L).containsEntry(Split.TEST, 10L);
        assertThat(categoryCounts)
                .containsEntry(Category.TECHNICAL_EXPERIENCE, 8L)
                .containsEntry(Category.PROBLEM_SOLVING, 6L)
                .containsEntry(Category.COLLABORATION, 4L)
                .containsEntry(Category.EXACT_VALUE, 6L)
                .containsEntry(Category.NO_EVIDENCE, 6L);
    }

    @Test
    void bothSplitsContainEvidenceAndNoEvidenceQuestions() {
        Dataset dataset = new SearchEvaluationDatasetLoader(new ObjectMapper()).load(SAMPLE_DATASET);

        for (Split split : Split.values()) {
            assertThat(dataset.questions().stream()
                    .filter(question -> question.split() == split)
                    .map(SearchEvaluationData.Question::noEvidence))
                    .contains(true, false);
        }
    }
}
