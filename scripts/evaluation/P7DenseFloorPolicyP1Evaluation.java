package com.prizm.search.evaluation;

import com.prizm.PrizmApplication;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.dto.response.CareerEvidenceSearchV2Response;
import com.prizm.search.service.SearchService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Re-scores the frozen P7-B corpus with the current Production search service. */
public final class P7DenseFloorPolicyP1Evaluation {

    private static final Map<String, Long> OWNERS = Map.of(
            "SYN2-U01", 1L, "SYN2-U02", 2L, "SYN2-U03", 3L, "SYN2-U04", 4L);

    private final ObjectMapper mapper = new ObjectMapper();
    private final SearchService searchService;

    private P7DenseFloorPolicyP1Evaluation(ConfigurableApplicationContext context) {
        searchService = context.getBean(SearchService.class);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Expected output directory and optional phase argument");
        }
        String phase = args.length == 2 ? args[1] : "PRZ-016-DENSE-FLOOR-POLICY-P1-P7-B-RECHECK";
        Map<String, Object> properties = Map.of(
                "spring.main.web-application-type", "servlet",
                "spring.main.banner-mode", "off",
                "server.port", "0",
                "prizm.ingestion.worker-enabled", "false",
                "prizm.cleanup.worker-enabled", "false",
                "prizm.change-log.scheduler.enabled", "false");
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(PrizmApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(properties)
                .run()) {
            new P7DenseFloorPolicyP1Evaluation(context).run(Path.of(args[0]), phase);
        }
    }

    private void run(Path outputDirectory, String phase) throws Exception {
        Path root = Path.of("specs", "PRZ-016-search-performance-v2");
        Path dataset = root.resolve("p7-cross-document-generalization-v2/dataset/questions.json");
        Path groundTruth = root.resolve("p7-cross-document-generalization-v2/dataset/ground-truth.json");
        Path originalRaw = root.resolve("p7-b-independent-generalization/raw-results.json");
        JsonNode questions = mapper.readTree(dataset.toFile());
        JsonNode groundTruthNode = mapper.readTree(groundTruth.toFile());
        JsonNode original = mapper.readTree(originalRaw.toFile());

        Map<String, JsonNode> entries = byId(groundTruthNode.path("entries"));
        Map<String, JsonNode> documents = fields(original.path("documentMap"));
        List<Map<String, Object>> evaluations = new ArrayList<>();
        for (JsonNode question : questions.path("questions")) {
            String id = question.path("id").asText();
            JsonNode expected = Objects.requireNonNull(entries.get(id), "Missing GT: " + id);
            long ownerId = Objects.requireNonNull(OWNERS.get(question.path("userKey").asText()), "Missing owner");
            CareerEvidenceSearchV2Response response = searchService.searchCareerEvidenceV2(ownerId, question.path("query").asText());
            Integer correctRank = correctRank(response, expected, documents);
            boolean positive = "EVIDENCE".equals(expected.path("expectedLabel").asText());
            boolean falsePositive = !positive && !response.results().isEmpty();
            boolean passed = positive ? Integer.valueOf(1).equals(correctRank) : !falsePositive;
            Map<String, Object> evaluation = new LinkedHashMap<>();
            evaluation.put("id", id);
            evaluation.put("userKey", question.path("userKey").asText());
            evaluation.put("polarity", question.path("polarity").asText());
            evaluation.put("state", response.state().name());
            evaluation.put("correctRank", correctRank);
            evaluation.put("falsePositive", falsePositive);
            evaluation.put("passed", passed);
            evaluations.add(evaluation);
        }

        long positive = evaluations.stream().filter(value -> "POSITIVE".equals(value.get("polarity"))).count();
        long negative = evaluations.size() - positive;
        long top1 = evaluations.stream().filter(value -> Integer.valueOf(1).equals(value.get("correctRank"))).count();
        long recall3 = evaluations.stream().filter(value -> rankAtMost(value, 3)).count();
        long recall5 = evaluations.stream().filter(value -> rankAtMost(value, 5)).count();
        long falsePositive = evaluations.stream().filter(value -> Boolean.TRUE.equals(value.get("falsePositive"))).count();
        double mrr5 = evaluations.stream()
                .mapToDouble(value -> reciprocalAtMost(value, 5))
                .sum() / positive;
        long passed = evaluations.stream().filter(value -> Boolean.TRUE.equals(value.get("passed"))).count();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", evaluations.size());
        summary.put("positive", positive);
        summary.put("negative", negative);
        summary.put("pass", passed);
        summary.put("fail", evaluations.size() - passed);
        summary.put("top1", ratio(top1, positive));
        summary.put("recallAt3", ratio(recall3, positive));
        summary.put("recallAt5", ratio(recall5, positive));
        summary.put("mrrAt5", mrr5);
        summary.put("negativeFpr", ratio(falsePositive, negative));

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("schemaVersion", 1);
        output.put("phase", phase);
        output.put("executedAt", Instant.now().toString());
        output.put("sourceQuestionsSha256", sha256(dataset));
        output.put("sourceGroundTruthSha256", sha256(groundTruth));
        output.put("sourceOriginalRawSha256", sha256(originalRaw));
        output.put("execution", "CURRENT_PRODUCTION_SEARCH_SERVICE_ON_EXISTING_P7_B_ISOLATED_CORPUS");
        output.put("summary", summary);
        output.put("evaluations", evaluations);
        output.put("productionChangesByEvaluation", 0);
        output.put("datasetChanges", 0);
        Files.createDirectories(outputDirectory);
        Path outputPath = outputDirectory.resolve("p7-b-48-current-profile.json");
        Files.writeString(outputPath, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output) + "\n", StandardCharsets.UTF_8);
        System.out.println("P7_DENSE_FLOOR_P1_EVALUATION=" + outputPath);
        System.out.println("P7_DENSE_FLOOR_P1_SUMMARY=" + mapper.writeValueAsString(summary));
    }

    private static Integer correctRank(
            CareerEvidenceSearchV2Response response,
            JsonNode expected,
            Map<String, JsonNode> documents) {
        if (!"EVIDENCE".equals(expected.path("expectedLabel").asText())) {
            return null;
        }
        JsonNode document = Objects.requireNonNull(
                documents.get(expected.path("documentKey").asText()), "Missing frozen document mapping");
        long documentId = document.path("documentId").asLong();
        long versionId = document.path("versionId").asLong();
        String sourceKind = expected.path("source").path("kind").asText();
        int page = expected.path("source").path("page").asInt(-1);
        for (int index = 0; index < response.results().size(); index++) {
            CareerEvidenceSearchResponse result = response.results().get(index);
            boolean sameDocument = result.documentId() == documentId && result.documentVersionId() == versionId;
            boolean sameKind = result.evidenceSourceType().name().equals(sourceKind);
            boolean samePage = !"PAGE".equals(sourceKind) || result.evidenceSourceIndex() == page;
            if (sameDocument && sameKind && samePage && containsAnyAnchor(result, expected.path("acceptableAnchors"))) {
                return index + 1;
            }
        }
        return null;
    }

    private static boolean containsAnyAnchor(CareerEvidenceSearchResponse result, JsonNode anchors) {
        String haystack = normalize(result.content() + "\n" + result.snippet());
        for (JsonNode anchor : anchors) {
            if (haystack.contains(normalize(anchor.asText()))) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, JsonNode> byId(JsonNode nodes) {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        for (JsonNode node : nodes) {
            values.put(node.path("id").asText(), node);
        }
        return values;
    }

    private static Map<String, JsonNode> fields(JsonNode node) {
        Map<String, JsonNode> values = new LinkedHashMap<>();
        node.properties().forEach(entry -> values.put(entry.getKey(), entry.getValue()));
        return values;
    }

    private static boolean rankAtMost(Map<String, Object> value, int maximum) {
        Object rank = value.get("correctRank");
        return rank instanceof Integer integer && integer <= maximum;
    }

    private static double reciprocalAtMost(Map<String, Object> value, int maximum) {
        Object rank = value.get("correctRank");
        return rank instanceof Integer integer && integer <= maximum ? 1.0d / integer : 0.0d;
    }

    private static double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0d : (double) numerator / denominator;
    }

    private static String normalize(String value) {
        return value == null ? "" : value
                .replaceAll("\\s+", "")
                .toLowerCase(Locale.forLanguageTag("ko-KR"));
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
