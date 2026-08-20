package com.prizm.search.evaluation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Evaluation-only bounded preselector over one already retrieved candidate chunk. */
final class LocalClaimWindowPreselector {
    record Selection(int firstSentence, int lastSentence, String window, int score) {}

    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?。！？])\\s+|\\R+");
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#._-]*");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9+#._-]*");
    private static final Pattern NUMBER = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(\\d[\\d,]*(?:\\.\\d+)?)\\s*"
                    + "(ms|밀리초|초|분|시간|%|퍼센트|gb|기가바이트|tb|테라바이트|건|개|회|명)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLANATION = Pattern.compile("(?:어떻게|어떤\\s+[^?]{0,30}(?:절차|방법))");
    private static final Pattern SUFFIX = Pattern.compile(
            "(?:했나요|했는가|인가요|있나요|습니까|했습니다|합니다|했다|하였다|되는|된|하며|하고|하여|해서|에서|으로|부터|에는|은|는|을|를|이|가|에|의|와|과|로)$");
    private static final Set<String> STOP = Set.of(
            "경험", "근거", "직접", "실제", "프로젝트", "어떻게", "어떤", "있", "사용",
            "구현", "적용", "운영", "개선", "복구", "생산", "production");

    Selection select(String query, String candidateContent) {
        List<String> sentences = sentences(candidateContent);
        if (sentences.isEmpty()) {
            return new Selection(0, 0, "", Integer.MIN_VALUE);
        }
        QuerySignal signal = signal(query);
        Selection best = null;
        for (int first = 0; first < sentences.size(); first++) {
            for (int length = 1; length <= 3 && first + length <= sentences.size(); length++) {
                List<String> slice = sentences.subList(first, first + length);
                int score = score(signal, slice, length);
                Selection candidate = new Selection(first + 1, first + length, String.join(" ", slice), score);
                if (best == null || candidate.score() > best.score()) {
                    best = candidate;
                }
            }
        }
        return best;
    }

    private int score(QuerySignal signal, List<String> sentences, int length) {
        String window = String.join(" ", sentences);
        Set<String> windowIdentifiers = identifiers(window);
        Set<String> windowTerms = terms(window);
        int identifierMatches = (int) signal.identifiers().stream().filter(windowIdentifiers::contains).count();
        int quantityMatches = (int) signal.quantities().stream().filter(value -> quantities(window).stream()
                .anyMatch(value::matches)).count();
        int termMatches = overlap(signal.terms(), windowTerms);
        int score = identifierMatches * 30 + quantityMatches * 24 + termMatches * 3 - length;
        if (signal.explanation()) {
            long supportingSentences = sentences.stream()
                    .map(this::terms)
                    .filter(sentenceTerms -> overlap(signal.terms(), sentenceTerms) > 0)
                    .count();
            if (supportingSentences > 1) {
                score += (int) supportingSentences * 12;
            }
        }
        return score;
    }

    private QuerySignal signal(String query) {
        return new QuerySignal(identifiers(query), terms(query), quantities(query), EXPLANATION.matcher(query).find());
    }

    private List<String> sentences(String content) {
        List<String> values = new ArrayList<>();
        for (String sentence : SENTENCE.split(content == null ? "" : content.strip())) {
            String normalized = sentence.strip();
            if (!normalized.isBlank()) {
                values.add(normalized);
            }
        }
        return List.copyOf(values);
    }

    private Set<String> identifiers(String text) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = IDENTIFIER.matcher(text == null ? "" : text);
        while (matcher.find()) {
            String value = matcher.group().toLowerCase(Locale.ROOT);
            if (!value.equals("production") && !value.matches("\\d+(?:ms|gb|tb)")) {
                values.add(value);
            }
        }
        return values;
    }

    private Set<String> terms(String text) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher((text == null ? "" : text).toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String value = stem(matcher.group());
            if (value.length() >= 2 && !STOP.contains(value) && !value.matches("\\d.*")) {
                values.add(value);
            }
        }
        return values;
    }

    private String stem(String token) {
        String result = token;
        for (int count = 0; count < 2; count++) {
            String stripped = SUFFIX.matcher(result).replaceFirst("");
            if (stripped.equals(result) || stripped.length() < 2) {
                break;
            }
            result = stripped;
        }
        return result;
    }

    private List<Quantity> quantities(String text) {
        List<Quantity> values = new ArrayList<>();
        Matcher matcher = NUMBER.matcher((text == null ? "" : text).replace(",", ""));
        while (matcher.find()) {
            values.add(new Quantity(new BigDecimal(matcher.group(1)), unit(matcher.group(2))));
        }
        return values;
    }

    private int overlap(Set<String> first, Set<String> second) {
        return (int) first.stream().filter(second::contains).count();
    }

    private String unit(String value) {
        if (value == null) return "";
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "밀리초" -> "ms";
            case "퍼센트" -> "%";
            case "기가바이트" -> "gb";
            case "테라바이트" -> "tb";
            default -> value.toLowerCase(Locale.ROOT);
        };
    }

    private record QuerySignal(Set<String> identifiers, Set<String> terms, List<Quantity> quantities, boolean explanation) {}

    private record Quantity(BigDecimal value, String unit) {
        private Quantity {
            value = value.stripTrailingZeros();
        }

        boolean matches(Quantity other) {
            return value.compareTo(other.value) == 0 && unit.equals(other.unit);
        }
    }
}
