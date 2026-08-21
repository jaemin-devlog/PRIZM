package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.changelog.service.ChangeLogDispatchTransaction;
import com.prizm.document.dto.response.DocumentUploadResponse;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.service.DocumentUploadService;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.ingestion.service.ClaimedProcessingJob;
import com.prizm.ingestion.service.DocumentIndexingProcessor;
import com.prizm.ingestion.service.ProcessingJobClaimService;
import com.prizm.search.evaluation.trace.GroundTruthV2Evaluator;
import com.prizm.search.evaluation.trace.ProductionSearchDecisionTracer;
import com.prizm.search.evaluation.trace.SearchDecisionTrace;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.service.EvidenceExpansionService;
import com.prizm.search.service.SearchService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Frozen independent final corpus; production upload/index/search only, never direct chunk seeding. */
@ActiveProfiles("search-evaluation")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
class FinalUnseenJudgeCE2ETest {
    private static final Path ROOT = Path.of("specs/PRZ-016-search-performance-v2/final-unseen-judge-c");
    private static final Path DATASET = ROOT.resolve("dataset");
    private static final Path STORAGE = Path.of("local/final-unseen-judge-c-storage").toAbsolutePath().normalize();
    private static final DockerImageName IMAGE = DockerImageName.parse("pgvector/pgvector:0.8.2-pg16-bookworm").asCompatibleSubstituteFor("postgres");
    @Container static final PostgreSQLContainer postgres = new PostgreSQLContainer(IMAGE).withDatabaseName("prizm_final_unseen_c").withUsername("prizm").withPassword("final-unseen-c");
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) { r.add("spring.datasource.url", postgres::getJdbcUrl); r.add("spring.datasource.username", postgres::getUsername); r.add("spring.datasource.password", postgres::getPassword); r.add("prizm.storage.root", STORAGE::toString); r.add("prizm.change-log.scheduler.enabled", () -> "false"); }

    @Autowired JdbcTemplate jdbc;
    @Autowired DocumentUploadService uploadService;
    @Autowired ChangeLogDispatchTransaction dispatch;
    @Autowired ProcessingJobClaimService claimService;
    @Autowired DocumentIndexingProcessor indexing;
    @Autowired EmbeddingService embeddingService;
    @Autowired EmbeddingValidator embeddingValidator;
    @Autowired VectorSearchRepository vectorRepository;
    @Autowired CompositeSearchProfile profile;
    @Autowired EvidenceExpansionService expansion;
    @Autowired SearchService searchService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final GroundTruthV2Evaluator evaluator = new GroundTruthV2Evaluator();

    @Test
    void runsFinalUnseenJudgeCThroughProductionIngestionAndSearch() throws Exception {
        Corpus corpus = ingest();
        ProductionSearchDecisionTracer tracer = new ProductionSearchDecisionTracer(embeddingService, embeddingValidator, vectorRepository, profile, expansion, searchService);
        JsonNode questions = mapper.readTree(DATASET.resolve("questions.json").toFile());
        Map<String, JsonNode> truth = index(mapper.readTree(DATASET.resolve("ground-truth.json").toFile()).path("queries"));
        Counters counters = new Counters(); ArrayNode results = mapper.createArrayNode(); ArrayNode traces = mapper.createArrayNode();
        boolean parity = true, ownerIsolation = true, activeIsolation = true;
        for (JsonNode question : questions.path("questions")) {
            String id = question.path("id").asText(), query = question.path("query").asText(), label = question.path("label").asText();
            Long owner = corpus.owners().get(question.path("userKey").asText()); SearchDecisionTrace trace = tracer.trace(owner, query);
            parity &= trace.productionResponseMatch(); ownerIsolation &= ownerIsolated(owner, trace); activeIsolation &= activeIsolated(corpus, trace);
            ObjectNode row = results.addObject(); row.put("id", id); row.put("query", query); row.put("label", label); row.put("type", question.path("type").asText()); row.put("ownerUserId", owner);
            row.put("responseState", trace.responseState()); row.put("productionResponseMatch", trace.productionResponseMatch()); row.set("finalResults", mapper.valueToTree(trace.finalResults())); row.set("displayedEvidence", mapper.valueToTree(trace.localization())); row.set("queryClaimRequirements", mapper.valueToTree(profile.queryClaimRequirements(query)));
            if ("POSITIVE".equals(label)) {
                SearchDecisionTrace.GroundTruthOutcome outcome = evaluator.evaluatePositive(trace, truth.get(id), corpus.fixtureByDocument()); RawHit hit = expectedRank(owner, query, truth.get(id), corpus);
                counters.positive(outcome, hit); if (hit.rank() == null) row.putNull("expectedEvidenceRawDenseRank"); else row.put("expectedEvidenceRawDenseRank", hit.rank()); if (hit.score() == null) row.putNull("expectedEvidenceRawDenseScore"); else row.put("expectedEvidenceRawDenseScore", hit.score());
                row.set("groundTruthOutcome", mapper.valueToTree(outcome)); row.put("firstFailureStage", outcome.firstFailureStage().name()); row.set("failureTrace", mapper.valueToTree(failureTrace(trace, outcome)));
            } else { boolean fp = evaluator.isFalsePositive(trace); counters.negative(question.path("type").asText(), fp); row.put("falsePositive", fp); row.set("traceSignals", mapper.valueToTree(trace.candidates().stream().limit(5).toList())); }
            traces.add(mapper.valueToTree(trace));
        }
        ObjectNode report = mapper.createObjectNode(); report.put("schemaVersion", 1); report.put("phase", "FINAL_UNSEEN_JUDGE_C"); report.put("executedAt", Instant.now().toString()); report.put("productionUploadPath", true); report.put("directChunkSeed", false); report.put("synchronousTestOrchestration", true);
        report.put("userCount", corpus.owners().size()); report.put("documentCount", corpus.documentIds().size()); report.put("activeChunkCount", corpus.activeChunkCount()); report.put("positiveQueryCount", counters.positives); report.put("negativeQueryCount", counters.negatives);
        report.set("metrics", counters.metrics(mapper)); report.set("negativeTypeDistribution", mapper.valueToTree(counters.negativeTypes)); report.set("negativeFalsePositivesByType", mapper.valueToTree(counters.falsePositives)); report.set("firstFailureDistribution", mapper.valueToTree(counters.failures));
        report.put("ownerIsolation", ownerIsolation ? "PASS" : "FAIL"); report.put("activeVersionIsolation", activeIsolation ? "PASS" : "FAIL"); report.put("crossUserLeakage", ownerIsolation ? 0 : 1); report.put("inactiveLeakage", activeIsolation ? 0 : 1); report.put("traceProductionParity", parity ? "PASS" : "FAIL"); report.put("multiProjectExpectedEvidenceSets", 2); report.put("multiProjectReturnedDistinctEvidence", multiProjectRetention(results)); report.put("duplicateResultCount", duplicateCount(results)); report.put("verdict", counters.pass(ownerIsolation, activeIsolation, parity) ? "PASS_SEARCH_FREEZE" : "FAIL_SEARCH_FREEZE_HOLD"); report.set("queries", results);
        Files.createDirectories(ROOT); Files.writeString(ROOT.resolve("judge-c-results.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n", StandardCharsets.UTF_8); Files.writeString(ROOT.resolve("judge-c-traces.json"), mapper.writerWithDefaultPrettyPrinter().writeValueAsString(traces) + "\n", StandardCharsets.UTF_8);
        assertThat(parity).isTrue(); assertThat(ownerIsolation).isTrue(); assertThat(activeIsolation).isTrue();
    }

    private Corpus ingest() throws Exception {
        JsonNode manifest = mapper.readTree(DATASET.resolve("corpus-manifest.json").toFile()); Map<String, Long> owners = new LinkedHashMap<>(); Map<Long, String> fixtures = new LinkedHashMap<>(); Set<Long> documents = new LinkedHashSet<>(), inactiveVersions = new LinkedHashSet<>(), inactiveChunks = new LinkedHashSet<>();
        for (JsonNode user : manifest.path("users")) { Long owner = jdbc.queryForObject("INSERT INTO users(email,password_hash,role,enabled) VALUES (?, '{noop}judge-c', 'USER', TRUE) RETURNING id", Long.class, user.path("email").asText()); owners.put(user.path("userKey").asText(), owner);
            for (JsonNode doc : user.path("documents")) { String active = doc.path("fixture").asText(), first = doc.hasNonNull("inactiveFixture") ? doc.path("inactiveFixture").asText() : active; DocumentUploadResponse upload = uploadService.upload(owner, doc.path("title").asText(), DocumentType.valueOf(doc.path("documentType").asText()), multipart(first)); process(upload.versionId()); documents.add(upload.documentId()); DocumentUploadResponse current = upload;
                if (doc.hasNonNull("inactiveFixture")) { inactiveVersions.add(upload.versionId()); inactiveChunks.addAll(chunkIds(upload.versionId())); current = uploadService.uploadVersion(owner, upload.documentId(), multipart(active)); process(current.versionId()); } fixtures.put(current.documentId(), active); }
        }
        Integer chunks = jdbc.queryForObject("SELECT count(*) FROM document_chunks c JOIN document_versions v ON v.id=c.document_version_id JOIN documents d ON d.active_version_id=v.id WHERE v.status='ACTIVE'", Integer.class); return new Corpus(Map.copyOf(owners), Map.copyOf(fixtures), Set.copyOf(documents), Set.copyOf(inactiveVersions), Set.copyOf(inactiveChunks), chunks);
    }
    private void process(Long version) { dispatch.dispatchNext(); ClaimedProcessingJob claimed = claimService.claimNext().orElseThrow(); assertThat(claimed.documentVersionId()).isEqualTo(version); indexing.process(claimed); }
    private MockMultipartFile multipart(String fixture) throws Exception { Path path = DATASET.resolve(fixture); return new MockMultipartFile("file", path.getFileName().toString(), "text/plain", Files.readAllBytes(path)); }
    private List<Long> chunkIds(Long version) { return jdbc.queryForList("SELECT id FROM document_chunks WHERE document_version_id=?", Long.class, version); }
    private RawHit expectedRank(Long owner, String query, JsonNode truth, Corpus corpus) { float[] e = embeddingService.embed(query); embeddingValidator.validate(e); String v = vector(e); List<RawHit> hits = jdbc.query("SELECT c.id,d.id,c.content,1.0-(c.embedding <=> CAST(? AS vector)) FROM document_chunks c JOIN document_versions v ON v.id=c.document_version_id JOIN documents d ON d.active_version_id=v.id WHERE v.status='ACTIVE' AND c.owner_user_id=? ORDER BY c.embedding <=> CAST(? AS vector),c.id", (rs,n) -> new RawHit(n + 1, rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getDouble(4)), v, owner, v); return hits.stream().filter(h -> matches(h, truth, corpus.fixtureByDocument())).findFirst().orElse(new RawHit(null, null, null, "", null)); }
    private static boolean matches(RawHit h, JsonNode truth, Map<Long, String> fixtures) { for (JsonNode set : truth.path("acceptableEvidenceSets")) { if (!Objects.equals(set.path("documentFixture").asText(), fixtures.get(h.document()))) continue; boolean ok = true; for (JsonNode clause : set.path("requiredClauses")) { boolean one = false; for (JsonNode anchor : clause.path("anchorAny")) if (norm(h.content()).contains(norm(anchor.asText()))) one = true; if (!one) ok = false; } if (ok) return true; } return false; }
    private boolean ownerIsolated(Long owner, SearchDecisionTrace trace) { return trace.candidates().stream().allMatch(c -> Objects.equals(owner, jdbc.queryForObject("SELECT owner_user_id FROM document_chunks WHERE id=?", Long.class, c.chunkId()))); }
    private static boolean activeIsolated(Corpus c, SearchDecisionTrace t) { return t.candidates().stream().noneMatch(x -> c.inactiveVersions().contains(x.documentVersionId())) && t.localization().stream().noneMatch(x -> c.inactiveChunks().contains(x.evidenceChunkId())); }
    private static Map<String, Object> failureTrace(SearchDecisionTrace t, SearchDecisionTrace.GroundTruthOutcome o) { return Map.of("firstFailureStage", o.firstFailureStage().name(), "diagnostics", o.diagnostics(), "finalResults", t.finalResults(), "evidence", t.localization()); }
    private static int duplicateCount(ArrayNode rows) { int duplicates = 0; for (JsonNode row : rows) { Set<String> snippets = new LinkedHashSet<>(); for (JsonNode evidence : row.path("displayedEvidence")) { String snippet = norm(evidence.path("snippet").asText()); if (!snippet.isBlank() && !snippets.add(snippet)) duplicates++; } } return duplicates; }
    private static int multiProjectRetention(ArrayNode rows) { for (JsonNode row : rows) if ("JC1-P1".equals(row.path("id").asText())) return row.path("groundTruthOutcome").path("matchedAcceptableEvidenceSetIds").size(); return 0; }
    private static String vector(float[] a) { StringBuilder b = new StringBuilder("["); for (int i = 0; i < a.length; i++) { if (i > 0) b.append(','); b.append(a[i]); } return b.append(']').toString(); }
    private static String norm(String s) { return Objects.requireNonNullElse(s, "").toLowerCase(Locale.ROOT).replace(",", "").replaceAll("\\s+", " ").trim(); }
    private static Map<String, JsonNode> index(JsonNode nodes) { Map<String, JsonNode> map = new LinkedHashMap<>(); nodes.forEach(n -> map.put(n.path("id").asText(), n)); return map; }
    private record Corpus(Map<String, Long> owners, Map<Long, String> fixtureByDocument, Set<Long> documentIds, Set<Long> inactiveVersions, Set<Long> inactiveChunks, int activeChunkCount) {}
    private record RawHit(Integer rank, Long chunk, Long document, String content, Double score) {}
    private static final class Counters { int positives, negatives, candidate5, candidate10, candidate20, top1, recall5, selected, displayed, localized; final Map<String, Integer> negativeTypes = new LinkedHashMap<>(), falsePositives = new LinkedHashMap<>(), failures = new LinkedHashMap<>();
        void positive(SearchDecisionTrace.GroundTruthOutcome o, RawHit h) { positives++; if (h.rank() != null && h.rank() <= 5) candidate5++; if (h.rank() != null && h.rank() <= 10) candidate10++; if (h.rank() != null && h.rank() <= 20) candidate20++; if (o.top1()) top1++; if (o.finalRecallAt5()) recall5++; if (o.selectedResultCorrect()) selected++; if (o.displayedEvidenceCorrect()) displayed++; if (o.localizationCorrect()) localized++; failures.merge(o.firstFailureStage().name(), 1, Integer::sum); }
        void negative(String type, boolean fp) { negatives++; negativeTypes.merge(type, 1, Integer::sum); if (fp) falsePositives.merge(type, 1, Integer::sum); }
        ObjectNode metrics(ObjectMapper m) { ObjectNode n = m.createObjectNode(); n.put("denseRecallAt5", ratio(candidate5, positives)); n.put("denseRecallAt10", ratio(candidate10, positives)); n.put("denseRecallAt20", ratio(candidate20, positives)); n.put("top1", ratio(top1, positives)); n.put("recallAt5", ratio(recall5, positives)); n.put("selectedResultCorrectness", ratio(selected, positives)); n.put("displayedEvidenceCorrectness", ratio(displayed, positives)); n.put("localizationCorrectness", ratio(localized, positives)); n.put("negativeFpr", ratio(falsePositives.values().stream().mapToInt(Integer::intValue).sum(), negatives)); return n; }
        boolean pass(boolean owner, boolean active, boolean parity) { return owner && active && parity && candidate20 >= Math.ceil(positives * .95) && recall5 >= Math.ceil(positives * .9) && falsePositives.values().stream().mapToInt(Integer::intValue).sum() <= Math.floor(negatives * .05) && displayed >= Math.ceil(positives * .9) && localized >= Math.ceil(positives * .9); }
        private static double ratio(int a, int b) { return b == 0 ? 0 : (double) a / b; }
    }
}
