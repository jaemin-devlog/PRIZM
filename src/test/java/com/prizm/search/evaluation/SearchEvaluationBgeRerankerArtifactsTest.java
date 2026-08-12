package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.InputChunk;
import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.InputQuestion;
import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.PreparedInput;
import com.prizm.search.evaluation.SearchEvaluationBgeRerankerArtifacts.RerankerOutput;
import com.prizm.search.evaluation.SearchEvaluationBgeRerankerArtifacts.RerankerQuestion;
import com.prizm.search.evaluation.SearchEvaluationBgeRerankerArtifacts.RerankerRank;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class SearchEvaluationBgeRerankerArtifactsTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SearchEvaluationBgeRerankerArtifacts artifacts =
            new SearchEvaluationBgeRerankerArtifacts(objectMapper);

    @Test
    void loadsAnExactCompleteRerankerArtifact() throws Exception {
        PreparedInput prepared = preparedInput();
        Path output = write(validOutput(prepared));

        SearchEvaluationBgeRerankerArtifacts.RerankerRun run =
                artifacts.loadOutput(output, prepared);

        assertThat(run.model()).isEqualTo(SearchEvaluationBgeRerankerArtifacts.MODEL);
        assertThat(run.question("q1").candidates())
                .extracting(RerankerRank::fixtureChunkId)
                .containsExactly("chunk-2", "chunk-1");
    }

    @Test
    void rejectsAnArtifactFromAStalePreparedInput() throws Exception {
        PreparedInput prepared = preparedInput();
        RerankerOutput valid = validOutput(prepared);
        RerankerOutput stale = copy(valid, "stale-digest", valid.questions());

        assertThatThrownBy(() -> artifacts.loadOutput(write(stale), prepared))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessageContaining("metadata");
    }

    @Test
    void rejectsUnknownOrIncompleteP14CandidateRanks() throws Exception {
        PreparedInput prepared = preparedInput();
        RerankerOutput valid = validOutput(prepared);
        RerankerQuestion invalidQuestion = new RerankerQuestion(
                "q1",
                "query-hash",
                2.0d,
                List.of(new RerankerRank(2, 1, "unknown", 10.0d)));
        RerankerOutput invalid = copy(valid, prepared.inputDigest(), List.of(invalidQuestion));

        assertThatThrownBy(() -> artifacts.loadOutput(write(invalid), prepared))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessageContaining("candidates");
    }

    private Path write(RerankerOutput output) throws Exception {
        Path path = temporaryDirectory.resolve("reranker-output.json");
        Files.writeString(path, objectMapper.writeValueAsString(output));
        return path;
    }

    private static PreparedInput preparedInput() {
        return new PreparedInput(
                1,
                "dataset",
                "production",
                "input-digest",
                List.of(
                        new InputChunk("chunk-1", "first", "content-hash-1"),
                        new InputChunk("chunk-2", "second", "content-hash-2")),
                List.of(new InputQuestion("q1", "query", "query-hash")));
    }

    private static RerankerOutput validOutput(PreparedInput prepared) {
        return new RerankerOutput(
                SearchEvaluationBgeRerankerArtifacts.SCHEMA_VERSION,
                "2026-08-12T00:00:00Z",
                prepared.datasetId(),
                prepared.chunkingProfile(),
                prepared.inputDigest(),
                SearchEvaluationBgeRerankerArtifacts.SOURCE_PROFILE,
                "p14-report-hash",
                "sparse-output-hash",
                SearchEvaluationBgeRerankerArtifacts.MODEL,
                "model-revision",
                SearchEvaluationBgeRerankerArtifacts.INFERENCE_LIBRARY,
                SearchEvaluationBgeRerankerArtifacts.INFERENCE_LIBRARY_VERSION,
                "3.10.0",
                "2.7.1",
                "5.15.0",
                "cuda:0",
                true,
                false,
                SearchEvaluationBgeRerankerArtifacts.MAX_LENGTH,
                SearchEvaluationBgeRerankerArtifacts.BATCH_SIZE,
                prepared.chunks().size(),
                prepared.questions().size(),
                100,
                100.0d,
                10.0d,
                1L,
                2L,
                3L,
                4L,
                5L,
                6L,
                7L,
                List.of(new RerankerQuestion(
                        "q1",
                        "query-hash",
                        2.0d,
                        List.of(
                                new RerankerRank(2, 1, "chunk-2", 5.0d),
                                new RerankerRank(1, 2, "chunk-1", 1.0d)))));
    }

    private static RerankerOutput copy(
            RerankerOutput source,
            String inputDigest,
            List<RerankerQuestion> questions) {
        return new RerankerOutput(
                source.schemaVersion(),
                source.generatedAt(),
                source.datasetId(),
                source.chunkingProfile(),
                inputDigest,
                source.sourceProfile(),
                source.p14ReportSha256(),
                source.sparseOutputSha256(),
                source.model(),
                source.modelRevision(),
                source.inferenceLibrary(),
                source.inferenceLibraryVersion(),
                source.pythonVersion(),
                source.torchVersion(),
                source.transformersVersion(),
                source.device(),
                source.useFp16(),
                source.normalized(),
                source.maxLength(),
                source.batchSize(),
                source.chunkCount(),
                source.questionCount(),
                source.maximumPairTokens(),
                source.modelLoadMillis(),
                source.warmupMillis(),
                source.gpuModelAllocatedBytes(),
                source.gpuModelReservedBytes(),
                source.gpuPeakAllocatedBytes(),
                source.gpuPeakReservedBytes(),
                source.processRssBeforeLoadBytes(),
                source.processRssAfterLoadBytes(),
                source.processRssPeakBytes(),
                questions);
    }
}
