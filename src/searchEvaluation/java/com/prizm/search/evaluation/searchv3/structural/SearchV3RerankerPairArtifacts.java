package com.prizm.search.evaluation.searchv3.structural;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

/** Gold-free R0 Top20 inference input and strict R1 score artifact boundary. */
final class SearchV3RerankerPairArtifacts {

    static final int SCHEMA_VERSION = 1;
    static final int TOP_K = 20;
    static final int MAX_LENGTH = 512;
    static final int BATCH_SIZE = 8;
    static final int CPU_THREADS = 8;
    static final String PROFILE = "PRZ027_B3_TOP20_GTE_MULTILINGUAL_RERANKER_BASE";
    static final String MODEL = "Alibaba-NLP/gte-multilingual-reranker-base";
    static final String MODEL_REVISION = "8215cf04918ba6f7b6a62bb44238ce2953d8831c";
    static final String CODE_REPOSITORY = "Alibaba-NLP/new-impl";
    static final String CODE_REVISION = "40ced75c3017eb27626c9d4ea981bde21a2662f4";
    static final String LICENSE = "apache-2.0";
    static final String TRANSFORMERS_VERSION = "4.39.1";
    static final String PYTHON_VERSION = "3.12.13";
    static final String TORCH_VERSION = "2.9.0+cpu";
    static final String PSUTIL_VERSION = "5.9.8";
    static final long MODEL_PARAMETER_COUNT = 305_959_681L;
    static final long MODEL_WEIGHT_BYTES = 611_934_706L;
    static final String MODEL_WEIGHT_SHA256 =
            "10ebaa49322dd7e01a13a91c49810939e3f91f231aceaa47fdf0cab3083954f6";
    static final String CONFIG_SHA256 =
            "995730781d157e147c13ccdfe0eb20a0875c486b6c4de8c97f0bbd845549dbc0";
    static final String REMOTE_CONFIGURATION_SHA256 =
            "3411088045ffb8a9a0aa9936eae275896b39983a2ee5b08f091b44e6289e4fe4";
    static final String REMOTE_MODELING_SHA256 =
            "374670b416fcc82f081c9cd28b5fd61c2bd91bbe18eb4798fcc48a81f9c250a0";

    private final ObjectMapper objectMapper = new ObjectMapper();

