package com.prizm.search.evaluation.trace;

import static com.prizm.search.evaluation.trace.SearchDecisionTrace.Decision.PASS;
import static com.prizm.search.evaluation.trace.SearchDecisionTrace.Decision.REJECT;
import static com.prizm.search.evaluation.trace.SearchDecisionTrace.Decision.REMOVED;
import static com.prizm.search.evaluation.trace.SearchDecisionTrace.Decision.SELECTED;

import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.search.dto.response.CareerEvidenceSearchResponse;
import com.prizm.search.dto.response.CareerEvidenceSearchV2Response;
import com.prizm.search.profile.CompositeSearchProfile;
import com.prizm.search.profile.NaturalLanguageQueryFallback;
import com.prizm.search.profile.NumericAnchorRescueProfile;
import com.prizm.search.profile.NumericQueryAnchors;
import com.prizm.search.profile.SearchIntent;
import com.prizm.search.profile.ShortGeneralExactTokenRescueProfile;
import com.prizm.search.repository.VectorSearchRepository;
import com.prizm.search.repository.VectorSearchResult;
import com.prizm.search.service.EvidenceExpansionService;
import com.prizm.search.service.EvidencePresentation;
import com.prizm.search.service.SearchService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

/**
 * Replays the production search orchestration with the real production components and exposes the
 * otherwise private stage decisions. It is compiled only in the {@code searchEvaluation} source
 * set and fails closed unless its final response is identical to {@link SearchService}.
 */
public final class ProductionSearchDecisionTracer {

    private static final int MAX_RESULTS = 5;

    private final EmbeddingService embeddingService;
    private final EmbeddingValidator embeddingValidator;
    private final VectorSearchRepository repository;
    private final CompositeSearchProfile profile;
    private final ShortGeneralExactTokenRescueProfile shortRescue;
    private final NumericAnchorRescueProfile numericRescue;
    private final EvidenceExpansionService expansionService;
    private final SearchService searchService;

    public ProductionSearchDecisionTracer(
            EmbeddingService embeddingService,
            EmbeddingValidator embeddingValidator,
            VectorSearchRepository repository,
            CompositeSearchProfile profile,
            EvidenceExpansionService expansionService,
            SearchService searchService) {
        this.embeddingService = embeddingService;
        this.embeddingValidator = embeddingValidator;
        this.repository = repository;
        this.profile = profile;
        this.shortRescue = new ShortGeneralExactTokenRescueProfile(profile);
        this.numericRescue = new NumericAnchorRescueProfile(profile);
        this.expansionService = expansionService;
        this.searchService = searchService;
    }

    public SearchDecisionTrace trace(Long ownerUserId, String originalQuery) {
        try {
            return traceChecked(ownerUserId, originalQuery);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(
                    "Production search internals changed; evaluation trace adapter must be reviewed.",
                    exception);
        }
    }

