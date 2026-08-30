package com.prizm.search.evaluation.searchv3.structural;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Strict DEV/CAL-only reader for the materialized PRZ-025 Search V3 seed. */
final class SearchV3DenseAblationDataset {

    static final Path BENCHMARK_ROOT = Path.of("src/test/resources/search-v3-evaluation");
    static final Path LONG_FORM_BENCHMARK_ROOT = Path.of(
            "src/searchEvaluation/resources/search-v3-evaluation/devcal-1.1.0");
    static final Path ROBUSTNESS_BENCHMARK_ROOT = Path.of(
            "src/searchEvaluation/resources/search-v3-evaluation/devcal-robustness-1.0.0");
    static final String ORIGINAL_DATASET_VERSION = "search-v3-fresh-seed-1.0.1";
    static final String LONG_FORM_DATASET_VERSION = "search-v3-fresh-devcal-1.1.0";
    static final String ROBUSTNESS_DATASET_VERSION = "search-v3-fresh-devcal-robustness-1.0.0";
    static final String ROBUSTNESS_SHA256 = "cb43832d48bb1f88e5a24abc520154b8562950ecc973295fdb16936aae08ab54";
    static final String OVERALL_SHA256 = "1f36c4bbb6948b97c4321821cc3d6b8a9e38ab44b81adb1594614c6f7e97289e";
    static final String SEALED_FINAL_SHA256 = "e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383";

