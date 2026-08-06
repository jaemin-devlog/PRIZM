package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.evaluation.SearchEvaluationData.CandidateResult;
import com.prizm.search.evaluation.SearchEvaluationData.Breakdown;
import com.prizm.search.evaluation.SearchEvaluationData.Category;
import com.prizm.search.evaluation.SearchEvaluationData.ExpectedEvidence;
import com.prizm.search.evaluation.SearchEvaluationData.QuestionResult;
import com.prizm.search.evaluation.SearchEvaluationData.SearchState;
import com.prizm.search.evaluation.SearchEvaluationData.Summary;
import com.prizm.search.evaluation.SearchEvaluationData.Split;
import com.prizm.ingestion.entity.ChunkSourceType;
import java.util.List;
import org.junit.jupiter.api.Test;

class SearchEvaluationMetricsTest {

    private final SearchEvaluationMetrics metrics = new SearchEvaluationMetrics();

    @Test
    void calculatesPrecisionAtFiveAndDirectPrecision() {
        QuestionResult result = result(
                List.of(expected("direct", 2, "group-direct"), expected("partial", 1, "group-partial")),
                List.of(
                        candidate(1, 2, "group-direct"),
                        candidate(2, 1, "group-partial"),
                        candidate(3, 0, "irrelevant-1"),
                        candidate(4, 2, "group-direct"),
                        candidate(5, 0, "irrelevant-2")));

        Summary summary = metrics.calculate(List.of(result));

        assertThat(summary.precisionAt5()).isEqualTo(0.6d);
        assertThat(summary.directPrecisionAt5()).isEqualTo(0.4d);
    }

    @Test
    void keepsPrecisionAtFiveDenominatorFixedForZeroOneAndFiveResults() {
        List<ExpectedEvidence> expected = List.of(expected("direct", 2, "direct-group"));
        QuestionResult zero = result(expected, List.of());
        QuestionResult one = result(expected, List.of(candidate(1, 2, "direct-group")));
        QuestionResult five = result(expected, List.of(
                candidate(1, 2, "group-1"),
                candidate(2, 1, "group-2"),
                candidate(3, 1, "group-3"),
                candidate(4, 2, "group-4"),
                candidate(5, 1, "group-5")));

        assertThat(metrics.calculate(List.of(zero)).precisionAt5()).isZero();
        assertThat(metrics.calculate(List.of(one)).precisionAt5()).isEqualTo(0.2d);
        assertThat(metrics.calculate(List.of(five)).precisionAt5()).isEqualTo(1.0d);
    }

    @Test
    void calculatesRecallAtTwentyForPartialAndDirectEvidenceSeparately() {
        QuestionResult hit = result(
                List.of(expected("partial", 1, "partial-group"), expected("direct", 2, "direct-group")),
                List.of(candidate(1, 1, "partial-group")));
        QuestionResult miss = result(
                List.of(expected("direct-2", 2, "direct-group-2")),
                List.of(candidate(1, 0, "irrelevant")));

        Summary summary = metrics.calculate(List.of(hit, miss));

        assertThat(summary.recallAt20()).isEqualTo(0.5d);
        assertThat(summary.directRecallAt20()).isZero();
    }

    @Test
    void calculatesMrrFromFirstDirectEvidenceRank() {
        QuestionResult result = result(
                List.of(expected("direct", 2, "direct-group")),
                List.of(
                        candidate(1, 0, "irrelevant"),
                        candidate(2, 1, "partial"),
                        candidate(3, 2, "direct-group")));

        assertThat(metrics.calculate(List.of(result)).directMrrAt20()).isEqualTo(1.0d / 3.0d);
    }

    @Test
    void distinguishesDirectMrrAtFiveAndTwentyCutoffs() {
        QuestionResult result = result(
                List.of(expected("direct", 2, "direct-group")),
                List.of(
                        candidate(1, 0, "irrelevant-1"),
                        candidate(2, 0, "irrelevant-2"),
                        candidate(3, 0, "irrelevant-3"),
                        candidate(4, 0, "irrelevant-4"),
                        candidate(5, 0, "irrelevant-5"),
                        candidate(6, 2, "direct-group")),
                false,
                Category.DIRECT_EVIDENCE,
                SearchState.EVIDENCE_FOUND,
                5,
                null,
                10L,
                3L,
                5L);

        Summary summary = metrics.calculate(List.of(result));

        assertThat(summary.directMrrAt5()).isZero();
        assertThat(summary.directMrrAt20()).isEqualTo(1.0d / 6.0d);
    }