    PreparedInput prepare(List<DatasetRun> runs) {
        if (runs.isEmpty()) {
            throw new IllegalArgumentException("PRZ-027 requires frozen DEV/CAL dataset runs");
        }
        List<PreparedDataset> datasets = new ArrayList<>();
        Set<String> datasetVersions = new LinkedHashSet<>();
        Set<String> questionKeys = new HashSet<>();
        Set<String> pairIds = new HashSet<>();
        for (DatasetRun run : runs) {
            if (!datasetVersions.add(run.report().datasetVersion())) {
                throw new IllegalArgumentException("Duplicate dataset version in PRZ-027 export");
            }
            Map<String, SearchV3DenseAblationDataset.Query> queries = run.slices().stream()
                    .flatMap(slice -> slice.queries().stream())
                    .collect(Collectors.toMap(
                            SearchV3DenseAblationDataset.Query::queryId,
                            query -> query,
                            (left, right) -> {
                                throw new IllegalArgumentException("Duplicate query ID in dataset slices");
                            },
                            LinkedHashMap::new));
            List<PreparedQuestion> preparedQuestions = new ArrayList<>();
            for (SearchV3DenseAblationEngine.QueryResult result : run.report().queries()) {
                SearchV3DenseAblationDataset.Query query = queries.get(result.queryId());
                if (query == null) {
                    throw new IllegalArgumentException("B3 report query is absent from frozen dataset");
                }
                String questionKey = run.report().datasetVersion() + ":" + result.queryId();
                if (!questionKeys.add(questionKey)) {
                    throw new IllegalArgumentException("Duplicate prepared question identity");
                }
                List<SearchV3DenseAblationEngine.RankedCandidate> fullRanking =
                        result.passage().rawDenseRanking();
                if (fullRanking.size() != result.passage().candidateCount()) {
                    throw new IllegalArgumentException("B3 raw ranking must contain the full owner candidate set");
                }
                String querySha = sha256(query.text());
                List<PreparedPair> pairs = new ArrayList<>();
                for (SearchV3DenseAblationEngine.RankedCandidate candidate :
                        fullRanking.stream().limit(TOP_K).toList()) {
                    String sourceSha = sha256(candidate.sourceText());
                    String provenanceSha = provenanceSha(candidate);
                    String pairId = sha256(String.join("\n",
                            run.report().datasetVersion(),
                            result.split().manifestName(),
                            result.queryId(),
                            candidate.candidateId(),
                            querySha,
                            sourceSha,
                            provenanceSha));
                    if (!pairIds.add(pairId)) {
                        throw new IllegalArgumentException("Duplicate deterministic reranker pair ID");
                    }
                    pairs.add(new PreparedPair(
                            pairId,
                            candidate.rank(),
                            candidate.candidateId(),
                            querySha,
                            sourceSha,
                            provenanceSha,
                            candidate.documentId(),
                            candidate.versionId(),
                            query.text(),
                            candidate.sourceText()));
                }
                preparedQuestions.add(new PreparedQuestion(
                        result.queryId(),
                        result.split().manifestName(),
                        querySha,
                        query.text(),
                        fullRanking.size(),
                        pairs.size(),
                        List.copyOf(pairs)));
            }
            if (!queries.keySet().equals(preparedQuestions.stream()
                    .map(PreparedQuestion::questionId)
                    .collect(Collectors.toSet()))) {
                throw new IllegalArgumentException("Prepared input must contain every frozen dataset question");
            }
            datasets.add(new PreparedDataset(
                    run.label(),
                    run.report().datasetVersion(),
                    run.report().splitManifestHashes(),
                    List.copyOf(preparedQuestions)));
        }
        String digest = inputDigest(datasets);
        PreparedInput prepared = new PreparedInput(
                SCHEMA_VERSION,
                PROFILE,
                TOP_K,
                MAX_LENGTH,
                BATCH_SIZE,
                CPU_THREADS,
                MODEL,
                MODEL_REVISION,
                CODE_REPOSITORY,
                CODE_REVISION,
                LICENSE,
                TRANSFORMERS_VERSION,
                "ORIGINAL_QUERY_AND_B3_SOURCE_TEXT_NO_INSTRUCTION",
                "GOLD_NOT_PRESENT",
                digest,
                List.copyOf(datasets));
        validatePrepared(prepared);
        return prepared;
    }

    void writePrepared(Path path, PreparedInput value) {
        validatePrepared(value);
        write(path, value);
    }

    PreparedInput readPrepared(Path path) {
        PreparedInput value = read(path, PreparedInput.class);
        validatePrepared(value);
        return value;
    }

    void writeBaseline(Path path, BaselineBundle value) {
        validateBaseline(value);
        write(path, value);
    }

    BaselineBundle readBaseline(Path path) {
        BaselineBundle value = read(path, BaselineBundle.class);
        validateBaseline(value);
        return value;
    }

    ScoreOutput readScores(Path scorePath, Path preparedPath, PreparedInput expected) {
        ScoreOutput output = read(scorePath, ScoreOutput.class);
        validateScores(output, expected, sha256File(preparedPath));
        return output;
    }

