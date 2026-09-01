package com.prizm.search.evaluation.searchv3.structural;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/** Evaluation-only immutable Search V3 generation aggregate. */
final class SearchIndexGeneration {

    private static final Pattern SHA256 = Pattern.compile("^[0-9a-f]{64}$");

    enum Status {
        BUILDING,
        READY,
        ACTIVE,
        FAILED,
        SUPERSEDED
    }

    enum JobStatus {
        PROCESSING,
        COMPLETED,
        FAILED
    }

    enum FailureStage {
        PASSAGE_GENERATION,
        PASSAGE_EMBEDDING,
        CHILD_GENERATION,
        CHILD_EMBEDDING,
        STORAGE,
        ACTIVATION
    }

    enum VectorKind {
        PASSAGE,
        CHILD
    }

    record EmbeddingContract(
            String modelId,
            String resolvedModelDigest,
            int dimension,
            String inputPolicyVersion) {

        EmbeddingContract {
            requireText(modelId, "modelId");
            requireSha256(resolvedModelDigest, "resolvedModelDigest");
            requireText(inputPolicyVersion, "inputPolicyVersion");
            if (dimension < 1) {
                throw new IllegalArgumentException("dimension must be positive");
            }
        }

        boolean sameVectorSpaceAs(EmbeddingContract other) {
            return other != null
                    && modelId.equals(other.modelId)
                    && resolvedModelDigest.equals(other.resolvedModelDigest)
                    && dimension == other.dimension;
        }
    }

    record ArtifactLineage(
            String generationId,
            long ownerUserId,
            String documentId,
            String documentVersionId) {

        ArtifactLineage {
            requireText(generationId, "generationId");
            requireText(documentId, "documentId");
            requireText(documentVersionId, "documentVersionId");
            if (ownerUserId < 1) {
                throw new IllegalArgumentException("ownerUserId must be positive");
            }
        }
    }

    record Metadata(
            ArtifactLineage lineage,
            String structurePolicyVersion,
            String passagePolicyVersion,
            String childPolicyVersion,
            EmbeddingContract passageEmbeddingContract,
            EmbeddingContract childEmbeddingContract,
            Instant createdAt) {

        Metadata {
            Objects.requireNonNull(lineage, "lineage");
            requireText(structurePolicyVersion, "structurePolicyVersion");
            requireText(passagePolicyVersion, "passagePolicyVersion");
            requireText(childPolicyVersion, "childPolicyVersion");
            Objects.requireNonNull(passageEmbeddingContract, "passageEmbeddingContract");
            Objects.requireNonNull(childEmbeddingContract, "childEmbeddingContract");
            Objects.requireNonNull(createdAt, "createdAt");
            if (!passageEmbeddingContract.sameVectorSpaceAs(childEmbeddingContract)) {
                throw new IllegalArgumentException(
                        "Passage and Child embeddings must share model, digest, and dimension");
            }
        }

        String generationId() {
            return lineage.generationId();
        }

        long ownerUserId() {
            return lineage.ownerUserId();
        }

        String documentId() {
            return lineage.documentId();
        }

        String documentVersionId() {
            return lineage.documentVersionId();
        }
    }

    record Claim(
            String generationId,
            long ownerUserId,
            String documentId,
            String documentVersionId,
            long claimVersion) {

        Claim {
            requireText(generationId, "generationId");
            requireText(documentId, "documentId");
            requireText(documentVersionId, "documentVersionId");
            if (ownerUserId < 1 || claimVersion < 1) {
                throw new IllegalArgumentException("owner and claim version must be positive");
            }
        }

        ArtifactLineage lineage() {
            return new ArtifactLineage(generationId, ownerUserId, documentId, documentVersionId);
        }
    }

    record RecoveryLock(Claim expiredClaim, Instant acquiredAt, String recoveryLockToken) {

        RecoveryLock {
            Objects.requireNonNull(expiredClaim, "expiredClaim");
            Objects.requireNonNull(acquiredAt, "acquiredAt");
            requireText(recoveryLockToken, "recoveryLockToken");
        }
    }

    record RecoveryToken(Claim expiredClaim, String recoveryLockToken) {

        RecoveryToken {
            Objects.requireNonNull(expiredClaim, "expiredClaim");
            requireText(recoveryLockToken, "recoveryLockToken");
        }
    }

