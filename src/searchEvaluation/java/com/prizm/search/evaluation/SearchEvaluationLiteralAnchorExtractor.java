package com.prizm.search.evaluation;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Evaluation-only generic literal anchor extraction for PRZ-016 P6 H2. */
final class SearchEvaluationLiteralAnchorExtractor {

    private static final Pattern NUMBER_WITH_UNIT = Pattern.compile(
            "(?<![0-9])(?:[0-9]{1,3}(?:,[0-9]{3})+|[0-9]+(?:\\.[0-9]+)?)"
                    + "(?:회|건|행|명|개|초|분|시간|ms|s|mb|gb|tb|%)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern NUMERIC_SLASH = Pattern.compile(
            "(?<![0-9])(?:[0-9]+/){2,}[0-9]+(?![0-9])");
    private static final Pattern ENGLISH_PHRASE = Pattern.compile(
            "(?<![A-Za-z0-9+#._/-])"
                    + "[A-Za-z][A-Za-z0-9+#._/-]*"
                    + "(?:[ \\t]+(?:[A-Za-z][A-Za-z0-9+#._/-]*|[vV]?[0-9]+\\.[0-9]+))+"
                    + "(?![A-Za-z0-9+#._/-])");
    private static final Pattern SINGLE_IDENTIFIER = Pattern.compile(
            "(?<![A-Za-z0-9+#._/-])(?:"
                    + "[A-Z][a-z0-9]+(?:[A-Z][A-Za-z0-9]*)+"
                    + "|[A-Z][A-Z0-9]{1,}"
                    + "|[A-Za-z]+[0-9]+[A-Za-z0-9]*"
                    + "|[A-Za-z][A-Za-z0-9]*[-/][A-Za-z0-9][A-Za-z0-9/-]*"
                    + ")(?![A-Za-z0-9+#._/-])");
    private static final Set<String> GENERIC_ACRONYMS = Set.of("api", "db", "ui", "ux", "id");

    List<Anchor> extract(String query) {
        String source = Normalizer.normalize(Objects.requireNonNullElse(query, ""), Normalizer.Form.NFKC);
        List<LocatedAnchor> candidates = new ArrayList<>();
        collect(candidates, source, NUMBER_WITH_UNIT, AnchorType.NUMERIC_UNIT);
        collect(candidates, source, NUMERIC_SLASH, AnchorType.CODE_IDENTIFIER);
        collect(candidates, source, ENGLISH_PHRASE, AnchorType.MULTI_TOKEN_IDENTIFIER);
        collect(candidates, source, SINGLE_IDENTIFIER, AnchorType.IDENTIFIER);
        candidates.removeIf(candidate -> GENERIC_ACRONYMS.contains(normalize(candidate.value())));
        candidates.sort(Comparator
                .comparingInt((LocatedAnchor anchor) -> anchor.end() - anchor.start()).reversed()
                .thenComparingInt(LocatedAnchor::start));

        List<LocatedAnchor> maximal = new ArrayList<>();
        for (LocatedAnchor candidate : candidates) {
            boolean contained = maximal.stream().anyMatch(existing ->
                    existing.start() <= candidate.start() && existing.end() >= candidate.end());
            if (!contained) {
                maximal.add(candidate);
            }
        }
        maximal.sort(Comparator.comparingInt(LocatedAnchor::start));
        Map<String, Anchor> unique = new LinkedHashMap<>();
        for (LocatedAnchor candidate : maximal) {
            String normalized = normalize(candidate.value());
            if (!normalized.isBlank()) {
                unique.putIfAbsent(normalized, new Anchor(candidate.value().strip(), normalized, candidate.type()));
            }
        }
        return List.copyOf(unique.values());
    }

    static String normalize(String value) {
        return Normalizer.normalize(Objects.requireNonNullElse(value, ""), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replace('\u2010', '-')
                .replace('\u2011', '-')
                .replace('\u2012', '-')
                .replace('\u2013', '-')
                .replace('\u2014', '-')
                .replaceAll("\\s*([/-])\\s*", "$1")
                .replaceAll("[\\p{Punct}&&[^+#._/-]]+", " ")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static void collect(
            List<LocatedAnchor> target,
            String source,
            Pattern pattern,
            AnchorType type) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) {
            target.add(new LocatedAnchor(matcher.group(), matcher.start(), matcher.end(), type));
        }
    }

    enum AnchorType {
        NUMERIC_UNIT,
        MULTI_TOKEN_IDENTIFIER,
        IDENTIFIER,
        CODE_IDENTIFIER
    }

    record Anchor(String extracted, String normalized, AnchorType type) {
    }

    private record LocatedAnchor(String value, int start, int end, AnchorType type) {
    }
}
