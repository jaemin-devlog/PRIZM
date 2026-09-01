package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Official one-shot PRZ-033 local child-selection ceiling. No model is called. */
class Prz033AtomicChildSelectionCeilingBenchmarkTest {

    static final String CODE_FREEZE_PROPERTY = "prizm.prz033.code-freeze-commit";
    static final Path PRZ032_OUTPUT = Path.of(
            "local/search-v3-evaluation/prz032/minimal-v3-shadow-output.json");
    static final Path PRZ032_REPORT = Path.of(
            "local/search-v3-evaluation/prz032/minimal-v3-shadow-report.json");
    static final Path CANDIDATE_INPUT = Path.of(
            "local/search-v3-evaluation/prz033/candidate-input.json");
    static final Path REPORT = Path.of(
            "local/search-v3-evaluation/prz033/atomic-child-selection-ceiling.json");
    static final Path SEALED_MANIFEST = Path.of(
            "src/test/resources/search-v3-evaluation/sealed-final/manifest.json");
    static final String SEALED_GIT_PATH = "src/test/resources/search-v3-evaluation/sealed-final";
    static final Pattern SHA = Pattern.compile("^[0-9a-f]{40}$");

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void runsOfficialCeilingOnceFromVerifiedPrz032CandidatesWithoutModelExecution() throws Exception {
        String codeFreeze = System.getProperty(CODE_FREEZE_PROPERTY, "");
        assumeTrue(!codeFreeze.isBlank(), "PRZ-033 official ceiling is opt-in");
        assertThat(codeFreeze).matches(SHA);
        assertThat(git("rev-parse", "HEAD")).isEqualTo(codeFreeze);
        assertThat(git("status", "--porcelain")).isBlank();
        assertThat(Files.exists(REPORT)).isFalse();

        SealedSnapshot sealedBefore = sealedSnapshot();
        SearchV3AtomicChildSelectionCeiling ceiling = new SearchV3AtomicChildSelectionCeiling();
        SearchV3AtomicChildSelectionCeiling.PhaseGuard guard =
                new SearchV3AtomicChildSelectionCeiling.PhaseGuard();
        SearchV3AtomicChildSelectionCeiling.VerifiedPrz032 artifact = guard.verifyArtifact(
                () -> ceiling.verifyPrz032(PRZ032_OUTPUT, PRZ032_REPORT));
        SearchV3MinimalShadowDataset.RuntimeInput runtime = new SearchV3MinimalShadowDataset().loadRuntime();
        SearchV3AtomicChildSelectionCeiling.FrozenCandidateInput frozen = guard.freezeCandidate(
                () -> ceiling.deriveCandidateInput(artifact, runtime));
        if (!Files.exists(CANDIDATE_INPUT)) {
            ceiling.writeCreateNew(CANDIDATE_INPUT, frozen);
        }
        SearchV3AtomicChildSelectionCeiling.VerifiedCandidateInput verified = guard.verifyCandidate(
                value -> ceiling.verifyCandidateInput(CANDIDATE_INPUT, value));

        SearchV3MinimalShadowGold.GoldSnapshot gold = guard.joinGold(
                (verifiedArtifact, verifiedInput) -> ceiling.loadGoldAfterCandidateVerified(
                        verifiedArtifact, verifiedInput, runtime));
        SearchV3AtomicChildSelectionCeiling.CeilingEvaluation evaluation = guard.oracle(
                candidate -> ceiling.evaluate(candidate, artifact.output(), gold));
        assertThat(guard.phase())
                .isEqualTo(SearchV3AtomicChildSelectionCeiling.Phase.ORACLE_EVALUATED);

        OfficialReport report = officialReport(codeFreeze, artifact, verified, evaluation, sealedBefore);
        writeCreateNew(REPORT, report);

        assertThat(sealedSnapshot()).isEqualTo(sealedBefore);
        assertThat(git("rev-parse", "HEAD")).isEqualTo(codeFreeze);
        assertThat(git("status", "--porcelain")).isBlank();
        assertThat(report.embeddingOrModelExecutions()).isZero();
        assertThat(report.failureStages().values().stream().mapToLong(Long::longValue).sum()).isEqualTo(85);
        assertThat(report.safety().valid()).isTrue();

        System.out.println("PRZ033_CANDIDATE_CANONICAL_SHA256=" + frozen.canonicalSha256());
        System.out.println("PRZ033_CANDIDATE_FILE_SHA256=" + verified.fileSha256());
        System.out.println("PRZ033_REPORT_SHA256="
                + SearchV3MinimalShadowDataset.sha256(Files.readAllBytes(REPORT)));
        System.out.println("PRZ033_DECISION=" + evaluation.decision());
    }

