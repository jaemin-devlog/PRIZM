package com.prizm.search.evaluation.trace;

import com.prizm.ingestion.entity.ChunkSourceType;
import java.util.List;
import java.util.Map;

/** Structured, evaluation-only explanation of one production Career Evidence search. */
public record SearchDecisionTrace(
        int schemaVersion,
        Long ownerUserId,
        String originalQuery,
        String responseState,
        List<QueryVariantTrace> queryVariants,
        List<CandidateTrace> candidates,
        List<CandidateGroupTrace> sourceConsolidation,
        List<CandidateGroupTrace> queryEvidenceConsolidation,
        List<RankingTrace> ranking,
        List<FinalResultTrace> finalResults,
        List<EvidenceTrace> localization,
        boolean productionResponseMatch,
        List<String> parityErrors) {

    public SearchDecisionTrace {
        queryVariants = List.copyOf(queryVariants);
        candidates = List.copyOf(candidates);
        sourceConsolidation = List.copyOf(sourceConsolidation);
        queryEvidenceConsolidation = List.copyOf(queryEvidenceConsolidation);
        ranking = List.copyOf(ranking);
        finalResults = List.copyOf(finalResults);
        localization = List.copyOf(localization);
        parityErrors = List.copyOf(parityErrors);
    }

    public enum QueryVariantType {
        ORIGINAL,
        FALLBACK,
        NUMERIC_RESCUE,
        IDENTIFIER_GUARD
    }

    public enum DecisionStage {
        RETRIEVAL,
        SOURCE_CONSOLIDATION,
        ELIGIBILITY,
        QUERY_EVIDENCE_CONSOLIDATION,
        RANKING,
        POST_FILTER,
        LOCALIZATION,
        FINAL
    }

    public enum Decision {
        PASS,
        REJECT,
        SELECTED,
        REMOVED,
        NOT_REACHED
    }

    public enum FirstFailureStage {
        RETRIEVAL,
        SOURCE_CONSOLIDATION,
        ELIGIBILITY,
        QUERY_EVIDENCE_CONSOLIDATION,
        RANKING,
        POST_FILTER,
        LOCALIZATION,
        NONE
    }

    public record QueryVariantTrace(
            String variantId,
            QueryVariantType type,
            String query,
            List<String> anchorQueries,
            boolean directAnchorRequired,
            List<Long> retrievedChunkIds,
            List<Long> mergedCandidateIds,
            List<Long> sourceRepresentativeIds,
            List<Long> eligibleIds,
            List<Long> queryRepresentativeIds,
            List<Long> rankedIds,
            List<Long> topFiveIds,
            List<Long> postFilterIds) {

        public QueryVariantTrace {
            anchorQueries = List.copyOf(anchorQueries);
            retrievedChunkIds = List.copyOf(retrievedChunkIds);
            mergedCandidateIds = List.copyOf(mergedCandidateIds);
            sourceRepresentativeIds = List.copyOf(sourceRepresentativeIds);
            eligibleIds = List.copyOf(eligibleIds);
            queryRepresentativeIds = List.copyOf(queryRepresentativeIds);
            rankedIds = List.copyOf(rankedIds);
            topFiveIds = List.copyOf(topFiveIds);
            postFilterIds = List.copyOf(postFilterIds);
        }
    }

    public record RetrievalHitTrace(
            String variantId,
            int denseRank,
            double denseScore,
            double distance) {
    }

    public record CandidateTrace(
            Long chunkId,
            Long documentId,
            Long documentVersionId,
            int chunkNo,
            ChunkSourceType sourceType,
            int sourceIndex,
            String sourceLabel,
            String content,
            List<RetrievalHitTrace> retrieval,
            List<CandidateDecisionTrace> decisions,
            FirstFailureStage firstFailureStage,
            String firstFailureReason) {

        public CandidateTrace {
            retrieval = List.copyOf(retrieval);
            decisions = List.copyOf(decisions);
        }
    }

    public record CandidateDecisionTrace(
            String variantId,
            DecisionStage stage,
            Decision decision,
            List<String> reasons,
            Long representativeChunkId) {

        public CandidateDecisionTrace {
            reasons = List.copyOf(reasons);
        }
    }

    public record CandidateGroupTrace(
            String variantId,
            String groupId,
            DecisionStage stage,
            List<Long> memberChunkIds,
            Long representativeChunkId,
            List<Long> removedChunkIds) {

        public CandidateGroupTrace {
            memberChunkIds = List.copyOf(memberChunkIds);
            removedChunkIds = List.copyOf(removedChunkIds);
        }
    }

    public record RankingTrace(
            String variantId,
            Long chunkId,
            double denseScore,
            double identifierBoost,
            double coreTermBoost,
            double numericBoost,
            double evidenceRerankerAdjustment,
            double finalRankingScore,
            int rank,
            boolean selectedInTopFive) {
    }

    public record FinalResultTrace(
            int rank,
            Long chunkId,
            Long documentId,
            Long documentVersionId,
            double denseScore,
            double distance) {
    }

    public record EvidenceTrace(
            int resultRank,
            Long originalResultChunkId,
            Long evidenceChunkId,
            ChunkSourceType evidenceSourceType,
            int evidenceSourceIndex,
            String evidenceSourceLabel,
            String snippet,
            boolean expanded) {
    }

    /** Ground-truth projection kept separate from the production decision trace. */
    public record GroundTruthOutcome(
            boolean candidateRecallAt20,
            boolean postSourceRetention,
            boolean postEligibilityRetention,
            boolean postQueryConsolidationRetention,
            boolean finalRecallAt5,
            boolean top1,
            boolean selectedResultCorrect,
            boolean displayedEvidenceCorrect,
            boolean localizationCorrect,
            FirstFailureStage firstFailureStage,
            List<String> matchedAcceptableEvidenceSetIds,
            Map<String, Object> diagnostics) {

        public GroundTruthOutcome {
            matchedAcceptableEvidenceSetIds = List.copyOf(matchedAcceptableEvidenceSetIds);
            diagnostics = Map.copyOf(diagnostics);
        }
    }
}