    private SearchDecisionTrace traceChecked(Long ownerUserId, String originalQuery)
            throws ReflectiveOperationException {
        TraceAccumulator accumulator = new TraceAccumulator();
        float[] originalEmbedding = embed(originalQuery);
        List<VectorSearchResult> originalCandidates =
                repository.findCareerEvidenceCandidates(ownerUserId, originalEmbedding);
        accumulator.retrieved("ORIGINAL", originalCandidates);

        List<VectorSearchResult> selected = List.of();
        Set<String> guardedIdentifiers = profile.strongIdentifiersForEvidenceGuard(originalQuery);
        boolean identifierGuardPassed = guardedIdentifiers.isEmpty()
                || repository.hasAllActiveIdentifiers(ownerUserId, guardedIdentifiers);
        if (!originalCandidates.isEmpty() && identifierGuardPassed) {
            AttemptData original = attempt(
                    "ORIGINAL",
                    SearchDecisionTrace.QueryVariantType.ORIGINAL,
                    originalQuery,
                    originalQuery,
                    List.of(originalQuery),
                    originalCandidates,
                    originalCandidates,
                    NaturalLanguageQueryFallback.requiresDirectAnchor(originalQuery));
            accumulator.add(original);
            selected = original.postFilter();

            boolean fallbackAllowed = profile.resolveIntent(originalQuery) == SearchIntent.GENERAL
                    || NaturalLanguageQueryFallback.isExperienceRequest(originalQuery);
            if (selected.isEmpty() && fallbackAllowed) {
                List<String> variants = NaturalLanguageQueryFallback.variants(originalQuery).stream()
                        .filter(variant -> NaturalLanguageQueryFallback.preservesRequiredAnchors(
                                originalQuery, variant, guardedIdentifiers))
                        .toList();
                List<VectorSearchResult> merged = originalCandidates;
                List<String> anchorQueries = new ArrayList<>(List.of(originalQuery));
                for (int index = 0; index < variants.size(); index++) {
                    String fallbackQuery = variants.get(index);
                    String variantId = "FALLBACK_" + (index + 1);
                    List<VectorSearchResult> incoming = repository.findCareerEvidenceCandidates(
                            ownerUserId, embed(fallbackQuery));
                    accumulator.retrieved(variantId, incoming);
                    merged = mergeCandidates(merged, incoming);
                    anchorQueries.add(fallbackQuery);
                    AttemptData fallback = attempt(
                            variantId,
                            SearchDecisionTrace.QueryVariantType.FALLBACK,
                            originalQuery,
                            fallbackQuery,
                            anchorQueries,
                            incoming,
                            merged,
                            true);
                    accumulator.add(fallback);
                    selected = fallback.postFilter();
                    if (!selected.isEmpty()) {
                        break;
                    }
                }
            }

            if (selected.isEmpty()) {
                Set<String> normalizedNumbers = NumericQueryAnchors.extract(originalQuery).stream()
                        .filter(NumericQueryAnchors.NumericAnchor::hasUnit)
                        .map(NumericQueryAnchors.NumericAnchor::number)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                if (!normalizedNumbers.isEmpty()) {
                    List<VectorSearchResult> numericCandidates = repository.findNumericAnchorCandidates(
                            ownerUserId, originalEmbedding, normalizedNumbers);
                    accumulator.retrieved("NUMERIC_RESCUE", numericCandidates);
                    AttemptData numeric = numericAttempt(originalQuery, numericCandidates);
                    accumulator.add(numeric);
                    selected = numeric.postFilter();
                }
            }
        } else if (!originalCandidates.isEmpty()) {
            accumulator.add(identifierGuardAttempt(
                    originalQuery, originalCandidates, guardedIdentifiers));
        } else {
            accumulator.add(emptyOriginalAttempt(originalQuery));
        }

        List<VectorSearchResult> beforePresentationDedup = selected;
        selected = deduplicatePresentation(selected);
        accumulator.recordPresentationDedup(beforePresentationDedup, selected);

        List<SearchDecisionTrace.FinalResultTrace> finalResults = new ArrayList<>();
        List<SearchDecisionTrace.EvidenceTrace> localizations = new ArrayList<>();
        for (int index = 0; index < selected.size(); index++) {
            VectorSearchResult result = selected.get(index);
            EvidencePresentation evidence = expansionService.select(ownerUserId, originalQuery, result);
            finalResults.add(new SearchDecisionTrace.FinalResultTrace(
                    index + 1,
                    result.chunkId(),
                    result.documentId(),
                    result.documentVersionId(),
                    result.score(),
                    result.distance()));
            localizations.add(new SearchDecisionTrace.EvidenceTrace(
                    index + 1,
                    result.chunkId(),
                    evidence.evidenceChunkId(),
                    evidence.evidenceSourceType(),
                    evidence.evidenceSourceIndex(),
                    evidence.evidenceSourceLabel(),
                    evidence.snippet(),
                    !result.chunkId().equals(evidence.evidenceChunkId())));
        }

        CareerEvidenceSearchV2Response production =
                searchService.searchCareerEvidenceV2(ownerUserId, originalQuery);
        List<String> parityErrors = parityErrors(production, selected, localizations);
        if (!parityErrors.isEmpty()) {
            throw new IllegalStateException("Trace diverged from production response: " + parityErrors);
        }

        accumulator.markFinal(selected);
        return new SearchDecisionTrace(
                1,
                ownerUserId,
                originalQuery,
                production.state().name(),
                accumulator.variantTraces(),
                accumulator.candidateTraces(selected),
                accumulator.sourceGroups,
                accumulator.queryGroups,
                accumulator.rankings,
                finalResults,
                localizations,
                true,
                parityErrors);
    }

    private AttemptData attempt(
            String variantId,
            SearchDecisionTrace.QueryVariantType variantType,
            String originalQuery,
            String searchQuery,
            List<String> anchorQueries,
            List<VectorSearchResult> incoming,
            List<VectorSearchResult> mergedCandidates,
            boolean requireDirectAnchor) throws ReflectiveOperationException {
        StageData stages = stages(variantId, searchQuery, mergedCandidates);
        List<VectorSearchResult> topFive = shortRescue.apply(searchQuery, mergedCandidates).results();
        List<VectorSearchResult> postFilter = topFive;
        Map<Long, List<String>> postFilterReasons = new LinkedHashMap<>();
        if (requireDirectAnchor) {
            postFilter = postFilter.stream()
                    .filter(candidate -> {
                        boolean pass = anchorQueries.stream().anyMatch(anchor ->
                                NaturalLanguageQueryFallback.hasDirectAnchor(anchor, candidate.content()));
                        if (!pass) {
                            postFilterReasons.computeIfAbsent(candidate.chunkId(), ignored -> new ArrayList<>())
                                    .add("DIRECT_ANCHOR_REQUIRED");
                        }
                        return pass;
                    })
                    .toList();
        }
        boolean numericRequired = NumericQueryAnchors.extract(originalQuery).stream()
                .anyMatch(NumericQueryAnchors.NumericAnchor::hasUnit);
        if (numericRequired) {
            postFilter = postFilter.stream()
                    .filter(candidate -> {
                        boolean pass = NumericQueryAnchors.hasAllContextualMatches(
                                originalQuery, candidate.content());
                        if (!pass) {
                            postFilterReasons.computeIfAbsent(candidate.chunkId(), ignored -> new ArrayList<>())
                                    .add("NUMERIC_MISMATCH");
                        }
                        return pass;
                    })
                    .toList();
        }
        return new AttemptData(
                variantId,
                variantType,
                searchQuery,
                List.copyOf(anchorQueries),
                requireDirectAnchor,
                incoming,
                mergedCandidates,
                stages,
                topFive,
                postFilter,
                postFilterReasons);
    }

