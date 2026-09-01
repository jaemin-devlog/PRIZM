package com.prizm.search.v3.indexing.structure;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** Parses observable text layout without career, company, job, or technology dictionaries. */
public final class StructuralBlockParser {

    private static final Pattern MARKDOWN_HEADING = Pattern.compile("^\\s{0,3}#{1,6}\\s+\\S.*$");
    private static final Pattern BULLET = Pattern.compile("^\\s*[-*+•▪◦]\\s+\\S.*$");
    private static final Pattern NUMBERED = Pattern.compile("^\\s*(?:\\d+|[A-Za-z])[.)]\\s+\\S.*$");
    private static final Pattern KEY_VALUE = Pattern.compile("^\\s*[^:：\\n]{1,40}[:：]\\s*\\S.*$");
    private static final Pattern TERMINAL_PUNCTUATION = Pattern.compile(".*[.!?。！？;；:]$");
    private static final Pattern OTHER_STRUCTURE = Pattern.compile("^\\s*(?:[-=_]{3,}|[─━]{3,})\\s*$");
    private static final Pattern MARKDOWN_TABLE_DIVIDER = Pattern.compile(
            "^\\s*\\|?\\s*:?-{3,}:?\\s*(?:\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");
    private static final Pattern URI_ONLY = Pattern.compile(
            "^\\s*[A-Za-z][A-Za-z0-9+.-]*://\\S+\\s*$");
    private static final Pattern DATE_VALUE = Pattern.compile(
            ".*(?<!\\d)(?:19|20)\\d{2}(?:[./-]\\d{1,2}(?:[./-]\\d{1,2})?)?(?!\\d).*");
    private static final Pattern QUANTITY_VALUE = Pattern.compile(
            ".*(?:[<>≤≥]=?\\s*)?\\d[\\d,.]*\\s*(?:%|[a-z]{2,}|[A-Z]{2,}|[가-힣]{1,8}).*");
    private static final Pattern INLINE_VALUE_SEPARATOR = Pattern.compile(".*\\s[—–]\\s*\\S.*");
    private static final int MAX_HEADING_CODE_POINTS = 80;

    public List<StructuralBlock> parse(StructuralSourceUnit sourceUnit) {
        List<LineSlice> lines = lines(sourceUnit.sourceText());
        List<BlockSlice> slices = new ArrayList<>();

        int index = 0;
        while (index < lines.size()) {
            LineSlice line = lines.get(index);
            if (line.blank()) {
                index++;
                continue;
            }

            StructuralBlockType special = classifySpecial(lines, index);
            if (special != StructuralBlockType.PARAGRAPH) {
                slices.add(new BlockSlice(special, line.charStart(), line.charEnd(), line.lineNumber(),
                        line.lineNumber()));
                index++;
                continue;
            }

            int start = index;
            int end = index;
            while (end + 1 < lines.size()
                    && !lines.get(end + 1).blank()
                    && classifySpecial(lines, end + 1) == StructuralBlockType.PARAGRAPH) {
                end++;
            }
            slices.add(new BlockSlice(
                    StructuralBlockType.PARAGRAPH,
                    lines.get(start).charStart(),
                    lines.get(end).charEnd(),
                    lines.get(start).lineNumber(),
                    lines.get(end).lineNumber()));
            index = end + 1;
        }

        List<StructuralBlock> blocks = new ArrayList<>();
        String currentParent = sourceUnit.sourceUnitKey() + "-PC-ROOT";
        for (int blockIndex = 0; blockIndex < slices.size(); blockIndex++) {
            BlockSlice slice = slices.get(blockIndex);
            String blockId = sourceUnit.sourceUnitKey() + "-SB-%04d".formatted(blockIndex + 1);
            if (slice.type() == StructuralBlockType.HEADING) {
                currentParent = blockId;
            }
            String sourceText = sourceUnit.sourceText().substring(slice.charStart(), slice.charEnd());
            SourceProvenance provenance = new SourceProvenance(
                    sourceUnit.documentId(),
                    sourceUnit.documentVersionId(),
                    sourceUnit.sourceUnitKey(),
                    sourceUnit.sourcePath(),
                    sourceUnit.pageNo(),
                    slice.lineStart(),
                    slice.lineEnd(),
                    sourceUnit.sourceText().codePointCount(0, slice.charStart()),
                    sourceUnit.sourceText().codePointCount(0, slice.charEnd()),
                    blockId,
                    currentParent,
                    sourceUnit.documentSourceSha256(),
                    sourceUnit.sourceUnitSha256(),
                    SearchV3StructureHashes.sha256Utf8(sourceText));
            blocks.add(new StructuralBlock(blockId, slice.type(), sourceText, provenance));
        }
        return List.copyOf(blocks);
    }

