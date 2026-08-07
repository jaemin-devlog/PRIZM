package com.prizm.search.evaluation;

import com.prizm.search.evaluation.SearchEvaluationData.CandidateResult;
import com.prizm.search.evaluation.SearchEvaluationData.Breakdown;
import com.prizm.search.evaluation.SearchEvaluationData.Category;
import com.prizm.search.evaluation.SearchEvaluationData.CountDistribution;
import com.prizm.search.evaluation.SearchEvaluationData.DecisionMetrics;
import com.prizm.search.evaluation.SearchEvaluationData.ExpectedEvidence;
import com.prizm.search.evaluation.SearchEvaluationData.LatencyDistribution;
import com.prizm.search.evaluation.SearchEvaluationData.QuestionResult;
import com.prizm.search.evaluation.SearchEvaluationData.ScoreDistribution;
import com.prizm.search.evaluation.SearchEvaluationData.SearchState;
import com.prizm.search.evaluation.SearchEvaluationData.Split;
import com.prizm.search.evaluation.SearchEvaluationData.Summary;
import com.prizm.ingestion.entity.ChunkSourceType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 현재 Dense 검색의 순위를 바꾸지 않고 평가 지표만 계산한다. */
public class SearchEvaluationMetrics {

    private static final int FINAL_RESULT_LIMIT = 5;
    private static final int CANDIDATE_LIMIT = 20;

