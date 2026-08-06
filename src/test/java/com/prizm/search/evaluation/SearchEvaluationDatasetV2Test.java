package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.search.evaluation.SearchEvaluationData.Category;
import com.prizm.search.evaluation.SearchEvaluationData.Dataset;
import com.prizm.search.evaluation.SearchEvaluationData.Question;
import com.prizm.search.evaluation.SearchEvaluationData.Split;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.service.TextChunker;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class SearchEvaluationDatasetV2Test {

    private static final Path DATASET_V2 =
            Path.of("src/test/resources/search-evaluation/v2");

    @TempDir
    Path temporaryDirectory;

    private final SearchEvaluationDatasetLoader loader =
            new SearchEvaluationDatasetLoader(new ObjectMapper());

    @Test
    void loadsVersionTwoWithRequiredScenariosAndDisjointSplits() {
        Dataset dataset = loader.load(DATASET_V2);

        assertThat(dataset.corpus().datasetId()).isEqualTo("prizm-search-evidence-synthetic-v2.2");
        assertThat(dataset.corpus().schemaVersion()).isEqualTo(2);
        assertThat(dataset.questions()).hasSize(25);
        assertThat(dataset.questions().stream().collect(Collectors.groupingBy(
                Question::split, Collectors.counting())))
                .containsEntry(Split.TUNING, 15L)
                .containsEntry(Split.TEST, 10L);
        for (Split split : Split.values()) {
            assertThat(dataset.questions().stream()
                    .filter(question -> question.split() == split)
                    .flatMap(question -> question.expectedEvidence().stream())
                    .map(SearchEvaluationData.ExpectedEvidence::relevance)
                    .collect(Collectors.toSet()))
                    .as("relevance grades in %s", split)
                    .containsExactlyInAnyOrder(0, 1, 2);
        }
        assertThat(dataset.questions().stream().map(Question::category).collect(Collectors.toSet()))
                .containsAll(EnumSet.of(
                        Category.NO_EVIDENCE,
                        Category.NEAR_TOPIC_NO_EVIDENCE,
                        Category.ABSENT_ENTITY,
                        Category.ALTERED_FACT,
                        Category.OWNER_BOUNDARY,
                        Category.VERSION_BOUNDARY,
                        Category.NO_SEARCHABLE_DOCUMENTS,
                        Category.DIRECT_EVIDENCE,
                        Category.PARAPHRASE,
                        Category.EXACT_VALUE,
                        Category.PDF_EVIDENCE,
                        Category.OVERLAP_DUPLICATE));

        assertNoCrossSplitReuse(dataset.questions(), Question::fixtureIds);
        assertNoCrossSplitReuse(dataset.questions(), question -> question.expectedEvidence().stream()
                .map(SearchEvaluationData.ExpectedEvidence::evidenceGroupId)
                .toList());
        assertNoCrossSplitReuse(dataset.questions(), question -> Set.of(question.questionGroupId()));
        assertThat(dataset.questions().stream()
                .filter(question -> question.category() == Category.PDF_EVIDENCE)
                .map(Question::goldPage))
                .containsOnly(2);
    }

    @Test
    void overlapFixturePlacesOneEvidenceAnchorInTwoCurrentChunks() {
        Dataset dataset = loader.load(DATASET_V2);
        SearchEvaluationData.FixtureDocument overlapDocument = dataset.corpus().documents().stream()
                .filter(document -> document.fixtureId().equals("tuning-overlap-boundary"))
                .findFirst()
                .orElseThrow();
        String anchor = overlapDocument.evidenceAnchors().get(0).anchorText();

        assertThat(new TextChunker(new IngestionProperties())
                .split(overlapDocument.pages().get(0).text()))
                .filteredOn(chunk -> chunk.content().contains(anchor))
                .hasSize(2);
    }

    @Test
    void reproducesPortfolioResumeTypoDuplicatePageAndNoEvidenceCasesInTuningOnly() {
        Dataset dataset = loader.load(DATASET_V2);
        Set<String> reproductionQuestionIds = Set.of(
                "v2-1-t-match-direct",
                "v2-1-t-match-typo",
                "v2-1-t-match-duplicate-page",
                "v2-1-t-notification-typo",
                "v2-1-t-no-evidence-kafka");

        assertThat(dataset.questions())
                .filteredOn(question -> reproductionQuestionIds.contains(question.questionId()))
                .hasSize(5)
                .allMatch(question -> question.split() == Split.TUNING);
        assertThat(dataset.questions())
                .filteredOn(question -> question.questionId().equals("v2-1-t-no-evidence-kafka"))
                .allMatch(Question::noEvidence);
        assertThat(dataset.questions())
                .filteredOn(question -> question.questionId().equals("v2-1-t-match-typo")
                        || question.questionId().equals("v2-1-t-notification-typo"))
                .extracting(Question::category)
                .containsOnly(Category.PARAPHRASE);

        SearchEvaluationData.FixtureDocument portfolio = dataset.corpus().documents().stream()
                .filter(document -> document.fixtureId().equals("tuning-synthetic-backend-portfolio"))
                .findFirst()
                .orElseThrow();
        String matchAnchor = portfolio.evidenceAnchors().stream()
                .filter(anchor -> anchor.fixtureEvidenceId().equals("t-fr-portfolio-match-unique"))
                .findFirst()
                .orElseThrow()
                .anchorText();
        assertThat(new TextChunker(new IngestionProperties())
                .split(portfolio.pages().get(1).text()))
                .filteredOn(chunk -> chunk.content().contains(matchAnchor))
                .as("same PDF page evidence repeated by the current 800/120 overlap")
                .hasSize(2);

        String truncatedLockAnchor = portfolio.evidenceAnchors().stream()
                .filter(anchor -> anchor.fixtureEvidenceId().equals("t-fr-portfolio-match-lock-overlap"))
                .findFirst()
                .orElseThrow()
                .anchorText();
        assertThat(new TextChunker(new IngestionProperties())
                .split(portfolio.pages().get(1).text()))
                .filteredOn(chunk -> chunk.content().contains(truncatedLockAnchor))
                .as("the overlap fragment remains direct DB-lock evidence in both chunks")
                .hasSize(2);
    }

    @Test
    void preservesTheFrozenTestQuestionsByteForByte() throws IOException, NoSuchAlgorithmException {
        String testQuestions = Files.readAllLines(
                        DATASET_V2.resolve(SearchEvaluationDatasetLoader.QUESTIONS_FILE),
                        StandardCharsets.UTF_8).stream()
                .filter(line -> line.contains("\"split\":\"TEST\""))
                .collect(Collectors.joining("\n", "", "\n"));

        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(testQuestions.getBytes(StandardCharsets.UTF_8));
        assertThat(HexFormat.of().formatHex(digest))
                .isEqualTo("6eeeffed3a93b53edbc474e8a57f2eba6b627c6f4358cbafdc7b2f0b2b29fce9");
    }

    @Test
    void rejectsFixtureDocumentUsedAcrossSplits() throws IOException {
        copyAndMutateQuestions(text -> text.replaceFirst(
                "\"questionId\":\"v2-t-direct-lumen\"([^\\r\\n]+)\"split\":\"TUNING\"",
                "\"questionId\":\"v2-t-direct-lumen\"$1\"split\":\"TEST\""));

        assertInvalidDataset("same fixture document cannot be used");
    }

    @Test
    void rejectsEvidenceGroupUsedAcrossSplits() throws IOException {
        copyAndMutateQuestions(text -> text.replaceFirst(
                "x-group-orchid-api",
                "t-group-lumen-release"));

        assertInvalidDataset("same evidenceGroup cannot be reused");
    }

    @Test
    void rejectsNormalizedQuestionUsedAcrossSplits() throws IOException {
        copyAndMutateQuestions(text -> text.replaceFirst(
                "Orchid 프로젝트에서 구현한 API 수를 직접 확인해줘\\.",
                "Lumen 주문 API를 배포한 직접 근거를 찾아줘."));

        assertInvalidDataset("Duplicate normalized query");
    }

    @Test
    void rejectsParaphraseGroupSplitAcrossTuningAndTest() throws IOException {
        copyAndMutateQuestions(text -> text.replaceFirst(
                "x-question-orchid-api",
                "t-question-lumen-release"));

        assertInvalidDataset("Paraphrase question groups cannot be split");
    }

    @Test
    void rejectsSameSourceFactDuplicatedAcrossOwnerFixtures() throws IOException {
        copyDataset(
                text -> text.replaceFirst(
                        "x-fact-other-owner-zephyr",
                        "t-fact-other-owner-terraform"),
                UnaryOperator.identity());

        assertInvalidDataset("same source fact cannot be duplicated");
    }

    @Test
    void rejectsSameSourceFactDuplicatedAcrossVersionFixtures() throws IOException {
        copyDataset(
                text -> text.replaceFirst("x-fact-past-rabbitmq", "t-fact-past-graphql"),
                UnaryOperator.identity());

        assertInvalidDataset("same source fact cannot be duplicated");
    }

    @Test
    void rejectsNoEvidenceQuestionWithPositiveRelevance() throws IOException {
        copyAndMutateQuestions(text -> text.replaceFirst(
                "\"fixtureEvidenceId\":\"t-kafka-theory-only\",\"relevance\":0",
                "\"fixtureEvidenceId\":\"t-kafka-theory-only\",\"relevance\":1"));

        assertInvalidDataset("noEvidence questions cannot contain positive evidence");
    }

    @Test
    void rejectsPdfEvidenceQuestionWithoutGoldPage() throws IOException {
        copyAndMutateQuestions(text -> text.replaceFirst(
                "\"questionId\":\"v2-t-pdf-date\"([^\\r\\n]+)\"goldPage\":2",
                "\"questionId\":\"v2-t-pdf-date\"$1\"goldPage\":null"));

        assertInvalidDataset("PDF evidence questions require a positive goldPage");
    }

    private void assertNoCrossSplitReuse(
            Iterable<Question> questions,
            java.util.function.Function<Question, ? extends Iterable<String>> values) {
        Map<String, Split> splitByValue = new HashMap<>();
        for (Question question : questions) {
            for (String value : values.apply(question)) {
                Split previous = splitByValue.putIfAbsent(value, question.split());
                assertThat(previous == null || previous == question.split()).isTrue();
            }
        }
    }

    private void copyAndMutateQuestions(UnaryOperator<String> questionMutation) throws IOException {
        copyDataset(UnaryOperator.identity(), questionMutation);
    }

    private void copyDataset(
            UnaryOperator<String> corpusMutation,
            UnaryOperator<String> questionMutation) throws IOException {
        Files.writeString(
                temporaryDirectory.resolve(SearchEvaluationDatasetLoader.CORPUS_FILE),
                corpusMutation.apply(Files.readString(
                        DATASET_V2.resolve(SearchEvaluationDatasetLoader.CORPUS_FILE),
                        StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
        Files.writeString(
                temporaryDirectory.resolve(SearchEvaluationDatasetLoader.QUESTIONS_FILE),
                questionMutation.apply(Files.readString(
                        DATASET_V2.resolve(SearchEvaluationDatasetLoader.QUESTIONS_FILE),
                        StandardCharsets.UTF_8)),
                StandardCharsets.UTF_8);
    }

    private void assertInvalidDataset(String message) {
        assertThatThrownBy(() -> loader.load(temporaryDirectory))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessageContaining(message);
    }
}
