package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.ingestion.service.DocumentTextExtractor;
import com.prizm.ingestion.service.PageText;
import com.prizm.ingestion.service.TextChunk;
import com.prizm.ingestion.service.TextChunker;
import com.prizm.search.evaluation.trace.ProductionSearchDecisionTracer;
import com.prizm.search.evaluation.trace.SearchDecisionTrace;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.repository.VectorSearchResult;
import com.prizm.search.service.EvidenceExpansionService;
import com.prizm.search.service.SearchService;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
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

/** Runs only the frozen PRZ-016 P16 Phase A candidate-recovery experiment. */
@ActiveProfiles("search-evaluation")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Testcontainers
class PhaseALiteralCandidateEvaluationTest {

    private static final Path PHASE = Path.of(
            "specs/PRZ-016-search-performance-v2/p16-literal-candidate-phase-a");
    private static final Path DATASET = PHASE.resolve("dataset-v3-realistic-pdf.json");
    private static final Path FREEZE = PHASE.resolve("freeze-manifest-v3-realistic-pdf.json");
    private static final Path RESULT = PHASE.resolve("phase-a-results-v3-realistic-pdf.json");
    private static final DockerImageName PGVECTOR_IMAGE = DockerImageName
            .parse("pgvector/pgvector:0.8.2-pg16-bookworm")
            .asCompatibleSubstituteFor("postgres");

    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer(PGVECTOR_IMAGE)
            .withDatabaseName("prizm_p16_literal_phase_a")
            .withUsername("prizm")
            .withPassword("p16-literal-phase-a");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("prizm.storage.root", () -> Path.of("local/p16-storage").toAbsolutePath().toString());
        registry.add("prizm.change-log.scheduler.enabled", () -> false);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired EmbeddingModel embeddingModel;
    @Autowired EmbeddingService embeddingService;
    @Autowired EmbeddingValidator embeddingValidator;
    @Autowired DocumentTextExtractor documentTextExtractor;
    @Autowired TextChunker textChunker;
    @Autowired VectorSearchRepository vectorRepository;
    @Autowired CompositeSearchProfile compositeSearchProfile;
    @Autowired EvidenceExpansionService evidenceExpansionService;
    @Autowired SearchService searchService;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void literalCandidateCanRecoverADenseMissWithoutChangingProduction() throws Exception {
        JsonNode dataset = mapper.readTree(DATASET.toFile());
        verifyFreeze(dataset);
        SeededCorpus corpus = seed(dataset);
        assertThat(corpus.primaryActiveChunkCount())
                .isBetween(60, 120)
                .isEqualTo(dataset.path("primaryOwnerActiveChunkCount").asInt());

        PhaseALiteralRetrievalEvaluator evaluator = new PhaseALiteralRetrievalEvaluator(
                vectorRepository,
                new PhaseALiteralCandidateRepository(jdbc),
                compositeSearchProfile);
        ProductionSearchDecisionTracer tracer = new ProductionSearchDecisionTracer(
                embeddingService,
                embeddingValidator,
                vectorRepository,
                compositeSearchProfile,
                evidenceExpansionService,
                searchService);

        ArrayNode queryResults = mapper.createArrayNode();
        int positiveCount = 0;
        int productionParityCount = 0;
        int literalOnlyRecoveryCount = 0;
        int d0Q0FilteredHitCount = 0;
        int d1Q0FilteredHitCount = 0;
        int d1Q0FilteredRecoveryCount = 0;
        int productionD0FinalHitCount = 0;
        int candidateUnionEqualsD0Count = 0;
        int q0FilteredEqualsCount = 0;
        boolean ownerIsolation = true;
        boolean activeIsolation = true;
        boolean boundaryIsolation = true;
        boolean caseInsensitiveMatch = false;

        for (JsonNode queryNode : dataset.path("queries")) {
            String id = queryNode.path("id").asText();
            String query = queryNode.path("query").asText();
            Long ownerId = corpus.ownerByKey().get(queryNode.path("owner").asText());
            Set<Long> expectedChunkIds = queryNode.hasNonNull("expectedKey")
                    ? corpus.chunkByKey().get(queryNode.path("expectedKey").asText())
                    : Set.of();

            float[] embedding = embeddingService.embed(query);
            embeddingValidator.validate(embedding);
            PhaseALiteralRetrievalEvaluator.Evaluation evaluation = evaluator.evaluate(
                    ownerId, query, embedding);
            SearchDecisionTrace trace = tracer.trace(ownerId, query);

            Set<Long> denseIds = ids(evaluation.denseCandidates());
            Set<Long> literalIds = ids(evaluation.literalCandidates());
            Set<Long> unionIds = ids(evaluation.unionCandidates());
            Set<Long> d0Q0FilteredIds = ids(evaluation.d0Filtered());
            Set<Long> d1Q0FilteredIds = ids(evaluation.d1Filtered());
            boolean denseHasExpected = intersects(denseIds, expectedChunkIds);
            boolean literalHasExpected = intersects(literalIds, expectedChunkIds);
            boolean literalOnlyHasExpected = literalHasExpected && !denseHasExpected;
            boolean unionHasExpected = intersects(unionIds, expectedChunkIds);
            boolean d0Q0FilteredHasExpected = intersects(d0Q0FilteredIds, expectedChunkIds);
            boolean d1Q0FilteredHasExpected = intersects(d1Q0FilteredIds, expectedChunkIds);
            boolean d1Q0FilteredRecoveredExpected =
                    d1Q0FilteredHasExpected && !d0Q0FilteredHasExpected;
            Long fullDenseRank = expectedChunkIds.isEmpty()
                    ? null
                    : expectedChunkIds.stream()
                            .map(expectedChunkId -> fullDenseRank(ownerId, embedding, expectedChunkId))
                            .min(Long::compareTo)
                            .orElseThrow();
            List<Long> productionIds = trace.finalResults().stream()
                    .map(SearchDecisionTrace.FinalResultTrace::chunkId)
                    .toList();
            boolean productionD0FinalHasExpected = productionIds.stream()
                    .anyMatch(expectedChunkIds::contains);
            candidateUnionEqualsD0Count += orderedIds(evaluation.denseCandidates())
                    .equals(orderedIds(evaluation.unionCandidates())) ? 1 : 0;
            q0FilteredEqualsCount += orderedIds(evaluation.d0Filtered())
                    .equals(orderedIds(evaluation.d1Filtered())) ? 1 : 0;
            boolean productionParity = trace.productionResponseMatch()
                    && !trace.queryVariants().isEmpty()
                    && orderedIds(evaluation.denseCandidates()).equals(
                            trace.queryVariants().get(0).retrievedChunkIds());

            boolean queryOwnerIsolated = candidatesBelongToOwner(ownerId, literalIds);
            boolean queryActiveIsolated = candidatesAreActive(literalIds);
            ownerIsolation &= queryOwnerIsolated;
            activeIsolation &= queryActiveIsolated;
            productionParityCount += productionParity ? 1 : 0;

            if (!expectedChunkIds.isEmpty()) {
                positiveCount++;
                literalOnlyRecoveryCount += literalOnlyHasExpected ? 1 : 0;
                d0Q0FilteredHitCount += d0Q0FilteredHasExpected ? 1 : 0;
                d1Q0FilteredHitCount += d1Q0FilteredHasExpected ? 1 : 0;
                d1Q0FilteredRecoveryCount += d1Q0FilteredRecoveredExpected ? 1 : 0;
                productionD0FinalHitCount += productionD0FinalHasExpected ? 1 : 0;
                assertThat(literalHasExpected).as("literal expected: %s", id).isTrue();
                assertThat(unionHasExpected).as("union expected: %s", id).isTrue();
                if (queryNode.path("caseVariant").asBoolean(false)) {
                    caseInsensitiveMatch = literalHasExpected;
                }
            } else {
                boolean literalEmpty = literalIds.isEmpty();
                boundaryIsolation &= literalEmpty;
                assertThat(literalIds).as("safety literal candidates: %s", id).isEmpty();
            }
            assertThat(productionParity).as("D0 production parity: %s", id).isTrue();
            assertThat(queryOwnerIsolated).as("owner isolation: %s", id).isTrue();
            assertThat(queryActiveIsolated).as("ACTIVE isolation: %s", id).isTrue();

            ObjectNode result = mapper.createObjectNode();
            result.put("id", id);
            result.put("owner", queryNode.path("owner").asText());
            result.put("query", query);
            result.set("expectedChunkIds", mapper.valueToTree(expectedChunkIds));
            result.put("denseCandidateHasExpected", denseHasExpected);
            result.put("literalCandidateHasExpected", literalHasExpected);
            result.put("literalOnlyCandidateHasExpected", literalOnlyHasExpected);
            result.put("unionCandidateHasExpected", unionHasExpected);
            result.put("d0Q0FilteredHasExpected", d0Q0FilteredHasExpected);
            result.put("d1Q0FilteredHasExpected", d1Q0FilteredHasExpected);
            result.put("d1Q0FilteredRecoveredExpected", d1Q0FilteredRecoveredExpected);
            result.put("productionD0FinalHasExpected", productionD0FinalHasExpected);
            if (fullDenseRank == null) {
                result.putNull("expectedFullDenseRank");
            } else {
                result.put("expectedFullDenseRank", fullDenseRank);
            }
            result.put("productionParity", productionParity);
            result.put("ownerIsolation", queryOwnerIsolated);
            result.put("activeIsolation", queryActiveIsolated);
            result.set("denseCandidateIds", mapper.valueToTree(orderedIds(evaluation.denseCandidates())));
            result.set("literalCandidateIds", mapper.valueToTree(orderedIds(evaluation.literalCandidates())));
            result.set("unionCandidateIds", mapper.valueToTree(orderedIds(evaluation.unionCandidates())));
            result.set("literalOnlyCandidateIds", mapper.valueToTree(literalIds.stream()
                    .filter(candidateId -> !denseIds.contains(candidateId)).toList()));
            result.set("d0ProductionFinalIds", mapper.valueToTree(productionIds));
            result.set("d0Q0FilteredIds", mapper.valueToTree(orderedIds(evaluation.d0Filtered())));
            result.set("d1Q0FilteredIds", mapper.valueToTree(orderedIds(evaluation.d1Filtered())));
            if (!expectedChunkIds.isEmpty() && !productionD0FinalHasExpected) {
                result.set("finalDropQueryVariants", mapper.valueToTree(trace.queryVariants()));
                result.set("finalDropExpectedCandidateTraces", mapper.valueToTree(trace.candidates().stream()
                        .filter(candidate -> expectedChunkIds.contains(candidate.chunkId()))
                        .toList()));
            }
            queryResults.add(result);
        }

        ObjectNode report = mapper.createObjectNode();
        report.put("schemaVersion", 1);
        report.put("phase", "PRZ-016-P16-LITERAL-CANDIDATE-PHASE-A");
        report.put("executedAt", Instant.now().toString());
        report.put("sourceCommit", "05044b11038eeebaebac650c67d0d90136ae10bc");
        report.put("database", "Testcontainers PostgreSQL 16 + pgvector 0.8.2");
        report.put("embeddingModel", "Ollama bge-m3");
        report.put("primaryOwnerActiveChunkCount", corpus.primaryActiveChunkCount());
        report.put("pdfPageCount", corpus.pdfPageCount());
        report.put("pdfExtractedCharacterCount", corpus.pdfExtractedCharacterCount());
        report.put("pdfChunkCount", corpus.pdfChunkCount());
        report.put("queryCount", dataset.path("queries").size());
        report.put("positiveQueryCount", positiveCount);
        report.put("productionParityCount", productionParityCount);
        report.put("literalOnlyRecoveryCount", literalOnlyRecoveryCount);
        report.put("d0Q0FilteredHitCount", d0Q0FilteredHitCount);
        report.put("d1Q0FilteredHitCount", d1Q0FilteredHitCount);
        report.put("d1Q0FilteredRecoveryCount", d1Q0FilteredRecoveryCount);
        report.put("productionD0FinalHitCount", productionD0FinalHitCount);
        report.put("candidateUnionEqualsD0Count", candidateUnionEqualsD0Count);
        report.put("q0FilteredEqualsCount", q0FilteredEqualsCount);
        report.put("ownerIsolation", ownerIsolation ? "PASS" : "FAIL");
        report.put("activeIsolation", activeIsolation ? "PASS" : "FAIL");
        report.put("exactBoundary", boundaryIsolation ? "PASS" : "FAIL");
        report.put("caseInsensitiveMatch", caseInsensitiveMatch ? "PASS" : "FAIL");
        report.put("productionChanges", 0);
        report.put("phaseBExecuted", false);
        report.set("queries", queryResults);
        Files.writeString(
                RESULT,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + System.lineSeparator(),
                StandardCharsets.UTF_8);

        assertThat(positiveCount).isEqualTo(7);
        assertThat(productionParityCount).isEqualTo(dataset.path("queries").size());
        assertThat(productionD0FinalHitCount).isEqualTo(positiveCount);
        assertThat(ownerIsolation).isTrue();
        assertThat(activeIsolation).isTrue();
        assertThat(boundaryIsolation).isTrue();
        assertThat(caseInsensitiveMatch).isTrue();
    }

