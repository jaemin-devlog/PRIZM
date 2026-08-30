package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.PreparedDataset;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.PreparedInput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.PreparedPair;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.PreparedQuestion;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.ScoreOutput;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.ScorePair;
import com.prizm.search.evaluation.searchv3.structural.SearchV3RerankerPairArtifacts.ScoreQuestion;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SearchV3RerankerPairArtifactsTest {

    private static final String INPUT_FILE_SHA = "a".repeat(64);
    private final SearchV3RerankerPairArtifacts artifacts = new SearchV3RerankerPairArtifacts();

    @Test
    void freezesDenseTop20AndExcludesGoldFromInferencePairs() throws Exception {
        PreparedInput prepared = prepared(21);

        artifacts.validatePrepared(prepared);
        assertThat(prepared.datasets().get(0).questions().get(0).pairs())
                .hasSize(20)
                .extracting(PreparedPair::denseRank)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList());

        JsonNode root = new ObjectMapper().valueToTree(prepared);
        List<String> forbidden = new ArrayList<>();
        collectForbiddenFields(root, forbidden);
        assertThat(forbidden).isEmpty();
        assertThat(root.at("/datasets/0/questions/0/pairs/0/query").asText()).isEqualTo("질문 원문");
        assertThat(root.at("/datasets/0/questions/0/pairs/0/sourceText").asText()).isEqualTo("근거 1");
    }

    @Test
    void acceptsDeterministicTieOrderAndRejectsReversedTieOrder() {
        PreparedInput prepared = prepared(2);
        ScoreOutput valid = scores(prepared, orderedPairs(prepared));
        artifacts.validateScores(valid, prepared, INPUT_FILE_SHA);

        List<ScorePair> reversed = new ArrayList<>(valid.questions().get(0).pairs());
        Collections.reverse(reversed);
        reversed = List.of(
                withRerankRank(reversed.get(0), 1),
                withRerankRank(reversed.get(1), 2));
        ScoreOutput invalid = withQuestions(valid, List.of(withPairs(valid.questions().get(0), reversed)));
        assertThatThrownBy(() -> artifacts.validateScores(invalid, prepared, INPUT_FILE_SHA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity/order");
    }

    @Test
    void rejectsMissingDuplicateAndUnknownScorePairs() {
        PreparedInput prepared = prepared(2);
        ScoreOutput valid = scores(prepared, orderedPairs(prepared));
        List<ScorePair> pairs = valid.questions().get(0).pairs();

        ScoreOutput missing = withQuestions(valid, List.of(withPairs(
                valid.questions().get(0), List.of(withRerankRank(pairs.get(0), 1)))));
        assertThatThrownBy(() -> artifacts.validateScores(missing, prepared, INPUT_FILE_SHA))
                .isInstanceOf(IllegalArgumentException.class);

        ScoreOutput duplicate = withQuestions(valid, List.of(withPairs(
                valid.questions().get(0),
                List.of(withRerankRank(pairs.get(0), 1), withRerankRank(pairs.get(0), 2)))));
        assertThatThrownBy(() -> artifacts.validateScores(duplicate, prepared, INPUT_FILE_SHA))
                .isInstanceOf(IllegalArgumentException.class);

        ScorePair unknown = new ScorePair(
                "unknown", "candidate-unknown", 2, 2,
                pairs.get(1).querySha256(), pairs.get(1).sourceSha256(), pairs.get(1).score());
        ScoreOutput added = withQuestions(valid, List.of(withPairs(
                valid.questions().get(0), List.of(pairs.get(0), unknown))));
        assertThatThrownBy(() -> artifacts.validateScores(added, prepared, INPUT_FILE_SHA))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsSourceOrCandidateIdentityMutation() {
        PreparedInput prepared = prepared(2);
        ScoreOutput valid = scores(prepared, orderedPairs(prepared));
        ScorePair original = valid.questions().get(0).pairs().get(0);
        ScorePair mutated = new ScorePair(
                original.pairId(), "changed-candidate", original.denseRank(), original.rerankerRank(),
                original.querySha256(), "b".repeat(64), original.score());
        ScoreOutput output = withQuestions(valid, List.of(withPairs(
                valid.questions().get(0), List.of(mutated, valid.questions().get(0).pairs().get(1)))));

        assertThatThrownBy(() -> artifacts.validateScores(output, prepared, INPUT_FILE_SHA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("identity/order");
    }

    private PreparedInput prepared(int fullCandidateCount) {
        int pairCount = Math.min(SearchV3RerankerPairArtifacts.TOP_K, fullCandidateCount);
        String query = "질문 원문";
        String querySha = SearchV3RerankerPairArtifacts.sha256(query);
        List<PreparedPair> pairs = new ArrayList<>();
        for (int rank = 1; rank <= pairCount; rank++) {
            String source = "근거 " + rank;
            pairs.add(new PreparedPair(
                    "pair-" + rank,
                    rank,
                    "candidate-" + rank,
                    querySha,
                    SearchV3RerankerPairArtifacts.sha256(source),
                    SearchV3RerankerPairArtifacts.sha256("provenance-" + rank),
                    "document-1",
                    "version-1",
                    query,
                    source));
        }
        PreparedQuestion question = new PreparedQuestion(
                "query-1", "DEV", querySha, query, fullCandidateCount, pairCount, List.copyOf(pairs));
        List<PreparedDataset> datasets = List.of(new PreparedDataset(
                "ORIGINAL", "dataset-1", Map.of("DEV", "c".repeat(64)), List.of(question)));
        return new PreparedInput(
                SearchV3RerankerPairArtifacts.SCHEMA_VERSION,
                SearchV3RerankerPairArtifacts.PROFILE,
                SearchV3RerankerPairArtifacts.TOP_K,
                SearchV3RerankerPairArtifacts.MAX_LENGTH,
                SearchV3RerankerPairArtifacts.BATCH_SIZE,
                SearchV3RerankerPairArtifacts.CPU_THREADS,
                SearchV3RerankerPairArtifacts.MODEL,
                SearchV3RerankerPairArtifacts.MODEL_REVISION,
                SearchV3RerankerPairArtifacts.CODE_REPOSITORY,
                SearchV3RerankerPairArtifacts.CODE_REVISION,
                SearchV3RerankerPairArtifacts.LICENSE,
                SearchV3RerankerPairArtifacts.TRANSFORMERS_VERSION,
                "ORIGINAL_QUERY_AND_B3_SOURCE_TEXT_NO_INSTRUCTION",
                "GOLD_NOT_PRESENT",
                artifacts.inputDigest(datasets),
                datasets);
    }

    private List<ScorePair> orderedPairs(PreparedInput prepared) {
        return prepared.datasets().get(0).questions().get(0).pairs().stream()
                .map(pair -> new ScorePair(
                        pair.pairId(), pair.candidateId(), pair.denseRank(), pair.denseRank(),
                        pair.querySha256(), pair.sourceSha256(), 0.5d))
                .toList();
    }

    private ScoreOutput scores(PreparedInput prepared, List<ScorePair> pairs) {
        PreparedQuestion question = prepared.datasets().get(0).questions().get(0);
        return new ScoreOutput(
                1, "2026-08-31T00:00:00Z", prepared.inputDigest(), INPUT_FILE_SHA,
                SearchV3RerankerPairArtifacts.MODEL, SearchV3RerankerPairArtifacts.MODEL_REVISION,
                SearchV3RerankerPairArtifacts.CODE_REPOSITORY, SearchV3RerankerPairArtifacts.CODE_REVISION,
                SearchV3RerankerPairArtifacts.LICENSE, SearchV3RerankerPairArtifacts.TRANSFORMERS_VERSION,
                SearchV3RerankerPairArtifacts.TORCH_VERSION,
                SearchV3RerankerPairArtifacts.PSUTIL_VERSION,
                SearchV3RerankerPairArtifacts.PYTHON_VERSION,
                "cpu", "float32", 20, 512, 8, 8,
                SearchV3RerankerPairArtifacts.MODEL_PARAMETER_COUNT,
                SearchV3RerankerPairArtifacts.MODEL_WEIGHT_BYTES,
                SearchV3RerankerPairArtifacts.MODEL_WEIGHT_BYTES + 1L,
                SearchV3RerankerPairArtifacts.MODEL_WEIGHT_SHA256,
                SearchV3RerankerPairArtifacts.CONFIG_SHA256,
                SearchV3RerankerPairArtifacts.REMOTE_CONFIGURATION_SHA256,
                SearchV3RerankerPairArtifacts.REMOTE_MODELING_SHA256,
                10.0d, 1.0d, 100L, 200L, 250L, false, 0L, 0L,
                List.of(new ScoreQuestion(
                        "dataset-1", "DEV", question.questionId(), question.querySha256(),
                        pairs.size(), 2.0d, pairs)));
    }

    private ScoreOutput withQuestions(ScoreOutput value, List<ScoreQuestion> questions) {
        return new ScoreOutput(
                value.schemaVersion(), value.generatedAt(), value.inputDigest(), value.inputSha256(),
                value.model(), value.modelRevision(), value.codeRepository(), value.codeRevision(), value.license(),
                value.transformersVersion(), value.torchVersion(), value.psutilVersion(), value.pythonVersion(),
                value.device(), value.dtype(),
                value.topK(), value.maxLength(), value.batchSize(), value.cpuThreads(), value.modelParameterCount(),
                value.modelWeightBytes(), value.modelCacheBytes(), value.modelWeightSha256(), value.configSha256(),
                value.remoteConfigurationSha256(), value.remoteModelingSha256(), value.modelLoadMillis(),
                value.warmupMillis(), value.processRssBeforeLoadBytes(), value.processRssAfterLoadBytes(),
                value.processRssPeakBytes(), value.gpuUsed(), value.gpuPeakAllocatedBytes(),
                value.gpuPeakReservedBytes(), questions);
    }

    private ScoreQuestion withPairs(ScoreQuestion value, List<ScorePair> pairs) {
        return new ScoreQuestion(
                value.datasetVersion(), value.split(), value.questionId(), value.querySha256(),
                pairs.size(), value.rerankMillis(), pairs);
    }

    private ScorePair withRerankRank(ScorePair value, int rerankRank) {
        return new ScorePair(
                value.pairId(), value.candidateId(), value.denseRank(), rerankRank,
                value.querySha256(), value.sourceSha256(), value.score());
    }

    private void collectForbiddenFields(JsonNode node, List<String> result) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                String normalized = entry.getKey().replace("_", "").toLowerCase();
                if (!"goldpolicy".equals(normalized)
                        && (normalized.contains("gold")
                                || normalized.contains("expected")
                                || normalized.contains("answerability")
                                || normalized.contains("category")
                                || normalized.contains("covered")
                                || normalized.contains("supportrelation"))) {
                    result.add(entry.getKey());
                }
                collectForbiddenFields(entry.getValue(), result);
            });
        } else if (node.isArray()) {
            node.forEach(value -> collectForbiddenFields(value, result));
        }
    }
}