    record PassageArtifact(
            ArtifactLineage lineage,
            String passageId,
            String retrievalTextSha256,
            List<String> orderedChildIds) {

        PassageArtifact {
            Objects.requireNonNull(lineage, "lineage");
            requireText(passageId, "passageId");
            requireSha256(retrievalTextSha256, "retrievalTextSha256");
            orderedChildIds = List.copyOf(Objects.requireNonNull(orderedChildIds, "orderedChildIds"));
            requireUniqueNonBlank(orderedChildIds, "Passage Child ID");
            if (orderedChildIds.isEmpty()) {
                throw new IllegalArgumentException("Passage must contain at least one Child");
            }
        }
    }

    record ChildArtifact(
            ArtifactLineage lineage,
            String childId,
            String passageId,
            String sourceTextSha256,
            SourceProvenance provenance) {

        ChildArtifact {
            Objects.requireNonNull(lineage, "lineage");
            requireText(childId, "childId");
            requireText(passageId, "passageId");
            requireSha256(sourceTextSha256, "sourceTextSha256");
            Objects.requireNonNull(provenance, "provenance");
            if (!sourceTextSha256.equals(provenance.exactTextSha256())) {
                throw new IllegalArgumentException("Child source hash must match its provenance");
            }
        }
    }

    /** Frozen independently before any persisted inventory is accepted. */
    record ExpectedManifest(
            ArtifactLineage lineage,
            List<PassageArtifact> passages,
            List<ChildArtifact> children) {

        ExpectedManifest {
            Objects.requireNonNull(lineage, "lineage");
            passages = List.copyOf(Objects.requireNonNull(passages, "passages"));
            children = List.copyOf(Objects.requireNonNull(children, "children"));
            if (passages.isEmpty() || children.isEmpty()) {
                throw new IllegalArgumentException("manifest must contain Passage and Child artifacts");
            }
            requireUniqueNonBlank(passages.stream().map(PassageArtifact::passageId).toList(), "Passage ID");
            requireUniqueNonBlank(children.stream().map(ChildArtifact::childId).toList(), "Child ID");
            if (passages.stream().anyMatch(passage -> !lineage.equals(passage.lineage()))
                    || children.stream().anyMatch(child -> !lineage.equals(child.lineage()))) {
                throw new IllegalArgumentException("manifest artifact lineage does not match generation");
            }

            Map<String, String> passageByChild = new LinkedHashMap<>();
            List<String> flattenedChildIds = new ArrayList<>();
            for (PassageArtifact passage : passages) {
                for (String childId : passage.orderedChildIds()) {
                    if (passageByChild.putIfAbsent(childId, passage.passageId()) != null) {
                        throw new IllegalArgumentException("Child cannot belong to multiple Passages");
                    }
                    flattenedChildIds.add(childId);
                }
            }
            if (!flattenedChildIds.equals(children.stream().map(ChildArtifact::childId).toList())) {
                throw new IllegalArgumentException("manifest Child order must match Passage membership order");
            }
            for (ChildArtifact child : children) {
                if (!child.passageId().equals(passageByChild.get(child.childId()))) {
                    throw new IllegalArgumentException("Child Passage membership is inconsistent");
                }
                if (!lineage.documentId().equals(child.provenance().documentId())
                        || !lineage.documentVersionId().equals(child.provenance().versionId())) {
                    throw new IllegalArgumentException("Child provenance escapes its document version");
                }
            }
        }
    }

    record VectorRow(
            ArtifactLineage lineage,
            VectorKind kind,
            String artifactId,
            String inputSha256,
            EmbeddingContract contract,
            float[] vector) {

        VectorRow {
            Objects.requireNonNull(lineage, "lineage");
            Objects.requireNonNull(kind, "kind");
            requireText(artifactId, "artifactId");
            requireSha256(inputSha256, "inputSha256");
            Objects.requireNonNull(contract, "contract");
            vector = vector == null ? null : vector.clone();
        }

        @Override
        public float[] vector() {
            return vector == null ? null : vector.clone();
        }
    }

    record PersistedInventory(
            ArtifactLineage lineage,
            List<PassageArtifact> passages,
            List<ChildArtifact> children,
            List<VectorRow> passageVectors,
            List<VectorRow> childVectors,
            boolean storageComplete) {

        PersistedInventory {
            Objects.requireNonNull(lineage, "lineage");
            passages = List.copyOf(Objects.requireNonNull(passages, "passages"));
            children = List.copyOf(Objects.requireNonNull(children, "children"));
            passageVectors = List.copyOf(Objects.requireNonNull(passageVectors, "passageVectors"));
            childVectors = List.copyOf(Objects.requireNonNull(childVectors, "childVectors"));
        }
    }