    void validatePrepared(PreparedInput input) {
        if (input == null
                || input.schemaVersion() != SCHEMA_VERSION
                || !PROFILE.equals(input.profile())
                || input.topK() != TOP_K
                || input.maxLength() != MAX_LENGTH
                || input.batchSize() != BATCH_SIZE
                || input.cpuThreads() != CPU_THREADS
                || !MODEL.equals(input.model())
                || !MODEL_REVISION.equals(input.modelRevision())
                || !CODE_REPOSITORY.equals(input.codeRepository())
                || !CODE_REVISION.equals(input.codeRevision())
                || !LICENSE.equals(input.license())
                || !TRANSFORMERS_VERSION.equals(input.transformersVersion())
                || !"ORIGINAL_QUERY_AND_B3_SOURCE_TEXT_NO_INSTRUCTION".equals(input.pairPolicy())
                || !"GOLD_NOT_PRESENT".equals(input.goldPolicy())
                || input.datasets() == null
                || input.datasets().isEmpty()) {
            throw new IllegalArgumentException("Prepared reranker input metadata changed");
        }
        Set<String> datasetVersions = new HashSet<>();
        Set<String> questionKeys = new HashSet<>();
        Set<String> pairIds = new HashSet<>();
        for (PreparedDataset dataset : input.datasets()) {
            if (isBlank(dataset.label())
                    || isBlank(dataset.datasetVersion())
                    || !datasetVersions.add(dataset.datasetVersion())
                    || dataset.splitManifestHashes() == null
                    || dataset.questions() == null
                    || dataset.questions().isEmpty()) {
                throw new IllegalArgumentException("Invalid prepared dataset identity");
            }
            for (PreparedQuestion question : dataset.questions()) {
                String key = dataset.datasetVersion() + ":" + question.questionId();
                if (!questionKeys.add(key)
                        || isBlank(question.questionId())
                        || isBlank(question.split())
                        || isBlank(question.query())
                        || !sha256(question.query()).equals(question.querySha256())
                        || question.fullCandidateCount() < 1
                        || question.pairCount() != Math.min(TOP_K, question.fullCandidateCount())
                        || question.pairs() == null
                        || question.pairs().size() != question.pairCount()) {
                    throw new IllegalArgumentException("Invalid prepared question identity or Top20 cutoff");
                }
                Set<String> candidates = new HashSet<>();
                for (int index = 0; index < question.pairs().size(); index++) {
                    PreparedPair pair = question.pairs().get(index);
                    if (pair.denseRank() != index + 1
                            || !pairIds.add(pair.pairId())
                            || !candidates.add(pair.candidateId())
                            || !question.query().equals(pair.query())
                            || !question.querySha256().equals(pair.querySha256())
                            || !sha256(pair.sourceText()).equals(pair.sourceSha256())
                            || isBlank(pair.provenanceSha256())
                            || isBlank(pair.documentId())
                            || isBlank(pair.versionId())) {
                        throw new IllegalArgumentException("Invalid, duplicate, or non-contiguous prepared pair");
                    }
                }
            }
        }
        if (!inputDigest(input.datasets()).equals(input.inputDigest())) {
            throw new IllegalArgumentException("Prepared reranker input digest mismatch");
        }
    }

