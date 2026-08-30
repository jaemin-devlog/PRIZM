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
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Runs the A/B raw Dense comparison without a database, threshold, reranking, or query policy. */
final class SearchV3DenseAblationEngine {

    static final String FIXED_PROFILE = "A_FIXED_800_OVERLAP_120_BGE_M3_DENSE";
    static final String STRUCTURAL_PROFILE =
            "B2_STRUCTURAL_CHILD_CONTEXT_ONLY_HEADING_BGE_M3_DENSE";
    private static final int FIXED_MAX_CHARACTERS = 800;
    private static final int FIXED_OVERLAP_CHARACTERS = 120;
    private static final List<Integer> CUTOFFS = List.of(5, 10, 20, 50);
    private static final List<String> LENGTH_BUCKETS = List.of("01-10", "11-20", "21-40", "41-80", "81+");

    private final StructuralBlockParser parser = new StructuralBlockParser();
    private final StructuralEvidenceChildBuilder childBuilder = new StructuralEvidenceChildBuilder();
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
        return run(slices, embeddingClient, modelMetadata, "PRZ-026-PHASE-1-ADJUSTMENT");
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
            assertSameEvaluationInputs(slice, fixedBuild, structuralBuild);

            PreparedProfile fixed = prepare(fixedBuild, embeddingClient);
            PreparedProfile structural = prepare(structuralBuild, embeddingClient);
            List<QueryResult> queryResults = evaluateQueries(slice, fixed, structural, embeddingClient);
            splitRuns.add(new SplitRun(slice, fixed, structural, queryResults));
        }

        List<QueryResult> allQueryResults = splitRuns.stream()
                .flatMap(run -> run.queryResults().stream())
                .toList();
        ProfileCorpusStats fixedCorpus = combineCorpusStats(
                FIXED_PROFILE, splitRuns.stream().map(run -> run.fixed().corpusStats()).toList());
        ProfileCorpusStats structuralCorpus = combineCorpusStats(
                STRUCTURAL_PROFILE, splitRuns.stream().map(run -> run.structural().corpusStats()).toList());
        ProfileLatency fixedLatency = combineLatency(
                FIXED_PROFILE, splitRuns.stream().map(run -> run.fixed().latency()).toList(),
                allQueryResults.stream().map(result -> result.fixed().totalLatencyMs()).toList(),
                allQueryResults.stream().map(result -> result.fixed().rankingLatencyMs()).toList());
        ProfileLatency structuralLatency = combineLatency(
                STRUCTURAL_PROFILE, splitRuns.stream().map(run -> run.structural().latency()).toList(),
                allQueryResults.stream().map(result -> result.structural().totalLatencyMs()).toList(),
                allQueryResults.stream().map(result -> result.structural().rankingLatencyMs()).toList());

        AggregatePair queryMicro = aggregatePair(allQueryResults);
        AggregatePair userMacro = macroPair(allQueryResults, QueryResult::userBundleId);
        Map<String, AggregatePair> splitMetrics = grouped(allQueryResults, result -> result.split().manifestName());
        Map<String, AggregatePair> professionMetrics = grouped(allQueryResults, QueryResult::professionGroup);
        Map<String, AggregatePair> languageMetrics = grouped(allQueryResults, QueryResult::language);
        Map<String, Long> queryEmbeddingLatency = latencySummary(
                allQueryResults.stream().map(QueryResult::queryEmbeddingLatencyMs).toList());
        Map<String, LengthBucketStats> structuralLengthBuckets =
                lengthBucketStats(splitRuns, allQueryResults);
        long structuralHeadingOnlyRank1 = allQueryResults.stream()
                .map(result -> result.structural().rawDenseRanking())
                .filter(ranking -> !ranking.isEmpty())
                .filter(ranking -> StructuralBlockType.HEADING.name().equals(ranking.get(0).sourceBlockType()))
                .count();

        Map<String, String> splitManifests = new LinkedHashMap<>();
        slices.forEach(slice -> splitManifests.put(
                slice.split().manifestName(), slice.manifestCombinedSha256()));
        return new ExperimentReport(
                2,
                phase,
                Instant.now().toString(),
                datasetVersions.iterator().next(),
                "a9d093dd48e99a8d19675b3a8caa09c794d2888b",
                "DEPENDS_ON_PRZ_025",
                Map.copyOf(splitManifests),
                modelMetadata,
                new ComparisonContract(
                        800,
                        120,
                        StructuralEvidenceChildBuilder.DEFAULT_MAX_CHILD_CODE_POINTS,
                        OllamaBgeM3EmbeddingClient.MODEL,
                        OllamaBgeM3EmbeddingClient.DIMENSIONS,
                        OllamaBgeM3EmbeddingClient.SIMILARITY,
                        "RAW_DENSE_ONLY",
                        "USER_BUNDLE_ACTIVE_DOCUMENTS",
                        "SAME_QUERY_VECTOR_PER_A_B",
                        "EVIDENCE_PARENT_AND_HEADING_CONTEXT_NOT_RUN",
                        "SOURCE_TABLE_HEADER_CONTEXT_EXCEPTION_ACTIVE"),
                fixedCorpus,
                structuralCorpus,
                fixedLatency,
                structuralLatency,
                queryEmbeddingLatency,
                queryMicro,
                userMacro,
                Map.copyOf(splitMetrics),
                Map.copyOf(professionMetrics),
                Map.copyOf(languageMetrics),
                Map.copyOf(structuralLengthBuckets),
                structuralHeadingOnlyRank1,
                List.copyOf(allQueryResults),
                false,
                false,
                "NOT_RUN",
                "NEEDS_ADJUSTMENT");
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
                        chunk.content(),
                        "FIXED_CHUNK",
                        null,
                        List.of(range),
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
                candidates.add(new CandidateSpec(
                        STRUCTURAL_PROFILE,
                        child.childId(),
                        document.userBundleId(),
                        provenance.documentId(),
                        provenance.versionId(),
                        child.sourceText(),
                        child.retrievalText(),
                        child.sourceBlockType().name(),
                        provenance.parentAnnotationCandidateId(),
                        List.of(new CandidateRange(
                                provenance.page(),
                                provenance.lineStart(),
                                provenance.lineEnd(),
                                provenance.codePointStart(),
                                provenance.codePointEnd())),
                        child.sourceBlockIds(),
                        child.contextSourceBlockIds()));
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

            QueryProfileResult fixedResult = queryResult(
                    query, fixedRanking, slice, queryBatch.elapsedNanos(), fixedRankNanos);
            QueryProfileResult structuralResult = queryResult(
                    query, structuralRanking, slice, queryBatch.elapsedNanos(), structuralRankNanos);
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
                    structuralResult));
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
                    value.spec().retrievalText(),
                    value.spec().parentAnnotationCandidateId(),
                    value.spec().sourceText().codePointCount(0, value.spec().sourceText().length()),
                    List.copyOf(unitIds),
                    List.copyOf(groupIds),
                    List.copyOf(parentIds)));
        }
        return List.copyOf(result);
    }

    private QueryProfileResult queryResult(
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
        return !unit.sourceSpans().isEmpty()
                && unit.sourceSpans().stream().allMatch(span -> candidate.ranges().stream().anyMatch(range ->
                samePage(range.page(), span.page())
                        && range.codePointStart() <= span.codePointStart()
                        && range.codePointEnd() >= span.codePointEnd()));
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
            CandidateBuild structural) {
        Set<String> expectedVersions = slice.activeDocumentsByVersion().keySet();
        Set<String> fixedVersions = fixed.candidates().stream()
                .map(CandidateSpec::versionId).collect(Collectors.toSet());
        Set<String> structuralVersions = structural.candidates().stream()
                .map(CandidateSpec::versionId).collect(Collectors.toSet());
        if (!fixedVersions.equals(expectedVersions) || !structuralVersions.equals(expectedVersions)) {
            throw new IllegalStateException("A/B did not use the same ACTIVE source versions");
        }
        if (slice.queries().stream().anyMatch(query -> query.split() != slice.split())) {
            throw new IllegalStateException("A/B query split mismatch");
        }
    }

    private AggregatePair aggregatePair(List<QueryResult> values) {
        return new AggregatePair(
                aggregate(values, QueryResult::fixed),
                aggregate(values, QueryResult::structural));
    }

    private AggregatePair macroPair(List<QueryResult> values, Function<QueryResult, String> key) {
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
        return new AggregatePair(averageAggregates(fixed), averageAggregates(structural));
    }

    private Map<String, AggregatePair> grouped(
            List<QueryResult> values,
            Function<QueryResult, String> key) {
        Map<String, List<QueryResult>> groups = values.stream().collect(Collectors.groupingBy(
                key, LinkedHashMap::new, Collectors.toList()));
        Map<String, AggregatePair> result = new LinkedHashMap<>();
        groups.forEach((name, group) -> result.put(name, aggregatePair(group)));
        return result;
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
            String retrievalText,
            String sourceBlockType,
            String parentAnnotationCandidateId,
            List<CandidateRange> ranges,
            List<String> sourceBlockIds,
            List<String> contextBlockIds) {
    }

    record CandidateBuild(
            String profile,
            DatasetSlice slice,
            List<CandidateSpec> candidates,
            long constructionNanos,
            ProfileCorpusStats corpusStats) {
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
            String retrievalText,
            String parentAnnotationCandidateId,
            int sourceCodePointLength,
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
            QueryProfileResult structural) {
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

    record AggregatePair(AggregateMetrics fixed, AggregateMetrics structural) {
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
            String embeddingModel,
            int embeddingDimensions,
            String similarity,
            String ranking,
            String scope,
            String queryVectorPolicy,
            String evidenceParentContextStatus,
            String tableHeaderContextPolicy) {
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
            ProfileLatency fixedLatency,
            ProfileLatency structuralLatency,
            Map<String, Long> sharedQueryEmbeddingLatency,
            AggregatePair queryMicro,
            AggregatePair userMacro,
            Map<String, AggregatePair> splitMetrics,
            Map<String, AggregatePair> professionMetrics,
            Map<String, AggregatePair> languageMetrics,
            Map<String, LengthBucketStats> structuralLengthBuckets,
            long structuralHeadingOnlyRank1Count,
            List<QueryResult> queries,
            boolean sealedFinalOpened,
            boolean sealedFinalSearchExecuted,
            String currentFreshBaseline,
            String decision) {
    }

    private record SplitRun(
            DatasetSlice slice,
            PreparedProfile fixed,
            PreparedProfile structural,
            List<QueryResult> queryResults) {
    }
}
