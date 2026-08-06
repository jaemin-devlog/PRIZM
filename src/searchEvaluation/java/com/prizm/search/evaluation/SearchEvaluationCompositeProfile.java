package com.prizm.search.evaluation;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.repository.VectorSearchResult;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TUNING-only candidate profile for evaluating source deduplication and non-score evidence signals.
 * This class is not part of the product runtime source set.
 */
public class SearchEvaluationCompositeProfile {

    public static final String PROFILE_ID = "source-dedup-evidence-signals-v1";

    private static final double MINIMUM_DENSE_SCORE = 0.50d;
    private static final int MINIMUM_TEXT_OVERLAP = 80;
    private static final double MINIMUM_TEXT_OVERLAP_RATIO = 0.30d;
    private static final int MINIMUM_CORE_TERM_MATCHES = 2;
    private static final int MAX_RESULTS = 5;
    private static final int MAX_IDENTIFIER_LENGTH = 64;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#._-]*");
    private static final Pattern ASCII_IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9+.#_-]*");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");
    private static final Set<String> GENERIC_ASCII_TERMS = Set.of(
            "api", "db", "http", "https", "sql", "jwt", "ui", "pdf", "txt");
    private static final Set<String> STOP_WORDS = Set.of(
            "근거", "보여", "찾아", "확인", "실제", "이력", "기록", "질문", "문서",
            "프로젝트", "경험", "이유", "어떻게", "무엇", "있는", "있", "없", "해줘",
            "했나", "했어", "인가", "인지", "같은", "없이", "대한", "관련", "현재");
    private static final List<String> KOREAN_SUFFIXES = List.of(
            "했나요", "했다는", "했는지", "했어", "했나", "되는", "보존되는", "맡았다는",
            "출시한", "배포한", "개발한", "운영한", "적용한", "으로", "에서", "에게", "까지",
            "부터", "처럼", "보다", "이라도", "이라고", "라는", "하며", "해서", "해도",
            "하고", "한", "된", "하는", "했", "인", "인지", "인가", "이나", "나도", "에도",
            "에는", "에서", "으로", "로", "을", "를", "은", "는", "이", "가", "와", "과",
            "의", "에", "도", "만");
    private static final List<String> NEGATION_MARKERS = List.of(
            "않", "아니", "없", "못", "미취득", "하지 않았다", "하지 않았");
    private static final List<String> POSITIVE_CLAIM_MARKERS = List.of(
            "근거가 있", "경험", "이력", "기록", "했나요", "맡았", "적용", "운영", "취득");
    private static final List<String> EXPLANATION_MARKERS = List.of(
            "어떻게", "이유", "왜", "방지", "막았");

    public Decision apply(String query, List<VectorSearchResult> denseCandidates) {
        if (denseCandidates.isEmpty()) {
            return new Decision(List.of(), List.of(), List.of("NO_SEARCHABLE_CANDIDATES"));
        }

        QuerySignals signals = querySignals(query);
        List<VectorSearchResult> sourceDistinctCandidates = consolidateSourceLocations(query, denseCandidates).stream()
                .map(CandidateGroup::representative)
                .toList();
        List<VectorSearchResult> eligibleCandidates = sourceDistinctCandidates.stream()
                .filter(candidate -> rejectionReasons(signals, candidate).isEmpty())
                .toList();
        if (eligibleCandidates.isEmpty()) {
            return new Decision(
                    denseCandidates,
                    List.of(),
                    rejectionReasons(signals, sourceDistinctCandidates.get(0)));
        }

        List<VectorSearchResult> diverseResults = consolidateQueryEvidence(
                        query,
                        signals,
                        eligibleCandidates)
                .stream()
                .map(CandidateGroup::representative)
                .limit(MAX_RESULTS)
                .toList();
        return new Decision(
                denseCandidates,
                diverseResults,
                List.of());
    }

    private List<CandidateGroup> consolidateSourceLocations(
            String query,
            List<VectorSearchResult> denseCandidates) {
        List<CandidateGroup> groups = new ArrayList<>();
        for (VectorSearchResult candidate : denseCandidates) {
            CandidateGroup duplicate = groups.stream()
                    .filter(group -> group.members().stream()
                            .anyMatch(member -> sameEvidenceLocation(member, candidate)))
                    .findFirst()
                    .orElse(null);
            if (duplicate == null) {
                groups.add(new CandidateGroup(candidate, new ArrayList<>(List.of(candidate))));
                continue;
            }

            duplicate.members().add(candidate);
            if (lexicalAffinity(query, candidate) > lexicalAffinity(query, duplicate.representative())) {
                duplicate.representative(candidate);
            }
        }
        return List.copyOf(groups);
    }

