package com.prizm.search.evaluation;

import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.profile.ShortGeneralExactTokenRescueProfile;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.repository.VectorSearchResult;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Evaluation-only D0/D1 candidate construction; no production request path references this class. */
final class PhaseALiteralRetrievalEvaluator {

    private final VectorSearchRepository denseRepository;
    private final PhaseALiteralCandidateRepository literalRepository;
    private final ShortGeneralExactTokenRescueProfile productionFilter;

    PhaseALiteralRetrievalEvaluator(
            VectorSearchRepository denseRepository,
            PhaseALiteralCandidateRepository literalRepository,
            CompositeSearchProfile compositeSearchProfile) {
        this.denseRepository = denseRepository;
        this.literalRepository = literalRepository;
        this.productionFilter = new ShortGeneralExactTokenRescueProfile(compositeSearchProfile);
    }

    Evaluation evaluate(Long ownerUserId, String query, float[] embedding) {
        List<VectorSearchResult> dense = denseRepository.findCareerEvidenceCandidates(
                ownerUserId, embedding);
        List<VectorSearchResult> literal = literalRepository.findCandidates(
                ownerUserId, query, embedding);
        List<VectorSearchResult> union = unionByChunkIdentity(dense, literal);
        List<VectorSearchResult> d0Filtered = productionFilter.apply(query, dense).results();
        List<VectorSearchResult> d1Filtered = productionFilter.apply(query, union).results();
        return new Evaluation(dense, literal, union, d0Filtered, d1Filtered);
    }

    static List<VectorSearchResult> unionByChunkIdentity(
            List<VectorSearchResult> dense,
            List<VectorSearchResult> literal) {
        Map<Long, VectorSearchResult> byChunkId = new LinkedHashMap<>();
        dense.forEach(candidate -> byChunkId.put(candidate.chunkId(), candidate));
        literal.forEach(candidate -> byChunkId.putIfAbsent(candidate.chunkId(), candidate));
        return byChunkId.values().stream()
                .sorted(Comparator.comparingDouble(VectorSearchResult::score)
                        .reversed()
                        .thenComparing(VectorSearchResult::chunkId))
                .toList();
    }

    record Evaluation(
            List<VectorSearchResult> denseCandidates,
            List<VectorSearchResult> literalCandidates,
            List<VectorSearchResult> unionCandidates,
            List<VectorSearchResult> d0Filtered,
            List<VectorSearchResult> d1Filtered) {

        Evaluation {
            denseCandidates = List.copyOf(denseCandidates);
            literalCandidates = List.copyOf(literalCandidates);
            unionCandidates = List.copyOf(unionCandidates);
            d0Filtered = List.copyOf(d0Filtered);
            d1Filtered = List.copyOf(d1Filtered);
        }
    }
}