    record StateToken(
            Metadata metadata,
            ExpectedManifest expectedManifest,
            Status status,
            JobStatus jobStatus,
            long claimVersion,
            Instant leaseExpiresAt,
            RecoveryLock recoveryLock,
            String inventorySha256,
            FailureStage failureStage) {}

    private final Metadata metadata;
    private final ExpectedManifest expectedManifest;
    private final Status status;
    private final JobStatus jobStatus;
    private final long claimVersion;
    private final Instant leaseExpiresAt;
    private final RecoveryLock recoveryLock;
    private final PersistedInventory inventory;
    private final FailureStage failureStage;

    private SearchIndexGeneration(
            Metadata metadata,
            ExpectedManifest expectedManifest,
            Status status,
            JobStatus jobStatus,
            long claimVersion,
            Instant leaseExpiresAt,
            RecoveryLock recoveryLock,
            PersistedInventory inventory,
            FailureStage failureStage) {
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.expectedManifest = Objects.requireNonNull(expectedManifest, "expectedManifest");
        this.status = Objects.requireNonNull(status, "status");
        this.jobStatus = Objects.requireNonNull(jobStatus, "jobStatus");
        if (claimVersion < 1) {
            throw new IllegalArgumentException("claimVersion must be positive");
        }
        this.claimVersion = claimVersion;
        this.leaseExpiresAt = leaseExpiresAt;
        this.recoveryLock = recoveryLock;
        this.inventory = inventory;
        this.failureStage = failureStage;
        if (!metadata.lineage().equals(expectedManifest.lineage())) {
            throw new IllegalArgumentException("metadata and expected manifest lineage must match");
        }
        if (jobStatus == JobStatus.PROCESSING && leaseExpiresAt == null) {
            throw new IllegalArgumentException("PROCESSING generation job requires a lease expiry");
        }
        if (jobStatus != JobStatus.PROCESSING && (leaseExpiresAt != null || recoveryLock != null)) {
            throw new IllegalArgumentException("completed or failed generation job cannot retain lease state");
        }
        if (recoveryLock != null
                && (!metadata.lineage().equals(recoveryLock.expiredClaim().lineage())
                        || claimVersion != recoveryLock.expiredClaim().claimVersion())) {
            throw new IllegalArgumentException("recovery lock must fence the current generation claim");
        }
    }

    static SearchIndexGeneration building(
            Metadata metadata,
            ExpectedManifest expectedManifest,
            Claim claim,
            Instant leaseExpiresAt) {
        requireClaimLineage(metadata, claim);
        Objects.requireNonNull(leaseExpiresAt, "leaseExpiresAt");
        return new SearchIndexGeneration(
                metadata, expectedManifest, Status.BUILDING, JobStatus.PROCESSING,
                claim.claimVersion(), leaseExpiresAt, null, null, null);
    }

    static SearchIndexGeneration active(
            Metadata metadata,
            ExpectedManifest expectedManifest,
            Claim claim,
            PersistedInventory inventory) {
        requireClaimLineage(metadata, claim);
        requireComplete(metadata, expectedManifest, inventory);
        return new SearchIndexGeneration(
                metadata, expectedManifest, Status.ACTIVE, JobStatus.COMPLETED,
                claim.claimVersion(), null, null, inventory, null);
    }

    SearchIndexGeneration lockForRecovery(
            Claim claim,
            Instant observedAt,
            String recoveryLockToken) {
        Objects.requireNonNull(observedAt, "observedAt");
        requireText(recoveryLockToken, "recoveryLockToken");
        requireClaim(claim);
        requireRecoverableStatus();
        if (observedAt.isBefore(leaseExpiresAt)) {
            throw new IllegalStateException("generation lease has not expired");
        }
        if (recoveryLock != null) {
            throw new IllegalStateException("generation already has a recovery lock");
        }
        return new SearchIndexGeneration(
                metadata, expectedManifest, status, jobStatus, claimVersion,
                leaseExpiresAt, new RecoveryLock(claim, observedAt, recoveryLockToken),
                inventory, failureStage);
    }

    SearchIndexGeneration reclaim(RecoveryToken recovery, Instant newLeaseExpiresAt) {
        Objects.requireNonNull(recovery, "recovery");
        requireClaim(recovery.expiredClaim());
        requireRecoverableStatus();
        if (recoveryLock == null
                || !recoveryLock.expiredClaim().equals(recovery.expiredClaim())
                || !recoveryLock.recoveryLockToken().equals(recovery.recoveryLockToken())) {
            throw new IllegalStateException("reclaim requires the current recovery lock");
        }
        Objects.requireNonNull(newLeaseExpiresAt, "newLeaseExpiresAt");
        if (!newLeaseExpiresAt.isAfter(recoveryLock.acquiredAt())) {
            throw new IllegalArgumentException("new lease must expire after recovery lock acquisition");
        }
        return new SearchIndexGeneration(
                metadata, expectedManifest, status, jobStatus, claimVersion + 1,
                newLeaseExpiresAt, null, inventory, failureStage);
    }

