package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.CandidateProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvaluationTrack;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvidenceChildProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.FreezeInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.QueryProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.VerifiedCandidates;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.DatasetSlice;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.ExpectedEvidence;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.GoldSpan;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.GoldUnit;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.Query;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.Split;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.GoldAnswerability;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.GoldAspect;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.GoldAspectExpression;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.ExpectedGoldEvidence;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.OracleRelation;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.QueryGold;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticOracleDataset.ExpectedRelation;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticOracleDataset.StressAspect;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticOracleDataset.StressGoldQuery;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticOracleDataset.StressGoldSlice;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticOracleDataset.StressGoldUnit;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Opens source-grounded evaluation Gold only after receiving a verified semantic candidate freeze,
 * then maps Gold source spans onto the already-frozen EvidenceChild inventory.
 *
 * <p>Gold unit IDs never become candidate identities. A relation is attached to a frozen passage
 * only when its EvidenceChild union has the same owner/document/version/page and covers every
 * source span of the Gold Evidence Unit. Expected relations are preserved independently of
 * candidate mapping so a DIRECT_SUPPORT unit outside Top20 remains an observable retrieval miss.
 */
final class SearchV3SemanticOracleGoldJoiner {

    private static final Pattern UUID = Pattern.compile(
            "(?i)^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    private final SearchV3DenseAblationDataset historicalDataset;
    private final SearchV3SemanticOracleDataset semanticDataset;
    private final Function<SearchV3B3CandidateReplay.Suite, SearchV3B3CandidateReplay.Replay> replayLoader;

    SearchV3SemanticOracleGoldJoiner() {
        this(
                new SearchV3DenseAblationDataset(),
                new SearchV3SemanticOracleDataset(),
                suite -> new SearchV3B3CandidateReplay().load(suite));
    }

    SearchV3SemanticOracleGoldJoiner(
            SearchV3DenseAblationDataset historicalDataset,
            SearchV3SemanticOracleDataset semanticDataset,
            Function<SearchV3B3CandidateReplay.Suite, SearchV3B3CandidateReplay.Replay> replayLoader) {
        this.historicalDataset = Objects.requireNonNull(historicalDataset, "historicalDataset");
        this.semanticDataset = Objects.requireNonNull(semanticDataset, "semanticDataset");
        this.replayLoader = Objects.requireNonNull(replayLoader, "replayLoader");
    }

    List<QueryGold> loadHistoricalGold(
            VerifiedCandidates verifiedCandidates,
            SearchV3B3CandidateReplay.Suite suite) {
        Objects.requireNonNull(suite, "suite");
        if (suite == SearchV3B3CandidateReplay.Suite.HISTORICAL_LONG_FORM) {
            throw new IllegalArgumentException(
                    "historicalLongForm duplicates longFormExpansion and is not an Oracle input suite");
        }
        FreezeInput input = verifiedSemanticInput(verifiedCandidates);
        SearchV3B3CandidateReplay.Replay sourceReplay = Objects.requireNonNull(
                replayLoader.apply(suite), "B3 replay");
        requireHistoricalFreezeIdentity(input, suite, sourceReplay);

        // Loading questions/evidence happens only after the verified, exact Gold-free freeze checks.
        Map<String, HistoricalGold> goldByQuery = new LinkedHashMap<>();
        Map<String, SearchV3B3CandidateReplay.QueryIdentity> approvedById = suite.queryInventory().stream()
                .collect(Collectors.toMap(
                        SearchV3B3CandidateReplay.QueryIdentity::queryId,
                        value -> value,
                        (left, right) -> {
                            throw new IllegalStateException("duplicate approved historical query");
                        },
                        LinkedHashMap::new));
        for (Split split : Split.values()) {
            DatasetSlice slice = loadHistoricalSlice(suite, split);
            String approvedManifestHash = suite.splitManifestHashes().get(split.manifestName());
            if (!Objects.equals(approvedManifestHash, slice.manifestCombinedSha256())) {
                throw new IllegalStateException(
                        "historical Gold manifest no longer matches the frozen B3 replay");
            }
            for (Query query : slice.queries()) {
                SearchV3B3CandidateReplay.QueryIdentity approved = approvedById.get(query.queryId());
                String profession = profession(slice, query.userBundleId());
                if (approved == null
                        || !approved.owner().equals(query.userBundleId())
                        || !approved.split().equals(query.split().manifestName())
                        || !approved.profession().equals(profession)
                        || !approved.language().equals(query.language())) {
                    throw new IllegalStateException(
                            "historical Gold query identity/profession/language drifted");
                }
                HistoricalGold previous = goldByQuery.put(query.queryId(), new HistoricalGold(slice, query));
                if (previous != null) {
                    throw new IllegalStateException("duplicate historical Gold query: " + query.queryId());
                }
            }
        }
        requireExactGoldInventory(input, goldByQuery.keySet());
        return input.queries().stream()
                .map(candidateQuery -> mapHistorical(candidateQuery, goldByQuery.get(candidateQuery.queryId())))
                .toList();
    }

    List<QueryGold> loadStressGold(VerifiedCandidates verifiedCandidates) {
        FreezeInput input = verifiedSemanticInput(verifiedCandidates);
        requireStressRuntimeIdentity(input);

        // The dataset loader repeats the verified suite/version/runtime-hash/query-inventory guard
        // before reading a single Gold payload.
        Map<String, StressGold> goldByQuery = new LinkedHashMap<>();
        for (Split split : Split.values()) {
            StressGoldSlice slice = semanticDataset.loadStressGold(split, verifiedCandidates);
            for (StressGoldQuery query : slice.questions()) {
                StressGold previous = goldByQuery.put(query.queryId(), new StressGold(slice, query));
                if (previous != null) {
                    throw new IllegalStateException("duplicate semantic stress Gold query: " + query.queryId());
                }
            }
        }
        requireExactGoldInventory(input, goldByQuery.keySet());
        return input.queries().stream()
                .map(candidateQuery -> mapStress(candidateQuery, goldByQuery.get(candidateQuery.queryId())))
                .toList();
    }

    private FreezeInput verifiedSemanticInput(VerifiedCandidates verifiedCandidates) {
        Objects.requireNonNull(verifiedCandidates, "verifiedCandidates");
        FreezeInput input = SearchV3CandidateFreeze.verify(verifiedCandidates.frozen()).frozen().input();
        if (input.track() != EvaluationTrack.SEMANTIC) {
            throw new IllegalArgumentException("semantic Gold join rejects typed candidate freezes");
        }
        return input;
    }

    private void requireHistoricalFreezeIdentity(
            FreezeInput input,
            SearchV3B3CandidateReplay.Suite suite,
            SearchV3B3CandidateReplay.Replay sourceReplay) {
        if (!suite.rootNode().equals(input.suite())
                || !suite.datasetVersion().equals(input.datasetVersion())
                || !sourceReplay.canonicalSha256().equals(input.sourceArtifactSha256())) {
            throw new IllegalArgumentException(
                    "historical Gold requires the exact verified B3 replay candidate freeze");
        }
        Map<String, String> expected = suite.queryInventory().stream().collect(Collectors.toMap(
                SearchV3B3CandidateReplay.QueryIdentity::queryId,
                value -> value.owner() + "\0" + value.split(),
                (left, right) -> {
                    throw new IllegalStateException("duplicate approved replay query inventory");
                },
                LinkedHashMap::new));
        requireExactCandidateInventory(input, expected);
        requireReplayCandidateParity(input, sourceReplay);
    }

    private void requireReplayCandidateParity(
            FreezeInput input,
            SearchV3B3CandidateReplay.Replay sourceReplay) {
        Map<String, List<SearchV3B3CandidateReplay.ReplayCandidate>> replayByQuery = sourceReplay.candidates()
                .stream()
                .collect(Collectors.groupingBy(
                        SearchV3B3CandidateReplay.ReplayCandidate::query,
                        LinkedHashMap::new,
                        Collectors.toList()));
        for (QueryProjection query : input.queries()) {
            List<SearchV3B3CandidateReplay.ReplayCandidate> expected = replayByQuery.remove(query.queryId());
            if (expected == null || expected.size() != query.rankedCandidates().size()) {
                throw new IllegalArgumentException(
                        "verified freeze candidate set differs from frozen B3 replay: " + query.queryId());
            }
            for (int index = 0; index < expected.size(); index++) {
                SearchV3B3CandidateReplay.ReplayCandidate replayCandidate = expected.get(index);
                CandidateProjection candidate = query.rankedCandidates().get(index);
                List<String> childIds = candidate.evidenceChildren().stream()
                        .map(EvidenceChildProjection::evidenceChildId)
                        .toList();
                if (replayCandidate.rank() != candidate.rank()
                        || !replayCandidate.candidateId().equals(candidate.candidateId())
                        || Double.compare(replayCandidate.cosine(), candidate.cosineScore()) != 0
                        || !replayCandidate.owner().equals(candidate.userBundleId())
                        || !replayCandidate.owner().equals(query.userBundleId())
                        || !replayCandidate.doc().equals(candidate.documentId())
                        || !replayCandidate.version().equals(candidate.versionId())
                        || !replayCandidate.parent().equals(candidate.parentAnnotationCandidateId())
                        || !replayCandidate.evidenceChildIds().equals(childIds)
                        || !replayCandidate.sourceSha256().equals(candidate.sourceTextSha256())
                        || !replayCandidate.retrievalSha256().equals(candidate.retrievalTextSha256())) {
                    throw new IllegalArgumentException(
                            "verified candidate identity/source differs from frozen B3 replay: "
                                    + candidate.candidateId());
                }
            }
        }
        if (!replayByQuery.isEmpty()) {
            throw new IllegalArgumentException("verified freeze omitted B3 replay candidate queries");
        }
    }

    private void requireStressRuntimeIdentity(FreezeInput input) {
        if (!SearchV3SemanticOracleDataset.STRESS_SUITE.equals(input.suite())
                || !SearchV3SemanticOracleDataset.STRESS_VERSION.equals(input.datasetVersion())
                || !SearchV3SemanticOracleDataset.STRESS_RUNTIME_SHA256.equals(input.sourceArtifactSha256())) {
            throw new IllegalArgumentException(
                    "semantic stress Gold requires the exact verified runtime candidate freeze");
        }
        Map<String, String> expected = new LinkedHashMap<>();
        for (Split split : Split.values()) {
            semanticDataset.loadStressRuntime(split).questions().forEach(question -> {
                String previous = expected.put(
                        question.queryId(), question.userBundleId() + "\0" + split.manifestName());
                if (previous != null) {
                    throw new IllegalStateException("duplicate semantic stress runtime query");
                }
            });
        }
        requireExactCandidateInventory(input, expected);
    }

    private void requireExactCandidateInventory(FreezeInput input, Map<String, String> expected) {
        Map<String, String> actual = new LinkedHashMap<>();
        for (QueryProjection query : input.queries()) {
            String previous = actual.put(
                    query.queryId(), query.userBundleId() + "\0" + query.split());
            if (previous != null) {
                throw new IllegalArgumentException("duplicate query in verified candidate freeze");
            }
        }
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException("verified candidate and Gold query inventories differ");
        }
    }

