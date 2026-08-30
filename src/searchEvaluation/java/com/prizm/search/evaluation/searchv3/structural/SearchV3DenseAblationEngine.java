package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.service.TextChunk;
import com.prizm.ingestion.service.TextChunker;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.AspectRequirement;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.DatasetSlice;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.ExpectedEvidence;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.GoldParent;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.GoldSpan;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.GoldUnit;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.Query;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.SourceDocument;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.Split;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.UserBundle;
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
import java.util.function.Function;
import java.util.stream.Collectors;

/** Runs the A/B2/B3 raw Dense comparison without a database, reranking, or query policy. */
final class SearchV3DenseAblationEngine {

    static final String FIXED_PROFILE = "A_FIXED_800_OVERLAP_120_BGE_M3_DENSE";
    static final String STRUCTURAL_PROFILE =
            "B2_STRUCTURAL_CHILD_CONTEXT_ONLY_HEADING_BGE_M3_DENSE";
    static final String PASSAGE_PROFILE =
            "B3_STRUCTURAL_RETRIEVAL_PASSAGE_BGE_M3_DENSE";
    static final String PARENT_CONTEXT_PROFILE =
            "C1_STRUCTURAL_RETRIEVAL_PASSAGE_HEADING_PATH_V1_BGE_M3_DENSE";
    private static final int FIXED_MAX_CHARACTERS = 800;
    private static final int FIXED_OVERLAP_CHARACTERS = 120;
    private static final List<Integer> CUTOFFS = List.of(5, 10, 20, 50);
    private static final List<String> LENGTH_BUCKETS = List.of("01-10", "11-20", "21-40", "41-80", "81+");

    private final StructuralBlockParser parser = new StructuralBlockParser();
    private final StructuralEvidenceChildBuilder childBuilder = new StructuralEvidenceChildBuilder();
    private final StructuralRetrievalPassageBuilder passageBuilder = new StructuralRetrievalPassageBuilder();
    private final StructuralHeadingPathContextBuilder contextBuilder =
            new StructuralHeadingPathContextBuilder();
    private final TextChunker productionTextChunker;

    SearchV3DenseAblationEngine() {
        IngestionProperties properties = new IngestionProperties();
        properties.setMaxChunkLength(800);
        properties.setOverlap(120);
        this.productionTextChunker = new TextChunker(properties);
    }

    ExperimentReport run(
            List<DatasetSlice> slices,
            OllamaBgeM3EmbeddingClient embeddingClient,
            OllamaBgeM3EmbeddingClient.ModelMetadata modelMetadata) {
        return run(slices, embeddingClient, modelMetadata, "PRZ-026-PHASE-1-RETRIEVAL-PASSAGE");
    }

    ExperimentReport run(
            List<DatasetSlice> slices,
            OllamaBgeM3EmbeddingClient embeddingClient,
            OllamaBgeM3EmbeddingClient.ModelMetadata modelMetadata,
            String phase) {
        Set<String> datasetVersions = slices.stream()
                .map(DatasetSlice::datasetVersion)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (datasetVersions.size() != 1) {
            throw new IllegalArgumentException("One A/B run cannot mix Search V3 dataset versions");
        }
        embeddingClient.embedOne("PRIZM Search V3 structural evaluation warmup");

        List<SplitRun> splitRuns = new ArrayList<>();
        for (DatasetSlice slice : slices) {
            CandidateBuild fixedBuild = buildFixedCandidates(slice);
            CandidateBuild structuralBuild = buildStructuralCandidates(slice);
            PassageCandidateBuild passageBuild = buildPassageCandidates(slice, structuralBuild);
            assertSameEvaluationInputs(slice, fixedBuild, structuralBuild, passageBuild.candidateBuild());

            PreparedProfile fixed = prepare(fixedBuild, embeddingClient);
            PreparedProfile structural = prepare(structuralBuild, embeddingClient);
            PreparedProfile passage = prepare(passageBuild.candidateBuild(), embeddingClient);
            List<QueryResult> queryResults = evaluateQueries(slice, fixed, structural, passage, embeddingClient);
            splitRuns.add(new SplitRun(
                    slice, fixed, structural, passage, passageBuild.passageStats(), queryResults));
        }

        List<QueryResult> allQueryResults = splitRuns.stream()
                .flatMap(run -> run.queryResults().stream())
                .toList();
        ProfileCorpusStats fixedCorpus = combineCorpusStats(
                FIXED_PROFILE, splitRuns.stream().map(run -> run.fixed().corpusStats()).toList());
        ProfileCorpusStats structuralCorpus = combineCorpusStats(
                STRUCTURAL_PROFILE, splitRuns.stream().map(run -> run.structural().corpusStats()).toList());
        ProfileCorpusStats passageCorpus = combineCorpusStats(
                PASSAGE_PROFILE, splitRuns.stream().map(run -> run.passage().corpusStats()).toList());
        PassageCorpusStats passageStats = combinePassageStats(
                splitRuns.stream().map(SplitRun::passageStats).toList());
        ProfileLatency fixedLatency = combineLatency(
                FIXED_PROFILE, splitRuns.stream().map(run -> run.fixed().latency()).toList(),
                allQueryResults.stream().map(result -> result.fixed().totalLatencyMs()).toList(),
                allQueryResults.stream().map(result -> result.fixed().rankingLatencyMs()).toList());
        ProfileLatency structuralLatency = combineLatency(
                STRUCTURAL_PROFILE, splitRuns.stream().map(run -> run.structural().latency()).toList(),
                allQueryResults.stream().map(result -> result.structural().totalLatencyMs()).toList(),
                allQueryResults.stream().map(result -> result.structural().rankingLatencyMs()).toList());
        ProfileLatency passageLatency = combineLatency(
                PASSAGE_PROFILE, splitRuns.stream().map(run -> run.passage().latency()).toList(),
                allQueryResults.stream().map(result -> result.passage().totalLatencyMs()).toList(),
                allQueryResults.stream().map(result -> result.passage().rankingLatencyMs()).toList());

        AggregateComparison queryMicro = aggregateComparison(allQueryResults);
        AggregateComparison userMacro = macroComparison(allQueryResults, QueryResult::userBundleId);
        Map<String, AggregateComparison> splitMetrics = grouped(
                allQueryResults, result -> result.split().manifestName());
        Map<String, AggregateComparison> professionMetrics = grouped(
                allQueryResults, QueryResult::professionGroup);
        Map<String, AggregateComparison> languageMetrics = grouped(allQueryResults, QueryResult::language);
        Map<String, Long> queryEmbeddingLatency = latencySummary(
                allQueryResults.stream().map(QueryResult::queryEmbeddingLatencyMs).toList());
        Map<String, LengthBucketStats> structuralLengthBuckets =
                lengthBucketStats(splitRuns, allQueryResults);
        long structuralHeadingOnlyRank1 = allQueryResults.stream()
                .map(result -> result.structural().rawDenseRanking())
                .filter(ranking -> !ranking.isEmpty())
                .filter(ranking -> StructuralBlockType.HEADING.name().equals(ranking.get(0).sourceBlockType()))
                .count();
        long passageHeadingOnlyRank1 = allQueryResults.stream()
                .map(result -> result.passage().rawDenseRanking())
                .filter(ranking -> !ranking.isEmpty())
                .filter(ranking -> StructuralBlockType.HEADING.name().equals(ranking.get(0).sourceBlockType()))
                .count();

        Map<String, String> splitManifests = new LinkedHashMap<>();
        slices.forEach(slice -> splitManifests.put(
                slice.split().manifestName(), slice.manifestCombinedSha256()));
        String decision = decision(
                structuralCorpus,
                passageCorpus,
                passageStats,
                queryMicro,
                professionMetrics,
                languageMetrics);
        return new ExperimentReport(
                3,
                phase,
                Instant.now().toString(),
                datasetVersions.iterator().next(),
                "e5012fd4949b05f4b8a136186ddefb60046985f8",
                "DEPENDS_ON_PRZ_025",
                Map.copyOf(splitManifests),
                modelMetadata,
                new ComparisonContract(
                        800,
                        120,
                        StructuralEvidenceChildBuilder.DEFAULT_MAX_CHILD_CODE_POINTS,
                        StructuralRetrievalPassageBuilder.DEFAULT_MIN_TARGET_CODE_POINTS,
                        StructuralRetrievalPassageBuilder.DEFAULT_TARGET_MAX_CODE_POINTS,
                        StructuralRetrievalPassageBuilder.DEFAULT_ABSOLUTE_MAX_CODE_POINTS,
                        OllamaBgeM3EmbeddingClient.MODEL,
                        OllamaBgeM3EmbeddingClient.DIMENSIONS,
                        OllamaBgeM3EmbeddingClient.SIMILARITY,
                        "RAW_DENSE_ONLY",
                        "USER_BUNDLE_ACTIVE_DOCUMENTS",
                        "SAME_QUERY_VECTOR_PER_A_B2_B3",
                        "EVIDENCE_PARENT_AND_HEADING_CONTEXT_NOT_RUN",
                        "SOURCE_TABLE_HEADER_CONTEXT_EXCEPTION_ACTIVE"),
                fixedCorpus,
                structuralCorpus,
                passageCorpus,
                passageStats,
                fixedLatency,
                structuralLatency,
                passageLatency,
                queryEmbeddingLatency,
                queryMicro,
                userMacro,
                Map.copyOf(splitMetrics),
                Map.copyOf(professionMetrics),
                Map.copyOf(languageMetrics),
                Map.copyOf(structuralLengthBuckets),
                structuralHeadingOnlyRank1,
                passageHeadingOnlyRank1,
                List.copyOf(allQueryResults),
                false,
                false,
                "NOT_RUN",
                decision);
    }

