package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.entity.ChunkSourceType;
import com.prizm.ingestion.service.TextChunk;
import com.prizm.ingestion.service.TextChunker;
import com.prizm.search.config.SearchProperties;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.dto.response.CareerEvidenceSearchV2Response;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.repository.EvidenceChunk;
import com.prizm.search.repository.EvidenceExpansionRepository;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.repository.VectorSearchResult;
import com.prizm.search.service.EvidenceExpansionService;
import com.prizm.search.service.SearchService;
import com.prizm.search.service.SearchSnippetGenerator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Calls the unchanged Production V2 service/repository/profile/localization source against a
 * deterministic evaluation JDBC boundary.
 *
 * <p>The host used for PRZ-032 has no Docker/PostgreSQL runtime. The adapter therefore executes
 * the real repository methods and their SQL construction but supplies rows from the frozen,
 * owner/ACTIVE-scoped fixture inventory. Exact cosine ordering, identifier existence and numeric
 * rescue predicates mirror the repository request at this boundary; this is search-quality
 * evidence, not a fresh SQL isolation verification.</p>
 */
final class ProductionV2ShadowAdapter {

    static final int MAX_CHUNK_LENGTH = 800;
    static final int OVERLAP = 120;
    static final int CANDIDATE_LIMIT = 20;
    static final int RESULT_LIMIT = 5;

    private final OllamaBgeM3EmbeddingClient model;
    private final CachingEmbeddingService embeddingService;
    private final EmbeddingValidator embeddingValidator = new EmbeddingValidator(
            OllamaBgeM3EmbeddingClient.DIMENSIONS);
    private final TextChunker textChunker;

    ProductionV2ShadowAdapter(OllamaBgeM3EmbeddingClient model) {
        this.model = Objects.requireNonNull(model, "model");
        this.embeddingService = new CachingEmbeddingService(model);
        IngestionProperties properties = new IngestionProperties();
        properties.setMaxChunkLength(MAX_CHUNK_LENGTH);
        properties.setOverlap(OVERLAP);
        this.textChunker = new TextChunker(properties);
    }

    IndexedCorpus index(List<SearchV3MinimalShadowDataset.RuntimeDocument> documents) {
        long constructionStarted = System.nanoTime();
        Map<String, Long> ownerIds = stableIds(documents.stream()
                .map(SearchV3MinimalShadowDataset.RuntimeDocument::userBundleId).distinct().sorted().toList());
        Map<String, Long> documentIds = stableIds(documents.stream()
                .map(SearchV3MinimalShadowDataset.RuntimeDocument::documentId).distinct().sorted().toList());
        Map<String, Long> versionIds = stableIds(documents.stream()
                .map(SearchV3MinimalShadowDataset.RuntimeDocument::versionId).distinct().sorted().toList());

        List<ChunkDraft> drafts = new ArrayList<>();
        AtomicLong chunkIds = new AtomicLong(1L);
        documents.stream()
                .sorted(Comparator.comparing(SearchV3MinimalShadowDataset.RuntimeDocument::versionId))
                .forEach(document -> {
                    List<ChunkRange> ranges = splitRanges(document.sourceText());
                    List<TextChunk> actual = textChunker.split(document.sourceText());
                    if (ranges.size() != actual.size()) {
                        throw new IllegalStateException("TextChunker/range projection count mismatch");
                    }
                    for (int index = 0; index < actual.size(); index++) {
                        TextChunk chunk = actual.get(index);
                        ChunkRange range = ranges.get(index);
                        if (chunk.chunkNo() != range.chunkNo() || !chunk.content().equals(range.content())) {
                            throw new IllegalStateException("Production TextChunker projection drifted");
                        }
                        drafts.add(new ChunkDraft(
                                chunkIds.getAndIncrement(),
                                ownerIds.get(document.userBundleId()),
                                documentIds.get(document.documentId()),
                                versionIds.get(document.versionId()),
                                document,
                                range));
                    }
                });
        double constructionMs = millis(System.nanoTime() - constructionStarted);

        long indexingStarted = System.nanoTime();
        OllamaBgeM3EmbeddingClient.EmbeddingBatch embedded = model.embedAll(
                drafts.stream().map(value -> value.range().content()).toList());
        List<FixedChunk> chunks = new ArrayList<>();
        for (int index = 0; index < drafts.size(); index++) {
            ChunkDraft draft = drafts.get(index);
            chunks.add(new FixedChunk(
                    draft.chunkId(),
                    draft.ownerId(),
                    draft.documentId(),
                    draft.versionId(),
                    draft.document().userBundleId(),
                    draft.document().documentId(),
                    draft.document().versionId(),
                    draft.document().title(),
                    draft.document().versionNumber(),
                    draft.document().active(),
                    draft.range().chunkNo(),
                    draft.range().content(),
                    draft.document().sourcePath(),
                    null,
                    draft.range().codePointStart(),
                    draft.range().codePointEnd(),
                    draft.document().contentSha256(),
                    embedded.embeddings().get(index)));
        }
        double indexingWallMs = millis(System.nanoTime() - indexingStarted);
        Map<Long, FixedChunk> byChunkId = chunks.stream().collect(java.util.stream.Collectors.toMap(
                FixedChunk::chunkId, value -> value, (left, right) -> {
                    throw new IllegalStateException("duplicate V2 chunk ID");
                }, LinkedHashMap::new));
        Map<String, List<FixedChunk>> activeByOwner = chunks.stream().filter(FixedChunk::active)
                .collect(java.util.stream.Collectors.groupingBy(
                        FixedChunk::userBundleId, LinkedHashMap::new, java.util.stream.Collectors.toList()));
        EvaluationJdbcBoundary boundary = new EvaluationJdbcBoundary(
                ownerIds, byChunkId, activeByOwner);
        JdbcTemplate jdbc = boundary.jdbcTemplate();
        VectorSearchRepository vectorRepository = new VectorSearchRepository(jdbc);
        EvidenceExpansionRepository expansionRepository = new EvidenceExpansionRepository(jdbc);
        EvidenceExpansionService expansion = new EvidenceExpansionService(
                expansionRepository, new SearchSnippetGenerator());
        SearchService service = new SearchService(
                embeddingService,
                embeddingValidator,
                vectorRepository,
                new SearchProperties(SearchProperties.DEFAULT_PROFILE),
                new CompositeSearchProfile(),
                expansion);
        return new IndexedCorpus(
                List.copyOf(chunks),
                Map.copyOf(byChunkId),
                Map.copyOf(ownerIds),
                vectorRepository,
                service,
                boundary,
                constructionMs,
                indexingWallMs,
                millis(embedded.elapsedNanos()),
                chunks.stream().filter(FixedChunk::active).count(),
                chunks.stream().filter(value -> !value.active()).count());
    }

