package com.prizm.search.profile;

import com.prizm.search.repository.VectorSearchResult;
import com.prizm.search.service.SearchSnippetGenerator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 자격을 이미 통과한 후보의 표시 순서에만 제한된 품질 보정을 적용한다.
 *
 * <p>질의와 직접 맞닿은 문장, 행동·문제·결과 구조, 구체적인 숫자 같은 근거 신호를 가산하고
 * 프로필 메타데이터나 요약 안내문처럼 직접성이 낮은 표현은 감산한다. 보정 폭은 정해진 상한과
 * 하한 안에 묶고 자격을 통과한 후보만 재정렬한다. 새 후보를 만들거나 경력 사실의 진위를
 * 판정하지 않는다.</p>
 */
public final class EvidenceQualityReranker {

    static final double MAX_ADJUSTMENT = 0.065d;
    static final double MIN_ADJUSTMENT = -0.035d;

    private static final Pattern NUMBER_PATTERN = Pattern.compile("(?<![\\p{N}])\\d[\\d,]*(?:\\.\\d+)?");
    private static final Pattern PROFILE_METADATA = Pattern.compile(
            "(?i)(?:[\\p{L}\\p{N}._%+-]+@[\\p{L}\\p{N}.-]+\\.[a-z]{2,}|"
                    + "(?:https?://|www\\.|github\\.com/)|"
                    + "(?:\\+?82[- .]?)?0(?:10|2|[3-6][1-5])[- .]?\\d{3,4}[- .]?\\d{4}|"
                    + "\\b(?:contact|email|phone|education|gpa)\\b)");
    private static final Pattern SUMMARY_GUIDE = Pattern.compile(
            "(?i)(?:포트폴리오|문서).{0,30}(?:요약|소개|정리)|"
                    + "(?:요약|개요|portfolio focus|검증 기준|중심으로 설명)");
    private static final Pattern LEADING_TECH_STACK = Pattern.compile(
            "(?i)^.{0,30}(?:cache|database|realtime|security|infra)");
    private static final Pattern STRUCTURED_EVIDENCE = Pattern.compile(
            "(?:문제\\s*원인|해결\\s*과정|테스트\\s*결과|성능\\s*측정\\s*결과|검증\\s*결과)");
    private static final Pattern ASCII_TECHNICAL_TOKEN = Pattern.compile(
            "(?<![a-z0-9+#._-])[a-z][a-z0-9+.#_-]{2,}(?![a-z0-9+#._-])");
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#._-]*");

    private static final Set<String> ACTION_TERMS = Set.of(
            "구현", "적용", "설계", "개선", "분리", "도입", "처리", "검증", "구축",
            "배포", "최적화", "전환", "방지", "재처리", "통합", "구성", "제어", "차단");
    private static final Set<String> PROBLEM_TERMS = Set.of(
            "문제", "장애", "실패", "병목", "충돌", "중복", "지연", "불일치", "누락",
            "위험", "경쟁", "누적", "어려");
    private static final Set<String> RESULT_TERMS = Set.of(
            "결과", "감소", "단축", "향상", "개선", "방지", "유지", "성공", "완료",
            "절감", "안정", "해결", "전환", "줄였", "낮췄", "높였", "제거", "통과", "0건");
    private static final Set<String> GENERIC_QUERY_TERMS = Set.of(
            "경험", "처리", "문제", "해결", "사용", "활용", "구현", "알려줘", "어떻게",
            "방법", "같이", "했던", "있어", "있나요", "백엔드");
    private static final List<String> KOREAN_SUFFIXES = List.of(
            "으로부터", "에게서", "에서는", "에서도", "이라고", "이라도", "이라면",
            "하던", "했던", "하는", "오는", "에서", "에게", "까지", "부터", "으로",
            "라고", "라는", "에는", "에도", "로", "을", "를", "은", "는", "이", "가",
            "와", "과", "의", "에", "도", "만", "인");

    private final SearchSnippetGenerator snippetGenerator = new SearchSnippetGenerator();