    private StructuralBlockType classifySpecial(List<LineSlice> lines, int index) {
        String stripped = lines.get(index).text().strip();
        if (OTHER_STRUCTURE.matcher(stripped).matches()
                || MARKDOWN_TABLE_DIVIDER.matcher(stripped).matches()) {
            return StructuralBlockType.OTHER;
        }
        if (MARKDOWN_HEADING.matcher(stripped).matches()) {
            return StructuralBlockType.HEADING;
        }
        if (BULLET.matcher(stripped).matches() || NUMBERED.matcher(stripped).matches()) {
            return StructuralBlockType.LIST_ITEM;
        }
        if (looksLikeTableRow(stripped)) {
            return StructuralBlockType.TABLE_ROW;
        }
        if (KEY_VALUE.matcher(stripped).matches() && !URI_ONLY.matcher(stripped).matches()) {
            return StructuralBlockType.KEY_VALUE;
        }
        if (URI_ONLY.matcher(stripped).matches()) {
            return StructuralBlockType.PARAGRAPH;
        }
        if (looksLikeEvidenceAssertion(stripped)) {
            return StructuralBlockType.PARAGRAPH;
        }
        if (looksLikeHeading(lines, index, stripped)) {
            return StructuralBlockType.HEADING;
        }
        return StructuralBlockType.PARAGRAPH;
    }

    private boolean looksLikeHeading(List<LineSlice> lines, int index, String stripped) {
        if (stripped.codePointCount(0, stripped.length()) > MAX_HEADING_CODE_POINTS
                || TERMINAL_PUNCTUATION.matcher(stripped).matches()) {
            return false;
        }
        boolean previousBoundary = index == 0 || lines.get(index - 1).blank();
        boolean nextBoundary = index + 1 == lines.size() || lines.get(index + 1).blank();
        if (previousBoundary && index + 1 < lines.size()
                && looksLikeCompactValueRow(lines.get(index + 1).text().strip())) {
            return false;
        }
        int tokenCount = stripped.split("\\s+").length;
        return previousBoundary && (nextBoundary || tokenCount <= 8);
    }

    private boolean looksLikeCompactValueRow(String value) {
        return !value.isBlank()
                && value.codePointCount(0, value.length()) <= 48
                && value.split("\\s+").length <= 6
                && !TERMINAL_PUNCTUATION.matcher(value).matches()
                && looksLikeEvidenceAssertion(value);
    }

    private boolean looksLikeEvidenceAssertion(String value) {
        return DATE_VALUE.matcher(value).matches()
                || QUANTITY_VALUE.matcher(value).matches()
                || INLINE_VALUE_SEPARATOR.matcher(value).matches();
    }

    private boolean looksLikeTableRow(String stripped) {
        return count(stripped, '|') >= 2 || count(stripped, '\t') >= 2;
    }

    private int count(String value, char target) {
        int count = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) == target) {
                count++;
            }
        }
        return count;
    }

    private List<LineSlice> lines(String source) {
        List<LineSlice> result = new ArrayList<>();
        int charStart = 0;
        int lineNumber = 1;
        for (int index = 0; index <= source.length(); index++) {
            if (index != source.length() && source.charAt(index) != '\n') {
                continue;
            }
            int charEnd = index;
            if (charEnd > charStart && source.charAt(charEnd - 1) == '\r') {
                charEnd--;
            }
            String text = source.substring(charStart, charEnd);
            result.add(new LineSlice(charStart, charEnd, lineNumber, text, text.isBlank()));
            charStart = index + 1;
            lineNumber++;
        }
        return result;
    }

    private record LineSlice(int charStart, int charEnd, int lineNumber, String text, boolean blank) {
    }

    private record BlockSlice(
            StructuralBlockType type,
            int charStart,
            int charEnd,
            int lineStart,
            int lineEnd) {
    }
}
