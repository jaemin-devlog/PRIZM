package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.search.evaluation.searchv3.typed.DeterministicTypedQueryParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Gold loader reachable only after PRZ-042 predictions have been frozen and verified. */
final class Prz042FinalGold {

    private static final String SUITE = "FRESH_FINAL";
    private static final Set<String> SUPPORT_RELATIONS = Set.of(
            "DIRECT_SUPPORT", "RELATED", "CONTRADICTS", "INSUFFICIENT");

    private final ObjectMapper mapper = new ObjectMapper();
    private final DeterministicTypedQueryParser typedQueryParser = new DeterministicTypedQueryParser();

    SearchV3MinimalShadowGold.GoldSnapshot load(
            Prz042FinalFreeze.VerifiedPredictions verifiedPredictions,
            Prz042FinalDataset.RuntimeInput runtime) {
        Objects.requireNonNull(verifiedPredictions, "verifiedPredictions");
        Objects.requireNonNull(runtime, "runtime");
        Prz042FinalFreeze.VerifiedPredictions reloaded = new Prz042FinalFreeze()
                .reloadVerifiedPredictions(verifiedPredictions.searchStarted());
        if (!verifiedPredictions.canonicalSha256().equals(reloaded.canonicalSha256())
                || !verifiedPredictions.fileSha256().equals(reloaded.fileSha256())
                || !verifiedPredictions.receiptSha256().equals(reloaded.receiptSha256())) {
            throw new IllegalStateException("PRZ-042 frozen prediction receipt changed before Gold access");
        }
        if (!verifiedPredictions.fileSha256().equals(
                Prz042FinalFreeze.sha256(verifiedPredictions.outputPath()))) {
            throw new IllegalStateException("PRZ-042 prediction artifact changed before Gold access");
        }
        if (!runtime.contractSha256().equals(
                verifiedPredictions.attempt().input().contractSha256())
                || !runtime.attemptSha256().equals(
                        verifiedPredictions.attempt().attemptSha256())
                || !runtime.canonicalSha256().equals(
                        verifiedPredictions.searchStarted().runtime().canonicalSha256())) {
            throw new IllegalStateException("PRZ-042 runtime and frozen predictions belong to different attempts");
        }

        JsonNode manifest = read(runtime.splitRoot().resolve("manifest.json"));
        requireIdentity(manifest, "MANIFEST", runtime);
        if (!runtime.manifestSha256().equals(Prz042FinalDataset.sha256(readBytes(
                        runtime.splitRoot().resolve("manifest.json"))))
                || !runtime.combinedSha256().equals(required(manifest, "combinedSha256"))) {
            throw new IllegalStateException("PRZ-042 Gold manifest identity changed after prediction freeze");
        }
        Map<String, ManifestFile> manifestFiles = manifestFiles(manifest);
        JsonNode questions = readVerified(runtime, "questions.json", manifestFiles);
        JsonNode goldRoot = readVerified(runtime, "gold-evidence.json", manifestFiles);
        requireIdentity(questions, "QUESTIONS", runtime);
        requireIdentity(goldRoot, "GOLD_EVIDENCE", runtime);

        Map<String, Prz042FinalDataset.RuntimeDocument> documentsByVersion = runtime.documents().stream()
                .collect(Collectors.toMap(
                        Prz042FinalDataset.RuntimeDocument::versionId,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalStateException("duplicate runtime version while joining Gold");
                        },
                        LinkedHashMap::new));
        Map<String, SearchV3MinimalShadowGold.GoldParent> parents = parents(goldRoot, documentsByVersion);
        Map<String, SearchV3MinimalShadowGold.GoldGroup> groups = groups(goldRoot);
        Map<String, SearchV3MinimalShadowGold.GoldUnit> units = units(
                goldRoot, documentsByVersion, parents, groups);
        Map<String, SearchV3MinimalShadowGold.GoldQuery> queries = queries(
                questions, runtime, units, groups);
        validateRuntimeParity(runtime, queries);

