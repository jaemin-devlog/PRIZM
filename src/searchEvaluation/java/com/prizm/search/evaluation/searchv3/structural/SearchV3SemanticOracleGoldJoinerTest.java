package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.CandidateProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvaluationTrack;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvidenceChildProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.FreezeInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.GoldJoined;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.PhaseGuard;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.QueryProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.VerifiedCandidates;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.DatasetSlice;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.ExpectedEvidence;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.GoldSpan;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.GoldUnit;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.Query;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.SourceDocument;
import com.prizm.search.evaluation.searchv3.structural.SearchV3DenseAblationDataset.Split;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.QueryGold;
import com.prizm.search.evaluation.searchv3.structural.SearchV3OracleCeilingEvaluator.CeilingState;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class SearchV3SemanticOracleGoldJoinerTest {

    private static final String REPLAY_SHA256 = "a".repeat(64);
    private final SearchV3DenseAblationDataset dataset = new SearchV3DenseAblationDataset();

    @Test
    void joinsHistoricalAspectGoldAndCoversMultiSpanUnitAcrossChildrenInOnePassage() {
        HistoricalFixture fixture = historicalFixture(false);
        SearchV3SemanticOracleGoldJoiner joiner = joiner(fixture.replay());

        GoldJoined<List<QueryGold>> joined = join(fixture.input(),
                verified -> joiner.loadHistoricalGold(verified, fixture.suite()));

        assertThat(joined.gold()).hasSize(fixture.suite().queryInventory().size());
        QueryGold multiSpan = byId(joined.gold()).get(fixture.multiSpanQueryId());
        assertThat(multiSpan.aspectExpression()).isNotNull();
        assertThat(multiSpan.aspects()).isNotEmpty();
        assertThat(multiSpan.coveredEvidenceUnitIdsByCandidateId()
                .get(fixture.multiSpanCandidateId()))
                .contains(fixture.multiSpanUnitId());
        assertThat(multiSpan.goldDirectPositive()).isTrue();
        assertThat(new SearchV3OracleCeilingEvaluator().evaluate(joined).queries()).hasSize(21);
    }

    @Test
    void keepsExpectedDirectGoldOffCandidateWhenOneConstituentSpanIsMissing() {
        HistoricalFixture fixture = historicalFixture(true);
        SearchV3SemanticOracleGoldJoiner joiner = joiner(fixture.replay());

        List<QueryGold> gold = join(fixture.input(),
                verified -> joiner.loadHistoricalGold(verified, fixture.suite())).gold();
        QueryGold multiSpan = byId(gold).get(fixture.multiSpanQueryId());

        assertThat(multiSpan.goldDirectPositive()).isTrue();
        assertThat(multiSpan.coveredEvidenceUnitIdsByCandidateId()
                .getOrDefault(fixture.multiSpanCandidateId(), List.of()))
                .doesNotContain(fixture.multiSpanUnitId());
        assertThat(multiSpan.aspects()).flatExtracting(value -> value.expectedEvidence())
                .anySatisfy(value -> assertThat(value.evidenceUnitId()).isEqualTo(fixture.multiSpanUnitId()));
    }

    @Test
    void rejectsCandidateIdentityDriftBeforeHistoricalGoldLoad() {
        HistoricalFixture fixture = historicalFixture(false);
        QueryProjection first = fixture.input().queries().get(0);
        CandidateProjection candidate = first.rankedCandidates().get(0);
        CandidateProjection changed = new CandidateProjection(
                candidate.rank(), candidate.candidateId() + "-DRIFT", candidate.cosineScore(),
                candidate.userBundleId(), candidate.documentId(), candidate.versionId(),
                candidate.parentAnnotationCandidateId(), candidate.sourceText(), candidate.retrievalText(),
                candidate.sourceTextSha256(), candidate.retrievalTextSha256(), candidate.evidenceChildren());
        List<QueryProjection> queries = new ArrayList<>(fixture.input().queries());
        queries.set(0, new QueryProjection(
                first.queryId(), first.userBundleId(), first.split(), first.track(), List.of(changed)));
        FreezeInput drifted = new FreezeInput(
                fixture.input().schemaVersion(), fixture.input().suite(), fixture.input().datasetVersion(),
                fixture.input().sourceArtifactSha256(), fixture.input().track(), queries);
        SearchV3SemanticOracleGoldJoiner joiner = joiner(fixture.replay());

        assertThatThrownBy(() -> join(drifted,
                verified -> joiner.loadHistoricalGold(verified, fixture.suite())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("differs from frozen B3 replay");
    }

    @Test
    void joinsAndEvaluatesAllThreeApprovedHistoricalSuites() {
        for (SearchV3B3CandidateReplay.Suite suite : List.of(
                SearchV3B3CandidateReplay.Suite.ORIGINAL_SEED,
                SearchV3B3CandidateReplay.Suite.LONG_FORM_EXPANSION,
                SearchV3B3CandidateReplay.Suite.INDEPENDENT_ROBUSTNESS)) {
            HistoricalFixture fixture = historicalFixture(suite, false, false);
            GoldJoined<List<QueryGold>> joined = join(
                    fixture.input(),
                    verified -> joiner(fixture.replay()).loadHistoricalGold(verified, suite));

            assertThat(joined.gold()).hasSize(suite.queryInventory().size());
            assertThat(new SearchV3OracleCeilingEvaluator().evaluate(joined).queries())
                    .hasSize(suite.queryInventory().size());
        }
    }

    @Test
    void typedFreezeIsRejectedBeforeReplayOrGoldCanOpen() {
        HistoricalFixture fixture = historicalFixture(false);
        FreezeInput typed = new FreezeInput(
                fixture.input().schemaVersion(), fixture.input().suite(), fixture.input().datasetVersion(),
                fixture.input().sourceArtifactSha256(), EvaluationTrack.TYPED,
                fixture.input().queries().stream()
                        .map(query -> new QueryProjection(
                                query.queryId(), query.userBundleId(), query.split(), EvaluationTrack.TYPED,
                                query.rankedCandidates()))
                        .toList());
        AtomicInteger replayCalls = new AtomicInteger();
        SearchV3SemanticOracleGoldJoiner joiner = new SearchV3SemanticOracleGoldJoiner(
                dataset,
                new SearchV3SemanticOracleDataset(),
                ignored -> {
                    replayCalls.incrementAndGet();
                    return fixture.replay();
                });

        assertThatThrownBy(() -> join(typed,
                verified -> joiner.loadHistoricalGold(verified, fixture.suite())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rejects typed");
        assertThat(replayCalls).hasValue(0);
    }

    @Test
    void mapsFrozenStressAspectsExactlyAndKeepsPartialCeilingPartial() {
        SearchV3SemanticOracleDataset semantic = new SearchV3SemanticOracleDataset();
        FreezeInput input = stressFullDocumentFreeze(semantic);
        SearchV3SemanticOracleGoldJoiner joiner = new SearchV3SemanticOracleGoldJoiner(
                dataset, semantic, ignored -> {
                    throw new AssertionError("stress Gold must not open a historical replay");
                });

        GoldJoined<List<QueryGold>> joined = join(input, joiner::loadStressGold);
        List<QueryGold> partialGold = joined.gold().stream()
                .filter(value -> value.answerability()
                        == SearchV3OracleCeilingEvaluator.GoldAnswerability.PARTIALLY_SUPPORTED)
                .toList();
        assertThat(joined.gold()).hasSize(24);
        assertThat(partialGold).hasSize(8).allSatisfy(value -> {
            assertThat(value.aspectExpression().operator()).isEqualTo("ALL");
            assertThat(value.aspects()).hasSize(2);
            assertThat(value.goldDirectPositive()).isTrue();
            assertThat(value.aspects()).flatExtracting(aspect -> aspect.expectedEvidence())
                    .extracting(expected -> expected.evidenceGroupId())
                    .doesNotHaveDuplicates();
        });

        SearchV3OracleCeilingEvaluator.OracleRun run = new SearchV3OracleCeilingEvaluator().evaluate(joined);
        assertThat(run.queries().stream()
                .filter(value -> value.expectedState() == CeilingState.PARTIAL)
                .toList())
                .hasSize(8)
                .allSatisfy(value -> assertThat(value.ceilingState()).isEqualTo(CeilingState.PARTIAL));
    }

    private SearchV3SemanticOracleGoldJoiner joiner(SearchV3B3CandidateReplay.Replay replay) {
        return new SearchV3SemanticOracleGoldJoiner(
                dataset, new SearchV3SemanticOracleDataset(), ignored -> replay);
    }

    private GoldJoined<List<QueryGold>> join(
            FreezeInput input,
            Function<VerifiedCandidates, List<QueryGold>> loader) {
        PhaseGuard guard = new PhaseGuard();
        guard.freezeCandidates(input);
        VerifiedCandidates verified = guard.verifyFreeze();
        return guard.joinGold(() -> loader.apply(verified));
    }

    private HistoricalFixture historicalFixture(boolean omitLastMultiSpan) {
        return historicalFixture(
                SearchV3B3CandidateReplay.Suite.ORIGINAL_SEED,
                omitLastMultiSpan,
                true);
    }

    private HistoricalFixture historicalFixture(
            SearchV3B3CandidateReplay.Suite suite,
            boolean omitLastMultiSpan,
            boolean requireMultiSpan) {
        Map<String, QueryGoldSource> sourceByQuery = new LinkedHashMap<>();
        MultiSpanTarget target = null;
        for (Split split : Split.values()) {
            DatasetSlice slice = switch (suite) {
                case ORIGINAL_SEED -> dataset.load(split);
                case LONG_FORM_EXPANSION -> dataset.loadLongForm(split);
                case INDEPENDENT_ROBUSTNESS -> dataset.loadRobustness(split);
                case HISTORICAL_LONG_FORM -> throw new IllegalArgumentException(
                        "duplicate historical long-form suite is excluded");
            };
            for (Query query : slice.queries()) {
                ExpectedEvidence first = query.allExpectedEvidence().get(0);
                GoldUnit unit = slice.units().get(first.evidenceUnitId());
                QueryGoldSource source = new QueryGoldSource(slice, query, unit);
                sourceByQuery.put(query.queryId(), source);
                if (target == null
                        && "DIRECT_SUPPORT".equals(first.supportRelation())
                        && unit.sourceSpans().size() > 1) {
                    target = new MultiSpanTarget(query.queryId(), unit.evidenceUnitId());
                }
            }
        }
        if (requireMultiSpan && target == null) {
            throw new IllegalStateException("original seed no longer has a multi-span DIRECT unit");
        }

        List<QueryProjection> queries = new ArrayList<>();
        List<SearchV3B3CandidateReplay.ReplayCandidate> replayCandidates = new ArrayList<>();
        String targetCandidateId = null;
        for (SearchV3B3CandidateReplay.QueryIdentity identity : suite.queryInventory()) {
            QueryGoldSource source = sourceByQuery.get(identity.queryId());
            GoldUnit unit = source.unit();
            SourceDocument document = source.slice().activeDocumentsByVersion().get(unit.versionId());
            if (document == null) {
                document = source.slice().bundles().stream()
                        .filter(bundle -> bundle.userBundleId().equals(identity.owner()))
                        .flatMap(bundle -> bundle.activeDocuments().stream())
                        .findFirst()
                        .orElseThrow();
            }
            String candidateId = identity.queryId() + "-RP-0001";
            List<EvidenceChildProjection> children;
            if (target != null && identity.queryId().equals(target.queryId())) {
                List<GoldSpan> spans = unit.sourceSpans().stream()
                        .sorted(Comparator.comparingInt(GoldSpan::codePointStart))
                        .toList();
                if (omitLastMultiSpan) {
                    spans = spans.subList(0, spans.size() - 1);
                }
                children = childProjections(candidateId, spans);
                targetCandidateId = candidateId;
            }
            else if (document.versionId().equals(unit.versionId())) {
                children = childProjections(candidateId, unit.sourceSpans());
            }
            else {
                String text = document.structuralDocument().sourceText();
                children = List.of(new EvidenceChildProjection(
                        candidateId + "-C01", document.documentId(), document.versionId(),
                        document.structuralDocument().page(), 0,
                        text.codePointCount(0, text.length()), text,
                        SearchV3CandidateFreeze.sha256(text)));
            }
            String sourceText = children.stream().map(EvidenceChildProjection::sourceText)
                    .collect(Collectors.joining("\n"));
            String sourceHash = SearchV3CandidateFreeze.sha256(sourceText);
            CandidateProjection candidate = new CandidateProjection(
                    1, candidateId, 0.5d, identity.owner(),
                    children.get(0).documentId(), children.get(0).versionId(),
                    identity.owner() + "-PC-TEST", sourceText, sourceText,
                    sourceHash, sourceHash, children);
            queries.add(new QueryProjection(
                    identity.queryId(), identity.owner(), identity.split(), EvaluationTrack.SEMANTIC,
                    List.of(candidate)));
            replayCandidates.add(new SearchV3B3CandidateReplay.ReplayCandidate(
                    suite.rootNode(), suite.datasetVersion(), identity.split(), identity.queryId(),
                    identity.owner(), identity.profession(), identity.language(), 1, candidateId, 0.5d,
                    candidate.documentId(), candidate.versionId(), candidate.parentAnnotationCandidateId(),
                    children.stream().map(EvidenceChildProjection::evidenceChildId).toList(),
                    sourceHash, sourceHash));
        }
        SearchV3B3CandidateReplay.Replay replay = new SearchV3B3CandidateReplay.Replay(
                replayCandidates, "test-replay".getBytes(StandardCharsets.UTF_8), REPLAY_SHA256);
        FreezeInput input = new FreezeInput(
                SearchV3CandidateFreeze.SCHEMA_VERSION,
                suite.rootNode(), suite.datasetVersion(), REPLAY_SHA256,
                EvaluationTrack.SEMANTIC, queries);
        return new HistoricalFixture(
                suite,
                input,
                replay,
                target == null ? null : target.queryId(),
                target == null ? null : target.evidenceUnitId(),
                targetCandidateId);
    }

    private FreezeInput stressFullDocumentFreeze(SearchV3SemanticOracleDataset semantic) {
        List<QueryProjection> queries = new ArrayList<>();
        for (Split split : Split.values()) {
            SearchV3SemanticOracleDataset.RuntimeSlice runtime = semantic.loadStressRuntime(split);
            for (SearchV3SemanticOracleDataset.RuntimeQuestion question : runtime.questions()) {
                SourceDocument document = runtime.bundles().stream()
                        .filter(bundle -> bundle.userBundleId().equals(question.userBundleId()))
                        .flatMap(bundle -> bundle.activeDocuments().stream())
                        .findFirst()
                        .orElseThrow();
                String text = document.structuralDocument().sourceText();
                String hash = SearchV3CandidateFreeze.sha256(text);
                String candidateId = question.queryId() + "-RP-FULL";
                EvidenceChildProjection child = new EvidenceChildProjection(
                        candidateId + "-C01",
                        document.documentId(),
                        document.versionId(),
                        document.structuralDocument().page(),
                        0,
                        text.codePointCount(0, text.length()),
                        text,
                        hash);
                CandidateProjection candidate = new CandidateProjection(
                        1,
                        candidateId,
                        0.5d,
                        question.userBundleId(),
                        document.documentId(),
                        document.versionId(),
                        question.userBundleId() + "-PC-TEST",
                        text,
                        text,
                        hash,
                        hash,
                        List.of(child));
                queries.add(new QueryProjection(
                        question.queryId(), question.userBundleId(), split.manifestName(),
                        EvaluationTrack.SEMANTIC, List.of(candidate)));
            }
        }
        return new FreezeInput(
                SearchV3CandidateFreeze.SCHEMA_VERSION,
                SearchV3SemanticOracleDataset.STRESS_SUITE,
                SearchV3SemanticOracleDataset.STRESS_VERSION,
                SearchV3SemanticOracleDataset.STRESS_RUNTIME_SHA256,
                EvaluationTrack.SEMANTIC,
                queries);
    }

    private List<EvidenceChildProjection> childProjections(String candidateId, List<GoldSpan> spans) {
        List<GoldSpan> ordered = spans.stream().sorted(Comparator.comparingInt(GoldSpan::codePointStart)).toList();
        List<EvidenceChildProjection> children = new ArrayList<>();
        for (int index = 0; index < ordered.size(); index++) {
            GoldSpan span = ordered.get(index);
            children.add(new EvidenceChildProjection(
                    candidateId + "-C%02d".formatted(index + 1),
                    span.documentId(), span.versionId(), span.page(),
                    span.codePointStart(), span.codePointEnd(), span.text(), span.textSha256()));
        }
        return List.copyOf(children);
    }

    private Map<String, QueryGold> byId(List<QueryGold> gold) {
        return gold.stream().collect(Collectors.toMap(
                QueryGold::queryId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
    }

    private record QueryGoldSource(DatasetSlice slice, Query query, GoldUnit unit) {
    }

    private record MultiSpanTarget(String queryId, String evidenceUnitId) {
    }

    private record HistoricalFixture(
            SearchV3B3CandidateReplay.Suite suite,
            FreezeInput input,
            SearchV3B3CandidateReplay.Replay replay,
            String multiSpanQueryId,
            String multiSpanUnitId,
            String multiSpanCandidateId) {
    }
}
