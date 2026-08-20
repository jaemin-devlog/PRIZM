package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Replays frozen candidate chunks through bounded preselection and unchanged ClaimVerifierV2. */
class LocalWindowPreselectionReplayTest {
    private static final Path SHADOW = Path.of(
            "specs/PRZ-016-search-performance-v2/claim-verification-architecture-audit/shadow-results.json");
    private static final Path V2_RESULTS = Path.of(
            "specs/PRZ-016-search-performance-v2/safety-first-claim-verifier-v2/claim-verifier-v2-results.json");
    private static final Path OUTPUT = Path.of(
            "specs/PRZ-016-search-performance-v2/local-window-preselection-contract/preselection-results.json");
    private static final String V2_HASH = "0e24f830030a2016308a3e1ac3a49ff369789b450ad8208fd23c7aba3e177b28";
    private static final Map<String, Path> TRACES = Map.of(
            "JUDGE_A", Path.of("specs/PRZ-016-search-performance-v2/final-unseen-judge-e2e/final-fix-regression-traces.json"),
            "JUDGE_B", Path.of("specs/PRZ-016-search-performance-v2/final-unseen-judge-b/judge-b-traces.json"),
            "JUDGE_C", Path.of("specs/PRZ-016-search-performance-v2/final-unseen-judge-c/judge-c-traces.json"));

    private final ObjectMapper mapper = new ObjectMapper();
    private final ClaimVerifierV2 verifier = new ClaimVerifierV2();
    private final LocalClaimWindowPreselector preselector = new LocalClaimWindowPreselector();

    @Test
    void replaysFrozenCandidateChunksWithBoundedPreselection() throws Exception {
        assertThat(sha256(Path.of("src/searchEvaluation/java/com/prizm/search/evaluation/ClaimVerifierV2.java")))
                .isEqualTo(V2_HASH);
        JsonNode frozen = mapper.readTree(SHADOW.toFile());
        Map<String, JsonNode> baseline = index(mapper.readTree(V2_RESULTS.toFile()).path("rows"));
        Map<String, JsonNode> traces = new LinkedHashMap<>();
        for (Map.Entry<String, Path> trace : TRACES.entrySet()) {
            traces.put(trace.getKey(), mapper.readTree(trace.getValue().toFile()));
        }
        Map<String, Summary> summaries = summaries();
        Map<String, Integer> afterFalsePositives = new LinkedHashMap<>();
        ArrayNode rows = mapper.createArrayNode();

        for (JsonNode source : frozen.path("rows")) {
            String key = key(source);
            String query = source.path("query").asText();
            String oldWindow = source.path("localWindow").asText();
            String content = candidateContent(source, traces.get(source.path("dataset").asText()));
            LocalClaimWindowPreselector.Selection selection = preselector.select(query, content);
            ClaimVerifierV2.Decision before = verifier.verify(query, oldWindow);
            ClaimVerifierV2.Decision after = verifier.verify(query, selection.window());
            assertThat(before.status().name()).isEqualTo(baseline.get(key).path("v2").asText());
            boolean positive = "POSITIVE".equals(source.path("label").asText());
            record(summaries.get("TOTAL"), positive, before.status(), after.status());
            record(summaries.get(source.path("dataset").asText()), positive, before.status(), after.status());
            if (!positive && after.status() == ClaimVerifierV2.Status.SUPPORTED) {
                afterFalsePositives.merge(source.path("type").asText(), 1, Integer::sum);
            }

            ObjectNode row = rows.addObject();
            row.put("dataset", source.path("dataset").asText());
            row.put("id", source.path("id").asText());
            row.put("label", source.path("label").asText());
            row.put("type", source.path("type").asText());
            row.put("query", query);
            row.put("candidateChunkId", source.path("candidateChunkId").asLong());
            row.put("beforeWindow", oldWindow);
            row.put("beforeV2", before.status().name());
            row.put("afterWindow", selection.window());
            row.put("selectedSentenceRange", "S" + selection.firstSentence() + "-S" + selection.lastSentence());
            row.put("selectionScore", selection.score());
            row.put("afterV2", after.status().name());
            row.put("afterReason", after.reason().name());
            row.set("groundedSentenceIds", mapper.valueToTree(after.groundedSentenceIds()));
        }

        Summary total = summaries.get("TOTAL");
        boolean targetMet = total.afterPositive >= 28 && total.afterFalsePositive == 0
                && requiredFalsePositivesAreZero(afterFalsePositives);
        ObjectNode output = mapper.createObjectNode();
        output.put("schemaVersion", 1);
        output.put("phase", "LOCAL_WINDOW_PRESELECTION_CONTRACT_SPIKE");
        output.put("executedAt", Instant.now().toString());
        output.put("frozenShadowSha256", sha256(SHADOW));
        output.put("claimVerifierV2Sha256", sha256(Path.of(
                "src/searchEvaluation/java/com/prizm/search/evaluation/ClaimVerifierV2.java")));
        output.put("productionSearchModified", false);
        output.put("semanticVerifier", "NOT_RUN");
        output.put("targetMet", targetMet);
        output.put("verdict", targetMet ? "PASS" : "PARTIAL_PASS");
        ObjectNode summary = output.putObject("summaries");
        summaries.forEach((name, value) -> summary.set(name, value.toJson(mapper)));
        output.set("afterFalsePositives", mapper.valueToTree(afterFalsePositives));
        output.set("rows", rows);
        Files.writeString(OUTPUT, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output) + "\n", StandardCharsets.UTF_8);

