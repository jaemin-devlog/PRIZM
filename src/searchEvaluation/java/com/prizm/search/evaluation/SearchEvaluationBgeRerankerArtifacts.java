package com.prizm.search.evaluation;

import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.InputChunk;
import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.InputQuestion;
import com.prizm.search.evaluation.SearchEvaluationBgeM3SparseArtifacts.PreparedInput;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Validates evaluation-only bge-reranker-v2-m3 score artifacts. */
final class SearchEvaluationBgeRerankerArtifacts {

    static final int SCHEMA_VERSION = 1;
    static final int MAX_LENGTH = 512;
    static final int BATCH_SIZE = 32;
    static final String MODEL = "BAAI/bge-reranker-v2-m3";
    static final String INFERENCE_LIBRARY = "transformers";
    static final String INFERENCE_LIBRARY_VERSION = "5.15.0";
    static final String SOURCE_PROFILE = SearchEvaluationDenseSparseRrfProfile.PROFILE_ID;
    static final String OUTPUT_ENVIRONMENT_VARIABLE =
            "PRIZM_SEARCH_EVALUATION_RERANKER_SCORES";

    private final ObjectMapper objectMapper;

    SearchEvaluationBgeRerankerArtifacts(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    RerankerRun loadOutput(Path path, PreparedInput expected) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new SearchEvaluationDataException(
                    "P15 requires an official BGE reranker score output file.");
        }
        final RerankerOutput output;
        try {
            output = objectMapper.readValue(
                    Files.readString(path, StandardCharsets.UTF_8),
                    RerankerOutput.class);
        }
        catch (IOException | JacksonException exception) {
            throw new SearchEvaluationDataException(
                    "Failed to read the P15 BGE reranker output.", exception);
        }
        validateOutput(output, expected);

