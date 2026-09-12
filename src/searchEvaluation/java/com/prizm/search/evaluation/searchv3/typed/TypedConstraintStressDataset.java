package com.prizm.search.evaluation.searchv3.typed;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Strict evaluation-only loader for the frozen PRZ-028 typed-constraint stress inputs.
 * Runtime parsing receives {@link RuntimeInputs}; Gold remains in {@link EvaluationGold}.
 */
public final class TypedConstraintStressDataset {

    public static final DatasetIdentity HISTORICAL_1_0_1 = new DatasetIdentity(
            Path.of("src/searchEvaluation/resources/search-v3-evaluation/typed-constraints-stress-1.0.1"),
            "search-v3-typed-constraints-stress-1.0.1",
            "FRESH_BENCHMARK_SEED_FROZEN",
            "96c1ddc6cbdd6722619d7806cbe418babc414c0d5179af84d4694a94c8ed015b",
            "35c6e84b85302aad5f1499bc5f8a96fdeeb3a635a3d2da3595f4473654e17350",
            "b754d92e49246aec955c3bef252eeb09a6978272b7b7ba869059bf5a536e606e",
            13,
            12,
            13,
            13,
            false);
    public static final DatasetIdentity OFFICIAL_1_1_0 = new DatasetIdentity(
            Path.of("src/searchEvaluation/resources/search-v3-evaluation/typed-constraints-stress-1.1.0"),
            "search-v3-typed-constraints-stress-1.1.0",
            "INPUT_FROZEN",
            "dec33f2c222f5b159166572aed807b1a50e656dccc7cf728dc19019b9ddcee77",
            "84fc74b7d44008b90a6a23bdaf5ea3dbebebc00eeaeb683281ef10ceb57f6a36",
            "184daa39aafada65b6d7165559c02ce5dd1e7a3e1813544392bf6932a75db408",
            12,
            12,
            12,
            12,
            true);

    /** Backward-compatible aliases for the historical PRZ-028 official input. */
    public static final Path DATASET_ROOT = HISTORICAL_1_0_1.root();
    public static final String DATASET_VERSION = HISTORICAL_1_0_1.version();
    public static final String ROOT_SHA256 = HISTORICAL_1_0_1.rootSha256();
    public static final String DEV_SHA256 = HISTORICAL_1_0_1.devSha256();
    public static final String CALIBRATION_SHA256 = HISTORICAL_1_0_1.calibrationSha256();

    private static final Pattern UUID = Pattern.compile(
            "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final Set<String> FORBIDDEN_RUNTIME_KEYS = Set.of(
            "chunkid", "expectedchunkid", "runtimechunkid", "runtimeparentid",
            "databaseparentid", "dbchunkid", "retrievalpassageid");
    private static final Map<String, Set<String>> DIAGNOSTIC_REASONS_BY_STATE = Map.of(
            "SATISFIED", Set.of("MATCHED"),
            "CONTRADICTED", Set.of("VALUE_MISMATCH", "DIRECTION_MISMATCH"),
            "UNKNOWN", Set.of(
                    "QUALIFIER_MISMATCH", "UNIT_MISMATCH",
                    "NO_MATCHING_OBSERVATION", "AMBIGUOUS_OBSERVATION"));
    private static final Set<String> MATCH_STATES = DIAGNOSTIC_REASONS_BY_STATE.keySet();
    private static final Set<String> DIAGNOSTIC_REASONS = DIAGNOSTIC_REASONS_BY_STATE.values().stream()
            .flatMap(Set::stream)
            .collect(Collectors.toUnmodifiableSet());
    private static final Set<String> SUPPORT_RELATIONS = Set.of(
            "DIRECT_SUPPORT", "RELATED", "CONTRADICTS", "INSUFFICIENT");
    private static final Set<String> CONSTRAINT_KINDS = Set.of(
            "QUANTITY", "DATE", "IDENTIFIER_NUMBER", "LITERAL_IDENTIFIER");

    private final ObjectMapper mapper = new ObjectMapper();

    public DatasetSlice load(Split split) {
        return load(HISTORICAL_1_0_1, split);
    }

    public DatasetSlice load(DatasetIdentity identity, Split split) {
        return load(identity, identity.root(), split, identity.rootSha256());
    }

    public DatasetSlice load(Path root, Split split, String expectedRootSha256) {
        return load(HISTORICAL_1_0_1, root, split, expectedRootSha256);
    }

    private DatasetSlice load(
            DatasetIdentity identity,
            Path root,
            Split split,
            String expectedRootSha256) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        rejectSealedPath(normalizedRoot);
        JsonNode rootManifest = read(normalizedRoot.resolve("manifest.json"));
        validateRootManifest(identity, normalizedRoot, rootManifest, expectedRootSha256);

        Path splitRoot = normalizedRoot.resolve(split.directory()).normalize();
        if (!splitRoot.startsWith(normalizedRoot)
                || !splitRoot.getFileName().toString().equals(split.directory())) {
            throw new IllegalArgumentException("DEV/CAL split path is invalid");
        }
        JsonNode manifest = read(splitRoot.resolve("manifest.json"));
        JsonNode corpus = read(splitRoot.resolve("corpus.json"));
        JsonNode gold = read(splitRoot.resolve("gold-evidence.json"));
        JsonNode questions = read(splitRoot.resolve("questions.json"));
        JsonNode typed = read(splitRoot.resolve("typed-annotations.json"));
        for (JsonNode artifact : List.of(manifest, corpus, gold, questions, typed)) {
            requireHeader(identity, artifact, split);
        }
        validateNoRuntimeDatabaseIdentifiers(corpus);
        validateNoRuntimeDatabaseIdentifiers(gold);
        validateNoRuntimeDatabaseIdentifiers(questions);
        validateNoRuntimeDatabaseIdentifiers(typed);
        verifyManifest(splitRoot, manifest, identity.splitSha256(split), true);

