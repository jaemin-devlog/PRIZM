package com.prizm.search.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/** Reconstructs semantic sentences while preserving exact source spans and PDF hard wraps. */
final class SentenceWindowExtractor {

    private static final Pattern SECTION_HEADING = Pattern.compile(
            "^(?:[0-9]{1,2}(?:\\.[0-9]{1,2})+|[0-9]{1,2}[.)]?)\\s+.+$");
    private static final Pattern DOCUMENT_TITLE = Pattern.compile(
            "(?i)^.{0,100}(?:이력서|포트폴리오|resume|portfolio)(?:\\s+\\d+\\s*/\\s*\\d+)?$");
    private static final Pattern SHORT_HEADING = Pattern.compile(
            "(?i)^(?:검증 가능한 )?(?:핵심 )?(?:경험|개요|요약|목차|기술 스택|"
                    + "문제 해결 사례|테스트 결과|성능 측정 결과|검증 결과)$");
    private static final Pattern PAGE_TITLE = Pattern.compile("(?i)^.{0,100}\\s+[0-9]{1,3}\\s*/\\s*[0-9]{1,3}$");
    private static final Pattern CONTACT_OR_PROFILE = Pattern.compile(
            "(?i)^(?:[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[a-z]{2,}|"
                    + "(?:https?://|www\\.|github\\.com/)[^\\s]+|"
                    + "(?:\\+?82[- .]?)?0(?:10|2|[3-6][1-5])[- .]?\\d{3,4}[- .]?\\d{4}|"
                    + "\\s*(?:contact|email|phone|github|education|gpa|name|school|major|status)\\b|"
                    + "\\s*gpa\\s*[: ]?\\s*\\d(?:\\.\\d+)?\\s*/\\s*\\d(?:\\.\\d+)?|"
                    + ".*(?:19|20)\\d{2}[./-]?(?:0?[1-9]|1[0-2])?\\s*(?:졸업\\s*)?예정\\s*$).*");
    private static final Pattern GUIDE_COPY = Pattern.compile(
            "(?i).*(?:포트폴리오|문서)(?:에서|에)\\s+.*(?:요약|소개|정리)(?:했|합니).*" );
    private static final Pattern NAME_ONLY = Pattern.compile("^[가-힣]{2,4}$");
    private static final Pattern PROFILE_LABEL = Pattern.compile(
            "(?i)^(?:backend|frontend|database|realtime|security|infra|기술 스택|담당 범위)$");
    private static final Pattern TECHNICAL_LIST = Pattern.compile(
            "^[\\p{L}\\p{N}+#._-]+(?:\\s*[|/,·]\\s*[\\p{L}\\p{N}+#._-]+)+$");
    private static final Pattern EVIDENCE_LINE = Pattern.compile(
            ".*(?:구현|개선|결과|성공|차단|방지|저장|갱신|감소|단축|복구|배포|적용).*" );

    Extraction extract(String content) {
        String source = Objects.requireNonNullElse(content, "");
        if (source.isBlank()) {
            return new Extraction(source, List.of());
        }

        Builder builder = new Builder(source);
        int lineStart = 0;
        while (lineStart < source.length()) {
            int lineEnd = lineEnd(source, lineStart);
            int nextLine = nextLine(source, lineEnd);
            int trimmedStart = skipWhitespaceForward(source, lineStart, lineEnd);
            int trimmedEnd = skipWhitespaceBackward(source, trimmedStart, lineEnd);
            if (trimmedStart == trimmedEnd) {
                builder.endBlock();
            } else {
                String line = source.substring(trimmedStart, trimmedEnd);
                if (isStandaloneMetadata(line)) {
                    builder.addHeading(trimmedStart, trimmedEnd);
                } else {
                    builder.addText(trimmedStart, trimmedEnd);
                }
            }
            lineStart = nextLine;
        }
        builder.finish();
        return new Extraction(source, builder.sentences());
    }

    List<SentenceWindow> windows(Extraction extraction, int maximumSentences) {
        List<SentenceWindow> windows = new ArrayList<>();
        List<SentenceUnit> units = extraction.sentences();
        for (int start = 0; start < units.size(); start++) {
            SentenceUnit first = units.get(start);
            if (first.metadata()) {
                continue;
            }
            for (int end = start; end < units.size() && end - start < maximumSentences; end++) {
                SentenceUnit last = units.get(end);
                if (last.metadata() || last.block() != first.block()) {
                    break;
                }
                windows.add(new SentenceWindow(
                        first.index(), last.index(), end - start + 1,
                        extraction.source().substring(first.start(), last.end()),
                        first.text(),
                        last.text()));
            }
        }
        return List.copyOf(windows);
    }

    String addFollowingSentence(String content, String snippet, int maximumSentences) {
        Extraction extraction = extract(content);
        String selected = Objects.requireNonNullElse(snippet, "");
        int snippetStart = extraction.source().indexOf(selected);
        if (snippetStart < 0) {
            return snippet;
        }
        int snippetEnd = snippetStart + selected.length();
        List<SentenceUnit> included = extraction.sentences().stream()
                .filter(unit -> !unit.metadata() && unit.start() >= snippetStart && unit.end() <= snippetEnd)
                .toList();
        if (included.isEmpty() || included.size() >= maximumSentences) {
            return snippet;
        }
        SentenceUnit last = included.get(included.size() - 1);
        if (last.index() + 1 >= extraction.sentences().size()) {
            return snippet;
        }
        SentenceUnit following = extraction.sentences().get(last.index() + 1);
        if (following.metadata() || following.block() != last.block()) {
            return snippet;
        }
        return extraction.source().substring(snippetStart, following.end());
    }

