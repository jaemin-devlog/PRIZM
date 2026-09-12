package com.prizm.search.v3.indexing.structure;

import com.prizm.search.v3.indexing.model.SearchV3IndexingPolicies;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Builds source-grounded EvidenceChild values without global overlap or generated context. */
public final class StructuralEvidenceChildBuilder {

    public static final int DEFAULT_MAX_CHILD_CODE_POINTS =
            SearchV3IndexingPolicies.RETRIEVAL_PASSAGE_ABSOLUTE_MAX_CODE_POINTS;
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile("[.!?。！？](?:\\s+|$)");
    private static final Pattern MARKDOWN_TABLE_DIVIDER = Pattern.compile(
            "^\\s*\\|?\\s*:?-{3,}:?\\s*(?:\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");

    private final int maxRetrievalCodePoints;

    public StructuralEvidenceChildBuilder() {
        this(DEFAULT_MAX_CHILD_CODE_POINTS);
    }

    StructuralEvidenceChildBuilder(int maxRetrievalCodePoints) {
        if (maxRetrievalCodePoints < 32) {
            throw new IllegalArgumentException("maxRetrievalCodePoints must be at least 32");
        }
        this.maxRetrievalCodePoints = maxRetrievalCodePoints;
    }

    public List<EvidenceChild> build(List<StructuralBlock> blocks) {
        List<EvidenceChild> children = new ArrayList<>();
        StructuralBlock tableHeader = null;
        StructuralBlock previous = null;

        for (StructuralBlock block : blocks) {
            if (block.type() == StructuralBlockType.HEADING) {
                tableHeader = null;
                previous = block;
                continue;
            }
            if (block.type() == StructuralBlockType.OTHER) {
                boolean explicitTableHeader = MARKDOWN_TABLE_DIVIDER.matcher(block.sourceText()).matches()
                        && previous != null
                        && previous.type() == StructuralBlockType.TABLE_ROW
                        && previous.provenance().lineEnd() + 1 == block.provenance().lineStart()
                        && previous.provenance().parentAnnotationCandidateId()
                                .equals(block.provenance().parentAnnotationCandidateId());
                tableHeader = explicitTableHeader ? previous : null;
                previous = block;
                continue;
            }
            if (block.type() == StructuralBlockType.TABLE_ROW) {
                boolean continuesTable = previous != null
                        && previous.provenance().lineEnd() + 1 == block.provenance().lineStart()
                        && previous.provenance().parentAnnotationCandidateId()
                                .equals(block.provenance().parentAnnotationCandidateId())
                        && (previous.type() == StructuralBlockType.TABLE_ROW
                                || (previous.type() == StructuralBlockType.OTHER
                                        && MARKDOWN_TABLE_DIVIDER.matcher(previous.sourceText()).matches()
                                        && tableHeader != null));
                if (!continuesTable) {
                    tableHeader = block;
                }
            }
            else {
                tableHeader = null;
            }

            boolean usesTableHeader = block.type() == StructuralBlockType.TABLE_ROW
                    && tableHeader != null
                    && tableHeader != block;
            String retrievalContext = usesTableHeader
                    ? SearchV3RetrievalTextPolicy.canonicalizeLineEndings(tableHeader.sourceText())
                    : "";
            int sourceBudget = sourceBudget(retrievalContext);
            List<Segment> segments = split(block, sourceBudget);
            for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
                Segment segment = segments.get(segmentIndex);
                String retrievalText = usesTableHeader
                        ? retrievalContext + "\n"
                                + SearchV3RetrievalTextPolicy.canonicalizeLineEndings(segment.sourceText())
                        : SearchV3RetrievalTextPolicy.canonicalizeLineEndings(segment.sourceText());
                requireRetrievalTextWithinBound(block, retrievalText);
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

    private int sourceBudget(String retrievalContext) {
        if (retrievalContext.isEmpty()) {
            return maxRetrievalCodePoints;
        }
        int budget = maxRetrievalCodePoints
                - SearchV3RetrievalTextPolicy.codePointLength(retrievalContext)
                - 1;
        if (budget < 1) {
            throw new SearchV3StructureException(
                    SearchV3StructureException.Reason.ATOMIC_CHILD_EXCEEDS_PASSAGE_BOUND,
                    "retrieval context leaves no EvidenceChild source budget");
        }
        return budget;
    }

    private List<Segment> split(StructuralBlock block, int sourceBudget) {
        String source = block.sourceText();
        if (canonicalCodePointLength(source, 0, source.length()) <= sourceBudget) {
            return List.of(new Segment(0, source.length(), source));
        }

        List<Segment> result = new ArrayList<>();
        int start = 0;
        while (start < source.length()) {
            start = skipWhitespace(source, start);
            if (start >= source.length()) {
                break;
            }
            int remainingCodePoints = canonicalCodePointLength(source, start, source.length());
            int hardEnd = remainingCodePoints <= sourceBudget
                    ? source.length()
                    : charIndexAfterCanonicalCodePoints(source, start, sourceBudget);
            int end = remainingCodePoints <= sourceBudget
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
        int sentenceBoundary = -1;
        Matcher matcher = SENTENCE_BOUNDARY.matcher(source);
        matcher.region(start, hardEnd);
        while (matcher.find()) {
            int candidate = matcher.start() + 1;
            if (candidate > start) {
                sentenceBoundary = candidate;
            }
        }
        if (sentenceBoundary > start) {
            return sentenceBoundary;
        }
        int lineBoundary = lastLineBoundary(source, start + 1, hardEnd);
        return lineBoundary > start ? lineBoundary : hardEnd;
    }

    private int lastLineBoundary(String source, int minimum, int hardEnd) {
        for (int index = hardEnd - 1; index >= minimum; index--) {
            char value = source.charAt(index);
            if (value == '\n' || value == '\r') {
                return index;
            }
        }
        return -1;
    }

    private int canonicalCodePointLength(String value, int start, int end) {
        int count = 0;
        int index = start;
        while (index < end) {
            char current = value.charAt(index);
            if (current == '\r' && index + 1 < end && value.charAt(index + 1) == '\n') {
                index += 2;
            }
            else {
                index += Character.charCount(value.codePointAt(index));
            }
            count++;
        }
        return count;
    }

    private int charIndexAfterCanonicalCodePoints(String value, int start, int count) {
        int index = start;
        int remaining = count;
        while (index < value.length() && remaining > 0) {
            char current = value.charAt(index);
            if (current == '\r' && index + 1 < value.length() && value.charAt(index + 1) == '\n') {
                index += 2;
            }
            else {
                index += Character.charCount(value.codePointAt(index));
            }
            remaining--;
        }
        return index;
    }

    private void requireRetrievalTextWithinBound(StructuralBlock block, String retrievalText) {
        if (SearchV3RetrievalTextPolicy.codePointLength(retrievalText) > maxRetrievalCodePoints) {
            throw new SearchV3StructureException(
                    SearchV3StructureException.Reason.ATOMIC_CHILD_EXCEEDS_PASSAGE_BOUND,
                    "EvidenceChild retrieval text exceeds the passage bound: " + block.blockId());
        }
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
                original.documentVersionId(),
                original.sourceUnitKey(),
                original.sourcePath(),
                original.pageNo(),
                lineStart,
                lineEnd,
                original.codePointStart() + localCodePointStart,
                original.codePointStart() + localCodePointEnd,
                original.sourceBlockId(),
                original.parentAnnotationCandidateId(),
                original.documentSourceSha256(),
                original.sourceUnitSha256(),
                SearchV3StructureHashes.sha256Utf8(segment.sourceText()));
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
