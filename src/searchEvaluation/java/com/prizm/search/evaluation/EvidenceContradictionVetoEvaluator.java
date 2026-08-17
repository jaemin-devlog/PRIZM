package com.prizm.search.evaluation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Evaluation-only veto: PASS_THROUGH means no explicit contradiction was found. */
final class EvidenceContradictionVetoEvaluator {

    enum Verdict { VETO, PASS_THROUGH }

    private static final Pattern IDENTIFIER =
            Pattern.compile("(?<![A-Za-z0-9])[A-Za-z][A-Za-z0-9+.#-]{1,}(?![A-Za-z0-9])");
    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile(
            "(?<![0-9])([0-9]+(?:[.,][0-9]+)?)(%|밀리초|ms|초|분|시간|개|건|명|회)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?])\\s+|\\n+");
    private static final Pattern NEGATED_COMPLETION = Pattern.compile(
            "(?:구현|도입|채택|배포|운영|사용|시작|완료|연결|경험|기록|근거|이전).{0,16}(?:않|없)");
    private static final Pattern TERMINAL_CURRENT_NEGATION = Pattern.compile(
            "폐기했|폐기된|제거했|제거된|중단했|중단된|retired|deprecated|"
                    + "현재.{0,20}(?:포함되지|사용하지)|더 이상.{0,20}사용하지");
    private static final Pattern AFFIRMATIVE_ACTION = Pattern.compile(
            "(?:구현|도입|채택|배포|운영|출시|전환|처리|사용|완료|달성|조정)(?:했|하였|됐다|되었|하고|하여|해|한)|"
                    + "(?:이전했다|이전을 완료했|migration을 수행했)");

    Verdict evaluate(String query, String candidateContent) {
        List<String> identifiers = identifiers(query);
        List<String> sentences = sentences(candidateContent);
        List<String> windows = targetWindows(sentences, identifiers);

        if (!numbers(query).isEmpty()) {
            for (String window : windows.isEmpty() ? sentenceWindows(sentences) : windows) {
                if (containsAllNumbers(window, numbers(query)) && hasEvidenceNegation(window)) {
                    return Verdict.VETO;
                }
            }
        }

        if (windows.isEmpty()) {
            return Verdict.PASS_THROUGH;
        }
        for (String window : windows) {
            if (NEGATED_COMPLETION.matcher(window).find()) {
                return Verdict.VETO;
            }
            if (requiresCurrentness(query) && currentStateIsExplicitlyNegated(window)
                    && !hasAffirmativeCurrentTransition(window)) {
                return Verdict.VETO;
            }
            if (requiresCurrentness(query) && prototypeOnly(window) && !hasAffirmativeCurrentTransition(window)) {
                return Verdict.VETO;
            }
        }
        return Verdict.PASS_THROUGH;
    }

    private static List<String> targetWindows(List<String> sentences, List<String> identifiers) {
        if (identifiers.isEmpty()) return sentenceWindows(sentences);
        List<String> windows = new ArrayList<>();
        for (int index = 0; index < sentences.size(); index++) {
            if (containsAll(sentences.get(index), identifiers)) {
                windows.add(index + 1 < sentences.size()
                        ? sentences.get(index) + " " + sentences.get(index + 1)
                        : sentences.get(index));
            }
        }
        return windows;
    }

    private static List<String> sentenceWindows(List<String> sentences) {
        List<String> windows = new ArrayList<>();
        for (int index = 0; index < sentences.size(); index++) {
            windows.add(index + 1 < sentences.size()
                    ? sentences.get(index) + " " + sentences.get(index + 1)
                    : sentences.get(index));
        }
        return windows;
    }

    private static boolean currentStateIsExplicitlyNegated(String window) {
        return TERMINAL_CURRENT_NEGATION.matcher(window).find();
    }

    private static boolean prototypeOnly(String window) {
        return (window.contains("prototype") || window.contains("프로토타입"))
                && Pattern.compile("(?:고객|운영|production|현재).{0,24}(?:연결하지|포함되지|사용하지)")
                        .matcher(window).find();
    }

    private static boolean hasAffirmativeCurrentTransition(String window) {
        return AFFIRMATIVE_ACTION.matcher(window).find()
                && (window.contains("현재") || window.contains("production") || window.contains("승격"));
    }

    private static boolean hasEvidenceNegation(String window) {
        return window.contains("않") || window.contains("없") || window.contains("부재");
    }

    private static boolean requiresCurrentness(String query) {
        String normalized = normalize(query);
        return normalized.contains("현재") || normalized.contains("운영") || normalized.contains("production")
                || normalized.contains("출시 제품") || normalized.contains("현행");
    }

    private static List<String> identifiers(String query) {
        List<String> values = new ArrayList<>();
        Matcher matcher = IDENTIFIER.matcher(query);
        while (matcher.find()) {
            String value = matcher.group().toLowerCase(Locale.ROOT);
            if (!value.equals("api")) values.add(value);
        }
        return values.stream().distinct().toList();
    }

    private static List<NumericValue> numbers(String text) {
        List<NumericValue> values = new ArrayList<>();
        Matcher matcher = NUMBER_WITH_UNIT.matcher(normalize(text));
        while (matcher.find()) {
            values.add(new NumericValue(matcher.group(1).replace(',', '.'), matcher.group(2).toLowerCase(Locale.ROOT)));
        }
        return values;
    }

    private static boolean containsAllNumbers(String text, List<NumericValue> expected) {
        return numbers(text).containsAll(expected);
    }

    private static boolean containsAll(String sentence, List<String> identifiers) {
        String normalized = normalize(sentence);
        return identifiers.stream().allMatch(normalized::contains);
    }

    private static List<String> sentences(String content) {
        List<String> values = new ArrayList<>();
        for (String sentence : SENTENCE.split(normalize(content))) {
            if (!sentence.isBlank()) values.add(sentence.strip());
        }
        return values;
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT).strip();
    }

    private record NumericValue(String value, String unit) {}
}
