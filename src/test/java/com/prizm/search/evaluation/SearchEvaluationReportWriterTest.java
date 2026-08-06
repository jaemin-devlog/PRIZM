package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.evaluation.SearchEvaluationData.Breakdown;
import com.prizm.search.evaluation.SearchEvaluationData.CandidateResult;
import com.prizm.search.evaluation.SearchEvaluationData.Category;
import com.prizm.search.evaluation.SearchEvaluationData.CountDistribution;
import com.prizm.search.evaluation.SearchEvaluationData.DecisionMetrics;
import com.prizm.search.evaluation.SearchEvaluationData.EvaluationProfile;
import com.prizm.search.evaluation.SearchEvaluationData.EvaluationProfileKind;
import com.prizm.search.evaluation.SearchEvaluationData.LatencyDistribution;
import com.prizm.search.evaluation.SearchEvaluationData.QuestionResult;
import com.prizm.search.evaluation.SearchEvaluationData.Report;
import com.prizm.search.evaluation.SearchEvaluationData.ReportFiles;
import com.prizm.search.evaluation.SearchEvaluationData.ScoreDistribution;
import com.prizm.search.evaluation.SearchEvaluationData.SearchState;
import com.prizm.search.evaluation.SearchEvaluationData.Split;
import com.prizm.search.evaluation.SearchEvaluationData.Summary;
import com.prizm.ingestion.entity.ChunkSourceType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class SearchEvaluationReportWriterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void givesEachReportWriteAUniqueRunTokenWhenClockTimeMatches() {
        SearchEvaluationReportWriter writer = new SearchEvaluationReportWriter(
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC));

        ReportFiles first = writer.write(temporaryDirectory, emptyReport());
        ReportFiles second = writer.write(temporaryDirectory, emptyReport());

        assertThat(first.report()).exists();
        assertThat(first.rawCandidates()).exists();
        assertThat(second.report()).exists();
        assertThat(second.rawCandidates()).exists();
        assertThat(first.report()).isNotEqualTo(second.report());
        assertThat(first.rawCandidates()).isNotEqualTo(second.rawCandidates());
    }

    @Test
    void writesProfileDecisionCountsSourceAndSeparateLatencies() throws Exception {
        SearchEvaluationReportWriter writer = new SearchEvaluationReportWriter(
                new ObjectMapper(), Clock.fixed(Instant.parse("2026-07-24T00:00:00Z"), ZoneOffset.UTC));
        CandidateResult candidate = new CandidateResult(
                1,
                101L,
                "pdf-fixture:chunk-1",
                List.of("pdf-evidence"),
                ChunkSourceType.PAGE,
                2,
                2,
                "pdf-group",
                0.9d,
                0.1d);
        QuestionResult question = new QuestionResult(
                "pdf-question",
                "synthetic question",
                false,
                Split.TEST,
                Category.PDF_EVIDENCE,
                List.of(),
                List.of(101L),
                List.of(2),
                0.9d,
                0.1d,
                false,
                30L,
                10L,
                15L,
                SearchState.EVIDENCE_FOUND,
                2,
                List.of(candidate));
        Report report = new Report(
                "2026-07-24T00:00:00Z",
                "synthetic-v2",
                new EvaluationProfile("tuning-candidate", EvaluationProfileKind.EVALUATION_THRESHOLD),
                emptyReport().metrics(),
                List.of(question));

        ReportFiles files = writer.write(temporaryDirectory, report);

        String json = Files.readString(files.report());
        String csv = Files.readString(files.rawCandidates());
        assertThat(json).contains("\"profileId\" : \"tuning-candidate\"")
                .contains("\"kind\" : \"EVALUATION_THRESHOLD\"")
                .contains("\"embeddingTimeMillis\" : 10")
                .contains("\"dbSearchTimeMillis\" : 15");
        assertThat(csv).contains("source_type,source_index,search_state,user_result_count,candidate_count")
                .contains("total_search_ms,embedding_ms,db_search_ms,profile_kind,profile_id")
                .contains("PAGE,2,EVIDENCE_FOUND,1,1,30,10,15,EVALUATION_THRESHOLD,\"tuning-candidate\"");
    }

    private Report emptyReport() {
        ScoreDistribution distribution = new ScoreDistribution(0, null, null, null, null, null, null);
        CountDistribution counts = new CountDistribution(0, 0, 0.0d, 0);
        LatencyDistribution latency = new LatencyDistribution(0, 0.0d, 0L, 0L);
        DecisionMetrics decisions = new DecisionMetrics(
                0, 0.0d, 0, 0.0d, 0, 0.0d, 0, 0.0d, 0, 0.0d);
        Summary summary = new Summary(
                0,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0.0d,
                0L,
                distribution,
                distribution,
                decisions,
                counts,
                counts,
                latency,
                latency,
                latency);
        return new Report(
                "2026-07-24T00:00:00Z",
                "synthetic",
                new EvaluationProfile("current-product", EvaluationProfileKind.CURRENT_PRODUCT),
                new Breakdown(summary, Map.of(), Map.of()),
                List.of());
    }
}