        return new SearchV3MinimalShadowGold.GoldSnapshot(
                Map.copyOf(queries), Map.copyOf(units), Map.copyOf(parents), Map.copyOf(groups));
    }

    private Map<String, SearchV3MinimalShadowGold.GoldParent> parents(
            JsonNode goldRoot,
            Map<String, Prz042FinalDataset.RuntimeDocument> documentsByVersion) {
        Map<String, SearchV3MinimalShadowGold.GoldParent> result = new LinkedHashMap<>();
        for (JsonNode node : goldRoot.path("parents")) {
            SearchV3MinimalShadowGold.GoldSpan span = span(node.path("sourceSpan"), documentsByVersion);
            SearchV3MinimalShadowGold.GoldParent parent = new SearchV3MinimalShadowGold.GoldParent(
                    required(node, "parentId"),
                    required(node, "userBundleId"),
                    required(node, "documentId"),
                    required(node, "versionId"),
                    span);
            if (!parent.documentId().equals(span.documentId())
                    || !parent.versionId().equals(span.versionId())) {
                throw new IllegalStateException("Gold parent/source span identity differs: " + parent.parentId());
            }
            putUnique(result, parent.parentId(), parent, "Gold parent");
        }
        return result;
    }

    private Map<String, SearchV3MinimalShadowGold.GoldGroup> groups(JsonNode goldRoot) {
        Map<String, SearchV3MinimalShadowGold.GoldGroup> result = new LinkedHashMap<>();
        for (JsonNode node : goldRoot.path("evidenceGroups")) {
            SearchV3MinimalShadowGold.GoldGroup group = new SearchV3MinimalShadowGold.GoldGroup(
                    required(node, "groupId"),
                    required(node, "userBundleId"),
                    strings(node.path("evidenceUnitIds")));
            if (group.evidenceUnitIds().isEmpty()
                    || new LinkedHashSet<>(group.evidenceUnitIds()).size() != group.evidenceUnitIds().size()) {
                throw new IllegalStateException("Gold group inventory is empty or duplicated: " + group.groupId());
            }
            putUnique(result, group.groupId(), group, "Gold group");
        }
        return result;
    }

    private Map<String, SearchV3MinimalShadowGold.GoldUnit> units(
            JsonNode goldRoot,
            Map<String, Prz042FinalDataset.RuntimeDocument> documentsByVersion,
            Map<String, SearchV3MinimalShadowGold.GoldParent> parents,
            Map<String, SearchV3MinimalShadowGold.GoldGroup> groups) {
        Map<String, SearchV3MinimalShadowGold.GoldUnit> result = new LinkedHashMap<>();
        for (JsonNode node : goldRoot.path("evidenceUnits")) {
            List<SearchV3MinimalShadowGold.GoldSpan> spans = new ArrayList<>();
            for (JsonNode sourceSpan : node.path("sourceSpans")) {
                spans.add(span(sourceSpan, documentsByVersion));
            }
            SearchV3MinimalShadowGold.GoldUnit unit = new SearchV3MinimalShadowGold.GoldUnit(
                    required(node, "evidenceUnitId"),
                    required(node, "userBundleId"),
                    required(node, "parentId"),
                    required(node, "groupId"),
                    required(node, "documentId"),
                    required(node, "versionId"),
                    List.copyOf(spans));
            SearchV3MinimalShadowGold.GoldParent parent = parents.get(unit.parentId());
            SearchV3MinimalShadowGold.GoldGroup group = groups.get(unit.groupId());
            if (parent == null || group == null
                    || !parent.userBundleId().equals(unit.userBundleId())
                    || !group.userBundleId().equals(unit.userBundleId())
                    || !parent.documentId().equals(unit.documentId())
                    || !parent.versionId().equals(unit.versionId())
                    || !group.evidenceUnitIds().contains(unit.evidenceUnitId())
                    || spans.isEmpty()
                    || spans.stream().anyMatch(value -> !value.documentId().equals(unit.documentId())
                            || !value.versionId().equals(unit.versionId()))) {
                throw new IllegalStateException("Gold unit lineage is invalid: " + unit.evidenceUnitId());
            }
            putUnique(result, unit.evidenceUnitId(), unit, "Gold unit");
        }
        for (SearchV3MinimalShadowGold.GoldGroup group : groups.values()) {
            if (!result.keySet().containsAll(group.evidenceUnitIds())) {
                throw new IllegalStateException("Gold group references an unknown unit: " + group.groupId());
            }
        }
        return result;
    }

    private Map<String, SearchV3MinimalShadowGold.GoldQuery> queries(
            JsonNode questions,
            Prz042FinalDataset.RuntimeInput runtime,
            Map<String, SearchV3MinimalShadowGold.GoldUnit> units,
            Map<String, SearchV3MinimalShadowGold.GoldGroup> groups) {
        Map<String, SearchV3MinimalShadowGold.GoldQuery> result = new LinkedHashMap<>();
        for (JsonNode node : questions.path("queries")) {
            String queryId = required(node, "queryId");
            String ownerId = required(node, "userBundleId");
            Map<String, String> allRelations = new LinkedHashMap<>();
            List<SearchV3MinimalShadowGold.Aspect> aspects = new ArrayList<>();
            for (JsonNode aspect : node.path("aspects")) {
                Map<String, String> relations = expectedRelations(aspect.path("expectedEvidence"));
                relations.forEach((unitId, relation) -> putSame(
                        allRelations, unitId, relation, "query relation"));
                SearchV3MinimalShadowGold.Aspect value = new SearchV3MinimalShadowGold.Aspect(
                        required(aspect, "aspectId"),
                        aspect.path("required").asBoolean(false),
                        aspect.path("minEvidenceGroups").asInt(0),
                        strings(aspect.path("requiredEvidenceGroupIds")),
                        relations);
                if (!groups.keySet().containsAll(value.requiredEvidenceGroupIds())) {
                    throw new IllegalStateException("query aspect references an unknown Gold group: " + queryId);
                }
                aspects.add(value);
            }
            for (String unitId : allRelations.keySet()) {
                SearchV3MinimalShadowGold.GoldUnit unit = units.get(unitId);
                if (unit == null || !unit.userBundleId().equals(ownerId)) {
                    throw new IllegalStateException("query relation crosses owner or references unknown unit: "
                            + queryId + " / " + unitId);
                }
            }
            String answerability = required(node, "answerability");
            boolean direct = allRelations.containsValue("DIRECT_SUPPORT");
            if (("SUPPORTED".equals(answerability) && !direct)
                    || ("NOT_SUPPORTED".equals(answerability) && direct)) {
                throw new IllegalStateException("query answerability/relation contract is invalid: " + queryId);
            }
            JsonNode expression = node.path("aspectExpression");
            String text = required(node, "query");
            SearchV3MinimalShadowGold.GoldQuery query = new SearchV3MinimalShadowGold.GoldQuery(
                    SUITE,
                    runtime.datasetVersion(),
                    runtime.split(),
                    queryId,
                    ownerId,
                    text,
                    answerability,
                    required(node, "language"),
                    strings(node.path("categories")),
                    new SearchV3MinimalShadowGold.AspectExpression(
                            required(expression, "operator"),
                            strings(expression.path("requiredAspectIds")),
                            expression.path("minShouldMatch").asInt(0)),
                    List.copyOf(aspects),
                    Map.copyOf(allRelations),
                    typedExpectedState(text, answerability));
            putUnique(result, queryId, query, "Gold query");
        }
        return result;
    }

    private String typedExpectedState(String query, String answerability) {
        if (typedQueryParser.parse(query).isEmpty()) {
            return null;
        }
        return switch (answerability) {
            case "SUPPORTED" -> "FOUND";
            case "PARTIALLY_SUPPORTED" -> "PARTIAL";
            case "NOT_SUPPORTED" -> "NONE";
            default -> throw new IllegalStateException("unknown answerability: " + answerability);
        };
    }

    private SearchV3MinimalShadowGold.GoldSpan span(
            JsonNode node,
            Map<String, Prz042FinalDataset.RuntimeDocument> documentsByVersion) {
        String documentId = required(node, "documentId");
        String versionId = required(node, "versionId");
        int start = node.path("charStart").asInt(-1);
        int end = node.path("charEnd").asInt(-1);
        String expectedText = required(node, "text");
        String expectedHash = required(node, "textSha256");
        Prz042FinalDataset.RuntimeDocument document = documentsByVersion.get(versionId);
        if (document == null
                || !document.documentId().equals(documentId)
                || start < 0
                || end <= start
                || end > document.sourceText().codePointCount(0, document.sourceText().length())) {
            throw new IllegalStateException("Gold span references an invalid runtime source: " + versionId);
        }
        int startOffset = document.sourceText().offsetByCodePoints(0, start);
        int endOffset = document.sourceText().offsetByCodePoints(0, end);
        String actualText = document.sourceText().substring(startOffset, endOffset);
        if (!expectedText.equals(actualText)
                || !expectedHash.equals(Prz042FinalDataset.sha256(expectedText))) {
            throw new IllegalStateException("Gold span text/hash differs from the runtime source: " + versionId);
        }
        return new SearchV3MinimalShadowGold.GoldSpan(
                documentId,
                versionId,
                nullableInt(node, "page"),
                start,
                end,
                expectedHash);
    }

    private void validateRuntimeParity(
            Prz042FinalDataset.RuntimeInput runtime,
            Map<String, SearchV3MinimalShadowGold.GoldQuery> gold) {
        if (runtime.queries().size() != gold.size()) {
            throw new IllegalStateException("PRZ-042 runtime/Gold query count differs");
        }
        for (Prz042FinalDataset.RuntimeQuery runtimeQuery : runtime.queries()) {
            SearchV3MinimalShadowGold.GoldQuery expected = gold.get(runtimeQuery.queryId());
            if (expected == null
                    || !runtimeQuery.userBundleId().equals(expected.userBundleId())
                    || !runtimeQuery.text().equals(expected.text())
                    || !runtimeQuery.language().equals(expected.language())) {
                throw new IllegalStateException("PRZ-042 runtime/Gold query identity differs: "
                        + runtimeQuery.queryId());
            }
        }
    }

    private Map<String, String> expectedRelations(JsonNode values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode value : values) {
            String unitId = required(value, "evidenceUnitId");
            String relation = required(value, "supportRelation");
            if (!SUPPORT_RELATIONS.contains(relation)) {
                throw new IllegalStateException("unknown Gold support relation: " + relation);
            }
            putSame(result, unitId, relation, "aspect relation");
        }
        return Map.copyOf(result);
    }

    private JsonNode readVerified(
            Prz042FinalDataset.RuntimeInput runtime,
            String relative,
            Map<String, ManifestFile> manifestFiles) {
        Path path = runtime.splitRoot().resolve(relative).normalize();
        if (!path.startsWith(runtime.splitRoot())
                || !Files.isRegularFile(path)
                || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("invalid PRZ-042 Gold path: " + path);
        }
        String manifestPath = runtime.splitRoot().getFileName().toString().replace('\\', '/') + "/" + relative;
        ManifestFile expected = manifestFiles.get(manifestPath);
        if (expected == null) {
            throw new IllegalStateException("Gold artifact is absent from the split manifest: " + manifestPath);
        }
        byte[] bytes = readBytes(path);
        if (bytes.length != expected.bytes() || !Prz042FinalDataset.sha256(bytes).equals(expected.sha256())) {
            throw new IllegalStateException("Gold artifact hash/size mismatch: " + manifestPath);
        }
        return read(bytes, path);
    }

    private JsonNode read(Path path) {
        return read(readBytes(path), path);
    }

    private JsonNode read(byte[] bytes, Path path) {
        try {
            return mapper.readTree(bytes);
        }
        catch (RuntimeException exception) {
            throw new IllegalStateException("cannot parse PRZ-042 Gold JSON: " + path, exception);
        }
    }

    private byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot read PRZ-042 Gold input: " + path, exception);
        }
    }

    private Map<String, ManifestFile> manifestFiles(JsonNode manifest) {
        Map<String, ManifestFile> result = new LinkedHashMap<>();
        for (JsonNode node : manifest.path("files")) {
            ManifestFile value = new ManifestFile(
                    required(node, "path").replace('\\', '/'),
                    node.path("bytes").asLong(-1),
                    required(node, "sha256"));
            if (value.bytes() < 0 || result.putIfAbsent(value.path(), value) != null) {
                throw new IllegalStateException("invalid or duplicate manifest file: " + value.path());
            }
        }
        return Map.copyOf(result);
    }

    private void requireIdentity(
            JsonNode root,
            String artifactType,
            Prz042FinalDataset.RuntimeInput runtime) {
        if (!artifactType.equals(required(root, "artifactType"))
                || !runtime.datasetVersion().equals(required(root, "datasetVersion"))
                || !runtime.split().equals(required(root, "split"))) {
            throw new IllegalStateException("PRZ-042 Gold artifact identity changed");
        }
    }

    private List<String> strings(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private Integer nullableInt(JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode()
                ? null : node.path(field).asInt();
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing PRZ-042 Gold field: " + field);
        }
        return value;
    }

    private <T> void putUnique(Map<String, T> values, String key, T value, String label) {
        if (values.putIfAbsent(key, value) != null) {
            throw new IllegalStateException("duplicate " + label + ": " + key);
        }
    }

    private <T> void putSame(Map<String, T> values, String key, T value, String label) {
        T previous = values.putIfAbsent(key, value);
        if (previous != null && !Objects.equals(previous, value)) {
            throw new IllegalStateException(label + " changed for " + key);
        }
    }

    private record ManifestFile(String path, long bytes, String sha256) {
    }
}
