package com.prizm.search.evaluation;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.search.evaluation.SearchEvaluationData.Corpus;
import com.prizm.search.evaluation.SearchEvaluationData.Dataset;
import com.prizm.search.evaluation.SearchEvaluationData.EvidenceAnchor;
import com.prizm.search.evaluation.SearchEvaluationData.ExpectedEvidence;
import com.prizm.search.evaluation.SearchEvaluationData.FixtureDocument;
import com.prizm.search.evaluation.SearchEvaluationData.FixturePage;
import com.prizm.search.evaluation.SearchEvaluationData.OwnerScenario;
import com.prizm.search.evaluation.SearchEvaluationData.Question;
import com.prizm.search.evaluation.SearchEvaluationData.Split;
import com.prizm.search.evaluation.SearchEvaluationData.VersionScenario;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        CorpusIndex corpusIndex = validateCorpus(corpus);
        validateQuestions(corpus, questions, corpusIndex);
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

    private CorpusIndex validateCorpus(Corpus corpus) {
        if (corpus == null || isBlank(corpus.datasetId()) || corpus.documents() == null
                || corpus.documents().isEmpty()) {
            throw new SearchEvaluationDataException("Evaluation corpus requires datasetId and documents.");
        }
        if (corpus.schemaVersion() != null
                && (corpus.schemaVersion() < 1 || corpus.schemaVersion() > 2)) {
            throw new SearchEvaluationDataException("Unsupported search evaluation dataset schemaVersion.");
        }

        Set<String> documentIds = new HashSet<>();
        Set<String> evidenceIds = new HashSet<>();
        Map<String, FixtureDocument> documentsById = new HashMap<>();
        Map<String, FixtureDocument> documentsByEvidenceId = new HashMap<>();
        Map<String, Split> sourceFactSplits = new HashMap<>();
        for (FixtureDocument document : corpus.documents()) {
            if (document == null || isBlank(document.fixtureId()) || isBlank(document.title())
                    || document.documentType() == null || document.fileType() == null) {
                throw new SearchEvaluationDataException("Every fixture document requires identifiers and types.");
            }
            if (!documentIds.add(document.fixtureId())) {
                throw new SearchEvaluationDataException("Duplicate fixture document ID is not allowed.");
            }
            documentsById.put(document.fixtureId(), document);
            if (isVersionTwo(corpus) && document.split() == null) {
                throw new SearchEvaluationDataException("Dataset v2 fixture documents require a split.");
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
                documentsByEvidenceId.put(anchor.fixtureEvidenceId(), document);
                if (isVersionTwo(corpus)) {
                    if (isBlank(anchor.sourceFactId())) {
                        throw new SearchEvaluationDataException(
                                "Dataset v2 evidence anchors require a sourceFactId.");
                    }
                    Split existingSplit = sourceFactSplits.putIfAbsent(
                            anchor.sourceFactId(), document.split());
                    if (existingSplit != null && existingSplit != document.split()) {
                        throw new SearchEvaluationDataException(
                                "The same source fact cannot be duplicated across TUNING and TEST fixtures.");
                    }
                }
            }
        }
        return new CorpusIndex(
                Map.copyOf(documentsById),
                Map.copyOf(documentsByEvidenceId),
                Set.copyOf(evidenceIds));
    }

    private void validateQuestions(Corpus corpus, List<Question> questions, CorpusIndex corpusIndex) {
        Set<String> questionIds = new HashSet<>();
        Set<String> normalizedQueries = new HashSet<>();
        Map<String, Split> positiveEvidenceSplits = new HashMap<>();
        Map<String, Split> evidenceGroupSplits = new HashMap<>();
        Map<String, Split> questionGroupSplits = new HashMap<>();
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
            if (isVersionTwo(corpus)) {
                validateVersionTwoQuestionMetadata(
                        question, corpusIndex.documentsById(), questionGroupSplits);
            }

            boolean hasPositiveEvidence = false;
            boolean hasPositivePdfEvidence = false;
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
                if (!corpusIndex.evidenceIds().contains(evidence.fixtureEvidenceId())) {
                    throw new SearchEvaluationDataException("Question references an unknown fixture evidence ID.");
                }
                FixtureDocument evidenceDocument = corpusIndex.documentsByEvidenceId()
                        .get(evidence.fixtureEvidenceId());
                if (isVersionTwo(corpus)) {
                    if (!question.fixtureIds().contains(evidenceDocument.fixtureId())) {
                        throw new SearchEvaluationDataException(
                                "Dataset v2 expected evidence must belong to a declared fixtureId.");
                    }
                    Split existingGroupSplit = evidenceGroupSplits.putIfAbsent(
                            evidence.evidenceGroupId(), question.split());
                    if (existingGroupSplit != null && existingGroupSplit != question.split()) {
                        throw new SearchEvaluationDataException(
                                "The same evidenceGroup cannot be reused across TUNING and TEST.");
                    }
                }
                if (evidence.relevance() > 0) {
                    Split existingSplit = positiveEvidenceSplits.putIfAbsent(
                            evidence.fixtureEvidenceId(), question.split());
                    if (existingSplit != null && existingSplit != question.split()) {
                        throw new SearchEvaluationDataException(
                                "Positive fixture evidence cannot be reused across TUNING and TEST.");
                    }
                    hasPositivePdfEvidence |= evidenceDocument.fileType() == DocumentFileType.PDF;
                }
                hasPositiveEvidence |= evidence.relevance() > 0;
            }
            if (question.noEvidence() && hasPositiveEvidence) {
                throw new SearchEvaluationDataException("noEvidence questions cannot contain positive evidence.");
            }
            if (!question.noEvidence() && !hasPositiveEvidence) {
                throw new SearchEvaluationDataException("Evidence-bearing questions require relevance 1 or 2.");
            }
            if (isVersionTwo(corpus)) {
                validateVersionTwoQuestionSemantics(question, hasPositivePdfEvidence, corpusIndex);
            }
        }
    }

    private void validateVersionTwoQuestionMetadata(
            Question question,
            Map<String, FixtureDocument> documentsById,
            Map<String, Split> questionGroupSplits) {
        if (question.fixtureIds() == null || question.fixtureIds().isEmpty()
                || isBlank(question.questionGroupId())
                || question.ownerScenario() == null || question.versionScenario() == null) {
            throw new SearchEvaluationDataException(
                    "Dataset v2 questions require fixtureIds, questionGroupId, ownerScenario, and versionScenario.");
        }
        if (question.expectedEvidence().isEmpty()) {
            throw new SearchEvaluationDataException(
                    "Dataset v2 questions require at least one evidenceGroup label.");
        }

        Set<String> uniqueFixtureIds = new HashSet<>();
        for (String fixtureId : question.fixtureIds()) {
            if (isBlank(fixtureId) || !uniqueFixtureIds.add(fixtureId)) {
                throw new SearchEvaluationDataException(
                        "Dataset v2 question fixtureIds must be unique and non-blank.");
            }
            FixtureDocument document = documentsById.get(fixtureId);
            if (document == null) {
                throw new SearchEvaluationDataException("Question references an unknown fixture document ID.");
            }
            if (document.split() != question.split()) {
                throw new SearchEvaluationDataException(
                        "The same fixture document cannot be used across TUNING and TEST.");
            }
        }

        Split existingQuestionGroupSplit = questionGroupSplits.putIfAbsent(
                question.questionGroupId(), question.split());
        if (existingQuestionGroupSplit != null && existingQuestionGroupSplit != question.split()) {
            throw new SearchEvaluationDataException(
                    "Paraphrase question groups cannot be split across TUNING and TEST.");
        }
    }

    private void validateVersionTwoQuestionSemantics(
            Question question,
            boolean hasPositivePdfEvidence,
            CorpusIndex corpusIndex) {
        if ((question.ownerScenario() == OwnerScenario.OTHER_OWNER_ONLY
                || question.ownerScenario() == OwnerScenario.NO_SEARCHABLE_DOCUMENTS
                || question.versionScenario() == VersionScenario.PAST_VERSION_ONLY
                || question.versionScenario() == VersionScenario.NO_ACTIVE_VERSION)
                && !question.noEvidence()) {
            throw new SearchEvaluationDataException(
                    "Owner and version boundary scenarios must be labelled noEvidence.");
        }
        if (question.ownerScenario() == OwnerScenario.NO_SEARCHABLE_DOCUMENTS
                && question.versionScenario() != VersionScenario.NO_ACTIVE_VERSION) {
            throw new SearchEvaluationDataException(
                    "NO_SEARCHABLE_DOCUMENTS requires the NO_ACTIVE_VERSION scenario.");
        }
        if (hasPositivePdfEvidence) {
            if (question.goldPage() == null || question.goldPage() < 1) {
                throw new SearchEvaluationDataException(
                        "Dataset v2 PDF evidence questions require a positive goldPage.");
            }
            boolean goldPageMatches = question.expectedEvidence().stream()
                    .filter(evidence -> evidence.relevance() > 0)
                    .map(evidence -> corpusIndex.documentsByEvidenceId().get(evidence.fixtureEvidenceId()))
                    .filter(document -> document.fileType() == DocumentFileType.PDF)
                    .anyMatch(document -> document.pages().stream()
                            .anyMatch(page -> page.pageNumber() == question.goldPage()
                                    && document.evidenceAnchors().stream()
                                            .filter(anchor -> question.expectedEvidence().stream()
                                                    .filter(evidence -> evidence.relevance() > 0)
                                                    .map(ExpectedEvidence::fixtureEvidenceId)
                                                    .anyMatch(anchor.fixtureEvidenceId()::equals))
                                            .anyMatch(anchor -> page.text().contains(anchor.anchorText()))));
            if (!goldPageMatches) {
                throw new SearchEvaluationDataException(
                        "Dataset v2 PDF goldPage must contain expected positive evidence.");
            }
        }
    }

    private boolean isVersionTwo(Corpus corpus) {
        return Integer.valueOf(2).equals(corpus.schemaVersion());
    }

    private String normalizeQuery(String query) {
        return query.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record CorpusIndex(
            Map<String, FixtureDocument> documentsById,
            Map<String, FixtureDocument> documentsByEvidenceId,
            Set<String> evidenceIds) {
    }
}