    SearchIndexGeneration markReady(Claim claim, PersistedInventory completedInventory) {
        requireStatus(Status.BUILDING);
        requireJobStatus(JobStatus.PROCESSING);
        requireClaim(claim);
        requireNoRecoveryLock();
        requireComplete(metadata, expectedManifest, completedInventory);
        return new SearchIndexGeneration(
                metadata, expectedManifest, Status.READY, jobStatus,
                claimVersion, leaseExpiresAt, null, completedInventory, null);
    }

    SearchIndexGeneration fail(Claim claim, FailureStage stage) {
        if (status != Status.BUILDING && status != Status.READY) {
            throw new IllegalStateException("only BUILDING or READY generation can fail");
        }
        requireJobStatus(JobStatus.PROCESSING);
        requireClaim(claim);
        requireNoRecoveryLock();
        return new SearchIndexGeneration(
                metadata, expectedManifest, Status.FAILED, JobStatus.FAILED,
                claimVersion, null, null, inventory, Objects.requireNonNull(stage, "stage"));
    }

    void requireActivatable(Claim claim) {
        requireStatus(Status.READY);
        requireJobStatus(JobStatus.PROCESSING);
        requireClaim(claim);
        requireNoRecoveryLock();
        requireComplete(metadata, expectedManifest, inventory);
    }

    SearchIndexGeneration activate(Claim claim) {
        requireActivatable(claim);
        return new SearchIndexGeneration(
                metadata, expectedManifest, Status.ACTIVE, JobStatus.COMPLETED,
                claimVersion, null, null, inventory, null);
    }

    SearchIndexGeneration supersede() {
        requireStatus(Status.ACTIVE);
        requireJobStatus(JobStatus.COMPLETED);
        return new SearchIndexGeneration(
                metadata, expectedManifest, Status.SUPERSEDED, jobStatus,
                claimVersion, null, null, inventory, null);
    }

    Claim currentClaim() {
        return new Claim(
                metadata.generationId(), metadata.ownerUserId(), metadata.documentId(),
                metadata.documentVersionId(), claimVersion);
    }

    boolean searchable() {
        return status == Status.ACTIVE && jobStatus == JobStatus.COMPLETED;
    }

    StateToken stateToken() {
        return new StateToken(
                metadata, expectedManifest, status, jobStatus, claimVersion,
                leaseExpiresAt, recoveryLock,
                inventory == null ? null : inventoryFingerprint(inventory), failureStage);
    }

    private void requireClaim(Claim claim) {
        Objects.requireNonNull(claim, "claim");
        if (!metadata.lineage().equals(claim.lineage()) || claimVersion != claim.claimVersion()) {
            throw new IllegalStateException("stale or mismatched Search V3 generation claim");
        }
    }

