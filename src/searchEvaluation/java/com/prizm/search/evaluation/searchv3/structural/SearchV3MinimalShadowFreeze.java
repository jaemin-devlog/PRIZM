package com.prizm.search.evaluation.searchv3.structural;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/** Canonical output freeze and fail-closed Gold phase guard for PRZ-032. */
final class SearchV3MinimalShadowFreeze {

    static final int SCHEMA_VERSION = 1;
    static final String ARTIFACT_TYPE = "PRZ032_MINIMAL_V3_SHADOW_OUTPUT";

    private final ObjectMapper mapper = new ObjectMapper();

    FrozenOutput freeze(OutputArtifact output) {
        Objects.requireNonNull(output, "output");
        if (output.schemaVersion() != SCHEMA_VERSION
                || !ARTIFACT_TYPE.equals(output.artifactType())
                || output.queries().isEmpty()) {
            throw new IllegalArgumentException("PRZ-032 output contract is invalid");
        }
        byte[] canonical = mapper.writeValueAsBytes(output);
        return new FrozenOutput(
                output,
                SearchV3MinimalShadowDataset.sha256(canonical),
                canonical.length);
    }

    void writeCreateNew(Path path, FrozenOutput frozen) {
        try {
            Files.createDirectories(path.toAbsolutePath().normalize().getParent());
            ObjectNode wrapper = mapper.createObjectNode();
            wrapper.put("canonicalSha256", frozen.canonicalSha256());
            wrapper.put("canonicalByteLength", frozen.canonicalByteLength());
            wrapper.set("output", mapper.valueToTree(frozen.output()));
            Files.writeString(
                    path,
                    mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper) + "\n",
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot CREATE_NEW PRZ-032 output: " + path, exception);
        }
    }

    VerifiedOutput verify(Path path, FrozenOutput expected) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            JsonNode wrapper = mapper.readTree(bytes);
            if (!expected.canonicalSha256().equals(wrapper.path("canonicalSha256").asText())
                    || expected.canonicalByteLength() != wrapper.path("canonicalByteLength").asInt(-1)) {
                throw new IllegalStateException("PRZ-032 output wrapper identity changed");
            }
            byte[] canonical = mapper.writeValueAsBytes(wrapper.path("output"));
            String actual = SearchV3MinimalShadowDataset.sha256(canonical);
            if (!expected.canonicalSha256().equals(actual)
                    || canonical.length != expected.canonicalByteLength()) {
                throw new IllegalStateException("PRZ-032 output canonical hash changed");
            }
            return new VerifiedOutput(
                    expected,
                    SearchV3MinimalShadowDataset.sha256(bytes),
                    bytes.length);
        }
        catch (IOException exception) {
            throw new IllegalStateException("cannot verify PRZ-032 output", exception);
        }
    }

    static final class PhaseGuard {

        private Phase phase = Phase.SOURCE_ONLY;
        private FrozenOutput frozen;
        private VerifiedOutput verified;

        Phase phase() {
            return phase;
        }

        FrozenOutput freeze(SearchV3MinimalShadowFreeze freezer, OutputArtifact output) {
            require(Phase.SOURCE_ONLY);
            frozen = freezer.freeze(output);
            phase = Phase.OUTPUT_FROZEN;
            return frozen;
        }

        VerifiedOutput verify(SearchV3MinimalShadowFreeze freezer, Path path) {
            require(Phase.OUTPUT_FROZEN);
            verified = freezer.verify(path, frozen);
            phase = Phase.OUTPUT_VERIFIED;
            return verified;
        }

        <T> T joinGold(Function<VerifiedOutput, T> supplier) {
            require(Phase.OUTPUT_VERIFIED);
            T value = Objects.requireNonNull(supplier.apply(verified), "Gold supplier returned null");
            phase = Phase.GOLD_JOINED;
            return value;
        }

        private void require(Phase expected) {
            if (phase != expected) {
                throw new IllegalStateException(
                        "PRZ-032 phase violation: expected " + expected + " but was " + phase);
            }
        }
    }

    enum Phase {
        SOURCE_ONLY,
        OUTPUT_FROZEN,
        OUTPUT_VERIFIED,
        GOLD_JOINED
    }

    record ModelIdentity(String name, String digest, int dimensions, String similarity) {
    }

    record SourceFreeze(
            String v2SourceSha256,
            String v3SourceSha256,
            String comparisonPolicySha256,
            String inputCanonicalSha256,
            String goldSchemaSha256,
            String contractFileSha256) {
    }

    record SealedState(
            String combinedSha256,
            String manifestSha256,
            String gitTree,
            boolean opened,
            boolean searchExecuted,
            String currentFreshBaseline) {
    }

    record IndexingStats(
            long unitCount,
            long embeddingCount,
            double constructionMs,
            double indexingWallMs,
            double embeddingMs,
            long vectorStorageEstimateBytes) {
    }

    record IndexUnit(
            String unitId,
            String parentId,
            List<ProductionV2ShadowAdapter.SourceSpan> spans) {

        IndexUnit {
            spans = List.copyOf(spans);
        }
    }

    record QueryOutput(
            String suite,
            String datasetVersion,
            String split,
            String queryId,
            String userBundleId,
            String professionGroup,
            String language,
            String queryTextSha256,
            boolean typedApplicabilityVerified,
            ProductionV2ShadowAdapter.QueryRun v2,
            MinimalV3ShadowAdapter.QueryRun v3) {
    }

    record OutputArtifact(
            int schemaVersion,
            String artifactType,
            String codeFreezeCommit,
            SourceFreeze sourceFreeze,
            ModelIdentity model,
            String v2Profile,
            String v3Profile,
            String jdbcExecutionBoundary,
            int queryCount,
            int userCount,
            int documentVersionCount,
            IndexingStats v2Indexing,
            IndexingStats v3Indexing,
            List<IndexUnit> v2IndexUnits,
            List<IndexUnit> v3IndexUnits,
            List<QueryOutput> queries,
            SealedState sealedState) {

        OutputArtifact {
            v2IndexUnits = List.copyOf(v2IndexUnits);
            v3IndexUnits = List.copyOf(v3IndexUnits);
            queries = List.copyOf(queries);
        }
    }

    record FrozenOutput(OutputArtifact output, String canonicalSha256, int canonicalByteLength) {
    }

    record VerifiedOutput(FrozenOutput frozen, String fileSha256, long fileBytes) {
    }
}