    private void requireExactGoldInventory(FreezeInput input, Set<String> goldQueryIds) {
        Set<String> candidateQueryIds = input.queries().stream()
                .map(QueryProjection::queryId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!candidateQueryIds.equals(goldQueryIds)) {
            throw new IllegalStateException("verified candidate and loaded Gold inventories differ");
        }
    }

    private DatasetSlice loadHistoricalSlice(SearchV3B3CandidateReplay.Suite suite, Split split) {
        return switch (suite) {
            case ORIGINAL_SEED -> historicalDataset.load(split);
            case LONG_FORM_EXPANSION -> historicalDataset.loadLongForm(split);
            case INDEPENDENT_ROBUSTNESS -> historicalDataset.loadRobustness(split);
            case HISTORICAL_LONG_FORM -> throw new IllegalArgumentException(
                    "historicalLongForm is not an Oracle input suite");
        };
    }

    private QueryGold mapHistorical(QueryProjection candidateQuery, HistoricalGold historical) {
        if (historical == null) {
            throw new IllegalStateException("missing historical Gold query: " + candidateQuery.queryId());
        }
        Query query = historical.query();
        DatasetSlice slice = historical.slice();
        requireQueryIdentity(candidateQuery, query.userBundleId(), query.split().manifestName());
        String profession = profession(slice, query.userBundleId());
        List<ExpectedUnit> expected = query.allExpectedEvidence().stream()
                .map(value -> historicalExpected(slice, query, value))
                .toList();
        GoldAspectExpression expression = new GoldAspectExpression(
                query.aspectExpression().operator(),
                query.aspectExpression().requiredAspectIds(),
                query.aspectExpression().minShouldMatch());
        List<GoldAspect> aspects = query.aspects().stream().map(aspect -> new GoldAspect(
                aspect.aspectId(),
                aspect.required(),
                aspect.minEvidenceGroups(),
                aspect.requiredEvidenceGroupIds(),
                aspect.expectedEvidence().stream()
                        .map(value -> {
                            ExpectedUnit unit = historicalExpected(slice, query, value);
                            return new ExpectedGoldEvidence(
                                    unit.evidenceUnitId(), unit.evidenceGroupId(), unit.relation());
                        })
                        .toList())).toList();
        return queryGold(
                candidateQuery,
                profession,
                query.language(),
                query.categories(),
                answerability(query.answerability()),
                expression,
                aspects,
                expected);
    }

