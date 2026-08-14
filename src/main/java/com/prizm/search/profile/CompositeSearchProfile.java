package com.prizm.search.profile;

import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.search.repository.VectorSearchResult;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Versioned opt-in search profile that combines dense candidates with source deduplication and
 * explicit evidence signals.
 */
@Component
public class CompositeSearchProfile {

    public static final String PROFILE_ID = "source-dedup-evidence-signals-v1";

    private static final double MINIMUM_DENSE_SCORE = 0.50d;
    private static final int MINIMUM_TEXT_OVERLAP = 80;
    private static final double MINIMUM_TEXT_OVERLAP_RATIO = 0.30d;
    private static final int MINIMUM_CORE_TERM_MATCHES = 2;
    private static final int MAX_RESULTS = 5;
    private static final int MAX_IDENTIFIER_LENGTH = 64;
    private static final double MAX_IDENTIFIER_RANKING_BOOST = 0.02d;
    private static final double MAX_CORE_TERM_RANKING_BOOST = 0.005d;
    private static final double MAX_NUMBER_RANKING_BOOST = 0.005d;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#._-]*");
    private static final Pattern ASCII_IDENTIFIER_PATTERN = Pattern.compile("^[A-Za-z][A-Za-z0-9+.#_-]*$");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d[\\d,]*(?:\\.\\d+)?");
    private static final Pattern QUESTION_ENDING_PATTERN = Pattern.compile(
            "(?:나요|습니까|인가요|일까요|죠)\\s*[.!。！]*$");
    private static final Pattern CORRECTION_MODALITY_PATTERN = Pattern.compile(
            "(?:하지\\s*(?:않|못)|사실이\\s*(?:아니|아닙)|거짓(?:입|이|으)|부인(?:합|했|하)"
                    + "|철회(?:합|했|하)|취소(?:합|했|하)|정정(?:합|했|하)|번복(?:합|했|하)"
                    + "|거둡|거두|되돌|미배포|사실무근|여부)");
    private static final Pattern SUPPORTED_CLAIM_PREFIX_ELEMENT_PATTERN = Pattern.compile(
            "\\s*(?:\\d{4}년\\s+\\d{1,2}월\\s+\\d{1,2}일에|문제없이|실제로)\\s+");
    private static final String SUPPORTED_PRODUCT_ALIAS_BODY = "[\\p{L}\\p{N} \\t+#_-]+";
    private static final Pattern SUPPORTED_QUOTED_PRODUCT_SUFFIX_PATTERN = Pattern.compile(
            "\\s+(?:\\\"" + SUPPORTED_PRODUCT_ALIAS_BODY + "\\\"|“"
                    + SUPPORTED_PRODUCT_ALIAS_BODY + "”|‘" + SUPPORTED_PRODUCT_ALIAS_BODY + "’"
                    + "|「" + SUPPORTED_PRODUCT_ALIAS_BODY + "」|『" + SUPPORTED_PRODUCT_ALIAS_BODY
                    + "』)(?:을|를|은|는)?\\s*$");
    private static final Pattern SUPPORTED_VERSION_SUFFIX_PATTERN = Pattern.compile(
            "\\s+v\\d+(?:\\.\\d+)+(?:을|를|은|는)?\\s*$");
    private static final Pattern SUPPORTED_TARGET_TEXT_PATTERN = Pattern.compile(
            "[\\p{L}\\p{N}\\s+#._-]+");
    private static final Set<String> GENERIC_ASCII_TERMS = Set.of(
            "a", "an", "and", "are", "as", "at", "be", "been", "being", "by",
            "did", "do", "does", "evidence", "experience", "find", "for", "from",
            "has", "have", "how", "in", "is", "of", "on", "or", "project",
            "projects", "production", "record", "role", "show", "the", "to", "used",
            "using", "was", "were", "what", "when", "where", "which", "why", "with",
            "without", "api", "endpoint", "endpoints", "db", "http", "https", "sql", "jwt", "ui", "pdf", "txt");
    private static final Set<String> QUERY_COMPLETED_RELEASE_FORMS = Set.of(
            "출시한", "출시했다", "출시했습니다", "출시했나요", "출시했다는",
            "출시하였다", "출시하였습니다", "출시하였나요", "출시하였는지", "출시했는지", "출시했어요",
            "배포한", "배포했다", "배포했습니다", "배포했나요", "배포했다는",
            "배포하였다", "배포하였습니다", "배포하였나요", "배포하였는지", "배포했는지", "배포했어요");
    private static final Set<String> QUERY_COMPLETED_RELEASE_SIGNAL_FORMS = Set.of(
            "출시", "출시한", "출시했다", "출시했습니다", "출시했나요", "출시했다는",
            "출시하였다", "출시하였습니다", "출시하였나요", "출시하였는지", "출시했는지", "출시했어요",
            "배포", "배포한", "배포했다", "배포했습니다", "배포했나요", "배포했다는",
            "배포하였다", "배포하였습니다", "배포하였나요", "배포하였는지", "배포했는지", "배포했어요");
    private static final Set<String> COMPLETED_RELEASE_NOUN_FORMS = Set.of("출시", "배포");
    private static final Set<String> COMPLETED_RELEASE_INTENT_MARKERS = Set.of(
            "이력", "경험", "여부");
    private static final Set<String> COMPLETED_RELEASE_QUERY_MODIFIERS = Set.of("실제로");
    private static final List<String> COMPLETION_GRAMMAR_PARTICLES = List.of(
            "이", "가", "을", "를", "은", "는", "의", "에", "도", "만");
    private static final Set<String> COMPLETED_RELEASE_EXISTENCE_FORMS = Set.of(
            "있", "있나요", "있나", "있어", "있습니까", "있었나", "있었나요",
            "있었어", "있는지");
    private static final Set<String> ATTRIBUTIVE_COMPLETED_RELEASE_FORMS = Set.of(
            "출시한", "출시했다는", "배포한", "배포했다는");
    private static final Set<String> CANDIDATE_COMPLETED_RELEASE_FORMS = Set.of(
            "출시했다", "출시했습니다", "출시하였다", "출시하였습니다", "출시했어요",
            "배포했다", "배포했습니다", "배포하였다", "배포하였습니다", "배포했어요");
    private static final List<String> DIRECT_IMPLEMENTATION_QUERY_TERMS = List.of(
            "구현", "만든", "만들", "endpoint", "엔드포인트");
    private static final List<String> IDENTIFIER_EVIDENCE_QUERY_TERMS = List.of("고유명사", "이름");
    private static final Set<String> DIRECT_EVIDENCE_NOUN_FORMS = Set.of("근거", "증거");
    private static final Set<String> DIRECT_EVIDENCE_REQUEST_FORMS = Set.of(
            "찾아줘", "보여줘", "확인해줘", "있나요", "있어", "있나");
    private static final Pattern PROJECT_NAME_DECLARATION_PATTERN = Pattern.compile(
            "^\\s*(?:[가-힣]+\\s+)?프로젝트\\s+이름은\\s+([a-z][a-z0-9+.#_-]*)이다\\s*[.!?]*\\s*$");
    private static final Pattern PROJECT_PARTICIPATION_DECLARATION_PATTERN = Pattern.compile(
            "^\\s*(?:[가-힣]+\\s+)?([a-z][a-z0-9+.#_-]*)\\s+프로젝트에서\\s+.+참여했다\\s*[.!?]*\\s*$");
    private static final Set<String> STOP_WORDS = Set.of(
            "근거", "보여", "찾아", "확인", "실제", "이력", "기록", "질문", "문서",
            "프로젝트", "경험", "이유", "어떻게", "무엇", "있는", "있", "없", "해줘",
            "보여줘", "있어", "있나", "있나요", "있었나", "있었어",
            "했나", "했어", "인가", "인지", "같은", "없이", "대한", "관련", "현재");
    private static final List<String> KOREAN_SUFFIXES = List.of(
            "했나요", "했다는", "했는지", "했어", "했나", "되는", "보존되는", "맡았다는",
            "출시한", "배포한", "개발한", "운영한", "적용한", "으로", "에서", "에게", "까지",
            "부터", "처럼", "보다", "이라도", "이라고", "라는", "하며", "해서", "해도",
            "하고", "한", "된", "하는", "했", "인", "인지", "인가", "이나", "나도", "에도",
            "에는", "에서", "으로", "로", "을", "를", "은", "는", "이", "가", "와", "과",
            "의", "에", "도", "만");
    private static final List<String> KOREAN_IDENTIFIER_SUFFIXES = List.of(
            "이다",
            "으로부터", "에게서", "에서는", "에서도", "이라고", "이라도", "이라면", "에는",
            "에도", "에서", "에게", "까지", "부터", "처럼", "보다", "으로", "라고", "라는",
            "로", "을", "를", "은", "는", "이", "가", "와", "과", "의", "에", "도", "만");
    private static final List<String> NEGATION_MARKERS = List.of(
            "않", "아니", "아닙", "없", "못", "미취득", "하지 않았다", "하지 않았");
    private static final List<String> POSITIVE_CLAIM_MARKERS = List.of(
            "근거가 있", "경험", "이력", "기록", "했나요", "맡았", "적용", "운영", "취득");
    private static final List<String> EXPLANATION_MARKERS = List.of(
            "어떻게", "이유", "왜", "방지", "막았");

