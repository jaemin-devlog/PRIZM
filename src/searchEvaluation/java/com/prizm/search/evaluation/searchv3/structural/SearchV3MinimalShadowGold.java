package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.search.evaluation.searchv3.typed.TypedConstraintStressDataset;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Gold loader that is reachable only after the PRZ-032 output freeze is verified. */
final class SearchV3MinimalShadowGold {

    private static final Path SEMANTIC_ROOT = Path.of(
            "src/searchEvaluation/resources/search-v3-evaluation/semantic-support-stress-1.0.1");

    private final ObjectMapper mapper = new ObjectMapper();

    GoldSnapshot loadAfterOutputVerified(
            SearchV3MinimalShadowFreeze.VerifiedOutput verified,
            SearchV3MinimalShadowDataset.RuntimeInput runtime) {
        Objects.requireNonNull(verified, "verified output");
        Map<String, GoldQuery> queries = new LinkedHashMap<>();
        Map<String, GoldUnit> units = new LinkedHashMap<>();
        Map<String, GoldParent> parents = new LinkedHashMap<>();
        Map<String, GoldGroup> groups = new LinkedHashMap<>();
        SearchV3DenseAblationDataset loader = new SearchV3DenseAblationDataset();
        for (SearchV3DenseAblationDataset.Split split : SearchV3DenseAblationDataset.Split.values()) {
            addDense("ORIGINAL", loader.load(split), null, queries, units, parents, groups);
            addDense("LONG_FORM", loader.loadLongForm(split), null, queries, units, parents, groups);
            addDense("ROBUSTNESS", loader.loadRobustness(split), null, queries, units, parents, groups);
            TypedConstraintStressDataset.DatasetSlice typedSidecar = new TypedConstraintStressDataset().load(
                    TypedConstraintStressDataset.OFFICIAL_1_1_0,
                    split == SearchV3DenseAblationDataset.Split.DEV
                            ? TypedConstraintStressDataset.Split.DEV
                            : TypedConstraintStressDataset.Split.CALIBRATION);
            Map<String, String> typedStates = typedExpectedStates(typedSidecar);
            addDense(
                    "TYPED_STRESS", loader.loadTypedStressOfficial(split), typedStates,
                    queries, units, parents, groups);
            addSemantic(split, queries, units, parents, groups);
        }
        validateRuntimeParity(runtime, queries);
        long directPositive = queries.values().stream().filter(GoldQuery::hasDirectSupport).count();
        long notSupported = queries.values().stream()
                .filter(value -> "NOT_SUPPORTED".equals(value.answerability())).count();
        if (queries.size() != 117 || directPositive != 85 || notSupported != 32) {
            throw new IllegalStateException("PRZ-032 Gold inventory changed: queries=" + queries.size()
                    + " direct=" + directPositive + " negatives=" + notSupported);
        }
        return new GoldSnapshot(
                Map.copyOf(queries), Map.copyOf(units), Map.copyOf(parents), Map.copyOf(groups));
    }

    private void addDense(
            String suite,
            SearchV3DenseAblationDataset.DatasetSlice slice,
            Map<String, String> typedStates,
            Map<String, GoldQuery> queries,
            Map<String, GoldUnit> units,
            Map<String, GoldParent> parents,
            Map<String, GoldGroup> groups) {
        slice.units().values().forEach(value -> putSame(units, value.evidenceUnitId(), new GoldUnit(
                value.evidenceUnitId(), value.userBundleId(), value.parentId(), value.groupId(),
                value.documentId(), value.versionId(), value.sourceSpans().stream()
                        .map(this::span).toList()), "Gold unit"));
        slice.parents().values().forEach(value -> putSame(parents, value.parentId(), new GoldParent(
                value.parentId(), value.userBundleId(), value.documentId(), value.versionId(),
                span(value.sourceSpan())), "Gold parent"));
        slice.groups().values().forEach(value -> putSame(groups, value.groupId(), new GoldGroup(
                value.groupId(), value.userBundleId(), value.evidenceUnitIds()), "Gold group"));
        for (SearchV3DenseAblationDataset.Query query : slice.queries()) {
            Map<String, String> relations = new LinkedHashMap<>();
            query.allExpectedEvidence().forEach(value -> putSame(
                    relations, value.evidenceUnitId(), value.supportRelation(), "query relation"));
            GoldQuery gold = new GoldQuery(
                    suite,
                    slice.datasetVersion(),
                    slice.split().manifestName(),
                    query.queryId(),
                    query.userBundleId(),
                    query.text(),
                    query.answerability(),
                    query.language(),
                    query.categories(),
                    new AspectExpression(
                            query.aspectExpression().operator(),
                            query.aspectExpression().requiredAspectIds(),
                            query.aspectExpression().minShouldMatch()),
                    query.aspects().stream().map(value -> new Aspect(
                            value.aspectId(), value.required(), value.minEvidenceGroups(),
                            value.requiredEvidenceGroupIds(), value.expectedEvidence().stream()
                                    .collect(java.util.stream.Collectors.toMap(
                                            SearchV3DenseAblationDataset.ExpectedEvidence::evidenceUnitId,
                                            SearchV3DenseAblationDataset.ExpectedEvidence::supportRelation,
                                            (left, right) -> left,
                                            LinkedHashMap::new)))).toList(),
                    Map.copyOf(relations),
                    typedStates == null ? null : typedStates.get(query.queryId()));
            putSame(queries, query.queryId(), gold, "Gold query");
        }
    }

