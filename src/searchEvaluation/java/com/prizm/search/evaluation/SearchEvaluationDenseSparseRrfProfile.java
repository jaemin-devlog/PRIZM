package com.prizm.search.evaluation;

import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.profile.SearchIntent;
import com.prizm.search.repository.VectorSearchResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Evaluation-only Ollama Dense + FlagEmbedding BGE-M3 Sparse + RRF profile for P14. */
final class SearchEvaluationDenseSparseRrfProfile {

    static final String PROFILE_ID = "dense-bge-m3-sparse-rrf-k60-v1";
    static final int RRF_K = 60;
    static final int BRANCH_CANDIDATE_LIMIT = 20;

    private static final double PRODUCTION_MINIMUM_DENSE_SCORE = 0.50d;
    private static final double INTERNAL_RANK_STEP = 0.04d;

    private final CompositeSearchProfile delegate = new CompositeSearchProfile();

    Outcome apply(
            String query,
            List<VectorSearchResult> denseCandidates,
            List<SparseCandidate> sparseCandidates) {
        List<VectorSearchResult> denseBranch = denseCandidates.stream()
                .limit(BRANCH_CANDIDATE_LIMIT)
                .toList();
        List<SparseCandidate> sparseBranch = sparseCandidates.stream()
                .limit(BRANCH_CANDIDATE_LIMIT)
                .toList();
        List<FusedCandidate> fusedCandidates = fuse(denseBranch, sparseBranch);

        CompositeSearchProfile.Decision productionDecision = delegate.apply(query, denseBranch);
        if (delegate.resolveIntent(query) != SearchIntent.GENERAL
                || sparseBranch.isEmpty()
                || fusedCandidates.isEmpty()) {
            return new Outcome(productionDecision, fusedCandidates);
        }

        List<FusedCandidate> channelEligible = fusedCandidates.stream()
                .filter(candidate -> candidate.candidate().score() >= PRODUCTION_MINIMUM_DENSE_SCORE
                        || candidate.sparseRank() != null)
                .toList();
        if (channelEligible.isEmpty()) {
            return new Outcome(productionDecision, fusedCandidates);
        }

        // The delegate owns the established safety gates and deduplication. Evaluation-only
        // score views preserve RRF order across the delegate's bounded 0.03 GENERAL boost;
        // original dense score and distance are restored before results leave this adapter.
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
            List<SparseCandidate> sparseCandidates) {
        Map<Long, MutableFusion> fusionByChunkId = new LinkedHashMap<>();
        for (int index = 0; index < Math.min(denseCandidates.size(), BRANCH_CANDIDATE_LIMIT); index++) {
            VectorSearchResult candidate = denseCandidates.get(index);
            MutableFusion fusion = fusionByChunkId.computeIfAbsent(
                    candidate.chunkId(),
                    ignored -> new MutableFusion(candidate));
            fusion.denseRank = index + 1;
        }
        for (int index = 0; index < Math.min(sparseCandidates.size(), BRANCH_CANDIDATE_LIMIT); index++) {
            SparseCandidate sparseCandidate = sparseCandidates.get(index);
            MutableFusion fusion = fusionByChunkId.computeIfAbsent(
                    sparseCandidate.candidate().chunkId(),
                    ignored -> new MutableFusion(sparseCandidate.candidate()));
            fusion.sparseRank = index + 1;
        }

        return fusionByChunkId.values().stream()
                .map(fusion -> new FusedCandidate(
                        fusion.candidate,
                        fusion.denseRank,
                        fusion.sparseRank,
                        rrfScore(fusion.denseRank, fusion.sparseRank)))
                .sorted(Comparator.comparingDouble(FusedCandidate::rrfScore)
                        .reversed()
                        .thenComparingInt(candidate -> rankOrMaximum(candidate.denseRank()))
                        .thenComparingInt(candidate -> rankOrMaximum(candidate.sparseRank()))
                        .thenComparing(candidate -> candidate.candidate().chunkId()))
                .toList();
    }

    static double rrfScore(Integer denseRank, Integer sparseRank) {
        double score = 0.0d;
        if (denseRank != null) {
            score += 1.0d / (RRF_K + denseRank);
        }
        if (sparseRank != null) {
            score += 1.0d / (RRF_K + sparseRank);
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

    record SparseCandidate(VectorSearchResult candidate, double sparseScore) {
    }

    record FusedCandidate(
            VectorSearchResult candidate,
            Integer denseRank,
            Integer sparseRank,
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
        private Integer sparseRank;

        private MutableFusion(VectorSearchResult candidate) {
            this.candidate = candidate;
        }
    }
}