    private List<CandidateGroup> consolidateQueryEvidence(
            String query,
            QuerySignals signals,
            List<VectorSearchResult> eligibleCandidates) {
        List<CandidateGroup> groups = new ArrayList<>();
        for (VectorSearchResult candidate : eligibleCandidates) {
            CandidateGroup duplicate = groups.stream()
                    .filter(group -> group.members().stream()
                            .anyMatch(member -> sameQueryEvidence(signals, member, candidate)))
                    .findFirst()
                    .orElse(null);
            if (duplicate == null) {
                groups.add(new CandidateGroup(candidate, new ArrayList<>(List.of(candidate))));
                continue;
            }

            duplicate.members().add(candidate);
            if (lexicalAffinity(query, candidate) > lexicalAffinity(query, duplicate.representative())) {
                duplicate.representative(candidate);
            }
        }
        return List.copyOf(groups);
    }

    private boolean sameQueryEvidence(
            QuerySignals signals,
            VectorSearchResult left,
            VectorSearchResult right) {
        String leftSearchable = normalized(left.documentTitle() + " " + left.content());
        String rightSearchable = normalized(right.documentTitle() + " " + right.content());
        Set<String> sharedCoreTerms = matchedCoreTermSet(signals.coreTerms(), leftSearchable);
        sharedCoreTerms.retainAll(matchedCoreTermSet(signals.coreTerms(), rightSearchable));

        boolean sharedIdentifierAnchor = !signals.requiredIdentifiers().isEmpty()
                && signals.requiredIdentifiers().stream()
                        .allMatch(identifier -> leftSearchable.contains(identifier)
                                && rightSearchable.contains(identifier));
        String leftNumberText = normalizedNumberText(leftSearchable);
        String rightNumberText = normalizedNumberText(rightSearchable);
        boolean sharedNumberAnchor = !signals.requiredNumbers().isEmpty()
                && signals.requiredNumbers().stream()
                        .allMatch(number -> leftNumberText.contains(number)
                                && rightNumberText.contains(number));

        return sharedCoreTerms.size() >= MINIMUM_CORE_TERM_MATCHES
                && (sharedIdentifierAnchor || sharedNumberAnchor);
    }

    private boolean sameEvidenceLocation(VectorSearchResult left, VectorSearchResult right) {
        if (!left.documentVersionId().equals(right.documentVersionId())) {
            return false;
        }
        if (left.sourceType() == ChunkSourceType.PAGE && right.sourceType() == ChunkSourceType.PAGE) {
            return left.sourceIndex() == right.sourceIndex();
        }
        if (left.sourceType() != ChunkSourceType.TEXT_CHUNK
                || right.sourceType() != ChunkSourceType.TEXT_CHUNK) {
            return false;
        }
        int overlap = exactBoundaryOverlap(left.content(), right.content());
        int shorterLength = Math.min(left.content().length(), right.content().length());
        return overlap >= MINIMUM_TEXT_OVERLAP
                && shorterLength > 0
                && ((double) overlap / shorterLength) >= MINIMUM_TEXT_OVERLAP_RATIO;
    }

    private int exactBoundaryOverlap(String left, String right) {
        return Math.max(suffixPrefixOverlap(left, right), suffixPrefixOverlap(right, left));
    }

    private int suffixPrefixOverlap(String left, String right) {
        int maximum = Math.min(left.length(), right.length());
        for (int length = maximum; length >= MINIMUM_TEXT_OVERLAP; length--) {
            if (left.regionMatches(left.length() - length, right, 0, length)) {
                return length;
            }
        }
        return 0;
    }

    private int lexicalAffinity(String query, VectorSearchResult candidate) {
        QuerySignals signals = querySignals(query);
        String searchable = normalized(candidate.documentTitle() + " " + candidate.content());
        int identifierMatches = (int) signals.requiredIdentifiers().stream()
                .filter(searchable::contains)
                .count();
        int numberMatches = (int) signals.requiredNumbers().stream()
                .filter(number -> normalizedNumberText(searchable).contains(number))
                .count();
        int coreMatches = matchedCoreTerms(signals.coreTerms(), searchable);
        return (identifierMatches * 100) + (numberMatches * 100) + coreMatches;
    }

