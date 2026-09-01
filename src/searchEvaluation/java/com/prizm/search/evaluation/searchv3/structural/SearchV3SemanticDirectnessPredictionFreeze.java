package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.CandidateProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvaluationTrack;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.FreezeInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.FrozenCandidates;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.QueryProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.VerifiedCandidates;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Evaluation-only freeze and phase guard for PRZ-031 semantic directness predictions.
 *
 * <p>The frozen model payload is exactly the original query and candidate source text. Gold,
 * answerability, categories, Oracle output, and typed-query annotations cannot be represented by
 * this schema. The guard keeps the verified source-candidate token private until every prediction
 * is frozen and verified.
 */
final class SearchV3SemanticDirectnessPredictionFreeze {

    static final int SCHEMA_VERSION = 2;
    static final int SEMANTIC_QUERY_COUNT = 79;
    static final int SEMANTIC_CANDIDATE_COUNT = 670;
    static final int INFERENCE_TOP_K = 10;
    static final int INFERENCE_PAIR_COUNT = 578;
    static final int TYPED_QUERY_COUNT = 0;

    private static final String COMBINED_SUITE = "PRZ031_SEMANTIC_DIRECTNESS_INPUT";
    private static final String COMBINED_VERSION = "PRZ031_SEMANTIC_DIRECTNESS_INPUT_V2";
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final List<ExpectedSuite> EXPECTED_SUITES = List.of(
            new ExpectedSuite(
                    "originalSeed",
                    "search-v3-fresh-seed-1.0.1",
                    "fe69d2cbbc3d679b49e449d5d2b7a4c7387069d3d0b29b43df8772dc76be6d79",
                    14,
                    42,
                    42),
            new ExpectedSuite(
                    "longFormExpansion",
                    "search-v3-fresh-devcal-1.1.0",
                    "0935f6eeaad188005011d25374f012b66e843f34b7653a1ec981645a4e182570",
                    18,
                    232,
                    172),
            new ExpectedSuite(
                    "independentRobustness",
                    "search-v3-fresh-devcal-robustness-1.0.0",
                    "20346aea334c7cb662dd459b7ca5b8e44a3a4dffa4382006f892c0c99fd0fba9",
                    23,
                    196,
                    180),
            new ExpectedSuite(
                    "PRZ030_SEMANTIC_SUPPORT_STRESS",
                    "semantic-support-stress-1.0.1",
                    "ee3142abfe2097799f03998cb6b7acfd35ebc0c70a58618c43c33cd8ab709da8",
                    24,
                    200,
                    184));

    private SearchV3SemanticDirectnessPredictionFreeze() {
    }

    static List<SourceSuite> expectedSourceSuites() {
        return EXPECTED_SUITES.stream().map(ExpectedSuite::source).toList();
    }

