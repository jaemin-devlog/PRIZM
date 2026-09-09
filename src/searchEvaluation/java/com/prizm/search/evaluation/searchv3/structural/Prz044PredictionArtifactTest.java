package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.document.entity.DocumentFileType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class Prz044PredictionArtifactTest {

    @Test
    void preservesGoldFreeSelectedAndDisplayProvenanceWithAtMostFiveResults() {
        var selected = span("selected");
        var displayed = span("displayed");
        var prediction = prediction(
                Prz044PredictionArtifact.Engine.V2,
                List.of(new Prz044PredictionArtifact.Result(
                        1, "V2|version-1|chunk-1", "V2|version-1", 0.75d, "RESULT",
                        List.of(selected), List.of(displayed))));
        var root = new ObjectMapper().valueToTree(prediction);

        assertThat(prediction.queries().get(0).finalResults()).hasSize(1);
        assertThat(prediction.queries().get(0).finalResults().get(0).selectedSpans())
                .containsExactly(selected);
        assertThat(prediction.queries().get(0).finalResults().get(0).displaySpans())
                .containsExactly(displayed);
        assertNoGoldField(root);
    }

    @Test
    void rejectsMoreThanFiveOrNonSequentialFinalResults() {
        List<Prz044PredictionArtifact.Result> six = new ArrayList<>();
        for (int rank = 1; rank <= 6; rank++) {
            six.add(result(Math.min(rank, 5)));
        }

        assertThatThrownBy(() -> query(six))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at most five");
        assertThatThrownBy(() -> query(List.of(result(2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("sequential");
    }

    @Test
    void rejectsUnsafeOrIncompleteSourceProvenance() {
        assertThatThrownBy(() -> new Prz044PredictionArtifact.SourceSpan(
                "user-1", "document-1", "version-1", "RESUME", DocumentFileType.PDF,
                "corpus\\user-1\\document.pdf", 1, 0, 3, "a".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("portable");
        assertThatThrownBy(() -> new Prz044PredictionArtifact.SourceSpan(
                "user-1", "document-1", "version-1", "RESUME", DocumentFileType.PDF,
                "corpus/user-1/document.pdf", null, 0, 3, "a".repeat(64)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page number");
    }

    static Prz044PredictionArtifact.PredictionSet prediction(
            Prz044PredictionArtifact.Engine engine,
            List<Prz044PredictionArtifact.Result> results) {
        return new Prz044PredictionArtifact.PredictionSet(
                Prz044PredictionArtifact.ARTIFACT_TYPE,
                Prz044PredictionArtifact.SCHEMA_VERSION,
                engine,
                engine == Prz044PredictionArtifact.Engine.V2 ? "V2_PROFILE" : "V3_PROFILE",
                "1".repeat(64),
                "2".repeat(64),
                "3".repeat(64),
                "4".repeat(64),
                "5".repeat(64),
                Map.of("V2", "6".repeat(64), "V3", "7".repeat(64),
                        "SHARED", "8".repeat(64), "EVALUATOR", "9".repeat(64)),
                new Prz044PredictionArtifact.ModelIdentity(
                        "bge-m3", "a".repeat(64), 1024, "COSINE"),
                "b".repeat(64),
                "2026-09-03T00:00:00Z",
                "2026-09-03T00:01:00Z",
                new Prz044PredictionArtifact.IndexingStats(
                        1, 1, 1, 4096, 1.0d, "UNIT"),
                new Prz044PredictionArtifact.RuntimeAudit(
                        1, 1, 1, 0, 0, 0, 0, 0, 0, true,
                        "bge-m3", "a".repeat(64), 1024, 0, 0, false),
                List.of(query(results)));
    }

    private static Prz044PredictionArtifact.QueryPrediction query(
            List<Prz044PredictionArtifact.Result> results) {
        return new Prz044PredictionArtifact.QueryPrediction(
                "query-1", "user-1", "BACKEND", "백엔드", "KO", "c".repeat(64),
                results.isEmpty() ? "NO_RESULTS" : "RESULTS", 1.0d, results);
    }

    private static Prz044PredictionArtifact.Result result(int rank) {
        return new Prz044PredictionArtifact.Result(
                rank, "stable-" + rank, "parent-" + rank, 1.0d / rank, "RESULT",
                List.of(span("selected-" + rank)), List.of(span("display-" + rank)));
    }

    private static Prz044PredictionArtifact.SourceSpan span(String text) {
        return new Prz044PredictionArtifact.SourceSpan(
                "user-1", "document-1", "version-1", "RESUME", DocumentFileType.TXT,
                "corpus/user-1/document.txt", 1, 0, 3, Prz044PredictionFreeze.sha256(text));
    }

    private static void assertNoGoldField(JsonNode value) {
        if (value.isObject()) {
            for (String property : value.propertyNames()) {
                assertThat(property).doesNotContainIgnoringCase("gold");
                assertNoGoldField(value.path(property));
            }
        }
        else if (value.isArray()) {
            value.forEach(Prz044PredictionArtifactTest::assertNoGoldField);
        }
    }
}