    ParentContextExperimentReport runParentContext(
            List<DatasetSlice> slices,
            OllamaBgeM3EmbeddingClient embeddingClient,
            OllamaBgeM3EmbeddingClient.ModelMetadata modelMetadata,
            String phase,
            String inputFreezeCommit) {
        Set<String> datasetVersions = slices.stream()
                .map(DatasetSlice::datasetVersion)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (datasetVersions.size() != 1) {
            throw new IllegalArgumentException("One B3/C1 run cannot mix Search V3 dataset versions");
        }
        embeddingClient.embedOne("PRIZM Search V3 parent context evaluation warmup");

        List<ParentContextSplitRun> splitRuns = new ArrayList<>();
        for (DatasetSlice slice : slices) {
            CandidateBuild structural = buildStructuralCandidates(slice);
            PassageCandidateBuild passageBuild = buildPassageCandidates(slice, structural);
            ContextCandidateBuild contextBuild = buildParentContextCandidates(slice, passageBuild);
            assertParentContextParity(passageBuild.candidateBuild(), contextBuild.candidateBuild());

            PreparedProfile passage = prepare(passageBuild.candidateBuild(), embeddingClient);
            PreparedProfile context = prepare(contextBuild.candidateBuild(), embeddingClient);
            if (passage.corpusStats().embeddingCount() != context.corpusStats().embeddingCount()) {
                throw new IllegalStateException("B3/C1 document embedding count parity failed");
            }
            List<ParentContextQueryResult> queryResults = evaluateParentContextQueries(
                    slice, passage, context, embeddingClient);
            splitRuns.add(new ParentContextSplitRun(
                    slice,
                    passage,
                    context,
                    passageBuild.passageStats(),
                    contextBuild.contextStats(),
                    queryResults));
        }

        List<ParentContextQueryResult> allQueries = splitRuns.stream()
                .flatMap(run -> run.queryResults().stream())
                .toList();
        ProfileCorpusStats passageCorpus = combineCorpusStats(
                PASSAGE_PROFILE, splitRuns.stream().map(run -> run.passage().corpusStats()).toList());
        ProfileCorpusStats contextCorpus = combineCorpusStats(
                PARENT_CONTEXT_PROFILE, splitRuns.stream().map(run -> run.context().corpusStats()).toList());
        PassageCorpusStats passageStats = combinePassageStats(
                splitRuns.stream().map(ParentContextSplitRun::passageStats).toList());
        ParentContextCorpusStats contextStats = combineParentContextStats(
                splitRuns.stream().map(ParentContextSplitRun::contextStats).toList());
        ProfileLatency passageLatency = combineLatency(
                PASSAGE_PROFILE,
                splitRuns.stream().map(run -> run.passage().latency()).toList(),
                allQueries.stream().map(result -> result.passage().totalLatencyMs()).toList(),
                allQueries.stream().map(result -> result.passage().rankingLatencyMs()).toList());
        ProfileLatency contextLatency = combineLatency(
                PARENT_CONTEXT_PROFILE,
                splitRuns.stream().map(run -> run.context().latency()).toList(),
                allQueries.stream().map(result -> result.context().totalLatencyMs()).toList(),
                allQueries.stream().map(result -> result.context().rankingLatencyMs()).toList());

        Map<String, String> splitManifests = new LinkedHashMap<>();
        slices.forEach(slice -> splitManifests.put(
                slice.split().manifestName(), slice.manifestCombinedSha256()));
        return new ParentContextExperimentReport(
                1,
                phase,
                Instant.now().toString(),
                datasetVersions.iterator().next(),
                inputFreezeCommit,
                "DEPENDS_ON_PRZ_025",
                Map.copyOf(splitManifests),
                modelMetadata,
                new ParentContextContract(
                        PASSAGE_PROFILE,
                        PARENT_CONTEXT_PROFILE,
                        StructuralHeadingPathContextBuilder.POLICY,
                        StructuralHeadingPathContextBuilder.MAX_HEADING_DEPTH,
                        StructuralHeadingPathContextBuilder.MAX_CONTEXT_CODE_POINTS,
                        OllamaBgeM3EmbeddingClient.MODEL,
                        OllamaBgeM3EmbeddingClient.DIMENSIONS,
                        OllamaBgeM3EmbeddingClient.SIMILARITY,
                        "RAW_DENSE_ONLY",
                        "SAME_QUERY_VECTOR_PER_B3_C1",
                        "SOURCE_RANGE_ONLY_CONTEXT_NEVER_GOLD",
                        "PARENT_DENSE_NOT_RUN"),
                passageCorpus,
                contextCorpus,
                passageStats,
                contextStats,
                passageLatency,
                contextLatency,
                latencySummary(allQueries.stream().map(ParentContextQueryResult::queryEmbeddingLatencyMs).toList()),
                parentContextAggregate(allQueries),
                parentContextMacro(allQueries, ParentContextQueryResult::userBundleId),
                parentContextGrouped(allQueries, result -> result.split().manifestName()),
                parentContextGrouped(allQueries, ParentContextQueryResult::professionGroup),
                parentContextGrouped(allQueries, ParentContextQueryResult::language),
                List.copyOf(allQueries),
                false,
                false,
                "NOT_RUN");
    }

    ContextCandidateBuild buildParentContextCandidates(
            DatasetSlice slice,
            PassageCandidateBuild passageBuild) {
        long started = System.nanoTime();
        Map<String, CandidateSpec> baseById = passageBuild.candidateBuild().candidates().stream()
                .collect(Collectors.toMap(
                        CandidateSpec::candidateId,
                        Function.identity(),
                        (left, right) -> {
                            throw new IllegalStateException("duplicate B3 passage ID");
                        },
                        LinkedHashMap::new));
        List<CandidateSpec> candidates = new ArrayList<>();
        List<ContextualRetrievalPassage> contextualPassages = new ArrayList<>();
        for (SourceDocument document : activeDocuments(slice)) {
            List<StructuralBlock> blocks = parser.parse(document.structuralDocument());
            List<RetrievalPassage> documentPassages = passageBuild.passages().stream()
                    .filter(passage -> passage.documentId().equals(document.documentId()))
                    .filter(passage -> passage.versionId().equals(document.versionId()))
                    .toList();
            List<ContextualRetrievalPassage> contextual = contextBuilder.build(blocks, documentPassages);
            contextualPassages.addAll(contextual);
            for (ContextualRetrievalPassage value : contextual) {
                CandidateSpec base = baseById.get(value.basePassage().passageId());
                if (base == null) {
                    throw new IllegalStateException("C1 context has no matching B3 passage candidate");
                }
                candidates.add(new CandidateSpec(
                        PARENT_CONTEXT_PROFILE,
                        base.candidateId(),
                        base.userBundleId(),
                        base.documentId(),
                        base.versionId(),
                        value.sourceText(),
                        value.contextText(),
                        value.retrievalText(),
                        base.sourceBlockType(),
                        base.parentAnnotationCandidateId(),
                        base.ranges(),
                        base.sourceBlockIds(),
                        base.contextBlockIds(),
                        value.contextSourceBlockIds(),
                        base.evidenceChildren()));
            }
        }
        long contextConstructionNanos = System.nanoTime() - started;
        long totalConstructionNanos = passageBuild.candidateBuild().constructionNanos() + contextConstructionNanos;
        ProfileCorpusStats corpus = copyCorpusStats(
                PARENT_CONTEXT_PROFILE,
                passageBuild.candidateBuild().corpusStats(),
                totalConstructionNanos);
        CandidateBuild candidateBuild = new CandidateBuild(
                PARENT_CONTEXT_PROFILE,
                slice,
                List.copyOf(candidates),
                totalConstructionNanos,
                corpus);
        assertParentContextParity(passageBuild.candidateBuild(), candidateBuild);
        return new ContextCandidateBuild(
                candidateBuild,
                parentContextStats(contextualPassages, contextConstructionNanos),
                List.copyOf(contextualPassages));
    }

    private ProfileCorpusStats copyCorpusStats(
            String profile,
            ProfileCorpusStats base,
            long constructionNanos) {
        return new ProfileCorpusStats(
                profile,
                base.candidateCount(),
                0,
                base.minimumCodePointLength(),
                base.averageCodePointLength(),
                base.maximumCodePointLength(),
                base.activeGoldUnitCount(),
                base.fragmentedGoldUnitCount(),
                base.fragmentationRate(),
                base.contaminatedCandidateCount(),
                base.contaminationRate(),
                base.duplicateGroupMappingCount(),
                base.goldGroupMappingCount(),
                base.duplicateRatio(),
                base.tableHeaderContextChildCount(),
                base.headingOnlyCandidateCount(),
                base.contextOnlyHeadingCount(),
                base.veryShortCandidateCount(),
                base.lengthBucketCandidateCount(),
                base.goldParentCountPerCandidateDistribution(),
                nanosToMillis(constructionNanos));
    }

    private ParentContextCorpusStats parentContextStats(
            List<ContextualRetrievalPassage> passages,
            long constructionNanos) {
        List<ContextualRetrievalPassage> applied = passages.stream()
                .filter(value -> !value.contextText().isBlank())
                .toList();
        List<Integer> lengths = applied.stream()
                .map(value -> value.contextText().codePointCount(0, value.contextText().length()))
                .toList();
        List<Integer> depths = applied.stream().map(value -> value.contextSourceBlockIds().size()).toList();
        Map<String, Long> depthDistribution = passages.stream().collect(Collectors.groupingBy(
                value -> Integer.toString(value.contextSourceBlockIds().size()),
                LinkedHashMap::new,
                Collectors.counting()));
        return new ParentContextCorpusStats(
                StructuralHeadingPathContextBuilder.POLICY,
                passages.size(),
                applied.size(),
                ratio(applied.size(), passages.size()),
                lengths.stream().mapToInt(Integer::intValue).min().orElse(0),
                lengths.stream().mapToInt(Integer::intValue).average().orElse(0.0d),
                lengths.stream().mapToInt(Integer::intValue).max().orElse(0),
                depths.stream().mapToInt(Integer::intValue).min().orElse(0),
                depths.stream().mapToInt(Integer::intValue).average().orElse(0.0d),
                depths.stream().mapToInt(Integer::intValue).max().orElse(0),
                Map.copyOf(depthDistribution),
                0,
                0,
                0,
                nanosToMillis(constructionNanos));
    }

    private ParentContextCorpusStats combineParentContextStats(List<ParentContextCorpusStats> values) {
        long passages = values.stream().mapToLong(ParentContextCorpusStats::passageCount).sum();
        long applied = values.stream().mapToLong(ParentContextCorpusStats::contextPassageCount).sum();
        double weightedLength = values.stream()
                .mapToDouble(value -> value.averageContextCodePointLength() * value.contextPassageCount())
                .sum();
        double weightedDepth = values.stream()
                .mapToDouble(value -> value.averageHeadingDepth() * value.contextPassageCount())
                .sum();
        Map<String, Long> depthDistribution = new LinkedHashMap<>();
        values.forEach(value -> value.headingDepthDistribution()
                .forEach((depth, count) -> depthDistribution.merge(depth, count, Long::sum)));
        return new ParentContextCorpusStats(
                StructuralHeadingPathContextBuilder.POLICY,
                passages,
                applied,
                ratio(applied, passages),
                values.stream().mapToInt(ParentContextCorpusStats::minimumContextCodePointLength).min().orElse(0),
                applied == 0 ? 0.0d : weightedLength / applied,
                values.stream().mapToInt(ParentContextCorpusStats::maximumContextCodePointLength).max().orElse(0),
                values.stream().mapToInt(ParentContextCorpusStats::minimumHeadingDepth).min().orElse(0),
                applied == 0 ? 0.0d : weightedDepth / applied,
                values.stream().mapToInt(ParentContextCorpusStats::maximumHeadingDepth).max().orElse(0),
                Map.copyOf(depthDistribution),
                values.stream().mapToLong(ParentContextCorpusStats::crossParentContextViolationCount).sum(),
                values.stream().mapToLong(ParentContextCorpusStats::sourceParityViolationCount).sum(),
                values.stream().mapToLong(ParentContextCorpusStats::evidenceChildParityViolationCount).sum(),
                values.stream().mapToDouble(ParentContextCorpusStats::contextConstructionLatencyMs).sum());
    }

