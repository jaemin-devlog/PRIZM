package com.prizm.search.evaluation;

import com.prizm.search.evaluation.SearchEvaluationData.CandidateResult;
import com.prizm.search.evaluation.SearchEvaluationData.QuestionResult;
import com.prizm.search.evaluation.SearchEvaluationData.Report;
import com.prizm.search.evaluation.SearchEvaluationData.ReportFiles;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** 전체 원문 없이 요약 JSON과 임계값 분석용 후보 CSV를 로컬 경로에 기록한다. */
public class SearchEvaluationReportWriter {

    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private final ObjectMapper objectMapper;
    private final Clock clock;

    public SearchEvaluationReportWriter(ObjectMapper objectMapper) {
        this(objectMapper, Clock.systemUTC());
    }

    SearchEvaluationReportWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public ReportFiles write(Path outputDirectory, Report report) {
        try {
            Files.createDirectories(outputDirectory);
            String runToken = FILE_TIMESTAMP.format(Instant.now(clock)) + "-"
                    + UUID.randomUUID().toString().substring(0, 8);
            Path reportPath = outputDirectory.resolve("dense-baseline-" + runToken + ".json");
            Path rawPath = outputDirectory.resolve("dense-baseline-candidates-" + runToken + ".csv");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
            Files.writeString(rawPath, rawCandidates(report), StandardCharsets.UTF_8);
            return new ReportFiles(reportPath, rawPath);
        }
        catch (IOException | JacksonException exception) {
            throw new SearchEvaluationDataException("Failed to write local search evaluation results.", exception);
        }
    }

    private String rawCandidates(Report report) {
        StringBuilder csv = new StringBuilder(
                "question_id,split,category,no_evidence,rank,chunk_id,fixture_chunk_id,fixture_evidence_ids,score,distance,relevance,evidence_group_id\n");
        for (QuestionResult question : report.questions()) {
            for (CandidateResult candidate : question.candidates()) {
                csv.append(escape(question.questionId())).append(',')
                        .append(question.split()).append(',')
                        .append(question.category()).append(',')
                        .append(question.noEvidence()).append(',')
                        .append(candidate.rank()).append(',')
                        .append(candidate.chunkId()).append(',')
                        .append(escape(candidate.fixtureChunkId())).append(',')
                        .append(escape(String.join("|", candidate.fixtureEvidenceIds()))).append(',')
                        .append(candidate.score()).append(',')
                        .append(candidate.distance()).append(',')
                        .append(candidate.relevance()).append(',')
                        .append(escape(candidate.evidenceGroupId())).append('\n');
            }
        }
        return csv.toString();
    }

    private String escape(String value) {
        String safe = value == null ? "" : value;
        return '"' + safe.replace("\"", "\"\"") + '"';
    }
}