    public Breakdown calculateBreakdown(List<QuestionResult> results) {
        Summary overall = calculate(results);
        Map<Split, Summary> splits = new EnumMap<>(Split.class);
        for (Split split : Split.values()) {
            List<QuestionResult> selected = results.stream()
                    .filter(result -> result.split() == split)
                    .toList();
            if (!selected.isEmpty()) {
                splits.put(split, calculate(selected));
            }
        }
        Map<Category, Summary> categories = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            List<QuestionResult> selected = results.stream()
                    .filter(result -> result.category() == category)
                    .toList();
            if (!selected.isEmpty()) {
                categories.put(category, calculate(selected));
            }
        }
        return new Breakdown(overall, Map.copyOf(splits), Map.copyOf(categories));
    }

    public Summary calculate(List<QuestionResult> results) {
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("Evaluation results must not be empty.");
        }

        int evidenceQuestionCount = 0;
        int directEvidenceQuestionCount = 0;
        int recallHits = 0;
        int directRecallHits = 0;
        double precisionSum = 0.0d;
        double directPrecisionSum = 0.0d;
        double reciprocalRankAt5Sum = 0.0d;
        double reciprocalRankAt20Sum = 0.0d;
        double ndcgSum = 0.0d;
        int ndcgQuestionCount = 0;
        int duplicateCount = 0;
        int returnedTop5Count = 0;
        int noEvidenceQuestionCount = 0;
        int noEvidenceRejectionCount = 0;
        int evidenceDecisionQuestionCount = 0;
        int falseRejectionCount = 0;
        int noSearchableDocumentsQuestionCount = 0;
        int noSearchableDocumentsCorrectCount = 0;
        int top1DirectHitCount = 0;
        int pdfEvidenceQuestionCount = 0;
        int pdfPageHitCount = 0;
        List<Integer> userResultCounts = new ArrayList<>();
        List<Integer> candidateCounts = new ArrayList<>();
        List<Long> totalLatencies = new ArrayList<>();
        List<Long> embeddingLatencies = new ArrayList<>();
        List<Long> dbLatencies = new ArrayList<>();

        for (QuestionResult result : results) {
            List<CandidateResult> top20 = result.candidates().stream()
                    .limit(CANDIDATE_LIMIT)
                    .toList();
            List<CandidateResult> top5 = returnedCandidates(result, top20);
            int userResultCount = top5.size();

            boolean hasExpectedEvidence = result.expectedEvidence().stream()
                    .anyMatch(evidence -> evidence.relevance() >= 1);
            boolean hasExpectedDirectEvidence = result.expectedEvidence().stream()
                    .anyMatch(evidence -> evidence.relevance() == 2);
            if (hasExpectedEvidence) {
                evidenceQuestionCount++;
                if (top20.stream().anyMatch(candidate -> candidate.relevance() >= 1)) {
                    recallHits++;
                }
                ndcgSum += ndcgAt5(result.expectedEvidence(), top5);
                ndcgQuestionCount++;
            }
            if (hasExpectedDirectEvidence) {
                directEvidenceQuestionCount++;
                if (top20.stream().anyMatch(candidate -> candidate.relevance() == 2)) {
                    directRecallHits++;
                }
                if (result.searchState() == SearchState.EVIDENCE_FOUND) {
                    reciprocalRankAt5Sum += reciprocalRank(top5, FINAL_RESULT_LIMIT);
                    reciprocalRankAt20Sum += reciprocalRank(top20, CANDIDATE_LIMIT);
                }
                if (result.searchState() == SearchState.EVIDENCE_FOUND
                        && !top5.isEmpty()
                        && top5.get(0).relevance() == 2) {
                    top1DirectHitCount++;
                }
            }

            if (result.noEvidence() && result.category() != Category.NO_SEARCHABLE_DOCUMENTS) {
                noEvidenceQuestionCount++;
                if (result.searchState() == SearchState.NO_EVIDENCE) {
                    noEvidenceRejectionCount++;
                }
            }
            if (hasExpectedEvidence) {
                evidenceDecisionQuestionCount++;
                if (result.searchState() == SearchState.NO_EVIDENCE) {
                    falseRejectionCount++;
                }
            }
            if (result.category() == Category.NO_SEARCHABLE_DOCUMENTS) {
                noSearchableDocumentsQuestionCount++;
                if (result.searchState() == SearchState.NO_SEARCHABLE_DOCUMENTS) {
                    noSearchableDocumentsCorrectCount++;
                }
            }
            if (result.category() == Category.PDF_EVIDENCE && hasExpectedDirectEvidence) {
                pdfEvidenceQuestionCount++;
                CandidateResult firstDirect = top5.stream()
                        .filter(candidate -> candidate.relevance() == 2)
                        .findFirst()
                        .orElse(null);
                if (result.searchState() == SearchState.EVIDENCE_FOUND
                        && firstDirect != null
                        && firstDirect.sourceType() == ChunkSourceType.PAGE
                        && result.goldPage() != null
                        && firstDirect.sourceIndex() == result.goldPage()) {
                    pdfPageHitCount++;
                }
            }

            long relevantTop5 = top5.stream().filter(candidate -> candidate.relevance() >= 1).count();
            long directTop5 = top5.stream().filter(candidate -> candidate.relevance() == 2).count();
            precisionSum += relevantTop5 / (double) FINAL_RESULT_LIMIT;
            directPrecisionSum += directTop5 / (double) FINAL_RESULT_LIMIT;
            Set<String> seenGroups = new HashSet<>();
            for (CandidateResult candidate : top5) {
                returnedTop5Count++;
                if (!seenGroups.add(candidate.evidenceGroupId())) {
                    duplicateCount++;
                }
            }
            userResultCounts.add(userResultCount);
            candidateCounts.add(result.candidates().size());
            totalLatencies.add(result.searchTimeMillis());
            embeddingLatencies.add(result.embeddingTimeMillis());
            dbLatencies.add(result.dbSearchTimeMillis());
        }

        LatencyDistribution totalLatency = latencyDistribution(totalLatencies);
        LatencyDistribution embeddingLatency = latencyDistribution(embeddingLatencies);
        LatencyDistribution dbLatency = latencyDistribution(dbLatencies);
        DecisionMetrics decisionMetrics = new DecisionMetrics(
                noEvidenceQuestionCount,
                divide(noEvidenceRejectionCount, noEvidenceQuestionCount),
                evidenceDecisionQuestionCount,
                divide(falseRejectionCount, evidenceDecisionQuestionCount),
                noSearchableDocumentsQuestionCount,
                divide(noSearchableDocumentsCorrectCount, noSearchableDocumentsQuestionCount),
                directEvidenceQuestionCount,
                divide(top1DirectHitCount, directEvidenceQuestionCount),
                pdfEvidenceQuestionCount,
                divide(pdfPageHitCount, pdfEvidenceQuestionCount));

        return new Summary(
                results.size(),
                divide(recallHits, evidenceQuestionCount),
                divide(directRecallHits, directEvidenceQuestionCount),
                precisionSum / results.size(),
                directPrecisionSum / results.size(),
                divide(reciprocalRankAt20Sum, directEvidenceQuestionCount),
                divide(reciprocalRankAt5Sum, directEvidenceQuestionCount),
                divide(ndcgSum, ndcgQuestionCount),
                divide(duplicateCount, returnedTop5Count),
                totalLatency.averageMillis(),
                totalLatency.p95Millis(),
                scoreDistribution(results.stream().filter(result -> !result.noEvidence()).toList()),
                scoreDistribution(results.stream().filter(QuestionResult::noEvidence).toList()),
                decisionMetrics,
                countDistribution(userResultCounts),
                countDistribution(candidateCounts),
                totalLatency,
                embeddingLatency,
                dbLatency);
    }

    private List<CandidateResult> returnedCandidates(
            QuestionResult result,
            List<CandidateResult> candidates) {
        Map<Long, CandidateResult> candidatesById = candidates.stream()
                .collect(java.util.stream.Collectors.toMap(CandidateResult::chunkId, candidate -> candidate));
        return result.returnedChunkIds().stream()
                .limit(FINAL_RESULT_LIMIT)
                .map(chunkId -> {
                    CandidateResult candidate = candidatesById.get(chunkId);
                    if (candidate == null) {
                        throw new IllegalStateException(
                                "Returned result is missing from the evaluated candidate set.");
                    }
                    return candidate;
                })
                .toList();
    }

    private double reciprocalRank(List<CandidateResult> candidates, int cutoff) {
        int limit = Math.min(cutoff, candidates.size());
        for (int index = 0; index < limit; index++) {
            if (candidates.get(index).relevance() == 2) {
                return 1.0d / (index + 1.0d);
            }
        }
        return 0.0d;
    }

    /** 동일 evidence group의 두 번째 결과부터 gain을 0으로 해 중복이 nDCG를 부풀리지 않게 한다. */
    private double ndcgAt5(List<ExpectedEvidence> expected, List<CandidateResult> actual) {
        Set<String> seenActualGroups = new HashSet<>();
        double dcg = 0.0d;
        for (int index = 0; index < actual.size(); index++) {
            CandidateResult candidate = actual.get(index);
            int relevance = seenActualGroups.add(candidate.evidenceGroupId()) ? candidate.relevance() : 0;
            dcg += gain(relevance) / log2(index + 2.0d);
        }

        Map<String, Integer> idealGroupRelevance = new HashMap<>();
        for (ExpectedEvidence evidence : expected) {
            idealGroupRelevance.merge(evidence.evidenceGroupId(), evidence.relevance(), Math::max);
        }
        List<Integer> ideal = idealGroupRelevance.values().stream()
                .sorted(Comparator.reverseOrder())
                .limit(FINAL_RESULT_LIMIT)
                .toList();
        double idealDcg = 0.0d;
        for (int index = 0; index < ideal.size(); index++) {
            idealDcg += gain(ideal.get(index)) / log2(index + 2.0d);
        }
        return idealDcg == 0.0d ? 0.0d : dcg / idealDcg;
    }

    private ScoreDistribution scoreDistribution(List<QuestionResult> results) {
        List<QuestionResult> withResult = results.stream()
                .filter(result -> result.top1Score() != null && result.top1Distance() != null)
                .toList();
        if (withResult.isEmpty()) {
            return new ScoreDistribution(0, null, null, null, null, null, null);
        }
        return new ScoreDistribution(
                withResult.size(),
                withResult.stream().mapToDouble(QuestionResult::top1Score).min().orElseThrow(),
                withResult.stream().mapToDouble(QuestionResult::top1Score).average().orElseThrow(),
                withResult.stream().mapToDouble(QuestionResult::top1Score).max().orElseThrow(),
                withResult.stream().mapToDouble(QuestionResult::top1Distance).min().orElseThrow(),
                withResult.stream().mapToDouble(QuestionResult::top1Distance).average().orElseThrow(),
                withResult.stream().mapToDouble(QuestionResult::top1Distance).max().orElseThrow());
    }

    private CountDistribution countDistribution(List<Integer> values) {
        return new CountDistribution(
                values.size(),
                values.stream().mapToInt(Integer::intValue).min().orElse(0),
                values.stream().mapToInt(Integer::intValue).average().orElse(0.0d),
                values.stream().mapToInt(Integer::intValue).max().orElse(0));
    }

    private LatencyDistribution latencyDistribution(List<Long> values) {
        return new LatencyDistribution(
                values.size(),
                values.stream().mapToLong(Long::longValue).average().orElse(0.0d),
                percentile(values, 0.50d),
                percentile(values, 0.95d));
    }

    /** 기존 p95와 동일한 nearest-rank 방식으로 p50·p95를 계산한다. */
    private long percentile(List<Long> values, double percentile) {
        if (values.isEmpty()) {
            return 0L;
        }
        List<Long> sorted = values.stream().sorted().toList();
        int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
        return sorted.get(index);
    }

    private double gain(int relevance) {
        return Math.pow(2.0d, relevance) - 1.0d;
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2.0d);
    }

    private double divide(double numerator, double denominator) {
        return denominator == 0.0d ? 0.0d : numerator / denominator;
    }
}
