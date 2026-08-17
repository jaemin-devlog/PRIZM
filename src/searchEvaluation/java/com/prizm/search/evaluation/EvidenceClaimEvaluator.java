package com.prizm.search.evaluation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluation-only claim verifier. It deliberately receives an already selected candidate and
 * never participates in production retrieval, ranking, or response construction.
 */
final class EvidenceClaimEvaluator {

    private static final Pattern LATIN_IDENTIFIER =
            Pattern.compile("(?<![A-Za-z0-9])[A-Za-z][A-Za-z0-9+.#-]{1,}(?![A-Za-z0-9])");
    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile(
            "(?<![0-9])([0-9]+(?:[.,][0-9]+)?)(%|밀리초|ms|초|분|시간|개|건|명|회)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("(?<=[.!?])\\s+|\\n+");
    private static final Pattern COMPLETED_ACTION = Pattern.compile(
            "(?:구현|도입|배포|운영|출시|전환|처리|사용|완료|달성|조정)(?:했|하였|됐다|되었|하고|하여|해|한)|"
                    + "(?:이전했다|이전을 완료했|이전 완료|migration을 수행했|migration 수행했)");

    Decision evaluate(String query, String candidateContent) {
        List<String> claimUnits = claimUnits(candidateContent);
        List<String> requiredIdentifiers = identifiers(query);
        boolean numericQuery = !numbers(query).isEmpty();

        if (numericQuery) {
            return evaluateNumeric(query, claimWindows(claimUnits), requiredIdentifiers);
        }

        List<String> targetUnits = claimUnits.stream()
                .filter(unit -> requiredIdentifiers.isEmpty() || containsAll(unit, requiredIdentifiers))
                .toList();
        if (!requiredIdentifiers.isEmpty() && targetUnits.isEmpty()) {
            return Decision.reject("TARGET_NOT_BOUND");
        }
        for (String unit : targetUnits.isEmpty() ? claimUnits : targetUnits) {
            if (hasNonCompletedState(unit)) {
                continue;
            }
            if (hasExplicitNegation(unit)) {
                continue;
            }
            if (requiresCurrentness(query) && hasCurrentnessConflict(unit)) {
                continue;
            }
            if (hasCompletedAction(unit)) {
                return Decision.accept("AFFIRMATIVE_BOUND_CLAIM");
            }
        }
        return Decision.reject(requiresCurrentness(query)
                ? "CURRENTNESS_OR_POLARITY_NOT_SUPPORTED"
                : "AFFIRMATIVE_ACTION_NOT_SUPPORTED");
    }

    private Decision evaluateNumeric(
            String query, List<String> claimUnits, List<String> requiredIdentifiers) {
        List<NumericValue> expected = numbers(query);
        for (String unit : claimUnits) {
            if (!requiredIdentifiers.isEmpty() && !containsAll(unit, requiredIdentifiers)) {
                continue;
            }
            if (!containsAllNumbers(unit, expected)) {
                continue;
            }
            if (hasNonCompletedState(unit)) {
                return Decision.reject("NUMERIC_ASSERTION_NOT_COMPLETED");
            }
            if (hasExplicitNegation(unit)) {
                return Decision.reject("NUMERIC_ASSERTION_NEGATED");
            }
            if (hasCompletedAction(unit)) {
                return Decision.accept("AFFIRMATIVE_NUMERIC_CLAIM");
            }
        }
        return Decision.reject("NUMERIC_VALUE_UNIT_OR_AFFIRMATION_MISSING");
    }

    private static List<String> claimWindows(List<String> units) {
        List<String> windows = new ArrayList<>();
        for (int index = 0; index < units.size(); index++) {
            windows.add(index == 0 ? units.get(index) : units.get(index - 1) + " " + units.get(index));
        }
        return windows;
    }

    private static List<String> claimUnits(String content) {
        String normalized = normalize(content);
        List<String> units = new ArrayList<>();
        for (String unit : SENTENCE_BOUNDARY.split(normalized)) {
            if (!unit.isBlank()) {
                units.add(unit.strip());
            }
        }
        return units;
    }

    private static List<String> identifiers(String text) {
        List<String> identifiers = new ArrayList<>();
        Matcher matcher = LATIN_IDENTIFIER.matcher(text);
        while (matcher.find()) {
            String identifier = matcher.group().toLowerCase(Locale.ROOT);
            if (!identifier.equals("api")) {
                identifiers.add(identifier);
            }
        }
        return identifiers.stream().distinct().toList();
    }

    private static List<NumericValue> numbers(String text) {
        List<NumericValue> values = new ArrayList<>();
        Matcher matcher = NUMBER_WITH_UNIT.matcher(normalize(text));
        while (matcher.find()) {
            values.add(new NumericValue(matcher.group(1).replace(',', '.'), matcher.group(2).toLowerCase(Locale.ROOT)));
        }
        return values;
    }

    private static boolean containsAll(String unit, List<String> requiredIdentifiers) {
        String normalized = normalize(unit);
        return requiredIdentifiers.stream().allMatch(normalized::contains);
    }

    private static boolean containsAllNumbers(String unit, List<NumericValue> expected) {
        List<NumericValue> actual = numbers(unit);
        return expected.stream().allMatch(actual::contains);
    }

    private static boolean requiresCurrentness(String query) {
        String normalized = normalize(query);
        return normalized.contains("현재")
                || normalized.contains("운영 서비스")
                || normalized.contains("production")
                || normalized.contains("출시 제품")
                || normalized.contains("현행");
    }

    private static boolean hasExplicitNegation(String unit) {
        String normalized = normalize(unit);
        return normalized.contains("않")
                || normalized.contains("없다")
                || normalized.contains("없으며")
                || normalized.contains("없고")
                || normalized.contains("못")
                || normalized.contains("미구현")
                || normalized.contains("미채택");
    }

    private static boolean hasCurrentnessConflict(String unit) {
        String normalized = normalize(unit);
        return normalized.contains("폐기")
                || normalized.contains("제거")
                || normalized.contains("중단")
                || normalized.contains("prototype")
                || normalized.contains("프로토타입")
                || normalized.contains("retired")
                || normalized.contains("deprecated")
                || normalized.contains("더 이상 사용하지")
                || normalized.contains("현행") && normalized.contains("없")
                || normalized.contains("현재") && normalized.contains("포함되지");
    }

    private static boolean hasNonCompletedState(String unit) {
        String normalized = normalize(unit);
        return normalized.contains("계획")
                || normalized.contains("예정")
                || normalized.contains("검토")
                || normalized.contains("논의")
                || normalized.contains("제안")
                || normalized.contains("단계")
                || normalized.contains("시작하지");
    }

    private static boolean hasCompletedAction(String unit) {
        return COMPLETED_ACTION.matcher(normalize(unit)).find();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ")
                .toLowerCase(Locale.ROOT)
                .strip();
    }

    record Decision(boolean accepted, String reason) {
        static Decision accept(String reason) {
            return new Decision(true, reason);
        }

        static Decision reject(String reason) {
            return new Decision(false, reason);
        }
    }

    private record NumericValue(String value, String unit) {}
}
