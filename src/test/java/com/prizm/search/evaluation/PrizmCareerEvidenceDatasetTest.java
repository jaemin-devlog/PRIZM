package com.prizm.search.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.service.TextChunker;
import com.prizm.search.evaluation.SearchEvaluationData.Category;
import com.prizm.search.evaluation.SearchEvaluationData.Dataset;
import com.prizm.search.evaluation.SearchEvaluationData.EvidenceAnchor;
import com.prizm.search.evaluation.SearchEvaluationData.ExpectedEvidence;
import com.prizm.search.evaluation.SearchEvaluationData.FixtureDocument;
import com.prizm.search.evaluation.SearchEvaluationData.FixturePage;
import com.prizm.search.evaluation.SearchEvaluationData.OwnerScenario;
import com.prizm.search.evaluation.SearchEvaluationData.Question;
import com.prizm.search.evaluation.SearchEvaluationData.Split;
import com.prizm.search.evaluation.SearchEvaluationData.VersionScenario;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PrizmCareerEvidenceDatasetTest {

    private static final Path DATASET_DIRECTORY =
            Path.of("src/test/resources/search-evaluation/prizm-v1");
    private static final String DATASET_ID = "prizm-career-evidence-synthetic-v1.0";
    private static final String FROZEN_TEST_QUESTIONS_SHA256 =
            "c07e105023287663542601133e82fbfa78f3a341e75efc326989a0aadcc63600";
    private static final Map<String, String> FROZEN_FILE_SHA256 = Map.of(
            "fact-matrix.json", "6ceaea80834c809a6eb56ed7e5e9dd7ecf56c22d61cad56151ab5c6b60cde3e7",
            "corpus.json", "68584bd3888873e0ff3f8b0d203c11c530e778a1a5bf1accae7dfa2d34240fdf",
            "questions.jsonl", "10739296597c5372ed7467a8dcf1867f66de4924c3a2931b7ddf59f1d5c15a61");
    private static final int MINIMUM_PRIMARY_ACTIVE_CHUNKS_PER_SPLIT = 150;
    private static final List<NamedPattern> PERSONAL_DATA_PATTERNS = List.of(
            new NamedPattern("email address", Pattern.compile(
                    "(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b")),
            new NamedPattern("Korean telephone number", Pattern.compile(
                    "(?<!\\d)(?:01[016789]|0(?:2|[3-6][1-5]))[- .]?\\d{3,4}[- .]?\\d{4}(?!\\d)")),
            new NamedPattern("Korean resident registration number", Pattern.compile(
                    "(?<!\\d)\\d{6}[- ]?[1-4]\\d{6}(?!\\d)")),
            new NamedPattern("IPv4 address", Pattern.compile(
                    "(?<!\\d)(?:\\d{1,3}\\.){3}\\d{1,3}(?!\\d)")),
            new NamedPattern("web URL", Pattern.compile("(?i)(?:https?://|www\\.)")),
            new NamedPattern("local absolute path", Pattern.compile(
                    "(?i)(?:[A-Z]:\\\\|/(?:Users|home)/)")),
            new NamedPattern("credential assignment", Pattern.compile(
                    "(?i)\\b(?:api[_-]?key|access[_-]?token|secret|password)\\s*[:=]\\s*\\S+")),
            new NamedPattern("Korean street address", Pattern.compile(
                    "(?:서울|부산|대구|인천|광주|대전|울산|세종|경기|강원|충북|충남|전북|전남|경북|경남|제주)"
                            + "[^\\r\\n]{0,20}(?:로|길)\\s*\\d{1,4}")));

    private final Dataset dataset = new SearchEvaluationDatasetLoader(new ObjectMapper())
            .load(DATASET_DIRECTORY);
    private final TextChunker textChunker = new TextChunker(new IngestionProperties());

    @Test
    void hasTheFrozenIdentityAndReviewedSplitBalance() {
        assertThat(dataset.corpus().datasetId()).isEqualTo(DATASET_ID);
        assertThat(dataset.corpus().schemaVersion()).isEqualTo(2);
        assertThat(dataset.corpus().documents()).hasSize(114);
        assertThat(dataset.questions()).hasSize(300);

        assertSplitBalance(Split.TUNING, 180, 90, 90);
        assertSplitBalance(Split.TEST, 120, 60, 60);
    }

    @Test
    void preservesTheFrozenTestQuestionsByteForByte()
            throws IOException, NoSuchAlgorithmException {
        String testQuestions = Files.readAllLines(
                        DATASET_DIRECTORY.resolve(SearchEvaluationDatasetLoader.QUESTIONS_FILE),
                        StandardCharsets.UTF_8).stream()
                .filter(line -> line.contains("\"split\":\"TEST\""))
                .collect(Collectors.joining("\n", "", "\n"));

        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(testQuestions.getBytes(StandardCharsets.UTF_8));
        assertThat(HexFormat.of().formatHex(digest))
                .isEqualTo(FROZEN_TEST_QUESTIONS_SHA256);
    }

    @Test
    void preservesFrozenDatasetFilesAndManifestHashes()
            throws IOException, NoSuchAlgorithmException {
        JsonNode manifest = new ObjectMapper().readTree(Files.readString(
                DATASET_DIRECTORY.resolve("freeze-manifest.json"),
                StandardCharsets.UTF_8));

        for (Map.Entry<String, String> entry : FROZEN_FILE_SHA256.entrySet()) {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(Files.readAllBytes(DATASET_DIRECTORY.resolve(entry.getKey())));
            String actualHash = HexFormat.of().formatHex(digest);
            assertThat(actualHash)
                    .as("hard-coded SHA-256 for %s", entry.getKey())
                    .isEqualTo(entry.getValue());
            assertThat(manifest.path("files").path(entry.getKey()).path("sha256").asText())
                    .as("manifest SHA-256 for %s", entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }

    @Test
    void coversEveryEvaluationCategoryAndDocumentType() {
        assertThat(dataset.questions().stream()
                .map(Question::category)
                .collect(Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(Category.class));
        assertThat(dataset.corpus().documents().stream()
                .map(FixtureDocument::documentType)
                .collect(Collectors.toSet()))
                .containsExactlyInAnyOrderElementsOf(EnumSet.allOf(DocumentType.class));
    }

    @Test
    void referencesEveryFixtureWithinOneConsistentOwnerAndVersionScenario() {
        Set<String> documentIds = dataset.corpus().documents().stream()
                .map(FixtureDocument::fixtureId)
                .collect(Collectors.toSet());
        Set<String> referencedIds = dataset.questions().stream()
                .flatMap(question -> question.fixtureIds().stream())
                .collect(Collectors.toSet());

        assertThat(referencedIds).containsExactlyInAnyOrderElementsOf(documentIds);

        Map<String, Set<FixtureScenario>> scenariosByFixture = new HashMap<>();
        for (Question question : dataset.questions()) {
            FixtureScenario scenario = new FixtureScenario(
                    question.ownerScenario(), question.versionScenario());
            for (String fixtureId : question.fixtureIds()) {
                scenariosByFixture.computeIfAbsent(fixtureId, ignored -> new HashSet<>())
                        .add(scenario);
            }
        }

        assertThat(scenariosByFixture).hasSize(114);
        scenariosByFixture.forEach((fixtureId, scenarios) ->
                assertThat(scenarios)
                        .as("owner/version scenario for %s", fixtureId)
                        .singleElement());

        assertThat(dataset.questions())
                .filteredOn(question -> question.category() == Category.OWNER_BOUNDARY)
                .isNotEmpty()
                .allMatch(question -> question.ownerScenario() == OwnerScenario.OTHER_OWNER_ONLY
                        && question.versionScenario() == VersionScenario.ACTIVE);
        assertThat(dataset.questions())
                .filteredOn(question -> question.category() == Category.VERSION_BOUNDARY)
                .isNotEmpty()
                .allMatch(question -> question.ownerScenario() == OwnerScenario.PRIMARY_OWNER
                        && question.versionScenario() == VersionScenario.PAST_VERSION_ONLY);
        assertThat(dataset.questions())
                .filteredOn(question -> question.category() == Category.NO_SEARCHABLE_DOCUMENTS)
                .isNotEmpty()
                .allMatch(question -> question.ownerScenario() == OwnerScenario.NO_SEARCHABLE_DOCUMENTS
                        && question.versionScenario() == VersionScenario.NO_ACTIVE_VERSION);
    }

    @Test
    void locatesEveryAnchorOnOnePageAndPreservesItInProductionChunks() {
        Map<String, AnchorLocation> anchors = anchorLocations();
        Set<String> overlapDetailEvidenceIds = dataset.questions().stream()
                .filter(question -> question.category() == Category.OVERLAP_DUPLICATE)
                .flatMap(question -> question.expectedEvidence().stream())
                .filter(evidence -> evidence.relevance() == 2)
                .map(ExpectedEvidence::fixtureEvidenceId)
                .collect(Collectors.toSet());

        assertThat(overlapDetailEvidenceIds).isNotEmpty();
        for (AnchorLocation location : anchors.values()) {
            EvidenceAnchor anchor = location.anchor();
            List<FixturePage> containingPages = location.document().pages().stream()
                    .filter(page -> page.text().contains(anchor.anchorText()))
                    .toList();
            assertThat(containingPages)
                    .as("page containing %s", anchor.fixtureEvidenceId())
                    .singleElement();
            assertThat(occurrences(containingPages.get(0).text(), anchor.anchorText()))
                    .as("occurrences of %s on its source page", anchor.fixtureEvidenceId())
                    .isEqualTo(1);

            long containingChunkCount = location.document().pages().stream()
                    .flatMap(page -> textChunker.split(page.text()).stream())
                    .filter(chunk -> chunk.content().contains(anchor.anchorText()))
                    .count();
            if (overlapDetailEvidenceIds.contains(anchor.fixtureEvidenceId())) {
                assertThat(containingChunkCount)
                        .as("overlap chunks containing %s", anchor.fixtureEvidenceId())
                        .isEqualTo(2);
            }
            else {
                assertThat(containingChunkCount)
                        .as("chunks containing %s", anchor.fixtureEvidenceId())
                        .isBetween(1L, 2L);
            }
        }
    }

    @Test
    void keepsPdfGoldPagesOnThePositiveEvidencePage() {
        Map<String, AnchorLocation> anchors = anchorLocations();

        for (Question question : dataset.questions()) {
            List<AnchorLocation> positivePdfEvidence = question.expectedEvidence().stream()
                    .filter(evidence -> evidence.relevance() > 0)
                    .map(ExpectedEvidence::fixtureEvidenceId)
                    .map(anchors::get)
                    .filter(location -> location.document().fileType() == DocumentFileType.PDF)
                    .toList();
            if (positivePdfEvidence.isEmpty()) {
                continue;
            }

            assertThat(question.goldPage())
                    .as("gold page for %s", question.questionId())
                    .isPositive();
            assertThat(positivePdfEvidence)
                    .as("positive PDF anchor on the gold page for %s", question.questionId())
                    .anyMatch(location -> location.document().pages().stream()
                            .anyMatch(page -> page.pageNumber() == question.goldPage()
                                    && page.text().contains(location.anchor().anchorText())));
        }
    }

    @Test
    void keepsQueriesOutOfTheCorpusAndRejectsCommonPersonalDataShapes() {
        List<String> corpusPages = dataset.corpus().documents().stream()
                .flatMap(document -> document.pages().stream())
                .map(FixturePage::text)
                .toList();
        List<String> normalizedCorpusPages = corpusPages.stream()
                .map(this::normalize)
                .toList();

        for (Question question : dataset.questions()) {
            assertThat(normalizedCorpusPages)
                    .as("full query absent from corpus for %s", question.questionId())
                    .noneMatch(page -> page.contains(normalize(question.query())));
        }

        List<String> privacySurfaces = Stream.concat(
                        dataset.corpus().documents().stream().flatMap(document -> Stream.concat(
                                Stream.of(document.title()),
                                document.pages().stream().map(FixturePage::text))),
                        dataset.questions().stream().map(Question::query))
                .toList();
        for (NamedPattern namedPattern : PERSONAL_DATA_PATTERNS) {
            assertThat(privacySurfaces)
                    .as("no %s in the synthetic dataset", namedPattern.name())
                    .noneMatch(value -> namedPattern.pattern().matcher(value).find());
        }
    }

    @Test
    void keepsEveryParaphraseFreeOfAsciiTechnologyNamesAndExactNumbers() {
        List<Question> paraphrases = dataset.questions().stream()
                .filter(question -> question.category() == Category.PARAPHRASE)
                .toList();

        assertThat(paraphrases)
                .hasSize(60)
                .allMatch(question -> !Pattern.compile("[A-Za-z0-9]")
                        .matcher(question.query())
                        .find());
    }

    @Test
    void providesEnoughProductionChunksWithoutExceedingTheCurrentChunkLimit() {
        IngestionProperties properties = new IngestionProperties();
        assertThat(properties.getMaxChunkLength()).isEqualTo(800);
        assertThat(properties.getOverlap()).isEqualTo(120);

        Map<String, FixtureScenario> scenariosByFixture = fixtureScenarios();
        for (FixtureDocument document : dataset.corpus().documents()) {
            for (FixturePage page : document.pages()) {
                assertThat(textChunker.split(page.text()))
                        .as("production chunks for %s page %d", document.fixtureId(), page.pageNumber())
                        .isNotEmpty()
                        .allMatch(chunk -> chunk.content().length() <= properties.getMaxChunkLength());
            }
        }

        for (Split split : Split.values()) {
            long primaryActiveChunkCount = dataset.corpus().documents().stream()
                    .filter(document -> document.split() == split)
                    .filter(document -> scenariosByFixture.get(document.fixtureId()).equals(
                            new FixtureScenario(OwnerScenario.PRIMARY_OWNER, VersionScenario.ACTIVE)))
                    .flatMap(document -> document.pages().stream())
                    .map(FixturePage::text)
                    .map(textChunker::split)
                    .mapToLong(List::size)
                    .sum();
            assertThat(primaryActiveChunkCount)
                    .as("primary-owner ACTIVE chunks in %s", split)
                    .isGreaterThanOrEqualTo(MINIMUM_PRIMARY_ACTIVE_CHUNKS_PER_SPLIT);
        }
    }

    private void assertSplitBalance(
            Split split,
            int totalQuestions,
            int evidenceQuestions,
            int noEvidenceQuestions) {
        List<Question> selected = dataset.questions().stream()
                .filter(question -> question.split() == split)
                .toList();

        assertThat(selected).hasSize(totalQuestions);
        assertThat(selected).filteredOn(question -> !question.noEvidence()).hasSize(evidenceQuestions);
        assertThat(selected).filteredOn(Question::noEvidence).hasSize(noEvidenceQuestions);
    }

    private Map<String, AnchorLocation> anchorLocations() {
        Map<String, AnchorLocation> result = new HashMap<>();
        for (FixtureDocument document : dataset.corpus().documents()) {
            for (EvidenceAnchor anchor : document.evidenceAnchors()) {
                result.put(anchor.fixtureEvidenceId(), new AnchorLocation(document, anchor));
            }
        }
        return Map.copyOf(result);
    }

    private Map<String, FixtureScenario> fixtureScenarios() {
        Map<String, FixtureScenario> result = new HashMap<>();
        for (Question question : dataset.questions()) {
            FixtureScenario scenario = new FixtureScenario(
                    question.ownerScenario(), question.versionScenario());
            for (String fixtureId : question.fixtureIds()) {
                FixtureScenario existing = result.putIfAbsent(fixtureId, scenario);
                assertThat(existing == null || existing.equals(scenario))
                        .as("consistent scenario for %s", fixtureId)
                        .isTrue();
            }
        }
        return Map.copyOf(result);
    }

    private long occurrences(String value, String target) {
        long count = 0;
        int offset = 0;
        while ((offset = value.indexOf(target, offset)) >= 0) {
            count++;
            offset += target.length();
        }
        return count;
    }

    private String normalize(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.KOREAN)
                .replaceAll("\\s+", " ")
                .trim();
    }

    private record FixtureScenario(
            OwnerScenario ownerScenario,
            VersionScenario versionScenario) {
    }

    private record AnchorLocation(
            FixtureDocument document,
            EvidenceAnchor anchor) {
    }

    private record NamedPattern(
            String name,
            Pattern pattern) {
    }
}
