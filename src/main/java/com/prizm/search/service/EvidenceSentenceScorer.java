package com.prizm.search.service;

import com.prizm.search.profile.SearchTokenNormalizer;
import com.prizm.search.service.SentenceWindowExtractor.SentenceWindow;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 원문의 짧은 연속 문장 구간 중 질의에 가장 직접적인 구간을 고른다.
 *
 * <p>질의 표현과 숫자의 포함 범위, 인접 표현, 행동·문제·결과 같은 국소적 완결성을 비교하고
 * 연락처나 기술 목록 같은 메타데이터는 낮게 평가한다. 한 구간은 최대 세 문장으로 제한되며,
 * 여기서 계산한 값은 근거 위치화 전용 점수다. 검색 후보의 관련성 점수나 사실 판정의
 * 신뢰도로 사용하지 않는다.</p>
 */
final class EvidenceSentenceScorer {

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#._-]*");
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}])\\d+(?:[.,]\\d+)?");
    private static final Pattern CONTACT = Pattern.compile(
            "(?i).*(?:[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[a-z]{2,}|"
                    + "(?:https?://|www\\.|github\\.com/)[^\\s]+|"
                    + "(?:contact|email|phone|github|education|gpa)\\b).*" );
    private static final Set<String> GENERIC_QUERY_TERMS = Set.of(
            "experience", "evidence", "find", "show", "경력", "경험", "근거", "검색",
            "관련", "활용", "보여줘", "찾아줘", "있나요", "했나요", "인가요");
    private static final Set<String> ACTION_TERMS = Set.of(
            "구현", "개선", "설계", "적용", "해결", "통합", "운영", "검증", "도입", "배포",
            "직접", "분리", "전환", "최적화", "처리", "구성", "갱신", "저장", "차단", "제어",
            "직렬화", "수행", "실행", "거절", "격리", "줄었", "막", "만들", "결합", "제한");
    private static final Set<String> PROBLEM_TERMS = Set.of(
            "문제", "장애", "실패", "병목", "충돌", "중복", "지연", "불일치", "누락", "위험",
            "급증", "빠지는", "재실행", "덮어쓰", "오류", "손상", "격리", "깨진", "거듭",
            "unique");
    private static final Set<String> RESULT_TERMS = Set.of(
            "감소", "단축", "향상", "개선", "방지", "유지", "성공", "완료", "절감",
            "안정", "해결", "전환", "갱신", "줄였", "낮췄", "높였", "제거", "완화", "차단",
            "보냈", "반영", "들어가지", "들어갈 수 없", "실행하지 않", "복구", "거절", "줄었", "막", "0건",
            "적용됐", "배포했");
    private static final Set<String> STATE_TERMS = Set.of(
            "생산", "production", "운영", "배포", "출시", "실제", "적용됐", "사용되", "현재");
    private static final Set<String> DETAIL_TERMS = Set.of(
            "반영", "보정", "병합", "결합", "제한", "분리", "이동", "대기열", "checkpoint",
            "체크포인트", "상한", "직렬화", "재시도", "보냈", "전환", "격리");
    private static final Set<String> ACTOR_TERMS = Set.of(
            "본인", "직접", "담당", "수행", "구현했", "만들었", "적용했", "배포했");
    private static final Set<String> METRIC_TERMS = Set.of(
            "p95", "p99", "응답", "시간", "지연", "비용", "처리량", "메모리", "rss", "누락률",
            "정확도", "건", "회", "퍼센트", "초", "분", "밀리초", "기가바이트", "gb");
    private static final List<String> QUERY_SUFFIXES = List.of(
            "했나요", "했는지", "했어", "인가요", "있나요", "주세요", "나요", "에서", "으로",
            "했던", "하는", "되는", "했다", "한다", "하게", "해", "한", "을", "를", "이", "가",
            "은", "는", "과", "와", "의", "에", "로");

    /** 질의 신호와 국소적 완결성을 가장 잘 충족하는 원문 구간 하나를 선택한다. */
    Selection select(String query, List<SentenceWindow> windows) {
        if (windows.isEmpty()) {
            return Selection.empty();
        }
        QuerySignals signals = querySignals(query);
        List<WindowScore> scores = windows.stream().map(window -> score(window, signals)).toList();
        WindowScore selected = scores.stream().max(WindowScore::compareTo).orElseThrow();
        return new Selection(selected, scores);
    }

    private static WindowScore score(SentenceWindow window, QuerySignals signals) {
        String normalized = normalize(window.text());
        int coverage = (int) signals.terms().stream().filter(normalized::contains).count();
        int lexicalWeight = signals.terms().stream()
                .filter(normalized::contains).mapToInt(String::length).sum();
        Set<String> windowNumbers = numbers(window.text());
        int numericMatches = (int) signals.numbers().stream().filter(windowNumbers::contains).count();
        int phraseMatches = adjacentPhraseMatches(signals.orderedTerms(), normalized);
        boolean leadingSentenceMatches = signals.terms().stream()
                .anyMatch(normalize(window.firstSentence())::contains);
        boolean exactPhrase = !signals.normalizedQuery().isBlank()
                && signals.normalizedQuery().codePointCount(0, signals.normalizedQuery().length()) >= 4
                && normalized.contains(signals.normalizedQuery());
        boolean action = containsAny(normalized, ACTION_TERMS);
        boolean problem = containsAny(normalized, PROBLEM_TERMS);
        boolean result = containsAny(normalized, RESULT_TERMS);
        boolean state = containsAny(normalized, STATE_TERMS);
        boolean detail = containsAny(normalized, DETAIL_TERMS);
        boolean actor = containsAny(normalized, ACTOR_TERMS);
        boolean metric = containsAny(normalized, METRIC_TERMS) || NUMBER.matcher(normalized).find();
        boolean metadata = CONTACT.matcher(window.text().trim()).matches();
        boolean technicalList = isTechnicalList(window.text(), normalized);
        boolean claimComplete = coverage > 0
                && (!signals.actionRequested() || action)
                && (!signals.problemRequested() || (problem && result))
                && (!signals.stateRequested() || state)
                && (signals.numbers().isEmpty() || numericMatches == signals.numbers().size());

        int claimComponents = countTrue(action, problem, result, state, actor, metric);
        int score = coverage * 10_000 + lexicalWeight * 20 + numericMatches * 5_000;
        score += phraseMatches * 2_000 + (exactPhrase ? 100_000 : 0);
        score += signals.actionRequested() && action ? 2_000 : 0;
        score += signals.problemRequested() && problem ? 1_500 : 0;
        score += signals.problemRequested() && result ? 1_800 : 0;
        score += signals.problemRequested() && detail ? 1_600 : 0;
        score += signals.stateRequested() && state ? 2_500 : 0;
        score += !signals.numbers().isEmpty() && numericMatches == signals.numbers().size() ? 4_000 : 0;
        score += action && result ? 1_200 : 0;
        score += action && state ? 1_300 : 0;
        score += actor && action ? 1_200 : 0;
        score += signals.metricRequested() && metric && result ? 700 : 0;
        score += claimComplete && (signals.actionRequested() || signals.problemRequested()) ? 4_000 : 0;
        score += claimComponents * 80;
        score -= (window.sentenceCount() - 1) * 1_000;
        score -= exactPhrase ? (window.sentenceCount() - 1) * 5_000 : 0;
        score -= window.sentenceCount() > 1 && !leadingSentenceMatches ? 3_000 : 0;
        score -= technicalList ? 3_000 : 0;
        score -= metadata ? 1_000_000 : 0;

        return new WindowScore(
                window.startSentenceIndex(), window.endSentenceIndex(), window.sentenceCount(), window.text(),
                score, coverage, numericMatches, phraseMatches, exactPhrase, claimComponents,
                action, problem, result, state, actor, metric, technicalList, metadata, claimComplete);
    }

    private static QuerySignals querySignals(String query) {
        String normalizedQuery = normalize(Objects.requireNonNullElse(query, ""));
        Matcher matcher = TOKEN.matcher(normalizedQuery);
        List<String> ordered = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        while (matcher.find()) {
            String term = stripSuffix(matcher.group());
            if (term.length() > 1 && !GENERIC_QUERY_TERMS.contains(term) && unique.add(term)) {
                ordered.add(term);
            }
        }
        if (normalizedQuery.contains("거절")) {
            unique.add("실행하지 않");
        }
        if (normalizedQuery.contains("줄였")) {
            unique.add("줄었");
        }
        Set<String> numbers = numbers(Objects.requireNonNullElse(query, ""));
        boolean asksAction = containsAny(normalizedQuery, ACTION_TERMS)
                || normalizedQuery.contains("했나") || normalizedQuery.contains("어떻게");
        boolean asksProblem = containsAny(normalizedQuery, PROBLEM_TERMS)
                || containsAny(normalizedQuery, Set.of("문제", "장애", "해결", "대응"));
        boolean asksState = containsAny(normalizedQuery, Set.of(
                "생산", "production", "운영", "배포", "출시", "적용", "사용"));
        boolean asksMetric = containsAny(normalizedQuery, METRIC_TERMS);
        return new QuerySignals(
                normalizedQuery, List.copyOf(ordered), Set.copyOf(unique), Set.copyOf(numbers),
                asksAction, asksProblem, asksState, asksMetric);
    }

    private static int adjacentPhraseMatches(List<String> terms, String normalizedWindow) {
        int matches = 0;
        for (int index = 0; index < terms.size() - 1; index++) {
            if (normalizedWindow.contains(terms.get(index) + " " + terms.get(index + 1))) {
                matches++;
            }
        }
        return matches;
    }

    private static int countTrue(boolean... values) {
        int count = 0;
        for (boolean value : values) {
            count += value ? 1 : 0;
        }
        return count;
    }

    private static boolean containsAny(String value, Set<String> terms) {
        return terms.stream().anyMatch(value::contains);
    }

    private static Set<String> numbers(String value) {
        Set<String> values = new LinkedHashSet<>();
        NUMBER.matcher(Objects.requireNonNullElse(value, "")).results()
                .forEach(result -> values.add(result.group().replace(",", "")));
        return Set.copyOf(values);
    }

    private static boolean isTechnicalList(String source, String normalized) {
        long separators = source.codePoints().filter(value -> value == '/' || value == '|' || value == '·').count();
        long tokens = TOKEN.matcher(normalized).results().count();
        return separators >= 2 && tokens >= 3 && !containsAny(normalized, ACTION_TERMS);
    }

    private static String stripSuffix(String value) {
        for (String suffix : QUERY_SUFFIXES) {
            if (value.endsWith(suffix)
                    && value.codePointCount(0, value.length())
                    > suffix.codePointCount(0, suffix.length()) + 1) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }

    private static String normalize(String value) {
        return SearchTokenNormalizer.normalize(value)
                .replace("카카오", "kakao")
                .replace(",", "")
                .toLowerCase(Locale.ROOT);
    }

    record QuerySignals(
            String normalizedQuery,
            List<String> orderedTerms,
            Set<String> terms,
            Set<String> numbers,
            boolean actionRequested,
            boolean problemRequested,
            boolean stateRequested,
            boolean metricRequested) {
    }

    record Selection(WindowScore selected, List<WindowScore> candidates) {
        Selection {
            candidates = List.copyOf(candidates);
        }

        static Selection empty() {
            return new Selection(WindowScore.empty(), List.of());
        }
    }

    record WindowScore(
            int startSentenceIndex,
            int endSentenceIndex,
            int sentenceCount,
            String snippet,
            int score,
            int queryCoverage,
            int numericMatches,
            int phraseMatches,
            boolean exactPhrase,
            int claimComponents,
            boolean action,
            boolean problem,
            boolean result,
            boolean state,
            boolean actor,
            boolean metric,
            boolean technicalList,
            boolean metadata,
            boolean claimComplete) implements Comparable<WindowScore> {

        @Override
        public int compareTo(WindowScore other) {
            if (score != other.score) {
                return Integer.compare(score, other.score);
            }
            if (queryCoverage != other.queryCoverage) {
                return Integer.compare(queryCoverage, other.queryCoverage);
            }
            if (claimComponents != other.claimComponents) {
                return Integer.compare(claimComponents, other.claimComponents);
            }
            if (numericMatches != other.numericMatches) {
                return Integer.compare(numericMatches, other.numericMatches);
            }
            if (sentenceCount != other.sentenceCount) {
                return Integer.compare(other.sentenceCount, sentenceCount);
            }
            return Integer.compare(other.startSentenceIndex, startSentenceIndex);
        }

        static WindowScore empty() {
            return new WindowScore(-1, -1, 0, "", 0, 0, 0, 0, false, 0,
                    false, false, false, false, false, false, false, false, false);
        }
    }
}
