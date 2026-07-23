package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class SearchEvaluationDatasetLoaderTest {

    @TempDir
    Path temporaryDirectory;

    private final SearchEvaluationDatasetLoader loader =
            new SearchEvaluationDatasetLoader(new ObjectMapper());

    @Test
    void parsesValidJsonlDataset() throws IOException {
        writeCorpus();
        writeQuestions(validQuestion("q-1"));

        SearchEvaluationData.Dataset dataset = loader.load(temporaryDirectory);

        assertThat(dataset.corpus().datasetId()).isEqualTo("synthetic-test");
        assertThat(dataset.questions()).hasSize(1);
        assertThat(dataset.questions().get(0).expectedEvidence().get(0).relevance()).isEqualTo(2);
    }

    @Test
    void rejectsRelevanceOutsideZeroToTwo() throws IOException {
        writeCorpus();
        writeQuestions(validQuestion("q-1").replace("\"relevance\":2", "\"relevance\":3"));

        assertThatThrownBy(() -> loader.load(temporaryDirectory))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessageContaining("relevance 0, 1, or 2");
    }

    @Test
    void rejectsNoEvidenceQuestionWithPositiveEvidence() throws IOException {
        writeCorpus();
        writeQuestions(validQuestion("q-1").replace("\"noEvidence\":false", "\"noEvidence\":true"));

        assertThatThrownBy(() -> loader.load(temporaryDirectory))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessageContaining("cannot contain positive evidence");
    }

    @Test
    void rejectsDuplicateQuestionIds() throws IOException {
        writeCorpus();
        writeQuestions(validQuestion("q-1"), validQuestion("q-1"));

        assertThatThrownBy(() -> loader.load(temporaryDirectory))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessageContaining("Duplicate questionId");
    }

    @Test
    void rejectsUnknownFixtureEvidenceId() throws IOException {
        writeCorpus();
        writeQuestions(validQuestion("q-1").replace("spring-evidence", "missing-evidence"));

        assertThatThrownBy(() -> loader.load(temporaryDirectory))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessageContaining("unknown fixture evidence ID");
    }

    @Test
    void rejectsMissingOrInvalidSplit() throws IOException {
        writeCorpus();
        writeQuestions(validQuestion("q-1").replace("\"TUNING\"", "\"INVALID\""));

        assertThatThrownBy(() -> loader.load(temporaryDirectory))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessageContaining("Invalid questions.jsonl entry");
    }

    @Test
    void rejectsDuplicateNormalizedQueriesAcrossSplits() throws IOException {
        writeCorpus();
        writeQuestions(
                validQuestion("q-1"),
                validQuestion("q-2").replace("\"TUNING\"", "\"TEST\"")
                        .replace("Spring Boot 경험은?", "  Spring   Boot 경험은?  "));

        assertThatThrownBy(() -> loader.load(temporaryDirectory))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessageContaining("Duplicate normalized query");
    }

    @Test
    void rejectsEmptyQuestionsFile() throws IOException {
        writeCorpus();
        writeQuestions();

        assertThatThrownBy(() -> loader.load(temporaryDirectory))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessageContaining("must not be empty");
    }

    @Test
    void rejectsMalformedJsonWithoutEchoingItsContent() throws IOException {
        writeCorpus();
        writeQuestions("{private malformed text");

        assertThatThrownBy(() -> loader.load(temporaryDirectory))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessage("Invalid questions.jsonl entry at line 1.")
                .hasMessageNotContaining("private malformed text");
    }

    private void writeCorpus() throws IOException {
        Files.writeString(
                temporaryDirectory.resolve(SearchEvaluationDatasetLoader.CORPUS_FILE),
                """
                        {
                          "datasetId":"synthetic-test",
                          "documents":[{
                            "fixtureId":"doc-1",
                            "title":"합성 문서",
                            "documentType":"PROJECT_REPORT",
                            "fileType":"TXT",
                            "pages":[{"pageNumber":1,"text":"Spring Boot 합성 근거 문장"}],
                            "evidenceAnchors":[{
                              "fixtureEvidenceId":"spring-evidence",
                              "anchorText":"Spring Boot 합성 근거"
                            }]
                          }]
                        }
                        """,
                StandardCharsets.UTF_8);
    }

    private void writeQuestions(String... lines) throws IOException {
        Files.writeString(
                temporaryDirectory.resolve(SearchEvaluationDatasetLoader.QUESTIONS_FILE),
                String.join(System.lineSeparator(), lines),
                StandardCharsets.UTF_8);
    }

    private String validQuestion(String questionId) {
        return """
                {"questionId":"%s","query":"Spring Boot 경험은?","expectedEvidence":[{"fixtureEvidenceId":"spring-evidence","relevance":2,"evidenceGroupId":"spring-group"}],"noEvidence":false,"split":"TUNING","category":"TECHNICAL_EXPERIENCE"}
                """.formatted(questionId).strip();
    }
}
