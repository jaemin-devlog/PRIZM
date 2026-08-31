package com.prizm.search.evaluation.searchv3.structural;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Loads the two frozen PRZ-026 B3 reports without invoking a model or exposing evaluation Gold.
 *
 * <p>The replay is deliberately independent of the current dense engine API. It accepts only a
 * frozen artifact path/root pair and emits a canonical flat projection suitable for a downstream
 * evaluation-only adapter.
 */
final class SearchV3B3CandidateReplay {

    static final String PHASE_ONE_ARTIFACT =
            "local/search-v3-evaluation/prz026/structural-retrieval-passage-b3.json";
    static final String ROBUSTNESS_ARTIFACT =
            "local/search-v3-evaluation/prz026/retrieval-passage-b3-robustness.json";
    static final String PHASE_ONE_ARTIFACT_SHA256 =
            "acc4c7e7bdae9296e7ae543ded16dde2f92ad39911df90171c6b09606fca2918";
    static final String ROBUSTNESS_ARTIFACT_SHA256 =
            "f0bf5481a572ad5e21f91916e5cd0fc6c309c50ec59e2f75ac2386433133324d";
    static final String BGE_M3_DIGEST =
            "7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab";

    private static final int REPORT_SCHEMA_VERSION = 3;
    private static final int EMBEDDING_DIMENSIONS = 1024;
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");
    private static final Set<String> CANONICAL_FIELDS = Set.of(
            "suite",
            "dataset",
            "split",
            "query",
            "owner",
            "profession",
            "language",
            "rank",
            "candidateId",
            "cosine",
            "doc",
            "version",
            "parent",
            "evidenceChildIds",
            "sourceSha256",
            "retrievalSha256");

    private final Path repositoryRoot;
    private final String phaseOneArtifactSha256;
    private final String robustnessArtifactSha256;
    private final ObjectMapper mapper = new ObjectMapper();

    SearchV3B3CandidateReplay() {
        this(
                Path.of("").toAbsolutePath().normalize(),
                PHASE_ONE_ARTIFACT_SHA256,
                ROBUSTNESS_ARTIFACT_SHA256);
    }

    SearchV3B3CandidateReplay(
            Path repositoryRoot,
            String phaseOneArtifactSha256,
            String robustnessArtifactSha256) {
        this.repositoryRoot = Objects.requireNonNull(repositoryRoot, "repositoryRoot")
                .toAbsolutePath()
                .normalize();
        this.phaseOneArtifactSha256 = requireSha256(phaseOneArtifactSha256, "phase-one artifact SHA-256");
        this.robustnessArtifactSha256 = requireSha256(robustnessArtifactSha256, "robustness artifact SHA-256");
    }

    Replay load(Path artifactPath, String rootNode) {
        Suite suite = Suite.fromRootNode(rootNode);
        Path approvedPath = approvedPath(suite);
        Path normalized = Objects.requireNonNull(artifactPath, "artifactPath")
                .toAbsolutePath()
                .normalize();
        rejectForbiddenPath(normalized);
        if (!normalized.equals(approvedPath)) {
            throw failure("artifact path/root pair is not approved: " + normalized + " / " + rootNode);
        }
        if (!Files.isRegularFile(normalized) || Files.isSymbolicLink(normalized)) {
            throw failure("approved artifact must be a regular non-symbolic file: " + normalized);
        }
        verifyRealPath(normalized);

        byte[] artifactBytes = readBytes(normalized);
        String artifactSha256 = sha256(artifactBytes);
        String expectedArtifactSha256 = suite.artifactRelativePath().equals(PHASE_ONE_ARTIFACT)
                ? phaseOneArtifactSha256
                : robustnessArtifactSha256;
        if (!expectedArtifactSha256.equals(artifactSha256)) {
            throw failure("artifact SHA-256 mismatch for " + suite.rootNode());
        }

        JsonNode root = readTree(artifactBytes);
        JsonNode report = requiredObject(root, suite.rootNode(), "artifact root");
        validateReportContract(report, suite);
        List<ReplayCandidate> candidates = projectCandidates(report, suite);
        byte[] canonicalBytes = canonicalBytes(candidates);
        return new Replay(candidates, canonicalBytes, sha256(canonicalBytes));
    }

