package com.prizm.search.evaluation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Writes and validates evaluation-only FlagEmbedding sparse ranking artifacts. */
final class SearchEvaluationBgeM3SparseArtifacts {

    static final int SCHEMA_VERSION = 1;
    static final int BRANCH_LIMIT = 20;
    static final int MAX_LENGTH = 8192;
    static final String MODEL = "BAAI/bge-m3";
    static final String FLAG_EMBEDDING_VERSION = "1.4.0";
    static final String INPUT_FILE = "p14-bge-m3-sparse-input.json";
    static final String OUTPUT_ENVIRONMENT_VARIABLE =
            "PRIZM_SEARCH_EVALUATION_SPARSE_RANKS";

    private final ObjectMapper objectMapper;

    SearchEvaluationBgeM3SparseArtifacts(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    PreparedInput writeInput(
            Path outputDirectory,
            String datasetId,
            String chunkingProfile,
            List<RawChunk> rawChunks,
            List<RawQuestion> rawQuestions) {
        if (outputDirectory == null || isBlank(datasetId) || !"production".equals(chunkingProfile)) {
            throw new SearchEvaluationDataException(
                    "P14 sparse input requires an output directory, dataset ID, and Production chunking.");
        }
        if (rawChunks == null || rawChunks.isEmpty() || rawQuestions == null || rawQuestions.isEmpty()) {
            throw new SearchEvaluationDataException("P14 sparse input requires chunks and questions.");
        }

        Set<String> chunkIds = new HashSet<>();
        List<InputChunk> chunks = new ArrayList<>();
        for (RawChunk chunk : rawChunks) {
            if (chunk == null || isBlank(chunk.fixtureChunkId()) || isBlank(chunk.content())
                    || !chunkIds.add(chunk.fixtureChunkId())) {
                throw new SearchEvaluationDataException(
                        "P14 sparse input chunk IDs and content must be unique and non-blank.");
            }
            chunks.add(new InputChunk(
                    chunk.fixtureChunkId(),
                    chunk.content(),
                    sha256(chunk.content())));
        }

        Set<String> questionIds = new HashSet<>();
        List<InputQuestion> questions = new ArrayList<>();
        for (RawQuestion question : rawQuestions) {
            if (question == null || isBlank(question.questionId()) || isBlank(question.query())
                    || !questionIds.add(question.questionId())) {
                throw new SearchEvaluationDataException(
                        "P14 sparse input question IDs and queries must be unique and non-blank.");
            }
            questions.add(new InputQuestion(
                    question.questionId(),
                    question.query(),
                    sha256(question.query())));
        }

        String inputDigest = inputDigest(datasetId, chunkingProfile, chunks, questions);
        PreparedInput prepared = new PreparedInput(
                SCHEMA_VERSION,
                datasetId,
                chunkingProfile,
                inputDigest,
                List.copyOf(chunks),
                List.copyOf(questions));
        try {
            Files.createDirectories(outputDirectory);
            Files.writeString(
                    outputDirectory.resolve(INPUT_FILE),
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(prepared) + "\n",
                    StandardCharsets.UTF_8);
        }
        catch (IOException | JacksonException exception) {
            throw new SearchEvaluationDataException(
                    "Failed to write the P14 sparse input artifact.", exception);
        }
        return prepared;
    }

    SparseRun loadOutput(Path path, PreparedInput expected) {
        if (path == null || !Files.isRegularFile(path)) {
            throw new SearchEvaluationDataException(
                    "P14 requires a FlagEmbedding sparse ranking output file.");
        }
        final SparseOutput output;
        try {
            output = objectMapper.readValue(Files.readString(path, StandardCharsets.UTF_8), SparseOutput.class);
        }
        catch (IOException | JacksonException exception) {
            throw new SearchEvaluationDataException(
                    "Failed to read the P14 sparse ranking output.", exception);
        }
        validateOutput(output, expected);

        Map<String, SparseQuestion> questionsById = new LinkedHashMap<>();
        for (SparseQuestion question : output.questions()) {
            questionsById.put(question.questionId(), question);
        }
        return new SparseRun(
                output.model(),
                output.modelRevision(),
                output.flagEmbeddingVersion(),
                output.device(),
                output.useFp16(),
                output.modelLoadMillis(),
                output.corpusEncodingMillis(),
                output.warmupMillis(),
                output.gpuPeakMemoryBytes(),
                Map.copyOf(questionsById));
    }

    private void validateOutput(SparseOutput output, PreparedInput expected) {
        if (output == null
                || output.schemaVersion() != SCHEMA_VERSION
                || !expected.datasetId().equals(output.datasetId())
                || !expected.chunkingProfile().equals(output.chunkingProfile())
                || !expected.inputDigest().equals(output.inputDigest())
                || !MODEL.equals(output.model())
                || !FLAG_EMBEDDING_VERSION.equals(output.flagEmbeddingVersion())
                || output.maxLength() != MAX_LENGTH
                || output.branchLimit() != BRANCH_LIMIT
                || output.chunkCount() != expected.chunks().size()
                || output.questionCount() != expected.questions().size()
                || isBlank(output.modelRevision())
                || isBlank(output.device())
                || output.questions() == null) {
            throw new SearchEvaluationDataException(
                    "P14 sparse output metadata does not match the prepared evaluation input.");
        }
        requireFiniteNonNegative(output.modelLoadMillis(), "modelLoadMillis");
        requireFiniteNonNegative(output.corpusEncodingMillis(), "corpusEncodingMillis");
        requireFiniteNonNegative(output.warmupMillis(), "warmupMillis");
        if (output.gpuPeakMemoryBytes() < 0L) {
            throw new SearchEvaluationDataException("P14 sparse GPU memory must not be negative.");
        }

        Map<String, InputQuestion> expectedQuestions = new HashMap<>();
        for (InputQuestion question : expected.questions()) {
            expectedQuestions.put(question.questionId(), question);
        }
        Set<String> knownChunkIds = new HashSet<>();
        for (InputChunk chunk : expected.chunks()) {
            knownChunkIds.add(chunk.fixtureChunkId());
        }

        Set<String> seenQuestionIds = new HashSet<>();
        for (SparseQuestion question : output.questions()) {
            InputQuestion expectedQuestion = expectedQuestions.get(question.questionId());
            if (expectedQuestion == null
                    || !seenQuestionIds.add(question.questionId())
                    || !expectedQuestion.querySha256().equals(question.querySha256())
                    || question.candidates() == null
                    || question.candidates().size() > BRANCH_LIMIT) {
                throw new SearchEvaluationDataException(
                        "P14 sparse output contains an unknown, duplicate, or mismatched question.");
            }
            requireFiniteNonNegative(question.queryEncodingMillis(), "queryEncodingMillis");
            requireFiniteNonNegative(question.scoringMillis(), "scoringMillis");
            validateCandidates(question.candidates(), knownChunkIds);
        }
        if (!seenQuestionIds.equals(expectedQuestions.keySet())) {
            throw new SearchEvaluationDataException(
                    "P14 sparse output must contain every prepared question exactly once.");
        }
    }

    private void validateCandidates(List<SparseRank> candidates, Set<String> knownChunkIds) {
        Set<String> seenChunkIds = new HashSet<>();
        double previousScore = Double.POSITIVE_INFINITY;
        String previousId = null;
        for (int index = 0; index < candidates.size(); index++) {
            SparseRank candidate = candidates.get(index);
            if (candidate == null
                    || candidate.rank() != index + 1
                    || !knownChunkIds.contains(candidate.fixtureChunkId())
                    || !seenChunkIds.add(candidate.fixtureChunkId())
                    || !Double.isFinite(candidate.sparseScore())
                    || candidate.sparseScore() <= 0.0d
                    || candidate.sparseScore() > previousScore
                    || (Double.compare(candidate.sparseScore(), previousScore) == 0
                            && previousId != null
                            && candidate.fixtureChunkId().compareTo(previousId) < 0)) {
                throw new SearchEvaluationDataException(
                        "P14 sparse candidates must be ranked, unique, known, finite, and positive.");
            }
            previousScore = candidate.sparseScore();
            previousId = candidate.fixtureChunkId();
        }
    }

    private static String inputDigest(
            String datasetId,
            String chunkingProfile,
            List<InputChunk> chunks,
            List<InputQuestion> questions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, datasetId);
            updateDigest(digest, chunkingProfile);
            for (InputChunk chunk : chunks) {
                updateDigest(digest, chunk.fixtureChunkId());
                updateDigest(digest, chunk.contentSha256());
            }
            for (InputQuestion question : questions) {
                updateDigest(digest, question.questionId());
                updateDigest(digest, question.querySha256());
            }
            return HexFormat.of().formatHex(digest.digest());
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static void requireFiniteNonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new SearchEvaluationDataException(
                    "P14 sparse " + field + " must be finite and non-negative.");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    record RawChunk(String fixtureChunkId, String content) {
    }

    record RawQuestion(String questionId, String query) {
    }

    record InputChunk(String fixtureChunkId, String content, String contentSha256) {
    }

    record InputQuestion(String questionId, String query, String querySha256) {
    }

    record PreparedInput(
            int schemaVersion,
            String datasetId,
            String chunkingProfile,
            String inputDigest,
            List<InputChunk> chunks,
            List<InputQuestion> questions) {
    }

    record SparseRank(int rank, String fixtureChunkId, double sparseScore) {
    }

    record SparseQuestion(
            String questionId,
            String querySha256,
            double queryEncodingMillis,
            double scoringMillis,
            Map<String, Double> queryLexicalWeights,
            List<SparseRank> candidates) {

        double totalMillis() {
            return queryEncodingMillis + scoringMillis;
        }
    }

    record SparseOutput(
            int schemaVersion,
            String generatedAt,
            String datasetId,
            String chunkingProfile,
            String inputDigest,
            String model,
            String modelRevision,
            String flagEmbeddingVersion,
            String device,
            boolean useFp16,
            int maxLength,
            int branchLimit,
            int chunkCount,
            int questionCount,
            double modelLoadMillis,
            double corpusEncodingMillis,
            double warmupMillis,
            long gpuPeakMemoryBytes,
            List<SparseQuestion> questions) {
    }

    record SparseRun(
            String model,
            String modelRevision,
            String flagEmbeddingVersion,
            String device,
            boolean useFp16,
            double modelLoadMillis,
            double corpusEncodingMillis,
            double warmupMillis,
            long gpuPeakMemoryBytes,
            Map<String, SparseQuestion> questionsById) {

        SparseQuestion question(String questionId) {
            SparseQuestion question = questionsById.get(questionId);
            if (question == null) {
                throw new SearchEvaluationDataException(
                        "P14 sparse output is missing question " + questionId + ".");
            }
            return question;
        }
    }
}
