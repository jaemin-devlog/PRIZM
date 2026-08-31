package com.prizm.search.evaluation.searchv3.typed;

import static com.prizm.search.evaluation.searchv3.typed.TypedTextSupport.DATE_ATOM;
import static com.prizm.search.evaluation.searchv3.typed.TypedTextSupport.NUMBER;
import static com.prizm.search.evaluation.searchv3.typed.TypedTextSupport.NUMBER_START_BOUNDARY;
import static com.prizm.search.evaluation.searchv3.typed.TypedTextSupport.UNIT_END_BOUNDARY;
import static com.prizm.search.evaluation.searchv3.typed.TypedTextSupport.UNIT;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DateConstraint;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.DateOperator;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.Direction;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.IdentifierNumberConstraint;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.LiteralIdentifierConstraint;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QuantityConstraint;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QuantityOperator;
import static com.prizm.search.evaluation.searchv3.typed.TypedValueModel.QueryConstraint;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Gold-free deterministic parser for source-grounded query constraints. */
public final class DeterministicTypedQueryParser {

    private static final Pattern KOREAN_DATE_RANGE = Pattern.compile(
            "(?iu)(?<start>" + DATE_ATOM + ")\\s*부터\\s*(?<end>" + DATE_ATOM + ")\\s*까지");
    private static final Pattern ENGLISH_DATE_RANGE = Pattern.compile(
            "(?iu)from\\s+(?<start>" + DATE_ATOM + ")\\s+to\\s+(?<end>" + DATE_ATOM + ")");
    private static final Pattern YEAR_RANGE = Pattern.compile(
            "(?<!\\p{Nd})(?<start>\\p{Nd}{4})\\s*[~〜–—]\\s*(?<end>\\p{Nd}{4})(?!\\p{Nd})");
    private static final Pattern PREFIX_DATE = Pattern.compile(
            "(?iu)(?<operator>on\\s+or\\s+after|after|before)\\s+(?<date>" + DATE_ATOM + ")");
    private static final Pattern SUFFIX_DATE = Pattern.compile(
            "(?iu)(?<date>" + DATE_ATOM + ")\\s*(?<operator>이후|부터|이전)");
    private static final Pattern BARE_DATE = Pattern.compile("(?iu)(?<date>" + DATE_ATOM + ")");

    private static final Pattern QUANTITY_RANGE = Pattern.compile(
            "(?iu)" + NUMBER_START_BOUNDARY + "(?<lower>" + NUMBER + ")\\s*[~〜–—]\\s*(?<upper>" + NUMBER
                    + ")\\s*(?<unit>" + UNIT + ")" + UNIT_END_BOUNDARY);
    private static final Pattern QUANTITY = Pattern.compile(
            "(?iu)(?:(?<prefix>no\\s+more\\s+than|on\\s+or\\s+above|at\\s+least|more\\s+than|at\\s+most|less\\s+than|exactly|over|under)\\s+)?"
                    + NUMBER_START_BOUNDARY + "(?<number>" + NUMBER + ")\\s*(?<unit>" + UNIT + ")"
                    + UNIT_END_BOUNDARY
                    + "(?:\\s*(?<suffix>이상|초과|이하|미만))?"
                    + "(?:\\s*(?<direction>감소|증가|decreas(?:e|ed|es|ing)|reduc(?:e|ed|es|ing|tion)|increas(?:e|ed|es|ing)))?");

    private static final Pattern SEPARATED_IDENTIFIER_NUMBER = Pattern.compile(
            "(?<![A-Za-z0-9_])(?<identifier>[A-Za-z][A-Za-z+_-]{0,31})(?<separator>\\s+|/)"
                    + "(?<number>\\p{Nd}+(?:\\.\\p{Nd}+){0,3})(?![A-Za-z0-9_])");
    private static final Pattern V_IDENTIFIER_NUMBER = Pattern.compile(
            "(?<![A-Za-z0-9_])(?<identifier>[vV])(?<number>\\p{Nd}+(?:\\.\\p{Nd}+){0,3})(?![A-Za-z0-9_])");
    private static final Pattern QUOTED_LITERAL = Pattern.compile(
            "(?:\"(?<double>[^\"\\r\\n]+)\"|'(?<single>[^'\\r\\n]+)'|“(?<curly>[^”\\r\\n]+)”)"
    );
    private static final Pattern UNQUOTED_LITERAL = Pattern.compile(
            "(?<![A-Za-z0-9_])(?<literal>[A-Za-z][A-Za-z0-9]*(?:[-_.][A-Za-z0-9]+)*)(?![A-Za-z0-9_])");

