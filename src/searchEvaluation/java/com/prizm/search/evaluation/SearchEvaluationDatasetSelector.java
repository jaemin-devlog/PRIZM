package com.prizm.search.evaluation;

import com.prizm.search.evaluation.SearchEvaluationData.Corpus;
import com.prizm.search.evaluation.SearchEvaluationData.Dataset;
import com.prizm.search.evaluation.SearchEvaluationData.FixtureDocument;
import com.prizm.search.evaluation.SearchEvaluationData.Question;
import com.prizm.search.evaluation.SearchEvaluationData.Split;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/** Dataset v2에서 승인된 split의 질문과 문서만 평가 실행에 전달한다. */
public class SearchEvaluationDatasetSelector {

    public Split parseRequiredSplit(String value) {
        if (value == null || value.isBlank()) {
            throw new SearchEvaluationDataException(
                    "Dataset v2 evaluation requires PRIZM_SEARCH_EVALUATION_SPLIT.");
        }
        try {
            return Split.valueOf(value.strip().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException exception) {
            throw new SearchEvaluationDataException(
                    "Evaluation split must be TUNING or TEST.", exception);
        }
    }

    public Dataset select(Dataset dataset, Split split) {
        List<Question> questions = dataset.questions().stream()
                .filter(question -> question.split() == split)
                .toList();
        List<FixtureDocument> documents = dataset.corpus().documents().stream()
                .filter(document -> document.split() == split)
                .toList();
        if (questions.isEmpty() || documents.isEmpty()) {
            throw new SearchEvaluationDataException("Selected evaluation split must contain questions and documents.");
        }

        Set<String> selectedFixtureIds = documents.stream()
                .map(FixtureDocument::fixtureId)
                .collect(Collectors.toSet());
        Set<String> referencedFixtureIds = questions.stream()
                .flatMap(question -> question.fixtureIds().stream())
                .collect(Collectors.toSet());
        if (!selectedFixtureIds.containsAll(referencedFixtureIds)) {
            throw new SearchEvaluationDataException(
                    "Selected evaluation split references a document outside the split.");
        }

        Corpus selectedCorpus = new Corpus(
                dataset.corpus().datasetId(),
                dataset.corpus().schemaVersion(),
                documents);
        return new Dataset(selectedCorpus, questions);
    }
}
