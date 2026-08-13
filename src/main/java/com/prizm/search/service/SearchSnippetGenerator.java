package com.prizm.search.service;

import com.prizm.search.profile.SearchTokenNormalizer;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Builds a short, query-related excerpt after a search result has already been selected. */
@Component
public class SearchSnippetGenerator {

    static final int MAX_SNIPPET_SENTENCES = 3;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#._-]*");
    private static final Pattern NUMERIC_UNIT_PATTERN = Pattern.compile("^([0-9]+)(?:행|건)$");
    private static final Pattern TOUR_API_FORMATTING = Pattern.compile(
            "(?<![a-z0-9+#.])tour(?:[\\p{Zs}\\t_-]*)api(?![a-z0-9+#.])");
    private static final Pattern TERMINAL_PUNCTUATION = Pattern.compile("[.!?。！？]$");
    private static final Pattern WRAPPED_SENTENCE_ENDING = Pattern.compile(
            ".*(?:하지|하지\\s+않|하도록|하며|하고|되어|되는|하는|위해|수)$");
    private static final Pattern WRAPPED_SENTENCE_START = Pattern.compile(
            "^(?:않도록|않게|하도록|하기|하여|해|하고|하며|되어|되는|수\\s|위해|때문에).*",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern METADATA_PREFIX = Pattern.compile(
            "^(?:담당\\s*범위|기술\\s*스택|backend|database|realtime|security|infra|프로젝트\\s*경험|[0-9]{2}\\s+).*");
    private static final Pattern CONTACT_OR_PROFILE_METADATA = Pattern.compile(
            "(?i).*(?:[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[a-z]{2,}|"
                    + "(?:https?://|www\\.|github\\.com/)[^\\s]+|"
                    + "(?:\\+?82[- .]?)?0(?:10|2|[3-6][1-5])[- .]?\\d{3,4}[- .]?\\d{4}|"
                    + "^\\s*(?:contact|email|phone|github|education|gpa|name|school|major|status)\\b|"
                    + "^\\s*gpa\\s*[: ]?\\s*\\d(?:\\.\\d+)?\\s*/\\s*\\d(?:\\.\\d+)?|"
                    + "(?:19|20)\\d{2}[./-]?(?:0?[1-9]|1[0-2])?\\s*(?:졸업\\s*)?예정\\s*$).*");
    private static final Pattern NAME_ONLY_LINE = Pattern.compile("^[가-힣]{2,4}$");
    private static final Pattern TITLE_OR_GUIDE = Pattern.compile(
            "(?i)^(?:\\d{1,2}[.)]?\\s*)?(?:동시성\\s+정합성\\s+테스트\\s+결과|"
                    + "대표\\s+문제\\s+해결\\s+사례|문제\\s+해결\\s+사례|목차|개요|요약|"
                    + "테스트\\s+결과|기술\\s+스택)(?:\\s.*)?$|"
                    + ".*(?:포트폴리오|문서)(?:에서|에)\\s+.*(?:요약|소개|정리)(?:했|합니).*");
    private static final Pattern INLINE_METADATA_BOUNDARY = Pattern.compile(
            "(?<=[.!?。])\\s+(?=(?i:contact|email|phone|github|education|gpa)\\b|"
                    + "[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[a-zA-Z]{2,}|"
                    + "(?:https?://|www\\.|github\\.com/))");
    private static final Set<String> GENERIC_QUERY_TERMS = Set.of(
            "experience", "evidence", "find", "show", "경력", "경험", "근거", "검색",
            "관련", "활용", "보여줘", "찾아줘", "있나요");
    private static final Set<String> EXPERIENCE_QUERY_TERMS = Set.of(
            "experience", "경험", "활용");
    private static final Set<String> ACTION_TERMS = Set.of(
            "구현", "개선", "설계", "적용", "해결", "통합", "운영", "검증", "도입",
            "분리", "전환", "최적화", "처리", "구성", "갱신", "저장", "차단", "제어");
    private static final Set<String> PROBLEM_TERMS = Set.of(
            "문제", "장애", "실패", "병목", "충돌", "중복", "지연", "불일치", "누락",
            "위험", "분리되어", "달라지는");
    private static final Set<String> RESULT_TERMS = Set.of(
            "결과", "감소", "단축", "향상", "개선", "방지", "유지", "성공", "완료",
            "절감", "안정", "해결", "전환", "갱신", "줄였", "낮췄", "높였", "제거",
            "완화");
    private static final List<String> KOREAN_QUERY_SUFFIXES = List.of(
            "에서", "으로", "했던", "하는", "했다", "한", "을", "를", "이", "가",
            "은", "는", "과", "와", "의", "에", "로");

    public String generate(String query, String content) {
        return select(query, content).snippet();
    }

    /**
     * Selects an extractive snippet and exposes only the existing anchor signals needed to decide
     * whether same-document evidence expansion is necessary.
     */
    public SnippetSelection select(String query, String content) {
        if (content == null || content.isBlank()) {
            return SnippetSelection.empty();
        }

        List<String> sentences = splitSentences(content);
        if (sentences.isEmpty()) {
            return new SnippetSelection(content.trim(), false, 0, 0, false, false, false, 0);
        }

        Set<String> queryTerms = queryTerms(query);
        String normalizedQuery = normalizeForMatching(query == null ? "" : query).trim();
        boolean experienceQuery = containsAnyNormalizedToken(query, EXPERIENCE_QUERY_TERMS);
        List<SentenceEvidence> evidence = new ArrayList<>(sentences.size());
        for (int index = 0; index < sentences.size(); index++) {
            evidence.add(scoreSentence(
                    index,
                    sentences.get(index),
                    queryTerms,
                    normalizedQuery,
                    experienceQuery));
        }

        SentenceEvidence anchor = evidence.stream()
                .max(SentenceEvidence::compareTo)
                .orElse(evidence.get(0));
        if (anchor.exactPhrase()) {
            return selection(anchor.sentence(), anchor);
        }

        int start = anchor.index();
        int end = anchor.index() + 1;
        boolean hasProblem = anchor.problem();
        boolean hasAction = anchor.action();
        boolean hasResult = anchor.result();
        while (end - start < MAX_SNIPPET_SENTENCES) {
            ContextCandidate left = start > 0
                    ? contextCandidate(evidence.get(start - 1), hasProblem, hasAction, hasResult)
                    : null;
            ContextCandidate right = end < evidence.size()
                    ? contextCandidate(evidence.get(end), hasProblem, hasAction, hasResult)
                    : null;
            ContextCandidate selected = betterContext(left, right);
            if (selected == null || selected.score() <= 0) {
                break;
            }
            if (selected.evidence().index() < start) {
                start--;
            } else {
                end++;
            }
            hasProblem = hasProblem || selected.evidence().problem();
            hasAction = hasAction || selected.evidence().action();
            hasResult = hasResult || selected.evidence().result();
        }
        return selection(String.join("\n", sentences.subList(start, end)), anchor);
    }

    private static SnippetSelection selection(String snippet, SentenceEvidence anchor) {
        return new SnippetSelection(
                snippet,
                anchor.exactPhrase(),
                anchor.queryCoverage(),
                anchor.numericMatches(),
                anchor.narrative(),
                anchor.technicalList(),
                anchor.metadata(),
                anchor.score());
    }

    /** Adds one complete following source sentence for an expanded fallback anchor. */
    String addFollowingSourceSentence(String content, String snippet) {
        List<String> contentSentences = splitSentences(content);
        List<String> snippetSentences = splitSentences(snippet);
        if (snippetSentences.isEmpty() || snippetSentences.size() >= MAX_SNIPPET_SENTENCES) {
            return snippet;
        }
        String lastSnippetSentence = snippetSentences.get(snippetSentences.size() - 1);
        for (int index = 0; index < contentSentences.size() - 1; index++) {
            if (contentSentences.get(index).equals(lastSnippetSentence)) {
                String following = contentSentences.get(index + 1);
                if (isTitleOrMetadata(following, normalizeForMatching(following))) {
                    return snippet;
                }
                return snippet + "\n" + following;
            }
        }
        return snippet;
    }

    private static List<String> splitSentences(String content) {
        List<String> sentences = new ArrayList<>();
        content.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .forEach(line -> splitLine(sentences, line));
        return List.copyOf(sentences);
    }

    private static void splitLine(List<String> sentences, String line) {
        String[] semanticLines = INLINE_METADATA_BOUNDARY.split(line);
        if (semanticLines.length > 1) {
            for (String semanticLine : semanticLines) {
                splitLine(sentences, semanticLine.trim());
            }
            return;
        }
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.KOREAN);
        iterator.setText(line);
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String fragment = line.substring(start, end).trim();
            if (fragment.isBlank()) {
                continue;
            }
            if (!sentences.isEmpty() && isWrappedContinuation(sentences.get(sentences.size() - 1), fragment)) {
                int previousIndex = sentences.size() - 1;
                sentences.set(previousIndex, sentences.get(previousIndex) + "\n" + fragment);
            } else {
                sentences.add(fragment);
            }
        }
    }

    private static boolean isWrappedContinuation(String previous, String current) {
        if (TERMINAL_PUNCTUATION.matcher(previous).find()) {
            return false;
        }
        String normalizedPrevious = normalizeForMatching(previous).trim();
        String normalizedCurrent = normalizeForMatching(current).trim();
        return WRAPPED_SENTENCE_ENDING.matcher(normalizedPrevious).matches()
                || WRAPPED_SENTENCE_START.matcher(normalizedCurrent).matches();
    }

    private static SentenceEvidence scoreSentence(
            int index,
            String sentence,
            Set<String> queryTerms,
            String normalizedQuery,
            boolean experienceQuery) {
        String normalizedSentence = normalizeForMatching(sentence);
        int queryCoverage = (int) queryTerms.stream().filter(normalizedSentence::contains).count();
        int lexicalWeight = queryTerms.stream()
                .filter(normalizedSentence::contains)
                .mapToInt(String::length)
                .sum();
        int numericMatches = (int) queryTerms.stream()
                .filter(SearchSnippetGenerator::isNumeric)
                .filter(normalizedSentence::contains)
                .count();
        boolean metadata = isTitleOrMetadata(sentence, normalizedSentence);
        boolean exactPhrase = !metadata
                && !normalizedQuery.isBlank()
                && normalizedQuery.codePointCount(0, normalizedQuery.length()) >= 4
                && normalizedSentence.contains(normalizedQuery);
        boolean action = ACTION_TERMS.stream().anyMatch(normalizedSentence::contains);
        boolean problem = PROBLEM_TERMS.stream().anyMatch(normalizedSentence::contains);
        boolean result = RESULT_TERMS.stream().anyMatch(normalizedSentence::contains);
        boolean narrative = TERMINAL_PUNCTUATION.matcher(sentence).find()
                && (action || problem || result);
        boolean technicalList = isTechnicalList(sentence);

        int score = queryCoverage * 10_000 + lexicalWeight * 20 + numericMatches * 1_500;
        score += exactPhrase ? 100_000 : 0;
        score += experienceQuery && narrative ? 3_000 : 0;
        if (queryCoverage > 0) {
            score += action ? 600 : 0;
            score += problem ? 250 : 0;
            score += result ? 300 : 0;
        }
        if (technicalList) {
            score -= experienceQuery ? 12_000 : 3_000;
        }
        if (metadata) {
            score -= 1_000_000;
        }
        return new SentenceEvidence(
                index,
                sentence,
                score,
                exactPhrase,
                queryCoverage,
                numericMatches,
                narrative,
                technicalList,
                metadata,
                problem,
                action,
                result);
    }

    private static ContextCandidate contextCandidate(
            SentenceEvidence candidate,
            boolean hasProblem,
            boolean hasAction,
            boolean hasResult) {
        if (candidate.metadata()) {
            return new ContextCandidate(candidate, -1_000_000);
        }
        int score = candidate.queryCoverage() * 300 + candidate.numericMatches() * 100;
        score += candidate.problem() && !hasProblem ? 120 : 0;
        score += candidate.action() && !hasAction ? 120 : 0;
        score += candidate.result() && !hasResult ? 120 : 0;
        score -= candidate.technicalList() ? 300 : 0;
        return new ContextCandidate(candidate, score);
    }

    private static ContextCandidate betterContext(ContextCandidate left, ContextCandidate right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        if (left.score() != right.score()) {
            return left.score() > right.score() ? left : right;
        }
        return right;
    }

    private static Set<String> queryTerms(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }

        Matcher matcher = TOKEN_PATTERN.matcher(normalizeForMatching(query));
        Set<String> terms = new LinkedHashSet<>();
        while (matcher.find()) {
            String term = normalizeQueryTerm(matcher.group());
            if (term.length() > 1 && !GENERIC_QUERY_TERMS.contains(term)) {
                terms.add(term);
            }
        }
        return Set.copyOf(terms);
    }

    private static boolean containsAnyNormalizedToken(String value, Set<String> expectedTerms) {
        if (value == null || value.isBlank()) {
            return false;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(normalizeForMatching(value));
        while (matcher.find()) {
            if (expectedTerms.contains(matcher.group())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTechnicalList(String sentence) {
        String normalized = normalizeForMatching(sentence);
        long separators = sentence.codePoints()
                .filter(value -> value == '/' || value == '|' || value == '·' || value == ',')
                .count();
        boolean hasNarrativeEnding = TERMINAL_PUNCTUATION.matcher(sentence).find();
        long tokenCount = TOKEN_PATTERN.matcher(normalized).results().count();
        return separators >= 1
                && !hasNarrativeEnding
                && tokenCount >= 2
                && ACTION_TERMS.stream().noneMatch(normalized::contains);
    }

    private static boolean isTitleOrMetadata(String sentence, String normalizedSentence) {
        String trimmed = sentence.trim();
        if (CONTACT_OR_PROFILE_METADATA.matcher(trimmed).matches()
                || NAME_ONLY_LINE.matcher(trimmed).matches()
                || TITLE_OR_GUIDE.matcher(trimmed).matches()) {
            return true;
        }
        if (TERMINAL_PUNCTUATION.matcher(sentence).find()) {
            return false;
        }
        return METADATA_PREFIX.matcher(normalizedSentence).matches()
                || (sentence.length() <= 60 && ACTION_TERMS.stream().anyMatch(normalizedSentence::contains));
    }

    private static boolean isNumeric(String value) {
        return value.codePoints().allMatch(Character::isDigit);
    }

    private static String normalizeQueryTerm(String value) {
        Matcher numericUnit = NUMERIC_UNIT_PATTERN.matcher(value);
        if (numericUnit.matches()) {
            return numericUnit.group(1);
        }
        for (String suffix : KOREAN_QUERY_SUFFIXES) {
            if (value.endsWith(suffix)
                    && value.codePointCount(0, value.length())
                    > suffix.codePointCount(0, suffix.length()) + 1) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }

    private static String normalizeForMatching(String value) {
        String normalized = SearchTokenNormalizer.normalize(value)
                .replace("카카오", "kakao");
        return TOUR_API_FORMATTING.matcher(normalized).replaceAll("tourapi");
    }

    private record SentenceEvidence(
            int index,
            String sentence,
            int score,
            boolean exactPhrase,
            int queryCoverage,
            int numericMatches,
            boolean narrative,
            boolean technicalList,
            boolean metadata,
            boolean problem,
            boolean action,
            boolean result) implements Comparable<SentenceEvidence> {

        @Override
        public int compareTo(SentenceEvidence other) {
            if (score != other.score) {
                return Integer.compare(score, other.score);
            }
            if (queryCoverage != other.queryCoverage) {
                return Integer.compare(queryCoverage, other.queryCoverage);
            }
            if (numericMatches != other.numericMatches) {
                return Integer.compare(numericMatches, other.numericMatches);
            }
            if (narrative != other.narrative) {
                return Boolean.compare(narrative, other.narrative);
            }
            return Integer.compare(other.index, index);
        }
    }

    private record ContextCandidate(SentenceEvidence evidence, int score) {
    }

    public record SnippetSelection(
            String snippet,
            boolean exactPhrase,
            int queryCoverage,
            int numericMatches,
            boolean narrative,
            boolean technicalList,
            boolean metadata,
            int anchorScore) {

        static SnippetSelection empty() {
            return new SnippetSelection("", false, 0, 0, false, false, false, 0);
        }

    }
}