    private static final Pattern UUID = Pattern.compile(
            "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
    private static final Set<String> ALLOWED_RELATIONS = Set.of(
            "DIRECT_SUPPORT", "RELATED", "CONTRADICTS", "INSUFFICIENT");

    private final ObjectMapper mapper = new ObjectMapper();

    DatasetSlice load(Split split) {
        return load(BENCHMARK_ROOT.resolve(split.directory()), split, ORIGINAL_DATASET_VERSION);
    }

    DatasetSlice loadLongForm(Split split) {
        return load(LONG_FORM_BENCHMARK_ROOT.resolve(split.directory()), split, LONG_FORM_DATASET_VERSION);
    }

    DatasetSlice loadRobustness(Split split) {
        return load(ROBUSTNESS_BENCHMARK_ROOT.resolve(split.directory()), split, ROBUSTNESS_DATASET_VERSION);
    }

    DatasetSlice load(Path splitDirectory, Split expectedSplit) {
        return load(splitDirectory, expectedSplit, ORIGINAL_DATASET_VERSION);
    }

    private DatasetSlice load(Path splitDirectory, Split expectedSplit, String expectedDatasetVersion) {
        Path normalized = splitDirectory.toAbsolutePath().normalize();
        String portablePath = normalized.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (portablePath.contains("/sealed-final") || portablePath.contains("sealed_final")) {
            throw new IllegalArgumentException("SEALED_FINAL_TEST access is forbidden for PRZ-026");
        }
        if (!normalized.getFileName().toString().equals(expectedSplit.directory())) {
            throw new IllegalArgumentException("Split path does not match the requested DEV/CAL split");
        }

        JsonNode manifest = read(normalized.resolve("manifest.json"));
        JsonNode corpus = read(normalized.resolve("corpus.json"));
        JsonNode questions = read(normalized.resolve("questions.json"));
        JsonNode gold = read(normalized.resolve("gold-evidence.json"));
        requireArtifact(manifest, expectedSplit, expectedDatasetVersion);
        requireArtifact(corpus, expectedSplit, expectedDatasetVersion);
        requireArtifact(questions, expectedSplit, expectedDatasetVersion);
        requireArtifact(gold, expectedSplit, expectedDatasetVersion);

        Map<String, GoldUnit> units = loadUnits(gold.path("evidenceUnits"));
        Map<String, GoldParent> parents = loadParents(gold.path("parents"));
        Map<String, GoldGroup> groups = loadGroups(gold.path("evidenceGroups"));
        List<UserBundle> bundles = loadBundles(normalized, corpus.path("userBundles"));
        List<Query> queryValues = loadQueries(questions.path("queries"), units, expectedSplit);

        if (queryValues.stream().anyMatch(query -> query.split() != expectedSplit)) {
            throw new IllegalArgumentException("Query split mismatch");
        }
        Map<String, SourceDocument> activeDocuments = new LinkedHashMap<>();
        for (UserBundle bundle : bundles) {
            for (SourceDocument document : bundle.activeDocuments()) {
                SourceDocument duplicate = activeDocuments.put(document.versionId(), document);
                if (duplicate != null) {
                    throw new IllegalArgumentException("Duplicate active versionId: " + document.versionId());
                }
            }
        }
        for (Query query : queryValues) {
            if (bundles.stream().noneMatch(bundle -> bundle.userBundleId().equals(query.userBundleId()))) {
                throw new IllegalArgumentException("Query references a missing user bundle: " + query.queryId());
            }
            query.allExpectedEvidence().stream()
                    .map(expected -> units.get(expected.evidenceUnitId()))
                    .filter(unit -> !unit.userBundleId().equals(query.userBundleId()))
                    .findFirst()
                    .ifPresent(unit -> {
                        throw new IllegalArgumentException(
                                "Query references evidence from another user bundle: " + query.queryId());
                    });
            query.allExpectedEvidence().stream()
                    .filter(expected -> "DIRECT_SUPPORT".equals(expected.supportRelation()))
                    .map(expected -> units.get(expected.evidenceUnitId()))
                    .filter(unit -> !activeDocuments.containsKey(unit.versionId())
                            || !unit.userBundleId().equals(activeDocuments.get(unit.versionId()).userBundleId()))
                    .findFirst()
                    .ifPresent(unit -> {
                        throw new IllegalArgumentException(
                                "Query references evidence outside the ACTIVE corpus: " + query.queryId());
                    });
        }
        validateGoldGraph(units, parents, groups);
        validateGrounding(activeDocuments, units, parents);
        return new DatasetSlice(
                expectedDatasetVersion,
                expectedSplit,
                manifest.path("combinedSha256").asText(),
                List.copyOf(bundles),
                List.copyOf(queryValues),
                Map.copyOf(activeDocuments),
                Map.copyOf(units),
                Map.copyOf(parents),
                Map.copyOf(groups));
    }

    SealedManifestMetadata readSealedManifestMetadata() {
        JsonNode manifest = read(BENCHMARK_ROOT.resolve("sealed-final/manifest.json"));
        String combined = manifest.path("combinedSha256").asText();
        boolean opened = manifest.path("opened").asBoolean(true);
        boolean searchExecuted = manifest.path("searchExecuted").asBoolean(true);
        if (!SEALED_FINAL_SHA256.equals(combined) || opened || searchExecuted) {
            throw new IllegalStateException("SEALED FINAL metadata changed or was opened");
        }
        int verifiedFileCount = verifySealedManifestFiles(manifest);
        return new SealedManifestMetadata(combined, opened, searchExecuted, verifiedFileCount);
    }

    LongFormManifestMetadata readLongFormManifestMetadata() {
        JsonNode manifest = read(LONG_FORM_BENCHMARK_ROOT.resolve("manifest.json"));
        String datasetVersion = required(manifest, "datasetVersion");
        String previousVersion = required(manifest, "previousVersion");
        String executionPolicy = required(manifest, "executionPolicy");
        if (!LONG_FORM_DATASET_VERSION.equals(datasetVersion)
                || !ORIGINAL_DATASET_VERSION.equals(previousVersion)
                || !"DEV_CAL_EVALUATION_ALLOWED".equals(executionPolicy)) {
            throw new IllegalStateException("Long-form DEV/CAL manifest contract changed");
        }
        return new LongFormManifestMetadata(
                datasetVersion,
                previousVersion,
                required(manifest, "combinedSha256"),
                manifest.path("counts").path("documents").asInt(),
                manifest.path("counts").path("queries").asInt(),
                executionPolicy);
    }

    RobustnessManifestMetadata readRobustnessManifestMetadata() {
        return readRobustnessManifestMetadata(ROBUSTNESS_BENCHMARK_ROOT, ROBUSTNESS_SHA256);
    }

    RobustnessManifestMetadata readRobustnessManifestMetadata(Path root, String expectedCombinedSha256) {
        JsonNode manifest = read(root.resolve("manifest.json"));
        String datasetVersion = required(manifest, "datasetVersion");
        String previousVersion = required(manifest, "previousVersion");
        String executionPolicy = required(manifest, "executionPolicy");
        String policyRevision = required(manifest, "b3PolicyRevision");
        String combinedSha256 = required(manifest, "combinedSha256");
        if (!ROBUSTNESS_DATASET_VERSION.equals(datasetVersion)
                || !LONG_FORM_DATASET_VERSION.equals(previousVersion)
                || !"DEV_CAL_EVALUATION_ALLOWED".equals(executionPolicy)
                || !"01d9ae2f90eff691d96041579e42a02aa04a3486".equals(policyRevision)
                || !expectedCombinedSha256.equals(combinedSha256)
                || manifest.path("counts").path("userBundles").asInt() != 6
                || manifest.path("counts").path("documents").asInt() != 6
                || manifest.path("counts").path("queries").asInt() != 24
                || manifest.path("counts").path("directQueries").asInt() != 24) {
            throw new IllegalStateException("Robustness DEV/CAL manifest contract changed");
        }
        int verifiedFileCount = verifyRobustnessManifestFiles(root, manifest, expectedCombinedSha256);
        return new RobustnessManifestMetadata(
                datasetVersion,
                previousVersion,
                combinedSha256,
                manifest.path("counts").path("userBundles").asInt(),
                manifest.path("counts").path("documents").asInt(),
                manifest.path("counts").path("queries").asInt(),
                manifest.path("counts").path("directQueries").asInt(),
                executionPolicy,
                policyRevision,
                verifiedFileCount);
    }

    private void requireArtifact(JsonNode artifact, Split split, String expectedDatasetVersion) {
        if (!expectedDatasetVersion.equals(artifact.path("datasetVersion").asText())) {
            throw new IllegalArgumentException("Unexpected Search V3 dataset version");
        }
        if (!split.manifestName().equals(artifact.path("split").asText())) {
            throw new IllegalArgumentException("Artifact split mismatch");
        }
    }

    private List<UserBundle> loadBundles(Path splitDirectory, JsonNode nodes) {
        List<UserBundle> bundles = new ArrayList<>();
        for (JsonNode node : nodes) {
            String userBundleId = required(node, "userBundleId");
            List<SourceDocument> activeDocuments = new ArrayList<>();
            for (JsonNode document : node.path("documents")) {
                if (!document.path("active").asBoolean()) {
                    continue;
                }
                if (!"TXT".equals(document.path("fileType").asText())) {
                    throw new IllegalArgumentException(
                            "PRZ-026 Phase 1 supports only the comparable TXT seed: "
                                    + document.path("versionId").asText());
                }
                Path sourcePath = splitDirectory.resolve(required(document, "contentPath")).normalize();
                if (!sourcePath.startsWith(splitDirectory)) {
                    throw new IllegalArgumentException("Source path escapes its split directory");
                }
                String source = readText(sourcePath);
                String expectedSha = required(document, "contentSha256");
                if (!expectedSha.equals(sha256(sourcePath))) {
                    throw new IllegalArgumentException("Source SHA-256 mismatch: " + sourcePath);
                }
                StructuralDocument structural = new StructuralDocument(
                        userBundleId,
                        required(document, "documentId"),
                        required(document, "versionId"),
                        splitDirectory.relativize(sourcePath).toString().replace('\\', '/'),
                        null,
                        source,
                        expectedSha);
                activeDocuments.add(new SourceDocument(
                        userBundleId,
                        structural,
                        required(document, "documentType"),
                        required(document, "documentStructure"),
                        required(document, "language")));
            }
            bundles.add(new UserBundle(
                    userBundleId,
                    required(node, "professionGroup"),
                    required(node, "profession"),
                    required(node, "languageProfile"),
                    List.copyOf(activeDocuments)));
        }
        return bundles;
    }

    private Map<String, GoldUnit> loadUnits(JsonNode nodes) {
        Map<String, GoldUnit> result = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            String id = required(node, "evidenceUnitId");
            rejectRuntimeId(id);
            List<GoldSpan> spans = new ArrayList<>();
            for (JsonNode span : node.path("sourceSpans")) {
                spans.add(span(span));
            }
            if (spans.isEmpty()) {
                throw new IllegalArgumentException("Evidence unit has no source span: " + id);
            }
            GoldUnit value = new GoldUnit(
                    id,
                    required(node, "userBundleId"),
                    required(node, "parentId"),
                    required(node, "groupId"),
                    required(node, "documentId"),
                    required(node, "versionId"),
                    required(node, "sourceFactId"),
                    List.copyOf(spans));
            if (result.put(id, value) != null) {
                throw new IllegalArgumentException("Duplicate evidence unit ID: " + id);
            }
        }
        return result;
    }

