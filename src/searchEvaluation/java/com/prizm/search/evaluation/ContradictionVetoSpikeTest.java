package com.prizm.search.evaluation;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

/** Single focused spike over the three frozen tuning sets. */
class ContradictionVetoSpikeTest {

    @Test
    void measuresContradictionVetoAgainstFrozenTuningSets() throws IOException {
        EvidenceContradictionVetoEvaluator evaluator = new EvidenceContradictionVetoEvaluator();
        Counts v1 = v1(evaluator);
        Counts v2 = direct(evaluator, Path.of("src/searchEvaluation/resources/rejection-shadow-unseen"));
        Counts adversarial = direct(evaluator, Path.of("src/searchEvaluation/resources/rejection-shadow-adversarial"));

        System.out.printf("V1 positive=%d/%d negative=%d/%d%n", v1.passed, v1.positive, v1.vetoed, v1.negative);
        System.out.printf("V2 positive=%d/%d negative=%d/%d%n", v2.passed, v2.positive, v2.vetoed, v2.negative);
        System.out.printf("ADVERSARIAL positive=%d/%d negative=%d/%d%n", adversarial.passed, adversarial.positive, adversarial.vetoed, adversarial.negative);

        assertEquals(v1.positive, v1.passed, "v1 positives must pass through");
        assertEquals(v1.negative, v1.vetoed, "v1 false positives must be vetoed");
        assertEquals(v2.positive, v2.passed, "v2 positives must pass through");
        assertEquals(v2.negative, v2.vetoed, "v2 negatives must be vetoed");
        assertEquals(adversarial.positive, adversarial.passed, "adversarial positives must pass through");
        assertEquals(adversarial.negative, adversarial.vetoed, "adversarial negatives must be vetoed");
    }

    private static Counts v1(EvidenceContradictionVetoEvaluator evaluator) throws IOException {
        Path root = Path.of("specs/PRZ-016-search-performance-v2/rejection-tuning");
        Map<String, Question> questions = questions(Files.readString(root.resolve("dataset.json")));
        Counts counts = new Counts();
        for (String run : arrayObjects(Files.readString(root.resolve("raw-results.json")), "queries")) {
            String state = stringField(run, "state");
            if (!"EVIDENCE_FOUND".equals(state)) continue;
            Question question = questions.get(stringField(run, "id"));
            String content = stringField(run, "content");
            boolean positive = "POSITIVE".equals(stringField(run, "polarity"))
                    && normalize(content).contains(normalize(question.anchor));
            if (positive) counts.positive++;
            else if (!"POSITIVE".equals(stringField(run, "polarity"))) counts.negative++;
            else continue;
            EvidenceContradictionVetoEvaluator.Verdict verdict = evaluator.evaluate(question.query, content);
            System.out.printf("V1 %s %s%n", question.id, verdict);
            if (positive && verdict == EvidenceContradictionVetoEvaluator.Verdict.PASS_THROUGH) counts.passed++;
            if (!positive && verdict == EvidenceContradictionVetoEvaluator.Verdict.VETO) counts.vetoed++;
        }
        return counts;
    }

    private static Counts direct(EvidenceContradictionVetoEvaluator evaluator, Path root) throws IOException {
        Map<String, String> expectations = expectations(Files.readString(root.resolve("ground-truth.json")));
        Counts counts = new Counts();
        for (String testCase : arrayObjects(Files.readString(root.resolve("dataset.json")), "cases")) {
            String id = stringField(testCase, "id");
            boolean positive = "ACCEPT".equals(expectations.get(id));
            EvidenceContradictionVetoEvaluator.Verdict verdict = evaluator.evaluate(
                    stringField(testCase, "query"), stringField(testCase, "candidate"));
            System.out.printf("%s %s%n", id, verdict);
            if (positive) {
                counts.positive++;
                if (verdict == EvidenceContradictionVetoEvaluator.Verdict.PASS_THROUGH) counts.passed++;
            } else {
                counts.negative++;
                if (verdict == EvidenceContradictionVetoEvaluator.Verdict.VETO) counts.vetoed++;
            }
        }
        return counts;
    }

    private static Map<String, Question> questions(String json) {
        Map<String, Question> questions = new HashMap<>();
        for (String object : arrayObjects(json, "questions")) {
            String id = stringField(object, "id");
            questions.put(id, new Question(id, stringField(object, "query"), stringField(object, "answerAnchor")));
        }
        return questions;
    }

    private static Map<String, String> expectations(String json) {
        Map<String, String> values = new HashMap<>();
        for (String object : arrayObjects(json, "expectations")) values.put(stringField(object, "id"), stringField(object, "expected"));
        return values;
    }

    private static List<String> arrayObjects(String json, String name) {
        int start = json.indexOf('[', json.indexOf('"' + name + '"'));
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
            } else if (character == '"') quoted = true;
            else if (character == '{') { if (depth++ == 0) objectStart = index; }
            else if (character == '}' && --depth == 0) objects.add(json.substring(objectStart, index + 1));
            else if (character == ']' && depth == 0) break;
        }
        return objects;
    }

    private static String stringField(String object, String name) {
        Pattern field = Pattern.compile("\\\"" + Pattern.quote(name) + "\\\"\\s*:\\s*(?:\\\"((?:\\\\.|[^\\\"\\\\])*)\\\"|null)");
        Matcher matcher = field.matcher(object);
        return matcher.find() && matcher.group(1) != null
                ? matcher.group(1).replace("\\\\n", "\n").replace("\\\\\"", "\"").replace("\\\\\\\\", "\\")
                : "";
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).strip();
    }

    private static final class Counts { int positive; int negative; int passed; int vetoed; }
    private record Question(String id, String query, String anchor) {}
}
