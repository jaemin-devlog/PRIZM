package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class SearchV3B3CandidateReplayTest {

    private static final Set<String> FORBIDDEN_OUTPUT_FIELDS = Set.of(
            "coveredUnitIds",
            "coveredGroupIds",
            "coveredParentIds",
            "answerability",
            "directSupport",
            "categories");

    private final ObjectMapper mapper = new ObjectMapper();

    @TempDir
    Path temporaryDirectory;

    @Test
    void projectsOnlyGoldFreeWhitelistAndProducesDeterministicCanonicalBytes() throws Exception {
        SearchV3B3CandidateReplay.Suite suite = SearchV3B3CandidateReplay.Suite.ORIGINAL_SEED;
        Fixture fixture = writeFixture(suite, artifact(suite, 1));

        SearchV3B3CandidateReplay.Replay first = fixture.loader().load(fixture.path(), suite.rootNode());
        SearchV3B3CandidateReplay.Replay second = fixture.loader().load(fixture.path(), suite.rootNode());

        assertThat(first.candidates()).hasSize(suite.queryInventory().size());
        assertThat(first.canonicalBytes()).containsExactly(second.canonicalBytes());
        assertThat(first.canonicalSha256()).isEqualTo(second.canonicalSha256());
        assertThat(sha256(first.canonicalBytes())).isEqualTo(first.canonicalSha256());

        JsonNode rows = mapper.readTree(first.canonicalBytes());
        assertThat(rows).hasSize(first.candidates().size());
        JsonNode firstRow = rows.get(0);
        assertThat(firstRow.size()).isEqualTo(SearchV3B3CandidateReplay.canonicalFields().size());
        SearchV3B3CandidateReplay.canonicalFields().forEach(field -> assertThat(firstRow.has(field)).isTrue());
        FORBIDDEN_OUTPUT_FIELDS.forEach(field -> assertThat(firstRow.has(field)).isFalse());

        String canonical = new String(first.canonicalBytes(), StandardCharsets.UTF_8);
        assertThat(canonical)
                .doesNotContain("source-SV3-U01-Q01-1", "retrieval-SV3-U01-Q01-1")
                .doesNotContain("coveredUnitIds", "coveredGroupIds", "coveredParentIds")
                .doesNotContain("answerability", "directSupport", "categories")
                .doesNotContain("GOLD-U01", "GOLD-G01", "GOLD-P01", "gold-must-not-leak", "SUPPORTED");
        assertThat(first.candidates().get(0).sourceSha256())
                .isEqualTo(sha256("source-SV3-U01-Q01-1".getBytes(StandardCharsets.UTF_8)));
        assertThat(first.candidates().get(0).retrievalSha256())
                .isEqualTo(sha256("retrieval-SV3-U01-Q01-1".getBytes(StandardCharsets.UTF_8)));

        byte[] returned = first.canonicalBytes();
        returned[0] = (byte) 'X';
        assertThat(first.canonicalBytes()[0]).isEqualTo((byte) '[');
    }

    @Test
    void acceptsAllFourApprovedRootNodesAtOnlyTheirApprovedPaths() throws Exception {
        for (SearchV3B3CandidateReplay.Suite suite : SearchV3B3CandidateReplay.Suite.values()) {
            Fixture fixture = writeFixture(suite, artifact(suite, 1));

            SearchV3B3CandidateReplay.Replay replay = fixture.loader().load(fixture.path(), suite.rootNode());

            assertThat(replay.candidates()).hasSize(suite.queryInventory().size());
            assertThat(replay.candidates()).allSatisfy(candidate -> {
                assertThat(candidate.suite()).isEqualTo(suite.rootNode());
                assertThat(candidate.dataset()).isEqualTo(suite.datasetVersion());
            });
        }
    }

    @Test
    void rejectsArtifactHashDriftAndUnapprovedPathOrRootPairs() throws Exception {
        SearchV3B3CandidateReplay.Suite suite = SearchV3B3CandidateReplay.Suite.ORIGINAL_SEED;
        Fixture fixture = writeFixture(suite, artifact(suite, 1));
        Files.writeString(fixture.path(), "\n", StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);

        assertThatThrownBy(() -> fixture.loader().load(fixture.path(), suite.rootNode()))
                .isInstanceOf(SearchV3B3CandidateReplay.ReplayValidationException.class)
                .hasMessageContaining("artifact SHA-256 mismatch");
        assertThatThrownBy(() -> fixture.loader().load(
                        temporaryDirectory.resolve("local/search-v3-evaluation/PRZ-029-semantic/report.json"),
                        suite.rootNode()))
                .isInstanceOf(SearchV3B3CandidateReplay.ReplayValidationException.class)
                .hasMessageContaining("forbidden");
        assertThatThrownBy(() -> fixture.loader().load(
                        temporaryDirectory.resolve("src/test/resources/search-v3-evaluation/sealed-final/report.json"),
                        suite.rootNode()))
                .isInstanceOf(SearchV3B3CandidateReplay.ReplayValidationException.class)
                .hasMessageContaining("forbidden");
        assertThatThrownBy(() -> fixture.loader().load(fixture.path(), "sealedFinal"))
                .isInstanceOf(SearchV3B3CandidateReplay.ReplayValidationException.class)
                .hasMessageContaining("root node is not approved");
        assertThatThrownBy(() -> fixture.loader().load(fixture.path(), "historicalLongForm"))
                .isInstanceOf(SearchV3B3CandidateReplay.ReplayValidationException.class)
                .hasMessageContaining("path/root pair is not approved");
    }

    @Test
    void rejectsFrozenSchemaModelDatasetManifestAndQueryInventoryDrift() {
        SearchV3B3CandidateReplay.Suite suite = SearchV3B3CandidateReplay.Suite.ORIGINAL_SEED;

        assertMutationRejected(suite,
                root -> report(root, suite).put("schemaVersion", 4),
                "schemaVersion mismatch");
        assertMutationRejected(suite,
                root -> ((ObjectNode) report(root, suite).path("model")).put("digest", "0".repeat(64)),
                "model.digest mismatch");
        assertMutationRejected(suite,
                root -> ((ObjectNode) report(root, suite).path("model")).put("dimensions", 768),
                "model.dimensions mismatch");
        assertMutationRejected(suite,
                root -> ((ObjectNode) report(root, suite).path("contract")).put("similarity", "DOT_PRODUCT"),
                "contract.similarity mismatch");
        assertMutationRejected(suite,
                root -> ((ObjectNode) report(root, suite).path("splitManifestHashes"))
                        .put("DEV", "0".repeat(64)),
                "splitManifestHashes.DEV mismatch");
        assertMutationRejected(suite,
                root -> ((ObjectNode) report(root, suite).path("queries").get(0)).put("queryId", "WRONG-Q01"),
                "query inventory ID mismatch");
    }

    @Test
    void rejectsCandidateCountRankIdentityChildAndCosineFailures() {
        SearchV3B3CandidateReplay.Suite suite = SearchV3B3CandidateReplay.Suite.ORIGINAL_SEED;

        assertMutationRejected(suite,
                root -> ((ObjectNode) firstPassage(root, suite)).put("candidateCount", 2),
                "candidateCount/full-ranking size mismatch");
        assertMutationRejected(suite,
                root -> ((ObjectNode) firstRanking(root, suite).get(0)).put("rank", 2),
                "ranks must be contiguous");

        assertMutationRejected(suite, 2, root -> {
            ArrayNode ranking = firstRanking(root, suite);
            ((ObjectNode) ranking.get(1)).put("candidateId", ranking.get(0).path("candidateId").textValue());
        }, "duplicate candidate ID");
        assertMutationRejected(suite, 2, root -> {
            ArrayNode ranking = firstRanking(root, suite);
            String child = ranking.get(0).path("evidenceChildIds").get(0).textValue();
            ((ArrayNode) ranking.get(1).path("evidenceChildIds")).set(0, mapper.getNodeFactory().textNode(child));
        }, "duplicate EvidenceChild ID");
        assertMutationRejected(suite,
                root -> ((ObjectNode) firstRanking(root, suite).get(0))
                        .put("cosineScore", new BigDecimal("1e400")),
                "cosineScore must be finite");
        assertMutationRejected(suite, 2, root -> {
            ArrayNode ranking = firstRanking(root, suite);
            ((ObjectNode) ranking.get(0)).put("cosineScore", 0.1d);
            ((ObjectNode) ranking.get(1)).put("cosineScore", 0.2d);
        }, "full ranking is not ordered");
    }

    @Test
    void replaysCurrentIgnoredArtifactsWithoutModelWhenTheyArePresent() {
        Path phaseOne = Path.of(SearchV3B3CandidateReplay.PHASE_ONE_ARTIFACT);
        Path robustness = Path.of(SearchV3B3CandidateReplay.ROBUSTNESS_ARTIFACT);
        assumeTrue(Files.isRegularFile(phaseOne) && Files.isRegularFile(robustness));

        SearchV3B3CandidateReplay loader = new SearchV3B3CandidateReplay();
        SearchV3B3CandidateReplay.Replay original =
                loader.load(SearchV3B3CandidateReplay.Suite.ORIGINAL_SEED);
        SearchV3B3CandidateReplay.Replay longForm =
                loader.load(SearchV3B3CandidateReplay.Suite.LONG_FORM_EXPANSION);
        SearchV3B3CandidateReplay.Replay historical =
                loader.load(SearchV3B3CandidateReplay.Suite.HISTORICAL_LONG_FORM);
        SearchV3B3CandidateReplay.Replay robustnessReplay =
                loader.load(SearchV3B3CandidateReplay.Suite.INDEPENDENT_ROBUSTNESS);

        assertThat(original.candidates()).hasSize(63);
        assertThat(longForm.candidates()).hasSize(288);
        assertThat(historical.candidates()).hasSize(288);
        assertThat(robustnessReplay.candidates()).hasSize(200);
        assertThat(longForm.canonicalBytes()).containsExactly(loader
                .load(SearchV3B3CandidateReplay.Suite.LONG_FORM_EXPANSION)
                .canonicalBytes());
    }

    private void assertMutationRejected(
            SearchV3B3CandidateReplay.Suite suite,
            Consumer<ObjectNode> mutation,
            String message) {
        assertMutationRejected(suite, 1, mutation, message);
    }

    private void assertMutationRejected(
            SearchV3B3CandidateReplay.Suite suite,
            int candidatesPerQuery,
            Consumer<ObjectNode> mutation,
            String message) {
        assertThatThrownBy(() -> {
                    ObjectNode root = artifact(suite, candidatesPerQuery);
                    mutation.accept(root);
                    Fixture fixture = writeFixture(suite, root);
                    fixture.loader().load(fixture.path(), suite.rootNode());
                })
                .isInstanceOf(SearchV3B3CandidateReplay.ReplayValidationException.class)
                .hasMessageContaining(message);
    }

    private Fixture writeFixture(SearchV3B3CandidateReplay.Suite suite, ObjectNode artifact) throws IOException {
        Path caseRoot = Files.createTempDirectory(temporaryDirectory, "replay-");
        Path artifactPath = caseRoot.resolve(suite.artifactRelativePath());
        Files.createDirectories(artifactPath.getParent());
        byte[] bytes = mapper.writeValueAsBytes(artifact);
        Files.write(artifactPath, bytes);
        String fixtureSha256 = sha256(bytes);
        String phaseOneSha256 = suite.artifactRelativePath().equals(SearchV3B3CandidateReplay.PHASE_ONE_ARTIFACT)
                ? fixtureSha256
                : SearchV3B3CandidateReplay.PHASE_ONE_ARTIFACT_SHA256;
        String robustnessSha256 = suite.artifactRelativePath().equals(SearchV3B3CandidateReplay.ROBUSTNESS_ARTIFACT)
                ? fixtureSha256
                : SearchV3B3CandidateReplay.ROBUSTNESS_ARTIFACT_SHA256;
        return new Fixture(
                artifactPath,
                new SearchV3B3CandidateReplay(caseRoot, phaseOneSha256, robustnessSha256));
    }

    private ObjectNode artifact(SearchV3B3CandidateReplay.Suite suite, int candidatesPerQuery) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode report = root.putObject(suite.rootNode());
        report.put("schemaVersion", 3);
        report.put("phase", suite.phase());
        report.put("datasetVersion", suite.datasetVersion());

        ObjectNode splitHashes = report.putObject("splitManifestHashes");
        suite.splitManifestHashes().forEach(splitHashes::put);
        ObjectNode model = report.putObject("model");
        model.put("resolvedName", "bge-m3:latest");
        model.put("digest", SearchV3B3CandidateReplay.BGE_M3_DIGEST);
        model.put("dimensions", 1024);
        model.put("embeddingCapable", true);
        ObjectNode contract = report.putObject("contract");
        contract.put("embeddingModel", "bge-m3");
        contract.put("embeddingDimensions", 1024);
        contract.put("similarity", "COSINE");
        contract.put("ranking", "RAW_DENSE_ONLY");
        report.put("sealedFinalOpened", false);
        report.put("sealedFinalSearchExecuted", false);
        report.put("currentFreshBaseline", "NOT_RUN");

        ArrayNode queries = report.putArray("queries");
        for (SearchV3B3CandidateReplay.QueryIdentity expected : suite.queryInventory()) {
            ObjectNode query = queries.addObject();
            query.put("queryId", expected.queryId());
            query.put("split", expected.split());
            query.put("userBundleId", expected.owner());
            query.put("professionGroup", expected.profession());
            query.put("language", expected.language());
            query.put("answerability", "SUPPORTED");
            query.put("directSupport", true);
            query.putArray("categories").add("gold-must-not-leak");
            ObjectNode passage = query.putObject("passage");
            passage.put("candidateCount", candidatesPerQuery);
            ArrayNode ranking = passage.putArray("rawDenseRanking");
            for (int rank = 1; rank <= candidatesPerQuery; rank++) {
                String candidateId = expected.owner() + "-V01-RP-%04d".formatted(rank);
                String source = "source-" + expected.queryId() + "-" + rank;
                String retrieval = "retrieval-" + expected.queryId() + "-" + rank;
                ObjectNode candidate = ranking.addObject();
                candidate.put("rank", rank);
                candidate.put("candidateId", candidateId);
                candidate.put("cosineScore", 0.9d - rank * 0.1d);
                candidate.put("documentId", expected.owner() + "-D01");
                candidate.put("versionId", expected.owner() + "-V01");
                candidate.put("sourceBlockType", "RETRIEVAL_PASSAGE");
                candidate.put("sourceText", source);
                candidate.put("retrievalText", retrieval);
                candidate.put("parentAnnotationCandidateId", expected.owner() + "-V01-SB-0001");
                candidate.put("sourceCodePointLength", source.codePointCount(0, source.length()));
                candidate.putArray("evidenceChildIds").add(candidateId + "-C01");
                candidate.putArray("coveredUnitIds").add("GOLD-U01");
                candidate.putArray("coveredGroupIds").add("GOLD-G01");
                candidate.putArray("coveredParentIds").add("GOLD-P01");
            }
        }
        return root;
    }

    private ObjectNode report(ObjectNode root, SearchV3B3CandidateReplay.Suite suite) {
        return (ObjectNode) root.path(suite.rootNode());
    }

    private JsonNode firstPassage(ObjectNode root, SearchV3B3CandidateReplay.Suite suite) {
        return report(root, suite).path("queries").get(0).path("passage");
    }

    private ArrayNode firstRanking(ObjectNode root, SearchV3B3CandidateReplay.Suite suite) {
        return (ArrayNode) firstPassage(root, suite).path("rawDenseRanking");
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private record Fixture(Path path, SearchV3B3CandidateReplay loader) {
    }
}