    Replay load(Suite suite) {
        Objects.requireNonNull(suite, "suite");
        return load(approvedPath(suite), suite.rootNode());
    }

    private Path approvedPath(Suite suite) {
        return repositoryRoot.resolve(suite.artifactRelativePath()).normalize();
    }

    private void rejectForbiddenPath(Path path) {
        String portable = path.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (portable.contains("sealed")
                || portable.contains("prz029")
                || portable.contains("prz-029")) {
            throw failure("PRZ-029 and SEALED paths are forbidden: " + path);
        }
    }

    private void verifyRealPath(Path artifactPath) {
        try {
            Path realRoot = repositoryRoot.toRealPath();
            Path realArtifact = artifactPath.toRealPath();
            rejectForbiddenPath(realArtifact);
            if (!realArtifact.startsWith(realRoot)) {
                throw failure("approved artifact resolves outside the repository root");
            }
        }
        catch (IOException error) {
            throw failure("failed to resolve approved artifact path", error);
        }
    }

    private void validateReportContract(JsonNode report, Suite suite) {
        requireEquals(requiredInt(report, "schemaVersion", suite.rootNode()), REPORT_SCHEMA_VERSION,
                "schemaVersion");
        requireEquals(requiredText(report, "phase", suite.rootNode()), suite.phase(), "phase");
        requireEquals(requiredText(report, "datasetVersion", suite.rootNode()), suite.datasetVersion(),
                "datasetVersion");

        JsonNode model = requiredObject(report, "model", suite.rootNode());
        requireEquals(requiredText(model, "resolvedName", "model"), "bge-m3:latest", "model.resolvedName");
        requireEquals(requiredText(model, "digest", "model"), BGE_M3_DIGEST, "model.digest");
        requireEquals(requiredInt(model, "dimensions", "model"), EMBEDDING_DIMENSIONS, "model.dimensions");
        if (!requiredBoolean(model, "embeddingCapable", "model")) {
            throw failure("model.embeddingCapable must be true");
        }

        JsonNode contract = requiredObject(report, "contract", suite.rootNode());
        requireEquals(requiredText(contract, "embeddingModel", "contract"), "bge-m3",
                "contract.embeddingModel");
        requireEquals(requiredInt(contract, "embeddingDimensions", "contract"), EMBEDDING_DIMENSIONS,
                "contract.embeddingDimensions");
        requireEquals(requiredText(contract, "similarity", "contract"), "COSINE", "contract.similarity");
        requireEquals(requiredText(contract, "ranking", "contract"), "RAW_DENSE_ONLY", "contract.ranking");

        JsonNode splitHashes = requiredObject(report, "splitManifestHashes", suite.rootNode());
        if (splitHashes.size() != suite.splitManifestHashes().size()) {
            throw failure("split manifest hash inventory mismatch for " + suite.rootNode());
        }
        for (Map.Entry<String, String> expected : suite.splitManifestHashes().entrySet()) {
            requireEquals(
                    requiredText(splitHashes, expected.getKey(), "splitManifestHashes"),
                    expected.getValue(),
                    "splitManifestHashes." + expected.getKey());
        }

        if (requiredBoolean(report, "sealedFinalOpened", suite.rootNode())) {
            throw failure("historical report says SEALED FINAL was opened");
        }
        if (requiredBoolean(report, "sealedFinalSearchExecuted", suite.rootNode())) {
            throw failure("historical report says SEALED FINAL search was executed");
        }
        requireEquals(requiredText(report, "currentFreshBaseline", suite.rootNode()), "NOT_RUN",
                "currentFreshBaseline");
    }