        assertThat(rows).hasSize(59);
        assertThat(total.positiveCount).isEqualTo(29);
        assertThat(total.negativeCount).isEqualTo(30);
    }

    private String candidateContent(JsonNode source, JsonNode traces) {
        Set<String> matches = new LinkedHashSet<>();
        String query = source.path("query").asText();
        long chunkId = source.path("candidateChunkId").asLong();
        String oldWindow = compact(source.path("localWindow").asText());
        for (JsonNode trace : traces) {
            if (!query.equals(trace.path("originalQuery").asText())) continue;
            for (JsonNode candidate : trace.path("candidates")) {
                String content = candidate.path("content").asText();
                if (candidate.path("chunkId").asLong() == chunkId && compact(content).contains(oldWindow)) {
                    matches.add(content);
                }
            }
        }
        assertThat(matches).as("frozen candidate content for %s", key(source)).hasSize(1);
        return matches.iterator().next();
    }

    private Map<String, JsonNode> index(JsonNode rows) {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        for (JsonNode row : rows) values.put(key(row), row);
        return values;
    }

    private String key(JsonNode row) {
        return row.path("dataset").asText() + "/" + row.path("id").asText();
    }

    private void record(
            Summary summary, boolean positive, ClaimVerifierV2.Status before, ClaimVerifierV2.Status after) {
        if (positive) {
            summary.positiveCount++;
            if (before == ClaimVerifierV2.Status.SUPPORTED) summary.beforePositive++;
            if (after == ClaimVerifierV2.Status.SUPPORTED) summary.afterPositive++;
        } else {
            summary.negativeCount++;
            if (before == ClaimVerifierV2.Status.SUPPORTED) summary.beforeFalsePositive++;
            if (after == ClaimVerifierV2.Status.SUPPORTED) summary.afterFalsePositive++;
        }
    }

    private boolean requiredFalsePositivesAreZero(Map<String, Integer> falsePositives) {
        return falsePositives.getOrDefault("NEGATED", 0) == 0
                && falsePositives.getOrDefault("NOT_ADOPTED", 0) == 0
                && falsePositives.getOrDefault("OTHER_ACTOR", 0) == 0
                && falsePositives.getOrDefault("WRONG_NUMBER", 0) == 0
                && falsePositives.getOrDefault("WRONG_METRIC", 0) == 0;
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", " ").strip();
    }

    private String sha256(Path path) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot hash " + path, exception);
        }
    }

    private Map<String, Summary> summaries() {
        Map<String, Summary> values = new LinkedHashMap<>();
        values.put("TOTAL", new Summary());
        values.put("JUDGE_A", new Summary());
        values.put("JUDGE_B", new Summary());
        values.put("JUDGE_C", new Summary());
        return values;
    }

    private static final class Summary {
        int positiveCount;
        int negativeCount;
        int beforePositive;
        int beforeFalsePositive;
        int afterPositive;
        int afterFalsePositive;

        ObjectNode toJson(ObjectMapper mapper) {
            ObjectNode node = mapper.createObjectNode();
            node.put("positiveCount", positiveCount);
            node.put("negativeCount", negativeCount);
            node.put("beforePositive", beforePositive);
            node.put("beforeFalsePositive", beforeFalsePositive);
            node.put("afterPositive", afterPositive);
            node.put("afterFalsePositive", afterFalsePositive);
            return node;
        }
    }
}
