package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.evaluation.SearchEvaluationData.Breakdown;
import com.prizm.search.evaluation.SearchEvaluationData.Report;
import com.prizm.search.evaluation.SearchEvaluationData.ReportFiles;
import com.prizm.search.evaluation.SearchEvaluationData.ScoreDistribution;
import com.prizm.search.evaluation.SearchEvaluationData.Summary;
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

    private Report emptyReport() {
        ScoreDistribution distribution = new ScoreDistribution(0, null, null, null, null, null, null);
        Summary summary = new Summary(0, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0.0d, 0L,
                distribution, distribution);
        return new Report("2026-07-24T00:00:00Z", "synthetic", new Breakdown(summary, Map.of(), Map.of()), List.of());
    }
}