    private AttemptData numericAttempt(String query, List<VectorSearchResult> candidates)
            throws ReflectiveOperationException {
        List<VectorSearchResult> contextual = candidates.stream()
                .filter(candidate -> NumericQueryAnchors.hasContextualMatch(query, candidate.content()))
                .toList();
        Method promote = NumericAnchorRescueProfile.class.getDeclaredMethod(
                "promoteForEligibility", VectorSearchResult.class);
        promote.setAccessible(true);
        Map<Long, VectorSearchResult> originals = contextual.stream()
                .collect(Collectors.toMap(VectorSearchResult::chunkId, Function.identity()));
        List<VectorSearchResult> promoted = new ArrayList<>();
        for (VectorSearchResult candidate : contextual) {
            promoted.add((VectorSearchResult) promote.invoke(null, candidate));
        }
        StageData promotedStages = stages("NUMERIC_RESCUE", query, promoted);
        StageData originalStages = promotedStages.remap(originals);
        List<VectorSearchResult> selected = numericRescue.apply(query, candidates);
        Map<Long, List<String>> reasons = new LinkedHashMap<>();
        candidates.stream()
                .filter(candidate -> !originals.containsKey(candidate.chunkId()))
                .forEach(candidate -> reasons.put(candidate.chunkId(), List.of("NUMERIC_MISMATCH")));
        return new AttemptData(
                "NUMERIC_RESCUE",
                SearchDecisionTrace.QueryVariantType.NUMERIC_RESCUE,
                query,
                List.of(query),
                false,
                candidates,
                candidates,
                originalStages,
                selected,
                selected,
                reasons);
    }

    private AttemptData identifierGuardAttempt(
            String query,
            List<VectorSearchResult> candidates,
            Set<String> identifiers) {
        Map<Long, List<String>> reasons = candidates.stream().collect(Collectors.toMap(
                VectorSearchResult::chunkId,
                ignored -> List.of("IDENTIFIER_GUARD:" + String.join(",", identifiers)),
                (left, right) -> left,
                LinkedHashMap::new));
        return new AttemptData(
                "IDENTIFIER_GUARD",
                SearchDecisionTrace.QueryVariantType.IDENTIFIER_GUARD,
                query,
                List.of(query),
                false,
                candidates,
                candidates,
                StageData.empty(),
                List.of(),
                List.of(),
                reasons);
    }

    private AttemptData emptyOriginalAttempt(String query) {
        return new AttemptData(
                "ORIGINAL",
                SearchDecisionTrace.QueryVariantType.ORIGINAL,
                query,
                List.of(query),
                NaturalLanguageQueryFallback.requiresDirectAnchor(query),
                List.of(),
                List.of(),
                StageData.empty(),
                List.of(),
                List.of(),
                Map.of());
    }

    @SuppressWarnings("unchecked")
    private StageData stages(String variantId, String query, List<VectorSearchResult> candidates)
            throws ReflectiveOperationException {
        if (candidates.isEmpty()) {
            return StageData.empty();
        }
        Object signals = invoke(profile, "querySignals", query);
        SearchIntent intent = profile.resolveIntent(query);
        List<GroupView> sourceGroups = groups(invoke(
                profile, "consolidateSourceLocations", intent, query, signals, candidates));
        List<VectorSearchResult> sourceRepresentatives = representatives(sourceGroups);
        Set<Long> denseTopFiveChunkIds = candidates.stream()
                .limit(MAX_RESULTS)
                .map(VectorSearchResult::chunkId)
                .collect(Collectors.toSet());

        Map<Long, List<String>> eligibility = new LinkedHashMap<>();
        List<VectorSearchResult> eligible = new ArrayList<>();
        for (VectorSearchResult candidate : sourceRepresentatives) {
            List<String> reasons = (List<String>) invoke(
                    profile,
                    "rejectionReasons",
                    intent,
                    signals,
                    candidate,
                    denseTopFiveChunkIds.contains(candidate.chunkId()));
            eligibility.put(candidate.chunkId(), reasons);
            if (reasons.isEmpty()) {
                eligible.add(candidate);
            }
        }
        if (eligible.isEmpty()) {
            return new StageData(sourceGroups, sourceRepresentatives, eligibility,
                    List.of(), List.of(), List.of(), List.of());
        }

        List<GroupView> queryGroups = groups(invoke(
                profile, "consolidateQueryEvidence", intent, query, signals, eligible));
        List<VectorSearchResult> queryRepresentatives = representatives(queryGroups);
        List<VectorSearchResult> ranked = queryRepresentatives;
        if (intent == SearchIntent.GENERAL) {
            Comparator<VectorSearchResult> comparator =
                    (Comparator<VectorSearchResult>) invoke(profile, "generalRankingComparator", query, signals);
            ranked = queryRepresentatives.stream().sorted(comparator).toList();
        }
        List<SearchDecisionTrace.RankingTrace> ranking = new ArrayList<>();
        for (int index = 0; index < ranked.size(); index++) {
            VectorSearchResult candidate = ranked.get(index);
            ranking.add(rankingTrace(variantId, query, signals, candidate, index + 1));
        }
        return new StageData(
                sourceGroups,
                sourceRepresentatives,
                eligibility,
                queryGroups,
                queryRepresentatives,
                ranked,
                ranking);
    }

