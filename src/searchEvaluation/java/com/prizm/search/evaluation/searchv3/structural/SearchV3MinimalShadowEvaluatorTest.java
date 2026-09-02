package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SearchV3MinimalShadowEvaluatorTest {

    @Test
    void scoresSourceSpansSeparatesCandidateAndFinalAndDetectsStructuralGain() {
        Fixture fixture = fixture();

        SearchV3MinimalShadowEvaluator.EvaluationReport report =
                new SearchV3MinimalShadowEvaluator().evaluate(fixture.output(), fixture.gold());

        assertThat(report.queries()).hasSize(117);
        assertThat(report.queryMicro().v2().candidate().directRecallAt20()).isEqualTo(1.0d);
        assertThat(report.queryMicro().v3().candidate().directRecallAt20()).isEqualTo(1.0d);
        assertThat(report.queryMicro().v2().finalRanking().top1()).isEqualTo(1.0d);
        assertThat(report.queryMicro().v3().finalRanking().top1()).isEqualTo(1.0d);
        assertThat(report.v2IndexStructure().crossParentContaminationRate()).isGreaterThan(0.0d);
        assertThat(report.v3IndexStructure().crossParentContaminationRate()).isZero();
        assertThat(report.queryMicro().classifications())
                .containsEntry(SearchV3MinimalShadowEvaluator.PrimaryClassification.BOTH_CORRECT, 85L)
                .containsEntry(SearchV3MinimalShadowEvaluator.PrimaryClassification.NOT_APPLICABLE, 32L);
        assertThat(report.semantic().v3AssessedStateViolationCount()).isZero();
        assertThat(report.decision())
                .isEqualTo(SearchV3MinimalShadowEvaluator.Decision.MINIMAL_V3_AHEAD);
    }

    @Test
    void acceptsAnExplicitInventoryWithoutChangingThePrz032Default() {
        Fixture fixture = subset(fixture(), 3);
        SearchV3MinimalShadowEvaluator.InventoryContract inventory =
                new SearchV3MinimalShadowEvaluator.InventoryContract(
                        "NON_SEALED_UNIT", 3, 3, 3, 0, Map.of("ORIGINAL", 3L));

        SearchV3MinimalShadowEvaluator.EvaluationReport report =
                new SearchV3MinimalShadowEvaluator().evaluate(
                        fixture.output(), fixture.gold(), inventory);

        assertThat(report.queries()).hasSize(3);
        assertThat(report.dedup().rawLineageCount()).isEqualTo(3);
        assertThat(report.dedup().suiteCounts()).containsExactlyEntriesOf(Map.of("ORIGINAL", 3L));
    }

    @Test
    void rejectsAnExplicitInventoryWhenTheDeclaredUserCountDoesNotMatch() {
        Fixture fixture = subset(fixture(), 3);
        SearchV3MinimalShadowEvaluator.InventoryContract inventory =
                new SearchV3MinimalShadowEvaluator.InventoryContract(
                        "NON_SEALED_UNIT", 3, 2, 3, 0, Map.of("ORIGINAL", 3L));

        assertThatThrownBy(() -> new SearchV3MinimalShadowEvaluator().evaluate(
                fixture.output(), fixture.gold(), inventory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("user inventory changed");
    }

    @Test
    void finalGateDoesNotTurnAProtocolSeedIntoAdoptionEvidence() {
        Fixture fixture = fixture();
        var report = new SearchV3MinimalShadowEvaluator().evaluate(fixture.output(), fixture.gold());
        var input = new Prz042FinalFreeze.VerifiedInput(
                Path.of("contract"), "a".repeat(64), Path.of("split"), Path.of("manifest"),
                "b".repeat(64), "c".repeat(64), "seed", "SEALED_FINAL_TEST",
                "d".repeat(64), "e".repeat(40), "f".repeat(40),
                "bge-m3", "digest", 1024, Map.of(), 23, 117, 85, 32);
        var runtime = new Prz042FinalFreeze.RuntimeAudit(
                23, 117, 117, 117, 0, 117, 117, 117, 117,
                0, 0, 0, 0, 0, true, "bge-m3", "digest", 1024,
                117, 117, 0, 0, false);

        Prz042FinalGate.GateReport gate = new Prz042FinalGate().evaluate(report, runtime, input);

        assertThat(gate.verdict()).isEqualTo(Prz042FinalGate.Verdict.V3_NEEDS_ADJUSTMENT);
        assertThat(gate.releaseAdequacy()).isEqualTo(Prz042FinalGate.Status.FAIL);
        assertThat(gate.primaryQuality()).isEqualTo(Prz042FinalGate.Status.NOT_ASSESSED);
        assertThat(gate.indexingAndStorage()).isEqualTo(Prz042FinalGate.Status.NOT_ASSESSED);
        assertThat(gate.actualRuntime()).isEqualTo(Prz042FinalGate.Status.PASS);
        assertThat(gate.resources()).isEqualTo(Prz042FinalGate.Status.PASS);
    }

    private Fixture subset(Fixture source, int count) {
        List<SearchV3MinimalShadowFreeze.QueryOutput> outputs = source.output().queries().stream()
                .limit(count).toList();
        Map<String, SearchV3MinimalShadowGold.GoldQuery> queries = new LinkedHashMap<>();
        outputs.forEach(value -> queries.put(value.queryId(), source.gold().queriesById().get(value.queryId())));
        SearchV3MinimalShadowFreeze.IndexingStats stats =
                new SearchV3MinimalShadowFreeze.IndexingStats(count, count, 1, 1, 1, count * 4096L);
        SearchV3MinimalShadowFreeze.OutputArtifact output =
                new SearchV3MinimalShadowFreeze.OutputArtifact(
                        source.output().schemaVersion(), source.output().artifactType(),
                        source.output().codeFreezeCommit(), source.output().sourceFreeze(),
                        source.output().model(), source.output().v2Profile(), source.output().v3Profile(),
                        source.output().jdbcExecutionBoundary(), count, count, count, stats, stats,
                        source.output().v2IndexUnits().stream().limit(count).toList(),
                        source.output().v3IndexUnits().stream().limit(count).toList(), outputs,
                        source.output().sealedState());
        SearchV3MinimalShadowGold.GoldSnapshot gold = new SearchV3MinimalShadowGold.GoldSnapshot(
                queries, source.gold().units(), source.gold().parents(), source.gold().groups());
        return new Fixture(output, gold);
    }

    private Fixture fixture() {
        int[] counts = {21, 24, 24, 24, 24};
        String[] suites = {"ORIGINAL", "LONG_FORM", "ROBUSTNESS", "TYPED_STRESS", "SEMANTIC_STRESS"};
        Map<String, SearchV3MinimalShadowGold.GoldQuery> queries = new LinkedHashMap<>();
        Map<String, SearchV3MinimalShadowGold.GoldUnit> units = new LinkedHashMap<>();
        Map<String, SearchV3MinimalShadowGold.GoldParent> parents = new LinkedHashMap<>();
        Map<String, SearchV3MinimalShadowGold.GoldGroup> groups = new LinkedHashMap<>();
        List<SearchV3MinimalShadowFreeze.QueryOutput> outputs = new ArrayList<>();
        List<SearchV3MinimalShadowFreeze.IndexUnit> v2Index = new ArrayList<>();
        List<SearchV3MinimalShadowFreeze.IndexUnit> v3Index = new ArrayList<>();
        int ordinal = 0;
        for (int suiteIndex = 0; suiteIndex < suites.length; suiteIndex++) {
            String suite = suites[suiteIndex];
            for (int local = 0; local < counts[suiteIndex]; local++) {
                ordinal++;
                boolean direct = ordinal <= 85;
                boolean typed = "TYPED_STRESS".equals(suite);
                String queryId = "Q" + ordinal;
                String owner = "U" + ((ordinal - 1) % 23 + 1);
                String document = "D" + ordinal;
                String version = document + "-V1";
                String unitId = "E" + ordinal;
                String parent = "P" + ordinal;
                String group = "G" + ordinal;
                String text = "abcdefghij";
                String wide = text + "klmnopqrst" + "uvwxyzABCD";
                ProductionV2ShadowAdapter.SourceSpan v2Span = span(owner, document, version, 0, 30, wide);
                ProductionV2ShadowAdapter.SourceSpan v3Span = span(owner, document, version, 0, 10, text);
                SearchV3MinimalShadowGold.GoldSpan goldSpan = new SearchV3MinimalShadowGold.GoldSpan(
                        document, version, null, 0, 10, SearchV3MinimalShadowDataset.sha256(text));
                SearchV3MinimalShadowGold.GoldUnit unit = new SearchV3MinimalShadowGold.GoldUnit(
                        unitId, owner, parent, group, document, version, List.of(goldSpan));
                units.put(unitId, unit);
                parents.put(parent, new SearchV3MinimalShadowGold.GoldParent(
                        parent, owner, document, version, goldSpan));
                if (direct) {
                    String otherParent = parent + "-OTHER";
                    parents.put(otherParent, new SearchV3MinimalShadowGold.GoldParent(
                            otherParent, owner, document, version,
                            new SearchV3MinimalShadowGold.GoldSpan(
                                    document, version, null, 20, 30,
                                    SearchV3MinimalShadowDataset.sha256(wide.substring(20, 30)))));
                }
                groups.put(group, new SearchV3MinimalShadowGold.GoldGroup(group, owner, List.of(unitId)));
                String relation = direct ? "DIRECT_SUPPORT" : "CONTRADICTS";
                String expectedState = typed ? (direct ? "FOUND" : "NONE") : null;
                SearchV3MinimalShadowGold.Aspect aspect = new SearchV3MinimalShadowGold.Aspect(
                        "claim", true, direct ? 1 : 0, direct ? List.of(group) : List.of(),
                        Map.of(unitId, relation));
                String queryText = "synthetic query " + ordinal;
                queries.put(queryId, new SearchV3MinimalShadowGold.GoldQuery(
                        suite, "version-1", "DEV", queryId, owner, queryText,
                        direct ? "SUPPORTED" : "NOT_SUPPORTED", "EN",
                        typed ? List.of("numeric_quantity") : List.of("semantic_paraphrase"),
                        new SearchV3MinimalShadowGold.AspectExpression("ALL", List.of("claim"), 1),
                        List.of(aspect), Map.of(unitId, relation), expectedState));

                ProductionV2ShadowAdapter.QueryRun v2 = new ProductionV2ShadowAdapter.QueryRun(
                        "EVIDENCE_FOUND", 1, 1, 1, 2, 2,
                        List.of(new ProductionV2ShadowAdapter.CandidateResult(1, "V2-" + ordinal, 0.9, v2Span)),
                        List.of(new ProductionV2ShadowAdapter.FinalResult(
                                1, "V2-" + ordinal, 0.9, v2Span, "V2-" + ordinal,
                                v2Span, wide, v2Span)), false, false);
                MinimalV3ShadowAdapter.QueryRun v3 = new MinimalV3ShadowAdapter.QueryRun(
                        typed ? expectedState : "UNASSESSED", typed, typed ? 1 : 0,
                        1, 1, 1, 1, 4,
                        List.of(new MinimalV3ShadowAdapter.CandidateResult(
                                1, "V3-" + ordinal, 0.9, parent, List.of(v3Span))),
                        List.of(new MinimalV3ShadowAdapter.FinalResult(
                                1, "V3-" + ordinal, 1, 0.9, "C" + ordinal, v3Span,
                                typed ? (direct ? "SATISFIED" : "CONTRADICTED") : null)),
                        false, 0);
                outputs.add(new SearchV3MinimalShadowFreeze.QueryOutput(
                        suite, "version-1", "DEV", queryId, owner, "GENERAL", "EN",
                        SearchV3MinimalShadowDataset.sha256(queryText), typed, v2, v3));
                v2Index.add(new SearchV3MinimalShadowFreeze.IndexUnit(
                        "V2-" + ordinal, null, List.of(v2Span)));
                v3Index.add(new SearchV3MinimalShadowFreeze.IndexUnit(
                        "V3-" + ordinal, parent, List.of(v3Span)));
            }
        }
        SearchV3MinimalShadowFreeze.IndexingStats v2Stats = new SearchV3MinimalShadowFreeze.IndexingStats(
                117, 117, 10, 20, 15, 117L * 4096);
        SearchV3MinimalShadowFreeze.IndexingStats v3Stats = new SearchV3MinimalShadowFreeze.IndexingStats(
                117, 117, 10, 20, 15, 117L * 4096);
        SearchV3MinimalShadowFreeze.OutputArtifact output = new SearchV3MinimalShadowFreeze.OutputArtifact(
                1, SearchV3MinimalShadowFreeze.ARTIFACT_TYPE, "0".repeat(40),
                new SearchV3MinimalShadowFreeze.SourceFreeze("a", "b", "c", "d", "e", "f"),
                new SearchV3MinimalShadowFreeze.ModelIdentity("bge-m3:latest", "digest", 1024, "COSINE"),
                "v2", "v3", "test", 117, 23, 117, v2Stats, v3Stats,
                v2Index, v3Index, outputs,
                new SearchV3MinimalShadowFreeze.SealedState("s", "m", "t", false, false, "NOT_RUN"));
        return new Fixture(output, new SearchV3MinimalShadowGold.GoldSnapshot(
                queries, units, parents, groups));
    }

    private ProductionV2ShadowAdapter.SourceSpan span(
            String owner, String document, String version, int start, int end, String text) {
        return new ProductionV2ShadowAdapter.SourceSpan(
                owner, document, version, "source.txt", null, start, end, text,
                SearchV3MinimalShadowDataset.sha256(text));
    }

    private record Fixture(
            SearchV3MinimalShadowFreeze.OutputArtifact output,
            SearchV3MinimalShadowGold.GoldSnapshot gold) {
    }
}