    private Map<String, String> typedExpectedStates(TypedConstraintStressDataset.DatasetSlice slice) {
        Map<String, String> result = new LinkedHashMap<>();
        slice.evaluationGold().queryAnnotations().forEach((queryId, annotation) -> {
            Set<String> states = annotation.expectedEvidenceStates().stream()
                    .map(TypedConstraintStressDataset.ExpectedEvidenceState::state)
                    .collect(java.util.stream.Collectors.toSet());
            String state = states.contains("SATISFIED") ? "FOUND"
                    : states.contains("CONTRADICTED") ? "NONE" : "PARTIAL";
            result.put(queryId, state);
        });
        return Map.copyOf(result);
    }

    private void addSemantic(
            SearchV3DenseAblationDataset.Split split,
            Map<String, GoldQuery> queries,
            Map<String, GoldUnit> units,
            Map<String, GoldParent> parents,
            Map<String, GoldGroup> groups) {
        Path root = SEMANTIC_ROOT.resolve(split.directory());
        JsonNode questionRoot = read(root.resolve("questions.json"));
        JsonNode goldRoot = read(root.resolve("gold-evidence.json"));
        if (!SearchV3SemanticOracleDataset.STRESS_VERSION.equals(required(questionRoot, "datasetVersion"))
                || !split.manifestName().equals(required(questionRoot, "split"))
                || !SearchV3SemanticOracleDataset.STRESS_VERSION.equals(required(goldRoot, "datasetVersion"))
                || !split.manifestName().equals(required(goldRoot, "split"))) {
            throw new IllegalStateException("semantic Gold identity changed");
        }
        for (JsonNode node : goldRoot.path("evidenceUnits")) {
            JsonNode source = node.path("sourceSpan");
            GoldSpan span = new GoldSpan(
                    required(node, "documentId"), required(node, "documentVersionId"),
                    nullableInt(source, "page"), source.path("codePointStart").asInt(-1),
                    source.path("codePointEnd").asInt(-1), required(source, "textSha256"));
            String structuralParentId = required(node, "baseParentId");
            GoldUnit unit = new GoldUnit(
                    required(node, "evidenceUnitId"), required(node, "userBundleId"),
                    structuralParentId, required(node, "evidenceGroupId"),
                    required(node, "documentId"), required(node, "documentVersionId"), List.of(span));
            putSame(units, unit.evidenceUnitId(), unit, "semantic Gold unit");
            GoldParent parent = parents.get(structuralParentId);
            if (parent == null) {
                parents.put(structuralParentId, new GoldParent(
                        structuralParentId, unit.userBundleId(), unit.documentId(), unit.versionId(), span));
            }
            else if (!parent.userBundleId().equals(unit.userBundleId())
                    || !parent.documentId().equals(unit.documentId())
                    || !parent.versionId().equals(unit.versionId())) {
                throw new IllegalStateException("semantic/base structural parent identity changed");
            }
            GoldGroup previous = groups.get(unit.groupId());
            List<String> unitIds = new ArrayList<>(previous == null ? List.of() : previous.evidenceUnitIds());
            unitIds.add(unit.evidenceUnitId());
            groups.put(unit.groupId(), new GoldGroup(unit.groupId(), unit.userBundleId(), List.copyOf(unitIds)));
        }
        for (JsonNode node : questionRoot.path("queries")) {
            Map<String, String> relations = expectedRelations(node.path("expectedEvidence"));
            List<Aspect> aspects = new ArrayList<>();
            for (JsonNode aspect : node.path("aspects")) {
                aspects.add(new Aspect(
                        required(aspect, "aspectId"), aspect.path("required").asBoolean(false),
                        aspect.path("minEvidenceGroups").asInt(0), strings(aspect.path("requiredEvidenceGroupIds")),
                        expectedRelations(aspect.path("expectedEvidence"))));
            }
            JsonNode expression = node.path("aspectExpression");
            GoldQuery query = new GoldQuery(
                    "SEMANTIC_STRESS", SearchV3SemanticOracleDataset.STRESS_VERSION,
                    split.manifestName(), required(node, "queryId"), required(node, "userBundleId"),
                    required(node, "query"), required(node, "answerability"), required(node, "language"),
                    strings(node.path("categories")), new AspectExpression(
                            required(expression, "operator"), strings(expression.path("requiredAspectIds")),
                            expression.path("minShouldMatch").asInt(0)),
                    List.copyOf(aspects), relations, null);
            putSame(queries, query.queryId(), query, "semantic Gold query");
        }
    }