        Map<String, SourceDocument> documents = loadDocuments(splitRoot, corpus, split);
        Map<String, EvidenceParent> parents = loadParents(gold.path("parents"), documents);
        Map<String, EvidenceUnit> units = loadUnits(gold.path("evidenceUnits"), documents, parents);
        Map<String, EvidenceGroup> groups = loadGroups(gold.path("evidenceGroups"), units);
        validateGoldGraph(parents, units, groups);
        Map<String, Question> questionValues = loadQuestions(questions.path("queries"), units);
        Map<String, TypedQueryAnnotation> annotations = loadTypedAnnotations(
                identity, typed.path("queryAnnotations"), questionValues, units);
        Map<String, ObservationAnnotation> observations = loadObservations(
                typed.path("observations"), documents, units);
        validateCounts(identity, split, manifest, documents, units, questionValues, annotations, observations);

        List<RuntimeQuestion> runtimeQuestions = questionValues.values().stream()
                .map(Question::runtimeQuestion)
                .toList();
        Map<String, QueryTruth> queryTruth = questionValues.values().stream()
                .collect(Collectors.toMap(
                        Question::queryId,
                        question -> new QueryTruth(
                                question.queryId(),
                                question.userBundleId(),
                                question.language(),
                                question.categories(),
                                question.answerability(),
                                question.expectedEvidence()),
                        (left, right) -> {
                            throw new IllegalArgumentException("Duplicate query truth: " + left.queryId());
                        },
                        LinkedHashMap::new));
        RuntimeInputs runtimeInputs = new RuntimeInputs(
                split,
                List.copyOf(documents.values()),
                runtimeQuestions);
        EvaluationGold evaluationGold = new EvaluationGold(
                Map.copyOf(parents),
                Map.copyOf(groups),
                Map.copyOf(units),
                Map.copyOf(queryTruth),
                Map.copyOf(annotations),
                Map.copyOf(observations));
        return new DatasetSlice(
                identity.version(),
                split,
                required(rootManifest, "combinedSha256"),
                required(manifest, "combinedSha256"),
                runtimeInputs,
                evaluationGold);
    }

    private void rejectSealedPath(Path root) {
        String portable = root.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (portable.contains("sealed-final") || portable.contains("sealed_final")) {
            throw new IllegalArgumentException("SEALED_FINAL_TEST access is forbidden for PRZ-028");
        }
    }

    private void validateRootManifest(
            DatasetIdentity identity,
            Path root,
            JsonNode manifest,
            String expectedRootSha256) {
        if (!identity.version().equals(required(manifest, "datasetVersion"))
                || !"ALL".equals(required(manifest, "split"))
                || !identity.status().equals(required(manifest, "status"))
                || manifest.path("mutable").asBoolean(true)
                || manifest.path("searchExecuted").asBoolean(true)
                || !expectedRootSha256.equals(required(manifest, "combinedSha256"))) {
            throw new IllegalStateException("PRZ-028 root manifest identity/freeze state changed");
        }
        validateHistoricalLineage(identity, manifest.path("previousDatasets"));
        verifyManifest(root, manifest, expectedRootSha256, false);
    }

    private void validateHistoricalLineage(DatasetIdentity identity, JsonNode previousDatasets) {
        if (identity.equals(HISTORICAL_1_0_1)) {
            JsonNode previous = previousDatasets.path(0);
            if (!"search-v3-typed-constraints-stress-1.0.0".equals(previous.path("datasetVersion").asText())
                    || !"INVALID_INPUT_HISTORICAL".equals(previous.path("status").asText())
                    || previous.path("benchmarkExecuted").asBoolean(true)) {
                throw new IllegalStateException("PRZ-028 v1.0.0 historical-invalid lineage changed");
            }
            return;
        }
        boolean historicalFrozen = false;
        for (JsonNode previous : previousDatasets) {
            if (HISTORICAL_1_0_1.version().equals(previous.path("datasetVersion").asText())
                    && "HISTORICAL_FROZEN".equals(previous.path("status").asText())
                    && previous.path("benchmarkExecuted").asBoolean(false)) {
                historicalFrozen = true;
            }
        }
        if (!historicalFrozen) {
            throw new IllegalStateException("PRZ-028 v1.0.1 historical-frozen lineage changed");
        }
    }

    private void requireHeader(DatasetIdentity identity, JsonNode artifact, Split split) {
        if (!identity.version().equals(required(artifact, "datasetVersion"))
                || !split.manifestName().equals(required(artifact, "split"))) {
            throw new IllegalArgumentException("Typed stress artifact version/split mismatch");
        }
    }

    private void verifyManifest(Path root, JsonNode manifest, String expectedCombined, boolean splitManifest) {
        Map<String, ManifestEntry> entries = new LinkedHashMap<>();
        for (JsonNode node : manifest.path("files")) {
            String relative = required(node, "path").replace('\\', '/');
            Path file = root.resolve(relative).normalize();
            if (relative.isBlank() || relative.startsWith("/") || relative.contains("..")
                    || !file.startsWith(root)) {
                throw new IllegalStateException("Manifest contains an unsafe path: " + relative);
            }
            ManifestEntry value = new ManifestEntry(
                    relative,
                    node.path("bytes").asLong(-1),
                    required(node, "sha256"));
            if (entries.put(relative, value) != null) {
                throw new IllegalStateException("Manifest duplicates path: " + relative);
            }
            try {
                if (!Files.isRegularFile(file)
                        || Files.size(file) != value.bytes()
                        || !value.sha256().equals(sha256(file))) {
                    throw new IllegalStateException("Manifest file hash/size mismatch: " + relative);
                }
            }
            catch (IOException exception) {
                throw new IllegalStateException("Cannot verify manifest file: " + relative, exception);
            }
        }
        String combined = combinedHash(entries.values());
        if (!expectedCombined.equals(combined)
                || !expectedCombined.equals(required(manifest, "combinedSha256"))) {
            throw new IllegalStateException("Manifest combined SHA-256 mismatch: expected="
                    + expectedCombined + ", actual=" + combined);
        }
        Set<String> actual;
        try (Stream<Path> paths = Files.walk(root)) {
            actual = paths.filter(Files::isRegularFile)
                    .map(root::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(relative -> !"manifest.json".equals(relative))
                    .collect(Collectors.toSet());
        }
        catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect manifest inventory", exception);
        }
        if (!actual.equals(entries.keySet())) {
            throw new IllegalStateException("Manifest file inventory mismatch");
        }
        if (actual.stream().anyMatch(relative -> relative.toLowerCase(Locale.ROOT)
                .matches(".*(?:result|prediction|output).*"))) {
            throw new IllegalStateException("Typed stress input contains a result/prediction/output file");
        }
        if (splitManifest
                && (manifest.path("mutable").asBoolean(true)
                    || manifest.path("searchExecuted").asBoolean(true))) {
            throw new IllegalStateException("DEV/CAL split is not frozen or claims search execution");
        }
    }

    private Map<String, SourceDocument> loadDocuments(Path splitRoot, JsonNode corpus, Split split) {
        Map<String, SourceDocument> result = new LinkedHashMap<>();
        for (JsonNode bundle : corpus.path("userBundles")) {
            String userBundleId = stableId(required(bundle, "userBundleId"), "user bundle");
            if (!split.manifestName().equals(required(bundle, "split"))) {
                throw new IllegalArgumentException("User bundle split mismatch: " + userBundleId);
            }
            String professionGroup = required(bundle, "professionGroup");
            for (JsonNode node : bundle.path("documents")) {
                if (!node.path("active").asBoolean(false)) {
                    throw new IllegalArgumentException("Typed stress corpus contains an inactive document");
                }
                String contentPath = required(node, "contentPath").replace('\\', '/');
                Path source = splitRoot.resolve(contentPath).normalize();
                if (!source.startsWith(splitRoot) || !Files.isRegularFile(source)) {
                    throw new IllegalArgumentException("Document contentPath is invalid: " + contentPath);
                }
                String sourceText = readText(source);
                String contentSha256 = required(node, "contentSha256");
                if (!contentSha256.equals(sha256(sourceText.getBytes(StandardCharsets.UTF_8)))) {
                    throw new IllegalArgumentException("Document content hash mismatch: " + contentPath);
                }
                SourceDocument document = new SourceDocument(
                        userBundleId,
                        stableId(required(node, "documentId"), "document"),
                        stableId(required(node, "versionId"), "document version"),
                        contentPath,
                        sourceText,
                        contentSha256,
                        required(node, "documentType"),
                        required(node, "fileType"),
                        required(node, "supportScope"),
                        required(node, "language"),
                        professionGroup);
                if (result.put(document.versionId(), document) != null) {
                    throw new IllegalArgumentException("Duplicate document version: " + document.versionId());
                }
            }
        }
        return result;
    }

    private Map<String, EvidenceParent> loadParents(JsonNode nodes, Map<String, SourceDocument> documents) {
        Map<String, EvidenceParent> result = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            String id = stableId(required(node, "parentId"), "Gold parent");
            SourceSpan span = sourceSpan(node.path("sourceSpan"));
            EvidenceParent parent = new EvidenceParent(
                    id,
                    stableId(required(node, "userBundleId"), "Gold parent owner"),
                    stableId(required(node, "documentId"), "Gold parent document"),
                    stableId(required(node, "versionId"), "Gold parent version"),
                    required(node, "label"),
                    span);
            validateSpan(parent.userBundleId(), parent.documentId(), parent.versionId(), span, documents);
            if (result.put(id, parent) != null) {
                throw new IllegalArgumentException("Duplicate Gold parent ID: " + id);
            }
        }
        return result;
    }

    private Map<String, EvidenceUnit> loadUnits(
            JsonNode nodes,
            Map<String, SourceDocument> documents,
            Map<String, EvidenceParent> parents) {
        Map<String, EvidenceUnit> result = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            String id = stableId(required(node, "evidenceUnitId"), "Gold evidence unit");
            List<SourceSpan> spans = new ArrayList<>();
            for (JsonNode span : node.path("sourceSpans")) spans.add(sourceSpan(span));
            if (spans.isEmpty()) throw new IllegalArgumentException("Evidence unit has no source span: " + id);
            EvidenceUnit unit = new EvidenceUnit(
                    id,
                    stableId(required(node, "userBundleId"), "Gold evidence owner"),
                    stableId(required(node, "parentId"), "Gold evidence parent"),
                    stableId(required(node, "groupId"), "Gold evidence group"),
                    stableId(required(node, "documentId"), "Gold evidence document"),
                    stableId(required(node, "versionId"), "Gold evidence version"),
                    stableId(required(node, "sourceFactId"), "Gold source fact"),
                    List.copyOf(spans),
                    required(node, "primarySpanId"));
            EvidenceParent parent = parents.get(unit.parentId());
            if (parent == null) throw new IllegalArgumentException("Evidence unit references missing parent: " + id);
            for (SourceSpan span : spans) {
                validateSpan(unit.userBundleId(), unit.documentId(), unit.versionId(), span, documents);
                if (span.codePointStart() < parent.sourceSpan().codePointStart()
                        || span.codePointEnd() > parent.sourceSpan().codePointEnd()) {
                    throw new IllegalArgumentException("Evidence unit escapes its parent: " + id);
                }
            }
            if (spans.stream().noneMatch(span -> span.spanId().equals(unit.primarySpanId()))) {
                throw new IllegalArgumentException("Evidence unit primary span is missing: " + id);
            }
            if (result.put(id, unit) != null) {
                throw new IllegalArgumentException("Duplicate Gold evidence ID: " + id);
            }
        }
        return result;
    }

    private Map<String, EvidenceGroup> loadGroups(JsonNode nodes, Map<String, EvidenceUnit> units) {
        Map<String, EvidenceGroup> result = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            String id = stableId(required(node, "groupId"), "Gold evidence group");
            List<String> unitIds = strings(node.path("evidenceUnitIds")).stream()
                    .map(value -> stableId(value, "Gold group evidence reference"))
                    .toList();
            if (unitIds.isEmpty() || unitIds.stream().anyMatch(unitId -> !units.containsKey(unitId))) {
                throw new IllegalArgumentException("Evidence group has invalid unit references: " + id);
            }
            EvidenceGroup group = new EvidenceGroup(
                    id,
                    stableId(required(node, "userBundleId"), "Gold group owner"),
                    stableId(required(node, "sourceFactId"), "Gold group source fact"),
                    unitIds);
            if (result.put(id, group) != null) {
                throw new IllegalArgumentException("Duplicate Gold evidence group ID: " + id);
            }
        }
        return result;
    }

    private void validateGoldGraph(
            Map<String, EvidenceParent> parents,
            Map<String, EvidenceUnit> units,
            Map<String, EvidenceGroup> groups) {
        for (EvidenceUnit unit : units.values()) {
            EvidenceParent parent = parents.get(unit.parentId());
            EvidenceGroup group = groups.get(unit.groupId());
            if (parent == null || group == null
                    || !unit.userBundleId().equals(parent.userBundleId())
                    || !unit.userBundleId().equals(group.userBundleId())
                    || !unit.documentId().equals(parent.documentId())
                    || !unit.versionId().equals(parent.versionId())
                    || !unit.sourceFactId().equals(group.sourceFactId())
                    || !group.evidenceUnitIds().contains(unit.evidenceUnitId())) {
                throw new IllegalArgumentException("Gold graph lineage mismatch: " + unit.evidenceUnitId());
            }
        }
    }

    private Map<String, Question> loadQuestions(JsonNode nodes, Map<String, EvidenceUnit> units) {
        Map<String, Question> result = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            String queryId = stableId(required(node, "queryId"), "query");
            String userBundleId = stableId(required(node, "userBundleId"), "query owner");
            List<ExpectedEvidence> expected = new ArrayList<>();
            for (JsonNode aspect : node.path("aspects")) {
                for (JsonNode relation : aspect.path("expectedEvidence")) {
                    String evidenceUnitId = stableId(
                            required(relation, "evidenceUnitId"), "query evidence reference");
                    String supportRelation = required(relation, "supportRelation");
                    EvidenceUnit unit = units.get(evidenceUnitId);
                    if (unit == null || !userBundleId.equals(unit.userBundleId())
                            || !SUPPORT_RELATIONS.contains(supportRelation)) {
                        throw new IllegalArgumentException("Query expected-evidence lineage mismatch: " + queryId);
                    }
                    expected.add(new ExpectedEvidence(evidenceUnitId, supportRelation));
                }
            }
            Question question = new Question(
                    queryId,
                    userBundleId,
                    required(node, "query"),
                    required(node, "language"),
                    strings(node.path("categories")),
                    required(node, "answerability"),
                    List.copyOf(expected));
            if (result.put(queryId, question) != null) {
                throw new IllegalArgumentException("Duplicate query ID: " + queryId);
            }
        }
        return result;
    }

    private Map<String, TypedQueryAnnotation> loadTypedAnnotations(
            DatasetIdentity identity,
            JsonNode nodes,
            Map<String, Question> questions,
            Map<String, EvidenceUnit> units) {
        Map<String, TypedQueryAnnotation> result = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            String queryId = stableId(required(node, "queryId"), "typed query annotation");
            Question question = questions.get(queryId);
            String userBundleId = stableId(required(node, "userBundleId"), "typed annotation owner");
            if (question == null || !question.userBundleId().equals(userBundleId)) {
                throw new IllegalArgumentException("Typed annotation query/owner mismatch: " + queryId);
            }
            ConstraintAnnotation constraint = constraintAnnotation(node.path("constraint"));
            validateConstraintGrounding(question.text(), constraint, identity.requiresReasons());
            List<ExpectedEvidenceState> expectedStates = new ArrayList<>();
            Set<String> stateUnitIds = new HashSet<>();
            for (JsonNode state : node.path("expectedEvidenceStates")) {
                String unitId = stableId(required(state, "evidenceUnitId"), "typed state evidence reference");
                String value = required(state, "state");
                EvidenceUnit unit = units.get(unitId);
                if (unit == null || !userBundleId.equals(unit.userBundleId())
                        || !MATCH_STATES.contains(value) || !stateUnitIds.add(unitId)) {
                    throw new IllegalArgumentException("Typed expected state mismatch: " + queryId);
                }
                String reason = text(state, "reason");
                if (reason != null && !DIAGNOSTIC_REASONS.contains(reason)) {
                    throw new IllegalArgumentException("Unknown typed diagnostic reason: " + reason);
                }
                if (identity.requiresReasons() && reason == null) {
                    throw new IllegalArgumentException("Typed expected reason is required: " + queryId);
                }
                if (identity.requiresReasons()) {
                    validateRequiredExpectedStateReasonPair(queryId, value, reason);
                }
                expectedStates.add(new ExpectedEvidenceState(unitId, value, reason));
            }
            Set<String> bundleUnitIds = units.values().stream()
                    .filter(unit -> userBundleId.equals(unit.userBundleId()))
                    .map(EvidenceUnit::evidenceUnitId)
                    .collect(Collectors.toSet());
            if (!bundleUnitIds.equals(stateUnitIds)) {
                throw new IllegalArgumentException("Typed query must label every owner evidence unit: " + queryId);
            }
            String primaryFamily = text(node, "primaryFamily");
            if (identity.requiresReasons() && primaryFamily == null) {
                throw new IllegalArgumentException("Typed primary family is required: " + queryId);
            }
            TypedQueryAnnotation annotation = new TypedQueryAnnotation(
                    queryId,
                    userBundleId,
                    primaryFamily == null ? "" : primaryFamily,
                    strings(node.path("stressFamilies")),
                    constraint,
                    List.copyOf(expectedStates));
            if (result.put(queryId, annotation) != null) {
                throw new IllegalArgumentException("Duplicate typed query annotation: " + queryId);
            }
        }
        if (!result.keySet().equals(questions.keySet())) {
            throw new IllegalArgumentException("Every query must have exactly one typed annotation");
        }
        return result;
    }

    static void validateRequiredExpectedStateReasonPair(String queryId, String state, String reason) {
        Set<String> allowedReasons = DIAGNOSTIC_REASONS_BY_STATE.get(state);
        if (reason == null || allowedReasons == null || !allowedReasons.contains(reason)) {
            throw new IllegalArgumentException("Typed expected state/reason mismatch: "
                    + queryId + " (" + state + " -> " + reason + ")");
        }
    }

    private Map<String, ObservationAnnotation> loadObservations(
            JsonNode nodes,
            Map<String, SourceDocument> documents,
            Map<String, EvidenceUnit> units) {
        Map<String, ObservationAnnotation> result = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            String observationId = stableId(required(node, "observationId"), "typed observation");
            String unitId = stableId(required(node, "evidenceUnitId"), "typed observation evidence reference");
            EvidenceUnit unit = units.get(unitId);
            if (unit == null) throw new IllegalArgumentException("Observation references missing evidence: " + observationId);
            String sourceSpanId = stableId(required(node, "sourceSpanId"), "typed observation span");
            SourceSpan span = unit.sourceSpans().stream()
                    .filter(candidate -> sourceSpanId.equals(candidate.spanId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Observation references missing source span: " + observationId));
            SourceDocument document = documents.get(unit.versionId());
            ObservationAnnotation observation = observationAnnotation(node);
            validateObservationGrounding(document, span, observation);
            if (result.put(observationId, observation) != null) {
                throw new IllegalArgumentException("Duplicate typed observation ID: " + observationId);
            }
        }
        return result;
    }

    private ConstraintAnnotation constraintAnnotation(JsonNode node) {
        String kind = required(node, "kind");
        if (!CONSTRAINT_KINDS.contains(kind)) throw new IllegalArgumentException("Unknown constraint kind: " + kind);
        return new ConstraintAnnotation(
                stableId(required(node, "constraintId"), "constraint"),
                kind,
                text(node, "operator"),
                required(node, "surface"),
                node.path("queryCharStart").asInt(-1),
                node.path("queryCharEnd").asInt(-1),
                text(node, "qualifier"),
                optionalInt(node, "qualifierCharStart"),
                optionalInt(node, "qualifierCharEnd"),
                optionalDouble(node, "value"),
                optionalDouble(node, "upperValue"),
                text(node, "normalizedUnit"),
                text(node, "direction"),
                text(node, "directionSourceSurface"),
                optionalInt(node, "directionCharStart"),
                optionalInt(node, "directionCharEnd"),
                date(node, "value"),
                date(node, "start"),
                date(node, "end"),
                text(node, "precision"),
                text(node, "identifier"),
                text(node, "numberSurface"),
                integers(node.path("normalizedSegments")),
                text(node, "normalizedLiteral"));
    }

    private ObservationAnnotation observationAnnotation(JsonNode node) {
        String kind = required(node, "kind");
        if (!CONSTRAINT_KINDS.contains(kind)) throw new IllegalArgumentException("Unknown observation kind: " + kind);
        return new ObservationAnnotation(
                stableId(required(node, "observationId"), "typed observation"),
                stableId(required(node, "evidenceUnitId"), "typed observation evidence reference"),
                stableId(required(node, "sourceSpanId"), "typed observation span"),
                kind,
                required(node, "sourceSurface"),
                node.path("charStart").asInt(-1),
                node.path("charEnd").asInt(-1),
                text(node, "qualifier"),
                optionalInt(node, "qualifierCharStart"),
                optionalInt(node, "qualifierCharEnd"),
                optionalDouble(node, "value"),
                text(node, "normalizedUnit"),
                text(node, "direction"),
                text(node, "directionSourceSurface"),
                optionalInt(node, "directionCharStart"),
                optionalInt(node, "directionCharEnd"),
                date(node, "start"),
                date(node, "end"),
                text(node, "precision"),
                text(node, "identifier"),
                text(node, "numberSurface"),
                integers(node.path("normalizedSegments")),
                text(node, "normalizedLiteral"));
    }

    private void validateConstraintGrounding(
            String query,
            ConstraintAnnotation constraint,
            boolean requiresExplicitDirectionGrounding) {
        exactSlice(query, constraint.queryCharStart(), constraint.queryCharEnd(), constraint.sourceSurface(),
                constraint.constraintId() + " constraint surface");
        if (constraint.qualifier() != null) {
            exactSlice(query, required(constraint.qualifierCharStart(), "qualifier start"),
                    required(constraint.qualifierCharEnd(), "qualifier end"), constraint.qualifier(),
                    constraint.constraintId() + " qualifier");
        }
        if (constraint.direction() != null && !"NONE".equals(constraint.direction())) {
            boolean hasExplicitDirectionGrounding = constraint.directionSourceSurface() != null
                    || constraint.directionCharStart() != null
                    || constraint.directionCharEnd() != null;
            if (requiresExplicitDirectionGrounding || hasExplicitDirectionGrounding) {
                if (constraint.directionSourceSurface() == null) {
                    throw new IllegalArgumentException("Direction source surface is required: "
                            + constraint.constraintId());
                }
                int start = required(constraint.directionCharStart(), "direction start");
                int end = required(constraint.directionCharEnd(), "direction end");
                exactSlice(query, start, end, constraint.directionSourceSurface(),
                        constraint.constraintId() + " direction");
            }
        }
        if ("QUANTITY".equals(constraint.kind())) {
            if (constraint.sourceSurface().codePoints().noneMatch(Character::isDigit)
                    || constraint.qualifier() != null
                        && normalize(constraint.sourceSurface()).contains(normalize(constraint.qualifier()))) {
                throw new IllegalArgumentException("Quantity core is not independently grounded: "
                        + constraint.constraintId());
            }
        }
        if ("IDENTIFIER_NUMBER".equals(constraint.kind())
                && (!normalize(constraint.sourceSurface()).contains(normalize(constraint.identifier()))
                    || !constraint.sourceSurface().contains(constraint.numberSurface()))) {
            throw new IllegalArgumentException("Identifier-number constraint is not grounded: "
                    + constraint.constraintId());
        }
        if ("LITERAL_IDENTIFIER".equals(constraint.kind())
                && !normalize(constraint.sourceSurface()).equals(constraint.normalizedLiteral())) {
            throw new IllegalArgumentException("Literal constraint normalization mismatch: "
                    + constraint.constraintId());
        }
    }

    private void validateObservationGrounding(
            SourceDocument document,
            SourceSpan span,
            ObservationAnnotation observation) {
        within(observation.charStart(), observation.charEnd(), span, observation.observationId());
        exactSlice(document.sourceText(), observation.charStart(), observation.charEnd(),
                observation.sourceSurface(), observation.observationId() + " source surface");
        if (observation.qualifier() != null) {
            int start = required(observation.qualifierCharStart(), "qualifier start");
            int end = required(observation.qualifierCharEnd(), "qualifier end");
            within(start, end, span, observation.observationId() + " qualifier");
            exactSlice(document.sourceText(), start, end, observation.qualifier(),
                    observation.observationId() + " qualifier");
        }
        if (observation.direction() != null && !"NONE".equals(observation.direction())) {
            int start = required(observation.directionCharStart(), "direction start");
            int end = required(observation.directionCharEnd(), "direction end");
            within(start, end, span, observation.observationId() + " direction");
            exactSlice(document.sourceText(), start, end, observation.directionSourceSurface(),
                    observation.observationId() + " direction");
        }
        if ("IDENTIFIER_NUMBER".equals(observation.kind())
                && (!normalize(observation.sourceSurface()).contains(normalize(observation.identifier()))
                    || !observation.sourceSurface().contains(observation.numberSurface()))) {
            throw new IllegalArgumentException("Identifier-number observation is not grounded: "
                    + observation.observationId());
        }
        if ("LITERAL_IDENTIFIER".equals(observation.kind())
                && !normalize(observation.sourceSurface()).equals(observation.normalizedLiteral())) {
            throw new IllegalArgumentException("Literal observation normalization mismatch: "
                    + observation.observationId());
        }
    }

    private void validateSpan(
            String expectedOwner,
            String expectedDocumentId,
            String expectedVersionId,
            SourceSpan span,
            Map<String, SourceDocument> documents) {
        SourceDocument document = documents.get(expectedVersionId);
        if (document == null
                || !expectedOwner.equals(document.userBundleId())
                || !expectedDocumentId.equals(document.documentId())
                || !expectedDocumentId.equals(span.documentId())
                || !expectedVersionId.equals(span.versionId())
                || !document.contentPath().equals(span.sourcePath())) {
            throw new IllegalArgumentException("Source span provenance mismatch: " + span.spanId());
        }
        exactSlice(document.sourceText(), span.codePointStart(), span.codePointEnd(),
                span.text(), span.spanId());
        if (!span.textSha256().equals(sha256(span.text().getBytes(StandardCharsets.UTF_8)))) {
            throw new IllegalArgumentException("Source span hash mismatch: " + span.spanId());
        }
    }

    private void validateCounts(
            DatasetIdentity identity,
            Split split,
            JsonNode manifest,
            Map<String, SourceDocument> documents,
            Map<String, EvidenceUnit> units,
            Map<String, Question> questions,
            Map<String, TypedQueryAnnotation> annotations,
            Map<String, ObservationAnnotation> observations) {
        JsonNode counts = manifest.path("counts");
        long userBundles = documents.values().stream().map(SourceDocument::userBundleId).distinct().count();
        if (userBundles != 3
                || documents.size() != 3
                || questions.size() != 12
                || annotations.size() != 12
                || units.size() != identity.expectedEvidenceUnits(split)
                || observations.size() != identity.expectedObservations(split)
                || counts.path("userBundles").asInt() != userBundles
                || counts.path("documents").asInt() != documents.size()
                || counts.path("queries").asInt() != questions.size()
                || counts.path("evidenceUnits").asInt() != units.size()
                || counts.path("typedObservations").asInt() != observations.size()) {
            throw new IllegalStateException("Typed stress split counts changed: " + split);
        }
    }

    static void validateNoRuntimeDatabaseIdentifiers(JsonNode node) {
        inspectRuntimeKeys(node, "artifact");
    }

    private static void inspectRuntimeKeys(JsonNode node, String location) {
        if (node.isArray()) {
            int index = 0;
            for (JsonNode child : node) inspectRuntimeKeys(child, location + "[" + index++ + "]");
            return;
        }
        if (!node.isObject()) return;
        node.forEachEntry((key, value) -> {
            if (FORBIDDEN_RUNTIME_KEYS.contains(key.toLowerCase(Locale.ROOT))) {
                throw new IllegalArgumentException(location + "." + key
                        + " is a forbidden runtime database identifier");
            }
            inspectRuntimeKeys(value, location + "." + key);
        });
    }

    private SourceSpan sourceSpan(JsonNode node) {
        return new SourceSpan(
                stableId(required(node, "spanId"), "source span"),
                stableId(required(node, "documentId"), "source span document"),
                stableId(required(node, "versionId"), "source span version"),
                required(node, "sourcePath"),
                node.path("charStart").asInt(-1),
                node.path("charEnd").asInt(-1),
                node.path("lineStart").asInt(-1),
                node.path("lineEnd").asInt(-1),
                optionalInt(node, "page"),
                required(node, "text"),
                required(node, "textSha256"));
    }

    private JsonNode read(Path path) {
        try {
            return mapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
        }
        catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read typed stress artifact: " + path, exception);
        }
    }

    private String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read typed stress document: " + path, exception);
        }
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asString(null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }

    private static int required(Integer value, String label) {
        if (value == null || value < 0) throw new IllegalArgumentException("Missing " + label);
        return value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asString();
    }

    private static Integer optionalInt(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asInt();
    }

    private static Double optionalDouble(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isNumber() ? value.asDouble() : null;
    }

    private static LocalDate date(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull() || !value.isString()) return null;
        String text = value.asString();
        return text.matches("\\d{4}-\\d{2}-\\d{2}") ? LocalDate.parse(text) : null;
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node.isArray()) for (JsonNode value : node) values.add(value.asString());
        return List.copyOf(values);
    }

    private static List<Integer> integers(JsonNode node) {
        List<Integer> values = new ArrayList<>();
        if (node.isArray()) for (JsonNode value : node) values.add(value.asInt());
        return List.copyOf(values);
    }

    private static String stableId(String id, String label) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (id.isBlank() || UUID.matcher(id).matches()
                || normalized.contains("runtime")
                || normalized.contains("database")
                || normalized.contains("chunkid")
                || normalized.contains("dbchunk")) {
            throw new IllegalArgumentException(label + " uses a runtime database ID: " + id);
        }
        return id;
    }

    private static void exactSlice(String content, int start, int end, String expected, String label) {
        if (start < 0 || end <= start || end > content.codePointCount(0, content.length())) {
            throw new IllegalArgumentException(label + " code-point range is invalid");
        }
        String actual = codePointSlice(content, start, end);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException(label + " source text mismatch");
        }
    }

    private static void within(int start, int end, SourceSpan span, String label) {
        if (start < span.codePointStart() || end > span.codePointEnd() || start >= end) {
            throw new IllegalArgumentException(label + " escapes its source span");
        }
    }

    private static String codePointSlice(String value, int start, int end) {
        int startUtf16 = value.offsetByCodePoints(0, start);
        int endUtf16 = value.offsetByCodePoints(0, end);
        return value.substring(startUtf16, endUtf16);
    }

    private static String normalize(String value) {
        return java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static String sha256(Path path) {
        try {
            return sha256(Files.readAllBytes(path));
        }
        catch (IOException exception) {
            throw new IllegalStateException("Cannot hash file: " + path, exception);
        }
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String combinedHash(Iterable<ManifestEntry> entries) {
        List<ManifestEntry> sorted = new ArrayList<>();
        entries.forEach(sorted::add);
        sorted.sort((left, right) -> String.CASE_INSENSITIVE_ORDER.compare(left.path(), right.path()));
        StringBuilder record = new StringBuilder();
        for (ManifestEntry entry : sorted) {
            record.append(entry.path()).append('\0').append(entry.sha256()).append('\n');
        }
        return sha256(record.toString());
    }

    public enum Split {
        DEV("dev", "DEV"),
        CALIBRATION("calibration", "CALIBRATION");

        private final String directory;
        private final String manifestName;

        Split(String directory, String manifestName) {
            this.directory = directory;
            this.manifestName = manifestName;
        }

        String directory() {
            return directory;
        }

        String manifestName() {
            return manifestName;
        }
    }

    public record DatasetIdentity(
            Path root,
            String version,
            String status,
            String rootSha256,
            String devSha256,
            String calibrationSha256,
            int devEvidenceUnits,
            int devObservations,
            int calibrationEvidenceUnits,
            int calibrationObservations,
            boolean requiresReasons) {

        public DatasetIdentity {
            root = root.normalize();
            if (version.isBlank() || status.isBlank() || rootSha256.isBlank()
                    || devSha256.isBlank() || calibrationSha256.isBlank()) {
                throw new IllegalArgumentException("typed dataset identity must be content-addressed");
            }
        }

        String splitSha256(Split split) {
            return split == Split.DEV ? devSha256 : calibrationSha256;
        }

        int expectedEvidenceUnits(Split split) {
            return split == Split.DEV ? devEvidenceUnits : calibrationEvidenceUnits;
        }

        int expectedObservations(Split split) {
            return split == Split.DEV ? devObservations : calibrationObservations;
        }
    }

    public record DatasetSlice(
            String datasetVersion,
            Split split,
            String rootSha256,
            String splitSha256,
            RuntimeInputs runtimeInputs,
            EvaluationGold evaluationGold) {

        public AttachedEvaluation attachPassages(List<EvaluationPassage> passages) {
            Map<String, SourceDocument> documentsByVersion = runtimeInputs.documents().stream()
                    .collect(Collectors.toMap(SourceDocument::versionId, value -> value));
            Map<String, EvaluationPassage> byId = new LinkedHashMap<>();
            Set<String> attachedChildIds = new HashSet<>();
            for (EvaluationPassage passage : passages) {
                stableId(passage.passageId(), "evaluation passage");
                SourceDocument document = documentsByVersion.get(passage.versionId());
                if (document == null
                        || !document.userBundleId().equals(passage.userBundleId())
                        || !document.documentId().equals(passage.documentId())
                        || passage.retrievalText().isBlank()
                        || passage.evidenceChildren().isEmpty()) {
                    throw new IllegalArgumentException("Passage document/owner/version provenance mismatch: "
                            + passage.passageId());
                }
                String parentCandidateId = null;
                int previousEnd = -1;
                for (EvaluationChildSlice child : passage.evidenceChildren()) {
                    stableId(child.evidenceChildId(), "evaluation evidence child");
                    stableId(child.parentAnnotationCandidateId(), "evaluation structural parent");
                    if (!attachedChildIds.add(child.evidenceChildId())) {
                        throw new IllegalArgumentException("Evaluation passages duplicate evidence child identity: "
                                + child.evidenceChildId());
                    }
                    if (!document.contentPath().equals(child.sourcePath())) {
                        throw new IllegalArgumentException("Passage child source path mismatch: "
                                + child.evidenceChildId());
                    }
                    if (!document.documentId().equals(child.documentId())
                            || !document.versionId().equals(child.versionId())
                            || !document.contentSha256().equals(child.documentSourceSha256())
                            || !sha256(child.sourceText()).equals(child.exactTextSha256())) {
                        throw new IllegalArgumentException("Passage child document/hash provenance mismatch: "
                                + child.evidenceChildId());
                    }
                    if ("TXT".equals(document.fileType()) && child.page() != null) {
                        throw new IllegalArgumentException("TXT passage child must not claim a page: "
                                + child.evidenceChildId());
                    }
                    exactSlice(document.sourceText(), child.codePointStart(), child.codePointEnd(),
                            child.sourceText(), child.evidenceChildId());
                    if (previousEnd > child.codePointStart()) {
                        throw new IllegalArgumentException("Passage children are not source ordered: "
                                + passage.passageId());
                    }
                    previousEnd = child.codePointEnd();
                    if (parentCandidateId == null) {
                        parentCandidateId = child.parentAnnotationCandidateId();
                    }
                    else if (!parentCandidateId.equals(child.parentAnnotationCandidateId())) {
                        throw new IllegalArgumentException("Passage crosses a structural parent boundary: "
                                + passage.passageId());
                    }
                }
                if (byId.put(passage.passageId(), passage) != null) {
                    throw new IllegalArgumentException("Duplicate evaluation passage ID: "
                            + passage.passageId());
                }
            }
            return new AttachedEvaluation(runtimeInputs, evaluationGold, Map.copyOf(byId));
        }
    }

    public record RuntimeInputs(Split split, List<SourceDocument> documents, List<RuntimeQuestion> questions) {
    }

    public record SourceDocument(
            String userBundleId,
            String documentId,
            String versionId,
            String contentPath,
            String sourceText,
            String contentSha256,
            String documentType,
            String fileType,
            String supportScope,
            String language,
            String professionGroup) {
    }

    public record RuntimeQuestion(
            String queryId,
            String userBundleId,
            String text,
            String language) {
    }

    public record EvaluationGold(
            Map<String, EvidenceParent> parents,
            Map<String, EvidenceGroup> groups,
            Map<String, EvidenceUnit> units,
            Map<String, QueryTruth> queryTruth,
            Map<String, TypedQueryAnnotation> queryAnnotations,
            Map<String, ObservationAnnotation> observations) {
    }

    public record QueryTruth(
            String queryId,
            String userBundleId,
            String language,
            List<String> categories,
            String answerability,
            List<ExpectedEvidence> expectedEvidence) {
    }

    public record EvidenceParent(
            String parentId,
            String userBundleId,
            String documentId,
            String versionId,
            String label,
            SourceSpan sourceSpan) {
    }

    public record EvidenceGroup(
            String groupId,
            String userBundleId,
            String sourceFactId,
            List<String> evidenceUnitIds) {
    }

    public record EvidenceUnit(
            String evidenceUnitId,
            String userBundleId,
            String parentId,
            String groupId,
            String documentId,
            String versionId,
            String sourceFactId,
            List<SourceSpan> sourceSpans,
            String primarySpanId) {
    }

    public record SourceSpan(
            String spanId,
            String documentId,
            String versionId,
            String sourcePath,
            int codePointStart,
            int codePointEnd,
            int lineStart,
            int lineEnd,
            Integer page,
            String text,
            String textSha256) {
    }

    record Question(
            String queryId,
            String userBundleId,
            String text,
            String language,
            List<String> categories,
            String answerability,
            List<ExpectedEvidence> expectedEvidence) {

        RuntimeQuestion runtimeQuestion() {
            return new RuntimeQuestion(queryId, userBundleId, text, language);
        }
    }

    public record ExpectedEvidence(String evidenceUnitId, String supportRelation) {
    }

    public record TypedQueryAnnotation(
            String queryId,
            String userBundleId,
            String primaryFamily,
            List<String> stressFamilies,
            ConstraintAnnotation constraint,
            List<ExpectedEvidenceState> expectedEvidenceStates) {
    }

    public record ExpectedEvidenceState(String evidenceUnitId, String state, String reason) {
    }

    public record ConstraintAnnotation(
            String constraintId,
            String kind,
            String operator,
            String sourceSurface,
            int queryCharStart,
            int queryCharEnd,
            String qualifier,
            Integer qualifierCharStart,
            Integer qualifierCharEnd,
            Double value,
            Double upperValue,
            String normalizedUnit,
            String direction,
            String directionSourceSurface,
            Integer directionCharStart,
            Integer directionCharEnd,
            LocalDate dateValue,
            LocalDate dateStart,
            LocalDate dateEnd,
            String precision,
            String identifier,
            String numberSurface,
            List<Integer> normalizedSegments,
            String normalizedLiteral) {
    }

    public record ObservationAnnotation(
            String observationId,
            String evidenceUnitId,
            String sourceSpanId,
            String kind,
            String sourceSurface,
            int charStart,
            int charEnd,
            String qualifier,
            Integer qualifierCharStart,
            Integer qualifierCharEnd,
            Double value,
            String normalizedUnit,
            String direction,
            String directionSourceSurface,
            Integer directionCharStart,
            Integer directionCharEnd,
            LocalDate dateStart,
            LocalDate dateEnd,
            String precision,
            String identifier,
            String numberSurface,
            List<Integer> normalizedSegments,
            String normalizedLiteral) {
    }

    public record EvaluationPassage(
            String passageId,
            String userBundleId,
            String documentId,
            String versionId,
            String retrievalText,
            List<EvaluationChildSlice> evidenceChildren) {

        public EvaluationPassage {
            evidenceChildren = List.copyOf(evidenceChildren);
        }

        public List<String> evidenceChildIds() {
            return evidenceChildren.stream().map(EvaluationChildSlice::evidenceChildId).toList();
        }
    }

    public record EvaluationChildSlice(
            String evidenceChildId,
            String documentId,
            String versionId,
            String sourcePath,
            Integer page,
            int codePointStart,
            int codePointEnd,
            String sourceText,
            String parentAnnotationCandidateId,
            String documentSourceSha256,
            String exactTextSha256) {
    }

    public record AttachedEvaluation(
            RuntimeInputs runtimeInputs,
            EvaluationGold evaluationGold,
            Map<String, EvaluationPassage> passagesById) {
    }

    private record ManifestEntry(String path, long bytes, String sha256) {
    }
}
