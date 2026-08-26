package com.prizm.search.service;

import com.prizm.search.repository.EvidenceChunk;
import com.prizm.search.repository.EvidenceExpansionRepository;
import com.prizm.search.repository.VectorSearchResult;
import com.prizm.search.profile.SearchTokenNormalizer;
import com.prizm.search.service.SearchSnippetGenerator.SnippetSelection;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 이미 선택된 검색 결과에서 질의에 더 직접적인 원문 구간을 찾아 표시한다.
 *
 * <p>먼저 선택된 청크 안에서 근거를 위치화한다. 그 구간이 충분히 직접적이지 않을 때만 같은
 * ACTIVE 문서 버전의 주변 청크를 살피며, 질의의 직접 식별자를 보존하고 더 나은 근거라는
 * 조건을 충족해야 교체한다. 이 과정은 표시할 근거를 고르는 단계일 뿐 검색 결과 자체의
 * 선택이나 순위를 바꾸지 않는다.</p>
 *
 * <p>확장 조회나 위치화에 실패하면 선택된 청크의 원문으로 돌아간다. 응답 생성을 위한 보조
 * 단계의 실패가 유효한 검색 결과까지 없애지 않게 하려는 경계다.</p>
 */
@Service
public class EvidenceExpansionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EvidenceExpansionService.class);
    private static final Pattern SUMMARY_CONTENT = Pattern.compile(
            "(?is)(?:portfolio focus|(?:포트폴리오|문서).{0,80}(?:요약|소개|정리)|간단히\s+요약|검증\s+기준)");
    private static final Pattern STRUCTURED_DETAIL = Pattern.compile(
            "(?:문제\s*원인|해결\s*과정|테스트\s*결과|성능\s*측정\s*결과|검증\s*결과)");
    private static final Pattern ASCII_ANCHOR = Pattern.compile(
            "(?<![a-z0-9+#._-])[a-z][a-z0-9+#._-]+(?![a-z0-9+#._-])");
    private static final Pattern EVIDENCE_TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#._-]*");
    private static final Set<String> GENERIC_EVIDENCE_TERMS = Set.of(
            "경험", "문제", "해결", "처리", "데이터", "어떻게", "알려줘", "있어", "있게", "했던", "적");
    private static final List<String> EVIDENCE_SUFFIXES = List.of(
            "에서", "으로", "했던", "하는", "되는", "했다", "해본", "한", "을", "를",
            "이", "가", "은", "는", "과", "와", "의", "에", "로");
    private static final Comparator<ExpansionCandidate> DIRECTNESS_ORDER = Comparator
            .comparing((ExpansionCandidate candidate) -> candidate.selection().exactPhrase())
            .thenComparingInt(ExpansionCandidate::phraseMatches)
            .thenComparing(candidate -> !candidate.summary())
            .thenComparingInt(candidate -> candidate.selection().queryCoverage())
            .thenComparingInt(candidate -> candidate.selection().numericMatches())
            .thenComparing(candidate -> candidate.selection().narrative())
            .thenComparing(ExpansionCandidate::structuredDetail)
            .thenComparingInt(candidate -> candidate.selection().anchorScore())
            .thenComparing(candidate -> candidate.chunk().chunkNo(), Comparator.reverseOrder());

    private final EvidenceExpansionRepository evidenceExpansionRepository;
    private final SearchSnippetGenerator searchSnippetGenerator;

    public EvidenceExpansionService(
            EvidenceExpansionRepository evidenceExpansionRepository,
            SearchSnippetGenerator searchSnippetGenerator) {
        this.evidenceExpansionRepository = evidenceExpansionRepository;
        this.searchSnippetGenerator = searchSnippetGenerator;
    }

    /** 선택된 결과를 유지하면서 응답에 제시할 가장 직접적인 원문 구간과 출처를 고른다. */
    public EvidencePresentation select(Long ownerUserId, String query, VectorSearchResult result) {
        String fallbackContent = Objects.requireNonNullElse(result.content(), "");
        try {
            SnippetSelection localSelection = searchSnippetGenerator.selectForLocalization(query, result.content());
            EvidencePresentation localPresentation = presentation(
                    localSnippetPreservingDirectAnchors(query, localSelection, fallbackContent),
                    result.chunkId(),
                    result.sourceType(),
                    result.sourceIndex(),
                    result.sourceLabel());
            ExpansionCandidate localCandidate = candidate(
                    query,
                    new EvidenceChunk(
                            result.chunkId(),
                            result.chunkNo(),
                            result.sourceType(),
                            result.sourceIndex(),
                            result.sourceLabel(),
                            fallbackContent),
                    localSelection);
            if (hasRequiredDirectAnchor(query, localCandidate)
                    || isSufficientLocalEvidence(query, localCandidate)) {
                return localPresentation;
            }
            // 검색 범위를 넓히지 않고, 이미 선택된 문서의 같은 ACTIVE 버전 안에서만 보완한다.
            return evidenceExpansionRepository.findActiveVersionChunks(
                            ownerUserId,
                            result.documentId(),
                            result.documentVersionId()).stream()
                    .filter(chunk -> !chunk.chunkId().equals(result.chunkId()))
                    .map(chunk -> candidate(
                            query,
                            chunk,
                            searchSnippetGenerator.selectForLocalization(query, chunk.content())))
                    .filter(candidate -> preservesRequiredDirectAnchors(query, localCandidate, candidate))
                    .filter(EvidenceExpansionService::isEligibleExpansionEvidence)
                    .filter(candidate -> isMeaningfullyMoreDirect(candidate, localCandidate))
                    .max(DIRECTNESS_ORDER)
                    .map(candidate -> presentation(
                            expandedSnippet(candidate),
                            candidate.chunk().chunkId(),
                            candidate.chunk().sourceType(),
                            candidate.chunk().sourceIndex(),
                            candidate.chunk().sourceLabel()))
                    .orElse(localPresentation);
        } catch (RuntimeException exception) {
            // 표시 단계의 장애 때문에 선택이 끝난 검색 결과까지 버리지 않는다.
            LOGGER.warn(
                    "Evidence presentation failed for selected chunk {}; using selected chunk content.",
                    result.chunkId(),
                    exception);
            return presentation(
                    fallbackContent,
                    result.chunkId(),
                    result.sourceType(),
                    result.sourceIndex(),
                    result.sourceLabel());
        }
    }

    private static String usableSnippet(String snippet, String fallbackContent) {
        return snippet == null || snippet.isBlank() ? fallbackContent : snippet;
    }

    private static String localSnippetPreservingDirectAnchors(
            String query,
            SnippetSelection selection,
            String fallbackContent) {
        String snippet = usableSnippet(selection.snippet(), fallbackContent);
        Set<String> anchors = asciiAnchors(query);
        if (anchors.isEmpty() || anchors.stream().allMatch(asciiAnchors(snippet)::contains)) {
            return snippet;
        }
        return fallbackContent;
    }

    private static boolean hasRequiredDirectAnchor(String query, ExpansionCandidate candidate) {
        return !requiredDirectAnchors(query, candidate.chunk().content()).isEmpty();
    }

    private static boolean preservesRequiredDirectAnchors(
            String query,
            ExpansionCandidate local,
            ExpansionCandidate candidate) {
        Set<String> required = requiredDirectAnchors(query, local.chunk().content());
        return required.isEmpty() || asciiAnchors(candidate.chunk().content()).containsAll(required);
    }

    private static Set<String> requiredDirectAnchors(String query, String content) {
        Set<String> anchors = new LinkedHashSet<>(asciiAnchors(query));
        anchors.retainAll(asciiAnchors(content));
        return Set.copyOf(anchors);
    }

    private static boolean isSufficientLocalEvidence(String query, ExpansionCandidate local) {
        SnippetSelection selection = local.selection();
        if (local.summary()) {
            return false;
        }
        if ((selection.numericMatches() > 0 || selection.exactPhrase())
                && !selection.technicalList()) {
            return true;
        }
        if (local.phraseMatches() > 0
                && !selection.technicalList()) {
            return true;
        }
        return selection.queryCoverage() >= 3
                && (selection.narrative() || hasAsciiAnchor(query, selection.snippet()))
                && !selection.technicalList()
                && !selection.metadata();
    }

    private static boolean hasAsciiAnchor(String query, String snippet) {
        Set<String> queryAnchors = asciiAnchors(query);
        if (queryAnchors.isEmpty()) {
            return false;
        }
        Set<String> snippetAnchors = asciiAnchors(snippet);
        return queryAnchors.stream().anyMatch(snippetAnchors::contains);
    }

    private static Set<String> asciiAnchors(String value) {
        Matcher matcher = ASCII_ANCHOR.matcher(
                Objects.requireNonNullElse(value, "").toLowerCase(java.util.Locale.ROOT));
        Set<String> anchors = new LinkedHashSet<>();
        while (matcher.find()) {
            anchors.add(matcher.group());
        }
        return Set.copyOf(anchors);
    }

    private static boolean isEligibleExpansionEvidence(ExpansionCandidate candidate) {
        SnippetSelection selection = candidate.selection();
        boolean directlyMatched = selection.exactPhrase()
                        || selection.numericMatches() > 0
                        || selection.queryCoverage() > 0;
        if (!directlyMatched || selection.metadata()) {
            return false;
        }
        if (!selection.technicalList()) {
            return true;
        }
        return candidate.structuredDetail() && selection.queryCoverage() >= 2;
    }

    private static boolean isMeaningfullyMoreDirect(
            ExpansionCandidate candidate,
            ExpansionCandidate local) {
        if (DIRECTNESS_ORDER.compare(candidate, local) <= 0) {
            return false;
        }
        if (candidate.selection().exactPhrase() && !local.selection().exactPhrase()) {
            return true;
        }
        if (candidate.selection().numericMatches() > local.selection().numericMatches()) {
            return true;
        }
        if (candidate.phraseMatches() > local.phraseMatches()
                && (!hasCompleteLocalAnchor(local)
                        || candidate.selection().queryCoverage()
                        > local.selection().queryCoverage())) {
            return true;
        }
        int coverageGain = candidate.selection().queryCoverage()
                - local.selection().queryCoverage();
        if (coverageGain >= 2 && !hasCompleteLocalAnchor(local)) {
            return true;
        }
        if (local.selection().queryCoverage() == 0
                && candidate.selection().queryCoverage() > 0
                && !hasCompleteLocalAnchor(local)
                && !candidate.summary()) {
            return true;
        }
        if (coverageGain == 0
                && candidate.selection().queryCoverage() >= 2
                && candidate.structuredDetail()
                && !hasCompleteLocalAnchor(local)
                && !local.structuredDetail()
                && !candidate.summary()) {
            return true;
        }
        if (coverageGain >= 0
                && candidate.selection().anchorScore() >= local.selection().anchorScore() + 1_000
                && !hasCompleteLocalAnchor(local)
                && !candidate.summary()) {
            return true;
        }
        return coverageGain == 0
                && candidate.selection().queryCoverage() >= 2
                && candidate.selection().narrative()
                && !local.selection().narrative()
                && !candidate.summary();
    }

    private static boolean hasCompleteLocalAnchor(ExpansionCandidate local) {
        SnippetSelection selection = local.selection();
        return (selection.claimComplete()
                        && selection.queryCoverage() > 0
                        && selection.action()
                        && selection.result())
                || (selection.metric() && selection.narrative());
    }

    private static ExpansionCandidate candidate(
            String query,
            EvidenceChunk chunk,
            SnippetSelection selection) {
        String content = Objects.requireNonNullElse(chunk.content(), "");
        String snippet = Objects.requireNonNullElse(selection.snippet(), "");
        return new ExpansionCandidate(
                chunk,
                selection,
                adjacentPhraseMatches(query, content),
                isSummaryEvidence(content, snippet),
                STRUCTURED_DETAIL.matcher(content).find());
    }

    private static boolean isSummaryEvidence(String content, String snippet) {
        if (SUMMARY_CONTENT.matcher(snippet).find()) {
            return true;
        }
        if (content.length() < 600 && SUMMARY_CONTENT.matcher(content).find()) {
            return true;
        }
        int snippetStart = content.indexOf(snippet);
        if (snippetStart < 0) {
            return false;
        }
        String leadingContext = content.substring(Math.max(0, snippetStart - 1_000), snippetStart);
        return SUMMARY_CONTENT.matcher(leadingContext).find();
    }

    private static int adjacentPhraseMatches(String query, String snippet) {
        List<String> queryTerms = evidenceTerms(query);
        List<String> snippetTerms = evidenceTerms(snippet);
        int matches = 0;
        for (int index = 0; index < queryTerms.size() - 1; index++) {
            String left = queryTerms.get(index);
            String right = queryTerms.get(index + 1);
            if (Character.isDigit(left.codePointAt(0))
                    && Character.isDigit(right.codePointAt(0))) {
                continue;
            }
            for (int snippetIndex = 0; snippetIndex < snippetTerms.size() - 1; snippetIndex++) {
                if (snippetTerms.get(snippetIndex).equals(left)
                        && snippetTerms.get(snippetIndex + 1).equals(right)) {
                    matches++;
                    break;
                }
            }
        }
        return matches;
    }

    private static List<String> evidenceTerms(String value) {
        Matcher matcher = EVIDENCE_TOKEN.matcher(SearchTokenNormalizer.normalize(
                Objects.requireNonNullElse(value, "")));
        List<String> terms = new ArrayList<>();
        while (matcher.find()) {
            String term = stripEvidenceSuffix(matcher.group());
            if (term.length() > 1 && !GENERIC_EVIDENCE_TERMS.contains(term)) {
                terms.add(term);
            }
        }
        return List.copyOf(terms);
    }

    private static String stripEvidenceSuffix(String value) {
        for (String suffix : EVIDENCE_SUFFIXES) {
            if (value.endsWith(suffix) && value.length() > suffix.length() + 1) {
                return value.substring(0, value.length() - suffix.length());
            }
        }
        return value;
    }

    private String expandedSnippet(ExpansionCandidate candidate) {
        return usableSnippet(candidate.selection().snippet(), candidate.chunk().content());
    }

    private static EvidencePresentation presentation(
            String snippet,
            Long chunkId,
            com.prizm.ingestion.entity.ChunkSourceType sourceType,
            int sourceIndex,
            String sourceLabel) {
        return new EvidencePresentation(snippet, chunkId, sourceType, sourceIndex, sourceLabel);
    }

    private record ExpansionCandidate(
            EvidenceChunk chunk,
            SnippetSelection selection,
            int phraseMatches,
            boolean summary,
            boolean structuredDetail) {
    }
}
