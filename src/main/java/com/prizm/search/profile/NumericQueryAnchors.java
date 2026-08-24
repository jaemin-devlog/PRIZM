package com.prizm.search.profile;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 질의와 근거에서 정확히 비교할 숫자와 단위 anchor를 추출한다.
 *
 * <p>쉼표와 불필요한 소수 자릿수를 정규화하고 {@code 밀리초}/{@code ms},
 * {@code 퍼센트}/{@code %}처럼 같은 단위를 하나의 표현으로 맞춘다. 숫자만 우연히 같은
 * 근거를 구제하지 않도록 문맥 일치는 단위가 있는 anchor에만 허용한다.</p>
 */
public final class NumericQueryAnchors {

    private static final Pattern NUMERIC_ANCHOR = Pattern.compile(
            "(?<![\\p{N}])(?<number>\\d[\\d,]*(?:\\.\\d+)?)(?:\\s*)"
                    + "(?<unit>밀리초|ms|회|건|행|초|분|개|명|번|%|퍼센트)?(?![\\p{N}])");

    private NumericQueryAnchors() {
    }

    /** 문자열에서 중복을 제거한 정규화 숫자·단위 anchor를 등장 순서대로 반환한다. */
    public static List<NumericAnchor> extract(String query) {
        Matcher matcher = NUMERIC_ANCHOR.matcher(SearchTokenNormalizer.normalize(query));
        Set<NumericAnchor> anchors = new LinkedHashSet<>();
        while (matcher.find()) {
            String unit = normalizeUnit(matcher.group("unit"));
            anchors.add(new NumericAnchor(normalizeNumber(matcher.group("number")), unit));
        }
        return List.copyOf(anchors);
    }

    /** 질의의 숫자·단위 anchor 중 하나 이상이 근거에 정확히 있는지 확인한다. */
    public static boolean hasContextualMatch(String query, String content) {
        return extract(query).stream()
                .filter(NumericAnchor::hasUnit)
                .anyMatch(anchor -> anchor.matches(content));
    }

    /** 질의에 있는 모든 숫자·단위 anchor가 근거에 정확히 있는지 확인한다. */
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