    /** 후보의 근거 품질 신호를 계산해 제한된 점수 보정값과 진단 항목을 반환한다. */
    public Evaluation evaluate(String query, VectorSearchResult candidate) {
        SearchSnippetGenerator.SnippetSelection selection =
                snippetGenerator.select(query, candidate.content());
        String snippet = SearchTokenNormalizer.normalize(selection.snippet());
        String content = SearchTokenNormalizer.normalize(candidate.content());
        Set<String> queryAnchors = specificQueryAnchors(query);
        Set<String> contentTokens = normalizedTokens(content);
        long matchedQueryAnchors = queryAnchors.stream()
                .filter(contentTokens::contains)
                .count();

        double adjustment = 0.0d;
        if (selection.exactPhrase()) {
            adjustment += 0.008d;
        }
        adjustment += Math.min(selection.queryCoverage(), 3) * 0.003d;
        if (selection.narrative()) {
            adjustment += 0.010d;
        }
        if (selection.technicalList()) {
            adjustment -= 0.018d;
        }
        if (selection.metadata() && matchedQueryAnchors <= 1) {
            adjustment -= 0.025d;
        }

        boolean action = containsAny(snippet, ACTION_TERMS);
        boolean problem = containsAny(snippet, PROBLEM_TERMS);
        boolean result = containsAny(snippet, RESULT_TERMS);
        if (problem && action && result) {
            adjustment += 0.020d;
        } else if (action && (problem || result)) {
            adjustment += 0.011d;
        } else if (action) {
            adjustment += 0.004d;
        }

        int numericEvidence = countMatches(NUMBER_PATTERN, snippet, 4);
        adjustment += numericEvidence >= 3 ? 0.010d : numericEvidence > 0 ? 0.005d : 0.0d;
        if (countDistinctTechnicalTokens(snippet, 3) >= 2 && action) {
            adjustment += 0.005d;
        }
        if (!queryAnchors.isEmpty()) {
            adjustment += ((double) matchedQueryAnchors / queryAnchors.size()) * 0.025d;
        }

        boolean fullAction = containsAny(content, ACTION_TERMS);
        boolean fullProblem = containsAny(content, PROBLEM_TERMS);
        boolean fullResult = containsAny(content, RESULT_TERMS);
        if (fullAction && fullProblem && fullResult) {
            adjustment += 0.015d;
        }
        int fullNumericEvidence = countMatches(NUMBER_PATTERN, content, 12);
        adjustment += fullNumericEvidence >= 8 ? 0.015d : fullNumericEvidence >= 3 ? 0.008d : 0.0d;
        if (STRUCTURED_EVIDENCE.matcher(content).find()) {
            adjustment += 0.012d;
        }
        if (LEADING_TECH_STACK.matcher(content).find()) {
            adjustment = Math.min(adjustment, 0.010d);
        }

        int profileMetadata = countMatches(PROFILE_METADATA, content, 3);
        if (profileMetadata >= 3 && matchedQueryAnchors <= 1) {
            adjustment = Math.min(adjustment, -0.030d);
        } else if (profileMetadata > 0) {
            adjustment -= 0.010d;
        }
        if (candidate.content().length() < 320 && SUMMARY_GUIDE.matcher(content).find()) {
            adjustment = Math.min(adjustment, -0.018d);
        }

        double queryAnchorRatio = queryAnchors.isEmpty()
                ? 0.0d
                : (double) matchedQueryAnchors / queryAnchors.size();
        if (selection.queryCoverage() == 0
                && matchedQueryAnchors == 0
                && !selection.exactPhrase()) {
            adjustment = Math.min(adjustment, 0.0d);
        } else if (!queryAnchors.isEmpty() && matchedQueryAnchors == 0) {
            adjustment = Math.min(adjustment, 0.015d);
        } else if (queryAnchors.size() >= 3 && queryAnchorRatio < 0.34d) {
            adjustment = Math.min(adjustment, 0.015d);
        }

        double bounded = Math.max(MIN_ADJUSTMENT, Math.min(MAX_ADJUSTMENT, adjustment));
        return new Evaluation(
                bounded,
                selection.queryCoverage(),
                action,
                problem,
                result,
                numericEvidence,
                selection.technicalList(),
                selection.metadata(),
                profileMetadata >= 3);
    }

    private static boolean containsAny(String value, Set<String> terms) {
        return terms.stream().anyMatch(value::contains);
    }

    private static Set<String> specificQueryAnchors(String query) {
        Set<String> anchors = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(SearchTokenNormalizer.normalize(query));
        while (matcher.find()) {
            String token = normalizeAnchorToken(matcher.group());
            if (token.length() >= 2
                    && !GENERIC_QUERY_TERMS.contains(token)
                    && !token.chars().allMatch(Character::isDigit)) {
                anchors.add(token);
            }
        }
        return Set.copyOf(anchors);
    }

    private static Set<String> normalizedTokens(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(value);
        while (matcher.find()) {
            String token = normalizeAnchorToken(matcher.group());
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return Set.copyOf(tokens);
    }

    private static String normalizeAnchorToken(String value) {
        for (String suffix : KOREAN_SUFFIXES) {
            if (value.endsWith(suffix) && value.length() - suffix.length() >= 2) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }

    private static int countMatches(Pattern pattern, String value, int limit) {
        int count = 0;
        Matcher matcher = pattern.matcher(value);
        while (matcher.find() && count < limit) {
            count++;
        }
        return count;
    }

    private static int countDistinctTechnicalTokens(String value, int limit) {
        java.util.LinkedHashSet<String> tokens = new java.util.LinkedHashSet<>();
        Matcher matcher = ASCII_TECHNICAL_TOKEN.matcher(value);
        while (matcher.find() && tokens.size() < limit) {
            tokens.add(matcher.group());
        }
        return tokens.size();
    }

    public record Evaluation(
            double adjustment,
            int queryCoverage,
            boolean action,
            boolean problem,
            boolean result,
            int numericEvidence,
            boolean technicalList,
            boolean metadata,
            boolean profileMetadata) {
    }
}
