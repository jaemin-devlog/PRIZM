package com.prizm.search.evaluation;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.search.evaluation.SearchEvaluationData.Corpus;
import com.prizm.search.evaluation.SearchEvaluationData.Dataset;
import com.prizm.search.evaluation.SearchEvaluationData.EvidenceAnchor;
import com.prizm.search.evaluation.SearchEvaluationData.ExpectedEvidence;
import com.prizm.search.evaluation.SearchEvaluationData.FixtureDocument;
import com.prizm.search.evaluation.SearchEvaluationData.FixturePage;
import com.prizm.search.evaluation.SearchEvaluationData.Question;
import com.prizm.search.evaluation.SearchEvaluationData.Split;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** corpus.json과 questions.jsonl을 읽고 실행 전에 형식과 라벨 불변식을 검증한다. */
public class SearchEvaluationDatasetLoader {

    static final String CORPUS_FILE = "corpus.json";
    static final String QUESTIONS_FILE = "questions.jsonl";

    private final ObjectMapper objectMapper;

    public SearchEvaluationDatasetLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Dataset load(Path datasetDirectory) {
        if (datasetDirectory == null || !Files.isDirectory(datasetDirectory)) {
            throw new SearchEvaluationDataException("Search evaluation dataset directory does not exist.");
        }

        Corpus corpus = readCorpus(datasetDirectory.resolve(CORPUS_FILE));
        List<Question> questions = readQuestions(datasetDirectory.resolve(QUESTIONS_FILE));
        Set<String> evidenceIds = validateCorpus(corpus);
        validateQuestions(questions, evidenceIds);
        return new Dataset(corpus, List.copyOf(questions));
    }

    private Corpus readCorpus(Path path) {
        try {
            return objectMapper.readValue(Files.readString(path, StandardCharsets.UTF_8), Corpus.class);
        }
        catch (IOException | JacksonException exception) {
            throw new SearchEvaluationDataException("Failed to read search evaluation corpus.json.", exception);
        }
    }