    private OfficialReport officialReport(
            String codeFreeze,
            SearchV3AtomicChildSelectionCeiling.VerifiedPrz032 artifact,
            SearchV3AtomicChildSelectionCeiling.VerifiedCandidateInput candidate,
            SearchV3AtomicChildSelectionCeiling.CeilingEvaluation evaluation,
            SealedSnapshot sealed) {
        SearchV3MinimalShadowEvaluator.EvaluationReport f0 = evaluation.f0();
        SearchV3MinimalShadowEvaluator.EvaluationReport oracle = evaluation.oracle();
        MetricSnapshot f0Metric = metrics(f0);
        MetricSnapshot oracleMetric = metrics(oracle);
        double denominator = f0.queryMicro().v3().candidate().top1()
                - f0.queryMicro().v3().finalRanking().top1();
        double capture = denominator == 0.0d ? 0.0d
                : (oracle.queryMicro().v3().finalRanking().top1()
                        - f0.queryMicro().v3().finalRanking().top1()) / denominator;
        long recoverableBundles = evaluation.queryTraces().stream()
                .filter(value -> value.failureStage()
                        == SearchV3AtomicChildSelectionCeiling.FailureStage.TOP_PASSAGE_CHILD_RECOVERABLE)
                .map(SearchV3AtomicChildSelectionCeiling.QueryTrace::userBundleId).distinct().count();
        return new OfficialReport(
                1,
                "PRZ033_ATOMIC_EVIDENCE_CHILD_SELECTION_CEILING",
                codeFreeze,
                artifact.verifiedOutput().fileSha256(),
                artifact.reportFileSha256(),
                artifact.verifiedOutput().frozen().canonicalSha256(),
                candidate.frozen().canonicalSha256(),
                candidate.fileSha256(),
                evaluation.candidateIdentitySha256(),
                0,
                85,
                evaluation.failureStages(),
                recoverableBundles,
                f0Metric,
                oracleMetric,
                new GainSnapshot(
                        oracleMetric.queryTop1() - f0Metric.queryTop1(),
                        oracleMetric.queryMrr() - f0Metric.queryMrr(),
                        oracleMetric.queryNdcgAt5() - f0Metric.queryNdcgAt5(),
                        oracleMetric.userTop1() - f0Metric.userTop1(),
                        oracleMetric.userMrr() - f0Metric.userMrr(),
                        f0.queryMicro().v3().candidate().top1(),
                        capture),
                sliceRecovery(f0.professionSlices(), oracle.professionSlices()),
                sliceRecovery(f0.languageSlices(), oracle.languageSlices()),
                new TypedSnapshot(
                        f0.typed().queryCount(),
                        f0.typed().stateAccuracy(), oracle.typed().stateAccuracy(),
                        f0.typed().stateMacroF1(), oracle.typed().stateMacroF1(),
                        f0.typed().falseNoneCount(), oracle.typed().falseNoneCount(),
                        f0.typed().constraintCorrectEvidencePrecision(),
                        oracle.typed().constraintCorrectEvidencePrecision(),
                        true,
                        oracle.typed().constraintCorrectEvidencePrecision()),
                evaluation.safety(),
                sealed,
                evaluation.decision(),
                evaluation.queryTraces());
    }

    private MetricSnapshot metrics(SearchV3MinimalShadowEvaluator.EvaluationReport report) {
        SearchV3MinimalShadowEvaluator.RankingAggregate query =
                report.queryMicro().v3().finalRanking();
        SearchV3MinimalShadowEvaluator.RankingAggregate user =
                report.userMacro().v3().finalRanking();
        return new MetricSnapshot(
                query.applicableQueryCount(), query.ndcgApplicableQueryCount(),
                query.top1(), query.mrr(), query.ndcgAt5(), query.directRecallAt5(),
                user.top1(), user.mrr(), user.ndcgAt5(), user.directRecallAt5());
    }

    private Map<String, SliceSnapshot> sliceRecovery(
            Map<String, SearchV3MinimalShadowEvaluator.ComparisonAggregate> f0,
            Map<String, SearchV3MinimalShadowEvaluator.ComparisonAggregate> oracle) {
        Map<String, SliceSnapshot> result = new LinkedHashMap<>();
        f0.forEach((key, before) -> {
            SearchV3MinimalShadowEvaluator.ComparisonAggregate after = oracle.get(key);
            SearchV3MinimalShadowEvaluator.RankingAggregate left = before.v3().finalRanking();
            SearchV3MinimalShadowEvaluator.RankingAggregate right = after.v3().finalRanking();
            result.put(key, new SliceSnapshot(
                    before.queryCount(), before.directPositiveQueryCount(),
                    left.top1(), right.top1(), right.top1() - left.top1(),
                    left.mrr(), right.mrr(), right.mrr() - left.mrr()));
        });
        return Collections.unmodifiableMap(new LinkedHashMap<>(result));
    }