    private void validateRuntimeParity(
            SearchV3MinimalShadowDataset.RuntimeInput runtime,
            Map<String, GoldQuery> gold) {
        if (runtime.queries().size() != gold.size()) {
            throw new IllegalStateException("runtime/Gold query count differs");
        }
        for (SearchV3MinimalShadowDataset.RuntimeQuery query : runtime.queries()) {
            GoldQuery expected = gold.get(query.queryId());
            if (expected == null
                    || !query.suite().equals(expected.suite())
                    || !query.datasetVersion().equals(expected.datasetVersion())
                    || !query.split().equals(expected.split())
                    || !query.userBundleId().equals(expected.userBundleId())
                    || !query.text().equals(expected.text())
                    || !query.language().equals(expected.language())
                    || query.typedApplicabilityVerified() != (expected.typedExpectedState() != null)) {
                throw new IllegalStateException("runtime/Gold query identity differs: " + query.queryId());
            }
        }
    }

    private GoldSpan span(SearchV3DenseAblationDataset.GoldSpan value) {
        return new GoldSpan(
                value.documentId(), value.versionId(), value.page(), value.codePointStart(),
                value.codePointEnd(), value.textSha256());
    }

    private Map<String, String> expectedRelations(JsonNode values) {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode value : values) {
            putSame(result, required(value, "evidenceUnitId"), required(value, "supportRelation"),
                    "semantic query relation");
        }
        return Map.copyOf(result);
    }

    private List<String> strings(JsonNode values) {
        List<String> result = new ArrayList<>();
        values.forEach(value -> result.add(value.asText()));
        return List.copyOf(result);
    }

    private JsonNode read(Path path) {
        String portable = path.toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase();
        if (portable.contains("sealed-final") || portable.contains("sealed_final")) {
            throw new IllegalArgumentException("SEALED FINAL Gold access is forbidden");
        }
        try {
            return mapper.readTree(Files.readString(path, StandardCharsets.UTF_8));
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot read PRZ-032 Gold: " + path, exception);
        }
    }

    private String required(JsonNode node, String field) {
        String value = node.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing Gold field: " + field);
        }
        return value;
    }

    private Integer nullableInt(JsonNode node, String field) {
        return node.path(field).isNull() || node.path(field).isMissingNode()
                ? null : node.path(field).asInt();
    }

    private <T> void putSame(Map<String, T> values, String key, T value, String label) {
        T previous = values.putIfAbsent(key, value);
        if (previous != null && !Objects.equals(previous, value)) {
            throw new IllegalStateException(label + " identity changed: " + key);
        }
    }

    record GoldSnapshot(
            Map<String, GoldQuery> queriesById,
            Map<String, GoldUnit> units,
            Map<String, GoldParent> parents,
            Map<String, GoldGroup> groups) {
    }

    record GoldQuery(
            String suite,
            String datasetVersion,
            String split,
            String queryId,
            String userBundleId,
            String text,
            String answerability,
            String language,
            List<String> categories,
            AspectExpression aspectExpression,
            List<Aspect> aspects,
            Map<String, String> relationByUnitId,
            String typedExpectedState) {

        boolean hasDirectSupport() {
            return relationByUnitId.containsValue("DIRECT_SUPPORT");
        }
    }

    record AspectExpression(String operator, List<String> requiredAspectIds, int minShouldMatch) {
    }

    record Aspect(
            String aspectId,
            boolean required,
            int minEvidenceGroups,
            List<String> requiredEvidenceGroupIds,
            Map<String, String> expectedRelations) {
    }

    record GoldUnit(
            String evidenceUnitId,
            String userBundleId,
            String parentId,
            String groupId,
            String documentId,
            String versionId,
            List<GoldSpan> spans) {
    }

    record GoldParent(
            String parentId,
            String userBundleId,
            String documentId,
            String versionId,
            GoldSpan span) {
    }

    record GoldGroup(String groupId, String userBundleId, List<String> evidenceUnitIds) {
    }

    record GoldSpan(
            String documentId,
            String versionId,
            Integer page,
            int codePointStart,
            int codePointEnd,
            String textSha256) {
    }
}
