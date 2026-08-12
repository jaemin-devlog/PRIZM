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

    static final int MAX_SNIPPET_CHARACTERS = 360;
    private static final int MAX_SNIPPET_SENTENCES = 3;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#._-]*");
    private static final Set<String> GENERIC_QUERY_TERMS = Set.of(
            "experience", "evidence", "find", "show", "경력", "경험", "근거", "검색",
            "관련", "보여줘", "찾아줘", "있나요");
    private static final Set<String> EXPERIENCE_QUERY_TERMS = Set.of(
            "experience", "경험", "활용");
    private static final Set<String> CONCRETE_EVIDENCE_TERMS = Set.of(
            "구현", "적용", "운영", "개선", "설계", "통합", "처리", "변경",
            "구성", "분리", "검증", "단축", "방지", "해결");

    public String generate(String query, String content) {
        if (content == null || content.isBlank()) {
            return "";
        }

        List<String> sentences = splitSentences(content);
        if (sentences.isEmpty()) {
            return abbreviate(content.trim());
        }

        Set<String> queryTerms = queryTerms(query);
        boolean experienceQuery = containsAnyNormalizedToken(query, EXPERIENCE_QUERY_TERMS);
        int bestIndex = 0;
        int bestScore = -1;
        int bestEvidenceScore = -1;
        for (int index = 0; index < sentences.size(); index++) {
            int score = relevanceScore(sentences.get(index), queryTerms);
            int evidenceScore = experienceQuery
                    ? followingEvidenceScore(sentences, index)
                    : 0;
            if (score > bestScore || (score == bestScore && evidenceScore > bestEvidenceScore)) {
                bestScore = score;
                bestEvidenceScore = evidenceScore;
                bestIndex = index;
            }
        }

        return abbreviate(buildContext(sentences, bestIndex));
    }

    private static List<String> splitSentences(String content) {
        BreakIterator iterator = BreakIterator.getSentenceInstance(Locale.KOREAN);
        iterator.setText(content);
        List<String> sentences = new ArrayList<>();
        int start = iterator.first();
        for (int end = iterator.next(); end != BreakIterator.DONE; start = end, end = iterator.next()) {
            String fragment = content.substring(start, end);
            fragment.lines()
                    .map(String::trim)
                    .filter(line -> !line.isBlank())
                    .forEach(sentences::add);
        }
        return List.copyOf(sentences);
    }

    private static Set<String> queryTerms(String query) {
        if (query == null || query.isBlank()) {
            return Set.of();
        }

        Matcher matcher = TOKEN_PATTERN.matcher(SearchTokenNormalizer.normalize(query));
        Set<String> terms = new LinkedHashSet<>();
        while (matcher.find()) {
            String term = matcher.group();
            if (term.length() > 1 && !GENERIC_QUERY_TERMS.contains(term)) {
                terms.add(term);
            }
        }
        return Set.copyOf(terms);
    }

    private static int relevanceScore(String sentence, Set<String> queryTerms) {
        String normalizedSentence = SearchTokenNormalizer.normalize(sentence);
        return queryTerms.stream()
                .filter(normalizedSentence::contains)
                .mapToInt(String::length)
                .sum();
    }

    private static boolean containsAnyNormalizedToken(String value, Set<String> expectedTerms) {
        if (value == null || value.isBlank()) {
            return false;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(SearchTokenNormalizer.normalize(value));
        while (matcher.find()) {
            if (expectedTerms.contains(matcher.group())) {
                return true;
            }
        }
        return false;
    }

    private static int followingEvidenceScore(List<String> sentences, int startIndex) {
        int endIndex = Math.min(sentences.size(), startIndex + MAX_SNIPPET_SENTENCES);
        String normalizedWindow = SearchTokenNormalizer.normalize(
                String.join(" ", sentences.subList(startIndex, endIndex)));
        return CONCRETE_EVIDENCE_TERMS.stream()
                .filter(normalizedWindow::contains)
                .mapToInt(String::length)
                .sum();
    }

    private static String buildContext(List<String> sentences, int bestIndex) {
        List<String> selected = new ArrayList<>();
        selected.add(sentences.get(bestIndex));

        int nextIndex = bestIndex + 1;
        if (nextIndex < sentences.size()) {
            selected.add(sentences.get(nextIndex));
            nextIndex++;
        }

        int previousIndex = bestIndex - 1;
        if (previousIndex >= 0 && selected.size() < MAX_SNIPPET_SENTENCES) {
            List<String> withPrevious = new ArrayList<>(selected.size() + 1);
            withPrevious.add(sentences.get(previousIndex));
            withPrevious.addAll(selected);
            if (String.join("\n", withPrevious).length() <= MAX_SNIPPET_CHARACTERS) {
                selected = withPrevious;
                previousIndex--;
            }
        }

        while (selected.size() < MAX_SNIPPET_SENTENCES && nextIndex < sentences.size()) {
            selected.add(sentences.get(nextIndex));
            nextIndex++;
        }
        while (selected.size() < MAX_SNIPPET_SENTENCES && previousIndex >= 0) {
            List<String> withPrevious = new ArrayList<>(selected.size() + 1);
            withPrevious.add(sentences.get(previousIndex));
            withPrevious.addAll(selected);
            if (String.join("\n", withPrevious).length() > MAX_SNIPPET_CHARACTERS) {
                break;
            }
            selected = withPrevious;
            previousIndex--;
        }
        return String.join("\n", selected);
    }

    private static String abbreviate(String value) {
        if (value.length() <= MAX_SNIPPET_CHARACTERS) {
            return value;
        }
        return value.substring(0, MAX_SNIPPET_CHARACTERS - 1).stripTrailing() + "…";
    }
}
