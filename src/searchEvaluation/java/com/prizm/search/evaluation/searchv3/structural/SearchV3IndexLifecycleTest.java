package com.prizm.search.evaluation.searchv3.structural;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SearchV3IndexLifecycleTest {

    private static final String SEALED_COMBINED =
            "e5b3159798ed55713c6112d735ee5edb0fb3c6304e87a127e0b9e37a395c7383";
    private static final String SEALED_MANIFEST_SHA =
            "d58165dc8609684e1bdd194457241abb2caeb860269caaba80761675fa7919aa";
    private static final String CURRENT_FRESH_BASELINE = "NOT_RUN";
    private static final long OWNER = 7L;
    private static final String DOCUMENT = "D1";
    private static final Instant INITIAL_LEASE_EXPIRY = Instant.parse("2026-09-01T00:05:00Z");
    private static final Instant RECOVERY_TIME = Instant.parse("2026-09-01T00:06:00Z");
    private static final Instant RECOVERED_LEASE_EXPIRY = Instant.parse("2026-09-01T00:11:00Z");

    @Test
    void exposesOnlyThePointedActiveAndCompletedGeneration() {
        SearchV3IndexLifecycle state = activeState();
        SearchIndexGeneration.Metadata next = metadata("G2", DOCUMENT, "V2", "a");
        state = begin(state, next);

        assertThat(state.searchableGeneration(OWNER, DOCUMENT).metadata().generationId()).isEqualTo("G1");
        assertThat(state.generation("G2").searchable()).isFalse();

        SearchIndexGeneration.Claim claim = state.generation("G2").currentClaim();
        state = state.markReady(claim, inventory(next, manifest(next)));

        assertThat(state.searchableGeneration(OWNER, DOCUMENT).metadata().generationId()).isEqualTo("G1");
        assertThat(state.generation("G2").status()).isEqualTo(SearchIndexGeneration.Status.READY);
        assertThat(state.generation("G2").jobStatus()).isEqualTo(SearchIndexGeneration.JobStatus.PROCESSING);
        assertThat(state.generation("G2").searchable()).isFalse();
    }

    @Test
    void activatesNewVersionGenerationAndJobInOneImmutableCommit() {
        SearchV3IndexLifecycle state = readyState("G2", "V2");
        SearchIndexGeneration.Claim claim = state.generation("G2").currentClaim();
        SearchV3IndexLifecycle.ActivationPlan plan = state.prepareActivation(claim);

        assertThat(state.document().activeDocumentVersionId()).isEqualTo("V1");
        assertThat(state.sourceVersion("V2").status())
                .isEqualTo(SearchV3IndexLifecycle.SourceVersionStatus.PROCESSING);
        assertThat(state.generation("G2").jobStatus()).isEqualTo(SearchIndexGeneration.JobStatus.PROCESSING);

        SearchV3IndexLifecycle committed = plan.commitAgainst(state);

        assertThat(committed.document().activeDocumentVersionId()).isEqualTo("V2");
        assertThat(committed.document().activeGenerationId()).isEqualTo("G2");
        assertThat(committed.sourceVersion("V2").status())
                .isEqualTo(SearchV3IndexLifecycle.SourceVersionStatus.ACTIVE);
        assertThat(committed.generation("G1").status()).isEqualTo(SearchIndexGeneration.Status.SUPERSEDED);
        assertThat(committed.generation("G2").status()).isEqualTo(SearchIndexGeneration.Status.ACTIVE);
        assertThat(committed.generation("G2").jobStatus()).isEqualTo(SearchIndexGeneration.JobStatus.COMPLETED);
        assertThat(committed.searchableGeneration(OWNER, DOCUMENT).metadata().generationId()).isEqualTo("G2");
    }

    @Test
    void reindexesSameActiveDocumentVersionWithoutChangingSourceVersionState() {
        SearchV3IndexLifecycle state = readyState("G2", "V1");
        SearchIndexGeneration.Claim claim = state.generation("G2").currentClaim();
        state = state.prepareActivation(claim).commitAgainst(state);

        assertThat(state.document().activeDocumentVersionId()).isEqualTo("V1");
        assertThat(state.document().activeGenerationId()).isEqualTo("G2");
        assertThat(state.sourceVersion("V1").status())
                .isEqualTo(SearchV3IndexLifecycle.SourceVersionStatus.ACTIVE);
        assertThat(state.generation("G2").jobStatus()).isEqualTo(SearchIndexGeneration.JobStatus.COMPLETED);
    }

    @Test
    void preservesPreviousActiveForEveryFailureStageAndFailsOnlyNewSourceVersion() {
        for (SearchIndexGeneration.FailureStage stage : SearchIndexGeneration.FailureStage.values()) {
            SearchV3IndexLifecycle state = activeState();
            SearchIndexGeneration.Metadata next = metadata("G-" + stage, DOCUMENT, "V2", "a");
            state = begin(state, next);
            SearchIndexGeneration.Claim claim = state.generation(next.generationId()).currentClaim();
            if (stage == SearchIndexGeneration.FailureStage.ACTIVATION) {
                state = state.markReady(claim, inventory(next, manifest(next)));
            }

            state = state.fail(claim, stage);

            assertThat(state.generation(next.generationId()).status())
                    .as(stage.name()).isEqualTo(SearchIndexGeneration.Status.FAILED);
            assertThat(state.generation(next.generationId()).jobStatus())
                    .as(stage.name()).isEqualTo(SearchIndexGeneration.JobStatus.FAILED);
            assertThat(state.sourceVersion("V2").status())
                    .as(stage.name()).isEqualTo(SearchV3IndexLifecycle.SourceVersionStatus.FAILED);
            assertThat(state.searchableGeneration(OWNER, DOCUMENT).metadata().generationId())
                    .as(stage.name()).isEqualTo("G1");
            assertThat(state.sourceVersion("V1").status())
                    .as(stage.name()).isEqualTo(SearchV3IndexLifecycle.SourceVersionStatus.ACTIVE);
        }
    }

    @Test
    void bindsActivationPlanToTheCompleteLifecycleStateNotOnlyRevision() {
        SearchV3IndexLifecycle state = readyState("G2", "V2");
        SearchIndexGeneration.Claim claim = state.generation("G2").currentClaim();
        SearchV3IndexLifecycle.ActivationPlan plan = state.prepareActivation(claim);

        SearchV3IndexLifecycle recoveryLocked = state.lockForRecovery(
                claim, RECOVERY_TIME, "recovery-lock-1");
        assertThatThrownBy(() -> plan.commitAgainst(recoveryLocked))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("stale");

        SearchV3IndexLifecycle sameRevisionOtherDocument = readyState("G2", "V2", 9L, "D2");
        assertThat(sameRevisionOtherDocument.revision()).isEqualTo(state.revision());
        assertThatThrownBy(() -> plan.commitAgainst(sameRevisionOtherDocument))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("another lifecycle");
        assertThat(state.document().activeGenerationId()).isEqualTo("G1");
    }

    @Test
    void requiresExpiredLeaseAndRecoveryLockBeforeReclaimingAndFencesStaleWorker() {
        SearchV3IndexLifecycle state = activeState();
        SearchIndexGeneration.Metadata next = metadata("G2", DOCUMENT, "V2", "a");
        state = begin(state, next);
        SearchIndexGeneration.Claim staleClaim = state.generation("G2").currentClaim();
        SearchV3IndexLifecycle building = state;

        assertThatThrownBy(() -> building.lockForRecovery(
                staleClaim, INITIAL_LEASE_EXPIRY.minusSeconds(1), "recovery-lock-1"))
                .hasMessageContaining("not expired");

        SearchIndexGeneration.RecoveryToken token = new SearchIndexGeneration.RecoveryToken(
                staleClaim, "recovery-lock-1");
        assertThatThrownBy(() -> building.reclaim(token, RECOVERED_LEASE_EXPIRY))
                .hasMessageContaining("current recovery lock");

        SearchV3IndexLifecycle recoveryLocked = building.lockForRecovery(
                staleClaim, RECOVERY_TIME, "recovery-lock-1");
        assertThatThrownBy(() -> recoveryLocked.reclaim(
                new SearchIndexGeneration.RecoveryToken(staleClaim, "wrong-lock"),
                RECOVERED_LEASE_EXPIRY))
                .hasMessageContaining("current recovery lock");
        assertThatThrownBy(() -> recoveryLocked.markReady(staleClaim, inventory(next, manifest(next))))
                .hasMessageContaining("recovery-locked");

        SearchV3IndexLifecycle reclaimed = recoveryLocked.reclaim(token, RECOVERED_LEASE_EXPIRY);

        assertThat(reclaimed.generation("G2").claimVersion()).isEqualTo(2);
        assertThatThrownBy(() -> reclaimed.markReady(staleClaim, inventory(next, manifest(next))))
                .hasMessageContaining("stale");
        assertThatThrownBy(() -> reclaimed.fail(staleClaim, SearchIndexGeneration.FailureStage.STORAGE))
                .hasMessageContaining("stale");
        assertThat(reclaimed.searchableGeneration(OWNER, DOCUMENT).metadata().generationId()).isEqualTo("G1");
    }

    @Test
    void requiresTrustedOwnerDocumentAndVersionLineage() {
        SearchV3IndexLifecycle state = activeState();
        SearchIndexGeneration.Metadata otherOwner = metadata("G2", 8L, DOCUMENT, "V2", "a");
        SearchIndexGeneration.Metadata unknownVersion = metadata("G3", OWNER, DOCUMENT, "V9", "a");

        assertThatThrownBy(() -> begin(state, otherOwner)).hasMessageContaining("owner");
        assertThatThrownBy(() -> begin(state, unknownVersion)).hasMessageContaining("trusted document version");
    }

    @Test
    void rejectsConsistentlyTruncatedInventoryAgainstIndependentFrozenManifest() {
        SearchV3IndexLifecycle state = activeState();
        SearchIndexGeneration.Metadata next = metadata("G2", DOCUMENT, "V2", "a");
        SearchIndexGeneration.ExpectedManifest expected = manifest(next);
        state = state.begin(next, expected, claim(next, 1), INITIAL_LEASE_EXPIRY);
        SearchIndexGeneration.Claim claim = state.generation("G2").currentClaim();

        SearchIndexGeneration.ExpectedManifest truncated = new SearchIndexGeneration.ExpectedManifest(
                next.lineage(), List.of(expected.passages().get(0)), expected.children().subList(0, 2));
        SearchIndexGeneration.PersistedInventory actual = inventory(next, truncated);
        SearchV3IndexLifecycle building = state;

        assertThatThrownBy(() -> building.markReady(claim, actual))
                .hasMessageContaining("frozen manifest");
        assertThat(building.generation("G2").status()).isEqualTo(SearchIndexGeneration.Status.BUILDING);
    }

    @Test
    void rejectsIncompleteStorageAndMissingPassageOrChildVectors() {
        SearchV3IndexLifecycle state = activeState();
        SearchIndexGeneration.Metadata next = metadata("G2", DOCUMENT, "V2", "a");
        SearchIndexGeneration.ExpectedManifest expected = manifest(next);
        state = state.begin(next, expected, claim(next, 1), INITIAL_LEASE_EXPIRY);
        SearchIndexGeneration.Claim claim = state.generation("G2").currentClaim();
        SearchIndexGeneration.PersistedInventory valid = inventory(next, expected);
        SearchV3IndexLifecycle building = state;

        assertThatThrownBy(() -> building.markReady(claim, new SearchIndexGeneration.PersistedInventory(
                valid.lineage(), valid.passages(), valid.children(),
                valid.passageVectors(), valid.childVectors(), false)))
                .hasMessageContaining("storage is incomplete");
        assertThatThrownBy(() -> building.markReady(claim, new SearchIndexGeneration.PersistedInventory(
                valid.lineage(), valid.passages(), valid.children(),
                valid.passageVectors().subList(0, 1), valid.childVectors(), true)))
                .hasMessageContaining("PASSAGE vector inventory");
        assertThatThrownBy(() -> building.markReady(claim, new SearchIndexGeneration.PersistedInventory(
                valid.lineage(), valid.passages(), valid.children(),
                valid.passageVectors(), valid.childVectors().subList(0, 2), true)))
                .hasMessageContaining("CHILD vector inventory");
    }

    @Test
    void rejectsCrossGenerationArtifactAndVectorLineage() {
        SearchV3IndexLifecycle state = activeState();
        SearchIndexGeneration.Metadata next = metadata("G2", DOCUMENT, "V2", "a");
        SearchIndexGeneration.ExpectedManifest expected = manifest(next);
        state = state.begin(next, expected, claim(next, 1), INITIAL_LEASE_EXPIRY);
        SearchIndexGeneration.Claim claim = state.generation("G2").currentClaim();
        SearchIndexGeneration.PersistedInventory valid = inventory(next, expected);
        SearchIndexGeneration.ArtifactLineage wrong = new SearchIndexGeneration.ArtifactLineage(
                "G9", OWNER, DOCUMENT, "V2");

        List<SearchIndexGeneration.PassageArtifact> wrongPassages = new ArrayList<>(valid.passages());
        SearchIndexGeneration.PassageArtifact first = wrongPassages.get(0);
        wrongPassages.set(0, new SearchIndexGeneration.PassageArtifact(
                wrong, first.passageId(), first.retrievalTextSha256(), first.orderedChildIds()));
        SearchIndexGeneration.PersistedInventory wrongArtifact = new SearchIndexGeneration.PersistedInventory(
                valid.lineage(), wrongPassages, valid.children(), valid.passageVectors(), valid.childVectors(), true);

        List<SearchIndexGeneration.VectorRow> wrongVectors = new ArrayList<>(valid.childVectors());
        SearchIndexGeneration.VectorRow row = wrongVectors.get(0);
        wrongVectors.set(0, new SearchIndexGeneration.VectorRow(
                wrong, row.kind(), row.artifactId(), row.inputSha256(), row.contract(), row.vector()));
        SearchIndexGeneration.PersistedInventory wrongVector = new SearchIndexGeneration.PersistedInventory(
                valid.lineage(), valid.passages(), valid.children(), valid.passageVectors(), wrongVectors, true);
        SearchV3IndexLifecycle building = state;

        assertThatThrownBy(() -> building.markReady(claim, wrongArtifact))
                .hasMessageContaining("Passage inventory");
        assertThatThrownBy(() -> building.markReady(claim, wrongVector))
                .hasMessageContaining("lineage");
    }

    @Test
    void rejectsNullWrongDimensionNonFiniteAndZeroVectorsForBothFamilies() {
        for (SearchIndexGeneration.VectorKind kind : SearchIndexGeneration.VectorKind.values()) {
            assertInvalidVector(kind, null, "dimension");
            assertInvalidVector(kind, new float[8], "dimension");
            float[] nonFinite = vector(1.0f, 1024);
            nonFinite[1] = Float.NaN;
            assertInvalidVector(kind, nonFinite, "finite");
            assertInvalidVector(kind, new float[1024], "non-zero");
        }
    }

    @Test
    void rejectsEmbeddingContractInputHashKindAndDuplicateVectorMismatch() {
        SearchV3IndexLifecycle state = activeState();
        SearchIndexGeneration.Metadata next = metadata("G2", DOCUMENT, "V2", "a");
        SearchIndexGeneration.ExpectedManifest expected = manifest(next);
        state = state.begin(next, expected, claim(next, 1), INITIAL_LEASE_EXPIRY);
        SearchIndexGeneration.Claim claim = state.generation("G2").currentClaim();
        SearchIndexGeneration.PersistedInventory valid = inventory(next, expected);
        SearchIndexGeneration.VectorRow first = valid.childVectors().get(0);
        SearchV3IndexLifecycle building = state;

        SearchIndexGeneration.VectorRow wrongContract = new SearchIndexGeneration.VectorRow(
                first.lineage(), first.kind(), first.artifactId(), first.inputSha256(),
                contract("b", 1024, "child-source-v1"), first.vector());
        assertThatThrownBy(() -> building.markReady(claim, replaceChildVector(valid, 0, wrongContract)))
                .hasMessageContaining("contract");

        SearchIndexGeneration.VectorRow wrongHash = new SearchIndexGeneration.VectorRow(
                first.lineage(), first.kind(), first.artifactId(), "f".repeat(64),
                first.contract(), first.vector());
        assertThatThrownBy(() -> building.markReady(claim, replaceChildVector(valid, 0, wrongHash)))
                .hasMessageContaining("input hash");

        SearchIndexGeneration.VectorRow wrongKind = new SearchIndexGeneration.VectorRow(
                first.lineage(), SearchIndexGeneration.VectorKind.PASSAGE, first.artifactId(),
                first.inputSha256(), first.contract(), first.vector());
        assertThatThrownBy(() -> building.markReady(claim, replaceChildVector(valid, 0, wrongKind)))
                .hasMessageContaining("wrong artifact kind");

        assertThatThrownBy(() -> building.markReady(
                claim, replaceChildVector(valid, 1, valid.childVectors().get(0))))
                .hasMessageContaining("duplicate");
    }

    @Test
    void permitsDistinctPassageAndChildInputPoliciesButRejectsDifferentVectorSpaces() {
        SearchIndexGeneration.Metadata metadata = metadata("G2", DOCUMENT, "V2", "a");

        assertThat(metadata.passageEmbeddingContract().inputPolicyVersion()).isEqualTo("passage-source-v1");
        assertThat(metadata.childEmbeddingContract().inputPolicyVersion()).isEqualTo("child-source-v1");
        assertThatThrownBy(() -> new SearchIndexGeneration.Metadata(
                metadata.lineage(), "structure-v1", "passage-v1", "child-v1",
                contract("a", 1024, "passage-source-v1"),
                contract("b", 1024, "child-source-v1"), metadata.createdAt()))
                .hasMessageContaining("share model");
    }

    @Test
    void failedAndSupersededGenerationsAreNeverSearchable() {
        SearchV3IndexLifecycle failed = activeState();
        SearchIndexGeneration.Metadata next = metadata("G2", DOCUMENT, "V2", "a");
        failed = begin(failed, next);
        SearchIndexGeneration.Claim failedClaim = failed.generation("G2").currentClaim();
        failed = failed.fail(failedClaim, SearchIndexGeneration.FailureStage.CHILD_EMBEDDING);

        assertThat(failed.generation("G2").searchable()).isFalse();

        SearchV3IndexLifecycle activated = readyState("G2", "V2");
        SearchIndexGeneration.Claim claim = activated.generation("G2").currentClaim();
        activated = activated.prepareActivation(claim).commitAgainst(activated);
        assertThat(activated.generation("G1").status()).isEqualTo(SearchIndexGeneration.Status.SUPERSEDED);
        assertThat(activated.generation("G1").searchable()).isFalse();
    }

    @Test
    void sealedFinalManifestRemainsFrozenWithoutOpeningDataset() throws IOException, NoSuchAlgorithmException {
        Path manifest = Path.of("src/test/resources/search-v3-evaluation/sealed-final/manifest.json");
        byte[] bytes = Files.readAllBytes(manifest);
        JsonNode json = new ObjectMapper().readTree(bytes);

        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))
                .isEqualTo(SEALED_MANIFEST_SHA);
        assertThat(json.path("combinedSha256").asText()).isEqualTo(SEALED_COMBINED);
        assertThat(json.path("opened").asBoolean(true)).isFalse();
        assertThat(json.path("searchExecuted").asBoolean(true)).isFalse();
        assertThat(CURRENT_FRESH_BASELINE).isEqualTo("NOT_RUN");
    }

    private void assertInvalidVector(SearchIndexGeneration.VectorKind kind, float[] vector, String message) {
        SearchV3IndexLifecycle state = activeState();
        SearchIndexGeneration.Metadata next = metadata("G2", DOCUMENT, "V2", "a");
        SearchIndexGeneration.ExpectedManifest expected = manifest(next);
        state = state.begin(next, expected, claim(next, 1), INITIAL_LEASE_EXPIRY);
        SearchIndexGeneration.Claim claim = state.generation("G2").currentClaim();
        SearchIndexGeneration.PersistedInventory valid = inventory(next, expected);
        List<SearchIndexGeneration.VectorRow> rows = kind == SearchIndexGeneration.VectorKind.PASSAGE
                ? valid.passageVectors() : valid.childVectors();
        SearchIndexGeneration.VectorRow first = rows.get(0);
        SearchIndexGeneration.VectorRow invalid = new SearchIndexGeneration.VectorRow(
                first.lineage(), first.kind(), first.artifactId(), first.inputSha256(), first.contract(), vector);
        SearchIndexGeneration.PersistedInventory actual = kind == SearchIndexGeneration.VectorKind.PASSAGE
                ? replacePassageVector(valid, 0, invalid)
                : replaceChildVector(valid, 0, invalid);
        SearchV3IndexLifecycle building = state;

        assertThatThrownBy(() -> building.markReady(claim, actual)).hasMessageContaining(message);
    }

    private SearchV3IndexLifecycle activeState() {
        return activeState(OWNER, DOCUMENT);
    }

    private SearchV3IndexLifecycle activeState(long owner, String document) {
        SearchIndexGeneration.Metadata current = metadata("G1", owner, document, "V1", "a");
        SearchIndexGeneration.ExpectedManifest expected = manifest(current);
        SearchIndexGeneration active = SearchIndexGeneration.active(
                current, expected, claim(current, 1), inventory(current, expected));
        return SearchV3IndexLifecycle.withActiveGeneration(
                new SearchV3IndexLifecycle.DocumentSlot(owner, document, "V1", "G1"),
                List.of(
                        version(owner, document, "V1", SearchV3IndexLifecycle.SourceVersionStatus.ACTIVE),
                        version(owner, document, "V2", SearchV3IndexLifecycle.SourceVersionStatus.PROCESSING)),
                active);
    }

    private SearchV3IndexLifecycle readyState(String generationId, String versionId) {
        return readyState(generationId, versionId, OWNER, DOCUMENT);
    }

    private SearchV3IndexLifecycle readyState(
            String generationId, String versionId, long owner, String document) {
        SearchV3IndexLifecycle state = activeState(owner, document);
        SearchIndexGeneration.Metadata next = metadata(generationId, owner, document, versionId, "a");
        SearchIndexGeneration.ExpectedManifest expected = manifest(next);
        state = state.begin(next, expected, claim(next, 1), INITIAL_LEASE_EXPIRY);
        SearchIndexGeneration.Claim claim = state.generation(generationId).currentClaim();
        return state.markReady(claim, inventory(next, expected));
    }

    private SearchV3IndexLifecycle begin(
            SearchV3IndexLifecycle state,
            SearchIndexGeneration.Metadata metadata) {
        return state.begin(metadata, manifest(metadata), claim(metadata, 1), INITIAL_LEASE_EXPIRY);
    }

    private SearchIndexGeneration.Metadata metadata(
            String generationId,
            String documentId,
            String documentVersionId,
            String digestCharacter) {
        return metadata(generationId, OWNER, documentId, documentVersionId, digestCharacter);
    }

    private SearchIndexGeneration.Metadata metadata(
            String generationId,
            long owner,
            String documentId,
            String documentVersionId,
            String digestCharacter) {
        SearchIndexGeneration.ArtifactLineage lineage = new SearchIndexGeneration.ArtifactLineage(
                generationId, owner, documentId, documentVersionId);
        return new SearchIndexGeneration.Metadata(
                lineage,
                "structure-v1",
                "passage-v1",
                "child-v1",
                contract(digestCharacter, 1024, "passage-source-v1"),
                contract(digestCharacter, 1024, "child-source-v1"),
                Instant.parse("2026-09-01T00:00:00Z"));
    }

    private SearchIndexGeneration.EmbeddingContract contract(
            String digestCharacter, int dimension, String inputPolicy) {
        return new SearchIndexGeneration.EmbeddingContract(
                "bge-m3", digestCharacter.repeat(64), dimension, inputPolicy);
    }

    private SearchIndexGeneration.Claim claim(SearchIndexGeneration.Metadata metadata, long claimVersion) {
        return new SearchIndexGeneration.Claim(
                metadata.generationId(), metadata.ownerUserId(), metadata.documentId(),
                metadata.documentVersionId(), claimVersion);
    }

    private SearchIndexGeneration.ExpectedManifest manifest(SearchIndexGeneration.Metadata metadata) {
        SearchIndexGeneration.ArtifactLineage lineage = metadata.lineage();
        String prefix = metadata.generationId();
        String p1 = prefix + "-P1";
        String p2 = prefix + "-P2";
        List<SearchIndexGeneration.PassageArtifact> passages = List.of(
                new SearchIndexGeneration.PassageArtifact(
                        lineage, p1, "4".repeat(64), List.of(prefix + "-C1", prefix + "-C2")),
                new SearchIndexGeneration.PassageArtifact(
                        lineage, p2, "5".repeat(64), List.of(prefix + "-C3")));
        List<SearchIndexGeneration.ChildArtifact> children = List.of(
                child(metadata, prefix + "-C1", p1, "1".repeat(64), 0),
                child(metadata, prefix + "-C2", p1, "2".repeat(64), 11),
                child(metadata, prefix + "-C3", p2, "3".repeat(64), 22));
        return new SearchIndexGeneration.ExpectedManifest(lineage, passages, children);
    }

    private SearchIndexGeneration.ChildArtifact child(
            SearchIndexGeneration.Metadata metadata,
            String childId,
            String passageId,
            String exactHash,
            int start) {
        return new SearchIndexGeneration.ChildArtifact(
                metadata.lineage(), childId, passageId, exactHash,
                new SourceProvenance(
                        metadata.documentId(), metadata.documentVersionId(), "fixture.txt", 1,
                        start / 11 + 1, start / 11 + 1, start, start + 10,
                        childId + "-SB", passageId, "d".repeat(64), exactHash));
    }

    private SearchIndexGeneration.PersistedInventory inventory(
            SearchIndexGeneration.Metadata metadata,
            SearchIndexGeneration.ExpectedManifest expected) {
        List<SearchIndexGeneration.VectorRow> passageVectors = expected.passages().stream()
                .map(passage -> new SearchIndexGeneration.VectorRow(
                        expected.lineage(), SearchIndexGeneration.VectorKind.PASSAGE,
                        passage.passageId(), passage.retrievalTextSha256(),
                        metadata.passageEmbeddingContract(), vector(1.0f, 1024)))
                .toList();
        List<SearchIndexGeneration.VectorRow> childVectors = expected.children().stream()
                .map(child -> new SearchIndexGeneration.VectorRow(
                        expected.lineage(), SearchIndexGeneration.VectorKind.CHILD,
                        child.childId(), child.sourceTextSha256(),
                        metadata.childEmbeddingContract(), vector(2.0f, 1024)))
                .toList();
        return new SearchIndexGeneration.PersistedInventory(
                expected.lineage(), expected.passages(), expected.children(),
                passageVectors, childVectors, true);
    }

    private SearchIndexGeneration.PersistedInventory replacePassageVector(
            SearchIndexGeneration.PersistedInventory inventory,
            int index,
            SearchIndexGeneration.VectorRow replacement) {
        List<SearchIndexGeneration.VectorRow> rows = new ArrayList<>(inventory.passageVectors());
        rows.set(index, replacement);
        return new SearchIndexGeneration.PersistedInventory(
                inventory.lineage(), inventory.passages(), inventory.children(),
                rows, inventory.childVectors(), inventory.storageComplete());
    }

    private SearchIndexGeneration.PersistedInventory replaceChildVector(
            SearchIndexGeneration.PersistedInventory inventory,
            int index,
            SearchIndexGeneration.VectorRow replacement) {
        List<SearchIndexGeneration.VectorRow> rows = new ArrayList<>(inventory.childVectors());
        rows.set(index, replacement);
        return new SearchIndexGeneration.PersistedInventory(
                inventory.lineage(), inventory.passages(), inventory.children(),
                inventory.passageVectors(), rows, inventory.storageComplete());
    }

    private float[] vector(float firstValue, int dimension) {
        float[] vector = new float[dimension];
        vector[0] = firstValue;
        return vector;
    }

    private SearchV3IndexLifecycle.DocumentVersionRef version(
            long owner,
            String document,
            String version,
            SearchV3IndexLifecycle.SourceVersionStatus status) {
        return new SearchV3IndexLifecycle.DocumentVersionRef(owner, document, version, status);
    }
}
