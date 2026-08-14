package com.prizm.search.profile;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts exact numeric anchors with an adjacent unit for the narrow numeric rescue path. */
public final class NumericQueryAnchors {

    private static final Pattern NUMERIC_ANCHOR = Pattern.compile(
            "(?<![\\p{N}])(?<number>\\d[\\d,]*(?:\\.\\d+)?)(?:\\s*)"
                    + "(?<unit>회|건|행|초|분|개|명|번|%|퍼센트)?(?![\\p{N}])");

    private NumericQueryAnchors() {
    }

    public static List<NumericAnchor> extract(String query) {
        Matcher matcher = NUMERIC_ANCHOR.matcher(SearchTokenNormalizer.normalize(query));
        Set<NumericAnchor> anchors = new LinkedHashSet<>();
        while (matcher.find()) {
            String unit = matcher.group("unit");
            anchors.add(new NumericAnchor(normalizeNumber(matcher.group("number")), unit));
        }
        return List.copyOf(anchors);
    }

    public static boolean hasContextualMatch(String query, String content) {
        return extract(query).stream()
                .filter(NumericAnchor::hasUnit)
                .anyMatch(anchor -> anchor.matches(content));
    }

    private static String normalizeNumber(String value) {
        return new BigDecimal(value.replace(",", ""))
                .stripTrailingZeros()
                .toPlainString();
    }

    public record NumericAnchor(String number, String unit) {

        public boolean hasUnit() {
            return unit != null && !unit.isBlank();
        }

        public boolean matches(String content) {
            if (!hasUnit()) {
                return false;
            }
            String normalizedContent = SearchTokenNormalizer.normalize(content)
                    .replaceAll("(?<=\\d),(?=\\d)", "");
            Pattern exact = Pattern.compile(
                    "(?<!\\d)" + Pattern.quote(number) + "(?!\\d)\\s*" + Pattern.quote(unit));
            return exact.matcher(normalizedContent).find();
        }
    }
}
