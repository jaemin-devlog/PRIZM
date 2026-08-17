package com.prizm.search.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Focused shadow measurement only; it never calls the production search service. */
class RejectionShadowEvaluatorTest {

    private static final Path DATASET = Path.of(
            "specs/PRZ-016-search-performance-v2/rejection-tuning/dataset.json");
    private static final Path RAW_RESULTS = Path.of(
            "specs/PRZ-016-search-performance-v2/rejection-tuning/raw-results.json");

    @Test
    void measuresFrozenBaselineWithoutChangingProductionResults() throws IOException {
        Map<String, Question> questions = questions(Files.readString(DATASET));
        List<Run> runs = runs(Files.readString(RAW_RESULTS));

        EvidenceClaimEvaluator evaluator = new EvidenceClaimEvaluator();
        int baselinePositivePass = 0;
        int positiveRetained = 0;
        int positiveRegression = 0;
        int baselineFalsePositives = 0;
        int blockedFalsePositives = 0;
        int remainingFalsePositives = 0;
        Map<String, CategoryCounts> categories = new HashMap<>();

        for (Run run : runs) {
            Question question = questions.get(run.id());
            String baselineState = run.baselineState();
            boolean evidenceFound = "EVIDENCE_FOUND".equals(baselineState);
            boolean positive = "POSITIVE".equals(run.polarity());
            boolean baselineCorrectPositive = positive && evidenceFound && containsAnswerAnchor(
                    run.content(), question.answerAnchor());
            EvidenceClaimEvaluator.Decision decision = evidenceFound
                    ? evaluator.evaluate(question.query(), run.content())
                    : EvidenceClaimEvaluator.Decision.reject("NO_BASELINE_EVIDENCE");

            System.out.printf("%s BASELINE_STATE=%s SHADOW_%s REASON=%s%n",
                    run.id(),
                    baselineState,
                    decision.accepted() ? "ACCEPT" : "REJECT",
                    decision.reason());

            if (baselineCorrectPositive) {
                baselinePositivePass++;
                if (decision.accepted()) {
                    positiveRetained++;
                } else {
                    positiveRegression++;
                }
            }
            if (!positive) {
                String category = run.negativeType();
                CategoryCounts counts = categories.computeIfAbsent(category, ignored -> new CategoryCounts());
                if (evidenceFound) {
                    baselineFalsePositives++;
                    counts.baselineFalsePositives++;
                    if (decision.accepted()) {
                        remainingFalsePositives++;
                        counts.remainingFalsePositives++;
                    } else {
                        blockedFalsePositives++;
                        counts.blockedFalsePositives++;
                    }
                }
            }
        }

        System.out.printf("SHADOW_SUMMARY Positive retained=%d/%d Positive regression=%d FP blocked=%d/%d FP remaining=%d/16%n",
                positiveRetained, baselinePositivePass, positiveRegression,
                blockedFalsePositives, baselineFalsePositives, remainingFalsePositives);
        categories.forEach((category, counts) -> System.out.printf(
                "CATEGORY %s BASELINE_FP=%d BLOCKED=%d REMAINING=%d%n",
                category, counts.baselineFalsePositives, counts.blockedFalsePositives, counts.remainingFalsePositives));

        assertEquals(3, baselinePositivePass, "frozen baseline positive pass count");
        assertEquals(5, baselineFalsePositives, "frozen baseline false positive count");
        assertEquals(3, positiveRetained, "all baseline-correct positives must remain accepted");
        assertEquals(0, positiveRegression, "shadow evaluator must not reject a baseline-correct positive");
        assertTrue(blockedFalsePositives >= 4, "shadow evaluator must block at least four baseline false positives");
        assertEquals(0, remainingFalsePositives, "no baseline false positive may remain accepted");
    }

    private static boolean containsAnswerAnchor(String content, String answerAnchor) {
        String normalizedAnchor = normalize(answerAnchor);
        return !normalizedAnchor.isBlank() && normalize(content).contains(normalizedAnchor);
    }

    private static Map<String, Question> questions(String json) {
        Map<String, Question> questions = new HashMap<>();
        for (String object : arrayObjects(json, "questions")) {
            String id = stringField(object, "id");
            questions.put(id, new Question(id, stringField(object, "query"), stringField(object, "answerAnchor")));
        }
        return questions;
    }

    private static List<Run> runs(String json) {
        List<Run> runs = new ArrayList<>();
        for (String object : arrayObjects(json, "queries")) {
            runs.add(new Run(
                    stringField(object, "id"),
                    stringField(object, "polarity"),
                    stringField(object, "negativeType"),
                    stringField(object, "state"),
                    stringField(object, "content")));
        }
        return runs;
    }

    private static List<String> arrayObjects(String json, String arrayName) {
        int key = json.indexOf('"' + arrayName + '"');
        int start = json.indexOf('[', key);
        List<String> objects = new ArrayList<>();
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        int objectStart = -1;
        for (int index = start + 1; index < json.length(); index++) {
            char character = json.charAt(index);
            if (quoted) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    quoted = false;
                }
                continue;
            }
            if (character == '"') {
                quoted = true;
            } else if (character == '{') {
                if (depth++ == 0) {
                    objectStart = index;
                }
            } else if (character == '}' && --depth == 0) {
                objects.add(json.substring(objectStart, index + 1));
            } else if (character == ']' && depth == 0) {
                break;
            }
        }
        return objects;
    }

    private static String stringField(String jsonObject, String name) {
        Pattern field = Pattern.compile("\\\"" + Pattern.quote(name)
                + "\\\"\\s*:\\s*(?:\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"|null)");
        Matcher matcher = field.matcher(jsonObject);
        if (!matcher.find() || matcher.group(1) == null) {
            return "";
        }
        return matcher.group(1)
                .replace("\\\\n", "\n")
                .replace("\\\\\"", "\"")
                .replace("\\\\\\\\", "\\");
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT)
                .strip();
    }

    private static final class CategoryCounts {
        private int baselineFalsePositives;
        private int blockedFalsePositives;
        private int remainingFalsePositives;
    }

    private record Question(String id, String query, String answerAnchor) {}

    private record Run(String id, String polarity, String negativeType, String baselineState, String content) {}
}