    private List<ReplayCandidate> projectCandidates(JsonNode report, Suite suite) {
        JsonNode queries = requiredArray(report, "queries", suite.rootNode());
        if (queries.size() != suite.queryInventory().size()) {
            throw failure("query inventory size mismatch for " + suite.rootNode());
        }

        List<ReplayCandidate> projection = new ArrayList<>();
        Set<String> observedQueryIds = new LinkedHashSet<>();
        for (int queryIndex = 0; queryIndex < queries.size(); queryIndex++) {
            JsonNode query = queries.get(queryIndex);
            if (!query.isObject()) {
                throw failure("query must be an object at index " + queryIndex);
            }
            QueryIdentity expectedQuery = suite.queryInventory().get(queryIndex);
            String queryId = requiredText(query, "queryId", "query[" + queryIndex + "]");
            if (!observedQueryIds.add(queryId)) {
                throw failure("duplicate query ID: " + queryId);
            }
            requireEquals(queryId, expectedQuery.queryId(), "query inventory ID");
            requireEquals(requiredText(query, "split", queryId), expectedQuery.split(), queryId + ".split");
            requireEquals(requiredText(query, "userBundleId", queryId), expectedQuery.owner(),
                    queryId + ".owner");
            requireEquals(requiredText(query, "professionGroup", queryId), expectedQuery.profession(),
                    queryId + ".profession");
            requireEquals(requiredText(query, "language", queryId), expectedQuery.language(),
                    queryId + ".language");

            JsonNode passage = requiredObject(query, "passage", queryId);
            int candidateCount = requiredInt(passage, "candidateCount", queryId + ".passage");
            JsonNode ranking = requiredArray(passage, "rawDenseRanking", queryId + ".passage");
            if (candidateCount <= 0 || candidateCount != ranking.size()) {
                throw failure("candidateCount/full-ranking size mismatch for " + queryId);
            }
            projectRanking(projection, suite, expectedQuery, ranking);
        }
        return List.copyOf(projection);
    }

    private void projectRanking(
            List<ReplayCandidate> projection,
            Suite suite,
            QueryIdentity query,
            JsonNode ranking) {
        Set<String> candidateIds = new LinkedHashSet<>();
        Set<String> evidenceChildIds = new LinkedHashSet<>();
        double previousCosine = Double.POSITIVE_INFINITY;
        String previousCandidateId = null;

        for (int candidateIndex = 0; candidateIndex < ranking.size(); candidateIndex++) {
            JsonNode candidate = ranking.get(candidateIndex);
            if (!candidate.isObject()) {
                throw failure("ranked candidate must be an object for " + query.queryId());
            }
            int expectedRank = candidateIndex + 1;
            int rank = requiredInt(candidate, "rank", query.queryId() + ".candidate");
            if (rank != expectedRank) {
                throw failure("ranks must be contiguous and one-based for " + query.queryId());
            }
            String candidateId = requiredText(candidate, "candidateId", query.queryId() + ".candidate");
            if (!candidateIds.add(candidateId)) {
                throw failure("duplicate candidate ID in full ranking: " + candidateId);
            }
            double cosine = requiredFiniteDouble(candidate, "cosineScore", candidateId);
            if (Double.compare(cosine, previousCosine) > 0
                    || (Double.compare(cosine, previousCosine) == 0
                            && previousCandidateId != null
                            && candidateId.compareTo(previousCandidateId) < 0)) {
                throw failure("full ranking is not ordered by cosine then candidate ID for " + query.queryId());
            }

            requireEquals(requiredText(candidate, "sourceBlockType", candidateId), "RETRIEVAL_PASSAGE",
                    candidateId + ".sourceBlockType");
            String sourceText = requiredText(candidate, "sourceText", candidateId);
            String retrievalText = requiredText(candidate, "retrievalText", candidateId);
            int sourceLength = requiredInt(candidate, "sourceCodePointLength", candidateId);
            if (sourceLength != sourceText.codePointCount(0, sourceText.length())) {
                throw failure("source code-point length mismatch for " + candidateId);
            }
            List<String> childIds = requiredStringList(candidate, "evidenceChildIds", candidateId);
            if (childIds.isEmpty()) {
                throw failure("evidenceChildIds must be non-empty for " + candidateId);
            }
            for (String childId : childIds) {
                if (!evidenceChildIds.add(childId)) {
                    throw failure("duplicate EvidenceChild ID in full ranking: " + childId);
                }
            }

            projection.add(new ReplayCandidate(
                    suite.rootNode(),
                    suite.datasetVersion(),
                    query.split(),
                    query.queryId(),
                    query.owner(),
                    query.profession(),
                    query.language(),
                    rank,
                    candidateId,
                    cosine,
                    requiredText(candidate, "documentId", candidateId),
                    requiredText(candidate, "versionId", candidateId),
                    requiredText(candidate, "parentAnnotationCandidateId", candidateId),
                    childIds,
                    sha256(sourceText.getBytes(StandardCharsets.UTF_8)),
                    sha256(retrievalText.getBytes(StandardCharsets.UTF_8))));
            previousCosine = cosine;
            previousCandidateId = candidateId;
        }
    }