    @Test
    void rejectedDirectQuestionHasZeroMrrAtBothCutoffs() {
        QuestionResult rejected = result(
                List.of(expected("direct", 2, "direct-group")),
                List.of(candidate(1, 2, "direct-group")),
                false,
                Category.DIRECT_EVIDENCE,
                SearchState.NO_EVIDENCE,
                0,
                null,
                10L,
                3L,
                5L);

        Summary summary = metrics.calculate(List.of(rejected));

        assertThat(summary.directMrrAt5()).isZero();
        assertThat(summary.directMrrAt20()).isZero();
    }

    @Test
    void calculatesDirectMrrOnlyAcrossQuestionsWithDirectEvidence() {
        QuestionResult directHit = result(
                List.of(expected("direct-hit", 2, "direct-hit-group")),
                List.of(candidate(1, 0, "irrelevant"), candidate(2, 2, "direct-hit-group")));
        QuestionResult directMiss = result(
                List.of(expected("direct-miss", 2, "direct-miss-group")),
                List.of(candidate(1, 0, "irrelevant")));
        QuestionResult partialOnly = result(
                List.of(expected("partial", 1, "partial-group")),
                List.of(candidate(1, 1, "partial-group")));

        Summary summary = metrics.calculate(List.of(directHit, directMiss, partialOnly));

        assertThat(summary.directMrrAt5()).isEqualTo(0.25d);
        assertThat(summary.directMrrAt20()).isEqualTo(0.25d);
        assertThat(metrics.calculate(List.of(partialOnly)).directMrrAt5()).isZero();
        assertThat(metrics.calculate(List.of(partialOnly)).directMrrAt20()).isZero();
    }

    @Test
    void calculatesPerfectNdcgForIdealGradedOrder() {
        QuestionResult result = result(
                List.of(expected("direct", 2, "direct-group"), expected("partial", 1, "partial-group")),
                List.of(candidate(1, 2, "direct-group"), candidate(2, 1, "partial-group")));

        assertThat(metrics.calculate(List.of(result)).ndcgAt5()).isEqualTo(1.0d);
    }

    @Test
    void givesZeroGainToRepeatedEvidenceGroupInNdcg() {
        QuestionResult result = result(
                List.of(
                        expected("direct-a", 2, "same-direct-group"),
                        expected("direct-b", 2, "same-direct-group"),
                        expected("partial", 1, "partial-group")),
                List.of(
                        candidate(1, 2, "same-direct-group"),
                        candidate(2, 2, "same-direct-group"),
                        candidate(3, 1, "partial-group")));
        double expected = (3.0d + 1.0d / log2(4.0d))
                / (3.0d + 1.0d / log2(3.0d));

        assertThat(metrics.calculate(List.of(result)).ndcgAt5()).isEqualTo(expected);
    }

    @Test
    void calculatesDuplicateEvidenceGroupRatio() {
        QuestionResult result = result(
                List.of(expected("direct", 2, "same-evidence")),
                List.of(
                        candidate(1, 2, "same-evidence"),
                        candidate(2, 2, "same-evidence"),
                        candidate(3, 0, "other-1"),
                        candidate(4, 0, "other-2"),
                        candidate(5, 0, "other-3")));

        assertThat(metrics.calculate(List.of(result)).duplicateResultRatio()).isEqualTo(0.2d);
    }

    @Test
    void calculatesReturnedRankingMetricsFromActualReturnedChunkIds() {
        List<CandidateResult> candidates = List.of(
                candidate(1, 0, "irrelevant"),
                candidate(2, 2, "direct"),
                candidate(3, 2, "direct"));
        QuestionResult reranked = new QuestionResult(
                "q-reranked",
                "합성 재정렬 질문",
                false,
                Split.TUNING,
                Category.DIRECT_EVIDENCE,
                List.of(expected("direct", 2, "direct")),
                List.of(2L),
                List.of(2),
                candidates.get(1).score(),
                candidates.get(1).distance(),
                false,
                10L,
                3L,
                5L,
                SearchState.EVIDENCE_FOUND,
                null,
                candidates);

        Summary summary = metrics.calculate(List.of(reranked));

        assertThat(summary.directMrrAt5()).isEqualTo(1.0d);
        assertThat(summary.directMrrAt20()).isEqualTo(0.5d);
        assertThat(summary.decisionMetrics().top1DirectEvidenceAccuracy()).isEqualTo(1.0d);
        assertThat(summary.precisionAt5()).isEqualTo(0.2d);
        assertThat(summary.duplicateResultRatio()).isZero();
        assertThat(summary.candidateCountDistribution().maximum()).isEqualTo(3);
    }

