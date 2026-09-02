package com.prizm.search.v3.query.typed;

import static com.prizm.search.v3.query.typed.TypedTextSupport.DATE_ATOM;
import static com.prizm.search.v3.query.typed.TypedTextSupport.NUMBER;
import static com.prizm.search.v3.query.typed.TypedTextSupport.NUMBER_START_BOUNDARY;
import static com.prizm.search.v3.query.typed.TypedTextSupport.UNIT_END_BOUNDARY;
import static com.prizm.search.v3.query.typed.TypedTextSupport.UNIT;
import static com.prizm.search.v3.query.typed.TypedValueModel.CandidateObservation;
import static com.prizm.search.v3.query.typed.TypedValueModel.DateObservation;
import static com.prizm.search.v3.query.typed.TypedValueModel.Direction;
import static com.prizm.search.v3.query.typed.TypedValueModel.IdentifierNumberObservation;
import static com.prizm.search.v3.query.typed.TypedValueModel.LiteralIdentifierObservation;
import static com.prizm.search.v3.query.typed.TypedValueModel.QuantityObservation;
import static com.prizm.search.v3.query.typed.TypedValueModel.SourceSlice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts observations from atomic sourceText only; retrieval/context text is not accepted. */
public final class DeterministicTypedObservationExtractor {

    private static final String ENGLISH_WORD = "[\\p{L}][\\p{L}\\p{N}_-]*";
    private static final String ENGLISH_NOUN_PHRASE = ENGLISH_WORD + "(?:\\s+" + ENGLISH_WORD + "){0,5}?";
    private static final String ENGLISH_DIRECTION =
            "(?:decreas(?:e|ed|es|ing)|reduc(?:e|ed|es|ing|tion)|increas(?:e|ed|es|ing))";
    private static final String ENGLISH_DURATION_PREDICATE =
            "(?:last(?:s|ed|ing)?|continu(?:e|es|ed|ing)|run|runs|ran|running)";
    private static final String ENGLISH_TIME_UNIT =
            "(?:milliseconds?|minutes?|seconds?|months?|hours?|years?|days?|secs?|mins?|hrs?|sec|min|hr|ms)";