    private byte[] canonicalBytes(List<ReplayCandidate> candidates) {
        ArrayNode rows = mapper.createArrayNode();
        for (ReplayCandidate candidate : candidates) {
            ObjectNode row = rows.addObject();
            row.put("suite", candidate.suite());
            row.put("dataset", candidate.dataset());
            row.put("split", candidate.split());
            row.put("query", candidate.query());
            row.put("owner", candidate.owner());
            row.put("profession", candidate.profession());
            row.put("language", candidate.language());
            row.put("rank", candidate.rank());
            row.put("candidateId", candidate.candidateId());
            row.put("cosine", candidate.cosine());
            row.put("doc", candidate.doc());
            row.put("version", candidate.version());
            row.put("parent", candidate.parent());
            ArrayNode children = row.putArray("evidenceChildIds");
            candidate.evidenceChildIds().forEach(children::add);
            row.put("sourceSha256", candidate.sourceSha256());
            row.put("retrievalSha256", candidate.retrievalSha256());
        }
        try {
            return mapper.writeValueAsBytes(rows);
        }
        catch (JacksonException error) {
            throw failure("failed to create canonical replay bytes", error);
        }
    }

    private byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        }
        catch (IOException error) {
            throw failure("failed to read approved historical artifact", error);
        }
    }

    private JsonNode readTree(byte[] bytes) {
        try {
            JsonNode root = mapper.readTree(bytes);
            if (root == null || !root.isObject()) {
                throw failure("historical artifact root must be a JSON object");
            }
            return root;
        }
        catch (JacksonException error) {
            throw failure("historical artifact is not valid JSON", error);
        }
    }

    private JsonNode requiredObject(JsonNode parent, String field, String label) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isObject()) {
            throw failure(label + "." + field + " must be an object");
        }
        return value;
    }

    private JsonNode requiredArray(JsonNode parent, String field, String label) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isArray()) {
            throw failure(label + "." + field + " must be an array");
        }
        return value;
    }

    private String requiredText(JsonNode parent, String field, String label) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw failure(label + "." + field + " must be non-blank text");
        }
        return value.textValue();
    }

    private int requiredInt(JsonNode parent, String field, String label) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt()) {
            throw failure(label + "." + field + " must be an integer");
        }
        return value.intValue();
    }

    private boolean requiredBoolean(JsonNode parent, String field, String label) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isBoolean()) {
            throw failure(label + "." + field + " must be a boolean");
        }
        return value.booleanValue();
    }

    private double requiredFiniteDouble(JsonNode parent, String field, String label) {
        JsonNode value = parent.get(field);
        if (value == null || !value.isNumber()) {
            throw failure(label + "." + field + " must be numeric");
        }
        double result = value.doubleValue();
        if (!Double.isFinite(result)) {
            throw failure(label + "." + field + " must be finite");
        }
        return result;
    }

    private List<String> requiredStringList(JsonNode parent, String field, String label) {
        JsonNode value = requiredArray(parent, field, label);
        List<String> result = new ArrayList<>();
        Set<String> unique = new LinkedHashSet<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.textValue().isBlank()) {
                throw failure(label + "." + field + " must contain only non-blank text");
            }
            if (!unique.add(item.textValue())) {
                throw failure(label + "." + field + " contains a duplicate ID: " + item.textValue());
            }
            result.add(item.textValue());
        }
        return List.copyOf(result);
    }

    private void requireEquals(Object actual, Object expected, String label) {
        if (!Objects.equals(actual, expected)) {
            throw failure(label + " mismatch: expected " + expected + " but was " + actual);
        }
    }

    private static String requireSha256(String value, String label) {
        Objects.requireNonNull(value, label);
        String normalized = value.toLowerCase(Locale.ROOT);
        if (!SHA256.matcher(normalized).matches()) {
            throw new IllegalArgumentException(label + " must be a lowercase 64-character SHA-256");
        }
        return normalized;
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private ReplayValidationException failure(String message) {
        return new ReplayValidationException(message);
    }

    private ReplayValidationException failure(String message, Throwable cause) {
        return new ReplayValidationException(message, cause);
    }

    enum Suite {
        ORIGINAL_SEED(
                "originalSeed",
                PHASE_ONE_ARTIFACT,
                "PRZ-026-PHASE-1-RETRIEVAL-PASSAGE-ORIGINAL-SEED",
                "search-v3-fresh-seed-1.0.1",
                Map.of(
                        "DEV", "e6d6e872045b002f8f74ddbabdd9fa220993db803d1d6f6829d4675434ee17c5",
                        "CALIBRATION", "92095ea10504063ebdd5c1284c34396155923a1639e6163322e341337cbf6598"),
                originalQueries()),
        LONG_FORM_EXPANSION(
                "longFormExpansion",
                PHASE_ONE_ARTIFACT,
                "PRZ-026-PHASE-1-RETRIEVAL-PASSAGE-LONG-FORM",
                "search-v3-fresh-devcal-1.1.0",
                Map.of(
                        "DEV", "4262a62a758e22f8b3a66573790edd00df2193caaa6046edd51e89975dd378af",
                        "CALIBRATION", "fb3175062a7879f4dfb586b4e02e608e01dcb03fb6dcd4750e85dcfdeb8a4405"),
                longFormQueries()),
        HISTORICAL_LONG_FORM(
                "historicalLongForm",
                ROBUSTNESS_ARTIFACT,
                "PRZ-026-B3-ROBUSTNESS-HISTORICAL-LONG-FORM",
                "search-v3-fresh-devcal-1.1.0",
                Map.of(
                        "DEV", "4262a62a758e22f8b3a66573790edd00df2193caaa6046edd51e89975dd378af",
                        "CALIBRATION", "fb3175062a7879f4dfb586b4e02e608e01dcb03fb6dcd4750e85dcfdeb8a4405"),
                longFormQueries()),
        INDEPENDENT_ROBUSTNESS(
                "independentRobustness",
                ROBUSTNESS_ARTIFACT,
                "PRZ-026-B3-ROBUSTNESS-INDEPENDENT-DEV-CAL",
                "search-v3-fresh-devcal-robustness-1.0.0",
                Map.of(
                        "DEV", "aa28c35bcc3d3e021c3c483f1c270bf174c330bfcb38fd40d9e8170936124f4d",
                        "CALIBRATION", "7daf613be385915a2895b3d2215fc0e616407cf25953c520b1e951476a784a19"),
                robustnessQueries());

        private final String rootNode;
        private final String artifactRelativePath;
        private final String phase;
        private final String datasetVersion;
        private final Map<String, String> splitManifestHashes;
        private final List<QueryIdentity> queryInventory;

        Suite(
                String rootNode,
                String artifactRelativePath,
                String phase,
                String datasetVersion,
                Map<String, String> splitManifestHashes,
                List<QueryIdentity> queryInventory) {
            this.rootNode = rootNode;
            this.artifactRelativePath = artifactRelativePath;
            this.phase = phase;
            this.datasetVersion = datasetVersion;
            this.splitManifestHashes = Map.copyOf(splitManifestHashes);
            this.queryInventory = List.copyOf(queryInventory);
        }

        String rootNode() {
            return rootNode;
        }

        String artifactRelativePath() {
            return artifactRelativePath;
        }

        String phase() {
            return phase;
        }

        String datasetVersion() {
            return datasetVersion;
        }

        Map<String, String> splitManifestHashes() {
            return splitManifestHashes;
        }

        List<QueryIdentity> queryInventory() {
            return queryInventory;
        }

        static Suite fromRootNode(String rootNode) {
            for (Suite suite : values()) {
                if (suite.rootNode.equals(rootNode)) {
                    return suite;
                }
            }
            throw new ReplayValidationException("artifact root node is not approved: " + rootNode);
        }
    }

    record QueryIdentity(String queryId, String split, String owner, String profession, String language) {
    }

    record ReplayCandidate(
            String suite,
            String dataset,
            String split,
            String query,
            String owner,
            String profession,
            String language,
            int rank,
            String candidateId,
            double cosine,
            String doc,
            String version,
            String parent,
            List<String> evidenceChildIds,
            String sourceSha256,
            String retrievalSha256) {

        ReplayCandidate {
            evidenceChildIds = List.copyOf(evidenceChildIds);
        }
    }

    record Replay(List<ReplayCandidate> candidates, byte[] canonicalBytes, String canonicalSha256) {

        Replay {
            candidates = List.copyOf(candidates);
            canonicalBytes = canonicalBytes.clone();
            requireSha256(canonicalSha256, "canonical replay SHA-256");
        }

        @Override
        public byte[] canonicalBytes() {
            return canonicalBytes.clone();
        }
    }

    static final class ReplayValidationException extends IllegalArgumentException {

        ReplayValidationException(String message) {
            super(message);
        }

        ReplayValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static Set<String> canonicalFields() {
        return CANONICAL_FIELDS;
    }

    private static List<QueryIdentity> originalQueries() {
        List<QueryIdentity> result = new ArrayList<>();
        addQueries(result, "SV3-U01", "DEV", "BACKEND", "KO_EN_MIXED", "KO", "KO_EN_MIXED", "KO",
                "KO_EN_MIXED");
        addQueries(result, "SV3-U04", "DEV", "DESIGN_PRODUCT", "KO", "KO_EN_MIXED", "KO_EN_MIXED", "KO");
        addQueries(result, "SV3-U06", "DEV", "MARKETING_SALES", "EN", "EN", "EN", "KO_EN_MIXED");
        addQueries(result, "SV3-U02", "CALIBRATION", "FRONTEND_MOBILE", "EN", "EN", "EN", "EN");
        addQueries(result, "SV3-U03", "CALIBRATION", "DATA_AI_INFRA", "KO_EN_MIXED", "KO_EN_MIXED",
                "KO_EN_MIXED", "KO_EN_MIXED");
        return List.copyOf(result);
    }

    private static List<QueryIdentity> longFormQueries() {
        List<QueryIdentity> result = new ArrayList<>();
        addQueries(result, "SV3-LF-U101", "DEV", "DESIGN_PRODUCT", "KO", "KO", "KO_EN_MIXED",
                "KO_EN_MIXED");
        addQueries(result, "SV3-LF-U102", "DEV", "DATA_AI_INFRA", "EN", "EN", "EN", "EN");
        addQueries(result, "SV3-LF-U103", "DEV", "MARKETING_SALES", "EN", "KO_EN_MIXED", "EN",
                "KO_EN_MIXED");
        addQueries(result, "SV3-LF-U104", "CALIBRATION", "FRONTEND_MOBILE", "EN", "EN", "EN", "EN");
        addQueries(result, "SV3-LF-U105", "CALIBRATION", "PLANNING", "KO", "KO", "KO_EN_MIXED", "KO");
        addQueries(result, "SV3-LF-U106", "CALIBRATION", "NON_DEVELOPMENT_GENERAL", "KO", "EN", "KO", "EN");
        return List.copyOf(result);
    }

    private static List<QueryIdentity> robustnessQueries() {
        List<QueryIdentity> result = new ArrayList<>();
        addQueries(result, "SV3-RB-U201", "DEV", "FRONTEND_MOBILE", "KO", "KO", "KO", "KO");
        addQueries(result, "SV3-RB-U202", "DEV", "DESIGN_PRODUCT", "EN", "EN", "EN", "EN");
        addQueries(result, "SV3-RB-U203", "DEV", "DATA_AI_INFRA", "EN", "KO", "EN", "KO");
        addQueries(result, "SV3-RB-U204", "CALIBRATION", "FRONTEND_MOBILE", "EN", "EN", "EN", "EN");
        addQueries(result, "SV3-RB-U205", "CALIBRATION", "MARKETING_SALES", "KO", "KO", "KO", "KO");
        addQueries(result, "SV3-RB-U206", "CALIBRATION", "NON_DEVELOPMENT_GENERAL", "KO", "EN", "KO", "EN");
        return List.copyOf(result);
    }

    private static void addQueries(
            List<QueryIdentity> target,
            String owner,
            String split,
            String profession,
            String... languages) {
        for (int index = 0; index < languages.length; index++) {
            target.add(new QueryIdentity(
                    owner + "-Q%02d".formatted(index + 1),
                    split,
                    owner,
                    profession,
                    languages[index]));
        }
    }
}
