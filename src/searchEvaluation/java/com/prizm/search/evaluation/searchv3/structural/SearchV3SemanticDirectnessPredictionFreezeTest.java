package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.CandidateProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvaluationTrack;
import com.prizm.search.evaluation.searchv3.structural.SearchV3CandidateFreeze.EvidenceChildProjection;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.FrozenInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.FrozenOutput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.GoldJoined;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.InferenceBatch;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.Input;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.InputVerification;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.Output;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.OutputVerification;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.Phase;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.PhaseGuard;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.Prediction;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.QueryInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.Relation;
import com.prizm.search.evaluation.searchv3.structural.SearchV3SemanticDirectnessPredictionFreeze.RunContract;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SearchV3SemanticDirectnessPredictionFreezeTest {

    @Test
    void freezesExactSemanticInventoryAndExposesOnlyQueryAndSourceTextForInference() {
        assertThat(SearchV3SemanticDirectnessPredictionFreeze.SCHEMA_VERSION).isEqualTo(2);
        Input input = validInput();
        List<QueryInput> reversedQueries = new ArrayList<>(input.queries());
        Collections.reverse(reversedQueries);
        List<SearchV3SemanticDirectnessPredictionFreeze.SourceSuite> reversedSources =
                new ArrayList<>(input.sourceSuites());
        Collections.reverse(reversedSources);

        FrozenInput first = SearchV3SemanticDirectnessPredictionFreeze.freezeInput(input);
        FrozenInput second = SearchV3SemanticDirectnessPredictionFreeze.freezeInput(new Input(
                input.schemaVersion(), input.contract(), reversedSources, reversedQueries));
        InputVerification verification = SearchV3SemanticDirectnessPredictionFreeze.verifyInput(first);

        assertThat(first.canonicalSha256()).isEqualTo(second.canonicalSha256());
        assertThat(first.canonicalByteLength()).isEqualTo(second.canonicalByteLength());
        assertThat(verification.semanticQueryCount()).isEqualTo(79);
        assertThat(verification.candidateCount()).isEqualTo(670);
        assertThat(verification.inferencePairCount()).isEqualTo(578);
        assertThat(verification.typedQueryCount()).isZero();

        PhaseGuard guard = new PhaseGuard();
        guard.freezeInput(input);
        guard.verifyInput();
        InferenceBatch batch = guard.openInference();
        assertThat(batch.pairs()).hasSize(578);
        assertThat(batch.pairs()).allSatisfy(pair -> {
            assertThat(pair.sourceRank()).isBetween(1, 10);
            assertThat(pair.payload().originalQuery()).isNotBlank();
            assertThat(pair.payload().sourceText()).isNotBlank();
        });
        assertThat(batch.pairs().get(0).payload().getClass().getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("originalQuery", "sourceText");
    }

    @Test
    void rejectsTypedQueriesWrongInventoryAndFrozenContractOrQueryMutation() {
        Input valid = validInput();
        QueryInput first = valid.queries().get(0);
        List<QueryInput> typed = new ArrayList<>(valid.queries());
        typed.set(0, new QueryInput(
                first.suite(), first.datasetVersion(), first.split(), first.queryId(),
                first.userBundleId(), first.language(), EvaluationTrack.TYPED,
                first.originalQuery(), first.originalQuerySha256(), first.rankedCandidates()));
        assertThatThrownBy(() -> SearchV3SemanticDirectnessPredictionFreeze.freezeInput(new Input(
                valid.schemaVersion(), valid.contract(), valid.sourceSuites(), typed)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("typed query");

        assertThatThrownBy(() -> SearchV3SemanticDirectnessPredictionFreeze.freezeInput(new Input(
                valid.schemaVersion(), valid.contract(), valid.sourceSuites(),
                valid.queries().subList(0, valid.queries().size() - 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("79 semantic queries");

        List<QueryInput> changedText = new ArrayList<>(valid.queries());
        changedText.set(0, new QueryInput(
                first.suite(), first.datasetVersion(), first.split(), first.queryId(),
                first.userBundleId(), first.language(), first.track(),
                first.originalQuery() + " changed", first.originalQuerySha256(), first.rankedCandidates()));
        assertThatThrownBy(() -> SearchV3SemanticDirectnessPredictionFreeze.freezeInput(new Input(
                valid.schemaVersion(), valid.contract(), valid.sourceSuites(), changedText)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query hash mismatch");

        FrozenInput frozen = SearchV3SemanticDirectnessPredictionFreeze.freezeInput(valid);
        RunContract contract = valid.contract();
        String changedPolicy = contract.policy() + " changed";
        RunContract changedContract = new RunContract(
                contract.modelId(), contract.modelRevision(), contract.modelLicense(),
                contract.modelSizeBytes(), contract.modelArtifactSha256(),
                contract.instruction(), contract.instructionSha256(),
                contract.outputSchema(), contract.outputSchemaSha256(),
                contract.config(), contract.configSha256(),
                changedPolicy, SearchV3SemanticDirectnessPredictionFreeze.sha256(changedPolicy),
                contract.inferenceTopK());
        FrozenInput tampered = new FrozenInput(
                new Input(valid.schemaVersion(), changedContract, valid.sourceSuites(), valid.queries()),
                frozen.candidateFreeze(), frozen.contractSha256(), frozen.canonicalSha256(),
                frozen.canonicalByteLength());
        assertThatThrownBy(() -> SearchV3SemanticDirectnessPredictionFreeze.verifyInput(tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("input freeze");
    }

    @Test
    void outputRequiresEveryTop10PairAndFreezesRelationOnly() {
        FrozenInput input = SearchV3SemanticDirectnessPredictionFreeze.freezeInput(validInput());
        InputVerification inputVerification = SearchV3SemanticDirectnessPredictionFreeze.verifyInput(input);
        PhaseGuard guard = new PhaseGuard();
        guard.freezeInput(input.input());
        guard.verifyInput();
        InferenceBatch batch = guard.openInference();
        Output valid = output(batch);

        FrozenOutput frozen = SearchV3SemanticDirectnessPredictionFreeze.freezeOutput(input, valid);
        OutputVerification verified = SearchV3SemanticDirectnessPredictionFreeze.verifyOutput(input, frozen);
        assertThat(verified.predictionCount()).isEqualTo(578);
        assertThat(verified.inputSha256()).isEqualTo(inputVerification.canonicalSha256());
        assertThat(verified.contractSha256()).isEqualTo(inputVerification.contractSha256());
        assertThat(Prediction.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("queryId", "candidateId", "sourceRank", "relation");

        List<Prediction> missing = new ArrayList<>(valid.predictions());
        missing.remove(missing.size() - 1);
        assertThatThrownBy(() -> SearchV3SemanticDirectnessPredictionFreeze.freezeOutput(input, new Output(
                valid.schemaVersion(), valid.inputSha256(), valid.contractSha256(), missing)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("578 prediction rows");

        List<Prediction> nonTop10 = new ArrayList<>(valid.predictions());
        Prediction last = nonTop10.get(nonTop10.size() - 1);
        nonTop10.set(nonTop10.size() - 1, new Prediction(
                last.queryId(), last.candidateId(), 11,
                last.relation()));
        assertThatThrownBy(() -> SearchV3SemanticDirectnessPredictionFreeze.freezeOutput(input, new Output(
                valid.schemaVersion(), valid.inputSha256(), valid.contractSha256(), nonTop10)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-Top10");
    }

    @Test
    void GoldSupplierCannotRunUntilPredictionOutputIsVerified() {
        PhaseGuard guard = new PhaseGuard();
        AtomicInteger GoldAccesses = new AtomicInteger();
        assertGoldBlocked(guard, GoldAccesses);

        guard.freezeInput(validInput());
        assertThat(guard.phase()).isEqualTo(Phase.INPUT_FROZEN);
        assertGoldBlocked(guard, GoldAccesses);

        guard.verifyInput();
        assertThat(guard.phase()).isEqualTo(Phase.INPUT_VERIFIED);
        assertGoldBlocked(guard, GoldAccesses);

        InferenceBatch batch = guard.openInference();
        assertThat(guard.phase()).isEqualTo(Phase.INFERENCE_OPEN);
        assertGoldBlocked(guard, GoldAccesses);

        guard.freezeOutput(output(batch));
        assertThat(guard.phase()).isEqualTo(Phase.OUTPUT_FROZEN);
        assertGoldBlocked(guard, GoldAccesses);

        guard.verifyOutput();
        assertThat(guard.phase()).isEqualTo(Phase.OUTPUT_VERIFIED);
        GoldJoined<String> joined = guard.joinGold(verifiedCandidates -> {
            GoldAccesses.incrementAndGet();
            assertThat(verifiedCandidates).isNotNull();
            return "Gold loaded";
        });
        assertThat(joined.gold()).isEqualTo("Gold loaded");
        assertThat(GoldAccesses).hasValue(1);
        assertThat(guard.phase()).isEqualTo(Phase.GOLD_JOINED);
    }

    @Test
    void frozenPredictionMutationIsDetected() {
        FrozenInput input = SearchV3SemanticDirectnessPredictionFreeze.freezeInput(validInput());
        PhaseGuard guard = new PhaseGuard();
        guard.freezeInput(input.input());
        guard.verifyInput();
        InferenceBatch batch = guard.openInference();
        Output output = output(batch);
        FrozenOutput frozen = SearchV3SemanticDirectnessPredictionFreeze.freezeOutput(input, output);

        List<Prediction> changed = new ArrayList<>(output.predictions());
        Prediction first = changed.get(0);
        changed.set(0, new Prediction(
                first.queryId(), first.candidateId(), first.sourceRank(),
                Relation.RELATED_CONTEXT));
        FrozenOutput tampered = new FrozenOutput(
                new Output(output.schemaVersion(), output.inputSha256(), output.contractSha256(), changed),
                frozen.canonicalSha256(), frozen.canonicalByteLength());
        assertThatThrownBy(() -> SearchV3SemanticDirectnessPredictionFreeze.verifyOutput(input, tampered))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("output freeze");
    }

    private void assertGoldBlocked(PhaseGuard guard, AtomicInteger accesses) {
        assertThatThrownBy(() -> guard.joinGold(verifiedCandidates -> {
            accesses.incrementAndGet();
            return "forbidden";
        })).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OUTPUT_VERIFIED");
        assertThat(accesses).hasValue(0);
    }

    private Output output(InferenceBatch batch) {
        Relation[] relations = Relation.values();
        List<Prediction> predictions = new ArrayList<>();
        for (int index = 0; index < batch.pairs().size(); index++) {
            SearchV3SemanticDirectnessPredictionFreeze.InferencePair pair = batch.pairs().get(index);
            Relation relation = relations[index % relations.length];
            predictions.add(new Prediction(
                    pair.queryId(), pair.candidateId(), pair.sourceRank(),
                    relation));
        }
        return new Output(
                SearchV3SemanticDirectnessPredictionFreeze.SCHEMA_VERSION,
                batch.inputSha256(), batch.contractSha256(), predictions);
    }

    private Input validInput() {
        List<QueryInput> queries = new ArrayList<>();
        addSuite(queries, "originalSeed", "search-v3-fresh-seed-1.0.1", counts(14, 3));
        addSuite(queries, "longFormExpansion", "search-v3-fresh-devcal-1.1.0",
                concat(repeat(12, 14), repeat(4, 13), repeat(2, 6)));
        addSuite(queries, "independentRobustness",
                "search-v3-fresh-devcal-robustness-1.0.0",
                concat(repeat(16, 11), repeat(1, 10), repeat(4, 2), repeat(2, 1)));
        addSuite(queries, "PRZ030_SEMANTIC_SUPPORT_STRESS", "semantic-support-stress-1.0.1",
                concat(repeat(16, 11), repeat(8, 3)));
        return new Input(
                SearchV3SemanticDirectnessPredictionFreeze.SCHEMA_VERSION,
                contract(),
                SearchV3SemanticDirectnessPredictionFreeze.expectedSourceSuites(),
                queries);
    }

    private RunContract contract() {
        String instruction = "Classify only query-to-source directness.";
        String schema = "{relation:enum}";
        String config = "temperature=0;seed=31;format=json";
        String policy = "Top10 stable partition; preserve ranks 11-20";
        return new RunContract(
                "local-model",
                "immutable-revision",
                "Apache-2.0",
                1_000_000L,
                SearchV3SemanticDirectnessPredictionFreeze.sha256("model artifact"),
                instruction,
                SearchV3SemanticDirectnessPredictionFreeze.sha256(instruction),
                schema,
                SearchV3SemanticDirectnessPredictionFreeze.sha256(schema),
                config,
                SearchV3SemanticDirectnessPredictionFreeze.sha256(config),
                policy,
                SearchV3SemanticDirectnessPredictionFreeze.sha256(policy),
                10);
    }

    private void addSuite(
            List<QueryInput> result,
            String suite,
            String datasetVersion,
            int[] candidateCounts) {
        for (int queryIndex = 0; queryIndex < candidateCounts.length; queryIndex++) {
            String prefix = suite.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
            String queryId = prefix + "-Q" + String.format("%02d", queryIndex + 1);
            String owner = prefix + "-U" + String.format("%02d", queryIndex + 1);
            String queryText = "Original semantic query " + queryId;
            List<CandidateProjection> candidates = new ArrayList<>();
            for (int rank = 1; rank <= candidateCounts[queryIndex]; rank++) {
                candidates.add(candidate(queryId, owner, rank));
            }
            result.add(new QueryInput(
                    suite,
                    datasetVersion,
                    queryIndex < candidateCounts.length / 2 ? "DEV" : "CALIBRATION",
                    queryId,
                    owner,
                    queryIndex % 3 == 0 ? "KO" : queryIndex % 3 == 1 ? "EN" : "KO_EN_MIXED",
                    EvaluationTrack.SEMANTIC,
                    queryText,
                    SearchV3SemanticDirectnessPredictionFreeze.sha256(queryText),
                    candidates));
        }
    }

    private CandidateProjection candidate(String queryId, String owner, int rank) {
        String candidateId = queryId + "-RP-" + String.format("%02d", rank);
        String documentId = queryId + "-D";
        String versionId = documentId + "-V1";
        String sourceText = "Source evidence for " + candidateId;
        String childId = candidateId + "-C1";
        int length = sourceText.codePointCount(0, sourceText.length());
        EvidenceChildProjection child = new EvidenceChildProjection(
                childId,
                documentId,
                versionId,
                null,
                0,
                length,
                sourceText,
                SearchV3SemanticDirectnessPredictionFreeze.sha256(sourceText));
        return new CandidateProjection(
                rank,
                candidateId,
                1.0d - rank * 0.01d,
                owner,
                documentId,
                versionId,
                candidateId + "-PARENT",
                sourceText,
                sourceText,
                SearchV3SemanticDirectnessPredictionFreeze.sha256(sourceText),
                SearchV3SemanticDirectnessPredictionFreeze.sha256(sourceText),
                List.of(child));
    }

    private int[] counts(int count, int value) {
        return repeat(count, value);
    }

    private int[] repeat(int count, int value) {
        int[] result = new int[count];
        java.util.Arrays.fill(result, value);
        return result;
    }

    private int[] concat(int[]... values) {
        int size = java.util.Arrays.stream(values).mapToInt(value -> value.length).sum();
        int[] result = new int[size];
        int offset = 0;
        for (int[] value : values) {
            System.arraycopy(value, 0, result, offset, value.length);
            offset += value.length;
        }
        return result;
    }
}