    public List<QueryConstraint> parse(String queryText) {
        Objects.requireNonNull(queryText, "queryText");
        if (queryText.isBlank()) {
            return List.of();
        }
        List<TypedTextSupport.CharRange> occupied = new ArrayList<>(TypedTextSupport.reserveUnsupportedScales(queryText));
        List<QueryConstraint> result = new ArrayList<>();

        parseDateRanges(queryText, occupied, result);
        parseDates(queryText, occupied, result);
        parseQuantities(queryText, occupied, result);
        parseIdentifierNumbers(queryText, occupied, result);
        parseLiterals(queryText, occupied, result);

        result.sort(Comparator.comparingInt(value -> value.span().startInclusive()));
        return List.copyOf(result);
    }

    private void parseDateRanges(
            String text,
            List<TypedTextSupport.CharRange> occupied,
            List<QueryConstraint> result) {
        parseDateRangePattern(text, KOREAN_DATE_RANGE, occupied, result);
        parseDateRangePattern(text, ENGLISH_DATE_RANGE, occupied, result);

        Matcher yearRange = YEAR_RANGE.matcher(text);
        while (yearRange.find()) {
            if (TypedTextSupport.overlaps(occupied, yearRange.start(), yearRange.end())) {
                continue;
            }
            var start = TypedTextSupport.parseDateAtom(yearRange.group("start") + "년");
            var end = TypedTextSupport.parseDateAtom(yearRange.group("end") + "년");
            var interval = TypedTextSupport.range(start, end);
            if (interval == null) {
                occupied.add(new TypedTextSupport.CharRange(yearRange.start(), yearRange.end()));
                continue;
            }
            result.add(new DateConstraint(
                    TypedTextSupport.span(text, yearRange.start(), yearRange.end(), 0),
                    DateOperator.RANGE,
                    interval,
                    TypedTextSupport.leftQualifier(text, yearRange.start(), 0, 4)));
            occupied.add(new TypedTextSupport.CharRange(yearRange.start(), yearRange.end()));
        }
    }

