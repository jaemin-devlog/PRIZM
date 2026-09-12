package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Official one-shot DEV/CAL Current V2 versus Minimal V3 shadow comparison. */
class Prz032MinimalV3ShadowComparisonBenchmarkTest {

    static final String CODE_FREEZE_PROPERTY = "prizm.prz032.code-freeze-commit";
    static final Path CONTRACT = Path.of(
            "specs/PRZ-032-minimal-v3-shadow-comparison/execution-contract.json");
    static final Path OUTPUT = Path.of(
            "local/search-v3-evaluation/prz032/minimal-v3-shadow-output.json");
    static final Path REPORT = Path.of(
            "local/search-v3-evaluation/prz032/minimal-v3-shadow-report.json");
    static final String EXPECTED_MODEL = "bge-m3:latest";
    static final String EXPECTED_DIGEST =
            "7907646426070047a77226ac3e684fbbe8410524f7b4a74d02837e43f2146bab";
    static final String SEALED_COMBINED =
            "e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383";
    static final String SEALED_MANIFEST_SHA =
            "d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa";
    static final String SEALED_TREE = "a129080861d7dafd32a9b3b3357b61aebb237e59";
    static final String SEALED_GIT_PATH = "src/test/resources/search-v3-evaluation/sealed-final";
    static final Path SEALED_MANIFEST = Path.of(SEALED_GIT_PATH, "manifest.json");
    static final Pattern SHA = Pattern.compile("^[0-9a-f]{40}$");