    private ExpectedUnit historicalExpected(DatasetSlice slice, Query query, ExpectedEvidence expected) {
        rejectRuntimeGoldId(expected.evidenceUnitId());
        GoldUnit unit = slice.units().get(expected.evidenceUnitId());
        if (unit == null || !query.userBundleId().equals(unit.userBundleId())) {
            throw new IllegalStateException("historical Gold unit owner/inventory mismatch");
        }
        rejectRuntimeGoldId(unit.groupId());
        rejectRuntimeGoldId(unit.parentId());
        rejectRuntimeGoldId(unit.sourceFactId());
        List<SourceSpan> spans = unit.sourceSpans().stream().map(SourceSpan::from).toList();
        return new ExpectedUnit(unit.evidenceUnitId(), unit.groupId(), relation(expected.supportRelation()),
                unit.userBundleId(), unit.documentId(), unit.versionId(), spans);
    }

    private QueryGold mapStress(QueryProjection candidateQuery, StressGold stress) {
        if (stress == null) {
            throw new IllegalStateException("missing semantic stress Gold query: " + candidateQuery.queryId());
        }
        StressGoldQuery query = stress.query();
        requireQueryIdentity(candidateQuery, query.userBundleId(), stress.slice().runtime().split().manifestName());
        List<ExpectedUnit> expected = query.expectedRelations().stream()
                .map(value -> stressExpected(stress.slice(), query, value))
                .toList();
        GoldAspectExpression expression = new GoldAspectExpression(
                query.aspectExpression().operator(),
                query.aspectExpression().requiredAspectIds(),
                query.aspectExpression().minShouldMatch());
        List<GoldAspect> aspects = query.aspects().stream()
                .map(value -> stressAspect(stress.slice(), query, value))
                .toList();
        return queryGold(
                candidateQuery,
                query.professionGroup(),
                query.language(),
                query.categories(),
                answerability(query.answerability()),
                expression,
                aspects,
                expected);
    }