    @Test
    void returnsZeroDuplicateRatioForEmptyResults() {
        QuestionResult result = result(List.of(expected("direct", 2, "direct-group")), List.of());

        assertThat(metrics.calculate(List.of(result)).duplicateResultRatio()).isZero();
    }

    @Test
    void calculatesNoEvidenceRejectionAndExcludesNoSearchableDocuments() {
        QuestionResult rejected = result(
                List.of(expected("negative-1", 0, "negative-1")),
                List.of(),
                true,
                Category.NO_EVIDENCE,
                SearchState.NO_EVIDENCE,
                0,
                null,
                10L,
                3L,
                5L);
        QuestionResult notRejected = result(
                List.of(expected("negative-2", 0, "negative-2")),
                List.of(candidate(1, 0, "negative-2")),
                true,
                Category.NEAR_TOPIC_NO_EVIDENCE,
                SearchState.EVIDENCE_FOUND,
                1,
                null,
                10L,
                3L,
                5L);
        QuestionResult noDocuments = result(
                List.of(expected("other-owner", 0, "other-owner")),
                List.of(),
                true,
                Category.NO_SEARCHABLE_DOCUMENTS,
                SearchState.NO_SEARCHABLE_DOCUMENTS,
                0,
                null,
                10L,
                3L,
                5L);

        Summary summary = metrics.calculate(List.of(rejected, notRejected, noDocuments));

        assertThat(summary.decisionMetrics().noEvidenceQuestionCount()).isEqualTo(2);
        assertThat(summary.decisionMetrics().noEvidenceRejectionRate()).isEqualTo(0.5d);
        assertThat(summary.decisionMetrics().noSearchableDocumentsQuestionCount()).isEqualTo(1);
        assertThat(summary.decisionMetrics().noSearchableDocumentsAccuracy()).isEqualTo(1.0d);
    }

    @Test
    void calculatesFalseRejectionAndHandlesEmptyDenominator() {
        QuestionResult rejected = result(
                List.of(expected("direct-1", 2, "direct-1")),
                List.of(candidate(1, 2, "direct-1")),
                false,
                Category.DIRECT_EVIDENCE,
                SearchState.NO_EVIDENCE,
                0,
                null,
                10L,
                3L,
                5L);
        QuestionResult accepted = result(
                List.of(expected("partial", 1, "partial")),
                List.of(candidate(1, 1, "partial")),
                false,
                Category.PARAPHRASE,
                SearchState.EVIDENCE_FOUND,
                1,
                null,
                10L,
                3L,
                5L);
        QuestionResult noEvidence = result(
                List.of(expected("negative", 0, "negative")),
                List.of(),
                true,
                Category.NO_EVIDENCE,
                SearchState.NO_EVIDENCE,
                0,
                null,
                10L,
                3L,
                5L);

        assertThat(metrics.calculate(List.of(rejected, accepted)).decisionMetrics().falseRejectionRate())
                .isEqualTo(0.5d);
        assertThat(metrics.calculate(List.of(noEvidence)).decisionMetrics().falseRejectionRate()).isZero();
    }

    @Test
    void returnsZeroForDecisionAndRankingMetricsWithEmptyDenominators() {
        QuestionResult partialOnly = result(
                List.of(expected("partial", 1, "partial")),
                List.of(candidate(1, 1, "partial")));
        QuestionResult noEvidence = result(
                List.of(expected("negative", 0, "negative")),
                List.of(),
                true,
                Category.NO_EVIDENCE,
                SearchState.NO_EVIDENCE,
                0,
                null,
                10L,
                3L,
                5L);

        Summary partialSummary = metrics.calculate(List.of(partialOnly));
        Summary noEvidenceSummary = metrics.calculate(List.of(noEvidence));

        assertThat(partialSummary.directMrrAt5()).isZero();
        assertThat(partialSummary.directMrrAt20()).isZero();
        assertThat(partialSummary.decisionMetrics().noEvidenceRejectionRate()).isZero();
        assertThat(partialSummary.decisionMetrics().noSearchableDocumentsAccuracy()).isZero();
        assertThat(partialSummary.decisionMetrics().top1DirectEvidenceAccuracy()).isZero();
        assertThat(partialSummary.decisionMetrics().pdfPageCitationAccuracy()).isZero();
        assertThat(noEvidenceSummary.ndcgAt5()).isZero();
        assertThat(noEvidenceSummary.decisionMetrics().falseRejectionRate()).isZero();
    }

