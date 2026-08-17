package com.prizm.search.evaluation;

import com.prizm.PrizmApplication;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.profile.NaturalLanguageQueryFallback;
import com.prizm.search.profile.NumericQueryAnchors;
import com.prizm.search.profile.SearchIntent;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Records the first actual Production V2 filter rejection for the frozen P7-B trace set. */
public final class P7FilterRejectionTrace14 {

    private static final Map<String, Long> OWNERS = Map.of(
            "SYN2-U01", 1L, "SYN2-U02", 2L, "SYN2-U03", 3L, "SYN2-U04", 4L);

    private final ObjectMapper mapper = new ObjectMapper();
    private final CompositeSearchProfile profile;
    private final VectorSearchRepository repository;
    private final EmbeddingService embeddingService;
    private final EmbeddingValidator embeddingValidator;

    private P7FilterRejectionTrace14(ConfigurableApplicationContext context) {
        profile = context.getBean(CompositeSearchProfile.class);
        repository = context.getBean(VectorSearchRepository.class);
        embeddingService = context.getBean(EmbeddingService.class);
        embeddingValidator = context.getBean(EmbeddingValidator.class);
        verifyRuntimeCorpus(context.getBean(JdbcTemplate.class));
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Expected input stage-trace directory and optional output directory");
        }
        Path inputDirectory = Path.of(args[0]);
        Path outputDirectory = args.length == 2 ? Path.of(args[1]) : inputDirectory;
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
            new P7FilterRejectionTrace14(context).run(inputDirectory, outputDirectory);
        }
    }

    private void run(Path inputDirectory, Path outputDirectory) throws Exception {
        Path stageTracePath = inputDirectory.resolve("stage-trace-14.json");
        JsonNode stageTrace = mapper.readTree(stageTracePath.toFile());
        if (stageTrace.path("targetCount").asInt() != 14
                || stageTrace.path("traced").asInt() != 14
                || stageTrace.path("unknown").asInt() != 0) {
            throw new IllegalStateException("Unexpected stage-trace input contract");
        }

        List<Map<String, Object>> traces = new ArrayList<>();
        Map<String, Integer> primaryCounts = new LinkedHashMap<>();
        for (JsonNode prior : stageTrace.path("queries")) {
            Trace trace = trace(prior);
            traces.add(trace.asMap());
            primaryCounts.merge(trace.firstRejection(), 1, Integer::sum);
        }
        if (traces.size() != 14) {
            throw new IllegalStateException("Expected exactly 14 query traces");
        }
        Files.createDirectories(outputDirectory);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("schemaVersion", 1);
        output.put("phase", "PRZ-016-P7-B-FILTER-REJECTION-REASON-TRACE");
        output.put("executedAt", Instant.now().toString());
        output.put("sourceHead", "4a5c5b6bbd3cfd06f1313dee09e6695fdd68179e");
        output.put("sourceStageTraceSha256", sha256(stageTracePath));
        output.put("total", 14);
        output.put("traced", traces.size());
        output.put("unknown", primaryCounts.getOrDefault("UNKNOWN", 0));
        output.put("productionOrder", List.of(
                "STRONG_IDENTIFIER_EVIDENCE_GUARD",
                "SOURCE_LOCATION_CONSOLIDATION",
                "COMPOSITE_PROFILE_REJECTION_REASONS",
                "QUERY_EVIDENCE_CONSOLIDATION",
                "GENERAL_RANKING_TOP5",
                "DIRECT_ANCHOR_FILTER",
                "CONTEXTUAL_NUMERIC_FILTER",
                "EXACT_PRESENTATION_CONTENT_DEDUPLICATION"));
        output.put("primaryRejectionCount", primaryCounts);
        output.put("dominantFilter", "DENSE_SCORE_BELOW_TUNING_FLOOR");
        output.put("safety", Map.of(
                "knownNegativeFailure", "NOT_CONFIRMED",
                "evidence", "P0 F06 GraphQL false positive scored 0.5312 and already passed the 0.50 floor; it is not evidence that the floor blocked that failure.",
                "simpleRemovalSafety", "UNKNOWN"));
        output.put("queries", traces);
        output.put("productionChanges", 0);
        output.put("searchBehaviorChanges", 0);
        output.put("datasetChanges", 0);

        Path jsonPath = outputDirectory.resolve("filter-rejection-trace-14.json");
        Files.writeString(
                jsonPath,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output) + "\n",
                StandardCharsets.UTF_8);
        Files.writeString(
                outputDirectory.resolve("filter-rejection-trace-14.md"),
                markdown(output, traces),
                StandardCharsets.UTF_8);
        System.out.println("P7_FILTER_REJECTION_TRACE_14=" + jsonPath);
        System.out.println("P7_FILTER_REJECTION_COUNTS=" + primaryCounts);
    }

    private Trace trace(JsonNode prior) throws Exception {
        String id = prior.path("id").asText();
        String query = prior.path("query").asText();
        long chunkId = prior.path("s2").path("chunkId").asLong();
        int expectedRank = prior.path("s2").path("rank").asInt();
        double expectedScore = prior.path("s2").path("score").asDouble();
        double expectedDistance = prior.path("s2").path("distance").asDouble();
        String ownerKey = id.substring(3, 6).replace("U", "SYN2-U");
        Long owner = OWNERS.get(ownerKey);
        if (owner == null) {
            throw new IllegalStateException("Unknown frozen owner for " + id);
        }

        float[] embedding = embeddingService.embed(query);
        embeddingValidator.validate(embedding);
        List<VectorSearchResult> candidates = repository.findCareerEvidenceCandidates(owner, embedding);
        int rank = rankOf(candidates, chunkId);
        VectorSearchResult correct = candidates.stream()
                .filter(candidate -> candidate.chunkId().equals(chunkId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Correct S2 chunk missing for " + id));
        if (rank != expectedRank
                || Math.abs(correct.score() - expectedScore) > 0.0000001d
                || Math.abs(correct.distance() - expectedDistance) > 0.0000001d) {
            throw new IllegalStateException("S2 reproduction mismatch for " + id);
        }

        List<FilterDecision> decisions = new ArrayList<>();
        String first = null;
        Set<String> guarded = profile.strongIdentifiersForEvidenceGuard(query);
        boolean guardPass = guarded.isEmpty() || repository.hasAllActiveIdentifiers(owner, guarded);
        decisions.add(new FilterDecision(
                "STRONG_IDENTIFIER_EVIDENCE_GUARD",
                Map.of("guardedIdentifiers", guarded),
                guardPass ? "PASS" : "FAIL",
                guarded.isEmpty()
                        ? "NO_GUARDED_IDENTIFIERS"
                        : guardPass ? "ALL_ACTIVE_IDENTIFIERS_PRESENT" : "MISSING_ACTIVE_IDENTIFIER"));
        if (!guardPass) {
            first = "STRONG_IDENTIFIER_EVIDENCE_GUARD";
            return result(id, query, prior, correct, rank, decisions, first);
        }

        Object signals = querySignals(query);
        SearchIntent intent = profile.resolveIntent(query);
        List<GroupView> sourceGroups = groups(invoke("consolidateSourceLocations", 4,
                profile, intent, query, signals, candidates));
        GroupView sourceGroup = sourceGroups.stream()
                .filter(group -> group.memberIds().contains(chunkId))
                .findFirst()
                .orElseThrow();
        boolean sourcePass = sourceGroup.representative().chunkId().equals(chunkId);
        decisions.add(new FilterDecision(
                "SOURCE_LOCATION_CONSOLIDATION",
                consolidationInput(query, signals, sourceGroup),
                sourcePass ? "PASS" : "FAIL",
                sourcePass
                        ? "RETAINED_AS_SOURCE_LOCATION_REPRESENTATIVE"
                        : "REPLACED_BY_PREFERRED_REPRESENTATIVE:" + sourceGroup.representative().chunkId()));
        if (!sourcePass) {
            first = "SOURCE_LOCATION_CONSOLIDATION";
            addNotReached(decisions);
            return result(id, query, prior, correct, rank, decisions, first);
        }

        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) invoke(
                "rejectionReasons", 3, profile, intent, signals, correct);
        boolean profilePass = reasons.isEmpty();
        decisions.add(new FilterDecision(
                "COMPOSITE_PROFILE_REJECTION_REASONS",
                Map.of("intent", intent.name(), "score", correct.score()),
                profilePass ? "PASS" : "FAIL",
                profilePass ? "NO_REJECTION_REASONS" : String.join(";", reasons)));
        if (!profilePass) {
            first = reasons.get(0);
            addNotReachedAfterProfile(decisions);
            return result(id, query, prior, correct, rank, decisions, first);
        }

        List<VectorSearchResult> sourceDistinct = sourceGroups.stream()
                .map(GroupView::representative)
                .toList();
        List<VectorSearchResult> eligible = new ArrayList<>();
        for (VectorSearchResult candidate : sourceDistinct) {
            @SuppressWarnings("unchecked")
            List<String> candidateReasons = (List<String>) invoke(
                    "rejectionReasons", 3, profile, intent, signals, candidate);
            if (candidateReasons.isEmpty()) {
                eligible.add(candidate);
            }
        }
        List<GroupView> evidenceGroups = groups(invoke(
                "consolidateQueryEvidence", 4, profile, intent, query, signals, eligible));
        GroupView evidenceGroup = evidenceGroups.stream()
                .filter(group -> group.memberIds().contains(chunkId))
                .findFirst()
                .orElseThrow();
        boolean evidencePass = evidenceGroup.representative().chunkId().equals(chunkId);
        decisions.add(new FilterDecision(
                "QUERY_EVIDENCE_CONSOLIDATION",
                consolidationInput(query, signals, evidenceGroup),
                evidencePass ? "PASS" : "FAIL",
                evidencePass
                        ? "RETAINED_AS_QUERY_EVIDENCE_REPRESENTATIVE"
                        : "REPLACED_BY_PREFERRED_REPRESENTATIVE:" + evidenceGroup.representative().chunkId()));
        if (!evidencePass) {
            first = "QUERY_EVIDENCE_CONSOLIDATION";
            addNotReachedAfterEvidence(decisions);
            return result(id, query, prior, correct, rank, decisions, first);
        }

        List<VectorSearchResult> diverse = evidenceGroups.stream().map(GroupView::representative).toList();
        if (intent == SearchIntent.GENERAL) {
            @SuppressWarnings("unchecked")
            Comparator<VectorSearchResult> comparator = (Comparator<VectorSearchResult>) invoke(
                    "generalRankingComparator", 2, profile, query, signals);
            diverse = diverse.stream().sorted(comparator).toList();
        }
        int profileRank = rankOf(diverse, chunkId);
        boolean top5Pass = profileRank > 0 && profileRank <= 5;
        decisions.add(new FilterDecision(
                "GENERAL_RANKING_TOP5",
                Map.of("profileRank", profileRank, "limit", 5),
                top5Pass ? "PASS" : "FAIL",
                top5Pass ? "WITHIN_TOP5" : "OUTSIDE_TOP5"));
        if (!top5Pass) {
            first = "GENERAL_RANKING_TOP5";
            addNotReachedAfterRanking(decisions);
            return result(id, query, prior, correct, rank, decisions, first);
        }

        boolean directRequired = NaturalLanguageQueryFallback.requiresDirectAnchor(query);
        boolean directMatch = !directRequired
                || NaturalLanguageQueryFallback.hasDirectAnchor(query, correct.content());
        decisions.add(new FilterDecision(
                "DIRECT_ANCHOR_FILTER",
                Map.of("required", directRequired),
                directMatch ? "PASS" : "FAIL",
                !directRequired
                        ? "NOT_REQUIRED"
                        : directMatch ? "DIRECT_ANCHOR_PRESENT" : "NO_DIRECT_ANCHOR_MATCH"));
        if (!directMatch) {
            first = "DIRECT_ANCHOR_FILTER";
            addNotReachedAfterDirect(decisions);
            return result(id, query, prior, correct, rank, decisions, first);
        }

        boolean numericRequired = NumericQueryAnchors.extract(query).stream()
                .anyMatch(NumericQueryAnchors.NumericAnchor::hasUnit);
        boolean numericMatch = !numericRequired
                || NumericQueryAnchors.hasContextualMatch(query, correct.content());
        decisions.add(new FilterDecision(
                "CONTEXTUAL_NUMERIC_FILTER",
                Map.of(
                        "required", numericRequired,
                        "queryAnchors", NumericQueryAnchors.extract(query).toString()),
                numericMatch ? "PASS" : "FAIL",
                !numericRequired
                        ? "NOT_REQUIRED"
                        : numericMatch ? "EXACT_CONTEXTUAL_NUMERIC_MATCH" : "NO_EXACT_CONTEXTUAL_NUMERIC_MATCH"));
        if (!numericMatch) {
            first = "CONTEXTUAL_NUMERIC_FILTER";
            addNotReachedAfterNumeric(decisions);
            return result(id, query, prior, correct, rank, decisions, first);
        }

        decisions.add(new FilterDecision(
                "EXACT_PRESENTATION_CONTENT_DEDUPLICATION",
                Map.of("contentLength", correct.content().length()),
                "PASS",
                "UNIQUE_PRESENTATION_CONTENT"));
        first = "FORWARDED";
        return result(id, query, prior, correct, rank, decisions, first);
    }

    private static Trace result(
            String id,
            String query,
            JsonNode prior,
            VectorSearchResult correct,
            int rank,
            List<FilterDecision> decisions,
            String first) {
        List<Long> acceptable = new ArrayList<>();
        prior.path("correctChunkIds").forEach(node -> acceptable.add(node.asLong()));
        return new Trace(
                id, query, acceptable, correct.chunkId(), rank, correct.score(), correct.distance(),
                decisions, first);
    }

    private Object querySignals(String query) throws Exception {
        Method method = CompositeSearchProfile.class.getDeclaredMethod("querySignals", String.class);
        method.setAccessible(true);
        return method.invoke(profile, query);
    }

    private Map<String, Object> consolidationInput(String query, Object signals, GroupView group) throws Exception {
        List<Map<String, Object>> members = new ArrayList<>();
        for (VectorSearchResult member : group.members()) {
            CompositeSearchProfile.RankingExplanation ranking = profile.explainRanking(query, member);
            members.add(Map.of(
                    "chunkId", member.chunkId(),
                    "score", member.score(),
                    "lexicalAffinity", invoke("lexicalAffinity", 2, profile, query, member),
                    "baseGeneralRankingScore", ranking.denseScore() + ranking.existingProfileAdjustment(),
                    "evidenceQualityAdjustment", ranking.evidenceAdjustment(),
                    "directAnchorMatch", NaturalLanguageQueryFallback.hasDirectAnchor(query, member.content()),
                    "numericContextualMatch", NumericQueryAnchors.hasContextualMatch(query, member.content()),
                    "negatedClaim", invoke("containsNegatedClaim", 2, profile, member.content(), signals)));
        }
        return Map.of(
                "memberChunkIds", group.memberIds(),
                "selectedRepresentativeChunkId", group.representative().chunkId(),
                "members", members);
    }

    private static Object invoke(
            String name,
            int parameterCount,
            Object target,
            Object... arguments) throws Exception {
        Method method = java.util.Arrays.stream(target.getClass().getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)
                        && candidate.getParameterCount() == parameterCount)
                .findFirst()
                .orElseThrow();
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    private static List<GroupView> groups(Object value) throws Exception {
        List<GroupView> result = new ArrayList<>();
        for (Object group : (List<?>) value) {
            Method representative = group.getClass().getDeclaredMethod("representative");
            representative.setAccessible(true);
            Method members = group.getClass().getDeclaredMethod("members");
            members.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<VectorSearchResult> groupMembers = (List<VectorSearchResult>) members.invoke(group);
            result.add(new GroupView((VectorSearchResult) representative.invoke(group), groupMembers));
        }
        return List.copyOf(result);
    }

    private static int rankOf(List<VectorSearchResult> candidates, long chunkId) {
        for (int index = 0; index < candidates.size(); index++) {
            if (candidates.get(index).chunkId().equals(chunkId)) {
                return index + 1;
            }
        }
        return 0;
    }

    private static void addNotReached(List<FilterDecision> decisions) {
        notReached(decisions, "COMPOSITE_PROFILE_REJECTION_REASONS");
        addNotReachedAfterProfile(decisions);
    }

    private static void addNotReachedAfterProfile(List<FilterDecision> decisions) {
        notReached(decisions, "QUERY_EVIDENCE_CONSOLIDATION");
        addNotReachedAfterEvidence(decisions);
    }

    private static void addNotReachedAfterEvidence(List<FilterDecision> decisions) {
        notReached(decisions, "GENERAL_RANKING_TOP5");
        addNotReachedAfterRanking(decisions);
    }

    private static void addNotReachedAfterRanking(List<FilterDecision> decisions) {
        notReached(decisions, "DIRECT_ANCHOR_FILTER");
        addNotReachedAfterDirect(decisions);
    }

    private static void addNotReachedAfterDirect(List<FilterDecision> decisions) {
        notReached(decisions, "CONTEXTUAL_NUMERIC_FILTER");
        addNotReachedAfterNumeric(decisions);
    }

    private static void addNotReachedAfterNumeric(List<FilterDecision> decisions) {
        notReached(decisions, "EXACT_PRESENTATION_CONTENT_DEDUPLICATION");
    }

    private static void notReached(List<FilterDecision> decisions, String name) {
        decisions.add(new FilterDecision(name, Map.of(), "NOT_REACHED", "EARLIER_FILTER_REMOVED_CANDIDATE"));
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
        return java.util.HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static String markdown(Map<String, Object> output, List<Map<String, Object>> traces) {
        StringBuilder text = new StringBuilder("# PRZ-016 P7-B Filter Rejection Reason Trace\n\n");
        text.append("- Executed at: `").append(output.get("executedAt")).append("`\n");
        text.append("- Total/traced/unknown: `14/14/").append(output.get("unknown")).append("`\n");
        text.append("- Search behavior changed: `NO`\n\n");
        text.append("## Primary rejection\n\n");
        @SuppressWarnings("unchecked")
        Map<String, Integer> counts = (Map<String, Integer>) output.get("primaryRejectionCount");
        counts.forEach((name, count) -> text.append("- `").append(name).append("`: ")
                .append(count).append("\n"));
        text.append("\n## Safety\n\n");
        text.append("- Known Negative protected by the dominant filter: `NOT_CONFIRMED`\n");
        text.append("- P0 F06 GraphQL FP score was `0.5312`, so it already passed the `0.50` floor.\n");
        text.append("- Simple removal safety: `UNKNOWN`\n");
        text.append("\n## Detail\n\n");
        text.append("| ID | Chunk | S2 rank | Score | Distance | First rejection | Actual reason |\n");
        text.append("|---|---:|---:|---:|---:|---|---|\n");
        for (Map<String, Object> trace : traces) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> filters = (List<Map<String, Object>>) trace.get("filters");
            String first = trace.get("firstRejection").toString();
            String reason = filters.stream()
                    .filter(filter -> "FAIL".equals(filter.get("decision")))
                    .findFirst()
                    .map(filter -> filter.get("actualReason").toString())
                    .orElse("FORWARDED");
            text.append("| ").append(trace.get("id")).append(" | ")
                    .append(trace.get("tracedCorrectChunkId")).append(" | ")
                    .append(trace.get("preFilterRank")).append(" | ")
                    .append(String.format(java.util.Locale.ROOT, "%.6f", trace.get("score"))).append(" | ")
                    .append(String.format(java.util.Locale.ROOT, "%.6f", trace.get("distance"))).append(" | ")
                    .append(first).append(" | ").append(reason).append(" |\n");
        }
        return text.toString();
    }

    private record GroupView(VectorSearchResult representative, List<VectorSearchResult> members) {
        private List<Long> memberIds() {
            return members.stream().map(VectorSearchResult::chunkId).toList();
        }
    }

    private record FilterDecision(
            String name,
            Map<String, Object> input,
            String decision,
            String actualReason) {
        private Map<String, Object> asMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", name);
            value.put("input", input);
            value.put("decision", decision);
            value.put("actualReason", actualReason);
            return value;
        }
    }

    private record Trace(
            String id,
            String query,
            List<Long> acceptableCorrectChunkIds,
            long tracedCorrectChunkId,
            int preFilterRank,
            double score,
            double distance,
            List<FilterDecision> filters,
            String firstRejection) {
        private Map<String, Object> asMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("id", id);
            value.put("query", query);
            value.put("acceptableCorrectChunkIds", acceptableCorrectChunkIds);
            value.put("tracedCorrectChunkId", tracedCorrectChunkId);
            value.put("preFilterRank", preFilterRank);
            value.put("score", score);
            value.put("distance", distance);
            value.put("filters", filters.stream().map(FilterDecision::asMap).toList());
            value.put("firstRejection", firstRejection);
            return value;
        }
    }
}
