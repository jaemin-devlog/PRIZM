package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Replays the frozen Judge A/B/C local windows without invoking Production evaluators. */
class ClaimVerifierV2ReplayTest {
    private static final Path INPUT = Path.of(
            "specs/PRZ-016-search-performance-v2/claim-verification-architecture-audit/shadow-results.json");
    private static final Path OUTPUT = Path.of(
            "specs/PRZ-016-search-performance-v2/safety-first-claim-verifier-v2/claim-verifier-v2-results.json");
    private static final Map<Path, String> FROZEN_HASHES = Map.of(
            INPUT, "aacb2445d600972a9574b3acee4bc19b4174f07a1ba9a68686589ec96d3e49fa",
            Path.of("src/main/java/com/prizm/search/profile/StructuredClaimSupportEvaluator.java"),
                    "951228dd4518a850fa2ca99345762dc75babf251e477171f8d67a7395e0efc35",
            Path.of("src/main/java/com/prizm/search/profile/CompositeSearchProfile.java"),
                    "757c067626aa76c0b69755eee20b1e143c48636264d30afa139f6dc2b8c7c479");

    private final ObjectMapper mapper = new ObjectMapper();
    private final ClaimVerifierV2 verifier = new ClaimVerifierV2();

    @Test
    void replaysFrozenJudgeWindows() throws Exception {
        FROZEN_HASHES.forEach((path, expected) -> assertThat(sha256(path)).isEqualTo(expected));
        JsonNode frozen = mapper.readTree(INPUT.toFile());
        Map<String, Summary> summaries = new LinkedHashMap<>();
        summaries.put("TOTAL", new Summary());
        summaries.put("JUDGE_A", new Summary());
        summaries.put("JUDGE_B", new Summary());
        summaries.put("JUDGE_C", new Summary());
        Map<String, Integer> hardNegativeFalsePositives = new LinkedHashMap<>();
        ArrayNode rows = mapper.createArrayNode();

        for (JsonNode source : frozen.path("rows")) {
            ClaimVerifierV2.Decision decision = verifier.verify(
                    source.path("query").asText(), source.path("localWindow").asText());
            boolean positive = "POSITIVE".equals(source.path("label").asText());
            String dataset = source.path("dataset").asText();
            record(summaries.get("TOTAL"), positive, source.path("d0").asText(), decision.status());
            record(summaries.get(dataset), positive, source.path("d0").asText(), decision.status());
            if (!positive && decision.status() == ClaimVerifierV2.Status.SUPPORTED) {
                hardNegativeFalsePositives.merge(source.path("type").asText(), 1, Integer::sum);
            }

            ObjectNode row = rows.addObject();
            row.put("dataset", dataset);
            row.put("id", source.path("id").asText());
            row.put("label", source.path("label").asText());
            row.put("type", source.path("type").asText());
            row.put("query", source.path("query").asText());
            row.put("candidateChunkId", source.path("candidateChunkId").asLong());
            row.put("localWindow", source.path("localWindow").asText());
            row.put("currentD0", source.path("d0").asText());
            row.put("v2", decision.status().name());
            row.put("v2Reason", decision.reason().name());
            row.set("groundedSentenceIds", mapper.valueToTree(decision.groundedSentenceIds()));
            if (positive && decision.status() != ClaimVerifierV2.Status.SUPPORTED) {
                row.put("failureLayer", failureLayer(decision.reason()));
            } else {
                row.put("failureLayer", "NONE");
            }
        }

        Summary total = summaries.get("TOTAL");
        boolean targetMet = total.v2PositiveRetained >= 28
                && total.v2NegativeFalsePositives == 0
                && requiredTypeFalsePositivesAreZero(hardNegativeFalsePositives);
        ObjectNode output = mapper.createObjectNode();
        output.put("schemaVersion", 1);
        output.put("phase", "SAFETY_FIRST_CLAIM_VERIFIER_V2_EVALUATION_SPIKE");
        output.put("executedAt", Instant.now().toString());
        output.put("inputSha256", sha256(INPUT));
        output.put("productionSearchModified", false);
        output.put("candidateGeneration", false);
        output.put("semanticVerifier", "NOT_RUN");
        output.put("targetMet", targetMet);
        output.put("verdict", targetMet
                ? "PASS_PRODUCTION_REPLACEMENT_RECOMMENDED"
                : total.v2NegativeFalsePositives == 0 ? "PARTIAL_PASS" : "FAIL");
        ObjectNode summaryNode = output.putObject("summaries");
        summaries.forEach((name, summary) -> summaryNode.set(name, summary.toJson(mapper)));
        output.set("hardNegativeFalsePositives", mapper.valueToTree(hardNegativeFalsePositives));
        ObjectNode hashes = output.putObject("productionSourceHashes");
        for (Map.Entry<Path, String> entry : FROZEN_HASHES.entrySet()) {
            if (!entry.getKey().equals(INPUT)) {
                hashes.put(entry.getKey().toString().replace('\\', '/'), sha256(entry.getKey()));
            }
        }
        output.set("rows", rows);
        Files.writeString(
                OUTPUT,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output) + "\n",
                StandardCharsets.UTF_8);

        assertThat(rows).hasSize(59);
        assertThat(total.positiveCount).isEqualTo(29);
        assertThat(total.negativeCount).isEqualTo(30);
    }

    private void record(
            Summary summary, boolean positive, String current, ClaimVerifierV2.Status v2) {
        if (positive) {
            summary.positiveCount++;
            if ("SUPPORTED".equals(current)) summary.currentPositiveRetained++;
            if (v2 == ClaimVerifierV2.Status.SUPPORTED) summary.v2PositiveRetained++;
        } else {
            summary.negativeCount++;
            if ("SUPPORTED".equals(current)) summary.currentNegativeFalsePositives++;
            if (v2 == ClaimVerifierV2.Status.SUPPORTED) summary.v2NegativeFalsePositives++;
        }
    }

    private String failureLayer(ClaimVerifierV2.Reason reason) {
        return switch (reason) {
            case REQUIRED_ENTITY_MISSING, INCOMPLETE_EXPLANATION_WINDOW -> "WINDOW_PRESELECTION";
            default -> "CLAIM_VERIFIER_V2";
        };
    }

    private boolean requiredTypeFalsePositivesAreZero(Map<String, Integer> falsePositives) {
        return falsePositives.getOrDefault("NEGATED", 0) == 0
                && falsePositives.getOrDefault("NOT_ADOPTED", 0) == 0
                && falsePositives.getOrDefault("OTHER_ACTOR", 0) == 0
                && falsePositives.getOrDefault("WRONG_NUMBER", 0) == 0
                && falsePositives.getOrDefault("WRONG_METRIC", 0) == 0;
    }

    private String sha256(Path path) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot hash " + path, exception);
        }
    }

    private static final class Summary {
        int positiveCount;
        int negativeCount;
        int currentPositiveRetained;
        int currentNegativeFalsePositives;
        int v2PositiveRetained;
        int v2NegativeFalsePositives;

        ObjectNode toJson(ObjectMapper mapper) {
            ObjectNode node = mapper.createObjectNode();
            node.put("positiveCount", positiveCount);
            node.put("negativeCount", negativeCount);
            node.put("currentPositiveRetained", currentPositiveRetained);
            node.put("currentNegativeFalsePositives", currentNegativeFalsePositives);
            node.put("v2PositiveRetained", v2PositiveRetained);
            node.put("v2NegativeFalsePositives", v2NegativeFalsePositives);
            return node;
        }
    }
}
