package com.prizm.search.evaluation;

import com.prizm.search.evaluation.SearchEvaluationLexicalCandidateRepository.LexicalCandidate;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.profile.SearchIntent;
import com.prizm.search.repository.VectorSearchResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Evaluation-only Dense + PostgreSQL lexical + RRF profile for P13. */
final class SearchEvaluationHybridRrfProfile {

    static final String PROFILE_ID = "dense-postgresql-lexical-rrf-k60-v1";
    static final int RRF_K = 60;
    static final int BRANCH_CANDIDATE_LIMIT = 20;

    private static final double PRODUCTION_MINIMUM_DENSE_SCORE = 0.50d;
    private static final double INTERNAL_RANK_STEP = 0.04d;

    private final CompositeSearchProfile delegate = new CompositeSearchProfile();

    Outcome apply(
            String query,
            List<VectorSearchResult> denseCandidates,
            List<LexicalCandidate> lexicalCandidates) {
        List<VectorSearchResult> denseBranch = denseCandidates.stream()
                .limit(BRANCH_CANDIDATE_LIMIT)
                .toList();
        List<LexicalCandidate> lexicalBranch = lexicalCandidates.stream()
                .limit(BRANCH_CANDIDATE_LIMIT)
                .toList();
        List<FusedCandidate> fusedCandidates = fuse(denseBranch, lexicalBranch);

        CompositeSearchProfile.Decision productionDecision = delegate.apply(query, denseBranch);
        if (lexicalBranch.isEmpty() || fusedCandidates.isEmpty()) {
            return new Outcome(productionDecision, fusedCandidates);
        }

        boolean generalIntent = delegate.resolveIntent(query) == SearchIntent.GENERAL;
        List<FusedCandidate> channelEligible = fusedCandidates.stream()
                .filter(candidate -> candidate.candidate().score() >= PRODUCTION_MINIMUM_DENSE_SCORE
                        || (generalIntent && candidate.lexicalRank() != null))
                .toList();
        if (channelEligible.isEmpty()) {
            return new Outcome(productionDecision, fusedCandidates);
        }

        // CompositeSearchProfile owns the existing safety gates and deduplication, but its GENERAL
        // final sort uses dense score plus a bounded 0.03 lexical boost. These evaluation-only views
        // encode the already-computed RRF order with a 0.04 gap, then originals are restored below.
        List<VectorSearchResult> rankedViews = new ArrayList<>();
        for (int index = 0; index < channelEligible.size(); index++) {
            double internalScore = PRODUCTION_MINIMUM_DENSE_SCORE
                    + (INTERNAL_RANK_STEP * (channelEligible.size() - index));
            rankedViews.add(withInternalScore(
                    channelEligible.get(index).candidate(),
                    internalScore));
        }

        CompositeSearchProfile.Decision fusedDecision = delegate.apply(query, rankedViews);
        Map<Long, VectorSearchResult> originalsByChunkId = new LinkedHashMap<>();
        for (FusedCandidate fusedCandidate : fusedCandidates) {
            originalsByChunkId.put(
                    fusedCandidate.candidate().chunkId(),
                    fusedCandidate.candidate());
        }
        List<VectorSearchResult> originalResults = fusedDecision.results().stream()
                .map(result -> originalsByChunkId.get(result.chunkId()))
                .toList();
        List<VectorSearchResult> originalCandidates = fusedCandidates.stream()
                .map(FusedCandidate::candidate)
                .toList();
        return new Outcome(
                new CompositeSearchProfile.Decision(
                        originalCandidates,
                        originalResults,
                        fusedDecision.rejectionReasons()),
                fusedCandidates);
    }

    static List<FusedCandidate> fuse(
            List<VectorSearchResult> denseCandidates,
            List<LexicalCandidate> lexicalCandidates) {
        Map<Long, MutableFusion> fusionByChunkId = new LinkedHashMap<>();
        for (int index = 0; index < Math.min(denseCandidates.size(), BRANCH_CANDIDATE_LIMIT); index++) {
            VectorSearchResult candidate = denseCandidates.get(index);
            MutableFusion fusion = fusionByChunkId.computeIfAbsent(
                    candidate.chunkId(),
                    ignored -> new MutableFusion(candidate));
            fusion.denseRank = index + 1;
        }
        for (int index = 0; index < Math.min(lexicalCandidates.size(), BRANCH_CANDIDATE_LIMIT); index++) {
            LexicalCandidate lexicalCandidate = lexicalCandidates.get(index);
            MutableFusion fusion = fusionByChunkId.computeIfAbsent(
                    lexicalCandidate.candidate().chunkId(),
                    ignored -> new MutableFusion(lexicalCandidate.candidate()));
            fusion.lexicalRank = index + 1;
        }

        return fusionByChunkId.values().stream()
                .map(fusion -> new FusedCandidate(
                        fusion.candidate,
                        fusion.denseRank,
                        fusion.lexicalRank,
                        rrfScore(fusion.denseRank, fusion.lexicalRank)))
                .sorted(Comparator.comparingDouble(FusedCandidate::rrfScore)
                        .reversed()
                        .thenComparingInt(candidate -> rankOrMaximum(candidate.denseRank()))
                        .thenComparingInt(candidate -> rankOrMaximum(candidate.lexicalRank()))
                        .thenComparing(candidate -> candidate.candidate().chunkId()))
                .toList();
    }

    static double rrfScore(Integer denseRank, Integer lexicalRank) {
        double score = 0.0d;
        if (denseRank != null) {
            score += 1.0d / (RRF_K + denseRank);
        }
        if (lexicalRank != null) {
            score += 1.0d / (RRF_K + lexicalRank);
        }
        return score;
    }

    private static int rankOrMaximum(Integer rank) {
        return rank == null ? Integer.MAX_VALUE : rank;
    }

    private static VectorSearchResult withInternalScore(
            VectorSearchResult candidate,
            double internalScore) {
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
                1.0d - internalScore,
                internalScore);
    }

    record FusedCandidate(
            VectorSearchResult candidate,
            Integer denseRank,
            Integer lexicalRank,
            double rrfScore) {
    }

    record Outcome(
            CompositeSearchProfile.Decision decision,
            List<FusedCandidate> fusedCandidates) {

        Outcome {
            fusedCandidates = List.copyOf(fusedCandidates);
        }
    }

    private static final class MutableFusion {

        private final VectorSearchResult candidate;
        private Integer denseRank;
        private Integer lexicalRank;

        private MutableFusion(VectorSearchResult candidate) {
            this.candidate = candidate;
        }
    }
}