    private SealedSnapshot sealedSnapshot() throws Exception {
        byte[] bytes = Files.readAllBytes(SEALED_MANIFEST);
        assertThat(SearchV3MinimalShadowDataset.sha256(bytes))
                .isEqualTo(SearchV3AtomicChildSelectionCeiling.EXPECTED_SEALED_MANIFEST);
        JsonNode manifest = mapper.readTree(bytes);
        assertThat(manifest.path("combinedSha256").asText())
                .isEqualTo(SearchV3AtomicChildSelectionCeiling.EXPECTED_SEALED_COMBINED);
        assertThat(manifest.path("opened").asBoolean(true)).isFalse();
        assertThat(manifest.path("searchExecuted").asBoolean(true)).isFalse();
        assertThat(git("rev-parse", "HEAD:" + SEALED_GIT_PATH))
                .isEqualTo(SearchV3AtomicChildSelectionCeiling.EXPECTED_SEALED_TREE);
        return new SealedSnapshot(
                SearchV3AtomicChildSelectionCeiling.EXPECTED_SEALED_COMBINED,
                SearchV3AtomicChildSelectionCeiling.EXPECTED_SEALED_MANIFEST,
                SearchV3AtomicChildSelectionCeiling.EXPECTED_SEALED_TREE,
                false, false, "NOT_RUN");
    }

    private void writeCreateNew(Path path, Object value) throws IOException {
        Files.createDirectories(path.toAbsolutePath().normalize().getParent());
        Files.writeString(
                path,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
    }

    private String git(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) throw new IllegalStateException("git failed: " + output);
        return output;
    }

    record OfficialReport(
            int schemaVersion,
            String artifactType,
            String codeFreezeCommit,
            String prz032OutputFileSha256,
            String prz032ReportFileSha256,
            String prz032OutputCanonicalSha256,
            String candidateInputCanonicalSha256,
            String candidateInputFileSha256,
            String candidateIdentitySha256,
            int embeddingOrModelExecutions,
            int directPositiveQueryCount,
            Map<SearchV3AtomicChildSelectionCeiling.FailureStage, Long> failureStages,
            long topPassageRecoverableUserBundles,
            MetricSnapshot f0,
            MetricSnapshot localChildOracle,
            GainSnapshot gain,
            Map<String, SliceSnapshot> professionSlices,
            Map<String, SliceSnapshot> languageSlices,
            TypedSnapshot typed,
            SearchV3AtomicChildSelectionCeiling.SafetyAudit safety,
            SealedSnapshot sealedFinal,
            SearchV3AtomicChildSelectionCeiling.Decision decision,
            List<SearchV3AtomicChildSelectionCeiling.QueryTrace> queryTraces) {
    }

    record MetricSnapshot(
            int directPositiveQueryCount,
            int ndcgApplicableQueryCount,
            double queryTop1,
            double queryMrr,
            double queryNdcgAt5,
            double queryRecallAt5,
            double userTop1,
            double userMrr,
            double userNdcgAt5,
            double userRecallAt5) {
    }

    record GainSnapshot(
            double queryTop1,
            double queryMrr,
            double queryNdcgAt5,
            double userTop1,
            double userMrr,
            double candidateTop1Ceiling,
            double candidateTop1HeadroomCaptureRatio) {
    }

    record SliceSnapshot(
            int queryCount,
            int directPositiveQueryCount,
            double f0Top1,
            double oracleTop1,
            double top1Gain,
            double f0Mrr,
            double oracleMrr,
            double mrrGain) {
    }

    record TypedSnapshot(
            int queryCount,
            double f0StateAccuracy,
            double oracleStateAccuracy,
            double f0StateMacroF1,
            double oracleStateMacroF1,
            long f0FalseNone,
            long oracleFalseNone,
            double f0SelectedEvidencePrecision,
            double oracleSelectedEvidencePrecision,
            boolean selectedSetAndOrderExact,
            double strictChildSelectionPrecisionCeiling) {
    }

    record SealedSnapshot(
            String combinedSha256,
            String manifestSha256,
            String gitTree,
            boolean opened,
            boolean searchExecuted,
            String currentFreshBaseline) {
    }
}