    QueryExecution query(
            IndexedCorpus corpus,
            SearchV3MinimalShadowDataset.RuntimeQuery query) {
        Long ownerId = corpus.ownerIds().get(query.userBundleId());
        if (ownerId == null) {
            throw new IllegalArgumentException("unknown query owner: " + query.userBundleId());
        }
        EmbeddingLookup lookup = embeddingService.lookup(query.text());
        embeddingValidator.validate(lookup.embedding());

        long candidatesStarted = System.nanoTime();
        List<VectorSearchResult> candidates = corpus.vectorRepository()
                .findCareerEvidenceCandidates(ownerId, lookup.embedding());
        double candidateMs = millis(System.nanoTime() - candidatesStarted);

        long finalStarted = System.nanoTime();
        CareerEvidenceSearchV2Response response = corpus.searchService()
                .searchCareerEvidenceV2(ownerId, query.text());
        double finalServiceMs = millis(System.nanoTime() - finalStarted);

        List<CandidateResult> candidateResults = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            VectorSearchResult result = candidates.get(index);
            FixedChunk chunk = requiredChunk(corpus, result.chunkId());
            candidateResults.add(new CandidateResult(
                    index + 1, stableChunkId(chunk), result.score(), sourceSpan(chunk)));
        }

        List<FinalResult> finalResults = new ArrayList<>();
        for (int index = 0; index < response.results().size(); index++) {
            CareerEvidenceSearchResponse result = response.results().get(index);
            FixedChunk selected = requiredChunk(corpus, result.chunkId());
            FixedChunk evidence = requiredChunk(corpus, result.evidenceChunkId());
            SourceSpan evidenceChunkSpan = sourceSpan(evidence);
            SourceSpan snippetSpan = snippetSpan(evidence, result.snippet());
            finalResults.add(new FinalResult(
                    index + 1,
                    stableChunkId(selected),
                    result.score(),
                    sourceSpan(selected),
                    stableChunkId(evidence),
                    evidenceChunkSpan,
                    result.snippet(),
                    snippetSpan));
        }

