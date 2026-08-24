package com.prizm.jobposting.service;

import com.prizm.jobposting.dto.response.JobPostingItemResponse;
import com.prizm.jobposting.exception.InvalidJobPostingSegmentationException;
import com.prizm.jobposting.exception.JobPostingItemLimitExceededException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Deterministically separates job-posting structure without a model or external service. */
@Service
public class JobPostingSegmentationService {

    public static final int MAX_ITEM_LENGTH = 500;
    public static final int MAX_ITEM_COUNT = 100;
    private static final int MAX_SECTION_HEADING_CODE_POINTS = 40;

    private static final Pattern UNICODE_WHITESPACE = Pattern.compile(
            "[\\p{Z}\\s]+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern HTML_LINE_BREAK = Pattern.compile(
            "(?i)<br\\s*/?>", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern MARKDOWN_HEADING_PREFIX = Pattern.compile(
            "^#{1,6}\\s+", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern MARKDOWN_TABLE_SEPARATOR = Pattern.compile(
            "^:?-{1,}:?$", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern ASCII_OR_DASH_BULLET_PREFIX = Pattern.compile(
            "^[-‐‑‒–—―*](?:\\s+|$|(?=[\\p{L}]))", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern UNICODE_BULLET_PREFIX = Pattern.compile(
            "^[•●◦▪▫‣⁃·ㆍ○◉]\\s*", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern NUMBERED_PREFIX = Pattern.compile(
            "^\\d{1,4}[.)](?:\\s+|(?=[\\p{L}]))", Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern HEADCOUNT_METADATA = Pattern.compile(
            "^.{1,80}\\s+\\d{1,4}\\s*명(?:\\s*(?:모집|채용))?$",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern HIRING_STAGE_METADATA = Pattern.compile(
            "^(?:서류(?:전형|심사)?|과제전형|코딩테스트|인성검사|\\d{1,2}차\\s*(?:면접|인터뷰)|"
                    + "최종\\s*(?:면접|인터뷰|합격)|처우협의)$",
            Pattern.UNICODE_CHARACTER_CLASS);
    private static final Pattern STANDALONE_TIME_RANGE = Pattern.compile(
            "^(?:오전|오후)?\\s*\\d{1,2}:\\d{2}\\s*[~～–-]\\s*"
                    + "(?:오전|오후)?\\s*\\d{1,2}:\\d{2}$",
            Pattern.UNICODE_CHARACTER_CLASS);

    private static final Set<String> CAREER_SECTION_HEADINGS = Set.of(
            "담당업무", "주요업무", "업무내용", "수행업무", "함께할업무", "함께할업무에요",
            "주요역할", "역할", "직무내용", "자격요건", "공통자격요건", "지원자격", "필수요건",
            "우대사항", "우대요건", "기술요건", "필요역량", "기본역량", "핵심역량",
            "이런분을찾고있어요", "이런분을찾아요", "이런분이면더좋아요", "이런분이면좋아요",
            "responsibilities", "requirements", "qualifications", "preferredqualifications",
            "preferred", "skills");
    private static final Set<String> EXCLUDED_SECTION_HEADINGS = Set.of(
            "복리후생", "복지", "복지사항", "혜택", "전형절차", "채용절차", "채용프로세스",
            "근무조건", "근무지", "근무시간", "근무일시", "급여", "연봉", "고용형태",
            "접수기간", "접수방법", "접수기간및방법", "제출서류", "유의사항", "회사소개",
            "기업소개", "조직소개", "benefits", "hiringprocess", "workconditions",
            "location", "compensation", "howtoapply", "aboutus");
    private static final Set<String> STRUCTURAL_SECTION_HEADINGS = Set.of(
            "모집부문", "모집부문및상세내용", "상세내용", "채용분야", "position", "jobdetails");
    private static final Set<String> METADATA_LABELS = Set.of(
            "학력", "경력", "채용인원", "모집인원", "근무일시", "근무시간", "근무지", "급여",
            "연봉", "고용형태", "접수기간", "접수방법", "제출서류", "전형절차", "채용절차",
            "location", "workhours", "employmenttype", "applicationperiod", "applicationmethod");

    /** Returns normalized items in source order with stable, one-based identifiers. */
    public List<JobPostingItemResponse> segment(String content) {
        if (content == null) {
            return List.of();
        }

        String[] rawLines = normalizeLineEndings(content).split("\\n", -1);
        List<ParsedLine> lines = new ArrayList<>(rawLines.length);
        for (String rawLine : rawLines) {
            lines.add(parseLine(rawLine));
        }

        List<DraftItem> orderedItems = new ArrayList<>();
        Set<String> seenNormalizedItems = new LinkedHashSet<>();
        String currentSection = null;
        boolean currentSectionSearchable = true;
        for (int index = 0; index < lines.size(); index++) {
            ParsedLine line = lines.get(index);
            if (line.text().isEmpty()) {
                continue;
            }
            if (isSectionHeading(lines, index)) {
                currentSection = normalizeSection(line.text());
                currentSectionSearchable = !isExcludedSection(currentSection);
                continue;
            }
            if (!currentSectionSearchable || isStandaloneMetadata(line.text())) {
                continue;
            }

            for (String sentence : splitAtClearSentenceBoundaries(line.text())) {
                String normalizedItem = normalizeWhitespace(sentence);
                if (!hasEnoughLettersOrDigits(normalizedItem)
                        || !seenNormalizedItems.add(normalizedItem)) {
                    continue;
                }
                List<String> boundedItems = splitForSearchLimit(normalizedItem);
                if (orderedItems.size() + boundedItems.size() > MAX_ITEM_COUNT) {
                    throw new JobPostingItemLimitExceededException(MAX_ITEM_COUNT);
                }
                for (String boundedItem : boundedItems) {
                    orderedItems.add(new DraftItem(currentSection, boundedItem));
                }
            }
        }

        List<JobPostingItemResponse> response = new ArrayList<>(orderedItems.size());
        for (int index = 0; index < orderedItems.size(); index++) {
            DraftItem item = orderedItems.get(index);
            response.add(new JobPostingItemResponse(index + 1, item.section(), item.text()));
        }
        return List.copyOf(response);
    }

    private static ParsedLine parseLine(String rawLine) {
        String remaining = normalizePresentationMarkup(rawLine);
        if (MARKDOWN_TABLE_SEPARATOR.matcher(remaining).matches()) {
            return new ParsedLine("", false);
        }
        boolean listItem = false;
        while (!remaining.isEmpty()) {
            Matcher asciiOrDashBullet = ASCII_OR_DASH_BULLET_PREFIX.matcher(remaining);
            Matcher unicodeBullet = UNICODE_BULLET_PREFIX.matcher(remaining);
            Matcher numbered = NUMBERED_PREFIX.matcher(remaining);
            if (asciiOrDashBullet.find()) {
                remaining = normalizePresentationMarkup(
                        remaining.substring(asciiOrDashBullet.end()));
                listItem = true;
            }
            else if (unicodeBullet.find()) {
                remaining = normalizePresentationMarkup(remaining.substring(unicodeBullet.end()));
                listItem = true;
            }
            else if (numbered.find()) {
                remaining = normalizePresentationMarkup(remaining.substring(numbered.end()));
                listItem = true;
            }
            else {
                break;
            }
        }
        return new ParsedLine(remaining, listItem);
    }

    private static String normalizePresentationMarkup(String value) {
        String normalized = normalizeWhitespace(HTML_LINE_BREAK.matcher(value).replaceAll(" "));
        if (normalized.startsWith("|")) {
            normalized = normalizeWhitespace(normalized.substring(1));
        }
        if (normalized.endsWith("|")) {
            normalized = normalizeWhitespace(normalized.substring(0, normalized.length() - 1));
        }
        while (normalized.endsWith("\\")) {
            normalized = normalizeWhitespace(normalized.substring(0, normalized.length() - 1));
        }
        normalized = normalizeWhitespace(MARKDOWN_HEADING_PREFIX.matcher(normalized).replaceFirst(""));
        while (isWrappedInMarkdownEmphasis(normalized, "**")
                || isWrappedInMarkdownEmphasis(normalized, "__")) {
            normalized = normalizeWhitespace(normalized.substring(2, normalized.length() - 2));
        }
        return normalized;
    }

    private static boolean isWrappedInMarkdownEmphasis(String value, String marker) {
        return value.length() >= marker.length() * 2
                && value.startsWith(marker)
                && value.endsWith(marker);
    }

    private static boolean isSectionHeading(List<ParsedLine> lines, int index) {
        ParsedLine current = lines.get(index);
        if (current.listItem() || current.text().isEmpty()) {
            return false;
        }
        if (endsWithSectionColon(current.text())) {
            return true;
        }
        if (endsWithSentenceTerminator(current.text())) {
            return false;
        }
        if (isRecognizedSectionHeading(current.text())) {
            return true;
        }
        if (!isCompactSectionHeading(current.text())) {
            return false;
        }
        for (int nextIndex = index + 1; nextIndex < lines.size(); nextIndex++) {
            ParsedLine next = lines.get(nextIndex);
            if (next.text().isEmpty()) {
                continue;
            }
            return next.listItem();
        }
        return false;
    }

    private static boolean isRecognizedSectionHeading(String text) {
        String canonical = canonicalizeHeading(text);
        return CAREER_SECTION_HEADINGS.contains(canonical)
                || EXCLUDED_SECTION_HEADINGS.contains(canonical)
                || STRUCTURAL_SECTION_HEADINGS.contains(canonical);
    }

    private static boolean isExcludedSection(String section) {
        return section != null && EXCLUDED_SECTION_HEADINGS.contains(canonicalizeHeading(section));
    }

    private static boolean isStandaloneMetadata(String text) {
        if (HEADCOUNT_METADATA.matcher(text).matches()
                || HIRING_STAGE_METADATA.matcher(text).matches()
                || STANDALONE_TIME_RANGE.matcher(text).matches()) {
            return true;
        }

        int colon = firstColonIndex(text);
        if (colon <= 0) {
            return false;
        }
        String label = canonicalizeHeading(text.substring(0, colon));
        return METADATA_LABELS.contains(label);
    }

    private static int firstColonIndex(String text) {
        int asciiColon = text.indexOf(':');
        int fullWidthColon = text.indexOf('：');
        if (asciiColon < 0) {
            return fullWidthColon;
        }
        if (fullWidthColon < 0) {
            return asciiColon;
        }
        return Math.min(asciiColon, fullWidthColon);
    }

    private static String canonicalizeHeading(String text) {
        StringBuilder canonical = new StringBuilder(text.length());
        text.toLowerCase(Locale.ROOT).codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(canonical::appendCodePoint);
        return canonical.toString();
    }

    private static boolean isCompactSectionHeading(String text) {
        if (text.codePointCount(0, text.length()) > MAX_SECTION_HEADING_CODE_POINTS) {
            return false;
        }
        return text.split(" ").length <= 2;
    }

    private static String normalizeSection(String text) {
        String normalized = text;
        while (endsWithSectionColon(normalized)) {
            normalized = normalizeWhitespace(normalized.substring(0, normalized.length() - 1));
        }
        return normalized.isEmpty() ? null : normalized;
    }

    private static List<String> splitAtClearSentenceBoundaries(String text) {
        List<String> sentences = new ArrayList<>();
        int sentenceStart = 0;
        int index = 0;
        while (index < text.length()) {
            if (!isSentenceTerminator(text.charAt(index))) {
                index++;
                continue;
            }

            int boundaryEnd = index + 1;
            while (boundaryEnd < text.length()
                    && isSentenceTerminator(text.charAt(boundaryEnd))) {
                boundaryEnd++;
            }
            while (boundaryEnd < text.length() && isClosingMark(text.charAt(boundaryEnd))) {
                boundaryEnd++;
            }
            if (boundaryEnd == text.length() || text.charAt(boundaryEnd) == ' ') {
                addNonBlank(sentences, text.substring(sentenceStart, boundaryEnd));
                while (boundaryEnd < text.length() && text.charAt(boundaryEnd) == ' ') {
                    boundaryEnd++;
                }
                sentenceStart = boundaryEnd;
                index = boundaryEnd;
            }
            else {
                index = boundaryEnd;
            }
        }
        if (sentenceStart < text.length()) {
            addNonBlank(sentences, text.substring(sentenceStart));
        }
        return sentences;
    }

    private static List<String> splitForSearchLimit(String text) {
        if (text.length() <= MAX_ITEM_LENGTH) {
            return List.of(text);
        }

        List<String> bounded = new ArrayList<>();
        int start = 0;
        while (text.length() - start > MAX_ITEM_LENGTH) {
            int maximumEnd = safeUtf16Boundary(text, start + MAX_ITEM_LENGTH);
            int split = preferredBoundary(text, start, maximumEnd);
            split = preserveMinimumEligibleRemainder(text, start, split);
            String chunk = normalizeWhitespace(text.substring(start, split));
            if (!hasEnoughLettersOrDigits(chunk)) {
                split = preserveMinimumEligibleRemainder(text, start, maximumEnd);
                chunk = normalizeWhitespace(text.substring(start, split));
            }
            if (split <= start || !hasEnoughLettersOrDigits(chunk)) {
                throw cannotSplitWithinSearchLimit();
            }
            bounded.add(chunk);
            start = split;
            while (start < text.length() && text.charAt(start) == ' ') {
                start++;
            }
        }
        if (start < text.length()) {
            String finalChunk = normalizeWhitespace(text.substring(start));
            if (!hasEnoughLettersOrDigits(finalChunk)) {
                throw cannotSplitWithinSearchLimit();
            }
            bounded.add(finalChunk);
        }
        return List.copyOf(bounded);
    }

    private static int preserveMinimumEligibleRemainder(String text, int start, int proposedSplit) {
        int split = proposedSplit;
        while (split > start) {
            String remainder = normalizeWhitespace(text.substring(split));
            if (remainder.isEmpty() || hasEnoughLettersOrDigits(remainder)) {
                return split;
            }
            split = text.offsetByCodePoints(split, -1);
        }
        return split;
    }

    private static InvalidJobPostingSegmentationException cannotSplitWithinSearchLimit() {
        return new InvalidJobPostingSegmentationException(
                "job posting item cannot be split within the 500 character search limit");
    }

    private static int preferredBoundary(String text, int start, int maximumEnd) {
        for (int index = maximumEnd - 1; index > start; index--) {
            char current = text.charAt(index);
            if (isGenericDelimiter(current)
                    && (index + 1 == text.length() || text.charAt(index + 1) == ' ')) {
                return index + 1;
            }
        }
        for (int index = maximumEnd - 1; index > start; index--) {
            if (text.charAt(index) == ' ') {
                return index;
            }
        }
        return maximumEnd;
    }

    private static int safeUtf16Boundary(String text, int proposedEnd) {
        int end = Math.min(proposedEnd, text.length());
        if (end > 0 && end < text.length()
                && Character.isHighSurrogate(text.charAt(end - 1))
                && Character.isLowSurrogate(text.charAt(end))) {
            return end - 1;
        }
        return end;
    }

    private static boolean hasEnoughLettersOrDigits(String text) {
        return text.codePoints().filter(Character::isLetterOrDigit).limit(2).count() == 2;
    }

    private static String normalizeLineEndings(String content) {
        return content.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u0085', '\n')
                .replace('\u2028', '\n')
                .replace('\u2029', '\n');
    }

    private static String normalizeWhitespace(String value) {
        return UNICODE_WHITESPACE.matcher(value).replaceAll(" ").strip();
    }

    private static void addNonBlank(List<String> target, String candidate) {
        String normalized = normalizeWhitespace(candidate);
        if (!normalized.isEmpty()) {
            target.add(normalized);
        }
    }

    private static boolean endsWithSectionColon(String text) {
        return text.endsWith(":") || text.endsWith("：");
    }

    private static boolean endsWithSentenceTerminator(String text) {
        int index = text.length() - 1;
        while (index >= 0 && isClosingMark(text.charAt(index))) {
            index--;
        }
        return index >= 0 && isSentenceTerminator(text.charAt(index));
    }

    private static boolean isSentenceTerminator(char value) {
        return value == '.' || value == '?' || value == '!'
                || value == '。' || value == '？' || value == '！';
    }

    private static boolean isClosingMark(char value) {
        return value == '\'' || value == '"' || value == '’' || value == '”'
                || value == ')' || value == ']' || value == '}';
    }

    private static boolean isGenericDelimiter(char value) {
        return isSentenceTerminator(value)
                || value == ';' || value == '；'
                || value == ',' || value == '，'
                || value == ':' || value == '：';
    }

    private record ParsedLine(String text, boolean listItem) {
    }

    private record DraftItem(String section, String text) {
    }
}
