package com.prizm.search.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Frozen tuning set for the evaluation-only shadow evaluator. */
class UnseenRejectionShadowValidationTest {

    private static final Path ROOT = Path.of("src/searchEvaluation/resources/rejection-shadow-unseen");
    private static final String DATASET_HASH = "1f404a1e18a17c2d7234dc30338f6560ca72b21ba518ab732bb6142ae91617f0";
    private static final String GROUND_TRUTH_HASH = "9631cc5cca80402e2a3d261d54257c1bc7c4673766f8263aacc65cd4697873d4";

    @Test
    void measuresFrozenSecondTuningSet() throws IOException {
        Path datasetPath = ROOT.resolve("dataset.json");
        Path groundTruthPath = ROOT.resolve("ground-truth.json");
        assertEquals(DATASET_HASH, sha256(datasetPath));
        assertEquals(GROUND_TRUTH_HASH, sha256(groundTruthPath));

        Map<String, String> expected = expectations(Files.readString(groundTruthPath));
        List<Case> cases = cases(Files.readString(datasetPath));
        assertEquals(24, cases.size());
        assertEquals(24, expected.size());

        EvidenceClaimEvaluator evaluator = new EvidenceClaimEvaluator();
        int positiveAccepted = 0;
        int positiveRegression = 0;
        int negativeRejected = 0;
        int negativeFalsePositive = 0;
        Map<String, Counts> categories = new HashMap<>();

        for (Case testCase : cases) {
            EvidenceClaimEvaluator.Decision decision = evaluator.evaluate(testCase.query(), testCase.candidate());
            boolean shouldAccept = "ACCEPT".equals(expected.get(testCase.id()));
            System.out.printf("%s SHADOW_%s REASON=%s%n", testCase.id(),
                    decision.accepted() ? "ACCEPT" : "REJECT", decision.reason());
            Counts counts = categories.computeIfAbsent(testCase.category(), ignored -> new Counts());
            if (shouldAccept) {
                if (decision.accepted()) {
                    positiveAccepted++;
                    counts.accepted++;
                } else {
                    positiveRegression++;
                    counts.rejected++;
                }
            } else if (decision.accepted()) {
                negativeFalsePositive++;
                counts.accepted++;
            } else {
                negativeRejected++;
                counts.rejected++;
            }
        }

        System.out.printf("UNSEEN_SUMMARY Positive accepted=%d/8 Positive regression=%d/8 Negative rejected=%d/16 Negative false positive=%d/16%n",
                positiveAccepted, positiveRegression, negativeRejected, negativeFalsePositive);
        categories.forEach((category, counts) -> System.out.printf(
                "CATEGORY %s ACCEPTED=%d REJECTED=%d%n", category, counts.accepted, counts.rejected));

        assertTrue(positiveAccepted >= 7, "unseen positive acceptance gate");
        assertEquals(16, negativeRejected, "second tuning negative rejection target");
        assertEquals(0, negativeFalsePositive, "second tuning negative false-positive target");
    }

    private static List<Case> cases(String json) {
        List<Case> cases = new ArrayList<>();
        for (String object : arrayObjects(json, "cases")) {
            cases.add(new Case(stringField(object, "id"), stringField(object, "category"),
                    stringField(object, "query"), stringField(object, "candidate")));
        }
        return cases;
    }

    private static Map<String, String> expectations(String json) {
        Map<String, String> expectations = new HashMap<>();
        for (String object : arrayObjects(json, "expectations")) {
            expectations.put(stringField(object, "id"), stringField(object, "expected"));
        }
        return expectations;
    }

    private static List<String> arrayObjects(String json, String arrayName) {
        int start = json.indexOf('[', json.indexOf('"' + arrayName + '"'));
        List<String> objects = new ArrayList<>();
        boolean quoted = false;
        boolean escaped = false;
        int depth = 0;
        int objectStart = -1;
        for (int index = start + 1; index < json.length(); index++) {
            char character = json.charAt(index);
            if (quoted) {
                if (escaped) escaped = false;
                else if (character == '\\') escaped = true;
                else if (character == '"') quoted = false;
            } else if (character == '"') {
                quoted = true;
            } else if (character == '{') {
                if (depth++ == 0) objectStart = index;
            } else if (character == '}' && --depth == 0) {
                objects.add(json.substring(objectStart, index + 1));
            } else if (character == ']' && depth == 0) {
                break;
            }
        }
        return objects;
    }

    private static String stringField(String object, String name) {
        Pattern field = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"");
        Matcher matcher = field.matcher(object);
        if (!matcher.find()) throw new IllegalArgumentException("missing field: " + name);
        return matcher.group(1).replace("\\\\\"", "\"").replace("\\\\\\\\", "\\");
    }

    private static String sha256(Path path) throws IOException {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
            StringBuilder hex = new StringBuilder();
            for (byte value : hash) hex.append(String.format(Locale.ROOT, "%02x", value));
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Case(String id, String category, String query, String candidate) {}

    private static final class Counts {
        private int accepted;
        private int rejected;
    }
}
