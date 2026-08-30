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
    static final String DATASET_VERSION = "search-v3-fresh-seed-1.0.1";
    static final String OVERALL_SHA256 = "1f36c4bbb6948b97c4321821cc3d6b8a9e38ab44b81adb1594614c6f7e97289e";
    static final String SEALED_FINAL_SHA256 = "e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383";

    private static final Pattern UUID = Pattern.compile(
            "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");
    private static final Set<String> ALLOWED_RELATIONS = Set.of(
            "DIRECT_SUPPORT", "RELATED", "CONTRADICTS", "INSUFFICIENT");

    private final ObjectMapper mapper = new ObjectMapper();

    DatasetSlice load(Split split) {
        return load(BENCHMARK_ROOT.resolve(split.directory()), split);
    }

    DatasetSlice load(Path splitDirectory, Split expectedSplit) {
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
        requireArtifact(manifest, expectedSplit);
        requireArtifact(corpus, expectedSplit);
        requireArtifact(questions, expectedSplit);
        requireArtifact(gold, expectedSplit);

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
        }
        return new DatasetSlice(
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
        return new SealedManifestMetadata(combined, opened, searchExecuted);
    }

    private void requireArtifact(JsonNode artifact, Split split) {
        if (!DATASET_VERSION.equals(artifact.path("datasetVersion").asText())) {
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
            List<String> unitIds = strings(node.path("evidenceUnitIds"));
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
                        List.copyOf(expected)));
            }
            result.add(new Query(
                    id,
                    required(node, "userBundleId"),
                    split,
                    required(node, "query"),
                    required(node, "answerability"),
                    required(node, "language"),
                    strings(node.path("categories")),
                    List.copyOf(aspects),
                    List.copyOf(allExpected)));
        }
        return result;
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
                node.path("lineEnd").asInt());
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
                            query.language(), query.categories(), query.aspects(), query.allExpectedEvidence()))
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
            List<AspectRequirement> aspects,
            List<ExpectedEvidence> allExpectedEvidence) {

        boolean hasDirectSupport() {
            return allExpectedEvidence.stream()
                    .anyMatch(expected -> "DIRECT_SUPPORT".equals(expected.supportRelation()));
        }
    }

    record AspectRequirement(
            String aspectId,
            boolean required,
            int minEvidenceGroups,
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
            int lineEnd) {
    }

    record SealedManifestMetadata(String combinedSha256, boolean opened, boolean searchExecuted) {
    }
}