    void validateScores(ScoreOutput output, PreparedInput expected, String preparedFileSha256) {
        if (output == null
                || output.schemaVersion() != SCHEMA_VERSION
                || !expected.inputDigest().equals(output.inputDigest())
                || !preparedFileSha256.equals(output.inputSha256())
                || !MODEL.equals(output.model())
                || !MODEL_REVISION.equals(output.modelRevision())
                || !CODE_REPOSITORY.equals(output.codeRepository())
                || !CODE_REVISION.equals(output.codeRevision())
                || !LICENSE.equals(output.license())
                || !TRANSFORMERS_VERSION.equals(output.transformersVersion())
                || !PYTHON_VERSION.equals(output.pythonVersion())
                || !TORCH_VERSION.equals(output.torchVersion())
                || !PSUTIL_VERSION.equals(output.psutilVersion())
                || !"cpu".equals(output.device())
                || !"float32".equals(output.dtype())
                || output.topK() != TOP_K
                || output.maxLength() != MAX_LENGTH
                || output.batchSize() != BATCH_SIZE
                || output.cpuThreads() != CPU_THREADS
                || output.modelParameterCount() != MODEL_PARAMETER_COUNT
                || output.modelWeightBytes() != MODEL_WEIGHT_BYTES
                || output.modelCacheBytes() < MODEL_WEIGHT_BYTES
                || !MODEL_WEIGHT_SHA256.equals(output.modelWeightSha256())
                || !CONFIG_SHA256.equals(output.configSha256())
                || !REMOTE_CONFIGURATION_SHA256.equals(output.remoteConfigurationSha256())
                || !REMOTE_MODELING_SHA256.equals(output.remoteModelingSha256())
                || output.questions() == null) {
            throw new IllegalArgumentException("Reranker score metadata does not match the frozen contract");
        }
        requireFiniteNonNegative(output.modelLoadMillis(), "modelLoadMillis");
        requireFiniteNonNegative(output.warmupMillis(), "warmupMillis");
        requireNonNegative(output.processRssBeforeLoadBytes(), "processRssBeforeLoadBytes");
        requireNonNegative(output.processRssAfterLoadBytes(), "processRssAfterLoadBytes");
        requireNonNegative(output.processRssPeakBytes(), "processRssPeakBytes");
        if (output.gpuUsed() || output.gpuPeakAllocatedBytes() != 0L || output.gpuPeakReservedBytes() != 0L) {
            throw new IllegalArgumentException("PRZ-027 frozen baseline is CPU-only");
        }

        Map<String, PreparedQuestion> expectedQuestions = expected.datasets().stream()
                .flatMap(dataset -> dataset.questions().stream()
                        .map(question -> Map.entry(questionKey(dataset.datasetVersion(), question.questionId()), question)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Set<String> seenQuestions = new HashSet<>();
        for (ScoreQuestion question : output.questions()) {
            String key = questionKey(question.datasetVersion(), question.questionId());
            PreparedQuestion prepared = expectedQuestions.get(key);
            if (prepared == null
                    || !seenQuestions.add(key)
                    || !prepared.split().equals(question.split())
                    || !prepared.querySha256().equals(question.querySha256())
                    || question.pairCount() != prepared.pairCount()
                    || question.pairs() == null
                    || question.pairs().size() != prepared.pairs().size()) {
                throw new IllegalArgumentException("Missing, duplicate, or unknown reranker question");
            }
            requireFiniteNonNegative(question.rerankMillis(), "rerankMillis");
            Map<String, PreparedPair> preparedPairs = prepared.pairs().stream()
                    .collect(Collectors.toMap(PreparedPair::pairId, pair -> pair));
            Set<String> seenPairs = new HashSet<>();
            ScorePair previous = null;
            for (int index = 0; index < question.pairs().size(); index++) {
                ScorePair pair = question.pairs().get(index);
                PreparedPair source = preparedPairs.get(pair.pairId());
                if (source == null
                        || !seenPairs.add(pair.pairId())
                        || pair.rerankerRank() != index + 1
                        || pair.denseRank() != source.denseRank()
                        || !pair.candidateId().equals(source.candidateId())
                        || !pair.querySha256().equals(source.querySha256())
                        || !pair.sourceSha256().equals(source.sourceSha256())
                        || !Double.isFinite(pair.score())
                        || (previous != null && compareScores(previous, pair) > 0)) {
                    throw new IllegalArgumentException("Reranker score pair identity/order is invalid");
                }
                previous = pair;
            }
            if (!seenPairs.equals(preparedPairs.keySet())) {
                throw new IllegalArgumentException("Reranker output must preserve every Top20 pair");
            }
        }
        if (!seenQuestions.equals(expectedQuestions.keySet())) {
            throw new IllegalArgumentException("Reranker output must contain every prepared question");
        }
    }

    private int compareScores(ScorePair left, ScorePair right) {
        return Comparator.comparingDouble(ScorePair::score).reversed()
                .thenComparingInt(ScorePair::denseRank)
                .thenComparing(ScorePair::candidateId)
                .compare(left, right);
    }

    private void validateBaseline(BaselineBundle baseline) {
        if (baseline == null
                || baseline.schemaVersion() != SCHEMA_VERSION
                || isBlank(baseline.inputFreezeCommit())
                || baseline.frozenB3SourceSha256() == null
                || baseline.frozenB3SourceSha256().size() < 5
                || baseline.frozenB3SourceSha256().entrySet().stream()
                        .anyMatch(entry -> isBlank(entry.getKey()) || isBlank(entry.getValue()))
                || baseline.datasets() == null
                || baseline.datasets().isEmpty()) {
            throw new IllegalArgumentException("Invalid PRZ-027 B3 baseline bundle");
        }
        Set<String> versions = new HashSet<>();
        for (BaselineDataset dataset : baseline.datasets()) {
            if (isBlank(dataset.label())
                    || dataset.report() == null
                    || !versions.add(dataset.report().datasetVersion())
                    || dataset.report().sealedFinalOpened()
                    || dataset.report().sealedFinalSearchExecuted()
                    || !"NOT_RUN".equals(dataset.report().currentFreshBaseline())
                    || dataset.report().passageCorpus().contaminationRate() != 0.0d
                    || dataset.report().passageCorpus().fragmentationRate() != 0.0d
                    || dataset.report().passageStats().crossParentPassageViolationCount() != 0L
                    || dataset.report().passageStats().directGoldEvidenceChildPreservationRate() != 1.0d) {
                throw new IllegalArgumentException("B3 baseline Safety or SEALED guard changed");
            }
        }
    }

    static String sha256File(Path path) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot hash reranker artifact", exception);
        }
    }

    static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    static String questionKey(String datasetVersion, String questionId) {
        return datasetVersion + ":" + questionId;
    }

    private String provenanceSha(SearchV3DenseAblationEngine.RankedCandidate candidate) {
        return sha256(String.join("\n",
                candidate.candidateId(),
                candidate.documentId(),
                candidate.versionId(),
                Objects.toString(candidate.parentAnnotationCandidateId(), ""),
                String.join("|", candidate.evidenceChildIds()),
                String.join("|", candidate.contextSourceBlockIds()),
                candidate.sourceText()));
    }