    private SearchDecisionTrace.RankingTrace rankingTrace(
            String variantId,
            String query,
            Object signals,
            VectorSearchResult candidate,
            int rank) throws ReflectiveOperationException {
        Object candidateSignals = invokeStatic(
                CompositeSearchProfile.class, "rankingSignals", candidate);
        Set<String> requiredIdentifiers = setAccessor(signals, "requiredIdentifiers");
        Set<String> requiredCoreTerms = setAccessor(signals, "coreTerms");
        Set<String> requiredNumbers = setAccessor(signals, "requiredNumbers");
        Set<String> identifiers = setAccessor(candidateSignals, "identifiers");
        Set<String> coreTerms = setAccessor(candidateSignals, "coreTerms");
        Set<String> numbers = setAccessor(candidateSignals, "numbers");
        double identifierBoost = matchRatio(requiredIdentifiers, identifiers)
                * staticDouble("MAX_IDENTIFIER_RANKING_BOOST");
        double coreBoost = matchRatio(requiredCoreTerms, coreTerms)
                * staticDouble("MAX_CORE_TERM_RANKING_BOOST");
        double numericBoost = matchRatio(requiredNumbers, numbers)
                * staticDouble("MAX_NUMBER_RANKING_BOOST");
        CompositeSearchProfile.RankingExplanation explanation = profile.explainRanking(query, candidate);
        return new SearchDecisionTrace.RankingTrace(
                variantId,
                candidate.chunkId(),
                candidate.score(),
                identifierBoost,
                coreBoost,
                numericBoost,
                explanation.evidenceAdjustment(),
                explanation.finalRankingValue(),
                rank,
                rank <= MAX_RESULTS);
    }

    private float[] embed(String query) {
        float[] embedding = embeddingService.embed(query);
        embeddingValidator.validate(embedding);
        return embedding;
    }

    private static List<String> parityErrors(
            CareerEvidenceSearchV2Response production,
            List<VectorSearchResult> selected,
            List<SearchDecisionTrace.EvidenceTrace> evidence) {
        List<String> errors = new ArrayList<>();
        if (production.results().size() != selected.size()) {
            errors.add("result-count trace=" + selected.size() + " production=" + production.results().size());
            return List.copyOf(errors);
        }
        for (int index = 0; index < selected.size(); index++) {
            VectorSearchResult traced = selected.get(index);
            CareerEvidenceSearchResponse actual = production.results().get(index);
            SearchDecisionTrace.EvidenceTrace localized = evidence.get(index);
            if (!Objects.equals(traced.chunkId(), actual.chunkId())) {
                errors.add("rank " + (index + 1) + " result chunk");
            }
            if (!Objects.equals(localized.evidenceChunkId(), actual.evidenceChunkId())) {
                errors.add("rank " + (index + 1) + " evidence chunk");
            }
            if (!Objects.equals(localized.snippet(), actual.snippet())) {
                errors.add("rank " + (index + 1) + " snippet");
            }
            if (Double.compare(traced.score(), actual.score()) != 0
                    || Double.compare(traced.distance(), actual.distance()) != 0) {
                errors.add("rank " + (index + 1) + " score/distance");
            }
        }
        return List.copyOf(errors);
    }

    private static List<VectorSearchResult> mergeCandidates(
            List<VectorSearchResult> existing,
            List<VectorSearchResult> incoming) {
        Map<Long, VectorSearchResult> byChunk = new LinkedHashMap<>();
        existing.forEach(candidate -> byChunk.put(candidate.chunkId(), candidate));
        incoming.forEach(candidate -> byChunk.merge(
                candidate.chunkId(), candidate,
                (current, replacement) -> replacement.score() > current.score()
                        ? replacement : current));
        return byChunk.values().stream()
                .sorted(Comparator.comparingDouble(VectorSearchResult::score)
                        .reversed()
                        .thenComparing(VectorSearchResult::chunkId))
                .toList();
    }

    private static List<VectorSearchResult> deduplicatePresentation(List<VectorSearchResult> selected) {
        Set<String> seen = new LinkedHashSet<>();
        return selected.stream()
                .filter(candidate -> seen.add(Objects.requireNonNullElse(candidate.content(), "")
                        .replace("\r\n", "\n")
                        .replace('\r', '\n')
                        .strip()))
                .toList();
    }