    private void parseDateRangePattern(
            String text,
            Pattern pattern,
            List<TypedTextSupport.CharRange> occupied,
            List<QueryConstraint> result) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (TypedTextSupport.overlaps(occupied, matcher.start(), matcher.end())) {
                continue;
            }
            var interval = TypedTextSupport.range(
                    TypedTextSupport.parseDateAtom(matcher.group("start")),
                    TypedTextSupport.parseDateAtom(matcher.group("end")));
            if (interval == null) {
                occupied.add(new TypedTextSupport.CharRange(matcher.start(), matcher.end()));
                continue;
            }
            result.add(new DateConstraint(
                    TypedTextSupport.span(text, matcher.start(), matcher.end(), 0),
                    DateOperator.RANGE,
                    interval,
                    TypedTextSupport.leftQualifier(text, matcher.start(), 0, 4)));
            occupied.add(new TypedTextSupport.CharRange(matcher.start(), matcher.end()));
        }
    }

    private void parseDates(
            String text,
            List<TypedTextSupport.CharRange> occupied,
            List<QueryConstraint> result) {
        Matcher prefix = PREFIX_DATE.matcher(text);
        while (prefix.find()) {
            DateOperator operator = switch (TypedTextSupport.normalizeCaptured(prefix.group("operator"))) {
                case "on or after" -> DateOperator.GTE;
                case "after" -> DateOperator.GT;
                case "before" -> DateOperator.LT;
                default -> throw new IllegalStateException("unreachable date operator");
            };
            addDate(text, prefix, operator, prefix.group("date"), occupied, result);
        }

        Matcher suffix = SUFFIX_DATE.matcher(text);
        while (suffix.find()) {
            DateOperator operator = switch (suffix.group("operator")) {
                case "이후", "부터" -> DateOperator.GTE;
                case "이전" -> DateOperator.LT;
                default -> throw new IllegalStateException("unreachable date operator");
            };
            addDate(text, suffix, operator, suffix.group("date"), occupied, result);
        }

        Matcher bare = BARE_DATE.matcher(text);
        while (bare.find()) {
            addDate(text, bare, DateOperator.EQ, bare.group("date"), occupied, result);
        }
    }

    private void addDate(
            String text,
            Matcher matcher,
            DateOperator operator,
            String atom,
            List<TypedTextSupport.CharRange> occupied,
            List<QueryConstraint> result) {
        if (TypedTextSupport.overlaps(occupied, matcher.start(), matcher.end())) {
            return;
        }
        var interval = TypedTextSupport.parseDateAtom(atom);
        if (interval != null) {
            result.add(new DateConstraint(
                    TypedTextSupport.span(text, matcher.start(), matcher.end(), 0),
                    operator,
                    interval,
                    TypedTextSupport.leftQualifier(text, matcher.start(), 0, 4)));
        }
        occupied.add(new TypedTextSupport.CharRange(matcher.start(), matcher.end()));
    }

    private void parseQuantities(
            String text,
            List<TypedTextSupport.CharRange> occupied,
            List<QueryConstraint> result) {
        Matcher range = QUANTITY_RANGE.matcher(text);
        while (range.find()) {
            if (TypedTextSupport.overlaps(occupied, range.start(), range.end())) {
                continue;
            }
            BigDecimal lower = TypedTextSupport.parseNumber(range.group("lower"));
            BigDecimal upper = TypedTextSupport.parseNumber(range.group("upper"));
            if (upper.compareTo(lower) < 0) {
                occupied.add(new TypedTextSupport.CharRange(range.start(), range.end()));
                continue;
            }
            String unit = TypedTextSupport.normalizeUnit(range.group("unit"));
            var qualifier = TypedTextSupport.hasGenitiveAfter(text, range.end())
                    ? TypedTextSupport.rightQualifier(text, range.end(), 0, 3)
                    : TypedTextSupport.leftQuantityQualifier(text, range.start(), 0, 3);
            result.add(new QuantityConstraint(
                    TypedTextSupport.span(text, range.start(), range.end(), 0),
                    QuantityOperator.RANGE,
                    lower,
                    upper,
                    unit,
                    qualifier,
                    TypedValueModel.DirectionMark.none()));
            occupied.add(new TypedTextSupport.CharRange(range.start(), range.end()));
        }

        Matcher matcher = QUANTITY.matcher(text);
        while (matcher.find()) {
            if (TypedTextSupport.overlaps(occupied, matcher.start(), matcher.end())) {
                continue;
            }
            QuantityOperator operator = quantityOperator(matcher.group("prefix"), matcher.group("suffix"));
            if (operator == null) {
                occupied.add(new TypedTextSupport.CharRange(matcher.start(), matcher.end()));
                continue;
            }
            String unit = TypedTextSupport.normalizeUnit(matcher.group("unit"));
            var direction = TypedTextSupport.directionAround(text, matcher.start(), matcher.end(), 0);
            var qualifier = quantityQualifier(text, matcher.start(), matcher.end(), unit, direction.direction());
            result.add(new QuantityConstraint(
                    TypedTextSupport.span(text, matcher.start(), matcher.end(), 0),
                    operator,
                    TypedTextSupport.parseNumber(matcher.group("number")),
                    null,
                    unit,
                    qualifier,
                    direction));
            occupied.add(new TypedTextSupport.CharRange(matcher.start(), matcher.end()));
        }
    }

    private QuantityOperator quantityOperator(String prefix, String suffix) {
        QuantityOperator prefixValue = prefix == null ? null : switch (TypedTextSupport.normalizeCaptured(prefix)) {
            case "at least", "on or above" -> QuantityOperator.GTE;
            case "more than", "over" -> QuantityOperator.GT;
            case "at most", "no more than" -> QuantityOperator.LTE;
            case "less than", "under" -> QuantityOperator.LT;
            case "exactly" -> QuantityOperator.EQ;
            default -> null;
        };
        QuantityOperator suffixValue = suffix == null ? null : switch (suffix) {
            case "이상" -> QuantityOperator.GTE;
            case "초과" -> QuantityOperator.GT;
            case "이하" -> QuantityOperator.LTE;
            case "미만" -> QuantityOperator.LT;
            default -> null;
        };
        if (prefixValue != null && suffixValue != null && prefixValue != suffixValue) {
            return null;
        }
        return prefixValue != null ? prefixValue : suffixValue != null ? suffixValue : QuantityOperator.EQ;
    }

    private TypedValueModel.Qualifier quantityQualifier(
            String text,
            int coreStart,
            int coreEnd,
            String unit,
            Direction direction) {
        if (TypedTextSupport.hasGenitiveAfter(text, coreEnd)) {
            return TypedTextSupport.rightQualifier(text, coreEnd, 0, 3);
        }
        if (unit.equals("%") && direction == Direction.NONE) {
            var right = TypedTextSupport.rightQualifier(text, coreEnd, 0, 3);
            if (!right.normalized().isBlank()) {
                return right;
            }
        }
        return TypedTextSupport.leftQuantityQualifier(text, coreStart, 0, 3);
    }

    private void parseIdentifierNumbers(
            String text,
            List<TypedTextSupport.CharRange> occupied,
            List<QueryConstraint> result) {
        parseIdentifierPattern(text, SEPARATED_IDENTIFIER_NUMBER, occupied, result);
        parseIdentifierPattern(text, V_IDENTIFIER_NUMBER, occupied, result);
    }

    private void parseIdentifierPattern(
            String text,
            Pattern pattern,
            List<TypedTextSupport.CharRange> occupied,
            List<QueryConstraint> result) {
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            if (TypedTextSupport.overlaps(occupied, matcher.start(), matcher.end())
                    || !identifierEligible(matcher.group("identifier"))) {
                continue;
            }
            String identifier = matcher.group("identifier");
            String number = matcher.group("number");
            result.add(new IdentifierNumberConstraint(
                    TypedTextSupport.span(text, matcher.start(), matcher.end(), 0),
                    identifier,
                    TypedTextSupport.normalizeCaptured(identifier),
                    number,
                    TypedTextSupport.parseSegments(number)));
            occupied.add(new TypedTextSupport.CharRange(matcher.start(), matcher.end()));
        }
    }

    private boolean identifierEligible(String identifier) {
        if (identifier.equalsIgnoreCase("v")) {
            return true;
        }
        return identifier.codePoints().anyMatch(Character::isUpperCase);
    }

    private void parseLiterals(
            String text,
            List<TypedTextSupport.CharRange> occupied,
            List<QueryConstraint> result) {
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
                result.add(new LiteralIdentifierConstraint(TypedTextSupport.span(text, start, end, 0), normalized));
                occupied.add(new TypedTextSupport.CharRange(start, end));
            }
        }

        Matcher literal = UNQUOTED_LITERAL.matcher(text);
        while (literal.find()) {
            if (TypedTextSupport.overlaps(occupied, literal.start(), literal.end())
                    || !literalEligible(literal.group("literal"))) {
                continue;
            }
            result.add(new LiteralIdentifierConstraint(
                    TypedTextSupport.span(text, literal.start(), literal.end(), 0),
                    TypedTextSupport.normalizeLiteral(literal.group("literal"))));
            occupied.add(new TypedTextSupport.CharRange(literal.start(), literal.end()));
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