    private static void requireClaimLineage(Metadata metadata, Claim claim) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(claim, "claim");
        if (!metadata.lineage().equals(claim.lineage())) {
            throw new IllegalArgumentException("generation claim lineage does not match metadata");
        }
    }

    private static void requireComplete(
            Metadata metadata,
            ExpectedManifest expected,
            PersistedInventory actual) {
        Objects.requireNonNull(actual, "inventory");
        if (!actual.storageComplete()) {
            throw new IllegalStateException("generation storage is incomplete");
        }
        if (!metadata.lineage().equals(expected.lineage())
                || !expected.lineage().equals(actual.lineage())) {
            throw new IllegalStateException("inventory lineage does not match frozen generation lineage");
        }
        if (!expected.passages().equals(actual.passages())) {
            throw new IllegalStateException("persisted Passage inventory differs from frozen manifest");
        }
        if (!expected.children().equals(actual.children())) {
            throw new IllegalStateException("persisted Child inventory differs from frozen manifest");
        }
        requireVectors(
                expected.lineage(), VectorKind.PASSAGE, expected.passages().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                PassageArtifact::passageId,
                                PassageArtifact::retrievalTextSha256,
                                (left, right) -> left,
                                LinkedHashMap::new)),
                metadata.passageEmbeddingContract(), actual.passageVectors());
        requireVectors(
                expected.lineage(), VectorKind.CHILD, expected.children().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                ChildArtifact::childId,
                                ChildArtifact::sourceTextSha256,
                                (left, right) -> left,
                                LinkedHashMap::new)),
                metadata.childEmbeddingContract(), actual.childVectors());
    }

    private static void requireVectors(
            ArtifactLineage lineage,
            VectorKind kind,
            Map<String, String> expectedInputHashes,
            EmbeddingContract contract,
            List<VectorRow> rows) {
        if (rows.size() != expectedInputHashes.size()) {
            throw new IllegalStateException(kind + " vector inventory is incomplete or contains extras");
        }
        Set<String> seen = new HashSet<>();
        for (VectorRow row : rows) {
            if (!lineage.equals(row.lineage())) {
                throw new IllegalStateException(kind + " vector lineage escapes the generation");
            }
            if (row.kind() != kind) {
                throw new IllegalStateException(kind + " vector has the wrong artifact kind");
            }
            if (!contract.equals(row.contract())) {
                throw new IllegalStateException(kind + " vector embedding contract mismatch");
            }
            String expectedHash = expectedInputHashes.get(row.artifactId());
            if (expectedHash == null || !expectedHash.equals(row.inputSha256())) {
                throw new IllegalStateException(kind + " vector input hash mismatch or unknown artifact");
            }
            if (!seen.add(row.artifactId())) {
                throw new IllegalStateException("duplicate " + kind + " vector artifact ID");
            }
            requireValidVector(row.vector(), contract.dimension(), kind);
        }
        if (!seen.equals(expectedInputHashes.keySet())) {
            throw new IllegalStateException(kind + " vector inventory differs from frozen manifest");
        }
    }

    private static void requireValidVector(float[] vector, int dimension, VectorKind kind) {
        if (vector == null || vector.length != dimension) {
            throw new IllegalStateException(kind + " vector dimension is invalid");
        }
        boolean nonZero = false;
        for (float value : vector) {
            if (!Float.isFinite(value)) {
                throw new IllegalStateException(kind + " vector must contain only finite values");
            }
            nonZero |= value != 0.0f;
        }
        if (!nonZero) {
            throw new IllegalStateException(kind + " vector norm must be non-zero");
        }
    }

    private static String inventoryFingerprint(PersistedInventory inventory) {
        StringBuilder value = new StringBuilder()
                .append(inventory.lineage()).append('|')
                .append(inventory.passages()).append('|')
                .append(inventory.children()).append('|')
                .append(inventory.storageComplete());
        appendVectors(value, inventory.passageVectors());
        appendVectors(value, inventory.childVectors());
        return sha256(value.toString());
    }

    private static void appendVectors(StringBuilder value, List<VectorRow> rows) {
        for (VectorRow row : rows) {
            value.append('|').append(row.lineage()).append('|').append(row.kind())
                    .append('|').append(row.artifactId()).append('|').append(row.inputSha256())
                    .append('|').append(row.contract());
            float[] vector = row.vector();
            if (vector == null) {
                value.append("|null");
                continue;
            }
            for (float element : vector) {
                value.append('|').append(Float.floatToIntBits(element));
            }
        }
    }

    private void requireStatus(Status expected) {
        if (status != expected) {
            throw new IllegalStateException("generation must be %s but was %s".formatted(expected, status));
        }
    }

    private void requireJobStatus(JobStatus expected) {
        if (jobStatus != expected) {
            throw new IllegalStateException(
                    "generation job must be %s but was %s".formatted(expected, jobStatus));
        }
    }

    private void requireRecoverableStatus() {
        requireJobStatus(JobStatus.PROCESSING);
        if (status != Status.BUILDING && status != Status.READY) {
            throw new IllegalStateException("only BUILDING or READY generation can be recovered");
        }
    }

    private void requireNoRecoveryLock() {
        if (recoveryLock != null) {
            throw new IllegalStateException("recovery-locked generation rejects stale worker completion");
        }
    }

    Metadata metadata() {
        return metadata;
    }

    ExpectedManifest expectedManifest() {
        return expectedManifest;
    }

    Status status() {
        return status;
    }

    JobStatus jobStatus() {
        return jobStatus;
    }

    long claimVersion() {
        return claimVersion;
    }

    PersistedInventory inventory() {
        return inventory;
    }

    FailureStage failureStage() {
        return failureStage;
    }

    private static void requireUniqueNonBlank(List<String> values, String label) {
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        if (new HashSet<>(values).size() != values.size()) {
            throw new IllegalArgumentException("duplicate " + label);
        }
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requireSha256(String value, String field) {
        if (value == null || !SHA256.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a lowercase SHA-256");
        }
    }
}