    private static List<GroupView> groups(Object value) throws ReflectiveOperationException {
        List<GroupView> result = new ArrayList<>();
        for (Object group : (List<?>) value) {
            Method representative = group.getClass().getDeclaredMethod("representative");
            representative.setAccessible(true);
            Method members = group.getClass().getDeclaredMethod("members");
            members.setAccessible(true);
            @SuppressWarnings("unchecked")
            List<VectorSearchResult> memberValues = (List<VectorSearchResult>) members.invoke(group);
            result.add(new GroupView(
                    (VectorSearchResult) representative.invoke(group),
                    List.copyOf(memberValues)));
        }
        return List.copyOf(result);
    }

    private static List<VectorSearchResult> representatives(List<GroupView> groups) {
        return groups.stream().map(GroupView::representative).toList();
    }

    private static Object invoke(Object target, String name, Object... arguments)
            throws ReflectiveOperationException {
        Method method = findMethod(target.getClass(), name, arguments.length);
        return method.invoke(target, arguments);
    }

    private static Object invokeStatic(Class<?> type, String name, Object... arguments)
            throws ReflectiveOperationException {
        return findMethod(type, name, arguments.length).invoke(null, arguments);
    }

    private static Method findMethod(Class<?> type, String name, int parameterCount) {
        Method method = java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(candidate -> candidate.getName().equals(name)
                        && candidate.getParameterCount() == parameterCount)
                .findFirst()
                .orElseThrow();
        method.setAccessible(true);
        return method;
    }

    @SuppressWarnings("unchecked")
    private static Set<String> setAccessor(Object record, String name)
            throws ReflectiveOperationException {
        Method accessor = record.getClass().getDeclaredMethod(name);
        accessor.setAccessible(true);
        return (Set<String>) accessor.invoke(record);
    }

    private static double staticDouble(String name) throws ReflectiveOperationException {
        Field field = CompositeSearchProfile.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.getDouble(null);
    }

    private static double matchRatio(Set<String> required, Set<String> candidate) {
        if (required.isEmpty()) {
            return 0.0d;
        }
        return (double) required.stream().filter(candidate::contains).count() / required.size();
    }

    private record GroupView(VectorSearchResult representative, List<VectorSearchResult> members) {
    }

    private record StageData(
            List<GroupView> sourceGroups,
            List<VectorSearchResult> sourceRepresentatives,
            Map<Long, List<String>> eligibility,
            List<GroupView> queryGroups,
            List<VectorSearchResult> queryRepresentatives,
            List<VectorSearchResult> ranked,
            List<SearchDecisionTrace.RankingTrace> ranking) {

        private static StageData empty() {
            return new StageData(List.of(), List.of(), Map.of(), List.of(), List.of(), List.of(), List.of());
        }

        private StageData remap(Map<Long, VectorSearchResult> originals) {
            Function<VectorSearchResult, VectorSearchResult> mapper = value ->
                    originals.getOrDefault(value.chunkId(), value);
            return new StageData(
                    sourceGroups.stream().map(group -> new GroupView(
                            mapper.apply(group.representative()),
                            group.members().stream().map(mapper).toList())).toList(),
                    sourceRepresentatives.stream().map(mapper).toList(),
                    eligibility,
                    queryGroups.stream().map(group -> new GroupView(
                            mapper.apply(group.representative()),
                            group.members().stream().map(mapper).toList())).toList(),
                    queryRepresentatives.stream().map(mapper).toList(),
                    ranked.stream().map(mapper).toList(),
                    ranking.stream().map(value -> {
                        VectorSearchResult original = originals.get(value.chunkId());
                        if (original == null) {
                            return value;
                        }
                        return new SearchDecisionTrace.RankingTrace(
                                value.variantId(),
                                value.chunkId(),
                                original.score(),
                                value.identifierBoost(),
                                value.coreTermBoost(),
                                value.numericBoost(),
                                value.evidenceRerankerAdjustment(),
                                value.finalRankingScore(),
                                value.rank(),
                                value.selectedInTopFive());
                    }).toList());
        }
    }

    private record AttemptData(
            String variantId,
            SearchDecisionTrace.QueryVariantType type,
            String query,
            List<String> anchorQueries,
            boolean directAnchorRequired,
            List<VectorSearchResult> incoming,
            List<VectorSearchResult> merged,
            StageData stages,
            List<VectorSearchResult> topFive,
            List<VectorSearchResult> postFilter,
            Map<Long, List<String>> postFilterReasons) {
    }

    private static final class TraceAccumulator {

        private final List<AttemptData> attempts = new ArrayList<>();
        private final Map<Long, VectorSearchResult> candidates = new LinkedHashMap<>();
        private final Map<Long, List<SearchDecisionTrace.RetrievalHitTrace>> retrieval = new LinkedHashMap<>();
        private final Map<Long, List<SearchDecisionTrace.CandidateDecisionTrace>> decisions = new LinkedHashMap<>();
        private final List<SearchDecisionTrace.CandidateGroupTrace> sourceGroups = new ArrayList<>();
        private final List<SearchDecisionTrace.CandidateGroupTrace> queryGroups = new ArrayList<>();
        private final List<SearchDecisionTrace.RankingTrace> rankings = new ArrayList<>();

