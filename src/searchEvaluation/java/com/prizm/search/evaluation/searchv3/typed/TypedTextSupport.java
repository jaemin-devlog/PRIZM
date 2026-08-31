package com.prizm.search.evaluation.searchv3.typed;

import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.CodePointSpan;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DateInterval;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DatePrecision;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.Direction;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DirectionMark;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.Qualifier;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class TypedTextSupport {

    static final String NUMBER_START_BOUNDARY = "(?<![\\p{L}\\p{N}_,，.])";
    static final String NUMBER = "(?:\\p{Nd}{1,3}(?:[,，]\\p{Nd}{3})+|\\p{Nd}+)(?:\\.\\p{Nd}+)?";
    static final String UNIT = "(?:milliseconds?|minutes?|seconds?|months?|hours?|years?|days?|secs?|mins?|hrs?"
            + "|sec|min|hr|ms|s|개월|시간|명|건|개|회|년|분|초|일|%)";
    static final String UNIT_END_BOUNDARY = "(?:(?![\\p{L}\\p{N}_])"
            + "|(?=(?:의|이|가|은|는|을|를|에|로|와|과|도|만|였|인|이상|초과|이하|미만|감소|증가)"
            + "(?:\\s|$|[\\p{L}]))|(?=decreas|reduc|increas))";
    static final String DATE_ATOM = "(?:\\p{Nd}{4}[-./]\\p{Nd}{1,2}[-./]\\p{Nd}{1,2}|\\p{Nd}{4}\\s*년(?:\\s*\\p{Nd}{1,2}\\s*월)?|\\p{Nd}{4}[-./]\\p{Nd}{1,2})";

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}]+(?:[-_.][\\p{L}\\p{N}]+)*");
    private static final Pattern QUALIFIER_COMPARISON_TOKEN = Pattern.compile("[\\p{L}\\p{N}]+");
    private static final Pattern FULL_DATE = Pattern.compile("(\\d{4})[-./](\\d{1,2})[-./](\\d{1,2})");
    private static final Pattern YEAR_MONTH_SEPARATED = Pattern.compile("(\\d{4})[-./](\\d{1,2})");
    private static final Pattern YEAR_MONTH_KOREAN = Pattern.compile("(\\d{4})\\s*년\\s*(\\d{1,2})\\s*월");
    private static final Pattern YEAR_KOREAN = Pattern.compile("(\\d{4})\\s*년");
    private static final Pattern DIRECTION = Pattern.compile(
            "(?iu)(감소|증가|decreas(?:e|ed|es|ing)|reduc(?:e|ed|es|ing|tion)|increas(?:e|ed|es|ing))");
    private static final Pattern KOREAN_ADNOMINAL = Pattern.compile(".*(?:된|하는|한|할|했던|되는|될|같은)$");
    private static final Set<String> LEFT_SKIP = Set.of(
            "of", "on", "at", "in", "from", "was", "were", "is", "are", "exactly", "precisely",
            "정확히", "뒤", "후");
    private static final Set<String> DETERMINERS = Set.of("a", "an", "the");
    private static final Set<String> RIGHT_STOP = Set.of(
            "in", "on", "at", "from", "to", "before", "after", "and", "or", "was", "were", "is", "are",
            "decrease", "decreased", "decreases", "decreasing", "increase", "increased", "increases", "increasing",
            "감소", "증가");
    /*
     * Only alternating case/topic particles are stripped. Non-alternating suffixes such as 만, 도, 의, 에 and
     * locative compounds are lexically ambiguous without a morphological analyzer (for example 불만). Keeping them
     * is the fail-closed choice. Hangul attachments must also obey 받침 allomorphy; a Latin/Hangul script boundary is
     * independently strong evidence of an attached particle (for example operations를).
     */
    private static final List<String> HIGH_CONFIDENCE_KOREAN_PARTICLES =
            List.of("으로", "은", "는", "이", "가", "을", "를", "로", "와", "과");
    private static final List<String> BOUNDARY_ONLY_KOREAN_PARTICLES =
            List.of("으로부터", "에게서", "에서는", "에서", "에게", "으로", "에", "로");
    private static final Set<String> KOREAN_BOUNDARY_PARTICLES = Set.of(
            "으로부터", "에게서", "에서는", "에서", "에게", "으로", "은", "는", "이", "가", "을", "를", "에", "로");

    private TypedTextSupport() {
    }

    static CodePointSpan span(String text, int charStart, int charEnd, int codePointBase) {
        int start = codePointBase + text.codePointCount(0, charStart);
        int end = codePointBase + text.codePointCount(0, charEnd);
        return new CodePointSpan(text.substring(charStart, charEnd), start, end);
    }

    static String normalizeCaptured(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).trim();
    }

    static String normalizeUnit(String value) {
        return normalizeCaptured(value).replaceAll("\\s+", "");
    }

    static String normalizeLiteral(String value) {
        String normalized = normalizeCaptured(value);
        return normalized.replaceAll("[\\s._-]+", "");
    }

    static boolean isDurationUnit(String normalizedUnit) {
        return Set.of(
                        "millisecond", "milliseconds", "ms",
                        "second", "seconds", "sec", "secs", "s",
                        "minute", "minutes", "min", "mins",
                        "hour", "hours", "hr", "hrs",
                        "day", "days", "month", "months", "year", "years",
                        "개월", "시간", "년", "분", "초", "일")
                .contains(normalizedUnit);
    }

    /**
     * Compares grounded qualifiers without changing their stored normalized value or source span.
     * A non-empty required token sequence must occur contiguously and in order inside the observed
     * sequence. Empty qualifiers remain comparable only with another empty qualifier.
     */
    static boolean qualifierCompatible(Qualifier required, Qualifier observed) {
        Objects.requireNonNull(required, "required qualifier");
        Objects.requireNonNull(observed, "observed qualifier");
        boolean requiredEmpty = required.normalized().isBlank();
        boolean observedEmpty = observed.normalized().isBlank();
        if (requiredEmpty || observedEmpty) {
            return requiredEmpty && observedEmpty;
        }

        List<String> requiredTokens = qualifierComparisonTokens(required);
        List<String> observedTokens = qualifierComparisonTokens(observed);
        if (requiredTokens.isEmpty() || observedTokens.isEmpty()
                || requiredTokens.size() > observedTokens.size()) {
            return false;
        }
        for (int start = 0; start <= observedTokens.size() - requiredTokens.size(); start++) {
            if (observedTokens.subList(start, start + requiredTokens.size()).equals(requiredTokens)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> qualifierComparisonTokens(Qualifier qualifier) {
        List<String> result = new ArrayList<>();
        for (String groundedToken : qualifier.orderedTokens()) {
            Matcher matcher = QUALIFIER_COMPARISON_TOKEN.matcher(normalizeCaptured(groundedToken));
            while (matcher.find()) {
                result.add(matcher.group());
            }
        }
        return List.copyOf(result);
    }

    static BigDecimal parseNumber(String surface) {
        String normalized = normalizeCaptured(surface).replace(",", "").replace("，", "");
        StringBuilder ascii = new StringBuilder();
        normalized.codePoints().forEach(codePoint -> {
            if (Character.isDigit(codePoint)) {
                ascii.append(Character.digit(codePoint, 10));
            }
            else {
                ascii.appendCodePoint(codePoint);
            }
        });
        return new BigDecimal(ascii.toString()).stripTrailingZeros();
    }

    static List<BigInteger> parseSegments(String surface) {
        String normalized = normalizeCaptured(surface);
        List<BigInteger> result = new ArrayList<>();
        for (String segment : normalized.split("\\.")) {
            StringBuilder ascii = new StringBuilder();
            segment.codePoints().forEach(codePoint -> ascii.append(Character.digit(codePoint, 10)));
            result.add(new BigInteger(ascii.toString()));
        }
        return List.copyOf(result);
    }

    static DateInterval parseDateAtom(String surface) {
        String normalized = normalizeCaptured(surface);
        try {
            Matcher full = FULL_DATE.matcher(normalized);
            if (full.matches()) {
                LocalDate date = LocalDate.of(
                        Integer.parseInt(full.group(1)),
                        Integer.parseInt(full.group(2)),
                        Integer.parseInt(full.group(3)));
                return new DateInterval(date, date, DatePrecision.FULL_DATE);
            }
            Matcher koreanMonth = YEAR_MONTH_KOREAN.matcher(normalized);
            if (koreanMonth.matches()) {
                YearMonth month = YearMonth.of(
                        Integer.parseInt(koreanMonth.group(1)), Integer.parseInt(koreanMonth.group(2)));
                return new DateInterval(month.atDay(1), month.atEndOfMonth(), DatePrecision.YEAR_MONTH);
            }
            Matcher separatedMonth = YEAR_MONTH_SEPARATED.matcher(normalized);
            if (separatedMonth.matches()) {
                YearMonth month = YearMonth.of(
                        Integer.parseInt(separatedMonth.group(1)), Integer.parseInt(separatedMonth.group(2)));
                return new DateInterval(month.atDay(1), month.atEndOfMonth(), DatePrecision.YEAR_MONTH);
            }
            Matcher year = YEAR_KOREAN.matcher(normalized);
            if (year.matches()) {
                int value = Integer.parseInt(year.group(1));
                return new DateInterval(LocalDate.of(value, 1, 1), LocalDate.of(value, 12, 31), DatePrecision.YEAR);
            }
            return null;
        }
        catch (DateTimeException | NumberFormatException ignored) {
            return null;
        }
    }

    static DateInterval range(DateInterval start, DateInterval end) {
        if (start == null || end == null || start.precision() != end.precision()
                || end.endInclusive().isBefore(start.startInclusive())) {
            return null;
        }
        return new DateInterval(start.startInclusive(), end.endInclusive(), start.precision());
    }

    static Qualifier leftQualifier(String text, int coreStart, int codePointBase, int maximumTokens) {
        return leftQualifier(text, coreStart, codePointBase, maximumTokens, false);
    }

    static Qualifier leftQuantityQualifier(String text, int coreStart, int codePointBase, int maximumTokens) {
        return leftQualifier(text, coreStart, codePointBase, maximumTokens, true);
    }

    private static Qualifier leftQualifier(
            String text,
            int coreStart,
            int codePointBase,
            int maximumTokens,
            boolean requireSameScript) {
        int clauseStart = clauseStart(text, coreStart);
        List<WordToken> tokens = words(text, clauseStart, coreStart);
        List<WordToken> selected = new ArrayList<>();
        Script firstScript = null;
        for (int index = tokens.size() - 1; index >= 0 && selected.size() < maximumTokens; index--) {
            WordToken token = tokens.get(index);
            if (LEFT_SKIP.contains(token.normalized())) {
                if (selected.isEmpty()) {
                    continue;
                }
                break;
            }
            if (DETERMINERS.contains(token.normalized())) {
                break;
            }
            if (!selected.isEmpty() && KOREAN_BOUNDARY_PARTICLES.contains(token.particle())) {
                break;
            }
            if (!selected.isEmpty() && KOREAN_ADNOMINAL.matcher(token.normalized()).matches()) {
                break;
            }
            if (requireSameScript && selected.size() == 2 && index > 0) {
                WordToken preceding = tokens.get(index - 1);
                boolean predicateBoundary = Set.of("을", "를").contains(preceding.particle());
                boolean adnominalBoundary = preceding.normalized().equals("같은");
                if (predicateBoundary || adnominalBoundary) {
                    break;
                }
            }
            Script tokenScript = script(token.normalized());
            if (requireSameScript && firstScript != null && tokenScript != firstScript) {
                break;
            }
            selected.add(token);
            if (firstScript == null) {
                firstScript = tokenScript;
            }
        }
        selected.sort(Comparator.comparingInt(WordToken::charStart));
        return qualifier(text, selected, codePointBase);
    }

    static Qualifier rightQualifier(String text, int coreEnd, int codePointBase, int maximumTokens) {
        int clauseEnd = clauseEnd(text, coreEnd);
        List<WordToken> tokens = words(text, coreEnd, clauseEnd);
        List<WordToken> selected = new ArrayList<>();
        for (WordToken token : tokens) {
            if (token.normalized().equals("의") && selected.isEmpty()) {
                continue;
            }
            if (RIGHT_STOP.contains(token.normalized())) {
                break;
            }
            if (token.normalized().isBlank()) {
                continue;
            }
            selected.add(token);
            if (selected.size() >= maximumTokens || Set.of("을", "를", "이", "가", "은", "는").contains(token.particle())) {
                break;
            }
        }
        return qualifier(text, selected, codePointBase);
    }

    /** Builds a qualifier only from the caller-provided, source-grounded character range. */
    static Qualifier boundedQualifier(
            String text,
            int charStart,
            int charEnd,
            int codePointBase,
            int maximumTokens) {
        if (charStart < 0 || charEnd <= charStart || charEnd > text.length() || maximumTokens <= 0) {
            return Qualifier.empty();
        }
        List<WordToken> tokens = new ArrayList<>(words(text, charStart, charEnd));
        while (!tokens.isEmpty() && DETERMINERS.contains(tokens.get(0).normalized())) {
            tokens.remove(0);
        }
        if (tokens.isEmpty() || tokens.size() > maximumTokens) {
            return Qualifier.empty();
        }
        return qualifier(text, tokens, codePointBase);
    }

    static DirectionMark directionAround(String text, int coreStart, int coreEnd, int codePointBase) {
        int start = clauseStart(text, coreStart);
        int end = clauseEnd(text, coreEnd);
        Matcher matcher = DIRECTION.matcher(text);
        List<DirectionMatch> matches = new ArrayList<>();
        while (matcher.find()) {
            if (matcher.start() >= start && matcher.end() <= end) {
                String value = normalizeCaptured(matcher.group());
                Direction direction = value.startsWith("증가") || value.startsWith("increas")
                        ? Direction.INCREASE : Direction.DECREASE;
                matches.add(new DirectionMatch(direction, matcher.start(), matcher.end()));
            }
        }
        if (matches.isEmpty() || matches.stream().map(DirectionMatch::direction).distinct().count() != 1) {
            return DirectionMark.none();
        }
        DirectionMatch nearest = matches.stream()
                .min(Comparator.comparingInt(value -> distance(value.charStart(), value.charEnd(), coreStart, coreEnd)))
                .orElseThrow();
        return new DirectionMark(nearest.direction(), span(text, nearest.charStart(), nearest.charEnd(), codePointBase));
    }

    static boolean hasGenitiveAfter(String text, int charEnd) {
        int index = charEnd;
        while (index < text.length() && Character.isWhitespace(text.codePointAt(index))) {
            index += Character.charCount(text.codePointAt(index));
        }
        return index < text.length() && text.startsWith("의", index);
    }

    static boolean overlaps(List<CharRange> occupied, int start, int end) {
        return occupied.stream().anyMatch(range -> range.overlaps(start, end));
    }

    static List<CharRange> reserveUnsupportedScales(String text) {
        Pattern scale = Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])" + NUMBER + "\\s*[kmb](?![\\p{L}\\p{N}_])");
        List<CharRange> result = new ArrayList<>();
        Matcher matcher = scale.matcher(text);
        while (matcher.find()) {
            result.add(new CharRange(matcher.start(), matcher.end()));
        }
        return result;
    }

    private static Qualifier qualifier(String text, List<WordToken> tokens, int codePointBase) {
        if (tokens.isEmpty()) {
            return Qualifier.empty();
        }
        int charStart = tokens.get(0).charStart();
        int charEnd = tokens.get(tokens.size() - 1).trimmedCharEnd();
        if (charEnd <= charStart) {
            return Qualifier.empty();
        }
        List<String> normalized = tokens.stream().map(WordToken::normalized).filter(value -> !value.isBlank()).toList();
        if (normalized.isEmpty()) {
            return Qualifier.empty();
        }
        return new Qualifier(String.join(" ", normalized), normalized, span(text, charStart, charEnd, codePointBase));
    }

    private static List<WordToken> words(String text, int start, int end) {
        Matcher matcher = WORD.matcher(text);
        matcher.region(start, end);
        List<WordToken> result = new ArrayList<>();
        while (matcher.find()) {
            String raw = matcher.group();
            ParticleStrip stripped = stripParticle(raw);
            String normalized = normalizeCaptured(stripped.value());
            if (!normalized.isBlank()) {
                result.add(new WordToken(
                        matcher.start(), matcher.end(), matcher.end() - stripped.removedChars(), normalized, stripped.particle()));
            }
        }
        return result;
    }

    private static ParticleStrip stripParticle(String raw) {
        if (KOREAN_ADNOMINAL.matcher(normalizeCaptured(raw)).matches()) {
            return new ParticleStrip(raw, "", 0);
        }
        for (String particle : HIGH_CONFIDENCE_KOREAN_PARTICLES) {
            if (raw.length() > particle.length() && raw.endsWith(particle)) {
                String value = raw.substring(0, raw.length() - particle.length());
                if (value.codePoints().anyMatch(Character::isLetterOrDigit)
                        && highConfidenceParticleAttachment(value, particle)) {
                    return new ParticleStrip(value, particle, particle.length());
                }
            }
        }
        for (String particle : BOUNDARY_ONLY_KOREAN_PARTICLES) {
            if (raw.length() > particle.length() && raw.endsWith(particle)) {
                // Preserve the raw lexical token and span. This marker may stop a qualifier from crossing a
                // grammatical boundary, but it is not sufficient evidence to normalize the suffix away.
                return new ParticleStrip(raw, particle, 0);
            }
        }
        return new ParticleStrip(raw, "", 0);
    }

    private static boolean highConfidenceParticleAttachment(String stem, String particle) {
        int finalCodePoint = stem.codePointBefore(stem.length());
        if (finalCodePoint < 0xac00 || finalCodePoint > 0xd7a3) {
            return Character.isLetterOrDigit(finalCodePoint);
        }
        int jongseong = (finalCodePoint - 0xac00) % 28;
        boolean hasFinalConsonant = jongseong != 0;
        return switch (particle) {
            case "은", "이", "을", "과" -> hasFinalConsonant;
            case "는", "가", "를", "와" -> !hasFinalConsonant;
            case "으로" -> hasFinalConsonant && jongseong != 8;
            case "로" -> !hasFinalConsonant || jongseong == 8;
            default -> false;
        };
    }

    private static int clauseStart(String text, int before) {
        int result = 0;
        for (int index = 0; index < before; index++) {
            char value = text.charAt(index);
            if (value == '\n' || value == '\r' || value == '.' || value == '!' || value == '?' || value == ';' || value == ':') {
                result = index + 1;
            }
        }
        return result;
    }

    private static int clauseEnd(String text, int after) {
        for (int index = after; index < text.length(); index++) {
            char value = text.charAt(index);
            if (value == '\n' || value == '\r' || value == '.' || value == '!' || value == '?' || value == ';' || value == ':') {
                return index;
            }
        }
        return text.length();
    }

    private static int distance(int start, int end, int coreStart, int coreEnd) {
        if (end <= coreStart) {
            return coreStart - end;
        }
        if (start >= coreEnd) {
            return start - coreEnd;
        }
        return 0;
    }

    private static Script script(String value) {
        boolean latin = value.codePoints().anyMatch(codePoint -> codePoint <= 0x024f && Character.isLetter(codePoint));
        boolean hangul = value.codePoints().anyMatch(codePoint -> codePoint >= 0xac00 && codePoint <= 0xd7a3);
        if (latin && !hangul) {
            return Script.LATIN;
        }
        if (hangul && !latin) {
            return Script.HANGUL;
        }
        return Script.OTHER;
    }

    record CharRange(int start, int end) {
        boolean overlaps(int otherStart, int otherEnd) {
            return start < otherEnd && otherStart < end;
        }
    }

    private record WordToken(
            int charStart,
            int charEnd,
            int trimmedCharEnd,
            String normalized,
            String particle) {
    }

    private record ParticleStrip(String value, String particle, int removedChars) {
    }

    private record DirectionMatch(Direction direction, int charStart, int charEnd) {
    }

    private enum Script {
        LATIN,
        HANGUL,
        OTHER
    }
}