    private GoldAspect stressAspect(
            StressGoldSlice slice,
            StressGoldQuery query,
            StressAspect aspect) {
        List<ExpectedGoldEvidence> expected = aspect.expectedRelations().stream()
                .map(value -> {
                    ExpectedUnit unit = stressExpected(slice, query, value);
                    return new ExpectedGoldEvidence(
                            unit.evidenceUnitId(), unit.evidenceGroupId(), unit.relation());
                })
                .toList();
        return new GoldAspect(
                aspect.aspectId(),
                aspect.required(),
                aspect.minEvidenceGroups(),
                aspect.requiredEvidenceGroupIds(),
                expected);
    }

    private ExpectedUnit stressExpected(
            StressGoldSlice slice,
            StressGoldQuery query,
            ExpectedRelation expected) {
        rejectRuntimeGoldId(expected.evidenceUnitId());
        StressGoldUnit unit = slice.units().get(expected.evidenceUnitId());
        if (unit == null || !query.userBundleId().equals(unit.userBundleId())
                || !expected.evidenceGroupId().equals(unit.evidenceGroupId())) {
            throw new IllegalStateException("semantic stress Gold unit owner/inventory mismatch");
        }
        rejectRuntimeGoldId(unit.evidenceGroupId());
        rejectRuntimeGoldId(unit.parentId());
        rejectRuntimeGoldId(unit.baseParentId());
        rejectRuntimeGoldId(unit.sourceFactId());
        SourceSpan span = new SourceSpan(
                unit.documentId(), unit.versionId(), unit.page(), unit.codePointStart(),
                unit.codePointEnd(), unit.sourceText(), unit.sourceTextSha256());
        return new ExpectedUnit(unit.evidenceUnitId(), unit.evidenceGroupId(), relation(expected.relation()),
                unit.userBundleId(), unit.documentId(), unit.versionId(), List.of(span));
    }

