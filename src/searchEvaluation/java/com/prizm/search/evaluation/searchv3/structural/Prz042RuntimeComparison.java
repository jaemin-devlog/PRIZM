package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.document.entity.DocumentType;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.ingestion.repository.DocumentChunkRepository;
import com.prizm.ingestion.service.IndexedChunk;
import com.prizm.ingestion.service.TextChunk;
import com.prizm.ingestion.service.TextChunker;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.dto.response.CareerEvidenceSearchV2Response;
import com.prizm.search.evaluation.trace.ProductionSearchDecisionTracer;
import com.prizm.search.evaluation.trace.SearchDecisionTrace;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.repository.VectorSearchResult;
import com.prizm.search.service.SearchService;
import com.prizm.search.v3.indexing.model.SearchV3EmbeddingModelContract;
import com.prizm.search.v3.indexing.service.SearchV3EmbeddingModelContractProvider;
import com.prizm.search.v3.indexing.service.SearchV3IndexingCoordinator;
import com.prizm.search.v3.indexing.service.SearchV3JobDispatchService;
import com.prizm.search.v3.query.model.SearchV3EvidenceChildCandidate;
import com.prizm.search.v3.query.model.SearchV3EvidenceResult;
import com.prizm.search.v3.query.model.SearchV3PassageCandidate;
import com.prizm.search.v3.query.model.SearchV3QueryResult;
import com.prizm.search.v3.query.repository.SearchV3ShadowQueryRepository;
import com.prizm.search.v3.query.service.SearchV3ShadowQueryService;
import com.prizm.search.v3.query.typed.DeterministicTypedQueryParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Executes the PRZ-042 comparison through the actual PostgreSQL V2 and shadow V3 runtime.
 *
 * <p>This adapter receives an already verified, Gold-free runtime projection. It never reads the
 * benchmark directory and does not accept answerability, relation, category, or expected evidence.
 * Fixture identities are kept in an evaluation-only map; production database IDs never become
 * benchmark Gold IDs.</p>
 */
final class Prz042RuntimeComparison {

    private static final int EMBEDDING_BYTES_PER_FLOAT = Float.BYTES;
    private static final int FIXED_MAX_LENGTH = 800;
    private static final int FIXED_OVERLAP = 120;

    private final JdbcTemplate jdbc;
    private final FileStorage fileStorage;
    private final TextChunker textChunker;
    private final EmbeddingService embeddingService;
    private final EmbeddingValidator embeddingValidator;
    private final DocumentChunkRepository chunkRepository;
    private final VectorSearchRepository vectorRepository;
    private final ProductionSearchDecisionTracer productionTracer;
    private final SearchService searchService;
    private final SearchV3JobDispatchService v3Dispatch;
    private final SearchV3IndexingCoordinator v3Coordinator;
    private final SearchV3ShadowQueryRepository v3Repository;
    private final SearchV3ShadowQueryService v3QueryService;
    private final SearchV3EmbeddingModelContractProvider v3ModelProvider;
    private final DeterministicTypedQueryParser typedParser = new DeterministicTypedQueryParser();

