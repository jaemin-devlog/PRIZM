package com.prizm.search.evaluation;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Generic evaluation-only literal expression used by PRZ-016 P16 Phase A. */
final class PhaseALiteralQueryExpression {

    private static final int MAX_CODE_POINTS = 100;
    private static final int MAX_TOKENS = 5;

    private final String normalized;

    private PhaseALiteralQueryExpression(String normalized) {
        this.normalized = normalized;
    }

    static Optional<PhaseALiteralQueryExpression> from(String query) {
        String normalized = normalize(query);
        if (normalized.isBlank()
                || normalized.codePointCount(0, normalized.length()) > MAX_CODE_POINTS) {
            return Optional.empty();
        }
        String[] tokens = normalized.split(" ");
        if (tokens.length > MAX_TOKENS) {
            return Optional.empty();
        }
        boolean hasLetterOrDigit = false;
        for (int offset = 0; offset < normalized.length();) {
            int codePoint = normalized.codePointAt(offset);
            if (Character.isLetterOrDigit(codePoint)) {
                hasLetterOrDigit = true;
            } else if (codePoint != ' '
                    && codePoint != '_'
                    && codePoint != '+'
                    && codePoint != '#'
                    && codePoint != '.'
                    && codePoint != '/'
                    && codePoint != '-') {
                return Optional.empty();
            }
            offset += Character.charCount(codePoint);
        }
        return hasLetterOrDigit
                ? Optional.of(new PhaseALiteralQueryExpression(normalized))
                : Optional.empty();
    }

    String databaseNeedle() {
        return normalized;
    }

    boolean matches(String content) {
        String source = normalize(content);
        int fromIndex = 0;
        while (fromIndex <= source.length() - normalized.length()) {
            int match = source.indexOf(normalized, fromIndex);
            if (match < 0) {
                return false;
            }
            int end = match + normalized.length();
            boolean leftBoundary = match == 0
                    || !isIdentifierCodePoint(source.codePointBefore(match));
            boolean rightBoundary = end == source.length()
                    || !isIdentifierCodePoint(source.codePointAt(end));
            if (leftBoundary && rightBoundary) {
                return true;
            }
            fromIndex = match + 1;
        }
        return false;
    }

    private static boolean isIdentifierCodePoint(int codePoint) {
        return Character.isLetterOrDigit(codePoint)
                || codePoint == '_'
                || codePoint == '+'
                || codePoint == '#'
                || codePoint == '.'
                || codePoint == '/'
                || codePoint == '-';
    }

    private static String normalize(String value) {
        return Normalizer.normalize(Objects.requireNonNullElse(value, ""), Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }
}