    static String sha256(String value) {
        Objects.requireNonNull(value, "value");
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    static FrozenInput freezeInput(Input input) {
        NormalizedInput normalized = normalizeInput(input);
        byte[] canonical = inputCanonicalBytes(normalized.input(), normalized.candidateFreeze());
        return new FrozenInput(
                normalized.input(),
                normalized.candidateFreeze(),
                contractSha256(normalized.input().contract()),
                sha256(canonical),
                canonical.length);
    }

    static InputVerification verifyInput(FrozenInput frozen) {
        return verifyInputState(frozen).verification();
    }

    static FrozenOutput freezeOutput(FrozenInput input, Output output) {
        VerifiedInputState verifiedInput = verifyInputState(input);
        NormalizedOutput normalized = normalizeOutput(verifiedInput, output);
        byte[] canonical = outputCanonicalBytes(normalized.output());
        return new FrozenOutput(normalized.output(), sha256(canonical), canonical.length);
    }

    static OutputVerification verifyOutput(FrozenInput input, FrozenOutput output) {
        VerifiedInputState verifiedInput = verifyInputState(input);
        return verifyOutputState(verifiedInput, output).verification();
    }

    private static VerifiedInputState verifyInputState(FrozenInput frozen) {
        Objects.requireNonNull(frozen, "frozen");
        NormalizedInput normalized = normalizeInput(frozen.input());
        VerifiedCandidates verifiedCandidates = SearchV3CandidateFreeze.verify(frozen.candidateFreeze());
        if (!verifiedCandidates.frozen().equals(normalized.candidateFreeze())) {
            throw new IllegalStateException("directness candidate freeze identity changed");
        }
        String actualContract = contractSha256(normalized.input().contract());
        byte[] canonical = inputCanonicalBytes(normalized.input(), normalized.candidateFreeze());
        String actual = sha256(canonical);
        if (!actualContract.equals(frozen.contractSha256())
                || !actual.equals(frozen.canonicalSha256())
                || canonical.length != frozen.canonicalByteLength()) {
            throw new IllegalStateException("directness input freeze hash or canonical length mismatch");
        }
        InputVerification verification = new InputVerification(
                actual,
                actualContract,
                normalized.input().queries().size(),
                candidateCount(normalized.input().queries()),
                inferencePairCount(normalized.input().queries()),
                TYPED_QUERY_COUNT);
        return new VerifiedInputState(normalized.input(), verifiedCandidates, verification);
    }

    private static VerifiedOutputState verifyOutputState(
            VerifiedInputState verifiedInput,
            FrozenOutput frozen) {
        Objects.requireNonNull(frozen, "frozen output");
        NormalizedOutput normalized = normalizeOutput(verifiedInput, frozen.output());
        byte[] canonical = outputCanonicalBytes(normalized.output());
        String actual = sha256(canonical);
        if (!actual.equals(frozen.canonicalSha256())
                || canonical.length != frozen.canonicalByteLength()) {
            throw new IllegalStateException("directness output freeze hash or canonical length mismatch");
        }
        return new VerifiedOutputState(
                normalized.output(),
                new OutputVerification(
                        actual,
                        normalized.output().inputSha256(),
                        normalized.output().contractSha256(),
                        normalized.output().predictions().size()));
    }

    private static NormalizedInput normalizeInput(Input input) {
        Objects.requireNonNull(input, "input");
        if (input.schemaVersion() != SCHEMA_VERSION) {
            throw new IllegalArgumentException("directness input schema version mismatch");
        }
        validateContract(input.contract());
        List<SourceSuite> sources = validateSources(input.sourceSuites());
        Map<String, ExpectedSuite> expectedByName = EXPECTED_SUITES.stream().collect(
                LinkedHashMap::new,
                (map, value) -> map.put(value.suite(), value),
                Map::putAll);

        List<QueryInput> queries = new ArrayList<>(input.queries());
        queries.sort(Comparator.comparingInt((QueryInput value) -> suiteOrder(value.suite()))
                .thenComparingInt(value -> splitOrder(value.split()))
                .thenComparing(QueryInput::queryId));
        if (queries.size() != SEMANTIC_QUERY_COUNT) {
            throw new IllegalArgumentException("PRZ-031 requires exactly 79 semantic queries");
        }

        Set<String> queryIds = new HashSet<>();
        Map<String, MutableSuiteCounts> counts = new HashMap<>();
        List<QueryProjection> candidateQueries = new ArrayList<>();
        for (QueryInput query : queries) {
            validateQuery(query, expectedByName);
            if (!queryIds.add(query.queryId())) {
                throw new IllegalArgumentException("duplicate directness queryId: " + query.queryId());
            }
            MutableSuiteCounts suiteCounts = counts.computeIfAbsent(
                    query.suite(), ignored -> new MutableSuiteCounts());
            suiteCounts.queries++;
            suiteCounts.candidates += query.rankedCandidates().size();
            suiteCounts.pairs += Math.min(INFERENCE_TOP_K, query.rankedCandidates().size());
            candidateQueries.add(new QueryProjection(
                    query.queryId(),
                    query.userBundleId(),
                    query.split(),
                    query.track(),
                    query.rankedCandidates()));
        }
        for (ExpectedSuite expected : EXPECTED_SUITES) {
            MutableSuiteCounts actual = counts.get(expected.suite());
            if (actual == null
                    || actual.queries != expected.semanticQueries()
                    || actual.candidates != expected.semanticCandidates()
                    || actual.pairs != expected.top10Pairs()) {
                throw new IllegalArgumentException(
                        "PRZ-031 semantic suite inventory mismatch: " + expected.suite());
            }
        }
        if (candidateCount(queries) != SEMANTIC_CANDIDATE_COUNT
                || inferencePairCount(queries) != INFERENCE_PAIR_COUNT) {
            throw new IllegalArgumentException("PRZ-031 combined semantic candidate inventory mismatch");
        }

        String sourceInventorySha256 = sourceInventorySha256(sources);
        FrozenCandidates candidateFreeze = SearchV3CandidateFreeze.freeze(new FreezeInput(
                SearchV3CandidateFreeze.SCHEMA_VERSION,
                COMBINED_SUITE,
                COMBINED_VERSION,
                sourceInventorySha256,
                EvaluationTrack.SEMANTIC,
                candidateQueries));
        Input normalized = new Input(
                SCHEMA_VERSION,
                input.contract(),
                sources,
                List.copyOf(queries));
        return new NormalizedInput(normalized, candidateFreeze);
    }

    private static void validateContract(RunContract contract) {
        Objects.requireNonNull(contract, "contract");
        requireNonBlank(contract.modelId(), "modelId");
        requireNonBlank(contract.modelRevision(), "modelRevision");
        requireNonBlank(contract.modelLicense(), "modelLicense");
        requireSha(contract.modelArtifactSha256(), "modelArtifactSha256");
        requireFrozenText(contract.instruction(), contract.instructionSha256(), "instruction");
        requireFrozenText(contract.outputSchema(), contract.outputSchemaSha256(), "outputSchema");
        requireFrozenText(contract.config(), contract.configSha256(), "config");
        requireFrozenText(contract.policy(), contract.policySha256(), "policy");
        if (contract.modelSizeBytes() <= 0 || contract.inferenceTopK() != INFERENCE_TOP_K) {
            throw new IllegalArgumentException("model size or directness TopK contract is invalid");
        }
    }

    private static List<SourceSuite> validateSources(List<SourceSuite> values) {
        if (values == null || values.size() != EXPECTED_SUITES.size()) {
            throw new IllegalArgumentException("PRZ-031 requires four frozen source suites");
        }
        Map<String, SourceSuite> byName = new LinkedHashMap<>();
        for (SourceSuite value : values) {
            Objects.requireNonNull(value, "source suite");
            requireNonBlank(value.suite(), "source suite name");
            requireNonBlank(value.datasetVersion(), "source dataset version");
            requireSha(value.candidateFreezeSha256(), "source candidate freeze SHA-256");
            if (byName.put(value.suite(), value) != null) {
                throw new IllegalArgumentException("duplicate source suite: " + value.suite());
            }
        }
        List<SourceSuite> normalized = new ArrayList<>();
        for (ExpectedSuite expected : EXPECTED_SUITES) {
            SourceSuite actual = byName.remove(expected.suite());
            if (!expected.source().equals(actual)) {
                throw new IllegalArgumentException("source candidate freeze drifted: " + expected.suite());
            }
            normalized.add(actual);
        }
        if (!byName.isEmpty()) {
            throw new IllegalArgumentException("unapproved source candidate suite");
        }
        return List.copyOf(normalized);
    }

    private static void validateQuery(QueryInput query, Map<String, ExpectedSuite> expectedByName) {
        Objects.requireNonNull(query, "query");
        ExpectedSuite expected = expectedByName.get(query.suite());
        if (expected == null || !expected.datasetVersion().equals(query.datasetVersion())) {
            throw new IllegalArgumentException("query source suite/version is invalid: " + query.queryId());
        }
        requireNonBlank(query.queryId(), "queryId");
        requireNonBlank(query.userBundleId(), "query userBundleId");
        requireNonBlank(query.split(), "query split");
        requireNonBlank(query.language(), "query language");
        requireNonBlank(query.originalQuery(), "original query");
        requireSha(query.originalQuerySha256(), "original query SHA-256");
        if (!query.originalQuerySha256().equals(sha256(query.originalQuery()))) {
            throw new IllegalArgumentException("original query hash mismatch: " + query.queryId());
        }
        if (query.track() != EvaluationTrack.SEMANTIC) {
            throw new IllegalArgumentException("typed query is forbidden in PRZ-031 inference input");
        }
        if (!("DEV".equals(query.split()) || "CALIBRATION".equals(query.split()))) {
            throw new IllegalArgumentException("only DEV/CALIBRATION are valid directness splits");
        }
        if (query.rankedCandidates().isEmpty()
                || query.rankedCandidates().size() > SearchV3CandidateFreeze.MAX_CANDIDATES_PER_QUERY) {
            throw new IllegalArgumentException("directness query candidate inventory is invalid");
        }
    }

    private static NormalizedOutput normalizeOutput(VerifiedInputState verifiedInput, Output output) {
        Objects.requireNonNull(output, "output");
        if (output.schemaVersion() != SCHEMA_VERSION
                || !verifiedInput.verification().canonicalSha256().equals(output.inputSha256())
                || !verifiedInput.verification().contractSha256().equals(output.contractSha256())) {
            throw new IllegalArgumentException("prediction output/input/contract identity mismatch");
        }
        LinkedHashMap<PairKey, ExpectedPair> expected = expectedPairs(verifiedInput.input());
        if (output.predictions().size() != INFERENCE_PAIR_COUNT) {
            throw new IllegalArgumentException("PRZ-031 requires exactly 578 prediction rows");
        }
        Map<PairKey, Prediction> observed = new HashMap<>();
        for (Prediction prediction : output.predictions()) {
            Objects.requireNonNull(prediction, "prediction");
            requireNonBlank(prediction.queryId(), "prediction queryId");
            requireNonBlank(prediction.candidateId(), "prediction candidateId");
            Objects.requireNonNull(prediction.relation(), "prediction relation");
            PairKey key = new PairKey(prediction.queryId(), prediction.candidateId());
            ExpectedPair expectedPair = expected.get(key);
            if (expectedPair == null || expectedPair.sourceRank() != prediction.sourceRank()) {
                throw new IllegalArgumentException("prediction references a non-Top10 input pair");
            }
            if (observed.put(key, prediction) != null) {
                throw new IllegalArgumentException("duplicate prediction pair");
            }
        }
        if (!observed.keySet().equals(expected.keySet())) {
            throw new IllegalArgumentException("prediction output omitted or added an inference pair");
        }
        List<Prediction> normalized = expected.keySet().stream().map(observed::get).toList();
        return new NormalizedOutput(new Output(
                SCHEMA_VERSION,
                output.inputSha256(),
                output.contractSha256(),
                normalized));
    }

    private static LinkedHashMap<PairKey, ExpectedPair> expectedPairs(Input input) {
        LinkedHashMap<PairKey, ExpectedPair> result = new LinkedHashMap<>();
        for (QueryInput query : input.queries()) {
            for (CandidateProjection candidate : query.rankedCandidates().stream()
                    .limit(INFERENCE_TOP_K)
                    .toList()) {
                PairKey key = new PairKey(query.queryId(), candidate.candidateId());
                ExpectedPair previous = result.put(key, new ExpectedPair(candidate.rank()));
                if (previous != null) {
                    throw new IllegalStateException("duplicate frozen inference pair");
                }
            }
        }
        if (result.size() != INFERENCE_PAIR_COUNT) {
            throw new IllegalStateException("frozen inference pair inventory changed");
        }
        return result;
    }

    private static InferenceBatch inferenceBatch(VerifiedInputState verifiedInput) {
        List<InferencePair> pairs = new ArrayList<>();
        for (QueryInput query : verifiedInput.input().queries()) {
            for (CandidateProjection candidate : query.rankedCandidates().stream()
                    .limit(INFERENCE_TOP_K)
                    .toList()) {
                pairs.add(new InferencePair(
                        query.queryId(),
                        candidate.candidateId(),
                        candidate.rank(),
                        new ModelPayload(query.originalQuery(), candidate.sourceText())));
            }
        }
        if (pairs.size() != INFERENCE_PAIR_COUNT) {
            throw new IllegalStateException("inference payload pair inventory changed");
        }
        return new InferenceBatch(
                verifiedInput.verification().canonicalSha256(),
                verifiedInput.verification().contractSha256(),
                List.copyOf(pairs));
    }

    private static String contractSha256(RunContract contract) {
        validateContract(contract);
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeString(output, "PRIZM_SEARCH_V3_DIRECTNESS_RUN_CONTRACT_V2");
                writeContract(output, contract);
            }
            return sha256(bytes.toByteArray());
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot canonicalize directness run contract", exception);
        }
    }

    private static byte[] inputCanonicalBytes(Input input, FrozenCandidates candidateFreeze) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                writeString(output, "PRIZM_SEARCH_V3_DIRECTNESS_INPUT_FREEZE_V2");
                output.writeInt(input.schemaVersion());
                writeContract(output, input.contract());
                output.writeInt(input.sourceSuites().size());
                for (SourceSuite source : input.sourceSuites()) {
                    writeString(output, source.suite());
                    writeString(output, source.datasetVersion());
                    writeString(output, source.candidateFreezeSha256());
                }
                writeString(output, candidateFreeze.canonicalSha256());
                output.writeInt(candidateFreeze.canonicalByteLength());
                output.writeInt(input.queries().size());
                for (QueryInput query : input.queries()) {
                    writeString(output, query.suite());
                    writeString(output, query.datasetVersion());
                    writeString(output, query.split());
                    writeString(output, query.queryId());
                    writeString(output, query.userBundleId());
                    writeString(output, query.language());
                    writeString(output, query.track().name());
                    writeString(output, query.originalQuery());
                    writeString(output, query.originalQuerySha256());
                }
            }
            return bytes.toByteArray();
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot canonicalize directness input", exception);
        }
    }

    private static byte[] outputCanonicalBytes(Output output) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream canonical = new DataOutputStream(bytes)) {
                writeString(canonical, "PRIZM_SEARCH_V3_DIRECTNESS_OUTPUT_FREEZE_V2");
                canonical.writeInt(output.schemaVersion());
                writeString(canonical, output.inputSha256());
                writeString(canonical, output.contractSha256());
                canonical.writeInt(output.predictions().size());
                for (Prediction prediction : output.predictions()) {
                    writeString(canonical, prediction.queryId());
                    writeString(canonical, prediction.candidateId());
                    canonical.writeInt(prediction.sourceRank());
                    writeString(canonical, prediction.relation().name());
                }
            }
            return bytes.toByteArray();
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot canonicalize directness output", exception);
        }
    }

    private static void writeContract(DataOutputStream output, RunContract contract) throws IOException {
        writeString(output, contract.modelId());
        writeString(output, contract.modelRevision());
        writeString(output, contract.modelLicense());
        output.writeLong(contract.modelSizeBytes());
        writeString(output, contract.modelArtifactSha256());
        writeString(output, contract.instruction());
        writeString(output, contract.instructionSha256());
        writeString(output, contract.outputSchema());
        writeString(output, contract.outputSchemaSha256());
        writeString(output, contract.config());
        writeString(output, contract.configSha256());
        writeString(output, contract.policy());
        writeString(output, contract.policySha256());
        output.writeInt(contract.inferenceTopK());
    }

    private static String sourceInventorySha256(List<SourceSuite> sources) {
        StringBuilder canonical = new StringBuilder("PRZ031_V2_SOURCE_CANDIDATE_FREEZES\n");
        for (SourceSuite source : sources) {
            canonical.append(source.suite()).append('\0')
                    .append(source.datasetVersion()).append('\0')
                    .append(source.candidateFreezeSha256()).append('\n');
        }
        return sha256(canonical.toString());
    }

    private static long candidateCount(List<QueryInput> queries) {
        return queries.stream().mapToLong(value -> value.rankedCandidates().size()).sum();
    }

    private static long inferencePairCount(List<QueryInput> queries) {
        return queries.stream()
                .mapToLong(value -> Math.min(INFERENCE_TOP_K, value.rankedCandidates().size()))
                .sum();
    }

    private static int suiteOrder(String suite) {
        for (int index = 0; index < EXPECTED_SUITES.size(); index++) {
            if (EXPECTED_SUITES.get(index).suite().equals(suite)) {
                return index;
            }
        }
        return Integer.MAX_VALUE;
    }

    private static int splitOrder(String split) {
        return switch (split) {
            case "DEV" -> 0;
            case "CALIBRATION" -> 1;
            default -> 2;
        };
    }

    private static void requireFrozenText(String value, String expectedHash, String label) {
        requireNonBlank(value, label);
        requireSha(expectedHash, label + "Sha256");
        if (!expectedHash.equals(sha256(value))) {
            throw new IllegalArgumentException(label + " content/hash mismatch");
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }

    private static void requireSha(String value, String label) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-256");
        }
    }

    private static void writeString(DataOutputStream output, String value) throws IOException {
        byte[] encoded = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(encoded.length);
        output.write(encoded);
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    enum Phase {
        SOURCE_ONLY,
        INPUT_FROZEN,
        INPUT_VERIFIED,
        INFERENCE_OPEN,
        OUTPUT_FROZEN,
        OUTPUT_VERIFIED,
        GOLD_JOINED
    }

    enum Relation {
        DIRECT_MATCH,
        RELATED_CONTEXT,
        QUERY_CONFLICT,
        INSUFFICIENT
    }

    record RunContract(
            String modelId,
            String modelRevision,
            String modelLicense,
            long modelSizeBytes,
            String modelArtifactSha256,
            String instruction,
            String instructionSha256,
            String outputSchema,
            String outputSchemaSha256,
            String config,
            String configSha256,
            String policy,
            String policySha256,
            int inferenceTopK) {
    }

    record SourceSuite(String suite, String datasetVersion, String candidateFreezeSha256) {
    }

    record QueryInput(
            String suite,
            String datasetVersion,
            String split,
            String queryId,
            String userBundleId,
            String language,
            EvaluationTrack track,
            String originalQuery,
            String originalQuerySha256,
            List<CandidateProjection> rankedCandidates) {

        QueryInput {
            rankedCandidates = rankedCandidates == null ? List.of() : List.copyOf(rankedCandidates);
        }
    }

    record Input(
            int schemaVersion,
            RunContract contract,
            List<SourceSuite> sourceSuites,
            List<QueryInput> queries) {

        Input {
            sourceSuites = sourceSuites == null ? List.of() : List.copyOf(sourceSuites);
            queries = queries == null ? List.of() : List.copyOf(queries);
        }
    }

    record FrozenInput(
            Input input,
            FrozenCandidates candidateFreeze,
            String contractSha256,
            String canonicalSha256,
            int canonicalByteLength) {

        FrozenInput {
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(candidateFreeze, "candidateFreeze");
            requireSha(contractSha256, "contractSha256");
            requireSha(canonicalSha256, "canonicalSha256");
            if (canonicalByteLength <= 0) {
                throw new IllegalArgumentException("canonicalByteLength must be positive");
            }
        }
    }

    record InputVerification(
            String canonicalSha256,
            String contractSha256,
            int semanticQueryCount,
            long candidateCount,
            long inferencePairCount,
            int typedQueryCount) {
    }

    record ModelPayload(String originalQuery, String sourceText) {
    }

    record InferencePair(
            String queryId,
            String candidateId,
            int sourceRank,
            ModelPayload payload) {
    }

    record InferenceBatch(
            String inputSha256,
            String contractSha256,
            List<InferencePair> pairs) {

        InferenceBatch {
            pairs = pairs == null ? List.of() : List.copyOf(pairs);
        }
    }

    record Prediction(
            String queryId,
            String candidateId,
            int sourceRank,
            Relation relation) {
    }

    record Output(
            int schemaVersion,
            String inputSha256,
            String contractSha256,
            List<Prediction> predictions) {

        Output {
            predictions = predictions == null ? List.of() : List.copyOf(predictions);
        }
    }

    record FrozenOutput(Output output, String canonicalSha256, int canonicalByteLength) {

        FrozenOutput {
            Objects.requireNonNull(output, "output");
            requireSha(canonicalSha256, "output canonicalSha256");
            if (canonicalByteLength <= 0) {
                throw new IllegalArgumentException("output canonicalByteLength must be positive");
            }
        }
    }

    record OutputVerification(
            String canonicalSha256,
            String inputSha256,
            String contractSha256,
            int predictionCount) {
    }

    record GoldJoined<T>(InputVerification input, OutputVerification output, T gold) {

        GoldJoined {
            Objects.requireNonNull(input, "input verification");
            Objects.requireNonNull(output, "output verification");
            Objects.requireNonNull(gold, "gold");
        }
    }

    @FunctionalInterface
    interface GoldSupplier<T> {
        T load(VerifiedCandidates verifiedCandidates);
    }

    static final class PhaseGuard {

        private Phase phase = Phase.SOURCE_ONLY;
        private FrozenInput frozenInput;
        private VerifiedInputState verifiedInput;
        private FrozenOutput frozenOutput;
        private VerifiedOutputState verifiedOutput;

        Phase phase() {
            return phase;
        }

        FrozenInput freezeInput(Input input) {
            requirePhase(Phase.SOURCE_ONLY, "input freeze");
            frozenInput = SearchV3SemanticDirectnessPredictionFreeze.freezeInput(input);
            phase = Phase.INPUT_FROZEN;
            return frozenInput;
        }

        InputVerification verifyInput() {
            requirePhase(Phase.INPUT_FROZEN, "input verify");
            verifiedInput = verifyInputState(frozenInput);
            phase = Phase.INPUT_VERIFIED;
            return verifiedInput.verification();
        }

        InferenceBatch openInference() {
            requirePhase(Phase.INPUT_VERIFIED, "inference open");
            InferenceBatch batch = inferenceBatch(verifiedInput);
            phase = Phase.INFERENCE_OPEN;
            return batch;
        }

        FrozenOutput freezeOutput(Output output) {
            requirePhase(Phase.INFERENCE_OPEN, "output freeze");
            NormalizedOutput normalized = normalizeOutput(verifiedInput, output);
            byte[] canonical = outputCanonicalBytes(normalized.output());
            frozenOutput = new FrozenOutput(normalized.output(), sha256(canonical), canonical.length);
            phase = Phase.OUTPUT_FROZEN;
            return frozenOutput;
        }

        OutputVerification verifyOutput() {
            requirePhase(Phase.OUTPUT_FROZEN, "output verify");
            verifiedOutput = verifyOutputState(verifiedInput, frozenOutput);
            phase = Phase.OUTPUT_VERIFIED;
            return verifiedOutput.verification();
        }

        <T> GoldJoined<T> joinGold(GoldSupplier<T> supplier) {
            Objects.requireNonNull(supplier, "Gold supplier");
            requirePhase(Phase.OUTPUT_VERIFIED, "Gold join");
            T gold = Objects.requireNonNull(
                    supplier.load(verifiedInput.verifiedCandidates()),
                    "Gold supplier returned null");
            GoldJoined<T> joined = new GoldJoined<>(
                    verifiedInput.verification(), verifiedOutput.verification(), gold);
            phase = Phase.GOLD_JOINED;
            return joined;
        }

        private void requirePhase(Phase expected, String operation) {
            if (phase != expected) {
                throw new IllegalStateException(
                        operation + " requires phase " + expected + ", actual " + phase);
            }
        }
    }

    private record ExpectedSuite(
            String suite,
            String datasetVersion,
            String candidateFreezeSha256,
            int semanticQueries,
            int semanticCandidates,
            int top10Pairs) {

        SourceSuite source() {
            return new SourceSuite(suite, datasetVersion, candidateFreezeSha256);
        }
    }

    private record NormalizedInput(Input input, FrozenCandidates candidateFreeze) {
    }

    private record VerifiedInputState(
            Input input,
            VerifiedCandidates verifiedCandidates,
            InputVerification verification) {
    }

    private record NormalizedOutput(Output output) {
    }

    private record VerifiedOutputState(Output output, OutputVerification verification) {
    }

    private record PairKey(String queryId, String candidateId) {
    }

    private record ExpectedPair(int sourceRank) {
    }

    private static final class MutableSuiteCounts {
        private int queries;
        private int candidates;
        private int pairs;
    }
}