    private QueryGold queryGold(
            QueryProjection candidateQuery,
            String profession,
            String language,
            List<String> categories,
            GoldAnswerability answerability,
            GoldAspectExpression aspectExpression,
            List<GoldAspect> aspects,
            List<ExpectedUnit> expected) {
        Map<String, List<String>> coveredByCandidate = new LinkedHashMap<>();
        for (CandidateProjection candidate : candidateQuery.rankedCandidates()) {
            List<String> covered = expected.stream()
                    .filter(unit -> candidateCovers(candidateQuery.userBundleId(), candidate, unit))
                    .map(ExpectedUnit::evidenceUnitId)
                    .distinct()
                    .toList();
            if (!covered.isEmpty()) {
                coveredByCandidate.put(candidate.candidateId(), covered);
            }
        }
        return new QueryGold(
                candidateQuery.queryId(),
                candidateQuery.userBundleId(),
                profession,
                language,
                categories,
                answerability,
                aspectExpression,
                aspects,
                Map.copyOf(coveredByCandidate));
    }

    private boolean candidateCovers(
            String queryOwner,
            CandidateProjection candidate,
            ExpectedUnit unit) {
        if (!queryOwner.equals(unit.userBundleId())
                || !queryOwner.equals(candidate.userBundleId())
                || !unit.documentId().equals(candidate.documentId())
                || !unit.versionId().equals(candidate.versionId())
                || unit.spans().isEmpty()) {
            return false;
        }
        return unit.spans().stream().allMatch(span -> candidate.evidenceChildren().stream()
                .anyMatch(child -> childContains(child, span)));
    }

    private boolean childContains(EvidenceChildProjection child, SourceSpan span) {
        if (!child.documentId().equals(span.documentId())
                || !child.versionId().equals(span.versionId())
                || !Objects.equals(child.page(), span.page())
                || child.codePointStart() > span.codePointStart()
                || child.codePointEnd() < span.codePointEnd()) {
            return false;
        }
        int relativeStart = span.codePointStart() - child.codePointStart();
        int relativeEnd = span.codePointEnd() - child.codePointStart();
        int childLength = child.sourceText().codePointCount(0, child.sourceText().length());
        if (relativeStart < 0 || relativeEnd > childLength || relativeEnd <= relativeStart) {
            return false;
        }
        int charStart = child.sourceText().offsetByCodePoints(0, relativeStart);
        int charEnd = child.sourceText().offsetByCodePoints(0, relativeEnd);
        String grounded = child.sourceText().substring(charStart, charEnd);
        return grounded.equals(span.sourceText())
                && SearchV3CandidateFreeze.sha256(grounded).equals(span.sourceTextSha256());
    }

    private void requireQueryIdentity(QueryProjection query, String owner, String split) {
        if (!query.userBundleId().equals(owner) || !query.split().equals(split)) {
            throw new IllegalStateException("candidate/Gold query owner or split mismatch: " + query.queryId());
        }
    }

    private String profession(DatasetSlice slice, String owner) {
        return slice.bundles().stream()
                .filter(value -> value.userBundleId().equals(owner))
                .map(SearchV3DenseAblationDataset.UserBundle::professionGroup)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing Gold profession owner"));
    }

    private GoldAnswerability answerability(String value) {
        try {
            return GoldAnswerability.valueOf(value);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalStateException("unknown Gold answerability: " + value, exception);
        }
    }

    private OracleRelation relation(String value) {
        try {
            return OracleRelation.valueOf(value);
        }
        catch (IllegalArgumentException exception) {
            throw new IllegalStateException("unknown Gold support relation: " + value, exception);
        }
    }

    private void rejectRuntimeGoldId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("Gold Evidence Unit ID must be non-blank");
        }
        String normalized = id.toLowerCase(java.util.Locale.ROOT);
        if (UUID.matcher(id).matches()
                || normalized.contains("chunkid")
                || normalized.contains("runtime")
                || normalized.contains("db-parent")) {
            throw new IllegalStateException("runtime-generated Gold ID is forbidden: " + id);
        }
    }

    private record HistoricalGold(DatasetSlice slice, Query query) {
    }

    private record StressGold(StressGoldSlice slice, StressGoldQuery query) {
    }

    private record ExpectedUnit(
            String evidenceUnitId,
            String evidenceGroupId,
            OracleRelation relation,
            String userBundleId,
            String documentId,
            String versionId,
            List<SourceSpan> spans) {

        ExpectedUnit {
            spans = List.copyOf(spans);
        }
    }

    private record SourceSpan(
            String documentId,
            String versionId,
            Integer page,
            int codePointStart,
            int codePointEnd,
            String sourceText,
            String sourceTextSha256) {

        static SourceSpan from(GoldSpan span) {
            return new SourceSpan(
                    span.documentId(), span.versionId(), span.page(), span.codePointStart(),
                    span.codePointEnd(), span.text(), span.textSha256());
        }
    }
}