    static final List<String> V2_SOURCE_FILES = List.of(
            "src/main/java/com/prizm/search/service/SearchService.java",
            "src/main/java/com/prizm/search/repository/VectorSearchRepository.java",
            "src/main/java/com/prizm/search/profile/CompositeSearchProfile.java",
            "src/main/java/com/prizm/search/profile/EvidenceQualityReranker.java",
            "src/main/java/com/prizm/search/profile/NaturalLanguageQueryFallback.java",
            "src/main/java/com/prizm/search/profile/NumericAnchorRescueProfile.java",
            "src/main/java/com/prizm/search/profile/ShortGeneralExactTokenRescueProfile.java",
            "src/main/java/com/prizm/ingestion/service/TextChunker.java",
            "src/main/java/com/prizm/search/service/EvidenceExpansionService.java",
            "src/main/java/com/prizm/search/service/SearchSnippetGenerator.java",
            "src/main/resources/application.yml");
    static final List<String> V3_SOURCE_FILES = List.of(
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralBlockParser.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralEvidenceChildBuilder.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/StructuralRetrievalPassageBuilder.java",
            "src/searchEvaluation/java/com/prizm/search/evaluation/searchv3/structural/EvidenceValidationSelector.java");
    static final List<String> GOLD_SCHEMA_FILES = List.of(
            "src/test/resources/search-v3-evaluation/schema/search-v3-benchmark.schema.json",
            "src/test/resources/search-v3-evaluation/schema/search-v3-prediction.schema.json",
            "src/searchEvaluation/resources/search-v3-evaluation/typed-constraints-stress-1.1.0/typed-stress.schema.json");
    static final String COMPARISON_POLICY = "PRZ032_POLICY_V1|V2_PRODUCTION_SOURCE_800_120_COMPOSITE|"
            + "V3_B3_BGE_TOP20_TYPED_ONLY_1_1_SELECTION5|SOURCE_SPAN_GOLD|"
            + "CANONICAL_OWNER_NFKC_LOWER_WHITESPACE_PUNCTUATION_PRESERVED|DEV_CAL_ONLY|ONE_SHOT";

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void runsOfficialComparisonOnceAfterSourceAndInputFreeze() throws Exception {
        String codeFreeze = System.getProperty(CODE_FREEZE_PROPERTY, "");
        assumeTrue(!codeFreeze.isBlank(), "PRZ-032 official comparison is opt-in");
        assertThat(codeFreeze).matches(SHA);
        String runHead = git("rev-parse", "HEAD");
        assertThat(git("rev-parse", codeFreeze)).isEqualTo(codeFreeze);
        assertThat(git("merge-base", "--is-ancestor", codeFreeze, runHead)).isBlank();
        assertThat(git("diff", "--name-only", codeFreeze + ".." + runHead))
                .isEqualTo(CONTRACT.toString().replace('\\', '/'));
        assertThat(git("status", "--porcelain")).isBlank();
        assertThat(Files.exists(OUTPUT)).isFalse();
        assertThat(Files.exists(REPORT)).isFalse();

        Contract contract = readContract();
        assertThat(contract.status()).isEqualTo("OFFICIAL_COMPARISON_NOT_RUN");
        assertThat(contract.codeFreezeCommit()).isEqualTo(codeFreeze);
        String v2SourceHash = sourceHash(V2_SOURCE_FILES);
        String v3SourceHash = sourceHash(V3_SOURCE_FILES);
        assertThat(v2SourceHash).isEqualTo(contract.v2SourceSha256());
        assertThat(v3SourceHash).isEqualTo(contract.v3SourceSha256());
        assertThat(SearchV3MinimalShadowDataset.sha256(COMPARISON_POLICY))
                .isEqualTo(contract.comparisonPolicySha256());
        assertThat(sourceHash(GOLD_SCHEMA_FILES)).isEqualTo(contract.goldSchemaSha256());

        SearchV3MinimalShadowFreeze.SealedState sealedBefore = sealedState();
        SearchV3MinimalShadowDataset.RuntimeInput runtime = new SearchV3MinimalShadowDataset().loadRuntime();
        assertThat(runtime.canonicalSha256()).isEqualTo(contract.inputCanonicalSha256());

        OllamaBgeM3EmbeddingClient model = new OllamaBgeM3EmbeddingClient();
        OllamaBgeM3EmbeddingClient.ModelMetadata metadata = model.inspectModel();
        assertThat(metadata.resolvedName()).isEqualTo(EXPECTED_MODEL);
        assertThat(metadata.digest()).isEqualTo(EXPECTED_DIGEST);
        assertThat(metadata.dimensions()).isEqualTo(1024);

        ProductionV2ShadowAdapter v2 = new ProductionV2ShadowAdapter(model);
        MinimalV3ShadowAdapter v3 = new MinimalV3ShadowAdapter(model);
        ProductionV2ShadowAdapter.IndexedCorpus v2Corpus = v2.index(runtime.documents());
        MinimalV3ShadowAdapter.IndexedCorpus v3Corpus = v3.index(runtime.documents());
        List<SearchV3MinimalShadowFreeze.QueryOutput> outputs = new ArrayList<>();
        for (SearchV3MinimalShadowDataset.RuntimeQuery query : runtime.queries()) {
            ProductionV2ShadowAdapter.QueryExecution v2Execution = v2.query(v2Corpus, query);
            ProductionV2ShadowAdapter.QueryRun v2Run = v2Execution.output();
            MinimalV3ShadowAdapter.QueryRun v3Run = v3.query(
                    v3Corpus, query, v2Execution.queryEmbedding(), v2Run.queryEmbeddingMs());
            outputs.add(new SearchV3MinimalShadowFreeze.QueryOutput(
                    query.suite(), query.datasetVersion(), query.split(), query.queryId(),
                    query.userBundleId(), query.professionGroup(), query.language(),
                    SearchV3MinimalShadowDataset.sha256(query.text()),
                    query.typedApplicabilityVerified(), v2Run, v3Run));
        }
        assertThat(outputs).hasSize(117);

        SearchV3MinimalShadowFreeze freezer = new SearchV3MinimalShadowFreeze();
        SearchV3MinimalShadowFreeze.PhaseGuard guard = new SearchV3MinimalShadowFreeze.PhaseGuard();
        SearchV3MinimalShadowFreeze.OutputArtifact artifact = new SearchV3MinimalShadowFreeze.OutputArtifact(
                SearchV3MinimalShadowFreeze.SCHEMA_VERSION,
                SearchV3MinimalShadowFreeze.ARTIFACT_TYPE,
                codeFreeze,
                new SearchV3MinimalShadowFreeze.SourceFreeze(
                        v2SourceHash,
                        v3SourceHash,
                        contract.comparisonPolicySha256(),
                        runtime.canonicalSha256(),
                        contract.goldSchemaSha256(),
                        SearchV3MinimalShadowDataset.sha256(Files.readAllBytes(CONTRACT))),
                new SearchV3MinimalShadowFreeze.ModelIdentity(
                        metadata.resolvedName(), metadata.digest(), metadata.dimensions(), "COSINE"),
                "source-dedup-evidence-signals-v1",
                "structural-b3-dense-top20 + typed-validation-selection-only",
                "PRODUCTION_SERVICE_REPOSITORY_SOURCE_WITH_EVALUATION_JDBC_ROWS;"
                        + "POSTGRESQL_SQL_RUNTIME_NOT_REVERIFIED",
                runtime.queries().size(),
                (int) runtime.queries().stream().map(SearchV3MinimalShadowDataset.RuntimeQuery::userBundleId)
                        .distinct().count(),
                runtime.documents().size(),
                indexing(v2Corpus),
                indexing(v3Corpus),
                v2IndexUnits(v2Corpus),
                v3IndexUnits(v3Corpus),
                List.copyOf(outputs),
                sealedBefore);
        SearchV3MinimalShadowFreeze.FrozenOutput frozen = guard.freeze(freezer, artifact);
        freezer.writeCreateNew(OUTPUT, frozen);
        SearchV3MinimalShadowFreeze.VerifiedOutput verified = guard.verify(freezer, OUTPUT);
        assertThat(guard.phase()).isEqualTo(SearchV3MinimalShadowFreeze.Phase.OUTPUT_VERIFIED);

        SearchV3MinimalShadowGold.GoldSnapshot gold = guard.joinGold(
                verifiedOutput -> new SearchV3MinimalShadowGold()
                        .loadAfterOutputVerified(verifiedOutput, runtime));
        SearchV3MinimalShadowEvaluator.EvaluationReport report =
                new SearchV3MinimalShadowEvaluator().evaluate(artifact, gold);
        writeCreateNew(REPORT, Map.of(
                "outputCanonicalSha256", frozen.canonicalSha256(),
                "outputFileSha256", verified.fileSha256(),
                "comparison", report));

        assertThat(sourceHash(V2_SOURCE_FILES)).isEqualTo(v2SourceHash);
        assertThat(sourceHash(V3_SOURCE_FILES)).isEqualTo(v3SourceHash);
        assertThat(sealedState()).isEqualTo(sealedBefore);
        assertThat(git("rev-parse", "HEAD")).isEqualTo(runHead);
        assertThat(git("status", "--porcelain")).isBlank();

        System.out.println("PRZ032_OUTPUT_SHA256=" + verified.fileSha256());
        System.out.println("PRZ032_REPORT_SHA256="
                + SearchV3MinimalShadowDataset.sha256(Files.readAllBytes(REPORT)));
        System.out.println("PRZ032_INPUT_SHA256=" + runtime.canonicalSha256());
        System.out.println("PRZ032_DECISION=" + report.decision());
    }

