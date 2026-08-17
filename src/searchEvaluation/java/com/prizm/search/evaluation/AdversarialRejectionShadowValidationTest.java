package com.prizm.search.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
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

/** Frozen adversarial validation of the unchanged evaluation-only evaluator. */
class AdversarialRejectionShadowValidationTest {

    private static final Path ROOT = Path.of("src/searchEvaluation/resources/rejection-shadow-adversarial");
    private static final String DATASET_HASH = "1475d0cc867dcc7eca2bbbdb8f3da242e0a8cbb1597d7f9026400078cd5e9c8f";
    private static final String GROUND_TRUTH_HASH = "a2bbd3b66d181adb62cb7192e4000f8a4c9d753fa1eab715889e8c65aa670b3d";

    @Test
    void measuresFrozenAdversarialCasesOnce() throws IOException {
        Path dataset = ROOT.resolve("dataset.json");
        Path groundTruth = ROOT.resolve("ground-truth.json");
        assertEquals(DATASET_HASH, sha256(dataset));
        assertEquals(GROUND_TRUTH_HASH, sha256(groundTruth));

        List<Case> cases = cases(Files.readString(dataset));
        Map<String, String> expected = expectations(Files.readString(groundTruth));
        assertEquals(16, cases.size());
        assertEquals(16, expected.size());

        EvidenceClaimEvaluator evaluator = new EvidenceClaimEvaluator();
        int positiveAccepted = 0;
        int falseRejection = 0;
        int negativeRejected = 0;
        int falsePositive = 0;
        for (Case testCase : cases) {
            EvidenceClaimEvaluator.Decision decision = evaluator.evaluate(testCase.query(), testCase.candidate());
            boolean positive = "ACCEPT".equals(expected.get(testCase.id()));
            System.out.printf("%s SHADOW_%s REASON=%s%n", testCase.id(),
                    decision.accepted() ? "ACCEPT" : "REJECT", decision.reason());
            if (positive && decision.accepted()) positiveAccepted++;
            else if (positive) falseRejection++;
            else if (decision.accepted()) falsePositive++;
            else negativeRejected++;
        }
        System.out.printf("ADVERSARIAL_SUMMARY Positive accepted=%d/10 False rejection=%d/10 Negative rejected=%d/6 False positive=%d/6%n",
                positiveAccepted, falseRejection, negativeRejected, falsePositive);

        assertTrue(positiveAccepted >= 9, "adversarial positive acceptance gate");
        assertEquals(6, negativeRejected, "adversarial negative rejection gate");
    }

    private static List<Case> cases(String json) {
        List<Case> cases = new ArrayList<>();
        for (String object : arrayObjects(json, "cases")) {
            cases.add(new Case(stringField(object, "id"), stringField(object, "query"), stringField(object, "candidate")));
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

    private record Case(String id, String query, String candidate) {}
}
