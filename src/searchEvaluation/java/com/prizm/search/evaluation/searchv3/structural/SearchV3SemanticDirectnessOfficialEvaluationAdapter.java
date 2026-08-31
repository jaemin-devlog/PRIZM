package com.prizm.search.evaluation.searchv3.structural;

import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.FrozenCandidates;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.GoldJoined;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.QueryProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.VerifiedCandidates;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.QueryGold;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.AggregateMetrics;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.CandidatePrediction;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.DirectnessRelation;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.DirectnessRun;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.DirectnessSummary;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.GateAssessment;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.QueryMetrics;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.QueryPredictions;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessEvaluator.SafetyInputs;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.FrozenInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.FrozenOutput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.InputVerification;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.OutputVerification;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.Prediction;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.QueryInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.SourceSuite;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import tools.jackson.databind.ObjectMapper;

/** Gold-after-output adapter for the one-shot PRZ-031 semantic directness evaluation. */
final class SearchV3SemanticDirectnessOfficialEvaluationAdapter {

    static final int REPORT_SCHEMA_VERSION = 1;
    static final String REPORT_ARTIFACT_TYPE = "PRZ031_SEMANTIC_DIRECTNESS_EVALUATION";
    static final Path DEFAULT_REPORT = Path.of(
            "local/search-v3-evaluation/prz031/semantic-directness-evaluation.json");
    private static final String LOCAL_REPORT_PREFIX = "local/search-v3-evaluation/prz031/";
    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    private final SearchV3SemanticDirectnessEvaluator evaluator;

    SearchV3SemanticDirectnessOfficialEvaluationAdapter() {
        this(new SearchV3SemanticDirectnessEvaluator());
    }

