package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.PreparedInput;
import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.RawChunk;
import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.RawQuestion;
import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.SparseOutput;
import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.SparseQuestion;
import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.SparseRank;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class SearchEvaluationBgeM3SparseArtifactsTest {

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SearchEvaluationBgeM3SparseArtifacts artifacts =
            new SearchEvaluationBgeM3SparseArtifacts(objectMapper);

    @Test
    void loadsOnlyACompleteSparseOutputBoundToTheExactPreparedInput() throws IOException {
        PreparedInput input = preparedInput();
        Path output = writeOutput(validOutput(input));

        SearchEvaluationBgeM3SparseArtifacts.SparseRun run =
                artifacts.loadOutput(output, input);

        assertThat(run.model()).isEqualTo("BAAI/bge-m3");
        assertThat(run.question("p8-01").candidates())
                .extracting(SparseRank::fixtureChunkId)
                .containsExactly("document:chunk-1");
    }

    @Test
    void rejectsAnOutputFromAStaleOrDifferentPreparedInput() throws IOException {
        PreparedInput input = preparedInput();
        SparseOutput valid = validOutput(input);
        SparseOutput stale = new SparseOutput(
                valid.schemaVersion(),
                valid.generatedAt(),
                valid.datasetId(),
                valid.chunkingProfile(),
                "different-input-digest",
                valid.model(),
                valid.modelRevision(),
                valid.flagEmbeddingVersion(),
                valid.device(),
                valid.useFp16(),
                valid.maxLength(),
                valid.branchLimit(),
                valid.chunkCount(),
                valid.questionCount(),
                valid.modelLoadMillis(),
                valid.corpusEncodingMillis(),
                valid.warmupMillis(),
                valid.gpuPeakMemoryBytes(),
                valid.questions());

        assertThatThrownBy(() -> artifacts.loadOutput(writeOutput(stale), input))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessageContaining("metadata");
    }

    @Test
    void rejectsAnUnknownSparseCandidateInsteadOfFallingBackToDense() throws IOException {
        PreparedInput input = preparedInput();
        SparseOutput valid = validOutput(input);
        SparseQuestion unknownCandidate = new SparseQuestion(
                "p8-01",
                input.questions().get(0).querySha256(),
                1.0d,
                0.1d,
                Map.of("알림", 0.5d),
                List.of(new SparseRank(1, "unknown:chunk-1", 0.5d)));
        SparseOutput malformed = new SparseOutput(
                valid.schemaVersion(),
                valid.generatedAt(),
                valid.datasetId(),
                valid.chunkingProfile(),
                valid.inputDigest(),
                valid.model(),
                valid.modelRevision(),
                valid.flagEmbeddingVersion(),
                valid.device(),
                valid.useFp16(),
                valid.maxLength(),
                valid.branchLimit(),
                valid.chunkCount(),
                valid.questionCount(),
                valid.modelLoadMillis(),
                valid.corpusEncodingMillis(),
                valid.warmupMillis(),
                valid.gpuPeakMemoryBytes(),
                List.of(unknownCandidate));

        assertThatThrownBy(() -> artifacts.loadOutput(writeOutput(malformed), input))
                .isInstanceOf(SearchEvaluationDataException.class)
                .hasMessageContaining("candidates");
    }

    private PreparedInput preparedInput() {
        return artifacts.writeInput(
                temporaryDirectory,
                "p14-dataset",
                "production",
                List.of(new RawChunk("document:chunk-1", "알림 처리 근거")),
                List.of(new RawQuestion("p8-01", "알림")));
    }

    private SparseOutput validOutput(PreparedInput input) {
        SparseQuestion question = new SparseQuestion(
                "p8-01",
                input.questions().get(0).querySha256(),
                1.0d,
                0.1d,
                Map.of("알림", 0.5d),
                List.of(new SparseRank(1, "document:chunk-1", 0.5d)));
        return new SparseOutput(
                SearchEvaluationBgeM3SparseArtifacts.SCHEMA_VERSION,
                "2026-08-12T00:00:00Z",
                input.datasetId(),
                input.chunkingProfile(),
                input.inputDigest(),
                SearchEvaluationBgeM3SparseArtifacts.MODEL,
                "model-revision",
                SearchEvaluationBgeM3SparseArtifacts.FLAG_EMBEDDING_VERSION,
                "cuda:0",
                true,
                SearchEvaluationBgeM3SparseArtifacts.MAX_LENGTH,
                SearchEvaluationBgeM3SparseArtifacts.BRANCH_LIMIT,
                input.chunks().size(),
                input.questions().size(),
                10.0d,
                2.0d,
                1.0d,
                1024L,
                List.of(question));
    }

    private Path writeOutput(SparseOutput output) throws IOException {
        Path path = temporaryDirectory.resolve("sparse-output.json");
        Files.writeString(
                path,
                objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(output),
                StandardCharsets.UTF_8);
        return path;
    }
}
