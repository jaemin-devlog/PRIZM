package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.search.profile.ClaimSupportDecision;
import com.prizm.search.profile.QueryClaimRequirements;
import com.prizm.search.profile.StructuredClaimSupportEvaluator;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Evaluation-only verifier comparison over preserved Judge A/B/C candidates. */
class ClaimVerificationArchitectureShadowTest {
    private static final Path AUDIT = Path.of(
            "specs/PRZ-016-search-performance-v2/claim-verification-architecture-audit");
    private static final List<Dataset> DATASETS = List.of(
            new Dataset(
                    "JUDGE_A",
                    Path.of("specs/PRZ-016-search-performance-v2/final-unseen-judge-e2e"),
                    "final-fix-regression-results.json",
                    "final-fix-regression-traces.json"),
            new Dataset(
                    "JUDGE_B",
                    Path.of("specs/PRZ-016-search-performance-v2/final-unseen-judge-b"),
                    "judge-b-results.json",
                    "judge-b-traces.json"),
            new Dataset(
                    "JUDGE_C",
                    Path.of("specs/PRZ-016-search-performance-v2/final-unseen-judge-c"),
                    "judge-c-results.json",
                    "judge-c-traces.json"));
    private static final URI OLLAMA_TAGS = URI.create("http://127.0.0.1:11434/api/tags");
    private static final URI OLLAMA_CHAT = URI.create("http://127.0.0.1:11434/api/chat");
    private static final String MODEL = "qwen3:4b-instruct";
    private static final Pattern SENTENCE = Pattern.compile("(?<=[.!?。！？])\\s+|\\n+");
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}+#._-]*");
    private static final Pattern DECLARATIVE_ENDING = Pattern.compile(
            ".*(?:했다|하였다|했습니다|되었습니다|됐다|였다|합니다|했습니다|않았습니다|않았다)\\s*[.!。！]*$");
    private static final Pattern FIRST_PERSON_DENIAL = Pattern.compile(
            "(?:나는|내가|저는|제가|본인은|본인이).{0,100}"
                    + "(?:하지\\s*(?:않|못)|아니|않았습니다|않았다)");
    private static final Pattern EXPLICIT_NOT_ADOPTED = Pattern.compile(
            "(?:도입|채택|사용|운영|적용|포함|배포).{0,24}하지\\s*(?:않|못)");
    private static final Set<String> QUERY_STOP = Set.of(
            "경험", "근거", "직접", "실제", "프로젝트", "어떻게", "있나요", "했나요",
            "했는가", "인가요", "사용", "구현", "적용", "운영", "개선", "복구");
    private static final String SYSTEM_PROMPT = """
            You are an evaluation-only local career-claim verifier. Use only the supplied local evidence sentences.
            Decide whether the evidence supports an affirmative answer to the query about this document owner.
            SUPPORTED requires a direct statement or an explicit problem-action-result chain in this same window.
            CONTRADICTED requires explicit counter-evidence for the same claim, including negation, not adopted,
            prototype instead of required production, another actor, wrong numeric value, wrong metric, or wrong state.
            Topic similarity, plans, comparisons, mere mentions, and another actor's work are not support.
            If support or contradiction is not explicit enough, return UNCERTAIN. Do not use external knowledge.
            Return only the schema-conforming JSON object and no reasoning.
            """;
    private static final String FORMAT = """
            {"type":"object","additionalProperties":false,"properties":{
            "label":{"type":"string","enum":["SUPPORTED","CONTRADICTED","UNCERTAIN"]},
            "evidenceSentenceIds":{"type":"array","items":{"type":"string"}}},
            "required":["label","evidenceSentenceIds"]}
            """;

    private final ObjectMapper mapper = new ObjectMapper();
    private final StructuredClaimSupportEvaluator deterministic =
            new StructuredClaimSupportEvaluator();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Test
    void comparesCurrentSafetyFirstAndLocalSemanticVerification() throws Exception {
        boolean semanticReady = installedModelAvailable();
        ArrayNode rows = mapper.createArrayNode();
        Map<String, Counters> totals = counters();
        long semanticLatency = 0;
        int semanticCalls = 0;

        for (Dataset dataset : DATASETS) {
            JsonNode report = mapper.readTree(dataset.root().resolve(dataset.results()).toFile());
            JsonNode traces = mapper.readTree(dataset.root().resolve(dataset.traces()).toFile());
            Map<String, JsonNode> traceByQuery = traceIndex(traces);
            Map<String, JsonNode> truth = index(
                    mapper.readTree(dataset.root().resolve("dataset/ground-truth.json").toFile())
                            .path("queries"),
                    "id");
            Map<Long, String> fixtureByDocument = fixtureByDocument(dataset.root());

            for (JsonNode queryRow : report.path("queries")) {
                boolean positive = "POSITIVE".equals(queryRow.path("label").asText());
                String id = queryRow.path("id").asText();
                String query = queryRow.path("query").asText();
                JsonNode trace = traceByQuery.get(traceKey(
                        queryRow.path("ownerUserId").asLong(), query));
                JsonNode candidate = selectCandidate(
                        positive, trace, truth.get(id), fixtureByDocument);
                String content = candidate == null ? "" : candidate.path("content").asText();
                long chunkId = candidate == null ? -1L : candidate.path("chunkId").asLong();
                String window = localWindow(queryRow, chunkId, query, content);

                ClaimSupportDecision d0Decision = deterministic.evaluate(query, content);
                Label d0 = d0Decision.status() == ClaimSupportDecision.Status.SUPPORTED
                        ? Label.SUPPORTED
                        : d0Decision.status() == ClaimSupportDecision.Status.CONTRADICTED
                                ? Label.CONTRADICTED
                                : Label.UNCERTAIN;
                ShadowDecision d1Decision = safetyFirst(query, window);
                SemanticDecision d2Decision = semanticReady
                        ? semanticVerify(query, window)
                        : new SemanticDecision(Label.NOT_RUN, List.of(), 0L, "MODEL_NOT_AVAILABLE");
                if (d2Decision.label() != Label.NOT_RUN && d2Decision.label() != Label.ERROR) {
                    semanticCalls++;
                    semanticLatency += d2Decision.latencyMs();
                }

                record(totals.get("TOTAL"), positive, queryRow.path("type").asText(), d0, d1Decision.label(), d2Decision.label());
                record(totals.get(dataset.id()), positive, queryRow.path("type").asText(), d0, d1Decision.label(), d2Decision.label());

                ObjectNode row = rows.addObject();
                row.put("dataset", dataset.id());
                row.put("id", id);
                row.put("label", queryRow.path("label").asText());
                row.put("type", queryRow.path("type").asText());
                row.put("query", query);
                row.put("candidateChunkId", chunkId);
                row.put("localWindow", window);
                row.put("d0", d0.name());
                row.set("d0Reasons", mapper.valueToTree(d0Decision.reasons()));
                row.put("d1", d1Decision.label().name());
                row.set("d1Reasons", mapper.valueToTree(d1Decision.reasons()));
                row.put("d2", d2Decision.label().name());
                row.set("d2EvidenceSentenceIds", mapper.valueToTree(d2Decision.evidenceSentenceIds()));
                row.put("d2Diagnostic", d2Decision.diagnostic());
                row.put("d2LatencyMs", d2Decision.latencyMs());
            }
        }

        ObjectNode output = mapper.createObjectNode();
        output.put("schemaVersion", 1);
        output.put("phase", "CLAIM_VERIFICATION_ARCHITECTURE_AUDIT");
        output.put("executedAt", Instant.now().toString());
        output.put("productionSearchModified", false);
        output.put("candidateGeneration", false);
        output.put("rankingChanged", false);
        output.put("semanticModel", semanticReady ? MODEL : "NOT_RUN");
        output.put("semanticCallCount", semanticCalls);
        output.put("semanticAverageLatencyMs", semanticCalls == 0 ? 0 : (double) semanticLatency / semanticCalls);
        ObjectNode summaries = output.putObject("summaries");
        totals.forEach((name, value) -> summaries.set(name, value.toJson(mapper)));
        output.set("rows", rows);
        Files.createDirectories(AUDIT);
        Files.writeString(
                AUDIT.resolve("shadow-results.json"),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output) + "\n",
                StandardCharsets.UTF_8);

        assertThat(rows).hasSize(59);
        assertThat(totals.get("TOTAL").positives).isEqualTo(29);
        assertThat(totals.get("TOTAL").negatives).isEqualTo(30);
    }

    private ShadowDecision safetyFirst(String query, String window) {
        ClaimSupportDecision local = deterministic.evaluate(query, window);
        if (local.status() == ClaimSupportDecision.Status.CONTRADICTED) {
            return new ShadowDecision(Label.CONTRADICTED, List.of("EXPLICIT_HARD_CONTRADICTION"));
        }
        String normalized = normalize(window);
        if (FIRST_PERSON_DENIAL.matcher(normalized).find()) {
            return new ShadowDecision(Label.CONTRADICTED, List.of("EXPLICIT_FIRST_PERSON_DENIAL"));
        }
        if (EXPLICIT_NOT_ADOPTED.matcher(normalized).find()) {
            return new ShadowDecision(Label.CONTRADICTED, List.of("EXPLICIT_NOT_ADOPTED"));
        }
        if (local.status() == ClaimSupportDecision.Status.SUPPORTED
                && !local.reasons().contains(ClaimSupportDecision.Reason.NON_CLAIM_QUERY)) {
            return new ShadowDecision(Label.SUPPORTED, List.of("CURRENT_LOCAL_DIRECT_SUPPORT"));
        }
        QueryClaimRequirements requirements = deterministic.extract(query);
        boolean entitiesBound = requirements.entities().stream()
                .allMatch(entity -> compact(normalized).contains(compact(entity)));
        long subjectMatches = requirements.subjectTerms().stream()
                .filter(normalized::contains)
                .count();
        int requiredSubjects = Math.min(2, requirements.subjectTerms().size());
        boolean subjectsBound = subjectMatches >= requiredSubjects;
        boolean numericBound = requirements.numericConstraints().stream()
                .allMatch(number -> normalized.replace(",", "").contains(number.value().toPlainString()));
        boolean stateBound = requirements.requiredState() != QueryClaimRequirements.State.PRODUCTION
                || normalized.contains("production")
                || normalized.contains("생산")
                || normalized.contains("운영")
                || normalized.contains("배포");
        boolean directAnchor = !requirements.entities().isEmpty()
                || !requirements.numericConstraints().isEmpty()
                || requiredSubjects > 0;
        boolean affirmative = DECLARATIVE_ENDING.matcher(normalized).matches()
                && !normalized.contains("?")
                && !normalized.contains("？");
        if (requirements.claimQuestion()
                && directAnchor
                && entitiesBound
                && subjectsBound
                && numericBound
                && stateBound
                && affirmative) {
            return new ShadowDecision(Label.SUPPORTED, List.of("DIRECT_AFFIRMATIVE_NO_CONTRADICTION"));
        }
        return new ShadowDecision(Label.UNCERTAIN, List.of("NO_DIRECT_SUPPORT"));
    }

    private SemanticDecision semanticVerify(String query, String window) {
        try {
            ArrayNode evidence = mapper.createArrayNode();
            List<String> sentences = sentences(window);
            for (int index = 0; index < sentences.size(); index++) {
                ObjectNode sentence = evidence.addObject();
                sentence.put("id", "S" + (index + 1));
                sentence.put("text", sentences.get(index));
            }
            ObjectNode userData = mapper.createObjectNode();
            userData.put("query", query);
            userData.set("evidenceSentences", evidence);
            ObjectNode payload = mapper.createObjectNode();
            payload.put("model", MODEL);
            payload.put("stream", false);
            payload.put("think", false);
            payload.set("format", mapper.readTree(FORMAT));
            payload.putObject("options").put("temperature", 0);
            ArrayNode messages = payload.putArray("messages");
            messages.addObject().put("role", "system").put("content", SYSTEM_PROMPT);
            messages.addObject().put("role", "user").put(
                    "content", "Evaluate this query and local evidence: " + mapper.writeValueAsString(userData));
            HttpRequest request = HttpRequest.newBuilder(OLLAMA_CHAT)
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload), StandardCharsets.UTF_8))
                    .build();
            long started = System.nanoTime();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            long latency = (System.nanoTime() - started) / 1_000_000L;
            if (response.statusCode() != 200) {
                return new SemanticDecision(Label.ERROR, List.of(), latency, "HTTP_" + response.statusCode());
            }
            JsonNode envelope = mapper.readTree(response.body());
            JsonNode decision = mapper.readTree(envelope.path("message").path("content").asText());
            Label label = Label.valueOf(decision.path("label").asText("ERROR"));
            List<String> ids = new ArrayList<>();
            decision.path("evidenceSentenceIds").forEach(id -> ids.add(id.asText()));
            return new SemanticDecision(label, List.copyOf(ids), latency, "VALID");
        } catch (Exception exception) {
            return new SemanticDecision(Label.ERROR, List.of(), 0L, exception.getClass().getSimpleName());
        }
    }

    private boolean installedModelAvailable() {
        try {
            HttpRequest request = HttpRequest.newBuilder(OLLAMA_TAGS)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            JsonNode response = mapper.readTree(http.send(
                            request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                    .body());
            for (JsonNode model : response.path("models")) {
                if (MODEL.equals(model.path("name").asText())) {
                    return true;
                }
            }
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    private JsonNode selectCandidate(
            boolean positive,
            JsonNode trace,
            JsonNode truth,
            Map<Long, String> fixtureByDocument) {
        if (trace == null) {
            return null;
        }
        if (!positive) {
            return trace.path("candidates").isEmpty() ? null : trace.path("candidates").get(0);
        }
        for (JsonNode candidate : trace.path("candidates")) {
            String fixture = fixtureByDocument.get(candidate.path("documentId").asLong());
            for (JsonNode set : truth.path("acceptableEvidenceSets")) {
                if (!Objects.equals(fixture, set.path("documentFixture").asText())) {
                    continue;
                }
                boolean matches = true;
                for (JsonNode clause : set.path("requiredClauses")) {
                    boolean clauseMatch = false;
                    for (JsonNode anchor : clause.path("anchorAny")) {
                        clauseMatch |= normalize(candidate.path("content").asText())
                                .contains(normalize(anchor.asText()));
                    }
                    matches &= clauseMatch;
                }
                if (matches) {
                    return candidate;
                }
            }
        }
        return trace.path("candidates").isEmpty() ? null : trace.path("candidates").get(0);
    }

    private String localWindow(JsonNode queryRow, long chunkId, String query, String content) {
        String source = content;
        for (JsonNode evidence : queryRow.path("displayedEvidence")) {
            if (evidence.path("originalResultChunkId").asLong() == chunkId
                    && !evidence.path("snippet").asText().isBlank()) {
                source = evidence.path("snippet").asText();
                break;
            }
        }
        List<String> sourceSentences = sentences(source);
        if (sourceSentences.isEmpty()) {
            return "";
        }
        Set<String> queryTerms = lexicalTerms(query);
        List<Window> windows = new ArrayList<>();
        for (int size = 1; size <= 3; size++) {
            for (int index = 0; index + size <= sourceSentences.size(); index++) {
                String text = String.join(" ", sourceSentences.subList(index, index + size));
                long coverage = queryTerms.stream().filter(normalize(text)::contains).count();
                windows.add(new Window(text, coverage, size));
            }
        }
        return windows.stream()
                .max(Comparator.comparingLong(Window::coverage)
                        .thenComparingInt(window -> -window.sentenceCount())
                        .thenComparingInt(window -> -window.text().length()))
                .orElseThrow()
                .text();
    }

    private Map<Long, String> fixtureByDocument(Path root) throws Exception {
        JsonNode manifest = mapper.readTree(root.resolve("dataset/corpus-manifest.json").toFile());
        Map<Long, String> result = new LinkedHashMap<>();
        long documentId = 1;
        for (JsonNode user : manifest.path("users")) {
            for (JsonNode document : user.path("documents")) {
                result.put(documentId++, document.path("fixture").asText());
            }
        }
        return Map.copyOf(result);
    }

    private static List<String> sentences(String value) {
        return Arrays.stream(SENTENCE.split(Objects.requireNonNullElse(value, "")))
                .map(String::strip)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static Set<String> lexicalTerms(String value) {
        Set<String> result = new LinkedHashSet<>();
        Matcher matcher = TOKEN.matcher(normalize(value));
        while (matcher.find()) {
            String token = matcher.group().replaceAll("[?!.。！？]", "");
            if (token.length() >= 2 && !QUERY_STOP.contains(token)) {
                result.add(token);
            }
        }
        return Set.copyOf(result);
    }

    private static String normalize(String value) {
        return Objects.requireNonNullElse(value, "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ")
                .strip();
    }

    private static String compact(String value) {
        return normalize(value).replaceAll("[^\\p{L}\\p{N}+#]", "");
    }

    private static Map<String, JsonNode> index(JsonNode nodes, String key) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        nodes.forEach(node -> result.put(node.path(key).asText(), node));
        return Map.copyOf(result);
    }

    private static Map<String, JsonNode> traceIndex(JsonNode nodes) {
        Map<String, JsonNode> result = new LinkedHashMap<>();
        nodes.forEach(node -> result.put(
                traceKey(node.path("ownerUserId").asLong(), node.path("originalQuery").asText()),
                node));
        return Map.copyOf(result);
    }

    private static String traceKey(long ownerUserId, String query) {
        return ownerUserId + "|" + query;
    }

    private static Map<String, Counters> counters() {
        Map<String, Counters> result = new LinkedHashMap<>();
        result.put("TOTAL", new Counters());
        DATASETS.forEach(dataset -> result.put(dataset.id(), new Counters()));
        return result;
    }

    private static void record(
            Counters counters,
            boolean positive,
            String type,
            Label d0,
            Label d1,
            Label d2) {
        if (positive) {
            counters.positives++;
            counters.d0Positive += d0 == Label.SUPPORTED ? 1 : 0;
            counters.d1Positive += d1 == Label.SUPPORTED ? 1 : 0;
            counters.d2Positive += d2 == Label.SUPPORTED ? 1 : 0;
            return;
        }
        counters.negatives++;
        if (d0 == Label.SUPPORTED) counters.d0FalsePositives.merge(type, 1, Integer::sum);
        if (d1 == Label.SUPPORTED) counters.d1FalsePositives.merge(type, 1, Integer::sum);
        if (d2 == Label.SUPPORTED) counters.d2FalsePositives.merge(type, 1, Integer::sum);
    }

    private record Dataset(String id, Path root, String results, String traces) {}
    private record Window(String text, long coverage, int sentenceCount) {}
    private record ShadowDecision(Label label, List<String> reasons) {}
    private record SemanticDecision(
            Label label,
            List<String> evidenceSentenceIds,
            long latencyMs,
            String diagnostic) {}
    private enum Label { SUPPORTED, CONTRADICTED, UNCERTAIN, NOT_RUN, ERROR }

    private static final class Counters {
        int positives;
        int negatives;
        int d0Positive;
        int d1Positive;
        int d2Positive;
        final Map<String, Integer> d0FalsePositives = new LinkedHashMap<>();
        final Map<String, Integer> d1FalsePositives = new LinkedHashMap<>();
        final Map<String, Integer> d2FalsePositives = new LinkedHashMap<>();

        ObjectNode toJson(ObjectMapper mapper) {
            ObjectNode node = mapper.createObjectNode();
            node.put("positiveCount", positives);
            node.put("negativeCount", negatives);
            node.set("D0", metric(mapper, d0Positive, d0FalsePositives));
            node.set("D1", metric(mapper, d1Positive, d1FalsePositives));
            node.set("D2", metric(mapper, d2Positive, d2FalsePositives));
            return node;
        }

        private ObjectNode metric(
                ObjectMapper mapper,
                int positiveRetained,
                Map<String, Integer> falsePositives) {
            int fp = falsePositives.values().stream().mapToInt(Integer::intValue).sum();
            ObjectNode node = mapper.createObjectNode();
            node.put("positiveRetained", positiveRetained);
            node.put("positiveRetention", positives == 0 ? 0 : (double) positiveRetained / positives);
            node.put("negativeFalsePositiveCount", fp);
            node.put("negativeFpr", negatives == 0 ? 0 : (double) fp / negatives);
            node.set("falsePositivesByType", mapper.valueToTree(falsePositives));
            return node;
        }
    }
}