    private void assertParentContextParity(CandidateBuild passage, CandidateBuild context) {
        if (passage.candidates().size() != context.candidates().size()) {
            throw new IllegalStateException("B3/C1 candidate count parity failed");
        }
        for (int index = 0; index < passage.candidates().size(); index++) {
            CandidateSpec base = passage.candidates().get(index);
            CandidateSpec contextual = context.candidates().get(index);
            boolean same = base.candidateId().equals(contextual.candidateId())
                    && base.userBundleId().equals(contextual.userBundleId())
                    && base.documentId().equals(contextual.documentId())
                    && base.versionId().equals(contextual.versionId())
                    && base.sourceText().equals(contextual.sourceText())
                    && base.sourceBlockType().equals(contextual.sourceBlockType())
                    && Objects.equals(base.parentAnnotationCandidateId(), contextual.parentAnnotationCandidateId())
                    && base.ranges().equals(contextual.ranges())
                    && base.sourceBlockIds().equals(contextual.sourceBlockIds())
                    && base.contextBlockIds().equals(contextual.contextBlockIds())
                    && base.evidenceChildren().equals(contextual.evidenceChildren());
            String expectedRetrieval = contextual.contextText().isBlank()
                    ? base.retrievalText()
                    : contextual.contextText() + "\n" + base.retrievalText();
            if (!same || !expectedRetrieval.equals(contextual.retrievalText())) {
                throw new IllegalStateException("C1 changed B3 passage identity, source, evidence, or provenance");
            }
        }
    }

    private List<ParentContextQueryResult> evaluateParentContextQueries(
            DatasetSlice slice,
            PreparedProfile passage,
            PreparedProfile context,
            OllamaBgeM3EmbeddingClient embeddingClient) {
        List<ParentContextQueryResult> results = new ArrayList<>();
        for (Query query : slice.queries()) {
            OllamaBgeM3EmbeddingClient.EmbeddingBatch queryBatch = embeddingClient.embedOne(query.text());
            float[] queryVector = queryBatch.embeddings().get(0);
            long passageStarted = System.nanoTime();
            List<RankedCandidate> passageRanking =
                    rank(queryVector, query.userBundleId(), passage.candidates(), slice);
            long passageNanos = System.nanoTime() - passageStarted;
            long contextStarted = System.nanoTime();
            List<RankedCandidate> contextRanking =
                    rank(queryVector, query.userBundleId(), context.candidates(), slice);
            long contextNanos = System.nanoTime() - contextStarted;
            QueryProfileResult passageResult = queryResult(
                    query, passageRanking, slice, queryBatch.elapsedNanos(), passageNanos);
            QueryProfileResult contextResult = queryResult(
                    query, contextRanking, slice, queryBatch.elapsedNanos(), contextNanos);
            UserBundle bundle = slice.bundles().stream()
                    .filter(value -> value.userBundleId().equals(query.userBundleId()))
                    .findFirst()
                    .orElseThrow();
            RankOutcome outcome = directRankOutcome(query, passageResult, contextResult);
            results.add(new ParentContextQueryResult(
                    query.queryId(),
                    query.userBundleId(),
                    slice.split(),
                    bundle.professionGroup(),
                    query.language(),
                    query.answerability(),
                    query.categories(),
                    query.hasDirectSupport(),
                    nanosToMillis(queryBatch.elapsedNanos()),
                    outcome.name(),
                    directRankDelta(query, passageResult, contextResult),
                    contextOnlyFalseHits(query, passageRanking, contextRanking),
                    passageResult,
                    contextResult));
        }
        return List.copyOf(results);
    }

    private RankOutcome directRankOutcome(
            Query query,
            QueryProfileResult passage,
            QueryProfileResult context) {
        if (!query.hasDirectSupport()) {
            return RankOutcome.NOT_APPLICABLE;
        }
        int delta = directRankDelta(query, passage, context);
        return delta > 0 ? RankOutcome.WIN : delta < 0 ? RankOutcome.LOSS : RankOutcome.TIE;
    }

    private int directRankDelta(
            Query query,
            QueryProfileResult passage,
            QueryProfileResult context) {
        if (!query.hasDirectSupport()) {
            return 0;
        }
        if (passage.firstDirectRank() == null || context.firstDirectRank() == null) {
            throw new IllegalStateException("source-preserving B3/C1 lost a DIRECT_SUPPORT rank");
        }
        return passage.firstDirectRank() - context.firstDirectRank();
    }

    List<ContextOnlyFalseHit> contextOnlyFalseHits(
            Query query,
            List<RankedCandidate> passageRanking,
            List<RankedCandidate> contextRanking) {
        if (!query.hasDirectSupport()) {
            return List.of();
        }
        Set<String> directUnitIds = query.allExpectedEvidence().stream()
                .filter(expected -> "DIRECT_SUPPORT".equals(expected.supportRelation()))
                .map(ExpectedEvidence::evidenceUnitId)
                .collect(Collectors.toSet());
        Integer firstDirect = contextRanking.stream()
                .filter(candidate -> candidate.coveredUnitIds().stream().anyMatch(directUnitIds::contains))
                .map(RankedCandidate::rank)
                .findFirst()
                .orElse(null);
        if (firstDirect == null || firstDirect == 1) {
            return List.of();
        }
        Map<String, Integer> passageRankById = passageRanking.stream().collect(Collectors.toMap(
                RankedCandidate::candidateId, RankedCandidate::rank));
        return contextRanking.stream()
                .filter(candidate -> candidate.rank() < firstDirect)
                .filter(candidate -> !candidate.contextText().isBlank())
                .filter(candidate -> candidate.coveredUnitIds().stream().noneMatch(directUnitIds::contains))
                .filter(candidate -> passageRankById.getOrDefault(candidate.candidateId(), Integer.MAX_VALUE)
                        > candidate.rank())
                .map(candidate -> new ContextOnlyFalseHit(
                        query.queryId(),
                        candidate.candidateId(),
                        passageRankById.getOrDefault(candidate.candidateId(), Integer.MAX_VALUE),
                        candidate.rank(),
                        firstDirect,
                        candidate.contextText(),
                        candidate.evidenceChildIds()))
                .toList();
    }

    private ParentContextAggregateComparison parentContextAggregate(List<ParentContextQueryResult> values) {
        return new ParentContextAggregateComparison(
                aggregateParentContext(values, ParentContextQueryResult::passage),
                aggregateParentContext(values, ParentContextQueryResult::context));
    }

    private ParentContextAggregateComparison parentContextMacro(
            List<ParentContextQueryResult> values,
            Function<ParentContextQueryResult, String> key) {
        Map<String, List<ParentContextQueryResult>> groups = values.stream().collect(Collectors.groupingBy(
                key, LinkedHashMap::new, Collectors.toList()));
        List<AggregateMetrics> passage = groups.values().stream()
                .map(group -> aggregateParentContext(group, ParentContextQueryResult::passage))
                .filter(metric -> metric.directQueryCount() > 0)
                .toList();
        List<AggregateMetrics> context = groups.values().stream()
                .map(group -> aggregateParentContext(group, ParentContextQueryResult::context))
                .filter(metric -> metric.directQueryCount() > 0)
                .toList();
        return new ParentContextAggregateComparison(averageAggregates(passage), averageAggregates(context));
    }

    private Map<String, ParentContextAggregateComparison> parentContextGrouped(
            List<ParentContextQueryResult> values,
            Function<ParentContextQueryResult, String> key) {
        Map<String, List<ParentContextQueryResult>> groups = values.stream().collect(Collectors.groupingBy(
                key, LinkedHashMap::new, Collectors.toList()));
        Map<String, ParentContextAggregateComparison> result = new LinkedHashMap<>();
        groups.forEach((name, group) -> result.put(name, parentContextAggregate(group)));
        return Map.copyOf(result);
    }

    private AggregateMetrics aggregateParentContext(
            List<ParentContextQueryResult> values,
            Function<ParentContextQueryResult, QueryProfileResult> profile) {
        List<QueryProfileResult> direct = values.stream()
                .filter(ParentContextQueryResult::directSupport)
                .map(profile)
                .toList();
        Map<Integer, Double> recall = new LinkedHashMap<>();
        Map<Integer, Double> unit = new LinkedHashMap<>();
        Map<Integer, Double> group = new LinkedHashMap<>();
        Map<Integer, Double> parent = new LinkedHashMap<>();
        for (int cutoff : CUTOFFS) {
            recall.put(cutoff, direct.stream()
                    .mapToDouble(value -> value.recallAtK().get(cutoff) ? 1.0d : 0.0d)
                    .average().orElse(0.0d));
            unit.put(cutoff, direct.stream().mapToDouble(value -> value.unitCoverageAtK().get(cutoff))
                    .average().orElse(0.0d));
            group.put(cutoff, direct.stream().mapToDouble(value -> value.groupCoverageAtK().get(cutoff))
                    .average().orElse(0.0d));
            parent.put(cutoff, direct.stream().mapToDouble(value -> value.parentCoverageAtK().get(cutoff))
                    .average().orElse(0.0d));
        }
        return new AggregateMetrics(
                direct.size(),
                values.size() - direct.size(),
                direct.size(),
                direct.stream().mapToDouble(value -> value.top1() ? 1.0d : 0.0d).average().orElse(0.0d),
                direct.stream().mapToDouble(QueryProfileResult::reciprocalRank).average().orElse(0.0d),
                Map.copyOf(recall),
                Map.copyOf(unit),
                Map.copyOf(group),
                Map.copyOf(parent));
    }

    CandidateBuild buildFixedCandidates(DatasetSlice slice) {
        long started = System.nanoTime();
        List<CandidateSpec> candidates = new ArrayList<>();
        for (SourceDocument document : activeDocuments(slice)) {
            String source = document.structuralDocument().sourceText();
            for (MappedTextChunk mapped : mappedProductionChunks(source)) {
                TextChunk chunk = mapped.chunk();
                CandidateRange range = range(document, mapped.exactStart(), mapped.exactEnd());
                candidates.add(new CandidateSpec(
                        FIXED_PROFILE,
                        "FIXED-%s-%04d".formatted(document.versionId(), chunk.chunkNo()),
                        document.userBundleId(),
                        document.documentId(),
                        document.versionId(),
                        chunk.content(),
                        "",
                        chunk.content(),
                        "FIXED_CHUNK",
                        null,
                        List.of(range),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));
            }
        }
        long constructionNanos = System.nanoTime() - started;
        return new CandidateBuild(
                FIXED_PROFILE,
                slice,
                List.copyOf(candidates),
                constructionNanos,
                corpusStats(FIXED_PROFILE, slice, candidates, constructionNanos, 0));
    }