        private void retrieved(String variantId, List<VectorSearchResult> values) {
            for (int index = 0; index < values.size(); index++) {
                VectorSearchResult candidate = values.get(index);
                candidates.putIfAbsent(candidate.chunkId(), candidate);
                retrieval.computeIfAbsent(candidate.chunkId(), ignored -> new ArrayList<>())
                        .add(new SearchDecisionTrace.RetrievalHitTrace(
                                variantId, index + 1, candidate.score(), candidate.distance()));
                decision(candidate.chunkId(), variantId,
                        SearchDecisionTrace.DecisionStage.RETRIEVAL, PASS, List.of(), null);
            }
        }

        private void add(AttemptData attempt) {
            attempts.add(attempt);
            attempt.merged().forEach(candidate -> candidates.putIfAbsent(candidate.chunkId(), candidate));
            if (attempt.type() == SearchDecisionTrace.QueryVariantType.IDENTIFIER_GUARD) {
                attempt.merged().forEach(candidate -> decision(
                        candidate.chunkId(), attempt.variantId(),
                        SearchDecisionTrace.DecisionStage.ELIGIBILITY,
                        REJECT,
                        attempt.postFilterReasons().get(candidate.chunkId()),
                        null));
                return;
            }
            Map<Long, Long> sourceRepresentativeByMember = addGroups(
                    attempt.variantId(),
                    "SRC",
                    SearchDecisionTrace.DecisionStage.SOURCE_CONSOLIDATION,
                    attempt.stages().sourceGroups(),
                    sourceGroups);
            for (VectorSearchResult candidate : attempt.merged()) {
                Long representative = sourceRepresentativeByMember.get(candidate.chunkId());
                if (representative == null) {
                    continue;
                }
                decision(candidate.chunkId(), attempt.variantId(),
                        SearchDecisionTrace.DecisionStage.SOURCE_CONSOLIDATION,
                        candidate.chunkId().equals(representative) ? SELECTED : REMOVED,
                        candidate.chunkId().equals(representative)
                                ? List.of() : List.of("REPLACED_BY_PREFERRED_REPRESENTATIVE"),
                        representative);
            }
            for (VectorSearchResult representative : attempt.stages().sourceRepresentatives()) {
                List<String> reasons = attempt.stages().eligibility()
                        .getOrDefault(representative.chunkId(), List.of());
                decision(representative.chunkId(), attempt.variantId(),
                        SearchDecisionTrace.DecisionStage.ELIGIBILITY,
                        reasons.isEmpty() ? PASS : REJECT,
                        canonicalReasons(reasons),
                        null);
            }
            Map<Long, Long> queryRepresentativeByMember = addGroups(
                    attempt.variantId(),
                    "QEV",
                    SearchDecisionTrace.DecisionStage.QUERY_EVIDENCE_CONSOLIDATION,
                    attempt.stages().queryGroups(),
                    queryGroups);
            queryRepresentativeByMember.forEach((chunkId, representative) -> decision(
                    chunkId,
                    attempt.variantId(),
                    SearchDecisionTrace.DecisionStage.QUERY_EVIDENCE_CONSOLIDATION,
                    chunkId.equals(representative) ? SELECTED : REMOVED,
                    chunkId.equals(representative)
                            ? List.of() : List.of("REPLACED_BY_PREFERRED_REPRESENTATIVE"),
                    representative));
            rankings.addAll(attempt.stages().ranking());
            Set<Long> topFive = attempt.topFive().stream().map(VectorSearchResult::chunkId).collect(Collectors.toSet());
            for (SearchDecisionTrace.RankingTrace ranking : attempt.stages().ranking()) {
                decision(ranking.chunkId(), attempt.variantId(),
                        SearchDecisionTrace.DecisionStage.RANKING,
                        topFive.contains(ranking.chunkId()) ? SELECTED : REMOVED,
                        topFive.contains(ranking.chunkId()) ? List.of() : List.of("OUTSIDE_TOP_5"),
                        null);
            }
            Set<Long> postFilter = attempt.postFilter().stream()
                    .map(VectorSearchResult::chunkId).collect(Collectors.toSet());
            for (VectorSearchResult candidate : attempt.topFive()) {
                boolean pass = postFilter.contains(candidate.chunkId());
                decision(candidate.chunkId(), attempt.variantId(),
                        SearchDecisionTrace.DecisionStage.POST_FILTER,
                        pass ? PASS : REJECT,
                        pass ? List.of() : canonicalReasons(attempt.postFilterReasons()
                                .getOrDefault(candidate.chunkId(), List.of("POST_FILTER"))),
                        null);
            }
        }