    @Test
    void calculatesTopOneDirectEvidenceHitAndMiss() {
        QuestionResult hit = result(
                List.of(expected("direct-hit", 2, "direct-hit")),
                List.of(candidate(1, 2, "direct-hit")));
        QuestionResult miss = result(
                List.of(expected("direct-miss", 2, "direct-miss")),
                List.of(candidate(1, 0, "irrelevant"), candidate(2, 2, "direct-miss")));

        assertThat(metrics.calculate(List.of(hit, miss)).decisionMetrics().top1DirectEvidenceAccuracy())
                .isEqualTo(0.5d);
    }

    @Test
    void calculatesPdfPageCitationHitAndMiss() {
        QuestionResult hit = result(
                List.of(expected("pdf-hit", 2, "pdf-hit")),
                List.of(pageCandidate(1, 2, "pdf-hit", 2)),
                false,
                Category.PDF_EVIDENCE,
                SearchState.EVIDENCE_FOUND,
                1,
                2,
                10L,
                3L,
                5L);
        QuestionResult miss = result(
                List.of(expected("pdf-miss", 2, "pdf-miss")),
                List.of(pageCandidate(1, 2, "pdf-miss", 3)),
                false,
                Category.PDF_EVIDENCE,
                SearchState.EVIDENCE_FOUND,
                1,
                2,
                10L,
                3L,
                5L);

        assertThat(metrics.calculate(List.of(hit, miss)).decisionMetrics().pdfPageCitationAccuracy())
                .isEqualTo(0.5d);
    }

    @Test
    void recordsUserAndCandidateResultCountsSeparately() {
        List<CandidateResult> threeCandidates = List.of(
                candidate(1, 0, "a"), candidate(2, 0, "b"), candidate(3, 0, "c"));
        List<CandidateResult> fiveCandidates = List.of(
                candidate(1, 0, "d"), candidate(2, 0, "e"), candidate(3, 0, "f"),
                candidate(4, 0, "g"), candidate(5, 0, "h"));
        QuestionResult noneReturned = result(
                List.of(expected("negative-a", 0, "negative-a")),
                threeCandidates,
                true,
                Category.NO_EVIDENCE,
                SearchState.NO_EVIDENCE,
                0,
                null,
                10L,
                3L,
                5L);
        QuestionResult twoReturned = result(
                List.of(expected("negative-b", 0, "negative-b")),
                fiveCandidates,
                true,
                Category.NO_EVIDENCE,
                SearchState.EVIDENCE_FOUND,
                2,
                null,
                10L,
                3L,
                5L);

        Summary summary = metrics.calculate(List.of(noneReturned, twoReturned));

        assertThat(summary.userResultCountDistribution().minimum()).isZero();
        assertThat(summary.userResultCountDistribution().average()).isEqualTo(1.0d);
        assertThat(summary.userResultCountDistribution().maximum()).isEqualTo(2);
        assertThat(summary.candidateCountDistribution().minimum()).isEqualTo(3);
        assertThat(summary.candidateCountDistribution().average()).isEqualTo(4.0d);
        assertThat(summary.candidateCountDistribution().maximum()).isEqualTo(5);
    }

    @Test
    void calculatesNearestRankP50AndP95ForOddEvenAndSingleSamples() {
        Summary odd = metrics.calculate(List.of(
                timedResult(10L, 3L, 5L),
                timedResult(30L, 4L, 6L),
                timedResult(20L, 5L, 7L)));
        Summary even = metrics.calculate(List.of(
                timedResult(10L, 3L, 5L),
                timedResult(20L, 4L, 6L),
                timedResult(30L, 5L, 7L),
                timedResult(40L, 6L, 8L)));
        Summary single = metrics.calculate(List.of(timedResult(17L, 7L, 9L)));

        assertThat(odd.totalLatency().p50Millis()).isEqualTo(20L);
        assertThat(odd.totalLatency().p95Millis()).isEqualTo(30L);
        assertThat(even.totalLatency().p50Millis()).isEqualTo(20L);
        assertThat(even.totalLatency().p95Millis()).isEqualTo(40L);
        assertThat(single.totalLatency().p50Millis()).isEqualTo(17L);
        assertThat(single.totalLatency().p95Millis()).isEqualTo(17L);
    }