    private Map<String, GoldParent> loadParents(JsonNode nodes) {
        Map<String, GoldParent> result = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            String id = required(node, "parentId");
            rejectRuntimeId(id);
            GoldParent value = new GoldParent(
                    id,
                    required(node, "userBundleId"),
                    required(node, "documentId"),
                    required(node, "versionId"),
                    span(node.path("sourceSpan")));
            if (result.put(id, value) != null) {
                throw new IllegalArgumentException("Duplicate parent ID: " + id);
            }
        }
        return result;
    }

    private Map<String, GoldGroup> loadGroups(JsonNode nodes) {
        Map<String, GoldGroup> result = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            String id = required(node, "groupId");
            rejectRuntimeId(id);
            List<String> unitIds = strings(node.path("evidenceUnitIds"));
            if (unitIds.isEmpty()) {
                throw new IllegalArgumentException("Evidence group has no units: " + id);
            }
            GoldGroup value = new GoldGroup(
                    id,
                    required(node, "userBundleId"),
                    required(node, "sourceFactId"),
                    unitIds);
            if (result.put(id, value) != null) {
                throw new IllegalArgumentException("Duplicate group ID: " + id);
            }
        }
        return result;
    }

    private void validateGoldGraph(
            Map<String, GoldUnit> units,
            Map<String, GoldParent> parents,
            Map<String, GoldGroup> groups) {
        for (GoldUnit unit : units.values()) {
            GoldParent parent = parents.get(unit.parentId());
            GoldGroup group = groups.get(unit.groupId());
            if (parent == null || group == null) {
                throw new IllegalArgumentException("Gold unit references a missing parent/group: "
                        + unit.evidenceUnitId());
            }
            if (!unit.userBundleId().equals(parent.userBundleId())
                    || !unit.userBundleId().equals(group.userBundleId())
                    || !unit.documentId().equals(parent.documentId())
                    || !unit.versionId().equals(parent.versionId())
                    || !unit.sourceFactId().equals(group.sourceFactId())
                    || !group.evidenceUnitIds().contains(unit.evidenceUnitId())) {
                throw new IllegalArgumentException("Gold unit parent/group lineage mismatch: "
                        + unit.evidenceUnitId());
            }
            for (GoldSpan span : unit.sourceSpans()) {
                if (!unit.documentId().equals(span.documentId())
                        || !unit.versionId().equals(span.versionId())) {
                    throw new IllegalArgumentException("Gold unit span lineage mismatch: " + span.spanId());
                }
                GoldSpan parentSpan = parent.sourceSpan();
                if (!java.util.Objects.equals(parentSpan.page(), span.page())
                        || parentSpan.codePointStart() > span.codePointStart()
                        || parentSpan.codePointEnd() < span.codePointEnd()) {
                    throw new IllegalArgumentException("Gold unit escapes its parent span: "
                            + unit.evidenceUnitId());
                }
            }
        }
        for (GoldGroup group : groups.values()) {
            for (String unitId : group.evidenceUnitIds()) {
                GoldUnit unit = units.get(unitId);
                if (unit == null
                        || !group.groupId().equals(unit.groupId())
                        || !group.userBundleId().equals(unit.userBundleId())) {
                    throw new IllegalArgumentException("Gold group unit lineage mismatch: "
                            + group.groupId());
                }
            }
        }
    }

    private List<Query> loadQueries(JsonNode nodes, Map<String, GoldUnit> units, Split split) {
        List<Query> result = new ArrayList<>();
        Set<String> ids = new java.util.HashSet<>();
        for (JsonNode node : nodes) {
            String id = required(node, "queryId");
            if (!ids.add(id)) {
                throw new IllegalArgumentException("Duplicate query ID: " + id);
            }
            // The split lives at artifact level and is attached by DatasetSlice below.
            List<AspectRequirement> aspects = new ArrayList<>();
            List<ExpectedEvidence> allExpected = new ArrayList<>();
            for (JsonNode aspect : node.path("aspects")) {
                List<ExpectedEvidence> expected = new ArrayList<>();
                for (JsonNode relation : aspect.path("expectedEvidence")) {
                    String unitId = required(relation, "evidenceUnitId");
                    String supportRelation = required(relation, "supportRelation");
                    if (!ALLOWED_RELATIONS.contains(supportRelation)) {
                        throw new IllegalArgumentException("Unknown support relation: " + supportRelation);
                    }
                    if (!units.containsKey(unitId)) {
                        throw new IllegalArgumentException("Query references missing evidence unit: " + unitId);
                    }
                    ExpectedEvidence value = new ExpectedEvidence(unitId, supportRelation);
                    expected.add(value);
                    allExpected.add(value);
                }
                aspects.add(new AspectRequirement(
                        required(aspect, "aspectId"),
                        aspect.path("required").asBoolean(),
                        aspect.path("minEvidenceGroups").asInt(),
                        strings(aspect.path("requiredEvidenceGroupIds")),
                        List.copyOf(expected)));
            }
            JsonNode expression = node.path("aspectExpression");
            String operator = required(expression, "operator");
            if (!Set.of("ALL", "ANY").contains(operator)) {
                throw new IllegalArgumentException("Unknown aspect expression operator: " + operator);
            }
            AspectExpression aspectExpression = new AspectExpression(
                    operator,
                    strings(expression.path("requiredAspectIds")),
                    expression.path("minShouldMatch").asInt());
            validateQueryRequirements(id, aspects, aspectExpression, units);
            result.add(new Query(
                    id,
                    required(node, "userBundleId"),
                    split,
                    required(node, "query"),
                    required(node, "answerability"),
                    required(node, "language"),
                    strings(node.path("categories")),
                    aspectExpression,
                    List.copyOf(aspects),
                    List.copyOf(allExpected)));
        }
        return result;
    }

    private void validateQueryRequirements(
            String queryId,
            List<AspectRequirement> aspects,
            AspectExpression expression,
            Map<String, GoldUnit> units) {
        Map<String, AspectRequirement> byId = new LinkedHashMap<>();
        for (AspectRequirement aspect : aspects) {
            if (byId.put(aspect.aspectId(), aspect) != null) {
                throw new IllegalArgumentException("Duplicate query aspect ID: " + queryId);
            }
            Set<String> directGroups = aspect.expectedEvidence().stream()
                    .filter(expected -> "DIRECT_SUPPORT".equals(expected.supportRelation()))
                    .map(expected -> units.get(expected.evidenceUnitId()).groupId())
                    .collect(java.util.stream.Collectors.toSet());
            if (!directGroups.containsAll(aspect.requiredEvidenceGroupIds())) {
                throw new IllegalArgumentException("Required evidence group is not DIRECT_SUPPORT: " + queryId);
            }
            if (aspect.minEvidenceGroups() < 0
                    || aspect.minEvidenceGroups() > directGroups.size()) {
                throw new IllegalArgumentException("Invalid minEvidenceGroups: " + queryId);
            }
        }
        if (expression.requiredAspectIds().isEmpty()
                || expression.requiredAspectIds().stream().anyMatch(id -> !byId.containsKey(id))
                || expression.minShouldMatch() < 1
                || expression.minShouldMatch() > expression.requiredAspectIds().size()
                || ("ALL".equals(expression.operator())
                        && expression.minShouldMatch() != expression.requiredAspectIds().size())
                || ("ANY".equals(expression.operator()) && expression.minShouldMatch() != 1)) {
            throw new IllegalArgumentException("Invalid aspect expression: " + queryId);
        }
    }

    private GoldSpan span(JsonNode node) {
        return new GoldSpan(
                required(node, "spanId"),
                required(node, "documentId"),
                required(node, "versionId"),
                node.path("page").isNull() ? null : node.path("page").asInt(),
                node.path("charStart").asInt(),
                node.path("charEnd").asInt(),
                node.path("lineStart").asInt(),
                node.path("lineEnd").asInt(),
                required(node, "text"),
                required(node, "textSha256"));
    }

    private void validateGrounding(
            Map<String, SourceDocument> activeDocuments,
            Map<String, GoldUnit> units,
            Map<String, GoldParent> parents) {
        units.values().stream()
                .filter(unit -> activeDocuments.containsKey(unit.versionId()))
                .forEach(unit -> {
                    SourceDocument document = activeDocuments.get(unit.versionId());
                    if (!unit.userBundleId().equals(document.userBundleId())) {
                        throw new IllegalArgumentException("Gold unit owner/document mismatch: "
                                + unit.evidenceUnitId());
                    }
                    unit.sourceSpans().forEach(span -> validateSpan(document, span));
                });
        parents.values().stream()
                .filter(parent -> activeDocuments.containsKey(parent.versionId()))
                .forEach(parent -> {
                    SourceDocument document = activeDocuments.get(parent.versionId());
                    if (!parent.userBundleId().equals(document.userBundleId())) {
                        throw new IllegalArgumentException("Gold parent owner/document mismatch: "
                                + parent.parentId());
                    }
                    validateSpan(document, parent.sourceSpan());
                });
    }

    private void validateSpan(SourceDocument document, GoldSpan span) {
        if (!document.documentId().equals(span.documentId())
                || !document.versionId().equals(span.versionId())
                || !java.util.Objects.equals(document.structuralDocument().page(), span.page())) {
            throw new IllegalArgumentException("Gold span document/version/page mismatch: " + span.spanId());
        }
        String source = document.structuralDocument().sourceText();
        int codePointLength = source.codePointCount(0, source.length());
        if (span.codePointStart() < 0
                || span.codePointEnd() <= span.codePointStart()
                || span.codePointEnd() > codePointLength) {
            throw new IllegalArgumentException("Gold span range is outside its source: " + span.spanId());
        }
        int charStart = source.offsetByCodePoints(0, span.codePointStart());
        int charEnd = source.offsetByCodePoints(0, span.codePointEnd());
        String actual = source.substring(charStart, charEnd);
        int lineStart = 1 + countNewlines(source, 0, charStart);
        int lineEnd = lineStart + countNewlines(source, charStart, Math.max(charStart, charEnd - 1));
        if (!actual.equals(span.text())
                || !StructuralBlockParser.sha256(actual).equals(span.textSha256())
                || lineStart != span.lineStart()
                || lineEnd != span.lineEnd()) {
            throw new IllegalArgumentException("Gold span text/hash/line mismatch: " + span.spanId());
        }
    }

    private int countNewlines(String value, int start, int end) {
        int count = 0;
        for (int index = start; index < end; index++) {
            if (value.charAt(index) == '\n') {
                count++;
            }
        }
        return count;
    }

    private void rejectRuntimeId(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (UUID.matcher(id).matches()
                || normalized.contains("chunkid")
                || normalized.contains("runtime")
                || normalized.contains("db-parent")) {
            throw new IllegalArgumentException("Runtime-generated ID is forbidden in Search V3 gold: " + id);
        }
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required field: " + field);
        }
        return value;
    }

    private List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        node.forEach(value -> values.add(value.asText()));
        return List.copyOf(values);
    }

    private JsonNode read(Path path) {
        try {
            return mapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
        }
        catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read Search V3 evaluation artifact: " + path, exception);
        }
    }

    private String readText(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        }
        catch (IOException exception) {
            throw new IllegalArgumentException("Cannot read source fixture: " + path, exception);
        }
    }

    private String sha256(Path path) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(path)));
        }
        catch (IOException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Cannot hash benchmark fixture", exception);
        }
    }

    private int verifySealedManifestFiles(JsonNode manifest) {
        List<String> combinedEntries = new ArrayList<>();
        int count = 0;
        for (JsonNode entry : manifest.path("files")) {
            String relative = required(entry, "path");
            Path file = BENCHMARK_ROOT.resolve(relative).normalize();
            if (!file.startsWith(BENCHMARK_ROOT.normalize()) || !Files.isRegularFile(file)) {
                throw new IllegalStateException("SEALED FINAL manifest path is invalid: " + relative);
            }
            String expectedHash = required(entry, "sha256");
            long expectedBytes = entry.path("bytes").asLong(-1);
            try {
                if (Files.size(file) != expectedBytes || !expectedHash.equals(sha256(file))) {
                    throw new IllegalStateException("SEALED FINAL file hash/size mismatch: " + relative);
                }
            }
            catch (IOException exception) {
                throw new IllegalStateException("Cannot inspect SEALED FINAL fixture: " + relative, exception);
            }
            combinedEntries.add(relative + "\0" + expectedHash + "\n");
            count++;
        }
        combinedEntries.sort(String::compareTo);
        String actualCombined = StructuralBlockParser.sha256(String.join("", combinedEntries));
        if (!required(manifest, "combinedSha256").equals(actualCombined)) {
            throw new IllegalStateException("SEALED FINAL combined hash mismatch");
        }
        return count;
    }

    private int verifyRobustnessManifestFiles(Path root, JsonNode manifest, String expectedCombinedSha256) {
        List<String> combinedEntries = new ArrayList<>();
        int count = 0;
        root = root.normalize();
        for (JsonNode entry : manifest.path("files")) {
            String relative = required(entry, "path");
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                throw new IllegalStateException("Robustness manifest path is invalid: " + relative);
            }
            String expectedHash = required(entry, "sha256");
            long expectedBytes = entry.path("bytes").asLong(-1);
            try {
                if (Files.size(file) != expectedBytes || !expectedHash.equals(sha256(file))) {
                    throw new IllegalStateException("Robustness file hash/size mismatch: " + relative);
                }
            }
            catch (IOException exception) {
                throw new IllegalStateException("Cannot inspect robustness fixture: " + relative, exception);
            }
            combinedEntries.add(relative + ":" + expectedHash);
            count++;
        }
        String actualCombined = StructuralBlockParser.sha256(String.join("\n", combinedEntries));
        if (!expectedCombinedSha256.equals(actualCombined) || count != 16) {
            throw new IllegalStateException("Robustness combined hash or file count mismatch");
        }
        return count;
    }

    enum Split {
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

        static Split fromManifest(String value) {
            for (Split split : values()) {
                if (split.manifestName.equals(value)) {
                    return split;
                }
            }
            throw new IllegalArgumentException("Only DEV and CALIBRATION are allowed: " + value);
        }
    }

    record DatasetSlice(
            String datasetVersion,
            Split split,
            String manifestCombinedSha256,
            List<UserBundle> bundles,
            List<Query> queries,
            Map<String, SourceDocument> activeDocumentsByVersion,
            Map<String, GoldUnit> units,
            Map<String, GoldParent> parents,
            Map<String, GoldGroup> groups) {

        DatasetSlice {
            queries = queries.stream()
                    .map(query -> new Query(
                            query.queryId(), query.userBundleId(), split, query.text(), query.answerability(),
                            query.language(), query.categories(), query.aspectExpression(), query.aspects(),
                            query.allExpectedEvidence()))
                    .toList();
        }
    }

    record UserBundle(
            String userBundleId,
            String professionGroup,
            String profession,
            String languageProfile,
            List<SourceDocument> activeDocuments) {
    }

    record SourceDocument(
            String userBundleId,
            StructuralDocument structuralDocument,
            String documentType,
            String documentStructure,
            String language) {

        String documentId() {
            return structuralDocument.documentId();
        }

        String versionId() {
            return structuralDocument.versionId();
        }
    }

    record Query(
            String queryId,
            String userBundleId,
            Split split,
            String text,
            String answerability,
            String language,
            List<String> categories,
            AspectExpression aspectExpression,
            List<AspectRequirement> aspects,
            List<ExpectedEvidence> allExpectedEvidence) {

        boolean hasDirectSupport() {
            return allExpectedEvidence.stream()
                    .anyMatch(expected -> "DIRECT_SUPPORT".equals(expected.supportRelation()));
        }
    }

    record AspectExpression(String operator, List<String> requiredAspectIds, int minShouldMatch) {
    }

    record AspectRequirement(
            String aspectId,
            boolean required,
            int minEvidenceGroups,
            List<String> requiredEvidenceGroupIds,
            List<ExpectedEvidence> expectedEvidence) {
    }

    record ExpectedEvidence(String evidenceUnitId, String supportRelation) {
    }

    record GoldUnit(
            String evidenceUnitId,
            String userBundleId,
            String parentId,
            String groupId,
            String documentId,
            String versionId,
            String sourceFactId,
            List<GoldSpan> sourceSpans) {
    }

    record GoldParent(
            String parentId,
            String userBundleId,
            String documentId,
            String versionId,
            GoldSpan sourceSpan) {
    }

    record GoldGroup(String groupId, String userBundleId, String sourceFactId, List<String> evidenceUnitIds) {
    }

    record GoldSpan(
            String spanId,
            String documentId,
            String versionId,
            Integer page,
            int codePointStart,
            int codePointEnd,
            int lineStart,
            int lineEnd,
            String text,
            String textSha256) {
    }

    record SealedManifestMetadata(
            String combinedSha256,
            boolean opened,
            boolean searchExecuted,
            int verifiedFileCount) {
    }

    record LongFormManifestMetadata(
            String datasetVersion,
            String previousVersion,
            String combinedSha256,
            int documentCount,
            int queryCount,
            String executionPolicy) {
    }

    record RobustnessManifestMetadata(
            String datasetVersion,
            String previousVersion,
            String combinedSha256,
            int userBundleCount,
            int documentCount,
            int queryCount,
            int directQueryCount,
            String executionPolicy,
            String b3PolicyRevision,
            int verifiedFileCount) {
    }
}