    List<MappedTextChunk> mappedProductionChunks(String source) {
        List<TextChunk> chunks = productionTextChunker.split(source);
        List<MappedTextChunk> mapped = new ArrayList<>();
        int emittedIndex = 0;
        int windowStart = 0;
        while (windowStart < source.length()) {
            int windowEnd = Math.min(windowStart + FIXED_MAX_CHARACTERS, source.length());
            int exactStart = skipWhitespace(source, windowStart, windowEnd);
            int exactEnd = trimWhitespace(source, exactStart, windowEnd);
            if (exactEnd > exactStart) {
                if (emittedIndex >= chunks.size()) {
                    throw new IllegalStateException("Production TextChunker emitted fewer chunks than mapped windows");
                }
                TextChunk chunk = chunks.get(emittedIndex++);
                String exact = source.substring(exactStart, exactEnd);
                if (!exact.equals(chunk.content()) || chunk.chunkNo() != emittedIndex) {
                    throw new IllegalStateException("Production TextChunker source mapping drifted");
                }
                mapped.add(new MappedTextChunk(chunk, exactStart, exactEnd));
            }
            if (windowEnd == source.length()) {
                break;
            }
            windowStart = windowEnd - FIXED_OVERLAP_CHARACTERS;
        }
        if (emittedIndex != chunks.size()) {
            throw new IllegalStateException("Production TextChunker emitted more chunks than mapped windows");
        }
        return List.copyOf(mapped);
    }

    CandidateBuild buildStructuralCandidates(DatasetSlice slice) {
        long started = System.nanoTime();
        List<CandidateSpec> candidates = new ArrayList<>();
        long contextOnlyHeadings = 0;
        for (SourceDocument document : activeDocuments(slice)) {
            List<StructuralBlock> blocks = parser.parse(document.structuralDocument());
            contextOnlyHeadings += blocks.stream()
                    .filter(block -> block.type() == StructuralBlockType.HEADING)
                    .count();
            for (EvidenceChild child : childBuilder.build(blocks)) {
                SourceProvenance provenance = child.provenance();
                CandidateRange childRange = new CandidateRange(
                        provenance.page(),
                        provenance.lineStart(),
                        provenance.lineEnd(),
                        provenance.codePointStart(),
                        provenance.codePointEnd());
                candidates.add(new CandidateSpec(
                        STRUCTURAL_PROFILE,
                        child.childId(),
                        document.userBundleId(),
                        provenance.documentId(),
                        provenance.versionId(),
                        child.sourceText(),
                        "",
                        child.retrievalText(),
                        child.sourceBlockType().name(),
                        provenance.parentAnnotationCandidateId(),
                        List.of(childRange),
                        child.sourceBlockIds(),
                        child.contextSourceBlockIds(),
                        List.of(),
                        List.of(new EvidenceChildRange(child.childId(), childRange))));
            }
        }
        long constructionNanos = System.nanoTime() - started;
        return new CandidateBuild(
                STRUCTURAL_PROFILE,
                slice,
                List.copyOf(candidates),
                constructionNanos,
                corpusStats(
                        STRUCTURAL_PROFILE, slice, candidates, constructionNanos, contextOnlyHeadings));
    }

    PassageCandidateBuild buildPassageCandidates(DatasetSlice slice, CandidateBuild structuralBuild) {
        long started = System.nanoTime();
        List<CandidateSpec> candidates = new ArrayList<>();
        List<RetrievalPassage> passages = new ArrayList<>();
        long contextOnlyHeadings = 0;
        for (SourceDocument document : activeDocuments(slice)) {
            List<StructuralBlock> blocks = parser.parse(document.structuralDocument());
            contextOnlyHeadings += blocks.stream()
                    .filter(block -> block.type() == StructuralBlockType.HEADING)
                    .count();
            List<EvidenceChild> children = childBuilder.build(blocks);
            List<RetrievalPassage> documentPassages = passageBuilder.build(children);
            passages.addAll(documentPassages);
            for (RetrievalPassage passage : documentPassages) {
                List<EvidenceChildRange> evidenceChildren = passage.evidenceChildren().stream()
                        .map(child -> new EvidenceChildRange(
                                child.childId(),
                                new CandidateRange(
                                        child.provenance().page(),
                                        child.provenance().lineStart(),
                                        child.provenance().lineEnd(),
                                        child.provenance().codePointStart(),
                                        child.provenance().codePointEnd())))
                        .toList();
                candidates.add(new CandidateSpec(
                        PASSAGE_PROFILE,
                        passage.passageId(),
                        document.userBundleId(),
                        passage.documentId(),
                        passage.versionId(),
                        passage.passageSourceText(),
                        "",
                        passage.retrievalText(),
                        "RETRIEVAL_PASSAGE",
                        passage.parentAnnotationCandidateId(),
                        evidenceChildren.stream().map(EvidenceChildRange::range).toList(),
                        passage.sourceBlockIds(),
                        passage.contextSourceBlockIds(),
                        List.of(),
                        evidenceChildren));
            }
        }
        validatePassageMembership(structuralBuild.candidates(), candidates);
        long constructionNanos = System.nanoTime() - started;
        CandidateBuild candidateBuild = new CandidateBuild(
                PASSAGE_PROFILE,
                slice,
                List.copyOf(candidates),
                constructionNanos,
                corpusStats(PASSAGE_PROFILE, slice, candidates, constructionNanos, contextOnlyHeadings));
        return new PassageCandidateBuild(
                candidateBuild,
                passageStats(slice, structuralBuild.candidates(), candidates, passages),
                List.copyOf(passages));
    }

    private void validatePassageMembership(
            List<CandidateSpec> structuralCandidates,
            List<CandidateSpec> passageCandidates) {
        List<String> structuralChildIds = structuralCandidates.stream()
                .flatMap(candidate -> candidate.evidenceChildren().stream())
                .map(EvidenceChildRange::evidenceChildId)
                .toList();
        List<String> passageChildIds = passageCandidates.stream()
                .flatMap(candidate -> candidate.evidenceChildren().stream())
                .map(EvidenceChildRange::evidenceChildId)
                .toList();
        if (!structuralChildIds.equals(passageChildIds)
                || new LinkedHashSet<>(passageChildIds).size() != passageChildIds.size()) {
            throw new IllegalStateException("B3 must preserve every B2 EvidenceChild exactly once and in order");
        }
    }