    private void verifyFreeze(JsonNode dataset) throws Exception {
        JsonNode freeze = mapper.readTree(FREEZE.toFile());
        assertThat(dataset.path("datasetId").asText()).isEqualTo(freeze.path("datasetId").asText());
        assertThat(freeze.path("frozenBeforeDenseOrLiteralEvaluation").asBoolean()).isTrue();
        for (JsonNode asset : freeze.path("assets")) {
            Path path = Path.of(asset.path("path").asText());
            assertThat(Files.size(path)).as("frozen bytes: %s", path)
                    .isEqualTo(asset.path("bytes").asLong());
            assertThat(sha256(path)).as("frozen hash: %s", path)
                    .isEqualTo(asset.path("sha256").asText());
        }
    }

    private SeededCorpus seed(JsonNode dataset) throws Exception {
        Long primaryOwner = insertUser("primary");
        Long otherOwner = insertUser("other");
        Map<String, Long> owners = Map.of("PRIMARY", primaryOwner, "OTHER", otherOwner);
        Map<String, Set<Long>> chunks = new LinkedHashMap<>();

        Long primaryDocument = insertDocument(primaryOwner, "Synthetic Long-form Project Report");
        Long primaryVersion = insertVersion(
                primaryOwner, primaryDocument, 1, "primary-active.pdf", DocumentFileType.PDF,
                sha256(Path.of(dataset.path("pdf").path("path").asText())));
        PdfSeedStats pdfStats = insertPdfChunks(primaryOwner, primaryVersion, dataset, chunks);
        activate(primaryDocument, primaryVersion);

        Long versionedDocument = insertDocument(primaryOwner, "Synthetic Version Isolation");
        Long inactiveVersion = insertVersion(
                primaryOwner, versionedDocument, 1, "inactive-v1.txt", DocumentFileType.TXT,
                "0".repeat(64));
        JsonNode inactive = dataset.path("primaryInactiveOnly");
        insertChunks(primaryOwner, inactiveVersion, List.of(new KeyedContent(
                inactive.path("key").asText(), inactive.path("content").asText())), chunks);
        Long activeVersion = insertVersion(
                primaryOwner, versionedDocument, 2, "active-v2.txt", DocumentFileType.TXT,
                "0".repeat(64));
        JsonNode replacement = dataset.path("primaryActiveReplacement");
        insertChunks(primaryOwner, activeVersion, List.of(new KeyedContent(
                replacement.path("key").asText(), replacement.path("content").asText())), chunks);
        activate(versionedDocument, activeVersion);

        List<KeyedContent> otherChunks = new ArrayList<>();
        dataset.path("otherOwnerActive").forEach(target -> otherChunks.add(new KeyedContent(
                target.path("key").asText(), target.path("content").asText())));
        Long otherDocument = insertDocument(otherOwner, "Synthetic Other Owner Corpus");
        Long otherVersion = insertVersion(
                otherOwner, otherDocument, 1, "other-active.txt", DocumentFileType.TXT,
                "0".repeat(64));
        insertChunks(otherOwner, otherVersion, otherChunks, chunks);
        activate(otherDocument, otherVersion);

        int primaryActiveChunks = jdbc.queryForObject("""
                SELECT count(*)
                FROM document_chunks chunk
                JOIN document_versions version
                  ON version.id = chunk.document_version_id
                 AND version.owner_user_id = chunk.owner_user_id
                JOIN documents document
                  ON document.id = version.document_id
                 AND document.active_version_id = version.id
                 AND document.owner_user_id = version.owner_user_id
                WHERE document.owner_user_id = ?
                  AND version.owner_user_id = ?
                  AND chunk.owner_user_id = ?
                  AND version.status = 'ACTIVE'
                """, Integer.class, primaryOwner, primaryOwner, primaryOwner);
        assertThat(pdfStats.pageCount()).isEqualTo(dataset.path("pdf").path("pageCount").asInt());
        assertThat(pdfStats.extractedCharacterCount())
                .isEqualTo(dataset.path("pdf").path("extractedCharacterCount").asInt());
        assertThat(pdfStats.chunkCount()).isEqualTo(dataset.path("pdf").path("chunkCount").asInt());
        assertThat(primaryActiveChunks).isEqualTo(dataset.path("primaryOwnerActiveChunkCount").asInt());
        return new SeededCorpus(
                owners,
                chunks.entrySet().stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> Set.copyOf(entry.getValue()))),
                primaryActiveChunks,
                pdfStats.pageCount(),
                pdfStats.extractedCharacterCount(),
                pdfStats.chunkCount());
    }

    private Long insertUser(String key) {
        return jdbc.queryForObject("""
                INSERT INTO users(email, password_hash, role, enabled)
                VALUES (?, 'synthetic-not-used', 'USER', true)
                RETURNING id
                """, Long.class, "p16-" + key + "@example.invalid");
    }

    private Long insertDocument(Long ownerId, String title) {
        return jdbc.queryForObject("""
                INSERT INTO documents(title, owner_user_id, document_type)
                VALUES (?, ?, 'OTHER')
                RETURNING id
                """, Long.class, title, ownerId);
    }

    private Long insertVersion(
            Long ownerId,
            Long documentId,
            int versionNo,
            String fileName,
            DocumentFileType fileType,
            String contentHash) {
        return jdbc.queryForObject("""
                INSERT INTO document_versions(
                    document_id, version_no, original_file_name, stored_file_path,
                    file_type, content_hash, status, owner_user_id)
                VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?)
                RETURNING id
                """, Long.class,
                documentId,
                versionNo,
                fileName,
                "p16/" + documentId + "/" + versionNo + "/" + fileName,
                fileType.name(),
                contentHash,
                ownerId);
    }

    private void insertChunks(
            Long ownerId,
            Long versionId,
            List<KeyedContent> keyedContents,
            Map<String, Set<Long>> chunks) {
        List<String> contents = keyedContents.stream().map(KeyedContent::content).toList();
        List<float[]> embeddings = embeddingModel.embed(contents);
        assertThat(embeddings).hasSameSizeAs(contents);
        for (int index = 0; index < keyedContents.size(); index++) {
            float[] embedding = embeddings.get(index);
            embeddingValidator.validate(embedding);
            int sourceIndex = index + 1;
            KeyedContent keyed = keyedContents.get(index);
            Long chunkId = jdbc.queryForObject("""
                    INSERT INTO document_chunks(
                        content, embedding, document_version_id, chunk_no, page_no,
                        owner_user_id, source_type, source_index, source_label)
                    VALUES (?, CAST(? AS vector), ?, ?, NULL, ?, 'TEXT_CHUNK', ?, ?)
                    RETURNING id
                    """, Long.class,
                    keyed.content(),
                    vector(embedding),
                    versionId,
                    sourceIndex,
                    ownerId,
                    sourceIndex,
                    "텍스트 구간 " + sourceIndex);
            chunks.computeIfAbsent(keyed.key(), ignored -> new LinkedHashSet<>()).add(chunkId);
        }
    }

    private PdfSeedStats insertPdfChunks(
            Long ownerId,
            Long versionId,
            JsonNode dataset,
            Map<String, Set<Long>> chunks) throws Exception {
        Path pdf = Path.of(dataset.path("pdf").path("path").asText());
        List<PageText> pages = documentTextExtractor.extract(
                DocumentFileType.PDF,
                Files.readAllBytes(pdf));
        Map<String, PhaseALiteralQueryExpression> targets = new LinkedHashMap<>();
        for (JsonNode target : dataset.path("targets")) {
            targets.put(
                    target.path("key").asText(),
                    PhaseALiteralQueryExpression.from(target.path("expression").asText())
                            .orElseThrow());
        }

        List<PdfContent> contents = new ArrayList<>();
        int nextChunkNo = 1;
        for (PageText page : pages) {
            for (TextChunk chunk : textChunker.split(page.text())) {
                contents.add(new PdfContent(nextChunkNo++, page.pageNumber(), chunk.content()));
            }
        }
        List<float[]> embeddings = embeddingModel.embed(
                contents.stream().map(PdfContent::content).toList());
        assertThat(embeddings).hasSameSizeAs(contents);
        for (int index = 0; index < contents.size(); index++) {
            PdfContent content = contents.get(index);
            float[] embedding = embeddings.get(index);
            embeddingValidator.validate(embedding);
            Long chunkId = jdbc.queryForObject("""
                    INSERT INTO document_chunks(
                        content, embedding, document_version_id, chunk_no, page_no,
                        owner_user_id, source_type, source_index, source_label)
                    VALUES (?, CAST(? AS vector), ?, ?, ?, ?, 'PAGE', ?, ?)
                    RETURNING id
                    """, Long.class,
                    content.content(),
                    vector(embedding),
                    versionId,
                    content.chunkNo(),
                    content.pageNumber(),
                    ownerId,
                    content.pageNumber(),
                    content.pageNumber() + "페이지");
            targets.forEach((key, expression) -> {
                if (expression.matches(content.content())) {
                    chunks.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(chunkId);
                }
            });
        }
        targets.keySet().forEach(key -> assertThat(chunks.getOrDefault(key, Set.of()))
                .as("seeded target chunks: %s", key)
                .isNotEmpty());
        return new PdfSeedStats(
                pages.size(),
                pages.stream().mapToInt(page -> page.text().length()).sum(),
                contents.size());
    }

    private void activate(Long documentId, Long versionId) {
        jdbc.update("UPDATE documents SET active_version_id = ? WHERE id = ?", versionId, documentId);
    }

    private boolean candidatesBelongToOwner(Long ownerId, Set<Long> candidateIds) {
        for (Long candidateId : candidateIds) {
            Long actual = jdbc.queryForObject(
                    "SELECT owner_user_id FROM document_chunks WHERE id = ?",
                    Long.class,
                    candidateId);
            if (!Objects.equals(ownerId, actual)) {
                return false;
            }
        }
        return true;
    }

    private boolean candidatesAreActive(Set<Long> candidateIds) {
        for (Long candidateId : candidateIds) {
            Integer active = jdbc.queryForObject("""
                    SELECT count(*)
                    FROM document_chunks chunk
                    JOIN document_versions version
                      ON version.id = chunk.document_version_id
                     AND version.owner_user_id = chunk.owner_user_id
                    JOIN documents document
                      ON document.id = version.document_id
                     AND document.active_version_id = version.id
                     AND document.owner_user_id = version.owner_user_id
                    WHERE chunk.id = ? AND version.status = 'ACTIVE'
                    """, Integer.class, candidateId);
            if (active == null || active != 1) {
                return false;
            }
        }
        return true;
    }

    private Long fullDenseRank(Long ownerId, float[] embedding, Long expectedChunkId) {
        return jdbc.queryForObject("""
                SELECT ranked.dense_rank
                FROM (
                    SELECT chunk.id,
                           row_number() OVER (
                               ORDER BY chunk.embedding <=> CAST(? AS vector), chunk.id
                           ) AS dense_rank
                    FROM document_chunks chunk
                    JOIN document_versions version
                      ON version.id = chunk.document_version_id
                     AND version.owner_user_id = chunk.owner_user_id
                    JOIN documents document
                      ON document.id = version.document_id
                     AND document.active_version_id = version.id
                     AND document.owner_user_id = version.owner_user_id
                    WHERE document.owner_user_id = ?
                      AND version.owner_user_id = ?
                      AND chunk.owner_user_id = ?
                      AND version.status = 'ACTIVE'
                ) ranked
                WHERE ranked.id = ?
                """, Long.class, vector(embedding), ownerId, ownerId, ownerId, expectedChunkId);
    }

    private static Set<Long> ids(List<VectorSearchResult> candidates) {
        return candidates.stream()
                .map(VectorSearchResult::chunkId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static boolean intersects(Set<Long> candidates, Set<Long> expected) {
        return expected.stream().anyMatch(candidates::contains);
    }

    private static List<Long> orderedIds(List<VectorSearchResult> candidates) {
        return candidates.stream().map(VectorSearchResult::chunkId).toList();
    }

    private static String vector(float[] embedding) {
        StringBuilder result = new StringBuilder("[");
        for (int index = 0; index < embedding.length; index++) {
            if (index > 0) {
                result.append(',');
            }
            result.append(embedding[index]);
        }
        return result.append(']').toString();
    }

    private static String sha256(Path path) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path));
        return java.util.HexFormat.of().formatHex(digest);
    }

    private record KeyedContent(String key, String content) {
    }

    private record PdfContent(int chunkNo, int pageNumber, String content) {
    }

    private record PdfSeedStats(int pageCount, int extractedCharacterCount, int chunkCount) {
    }


    private record SeededCorpus(
            Map<String, Long> ownerByKey,
            Map<String, Set<Long>> chunkByKey,
            int primaryActiveChunkCount,
            int pdfPageCount,
            int pdfExtractedCharacterCount,
            int pdfChunkCount) {
    }
}