    SearchV3SemanticDirectnessOfficialEvaluationAdapter(
            SearchV3SemanticDirectnessEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    EvaluationReport evaluate(
            FrozenInput suppliedInput,
            FrozenOutput suppliedOutput,
            ArtifactHashes artifactHashes,
            OfficialCost cost,
            SealedState sealed,
            GoldLoader goldLoader) {
        Objects.requireNonNull(suppliedInput, "suppliedInput");
        Objects.requireNonNull(suppliedOutput, "suppliedOutput");
        Objects.requireNonNull(artifactHashes, "artifactHashes");
        Objects.requireNonNull(cost, "cost");
        Objects.requireNonNull(sealed, "sealed");
        Objects.requireNonNull(goldLoader, "goldLoader");
        requireArtifactHashParity(suppliedInput, suppliedOutput, artifactHashes);

        SearchV3SemanticDirectnessPredictionFreeze.PhaseGuard guard =
                new SearchV3SemanticDirectnessPredictionFreeze.PhaseGuard();
        FrozenInput refrozenInput = guard.freezeInput(suppliedInput.input());
        if (!refrozenInput.equals(suppliedInput)) {
            throw new IllegalStateException("supplied PRZ-031 input differs from canonical freeze");
        }
        InputVerification inputVerification = guard.verifyInput();
        guard.openInference();
        FrozenOutput refrozenOutput = guard.freezeOutput(suppliedOutput.output());
        if (!refrozenOutput.equals(suppliedOutput)) {
            throw new IllegalStateException("supplied PRZ-031 output differs from canonical freeze");
        }
        OutputVerification outputVerification = guard.verifyOutput();

        SearchV3SemanticDirectnessPredictionFreeze.GoldJoined<List<SuiteGold>> joined =
                guard.joinGold(goldLoader::load);
        if (!joined.input().equals(inputVerification)
                || !joined.output().equals(outputVerification)) {
            throw new IllegalStateException("Gold join lost verified input/output identity");
        }

        Map<String, QueryInput> inputByQuery = uniqueMap(
                suppliedInput.input().queries(), QueryInput::queryId, "input query");
        Map<String, QueryPredictions> predictionsByQuery = predictions(suppliedOutput);
        if (!inputByQuery.keySet().equals(predictionsByQuery.keySet())) {
            throw new IllegalStateException("input/output semantic query inventories differ");
        }
        Map<String, SuiteGold> goldSuites = uniqueMap(joined.gold(), SuiteGold::suite, "Gold suite");

        List<SuiteReport> suites = new ArrayList<>();
        List<QueryMetrics> combinedQueries = new ArrayList<>();
        boolean candidateIdentityParity = true;
        boolean sourceProvenanceUnchanged = true;
        boolean crossParentMergeFree = true;
        for (SourceSuite source : suppliedInput.input().sourceSuites()) {
            SuiteGold suiteMaterial = goldSuites.remove(source.suite());
            if (suiteMaterial == null) {
                throw new IllegalStateException("Gold loader omitted source suite: " + source.suite());
            }
            FrozenCandidates exactSuiteFreeze = suiteMaterial.candidates();
            if (!source.suite().equals(exactSuiteFreeze.input().suite())
                    || !source.datasetVersion().equals(exactSuiteFreeze.input().datasetVersion())
                    || !source.candidateFreezeSha256().equals(exactSuiteFreeze.canonicalSha256())) {
                throw new IllegalStateException("Gold suite is not the exact source candidate freeze: "
                        + source.suite());
            }
            List<QueryInput> suiteInputs = suppliedInput.input().queries().stream()
                    .filter(value -> value.suite().equals(source.suite()))
                    .toList();
            if (suiteInputs.isEmpty()) {
                throw new IllegalStateException("verified input omitted source suite: " + source.suite());
            }
            Map<String, QueryProjection> exactQueries = uniqueMap(
                    exactSuiteFreeze.input().queries(), QueryProjection::queryId, "exact source query");
            for (QueryInput semantic : suiteInputs) {
                QueryProjection exact = exactQueries.get(semantic.queryId());
                if (exact == null
                        || !semantic.userBundleId().equals(exact.userBundleId())
                        || !semantic.split().equals(exact.split())
                        || semantic.track() != exact.track()
                        || !semantic.rankedCandidates().equals(exact.rankedCandidates())) {
                    throw new IllegalStateException(
                            "semantic input candidate projection differs from exact source freeze: "
                                    + semantic.queryId());
                }
            }
            SearchV3CandidateFreeze.PhaseGuard suiteGuard = new SearchV3CandidateFreeze.PhaseGuard();
            FrozenCandidates suiteFreeze = suiteGuard.freezeCandidates(exactSuiteFreeze.input());
            if (!suiteFreeze.equals(exactSuiteFreeze)) {
                throw new IllegalStateException("Gold suite differs from its canonical source freeze");
            }
            VerifiedCandidates suiteVerified = suiteGuard.verifyFreeze();
            boolean exactVerification = SearchV3CandidateFreeze.verify(suiteFreeze).frozen()
                    .equals(suiteVerified.frozen());
            if (!exactVerification) {
                throw new IllegalStateException("suite candidate freeze verification drifted");
            }
            candidateIdentityParity &= exactVerification
                    && suiteVerified.frozen().canonicalSha256().equals(source.candidateFreezeSha256());
            sourceProvenanceUnchanged &= suiteFreeze.equals(exactSuiteFreeze);
            crossParentMergeFree &= structuralBoundaryIntact(exactSuiteFreeze);
            Map<String, QueryGold> fullGoldByQuery = uniqueMap(
                    suiteMaterial.gold(), QueryGold::queryId, "suite Gold query");
            Set<String> fullCandidateIds = exactSuiteFreeze.input().queries().stream()
                    .map(value -> value.queryId())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (!fullCandidateIds.equals(fullGoldByQuery.keySet())) {
                throw new IllegalStateException("full suite candidate/Gold inventories differ");
            }
            Set<String> suiteIds = suiteInputs.stream().map(QueryInput::queryId)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            for (QueryInput query : suiteInputs) {
                QueryGold gold = fullGoldByQuery.get(query.queryId());
                if (gold == null || !query.userBundleId().equals(gold.userBundleId())) {
                    throw new IllegalStateException("semantic input/Gold owner differs: " + query.queryId());
                }
            }
            List<QueryPredictions> suitePredictions = suiteInputs.stream()
                    .map(value -> predictionsByQuery.get(value.queryId()))
                    .toList();
            GoldJoined<List<QueryGold>> suiteJoined = suiteGuard.joinGold(suiteMaterial::gold);
            DirectnessRun run = evaluator.evaluate(suiteJoined, suiteIds, suitePredictions);
            combinedQueries.addAll(run.queries());
            suites.add(new SuiteReport(
                    source.suite(), source.datasetVersion(), source.candidateFreezeSha256(),
                    suiteIds.size(), run.summary()));
        }

        if (combinedQueries.size() != SearchV3SemanticDirectnessPredictionFreeze.SEMANTIC_QUERY_COUNT) {
            throw new IllegalStateException("suite evaluation did not preserve all semantic queries");
        }
        DirectnessSummary aggregate = SearchV3SemanticDirectnessEvaluator.summarize(combinedQueries);
        if (!goldSuites.isEmpty()) {
            throw new IllegalStateException("Gold loader added an unapproved source suite");
        }
        SafetyInputs safety = new SafetyInputs(
                candidateIdentityParity,
                sourceProvenanceUnchanged,
                crossParentMergeFree,
                guard.phase() == SearchV3SemanticDirectnessPredictionFreeze.Phase.GOLD_JOINED
                        && joined.output().equals(outputVerification),
                !sealed.opened()
                        && !sealed.searchExecuted()
                        && "NOT_RUN".equals(sealed.currentFreshBaseline()));
        GateAssessment gate = SearchV3SemanticDirectnessEvaluator.assessGate(
                aggregate.aggregate(), safety);
        List<QueryRow> rows = combinedQueries.stream().map(QueryRow::from).toList();
        return new EvaluationReport(
                REPORT_SCHEMA_VERSION,
                REPORT_ARTIFACT_TYPE,
                artifactHashes,
                inputVerification,
                outputVerification,
                List.copyOf(suites),
                aggregate,
                rows,
                gate,
                decision(gate, aggregate.aggregate()),
                DECISION_POLICY,
                cost,
                sealed,
                "GOLD_JOINED_AFTER_OUTPUT_VERIFIED");
    }

    static Path writeCreateNew(
            Path repositoryRoot,
            Path relativeReport,
            Object report,
            ObjectMapper mapper) throws IOException {
        Objects.requireNonNull(repositoryRoot, "repositoryRoot");
        Objects.requireNonNull(report, "report");
        Objects.requireNonNull(mapper, "mapper");
        Path portable = requireLocalReportPath(relativeReport);
        Path target = repositoryRoot.toAbsolutePath().normalize().resolve(portable).normalize();
        Path root = repositoryRoot.toAbsolutePath().normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("report path escapes repository root");
        }
        Files.createDirectories(target.getParent());
        Files.writeString(
                target,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
        return target;
    }

    static Path requireLocalReportPath(Path relativeReport) {
        Objects.requireNonNull(relativeReport, "relativeReport");
        if (relativeReport.isAbsolute()) {
            throw new IllegalArgumentException("report path must be repository-relative");
        }
        Path normalized = relativeReport.normalize();
        String portable = normalized.toString().replace('\\', '/');
        if (!portable.startsWith(LOCAL_REPORT_PREFIX) || portable.contains("../")) {
            throw new IllegalArgumentException("report must stay in ignored PRZ-031 local storage");
        }
        return normalized;
    }

    private Map<String, QueryPredictions> predictions(FrozenOutput suppliedOutput) {
        Map<String, List<Prediction>> grouped = suppliedOutput.output().predictions().stream()
                .collect(Collectors.groupingBy(
                        Prediction::queryId, LinkedHashMap::new, Collectors.toList()));
        Map<String, QueryPredictions> result = new LinkedHashMap<>();
        grouped.forEach((queryId, values) -> result.put(queryId, new QueryPredictions(
                queryId,
                values.stream().map(value -> new CandidatePrediction(
                        value.sourceRank(),
                        value.candidateId(),
                        DirectnessRelation.valueOf(value.relation().name()),
                        value.reasonCode().name())).toList())));
        return Map.copyOf(result);
    }

    private void requireArtifactHashParity(
            FrozenInput input,
            FrozenOutput output,
            ArtifactHashes hashes) {
        if (!hashes.inputCanonicalSha256().equals(input.canonicalSha256())
                || !hashes.guardContractSha256().equals(input.contractSha256())
                || !hashes.outputFreezeCanonicalSha256().equals(output.canonicalSha256())) {
            throw new IllegalArgumentException("artifact hash chain differs from supplied freezes");
        }
    }

    private boolean structuralBoundaryIntact(FrozenCandidates frozen) {
        return frozen.input().queries().stream().flatMap(value -> value.rankedCandidates().stream())
                .allMatch(candidate -> candidate.parentAnnotationCandidateId() != null
                        && !candidate.parentAnnotationCandidateId().isBlank()
                        && candidate.evidenceChildren().stream().allMatch(child ->
                                child.documentId().equals(candidate.documentId())
                                        && child.versionId().equals(candidate.versionId())));
    }

    private static <T> Map<String, T> uniqueMap(
            List<T> values,
            Function<T, String> key,
            String label) {
        return values.stream().collect(Collectors.toMap(
                key,
                Function.identity(),
                (left, right) -> {
                    throw new IllegalArgumentException("duplicate " + label + ": " + key.apply(left));
                },
                LinkedHashMap::new));
    }

    @FunctionalInterface
    interface GoldLoader {
        List<SuiteGold> load(VerifiedCandidates verifiedCombinedCandidates);
    }

    record SuiteGold(String suite, FrozenCandidates candidates, List<QueryGold> gold) {

        SuiteGold {
            Objects.requireNonNull(candidates, "candidates");
            gold = List.copyOf(gold);
        }
    }

    record ArtifactHashes(
            String candidateArtifactFileSha256,
            String officialRunMarkerFileSha256,
            String inputArtifactFileSha256,
            String inputCanonicalSha256,
            String executionContractFileSha256,
            String guardContractSha256,
            String outputArtifactFileSha256,
            String outputArtifactCanonicalSha256,
            String outputFreezeCanonicalSha256) {

        ArtifactHashes {
            requireSha(candidateArtifactFileSha256, "candidateArtifactFileSha256");
            requireSha(officialRunMarkerFileSha256, "officialRunMarkerFileSha256");
            requireSha(inputArtifactFileSha256, "inputArtifactFileSha256");
            requireSha(inputCanonicalSha256, "inputCanonicalSha256");
            requireSha(executionContractFileSha256, "executionContractFileSha256");
            requireSha(guardContractSha256, "guardContractSha256");
            requireSha(outputArtifactFileSha256, "outputArtifactFileSha256");
            requireSha(outputArtifactCanonicalSha256, "outputArtifactCanonicalSha256");
            requireSha(outputFreezeCanonicalSha256, "outputFreezeCanonicalSha256");
        }
    }

    record SuiteReport(
            String suite,
            String datasetVersion,
            String sourceCandidateFreezeSha256,
            int queryCount,
            DirectnessSummary summary) {
    }

    record QueryRow(
            String queryId,
            String userBundleId,
            String suite,
            String split,
            String professionGroup,
            String language,
            List<String> categories,
            String answerability,
            Integer d0FirstDirectRank,
            Integer d1FirstDirectRank,
            Integer o10FirstDirectRank,
            String rankOutcome,
            boolean retained,
            boolean recoveredToRank1,
            boolean denseTop1PredictedDirect,
            boolean finalTop1PredictedDirect,
            SearchV3OracleCeilingEvaluator.RankingMetrics d0,
            SearchV3OracleCeilingEvaluator.RankingMetrics d1,
            SearchV3OracleCeilingEvaluator.RankingMetrics o10) {

        QueryRow {
            categories = List.copyOf(categories);
        }

        static QueryRow from(QueryMetrics value) {
            return new QueryRow(
                    value.queryId(), value.userBundleId(), value.suite(), value.split(),
                    value.professionGroup(), value.language(), value.categories(),
                    value.answerability().name(), value.d0FirstDirectRank(), value.d1FirstDirectRank(),
                    value.o10FirstDirectRank(), value.rankOutcome().name(), value.retained(),
                    value.recoveredToRank1(), value.denseTop1PredictedDirect(),
                    value.finalTop1PredictedDirect(), value.d0(), value.d1(), value.o10());
        }
    }

    record OfficialCost(
            double officialWallMs,
            double pairLatencyAverageMs,
            double pairLatencyP50Ms,
            double pairLatencyP95Ms,
            double pairLatencyMaxMs,
            double queryTop10LatencyP50Ms,
            double queryTop10LatencyP95Ms,
            double queryTop10LatencyMaxMs,
            ResourceSnapshot processRssBytes,
            ResourceSnapshot gpuUsedMiB,
            long modelArtifactBytes) {

        OfficialCost {
            if (!finiteNonNegative(officialWallMs)
                    || !finiteNonNegative(pairLatencyAverageMs)
                    || !finiteNonNegative(pairLatencyP50Ms)
                    || !finiteNonNegative(pairLatencyP95Ms)
                    || !finiteNonNegative(pairLatencyMaxMs)
                    || !finiteNonNegative(queryTop10LatencyP50Ms)
                    || !finiteNonNegative(queryTop10LatencyP95Ms)
                    || !finiteNonNegative(queryTop10LatencyMaxMs)
                    || modelArtifactBytes < 0) {
                throw new IllegalArgumentException("official cost metrics must be finite/non-negative");
            }
            Objects.requireNonNull(processRssBytes, "processRssBytes");
            Objects.requireNonNull(gpuUsedMiB, "gpuUsedMiB");
        }
    }

    record ResourceSnapshot(Long before, Long peak, Long after) {

        ResourceSnapshot {
            if ((before != null && before < 0)
                    || (peak != null && peak < 0)
                    || (after != null && after < 0)) {
                throw new IllegalArgumentException("resource snapshot must be non-negative when available");
            }
        }
    }

    record SealedState(
            String combinedSha256,
            String manifestFileSha256,
            String gitTree,
            boolean opened,
            boolean searchExecuted,
            String currentFreshBaseline) {

        SealedState {
            requireSha(combinedSha256, "sealed combinedSha256");
            requireSha(manifestFileSha256, "sealed manifestFileSha256");
            if (gitTree == null || !gitTree.matches("^[0-9a-f]{40}$")) {
                throw new IllegalArgumentException("sealed gitTree must be a lowercase Git tree SHA");
            }
            if (opened || searchExecuted || !"NOT_RUN".equals(currentFreshBaseline)) {
                throw new IllegalArgumentException("SEALED FINAL must remain closed and unexecuted");
            }
        }
    }

    record EvaluationReport(
            int schemaVersion,
            String artifactType,
            ArtifactHashes hashes,
            InputVerification inputVerification,
            OutputVerification outputVerification,
            List<SuiteReport> suites,
            DirectnessSummary aggregate,
            List<QueryRow> queries,
            GateAssessment capabilityGate,
            Decision decision,
            String decisionPolicy,
            OfficialCost cost,
            SealedState sealedFinal,
            String goldAccessState) {

        EvaluationReport {
            suites = List.copyOf(suites);
            queries = List.copyOf(queries);
        }
    }

    enum Decision {
        PROMISING,
        NEEDS_ADJUSTMENT,
        NO_GO
    }

    static final String DECISION_POLICY = "PROMISING iff capability Gate PASS; NO_GO iff "
            + "relation macro-F1 < 0.85, loss >= win, user-macro Direct Top1 gain <= 0, "
            + "or rank1 DIRECT retention < 0.98/not applicable; otherwise NEEDS_ADJUSTMENT";

    static Decision decision(GateAssessment gate, AggregateMetrics metrics) {
        Objects.requireNonNull(gate, "gate");
        Objects.requireNonNull(metrics, "metrics");
        return decision(
                gate.status(),
                metrics.relations().macroF1(),
                metrics.lossCount(),
                metrics.winCount(),
                metrics.userMacro().d0().top1(),
                metrics.userMacro().d1().top1(),
                metrics.rank1Retention().status(),
                metrics.rank1Retention().value());
    }

    static Decision decision(
            SearchV3SemanticDirectnessEvaluator.GateStatus gate,
            double relationMacroF1,
            long losses,
            long wins,
            double userMacroD0Top1,
            double userMacroD1Top1,
            SearchV3SemanticDirectnessEvaluator.MetricStatus retentionStatus,
            Double retention) {
        if (gate == SearchV3SemanticDirectnessEvaluator.GateStatus.PASS) {
            return Decision.PROMISING;
        }
        boolean coreNoGo = relationMacroF1 + SearchV3SemanticDirectnessEvaluator.GATE_EPSILON
                        < SearchV3SemanticDirectnessEvaluator.MIN_RELATION_MACRO_F1
                || losses >= wins
                || userMacroD1Top1
                        <= userMacroD0Top1
                                + SearchV3SemanticDirectnessEvaluator.GATE_EPSILON
                || retentionStatus != SearchV3SemanticDirectnessEvaluator.MetricStatus.APPLICABLE
                || retention == null
                || retention
                                + SearchV3SemanticDirectnessEvaluator.GATE_EPSILON
                        < SearchV3SemanticDirectnessEvaluator.MIN_RANK1_RETENTION;
        return coreNoGo ? Decision.NO_GO : Decision.NEEDS_ADJUSTMENT;
    }

    private static boolean finiteNonNegative(double value) {
        return Double.isFinite(value) && value >= 0.0d;
    }

    private static void requireSha(String value, String label) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(label + " must be a lowercase SHA-256");
        }
    }
}