        private void recordPresentationDedup(
                List<VectorSearchResult> before,
                List<VectorSearchResult> after) {
            Set<Long> retained = after.stream().map(VectorSearchResult::chunkId).collect(Collectors.toSet());
            for (VectorSearchResult candidate : before) {
                if (!retained.contains(candidate.chunkId())) {
                    decision(candidate.chunkId(), "FINAL", SearchDecisionTrace.DecisionStage.POST_FILTER,
                            REJECT, List.of("DUPLICATE_PRESENTATION_CONTENT"), null);
                }
            }
        }

        private void markFinal(List<VectorSearchResult> selected) {
            selected.forEach(candidate -> decision(
                    candidate.chunkId(), "FINAL", SearchDecisionTrace.DecisionStage.FINAL,
                    SELECTED, List.of(), null));
        }

        private Map<Long, Long> addGroups(
                String variantId,
                String prefix,
                SearchDecisionTrace.DecisionStage stage,
                List<GroupView> groups,
                List<SearchDecisionTrace.CandidateGroupTrace> output) {
            Map<Long, Long> representativeByMember = new LinkedHashMap<>();
            for (int index = 0; index < groups.size(); index++) {
                GroupView group = groups.get(index);
                List<Long> members = group.members().stream().map(VectorSearchResult::chunkId).toList();
                Long representative = group.representative().chunkId();
                members.forEach(member -> representativeByMember.put(member, representative));
                output.add(new SearchDecisionTrace.CandidateGroupTrace(
                        variantId,
                        variantId + "-" + prefix + "-" + (index + 1),
                        stage,
                        members,
                        representative,
                        members.stream().filter(member -> !member.equals(representative)).toList()));
            }
            return representativeByMember;
        }

        private void decision(
                Long chunkId,
                String variantId,
                SearchDecisionTrace.DecisionStage stage,
                SearchDecisionTrace.Decision decision,
                List<String> reasons,
                Long representative) {
            decisions.computeIfAbsent(chunkId, ignored -> new ArrayList<>())
                    .add(new SearchDecisionTrace.CandidateDecisionTrace(
                            variantId, stage, decision, reasons == null ? List.of() : reasons, representative));
        }

        private List<SearchDecisionTrace.QueryVariantTrace> variantTraces() {
            return attempts.stream().map(attempt -> new SearchDecisionTrace.QueryVariantTrace(
                    attempt.variantId(),
                    attempt.type(),
                    attempt.query(),
                    attempt.anchorQueries(),
                    attempt.directAnchorRequired(),
                    ids(attempt.incoming()),
                    ids(attempt.merged()),
                    ids(attempt.stages().sourceRepresentatives()),
                    attempt.stages().eligibility().entrySet().stream()
                            .filter(entry -> entry.getValue().isEmpty())
                            .map(Map.Entry::getKey).toList(),
                    ids(attempt.stages().queryRepresentatives()),
                    ids(attempt.stages().ranked()),
                    ids(attempt.topFive()),
                    ids(attempt.postFilter()))).toList();
        }

        private List<SearchDecisionTrace.CandidateTrace> candidateTraces(
                List<VectorSearchResult> finalSelected) {
            Set<Long> finalIds = finalSelected.stream().map(VectorSearchResult::chunkId).collect(Collectors.toSet());
            return candidates.values().stream().map(candidate -> {
                Failure failure = firstFailure(
                        decisions.getOrDefault(candidate.chunkId(), List.of()),
                        finalIds.contains(candidate.chunkId()));
                return new SearchDecisionTrace.CandidateTrace(
                        candidate.chunkId(),
                        candidate.documentId(),
                        candidate.documentVersionId(),
                        candidate.chunkNo(),
                        candidate.sourceType(),
                        candidate.sourceIndex(),
                        candidate.sourceLabel(),
                        candidate.content(),
                        retrieval.getOrDefault(candidate.chunkId(), List.of()),
                        decisions.getOrDefault(candidate.chunkId(), List.of()),
                        failure.stage(),
                        failure.reason());
            }).toList();
        }

