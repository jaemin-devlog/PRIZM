package com.prizm.search.evaluation;

import com.prizm.search.evaluation.SearchEvaluationDenseSparseRrfProfile.FusedCandidate;
import com.prizm.search.evaluation.SearchEvaluationDenseSparseRrfProfile.Outcome;
import com.prizm.search.evaluation.SearchEvaluationDenseSparseRrfProfile.SparseCandidate;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.profile.SearchIntent;
import com.prizm.search.repository.VectorSearchResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Evaluation-only P14 candidate reranking with the multilingual BGE reranker. */
final class SearchEvaluationDenseSparseRerankerProfile {

    static final String PROFILE_ID =
            "dense-bge-m3-sparse-rrf-k60-bge-reranker-v2-m3-v1";

    private static final double PRODUCTION_MINIMUM_DENSE_SCORE = 0.50d;
    private static final double INTERNAL_RANK_STEP = 0.04d;

    private final SearchEvaluationDenseSparseRrfProfile p14Profile =
            new SearchEvaluationDenseSparseRrfProfile();
    private final CompositeSearchProfile delegate = new CompositeSearchProfile();

    RerankerOutcome apply(
            String query,
            List<VectorSearchResult> denseCandidates,
            List<SparseCandidate> sparseCandidates,
            List<RerankerCandidate> rerankerCandidates) {
        Outcome p14Outcome = p14Profile.apply(query, denseCandidates, sparseCandidates);
        if (!reranks(query)) {
            return new RerankerOutcome(
                    p14Outcome.decision(),
                    p14Outcome.fusedCandidates(),
                    List.of());
        }

        List<FusedCandidate> eligibleCandidates = p14Outcome.fusedCandidates().stream()
                .filter(candidate -> candidate.candidate().score() >= PRODUCTION_MINIMUM_DENSE_SCORE
                        || candidate.sparseRank() != null)
                .toList();
        List<RerankedCandidate> reranked = validateAndOrder(
                eligibleCandidates,
                rerankerCandidates);

        List<VectorSearchResult> rankedViews = new ArrayList<>();
        for (int index = 0; index < reranked.size(); index++) {
            double internalScore = PRODUCTION_MINIMUM_DENSE_SCORE
                    + (INTERNAL_RANK_STEP * (reranked.size() - index));
            rankedViews.add(withInternalScore(
                    reranked.get(index).candidate(),
                    internalScore));
        }

        CompositeSearchProfile.Decision rerankedDecision = delegate.apply(query, rankedViews);
        Map<Long, VectorSearchResult> originalsByChunkId = new LinkedHashMap<>();
        for (RerankedCandidate candidate : reranked) {
            originalsByChunkId.put(candidate.candidate().chunkId(), candidate.candidate());
        }
        List<VectorSearchResult> originalResults = rerankedDecision.results().stream()
                .map(result -> originalsByChunkId.get(result.chunkId()))
                .toList();
        List<VectorSearchResult> originalCandidates = reranked.stream()
                .map(RerankedCandidate::candidate)
                .toList();
        return new RerankerOutcome(
                new CompositeSearchProfile.Decision(
                        originalCandidates,
                        originalResults,
                        rerankedDecision.rejectionReasons()),
                p14Outcome.fusedCandidates(),
                reranked);
    }

    boolean reranks(String query) {
        return delegate.resolveIntent(query) == SearchIntent.GENERAL;
    }

    private static List<RerankedCandidate> validateAndOrder(
            List<FusedCandidate> eligibleCandidates,
            List<RerankerCandidate> rerankerCandidates) {
        if (rerankerCandidates == null || rerankerCandidates.size() != eligibleCandidates.size()) {
            throw new SearchEvaluationDataException(
                    "P15 reranker scores must exactly cover the P14 eligible candidate pool.");
        }

        Map<Long, RerankerCandidate> rerankerByChunkId = new HashMap<>();
        for (RerankerCandidate candidate : rerankerCandidates) {
            if (candidate == null
                    || !Double.isFinite(candidate.rerankerScore())
                    || rerankerByChunkId.put(candidate.candidate().chunkId(), candidate) != null) {
                throw new SearchEvaluationDataException(
                        "P15 reranker candidates must be unique and finite.");
            }
        }

        List<RerankedCandidate> reranked = new ArrayList<>();
        for (int index = 0; index < eligibleCandidates.size(); index++) {
            FusedCandidate eligible = eligibleCandidates.get(index);
            RerankerCandidate scored = rerankerByChunkId.get(eligible.candidate().chunkId());
            if (scored == null
                    || scored.p14Rank() != index + 1
                    || scored.candidate() != eligible.candidate()) {
                throw new SearchEvaluationDataException(
                        "P15 reranker candidates must preserve the exact P14 eligible pool and ranks.");
            }
            reranked.add(new RerankedCandidate(
                    eligible.candidate(),
                    scored.p14Rank(),
                    scored.rerankerRank(),
                    scored.rerankerScore()));
        }

        reranked.sort(Comparator.comparingDouble(RerankedCandidate::rerankerScore)
                .reversed()
                .thenComparingInt(RerankedCandidate::p14Rank)
                .thenComparing(candidate -> candidate.candidate().chunkId()));
        for (int index = 0; index < reranked.size(); index++) {
            if (reranked.get(index).rerankerRank() != index + 1) {
                throw new SearchEvaluationDataException(
                        "P15 reranker ranks must match raw-score ordering.");
            }
        }
        return List.copyOf(reranked);
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

    record RerankerCandidate(
            VectorSearchResult candidate,
            int p14Rank,
            int rerankerRank,
            double rerankerScore) {
    }

    record RerankedCandidate(
            VectorSearchResult candidate,
            int p14Rank,
            int rerankerRank,
            double rerankerScore) {
    }

    record RerankerOutcome(
            CompositeSearchProfile.Decision decision,
            List<FusedCandidate> p14Candidates,
            List<RerankedCandidate> rerankedCandidates) {

        RerankerOutcome {
            p14Candidates = List.copyOf(p14Candidates);
            rerankedCandidates = List.copyOf(rerankedCandidates);
        }
    }
}
