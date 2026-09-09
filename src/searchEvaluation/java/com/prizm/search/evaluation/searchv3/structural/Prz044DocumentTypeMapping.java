package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.document.entity.DocumentType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Explicit, fail-closed translation from the release fixture vocabulary to Production types. */
final class Prz044DocumentTypeMapping {

    static final String CONTRACT_RELATIVE =
            "specs/PRZ-044-search-v3-release-grade-evaluation/document-type-mapping-v1.json";
    static final String ARTIFACT_TYPE = "PRZ044_DOCUMENT_TYPE_MAPPING";
    static final String VERSION = "DOCUMENT_TYPE_MAPPING_V1";
    static final Set<String> DATASET_TYPES = Set.of("CAREER_DESCRIPTION", "PORTFOLIO", "RESUME");

    private static final Map<String, DocumentType> MAPPINGS = Map.of(
            "CAREER_DESCRIPTION", DocumentType.RESUME,
            "PORTFOLIO", DocumentType.PORTFOLIO,
            "RESUME", DocumentType.RESUME);

    private final ObjectMapper mapper = new ObjectMapper();

    VerifiedMapping verifyContract(Path projectRoot) {
        Path path = Prz044PredictionFreeze.resolvePortable(projectRoot, CONTRACT_RELATIVE);
        JsonNode root;
        try {
            root = mapper.readTree(Files.readAllBytes(path));
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot read PRZ-044 document type mapping contract", exception);
        }
        requireExactFields(root, "mapping contract", Set.of(
                "artifactType", "version", "datasetTypes", "productionTypes", "mappings",
                "unknownPolicy", "fallbackAllowed"));
        require(ARTIFACT_TYPE.equals(text(root, "artifactType")), "mapping artifact type changed");
        require(VERSION.equals(text(root, "version")), "mapping version changed");
        require("FAIL_CLOSED".equals(text(root, "unknownPolicy")), "unknown policy must fail closed");
        require(!root.path("fallbackAllowed").asBoolean(true), "document type fallback must be disabled");

        List<String> datasetTypes = stringList(root.path("datasetTypes"), "datasetTypes");
        require(datasetTypes.equals(DATASET_TYPES.stream().sorted().toList()),
                "dataset document type inventory changed");
        List<String> productionTypes = stringList(root.path("productionTypes"), "productionTypes");
        require(productionTypes.equals(Arrays.stream(DocumentType.values()).map(Enum::name).toList()),
                "Production DocumentType inventory changed");

        JsonNode mappingNodes = root.path("mappings");
        require(mappingNodes.isArray(), "mappings must be an array");
        Map<String, DocumentType> contractMappings = new LinkedHashMap<>();
        for (JsonNode entry : mappingNodes) {
            requireExactFields(entry, "mapping entry", Set.of("source", "target"));
            String source = text(entry, "source");
            DocumentType target;
            try {
                target = DocumentType.valueOf(text(entry, "target"));
            }
            catch (IllegalArgumentException exception) {
                throw new IllegalStateException("mapping target is not a Production DocumentType", exception);
            }
            require(contractMappings.put(source, target) == null,
                    "ambiguous duplicate document type mapping: " + source);
        }
        require(contractMappings.equals(MAPPINGS), "mapping contract differs from executable mapping");
        require(contractMappings.keySet().equals(DATASET_TYPES), "mapping is incomplete");
        return new VerifiedMapping(path.toAbsolutePath().normalize(),
                Prz044PredictionFreeze.sha256(path), Map.copyOf(contractMappings));
    }

    DocumentType map(String sourceType) {
        DocumentType target = MAPPINGS.get(sourceType);
        if (target == null) {
            throw new IllegalStateException("unknown PRZ-044 source document type: " + sourceType);
        }
        return target;
    }

    MappingAudit audit(List<Prz044PredictionDataset.RuntimeDocument> documents) {
        Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        Map<DocumentType, Integer> targetCounts = new EnumMap<>(DocumentType.class);
        List<String> unknown = new ArrayList<>();
        for (Prz044PredictionDataset.RuntimeDocument document : documents) {
            String sourceType = document.sourceDocumentType();
            sourceCounts.merge(sourceType, 1, Integer::sum);
            try {
                targetCounts.merge(map(sourceType), 1, Integer::sum);
            }
            catch (IllegalStateException exception) {
                unknown.add(sourceType);
            }
        }
        sourceCounts = sortedCopy(sourceCounts);
        require(unknown.isEmpty(), "unmapped document types: " + unknown.stream().distinct().sorted().toList());
        require(sourceCounts.keySet().equals(DATASET_TYPES),
                "official dataset document type inventory changed: " + sourceCounts.keySet());
        return new MappingAudit(documents.size(), documents.size(), 0, 0,
                Map.copyOf(sourceCounts), Map.copyOf(targetCounts));
    }

    private static Map<String, Integer> sortedCopy(Map<String, Integer> values) {
        Map<String, Integer> sorted = new LinkedHashMap<>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.naturalOrder()))
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return sorted;
    }

    private static List<String> stringList(JsonNode node, String label) {
        require(node.isArray(), label + " must be an array");
        List<String> values = new ArrayList<>();
        for (JsonNode value : node) {
            require(value.isTextual() && !value.asText().isBlank(), label + " contains invalid text");
            values.add(value.asText());
        }
        return List.copyOf(values);
    }

    private static void requireExactFields(JsonNode node, String label, Set<String> expected) {
        require(node.isObject(), label + " must be an object");
        require(Set.copyOf(node.propertyNames()).equals(expected), label + " fields changed");
    }

    private static String text(JsonNode node, String field) {
        String value = node.path(field).asText();
        require(value != null && !value.isBlank(), field + " is required");
        return value;
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }

    record VerifiedMapping(Path path, String sha256, Map<String, DocumentType> mappings) {
    }

    record MappingAudit(
            int documentCount,
            int mappedCount,
            int unmappedCount,
            int ambiguousCount,
            Map<String, Integer> sourceCounts,
            Map<DocumentType, Integer> targetCounts) {
    }
}
