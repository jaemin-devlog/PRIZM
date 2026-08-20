package com.prizm.search.evaluation.trace;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.ingestion.entity.ChunkSourceType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class FreshGeneralizationV2DatasetTest {

    private static final Path ROOT = Path.of(
            "specs/PRZ-016-search-performance-v2/fresh-generalization-evaluation-v2/dataset");
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void requireLocallyRetainedFrozenFixture() {
        Assumptions.assumeTrue(
                Files.isRegularFile(ROOT.resolve("corpus-manifest.json"))
                        && Files.isRegularFile(ROOT.resolve("questions.json"))
                        && Files.isRegularFile(ROOT.resolve("ground-truth.json")),
                "Fresh Generalization V2 fixture is retained only for local evaluation.");
    }

    @Test
    void frozenInputsHaveFourOwnersEightActiveDocumentsAndBalancedQueryContracts() throws Exception {
        JsonNode corpus = mapper.readTree(ROOT.resolve("corpus-manifest.json").toFile());
        JsonNode questions = mapper.readTree(ROOT.resolve("questions.json").toFile());
        JsonNode groundTruth = mapper.readTree(ROOT.resolve("ground-truth.json").toFile());

        assertThat(corpus.path("users")).hasSize(4);
        int activeDocuments = 0;
        for (JsonNode user : corpus.path("users")) {
            assertThat(user.path("activeDocuments")).hasSize(2);
            assertThat(user.path("inactiveVersion").path("fixture").asText()).isNotBlank();
            activeDocuments += user.path("activeDocuments").size();
            for (JsonNode document : user.path("activeDocuments")) {
                assertThat(Files.isRegularFile(ROOT.resolve(document.path("fixture").asText()))).isTrue();
            }
            assertThat(Files.isRegularFile(ROOT.resolve(
                    user.path("inactiveVersion").path("fixture").asText()))).isTrue();
        }
        assertThat(activeDocuments).isEqualTo(8);

        assertThat(questions.path("questions")).hasSize(44);
        long positives = stream(questions.path("questions"))
                .filter(question -> "POSITIVE".equals(question.path("label").asText())).count();
        long negatives = stream(questions.path("questions"))
                .filter(question -> "NEGATIVE".equals(question.path("label").asText())).count();
        assertThat(positives).isEqualTo(24);
        assertThat(negatives).isEqualTo(20);

        Map<String, Long> negativeTypes = stream(questions.path("questions"))
                .filter(question -> "NEGATIVE".equals(question.path("label").asText()))
                .collect(java.util.stream.Collectors.groupingBy(
                        question -> question.path("type").asText(),
                        LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));
        assertThat(negativeTypes).containsOnlyKeys(
                "ABSENT", "CROSS_USER_ONLY", "INACTIVE_VERSION_ONLY", "NEGATED",
                "NOT_ADOPTED", "HISTORICAL_ONLY", "PROTOTYPE_ONLY", "WRONG_NUMBER",
                "WRONG_METRIC", "RELATED_BUT_NOT_SUPPORTED");
        assertThat(negativeTypes.values()).allMatch(count -> count == 2L);

        Set<String> questionIds = ids(questions.path("questions"));
        Set<String> groundTruthIds = ids(groundTruth.path("queries"));
        assertThat(groundTruthIds).isEqualTo(questionIds);
    }

    @Test
    void everyPositiveClauseAndNegativeOracleAnchorExistsInItsDeclaredFixture() throws Exception {
        JsonNode groundTruth = mapper.readTree(ROOT.resolve("ground-truth.json").toFile());
        for (JsonNode query : groundTruth.path("queries")) {
            if ("SUPPORTED".equals(query.path("expected").asText())) {
                assertThat(query.path("acceptableEvidenceSets").isEmpty())
                        .as(query.path("id").asText()).isFalse();
                for (JsonNode evidenceSet : query.path("acceptableEvidenceSets")) {
                    String content = Files.readString(ROOT.resolve(
                            evidenceSet.path("documentFixture").asText()));
                    for (JsonNode clause : evidenceSet.path("requiredClauses")) {
                        assertThat(clause.path("anchorAny").isEmpty()).isFalse();
                        boolean found = stream(clause.path("anchorAny"))
                                .anyMatch(anchor -> normalize(content).contains(normalize(anchor.asText())));
                        assertThat(found)
                                .as(query.path("id").asText() + "/" + clause.path("id").asText())
                                .isTrue();
                    }
                }
            } else {
                assertThat(query.path("acceptableEvidenceSets")).isEmpty();
                JsonNode negativeEvidence = query.path("negativeEvidence");
                assertThat(negativeEvidence.isMissingNode()).isFalse();
                if (negativeEvidence.hasNonNull("sourceFixture")) {
                    String content = Files.readString(ROOT.resolve(
                            negativeEvidence.path("sourceFixture").asText()));
                    for (JsonNode anchor : negativeEvidence.path("anchorAny")) {
                        assertThat(normalize(content)).contains(normalize(anchor.asText()));
                    }
                }
            }
        }
    }

    @Test
    void freshQuestionsDoNotDuplicateP0P5OrEitherP7QuestionSet() throws Exception {
        JsonNode fresh = mapper.readTree(ROOT.resolve("questions.json").toFile());
        Set<String> historical = new LinkedHashSet<>();
        for (String path : List.of(
                "specs/PRZ-016-search-performance-v2/p0-benchmark/evaluation-dataset.json",
                "specs/PRZ-016-search-performance-v2/p5-final-holdout/holdout-dataset.json",
                "specs/PRZ-016-search-performance-v2/p7-cross-document-generalization/dataset/questions.json",
                "specs/PRZ-016-search-performance-v2/p7-cross-document-generalization-v2/dataset/questions.json")) {
            JsonNode root = mapper.readTree(Path.of(path).toFile());
            stream(root.path("queries").isMissingNode() ? root.path("questions") : root.path("queries"))
                    .map(question -> normalize(question.path("query").asText()))
                    .forEach(historical::add);
        }
        List<String> duplicates = stream(fresh.path("questions"))
                .map(question -> normalize(question.path("query").asText()))
                .filter(historical::contains)
                .toList();
        assertThat(duplicates).isEmpty();
    }

    @Test
    void evaluatorSeparatesEligibilityFailureFromLocalizationFailure() throws Exception {
        JsonNode gt = mapper.readTree(ROOT.resolve("ground-truth.json").toFile()).path("queries").get(2);
        String correctContent = Files.readString(ROOT.resolve("documents/fresh-u01-resume-v1.txt"));
        SearchDecisionTrace.CandidateTrace candidate = candidate(correctContent);
        SearchDecisionTrace eligibilityFailure = trace(
                candidate,
                List.of(),
                List.of(),
                List.of(),
                List.of());

        GroundTruthV2Evaluator evaluator = new GroundTruthV2Evaluator();
        SearchDecisionTrace.GroundTruthOutcome eligibility = evaluator.evaluatePositive(
                eligibilityFailure, gt, Map.of(10L, "documents/fresh-u01-resume-v1.txt"));
        assertThat(eligibility.candidateRecallAt20()).isTrue();
        assertThat(eligibility.postSourceRetention()).isTrue();
        assertThat(eligibility.postEligibilityRetention()).isFalse();
        assertThat(eligibility.firstFailureStage())
                .isEqualTo(SearchDecisionTrace.FirstFailureStage.ELIGIBILITY);

        SearchDecisionTrace localizationFailure = trace(
                candidate,
                List.of(1L),
                List.of(1L),
                List.of(new SearchDecisionTrace.FinalResultTrace(1, 1L, 10L, 20L, 0.8, 0.2)),
                List.of(new SearchDecisionTrace.EvidenceTrace(
                        1, 1L, 2L, ChunkSourceType.TEXT_CHUNK, 2, "텍스트 구간 2",
                        "관련 없는 표시 근거", true)));
        SearchDecisionTrace.GroundTruthOutcome localization = evaluator.evaluatePositive(
                localizationFailure, gt, Map.of(10L, "documents/fresh-u01-resume-v1.txt"));
        assertThat(localization.selectedResultCorrect()).isTrue();
        assertThat(localization.displayedEvidenceCorrect()).isFalse();
        assertThat(localization.firstFailureStage())
                .isEqualTo(SearchDecisionTrace.FirstFailureStage.LOCALIZATION);
    }

    private static SearchDecisionTrace trace(
            SearchDecisionTrace.CandidateTrace candidate,
            List<Long> eligible,
            List<Long> queryRepresentatives,
            List<SearchDecisionTrace.FinalResultTrace> finalResults,
            List<SearchDecisionTrace.EvidenceTrace> evidence) {
        return new SearchDecisionTrace(
                1,
                7L,
                "동기화 P95를 9.4초에서 2.1초로 개선했는가?",
                finalResults.isEmpty() ? "NO_RELEVANT_RESULTS" : "EVIDENCE_FOUND",
                List.of(new SearchDecisionTrace.QueryVariantTrace(
                        "ORIGINAL",
                        SearchDecisionTrace.QueryVariantType.ORIGINAL,
                        "동기화 P95를 9.4초에서 2.1초로 개선했는가?",
                        List.of(), false,
                        List.of(1L), List.of(1L), List.of(1L), eligible,
                        queryRepresentatives, queryRepresentatives, finalResults.isEmpty()
                                ? List.of() : List.of(1L), finalResults.isEmpty() ? List.of() : List.of(1L))),
                List.of(candidate),
                List.of(), List.of(), List.of(), finalResults, evidence, true, List.of());
    }

    private static SearchDecisionTrace.CandidateTrace candidate(String content) {
        return new SearchDecisionTrace.CandidateTrace(
                1L, 10L, 20L, 1, ChunkSourceType.TEXT_CHUNK, 1, "텍스트 구간 1", content,
                List.of(new SearchDecisionTrace.RetrievalHitTrace("ORIGINAL", 1, 0.8, 0.2)),
                List.of(), SearchDecisionTrace.FirstFailureStage.NONE, "NONE");
    }

    private static Set<String> ids(JsonNode values) {
        Set<String> result = new LinkedHashSet<>();
        values.forEach(value -> result.add(value.path("id").asText()));
        return result;
    }

    private static java.util.stream.Stream<JsonNode> stream(JsonNode values) {
        List<JsonNode> nodes = new ArrayList<>();
        values.forEach(nodes::add);
        return nodes.stream();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", "")
                .trim();
    }
}
