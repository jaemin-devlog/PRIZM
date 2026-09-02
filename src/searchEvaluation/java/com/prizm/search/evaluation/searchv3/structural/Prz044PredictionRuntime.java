package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.ingestion.service.DocumentTextExtractor;
import com.prizm.ingestion.service.IndexingCoordinator;
import com.prizm.ingestion.service.PageText;
import com.prizm.ingestion.service.TextChunk;
import com.prizm.ingestion.service.TextChunker;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.dto.response.CareerEvidenceSearchV2Response;
import com.prizm.search.service.SearchService;
import com.prizm.search.v3.indexing.model.SearchV3EmbeddingModelContract;
import com.prizm.search.v3.indexing.service.SearchV3EmbeddingModelContractProvider;
import com.prizm.search.v3.indexing.service.SearchV3IndexingCoordinator;
import com.prizm.search.v3.indexing.service.SearchV3JobDispatchService;
import com.prizm.search.v3.query.model.SearchV3EvidenceResult;
import com.prizm.search.v3.query.model.SearchV3QueryResult;
import com.prizm.search.v3.query.service.SearchV3ShadowQueryService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Runs the Gold-free PRZ-044 prediction pass through the actual Production V2 and Search V3
 * runtimes.
 *
 * <p>The adapter accepts only the verified raw input projection and artifact commitments. It does
 * not accept Gold, labels, metrics, expected evidence, or a candidate replay. V2 indexing is the
 * normal {@link IndexingCoordinator} path from a stored immutable source and a PENDING job. Search
 * V3 uses its normal dispatch, worker, and query services. Every V2 query completes and the V2
 * artifact is frozen and reloaded before any Search V3 work starts.</p>
 */
final class Prz044PredictionRuntime {

    static final int OFFICIAL_OWNER_COUNT = 75;
    static final int OFFICIAL_DOCUMENT_COUNT = 90;
    static final int OFFICIAL_QUERY_COUNT = 600;
    static final int OFFICIAL_MODEL_DIMENSION = 1024;
    static final String OFFICIAL_MODEL_DIGEST =
            "7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab";

    private static final int EMBEDDING_BYTES_PER_FLOAT = Float.BYTES;
    private static final String V2_INDEX_BOUNDARY =
            "ACTUAL_INDEXING_COORDINATOR_QUARANTINED_VERSION_PENDING_JOB_DRAIN";
    private static final String V3_INDEX_BOUNDARY =
            "ACTUAL_SEARCH_V3_DISPATCH_ALL_THEN_INDEXING_COORDINATOR_DRAIN";

    private final JdbcTemplate jdbc;
    private final FileStorage fileStorage;
    private final DocumentTextExtractor textExtractor;
    private final TextChunker textChunker;
    private final int maxChunkLength;
    private final int chunkOverlap;
    private final EmbeddingService embeddingService;
    private final EmbeddingValidator embeddingValidator;
    private final IndexingCoordinator v2Coordinator;
    private final SearchService v2SearchService;
    private final SearchV3JobDispatchService v3Dispatch;
    private final SearchV3IndexingCoordinator v3Coordinator;
    private final SearchV3ShadowQueryService v3QueryService;
    private final SearchV3EmbeddingModelContractProvider modelProvider;

