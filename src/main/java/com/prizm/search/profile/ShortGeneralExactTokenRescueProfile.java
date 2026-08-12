package com.prizm.search.profile;

import com.prizm.search.repository.VectorSearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Rescues at most one exact-token GENERAL result immediately below the dense floor.
 *
 * <p>The existing composite decision remains authoritative unless it is empty. Completed-release
 * evidence never enters the rescue path.</p>
 */
public final class ShortGeneralExactTokenRescueProfile {

    public static final double RESCUE_MINIMUM_DENSE_SCORE = 0.49d;

    private static final double PRODUCTION_MINIMUM_DENSE_SCORE = 0.50d;
    private static final int MINIMUM_SHORT_QUERY_CODE_POINTS = 2;
    private static final int MAXIMUM_SHORT_QUERY_CODE_POINTS = 4;
    private static final int MAXIMUM_RESCUED_RESULTS = 1;
    private static final Pattern TOKEN_PATTERN =
            Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#._-]*");

    private final CompositeSearchProfile delegate;

    public ShortGeneralExactTokenRescueProfile(CompositeSearchProfile delegate) {
        this.delegate = delegate;
    }

    public CompositeSearchProfile.Decision apply(
            String query,
            List<VectorSearchResult> denseCandidates) {
        CompositeSearchProfile.Decision productionDecision =
                delegate.apply(query, denseCandidates);
        if (!productionDecision.rejected()
                || delegate.resolveIntent(query) != SearchIntent.GENERAL
                || denseCandidates.stream()
                        .anyMatch(candidate -> candidate.score() >= PRODUCTION_MINIMUM_DENSE_SCORE)) {
            return productionDecision;
        }

        Optional<String> shortQueryToken = singleShortQueryToken(query);
        if (shortQueryToken.isEmpty()) {
            return productionDecision;
        }

        String token = shortQueryToken.orElseThrow();
        boolean hasRescueCandidate = denseCandidates.stream()
                .anyMatch(candidate -> isRescueCandidate(candidate, token));
        if (!hasRescueCandidate) {
            return productionDecision;
        }

        List<VectorSearchResult> promotedCandidates = denseCandidates.stream()
                .map(candidate -> isRescueCandidate(candidate, token)
                        ? promoteForEligibility(candidate)
                        : candidate)
                .toList();
        CompositeSearchProfile.Decision promotedDecision =
                delegate.apply(query, promotedCandidates);
        if (promotedDecision.rejected()) {
            return productionDecision;
        }

        Map<Long, VectorSearchResult> originalsByChunkId = denseCandidates.stream()
                .collect(Collectors.toMap(
                        VectorSearchResult::chunkId,
                        Function.identity()));
        List<VectorSearchResult> rescuedResults = promotedDecision.results().stream()
                .map(result -> originalsByChunkId.get(result.chunkId()))
                .filter(candidate -> candidate != null && isRescueCandidate(candidate, token))
                .limit(MAXIMUM_RESCUED_RESULTS)
                .toList();
        if (rescuedResults.isEmpty()) {
            return productionDecision;
        }
        return new CompositeSearchProfile.Decision(
                denseCandidates,
                rescuedResults,
                List.of());
    }

    private static Optional<String> singleShortQueryToken(String query) {
        List<String> tokens = normalizedTokens(query);
        if (tokens.size() != 1) {
            return Optional.empty();
        }

        String token = tokens.get(0);
        int codePoints = token.codePointCount(0, token.length());
        if (codePoints < MINIMUM_SHORT_QUERY_CODE_POINTS
                || codePoints > MAXIMUM_SHORT_QUERY_CODE_POINTS) {
            return Optional.empty();
        }
        return Optional.of(token);
    }

    private static boolean isRescueCandidate(
            VectorSearchResult candidate,
            String queryToken) {
        return candidate.score() >= RESCUE_MINIMUM_DENSE_SCORE
                && candidate.score() < PRODUCTION_MINIMUM_DENSE_SCORE
                && normalizedTokens(candidate.content()).contains(queryToken);
    }

    private static VectorSearchResult promoteForEligibility(VectorSearchResult candidate) {
        double promotedScore = candidate.score()
                + (PRODUCTION_MINIMUM_DENSE_SCORE - RESCUE_MINIMUM_DENSE_SCORE);
        return new VectorSearchResult(
                candidate.chunkId(),
                candidate.documentId(),
                candidate.documentVersionId(),
                candidate.documentTitle(),
                candidate.versionNo(),
                candidate.chunkNo(),
                candidate.pageNo(),
                candidate.sourceType(),
                candidate.sourceIndex(),
                candidate.sourceLabel(),
                candidate.content(),
                1.0d - promotedScore,
                promotedScore);
    }

    private static List<String> normalizedTokens(String value) {
        Matcher matcher = TOKEN_PATTERN.matcher(SearchTokenNormalizer.normalize(value));
        List<String> tokens = new ArrayList<>();
        while (matcher.find()) {
            String token = stripTrailingTokenPunctuation(matcher.group());
            if (!token.isEmpty()) {
                tokens.add(token);
            }
        }
        return List.copyOf(tokens);
    }

    private static String stripTrailingTokenPunctuation(String value) {
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
}