    @Test
    void preservesTotalEmbeddingAndDbLatenciesInSeparateFields() {
        Summary summary = metrics.calculate(List.of(timedResult(30L, 10L, 15L)));

        assertThat(summary.totalLatency().p50Millis()).isEqualTo(30L);
        assertThat(summary.embeddingLatency().p50Millis()).isEqualTo(10L);
        assertThat(summary.dbSearchLatency().p50Millis()).isEqualTo(15L);
        assertThat(summary.averageSearchTimeMillis()).isEqualTo(30.0d);
        assertThat(summary.p95SearchTimeMillis()).isEqualTo(30L);
    }

    @Test
    void calculatesSplitAndCategoryBreakdowns() {
        QuestionResult tuning = result(
                List.of(expected("direct", 2, "direct-group")),
                List.of(candidate(1, 2, "direct-group")));
        QuestionResult test = new QuestionResult(
                "q-2",
                "합성 테스트 질문",
                true,
                Split.TEST,
                Category.NO_EVIDENCE,
                List.of(),
                List.of(1L),
                List.of(0),
                0.4d,
                0.6d,
                false,
                12L,
                4L,
                6L,
                SearchState.EVIDENCE_FOUND,
                null,
                List.of(candidate(1, 0, "irrelevant")));

        Breakdown breakdown = metrics.calculateBreakdown(List.of(tuning, test));

        assertThat(breakdown.overall().questionCount()).isEqualTo(2);
        assertThat(breakdown.splits().get(Split.TUNING).questionCount()).isEqualTo(1);
        assertThat(breakdown.splits().get(Split.TEST).questionCount()).isEqualTo(1);
        assertThat(breakdown.categories().get(Category.TECHNICAL_EXPERIENCE).questionCount()).isEqualTo(1);
        assertThat(breakdown.categories().get(Category.NO_EVIDENCE).noEvidenceScoreDistribution().questionCount())
                .isEqualTo(1);
    }

    private QuestionResult result(List<ExpectedEvidence> expected, List<CandidateResult> candidates) {
        return result(
                expected,
                candidates,
                false,
                Category.TECHNICAL_EXPERIENCE,
                SearchState.EVIDENCE_FOUND,
                Math.min(5, candidates.size()),
                null,
                10L,
                3L,
                5L);
    }

    private QuestionResult result(
            List<ExpectedEvidence> expected,
            List<CandidateResult> candidates,
            boolean noEvidence,
            Category category,
            SearchState searchState,
            int returnedCount,
            Integer goldPage,
            long totalMillis,
            long embeddingMillis,
            long dbMillis) {
        List<Long> chunkIds = candidates.stream().limit(returnedCount).map(CandidateResult::chunkId).toList();
        List<Integer> relevance = candidates.stream().limit(returnedCount).map(CandidateResult::relevance).toList();
        return new QuestionResult(
                "q-1",
                "합성 질문",
                noEvidence,
                Split.TUNING,
                category,
                expected,
                chunkIds,
                relevance,
                candidates.isEmpty() ? null : candidates.get(0).score(),
                candidates.isEmpty() ? null : candidates.get(0).distance(),
                false,
                totalMillis,
                embeddingMillis,
                dbMillis,
                searchState,
                goldPage,
                candidates);
    }

    private QuestionResult timedResult(long totalMillis, long embeddingMillis, long dbMillis) {
        return result(
                List.of(expected("negative", 0, "negative")),
                List.of(),
                true,
                Category.NO_EVIDENCE,
                SearchState.NO_EVIDENCE,
                0,
                null,
                totalMillis,
                embeddingMillis,
                dbMillis);
    }

    private ExpectedEvidence expected(String fixtureId, int relevance, String group) {
        return new ExpectedEvidence(fixtureId, relevance, group);
    }

    private CandidateResult candidate(int rank, int relevance, String group) {
        return candidate(rank, relevance, group, ChunkSourceType.TEXT_CHUNK, rank);
    }

    private CandidateResult pageCandidate(int rank, int relevance, String group, int pageNumber) {
        return candidate(rank, relevance, group, ChunkSourceType.PAGE, pageNumber);
    }

    private CandidateResult candidate(
            int rank,
            int relevance,
            String group,
            ChunkSourceType sourceType,
            int sourceIndex) {
        return new CandidateResult(
                rank,
                rank,
                "fixture:chunk-" + rank,
                List.of("evidence-" + rank),
                sourceType,
                sourceIndex,
                relevance,
                group,
                0.8d - rank * 0.01d,
                0.2d + rank * 0.01d);
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2.0d);
    }
}