    private List<String> rejectionReasons(QuerySignals signals, VectorSearchResult top) {
        List<String> reasons = new ArrayList<>();
        String searchable = normalized(top.documentTitle() + " " + top.content());
        if (top.score() < MINIMUM_DENSE_SCORE) {
            reasons.add("DENSE_SCORE_BELOW_TUNING_FLOOR");
        }
        for (String identifier : signals.requiredIdentifiers()) {
            if (!searchable.contains(identifier)) {
                reasons.add("MISSING_IDENTIFIER:" + identifier);
            }
        }
        String numberText = normalizedNumberText(searchable);
        for (String number : signals.requiredNumbers()) {
            if (!numberText.contains(number)) {
                reasons.add("MISSING_NUMBER:" + number);
            }
        }
        if (matchedCoreTerms(signals.coreTerms(), searchable) < MINIMUM_CORE_TERM_MATCHES) {
            reasons.add("INSUFFICIENT_CORE_TERM_COVERAGE");
        }
        if (signals.positiveClaimQuestion() && containsNegatedClaim(top.content(), signals)) {
            reasons.add("NEGATED_CLAIM");
        }
        return List.copyOf(reasons);
    }

    private boolean containsNegatedClaim(String content, QuerySignals signals) {
        return Arrays.stream(content.split("[.!?\\n]"))
                .map(SearchEvaluationCompositeProfile::normalized)
                .anyMatch(sentence -> NEGATION_MARKERS.stream().anyMatch(sentence::contains)
                        && (signals.requiredIdentifiers().stream().anyMatch(sentence::contains)
                        || matchedCoreTerms(signals.coreTerms(), sentence) >= MINIMUM_CORE_TERM_MATCHES));
    }

    private QuerySignals querySignals(String query) {
        String normalizedQuery = normalized(query);
        Set<String> identifiers = new LinkedHashSet<>();
        Matcher identifierMatcher = ASCII_IDENTIFIER_PATTERN.matcher(query);
        while (identifierMatcher.find()) {
            String identifier = identifierMatcher.group().toLowerCase(Locale.ROOT);
            if (identifier.length() <= MAX_IDENTIFIER_LENGTH && !GENERIC_ASCII_TERMS.contains(identifier)) {
                identifiers.add(identifier);
            }
        }

        Set<String> numbers = new LinkedHashSet<>();
        Matcher numberMatcher = NUMBER_PATTERN.matcher(query);
        while (numberMatcher.find()) {
            numbers.add(numberMatcher.group().replace(",", ""));
        }

        Set<String> coreTerms = new LinkedHashSet<>();
        Matcher tokenMatcher = TOKEN_PATTERN.matcher(normalizedQuery);
        while (tokenMatcher.find()) {
            String token = stem(tokenMatcher.group());
            if (token.length() >= 2
                    && !identifiers.contains(token)
                    && !numbers.contains(token)
                    && !STOP_WORDS.contains(token)) {
                coreTerms.add(token);
            }
        }
        boolean positiveClaim = POSITIVE_CLAIM_MARKERS.stream().anyMatch(normalizedQuery::contains)
                && EXPLANATION_MARKERS.stream().noneMatch(normalizedQuery::contains);
        return new QuerySignals(
                Set.copyOf(identifiers),
                Set.copyOf(numbers),
                Set.copyOf(coreTerms),
                positiveClaim);
    }

    private static int matchedCoreTerms(Set<String> coreTerms, String searchable) {
        return (int) coreTerms.stream().filter(searchable::contains).count();
    }

    private static Set<String> matchedCoreTermSet(Set<String> coreTerms, String searchable) {
        Set<String> matches = new HashSet<>();
        coreTerms.stream().filter(searchable::contains).forEach(matches::add);
        return matches;
    }

    private static String stem(String value) {
        String result = value.toLowerCase(Locale.ROOT);
        boolean changed;
        do {
            changed = false;
            for (String suffix : KOREAN_SUFFIXES) {
                if (result.length() - suffix.length() >= 2 && result.endsWith(suffix)) {
                    result = result.substring(0, result.length() - suffix.length());
                    changed = true;
                    break;
                }
            }
        }
        while (changed);
        return result;
    }

    private static String normalized(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
    }

    private static String normalizedNumberText(String value) {
        return value.replace(",", "");
    }

    public record Decision(
            List<VectorSearchResult> candidates,
            List<VectorSearchResult> results,
            List<String> rejectionReasons) {

        public Decision {
            candidates = List.copyOf(candidates);
            results = List.copyOf(results);
            rejectionReasons = List.copyOf(rejectionReasons);
        }

        public boolean rejected() {
            return results.isEmpty();
        }
    }

    private record QuerySignals(
            Set<String> requiredIdentifiers,
            Set<String> requiredNumbers,
            Set<String> coreTerms,
            boolean positiveClaimQuestion) {
    }

    private static final class CandidateGroup {

        private VectorSearchResult representative;
        private final List<VectorSearchResult> members;

        private CandidateGroup(VectorSearchResult representative, List<VectorSearchResult> members) {
            this.representative = representative;
            this.members = members;
        }

        private VectorSearchResult representative() {
            return representative;
        }

        private void representative(VectorSearchResult replacement) {
            representative = replacement;
        }

        private List<VectorSearchResult> members() {
            return members;
        }
    }
}