    private static boolean isStandaloneMetadata(String line) {
        if (CONTACT_OR_PROFILE.matcher(line).matches()
                || GUIDE_COPY.matcher(line).matches()
                || NAME_ONLY.matcher(line).matches()
                || PROFILE_LABEL.matcher(line).matches()
                || TECHNICAL_LIST.matcher(line).matches()
                || looksLikeTechnicalList(line)) {
            return true;
        }
        return !endsSentence(line) && (SECTION_HEADING.matcher(line).matches()
                || DOCUMENT_TITLE.matcher(line).matches()
                || SHORT_HEADING.matcher(line).matches()
                || PAGE_TITLE.matcher(line).matches());
    }

    private static boolean looksLikeTechnicalList(String line) {
        if (endsSentence(line) || EVIDENCE_LINE.matcher(line).matches()) {
            return false;
        }
        long separators = line.codePoints()
                .filter(value -> value == '|' || value == '/' || value == ',' || value == '·')
                .count();
        return separators > 0 && line.trim().split("\\s+").length >= 2;
    }

    private static boolean endsSentence(String value) {
        int last = value.codePointBefore(value.length());
        return last == '.' || last == '!' || last == '?' || last == '。' || last == '！' || last == '？';
    }

    private static int lineEnd(String source, int start) {
        int index = start;
        while (index < source.length() && source.charAt(index) != '\r' && source.charAt(index) != '\n') {
            index++;
        }
        return index;
    }

    private static int nextLine(String source, int lineEnd) {
        int index = lineEnd;
        if (index < source.length() && source.charAt(index) == '\r') {
            index++;
        }
        if (index < source.length() && source.charAt(index) == '\n') {
            index++;
        }
        return index;
    }

    private static int skipWhitespaceForward(String value, int start, int end) {
        int index = start;
        while (index < end && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static int skipWhitespaceBackward(String value, int start, int end) {
        int index = end;
        while (index > start && Character.isWhitespace(value.charAt(index - 1))) {
            index--;
        }
        return index;
    }

    record Extraction(String source, List<SentenceUnit> sentences) {
        Extraction {
            sentences = List.copyOf(sentences);
        }
    }

    record SentenceUnit(int index, int block, int start, int end, String text, boolean metadata) {
    }

    record SentenceWindow(
            int startSentenceIndex,
            int endSentenceIndex,
            int sentenceCount,
            String text,
            String firstSentence,
            String lastSentence) {
    }

    private static final class Builder {
        private final String source;
        private final List<SentenceUnit> sentences = new ArrayList<>();
        private int block;
        private Integer pendingStart;
        private int pendingEnd;

        private Builder(String source) {
            this.source = source;
        }

        private void addText(int start, int end) {
            if (pendingStart == null) {
                pendingStart = start;
            }
            pendingEnd = end;
            emitTerminatedSentences();
        }

        private void addHeading(int start, int end) {
            flushPending();
            sentences.add(new SentenceUnit(
                    sentences.size(), block++, start, end, source.substring(start, end), true));
        }

        private void endBlock() {
            flushPending();
            block++;
        }

        private void finish() {
            flushPending();
        }

        private void emitTerminatedSentences() {
            int scan = pendingStart;
            while (scan < pendingEnd) {
                int codePoint = source.codePointAt(scan);
                int next = scan + Character.charCount(codePoint);
                if (isTerminal(codePoint) && isBoundaryAfter(next, pendingEnd)) {
                    addSentence(pendingStart, next, false);
                    pendingStart = nextNonWhitespace(next, pendingEnd);
                    if (pendingStart >= pendingEnd) {
                        pendingStart = null;
                        return;
                    }
                    scan = pendingStart;
                } else {
                    scan = next;
                }
            }
        }

        private void flushPending() {
            if (pendingStart != null) {
                addSentence(pendingStart, pendingEnd, false);
                pendingStart = null;
            }
        }

        private void addSentence(int start, int end, boolean metadata) {
            int actualStart = nextNonWhitespace(start, end);
            int actualEnd = skipWhitespaceBackward(source, actualStart, end);
            if (actualStart < actualEnd) {
                String text = source.substring(actualStart, actualEnd);
                sentences.add(new SentenceUnit(
                        sentences.size(), block, actualStart, actualEnd,
                        text, metadata || isStandaloneMetadata(text)));
            }
        }

        private boolean isBoundaryAfter(int start, int end) {
            return start >= end || Character.isWhitespace(source.charAt(start));
        }

        private int nextNonWhitespace(int start, int end) {
            return skipWhitespaceForward(source, start, end);
        }

        private static boolean isTerminal(int codePoint) {
            return codePoint == '.' || codePoint == '!' || codePoint == '?'
                    || codePoint == '。' || codePoint == '！' || codePoint == '？';
        }

        private List<SentenceUnit> sentences() {
            return List.copyOf(sentences);
        }
    }
}