        boolean ownerLeakage = java.util.stream.Stream.concat(
                        candidateResults.stream().map(value -> value.span()),
                        finalResults.stream().map(value -> value.displaySpan()))
                .anyMatch(span -> !query.userBundleId().equals(span.userBundleId()));
        boolean inactiveLeakage = java.util.stream.Stream.concat(
                        candidates.stream().map(VectorSearchResult::chunkId),
                        response.results().stream().flatMap(result -> java.util.stream.Stream.of(
                                result.chunkId(), result.evidenceChunkId())))
                .map(id -> requiredChunk(corpus, id))
                .anyMatch(value -> !value.active());
        return new QueryExecution(
                new QueryRun(
                        response.state().name(),
                        lookup.elapsedMs(),
                        candidateMs,
                        finalServiceMs,
                        lookup.elapsedMs() + candidateMs,
                        lookup.elapsedMs() + finalServiceMs,
                        List.copyOf(candidateResults),
                        List.copyOf(finalResults),
                        ownerLeakage,
                        inactiveLeakage),
                lookup.embedding());
    }

    private FixedChunk requiredChunk(IndexedCorpus corpus, Long id) {
        FixedChunk value = corpus.byChunkId().get(id);
        if (value == null) {
            throw new IllegalStateException("Production V2 returned an unknown runtime chunk ID");
        }
        return value;
    }

    private SourceSpan snippetSpan(FixedChunk chunk, String snippet) {
        String value = Objects.requireNonNullElse(snippet, "");
        int local = chunk.content().indexOf(value);
        if (value.isBlank() || local < 0) {
            throw new IllegalStateException("Production snippet is not an exact selected source substring");
        }
        int localStart = chunk.content().codePointCount(0, local);
        int localEnd = chunk.content().codePointCount(0, local + value.length());
        return new SourceSpan(
                chunk.userBundleId(), chunk.documentFixtureId(), chunk.versionFixtureId(),
                chunk.sourcePath(), chunk.page(), chunk.codePointStart() + localStart,
                chunk.codePointStart() + localEnd, value,
                SearchV3MinimalShadowDataset.sha256(value));
    }

    private SourceSpan sourceSpan(FixedChunk chunk) {
        return new SourceSpan(
                chunk.userBundleId(), chunk.documentFixtureId(), chunk.versionFixtureId(),
                chunk.sourcePath(), chunk.page(), chunk.codePointStart(), chunk.codePointEnd(),
                chunk.content(), SearchV3MinimalShadowDataset.sha256(chunk.content()));
    }

    private String stableChunkId(FixedChunk chunk) {
        return "FIXED|" + chunk.versionFixtureId() + "|" + chunk.chunkNo();
    }

    private List<ChunkRange> splitRanges(String source) {
        List<ChunkRange> result = new ArrayList<>();
        int start = 0;
        int chunkNo = 1;
        while (start < source.length()) {
            int end = Math.min(start + MAX_CHUNK_LENGTH, source.length());
            String raw = source.substring(start, end);
            String content = raw.strip();
            if (!content.isBlank()) {
                int localStart = raw.indexOf(content);
                int charStart = start + localStart;
                int charEnd = charStart + content.length();
                result.add(new ChunkRange(
                        chunkNo++,
                        content,
                        source.codePointCount(0, charStart),
                        source.codePointCount(0, charEnd)));
            }
            if (end == source.length()) {
                break;
            }
            start = end - OVERLAP;
        }
        return List.copyOf(result);
    }

    private Map<String, Long> stableIds(List<String> keys) {
        Map<String, Long> values = new LinkedHashMap<>();
        AtomicLong next = new AtomicLong(1L);
        keys.forEach(key -> values.put(key, next.getAndIncrement()));
        return Map.copyOf(values);
    }

    private static double cosine(float[] left, float[] right) {
        if (left.length != right.length) {
            throw new IllegalArgumentException("cosine vectors differ in dimension");
        }
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int index = 0; index < left.length; index++) {
            dot += (double) left[index] * right[index];
            leftNorm += (double) left[index] * left[index];
            rightNorm += (double) right[index] * right[index];
        }
        if (leftNorm == 0.0d || rightNorm == 0.0d) {
            throw new IllegalArgumentException("cosine vector has zero norm");
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private static float[] parseVector(String literal) {
        if (literal == null || literal.length() < 2 || literal.charAt(0) != '['
                || literal.charAt(literal.length() - 1) != ']') {
            throw new IllegalArgumentException("invalid pgvector literal at evaluation boundary");
        }
        String body = literal.substring(1, literal.length() - 1);
        String[] tokens = body.split(",");
        float[] result = new float[tokens.length];
        for (int index = 0; index < tokens.length; index++) {
            result[index] = Float.parseFloat(tokens[index]);
        }
        return result;
    }

    private static double millis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    private final class CachingEmbeddingService implements EmbeddingService {

        private final OllamaBgeM3EmbeddingClient client;
        private final Map<String, EmbeddingLookup> cache = new HashMap<>();

        private CachingEmbeddingService(OllamaBgeM3EmbeddingClient client) {
            this.client = client;
        }

        @Override
        public float[] embed(String text) {
            return lookup(text).embedding().clone();
        }

        private EmbeddingLookup lookup(String text) {
            EmbeddingLookup existing = cache.get(text);
            if (existing != null) {
                return new EmbeddingLookup(existing.embedding().clone(), 0.0d, true);
            }
            OllamaBgeM3EmbeddingClient.EmbeddingBatch batch = client.embedOne(text);
            EmbeddingLookup value = new EmbeddingLookup(
                    batch.embeddings().get(0).clone(), millis(batch.elapsedNanos()), false);
            cache.put(text, value);
            return new EmbeddingLookup(value.embedding().clone(), value.elapsedMs(), false);
        }
    }

    private static final class EvaluationJdbcBoundary {

        private final Map<String, Long> ownerIds;
        private final Map<Long, FixedChunk> byChunkId;
        private final Map<String, List<FixedChunk>> activeByOwner;
        private final JdbcTemplate jdbc;

        private EvaluationJdbcBoundary(
                Map<String, Long> ownerIds,
                Map<Long, FixedChunk> byChunkId,
                Map<String, List<FixedChunk>> activeByOwner) {
            this.ownerIds = ownerIds;
            this.byChunkId = byChunkId;
            this.activeByOwner = activeByOwner;
            this.jdbc = Mockito.mock(JdbcTemplate.class, invocation -> {
                String method = invocation.getMethod().getName();
                if ("query".equals(method)) {
                    return query(invocation.getArguments());
                }
                if ("queryForObject".equals(method)) {
                    return queryForObject(invocation.getArguments());
                }
                return Answers.RETURNS_DEFAULTS.answer(invocation);
            });
        }

        private JdbcTemplate jdbcTemplate() {
            return jdbc;
        }

        private Object query(Object[] invocationArguments) {
            String sql = String.valueOf(invocationArguments[0]);
            List<Object> arguments = flatten(invocationArguments, 2);
            if (sql.contains("ORDER BY chunk.chunk_no")) {
                Long ownerId = ((Number) arguments.get(0)).longValue();
                Long documentId = ((Number) arguments.get(3)).longValue();
                Long versionId = ((Number) arguments.get(4)).longValue();
                return byChunkId.values().stream()
                        .filter(FixedChunk::active)
                        .filter(value -> value.ownerId().equals(ownerId)
                                && value.documentId().equals(documentId)
                                && value.versionId().equals(versionId))
                        .sorted(Comparator.comparingInt(FixedChunk::chunkNo)
                                .thenComparingLong(FixedChunk::chunkId))
                        .map(value -> new EvidenceChunk(
                                value.chunkId(), value.chunkNo(), ChunkSourceType.TEXT_CHUNK,
                                value.chunkNo(), "텍스트 구간 " + value.chunkNo(), value.content()))
                        .toList();
            }
            if (!sql.contains("document_title")) {
                throw new IllegalStateException("unapproved Production JDBC query in PRZ-032 adapter");
            }
            float[] queryVector = parseVector(String.valueOf(arguments.get(0)));
            Long ownerId = ((Number) arguments.get(1)).longValue();
            String owner = ownerIds.entrySet().stream()
                    .filter(value -> value.getValue().equals(ownerId))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("unknown Production JDBC owner"));
            List<FixedChunk> eligible = new ArrayList<>(activeByOwner.getOrDefault(owner, List.of()));
            if (sql.contains("regexp_replace(chunk.content, ',', '', 'g')")) {
                List<String> patterns = arguments.subList(4, arguments.size() - 1).stream()
                        .map(String::valueOf).toList();
                eligible.removeIf(chunk -> patterns.stream().noneMatch(pattern ->
                        Pattern.compile(pattern).matcher(chunk.content().replace(",", "")).find()));
            }
            int limit = sql.contains("LIMIT 1") ? 1 : sql.contains("LIMIT 5") ? 5 : 20;
            return eligible.stream()
                    .map(chunk -> result(chunk, queryVector))
                    .sorted(Comparator.comparingDouble(VectorSearchResult::distance)
                            .thenComparing(VectorSearchResult::chunkId))
                    .limit(limit)
                    .toList();
        }

        private Object queryForObject(Object[] invocationArguments) {
            String sql = String.valueOf(invocationArguments[0]);
            if (!sql.contains("ACTIVE_IDENTIFIER_EXISTS_SQL") && !sql.contains("SELECT EXISTS")) {
                return Answers.RETURNS_DEFAULTS;
            }
            List<Object> arguments = flatten(invocationArguments, 2);
            Long ownerId = ((Number) arguments.get(0)).longValue();
            String pattern = String.valueOf(arguments.get(arguments.size() - 1));
            Pattern boundary = Pattern.compile(pattern);
            return activeByOwner.values().stream().flatMap(List::stream)
                    .filter(value -> value.ownerId().equals(ownerId))
                    .map(value -> (value.documentTitle() + " " + value.content()).toLowerCase(Locale.ROOT)
                            .replaceAll("spring[\\s_-]*boot", "springboot"))
                    .anyMatch(value -> boundary.matcher(value).find());
        }

        private VectorSearchResult result(FixedChunk chunk, float[] queryVector) {
            double score = cosine(queryVector, chunk.embedding());
            return new VectorSearchResult(
                    chunk.chunkId(), chunk.documentId(), chunk.versionId(), chunk.documentTitle(),
                    chunk.versionNumber(), chunk.chunkNo(), null, ChunkSourceType.TEXT_CHUNK,
                    chunk.chunkNo(), "텍스트 구간 " + chunk.chunkNo(), chunk.content(),
                    1.0d - score, score);
        }

        private List<Object> flatten(Object[] values, int start) {
            if (values.length == start + 1 && values[start] instanceof Object[] nested) {
                return Arrays.asList(nested);
            }
            return Arrays.asList(Arrays.copyOfRange(values, start, values.length));
        }
    }

    record IndexedCorpus(
            List<FixedChunk> chunks,
            Map<Long, FixedChunk> byChunkId,
            Map<String, Long> ownerIds,
            VectorSearchRepository vectorRepository,
            SearchService searchService,
            EvaluationJdbcBoundary boundary,
            double constructionMs,
            double indexingWallMs,
            double embeddingMs,
            long activeChunkCount,
            long inactiveChunkCount) {

        IndexedCorpus {
            chunks = List.copyOf(chunks);
            byChunkId = Map.copyOf(byChunkId);
            ownerIds = Map.copyOf(ownerIds);
        }
    }

    record FixedChunk(
            Long chunkId,
            Long ownerId,
            Long documentId,
            Long versionId,
            String userBundleId,
            String documentFixtureId,
            String versionFixtureId,
            String documentTitle,
            int versionNumber,
            boolean active,
            int chunkNo,
            String content,
            String sourcePath,
            Integer page,
            int codePointStart,
            int codePointEnd,
            String documentSourceSha256,
            float[] embedding) {

        FixedChunk {
            embedding = embedding.clone();
        }
    }

    record SourceSpan(
            String userBundleId,
            String documentId,
            String versionId,
            String sourcePath,
            Integer page,
            int codePointStart,
            int codePointEnd,
            String sourceText,
            String sourceTextSha256) {
    }

    record CandidateResult(int rank, String candidateId, double cosineScore, SourceSpan span) {
    }

    record FinalResult(
            int rank,
            String selectedCandidateId,
            double score,
            SourceSpan selectedSpan,
            String evidenceCandidateId,
            SourceSpan evidenceChunkSpan,
            String snippet,
            SourceSpan displaySpan) {
    }

    record QueryRun(
            String responseState,
            double queryEmbeddingMs,
            double candidateOnlyMs,
            double finalServiceOnlyMs,
            double candidateTotalMs,
            double finalTotalMs,
            List<CandidateResult> candidates,
            List<FinalResult> finalResults,
            boolean ownerLeakage,
            boolean inactiveVersionLeakage) {

        QueryRun {
            candidates = List.copyOf(candidates);
            finalResults = List.copyOf(finalResults);
        }
    }

    record QueryExecution(QueryRun output, float[] queryEmbedding) {

        QueryExecution {
            queryEmbedding = queryEmbedding.clone();
        }
    }

    private record ChunkDraft(
            long chunkId,
            Long ownerId,
            Long documentId,
            Long versionId,
            SearchV3MinimalShadowDataset.RuntimeDocument document,
            ChunkRange range) {
    }

    private record ChunkRange(int chunkNo, String content, int codePointStart, int codePointEnd) {
    }

    private record EmbeddingLookup(float[] embedding, double elapsedMs, boolean cacheHit) {

        EmbeddingLookup {
            embedding = embedding.clone();
        }
    }
}