        private static Failure firstFailure(
                List<SearchDecisionTrace.CandidateDecisionTrace> values,
                boolean finalSelected) {
            if (finalSelected) {
                return new Failure(SearchDecisionTrace.FirstFailureStage.NONE, "NONE");
            }
            List<SearchDecisionTrace.DecisionStage> order = List.of(
                    SearchDecisionTrace.DecisionStage.POST_FILTER,
                    SearchDecisionTrace.DecisionStage.RANKING,
                    SearchDecisionTrace.DecisionStage.QUERY_EVIDENCE_CONSOLIDATION,
                    SearchDecisionTrace.DecisionStage.ELIGIBILITY,
                    SearchDecisionTrace.DecisionStage.SOURCE_CONSOLIDATION,
                    SearchDecisionTrace.DecisionStage.RETRIEVAL);
            for (SearchDecisionTrace.DecisionStage stage : order) {
                SearchDecisionTrace.CandidateDecisionTrace rejection = values.stream()
                        .filter(value -> value.stage() == stage)
                        .filter(value -> value.decision() == REJECT || value.decision() == REMOVED)
                        .findFirst().orElse(null);
                boolean passedThisOrLater = values.stream().anyMatch(value ->
                        order.indexOf(value.stage()) <= order.indexOf(stage)
                                && (value.decision() == PASS || value.decision() == SELECTED));
                if (rejection != null && !passedThisOrLater) {
                    return new Failure(mapFailureStage(stage), String.join(";", rejection.reasons()));
                }
            }
            SearchDecisionTrace.CandidateDecisionTrace deepest = values.stream()
                    .filter(value -> value.decision() == PASS || value.decision() == SELECTED)
                    .max(Comparator.comparingInt(value -> stageOrdinal(value.stage())))
                    .orElse(null);
            if (deepest == null) {
                return new Failure(SearchDecisionTrace.FirstFailureStage.RETRIEVAL, "NOT_RETRIEVED");
            }
            return switch (deepest.stage()) {
                case RETRIEVAL -> new Failure(SearchDecisionTrace.FirstFailureStage.SOURCE_CONSOLIDATION,
                        rejectionReason(values, SearchDecisionTrace.DecisionStage.SOURCE_CONSOLIDATION));
                case SOURCE_CONSOLIDATION -> new Failure(SearchDecisionTrace.FirstFailureStage.ELIGIBILITY,
                        rejectionReason(values, SearchDecisionTrace.DecisionStage.ELIGIBILITY));
                case ELIGIBILITY -> new Failure(SearchDecisionTrace.FirstFailureStage.QUERY_EVIDENCE_CONSOLIDATION,
                        rejectionReason(values, SearchDecisionTrace.DecisionStage.QUERY_EVIDENCE_CONSOLIDATION));
                case QUERY_EVIDENCE_CONSOLIDATION -> new Failure(SearchDecisionTrace.FirstFailureStage.RANKING,
                        "OUTSIDE_TOP_5");
                case RANKING -> new Failure(SearchDecisionTrace.FirstFailureStage.POST_FILTER,
                        rejectionReason(values, SearchDecisionTrace.DecisionStage.POST_FILTER));
                case POST_FILTER, FINAL, LOCALIZATION -> new Failure(
                        SearchDecisionTrace.FirstFailureStage.POST_FILTER,
                        "DUPLICATE_PRESENTATION_CONTENT");
            };
        }

        private static int stageOrdinal(SearchDecisionTrace.DecisionStage stage) {
            return switch (stage) {
                case RETRIEVAL -> 0;
                case SOURCE_CONSOLIDATION -> 1;
                case ELIGIBILITY -> 2;
                case QUERY_EVIDENCE_CONSOLIDATION -> 3;
                case RANKING -> 4;
                case POST_FILTER -> 5;
                case LOCALIZATION -> 6;
                case FINAL -> 7;
            };
        }

        private static SearchDecisionTrace.FirstFailureStage mapFailureStage(
                SearchDecisionTrace.DecisionStage stage) {
            return switch (stage) {
                case RETRIEVAL -> SearchDecisionTrace.FirstFailureStage.RETRIEVAL;
                case SOURCE_CONSOLIDATION -> SearchDecisionTrace.FirstFailureStage.SOURCE_CONSOLIDATION;
                case ELIGIBILITY -> SearchDecisionTrace.FirstFailureStage.ELIGIBILITY;
                case QUERY_EVIDENCE_CONSOLIDATION ->
                        SearchDecisionTrace.FirstFailureStage.QUERY_EVIDENCE_CONSOLIDATION;
                case RANKING -> SearchDecisionTrace.FirstFailureStage.RANKING;
                case POST_FILTER, FINAL -> SearchDecisionTrace.FirstFailureStage.POST_FILTER;
                case LOCALIZATION -> SearchDecisionTrace.FirstFailureStage.LOCALIZATION;
            };
        }

        private static String rejectionReason(
                List<SearchDecisionTrace.CandidateDecisionTrace> values,
                SearchDecisionTrace.DecisionStage stage) {
            return values.stream()
                    .filter(value -> value.stage() == stage)
                    .filter(value -> value.decision() == REJECT || value.decision() == REMOVED)
                    .map(value -> String.join(";", value.reasons()))
                    .findFirst()
                    .orElse("NOT_REACHED");
        }

        private static List<Long> ids(List<VectorSearchResult> values) {
            return values.stream().map(VectorSearchResult::chunkId).toList();
        }

        private static List<String> canonicalReasons(List<String> reasons) {
            return reasons.stream().map(reason -> {
                if ("DENSE_SCORE_BELOW_TUNING_FLOOR".equals(reason)) {
                    return "BELOW_DENSE_FLOOR";
                }
                if (reason.startsWith("MISSING_IDENTIFIER:")) {
                    return "IDENTIFIER_GUARD:" + reason.substring("MISSING_IDENTIFIER:".length());
                }
                if (reason.startsWith("MISSING_NUMBER:")) {
                    return "NUMERIC_MISMATCH:" + reason.substring("MISSING_NUMBER:".length());
                }
                return reason;
            }).toList();
        }
    }

    private record Failure(SearchDecisionTrace.FirstFailureStage stage, String reason) {
    }
}