    Prz044PredictionRuntime(
            JdbcTemplate jdbc,
            FileStorage fileStorage,
            DocumentTextExtractor textExtractor,
            TextChunker textChunker,
            IngestionProperties ingestionProperties,
            EmbeddingService embeddingService,
            EmbeddingValidator embeddingValidator,
            IndexingCoordinator v2Coordinator,
            SearchService v2SearchService,
            SearchV3JobDispatchService v3Dispatch,
            SearchV3IndexingCoordinator v3Coordinator,
            SearchV3ShadowQueryService v3QueryService,
            SearchV3EmbeddingModelContractProvider modelProvider) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
        this.fileStorage = Objects.requireNonNull(fileStorage, "fileStorage");
        this.textExtractor = Objects.requireNonNull(textExtractor, "textExtractor");
        this.textChunker = Objects.requireNonNull(textChunker, "textChunker");
        Objects.requireNonNull(ingestionProperties, "ingestionProperties").validate();
        this.maxChunkLength = ingestionProperties.getMaxChunkLength();
        this.chunkOverlap = ingestionProperties.getOverlap();
        this.embeddingService = Objects.requireNonNull(embeddingService, "embeddingService");
        this.embeddingValidator = Objects.requireNonNull(embeddingValidator, "embeddingValidator");
        this.v2Coordinator = Objects.requireNonNull(v2Coordinator, "v2Coordinator");
        this.v2SearchService = Objects.requireNonNull(v2SearchService, "v2SearchService");
        this.v3Dispatch = Objects.requireNonNull(v3Dispatch, "v3Dispatch");
        this.v3Coordinator = Objects.requireNonNull(v3Coordinator, "v3Coordinator");
        this.v3QueryService = Objects.requireNonNull(v3QueryService, "v3QueryService");
        this.modelProvider = Objects.requireNonNull(modelProvider, "modelProvider");
    }

    /** Performs the real model resolve/warm-up/re-resolve check before an official attempt is claimed. */
    ModelPrecheck precheckAndWarmUp(RunContract contract) {
        Objects.requireNonNull(contract, "contract");
        SearchV3EmbeddingModelContract before = modelProvider.resolve();
        requireModel(contract.model(), before);
        String warmUpText = "PRZ-044 neutral bge-m3 runtime warm-up";
        float[] vector = embeddingService.embed(warmUpText);
        embeddingValidator.validate(vector);
        if (vector.length != before.dimension()) {
            throw new IllegalStateException("PRZ-044 warm-up embedding dimension changed");
        }
        SearchV3EmbeddingModelContract after = modelProvider.resolve();
        if (!before.equals(after)) {
            Arrays.fill(vector, 0.0f);
            throw new IllegalStateException("PRZ-044 model contract changed during warm-up");
        }
        Arrays.fill(vector, 0.0f);
        return new ModelPrecheck(contract.model(), sha256(warmUpText), Instant.now().toString());
    }

    /**
     * Executes the phase-ordered run. The freeze callback must persist and reload V2; V3 cannot
     * begin until the callback returns successfully.
     */
    <T> CompletedRun<T> execute(
            Prz044PredictionDataset.VerifiedInputPackage input,
            RunContract contract,
            ModelPrecheck precheck,
            V2FreezeBoundary<T> freezeBoundary) {
        Objects.requireNonNull(input, "input");
        Objects.requireNonNull(contract, "contract");
        Objects.requireNonNull(precheck, "precheck");
        Objects.requireNonNull(freezeBoundary, "freezeBoundary");
        validateInputInventory(input, contract);
        if (!contract.model().equals(precheck.model())) {
            throw new IllegalStateException("PRZ-044 precheck does not belong to this run contract");
        }
        requireModel(contract.model(), modelProvider.resolve());
        requireEmptyEvaluationDatabase();

        FixtureDatabase fixtures = seedRawFixtures(input);
        requireModel(contract.model(), modelProvider.resolve());

        String v2StartedAt = Instant.now().toString();
        V2IndexResult v2Index = indexV2ThroughProductionCoordinator(fixtures, contract.model());
        requireModel(contract.model(), modelProvider.resolve());
        PredictionBatch v2Batch = runAllV2Queries(input.queries(), fixtures);
        requireCleanPredictionBoundary(v2Batch, "Production V2");
        requireModel(contract.model(), modelProvider.resolve());
        Prz044PredictionArtifact.PredictionSet v2 = artifact(
                Prz044PredictionArtifact.Engine.V2,
                contract.v2Profile(),
                contract,
                v2StartedAt,
                v2Index.stats(),
                audit(fixtures, v2Batch, v2LifecycleViolationCount(), v2DuplicateCount(),
                        v2MixedArtifactCount(), 0L, contract),
                v2Batch.predictions());

        T frozenV2 = Objects.requireNonNull(
                freezeBoundary.freezeAndReload(v2),
                "V2 freeze boundary must return the reloaded frozen handle");

        // This check occurs after disk reload and before the first V3 dispatch/model call.
        requireModel(contract.model(), modelProvider.resolve());
        String v3StartedAt = Instant.now().toString();
        V3IndexResult v3Index = indexV3ThroughProductionRuntime(fixtures, contract.model());
        requireModel(contract.model(), modelProvider.resolve());
        PredictionBatch v3Batch = runAllV3Queries(input.queries(), fixtures);
        requireCleanPredictionBoundary(v3Batch, "Search V3");
        requireModel(contract.model(), modelProvider.resolve());
        Prz044PredictionArtifact.PredictionSet v3 = artifact(
                Prz044PredictionArtifact.Engine.V3,
                contract.v3Profile(),
                contract,
                v3StartedAt,
                v3Index.stats(),
                audit(fixtures, v3Batch, v3LifecycleViolationCount(), v3DuplicateCount(),
                        v3MixedArtifactCount(), v3CrossParentCount(), contract),
                v3Batch.predictions());
        return new CompletedRun<>(v2, frozenV2, v3);
    }

    private FixtureDatabase seedRawFixtures(Prz044PredictionDataset.VerifiedInputPackage input) {
        Map<String, FixtureOwner> owners = new LinkedHashMap<>();
        List<Prz044PredictionDataset.RuntimeUser> orderedUsers = input.users().stream()
                .sorted(Comparator.comparing(Prz044PredictionDataset.RuntimeUser::userId))
                .toList();
        for (int index = 0; index < orderedUsers.size(); index++) {
            Prz044PredictionDataset.RuntimeUser source = orderedUsers.get(index);
            long ownerId = requiredLong(jdbc.queryForObject(
                    """
                    INSERT INTO users(email, password_hash, role, enabled)
                    VALUES (?, 'prz044-not-used', 'USER', TRUE)
                    RETURNING id
                    """,
                    Long.class,
                    "prz044-owner-%03d@example.invalid".formatted(index + 1)), "owner id");
            if (owners.put(source.userId(), new FixtureOwner(source, ownerId)) != null) {
                throw new IllegalStateException("duplicate PRZ-044 runtime owner: " + source.userId());
            }
        }

        Map<String, FixtureDocument> documents = new LinkedHashMap<>();
        Map<String, FixtureVersion> versions = new LinkedHashMap<>();
        Map<Long, FixtureVersion> versionsByDatabaseId = new LinkedHashMap<>();
        Map<Long, FixtureDocument> documentsByDatabaseId = new LinkedHashMap<>();
        List<Prz044PredictionDataset.RuntimeDocument> orderedDocuments = input.documents().stream()
                .sorted(Comparator.comparing(Prz044PredictionDataset.RuntimeDocument::documentId)
                        .thenComparing(Prz044PredictionDataset.RuntimeDocument::versionId))
                .toList();
        for (int index = 0; index < orderedDocuments.size(); index++) {
            Prz044PredictionDataset.RuntimeDocument source = orderedDocuments.get(index);
            FixtureOwner owner = requiredOwner(owners, source.userId());
            DocumentType type;
            try {
                type = DocumentType.valueOf(source.sourceDocumentType());
            }
            catch (IllegalArgumentException exception) {
                throw new IllegalStateException(
                        "unknown PRZ-044 source document type: " + source.sourceDocumentType(), exception);
            }
            long documentId = requiredLong(jdbc.queryForObject(
                    """
                    INSERT INTO documents(owner_user_id, title, document_type)
                    VALUES (?, ?, ?) RETURNING id
                    """,
                    Long.class,
                    owner.databaseId(),
                    "PRZ-044 source %03d".formatted(index + 1),
                    type.name()), "document id");
            FixtureDocument document = new FixtureDocument(source.documentId(), owner, documentId);
            if (documents.put(source.documentId(), document) != null) {
                throw new IllegalStateException("PRZ-044 requires one version per document identity");
            }
            documentsByDatabaseId.put(documentId, document);

            if (!sha256(source.sourceBytes()).equals(source.rawContentSha256())) {
                throw new IllegalStateException("raw source hash changed before runtime storage: " + source.versionId());
            }
            long versionId = requiredLong(jdbc.queryForObject(
                    """
                    INSERT INTO document_versions(
                        owner_user_id, document_id, version_no, original_file_name,
                        stored_file_path, file_type, content_hash, status
                    ) VALUES (?, ?, 1, ?, 'pending', ?, ?, 'QUARANTINED')
                    RETURNING id
                    """,
                    Long.class,
                    owner.databaseId(),
                    documentId,
                    source.filename(),
                    source.fileType().name(),
                    source.rawContentSha256()), "document version id");
            String storedPath = fileStorage.store(
                    documentId, versionId, source.filename(), source.sourceBytes());
            jdbc.update("UPDATE document_versions SET stored_file_path = ? WHERE id = ?", storedPath, versionId);
            byte[] storedBytes = fileStorage.read(storedPath);
            if (!Arrays.equals(source.sourceBytes(), storedBytes)) {
                throw new IllegalStateException("FileStorage did not preserve exact PRZ-044 source bytes");
            }
            List<PageText> actualPages = textExtractor.extract(source.fileType(), storedBytes);
            if (!actualPages.equals(source.pages())) {
                throw new IllegalStateException(
                        "production DocumentTextExtractor differs from verified input: " + source.versionId());
            }
            FixtureVersion version = new FixtureVersion(
                    source,
                    owner,
                    document,
                    versionId,
                    storedPath,
                    actualPages,
                    projectV2Chunks(source, actualPages));
            if (versions.put(source.versionId(), version) != null
                    || versionsByDatabaseId.put(versionId, version) != null) {
                throw new IllegalStateException("duplicate PRZ-044 version identity");
            }
            jdbc.update(
                    """
                    INSERT INTO processing_jobs(owner_user_id, document_version_id, job_type, status)
                    VALUES (?, ?, 'INDEXING', 'PENDING')
                    """,
                    owner.databaseId(),
                    versionId);
        }
        return new FixtureDatabase(
                Map.copyOf(owners),
                Map.copyOf(documents),
                Map.copyOf(versions),
                Map.copyOf(documentsByDatabaseId),
                Map.copyOf(versionsByDatabaseId),
                new LinkedHashMap<>());
    }

    private V2IndexResult indexV2ThroughProductionCoordinator(
            FixtureDatabase fixtures,
            Prz044PredictionArtifact.ModelIdentity model) {
        long started = System.nanoTime();
        int processed = 0;
        while (v2Coordinator.processNext()) {
            processed++;
            if (processed > fixtures.versions().size()) {
                throw new IllegalStateException("V2 coordinator claimed more jobs than the input inventory");
            }
        }
        double wallMillis = elapsedMillis(started);
        if (processed != fixtures.versions().size()) {
            throw new IllegalStateException("V2 coordinator did not claim every PENDING input job");
        }
        requireCompletedV2Lifecycle(fixtures);

        long chunkCount = 0L;
        for (FixtureVersion version : fixtures.versions().values().stream()
                .sorted(Comparator.comparingLong(FixtureVersion::databaseId)).toList()) {
            List<StoredChunk> rows = jdbc.query(
                    """
                    SELECT id, chunk_no, page_no, source_type, source_index, source_label, content
                    FROM document_chunks
                    WHERE owner_user_id = ? AND document_version_id = ?
                    ORDER BY chunk_no
                    """,
                    (resultSet, row) -> new StoredChunk(
                            resultSet.getLong("id"),
                            resultSet.getInt("chunk_no"),
                            resultSet.getObject("page_no", Integer.class),
                            ChunkSourceType.valueOf(resultSet.getString("source_type")),
                            resultSet.getInt("source_index"),
                            resultSet.getString("source_label"),
                            resultSet.getString("content")),
                    version.owner().databaseId(),
                    version.databaseId());
            if (rows.size() != version.v2Projection().size()) {
                throw new IllegalStateException("actual V2 chunk inventory differs from TextChunker projection");
            }
            for (int index = 0; index < rows.size(); index++) {
                StoredChunk row = rows.get(index);
                ProjectedChunk expected = version.v2Projection().get(index);
                if (row.chunkNo() != expected.chunkNo()
                        || row.pageNo() != null
                        || row.sourceType() != expected.sourceType()
                        || row.sourceIndex() != expected.sourceIndex()
                        || !row.sourceLabel().equals(expected.sourceLabel())
                        || !row.content().equals(expected.text())) {
                    throw new IllegalStateException("persisted V2 chunk differs from production extraction projection");
                }
                fixtures.chunksByDatabaseId().put(row.id(), expected);
            }
            chunkCount += rows.size();
        }
        long wrongDimensions = requiredLong(jdbc.queryForObject(
                "SELECT count(*) FROM document_chunks WHERE vector_dims(embedding) <> ?",
                Long.class,
                model.dimension()), "V2 wrong dimension count");
        if (wrongDimensions != 0L || chunkCount == 0L
                || chunkCount != count("document_chunks")) {
            throw new IllegalStateException("actual V2 vector inventory is incomplete or contaminated");
        }
        return new V2IndexResult(new Prz044PredictionArtifact.IndexingStats(
                fixtures.documents().size(),
                chunkCount,
                chunkCount,
                Math.multiplyExact(chunkCount, (long) model.dimension() * EMBEDDING_BYTES_PER_FLOAT),
                wallMillis,
                V2_INDEX_BOUNDARY));
    }

    private PredictionBatch runAllV2Queries(
            List<Prz044PredictionDataset.RuntimeQuery> queries,
            FixtureDatabase fixtures) {
        List<Prz044PredictionArtifact.QueryPrediction> predictions = new ArrayList<>(queries.size());
        long ownerLeakage = 0L;
        long inactiveLeakage = 0L;
        for (Prz044PredictionDataset.RuntimeQuery query : queries) {
            FixtureOwner owner = requiredOwner(fixtures.owners(), query.userId());
            long started = System.nanoTime();
            // The measured boundary deliberately contains exactly one final Production V2 service call.
            CareerEvidenceSearchV2Response response =
                    v2SearchService.searchCareerEvidenceV2(owner.databaseId(), query.query());
            double totalMillis = elapsedMillis(started);
            List<Prz044PredictionArtifact.Result> results = new ArrayList<>(response.results().size());
            for (int index = 0; index < response.results().size(); index++) {
                CareerEvidenceSearchResponse result = response.results().get(index);
                ProjectedChunk selected = requiredChunk(fixtures, result.chunkId());
                ProjectedChunk evidence = requiredChunk(fixtures, result.evidenceChunkId());
                requireV2ResultMatchesRuntime(result, selected, evidence);
                List<Prz044PredictionArtifact.SourceSpan> displays =
                        displaySpans(evidence, result.snippet());
                ownerLeakage += selected.version().owner().source().userId().equals(query.userId())
                                && evidence.version().owner().source().userId().equals(query.userId())
                        ? 0L : 1L;
                inactiveLeakage += isActiveVersion(selected.version().databaseId())
                                && isActiveVersion(evidence.version().databaseId())
                        ? 0L : 1L;
                results.add(new Prz044PredictionArtifact.Result(
                        index + 1,
                        stableV2ChunkId(selected),
                        "V2|" + selected.version().source().versionId(),
                        result.score(),
                        response.state().name(),
                        List.of(selected.span()),
                        displays));
            }
            predictions.add(new Prz044PredictionArtifact.QueryPrediction(
                    query.queryId(),
                    query.userId(),
                    query.professionId(),
                    query.professionLabel(),
                    query.language(),
                    query.querySha256(),
                    response.state().name(),
                    totalMillis,
                    results));
        }
        return new PredictionBatch(List.copyOf(predictions), ownerLeakage, inactiveLeakage, 0L);
    }

    private V3IndexResult indexV3ThroughProductionRuntime(
            FixtureDatabase fixtures,
            Prz044PredictionArtifact.ModelIdentity model) {
        long started = System.nanoTime();
        int dispatched = 0;
        while (v3Dispatch.dispatchNext().isPresent()) {
            dispatched++;
            if (dispatched > fixtures.documents().size()) {
                throw new IllegalStateException("Search V3 dispatched more jobs than active documents");
            }
        }
        if (dispatched != fixtures.documents().size()) {
            throw new IllegalStateException("Search V3 did not dispatch every active document");
        }
        int processed = 0;
        while (v3Coordinator.processNext()) {
            processed++;
            if (processed > dispatched) {
                throw new IllegalStateException("Search V3 claimed more jobs than were dispatched");
            }
        }
        double wallMillis = elapsedMillis(started);
        if (processed != dispatched) {
            throw new IllegalStateException("Search V3 did not claim every dispatched job");
        }
        requireCompletedV3Lifecycle(fixtures);
        long passages = count("search_v3_retrieval_passages");
        long children = count("search_v3_evidence_children");
        long passageVectors = count("search_v3_passage_embeddings");
        long childVectors = count("search_v3_child_embeddings");
        if (passages == 0L || children == 0L
                || passages != passageVectors || children != childVectors) {
            throw new IllegalStateException("actual Search V3 Passage/Child vector inventory is incomplete");
        }
        long vectorCount = Math.addExact(passageVectors, childVectors);
        return new V3IndexResult(new Prz044PredictionArtifact.IndexingStats(
                fixtures.documents().size(),
                passages,
                vectorCount,
                Math.multiplyExact(vectorCount, (long) model.dimension() * EMBEDDING_BYTES_PER_FLOAT),
                wallMillis,
                V3_INDEX_BOUNDARY));
    }

    private PredictionBatch runAllV3Queries(
            List<Prz044PredictionDataset.RuntimeQuery> queries,
            FixtureDatabase fixtures) {
        List<Prz044PredictionArtifact.QueryPrediction> predictions = new ArrayList<>(queries.size());
        long ownerLeakage = 0L;
        long inactiveLeakage = 0L;
        long crossParent = 0L;
        for (Prz044PredictionDataset.RuntimeQuery query : queries) {
            FixtureOwner owner = requiredOwner(fixtures.owners(), query.userId());
            long started = System.nanoTime();
            // The service owns query embedding, candidate retrieval, typed selection, and max-five output.
            SearchV3QueryResult response = v3QueryService.search(owner.databaseId(), query.query());
            double totalMillis = elapsedMillis(started);
            List<Prz044PredictionArtifact.Result> results = new ArrayList<>(response.evidence().size());
            for (SearchV3EvidenceResult result : response.evidence()) {
                FixtureVersion version = requiredVersion(fixtures, result.documentVersionId());
                Prz044PredictionArtifact.SourceSpan span = v3Span(version, result);
                ownerLeakage += version.owner().source().userId().equals(query.userId()) ? 0L : 1L;
                inactiveLeakage += isActiveVersion(version.databaseId()) ? 0L : 1L;
                String stableId = "V3|" + canonicalV3Identity(version, result.childKey());
                String parentStableId =
                        "V3P|" + canonicalV3Identity(version, result.parentAnnotationCandidateId());
                results.add(new Prz044PredictionArtifact.Result(
                        result.rank(),
                        stableId,
                        parentStableId,
                        result.childCosineScore() == null
                                ? result.passageCosineScore() : result.childCosineScore(),
                        result.typedMatchState() == null
                                ? "UNASSESSED" : result.typedMatchState().name(),
                        List.of(span),
                        List.of(span)));
            }
            predictions.add(new Prz044PredictionArtifact.QueryPrediction(
                    query.queryId(),
                    query.userId(),
                    query.professionId(),
                    query.professionLabel(),
                    query.language(),
                    query.querySha256(),
                    response.state().name(),
                    totalMillis,
                    results));
        }
        return new PredictionBatch(List.copyOf(predictions), ownerLeakage, inactiveLeakage, crossParent);
    }

    List<ProjectedChunk> projectV2Chunks(
            Prz044PredictionDataset.RuntimeDocument source,
            List<PageText> pages) {
        List<ProjectedChunk> result = new ArrayList<>();
        int nextGlobalChunkNo = 1;
        for (PageText page : pages) {
            List<TextChunk> actualChunks = textChunker.split(page.text());
            int actualIndex = 0;
            int start = 0;
            while (start < page.text().length()) {
                int end = Math.min(start + maxChunkLength, page.text().length());
                String raw = page.text().substring(start, end);
                String content = raw.strip();
                if (!content.isBlank()) {
                    if (actualIndex >= actualChunks.size()
                            || !actualChunks.get(actualIndex).content().equals(content)) {
                        throw new IllegalStateException("production TextChunker contract changed during projection");
                    }
                    int leadingChars = leadingStripChars(raw);
                    int trailingChars = trailingStripChars(raw);
                    int charStart = start + leadingChars;
                    int charEnd = end - trailingChars;
                    int codePointStart = page.text().codePointCount(0, charStart);
                    int codePointEnd = page.text().codePointCount(0, charEnd);
                    int chunkNo = nextGlobalChunkNo++;
                    ChunkSourceType sourceType = source.fileType() == DocumentFileType.TXT
                            ? ChunkSourceType.TEXT_CHUNK : ChunkSourceType.PAGE;
                    int sourceIndex = source.fileType() == DocumentFileType.TXT
                            ? chunkNo : page.pageNumber();
                    String sourceLabel = source.fileType() == DocumentFileType.TXT
                            ? "텍스트 구간 " + chunkNo : page.pageNumber() + "페이지";
                    Integer pageNumber = source.fileType() == DocumentFileType.TXT
                            ? null : page.pageNumber();
                    result.add(new ProjectedChunk(
                            null,
                            chunkNo,
                            sourceType,
                            sourceIndex,
                            sourceLabel,
                            content,
                            pageNumber,
                            codePointStart,
                            codePointEnd,
                            null));
                    actualIndex++;
                }
                if (end == page.text().length()) break;
                start = end - chunkOverlap;
            }
            if (actualIndex != actualChunks.size()) {
                throw new IllegalStateException("production TextChunker returned an unprojected chunk");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalStateException("verified source produced no V2 chunks");
        }
        // FixtureVersion is not available until the projections have been created. It is attached
        // immediately below by the caller through attachProjectionVersion.
        return List.copyOf(result);
    }

    private static int leadingStripChars(String value) {
        int index = 0;
        while (index < value.length()) {
            int codePoint = value.codePointAt(index);
            if (!Character.isWhitespace(codePoint)) break;
            index += Character.charCount(codePoint);
        }
        return index;
    }

    private static int trailingStripChars(String value) {
        int index = value.length();
        while (index > 0) {
            int codePoint = value.codePointBefore(index);
            if (!Character.isWhitespace(codePoint)) break;
            index -= Character.charCount(codePoint);
        }
        return value.length() - index;
    }

    private Prz044PredictionArtifact.SourceSpan v3Span(
            FixtureVersion version,
            SearchV3EvidenceResult result) {
        if (result.documentId() != version.document().databaseId()
                || !result.sourcePath().equals(version.storedPath())) {
            throw new IllegalStateException("Search V3 returned source lineage outside its fixture version");
        }
        Integer expectedPage = version.source().fileType() == DocumentFileType.TXT
                ? null : result.pageNo();
        if (!Objects.equals(expectedPage, result.pageNo())
                || (version.source().fileType() == DocumentFileType.PDF && result.pageNo() == null)) {
            throw new IllegalStateException("Search V3 page provenance does not match source file type");
        }
        PageText page = version.pages().stream()
                .filter(value -> version.source().fileType() == DocumentFileType.TXT
                        || value.pageNumber() == result.pageNo())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Search V3 returned an unknown PDF page"));
        String sourceText = codePointSubstring(
                page.text(), result.codePointStart(), result.codePointEnd());
        if (!sourceText.equals(result.sourceText())) {
            throw new IllegalStateException("Search V3 source offsets do not select its returned source text");
        }
        return new Prz044PredictionArtifact.SourceSpan(
                version.owner().source().userId(),
                version.source().documentId(),
                version.source().versionId(),
                version.source().sourceDocumentType(),
                version.source().fileType(),
                version.source().relativePath(),
                expectedPage,
                result.codePointStart(),
                result.codePointEnd(),
                sha256(result.sourceText()));
    }

    private List<Prz044PredictionArtifact.SourceSpan> displaySpans(
            ProjectedChunk evidence,
            String snippet) {
        if (snippet == null || snippet.isBlank()) {
            throw new IllegalStateException("Production V2 returned a blank display snippet");
        }
        List<Prz044PredictionArtifact.SourceSpan> matches = new ArrayList<>();
        int fromIndex = 0;
        while (fromIndex <= evidence.text().length() - snippet.length()) {
            int charStart = evidence.text().indexOf(snippet, fromIndex);
            if (charStart < 0) break;
            int localCodePointStart = evidence.text().codePointCount(0, charStart);
            int localCodePointEnd = evidence.text().codePointCount(0, charStart + snippet.length());
            matches.add(new Prz044PredictionArtifact.SourceSpan(
                    evidence.version().owner().source().userId(),
                    evidence.version().source().documentId(),
                    evidence.version().source().versionId(),
                    evidence.version().source().sourceDocumentType(),
                    evidence.version().source().fileType(),
                    evidence.version().source().relativePath(),
                    evidence.pageNumber(),
                    evidence.codePointStart() + localCodePointStart,
                    evidence.codePointStart() + localCodePointEnd,
                    sha256(snippet)));
            fromIndex = charStart + 1;
        }
        if (matches.isEmpty()) {
            throw new IllegalStateException("Production V2 display snippet is not source-locatable");
        }
        return List.copyOf(matches);
    }

    private void requireV2ResultMatchesRuntime(
            CareerEvidenceSearchResponse result,
            ProjectedChunk selected,
            ProjectedChunk evidence) {
        if (result.documentId() != selected.version().document().databaseId()
                || result.documentVersionId() != selected.version().databaseId()
                || !result.content().equals(selected.text())
                || result.sourceType() != selected.sourceType()
                || result.sourceIndex() != selected.sourceIndex()
                || !result.sourceLabel().equals(selected.sourceLabel())
                || evidence.version() != selected.version()
                || result.evidenceSourceType() != evidence.sourceType()
                || result.evidenceSourceIndex() != evidence.sourceIndex()
                || !result.evidenceSourceLabel().equals(evidence.sourceLabel())) {
            throw new IllegalStateException("Production V2 result provenance differs from persisted chunks");
        }
    }

    private static String stableV2ChunkId(ProjectedChunk chunk) {
        return "V2|%s|%04d".formatted(chunk.version().source().versionId(), chunk.chunkNo());
    }

    private static String canonicalV3Identity(FixtureVersion version, String runtimeIdentity) {
        String databasePrefix = "D%d-V%d-".formatted(
                version.document().databaseId(), version.databaseId());
        if (!runtimeIdentity.startsWith(databasePrefix)) {
            throw new IllegalStateException("Search V3 identity is outside the selected document version");
        }
        return "D%s-V%s-%s".formatted(
                version.source().documentId(),
                version.source().versionId(),
                runtimeIdentity.substring(databasePrefix.length()));
    }

    private void requireCompletedV2Lifecycle(FixtureDatabase fixtures) {
        long completed = requiredLong(jdbc.queryForObject(
                "SELECT count(*) FROM processing_jobs WHERE status = 'COMPLETED'",
                Long.class), "completed V2 jobs");
        long activeVersions = requiredLong(jdbc.queryForObject(
                "SELECT count(*) FROM document_versions WHERE status = 'ACTIVE'",
                Long.class), "active V2 versions");
        long activeDocuments = requiredLong(jdbc.queryForObject(
                "SELECT count(*) FROM documents WHERE active_version_id IS NOT NULL",
                Long.class), "active V2 documents");
        if (completed != fixtures.documents().size()
                || activeVersions != fixtures.documents().size()
                || activeDocuments != fixtures.documents().size()
                || v2LifecycleViolationCount() != 0L) {
            throw new IllegalStateException("actual V2 lifecycle did not complete cleanly");
        }
    }

    private void requireCompletedV3Lifecycle(FixtureDatabase fixtures) {
        long activeGenerations = requiredLong(jdbc.queryForObject(
                "SELECT count(*) FROM search_v3_index_generations WHERE status = 'ACTIVE'",
                Long.class), "active V3 generations");
        long completedJobs = requiredLong(jdbc.queryForObject(
                "SELECT count(*) FROM search_v3_indexing_jobs WHERE status = 'COMPLETED'",
                Long.class), "completed V3 jobs");
        if (activeGenerations != fixtures.documents().size()
                || completedJobs != fixtures.documents().size()
                || v3LifecycleViolationCount() != 0L) {
            throw new IllegalStateException("actual Search V3 lifecycle did not complete cleanly");
        }
    }

    private long v2LifecycleViolationCount() {
        return requiredLong(jdbc.queryForObject(
                """
                SELECT count(*)
                FROM documents document
                LEFT JOIN document_versions version ON version.id = document.active_version_id
                LEFT JOIN processing_jobs job
                  ON job.document_version_id = version.id AND job.owner_user_id = version.owner_user_id
                WHERE version.id IS NULL OR version.status <> 'ACTIVE'
                   OR version.owner_user_id <> document.owner_user_id
                   OR version.document_id <> document.id
                   OR job.status <> 'COMPLETED'
                """, Long.class), "V2 lifecycle violations");
    }

    private long v2DuplicateCount() {
        return requiredLong(jdbc.queryForObject(
                """
                SELECT count(*) FROM (
                  SELECT document_version_id, chunk_no
                  FROM document_chunks
                  GROUP BY document_version_id, chunk_no HAVING count(*) > 1
                ) duplicate_chunks
                """, Long.class), "V2 duplicate chunks");
    }

    private long v2MixedArtifactCount() {
        return requiredLong(jdbc.queryForObject(
                """
                SELECT count(*)
                FROM document_chunks chunk
                JOIN document_versions version ON version.id = chunk.document_version_id
                WHERE chunk.owner_user_id <> version.owner_user_id
                """, Long.class), "V2 mixed artifacts");
    }

    private long v3LifecycleViolationCount() {
        return requiredLong(jdbc.queryForObject(
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
                """, Long.class), "V3 lifecycle violations");
    }

    private long v3DuplicateCount() {
        return requiredLong(jdbc.queryForObject(
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
                """, Long.class), "V3 duplicate artifacts");
    }

    private long v3MixedArtifactCount() {
        return requiredLong(jdbc.queryForObject(
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
                """, Long.class), "V3 mixed artifacts");
    }

    private long v3CrossParentCount() {
        return requiredLong(jdbc.queryForObject(
                """
                SELECT count(*)
                FROM search_v3_evidence_children child
                JOIN search_v3_retrieval_passages passage ON passage.id = child.passage_id
                WHERE child.parent_annotation_candidate_id <> passage.parent_annotation_candidate_id
                """, Long.class), "V3 cross-parent artifacts");
    }

    private Prz044PredictionArtifact.PredictionSet artifact(
            Prz044PredictionArtifact.Engine engine,
            String profile,
            RunContract contract,
            String startedAt,
            Prz044PredictionArtifact.IndexingStats stats,
            Prz044PredictionArtifact.RuntimeAudit audit,
            List<Prz044PredictionArtifact.QueryPrediction> predictions) {
        return new Prz044PredictionArtifact.PredictionSet(
                Prz044PredictionArtifact.ARTIFACT_TYPE,
                Prz044PredictionArtifact.SCHEMA_VERSION,
                engine,
                profile,
                contract.contractSha256(),
                contract.inputZipSha256(),
                contract.manifestCanonicalSha256(),
                contract.physicalPayloadCombinedSha256(),
                contract.manifestCombinedCommitmentSha256(),
                contract.sourceBoundaryHashes(),
                contract.model(),
                contract.queryInventorySha256(),
                startedAt,
                Instant.now().toString(),
                stats,
                audit,
                predictions);
    }

    private Prz044PredictionArtifact.RuntimeAudit audit(
            FixtureDatabase fixtures,
            PredictionBatch batch,
            long lifecycleViolations,
            long duplicates,
            long mixedArtifacts,
            long crossParent,
            RunContract contract) {
        return new Prz044PredictionArtifact.RuntimeAudit(
                fixtures.owners().size(),
                fixtures.documents().size(),
                batch.predictions().size(),
                batch.ownerLeakage(),
                batch.inactiveLeakage(),
                lifecycleViolations,
                duplicates,
                mixedArtifacts,
                Math.addExact(crossParent, batch.crossParent()),
                contract.realBgeM3(),
                contract.model().modelId(),
                contract.model().resolvedDigest(),
                contract.model().dimension(),
                0,
                0,
                false);
    }

    private static void requireCleanPredictionBoundary(PredictionBatch batch, String engine) {
        if (batch.ownerLeakage() != 0L || batch.inactiveLeakage() != 0L
                || batch.crossParent() != 0L) {
            throw new IllegalStateException(engine + " prediction isolation audit failed");
        }
    }

    private void validateInputInventory(
            Prz044PredictionDataset.VerifiedInputPackage input,
            RunContract contract) {
        if (input.users().size() != contract.expectedOwnerCount()
                || input.documents().size() != contract.expectedDocumentCount()
                || input.queries().size() != contract.expectedQueryCount()) {
            throw new IllegalStateException("PRZ-044 verified input scale differs from the run contract");
        }
        if (!Prz044PredictionFreeze.queryInventorySha256(input.queries())
                .equals(contract.queryInventorySha256())) {
            throw new IllegalStateException("PRZ-044 query inventory differs from the run contract");
        }
        Set<String> owners = new HashSet<>();
        Map<String, Prz044PredictionDataset.RuntimeUser> users = new LinkedHashMap<>();
        for (Prz044PredictionDataset.RuntimeUser user : input.users()) {
            if (!owners.add(user.userId())) throw new IllegalStateException("duplicate runtime owner");
            users.put(user.userId(), user);
        }
        Set<String> documents = new HashSet<>();
        Set<String> versions = new HashSet<>();
        int txt = 0;
        int pdf = 0;
        for (Prz044PredictionDataset.RuntimeDocument document : input.documents()) {
            Prz044PredictionDataset.RuntimeUser user = users.get(document.userId());
            if (user == null
                    || !user.professionId().equals(document.professionId())
                    || !user.professionLabel().equals(document.professionLabel())) {
                throw new IllegalStateException("runtime document owner/profession lineage changed");
            }
            if (!documents.add(document.documentId()) || !versions.add(document.versionId())) {
                throw new IllegalStateException("duplicate runtime document/version identity");
            }
            if (document.fileType() == DocumentFileType.TXT) txt++;
            if (document.fileType() == DocumentFileType.PDF) pdf++;
        }
        Set<String> queryIds = new HashSet<>();
        for (Prz044PredictionDataset.RuntimeQuery query : input.queries()) {
            Prz044PredictionDataset.RuntimeUser user = users.get(query.userId());
            if (user == null
                    || !user.professionId().equals(query.professionId())
                    || !user.professionLabel().equals(query.professionLabel())
                    || !queryIds.add(query.queryId())) {
                throw new IllegalStateException("runtime query owner/profession/identity changed");
            }
            if (!sha256(query.query()).equals(query.querySha256())) {
                throw new IllegalStateException("runtime query text hash changed");
            }
        }
        if (contract.mode() == RunMode.OFFICIAL && (txt != 45 || pdf != 45)) {
            throw new IllegalStateException("official PRZ-044 input must contain 45 TXT and 45 PDF sources");
        }
    }

    private void requireEmptyEvaluationDatabase() {
        for (String table : List.of(
                "users",
                "documents",
                "document_versions",
                "document_chunks",
                "processing_jobs",
                "document_change_logs",
                "search_v3_index_generations",
                "search_v3_indexing_jobs",
                "search_v3_retrieval_passages",
                "search_v3_evidence_children",
                "search_v3_passage_embeddings",
                "search_v3_child_embeddings")) {
            if (count(table) != 0L) {
                throw new IllegalStateException("PRZ-044 requires an empty isolated database: " + table);
            }
        }
    }

    private void requireModel(
            Prz044PredictionArtifact.ModelIdentity expected,
            SearchV3EmbeddingModelContract actual) {
        if (!expected.modelId().equals(actual.modelId())
                || !expected.resolvedDigest().equals(actual.resolvedModelDigest())
                || expected.dimension() != actual.dimension()
                || !"COSINE".equals(expected.similarity().toUpperCase(Locale.ROOT))) {
            throw new IllegalStateException("PRZ-044 resolved model differs from the frozen contract");
        }
    }

    private ProjectedChunk requiredChunk(FixtureDatabase fixtures, Long chunkId) {
        ProjectedChunk value = fixtures.chunksByDatabaseId().get(chunkId);
        if (value == null) {
            throw new IllegalStateException("Production V2 returned an unknown chunk ID: " + chunkId);
        }
        return value;
    }

    private static FixtureOwner requiredOwner(Map<String, FixtureOwner> owners, String userId) {
        FixtureOwner value = owners.get(userId);
        if (value == null) throw new IllegalStateException("unknown PRZ-044 runtime owner: " + userId);
        return value;
    }

    private static FixtureVersion requiredVersion(FixtureDatabase fixtures, long databaseVersionId) {
        FixtureVersion value = fixtures.versionsByDatabaseId().get(databaseVersionId);
        if (value == null) {
            throw new IllegalStateException("Search V3 returned an unknown document version");
        }
        return value;
    }

    private boolean isActiveVersion(long databaseVersionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                SELECT EXISTS (
                  SELECT 1 FROM document_versions version
                  JOIN documents document ON document.id = version.document_id
                  WHERE version.id = ? AND version.status = 'ACTIVE'
                    AND document.active_version_id = version.id
                )
                """, Boolean.class, databaseVersionId));
    }

    private long count(String table) {
        return requiredLong(jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class), table);
    }

    private static long requiredLong(Long value, String label) {
        if (value == null) throw new IllegalStateException(label + " query returned null");
        return value;
    }

    private static String codePointSubstring(String value, int start, int end) {
        int count = value.codePointCount(0, value.length());
        if (start < 0 || end <= start || end > count) {
            throw new IllegalStateException("runtime returned an invalid code-point range");
        }
        return value.substring(value.offsetByCodePoints(0, start), value.offsetByCodePoints(0, end));
    }

    private static double elapsedMillis(long startedNanos) {
        return (System.nanoTime() - startedNanos) / 1_000_000.0d;
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireSha256(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + " must be lowercase SHA-256");
        }
    }

    private static void requireText(String value, String label) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(label + " is required");
    }

    enum RunMode {
        PREFLIGHT,
        OFFICIAL
    }

    record RunContract(
            RunMode mode,
            String v2Profile,
            String v3Profile,
            String contractSha256,
            String inputZipSha256,
            String manifestCanonicalSha256,
            String physicalPayloadCombinedSha256,
            String manifestCombinedCommitmentSha256,
            Map<String, String> sourceBoundaryHashes,
            String queryInventorySha256,
            Prz044PredictionArtifact.ModelIdentity model,
            int expectedOwnerCount,
            int expectedDocumentCount,
            int expectedQueryCount,
            boolean realBgeM3) {

        RunContract {
            Objects.requireNonNull(mode, "mode");
            requireText(v2Profile, "v2Profile");
            requireText(v3Profile, "v3Profile");
            requireSha256(contractSha256, "contractSha256");
            requireSha256(inputZipSha256, "inputZipSha256");
            requireSha256(manifestCanonicalSha256, "manifestCanonicalSha256");
            requireSha256(physicalPayloadCombinedSha256, "physicalPayloadCombinedSha256");
            requireSha256(manifestCombinedCommitmentSha256, "manifestCombinedCommitmentSha256");
            sourceBoundaryHashes = Map.copyOf(sourceBoundaryHashes);
            if (sourceBoundaryHashes.isEmpty()) {
                throw new IllegalArgumentException("sourceBoundaryHashes must not be empty");
            }
            sourceBoundaryHashes.forEach((name, hash) -> {
                requireText(name, "source boundary name");
                requireSha256(hash, "source boundary hash");
            });
            requireSha256(queryInventorySha256, "queryInventorySha256");
            Objects.requireNonNull(model, "model");
            if (expectedOwnerCount < 1 || expectedDocumentCount < 1 || expectedQueryCount < 1) {
                throw new IllegalArgumentException("expected runtime scale must be positive");
            }
            if (!realBgeM3
                    || !model.modelId().toLowerCase(Locale.ROOT).contains("bge-m3")
                    || !OFFICIAL_MODEL_DIGEST.equals(model.resolvedDigest())
                    || model.dimension() != OFFICIAL_MODEL_DIMENSION
                    || !"COSINE".equals(model.similarity().toUpperCase(Locale.ROOT))) {
                throw new IllegalArgumentException("PRZ-044 requires the frozen real bge-m3 contract");
            }
            if (mode == RunMode.OFFICIAL
                    && (expectedOwnerCount != OFFICIAL_OWNER_COUNT
                        || expectedDocumentCount != OFFICIAL_DOCUMENT_COUNT
                        || expectedQueryCount != OFFICIAL_QUERY_COUNT)) {
                throw new IllegalArgumentException("official PRZ-044 scale is fixed at 75/90/600");
            }
        }

        static RunContract official(
                Prz044PredictionFreeze.VerifiedContract contract,
                Prz044PredictionDataset.VerifiedInputPackage input) {
            Objects.requireNonNull(contract, "contract");
            Objects.requireNonNull(input, "input");
            return new RunContract(
                    RunMode.OFFICIAL,
                    contract.expectedProfiles().get(Prz044PredictionArtifact.Engine.V2),
                    contract.expectedProfiles().get(Prz044PredictionArtifact.Engine.V3),
                    contract.contractSha256(),
                    input.zipSha256(),
                    input.manifestCanonicalSha256(),
                    input.physicalCombinedSha256(),
                    input.commitmentCombinedSha256(),
                    contract.sourceBoundaryHashes(),
                    Prz044PredictionFreeze.queryInventorySha256(input.queries()),
                    contract.expectedModel(),
                    contract.expectedInput().userCount(),
                    contract.expectedInput().documentCount(),
                    contract.expectedInput().queryCount(),
                    true);
        }
    }

    record ModelPrecheck(
            Prz044PredictionArtifact.ModelIdentity model,
            String warmUpTextSha256,
            String checkedAt) {

        ModelPrecheck {
            Objects.requireNonNull(model, "model");
            requireSha256(warmUpTextSha256, "warmUpTextSha256");
            requireText(checkedAt, "checkedAt");
        }
    }

    @FunctionalInterface
    interface V2FreezeBoundary<T> {
        T freezeAndReload(Prz044PredictionArtifact.PredictionSet v2Prediction);
    }

    record CompletedRun<T>(
            Prz044PredictionArtifact.PredictionSet v2,
            T frozenV2,
            Prz044PredictionArtifact.PredictionSet v3) {

        CompletedRun {
            Objects.requireNonNull(v2, "v2");
            Objects.requireNonNull(frozenV2, "frozenV2");
            Objects.requireNonNull(v3, "v3");
            if (v2.engine() != Prz044PredictionArtifact.Engine.V2
                    || v3.engine() != Prz044PredictionArtifact.Engine.V3) {
                throw new IllegalArgumentException("PRZ-044 completed run engine order changed");
            }
        }
    }

    private record FixtureOwner(Prz044PredictionDataset.RuntimeUser source, long databaseId) {
    }

    private record FixtureDocument(String stableId, FixtureOwner owner, long databaseId) {
    }

    private record FixtureVersion(
            Prz044PredictionDataset.RuntimeDocument source,
            FixtureOwner owner,
            FixtureDocument document,
            long databaseId,
            String storedPath,
            List<PageText> pages,
            List<ProjectedChunk> v2Projection) {

        FixtureVersion {
            pages = List.copyOf(pages);
            List<ProjectedChunk> attached = new ArrayList<>(v2Projection.size());
            for (ProjectedChunk projection : v2Projection) {
                Prz044PredictionArtifact.SourceSpan span = new Prz044PredictionArtifact.SourceSpan(
                        owner.source().userId(),
                        source.documentId(),
                        source.versionId(),
                        source.sourceDocumentType(),
                        source.fileType(),
                        source.relativePath(),
                        projection.pageNumber(),
                        projection.codePointStart(),
                        projection.codePointEnd(),
                        sha256(projection.text()));
                attached.add(new ProjectedChunk(
                        this,
                        projection.chunkNo(),
                        projection.sourceType(),
                        projection.sourceIndex(),
                        projection.sourceLabel(),
                        projection.text(),
                        projection.pageNumber(),
                        projection.codePointStart(),
                        projection.codePointEnd(),
                        span));
            }
            v2Projection = List.copyOf(attached);
        }
    }

    record ProjectedChunk(
            FixtureVersion version,
            int chunkNo,
            ChunkSourceType sourceType,
            int sourceIndex,
            String sourceLabel,
            String text,
            Integer pageNumber,
            int codePointStart,
            int codePointEnd,
            Prz044PredictionArtifact.SourceSpan span) {
    }

    private record StoredChunk(
            long id,
            int chunkNo,
            Integer pageNo,
            ChunkSourceType sourceType,
            int sourceIndex,
            String sourceLabel,
            String content) {
    }

    private record FixtureDatabase(
            Map<String, FixtureOwner> owners,
            Map<String, FixtureDocument> documents,
            Map<String, FixtureVersion> versions,
            Map<Long, FixtureDocument> documentsByDatabaseId,
            Map<Long, FixtureVersion> versionsByDatabaseId,
            Map<Long, ProjectedChunk> chunksByDatabaseId) {
    }

    private record V2IndexResult(Prz044PredictionArtifact.IndexingStats stats) {
    }

    private record V3IndexResult(Prz044PredictionArtifact.IndexingStats stats) {
    }

    private record PredictionBatch(
            List<Prz044PredictionArtifact.QueryPrediction> predictions,
            long ownerLeakage,
            long inactiveLeakage,
            long crossParent) {
    }
}