    private static final Pattern KOREAN_DATE_RANGE = Pattern.compile(
            "(?iu)(?<start>" + DATE_ATOM + ")\\s*부터\\s*(?<end>" + DATE_ATOM + ")\\s*까지");
    private static final Pattern ENGLISH_DATE_RANGE = Pattern.compile(
            "(?iu)from\\s+(?<start>" + DATE_ATOM + ")\\s+to\\s+(?<end>" + DATE_ATOM + ")");
    private static final Pattern YEAR_RANGE = Pattern.compile(
            "(?<!\\p{Nd})(?<start>\\p{Nd}{4})\\s*[~〜–—]\\s*(?<end>\\p{Nd}{4})(?!\\p{Nd})");
    private static final Pattern DATE = Pattern.compile("(?iu)(?<date>" + DATE_ATOM + ")");
    private static final Pattern QUANTITY_RANGE = Pattern.compile(
            "(?iu)" + NUMBER_START_BOUNDARY + NUMBER + "\\s*[~〜–—]\\s*" + NUMBER + "\\s*" + UNIT
                    + UNIT_END_BOUNDARY);
    private static final Pattern QUANTITY = Pattern.compile(
            "(?iu)" + NUMBER_START_BOUNDARY + "(?<number>" + NUMBER + ")\\s*(?<unit>" + UNIT + ")"
                    + UNIT_END_BOUNDARY);
    private static final Pattern PASSIVE_DIRECTIONAL_PERCENTAGE = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}_])(?<direction>" + ENGLISH_DIRECTION + ")\\s+by\\s+"
                    + NUMBER_START_BOUNDARY + "(?<number>" + NUMBER + ")\\s*(?<unit>%)(?![\\p{L}\\p{N}_])");
    private static final Pattern ENGLISH_DURATION = Pattern.compile(
            "(?iu)(?<![\\p{L}\\p{N}_])(?<predicate>" + ENGLISH_DURATION_PREDICATE + ")\\s+for\\s+"
                    + NUMBER_START_BOUNDARY + "(?<number>" + NUMBER + ")\\s*(?<unit>" + ENGLISH_TIME_UNIT + ")"
                    + UNIT_END_BOUNDARY);
    private static final Pattern BARE_ENGLISH_COUNT = Pattern.compile(
            "(?iu)" + NUMBER_START_BOUNDARY + "(?<number>" + NUMBER + ")"
                    + "(?!\\s*(?:" + UNIT + ")" + UNIT_END_BOUNDARY + ")\\s+"
                    + "(?<qualifier>" + ENGLISH_NOUN_PHRASE + ")"
                    + "(?=\\s+(?:in|on|at|after|before|during|when|while|from|to|and|or|with|for|as)\\b"
                    + "|\\s*[?.,!;]|$)");
    private static final Pattern PRECEDING_IDENTIFIER = Pattern.compile(
            "(?<identifier>[A-Za-z][A-Za-z+_-]{0,31})(?:\\s+|/)$");
    private static final Pattern SEPARATED_IDENTIFIER_NUMBER = Pattern.compile(
            "(?<![A-Za-z0-9_])(?<identifier>[A-Za-z][A-Za-z+_-]{0,31})(?:\\s+|/)"
                    + "(?<number>\\p{Nd}+(?:\\.\\p{Nd}+){0,3})(?![A-Za-z0-9_])");
    private static final Pattern V_IDENTIFIER_NUMBER = Pattern.compile(
            "(?<![A-Za-z0-9_])(?<identifier>[vV])(?<number>\\p{Nd}+(?:\\.\\p{Nd}+){0,3})(?![A-Za-z0-9_])");
    private static final Pattern QUOTED_LITERAL = Pattern.compile(
            "(?:\"(?<double>[^\"\\r\\n]+)\"|'(?<single>[^'\\r\\n]+)'|“(?<curly>[^”\\r\\n]+)”)"
    );
    private static final Pattern UNQUOTED_LITERAL = Pattern.compile(
            "(?<![A-Za-z0-9_])(?<literal>[A-Za-z][A-Za-z0-9]*(?:[-_.][A-Za-z0-9]+)*)(?![A-Za-z0-9_])");

    public List<CandidateObservation> extract(SourceSlice source) {
        Objects.requireNonNull(source, "source");
        String text = source.sourceText();
        List<TypedTextSupport.CharRange> occupied = new ArrayList<>(TypedTextSupport.reserveUnsupportedScales(text));
        List<CandidateObservation> result = new ArrayList<>();

        parseDateRanges(source, occupied, result);
        parseDates(source, occupied, result);
        reserveQuantityRanges(text, occupied);
        parseQuantities(source, occupied, result);
        parseIdentifierNumbers(source, occupied, result);
        parseLiterals(source, occupied, result);

        result.sort(Comparator.comparingInt(value -> value.span().startInclusive()));
        return List.copyOf(result);
    }

    public List<CandidateObservation> extractAll(List<SourceSlice> atomicSources) {
        Objects.requireNonNull(atomicSources, "atomicSources");
        List<CandidateObservation> result = new ArrayList<>();
        for (SourceSlice source : atomicSources) {
            result.addAll(extract(source));
        }
        result.sort(Comparator.comparing((CandidateObservation value) -> value.source().documentId())
                .thenComparing(value -> value.source().versionId())
                .thenComparingInt(value -> value.span().startInclusive()));
        return List.copyOf(result);
    }

    private void parseDateRanges(
            SourceSlice source,
            List<TypedTextSupport.CharRange> occupied,
            List<CandidateObservation> result) {
        parseDateRangePattern(source, KOREAN_DATE_RANGE, occupied, result);
        parseDateRangePattern(source, ENGLISH_DATE_RANGE, occupied, result);

        Matcher matcher = YEAR_RANGE.matcher(source.sourceText());
        while (matcher.find()) {
            if (TypedTextSupport.overlaps(occupied, matcher.start(), matcher.end())) {
                continue;
            }
            var interval = TypedTextSupport.range(
                    TypedTextSupport.parseDateAtom(matcher.group("start") + "년"),
                    TypedTextSupport.parseDateAtom(matcher.group("end") + "년"));
            addDateObservation(source, matcher.start(), matcher.end(), interval, occupied, result);
        }
    }

    private void parseDateRangePattern(
            SourceSlice source,
            Pattern pattern,
            List<TypedTextSupport.CharRange> occupied,
            List<CandidateObservation> result) {
        Matcher matcher = pattern.matcher(source.sourceText());
        while (matcher.find()) {
            if (TypedTextSupport.overlaps(occupied, matcher.start(), matcher.end())) {
                continue;
            }
            var interval = TypedTextSupport.range(
                    TypedTextSupport.parseDateAtom(matcher.group("start")),
                    TypedTextSupport.parseDateAtom(matcher.group("end")));
            addDateObservation(source, matcher.start(), matcher.end(), interval, occupied, result);
        }
    }

    private void parseDates(
            SourceSlice source,
            List<TypedTextSupport.CharRange> occupied,
            List<CandidateObservation> result) {
        Matcher matcher = DATE.matcher(source.sourceText());
        while (matcher.find()) {
            if (TypedTextSupport.overlaps(occupied, matcher.start(), matcher.end())) {
                continue;
            }
            addDateObservation(
                    source,
                    matcher.start(),
                    matcher.end(),
                    TypedTextSupport.parseDateAtom(matcher.group("date")),
                    occupied,
                    result);
        }
    }

    private void addDateObservation(
            SourceSlice source,
            int charStart,
            int charEnd,
            TypedValueModel.DateInterval interval,
            List<TypedTextSupport.CharRange> occupied,
            List<CandidateObservation> result) {
        if (interval != null) {
            result.add(new DateObservation(
                    TypedTextSupport.span(source.sourceText(), charStart, charEnd, source.documentCodePointStart()),
                    interval,
                    TypedTextSupport.leftQualifier(
                            source.sourceText(), charStart, source.documentCodePointStart(), 8),
                    source));
        }
        occupied.add(new TypedTextSupport.CharRange(charStart, charEnd));
    }

    /**
     * Candidate range observations are intentionally unsupported in v1: the runtime observation model contains one
     * exact value only. Reserving the complete range prevents either endpoint from being tail-matched as an exact
     * observation, so range-vs-range evaluation remains UNKNOWN rather than producing false evidence.
     */
    private void reserveQuantityRanges(String text, List<TypedTextSupport.CharRange> occupied) {
        Matcher matcher = QUANTITY_RANGE.matcher(text);
        while (matcher.find()) {
            if (!TypedTextSupport.overlaps(occupied, matcher.start(), matcher.end())) {
                occupied.add(new TypedTextSupport.CharRange(matcher.start(), matcher.end()));
            }
        }
    }

    private void parseQuantities(
            SourceSlice source,
            List<TypedTextSupport.CharRange> occupied,
            List<CandidateObservation> result) {
        String text = source.sourceText();
        parsePassiveDirectionalPercentages(source, occupied, result);
        parseEnglishDurations(source, occupied, result);

        Matcher matcher = QUANTITY.matcher(text);
        while (matcher.find()) {
            if (TypedTextSupport.overlaps(occupied, matcher.start(), matcher.end())) {
                continue;
            }
            String unit = TypedTextSupport.normalizeUnit(matcher.group("unit"));
            var direction = TypedTextSupport.directionAround(
                    text, matcher.start(), matcher.end(), source.documentCodePointStart());
            var qualifier = quantityQualifier(source, matcher.start(), matcher.end(), unit, direction.direction());
            result.add(new QuantityObservation(
                    TypedTextSupport.span(text, matcher.start(), matcher.end(), source.documentCodePointStart()),
                    TypedTextSupport.parseNumber(matcher.group("number")),
                    unit,
                    qualifier,
                    direction,
                    source));
            occupied.add(new TypedTextSupport.CharRange(matcher.start(), matcher.end()));
        }

        parseBareEnglishCounts(source, occupied, result);
    }

    private void parsePassiveDirectionalPercentages(
            SourceSlice source,
            List<TypedTextSupport.CharRange> occupied,
            List<CandidateObservation> result) {
        String text = source.sourceText();
        Matcher matcher = PASSIVE_DIRECTIONAL_PERCENTAGE.matcher(text);
        while (matcher.find()) {
            if (TypedTextSupport.overlaps(occupied, matcher.start(), matcher.end())) {
                continue;
            }
            var qualifier = TypedTextSupport.leftQualifier(
                    text, matcher.start("direction"), source.documentCodePointStart(), 4);
            if (qualifier.normalized().isBlank()) {
                continue;
            }
            result.add(new QuantityObservation(
                    TypedTextSupport.span(
                            text, matcher.start("number"), matcher.end("unit"), source.documentCodePointStart()),
                    TypedTextSupport.parseNumber(matcher.group("number")),
                    "%",
                    qualifier,
                    new TypedValueModel.DirectionMark(
                            englishDirection(matcher.group("direction")),
                            TypedTextSupport.span(
                                    text,
                                    matcher.start("direction"),
                                    matcher.end("direction"),
                                    source.documentCodePointStart())),
                    source));
            occupied.add(new TypedTextSupport.CharRange(matcher.start(), matcher.end()));
        }
    }

    private void parseEnglishDurations(
            SourceSlice source,
            List<TypedTextSupport.CharRange> occupied,
            List<CandidateObservation> result) {
        String text = source.sourceText();
        Matcher matcher = ENGLISH_DURATION.matcher(text);
        while (matcher.find()) {
            if (TypedTextSupport.overlaps(occupied, matcher.start(), matcher.end())) {
                continue;
            }
            var qualifier = TypedTextSupport.leftQualifier(
                    text, matcher.start("predicate"), source.documentCodePointStart(), 4);
            if (qualifier.normalized().isBlank()) {
                continue;
            }
            result.add(new QuantityObservation(
                    TypedTextSupport.span(
                            text, matcher.start("number"), matcher.end("unit"), source.documentCodePointStart()),
                    TypedTextSupport.parseNumber(matcher.group("number")),
                    TypedTextSupport.normalizeUnit(matcher.group("unit")),
                    qualifier,
                    TypedValueModel.DirectionMark.none(),
                    source));
            occupied.add(new TypedTextSupport.CharRange(matcher.start(), matcher.end()));
        }
    }

    private void parseBareEnglishCounts(
            SourceSlice source,
            List<TypedTextSupport.CharRange> occupied,
            List<CandidateObservation> result) {
        String text = source.sourceText();
        Matcher matcher = BARE_ENGLISH_COUNT.matcher(text);
        while (matcher.find()) {
            if (TypedTextSupport.overlaps(occupied, matcher.start(), matcher.end())
                    || precededByIdentifier(text, matcher.start("number"))) {
                continue;
            }
            var qualifier = TypedTextSupport.boundedQualifier(
                    text,
                    matcher.start("qualifier"),
                    matcher.end("qualifier"),
                    source.documentCodePointStart(),
                    6);
            if (qualifier.normalized().isBlank()) {
                continue;
            }
            result.add(new QuantityObservation(
                    TypedTextSupport.span(
                            text, matcher.start("number"), matcher.end("number"), source.documentCodePointStart()),
                    TypedTextSupport.parseNumber(matcher.group("number")),
                    "count",
                    qualifier,
                    TypedValueModel.DirectionMark.none(),
                    source));
            occupied.add(new TypedTextSupport.CharRange(matcher.start(), matcher.end()));
        }
    }

    private boolean precededByIdentifier(String text, int numberStart) {
        Matcher matcher = PRECEDING_IDENTIFIER.matcher(text.substring(0, numberStart));
        if (!matcher.find()) {
            return false;
        }
        String identifier = matcher.group("identifier");
        return identifier.equalsIgnoreCase("v")
                || identifier.codePoints().anyMatch(Character::isUpperCase);
    }

    private Direction englishDirection(String surface) {
        return TypedTextSupport.normalizeCaptured(surface).startsWith("increas")
                ? Direction.INCREASE : Direction.DECREASE;
    }

    private TypedValueModel.Qualifier quantityQualifier(
            SourceSlice source,
            int coreStart,
            int coreEnd,
            String unit,
            Direction direction) {
        String text = source.sourceText();
        int base = source.documentCodePointStart();
        if (TypedTextSupport.hasGenitiveAfter(text, coreEnd)) {
            return TypedTextSupport.rightQualifier(text, coreEnd, base, 3);
        }
        if (unit.equals("%") && direction == Direction.NONE) {
            var right = TypedTextSupport.rightQualifier(text, coreEnd, base, 3);
            if (!right.normalized().isBlank()) {
                return right;
            }
        }
        int maximumTokens = unit.equals("%") || TypedTextSupport.isDurationUnit(unit) ? 3 : 4;
        return TypedTextSupport.leftQuantityQualifier(text, coreStart, base, maximumTokens);
    }

    private void parseIdentifierNumbers(
            SourceSlice source,
            List<TypedTextSupport.CharRange> occupied,
            List<CandidateObservation> result) {
        parseIdentifierPattern(source, SEPARATED_IDENTIFIER_NUMBER, occupied, result);
        parseIdentifierPattern(source, V_IDENTIFIER_NUMBER, occupied, result);
    }

    private void parseIdentifierPattern(
            SourceSlice source,
            Pattern pattern,
            List<TypedTextSupport.CharRange> occupied,
            List<CandidateObservation> result) {
        Matcher matcher = pattern.matcher(source.sourceText());
        while (matcher.find()) {
            if (TypedTextSupport.overlaps(occupied, matcher.start(), matcher.end())
                    || !matcher.group("identifier").codePoints().anyMatch(Character::isUpperCase)
                    && !matcher.group("identifier").equalsIgnoreCase("v")) {
                continue;
            }
            String identifier = matcher.group("identifier");
            String number = matcher.group("number");
            result.add(new IdentifierNumberObservation(
                    TypedTextSupport.span(
                            source.sourceText(), matcher.start(), matcher.end(), source.documentCodePointStart()),
                    identifier,
                    TypedTextSupport.normalizeCaptured(identifier),
                    number,
                    TypedTextSupport.parseSegments(number),
                    source));
            occupied.add(new TypedTextSupport.CharRange(matcher.start(), matcher.end()));
        }
    }

    private void parseLiterals(
            SourceSlice source,
            List<TypedTextSupport.CharRange> occupied,
            List<CandidateObservation> result) {
        String text = source.sourceText();
        Matcher quoted = QUOTED_LITERAL.matcher(text);
        while (quoted.find()) {
            String groupName = quoted.group("double") != null ? "double" : quoted.group("single") != null ? "single" : "curly";
            int start = quoted.start(groupName);
            int end = quoted.end(groupName);
            if (TypedTextSupport.overlaps(occupied, start, end)) {
                continue;
            }
            String normalized = TypedTextSupport.normalizeLiteral(quoted.group(groupName));
            if (!normalized.isBlank()) {
                result.add(new LiteralIdentifierObservation(
                        TypedTextSupport.span(text, start, end, source.documentCodePointStart()), normalized, source));
                occupied.add(new TypedTextSupport.CharRange(start, end));
            }
        }

        Matcher matcher = UNQUOTED_LITERAL.matcher(text);
        while (matcher.find()) {
            String literal = matcher.group("literal");
            if (TypedTextSupport.overlaps(occupied, matcher.start(), matcher.end()) || !literalEligible(literal)) {
                continue;
            }
            result.add(new LiteralIdentifierObservation(
                    TypedTextSupport.span(
                            text, matcher.start(), matcher.end(), source.documentCodePointStart()),
                    TypedTextSupport.normalizeLiteral(literal),
                    source));
            occupied.add(new TypedTextSupport.CharRange(matcher.start(), matcher.end()));
        }
    }

    private boolean literalEligible(String value) {
        long uppercase = value.codePoints().filter(Character::isUpperCase).count();
        boolean internalUppercase = value.codePoints().skip(1).anyMatch(Character::isUpperCase);
        boolean digit = value.codePoints().anyMatch(Character::isDigit);
        boolean connector = value.indexOf('-') >= 0 || value.indexOf('_') >= 0 || value.indexOf('.') >= 0;
        return uppercase >= 2 || internalUppercase || digit || (connector && (uppercase > 0 || digit));
    }
}
