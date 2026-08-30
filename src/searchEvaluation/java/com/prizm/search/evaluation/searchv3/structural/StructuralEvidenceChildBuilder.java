package com.prizm.search.evaluation.searchv3.structural;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds source-grounded search children without document-wide overlap or generated context. */
public final class StructuralEvidenceChildBuilder {

    public static final int DEFAULT_MAX_CHILD_CODE_POINTS = 800;
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("[.!?。！？](?:\\s+|$)");

    private final int maxChildCodePoints;

    public StructuralEvidenceChildBuilder() {
        this(DEFAULT_MAX_CHILD_CODE_POINTS);
    }

    public StructuralEvidenceChildBuilder(int maxChildCodePoints) {
        if (maxChildCodePoints < 32) {
            throw new IllegalArgumentException("maxChildCodePoints must be at least 32");
        }
        this.maxChildCodePoints = maxChildCodePoints;
    }

    public List<EvidenceChild> build(List<StructuralBlock> blocks) {
        List<EvidenceChild> children = new ArrayList<>();
        StructuralBlock tableHeader = null;
        StructuralBlock previous = null;

        for (StructuralBlock block : blocks) {
            if (block.type() == StructuralBlockType.TABLE_ROW) {
                boolean beginsTable = previous == null
                        || previous.type() != StructuralBlockType.TABLE_ROW
                        || !previous.provenance().parentAnnotationCandidateId()
                                .equals(block.provenance().parentAnnotationCandidateId());
                if (beginsTable) {
                    tableHeader = block;
                }
            }
            else {
                tableHeader = null;
            }

            List<Segment> segments = split(block);
            for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
                Segment segment = segments.get(segmentIndex);
                boolean usesTableHeader = block.type() == StructuralBlockType.TABLE_ROW
                        && tableHeader != null
                        && tableHeader != block;
                String retrievalText = usesTableHeader
                        ? tableHeader.sourceText() + "\n" + segment.sourceText()
                        : segment.sourceText();
                List<String> contextBlockIds = usesTableHeader
                        ? List.of(tableHeader.blockId())
                        : List.of();
                SourceProvenance provenance = segmentProvenance(block, segment);
                children.add(new EvidenceChild(
                        block.blockId() + "-C%02d".formatted(segmentIndex + 1),
                        block.type(),
                        segment.sourceText(),
                        retrievalText,
                        List.of(block.blockId()),
                        contextBlockIds,
                        provenance));
            }
            previous = block;
        }
        return List.copyOf(children);
    }

    private List<Segment> split(StructuralBlock block) {
        String source = block.sourceText();
        if (source.codePointCount(0, source.length()) <= maxChildCodePoints) {
            return List.of(new Segment(0, source.length(), source));
        }

        List<Segment> result = new ArrayList<>();
        int start = 0;
        while (start < source.length()) {
            start = skipWhitespace(source, start);
            if (start >= source.length()) {
                break;
            }
            int remainingCodePoints = source.codePointCount(start, source.length());
            int hardEnd = remainingCodePoints <= maxChildCodePoints
                    ? source.length()
                    : source.offsetByCodePoints(start, maxChildCodePoints);
            int end = remainingCodePoints <= maxChildCodePoints
                    ? source.length()
                    : preferredBoundary(source, start, hardEnd);
            end = trimTrailingWhitespace(source, start, end);
            if (end <= start) {
                end = hardEnd;
            }
            result.add(new Segment(start, end, source.substring(start, end)));
            start = end;
        }
        return List.copyOf(result);
    }

    private int preferredBoundary(String source, int start, int hardEnd) {
        int minimum = source.offsetByCodePoints(start, Math.max(1, maxChildCodePoints / 2));
        int boundary = -1;
        Matcher matcher = SENTENCE_BOUNDARY.matcher(source);
        matcher.region(start, hardEnd);
        while (matcher.find()) {
            int candidate = matcher.start() + 1;
            if (candidate >= minimum) {
                boundary = candidate;
            }
        }
        int newline = source.lastIndexOf('\n', hardEnd - 1);
        if (newline >= minimum) {
            boundary = Math.max(boundary, newline);
        }
        return boundary > start ? boundary : hardEnd;
    }

    private int skipWhitespace(String source, int index) {
        int current = index;
        while (current < source.length()) {
            int codePoint = source.codePointAt(current);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            current += Character.charCount(codePoint);
        }
        return current;
    }

    private int trimTrailingWhitespace(String source, int start, int end) {
        int current = end;
        while (current > start) {
            int codePoint = source.codePointBefore(current);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            current -= Character.charCount(codePoint);
        }
        return current;
    }

    private SourceProvenance segmentProvenance(StructuralBlock block, Segment segment) {
        SourceProvenance original = block.provenance();
        String blockText = block.sourceText();
        int localCodePointStart = blockText.codePointCount(0, segment.charStart());
        int localCodePointEnd = blockText.codePointCount(0, segment.charEnd());
        int lineStart = original.lineStart() + countNewlines(blockText, 0, segment.charStart());
        int lineEnd = lineStart + countNewlines(blockText, segment.charStart(), segment.charEnd());
        return new SourceProvenance(
                original.documentId(),
                original.versionId(),
                original.sourcePath(),
                original.page(),
                lineStart,
                lineEnd,
                original.codePointStart() + localCodePointStart,
                original.codePointStart() + localCodePointEnd,
                original.sourceBlockId(),
                original.parentAnnotationCandidateId(),
                original.documentSourceSha256(),
                StructuralBlockParser.sha256(segment.sourceText()));
    }

    private int countNewlines(String value, int start, int end) {
        int count = 0;
        for (int index = start; index < end; index++) {
            if (value.charAt(index) == '\n') {
                count++;
            }
        }
        return count;
    }

    private record Segment(int charStart, int charEnd, String sourceText) {
    }
}