    private SearchV3MinimalShadowFreeze.IndexingStats indexing(
            ProductionV2ShadowAdapter.IndexedCorpus value) {
        return new SearchV3MinimalShadowFreeze.IndexingStats(
                value.chunks().size(), value.chunks().size(), value.constructionMs(),
                value.indexingWallMs(), value.embeddingMs(), value.chunks().size() * 1024L * Float.BYTES);
    }

    private SearchV3MinimalShadowFreeze.IndexingStats indexing(MinimalV3ShadowAdapter.IndexedCorpus value) {
        return new SearchV3MinimalShadowFreeze.IndexingStats(
                value.passages().size(), value.passages().size(), value.constructionMs(),
                value.indexingWallMs(), value.embeddingMs(), value.passages().size() * 1024L * Float.BYTES);
    }

    private List<SearchV3MinimalShadowFreeze.IndexUnit> v2IndexUnits(
            ProductionV2ShadowAdapter.IndexedCorpus corpus) {
        return corpus.chunks().stream().filter(ProductionV2ShadowAdapter.FixedChunk::active)
                .map(value -> new SearchV3MinimalShadowFreeze.IndexUnit(
                        "FIXED|" + value.versionFixtureId() + "|" + value.chunkNo(), null,
                        List.of(new ProductionV2ShadowAdapter.SourceSpan(
                                value.userBundleId(), value.documentFixtureId(), value.versionFixtureId(),
                                value.sourcePath(), value.page(), value.codePointStart(), value.codePointEnd(),
                                value.content(), SearchV3MinimalShadowDataset.sha256(value.content())))))
                .toList();
    }

