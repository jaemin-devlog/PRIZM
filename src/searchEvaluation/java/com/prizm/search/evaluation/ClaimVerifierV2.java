package com.prizm.search.evaluation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Evaluation-only safety-first verification over an already selected local window. */
final class ClaimVerifierV2 {
    enum Status { SUPPORTED, CONTRADICTED, UNCERTAIN }

    enum Reason {
        DIRECT_LOCAL_SUPPORT,
        STRUCTURED_TECHNOLOGY_DECLARATION,
        EXPLICIT_NEGATION_OR_NON_ADOPTION,
        EXPLICIT_OTHER_ACTOR_OR_SELF_DENIAL,
        NUMERIC_OR_UNIT_CONTRADICTION,
        METRIC_CONTEXT_CONTRADICTION,
        STATE_CONTRADICTION,
        REQUIRED_ENTITY_MISSING,
        INCOMPLETE_EXPLANATION_WINDOW,
        NO_DIRECT_LOCAL_SUPPORT
    }

    record Decision(Status status, Reason reason, List<String> groundedSentenceIds) {}

    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?。！？])\\s+|\\R+");
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#._-]*");
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9+#._-]*");
    private static final Pattern NUMBER = Pattern.compile(
            "(?<![\\p{L}\\p{N}])(\\d[\\d,]*(?:\\.\\d+)?)\\s*"
                    + "(ms|밀리초|초|분|시간|%|퍼센트|gb|기가바이트|tb|테라바이트|건|개|회|명)?",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DECLARATIVE = Pattern.compile(
            ".*(?:습니다|합니다|했습니다|했습니다|했다|하였다|됩니다|됐다|입니다|재개합니다)\\s*[.!。！]*$");
    private static final Pattern STRUCTURED_TECH = Pattern.compile(
            "(?:사용\\s*기술|기술(?:\\s*스택)?)\\s*:", Pattern.CASE_INSENSITIVE);
    private static final Pattern SELF_DENIAL = Pattern.compile(
            "(?:나는|내가|저는|제가|본인은|본인이).{0,100}(?:하지\\s*(?:않|못)|담당하지\\s*않|아니)");
    private static final Pattern NOT_ADOPTED = Pattern.compile(
            "(?:도입|채택|사용|운영|적용|포함|배포|구현|개발|생성).{0,28}(?:하지\\s*(?:않|못)|않았습니다|않았다)");
    private static final Pattern EXTERNAL_ACTOR = Pattern.compile(
            "(?:외부|파트너|업체|다른\\s+(?:팀|조직|사람)).{0,80}(?:구현|개발|생성|담당)");
    private static final Pattern PROTOTYPE_ONLY = Pattern.compile(
            "(?:prototype|프로토타입|실험).{0,32}(?:에서만|에만|한정|검증).{0,48}"
                    + "(?:production|운영|실제).{0,32}(?:않|제외|미적용|포함하지)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EXPLANATION_QUERY = Pattern.compile("(?:어떻게|어떤\\s+[^?]{0,30}(?:절차|방법))");
    private static final Pattern PROCEDURAL_LINK = Pattern.compile(
            "(?:으로|로|부터|통해|위해|하도록|하여|해서|해\\s|하고\\s|며\\s)");
    private static final Pattern SUFFIX = Pattern.compile(
            "(?:했나요|했는가|인가요|있나요|습니까|했습니다|합니다|했다|하였다|되는|된|하며|하고|하여|해서|에서|으로|부터|에는|은|는|을|를|이|가|에|의|와|과|로)$");
    private static final Set<String> STOP = Set.of(
            "경험", "근거", "직접", "실제", "프로젝트", "어떻게", "어떤", "있", "사용",
            "구현", "적용", "운영", "개선", "복구", "생산", "production");

    Decision verify(String query, String localWindow) {
        String normalizedQuery = normalize(query);
        String normalizedWindow = normalize(localWindow);
        List<String> sentences = sentences(localWindow);
        Set<String> queryTerms = terms(normalizedQuery);
        Set<String> windowTerms = terms(normalizedWindow);
        Set<String> identifiers = identifiers(normalizedQuery);
        boolean entityBound = identifiers.stream().allMatch(id -> containsIdentifier(normalizedWindow, id));
        boolean topicBound = entityBound && overlap(queryTerms, windowTerms) > 0;

        if (entityBound && topicBound && SELF_DENIAL.matcher(normalizedWindow).find()) {
            return contradicted(Reason.EXPLICIT_OTHER_ACTOR_OR_SELF_DENIAL, sentences, queryTerms, identifiers);
        }
        if (entityBound && topicBound && EXTERNAL_ACTOR.matcher(normalizedWindow).find()
                && normalizedWindow.matches(".*(?:않|못|아니).*")) {
            return contradicted(Reason.EXPLICIT_OTHER_ACTOR_OR_SELF_DENIAL, sentences, queryTerms, identifiers);
        }
        if (entityBound && topicBound && NOT_ADOPTED.matcher(normalizedWindow).find()) {
            return contradicted(Reason.EXPLICIT_NEGATION_OR_NON_ADOPTION, sentences, queryTerms, identifiers);
        }
        if (requiresProduction(normalizedQuery) && entityBound && topicBound
                && PROTOTYPE_ONLY.matcher(normalizedWindow).find()) {
            return contradicted(Reason.STATE_CONTRADICTION, sentences, queryTerms, identifiers);
        }

        Decision numeric = numericDecision(normalizedQuery, normalizedWindow, sentences, queryTerms, identifiers);
        if (numeric != null) {
            return numeric;
        }
        if (!entityBound) {
            return new Decision(Status.UNCERTAIN, Reason.REQUIRED_ENTITY_MISSING, List.of());
        }

        boolean structured = STRUCTURED_TECH.matcher(normalizedWindow).find() && !identifiers.isEmpty();
        boolean affirmative = sentences.stream().anyMatch(sentence -> DECLARATIVE.matcher(normalize(sentence)).matches());
        int commonTerms = overlap(queryTerms, windowTerms);
        boolean directlyBound = structured || commonTerms >= (identifiers.isEmpty() ? 2 : 1);
        if (EXPLANATION_QUERY.matcher(normalizedQuery).find()
                && directlyBound
                && !PROCEDURAL_LINK.matcher(normalizedWindow).find()) {
            return new Decision(Status.UNCERTAIN, Reason.INCOMPLETE_EXPLANATION_WINDOW, List.of());
        }
        if (structured) {
            return supported(Reason.STRUCTURED_TECHNOLOGY_DECLARATION, sentences, queryTerms, identifiers);
        }
        if (affirmative && directlyBound) {
            return supported(Reason.DIRECT_LOCAL_SUPPORT, sentences, queryTerms, identifiers);
        }
        return new Decision(Status.UNCERTAIN, Reason.NO_DIRECT_LOCAL_SUPPORT, List.of());
    }

    private Decision numericDecision(
            String query,
            String window,
            List<String> sentences,
            Set<String> queryTerms,
            Set<String> identifiers) {
        List<Quantity> expected = quantities(query);
        if (expected.isEmpty()) {
            return null;
        }
        List<Quantity> actual = quantities(window);
        boolean sameValues = expected.stream().allMatch(value -> actual.stream().anyMatch(value::sameValue));
        boolean exact = expected.stream().allMatch(actual::contains);
        boolean topical = identifiers.stream().allMatch(id -> containsIdentifier(window, id))
                && overlap(contextTerms(query), contextTerms(window)) > 0;
        if (!exact && topical) {
            return contradicted(Reason.NUMERIC_OR_UNIT_CONTRADICTION, sentences, queryTerms, identifiers);
        }
        Set<String> queryContext = contextTerms(query);
        Set<String> windowContext = contextTerms(window);
        if (exact && sameValues && queryContext.size() >= 2 && windowContext.size() >= 2
                && overlap(queryContext, windowContext) == 0) {
            return contradicted(Reason.METRIC_CONTEXT_CONTRADICTION, sentences, queryTerms, identifiers);
        }
        return null;
    }

    private Decision supported(
            Reason reason, List<String> sentences, Set<String> queryTerms, Set<String> identifiers) {
        return new Decision(Status.SUPPORTED, reason, grounded(sentences, queryTerms, identifiers));
    }

    private Decision contradicted(
            Reason reason, List<String> sentences, Set<String> queryTerms, Set<String> identifiers) {
        return new Decision(Status.CONTRADICTED, reason, grounded(sentences, queryTerms, identifiers));
    }

    private List<String> grounded(
            List<String> sentences, Set<String> queryTerms, Set<String> identifiers) {
        List<String> ids = new ArrayList<>();
        for (int index = 0; index < sentences.size(); index++) {
            String sentence = normalize(sentences.get(index));
            boolean idMatch = identifiers.isEmpty()
                    || identifiers.stream().anyMatch(id -> containsIdentifier(sentence, id));
            if (idMatch && overlap(queryTerms, terms(sentence)) > 0) {
                ids.add("S" + (index + 1));
            }
        }
        return ids.isEmpty() && !sentences.isEmpty() ? List.of("S1") : List.copyOf(ids);
    }

    private Set<String> contextTerms(String text) {
        Matcher number = NUMBER.matcher(text);
        return terms(number.find() ? text.substring(0, number.start()) : text);
    }

    private List<Quantity> quantities(String text) {
        List<Quantity> values = new ArrayList<>();
        Matcher matcher = NUMBER.matcher(text.replace(",", ""));
        while (matcher.find()) {
            values.add(new Quantity(new BigDecimal(matcher.group(1)), canonicalUnit(matcher.group(2))));
        }
        return values;
    }

    private Set<String> identifiers(String text) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = IDENTIFIER.matcher(text);
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
        Matcher matcher = TOKEN.matcher(text.toLowerCase(Locale.ROOT));
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

    private List<String> sentences(String window) {
        return List.of(SENTENCE.split(window.strip())).stream()
                .map(String::strip)
                .filter(sentence -> !sentence.isBlank())
                .limit(3)
                .toList();
    }

    private int overlap(Set<String> left, Set<String> right) {
        return (int) left.stream().filter(right::contains).count();
    }

    private boolean containsIdentifier(String text, String identifier) {
        return identifiers(text).contains(identifier);
    }

    private boolean requiresProduction(String query) {
        return query.contains("production") || query.contains("운영") || query.contains("실제");
    }

    private String canonicalUnit(String unit) {
        if (unit == null) return "";
        return switch (unit.toLowerCase(Locale.ROOT)) {
            case "밀리초" -> "ms";
            case "퍼센트" -> "%";
            case "기가바이트" -> "gb";
            case "테라바이트" -> "tb";
            default -> unit.toLowerCase(Locale.ROOT);
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.strip().replaceAll("\\s+", " ");
    }

    private record Quantity(BigDecimal value, String unit) {
        private Quantity {
            value = value.stripTrailingZeros();
        }

        boolean sameValue(Quantity other) {
            return value.compareTo(other.value) == 0;
        }
    }
}
