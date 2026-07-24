package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.evaluation.SearchEvaluationData.CandidateResult;
import com.prizm.search.evaluation.SearchEvaluationData.Breakdown;
import com.prizm.search.evaluation.SearchEvaluationData.Category;
import com.prizm.search.evaluation.SearchEvaluationData.ExpectedEvidence;
import com.prizm.search.evaluation.SearchEvaluationData.QuestionResult;
import com.prizm.search.evaluation.SearchEvaluationData.Summary;
import com.prizm.search.evaluation.SearchEvaluationData.Split;
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

        assertThat(metrics.calculate(List.of(directHit, directMiss, partialOnly)).directMrrAt20()).isEqualTo(0.25d);
    }

    @Test
    void calculatesPerfectNdcgForIdealGradedOrder() {
        QuestionResult result = result(
                List.of(expected("direct", 2, "direct-group"), expected("partial", 1, "partial-group")),
                List.of(candidate(1, 2, "direct-group"), candidate(2, 1, "partial-group")));

        assertThat(metrics.calculate(List.of(result)).ndcgAt5()).isEqualTo(1.0d);
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
                List.of(2L),
                List.of(0),
                0.4d,
                0.6d,
                false,
                12L,
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
        List<Long> chunkIds = candidates.stream().limit(5).map(CandidateResult::chunkId).toList();
        List<Integer> relevance = candidates.stream().limit(5).map(CandidateResult::relevance).toList();
        return new QuestionResult(
                "q-1",
                "합성 질문",
                false,
                Split.TUNING,
                Category.TECHNICAL_EXPERIENCE,
                expected,
                chunkIds,
                relevance,
                candidates.isEmpty() ? null : candidates.get(0).score(),
                candidates.isEmpty() ? null : candidates.get(0).distance(),
                false,
                10L,
                candidates);
    }

    private ExpectedEvidence expected(String fixtureId, int relevance, String group) {
        return new ExpectedEvidence(fixtureId, relevance, group);
    }

    private CandidateResult candidate(int rank, int relevance, String group) {
        return new CandidateResult(
                rank,
                rank,
                "fixture:chunk-" + rank,
                List.of("evidence-" + rank),
                relevance,
                group,
                0.8d - rank * 0.01d,
                0.2d + rank * 0.01d);
    }
}
