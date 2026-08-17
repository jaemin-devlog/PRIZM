package com.prizm.search.evaluation;

import com.prizm.PrizmApplication;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.profile.NaturalLanguageQueryFallback;
import com.prizm.search.profile.NumericAnchorRescueProfile;
import com.prizm.search.profile.NumericQueryAnchors;
import com.prizm.search.profile.SearchIntent;
import com.prizm.search.profile.ShortGeneralExactTokenRescueProfile;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.repository.VectorSearchResult;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Read-only stage tracer for the fourteen frozen PRZ-016 P7-B unknown Positive queries. */
public final class P7StageTrace14 {

    private static final List<String> TARGET_IDS = List.of(
            "V2-U01-D01", "V2-U01-NV02",
            "V2-U02-D02", "V2-U02-NV02", "V2-U02-IP02", "V2-U02-NI01",
            "V2-U02-CN01", "V2-U02-CN02", "V2-U03-NI01",
            "V2-U04-D01", "V2-U04-D02", "V2-U04-IP01", "V2-U04-NI01",
            "V2-U04-CN02");
    private static final Map<String, Long> OWNERS = Map.of(
            "SYN2-U01", 1L, "SYN2-U02", 2L, "SYN2-U03", 3L, "SYN2-U04", 4L);
    private static final Pattern CHUNK_IDS = Pattern.compile("chunks? (\\d+)(?: and (\\d+))?");

    private final ObjectMapper mapper = new ObjectMapper();
    private final CompositeSearchProfile profile;
    private final ShortGeneralExactTokenRescueProfile shortRescue;
    private final NumericAnchorRescueProfile numericRescue;
    private final VectorSearchRepository repository;
    private final EmbeddingService embeddingService;
    private final EmbeddingValidator embeddingValidator;