    String inputDigest(List<PreparedDataset> datasets) {
        return sha256(datasets.stream()
                .flatMap(dataset -> dataset.questions().stream()
                        .flatMap(question -> question.pairs().stream()
                                .map(pair -> String.join(":",
                                        dataset.datasetVersion(),
                                        question.split(),
                                        question.questionId(),
                                        pair.pairId(),
                                        Integer.toString(pair.denseRank()),
                                        pair.candidateId(),
                                        pair.querySha256(),
                                        pair.sourceSha256(),
                                        pair.provenanceSha256()))))
                .collect(Collectors.joining("\n")));
    }

    private <T> T read(Path path, Class<T> type) {
        try {
            return objectMapper.readValue(Files.readString(path, StandardCharsets.UTF_8), type);
        } catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read PRZ-027 artifact " + path, exception);
        }
    }

    private void write(Path path, Object value) {
        try {
            Files.createDirectories(path.toAbsolutePath().normalize().getParent());
            Files.writeString(
                    path,
                    objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n",
                    StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot write PRZ-027 artifact " + path, exception);
        }
    }

    private void requireFiniteNonNegative(double value, String label) {
        if (!Double.isFinite(value) || value < 0.0d) {
            throw new IllegalArgumentException(label + " must be finite and non-negative");
        }
    }

    private void requireNonNegative(long value, String label) {
        if (value < 0L) {
            throw new IllegalArgumentException(label + " must be non-negative");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    record DatasetRun(
            String label,
            List<SearchV3DenseAblationDataset.DatasetSlice> slices,
            SearchV3DenseAblationEngine.ExperimentReport report) {
    }

    record PreparedInput(
            int schemaVersion,
            String profile,
            int topK,
            int maxLength,
            int batchSize,
            int cpuThreads,
            String model,
            String modelRevision,
            String codeRepository,
            String codeRevision,
            String license,
            String transformersVersion,
            String pairPolicy,
            String goldPolicy,
            String inputDigest,
            List<PreparedDataset> datasets) {
    }

    record PreparedDataset(
            String label,
            String datasetVersion,
            Map<String, String> splitManifestHashes,
            List<PreparedQuestion> questions) {
    }

    record PreparedQuestion(
            String questionId,
            String split,
            String querySha256,
            String query,
            int fullCandidateCount,
            int pairCount,
            List<PreparedPair> pairs) {
    }

    record PreparedPair(
            String pairId,
            int denseRank,
            String candidateId,
            String querySha256,
            String sourceSha256,
            String provenanceSha256,
            String documentId,
            String versionId,
            String query,
            String sourceText) {
    }

    record BaselineBundle(
            int schemaVersion,
            String inputFreezeCommit,
            Map<String, String> frozenB3SourceSha256,
            List<BaselineDataset> datasets) {
    }

    record BaselineDataset(String label, SearchV3DenseAblationEngine.ExperimentReport report) {
    }

    record ScoreOutput(
            int schemaVersion,
            String generatedAt,
            String inputDigest,
            String inputSha256,
            String model,
            String modelRevision,
            String codeRepository,
            String codeRevision,
            String license,
            String transformersVersion,
            String torchVersion,
            String psutilVersion,
            String pythonVersion,
            String device,
            String dtype,
            int topK,
            int maxLength,
            int batchSize,
            int cpuThreads,
            long modelParameterCount,
            long modelWeightBytes,
            long modelCacheBytes,
            String modelWeightSha256,
            String configSha256,
            String remoteConfigurationSha256,
            String remoteModelingSha256,
            double modelLoadMillis,
            double warmupMillis,
            long processRssBeforeLoadBytes,
            long processRssAfterLoadBytes,
            long processRssPeakBytes,
            boolean gpuUsed,
            long gpuPeakAllocatedBytes,
            long gpuPeakReservedBytes,
            List<ScoreQuestion> questions) {
    }

    record ScoreQuestion(
            String datasetVersion,
            String split,
            String questionId,
            String querySha256,
            int pairCount,
            double rerankMillis,
            List<ScorePair> pairs) {
    }

    record ScorePair(
            String pairId,
            String candidateId,
            int denseRank,
            int rerankerRank,
            String querySha256,
            String sourceSha256,
            double score) {
    }
}
