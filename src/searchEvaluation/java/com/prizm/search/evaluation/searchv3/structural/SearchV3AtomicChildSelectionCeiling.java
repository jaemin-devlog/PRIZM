package com.prizm.search.evaluation.searchv3.structural;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Gold-after-candidate local atomic-child oracle for PRZ-033. */
final class SearchV3AtomicChildSelectionCeiling {

    static final int SCHEMA_VERSION = 1;
    static final String ARTIFACT_TYPE = "PRZ033_ATOMIC_CHILD_CANDIDATE_INPUT";
    static final String EXPECTED_OUTPUT_FILE_SHA256 =
            "647bf37eae00d5e8c9b909faf0767befeb69e2b31d77b36fa863d7cb2231b1f7";
    static final String EXPECTED_OUTPUT_CANONICAL_SHA256 =
            "d6b29ce518f9571f7313a92feb7e1d8ac8b4b207d2fb7dc7fa0f8527dfc414a4";
    static final String EXPECTED_REPORT_FILE_SHA256 =
            "29af223023a50564aaf276261459b60eb521c3fcd37045588248b0907ffd8847";
    static final String EXPECTED_INPUT_SHA256 =
            "166a8aef77f59d322216d5b1b77cb872d0c18a6e78cfbab07757f281441e83cf";
    static final String EXPECTED_V3_SOURCE_SHA256 =
            "65f301b96bb243b5f9393926a3a502adaa054a0aa7716d7f4fc48f4b6ab2cdad";
    static final String EXPECTED_BGE_DIGEST =
            "7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab";
    static final String EXPECTED_SEALED_COMBINED =
            "e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383";
    static final String EXPECTED_SEALED_MANIFEST =
            "d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa";
    static final String EXPECTED_SEALED_TREE = "a129080861d7dafd32a9b3b3357b61aebb237e59";
    static final double MEANINGFUL_USER_MACRO_TOP1_GAIN = 0.0300d;
    private static final List<String> FROZEN_V3_SOURCE_FILES = List.of(
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralBlockParser.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralEvidenceChildBuilder.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralRetrievalPassageBuilder.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/EvidenceValidationSelector.java");

    private final ObjectMapper mapper = new ObjectMapper();
    private final StructuralBlockParser parser = new StructuralBlockParser();
    private final StructuralEvidenceChildBuilder childBuilder = new StructuralEvidenceChildBuilder();
    private final StructuralRetrievalPassageBuilder passageBuilder = new StructuralRetrievalPassageBuilder();

    VerifiedPrz032 verifyPrz032(Path outputPath, Path reportPath) {
        requireRegularLocalArtifact(outputPath);
        requireRegularLocalArtifact(reportPath);
        try {
            byte[] outputBytes = Files.readAllBytes(outputPath);
            byte[] reportBytes = Files.readAllBytes(reportPath);
            requireHash(EXPECTED_OUTPUT_FILE_SHA256, outputBytes, "PRZ-032 output file");
            requireHash(EXPECTED_REPORT_FILE_SHA256, reportBytes, "PRZ-032 report file");

            JsonNode wrapper = mapper.readTree(outputBytes);
            if (!EXPECTED_OUTPUT_CANONICAL_SHA256.equals(wrapper.path("canonicalSha256").asText())) {
                throw blocked("PRZ-032 wrapper canonical hash changed");
            }
            byte[] canonical = mapper.writeValueAsBytes(wrapper.path("output"));
            requireHash(EXPECTED_OUTPUT_CANONICAL_SHA256, canonical, "PRZ-032 canonical output");
            if (wrapper.path("canonicalByteLength").asInt(-1) != canonical.length) {
                throw blocked("PRZ-032 canonical byte length changed");
            }
            SearchV3MinimalShadowFreeze.OutputArtifact output = mapper.readValue(
                    canonical, SearchV3MinimalShadowFreeze.OutputArtifact.class);
            SearchV3MinimalShadowFreeze freezer = new SearchV3MinimalShadowFreeze();
            SearchV3MinimalShadowFreeze.FrozenOutput frozen = freezer.freeze(output);
            SearchV3MinimalShadowFreeze.VerifiedOutput verified = freezer.verify(outputPath, frozen);
            validateFrozenIdentity(output);
            if (!EXPECTED_V3_SOURCE_SHA256.equals(sourceHash(FROZEN_V3_SOURCE_FILES))) {
                throw blocked("current B3/Typed source differs from PRZ-032 source freeze");
            }

            JsonNode report = mapper.readTree(reportBytes);
            if (!EXPECTED_OUTPUT_CANONICAL_SHA256.equals(
                    report.path("outputCanonicalSha256").asText())
                    || !EXPECTED_OUTPUT_FILE_SHA256.equals(report.path("outputFileSha256").asText())) {
                throw blocked("PRZ-032 report/output identity changed");
            }
            return new VerifiedPrz032(output, verified, EXPECTED_REPORT_FILE_SHA256);
        }
        catch (IOException exception) {
            throw blocked("cannot read PRZ-032 frozen artifacts", exception);
        }
    }

    FrozenCandidateInput deriveCandidateInput(
            VerifiedPrz032 verified,
            SearchV3MinimalShadowDataset.RuntimeInput runtime) {
        Objects.requireNonNull(verified, "verified PRZ-032");
        Objects.requireNonNull(runtime, "runtime");
        if (!EXPECTED_INPUT_SHA256.equals(runtime.canonicalSha256())) {
            throw blocked("PRZ-032 runtime input hash changed");
        }

        ReplayTopology topology = replay(runtime);
        if (!topology.indexUnits().equals(verified.output().v3IndexUnits())) {
            throw blocked("Gold-free B3 index topology differs from PRZ-032 output");
        }
        Map<String, SearchV3MinimalShadowDataset.RuntimeQuery> runtimeQueries = runtime.queries().stream()
                .collect(Collectors.toMap(
                        SearchV3MinimalShadowDataset.RuntimeQuery::queryId,
                        Function.identity(),
                        (left, right) -> { throw blocked("duplicate runtime query"); },
                        LinkedHashMap::new));
        List<QueryCandidateInput> queries = new ArrayList<>();
        for (SearchV3MinimalShadowFreeze.QueryOutput query : verified.output().queries()) {
            SearchV3MinimalShadowDataset.RuntimeQuery runtimeQuery = runtimeQueries.get(query.queryId());
            if (runtimeQuery == null
                    || !query.userBundleId().equals(runtimeQuery.userBundleId())
                    || !query.queryTextSha256().equals(
                            SearchV3MinimalShadowDataset.sha256(runtimeQuery.text()))) {
                throw blocked("runtime/frozen query identity changed: " + query.queryId());
            }
            List<PassageCandidateInput> passages = new ArrayList<>();
            int expectedRank = 1;
            Set<String> candidateIds = new LinkedHashSet<>();
            for (MinimalV3ShadowAdapter.CandidateResult candidate : query.v3().candidates()) {
                if (candidate.rank() != expectedRank++ || !candidateIds.add(candidate.candidateId())
                        || !Double.isFinite(candidate.cosineScore())) {
                    throw blocked("invalid frozen candidate sequence: " + query.queryId());
                }
                ReplayPassage passage = topology.passagesById().get(candidate.candidateId());
                if (passage == null
                        || !query.userBundleId().equals(passage.userBundleId())
                        || !candidate.parentId().equals(passage.passage().parentAnnotationCandidateId())
                        || !candidate.spans().equals(passage.children().stream()
                                .map(ChildInput::span).toList())) {
                    throw blocked("frozen candidate/B3 replay differs: " + query.queryId()
                            + " rank " + candidate.rank());
                }
                passages.add(new PassageCandidateInput(
                        candidate.rank(), candidate.candidateId(), candidate.cosineScore(),
                        candidate.parentId(), passage.children()));
            }
            queries.add(new QueryCandidateInput(
                    query.queryId(), query.userBundleId(), query.professionGroup(), query.language(),
                    query.typedApplicabilityVerified(), List.copyOf(passages)));
        }
        CandidateInput input = new CandidateInput(
                SCHEMA_VERSION, ARTIFACT_TYPE, EXPECTED_OUTPUT_CANONICAL_SHA256,
                EXPECTED_INPUT_SHA256, EXPECTED_BGE_DIGEST, List.copyOf(queries));
        byte[] canonical = mapper.writeValueAsBytes(input);
        return new FrozenCandidateInput(
                input, SearchV3MinimalShadowDataset.sha256(canonical), canonical.length);
    }

    void writeCreateNew(Path path, FrozenCandidateInput frozen) {
        Objects.requireNonNull(frozen, "frozen candidate input");
        try {
            Files.createDirectories(path.toAbsolutePath().normalize().getParent());
            ObjectNode wrapper = mapper.createObjectNode();
            wrapper.put("canonicalSha256", frozen.canonicalSha256());
            wrapper.put("canonicalByteLength", frozen.canonicalByteLength());
            wrapper.set("candidateInput", mapper.valueToTree(frozen.input()));
            Files.writeString(
                    path,
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot CREATE_NEW PRZ-033 candidate input", exception);
        }
    }

    VerifiedCandidateInput verifyCandidateInput(Path path, FrozenCandidateInput expected) {
        requireRegularLocalArtifact(path);
        try {
            byte[] bytes = Files.readAllBytes(path);
            JsonNode wrapper = mapper.readTree(bytes);
            if (!expected.canonicalSha256().equals(wrapper.path("canonicalSha256").asText())
                    || expected.canonicalByteLength()
                    != wrapper.path("canonicalByteLength").asInt(-1)) {
                throw blocked("PRZ-033 candidate wrapper identity changed");
            }
            byte[] canonical = mapper.writeValueAsBytes(wrapper.path("candidateInput"));
            if (!expected.canonicalSha256().equals(SearchV3MinimalShadowDataset.sha256(canonical))
                    || expected.canonicalByteLength() != canonical.length) {
                throw blocked("PRZ-033 candidate canonical identity changed");
            }
            return new VerifiedCandidateInput(
                    expected, SearchV3MinimalShadowDataset.sha256(bytes), bytes.length);
        }
        catch (IOException exception) {
            throw blocked("cannot verify PRZ-033 candidate input", exception);
        }
    }

    SearchV3MinimalShadowGold.GoldSnapshot loadGoldAfterCandidateVerified(
            VerifiedPrz032 artifact,
            VerifiedCandidateInput candidate,
            SearchV3MinimalShadowDataset.RuntimeInput runtime) {
        Objects.requireNonNull(artifact, "verified PRZ-032");
        Objects.requireNonNull(candidate, "verified candidate input");
        Objects.requireNonNull(runtime, "runtime");
        if (!EXPECTED_OUTPUT_CANONICAL_SHA256.equals(
                        candidate.frozen().input().prz032OutputCanonicalSha256())
                || !EXPECTED_INPUT_SHA256.equals(candidate.frozen().input().runtimeInputSha256())) {
            throw new IllegalStateException("verified candidate token is not bound to PRZ-032 input");
        }
        return new SearchV3MinimalShadowGold().loadAfterOutputVerified(
                artifact.verifiedOutput(), runtime);
    }

    CeilingEvaluation evaluate(
            VerifiedCandidateInput verifiedCandidate,
            SearchV3MinimalShadowFreeze.OutputArtifact f0Output,
            SearchV3MinimalShadowGold.GoldSnapshot gold) {
        Objects.requireNonNull(verifiedCandidate, "verified candidate input");
        Objects.requireNonNull(f0Output, "F0 output");
        Objects.requireNonNull(gold, "Gold");
        Map<String, QueryCandidateInput> candidatesByQuery = verifiedCandidate.frozen().input().queries().stream()
                .collect(Collectors.toMap(
                        QueryCandidateInput::queryId, Function.identity(),
                        (left, right) -> { throw new IllegalStateException("duplicate candidate query"); },
                        LinkedHashMap::new));

        SearchV3MinimalShadowEvaluator evaluator = new SearchV3MinimalShadowEvaluator();
        SearchV3MinimalShadowEvaluator.EvaluationReport f0 = evaluator.evaluate(f0Output, gold);
        assertF0Parity(f0);

        List<SearchV3MinimalShadowFreeze.QueryOutput> oracleQueries = new ArrayList<>();
        for (SearchV3MinimalShadowFreeze.QueryOutput query : f0Output.queries()) {
            QueryCandidateInput candidates = candidatesByQuery.get(query.queryId());
            SearchV3MinimalShadowGold.GoldQuery queryGold = gold.queriesById().get(query.queryId());
            if (candidates == null || queryGold == null) {
                throw new IllegalStateException("missing candidate/Gold query: " + query.queryId());
            }
            MinimalV3ShadowAdapter.QueryRun oracleRun = query.typedApplicabilityVerified()
                    ? query.v3()
                    : oracleRun(query.v3(), candidates, queryGold, gold);
            oracleQueries.add(new SearchV3MinimalShadowFreeze.QueryOutput(
                    query.suite(), query.datasetVersion(), query.split(), query.queryId(),
                    query.userBundleId(), query.professionGroup(), query.language(),
                    query.queryTextSha256(), query.typedApplicabilityVerified(), query.v2(), oracleRun));
        }
        SearchV3MinimalShadowFreeze.OutputArtifact oracleOutput = copyWithQueries(
                f0Output, List.copyOf(oracleQueries));
        String f0CandidateHash = candidateIdentity(f0Output);
        String oracleCandidateHash = candidateIdentity(oracleOutput);
        if (!f0CandidateHash.equals(oracleCandidateHash)) {
            throw new IllegalStateException("F0/O_CHILD candidate identity changed");
        }
        assertPassageAndTypedInvariants(f0Output, oracleOutput);

        SearchV3MinimalShadowEvaluator.EvaluationReport oracle = evaluator.evaluate(oracleOutput, gold);
        List<QueryTrace> traces = failureTraces(
                f0, oracle, candidatesByQuery, gold);
        Map<FailureStage, Long> stages = new EnumMap<>(FailureStage.class);
        for (FailureStage stage : FailureStage.values()) stages.put(stage, 0L);
        traces.forEach(value -> stages.compute(value.failureStage(), (key, count) -> count + 1L));
        if (traces.size() != SearchV3MinimalShadowEvaluator.EXPECTED_DIRECT_POSITIVE_COUNT
                || stages.values().stream().mapToLong(Long::longValue).sum() != traces.size()) {
            throw new IllegalStateException("failure-stage classification is not exhaustive/exclusive");
        }

        SafetyAudit safety = safety(
                f0Output, oracleOutput, f0, oracle, candidatesByQuery, f0CandidateHash);
        if (!safety.valid()) {
            throw new IllegalStateException("PRZ-033 structural safety invariant failed: " + safety);
        }
        Decision decision = decide(f0, oracle, traces, stages, safety);
        return new CeilingEvaluation(
                f0Output, oracleOutput, f0, oracle, List.copyOf(traces),
                Collections.unmodifiableMap(new EnumMap<>(stages)),
                f0CandidateHash, safety, decision);
    }

    private ReplayTopology replay(SearchV3MinimalShadowDataset.RuntimeInput runtime) {
        List<SearchV3MinimalShadowFreeze.IndexUnit> indexUnits = new ArrayList<>();
        Map<String, ReplayPassage> passages = new LinkedHashMap<>();
        for (SearchV3MinimalShadowDataset.RuntimeDocument document : runtime.activeDocuments().stream()
                .sorted(Comparator.comparing(SearchV3MinimalShadowDataset.RuntimeDocument::versionId))
                .toList()) {
            StructuralDocument structural = new StructuralDocument(
                    document.userBundleId(), document.documentId(), document.versionId(),
                    document.sourcePath(), null, document.sourceText(), document.contentSha256());
            List<EvidenceChild> children = childBuilder.build(parser.parse(structural));
            for (RetrievalPassage passage : passageBuilder.build(children)) {
                if (!passage.evidenceChildIds().equals(
                        passage.evidenceChildren().stream().map(EvidenceChild::childId).toList())) {
                    throw blocked("B3 replay lost child identity/order");
                }
                List<ChildInput> childInputs = passage.evidenceChildren().stream()
                        .map(child -> child(document.userBundleId(), passage, child)).toList();
                ReplayPassage replay = new ReplayPassage(
                        document.userBundleId(), passage, childInputs);
                if (passages.putIfAbsent(passage.passageId(), replay) != null) {
                    throw blocked("duplicate replay passage ID");
                }
                indexUnits.add(new SearchV3MinimalShadowFreeze.IndexUnit(
                        passage.passageId(), passage.parentAnnotationCandidateId(),
                        childInputs.stream().map(ChildInput::span).toList()));
            }
        }
        return new ReplayTopology(List.copyOf(indexUnits), Map.copyOf(passages));
    }

    private ChildInput child(String owner, RetrievalPassage passage, EvidenceChild child) {
        SourceProvenance source = child.provenance();
        if (!passage.parentAnnotationCandidateId().equals(source.parentAnnotationCandidateId())) {
            throw blocked("B3 child crossed structural parent");
        }
        return new ChildInput(
                child.childId(), source.parentAnnotationCandidateId(),
                new ProductionV2ShadowAdapter.SourceSpan(
                        owner, source.documentId(), source.versionId(), source.sourcePath(), source.page(),
                        source.codePointStart(), source.codePointEnd(), child.sourceText(),
                        source.exactTextSha256()));
    }

    private MinimalV3ShadowAdapter.QueryRun oracleRun(
            MinimalV3ShadowAdapter.QueryRun original,
            QueryCandidateInput candidates,
            SearchV3MinimalShadowGold.GoldQuery queryGold,
            SearchV3MinimalShadowGold.GoldSnapshot gold) {
        List<MinimalV3ShadowAdapter.FinalResult> finals = new ArrayList<>();
        Set<String> selectedChildIds = new LinkedHashSet<>();
        for (PassageCandidateInput passage : candidates.passages()) {
            List<ChildInput> ordered = stableLocalPartition(passage, queryGold, gold);
            for (ChildInput child : ordered) {
                if (selectedChildIds.add(child.evidenceChildId())) {
                    finals.add(new MinimalV3ShadowAdapter.FinalResult(
                            finals.size() + 1, passage.passageId(), passage.rank(), passage.cosineScore(),
                            child.evidenceChildId(), child.span(), null));
                }
                if (finals.size() == MinimalV3ShadowAdapter.RESULT_LIMIT) break;
            }
            if (finals.size() == MinimalV3ShadowAdapter.RESULT_LIMIT) break;
        }
        return new MinimalV3ShadowAdapter.QueryRun(
                original.state(), original.typedApplicabilityVerified(), original.parsedConstraintCount(),
                original.queryEmbeddingMs(), original.rankingMs(), original.preparationMs(),
                original.selectionMs(), original.totalMs(), original.candidates(), List.copyOf(finals),
                original.ownerLeakage(), original.crossParentPassageViolations());
    }

    List<ChildInput> stableLocalPartition(
            PassageCandidateInput passage,
            SearchV3MinimalShadowGold.GoldQuery query,
            SearchV3MinimalShadowGold.GoldSnapshot gold) {
        List<ChildInput> ordered = new ArrayList<>(passage.children());
        ordered.sort(Comparator.comparingInt(child -> relationTier(passage, child, query, gold)));
        return List.copyOf(ordered);
    }

    private int relationTier(
            PassageCandidateInput passage,
            ChildInput child,
            SearchV3MinimalShadowGold.GoldQuery query,
            SearchV3MinimalShadowGold.GoldSnapshot gold) {
        int tier = 2;
        for (Map.Entry<String, String> relation : query.relationByUnitId().entrySet()) {
            SearchV3MinimalShadowGold.GoldUnit unit = gold.units().get(relation.getKey());
            if (unit == null || !covers(child.span(), unit)) continue;
            if (!passage.parentId().equals(unit.parentId())) {
                throw new IllegalStateException("Gold relation crossed structural parent: "
                        + query.queryId() + " / " + child.evidenceChildId());
            }
            if ("DIRECT_SUPPORT".equals(relation.getValue())) return 0;
            if ("RELATED".equals(relation.getValue())) tier = 1;
        }
        return tier;
    }

    private boolean covers(
            ProductionV2ShadowAdapter.SourceSpan child,
            SearchV3MinimalShadowGold.GoldUnit unit) {
        if (!child.userBundleId().equals(unit.userBundleId())) return false;
        for (SearchV3MinimalShadowGold.GoldSpan span : unit.spans()) {
            if (!child.documentId().equals(span.documentId())
                    || !child.versionId().equals(span.versionId())
                    || !Objects.equals(child.page(), span.page())
                    || child.codePointStart() > span.codePointStart()
                    || child.codePointEnd() < span.codePointEnd()) {
                return false;
            }
            verifyContainedText(child, span);
        }
        return true;
    }

    private void verifyContainedText(
            ProductionV2ShadowAdapter.SourceSpan child,
            SearchV3MinimalShadowGold.GoldSpan gold) {
        if (gold.textSha256() == null || gold.textSha256().isBlank()) return;
        int expectedLength = child.codePointEnd() - child.codePointStart();
        if (child.sourceText().codePointCount(0, child.sourceText().length()) != expectedLength) {
            throw new IllegalStateException("child source text/code-point range mismatch");
        }
        int localStart = gold.codePointStart() - child.codePointStart();
        int localEnd = gold.codePointEnd() - child.codePointStart();
        int charStart = child.sourceText().offsetByCodePoints(0, localStart);
        int charEnd = child.sourceText().offsetByCodePoints(0, localEnd);
        String actual = SearchV3MinimalShadowDataset.sha256(
                child.sourceText().substring(charStart, charEnd));
        if (!gold.textSha256().equals(actual)) {
            throw new IllegalStateException("Gold/child contained text hash mismatch");
        }
    }

    private List<QueryTrace> failureTraces(
            SearchV3MinimalShadowEvaluator.EvaluationReport f0,
            SearchV3MinimalShadowEvaluator.EvaluationReport oracle,
            Map<String, QueryCandidateInput> candidates,
            SearchV3MinimalShadowGold.GoldSnapshot gold) {
        Map<String, SearchV3MinimalShadowEvaluator.QueryEvaluation> oracleById = oracle.queries().stream()
                .collect(Collectors.toMap(SearchV3MinimalShadowEvaluator.QueryEvaluation::queryId,
                        Function.identity()));
        List<QueryTrace> result = new ArrayList<>();
        for (SearchV3MinimalShadowEvaluator.QueryEvaluation query : f0.queries()) {
            if (!query.directPositive()) continue;
            SearchV3MinimalShadowEvaluator.QueryEvaluation oracleQuery = oracleById.get(query.queryId());
            SearchV3MinimalShadowGold.GoldQuery queryGold = gold.queriesById().get(query.queryId());
            QueryCandidateInput candidate = candidates.get(query.queryId());
            Integer firstDirectPassage = firstDirectPassage(candidate, queryGold, gold);
            PassageBand underlying = passageBand(firstDirectPassage);
            FailureStage stage;
            boolean multiAspectCoverageError = queryGold.aspectExpression().requiredAspectIds().stream()
                    .distinct().count() > 1
                    && query.v3().candidateRanking().directRecallAt20()
                    && !query.v3().finalRanking().directRecallAt5();
            stage = classifyFailureStage(
                    multiAspectCoverageError, query.v3().finalRanking().top1(), underlying);
            result.add(new QueryTrace(
                    query.queryId(), query.userBundleId(), query.professionGroup(), query.language(),
                    stage, underlying, firstDirectPassage,
                    query.v3().finalRanking().firstDirectRank(),
                    oracleQuery.v3().finalRanking().firstDirectRank(),
                    query.v3().finalRanking().top1(), oracleQuery.v3().finalRanking().top1()));
        }
        return List.copyOf(result);
    }

    FailureStage classifyFailureStage(
            boolean multiAspectCoverageError,
            boolean f0Top1,
            PassageBand underlying) {
        if (multiAspectCoverageError) return FailureStage.MULTI_ASPECT_SELECTION_ERROR;
        if (f0Top1) return FailureStage.FINAL_ALREADY_CORRECT;
        return switch (underlying) {
            case TOP -> FailureStage.TOP_PASSAGE_CHILD_RECOVERABLE;
            case LOWER -> FailureStage.LOWER_PASSAGE_RECOVERABLE;
            case DEEP -> FailureStage.DEEP_PASSAGE_RECOVERABLE;
            case MISS -> FailureStage.RETRIEVAL_MISS;
        };
    }

    private Integer firstDirectPassage(
            QueryCandidateInput candidates,
            SearchV3MinimalShadowGold.GoldQuery query,
            SearchV3MinimalShadowGold.GoldSnapshot gold) {
        for (PassageCandidateInput passage : candidates.passages()) {
            if (passage.children().stream().anyMatch(child -> relationTier(passage, child, query, gold) == 0)) {
                return passage.rank();
            }
        }
        return null;
    }

    private PassageBand passageBand(Integer rank) {
        if (rank == null) return PassageBand.MISS;
        if (rank == 1) return PassageBand.TOP;
        if (rank <= 5) return PassageBand.LOWER;
        return PassageBand.DEEP;
    }

    private void assertF0Parity(SearchV3MinimalShadowEvaluator.EvaluationReport f0) {
        SearchV3MinimalShadowEvaluator.RankingAggregate micro = f0.queryMicro().v3().finalRanking();
        SearchV3MinimalShadowEvaluator.RankingAggregate macro = f0.userMacro().v3().finalRanking();
        requireClose(0.5411764705882353d, micro.top1(), "F0 micro Top1");
        requireClose(0.7576470588235295d, micro.mrr(), "F0 micro MRR");
        requireClose(0.7942377401291937d, micro.ndcgAt5(), "F0 micro nDCG@5");
        requireClose(0.9882352941176471d, micro.directRecallAt5(), "F0 micro Recall@5");
        requireClose(0.587991718426501d, macro.top1(), "F0 user-macro Top1");
        requireClose(0.782712215320911d, macro.mrr(), "F0 user-macro MRR");
        requireClose(0.631578947368421d, f0.typed().constraintCorrectEvidencePrecision(),
                "F0 typed Evidence precision");
    }

    private SafetyAudit safety(
            SearchV3MinimalShadowFreeze.OutputArtifact f0Output,
            SearchV3MinimalShadowFreeze.OutputArtifact oracleOutput,
            SearchV3MinimalShadowEvaluator.EvaluationReport f0,
            SearchV3MinimalShadowEvaluator.EvaluationReport oracle,
            Map<String, QueryCandidateInput> verifiedCandidates,
            String candidateHash) {
        boolean typedExact = true;
        boolean finalProvenance = true;
        Map<String, SearchV3MinimalShadowFreeze.QueryOutput> f0ById = f0Output.queries().stream()
                .collect(Collectors.toMap(SearchV3MinimalShadowFreeze.QueryOutput::queryId,
                        Function.identity()));
        for (SearchV3MinimalShadowFreeze.QueryOutput query : oracleOutput.queries()) {
            SearchV3MinimalShadowFreeze.QueryOutput original = f0ById.get(query.queryId());
            QueryCandidateInput verifiedQuery = verifiedCandidates.get(query.queryId());
            if (query.typedApplicabilityVerified() && !query.v3().equals(original.v3())) typedExact = false;
            Map<String, PassageCandidateInput> passageById = verifiedQuery.passages().stream()
                    .collect(Collectors.toMap(PassageCandidateInput::passageId, Function.identity()));
            for (MinimalV3ShadowAdapter.FinalResult value : query.v3().finalResults()) {
                PassageCandidateInput passage = passageById.get(value.candidateId());
                ChildInput verifiedChild = passage == null ? null : passage.children().stream()
                        .filter(child -> child.evidenceChildId().equals(value.evidenceChildId()))
                        .findFirst().orElse(null);
                if (passage == null || value.denseRank() != passage.rank()
                        || Double.compare(value.cosineScore(), passage.cosineScore()) != 0
                        || verifiedChild == null || !verifiedChild.span().equals(value.span())
                        || !passage.parentId().equals(verifiedChild.parentId())) {
                    finalProvenance = false;
                }
            }
        }
        SearchV3MinimalShadowEvaluator.StructureAggregate f0Structure =
                f0.queryMicro().v3().finalStructure();
        SearchV3MinimalShadowEvaluator.StructureAggregate oracleStructure =
                oracle.queryMicro().v3().finalStructure();
        boolean valid = candidateHash.equals(candidateIdentity(oracleOutput))
                && typedExact && finalProvenance
                && oracleStructure.crossParentContaminationRate() == 0.0d
                && oracleStructure.fragmentationRate() <= f0Structure.fragmentationRate()
                && oracleStructure.duplicateRate() <= f0Structure.duplicateRate();
        return new SafetyAudit(
                valid, candidateHash, typedExact, finalProvenance,
                oracleStructure.crossParentContaminationRate(),
                oracleStructure.fragmentationRate(), oracleStructure.duplicateRate(),
                oracle.v3IndexStructure().crossParentContaminatedUnitCount(),
                oracle.v3IndexStructure().indexUnitCount());
    }

    private Decision decide(
            SearchV3MinimalShadowEvaluator.EvaluationReport f0,
            SearchV3MinimalShadowEvaluator.EvaluationReport oracle,
            List<QueryTrace> traces,
            Map<FailureStage, Long> stages,
            SafetyAudit safety) {
        double userGain = oracle.userMacro().v3().finalRanking().top1()
                - f0.userMacro().v3().finalRanking().top1();
        long recoverableBundles = traces.stream()
                .filter(value -> value.failureStage() == FailureStage.TOP_PASSAGE_CHILD_RECOVERABLE)
                .map(QueryTrace::userBundleId).distinct().count();
        boolean professionRecovered = List.of(
                        "MARKETING_SALES", "FRONTEND_MOBILE", "NON_DEVELOPMENT_GENERAL")
                .stream().anyMatch(key -> sliceGain(f0.professionSlices(), oracle.professionSlices(), key) > 0);
        boolean languageRecovered = List.of("KO", "KO_EN_MIXED").stream()
                .anyMatch(key -> sliceGain(f0.languageSlices(), oracle.languageSlices(), key) > 0);
        long top = stages.get(FailureStage.TOP_PASSAGE_CHILD_RECOVERABLE);
        long lowerAndDeep = stages.get(FailureStage.LOWER_PASSAGE_RECOVERABLE)
                + stages.get(FailureStage.DEEP_PASSAGE_RECOVERABLE);
        boolean build = safety.valid()
                && userGain + 1.0e-12 >= MEANINGFUL_USER_MACRO_TOP1_GAIN
                && top >= 5
                && recoverableBundles >= 3
                && professionRecovered
                && languageRecovered
                && stages.get(FailureStage.RETRIEVAL_MISS) == 0;
        if (build) return Decision.BUILD_CHILD_SELECTOR;
        if (userGain < MEANINGFUL_USER_MACRO_TOP1_GAIN && lowerAndDeep > top) {
            return Decision.PASSAGE_RANKING_FIRST;
        }
        return Decision.CHILD_SELECTOR_NOT_JUSTIFIED;
    }

    private double sliceGain(
            Map<String, SearchV3MinimalShadowEvaluator.ComparisonAggregate> before,
            Map<String, SearchV3MinimalShadowEvaluator.ComparisonAggregate> after,
            String key) {
        SearchV3MinimalShadowEvaluator.ComparisonAggregate left = before.get(key);
        SearchV3MinimalShadowEvaluator.ComparisonAggregate right = after.get(key);
        return left == null || right == null ? 0.0d
                : right.v3().finalRanking().top1() - left.v3().finalRanking().top1();
    }

    private SearchV3MinimalShadowFreeze.OutputArtifact copyWithQueries(
            SearchV3MinimalShadowFreeze.OutputArtifact source,
            List<SearchV3MinimalShadowFreeze.QueryOutput> queries) {
        return new SearchV3MinimalShadowFreeze.OutputArtifact(
                source.schemaVersion(), source.artifactType(), source.codeFreezeCommit(),
                source.sourceFreeze(), source.model(), source.v2Profile(), source.v3Profile(),
                source.jdbcExecutionBoundary(), source.queryCount(), source.userCount(),
                source.documentVersionCount(), source.v2Indexing(), source.v3Indexing(),
                source.v2IndexUnits(), source.v3IndexUnits(), queries, source.sealedState());
    }

    private String candidateIdentity(SearchV3MinimalShadowFreeze.OutputArtifact output) {
        List<QueryCandidateIdentity> values = output.queries().stream()
                .map(value -> new QueryCandidateIdentity(value.queryId(), value.v3().candidates()))
                .toList();
        return SearchV3MinimalShadowDataset.sha256(mapper.writeValueAsBytes(values));
    }

    private void assertPassageAndTypedInvariants(
            SearchV3MinimalShadowFreeze.OutputArtifact f0,
            SearchV3MinimalShadowFreeze.OutputArtifact oracle) {
        if (!f0.v3IndexUnits().equals(oracle.v3IndexUnits())
                || f0.queries().size() != oracle.queries().size()) {
            throw new IllegalStateException("O_CHILD changed B3 topology");
        }
        for (int index = 0; index < f0.queries().size(); index++) {
            SearchV3MinimalShadowFreeze.QueryOutput left = f0.queries().get(index);
            SearchV3MinimalShadowFreeze.QueryOutput right = oracle.queries().get(index);
            if (!left.queryId().equals(right.queryId()) || !left.v3().candidates().equals(right.v3().candidates())) {
                throw new IllegalStateException("O_CHILD changed query/candidate identity");
            }
            if (left.typedApplicabilityVerified() && !left.v3().equals(right.v3())) {
                throw new IllegalStateException("O_CHILD changed PRZ-029 typed selection/state");
            }
        }
    }

    private void validateFrozenIdentity(SearchV3MinimalShadowFreeze.OutputArtifact output) {
        if (output.schemaVersion() != SearchV3MinimalShadowFreeze.SCHEMA_VERSION
                || !SearchV3MinimalShadowFreeze.ARTIFACT_TYPE.equals(output.artifactType())
                || output.queryCount() != 117 || output.queries().size() != 117
                || output.v3IndexUnits().size() != 160
                || !EXPECTED_INPUT_SHA256.equals(output.sourceFreeze().inputCanonicalSha256())
                || !EXPECTED_V3_SOURCE_SHA256.equals(output.sourceFreeze().v3SourceSha256())
                || !"bge-m3:latest".equals(output.model().name())
                || !EXPECTED_BGE_DIGEST.equals(output.model().digest())
                || output.model().dimensions() != 1024
                || !"COSINE".equals(output.model().similarity())
                || !EXPECTED_SEALED_COMBINED.equals(output.sealedState().combinedSha256())
                || !EXPECTED_SEALED_MANIFEST.equals(output.sealedState().manifestSha256())
                || !EXPECTED_SEALED_TREE.equals(output.sealedState().gitTree())
                || output.sealedState().opened()
                || output.sealedState().searchExecuted()
                || !"NOT_RUN".equals(output.sealedState().currentFreshBaseline())) {
            throw blocked("PRZ-032 frozen identity changed");
        }
    }

    private void requireRegularLocalArtifact(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        String portable = normalized.toString().replace('\\', '/').toLowerCase();
        if (!portable.contains("/local/search-v3-evaluation/")
                || !Files.isRegularFile(normalized) || Files.isSymbolicLink(normalized)) {
            throw blocked("invalid local artifact path: " + path);
        }
    }

    private void requireHash(String expected, byte[] value, String label) {
        if (!expected.equals(SearchV3MinimalShadowDataset.sha256(value))) {
            throw blocked(label + " SHA-256 changed");
        }
    }

    private String sourceHash(List<String> paths) throws IOException {
        StringBuilder canonical = new StringBuilder();
        for (String path : paths.stream().sorted().toList()) {
            byte[] bytes = Files.readAllBytes(Path.of(path));
            canonical.append(path.replace('\\', '/')).append('|').append(bytes.length).append('|')
                    .append(SearchV3MinimalShadowDataset.sha256(bytes)).append('\n');
        }
        return SearchV3MinimalShadowDataset.sha256(canonical.toString());
    }

    private void requireClose(double expected, double actual, String label) {
        if (Math.abs(expected - actual) > 1.0e-12) {
            throw new IllegalStateException(label + " differs: expected=" + expected + " actual=" + actual);
        }
    }

    private IllegalStateException blocked(String message) {
        return new IllegalStateException("BLOCKED_PRZ032_ARTIFACT_PARITY: " + message);
    }

    private IllegalStateException blocked(String message, Throwable cause) {
        return new IllegalStateException("BLOCKED_PRZ032_ARTIFACT_PARITY: " + message, cause);
    }

    static final class PhaseGuard {

        private Phase phase = Phase.SOURCE_ONLY;
        private VerifiedPrz032 artifact;
        private FrozenCandidateInput frozen;
        private VerifiedCandidateInput verified;

        Phase phase() {
            return phase;
        }

        VerifiedPrz032 verifyArtifact(Supplier<VerifiedPrz032> supplier) {
            require(Phase.SOURCE_ONLY);
            artifact = Objects.requireNonNull(supplier.get(), "verified artifact");
            phase = Phase.ARTIFACT_VERIFIED;
            return artifact;
        }

        FrozenCandidateInput freezeCandidate(Supplier<FrozenCandidateInput> supplier) {
            require(Phase.ARTIFACT_VERIFIED);
            frozen = Objects.requireNonNull(supplier.get(), "frozen candidate");
            phase = Phase.CANDIDATE_INPUT_FROZEN;
            return frozen;
        }

        VerifiedCandidateInput verifyCandidate(Function<FrozenCandidateInput, VerifiedCandidateInput> verifier) {
            require(Phase.CANDIDATE_INPUT_FROZEN);
            verified = Objects.requireNonNull(verifier.apply(frozen), "verified candidate");
            phase = Phase.CANDIDATE_INPUT_VERIFIED;
            return verified;
        }

        <T> T joinGold(BiFunction<VerifiedPrz032, VerifiedCandidateInput, T> supplier) {
            require(Phase.CANDIDATE_INPUT_VERIFIED);
            T value = Objects.requireNonNull(
                    supplier.apply(artifact, verified), "Gold supplier returned null");
            phase = Phase.GOLD_JOINED;
            return value;
        }

        CeilingEvaluation oracle(Function<VerifiedCandidateInput, CeilingEvaluation> evaluator) {
            require(Phase.GOLD_JOINED);
            CeilingEvaluation value = Objects.requireNonNull(evaluator.apply(verified), "oracle result");
            phase = Phase.ORACLE_EVALUATED;
            return value;
        }

        private void require(Phase expected) {
            if (phase != expected) {
                throw new IllegalStateException(
                        "PRZ-033 phase violation: expected " + expected + " but was " + phase);
            }
        }
    }

    enum Phase {
        SOURCE_ONLY,
        ARTIFACT_VERIFIED,
        CANDIDATE_INPUT_FROZEN,
        CANDIDATE_INPUT_VERIFIED,
        GOLD_JOINED,
        ORACLE_EVALUATED
    }

    enum FailureStage {
        FINAL_ALREADY_CORRECT,
        TOP_PASSAGE_CHILD_RECOVERABLE,
        LOWER_PASSAGE_RECOVERABLE,
        DEEP_PASSAGE_RECOVERABLE,
        RETRIEVAL_MISS,
        MULTI_ASPECT_SELECTION_ERROR
    }

    enum PassageBand {
        TOP,
        LOWER,
        DEEP,
        MISS
    }

    enum Decision {
        BUILD_CHILD_SELECTOR,
        PASSAGE_RANKING_FIRST,
        CHILD_SELECTOR_NOT_JUSTIFIED
    }

    record VerifiedPrz032(
            SearchV3MinimalShadowFreeze.OutputArtifact output,
            SearchV3MinimalShadowFreeze.VerifiedOutput verifiedOutput,
            String reportFileSha256) {
    }

    record CandidateInput(
            int schemaVersion,
            String artifactType,
            String prz032OutputCanonicalSha256,
            String runtimeInputSha256,
            String bgeM3Digest,
            List<QueryCandidateInput> queries) {

        CandidateInput {
            queries = List.copyOf(queries);
        }
    }

    record QueryCandidateInput(
            String queryId,
            String userBundleId,
            String professionGroup,
            String language,
            boolean typedApplicabilityVerified,
            List<PassageCandidateInput> passages) {

        QueryCandidateInput {
            passages = List.copyOf(passages);
        }
    }

    record PassageCandidateInput(
            int rank,
            String passageId,
            double cosineScore,
            String parentId,
            List<ChildInput> children) {

        PassageCandidateInput {
            children = List.copyOf(children);
            if (children.stream().anyMatch(child -> !parentId.equals(child.parentId()))) {
                throw new IllegalArgumentException("candidate child crossed passage parent");
            }
        }
    }

    record ChildInput(
            String evidenceChildId,
            String parentId,
            ProductionV2ShadowAdapter.SourceSpan span) {
    }

    record FrozenCandidateInput(CandidateInput input, String canonicalSha256, int canonicalByteLength) {
    }

    record VerifiedCandidateInput(FrozenCandidateInput frozen, String fileSha256, long fileBytes) {
    }

    record QueryTrace(
            String queryId,
            String userBundleId,
            String professionGroup,
            String language,
            FailureStage failureStage,
            PassageBand underlyingPassageBand,
            Integer firstDirectPassageRank,
            Integer f0FirstDirectFinalRank,
            Integer oracleFirstDirectFinalRank,
            boolean f0Top1,
            boolean oracleTop1) {
    }

    record SafetyAudit(
            boolean valid,
            String candidateIdentitySha256,
            boolean typedSelectionAndStateExact,
            boolean provenanceExact,
            double finalCrossParentContaminationRate,
            double finalFragmentationRate,
            double finalDuplicateRate,
            long frozenIndexCrossParentContaminatedPassages,
            long frozenIndexPassageCount) {
    }

    record CeilingEvaluation(
            SearchV3MinimalShadowFreeze.OutputArtifact f0Output,
            SearchV3MinimalShadowFreeze.OutputArtifact oracleOutput,
            SearchV3MinimalShadowEvaluator.EvaluationReport f0,
            SearchV3MinimalShadowEvaluator.EvaluationReport oracle,
            List<QueryTrace> queryTraces,
            Map<FailureStage, Long> failureStages,
            String candidateIdentitySha256,
            SafetyAudit safety,
            Decision decision) {
    }

    private record ReplayTopology(
            List<SearchV3MinimalShadowFreeze.IndexUnit> indexUnits,
            Map<String, ReplayPassage> passagesById) {
    }

    private record ReplayPassage(
            String userBundleId,
            RetrievalPassage passage,
            List<ChildInput> children) {
    }

    private record QueryCandidateIdentity(
            String queryId,
            List<MinimalV3ShadowAdapter.CandidateResult> candidates) {
    }
}