    private PassageCorpusStats passageStats(
            DatasetSlice slice,
            List<CandidateSpec> structuralCandidates,
            List<CandidateSpec> passageCandidates,
            List<RetrievalPassage> passages) {
        List<Integer> childCounts = passages.stream().map(passage -> passage.evidenceChildIds().size()).toList();
        List<Integer> lengths = passages.stream()
                .map(passage -> passage.retrievalText().codePointCount(0, passage.retrievalText().length()))
                .toList();
        long single = childCounts.stream().filter(count -> count == 1).count();
        long multi = childCounts.stream().filter(count -> count > 1).count();
        long crossParentViolations = passages.stream()
                .filter(passage -> passage.evidenceChildren().stream()
                        .map(child -> child.provenance().parentAnnotationCandidateId())
                        .distinct().count() > 1)
                .count();
        Set<String> directUnitIds = slice.queries().stream()
                .flatMap(query -> query.allExpectedEvidence().stream())
                .filter(expected -> "DIRECT_SUPPORT".equals(expected.supportRelation()))
                .map(ExpectedEvidence::evidenceUnitId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<GoldUnit> directUnits = directUnitIds.stream().map(slice.units()::get).toList();
        long atomicDirectUnits = directUnits.stream()
                .filter(unit -> structuralCandidates.stream().anyMatch(candidate -> covers(candidate, unit)))
                .count();
        long preservedDirectUnits = directUnits.stream()
                .filter(unit -> structuralCandidates.stream().anyMatch(candidate -> covers(candidate, unit)))
                .filter(unit -> passageCandidates.stream().anyMatch(candidate -> covers(candidate, unit)))
                .count();
        Set<String> directGoldChildIds = structuralCandidates.stream()
                .filter(candidate -> directUnits.stream().anyMatch(unit -> covers(candidate, unit)))
                .flatMap(candidate -> candidate.evidenceChildren().stream())
                .map(EvidenceChildRange::evidenceChildId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> passageChildIds = passageCandidates.stream()
                .flatMap(candidate -> candidate.evidenceChildren().stream())
                .map(EvidenceChildRange::evidenceChildId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        long preservedDirectGoldChildren = directGoldChildIds.stream().filter(passageChildIds::contains).count();
        if (crossParentViolations > 0 || atomicDirectUnits != preservedDirectUnits) {
            throw new IllegalStateException("B3 violated parent isolation or lost a direct EvidenceChild");
        }
        return new PassageCorpusStats(
                PASSAGE_PROFILE,
                passages.size(),
                childCounts.stream().mapToLong(Integer::longValue).sum(),
                childCounts.stream().mapToInt(Integer::intValue).min().orElse(0),
                childCounts.stream().mapToInt(Integer::intValue).average().orElse(0.0d),
                childCounts.stream().mapToInt(Integer::intValue).max().orElse(0),
                lengths.stream().mapToInt(Integer::intValue).min().orElse(0),
                lengths.stream().mapToInt(Integer::intValue).average().orElse(0.0d),
                lengths.stream().mapToInt(Integer::intValue).max().orElse(0),
                single,
                ratio(single, passages.size()),
                multi,
                ratio(multi, passages.size()),
                crossParentViolations,
                directGoldChildIds.size(),
                preservedDirectGoldChildren,
                ratio(preservedDirectGoldChildren, directGoldChildIds.size()));
    }

    private PassageCorpusStats combinePassageStats(List<PassageCorpusStats> values) {
        long passageCount = values.stream().mapToLong(PassageCorpusStats::passageCount).sum();
        long childMemberships = values.stream().mapToLong(PassageCorpusStats::evidenceChildMembershipCount).sum();
        long single = values.stream().mapToLong(PassageCorpusStats::singleChildPassageCount).sum();
        long multi = values.stream().mapToLong(PassageCorpusStats::multiChildPassageCount).sum();
        long direct = values.stream().mapToLong(PassageCorpusStats::directGoldEvidenceChildCount).sum();
        long preserved = values.stream().mapToLong(PassageCorpusStats::preservedDirectGoldEvidenceChildCount).sum();
        double weightedChildCount = values.stream()
                .mapToDouble(value -> value.averageChildrenPerPassage() * value.passageCount())
                .sum();
        double weightedLength = values.stream()
                .mapToDouble(value -> value.averagePassageCodePointLength() * value.passageCount())
                .sum();
        return new PassageCorpusStats(
                PASSAGE_PROFILE,
                passageCount,
                childMemberships,
                values.stream().mapToInt(PassageCorpusStats::minimumChildrenPerPassage).min().orElse(0),
                passageCount == 0 ? 0.0d : weightedChildCount / passageCount,
                values.stream().mapToInt(PassageCorpusStats::maximumChildrenPerPassage).max().orElse(0),
                values.stream().mapToInt(PassageCorpusStats::minimumPassageCodePointLength).min().orElse(0),
                passageCount == 0 ? 0.0d : weightedLength / passageCount,
                values.stream().mapToInt(PassageCorpusStats::maximumPassageCodePointLength).max().orElse(0),
                single,
                ratio(single, passageCount),
                multi,
                ratio(multi, passageCount),
                values.stream().mapToLong(PassageCorpusStats::crossParentPassageViolationCount).sum(),
                direct,
                preserved,
                ratio(preserved, direct));
    }

    private PreparedProfile prepare(CandidateBuild build, OllamaBgeM3EmbeddingClient embeddingClient) {
        OllamaBgeM3EmbeddingClient.EmbeddingBatch batch = embeddingClient.embedAll(
                build.candidates().stream().map(CandidateSpec::retrievalText).toList());
        if (batch.embeddings().size() != build.candidates().size()) {
            throw new IllegalStateException("Candidate embedding count mismatch");
        }
        List<EmbeddedCandidate> candidates = new ArrayList<>();
        for (int index = 0; index < build.candidates().size(); index++) {
            candidates.add(new EmbeddedCandidate(build.candidates().get(index), batch.embeddings().get(index)));
        }
        ProfileLatency latency = new ProfileLatency(
                build.profile(),
                nanosToMillis(build.constructionNanos()),
                nanosToMillis(batch.elapsedNanos()),
                nanosToMillis(build.constructionNanos() + batch.elapsedNanos()),
                0.0d,
                0.0d,
                0.0d,
                0.0d);
        ProfileCorpusStats stats = new ProfileCorpusStats(
                build.corpusStats().profile(),
                build.corpusStats().candidateCount(),
                build.corpusStats().candidateCount(),
                build.corpusStats().minimumCodePointLength(),
                build.corpusStats().averageCodePointLength(),
                build.corpusStats().maximumCodePointLength(),
                build.corpusStats().activeGoldUnitCount(),
                build.corpusStats().fragmentedGoldUnitCount(),
                build.corpusStats().fragmentationRate(),
                build.corpusStats().contaminatedCandidateCount(),
                build.corpusStats().contaminationRate(),
                build.corpusStats().duplicateGroupMappingCount(),
                build.corpusStats().goldGroupMappingCount(),
                build.corpusStats().duplicateRatio(),
                build.corpusStats().tableHeaderContextChildCount(),
                build.corpusStats().headingOnlyCandidateCount(),
                build.corpusStats().contextOnlyHeadingCount(),
                build.corpusStats().veryShortCandidateCount(),
                build.corpusStats().lengthBucketCandidateCount(),
                build.corpusStats().goldParentCountPerCandidateDistribution(),
                build.corpusStats().constructionLatencyMs());
        return new PreparedProfile(build.profile(), build.slice(), List.copyOf(candidates), stats, latency);
    }

    private List<QueryResult> evaluateQueries(
            DatasetSlice slice,
            PreparedProfile fixed,
            PreparedProfile structural,
            PreparedProfile passage,
            OllamaBgeM3EmbeddingClient embeddingClient) {
        List<QueryResult> results = new ArrayList<>();
        for (Query query : slice.queries()) {
            OllamaBgeM3EmbeddingClient.EmbeddingBatch queryBatch = embeddingClient.embedOne(query.text());
            float[] queryVector = queryBatch.embeddings().get(0);

            long fixedStarted = System.nanoTime();
            List<RankedCandidate> fixedRanking = rank(queryVector, query.userBundleId(), fixed.candidates(), slice);
            long fixedRankNanos = System.nanoTime() - fixedStarted;
            long structuralStarted = System.nanoTime();
            List<RankedCandidate> structuralRanking =
                    rank(queryVector, query.userBundleId(), structural.candidates(), slice);
            long structuralRankNanos = System.nanoTime() - structuralStarted;
            long passageStarted = System.nanoTime();
            List<RankedCandidate> passageRanking =
                    rank(queryVector, query.userBundleId(), passage.candidates(), slice);
            long passageRankNanos = System.nanoTime() - passageStarted;

            QueryProfileResult fixedResult = queryResult(
                    query, fixedRanking, slice, queryBatch.elapsedNanos(), fixedRankNanos);
            QueryProfileResult structuralResult = queryResult(
                    query, structuralRanking, slice, queryBatch.elapsedNanos(), structuralRankNanos);
            QueryProfileResult passageResult = queryResult(
                    query, passageRanking, slice, queryBatch.elapsedNanos(), passageRankNanos);
            UserBundle bundle = slice.bundles().stream()
                    .filter(value -> value.userBundleId().equals(query.userBundleId()))
                    .findFirst()
                    .orElseThrow();
            results.add(new QueryResult(
                    query.queryId(),
                    query.userBundleId(),
                    slice.split(),
                    bundle.professionGroup(),
                    query.language(),
                    query.answerability(),
                    query.categories(),
                    query.hasDirectSupport(),
                    nanosToMillis(queryBatch.elapsedNanos()),
                    fixedResult,
                    structuralResult,
                    passageResult));
        }
        return List.copyOf(results);
    }

    private List<RankedCandidate> rank(
            float[] queryVector,
            String userBundleId,
            List<EmbeddedCandidate> candidates,
            DatasetSlice slice) {
        List<ScoredCandidate> scored = candidates.stream()
                .filter(candidate -> candidate.spec().userBundleId().equals(userBundleId))
                .map(candidate -> new ScoredCandidate(candidate.spec(), cosine(queryVector, candidate.embedding())))
                .sorted(Comparator.comparingDouble(ScoredCandidate::score).reversed()
                        .thenComparing(value -> value.spec().candidateId()))
                .toList();
        List<RankedCandidate> result = new ArrayList<>();
        for (int index = 0; index < scored.size(); index++) {
            ScoredCandidate value = scored.get(index);
            Set<String> unitIds = slice.units().values().stream()
                    .filter(unit -> covers(value.spec(), unit))
                    .map(GoldUnit::evidenceUnitId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> groupIds = unitIds.stream()
                    .map(slice.units()::get)
                    .map(GoldUnit::groupId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> parentIds = unitIds.stream()
                    .map(slice.units()::get)
                    .map(GoldUnit::parentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            result.add(new RankedCandidate(
                    index + 1,
                    value.spec().candidateId(),
                    value.score(),
                    value.spec().documentId(),
                    value.spec().versionId(),
                    value.spec().sourceBlockType(),
                    value.spec().sourceText(),
                    value.spec().contextText(),
                    value.spec().retrievalText(),
                    value.spec().parentAnnotationCandidateId(),
                    value.spec().sourceText().codePointCount(0, value.spec().sourceText().length()),
                    value.spec().evidenceChildren().stream().map(EvidenceChildRange::evidenceChildId).toList(),
                    value.spec().addedContextBlockIds(),
                    List.copyOf(unitIds),
                    List.copyOf(groupIds),
                    List.copyOf(parentIds)));
        }
        return List.copyOf(result);
    }

    QueryProfileResult queryResult(
            Query query,
            List<RankedCandidate> ranking,
            DatasetSlice slice,
            long queryEmbeddingNanos,
            long rankingNanos) {
        Set<String> directUnitIds = query.allExpectedEvidence().stream()
                .filter(expected -> "DIRECT_SUPPORT".equals(expected.supportRelation()))
                .map(ExpectedEvidence::evidenceUnitId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> allExpectedUnitIds = query.allExpectedEvidence().stream()
                .map(ExpectedEvidence::evidenceUnitId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Integer firstDirectRank = firstRank(ranking, directUnitIds);
        Integer firstExpectedRank = firstRank(ranking, allExpectedUnitIds);

        Map<Integer, Boolean> recallAtK = new LinkedHashMap<>();
        Map<Integer, Double> unitCoverageAtK = new LinkedHashMap<>();
        Map<Integer, Double> groupCoverageAtK = new LinkedHashMap<>();
        Map<Integer, Double> parentCoverageAtK = new LinkedHashMap<>();
        Set<String> directGroupIds = directUnitIds.stream()
                .map(slice.units()::get)
                .map(GoldUnit::groupId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> directParentIds = directUnitIds.stream()
                .map(slice.units()::get)
                .map(GoldUnit::parentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (int cutoff : CUTOFFS) {
            List<RankedCandidate> top = ranking.stream().limit(cutoff).toList();
            Set<String> hitUnits = top.stream()
                    .flatMap(candidate -> candidate.coveredUnitIds().stream())
                    .filter(directUnitIds::contains)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> hitGroups = hitUnits.stream()
                    .map(slice.units()::get)
                    .map(GoldUnit::groupId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> hitParents = hitUnits.stream()
                    .map(slice.units()::get)
                    .map(GoldUnit::parentId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            recallAtK.put(cutoff, !directUnitIds.isEmpty() && requirementsMet(query, hitUnits, slice));
            unitCoverageAtK.put(cutoff, ratio(hitUnits.size(), directUnitIds.size()));
            groupCoverageAtK.put(cutoff, ratio(hitGroups.size(), directGroupIds.size()));
            parentCoverageAtK.put(cutoff, ratio(hitParents.size(), directParentIds.size()));
        }
        List<RankedCandidate> rawCandidates = ranking.stream().limit(50).toList();
        return new QueryProfileResult(
                ranking.size(),
                firstDirectRank,
                firstExpectedRank,
                firstDirectRank != null && firstDirectRank == 1,
                firstDirectRank == null ? 0.0d : 1.0d / firstDirectRank,
                Map.copyOf(recallAtK),
                Map.copyOf(unitCoverageAtK),
                Map.copyOf(groupCoverageAtK),
                Map.copyOf(parentCoverageAtK),
                nanosToMillis(rankingNanos),
                nanosToMillis(queryEmbeddingNanos + rankingNanos),
                rawCandidates);
    }

    boolean requirementsMet(Query query, Set<String> hitUnits, DatasetSlice slice) {
        List<AspectRequirement> directAspects = query.aspects().stream()
                .filter(aspect -> aspect.expectedEvidence().stream()
                        .anyMatch(expected -> "DIRECT_SUPPORT".equals(expected.supportRelation())))
                .toList();
        if (directAspects.isEmpty()) {
            return false;
        }
        Map<String, Boolean> satisfiedByAspect = new LinkedHashMap<>();
        for (AspectRequirement aspect : directAspects) {
            Set<String> directGroups = aspect.expectedEvidence().stream()
                    .filter(expected -> "DIRECT_SUPPORT".equals(expected.supportRelation()))
                    .map(ExpectedEvidence::evidenceUnitId)
                    .map(slice.units()::get)
                    .map(GoldUnit::groupId)
                    .collect(Collectors.toSet());
            Set<String> hitGroups = aspect.expectedEvidence().stream()
                    .filter(expected -> "DIRECT_SUPPORT".equals(expected.supportRelation()))
                    .map(ExpectedEvidence::evidenceUnitId)
                    .filter(hitUnits::contains)
                    .map(slice.units()::get)
                    .map(GoldUnit::groupId)
                    .collect(Collectors.toSet());
            Set<String> explicitlyRequiredGroups = Set.copyOf(aspect.requiredEvidenceGroupIds());
            boolean explicitRequiredGroupsHit = hitGroups.containsAll(explicitlyRequiredGroups);
            int minimum = Math.max(1, aspect.minEvidenceGroups());
            satisfiedByAspect.put(
                    aspect.aspectId(), explicitRequiredGroupsHit && hitGroups.size() >= minimum);
        }
        List<String> requiredDirectAspectIds = query.aspectExpression().requiredAspectIds().stream()
                .filter(satisfiedByAspect::containsKey)
                .toList();
        if (requiredDirectAspectIds.isEmpty()) {
            requiredDirectAspectIds = directAspects.stream().map(AspectRequirement::aspectId).toList();
        }
        long satisfied = requiredDirectAspectIds.stream()
                .filter(id -> Boolean.TRUE.equals(satisfiedByAspect.get(id)))
                .count();
        if ("ALL".equals(query.aspectExpression().operator())) {
            return satisfied == requiredDirectAspectIds.size();
        }
        int minimum = Math.min(query.aspectExpression().minShouldMatch(), requiredDirectAspectIds.size());
        return satisfied >= minimum;
    }

    private Integer firstRank(List<RankedCandidate> ranking, Set<String> expectedUnitIds) {
        if (expectedUnitIds.isEmpty()) {
            return null;
        }
        return ranking.stream()
                .filter(candidate -> candidate.coveredUnitIds().stream().anyMatch(expectedUnitIds::contains))
                .map(RankedCandidate::rank)
                .findFirst()
                .orElse(null);
    }

    private ProfileCorpusStats corpusStats(
            String profile,
            DatasetSlice slice,
            List<CandidateSpec> candidates,
            long constructionNanos,
            long contextOnlyHeadingCount) {
        List<GoldUnit> activeUnits = slice.units().values().stream()
                .filter(unit -> slice.activeDocumentsByVersion().containsKey(unit.versionId()))
                .toList();
        long fragmented = activeUnits.stream()
                .filter(unit -> candidates.stream().noneMatch(candidate -> covers(candidate, unit)))
                .count();
        long contaminated = candidates.stream()
                .filter(candidate -> overlappingParentIds(candidate, slice).size() > 1)
                .count();

        Map<String, Integer> groupMappings = new HashMap<>();
        for (CandidateSpec candidate : candidates) {
            activeUnits.stream()
                    .filter(unit -> covers(candidate, unit))
                    .map(GoldUnit::groupId)
                    .distinct()
                    .forEach(group -> groupMappings.merge(group, 1, Integer::sum));
        }
        long mappingCount = groupMappings.values().stream().mapToLong(Integer::longValue).sum();
        long duplicateMappings = groupMappings.values().stream()
                .mapToLong(count -> Math.max(0, count - 1))
                .sum();
        List<Integer> lengths = candidates.stream()
                .map(candidate -> candidate.sourceText().codePointCount(0, candidate.sourceText().length()))
                .toList();
        int minimum = lengths.stream().mapToInt(Integer::intValue).min().orElse(0);
        int maximum = lengths.stream().mapToInt(Integer::intValue).max().orElse(0);
        double average = lengths.stream().mapToInt(Integer::intValue).average().orElse(0.0d);
        long tableHeaderContext = candidates.stream().filter(candidate -> !candidate.contextBlockIds().isEmpty()).count();
        long headingOnlyCandidates = candidates.stream()
                .filter(candidate -> StructuralBlockType.HEADING.name().equals(candidate.sourceBlockType()))
                .count();
        long veryShortCandidates = lengths.stream().filter(length -> length <= 20).count();
        Map<String, Long> lengthBuckets = candidates.stream().collect(Collectors.groupingBy(
                candidate -> lengthBucket(candidate.sourceText().codePointCount(0, candidate.sourceText().length())),
                LinkedHashMap::new,
                Collectors.counting()));
        Map<String, Long> parentCountDistribution = candidates.stream().collect(Collectors.groupingBy(
                candidate -> Integer.toString(overlappingParentIds(candidate, slice).size()),
                LinkedHashMap::new,
                Collectors.counting()));
        return new ProfileCorpusStats(
                profile,
                candidates.size(),
                0,
                minimum,
                average,
                maximum,
                activeUnits.size(),
                fragmented,
                ratio(fragmented, activeUnits.size()),
                contaminated,
                ratio(contaminated, candidates.size()),
                duplicateMappings,
                mappingCount,
                ratio(duplicateMappings, mappingCount),
                tableHeaderContext,
                headingOnlyCandidates,
                contextOnlyHeadingCount,
                veryShortCandidates,
                Map.copyOf(lengthBuckets),
                Map.copyOf(parentCountDistribution),
                nanosToMillis(constructionNanos));
    }

    private Set<String> overlappingParentIds(CandidateSpec candidate, DatasetSlice slice) {
        return slice.parents().values().stream()
                .filter(parent -> parent.documentId().equals(candidate.documentId()))
                .filter(parent -> parent.versionId().equals(candidate.versionId()))
                .filter(parent -> overlaps(candidate, parent))
                .map(GoldParent::parentId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean overlaps(CandidateSpec candidate, GoldParent parent) {
        GoldSpan parentSpan = parent.sourceSpan();
        return candidate.ranges().stream().anyMatch(range ->
                samePage(range.page(), parentSpan.page())
                        && range.codePointStart() < parentSpan.codePointEnd()
                        && parentSpan.codePointStart() < range.codePointEnd());
    }

    private boolean covers(CandidateSpec candidate, GoldUnit unit) {
        if (!candidate.documentId().equals(unit.documentId())
                || !candidate.versionId().equals(unit.versionId())) {
            return false;
        }
        if (unit.sourceSpans().isEmpty()) {
            return false;
        }
        if (!candidate.evidenceChildren().isEmpty()) {
            return candidate.evidenceChildren().stream().anyMatch(child ->
                    unit.sourceSpans().stream().allMatch(span -> covers(child.range(), span)));
        }
        return unit.sourceSpans().stream().allMatch(span ->
                candidate.ranges().stream().anyMatch(range -> covers(range, span)));
    }

    private boolean covers(CandidateRange range, GoldSpan span) {
        return samePage(range.page(), span.page())
                && range.codePointStart() <= span.codePointStart()
                && range.codePointEnd() >= span.codePointEnd();
    }

    private boolean samePage(Integer left, Integer right) {
        return left == null ? right == null : left.equals(right);
    }

    private CandidateRange range(SourceDocument document, int charStart, int charEnd) {
        String source = document.structuralDocument().sourceText();
        int lineStart = 1 + countNewlines(source, 0, charStart);
        int lineEnd = lineStart + countNewlines(source, charStart, charEnd);
        return new CandidateRange(
                document.structuralDocument().page(),
                lineStart,
                lineEnd,
                source.codePointCount(0, charStart),
                source.codePointCount(0, charEnd));
    }

    private List<SourceDocument> activeDocuments(DatasetSlice slice) {
        return slice.bundles().stream().flatMap(bundle -> bundle.activeDocuments().stream()).toList();
    }

    private void assertSameEvaluationInputs(
            DatasetSlice slice,
            CandidateBuild fixed,
            CandidateBuild structural,
            CandidateBuild passage) {
        Set<String> expectedVersions = slice.activeDocumentsByVersion().keySet();
        Set<String> fixedVersions = fixed.candidates().stream()
                .map(CandidateSpec::versionId).collect(Collectors.toSet());
        Set<String> structuralVersions = structural.candidates().stream()
                .map(CandidateSpec::versionId).collect(Collectors.toSet());
        Set<String> passageVersions = passage.candidates().stream()
                .map(CandidateSpec::versionId).collect(Collectors.toSet());
        if (!fixedVersions.equals(expectedVersions)
                || !structuralVersions.equals(expectedVersions)
                || !passageVersions.equals(expectedVersions)) {
            throw new IllegalStateException("A/B2/B3 did not use the same ACTIVE source versions");
        }
        if (slice.queries().stream().anyMatch(query -> query.split() != slice.split())) {
            throw new IllegalStateException("A/B2/B3 query split mismatch");
        }
    }

    private AggregateComparison aggregateComparison(List<QueryResult> values) {
        return new AggregateComparison(
                aggregate(values, QueryResult::fixed),
                aggregate(values, QueryResult::structural),
                aggregate(values, QueryResult::passage));
    }

    private AggregateComparison macroComparison(List<QueryResult> values, Function<QueryResult, String> key) {
        Map<String, List<QueryResult>> groups = values.stream().collect(Collectors.groupingBy(
                key, LinkedHashMap::new, Collectors.toList()));
        List<AggregateMetrics> fixed = groups.values().stream()
                .map(group -> aggregate(group, QueryResult::fixed))
                .filter(metric -> metric.directQueryCount() > 0)
                .toList();
        List<AggregateMetrics> structural = groups.values().stream()
                .map(group -> aggregate(group, QueryResult::structural))
                .filter(metric -> metric.directQueryCount() > 0)
                .toList();
        List<AggregateMetrics> passage = groups.values().stream()
                .map(group -> aggregate(group, QueryResult::passage))
                .filter(metric -> metric.directQueryCount() > 0)
                .toList();
        return new AggregateComparison(
                averageAggregates(fixed),
                averageAggregates(structural),
                averageAggregates(passage));
    }

    private Map<String, AggregateComparison> grouped(
            List<QueryResult> values,
            Function<QueryResult, String> key) {
        Map<String, List<QueryResult>> groups = values.stream().collect(Collectors.groupingBy(
                key, LinkedHashMap::new, Collectors.toList()));
        Map<String, AggregateComparison> result = new LinkedHashMap<>();
        groups.forEach((name, group) -> result.put(name, aggregateComparison(group)));
        return result;
    }

    private String decision(
            ProfileCorpusStats structural,
            ProfileCorpusStats passage,
            PassageCorpusStats passageStats,
            AggregateComparison queryMicro,
            Map<String, AggregateComparison> profession,
            Map<String, AggregateComparison> language) {
        boolean boundaryFailure = passage.contaminatedCandidateCount() > 0
                || passage.fragmentationRate() > structural.fragmentationRate()
                || passage.headingOnlyCandidateCount() > 0
                || passageStats.crossParentPassageViolationCount() > 0
                || passageStats.directGoldEvidenceChildPreservationRate() < 1.0d;
        boolean costReduced = passage.candidateCount() < structural.candidateCount();
        if (boundaryFailure || !costReduced) {
            return "NO_GO";
        }
        boolean aggregateRegression = queryMicro.passage().top1() < queryMicro.structural().top1()
                || queryMicro.passage().mrr() < queryMicro.structural().mrr()
                || CUTOFFS.stream().anyMatch(cutoff ->
                        queryMicro.passage().recallAtK().get(cutoff)
                                < queryMicro.structural().recallAtK().get(cutoff));
        boolean sliceRegression = hasSliceRegression(profession) || hasSliceRegression(language);
        return aggregateRegression || sliceRegression ? "NEEDS_ADJUSTMENT" : "PROMISING";
    }

    private boolean hasSliceRegression(Map<String, AggregateComparison> slices) {
        return slices.values().stream().anyMatch(value ->
                value.passage().top1() < value.structural().top1()
                        || value.passage().mrr() < value.structural().mrr());
    }

    private AggregateMetrics aggregate(
            List<QueryResult> values,
            Function<QueryResult, QueryProfileResult> profile) {
        List<QueryProfileResult> direct = values.stream()
                .filter(QueryResult::directSupport)
                .map(profile)
                .toList();
        Map<Integer, Double> recall = new LinkedHashMap<>();
        Map<Integer, Double> unit = new LinkedHashMap<>();
        Map<Integer, Double> group = new LinkedHashMap<>();
        Map<Integer, Double> parent = new LinkedHashMap<>();
        for (int cutoff : CUTOFFS) {
            recall.put(cutoff, direct.stream().mapToDouble(value -> value.recallAtK().get(cutoff) ? 1.0d : 0.0d)
                    .average().orElse(0.0d));
            unit.put(cutoff, direct.stream().mapToDouble(value -> value.unitCoverageAtK().get(cutoff))
                    .average().orElse(0.0d));
            group.put(cutoff, direct.stream().mapToDouble(value -> value.groupCoverageAtK().get(cutoff))
                    .average().orElse(0.0d));
            parent.put(cutoff, direct.stream().mapToDouble(value -> value.parentCoverageAtK().get(cutoff))
                    .average().orElse(0.0d));
        }
        return new AggregateMetrics(
                direct.size(),
                values.size() - direct.size(),
                direct.size(),
                direct.stream().mapToDouble(value -> value.top1() ? 1.0d : 0.0d).average().orElse(0.0d),
                direct.stream().mapToDouble(QueryProfileResult::reciprocalRank).average().orElse(0.0d),
                Map.copyOf(recall),
                Map.copyOf(unit),
                Map.copyOf(group),
                Map.copyOf(parent));
    }

    AggregateMetrics averageAggregates(List<AggregateMetrics> values) {
        Map<Integer, Double> recall = averageMap(values, AggregateMetrics::recallAtK);
        Map<Integer, Double> unit = averageMap(values, AggregateMetrics::unitCoverageAtK);
        Map<Integer, Double> group = averageMap(values, AggregateMetrics::groupCoverageAtK);
        Map<Integer, Double> parent = averageMap(values, AggregateMetrics::parentCoverageAtK);
        return new AggregateMetrics(
                values.stream().mapToInt(AggregateMetrics::directQueryCount).sum(),
                values.stream().mapToInt(AggregateMetrics::diagnosticOnlyQueryCount).sum(),
                values.size(),
                values.stream().mapToDouble(AggregateMetrics::top1).average().orElse(0.0d),
                values.stream().mapToDouble(AggregateMetrics::mrr).average().orElse(0.0d),
                recall,
                unit,
                group,
                parent);
    }

    private Map<Integer, Double> averageMap(
            List<AggregateMetrics> values,
            Function<AggregateMetrics, Map<Integer, Double>> getter) {
        Map<Integer, Double> result = new LinkedHashMap<>();
        for (int cutoff : CUTOFFS) {
            result.put(cutoff, values.stream().mapToDouble(value -> getter.apply(value).get(cutoff))
                    .average().orElse(0.0d));
        }
        return Map.copyOf(result);
    }

    private Map<String, LengthBucketStats> lengthBucketStats(
            List<SplitRun> splitRuns,
            List<QueryResult> queryResults) {
        Map<String, long[]> values = new LinkedHashMap<>();
        LENGTH_BUCKETS.forEach(bucket -> values.put(bucket, new long[4]));
        for (SplitRun run : splitRuns) {
            List<GoldUnit> activeUnits = run.slice().units().values().stream()
                    .filter(unit -> run.slice().activeDocumentsByVersion().containsKey(unit.versionId()))
                    .toList();
            for (EmbeddedCandidate candidate : run.structural().candidates()) {
                int length = candidate.spec().sourceText()
                        .codePointCount(0, candidate.spec().sourceText().length());
                long[] bucket = values.get(lengthBucket(length));
                bucket[0]++;
                if (activeUnits.stream().anyMatch(unit -> covers(candidate.spec(), unit))) {
                    bucket[1]++;
                }
            }
        }
        queryResults.stream()
                .filter(QueryResult::directSupport)
                .map(QueryResult::structural)
                .filter(result -> !result.rawDenseRanking().isEmpty())
                .forEach(result -> {
                    RankedCandidate top = result.rawDenseRanking().get(0);
                    long[] bucket = values.get(lengthBucket(top.sourceCodePointLength()));
                    bucket[2]++;
                    if (result.firstDirectRank() == null || result.firstDirectRank() != 1) {
                        bucket[3]++;
                    }
                });
        Map<String, LengthBucketStats> result = new LinkedHashMap<>();
        values.forEach((bucket, counts) -> result.put(
                bucket,
                new LengthBucketStats(counts[0], counts[1], counts[2], counts[3])));
        return Map.copyOf(result);
    }

    private String lengthBucket(int length) {
        if (length <= 10) {
            return "01-10";
        }
        if (length <= 20) {
            return "11-20";
        }
        if (length <= 40) {
            return "21-40";
        }
        if (length <= 80) {
            return "41-80";
        }
        return "81+";
    }

    ProfileCorpusStats combineCorpusStats(String profile, List<ProfileCorpusStats> values) {
        long candidates = values.stream().mapToLong(ProfileCorpusStats::candidateCount).sum();
        long embeddings = values.stream().mapToLong(ProfileCorpusStats::embeddingCount).sum();
        long activeUnits = values.stream().mapToLong(ProfileCorpusStats::activeGoldUnitCount).sum();
        long fragmented = values.stream().mapToLong(ProfileCorpusStats::fragmentedGoldUnitCount).sum();
        long contaminated = values.stream().mapToLong(ProfileCorpusStats::contaminatedCandidateCount).sum();
        long duplicates = values.stream().mapToLong(ProfileCorpusStats::duplicateGroupMappingCount).sum();
        long tableContext = values.stream().mapToLong(ProfileCorpusStats::tableHeaderContextChildCount).sum();
        long headingOnly = values.stream().mapToLong(ProfileCorpusStats::headingOnlyCandidateCount).sum();
        long contextOnlyHeadings = values.stream().mapToLong(ProfileCorpusStats::contextOnlyHeadingCount).sum();
        long veryShort = values.stream().mapToLong(ProfileCorpusStats::veryShortCandidateCount).sum();
        Map<String, Long> lengthBuckets = sumMaps(values, ProfileCorpusStats::lengthBucketCandidateCount);
        Map<String, Long> parentCounts = sumMaps(
                values, ProfileCorpusStats::goldParentCountPerCandidateDistribution);
        int minimum = values.stream().mapToInt(ProfileCorpusStats::minimumCodePointLength).min().orElse(0);
        int maximum = values.stream().mapToInt(ProfileCorpusStats::maximumCodePointLength).max().orElse(0);
        double weightedLength = values.stream()
                .mapToDouble(value -> value.averageCodePointLength() * value.candidateCount())
                .sum();
        long mappingCount = values.stream().mapToLong(ProfileCorpusStats::goldGroupMappingCount).sum();
        return new ProfileCorpusStats(
                profile,
                candidates,
                embeddings,
                minimum,
                candidates == 0 ? 0.0d : weightedLength / candidates,
                maximum,
                activeUnits,
                fragmented,
                ratio(fragmented, activeUnits),
                contaminated,
                ratio(contaminated, candidates),
                duplicates,
                mappingCount,
                ratio(duplicates, mappingCount),
                tableContext,
                headingOnly,
                contextOnlyHeadings,
                veryShort,
                Map.copyOf(lengthBuckets),
                Map.copyOf(parentCounts),
                values.stream().mapToDouble(ProfileCorpusStats::constructionLatencyMs).sum());
    }

    private Map<String, Long> sumMaps(
            List<ProfileCorpusStats> values,
            Function<ProfileCorpusStats, Map<String, Long>> getter) {
        Map<String, Long> result = new LinkedHashMap<>();
        values.forEach(value -> getter.apply(value)
                .forEach((key, count) -> result.merge(key, count, Long::sum)));
        return result;
    }

    private ProfileLatency combineLatency(
            String profile,
            List<ProfileLatency> indexing,
            List<Double> totalQuery,
            List<Double> ranking) {
        return new ProfileLatency(
                profile,
                indexing.stream().mapToDouble(ProfileLatency::constructionLatencyMs).sum(),
                indexing.stream().mapToDouble(ProfileLatency::embeddingLatencyMs).sum(),
                indexing.stream().mapToDouble(ProfileLatency::indexingLatencyMs).sum(),
                percentile(totalQuery, 0.50d),
                percentile(totalQuery, 0.95d),
                percentile(ranking, 0.50d),
                percentile(ranking, 0.95d));
    }

    private Map<String, Long> latencySummary(List<Double> values) {
        return Map.of(
                "sampleCount", (long) values.size(),
                "p50Micros", Math.round(percentile(values, 0.50d) * 1000.0d),
                "p95Micros", Math.round(percentile(values, 0.95d) * 1000.0d));
    }

    private double percentile(List<Double> values, double percentile) {
        if (values.isEmpty()) {
            return 0.0d;
        }
        List<Double> sorted = values.stream().sorted().toList();
        int rank = (int) Math.ceil(percentile * sorted.size());
        return sorted.get(Math.max(0, rank - 1));
    }

    private double cosine(float[] left, float[] right) {
        double dot = 0.0d;
        double leftNorm = 0.0d;
        double rightNorm = 0.0d;
        for (int index = 0; index < left.length; index++) {
            dot += (double) left[index] * right[index];
            leftNorm += (double) left[index] * left[index];
            rightNorm += (double) right[index] * right[index];
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private int skipWhitespace(String value, int start, int end) {
        int current = start;
        while (current < end) {
            int codePoint = value.codePointAt(current);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            current += Character.charCount(codePoint);
        }
        return current;
    }

    private int trimWhitespace(String value, int start, int end) {
        int current = end;
        while (current > start) {
            int codePoint = value.codePointBefore(current);
            if (!Character.isWhitespace(codePoint)) {
                break;
            }
            current -= Character.charCount(codePoint);
        }
        return current;
    }

    private int countNewlines(String value, int start, int end) {
        int count = 0;
        for (int index = start; index < end; index++) {
            if (value.charAt(index) == '\n') {
                count++;
            }
        }
        return count;
    }

    private double ratio(long numerator, long denominator) {
        return denominator == 0 ? 0.0d : (double) numerator / denominator;
    }

    private double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0d;
    }

    record CandidateRange(Integer page, int lineStart, int lineEnd, int codePointStart, int codePointEnd) {
    }

    record CandidateSpec(
            String profile,
            String candidateId,
            String userBundleId,
            String documentId,
            String versionId,
            String sourceText,
            String contextText,
            String retrievalText,
            String sourceBlockType,
            String parentAnnotationCandidateId,
            List<CandidateRange> ranges,
            List<String> sourceBlockIds,
            List<String> contextBlockIds,
            List<String> addedContextBlockIds,
            List<EvidenceChildRange> evidenceChildren) {
    }

    record EvidenceChildRange(String evidenceChildId, CandidateRange range) {
    }

    record CandidateBuild(
            String profile,
            DatasetSlice slice,
            List<CandidateSpec> candidates,
            long constructionNanos,
            ProfileCorpusStats corpusStats) {
    }

    record PassageCandidateBuild(
            CandidateBuild candidateBuild,
            PassageCorpusStats passageStats,
            List<RetrievalPassage> passages) {
    }

    record ContextCandidateBuild(
            CandidateBuild candidateBuild,
            ParentContextCorpusStats contextStats,
            List<ContextualRetrievalPassage> passages) {
    }

    private record EmbeddedCandidate(CandidateSpec spec, float[] embedding) {
    }

    private record PreparedProfile(
            String profile,
            DatasetSlice slice,
            List<EmbeddedCandidate> candidates,
            ProfileCorpusStats corpusStats,
            ProfileLatency latency) {
    }

    private record ScoredCandidate(CandidateSpec spec, double score) {
    }

    record RankedCandidate(
            int rank,
            String candidateId,
            double cosineScore,
            String documentId,
            String versionId,
            String sourceBlockType,
            String sourceText,
            String contextText,
            String retrievalText,
            String parentAnnotationCandidateId,
            int sourceCodePointLength,
            List<String> evidenceChildIds,
            List<String> contextSourceBlockIds,
            List<String> coveredUnitIds,
            List<String> coveredGroupIds,
            List<String> coveredParentIds) {
    }

    record QueryProfileResult(
            int candidateCount,
            Integer firstDirectRank,
            Integer firstExpectedRelationRank,
            boolean top1,
            double reciprocalRank,
            Map<Integer, Boolean> recallAtK,
            Map<Integer, Double> unitCoverageAtK,
            Map<Integer, Double> groupCoverageAtK,
            Map<Integer, Double> parentCoverageAtK,
            double rankingLatencyMs,
            double totalLatencyMs,
            List<RankedCandidate> rawDenseRanking) {
    }

    record QueryResult(
            String queryId,
            String userBundleId,
            Split split,
            String professionGroup,
            String language,
            String answerability,
            List<String> categories,
            boolean directSupport,
            double queryEmbeddingLatencyMs,
            QueryProfileResult fixed,
            QueryProfileResult structural,
            QueryProfileResult passage) {
    }

    record AggregateMetrics(
            int directQueryCount,
            int diagnosticOnlyQueryCount,
            int aggregationUnitCount,
            double top1,
            double mrr,
            Map<Integer, Double> recallAtK,
            Map<Integer, Double> unitCoverageAtK,
            Map<Integer, Double> groupCoverageAtK,
            Map<Integer, Double> parentCoverageAtK) {
    }

    record AggregateComparison(
            AggregateMetrics fixed,
            AggregateMetrics structural,
            AggregateMetrics passage) {
    }

    record ParentContextAggregateComparison(
            AggregateMetrics passage,
            AggregateMetrics context) {
    }

    record ProfileCorpusStats(
            String profile,
            long candidateCount,
            long embeddingCount,
            int minimumCodePointLength,
            double averageCodePointLength,
            int maximumCodePointLength,
            long activeGoldUnitCount,
            long fragmentedGoldUnitCount,
            double fragmentationRate,
            long contaminatedCandidateCount,
            double contaminationRate,
            long duplicateGroupMappingCount,
            long goldGroupMappingCount,
            double duplicateRatio,
            long tableHeaderContextChildCount,
            long headingOnlyCandidateCount,
            long contextOnlyHeadingCount,
            long veryShortCandidateCount,
            Map<String, Long> lengthBucketCandidateCount,
            Map<String, Long> goldParentCountPerCandidateDistribution,
            double constructionLatencyMs) {
    }

    record PassageCorpusStats(
            String profile,
            long passageCount,
            long evidenceChildMembershipCount,
            int minimumChildrenPerPassage,
            double averageChildrenPerPassage,
            int maximumChildrenPerPassage,
            int minimumPassageCodePointLength,
            double averagePassageCodePointLength,
            int maximumPassageCodePointLength,
            long singleChildPassageCount,
            double singleChildPassageRatio,
            long multiChildPassageCount,
            double multiChildPassageRatio,
            long crossParentPassageViolationCount,
            long directGoldEvidenceChildCount,
            long preservedDirectGoldEvidenceChildCount,
            double directGoldEvidenceChildPreservationRate) {
    }

    record ParentContextCorpusStats(
            String policy,
            long passageCount,
            long contextPassageCount,
            double contextPassageRatio,
            int minimumContextCodePointLength,
            double averageContextCodePointLength,
            int maximumContextCodePointLength,
            int minimumHeadingDepth,
            double averageHeadingDepth,
            int maximumHeadingDepth,
            Map<String, Long> headingDepthDistribution,
            long crossParentContextViolationCount,
            long sourceParityViolationCount,
            long evidenceChildParityViolationCount,
            double contextConstructionLatencyMs) {
    }

    record MappedTextChunk(TextChunk chunk, int exactStart, int exactEnd) {
    }

    record LengthBucketStats(
            long candidateCount,
            long goldMappedCandidateCount,
            long directQueryRank1Count,
            long directQueryRank1MissCount) {
    }

    record ProfileLatency(
            String profile,
            double constructionLatencyMs,
            double embeddingLatencyMs,
            double indexingLatencyMs,
            double queryP50Ms,
            double queryP95Ms,
            double rankingOnlyP50Ms,
            double rankingOnlyP95Ms) {
    }

    record ComparisonContract(
            int fixedMaxCharacters,
            int fixedOverlapCharacters,
            int structuralMaxCodePoints,
            int passageMinimumTargetCodePoints,
            int passageTargetMaximumCodePoints,
            int passageAbsoluteMaximumCodePoints,
            String embeddingModel,
            int embeddingDimensions,
            String similarity,
            String ranking,
            String scope,
            String queryVectorPolicy,
            String evidenceParentContextStatus,
            String tableHeaderContextPolicy) {
    }

    record ParentContextContract(
            String baselineProfile,
            String contextProfile,
            String contextPolicy,
            int maximumHeadingDepth,
            int maximumContextCodePoints,
            String embeddingModel,
            int embeddingDimensions,
            String similarity,
            String ranking,
            String queryVectorPolicy,
            String goldPolicy,
            String parentDenseStatus) {
    }

    record ExperimentReport(
            int schemaVersion,
            String phase,
            String executedAt,
            String datasetVersion,
            String adjustmentStartCommit,
            String dependency,
            Map<String, String> splitManifestHashes,
            OllamaBgeM3EmbeddingClient.ModelMetadata model,
            ComparisonContract contract,
            ProfileCorpusStats fixedCorpus,
            ProfileCorpusStats structuralCorpus,
            ProfileCorpusStats passageCorpus,
            PassageCorpusStats passageStats,
            ProfileLatency fixedLatency,
            ProfileLatency structuralLatency,
            ProfileLatency passageLatency,
            Map<String, Long> sharedQueryEmbeddingLatency,
            AggregateComparison queryMicro,
            AggregateComparison userMacro,
            Map<String, AggregateComparison> splitMetrics,
            Map<String, AggregateComparison> professionMetrics,
            Map<String, AggregateComparison> languageMetrics,
            Map<String, LengthBucketStats> structuralLengthBuckets,
            long structuralHeadingOnlyRank1Count,
            long passageHeadingOnlyRank1Count,
            List<QueryResult> queries,
            boolean sealedFinalOpened,
            boolean sealedFinalSearchExecuted,
            String currentFreshBaseline,
            String decision) {
    }

    record ContextOnlyFalseHit(
            String queryId,
            String candidateId,
            int passageRank,
            int contextRank,
            int firstDirectContextRank,
            String contextText,
            List<String> evidenceChildIds) {
    }

    enum RankOutcome {
        WIN,
        LOSS,
        TIE,
        NOT_APPLICABLE
    }

    record ParentContextQueryResult(
            String queryId,
            String userBundleId,
            Split split,
            String professionGroup,
            String language,
            String answerability,
            List<String> categories,
            boolean directSupport,
            double queryEmbeddingLatencyMs,
            String directRankOutcome,
            int directRankDelta,
            List<ContextOnlyFalseHit> contextOnlyFalseHits,
            QueryProfileResult passage,
            QueryProfileResult context) {
    }

    record ParentContextExperimentReport(
            int schemaVersion,
            String phase,
            String executedAt,
            String datasetVersion,
            String inputFreezeCommit,
            String dependency,
            Map<String, String> splitManifestHashes,
            OllamaBgeM3EmbeddingClient.ModelMetadata model,
            ParentContextContract contract,
            ProfileCorpusStats passageCorpus,
            ProfileCorpusStats contextCorpus,
            PassageCorpusStats passageStats,
            ParentContextCorpusStats contextStats,
            ProfileLatency passageLatency,
            ProfileLatency contextLatency,
            Map<String, Long> sharedQueryEmbeddingLatency,
            ParentContextAggregateComparison queryMicro,
            ParentContextAggregateComparison userMacro,
            Map<String, ParentContextAggregateComparison> splitMetrics,
            Map<String, ParentContextAggregateComparison> professionMetrics,
            Map<String, ParentContextAggregateComparison> languageMetrics,
            List<ParentContextQueryResult> queries,
            boolean sealedFinalOpened,
            boolean sealedFinalSearchExecuted,
            String currentFreshBaseline) {
    }

    private record SplitRun(
            DatasetSlice slice,
            PreparedProfile fixed,
            PreparedProfile structural,
            PreparedProfile passage,
            PassageCorpusStats passageStats,
            List<QueryResult> queryResults) {
    }

    private record ParentContextSplitRun(
            DatasetSlice slice,
            PreparedProfile passage,
            PreparedProfile context,
            PassageCorpusStats passageStats,
            ParentContextCorpusStats contextStats,
            List<ParentContextQueryResult> queryResults) {
    }
}