    private final EvidenceQualityReranker evidenceQualityReranker = new EvidenceQualityReranker();

    public SearchIntent resolveIntent(String query) {
        return resolveIntent(querySignals(query));
    }

    /** Returns only existing P4 identifiers for an explicit experience or evidence request. */
    public Set<String> strongIdentifiersForEvidenceGuard(String query) {
        QuerySignals signals = querySignals(query);
        if (!signals.positiveClaimQuestion()
                && !signals.directImplementationEvidenceRequest()
                && !signals.identifierEvidenceRequest()) {
            return Set.of();
        }
        return signals.requiredIdentifiers();
    }

    public Decision apply(String query, List<VectorSearchResult> denseCandidates) {
        if (denseCandidates.isEmpty()) {
            return new Decision(List.of(), List.of(), List.of("NO_SEARCHABLE_CANDIDATES"));
        }

        QuerySignals signals = querySignals(query);
        return switch (resolveIntent(signals)) {
            case GENERAL -> applyProfile(
                    SearchIntent.GENERAL, query, denseCandidates, signals);
            case COMPLETED_RELEASE_EVIDENCE -> applyProfile(
                    SearchIntent.COMPLETED_RELEASE_EVIDENCE, query, denseCandidates, signals);
        };
    }

    private Decision applyProfile(
            SearchIntent intent,
            String query,
            List<VectorSearchResult> denseCandidates,
            QuerySignals signals) {
        List<VectorSearchResult> sourceDistinctCandidates = consolidateSourceLocations(
                        intent, query, signals, denseCandidates)
                .stream()
                .map(CandidateGroup::representative)
                .toList();
        List<VectorSearchResult> eligibleCandidates = sourceDistinctCandidates.stream()
                .filter(candidate -> rejectionReasons(intent, signals, candidate).isEmpty())
                .toList();
        if (eligibleCandidates.isEmpty()) {
            return new Decision(
                    denseCandidates,
                    List.of(),
                    rejectionReasons(intent, signals, sourceDistinctCandidates.get(0)));
        }

        List<VectorSearchResult> diverseCandidates = consolidateQueryEvidence(
                        intent,
                        query,
                        signals,
                        eligibleCandidates)
                .stream()
                .map(CandidateGroup::representative)
                .toList();
        if (intent == SearchIntent.GENERAL) {
            diverseCandidates = diverseCandidates.stream()
                    .sorted(generalRankingComparator(query, signals))
                    .toList();
        }
        List<VectorSearchResult> diverseResults = diverseCandidates.stream()
                .limit(MAX_RESULTS)
                .toList();
        return new Decision(
                denseCandidates,
                diverseResults,
                List.of());
    }

    private static SearchIntent resolveIntent(QuerySignals signals) {
        return signals.completionSensitiveQuery()
                ? SearchIntent.COMPLETED_RELEASE_EVIDENCE
                : SearchIntent.GENERAL;
    }

    private List<String> rejectionReasons(
            SearchIntent intent,
            QuerySignals signals,
            VectorSearchResult top) {
        if (intent == SearchIntent.COMPLETED_RELEASE_EVIDENCE) {
            return rejectionReasons(signals, top);
        }

        List<String> reasons = new ArrayList<>();
        if (top.score() < MINIMUM_DENSE_SCORE) {
            reasons.add("DENSE_SCORE_BELOW_TUNING_FLOOR");
        }
        if (signals.positiveClaimQuestion() && containsNegatedClaim(top.content(), signals)) {
            reasons.add("NEGATED_CLAIM");
        }
        return List.copyOf(reasons);
    }