        Map<String, RerankerQuestion> questionsById = new LinkedHashMap<>();
        for (RerankerQuestion question : output.questions()) {
            questionsById.put(question.questionId(), question);
        }
        return new RerankerRun(
                output.model(),
                output.modelRevision(),
                output.inferenceLibrary(),
                output.inferenceLibraryVersion(),
                output.device(),
                output.useFp16(),
                output.modelLoadMillis(),
                output.warmupMillis(),
                output.gpuModelAllocatedBytes(),
                output.gpuModelReservedBytes(),
                output.gpuPeakAllocatedBytes(),
                output.gpuPeakReservedBytes(),
                output.processRssBeforeLoadBytes(),
                output.processRssAfterLoadBytes(),
                output.processRssPeakBytes(),
                output.maximumPairTokens(),
                Map.copyOf(questionsById));
    }

    private void validateOutput(RerankerOutput output, PreparedInput expected) {
        if (output == null
                || output.schemaVersion() != SCHEMA_VERSION
                || !expected.datasetId().equals(output.datasetId())
                || !expected.chunkingProfile().equals(output.chunkingProfile())
                || !expected.inputDigest().equals(output.inputDigest())
                || !MODEL.equals(output.model())
                || !INFERENCE_LIBRARY.equals(output.inferenceLibrary())
                || !INFERENCE_LIBRARY_VERSION.equals(output.inferenceLibraryVersion())
                || !SOURCE_PROFILE.equals(output.sourceProfile())
                || output.maxLength() != MAX_LENGTH
                || output.batchSize() != BATCH_SIZE
                || output.normalized()
                || output.chunkCount() != expected.chunks().size()
                || output.questionCount() != expected.questions().size()
                || isBlank(output.modelRevision())
                || isBlank(output.device())
                || output.useFp16() != output.device().startsWith("cuda")
                || isBlank(output.p14ReportSha256())
                || isBlank(output.sparseOutputSha256())
                || output.maximumPairTokens() < 1
                || output.maximumPairTokens() > MAX_LENGTH
                || output.questions() == null) {
            throw new SearchEvaluationDataException(
                    "P15 reranker output metadata does not match the prepared P14 evaluation input.");
        }
        requireFiniteNonNegative(output.modelLoadMillis(), "modelLoadMillis");
        requireFiniteNonNegative(output.warmupMillis(), "warmupMillis");
        requireNonNegative(output.gpuModelAllocatedBytes(), "gpuModelAllocatedBytes");
        requireNonNegative(output.gpuModelReservedBytes(), "gpuModelReservedBytes");
        requireNonNegative(output.gpuPeakAllocatedBytes(), "gpuPeakAllocatedBytes");
        requireNonNegative(output.gpuPeakReservedBytes(), "gpuPeakReservedBytes");
        requireNonNegative(output.processRssBeforeLoadBytes(), "processRssBeforeLoadBytes");
        requireNonNegative(output.processRssAfterLoadBytes(), "processRssAfterLoadBytes");
        requireNonNegative(output.processRssPeakBytes(), "processRssPeakBytes");

        Map<String, InputQuestion> expectedQuestions = new HashMap<>();
        for (InputQuestion question : expected.questions()) {
            expectedQuestions.put(question.questionId(), question);
        }
        Set<String> knownChunkIds = new HashSet<>();
        for (InputChunk chunk : expected.chunks()) {
            knownChunkIds.add(chunk.fixtureChunkId());
        }

        Set<String> seenQuestionIds = new HashSet<>();
        for (RerankerQuestion question : output.questions()) {
            InputQuestion expectedQuestion = expectedQuestions.get(question.questionId());
            if (expectedQuestion == null
                    || !seenQuestionIds.add(question.questionId())
                    || !expectedQuestion.querySha256().equals(question.querySha256())
                    || question.candidates() == null
                    || question.candidates().size() > expected.chunks().size()) {
                throw new SearchEvaluationDataException(
                        "P15 reranker output contains an unknown, duplicate, or mismatched question.");
            }
            requireFiniteNonNegative(question.inferenceMillis(), "inferenceMillis");
            validateCandidates(question.candidates(), knownChunkIds);
        }
        if (!seenQuestionIds.equals(expectedQuestions.keySet())) {
            throw new SearchEvaluationDataException(
                    "P15 reranker output must contain every prepared question exactly once.");
        }
    }

    private void validateCandidates(
            List<RerankerRank> candidates,
            Set<String> knownChunkIds) {
        Set<String> seenChunkIds = new HashSet<>();
        Set<Integer> seenP14Ranks = new HashSet<>();
        double previousScore = Double.POSITIVE_INFINITY;
        int previousP14Rank = -1;
        for (int index = 0; index < candidates.size(); index++) {
            RerankerRank candidate = candidates.get(index);
            boolean invalidTieOrder = Double.compare(candidate == null
                            ? Double.NaN
                            : candidate.rerankerScore(), previousScore) == 0
                    && candidate != null
                    && candidate.p14Rank() < previousP14Rank;
            if (candidate == null
                    || candidate.rerankerRank() != index + 1
                    || candidate.p14Rank() < 1
                    || candidate.p14Rank() > candidates.size()
                    || !seenP14Ranks.add(candidate.p14Rank())
                    || !knownChunkIds.contains(candidate.fixtureChunkId())
                    || !seenChunkIds.add(candidate.fixtureChunkId())
                    || !Double.isFinite(candidate.rerankerScore())
                    || candidate.rerankerScore() > previousScore
                    || invalidTieOrder) {
                throw new SearchEvaluationDataException(
                        "P15 reranker candidates must exactly rank known P14 candidates with finite scores.");
            }
            previousScore = candidate.rerankerScore();
            previousP14Rank = candidate.p14Rank();
        }
    }

    private static void requireFiniteNonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new SearchEvaluationDataException(
                    "P15 reranker " + field + " must be finite and non-negative.");
        }
    }

    private static void requireNonNegative(long value, String field) {
        if (value < 0L) {
            throw new SearchEvaluationDataException(
                    "P15 reranker " + field + " must not be negative.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    record RerankerRank(
            int p14Rank,
            int rerankerRank,
            String fixtureChunkId,
            double rerankerScore) {
    }

    record RerankerQuestion(
            String questionId,
            String querySha256,
            double inferenceMillis,
            List<RerankerRank> candidates) {
    }

    record RerankerOutput(
            int schemaVersion,
            String generatedAt,
            String datasetId,
            String chunkingProfile,
            String inputDigest,
            String sourceProfile,
            String p14ReportSha256,
            String sparseOutputSha256,
            String model,
            String modelRevision,
            String inferenceLibrary,
            String inferenceLibraryVersion,
            String pythonVersion,
            String torchVersion,
            String transformersVersion,
            String device,
            boolean useFp16,
            boolean normalized,
            int maxLength,
            int batchSize,
            int chunkCount,
            int questionCount,
            int maximumPairTokens,
            double modelLoadMillis,
            double warmupMillis,
            long gpuModelAllocatedBytes,
            long gpuModelReservedBytes,
            long gpuPeakAllocatedBytes,
            long gpuPeakReservedBytes,
            long processRssBeforeLoadBytes,
            long processRssAfterLoadBytes,
            long processRssPeakBytes,
            List<RerankerQuestion> questions) {
    }

    record RerankerRun(
            String model,
            String modelRevision,
            String inferenceLibrary,
            String inferenceLibraryVersion,
            String device,
            boolean useFp16,
            double modelLoadMillis,
            double warmupMillis,
            long gpuModelAllocatedBytes,
            long gpuModelReservedBytes,
            long gpuPeakAllocatedBytes,
            long gpuPeakReservedBytes,
            long processRssBeforeLoadBytes,
            long processRssAfterLoadBytes,
            long processRssPeakBytes,
            int maximumPairTokens,
            Map<String, RerankerQuestion> questionsById) {

        RerankerQuestion question(String questionId) {
            RerankerQuestion question = questionsById.get(questionId);
            if (question == null) {
                throw new SearchEvaluationDataException(
                        "P15 reranker output is missing question " + questionId + ".");
            }
            return question;
        }
    }
}
