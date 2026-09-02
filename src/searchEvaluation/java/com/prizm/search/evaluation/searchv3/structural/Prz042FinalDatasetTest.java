package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class Prz042FinalDatasetTest {

    private static final Path DEV = Path.of("src/test/resources/search-v3-evaluation/dev");
    private static final String DATASET_VERSION = "search-v3-fresh-seed-1.0.1";
    private static final String COMBINED_SHA = "a".repeat(64);

    @TempDir
    Path temporaryDirectory;

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void projectsOnlyGoldFreeRuntimeIdentityAfterFrozenInputAndAttempt() throws Exception {
        Fixture fixture = syntheticFinalFixture();

        Prz042FinalDataset.RuntimeInput runtime = new Prz042FinalDataset().load(fixture.attempt());

        assertThat(runtime.datasetVersion()).isEqualTo(DATASET_VERSION);
        assertThat(runtime.split()).isEqualTo("SEALED_FINAL_TEST");
        assertThat(runtime.documents()).hasSize(5);
        assertThat(runtime.activeDocuments()).hasSize(4);
        assertThat(runtime.queries()).hasSize(13);
        assertThat(runtime.queries()).allSatisfy(query -> {
            assertThat(query.queryId()).startsWith("SV3-");
            assertThat(query.userBundleId()).startsWith("SV3-");
            assertThat(query.text()).isNotBlank();
            assertThat(query.language()).isIn("KO", "EN", "KO_EN_MIXED");
            assertThat(query.professionGroup()).isNotBlank();
            assertThat(query.profession()).isNotBlank();
        });
        assertThat(runtime.queries().stream().map(query -> Prz042FinalDataset.normalizeQuery(query.text())))
                .doesNotHaveDuplicates();
    }

    @Test
    void failsClosedWithoutAnOfficialAttemptBeforeAnyPayloadCanBeLoaded() {
        assertThatThrownBy(() -> new Prz042FinalDataset().load(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("attempt");
    }

    @Test
    void goldCannotBeLoadedWithoutVerifiedPredictions() throws Exception {
        Fixture fixture = syntheticFinalFixture();
        Prz042FinalDataset.RuntimeInput runtime = new Prz042FinalDataset().load(fixture.attempt());

        assertThatThrownBy(() -> new Prz042FinalGold().load(null, runtime))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("verifiedPredictions");
    }

    private Fixture syntheticFinalFixture() throws Exception {
        Path splitRoot = temporaryDirectory.resolve("sealed-final");
        Path documents = splitRoot.resolve("documents");
        Files.createDirectories(documents);

        ObjectNode corpus = (ObjectNode) mapper.readTree(Files.readAllBytes(DEV.resolve("corpus.json")));
        corpus.put("split", "SEALED_FINAL_TEST");
        for (JsonNode bundle : corpus.path("userBundles")) {
            ((ObjectNode) bundle).put("split", "SEALED_FINAL_TEST");
            for (JsonNode document : bundle.path("documents")) {
                Path source = DEV.resolve(document.path("contentPath").asText());
                Files.copy(source, splitRoot.resolve(document.path("contentPath").asText()));
            }
        }
        ObjectNode questions = (ObjectNode) mapper.readTree(Files.readAllBytes(DEV.resolve("questions.json")));
        questions.put("split", "SEALED_FINAL_TEST");
        write(splitRoot.resolve("corpus.json"), corpus);
        write(splitRoot.resolve("questions.json"), questions);

        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("artifactType", "MANIFEST");
        manifest.put("schemaVersion", "1.0.0");
        manifest.put("datasetVersion", DATASET_VERSION);
        manifest.put("split", "SEALED_FINAL_TEST");
        manifest.put("status", "SEALED");
        manifest.put("mutable", false);
        manifest.put("opened", false);
        manifest.put("searchExecuted", false);
        ObjectNode counts = manifest.putObject("counts");
        counts.put("userBundles", corpus.path("userBundles").size());
        counts.put("documentVersions", 5);
        counts.put("activeDocumentVersions", 4);
        counts.put("queries", questions.path("queries").size());
        ArrayNode files = manifest.putArray("files");
        addManifestFile(files, splitRoot, "corpus.json");
        addManifestFile(files, splitRoot, "questions.json");
        for (JsonNode bundle : corpus.path("userBundles")) {
            for (JsonNode document : bundle.path("documents")) {
                addManifestFile(files, splitRoot, document.path("contentPath").asText());
            }
        }
        manifest.put("combinedSha256", COMBINED_SHA);
        Path manifestPath = splitRoot.resolve("manifest.json");
        write(manifestPath, manifest);

        String contractSha = "b".repeat(64);
        Prz042FinalFreeze.VerifiedInput verifiedInput = new Prz042FinalFreeze.VerifiedInput(
                temporaryDirectory.resolve("execution-contract.json"),
                contractSha,
                splitRoot.toAbsolutePath().normalize(),
                manifestPath.toAbsolutePath().normalize(),
                Prz042FinalDataset.sha256(Files.readAllBytes(manifestPath)),
                COMBINED_SHA,
                DATASET_VERSION,
                "SEALED_FINAL_TEST",
                "e".repeat(64),
                "f".repeat(40),
                "c".repeat(40),
                "bge-m3",
                "d".repeat(64),
                1024,
                Map.of(),
                corpus.path("userBundles").size(),
                questions.path("queries").size(),
                questions.path("queries").size(),
                0);

        Path runDirectory = temporaryDirectory.resolve("run");
        Files.createDirectories(runDirectory);
        Path attemptPath = runDirectory.resolve("attempt.json");
        ObjectNode attemptNode = mapper.createObjectNode();
        attemptNode.put("artifactType", "PRZ042_OFFICIAL_ATTEMPT");
        attemptNode.put("protocolVersion", Prz042FinalFreeze.PROTOCOL_VERSION);
        attemptNode.put("attempt", 1);
        attemptNode.put("contractSha256", contractSha);
        attemptNode.put("sealedCombinedSha256", COMBINED_SHA);
        attemptNode.put("startedAt", "2026-09-02T00:00:00Z");
        write(attemptPath, attemptNode);
        Prz042FinalFreeze.Attempt attempt = new Prz042FinalFreeze.Attempt(
                verifiedInput,
                runDirectory.toAbsolutePath().normalize(),
                attemptPath.toAbsolutePath().normalize(),
                Prz042FinalDataset.sha256(Files.readAllBytes(attemptPath)));
        return new Fixture(attempt);
    }

    private void addManifestFile(ArrayNode files, Path splitRoot, String relative) throws Exception {
        Path path = splitRoot.resolve(relative);
        byte[] bytes = Files.readAllBytes(path);
        ObjectNode entry = files.addObject();
        entry.put("path", splitRoot.getFileName() + "/" + relative.replace('\\', '/'));
        entry.put("bytes", bytes.length);
        entry.put("sha256", Prz042FinalDataset.sha256(bytes));
    }

    private void write(Path path, JsonNode node) throws Exception {
        Files.writeString(
                path,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(node) + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW,
                StandardOpenOption.WRITE);
    }

    private record Fixture(Prz042FinalFreeze.Attempt attempt) {
    }
}