    private List<Question> readQuestions(Path path) {
        final List<String> lines;
        try {
            lines = Files.readAllLines(path, StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new SearchEvaluationDataException("Failed to read search evaluation questions.jsonl.", exception);
        }

        List<Question> questions = new ArrayList<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.isBlank()) {
                continue;
            }
            try {
                questions.add(objectMapper.readValue(line, Question.class));
            }
            catch (JacksonException exception) {
                throw new SearchEvaluationDataException(
                        "Invalid questions.jsonl entry at line " + (index + 1) + ".",
                        exception);
            }
        }
        if (questions.isEmpty()) {
            throw new SearchEvaluationDataException("Search evaluation questions.jsonl must not be empty.");
        }
        return questions;
    }

    private Set<String> validateCorpus(Corpus corpus) {
        if (corpus == null || isBlank(corpus.datasetId()) || corpus.documents() == null
                || corpus.documents().isEmpty()) {
            throw new SearchEvaluationDataException("Evaluation corpus requires datasetId and documents.");
        }

        Set<String> documentIds = new HashSet<>();
        Set<String> evidenceIds = new HashSet<>();
        for (FixtureDocument document : corpus.documents()) {
            if (document == null || isBlank(document.fixtureId()) || isBlank(document.title())
                    || document.documentType() == null || document.fileType() == null) {
                throw new SearchEvaluationDataException("Every fixture document requires identifiers and types.");
            }
            if (!documentIds.add(document.fixtureId())) {
                throw new SearchEvaluationDataException("Duplicate fixture document ID is not allowed.");
            }
            if (document.pages() == null || document.pages().isEmpty()) {
                throw new SearchEvaluationDataException("Every fixture document requires at least one page.");
            }
            if (document.fileType() == DocumentFileType.TXT && document.pages().size() != 1) {
                throw new SearchEvaluationDataException("TXT evaluation fixtures require exactly one page.");
            }

            Set<Integer> pageNumbers = new HashSet<>();
            StringBuilder fullText = new StringBuilder();
            for (FixturePage page : document.pages()) {
                if (page == null || page.pageNumber() < 1 || isBlank(page.text())
                        || !pageNumbers.add(page.pageNumber())) {
                    throw new SearchEvaluationDataException("Fixture pages require unique positive numbers and text.");
                }
                fullText.append(page.text());
            }

            if (document.evidenceAnchors() == null) {
                throw new SearchEvaluationDataException("Fixture evidenceAnchors must be an array.");
            }
            for (EvidenceAnchor anchor : document.evidenceAnchors()) {
                if (anchor == null || isBlank(anchor.fixtureEvidenceId()) || isBlank(anchor.anchorText())) {
                    throw new SearchEvaluationDataException("Evidence anchors require an ID and anchor text.");
                }
                if (!evidenceIds.add(anchor.fixtureEvidenceId())) {
                    throw new SearchEvaluationDataException("Duplicate fixture evidence ID is not allowed.");
                }
                if (fullText.indexOf(anchor.anchorText()) < 0) {
                    throw new SearchEvaluationDataException("Evidence anchor text was not found in its fixture document.");
                }
            }
        }
        return Set.copyOf(evidenceIds);
    }

    private void validateQuestions(List<Question> questions, Set<String> corpusEvidenceIds) {
        Set<String> questionIds = new HashSet<>();
        Set<String> normalizedQueries = new HashSet<>();
        Map<String, Split> positiveEvidenceSplits = new HashMap<>();
        for (Question question : questions) {
            if (question == null || isBlank(question.questionId()) || isBlank(question.query())
                    || question.query().length() > 500 || question.split() == null
                    || question.category() == null) {
                throw new SearchEvaluationDataException(
                        "Every question requires a valid ID, query, split, and category.");
            }
            if (!questionIds.add(question.questionId())) {
                throw new SearchEvaluationDataException("Duplicate questionId is not allowed.");
            }
            if (!normalizedQueries.add(normalizeQuery(question.query()))) {
                throw new SearchEvaluationDataException("Duplicate normalized query is not allowed across splits.");
            }
            if (question.expectedEvidence() == null) {
                throw new SearchEvaluationDataException("expectedEvidence must be an array.");
            }

            boolean hasPositiveEvidence = false;
            Set<String> expectedIds = new HashSet<>();
            for (ExpectedEvidence evidence : question.expectedEvidence()) {
                if (evidence == null || isBlank(evidence.fixtureEvidenceId())
                        || isBlank(evidence.evidenceGroupId())
                        || evidence.relevance() < 0 || evidence.relevance() > 2) {
                    throw new SearchEvaluationDataException("Expected evidence requires relevance 0, 1, or 2.");
                }
                if (!expectedIds.add(evidence.fixtureEvidenceId())) {
                    throw new SearchEvaluationDataException("Duplicate expected evidence is not allowed per question.");
                }
                if (!corpusEvidenceIds.contains(evidence.fixtureEvidenceId())) {
                    throw new SearchEvaluationDataException("Question references an unknown fixture evidence ID.");
                }
                if (evidence.relevance() > 0) {
                    Split existingSplit = positiveEvidenceSplits.putIfAbsent(
                            evidence.fixtureEvidenceId(), question.split());
                    if (existingSplit != null && existingSplit != question.split()) {
                        throw new SearchEvaluationDataException(
                                "Positive fixture evidence cannot be reused across TUNING and TEST.");
                    }
                }
                hasPositiveEvidence |= evidence.relevance() > 0;
            }
            if (question.noEvidence() && hasPositiveEvidence) {
                throw new SearchEvaluationDataException("noEvidence questions cannot contain positive evidence.");
            }
            if (!question.noEvidence() && !hasPositiveEvidence) {
                throw new SearchEvaluationDataException("Evidence-bearing questions require relevance 1 or 2.");
            }
        }
    }

    private String normalizeQuery(String query) {
        return query.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
