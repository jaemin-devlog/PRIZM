package com.prizm.search.profile;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts normalized numeric anchors for exact relevance matching and numeric rescue. */
public final class NumericQueryAnchors {

    private static final Pattern NUMERIC_ANCHOR = Pattern.compile(
            "(?<![\\p{N}])(?<number>\\d[\\d,]*(?:\\.\\d+)?)(?:\\s*)"
                    + "(?<unit>밀리초|ms|회|건|행|초|분|개|명|번|%|퍼센트)?(?![\\p{N}])");

    private NumericQueryAnchors() {
    }

    public static List<NumericAnchor> extract(String query) {
        Matcher matcher = NUMERIC_ANCHOR.matcher(SearchTokenNormalizer.normalize(query));
        Set<NumericAnchor> anchors = new LinkedHashSet<>();
        while (matcher.find()) {
            String unit = normalizeUnit(matcher.group("unit"));
            anchors.add(new NumericAnchor(normalizeNumber(matcher.group("number")), unit));
        }
        return List.copyOf(anchors);
    }

    public static boolean hasContextualMatch(String query, String content) {
        return extract(query).stream()
                .filter(NumericAnchor::hasUnit)
                .anyMatch(anchor -> anchor.matches(content));
    }

    public static boolean hasAllContextualMatches(String query, String content) {
        List<NumericAnchor> required = extract(query).stream()
                .filter(NumericAnchor::hasUnit)
                .toList();
        return !required.isEmpty()
                && required.stream().allMatch(anchor -> anchor.matches(content));
    }

    private static String normalizeNumber(String value) {
        return new BigDecimal(value.replace(",", ""))
                .stripTrailingZeros()
                .toPlainString();
    }

    private static String normalizeUnit(String unit) {
        if ("밀리초".equals(unit)) {
            return "ms";
        }
        if ("퍼센트".equals(unit)) {
            return "%";
        }
        return unit;
    }

    public record NumericAnchor(String number, String unit) {

        public boolean hasUnit() {
            return unit != null && !unit.isBlank();
        }

        public boolean matches(String content) {
            if (!hasUnit()) {
                return false;
            }
            return extract(content).contains(this);
        }
    }
}