    private List<CandidateGroup> consolidateSourceLocations(
            SearchIntent intent,
            String query,
            QuerySignals signals,
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
            if (isPreferredRepresentative(
                    intent, query, signals, candidate, duplicate.representative())) {
                duplicate.representative(candidate);
            }
        }
        return List.copyOf(groups);
    }

    private List<CandidateGroup> consolidateQueryEvidence(
            SearchIntent intent,
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
            if (isPreferredRepresentative(
                    intent, query, signals, candidate, duplicate.representative())) {
                duplicate.representative(candidate);
            }
        }
        return List.copyOf(groups);
    }

    private boolean sameQueryEvidence(
            QuerySignals signals,
            VectorSearchResult left,
            VectorSearchResult right) {
        CandidateSignals leftSignals = evidenceSignals(left);
        CandidateSignals rightSignals = evidenceSignals(right);
        Set<String> sharedCoreTerms = matchedCoreTermSet(signals.coreTerms(), leftSignals.coreTerms());
        sharedCoreTerms.retainAll(matchedCoreTermSet(signals.coreTerms(), rightSignals.coreTerms()));

        boolean sharedIdentifierAnchor = !signals.requiredIdentifiers().isEmpty()
                && signals.requiredIdentifiers().stream()
                        .allMatch(identifier -> leftSignals.identifiers().contains(identifier)
                                && rightSignals.identifiers().contains(identifier));
        boolean sharedNumberAnchor = !signals.requiredNumbers().isEmpty()
                && signals.requiredNumbers().stream()
                        .allMatch(number -> leftSignals.numbers().contains(number)
                                && rightSignals.numbers().contains(number));

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

    private boolean isPreferredRepresentative(
            SearchIntent intent,
            String query,
            QuerySignals signals,
            VectorSearchResult candidate,
            VectorSearchResult current) {
        if (intent == SearchIntent.COMPLETED_RELEASE_EVIDENCE) {
            return lexicalAffinity(query, candidate) > lexicalAffinity(query, current);
        }

        boolean candidateMeetsDenseFloor = candidate.score() >= MINIMUM_DENSE_SCORE;
        boolean currentMeetsDenseFloor = current.score() >= MINIMUM_DENSE_SCORE;
        if (candidateMeetsDenseFloor != currentMeetsDenseFloor) {
            return candidateMeetsDenseFloor;
        }
        return baseGeneralRankingScore(signals, candidate)
                > baseGeneralRankingScore(signals, current);
    }

    private Comparator<VectorSearchResult> generalRankingComparator(
            String query,
            QuerySignals signals) {
        return Comparator.comparingDouble(
                        (VectorSearchResult candidate) -> generalRankingScore(query, signals, candidate))
                .reversed()
                .thenComparing(Comparator.comparingDouble(VectorSearchResult::score).reversed());
    }

    private double generalRankingScore(
            String query,
            QuerySignals signals,
            VectorSearchResult candidate) {
        return baseGeneralRankingScore(signals, candidate)
                + evidenceQualityReranker.evaluate(query, candidate).adjustment();
    }

    private static double baseGeneralRankingScore(
            QuerySignals signals,
            VectorSearchResult candidate) {
        CandidateSignals candidateSignals = rankingSignals(candidate);
        double identifierBoost = matchRatio(
                        signals.requiredIdentifiers(), candidateSignals.identifiers())
                * MAX_IDENTIFIER_RANKING_BOOST;
        double coreTermBoost = matchRatio(
                        signals.coreTerms(), candidateSignals.coreTerms())
                * MAX_CORE_TERM_RANKING_BOOST;
        double numberBoost = matchRatio(
                        signals.requiredNumbers(), candidateSignals.numbers())
                * MAX_NUMBER_RANKING_BOOST;
        return candidate.score() + identifierBoost + coreTermBoost + numberBoost;
    }

    /** Exposes deterministic ranking components for tests and offline evaluation only. */
    public RankingExplanation explainRanking(String query, VectorSearchResult candidate) {
        QuerySignals signals = querySignals(query);
        double base = baseGeneralRankingScore(signals, candidate);
        double evidenceAdjustment = evidenceQualityReranker.evaluate(query, candidate).adjustment();
        return new RankingExplanation(
                candidate.score(),
                base - candidate.score(),
                evidenceAdjustment,
                base + evidenceAdjustment);
    }

    private static double matchRatio(Set<String> required, Set<String> candidate) {
        if (required.isEmpty()) {
            return 0.0d;
        }
        long matches = required.stream().filter(candidate::contains).count();
        return (double) matches / required.size();
    }

    private int lexicalAffinity(String query, VectorSearchResult candidate) {
        QuerySignals signals = querySignals(query);
        CandidateSignals candidateSignals = rankingSignals(candidate);
        int identifierMatches = (int) signals.requiredIdentifiers().stream()
                .filter(candidateSignals.identifiers()::contains)
                .count();
        int numberMatches = (int) signals.requiredNumbers().stream()
                .filter(candidateSignals.numbers()::contains)
                .count();
        int coreMatches = matchedCoreTerms(signals, candidateSignals);
        return (identifierMatches * 100) + (numberMatches * 100) + coreMatches;
    }

    private List<String> rejectionReasons(QuerySignals signals, VectorSearchResult top) {
        List<String> reasons = new ArrayList<>();
        CandidateSignals candidateSignals = evidenceSignals(top);
        if (!signals.hasExplicitEvidenceSignal()) {
            reasons.add("NO_EXPLICIT_EVIDENCE_SIGNAL");
        }
        if (top.score() < MINIMUM_DENSE_SCORE) {
            reasons.add("DENSE_SCORE_BELOW_TUNING_FLOOR");
        }
        if (signals.unsupportedCompletedReleaseQuery()) {
            reasons.add("UNSUPPORTED_COMPLETED_RELEASE_QUERY");
        }
        else if (signals.completedReleaseIntent()) {
            if (!hasAnchoredDirectCompletedReleaseClaim(top.content(), signals)) {
                reasons.add("MISSING_ASSERTED_COMPLETED_RELEASE_CLAIM");
            }
        }
        else if (signals.directCompletedReleaseEvidenceQuery()) {
            if (!hasAnchoredDirectCompletedReleaseClaim(top.content(), signals)
                    && !hasScopedDirectCompletedReleaseClaim(top.content(), signals)) {
                reasons.add("MISSING_ASSERTED_COMPLETED_RELEASE_CLAIM");
            }
        }
        else if (signals.exactCompletedReleaseFactQuery()) {
            if (!hasAnchoredExactCompletedReleaseFact(top.content(), signals)) {
                reasons.add("MISSING_ASSERTED_COMPLETED_RELEASE_FACT");
            }
        }
        else if (signals.directImplementationEvidenceRequest()
                && hasScopedDirectCompletedReleaseClaim(top.content(), signals)) {
            // The explicit project-name declaration binds only the immediately following assertion.
        }
        else if (signals.identifierEvidenceRequest()
                && hasDeclaredProjectIdentity(top.content(), signals)) {
            // A direct identifier-evidence request may cite only the explicit body declaration.
        }
        else {
            for (String identifier : signals.requiredIdentifiers()) {
                if (!candidateSignals.identifiers().contains(identifier)) {
                    reasons.add("MISSING_IDENTIFIER:" + identifier);
                }
            }
            for (String number : signals.requiredNumbers()) {
                if (!candidateSignals.numbers().contains(number)) {
                    reasons.add("MISSING_NUMBER:" + number);
                }
            }
            if (matchedCoreTerms(signals, candidateSignals) < requiredCoreTermMatches(signals)) {
                reasons.add("INSUFFICIENT_CORE_TERM_COVERAGE");
            }
        }
        if (!signals.completionSensitiveQuery()
                && signals.positiveClaimQuestion()
                && containsNegatedClaim(top.content(), signals)) {
            reasons.add("NEGATED_CLAIM");
        }
        return List.copyOf(reasons);
    }

    private static boolean hasScopedDirectCompletedReleaseClaim(String content, QuerySignals signals) {
        List<String> units = claimUnits(content);
        for (int index = 1; index < units.size(); index++) {
            String declaredProjectIdentifier = declaredProjectIdentifier(units.get(index - 1));
            if (declaredProjectIdentifier == null
                    || !signals.requiredIdentifiers().contains(declaredProjectIdentifier)
                    || !isAssertedCompletedReleaseClaim(units, index)) {
                continue;
            }
            Set<String> actionIdentifiers = new HashSet<>(signals.requiredIdentifiers());
            actionIdentifiers.remove(declaredProjectIdentifier);
            if (candidateSignals(units.get(index)).identifiers().containsAll(actionIdentifiers)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasDeclaredProjectIdentity(String content, QuerySignals signals) {
        return claimUnits(content).stream()
                .anyMatch(unit -> hasProjectNameDeclaration(unit, signals.requiredIdentifiers()));
    }

    private static boolean hasAnchoredExactCompletedReleaseFact(String content, QuerySignals signals) {
        List<String> units = claimUnits(content);
        for (int index = 0; index < units.size(); index++) {
            if (!isAssertedCompletedReleaseClaim(units, index)) {
                continue;
            }
            CandidateSignals unitSignals = candidateSignals(units.get(index));
            if (!unitSignals.numbers().containsAll(signals.requiredNumbers())) {
                continue;
            }
            if (unitSignals.identifiers().containsAll(signals.requiredIdentifiers())
                    || (index > 0 && hasProjectNameDeclaration(
                            units.get(index - 1), signals.requiredIdentifiers()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAssertedCompletedReleaseClaim(List<String> units, int index) {
        return hasTerminalCompletedReleasePredicate(units.get(index))
                && !isNonAssertiveFollowup(units.get(index))
                && (index + 1 >= units.size() || !isNonAssertiveFollowup(units.get(index + 1)));
    }

    private static boolean hasProjectNameDeclaration(String unit, Set<String> requiredIdentifiers) {
        String declaredProjectIdentifier = declaredProjectIdentifier(unit);
        return declaredProjectIdentifier != null && requiredIdentifiers.contains(declaredProjectIdentifier);
    }

    private static String declaredProjectIdentifier(String unit) {
        String normalizedUnit = normalized(unit);
        if (isNonAssertiveFollowup(normalizedUnit)) {
            return null;
        }
        Matcher nameDeclaration = PROJECT_NAME_DECLARATION_PATTERN.matcher(normalizedUnit);
        if (nameDeclaration.matches()) {
            return nameDeclaration.group(1);
        }
        Matcher participationDeclaration = PROJECT_PARTICIPATION_DECLARATION_PATTERN.matcher(normalizedUnit);
        return participationDeclaration.matches() ? participationDeclaration.group(1) : null;
    }

    private static CandidateSignals evidenceSignals(VectorSearchResult candidate) {
        return candidateSignals(candidate.content());
    }

    private static CandidateSignals rankingSignals(VectorSearchResult candidate) {
        return candidateSignals(candidate.documentTitle() + " " + candidate.content());
    }

    private boolean containsNegatedClaim(String content, QuerySignals signals) {
        return Arrays.stream(content.split("[.!?\\n]"))
                .map(CompositeSearchProfile::normalized)
                .anyMatch(sentence -> {
                    CandidateSignals sentenceSignals = candidateSignals(sentence);
                    int requiredCoreMatches = requiredCoreTermMatches(signals);
                    boolean anchoredByIdentifier = signals.requiredIdentifiers().stream()
                            .anyMatch(sentenceSignals.identifiers()::contains);
                    boolean anchoredByCoreTerms = requiredCoreMatches > 0
                            && matchedCoreTerms(signals.coreTerms(), sentenceSignals.coreTerms())
                            >= requiredCoreMatches;
                    return NEGATION_MARKERS.stream().anyMatch(sentence::contains)
                            && (anchoredByIdentifier || anchoredByCoreTerms);
                });
    }

    private QuerySignals querySignals(String query) {
        String normalizedQuery = normalized(query);
        Set<String> identifiers = identifierTokens(query).stream()
                .filter(identifier -> identifier.length() <= MAX_IDENTIFIER_LENGTH)
                .filter(identifier -> !GENERIC_ASCII_TERMS.contains(identifier))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Set<String> numbers = numberTokens(query);

        CompletionQueryParse completionQuery = parseCompletionQuery(query);
        if ((completionQuery.intent() == CompletionQueryIntent.UNSUPPORTED
                        || completionQuery.intent() == CompletionQueryIntent.DIRECT_EVIDENCE)
                && isSupportedExactCompletedReleaseFactQuery(normalizedQuery, identifiers, numbers)) {
            completionQuery = CompletionQueryParse.factQualifier();
        }
        Set<String> coreTerms = queryCoreTerms(
                normalizedQuery,
                identifiers,
                numbers,
                completionQuery.intent() != CompletionQueryIntent.NONE
                        ? QUERY_COMPLETED_RELEASE_SIGNAL_FORMS
                        : QUERY_COMPLETED_RELEASE_FORMS);
        boolean positiveClaim = POSITIVE_CLAIM_MARKERS.stream().anyMatch(normalizedQuery::contains)
                && EXPLANATION_MARKERS.stream().noneMatch(normalizedQuery::contains);
        return new QuerySignals(
                Set.copyOf(identifiers),
                Set.copyOf(numbers),
                Set.copyOf(coreTerms),
                completionQuery.intent(),
                completionQuery.targetTokens(),
                positiveClaim,
                isDirectImplementationEvidenceRequest(normalizedQuery),
                isIdentifierEvidenceRequest(normalizedQuery));
    }

    private static boolean isSupportedExactCompletedReleaseFactQuery(
            String normalizedQuery,
            Set<String> identifiers,
            Set<String> numbers) {
        return !identifiers.isEmpty()
                && !numbers.isEmpty()
                && (normalizedQuery.contains("배포") || normalizedQuery.contains("출시"))
                && (normalizedQuery.contains("근거")
                        || normalizedQuery.contains("증거")
                        || normalizedQuery.contains("날짜")
                        || normalizedQuery.contains("일자"));
    }

    private static boolean isDirectImplementationEvidenceRequest(String normalizedQuery) {
        return DIRECT_IMPLEMENTATION_QUERY_TERMS.stream().anyMatch(normalizedQuery::contains);
    }

    private static boolean isIdentifierEvidenceRequest(String normalizedQuery) {
        return IDENTIFIER_EVIDENCE_QUERY_TERMS.stream().anyMatch(normalizedQuery::contains);
    }

    private static Set<String> queryCoreTerms(
            String query,
            Set<String> identifiers,
            Set<String> numbers,
            Set<String> semanticForms) {
        Set<String> coreTerms = new LinkedHashSet<>();
        for (String token : lexicalTokens(query, semanticForms)) {
            if (!identifiers.contains(token)
                    && !numbers.contains(token)
                    && !GENERIC_ASCII_TERMS.contains(token)
                    && !STOP_WORDS.contains(token)) {
                coreTerms.add(token);
            }
        }
        return Set.copyOf(coreTerms);
    }

    private static CandidateSignals candidateSignals(String value) {
        return new CandidateSignals(
                identifierTokens(value),
                numberTokens(value),
                lexicalTokens(value, CANDIDATE_COMPLETED_RELEASE_FORMS),
                containsAssertedCompletedReleaseClaim(value));
    }

    private static int requiredCoreTermMatches(QuerySignals signals) {
        int signalCount = signals.coreTerms().size() + (signals.completedReleaseIntent() ? 1 : 0);
        return Math.min(MINIMUM_CORE_TERM_MATCHES, signalCount);
    }

    private static int matchedCoreTerms(QuerySignals signals, CandidateSignals candidateSignals) {
        int exactMatches = matchedCoreTerms(signals.coreTerms(), candidateSignals.coreTerms());
        int completedReleaseMatch = signals.completedReleaseIntent()
                        && candidateSignals.completedReleaseClaim()
                ? 1
                : 0;
        return exactMatches + completedReleaseMatch;
    }

    private static int matchedCoreTerms(Set<String> coreTerms, Set<String> candidateTerms) {
        return (int) coreTerms.stream().filter(candidateTerms::contains).count();
    }

    private static Set<String> matchedCoreTermSet(Set<String> coreTerms, Set<String> candidateTerms) {
        Set<String> matches = new HashSet<>();
        coreTerms.stream().filter(candidateTerms::contains).forEach(matches::add);
        return matches;
    }

    private static Set<String> lexicalTokens(String value, Set<String> semanticForms) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(normalized(value));
        while (matcher.find()) {
            String rawToken = stripTokenBoundaryPunctuation(matcher.group());
            if (semanticForms.contains(rawToken)) {
                continue;
            }
            String token = stem(rawToken);
            if (token.length() >= 2) {
                tokens.add(token);
            }
        }
        return Set.copyOf(tokens);
    }

    private static CompletionQueryParse parseCompletionQuery(String query) {
        String normalizedQuery = normalized(query);
        List<GrammarToken> positionedTokens = positionedGrammarTokens(normalizedQuery);
        List<String> tokens = positionedTokens.stream().map(GrammarToken::value).toList();
        List<CompletionQueryCandidate> candidates = new ArrayList<>();
        boolean separatedReleaseAuxiliary = false;
        boolean malformedNominalIntent = false;
        for (int index = 0; index < tokens.size(); index++) {
            String token = tokens.get(index);
            if (QUERY_COMPLETED_RELEASE_FORMS.contains(token)) {
                candidates.add(new CompletionQueryCandidate(
                        index,
                        index + 1,
                        ATTRIBUTIVE_COMPLETED_RELEASE_FORMS.contains(token)
                                ? CompletionQueryProduction.ATTRIBUTIVE
                                : CompletionQueryProduction.FINITE));
                continue;
            }
            if (COMPLETED_RELEASE_NOUN_FORMS.contains(normalizedRegisteredGrammarToken(
                            token,
                            COMPLETED_RELEASE_NOUN_FORMS))
                    && index + 1 < tokens.size()) {
                String nextToken = normalizedRegisteredGrammarToken(
                        tokens.get(index + 1),
                        COMPLETED_RELEASE_INTENT_MARKERS);
                if (COMPLETED_RELEASE_INTENT_MARKERS.contains(nextToken)) {
                    candidates.add(new CompletionQueryCandidate(
                            index,
                            index + 2,
                            CompletionQueryProduction.NOMINAL));
                }
                else if (isCompletionAuxiliaryToken(
                        stripTokenBoundaryPunctuation(tokens.get(index + 1)))) {
                    separatedReleaseAuxiliary = true;
                }
                else if (isUnsupportedIntentMarkerToken(tokens.get(index + 1))) {
                    malformedNominalIntent = true;
                }
            }
        }

        List<List<String>> supportedTargets = new ArrayList<>();
        List<List<String>> directEvidenceTargets = new ArrayList<>();
        boolean supportedSeparators = hasSupportedCompletionQuerySeparators(
                normalizedQuery,
                positionedTokens);
        for (CompletionQueryCandidate candidate : candidates) {
            List<String> targetTokens = completionTargetTokens(
                    tokens.subList(0, candidate.releaseIndex()));
            List<String> tail = tokens.subList(candidate.tailStart(), tokens.size());
            if (!targetTokens.isEmpty()
                    && isSupportedCompletionTarget(targetTokens)
                    && supportedSeparators
                    && isSupportedCompletionQueryTail(tail, candidate.production())) {
                supportedTargets.add(targetTokens);
            }
            if (!targetTokens.isEmpty()
                    && candidate.production() == CompletionQueryProduction.ATTRIBUTIVE
                    && isSupportedCompletionTarget(targetTokens)
                    && supportedSeparators
                    && isSupportedDirectEvidenceTail(tail)) {
                directEvidenceTargets.add(targetTokens);
            }
        }
        if (supportedTargets.size() == 1) {
            return new CompletionQueryParse(
                    CompletionQueryIntent.SUPPORTED,
                    supportedTargets.get(0));
        }
        if (directEvidenceTargets.size() == 1) {
            return CompletionQueryParse.directEvidence(directEvidenceTargets.get(0));
        }
        if (!candidates.isEmpty()) {
            return CompletionQueryParse.unsupported();
        }
        if (separatedReleaseAuxiliary
                || malformedNominalIntent
                || tokens.stream().anyMatch(CompositeSearchProfile::isUnsupportedReleaseQueryToken)) {
            return CompletionQueryParse.unsupported();
        }
        return CompletionQueryParse.none();
    }

    private static List<String> completionTargetTokens(List<String> tokens) {
        List<String> targetTokens = new ArrayList<>();
        for (String token : tokens) {
            if (COMPLETED_RELEASE_QUERY_MODIFIERS.contains(token)) {
                continue;
            }
            targetTokens.add(normalizedCompletionTargetToken(token));
        }
        return List.copyOf(targetTokens);
    }

    private static boolean isSupportedCompletionTarget(List<String> targetTokens) {
        return targetTokens.stream().allMatch(CompositeSearchProfile::isSupportedCompletionTargetToken);
    }

    private static boolean isSupportedCompletionTargetToken(String token) {
        if (token.isBlank()) {
            return false;
        }
        for (int index = 0; index < token.length(); index++) {
            if (token.charAt(index) == '.'
                    && (index == 0
                            || index + 1 >= token.length()
                            || !isAsciiLetterOrDigit(token.charAt(index - 1))
                            || !isAsciiLetterOrDigit(token.charAt(index + 1)))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSupportedCompletionQueryTail(
            List<String> tokens,
            CompletionQueryProduction production) {
        List<String> tail = tokens.stream()
                .map(CompositeSearchProfile::stripTokenBoundaryPunctuation)
                .toList();
        return switch (production) {
            case ATTRIBUTIVE -> (tail.size() == 1
                            && COMPLETED_RELEASE_INTENT_MARKERS.contains(
                                    normalizedRegisteredGrammarToken(
                                            tail.get(0),
                                            COMPLETED_RELEASE_INTENT_MARKERS)))
                    || (tail.size() == 2
                            && COMPLETED_RELEASE_INTENT_MARKERS.contains(
                                    normalizedRegisteredGrammarToken(
                                            tail.get(0),
                                            COMPLETED_RELEASE_INTENT_MARKERS))
                            && COMPLETED_RELEASE_EXISTENCE_FORMS.contains(tail.get(1)));
            case FINITE -> tail.isEmpty();
            case NOMINAL -> tail.isEmpty()
                    || (tail.size() == 1
                            && COMPLETED_RELEASE_EXISTENCE_FORMS.contains(tail.get(0)));
        };
    }

    private static boolean isSupportedDirectEvidenceTail(List<String> tokens) {
        List<String> tail = tokens.stream()
                .map(CompositeSearchProfile::stripTokenBoundaryPunctuation)
                .toList();
        int evidenceNounIndex = tail.size() == 3 && "직접".equals(tail.get(0)) ? 1 : 0;
        return tail.size() == evidenceNounIndex + 2
                && DIRECT_EVIDENCE_NOUN_FORMS.contains(normalizedRegisteredGrammarToken(
                        tail.get(evidenceNounIndex), DIRECT_EVIDENCE_NOUN_FORMS))
                && DIRECT_EVIDENCE_REQUEST_FORMS.contains(tail.get(evidenceNounIndex + 1));
    }

    private static boolean hasSupportedCompletionQuerySeparators(
            String normalizedQuery,
            List<GrammarToken> tokens) {
        if (tokens.isEmpty()
                || !normalizedQuery.substring(0, tokens.get(0).start()).isBlank()) {
            return false;
        }
        for (int index = 1; index < tokens.size(); index++) {
            if (!normalizedQuery.substring(
                            tokens.get(index - 1).end(),
                            tokens.get(index).start())
                    .isBlank()) {
                return false;
            }
        }
        String suffix = normalizedQuery.substring(tokens.get(tokens.size() - 1).end());
        for (int index = 0; index < suffix.length(); index++) {
            char character = suffix.charAt(index);
            if (!Character.isWhitespace(character) && !isTerminalPunctuation(character)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isCompletionAuxiliaryToken(String token) {
        return token.startsWith("하")
                || token.startsWith("했")
                || token.startsWith("한")
                || token.startsWith("되")
                || token.startsWith("됐");
    }

    private static boolean isUnsupportedReleaseQueryToken(String token) {
        String normalizedToken = stripTokenBoundaryPunctuation(token);
        if (COMPLETED_RELEASE_NOUN_FORMS.contains(normalizedToken)) {
            return false;
        }
        return COMPLETED_RELEASE_NOUN_FORMS.stream().anyMatch(normalizedToken::contains);
    }

    private static boolean isUnsupportedIntentMarkerToken(String token) {
        String normalizedToken = stripTokenBoundaryPunctuation(token);
        if (COMPLETED_RELEASE_INTENT_MARKERS.contains(normalizedRegisteredGrammarToken(
                normalizedToken,
                COMPLETED_RELEASE_INTENT_MARKERS))) {
            return false;
        }
        return COMPLETED_RELEASE_INTENT_MARKERS.stream().anyMatch(normalizedToken::contains);
    }

    private static List<String> grammarTokens(String value) {
        return positionedGrammarTokens(normalized(value)).stream()
                .map(GrammarToken::value)
                .toList();
    }

    private static List<GrammarToken> positionedGrammarTokens(String normalizedValue) {
        List<GrammarToken> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(normalizedValue);
        while (matcher.find()) {
            String token = stripTokenBoundaryPunctuation(matcher.group());
            tokens.add(new GrammarToken(
                    token,
                    matcher.start(),
                    matcher.start() + token.length()));
        }
        return List.copyOf(tokens);
    }

    private static String normalizedRegisteredGrammarToken(
            String token,
            Set<String> registeredForms) {
        String normalizedToken = stripTokenBoundaryPunctuation(token);
        if (registeredForms.contains(normalizedToken)) {
            return normalizedToken;
        }
        for (String particle : COMPLETION_GRAMMAR_PARTICLES) {
            if (normalizedToken.endsWith(particle)) {
                String candidate = normalizedToken.substring(
                        0,
                        normalizedToken.length() - particle.length());
                if (registeredForms.contains(candidate)) {
                    return candidate;
                }
            }
        }
        return normalizedToken;
    }

    private static String normalizedCompletionTargetToken(String token) {
        return stripKoreanIdentifierSuffix(stripTokenBoundaryPunctuation(token));
    }

    private boolean hasAnchoredDirectCompletedReleaseClaim(String content, QuerySignals querySignals) {
        List<String> units = claimUnits(content);
        for (int index = 0; index < units.size(); index++) {
            if (!isSupportedDirectCompletedReleaseClaim(units.get(index), querySignals)) {
                continue;
            }
            if (index + 1 < units.size()
                    && isNonAssertiveFollowup(units.get(index + 1))) {
                continue;
            }
            return true;
        }
        return false;
    }

    private static boolean containsAssertedCompletedReleaseClaim(String value) {
        List<String> units = claimUnits(value);
        for (int index = 0; index < units.size(); index++) {
            if (hasTerminalCompletedReleasePredicate(units.get(index))
                    && (index + 1 >= units.size()
                            || !isNonAssertiveFollowup(units.get(index + 1)))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isSupportedDirectCompletedReleaseClaim(
            String unit,
            QuerySignals querySignals) {
        String normalizedUnit = normalized(unit).trim();
        Matcher matcher = TOKEN_PATTERN.matcher(normalizedUnit);
        while (matcher.find()) {
            String token = stripTokenBoundaryPunctuation(matcher.group());
            if (!CANDIDATE_COMPLETED_RELEASE_FORMS.contains(token)) {
                continue;
            }
            String prefix = normalizedUnit.substring(0, matcher.start());
            String suffix = normalizedUnit.substring(matcher.start() + token.length());
            if (isPlainAssertionSuffix(suffix)
                    && matchesSupportedClaimTarget(prefix, querySignals.completionTargetTokens())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasTerminalCompletedReleasePredicate(String unit) {
        String normalizedUnit = normalized(unit).trim();
        Matcher matcher = TOKEN_PATTERN.matcher(normalizedUnit);
        while (matcher.find()) {
            String token = stripTokenBoundaryPunctuation(matcher.group());
            if (CANDIDATE_COMPLETED_RELEASE_FORMS.contains(token)
                    && isPlainAssertionSuffix(
                            normalizedUnit.substring(matcher.start() + token.length()))) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesSupportedClaimTarget(
            String prefix,
            List<String> expectedTargetTokens) {
        int offset = 0;
        while (true) {
            if (matchesSupportedClaimTargetAfterPrefix(
                    prefix.substring(offset).trim(),
                    expectedTargetTokens)) {
                return true;
            }
            Matcher supportedPrefix = SUPPORTED_CLAIM_PREFIX_ELEMENT_PATTERN.matcher(prefix);
            supportedPrefix.region(offset, prefix.length());
            if (!supportedPrefix.lookingAt() || supportedPrefix.end() == offset) {
                return false;
            }
            offset = supportedPrefix.end();
        }
    }

    private static boolean matchesSupportedClaimTargetAfterPrefix(
            String targetText,
            List<String> expectedTargetTokens) {
        Matcher quotedProduct = SUPPORTED_QUOTED_PRODUCT_SUFFIX_PATTERN.matcher(targetText);
        if (quotedProduct.find()) {
            return matchesTargetTokens(
                    targetText.substring(0, quotedProduct.start()).trim(),
                    expectedTargetTokens);
        }
        if (matchesTargetTokens(targetText, expectedTargetTokens)) {
            return true;
        }
        Matcher version = SUPPORTED_VERSION_SUFFIX_PATTERN.matcher(targetText);
        return version.find()
                && matchesTargetTokens(
                        targetText.substring(0, version.start()).trim(),
                        expectedTargetTokens);
    }

    private static boolean matchesTargetTokens(
            String targetText,
            List<String> expectedTargetTokens) {
        if (targetText.isEmpty()
                || !SUPPORTED_TARGET_TEXT_PATTERN.matcher(targetText).matches()
                || !hasWhitespaceOnlyGrammarTokenSeparators(targetText)) {
            return false;
        }
        List<String> actualTargetTokens = grammarTokens(targetText).stream()
                .map(CompositeSearchProfile::normalizedCompletionTargetToken)
                .toList();
        return actualTargetTokens.equals(expectedTargetTokens);
    }

    private static boolean hasWhitespaceOnlyGrammarTokenSeparators(String value) {
        String normalizedValue = normalized(value);
        List<GrammarToken> tokens = positionedGrammarTokens(normalizedValue);
        if (tokens.isEmpty()
                || !normalizedValue.substring(0, tokens.get(0).start()).isBlank()) {
            return false;
        }
        for (int index = 1; index < tokens.size(); index++) {
            if (!normalizedValue.substring(
                            tokens.get(index - 1).end(),
                            tokens.get(index).start())
                    .isBlank()) {
                return false;
            }
        }
        return normalizedValue.substring(tokens.get(tokens.size() - 1).end()).isBlank();
    }

    private static boolean isPlainAssertionSuffix(String value) {
        boolean terminalPunctuation = false;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (!Character.isWhitespace(character)
                    && character != '.'
                    && character != '!'
                    && character != '！'
                    && character != '。') {
                return false;
            }
            if (!Character.isWhitespace(character)) {
                terminalPunctuation = true;
            }
        }
        return terminalPunctuation;
    }

    private static boolean isNonAssertiveFollowup(String unit) {
        String normalizedUnit = normalized(unit).trim();
        return normalizedUnit.indexOf('?') >= 0
                || normalizedUnit.indexOf('？') >= 0
                || QUESTION_ENDING_PATTERN.matcher(normalizedUnit).find()
                || CORRECTION_MODALITY_PATTERN.matcher(normalizedUnit).find();
    }

    private static List<String> claimUnits(String value) {
        String normalizedValue = normalized(value);
        List<String> units = new ArrayList<>();
        int unitStart = 0;
        int index = 0;
        while (index < normalizedValue.length()) {
            char character = normalizedValue.charAt(index);
            if (character == '\r' || character == '\n') {
                addClaimUnits(units, normalizedValue.substring(unitStart, index));
                index = skipWhitespace(normalizedValue, index);
                unitStart = index;
                continue;
            }
            if (isSentenceBoundary(normalizedValue, index)) {
                int unitEnd = index + 1;
                while (unitEnd < normalizedValue.length()
                        && isTerminalPunctuation(normalizedValue.charAt(unitEnd))) {
                    unitEnd++;
                }
                while (unitEnd < normalizedValue.length()
                        && isClosingClaimDelimiter(normalizedValue.charAt(unitEnd))) {
                    unitEnd++;
                }
                addClaimUnits(units, normalizedValue.substring(unitStart, unitEnd));
                index = skipWhitespace(normalizedValue, unitEnd);
                unitStart = index;
                continue;
            }
            index++;
        }
        addClaimUnits(units, normalizedValue.substring(unitStart));
        return List.copyOf(units);
    }

    private static void addClaimUnits(List<String> units, String value) {
        int clauseStart = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (isClauseDelimiter(value, clauseStart, index, character)) {
                addNonEmptyClaimUnit(units, value.substring(clauseStart, index));
                clauseStart = index + 1;
            }
        }
        addNonEmptyClaimUnit(units, value.substring(clauseStart));
    }

    private static void addNonEmptyClaimUnit(List<String> units, String value) {
        String unit = value.trim();
        if (!unit.isEmpty()) {
            units.add(unit);
        }
    }

    private static boolean isEmbeddedNumericSeparator(String value, int index) {
        return index > 0
                && index + 1 < value.length()
                && Character.isDigit(value.charAt(index - 1))
                && Character.isDigit(value.charAt(index + 1));
    }

    private static boolean isClauseDelimiter(
            String value,
            int clauseStart,
            int index,
            char character) {
        if (isEmbeddedNumericSeparator(value, index)) {
            return false;
        }
        if (character == ';' || character == '；') {
            return true;
        }
        return (character == ',' || character == '，')
                && hasFiniteClauseEnding(value.substring(clauseStart, index));
    }

    private static boolean hasFiniteClauseEnding(String value) {
        Matcher matcher = TOKEN_PATTERN.matcher(value);
        String lastToken = "";
        while (matcher.find()) {
            lastToken = stripTokenBoundaryPunctuation(matcher.group());
        }
        return lastToken.endsWith("다")
                || lastToken.endsWith("요")
                || lastToken.endsWith("죠")
                || lastToken.endsWith("까");
    }

    private static int skipWhitespace(String value, int offset) {
        int index = offset;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return index;
    }

    private static boolean isSentenceBoundary(String value, int index) {
        char character = value.charAt(index);
        if (character == '.' && index > 0 && index + 1 < value.length()
                && isAsciiLetterOrDigit(value.charAt(index - 1))
                && isAsciiLetterOrDigit(value.charAt(index + 1))) {
            return false;
        }
        return character == '.'
                || character == '!'
                || character == '！'
                || character == '。'
                || character == '?'
                || character == '？';
    }

    private static boolean isAsciiLetterOrDigit(char character) {
        return (character >= 'a' && character <= 'z')
                || (character >= 'A' && character <= 'Z')
                || (character >= '0' && character <= '9');
    }

    private static boolean isTerminalPunctuation(char character) {
        return character == '.'
                || character == '!'
                || character == '！'
                || character == '。'
                || character == '?'
                || character == '？';
    }

    private static boolean isClosingClaimDelimiter(char character) {
        return character == '"'
                || character == '\''
                || character == '’'
                || character == '”'
                || character == '」'
                || character == '』'
                || character == ']'
                || character == '】'
                || character == '〉'
                || character == '》';
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
        return SearchTokenNormalizer.normalize(value);
    }

    private static Set<String> identifierTokens(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = TOKEN_PATTERN.matcher(normalized(value));
        while (matcher.find()) {
            String token = stripKoreanIdentifierSuffix(stripTokenBoundaryPunctuation(matcher.group()));
            if (ASCII_IDENTIFIER_PATTERN.matcher(token).matches()) {
                tokens.add(token);
            }
        }
        return Set.copyOf(tokens);
    }

    private static String stripKoreanIdentifierSuffix(String value) {
        for (String suffix : KOREAN_IDENTIFIER_SUFFIXES) {
            if (!value.endsWith(suffix) || value.length() == suffix.length()) {
                continue;
            }
            String candidate = value.substring(0, value.length() - suffix.length());
            if (ASCII_IDENTIFIER_PATTERN.matcher(candidate).matches()) {
                return candidate;
            }
        }
        return value;
    }

    private static String stripTokenBoundaryPunctuation(String value) {
        int end = value.length();
        while (end > 0) {
            char last = value.charAt(end - 1);
            if (last != '.' && last != '_' && last != '-') {
                break;
            }
            end--;
        }
        return value.substring(0, end);
    }

    private static Set<String> numberTokens(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        Matcher matcher = NUMBER_PATTERN.matcher(normalized(value));
        while (matcher.find()) {
            tokens.add(normalizedNumberToken(matcher.group()));
        }
        return Set.copyOf(tokens);
    }

    private static String normalizedNumberToken(String value) {
        return new BigDecimal(value.replace(",", ""))
                .stripTrailingZeros()
                .toPlainString();
    }

    public record RankingExplanation(
            double denseScore,
            double existingProfileAdjustment,
            double evidenceAdjustment,
            double finalRankingValue) {
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
            CompletionQueryIntent completionQueryIntent,
            List<String> completionTargetTokens,
            boolean positiveClaimQuestion,
            boolean directImplementationEvidenceRequest,
            boolean identifierEvidenceRequest) {

        private QuerySignals {
            completionTargetTokens = List.copyOf(completionTargetTokens);
        }

        private boolean completedReleaseIntent() {
            return completionQueryIntent == CompletionQueryIntent.SUPPORTED;
        }

        private boolean directCompletedReleaseEvidenceQuery() {
            return completionQueryIntent == CompletionQueryIntent.DIRECT_EVIDENCE;
        }

        private boolean unsupportedCompletedReleaseQuery() {
            return completionQueryIntent == CompletionQueryIntent.UNSUPPORTED;
        }

        private boolean exactCompletedReleaseFactQuery() {
            return completionQueryIntent == CompletionQueryIntent.FACT_QUALIFIER;
        }

        private boolean completionSensitiveQuery() {
            return completionQueryIntent != CompletionQueryIntent.NONE;
        }

        private boolean hasExplicitEvidenceSignal() {
            return !requiredIdentifiers.isEmpty()
                    || !requiredNumbers.isEmpty()
                    || !coreTerms.isEmpty()
                    || completionSensitiveQuery();
        }
    }

    private enum CompletionQueryIntent {
        NONE,
        SUPPORTED,
        DIRECT_EVIDENCE,
        FACT_QUALIFIER,
        UNSUPPORTED
    }

    private record CompletionQueryParse(
            CompletionQueryIntent intent,
            List<String> targetTokens) {

        private CompletionQueryParse {
            targetTokens = List.copyOf(targetTokens);
        }

        private static CompletionQueryParse none() {
            return new CompletionQueryParse(CompletionQueryIntent.NONE, List.of());
        }

        private static CompletionQueryParse unsupported() {
            return new CompletionQueryParse(CompletionQueryIntent.UNSUPPORTED, List.of());
        }

        private static CompletionQueryParse factQualifier() {
            return new CompletionQueryParse(CompletionQueryIntent.FACT_QUALIFIER, List.of());
        }

        private static CompletionQueryParse directEvidence(List<String> targetTokens) {
            return new CompletionQueryParse(CompletionQueryIntent.DIRECT_EVIDENCE, targetTokens);
        }
    }

    private record CompletionQueryCandidate(
            int releaseIndex,
            int tailStart,
            CompletionQueryProduction production) {
    }

    private enum CompletionQueryProduction {
        ATTRIBUTIVE,
        FINITE,
        NOMINAL
    }

    private record GrammarToken(
            String value,
            int start,
            int end) {
    }

    private record CandidateSignals(
            Set<String> identifiers,
            Set<String> numbers,
            Set<String> coreTerms,
            boolean completedReleaseClaim) {
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