    private P7StageTrace14(ConfigurableApplicationContext context) {
        this.profile = context.getBean(CompositeSearchProfile.class);
        this.shortRescue = new ShortGeneralExactTokenRescueProfile(profile);
        this.numericRescue = new NumericAnchorRescueProfile(profile);
        this.repository = context.getBean(VectorSearchRepository.class);
        this.embeddingService = context.getBean(EmbeddingService.class);
        this.embeddingValidator = context.getBean(EmbeddingValidator.class);
        verifyRuntimeCorpus(context.getBean(JdbcTemplate.class));
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected output directory argument");
        }
        Map<String, Object> properties = Map.of(
                "spring.main.web-application-type", "servlet",
                "spring.main.banner-mode", "off",
                "server.port", "0",
                "prizm.ingestion.worker-enabled", "false",
                "prizm.cleanup.worker-enabled", "false",
                "prizm.change-log.scheduler.enabled", "false",
                "prizm.bootstrap-system-admin.enabled", "false",
                "prizm.bootstrap-demo-user.enabled", "false");
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(PrizmApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(properties)
                .run()) {
            new P7StageTrace14(context).run(Path.of(args[0]));
        }
    }

    private void run(Path outputDirectory) throws Exception {
        Path repo = outputDirectory.toAbsolutePath().normalize().getParent().getParent().getParent();
        Path questionsPath = repo.resolve(
                "specs/PRZ-016-search-performance-v2/p7-cross-document-generalization-v2/dataset/questions.json");
        Path ceilingPath = outputDirectory.resolve("stage-ceiling.json");
        Path rawPath = repo.resolve(
                "specs/PRZ-016-search-performance-v2/p7-b-independent-generalization/raw-results.json");
        JsonNode questionsRoot = mapper.readTree(questionsPath.toFile());
        JsonNode ceilingRoot = mapper.readTree(ceilingPath.toFile());
        JsonNode rawRoot = mapper.readTree(rawPath.toFile());

        Map<String, JsonNode> questions = index(questionsRoot.path("questions"), "id");
        Map<String, JsonNode> failures = index(ceilingRoot.path("failedPositiveQueries"), "id");
        Map<String, JsonNode> frozenQueries = index(rawRoot.path("queries"), "id");
        if (!questions.keySet().containsAll(TARGET_IDS)
                || !failures.keySet().containsAll(TARGET_IDS)
                || !frozenQueries.keySet().containsAll(TARGET_IDS)) {
            throw new IllegalStateException("One or more target queries are missing from frozen inputs");
        }

        ArrayNode traces = mapper.createArrayNode();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String key : List.of("CANDIDATE_RECALL", "FILTERING", "RANKING", "PASSED_TO_TOP5", "UNKNOWN")) {
            counts.put(key, 0);
        }
        int baselineReproductionMatches = 0;
        for (String id : TARGET_IDS) {
            JsonNode question = questions.get(id);
            Set<Long> correctChunks = parseCorrectChunks(failures.get(id).path("actualStageEvidence").asText());
            Trace trace = trace(
                    id,
                    OWNERS.get(question.path("userKey").asText()),
                    question.path("query").asText(),
                    correctChunks);
            JsonNode frozenResponse = frozenQueries.get(id).path("response");
            List<Long> frozenIds = new ArrayList<>();
            frozenResponse.path("results").forEach(result -> frozenIds.add(result.path("chunkId").asLong()));
            boolean reproductionMatch = frozenIds.equals(trace.finalIds());
            if (reproductionMatch) {
                baselineReproductionMatches++;
            }
            ObjectNode node = trace.toJson(mapper);
            node.put("expectedEvidence", failures.get(id).path("expectedEvidence").toString());
            node.put("frozenFinalState", frozenResponse.path("state").asText());
            node.set("frozenFinalChunkIds", mapper.valueToTree(frozenIds));
            node.put("baselineReproductionMatch", reproductionMatch);
            traces.add(node);
            counts.compute(trace.firstFailure(), (key, value) -> value + 1);
        }
        if (baselineReproductionMatches != TARGET_IDS.size()) {
            throw new IllegalStateException(
                    "Frozen final-result reproduction mismatch: " + baselineReproductionMatches + "/14");
        }

        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", 1);
        root.put("phase", "PRZ-016-P7-B-UNKNOWN-14-STAGE-TRACE");
        root.put("executedAt", Instant.now().toString());
        root.put("sourceHead", "4a5c5b6bbd3cfd06f1313dee09e6695fdd68179e");
        root.put("sourceStageCeilingSha256", sha256(ceilingPath));
        root.put("sourceQuestionsSha256", sha256(questionsPath));
        root.put("sourceRawResultsSha256", sha256(rawPath));
        root.put("targetCount", TARGET_IDS.size());
        root.put("traced", TARGET_IDS.size());
        root.put("unknown", counts.get("UNKNOWN"));
        root.put("baselineReproductionMatches", baselineReproductionMatches);
        root.set("firstFailure", mapper.valueToTree(counts));
        root.set("queries", traces);
        root.put("productionChanges", 0);
        root.put("searchBehaviorChanges", 0);
        root.put("datasetChanges", 0);

        Path json = outputDirectory.resolve("stage-trace-14.json");
        Files.writeString(
                json,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(root) + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(outputDirectory.resolve("stage-trace-14.md"), markdown(root), StandardCharsets.UTF_8);
        System.out.println("P7_STAGE_TRACE_14_JSON=" + json);
        System.out.println("P7_STAGE_TRACE_14_COUNTS=" + counts);
    }

    private Trace trace(String id, long owner, String query, Set<Long> correctChunks) throws Exception {
        List<Attempt> attempts = new ArrayList<>();
        float[] embedding = embed(query);
        List<VectorSearchResult> candidates = repository.findCareerEvidenceCandidates(owner, embedding);
        Set<String> guarded = profile.strongIdentifiersForEvidenceGuard(query);
        List<VectorSearchResult> selected = List.of();
        if (guarded.isEmpty() || repository.hasAllActiveIdentifiers(owner, guarded)) {
            Attempt initial = attempt("ORIGINAL", query, query, List.of(query), candidates,
                    NaturalLanguageQueryFallback.requiresDirectAnchor(query));
            attempts.add(initial);
            selected = initial.top5();
            boolean fallbackAllowed = profile.resolveIntent(query) == SearchIntent.GENERAL
                    || NaturalLanguageQueryFallback.isExperienceRequest(query);
            if (selected.isEmpty() && fallbackAllowed) {
                List<String> variants = NaturalLanguageQueryFallback.variants(query).stream()
                        .filter(variant -> NaturalLanguageQueryFallback.preservesRequiredAnchors(
                                query, variant, guarded))
                        .toList();
                List<VectorSearchResult> merged = candidates;
                List<String> anchors = new ArrayList<>(List.of(query));
                int variant = 0;
                for (String fallback : variants) {
                    variant++;
                    List<VectorSearchResult> incoming = repository.findCareerEvidenceCandidates(owner, embed(fallback));
                    merged = mergeCandidates(merged, incoming);
                    anchors.add(fallback);
                    Attempt fallbackAttempt = attempt(
                            "VARIANT_" + variant, query, fallback, anchors, merged, true);
                    attempts.add(fallbackAttempt);
                    selected = fallbackAttempt.top5();
                    if (!selected.isEmpty()) {
                        break;
                    }
                }
            }
            if (selected.isEmpty()) {
                Set<String> numbers = NumericQueryAnchors.extract(query).stream()
                        .filter(NumericQueryAnchors.NumericAnchor::hasUnit)
                        .map(NumericQueryAnchors.NumericAnchor::number)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                if (!numbers.isEmpty()) {
                    List<VectorSearchResult> numericCandidates =
                            repository.findNumericAnchorCandidates(owner, embedding, numbers);
                    Attempt numericAttempt = numericAttempt(query, numericCandidates);
                    attempts.add(numericAttempt);
                    selected = numericAttempt.top5();
                }
            }
        } else {
            attempts.add(new Attempt("IDENTIFIER_GUARD", candidates, List.of(), List.of()));
        }
        selected = deduplicatePresentation(selected);

        CandidateObservation s2 = observe(attempts, correctChunks, false);
        CandidateObservation s3 = observe(attempts, correctChunks, true);
        int finalRank = rankOf(selected, correctChunks);
        String firstFailure = !s2.present()
                ? "CANDIDATE_RECALL"
                : !s3.present()
                        ? "FILTERING"
                        : finalRank == 0 ? "RANKING" : "PASSED_TO_TOP5";
        return new Trace(id, query, correctChunks, attempts, s2, s3, finalRank, firstFailure,
                selected.stream().map(VectorSearchResult::chunkId).toList());
    }

    private Attempt attempt(
            String name,
            String originalQuery,
            String searchQuery,
            List<String> anchorQueries,
            List<VectorSearchResult> candidates,
            boolean requireDirectAnchor) throws Exception {
        List<VectorSearchResult> postFilter = unlimitedPostFilter(searchQuery, candidates);
        if (requireDirectAnchor) {
            postFilter = postFilter.stream()
                    .filter(candidate -> anchorQueries.stream().anyMatch(anchor ->
                            NaturalLanguageQueryFallback.hasDirectAnchor(anchor, candidate.content())))
                    .toList();
        }
        if (NumericQueryAnchors.extract(originalQuery).stream().anyMatch(NumericQueryAnchors.NumericAnchor::hasUnit)) {
            postFilter = postFilter.stream()
                    .filter(candidate -> NumericQueryAnchors.hasContextualMatch(originalQuery, candidate.content()))
                    .toList();
        }
        List<VectorSearchResult> top5 = shortRescue.apply(searchQuery, candidates).results();
        if (requireDirectAnchor) {
            top5 = top5.stream()
                    .filter(candidate -> anchorQueries.stream().anyMatch(anchor ->
                            NaturalLanguageQueryFallback.hasDirectAnchor(anchor, candidate.content())))
                    .toList();
        }
        if (NumericQueryAnchors.extract(originalQuery).stream().anyMatch(NumericQueryAnchors.NumericAnchor::hasUnit)) {
            top5 = top5.stream()
                    .filter(candidate -> NumericQueryAnchors.hasContextualMatch(originalQuery, candidate.content()))
                    .toList();
        }
        return new Attempt(name, candidates, postFilter, top5);
    }

    private Attempt numericAttempt(String query, List<VectorSearchResult> candidates) throws Exception {
        List<VectorSearchResult> contextual = candidates.stream()
                .filter(candidate -> NumericQueryAnchors.hasContextualMatch(query, candidate.content()))
                .toList();
        Method promote = NumericAnchorRescueProfile.class.getDeclaredMethod(
                "promoteForEligibility", VectorSearchResult.class);
        promote.setAccessible(true);
        Map<Long, VectorSearchResult> originals = contextual.stream()
                .collect(Collectors.toMap(VectorSearchResult::chunkId, candidate -> candidate));
        List<VectorSearchResult> promoted = new ArrayList<>();
        for (VectorSearchResult candidate : contextual) {
            promoted.add((VectorSearchResult) promote.invoke(null, candidate));
        }
        List<VectorSearchResult> postFilter = unlimitedPostFilter(query, promoted).stream()
                .map(candidate -> originals.get(candidate.chunkId()))
                .filter(Objects::nonNull)
                .toList();
        return new Attempt("NUMERIC_RESCUE", candidates, postFilter, numericRescue.apply(query, candidates));
    }

    @SuppressWarnings("unchecked")
    private List<VectorSearchResult> unlimitedPostFilter(
            String query,
            List<VectorSearchResult> candidates) throws Exception {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Method signalsMethod = CompositeSearchProfile.class.getDeclaredMethod("querySignals", String.class);
        signalsMethod.setAccessible(true);
        Object signals = signalsMethod.invoke(profile, query);
        SearchIntent intent = profile.resolveIntent(query);

        Method sourceMethod = declaredMethod("consolidateSourceLocations", 4);
        List<?> sourceGroups = (List<?>) sourceMethod.invoke(profile, intent, query, signals, candidates);
        List<VectorSearchResult> sourceDistinct = representatives(sourceGroups);

        Method rejectionMethod = declaredMethod("rejectionReasons", 3);
        List<VectorSearchResult> eligible = new ArrayList<>();
        for (VectorSearchResult candidate : sourceDistinct) {
            List<String> reasons = (List<String>) rejectionMethod.invoke(profile, intent, signals, candidate);
            if (reasons.isEmpty()) {
                eligible.add(candidate);
            }
        }
        if (eligible.isEmpty()) {
            return List.of();
        }

        Method evidenceMethod = declaredMethod("consolidateQueryEvidence", 4);
        List<?> evidenceGroups = (List<?>) evidenceMethod.invoke(profile, intent, query, signals, eligible);
        List<VectorSearchResult> diverse = representatives(evidenceGroups);
        if (intent == SearchIntent.GENERAL) {
            Method comparatorMethod = declaredMethod("generalRankingComparator", 2);
            Comparator<VectorSearchResult> comparator =
                    (Comparator<VectorSearchResult>) comparatorMethod.invoke(profile, query, signals);
            diverse = diverse.stream().sorted(comparator).toList();
        }
        return diverse;
    }

    private Method declaredMethod(String name, int parameterCount) {
        Method method = java.util.Arrays.stream(CompositeSearchProfile.class.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)
                        && candidate.getParameterCount() == parameterCount)
                .findFirst()
                .orElseThrow();
        method.setAccessible(true);
        return method;
    }

    private static List<VectorSearchResult> representatives(List<?> groups) throws Exception {
        List<VectorSearchResult> results = new ArrayList<>();
        for (Object group : groups) {
            Method representative = group.getClass().getDeclaredMethod("representative");
            representative.setAccessible(true);
            results.add((VectorSearchResult) representative.invoke(group));
        }
        return List.copyOf(results);
    }

    private float[] embed(String query) {
        float[] embedding = embeddingService.embed(query);
        embeddingValidator.validate(embedding);
        return embedding;
    }

    private static List<VectorSearchResult> mergeCandidates(
            List<VectorSearchResult> existing,
            List<VectorSearchResult> incoming) {
        Map<Long, VectorSearchResult> byChunk = new LinkedHashMap<>();
        existing.forEach(candidate -> byChunk.put(candidate.chunkId(), candidate));
        incoming.forEach(candidate -> byChunk.merge(
                candidate.chunkId(), candidate,
                (current, replacement) -> replacement.score() > current.score() ? replacement : current));
        return byChunk.values().stream()
                .sorted(Comparator.comparingDouble(VectorSearchResult::score)
                        .reversed()
                        .thenComparing(VectorSearchResult::chunkId))
                .toList();
    }

    private static List<VectorSearchResult> deduplicatePresentation(List<VectorSearchResult> selected) {
        Set<String> seen = new LinkedHashSet<>();
        return selected.stream()
                .filter(candidate -> seen.add(candidate.content()
                        .replace("\r\n", "\n").replace('\r', '\n').strip()))
                .toList();
    }

    private static CandidateObservation observe(
            List<Attempt> attempts,
            Set<Long> correctChunks,
            boolean postFilter) {
        CandidateObservation best = CandidateObservation.absent();
        for (Attempt attempt : attempts) {
            List<VectorSearchResult> values = postFilter ? attempt.postFilter() : attempt.preFilter();
            for (int index = 0; index < values.size(); index++) {
                VectorSearchResult candidate = values.get(index);
                if (correctChunks.contains(candidate.chunkId())
                        && (!best.present() || index + 1 < best.rank())) {
                    best = new CandidateObservation(
                            true, attempt.name(), index + 1, candidate.chunkId(),
                            candidate.score(), candidate.distance());
                }
            }
        }
        return best;
    }

    private static int rankOf(List<VectorSearchResult> values, Set<Long> correctChunks) {
        for (int index = 0; index < values.size(); index++) {
            if (correctChunks.contains(values.get(index).chunkId())) {
                return index + 1;
            }
        }
        return 0;
    }

    private static Set<Long> parseCorrectChunks(String evidence) {
        Matcher matcher = CHUNK_IDS.matcher(evidence);
        Set<Long> chunks = new LinkedHashSet<>();
        while (matcher.find()) {
            chunks.add(Long.parseLong(matcher.group(1)));
            if (matcher.group(2) != null) {
                chunks.add(Long.parseLong(matcher.group(2)));
            }
        }
        if (chunks.isEmpty()) {
            throw new IllegalStateException("No correct chunk ID in stage ceiling: " + evidence);
        }
        return Set.copyOf(chunks);
    }

    private static Map<String, JsonNode> index(JsonNode array, String field) {
        Map<String, JsonNode> result = new HashMap<>();
        array.forEach(node -> result.put(node.path(field).asText(), node));
        return result;
    }

    private static void verifyRuntimeCorpus(JdbcTemplate jdbc) {
        Integer total = jdbc.queryForObject("SELECT count(*) FROM document_chunks", Integer.class);
        Integer active = jdbc.queryForObject("""
                SELECT count(*) FROM document_chunks c
                JOIN document_versions v ON v.id = c.document_version_id
                JOIN documents d ON d.id = v.document_id AND d.active_version_id = v.id
                WHERE v.status = 'ACTIVE'
                """, Integer.class);
        Integer owners = jdbc.queryForObject("SELECT count(*) FROM users", Integer.class);
        if (!Objects.equals(total, 31) || !Objects.equals(active, 30) || !Objects.equals(owners, 4)) {
            throw new IllegalStateException(
                    "Unexpected runtime corpus: chunks=" + total + ", active=" + active + ", owners=" + owners);
        }
    }

    private static String sha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private static String markdown(JsonNode root) {
        StringBuilder out = new StringBuilder("# PRZ-016 P7-B Unknown 14 Query Stage Trace\n\n");
        out.append("- Executed at: `").append(root.path("executedAt").asText()).append("`\n");
        out.append("- Baseline reproduction: `")
                .append(root.path("baselineReproductionMatches").asInt()).append("/14`\n");
        out.append("- Search behavior changed: `NO`\n\n");
        out.append("| ID | Correct chunk | S2 | S3 | S4 | First failure |\n");
        out.append("|---|---:|---|---|---|---|\n");
        root.path("queries").forEach(query -> out.append("| ")
                .append(query.path("id").asText()).append(" | ")
                .append(query.path("correctChunkIds").toString()).append(" | ")
                .append(stageCell(query.path("s2"))).append(" | ")
                .append(stageCell(query.path("s3"))).append(" | ")
                .append(query.path("s4Present").asBoolean()
                        ? "YES r" + query.path("s4Rank").asInt() : "NO")
                .append(" | ").append(query.path("firstFailure").asText()).append(" |\n"));
        return out.toString();
    }

    private static String stageCell(JsonNode stage) {
        if (!stage.path("present").asBoolean()) {
            return "NO";
        }
        return "YES " + stage.path("attempt").asText() + " r" + stage.path("rank").asInt()
                + " score=" + String.format(java.util.Locale.ROOT, "%.6f", stage.path("score").asDouble());
    }

    private record Attempt(
            String name,
            List<VectorSearchResult> preFilter,
            List<VectorSearchResult> postFilter,
            List<VectorSearchResult> top5) {
    }

    private record CandidateObservation(
            boolean present,
            String attempt,
            int rank,
            Long chunkId,
            double score,
            double distance) {
        private static CandidateObservation absent() {
            return new CandidateObservation(false, null, 0, null, 0.0d, 0.0d);
        }

        private ObjectNode toJson(ObjectMapper mapper) {
            ObjectNode node = mapper.createObjectNode();
            node.put("present", present);
            if (present) {
                node.put("attempt", attempt);
                node.put("rank", rank);
                node.put("chunkId", chunkId);
                node.put("score", score);
                node.put("distance", distance);
            }
            return node;
        }
    }

    private record Trace(
            String id,
            String query,
            Set<Long> correctChunks,
            List<Attempt> attempts,
            CandidateObservation s2,
            CandidateObservation s3,
            int finalRank,
            String firstFailure,
            List<Long> finalIds) {
        private ObjectNode toJson(ObjectMapper mapper) {
            ObjectNode node = mapper.createObjectNode();
            node.put("id", id);
            node.put("query", query);
            node.set("correctChunkIds", mapper.valueToTree(correctChunks.stream().sorted().toList()));
            node.set("s2", s2.toJson(mapper));
            node.set("s3", s3.toJson(mapper));
            node.put("s4Present", finalRank > 0);
            if (finalRank > 0) {
                node.put("s4Rank", finalRank);
            }
            node.put("firstFailure", firstFailure);
            node.set("finalChunkIds", mapper.valueToTree(finalIds));
            ArrayNode attemptNodes = mapper.createArrayNode();
            for (Attempt attempt : attempts) {
                ObjectNode item = mapper.createObjectNode();
                item.put("name", attempt.name());
                item.set("preFilterChunkIds", mapper.valueToTree(
                        attempt.preFilter().stream().map(VectorSearchResult::chunkId).toList()));
                item.set("postFilterChunkIds", mapper.valueToTree(
                        attempt.postFilter().stream().map(VectorSearchResult::chunkId).toList()));
                item.set("top5ChunkIds", mapper.valueToTree(
                        attempt.top5().stream().map(VectorSearchResult::chunkId).toList()));
                attemptNodes.add(item);
            }
            node.set("attempts", attemptNodes);
            return node;
        }
    }
}