    private List<SearchV3MinimalShadowFreeze.IndexUnit> v3IndexUnits(
            MinimalV3ShadowAdapter.IndexedCorpus corpus) {
        return corpus.passages().stream().map(value -> new SearchV3MinimalShadowFreeze.IndexUnit(
                value.passage().passageId(), value.passage().parentAnnotationCandidateId(),
                value.passage().evidenceChildren().stream().map(child -> {
                    SourceProvenance source = child.provenance();
                    return new ProductionV2ShadowAdapter.SourceSpan(
                            value.userBundleId(), source.documentId(), source.versionId(), source.sourcePath(),
                            source.page(), source.codePointStart(), source.codePointEnd(), child.sourceText(),
                            source.exactTextSha256());
                }).toList())).toList();
    }

    private SearchV3MinimalShadowFreeze.SealedState sealedState() throws Exception {
        byte[] manifestBytes = Files.readAllBytes(SEALED_MANIFEST);
        JsonNode manifest = mapper.readTree(manifestBytes);
        assertThat(SearchV3MinimalShadowDataset.sha256(manifestBytes)).isEqualTo(SEALED_MANIFEST_SHA);
        assertThat(manifest.path("combinedSha256").asText()).isEqualTo(SEALED_COMBINED);
        assertThat(manifest.path("opened").asBoolean(true)).isFalse();
        assertThat(manifest.path("searchExecuted").asBoolean(true)).isFalse();
        assertThat(git("rev-parse", "HEAD:" + SEALED_GIT_PATH)).isEqualTo(SEALED_TREE);
        return new SearchV3MinimalShadowFreeze.SealedState(
                SEALED_COMBINED, SEALED_MANIFEST_SHA, SEALED_TREE, false, false, "NOT_RUN");
    }

    private Contract readContract() throws IOException {
        JsonNode root = mapper.readTree(Files.readString(CONTRACT, StandardCharsets.UTF_8));
        return new Contract(
                root.path("status").asText(), root.path("codeFreezeCommit").asText(),
                root.path("v2SourceSha256").asText(), root.path("v3SourceSha256").asText(),
                root.path("comparisonPolicySha256").asText(), root.path("inputCanonicalSha256").asText(),
                root.path("goldSchemaSha256").asText());
    }

    static String sourceHash(List<String> paths) throws IOException {
        StringBuilder canonical = new StringBuilder();
        for (String path : paths.stream().sorted().toList()) {
            byte[] bytes = Files.readAllBytes(Path.of(path));
            canonical.append(path.replace('\\', '/')).append('|').append(bytes.length).append('|')
                    .append(SearchV3MinimalShadowDataset.sha256(bytes)).append('\n');
        }
        return SearchV3MinimalShadowDataset.sha256(canonical.toString());
    }

    private void writeCreateNew(Path path, Object value) throws IOException {
        Files.createDirectories(path.toAbsolutePath().normalize().getParent());
        Files.writeString(
                path, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n",
                StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
    }

    private String git(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        if (process.waitFor() != 0) {
            throw new IllegalStateException("git failed: " + output);
        }
        return output;
    }

    private record Contract(
            String status,
            String codeFreezeCommit,
            String v2SourceSha256,
            String v3SourceSha256,
            String comparisonPolicySha256,
            String inputCanonicalSha256,
            String goldSchemaSha256) {
    }
}