    Prz042RuntimeComparison(
            JdbcTemplate jdbc,
            FileStorage fileStorage,
            TextChunker textChunker,
            EmbeddingService embeddingService,
            EmbeddingValidator embeddingValidator,
            DocumentChunkRepository chunkRepository,
            VectorSearchRepository vectorRepository,
            ProductionSearchDecisionTracer productionTracer,
            SearchService searchService,
            SearchV3JobDispatchService v3Dispatch,
            SearchV3IndexingCoordinator v3Coordinator,
            SearchV3ShadowQueryRepository v3Repository,
            SearchV3ShadowQueryService v3QueryService,
            SearchV3EmbeddingModelContractProvider v3ModelProvider) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.fileStorage = Objects.requireNonNull(fileStorage, "fileStorage");
        this.textChunker = Objects.requireNonNull(textChunker, "textChunker");
        this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService");
        this.embeddingValidator = Objects.requireNonNull(embeddingValidator, "embeddingValidator");
        this.chunkRepository = Objects.requireNonNull(chunkRepository, "chunkRepository");
        this.vectorRepository = Objects.requireNonNull(vectorRepository, "vectorRepository");
        this.productionTracer = Objects.requireNonNull(productionTracer, "productionTracer");
        this.searchService = Objects.requireNonNull(searchService, "searchService");
        this.v3Dispatch = Objects.requireNonNull(v3Dispatch, "v3Dispatch");
        this.v3Coordinator = Objects.requireNonNull(v3Coordinator, "v3Coordinator");
        this.v3Repository = Objects.requireNonNull(v3Repository, "v3Repository");
        this.v3QueryService = Objects.requireNonNull(v3QueryService, "v3QueryService");
        this.v3ModelProvider = Objects.requireNonNull(v3ModelProvider, "v3ModelProvider");
    }

    Prz042FinalFreeze.PredictionBundle compare(
            Prz042FinalDataset.RuntimeInput input,
            RunMetadata metadata) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(metadata, "metadata");
        requireEmptyEvaluationDatabase();

        SearchV3EmbeddingModelContract model = v3ModelProvider.resolve();
        metadata.requireModel(model);
        warmUpEmbedding(model);
        FixtureDatabase fixtures = seedFixtures(input);
        V2IndexResult v2 = indexV2(fixtures);
        V3IndexResult v3 = indexV3(fixtures, model);

        List<SearchV3MinimalShadowFreeze.QueryOutput> queries = new ArrayList<>();
        long ownerLeakageCount = 0L;
        long inactiveLeakageCount = 0L;
        for (Prz042FinalDataset.RuntimeQuery query : input.queries()) {
            FixtureOwner owner = fixtures.owners().get(query.userBundleId());
            if (owner == null) {
                throw new IllegalStateException("PRZ-042 query owner was not seeded: " + query.userBundleId());
            }
            boolean typed = !typedParser.parse(query.text()).isEmpty();
            ProductionV2ShadowAdapter.QueryRun v2Run = runV2(query, owner.ownerId(), fixtures);
            MinimalV3ShadowAdapter.QueryRun v3Run = runV3(query, owner.ownerId(), fixtures, model, typed);
            ownerLeakageCount += (v2Run.ownerLeakage() ? 1 : 0) + (v3Run.ownerLeakage() ? 1 : 0);
            inactiveLeakageCount += v2Run.inactiveVersionLeakage() ? 1 : 0;
            queries.add(new SearchV3MinimalShadowFreeze.QueryOutput(
                    "FRESH_FINAL",
                    input.datasetVersion(),
                    input.split(),
                    query.queryId(),
                    query.userBundleId(),
                    query.professionGroup(),
                    query.language(),
                    Prz042FinalDataset.sha256(query.text()),
                    typed,
                    v2Run,
                    v3Run));
        }

        SearchV3MinimalShadowFreeze.OutputArtifact comparison = new SearchV3MinimalShadowFreeze.OutputArtifact(
                1,
                Prz042FinalFreeze.OUTPUT_TYPE,
                metadata.codeFreezeCommit(),
                metadata.sourceFreeze(),
                new SearchV3MinimalShadowFreeze.ModelIdentity(
                        model.modelId(), model.resolvedModelDigest(), model.dimension(), "COSINE"),
                "PRODUCTION_SEARCH_V2_SOURCE_DEDUP_EVIDENCE_SIGNALS_V1",
                "MINIMAL_V3_B3_TYPED_CHILD_DENSE_V1",
                "ACTUAL_POSTGRESQL_PGVECTOR_AND_SPRING_RUNTIME",
                queries.size(),
                fixtures.owners().size(),
                fixtures.versions().size(),
                v2.stats(),
                v3.stats(),
                v2.units(),
                v3.units(),
                List.copyOf(queries),
                metadata.sealedState());
        Prz042FinalFreeze.RuntimeAudit audit = new Prz042FinalFreeze.RuntimeAudit(
                fixtures.owners().size(),
                fixtures.documents().size(),
                Math.toIntExact(fixtures.versions().values().stream()
                        .filter(value -> value.source().active()).count()),
                v2.activeChunkCount(),
                v2.inactiveDecoyChunkCount(),
                v3.stats().unitCount(),
                v3.childCount(),
                v3.passageVectorCount(),
                v3.childVectorCount(),
                ownerLeakageCount,
                inactiveLeakageCount,
                lifecycleViolationCount(),
                duplicateArtifactCount(),
                mixedArtifactCount(),
                metadata.realBgeM3(),
                model.modelId(),
                model.resolvedModelDigest(),
                model.dimension(),
                queries.size(),
                queries.size(),
                0,
                0,
                false);
        return new Prz042FinalFreeze.PredictionBundle(comparison, audit);
    }

    private void warmUpEmbedding(SearchV3EmbeddingModelContract model) {
        float[] vector = embeddingService.embed("PRZ-042 runtime warm-up");
        embeddingValidator.validate(vector);
        if (vector.length != model.dimension() || !model.equals(v3ModelProvider.resolve())) {
            throw new IllegalStateException("BGE-M3 model contract changed during neutral warm-up");
        }
    }

    private FixtureDatabase seedFixtures(Prz042FinalDataset.RuntimeInput input) {
        Map<String, FixtureOwner> owners = new LinkedHashMap<>();
        java.util.stream.Stream.concat(
                        input.documents().stream().map(Prz042FinalDataset.RuntimeDocument::userBundleId),
                        input.queries().stream().map(Prz042FinalDataset.RuntimeQuery::userBundleId))
                .distinct()
                .sorted()
                .forEach(ownerKey -> {
                    long id = jdbc.queryForObject(
                            """
                            INSERT INTO users(email, password_hash, role, enabled)
                            VALUES (?, 'prz042-not-used', 'USER', TRUE)
                            RETURNING id
                            """,
                            Long.class,
                            "prz042-" + ownerKey.toLowerCase(java.util.Locale.ROOT) + "@example.invalid");
                    owners.put(ownerKey, new FixtureOwner(ownerKey, id));
                });

        Map<String, List<Prz042FinalDataset.RuntimeDocument>> logicalDocuments = input.documents().stream()
                .collect(Collectors.groupingBy(
                        document -> document.userBundleId() + "|" + document.logicalDocumentId(),
                        LinkedHashMap::new,
                        Collectors.toList()));
        Map<String, FixtureDocument> documents = new LinkedHashMap<>();
        Map<String, FixtureVersion> versions = new LinkedHashMap<>();
        for (var logical : logicalDocuments.entrySet()) {
            List<Prz042FinalDataset.RuntimeDocument> sourceVersions = logical.getValue().stream()
                    .sorted(Comparator.comparingInt(Prz042FinalDataset.RuntimeDocument::versionNumber))
                    .toList();
            Prz042FinalDataset.RuntimeDocument first = sourceVersions.get(0);
            FixtureOwner owner = requiredOwner(owners, first.userBundleId());
            DocumentType type = DocumentType.valueOf(first.documentType());
            long documentId = jdbc.queryForObject(
                    """
                    INSERT INTO documents(owner_user_id, title, document_type)
                    VALUES (?, ?, ?) RETURNING id
                    """,
                    Long.class,
                    owner.ownerId(),
                    first.title(),
                    type.name());
            FixtureDocument fixtureDocument = new FixtureDocument(
                    first.userBundleId(), first.logicalDocumentId(), documentId);
            documents.put(logical.getKey(), fixtureDocument);

            Long activeVersionId = null;
            for (Prz042FinalDataset.RuntimeDocument source : sourceVersions) {
                requireSameDocumentContract(first, source);
                String fileName = "prz042-" + safeFileComponent(source.versionId()) + ".txt";
                long versionId = jdbc.queryForObject(
                        """
                        INSERT INTO document_versions(
                            owner_user_id, document_id, version_no, original_file_name,
                            stored_file_path, file_type, content_hash, status
                        ) VALUES (?, ?, ?, ?, 'pending', 'TXT', ?, 'ACTIVE') RETURNING id
                        """,
                        Long.class,
                        owner.ownerId(),
                        documentId,
                        source.versionNumber(),
                        fileName,
                        source.contentSha256());
                String stored = fileStorage.store(
                        documentId,
                        versionId,
                        fileName,
                        source.sourceText().getBytes(StandardCharsets.UTF_8));
                jdbc.update(
                        "UPDATE document_versions SET stored_file_path = ? WHERE id = ?",
                        stored,
                        versionId);
                FixtureVersion fixtureVersion = new FixtureVersion(
                        source, owner.ownerId(), documentId, versionId, stored);
                versions.put(source.versionId(), fixtureVersion);
                if (source.active()) {
                    if (activeVersionId != null) {
                        throw new IllegalStateException("multiple ACTIVE versions in logical document: " + logical.getKey());
                    }
                    activeVersionId = versionId;
                }
            }
            if (activeVersionId == null) {
                throw new IllegalStateException("logical document has no ACTIVE version: " + logical.getKey());
            }
            jdbc.update("UPDATE documents SET active_version_id = ? WHERE id = ?", activeVersionId, documentId);
        }
        FixtureDatabase result = new FixtureDatabase(Map.copyOf(owners), Map.copyOf(documents), Map.copyOf(versions));
        result.inactiveDecoyVersion = createInactiveV2Decoy(result);
        return result;
    }

    private V2IndexResult indexV2(FixtureDatabase fixtures) {
        long constructionStarted = System.nanoTime();
        Map<Long, ProductionV2ShadowAdapter.SourceSpan> spans = new LinkedHashMap<>();
        List<SearchV3MinimalShadowFreeze.IndexUnit> units = new ArrayList<>();
        long embeddingNanos = 0L;
        long chunkCount = 0L;
        long indexingStarted = System.nanoTime();
        for (FixtureVersion fixture : fixtures.versions().values().stream()
                .sorted(Comparator.comparingLong(FixtureVersion::versionId))
                .toList()) {
            List<TextChunk> chunks = textChunker.split(fixture.source().sourceText());
            List<ProjectedChunk> projected = projectFixedChunks(fixture.source().sourceText(), chunks);
            List<IndexedChunk> indexed = new ArrayList<>();
            for (ProjectedChunk chunk : projected) {
                long embeddingStarted = System.nanoTime();
                float[] vector = embeddingService.embed(chunk.content());
                embeddingNanos += System.nanoTime() - embeddingStarted;
                embeddingValidator.validate(vector);
                indexed.add(new IndexedChunk(
                        chunk.chunkNo(),
                        ChunkSourceType.TEXT_CHUNK,
                        chunk.chunkNo(),
                        "텍스트 구간 " + chunk.chunkNo(),
                        chunk.content(),
                        vector));
            }
            chunkRepository.replaceAll(fixture.ownerId(), fixture.versionId(), indexed);
            List<Map<String, Object>> stored = jdbc.queryForList(
                    """
                    SELECT id, chunk_no, content
                    FROM document_chunks
                    WHERE owner_user_id = ? AND document_version_id = ?
                    ORDER BY chunk_no
                    """,
                    fixture.ownerId(),
                    fixture.versionId());
            if (stored.size() != projected.size()) {
                throw new IllegalStateException("actual V2 stored chunk count differs from TextChunker output");
            }
            for (int index = 0; index < stored.size(); index++) {
                long chunkId = ((Number) stored.get(index).get("id")).longValue();
                int chunkNo = ((Number) stored.get(index).get("chunk_no")).intValue();
                String content = (String) stored.get(index).get("content");
                ProjectedChunk projection = projected.get(index);
                if (chunkNo != projection.chunkNo() || !content.equals(projection.content())) {
                    throw new IllegalStateException("actual V2 persisted content differs from TextChunker projection");
                }
                ProductionV2ShadowAdapter.SourceSpan span = span(fixture, projection);
                spans.put(chunkId, span);
                fixtures.chunkSpans().put(chunkId, span);
                units.add(new SearchV3MinimalShadowFreeze.IndexUnit(
                        stableChunkId(fixture, chunkNo),
                        "FIXED|" + fixture.source().versionId(),
                        List.of(span)));
            }
            chunkCount += stored.size();
        }
        double constructionMs = millis(indexingStarted - constructionStarted);
        double indexingMs = millis(System.nanoTime() - indexingStarted);
        long inactiveDecoyChunkCount = indexInactiveV2Decoy(fixtures);
        long activeCount = jdbc.queryForObject(
                """
                SELECT count(*)
                FROM document_chunks chunk
                JOIN document_versions version ON version.id = chunk.document_version_id
                JOIN documents document ON document.id = version.document_id
                WHERE document.active_version_id = version.id AND version.status = 'ACTIVE'
                """,
                Long.class);
        if (activeCount != chunkCount || chunkCount != spans.size()) {
            throw new IllegalStateException("actual V2 index inventory is invalid");
        }
        return new V2IndexResult(
                new SearchV3MinimalShadowFreeze.IndexingStats(
                        chunkCount,
                        chunkCount,
                        constructionMs,
                        indexingMs,
                        millis(embeddingNanos),
                        chunkCount * v3ModelProvider.resolve().dimension() * EMBEDDING_BYTES_PER_FLOAT),
                Map.copyOf(spans),
                List.copyOf(units),
                inactiveDecoyChunkCount,
                activeCount);
    }

    private V3IndexResult indexV3(
            FixtureDatabase fixtures,
            SearchV3EmbeddingModelContract frozenModel) {
        long started = System.nanoTime();
        int dispatched = 0;
        while (v3Dispatch.dispatchNext().isPresent()) {
            if (!v3Coordinator.processNext()) {
                throw new IllegalStateException("Search V3 dispatched job could not be claimed");
            }
            dispatched++;
            if (dispatched > fixtures.documents().size()) {
                throw new IllegalStateException("Search V3 dispatch exceeded active document inventory");
            }
        }
        double indexingMs = millis(System.nanoTime() - started);
        long expectedActiveDocuments = fixtures.versions().values().stream()
                .filter(value -> value.source().active())
                .count();
        if (dispatched != expectedActiveDocuments) {
            throw new IllegalStateException("Search V3 did not index every ACTIVE fixture version");
        }
        SearchV3EmbeddingModelContract after = v3ModelProvider.resolve();
        if (!frozenModel.equals(after)) {
            throw new IllegalStateException("BGE-M3 model contract changed while indexing PRZ-042");
        }

        List<V3UnitRow> rows = jdbc.query(
                """
                SELECT passage.id AS passage_id, passage.passage_key,
                       passage.parent_annotation_candidate_id,
                       child.id AS child_id, child.child_key, child.owner_user_id,
                       child.document_id, child.document_version_id, child.source_text,
                       child.source_path, child.page_no, child.code_point_start, child.code_point_end,
                       child.source_text_sha256, child.passage_child_order
                FROM search_v3_retrieval_passages passage
                JOIN search_v3_evidence_children child ON child.passage_id = passage.id
                JOIN search_v3_index_generations generation ON generation.id = passage.generation_id
                JOIN search_v3_indexing_jobs job ON job.generation_id = generation.id
                WHERE generation.status = 'ACTIVE' AND job.status = 'COMPLETED'
                ORDER BY passage.id, child.passage_child_order, child.id
                """,
                (resultSet, row) -> new V3UnitRow(
                        resultSet.getLong("passage_id"),
                        resultSet.getString("passage_key"),
                        resultSet.getString("parent_annotation_candidate_id"),
                        resultSet.getLong("child_id"),
                        resultSet.getString("child_key"),
                        resultSet.getLong("owner_user_id"),
                        resultSet.getLong("document_id"),
                        resultSet.getLong("document_version_id"),
                        resultSet.getString("source_text"),
                        resultSet.getString("source_path"),
                        resultSet.getObject("page_no", Integer.class),
                        resultSet.getInt("code_point_start"),
                        resultSet.getInt("code_point_end"),
                        resultSet.getString("source_text_sha256")));
        Map<Long, List<V3UnitRow>> byPassage = rows.stream().collect(Collectors.groupingBy(
                V3UnitRow::passageId, LinkedHashMap::new, Collectors.toList()));
        List<SearchV3MinimalShadowFreeze.IndexUnit> units = byPassage.values().stream()
                .map(group -> new SearchV3MinimalShadowFreeze.IndexUnit(
                        group.get(0).passageKey(),
                        group.get(0).parentId(),
                        group.stream().map(value -> span(fixtures, value)).toList()))
                .toList();
        long passages = byPassage.size();
        long children = rows.size();
        long passageVectors = count("search_v3_passage_embeddings");
        long childVectors = count("search_v3_child_embeddings");
        if (passages == 0 || children == 0 || passages != passageVectors || children != childVectors) {
            throw new IllegalStateException("actual V3 active Passage/Child vector inventory is incomplete");
        }
        return new V3IndexResult(
                new SearchV3MinimalShadowFreeze.IndexingStats(
                        passages,
                        passageVectors + childVectors,
                        0.0d,
                        indexingMs,
                        indexingMs,
                        (passageVectors + childVectors) * frozenModel.dimension() * EMBEDDING_BYTES_PER_FLOAT),
                List.copyOf(units),
                children,
                passageVectors,
                childVectors);
    }

    private ProductionV2ShadowAdapter.QueryRun runV2(
            Prz042FinalDataset.RuntimeQuery query,
            long ownerId,
            FixtureDatabase fixtures) {
        long embeddingStarted = System.nanoTime();
        float[] queryVector = embeddingService.embed(query.text());
        double embeddingMs = millis(System.nanoTime() - embeddingStarted);
        embeddingValidator.validate(queryVector);

        long candidateStarted = System.nanoTime();
        List<VectorSearchResult> candidates = vectorRepository.findCareerEvidenceCandidates(ownerId, queryVector);
        double candidateMs = millis(System.nanoTime() - candidateStarted);
        long finalStarted = System.nanoTime();
        CareerEvidenceSearchV2Response response = searchService.searchCareerEvidenceV2(ownerId, query.text());
        double finalMs = millis(System.nanoTime() - finalStarted);
        SearchDecisionTrace trace = productionTracer.trace(ownerId, query.text());
        if (!trace.productionResponseMatch() || !trace.parityErrors().isEmpty()
                || !trace.responseState().equals(response.state().name())) {
            throw new IllegalStateException("actual Production V2 trace/service parity failed");
        }
        List<Long> tracedOriginal = trace.queryVariants().stream()
                .filter(value -> value.type() == SearchDecisionTrace.QueryVariantType.ORIGINAL)
                .findFirst()
                .map(SearchDecisionTrace.QueryVariantTrace::retrievedChunkIds)
                .orElseThrow(() -> new IllegalStateException("Production V2 trace omitted ORIGINAL retrieval"));
        if (!tracedOriginal.equals(candidates.stream().map(VectorSearchResult::chunkId).toList())) {
            throw new IllegalStateException("actual Production V2 trace candidate order changed");
        }

        List<ProductionV2ShadowAdapter.CandidateResult> candidateOutputs = new ArrayList<>();
        for (int rank = 0; rank < candidates.size(); rank++) {
            VectorSearchResult candidate = candidates.get(rank);
            candidateOutputs.add(new ProductionV2ShadowAdapter.CandidateResult(
                    rank + 1,
                    stableChunkId(fixtures, candidate.chunkId()),
                    candidate.score(),
                    requiredChunkSpan(fixtures, candidate.chunkId())));
        }
        List<ProductionV2ShadowAdapter.FinalResult> finalOutputs = new ArrayList<>();
        for (int rank = 0; rank < response.results().size(); rank++) {
            CareerEvidenceSearchResponse result = response.results().get(rank);
            ProductionV2ShadowAdapter.SourceSpan selected = requiredChunkSpan(fixtures, result.chunkId());
            ProductionV2ShadowAdapter.SourceSpan evidence = requiredChunkSpan(fixtures, result.evidenceChunkId());
            ProductionV2ShadowAdapter.SourceSpan display = snippetSpan(evidence, result.snippet());
            finalOutputs.add(new ProductionV2ShadowAdapter.FinalResult(
                    rank + 1,
                    stableChunkId(fixtures, result.chunkId()),
                    result.score(),
                    selected,
                    stableChunkId(fixtures, result.evidenceChunkId()),
                    evidence,
                    result.snippet(),
                    display));
        }
        boolean ownerLeakage = java.util.stream.Stream.concat(
                        candidateOutputs.stream().map(ProductionV2ShadowAdapter.CandidateResult::span),
                        finalOutputs.stream().map(ProductionV2ShadowAdapter.FinalResult::displaySpan))
                .anyMatch(span -> !query.userBundleId().equals(span.userBundleId()));
        boolean inactiveLeakage = java.util.stream.Stream.concat(
                        candidates.stream().map(VectorSearchResult::documentVersionId),
                        response.results().stream().flatMap(result -> java.util.stream.Stream.of(
                                result.documentVersionId(),
                                databaseVersionIdForChunk(result.evidenceChunkId()))))
                .anyMatch(versionId -> !isDatabaseVersionActive(versionId));
        return new ProductionV2ShadowAdapter.QueryRun(
                response.state().name(),
                embeddingMs,
                candidateMs,
                finalMs,
                embeddingMs + candidateMs,
                finalMs,
                List.copyOf(candidateOutputs),
                List.copyOf(finalOutputs),
                ownerLeakage,
                inactiveLeakage);
    }

    private MinimalV3ShadowAdapter.QueryRun runV3(
            Prz042FinalDataset.RuntimeQuery query,
            long ownerId,
            FixtureDatabase fixtures,
            SearchV3EmbeddingModelContract model,
            boolean typed) {
        long embeddingStarted = System.nanoTime();
        float[] queryVector = embeddingService.embed(query.text());
        double embeddingMs = millis(System.nanoTime() - embeddingStarted);
        embeddingValidator.validate(queryVector);
        if (!model.equals(v3ModelProvider.resolve())) {
            throw new IllegalStateException("BGE-M3 model contract changed before PRZ-042 V3 query");
        }

        long candidateStarted = System.nanoTime();
        List<SearchV3PassageCandidate> passages = v3Repository.findPassages(ownerId, queryVector, model);
        List<SearchV3EvidenceChildCandidate> children = v3Repository.findChildren(ownerId, passages);
        double candidateMs = millis(System.nanoTime() - candidateStarted);
        Map<Long, List<SearchV3EvidenceChildCandidate>> byPassage = children.stream()
                .collect(Collectors.groupingBy(
                        SearchV3EvidenceChildCandidate::passageId,
                        LinkedHashMap::new,
                        Collectors.toList()));
        List<MinimalV3ShadowAdapter.CandidateResult> candidateOutputs = passages.stream()
                .map(passage -> new MinimalV3ShadowAdapter.CandidateResult(
                        passage.rank(),
                        passage.passageKey(),
                        passage.cosineScore(),
                        passage.parentAnnotationCandidateId(),
                        byPassage.getOrDefault(passage.passageId(), List.of()).stream()
                                .map(child -> span(fixtures, child)).toList()))
                .toList();

        long selectionStarted = System.nanoTime();
        SearchV3QueryResult result = v3QueryService.search(ownerId, query.text());
        double selectionMs = millis(System.nanoTime() - selectionStarted);
        if (result.passageCandidateCount() != passages.size()) {
            throw new IllegalStateException("actual Search V3 service/repository candidate parity failed");
        }
        List<MinimalV3ShadowAdapter.FinalResult> finalOutputs = result.evidence().stream()
                .map(value -> new MinimalV3ShadowAdapter.FinalResult(
                        value.rank(),
                        value.passageKey(),
                        value.passageRank(),
                        value.childCosineScore() == null ? value.passageCosineScore() : value.childCosineScore(),
                        value.childKey(),
                        span(fixtures, value),
                        value.typedMatchState() == null ? null : value.typedMatchState().name()))
                .toList();
        boolean ownerLeakage = java.util.stream.Stream.concat(
                        candidateOutputs.stream().flatMap(value -> value.spans().stream()),
                        finalOutputs.stream().map(MinimalV3ShadowAdapter.FinalResult::span))
                .anyMatch(span -> !query.userBundleId().equals(span.userBundleId()));
        long crossParent = passages.stream().filter(passage -> byPassage
                        .getOrDefault(passage.passageId(), List.of()).stream()
                        .anyMatch(child -> child.generationId() != passage.generationId()
                                || child.ownerUserId() != passage.ownerUserId()
                                || child.documentId() != passage.documentId()
                                || child.documentVersionId() != passage.documentVersionId()
                                || !child.parentAnnotationCandidateId().equals(
                                        passage.parentAnnotationCandidateId())))
                .count();
        boolean inactive = java.util.stream.Stream.concat(
                        passages.stream().map(SearchV3PassageCandidate::documentVersionId),
                        result.evidence().stream().map(SearchV3EvidenceResult::documentVersionId))
                .anyMatch(versionId -> !isDatabaseVersionActive(versionId));
        if (inactive) {
            throw new IllegalStateException("Search V3 exposed an inactive fixture version");
        }
        return new MinimalV3ShadowAdapter.QueryRun(
                result.state().name(),
                typed,
                result.parsedConstraintCount(),
                embeddingMs,
                candidateMs,
                0.0d,
                selectionMs,
                selectionMs,
                List.copyOf(candidateOutputs),
                List.copyOf(finalOutputs),
                ownerLeakage,
                crossParent);
    }

    private List<ProjectedChunk> projectFixedChunks(String source, List<TextChunk> actual) {
        List<ProjectedChunk> result = new ArrayList<>();
        int start = 0;
        int outputIndex = 0;
        while (start < source.length()) {
            int end = Math.min(start + FIXED_MAX_LENGTH, source.length());
            String raw = source.substring(start, end);
            String content = raw.strip();
            if (!content.isBlank()) {
                if (outputIndex >= actual.size()) {
                    throw new IllegalStateException("Production TextChunker returned fewer chunks than projection");
                }
                TextChunk chunk = actual.get(outputIndex++);
                if (!content.equals(chunk.content())) {
                    throw new IllegalStateException("Production TextChunker 800/120 contract changed");
                }
                int localStart = raw.indexOf(content);
                int charStart = start + localStart;
                int charEnd = charStart + content.length();
                result.add(new ProjectedChunk(
                        chunk.chunkNo(),
                        content,
                        source.codePointCount(0, charStart),
                        source.codePointCount(0, charEnd)));
            }
            if (end == source.length()) break;
            start = end - FIXED_OVERLAP;
        }
        if (outputIndex != actual.size()) {
            throw new IllegalStateException("Production TextChunker returned more chunks than projection");
        }
        return List.copyOf(result);
    }

    private ProductionV2ShadowAdapter.SourceSpan span(
            FixtureVersion fixture,
            ProjectedChunk chunk) {
        return new ProductionV2ShadowAdapter.SourceSpan(
                fixture.source().userBundleId(),
                fixture.source().documentId(),
                fixture.source().versionId(),
                fixture.source().sourcePath(),
                null,
                chunk.codePointStart(),
                chunk.codePointEnd(),
                chunk.content(),
                Prz042FinalDataset.sha256(chunk.content()));
    }

    private ProductionV2ShadowAdapter.SourceSpan span(FixtureDatabase fixtures, V3UnitRow row) {
        FixtureVersion fixture = requiredVersion(fixtures, row.documentVersionId());
        if (!fixture.storedPath().equals(row.storedSourcePath())) {
            throw new IllegalStateException("actual V3 stored source path differs from the fixture version");
        }
        verifyV3Source(fixture, row.sourceText(), row.codePointStart(), row.codePointEnd(), row.sourceTextSha256());
        return sourceSpan(fixture, row.page(), row.codePointStart(), row.codePointEnd(), row.sourceText());
    }

    private ProductionV2ShadowAdapter.SourceSpan span(
            FixtureDatabase fixtures,
            SearchV3EvidenceChildCandidate child) {
        FixtureVersion fixture = requiredVersion(fixtures, child.documentVersionId());
        if (!fixture.storedPath().equals(child.sourcePath())) {
            throw new IllegalStateException("actual V3 Child source path differs from the fixture version");
        }
        verifyV3Source(
                fixture,
                child.sourceText(),
                child.codePointStart(),
                child.codePointEnd(),
                child.sourceTextSha256());
        return sourceSpan(
                fixture,
                child.pageNo(),
                child.codePointStart(),
                child.codePointEnd(),
                child.sourceText());
    }

    private ProductionV2ShadowAdapter.SourceSpan span(
            FixtureDatabase fixtures,
            SearchV3EvidenceResult child) {
        FixtureVersion fixture = requiredVersion(fixtures, child.documentVersionId());
        if (!fixture.storedPath().equals(child.sourcePath())) {
            throw new IllegalStateException("actual V3 result source path differs from the fixture version");
        }
        verifyV3Source(
                fixture,
                child.sourceText(),
                child.codePointStart(),
                child.codePointEnd(),
                Prz042FinalDataset.sha256(child.sourceText()));
        return sourceSpan(
                fixture,
                child.pageNo(),
                child.codePointStart(),
                child.codePointEnd(),
                child.sourceText());
    }

    private ProductionV2ShadowAdapter.SourceSpan sourceSpan(
            FixtureVersion fixture,
            Integer page,
            int start,
            int end,
            String text) {
        return new ProductionV2ShadowAdapter.SourceSpan(
                fixture.source().userBundleId(),
                fixture.source().documentId(),
                fixture.source().versionId(),
                fixture.source().sourcePath(),
                page,
                start,
                end,
                text,
                Prz042FinalDataset.sha256(text));
    }

    private void verifyV3Source(
            FixtureVersion fixture,
            String sourceText,
            int codePointStart,
            int codePointEnd,
            String sourceTextSha256) {
        String actual = codePointSubstring(fixture.source().sourceText(), codePointStart, codePointEnd);
        if (!actual.equals(sourceText)
                || !Prz042FinalDataset.sha256(sourceText).equals(sourceTextSha256)) {
            throw new IllegalStateException("actual V3 source provenance differs from the fixture source");
        }
    }

    private ProductionV2ShadowAdapter.SourceSpan snippetSpan(
            ProductionV2ShadowAdapter.SourceSpan evidence,
            String snippet) {
        if (snippet == null || snippet.isBlank()) {
            throw new IllegalStateException("Production V2 snippet is blank");
        }
        int localChar = evidence.sourceText().indexOf(snippet);
        if (localChar < 0) {
            throw new IllegalStateException("Production V2 snippet is not an exact source substring");
        }
        int localStart = evidence.sourceText().codePointCount(0, localChar);
        int localEnd = evidence.sourceText().codePointCount(0, localChar + snippet.length());
        return new ProductionV2ShadowAdapter.SourceSpan(
                evidence.userBundleId(),
                evidence.documentId(),
                evidence.versionId(),
                evidence.sourcePath(),
                evidence.page(),
                evidence.codePointStart() + localStart,
                evidence.codePointStart() + localEnd,
                snippet,
                Prz042FinalDataset.sha256(snippet));
    }

    private ProductionV2ShadowAdapter.SourceSpan requiredChunkSpan(
            FixtureDatabase fixtures,
            long chunkId) {
        ProductionV2ShadowAdapter.SourceSpan span = fixtures.chunkSpans().get(chunkId);
        if (span == null) throw new IllegalStateException("actual V2 returned an unknown chunk ID: " + chunkId);
        return span;
    }

    private String stableChunkId(FixtureDatabase fixtures, long chunkId) {
        ProductionV2ShadowAdapter.SourceSpan span = requiredChunkSpan(fixtures, chunkId);
        Integer no = jdbc.queryForObject("SELECT chunk_no FROM document_chunks WHERE id = ?", Integer.class, chunkId);
        return "FIXED|" + span.versionId() + "|" + no;
    }

    private static String stableChunkId(FixtureVersion fixture, int chunkNo) {
        return "FIXED|" + fixture.source().versionId() + "|" + chunkNo;
    }

    private FixtureVersion requiredVersion(FixtureDatabase fixtures, long databaseVersionId) {
        FixtureVersion value = fixtures.byDatabaseVersionId().get(databaseVersionId);
        if (value == null) {
            throw new IllegalStateException("runtime returned unknown document version ID: " + databaseVersionId);
        }
        return value;
    }

    private FixtureVersion createInactiveV2Decoy(FixtureDatabase fixtures) {
        FixtureVersion source = fixtures.versions().values().stream()
                .filter(value -> value.source().active())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("PRZ-042 needs one active source for inactive decoy"));
        Integer maxVersion = jdbc.queryForObject(
                "SELECT max(version_no) FROM document_versions WHERE document_id = ?",
                Integer.class,
                source.documentId());
        int versionNo = Math.addExact(10_000, Objects.requireNonNullElse(maxVersion, 0));
        String fileName = "prz042-inactive-decoy-" + source.versionId() + ".txt";
        long versionId = jdbc.queryForObject(
                """
                INSERT INTO document_versions(
                    owner_user_id, document_id, version_no, original_file_name,
                    stored_file_path, file_type, content_hash, status
                ) VALUES (?, ?, ?, ?, 'pending', 'TXT', ?, 'ACTIVE') RETURNING id
                """,
                Long.class,
                source.ownerId(),
                source.documentId(),
                versionNo,
                fileName,
                source.source().contentSha256());
        String stored = fileStorage.store(
                source.documentId(),
                versionId,
                fileName,
                source.source().sourceText().getBytes(StandardCharsets.UTF_8));
        jdbc.update("UPDATE document_versions SET stored_file_path = ? WHERE id = ?", stored, versionId);
        FixtureVersion decoy = new FixtureVersion(
                source.source(), source.ownerId(), source.documentId(), versionId, stored);
        fixtures.byDatabaseVersionId().put(versionId, decoy);
        return decoy;
    }

    private long indexInactiveV2Decoy(FixtureDatabase fixtures) {
        FixtureVersion decoy = Objects.requireNonNull(fixtures.inactiveDecoyVersion, "inactive decoy");
        List<TextChunk> chunks = textChunker.split(decoy.source().sourceText());
        List<ProjectedChunk> projected = projectFixedChunks(decoy.source().sourceText(), chunks);
        List<IndexedChunk> indexed = new ArrayList<>();
        for (ProjectedChunk chunk : projected) {
            float[] vector = embeddingService.embed(chunk.content());
            embeddingValidator.validate(vector);
            indexed.add(new IndexedChunk(
                    chunk.chunkNo(), ChunkSourceType.TEXT_CHUNK, chunk.chunkNo(),
                    "텍스트 구간 " + chunk.chunkNo(), chunk.content(), vector));
        }
        chunkRepository.replaceAll(decoy.ownerId(), decoy.versionId(), indexed);
        List<Long> ids = jdbc.queryForList(
                "SELECT id FROM document_chunks WHERE document_version_id = ? ORDER BY chunk_no",
                Long.class,
                decoy.versionId());
        if (ids.size() != projected.size() || isDatabaseVersionActive(decoy.versionId())) {
            throw new IllegalStateException("inactive V2 safety decoy was not isolated from active pointer");
        }
        for (int index = 0; index < ids.size(); index++) {
            fixtures.chunkSpans().put(ids.get(index), span(decoy, projected.get(index)));
        }
        return ids.size();
    }

    private boolean isDatabaseVersionActive(long versionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1 FROM document_versions version
                    JOIN documents document ON document.id = version.document_id
                    WHERE version.id = ? AND version.status = 'ACTIVE'
                      AND document.active_version_id = version.id
                )
                """,
                Boolean.class,
                versionId));
    }

    private long databaseVersionIdForChunk(long chunkId) {
        Long value = jdbc.queryForObject(
                "SELECT document_version_id FROM document_chunks WHERE id = ?",
                Long.class,
                chunkId);
        if (value == null) throw new IllegalStateException("V2 evidence chunk disappeared: " + chunkId);
        return value;
    }

    private long lifecycleViolationCount() {
        return jdbc.queryForObject(
                """
                SELECT count(*)
                FROM documents document
                LEFT JOIN search_v3_index_generations generation
                  ON generation.id = document.active_search_v3_generation_id
                LEFT JOIN search_v3_indexing_jobs job ON job.generation_id = generation.id
                WHERE document.active_version_id IS NOT NULL
                  AND (generation.id IS NULL OR generation.status <> 'ACTIVE'
                    OR generation.owner_user_id <> document.owner_user_id
                    OR generation.document_id <> document.id
                    OR generation.document_version_id <> document.active_version_id
                    OR job.status <> 'COMPLETED')
                """,
                Long.class);
    }

    private long duplicateArtifactCount() {
        return jdbc.queryForObject(
                """
                SELECT
                  (SELECT count(*) FROM (
                    SELECT generation_id, passage_key FROM search_v3_retrieval_passages
                    GROUP BY generation_id, passage_key HAVING count(*) > 1
                  ) duplicate_passages)
                + (SELECT count(*) FROM (
                    SELECT generation_id, child_key FROM search_v3_evidence_children
                    GROUP BY generation_id, child_key HAVING count(*) > 1
                  ) duplicate_children)
                + (SELECT count(*) FROM (
                    SELECT passage_id FROM search_v3_passage_embeddings
                    GROUP BY passage_id HAVING count(*) > 1
                  ) duplicate_passage_vectors)
                + (SELECT count(*) FROM (
                    SELECT child_id FROM search_v3_child_embeddings
                    GROUP BY child_id HAVING count(*) > 1
                  ) duplicate_child_vectors)
                """,
                Long.class);
    }

    private long mixedArtifactCount() {
        return jdbc.queryForObject(
                """
                SELECT
                  (SELECT count(*) FROM search_v3_evidence_children child
                   JOIN search_v3_retrieval_passages passage ON passage.id = child.passage_id
                   WHERE child.generation_id <> passage.generation_id
                      OR child.owner_user_id <> passage.owner_user_id
                      OR child.document_id <> passage.document_id
                      OR child.document_version_id <> passage.document_version_id
                      OR child.parent_annotation_candidate_id <> passage.parent_annotation_candidate_id)
                + (SELECT count(*) FROM search_v3_passage_embeddings embedding
                   JOIN search_v3_retrieval_passages passage ON passage.id = embedding.passage_id
                   WHERE embedding.generation_id <> passage.generation_id
                      OR embedding.owner_user_id <> passage.owner_user_id
                      OR embedding.document_id <> passage.document_id
                      OR embedding.document_version_id <> passage.document_version_id)
                + (SELECT count(*) FROM search_v3_child_embeddings embedding
                   JOIN search_v3_evidence_children child ON child.id = embedding.child_id
                   WHERE embedding.generation_id <> child.generation_id
                      OR embedding.owner_user_id <> child.owner_user_id
                      OR embedding.document_id <> child.document_id
                      OR embedding.document_version_id <> child.document_version_id)
                """,
                Long.class);
    }

    private static FixtureOwner requiredOwner(Map<String, FixtureOwner> owners, String id) {
        FixtureOwner value = owners.get(id);
        if (value == null) throw new IllegalStateException("unknown owner fixture: " + id);
        return value;
    }

    private void requireEmptyEvaluationDatabase() {
        for (String table : List.of(
                "users", "documents", "document_versions", "document_chunks",
                "search_v3_index_generations", "search_v3_indexing_jobs")) {
            Long count = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
            if (count == null || count != 0L) {
                throw new IllegalStateException("PRZ-042 requires an empty isolated database: " + table);
            }
        }
    }

    private static void requireSameDocumentContract(
            Prz042FinalDataset.RuntimeDocument first,
            Prz042FinalDataset.RuntimeDocument next) {
        if (!first.userBundleId().equals(next.userBundleId())
                || !first.logicalDocumentId().equals(next.logicalDocumentId())
                || !first.documentType().equals(next.documentType())) {
            throw new IllegalStateException("logical document versions disagree on owner/type");
        }
    }

    private long count(String table) {
        Long value = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return value == null ? 0L : value;
    }

    private static String safeFileComponent(String value) {
        String safe = value.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.isBlank()) throw new IllegalArgumentException("fixture version ID cannot form a file name");
        return safe;
    }

    private static String codePointSubstring(String value, int start, int end) {
        int charStart = value.offsetByCodePoints(0, start);
        int charEnd = value.offsetByCodePoints(0, end);
        return value.substring(charStart, charEnd);
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    record RunMetadata(
            String codeFreezeCommit,
            SearchV3MinimalShadowFreeze.SourceFreeze sourceFreeze,
            SearchV3MinimalShadowFreeze.SealedState sealedState,
            String expectedModelId,
            String expectedModelDigest,
            int expectedDimension,
            boolean realBgeM3) {

        RunMetadata {
            Objects.requireNonNull(codeFreezeCommit, "codeFreezeCommit");
            Objects.requireNonNull(sourceFreeze, "sourceFreeze");
            Objects.requireNonNull(sealedState, "sealedState");
            Objects.requireNonNull(expectedModelId, "expectedModelId");
            Objects.requireNonNull(expectedModelDigest, "expectedModelDigest");
            if (expectedDimension < 1) throw new IllegalArgumentException("expectedDimension must be positive");
        }

        void requireModel(SearchV3EmbeddingModelContract actual) {
            if (!expectedModelId.equals(actual.modelId())
                    || !expectedModelDigest.equals(actual.resolvedModelDigest())
                    || expectedDimension != actual.dimension()) {
                throw new IllegalStateException("actual BGE-M3 identity differs from PRZ-042 freeze");
            }
        }
    }

    private record FixtureOwner(String fixtureId, long ownerId) {
    }

    private record FixtureDocument(String ownerFixtureId, String logicalDocumentId, long documentId) {
    }

    private record FixtureVersion(
            Prz042FinalDataset.RuntimeDocument source,
            long ownerId,
            long documentId,
            long versionId,
            String storedPath) {
    }

    private static final class FixtureDatabase {
        private final Map<String, FixtureOwner> owners;
        private final Map<String, FixtureDocument> documents;
        private final Map<String, FixtureVersion> versions;
        private final Map<Long, FixtureVersion> byDatabaseVersionId;
        private final Map<Long, ProductionV2ShadowAdapter.SourceSpan> chunkSpans;
        private FixtureVersion inactiveDecoyVersion;

        Map<String, FixtureOwner> owners() { return owners; }
        Map<String, FixtureDocument> documents() { return documents; }
        Map<String, FixtureVersion> versions() { return versions; }
        Map<Long, FixtureVersion> byDatabaseVersionId() { return byDatabaseVersionId; }
        Map<Long, ProductionV2ShadowAdapter.SourceSpan> chunkSpans() { return chunkSpans; }

        private FixtureDatabase(
                Map<String, FixtureOwner> owners,
                Map<String, FixtureDocument> documents,
                Map<String, FixtureVersion> versions,
                Map<Long, FixtureVersion> byDatabaseVersionId,
                Map<Long, ProductionV2ShadowAdapter.SourceSpan> chunkSpans) {
            this.owners = owners;
            this.documents = documents;
            this.versions = versions;
            this.byDatabaseVersionId = byDatabaseVersionId;
            this.chunkSpans = chunkSpans;
        }

        FixtureDatabase(
                Map<String, FixtureOwner> owners,
                Map<String, FixtureDocument> documents,
                Map<String, FixtureVersion> versions) {
            this(
                    Map.copyOf(owners),
                    Map.copyOf(documents),
                    Map.copyOf(versions),
                    new LinkedHashMap<>(versions.values().stream().collect(Collectors.toMap(
                            FixtureVersion::versionId, Function.identity()))),
                    new LinkedHashMap<>());
        }
    }

    private record V2IndexResult(
            SearchV3MinimalShadowFreeze.IndexingStats stats,
            Map<Long, ProductionV2ShadowAdapter.SourceSpan> spans,
            List<SearchV3MinimalShadowFreeze.IndexUnit> units,
            long inactiveDecoyChunkCount,
            long activeChunkCount) {

        V2IndexResult {
            spans = Map.copyOf(spans);
            units = List.copyOf(units);
        }
    }

    private record V3IndexResult(
            SearchV3MinimalShadowFreeze.IndexingStats stats,
            List<SearchV3MinimalShadowFreeze.IndexUnit> units,
            long childCount,
            long passageVectorCount,
            long childVectorCount) {

        V3IndexResult {
            units = List.copyOf(units);
        }
    }

    private record ProjectedChunk(
            int chunkNo,
            String content,
            int codePointStart,
            int codePointEnd) {
    }

    private record V3UnitRow(
            long passageId,
            String passageKey,
            String parentId,
            long childId,
            String childKey,
            long ownerId,
            long documentId,
            long documentVersionId,
            String sourceText,
            String storedSourcePath,
            Integer page,
            int codePointStart,
            int codePointEnd,
            String sourceTextSha256) {
    }
}
