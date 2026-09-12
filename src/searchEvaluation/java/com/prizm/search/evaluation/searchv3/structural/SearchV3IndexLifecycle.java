package com.prizm.search.evaluation.searchv3.structural;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Evaluation-only document-version pointer and atomic Search V3 generation transition model. */
final class SearchV3IndexLifecycle {

    enum SourceVersionStatus {
        QUARANTINED,
        PROCESSING,
        ACTIVE,
        FAILED
    }

    record DocumentVersionRef(
            long ownerUserId,
            String documentId,
            String documentVersionId,
            SourceVersionStatus status) {

        DocumentVersionRef {
            if (ownerUserId < 1) {
                throw new IllegalArgumentException("ownerUserId must be positive");
            }
            requireText(documentId, "documentId");
            requireText(documentVersionId, "documentVersionId");
            Objects.requireNonNull(status, "status");
        }

        DocumentVersionRef activate() {
            if (status != SourceVersionStatus.PROCESSING) {
                throw new IllegalStateException("only PROCESSING source version can become ACTIVE");
            }
            return new DocumentVersionRef(ownerUserId, documentId, documentVersionId, SourceVersionStatus.ACTIVE);
        }

        DocumentVersionRef fail() {
            if (status != SourceVersionStatus.PROCESSING) {
                throw new IllegalStateException("only PROCESSING source version can fail");
            }
            return new DocumentVersionRef(ownerUserId, documentId, documentVersionId, SourceVersionStatus.FAILED);
        }
    }

    record DocumentSlot(
            long ownerUserId,
            String documentId,
            String activeDocumentVersionId,
            String activeGenerationId) {

        DocumentSlot {
            if (ownerUserId < 1) {
                throw new IllegalArgumentException("ownerUserId must be positive");
            }
            requireText(documentId, "documentId");
            requireText(activeDocumentVersionId, "activeDocumentVersionId");
            if (activeGenerationId != null && activeGenerationId.isBlank()) {
                throw new IllegalArgumentException("activeGenerationId must be null or non-blank");
            }
        }

        DocumentSlot activate(String documentVersionId, String generationId) {
            requireText(documentVersionId, "documentVersionId");
            requireText(generationId, "generationId");
            return new DocumentSlot(ownerUserId, documentId, documentVersionId, generationId);
        }
    }

    record StateToken(
            DocumentSlot document,
            Map<String, DocumentVersionRef> sourceVersions,
            Map<String, SearchIndexGeneration.StateToken> generations,
            long revision) {

        StateToken {
            Objects.requireNonNull(document, "document");
            sourceVersions = Map.copyOf(Objects.requireNonNull(sourceVersions, "sourceVersions"));
            generations = Map.copyOf(Objects.requireNonNull(generations, "generations"));
        }
    }

    record ActivationPlan(
            StateToken expectedState,
            SearchIndexGeneration.Claim candidateClaim,
            String previousGenerationId,
            String nextGenerationId) {

        ActivationPlan {
            Objects.requireNonNull(expectedState, "expectedState");
            Objects.requireNonNull(candidateClaim, "candidateClaim");
            requireText(nextGenerationId, "nextGenerationId");
        }

        SearchV3IndexLifecycle commitAgainst(SearchV3IndexLifecycle currentState) {
            Objects.requireNonNull(currentState, "currentState");
            if (!expectedState.equals(currentState.stateToken())) {
                throw new IllegalStateException("activation plan is stale or bound to another lifecycle state");
            }
            return currentState.applyActivation(candidateClaim);
        }
    }

    private final DocumentSlot document;
    private final Map<String, DocumentVersionRef> sourceVersions;
    private final Map<String, SearchIndexGeneration> generations;
    private final long revision;

    private SearchV3IndexLifecycle(
            DocumentSlot document,
            Map<String, DocumentVersionRef> sourceVersions,
            Map<String, SearchIndexGeneration> generations,
            long revision) {
        this.document = Objects.requireNonNull(document, "document");
        this.sourceVersions = Map.copyOf(Objects.requireNonNull(sourceVersions, "sourceVersions"));
        this.generations = Map.copyOf(Objects.requireNonNull(generations, "generations"));
        if (revision < 0) {
            throw new IllegalArgumentException("revision must not be negative");
        }
        this.revision = revision;
        validateTrustedVersions();
        validateActivePointer();
    }

    static SearchV3IndexLifecycle withoutV3Generation(
            DocumentSlot document,
            List<DocumentVersionRef> sourceVersions) {
        return new SearchV3IndexLifecycle(document, indexVersions(sourceVersions), Map.of(), 0);
    }

    static SearchV3IndexLifecycle withActiveGeneration(
            DocumentSlot document,
            List<DocumentVersionRef> sourceVersions,
            SearchIndexGeneration activeGeneration) {
        Objects.requireNonNull(activeGeneration, "activeGeneration");
        if (activeGeneration.status() != SearchIndexGeneration.Status.ACTIVE
                || activeGeneration.jobStatus() != SearchIndexGeneration.JobStatus.COMPLETED) {
            throw new IllegalArgumentException("bootstrap generation must be ACTIVE and COMPLETED");
        }
        return new SearchV3IndexLifecycle(
                document,
                indexVersions(sourceVersions),
                Map.of(activeGeneration.metadata().generationId(), activeGeneration),
                0);
    }

    SearchV3IndexLifecycle begin(
            SearchIndexGeneration.Metadata metadata,
            SearchIndexGeneration.ExpectedManifest expectedManifest,
            SearchIndexGeneration.Claim initialClaim,
            Instant leaseExpiresAt) {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(expectedManifest, "expectedManifest");
        requireDocumentLineage(metadata.lineage());
        requireTrustedSourceVersion(metadata.lineage(), true);
        if (generations.containsKey(metadata.generationId())) {
            throw new IllegalArgumentException("duplicate Search V3 generation ID");
        }
        Map<String, SearchIndexGeneration> next = mutableGenerations();
        next.put(metadata.generationId(), SearchIndexGeneration.building(
                metadata, expectedManifest, initialClaim, leaseExpiresAt));
        return next(document, sourceVersions, next);
    }

    SearchV3IndexLifecycle lockForRecovery(
            SearchIndexGeneration.Claim claim,
            Instant observedAt,
            String recoveryLockToken) {
        SearchIndexGeneration current = generationForClaim(claim);
        Map<String, SearchIndexGeneration> next = mutableGenerations();
        next.put(claim.generationId(), current.lockForRecovery(claim, observedAt, recoveryLockToken));
        return next(document, sourceVersions, next);
    }

    SearchV3IndexLifecycle reclaim(
            SearchIndexGeneration.RecoveryToken recovery,
            Instant newLeaseExpiresAt) {
        Objects.requireNonNull(recovery, "recovery");
        SearchIndexGeneration.Claim claim = recovery.expiredClaim();
        SearchIndexGeneration current = generationForClaim(claim);
        Map<String, SearchIndexGeneration> next = mutableGenerations();
        next.put(claim.generationId(), current.reclaim(recovery, newLeaseExpiresAt));
        return next(document, sourceVersions, next);
    }

    SearchV3IndexLifecycle markReady(
            SearchIndexGeneration.Claim claim,
            SearchIndexGeneration.PersistedInventory inventory) {
        SearchIndexGeneration current = generationForClaim(claim);
        Map<String, SearchIndexGeneration> next = mutableGenerations();
        next.put(claim.generationId(), current.markReady(claim, inventory));
        return next(document, sourceVersions, next);
    }

    SearchV3IndexLifecycle fail(
            SearchIndexGeneration.Claim claim,
            SearchIndexGeneration.FailureStage stage) {
        SearchIndexGeneration current = generationForClaim(claim);
        Map<String, SearchIndexGeneration> nextGenerations = mutableGenerations();
        nextGenerations.put(claim.generationId(), current.fail(claim, stage));

        Map<String, DocumentVersionRef> nextVersions = mutableSourceVersions();
        DocumentVersionRef source = nextVersions.get(claim.documentVersionId());
        if (source.status() == SourceVersionStatus.PROCESSING
                && !source.documentVersionId().equals(document.activeDocumentVersionId())) {
            nextVersions.put(source.documentVersionId(), source.fail());
        }
        return next(document, nextVersions, nextGenerations);
    }

    ActivationPlan prepareActivation(SearchIndexGeneration.Claim claim) {
        SearchIndexGeneration candidate = generationForClaim(claim);
        candidate.requireActivatable(claim);
        requireTrustedSourceVersion(candidate.metadata().lineage(), true);

        String previousGenerationId = document.activeGenerationId();
        if (previousGenerationId != null) {
            if (previousGenerationId.equals(candidate.metadata().generationId())) {
                throw new IllegalStateException("candidate generation is already active");
            }
            requireReplaceablePrevious(candidate, previousGenerationId);
        }
        return new ActivationPlan(
                stateToken(), claim, previousGenerationId, candidate.metadata().generationId());
    }

    SearchIndexGeneration searchableGeneration(long ownerUserId, String documentId) {
        if (ownerUserId != document.ownerUserId() || !document.documentId().equals(documentId)) {
            throw new IllegalArgumentException("owner or document scope does not match lifecycle");
        }
        String activeGenerationId = document.activeGenerationId();
        if (activeGenerationId == null) {
            throw new IllegalStateException("document has no active Search V3 generation");
        }
        SearchIndexGeneration active = generations.get(activeGenerationId);
        if (active == null || !active.searchable()) {
            throw new IllegalStateException("active Search V3 generation is not searchable");
        }
        return active;
    }

    private SearchV3IndexLifecycle applyActivation(SearchIndexGeneration.Claim claim) {
        SearchIndexGeneration candidate = generationForClaim(claim);
        candidate.requireActivatable(claim);
        SearchIndexGeneration.Metadata candidateMetadata = candidate.metadata();
        requireTrustedSourceVersion(candidateMetadata.lineage(), true);

        Map<String, SearchIndexGeneration> nextGenerations = mutableGenerations();
        String previousGenerationId = document.activeGenerationId();
        if (previousGenerationId != null) {
            requireReplaceablePrevious(candidate, previousGenerationId);
            nextGenerations.put(previousGenerationId, generations.get(previousGenerationId).supersede());
        }
        nextGenerations.put(candidateMetadata.generationId(), candidate.activate(claim));

        Map<String, DocumentVersionRef> nextVersions = mutableSourceVersions();
        DocumentVersionRef sourceVersion = nextVersions.get(candidateMetadata.documentVersionId());
        if (candidateMetadata.documentVersionId().equals(document.activeDocumentVersionId())) {
            if (sourceVersion.status() != SourceVersionStatus.ACTIVE) {
                throw new IllegalStateException("same-version reindex requires an ACTIVE source version");
            }
        }
        else {
            nextVersions.put(sourceVersion.documentVersionId(), sourceVersion.activate());
        }

        DocumentSlot nextDocument = document.activate(
                candidateMetadata.documentVersionId(), candidateMetadata.generationId());
        return next(nextDocument, nextVersions, nextGenerations);
    }

    private void requireReplaceablePrevious(
            SearchIndexGeneration candidate,
            String previousGenerationId) {
        SearchIndexGeneration previous = generations.get(previousGenerationId);
        if (previous == null || !previous.searchable()) {
            throw new IllegalStateException("active generation pointer is invalid");
        }
        SearchIndexGeneration.Metadata previousMetadata = previous.metadata();
        SearchIndexGeneration.Metadata candidateMetadata = candidate.metadata();
        if (previousMetadata.ownerUserId() != candidateMetadata.ownerUserId()
                || !previousMetadata.documentId().equals(candidateMetadata.documentId())) {
            throw new IllegalStateException("candidate cannot replace another owner or document generation");
        }
    }

    private SearchIndexGeneration generationForClaim(SearchIndexGeneration.Claim claim) {
        Objects.requireNonNull(claim, "claim");
        SearchIndexGeneration generation = generations.get(claim.generationId());
        if (generation == null) {
            throw new IllegalArgumentException("Search V3 generation was not found");
        }
        if (claim.ownerUserId() != document.ownerUserId()
                || !claim.documentId().equals(document.documentId())) {
            throw new IllegalStateException("generation claim is outside owner or document scope");
        }
        return generation;
    }

    private void requireTrustedSourceVersion(
            SearchIndexGeneration.ArtifactLineage lineage,
            boolean requireBuildable) {
        requireDocumentLineage(lineage);
        DocumentVersionRef trusted = sourceVersions.get(lineage.documentVersionId());
        if (trusted == null
                || trusted.ownerUserId() != lineage.ownerUserId()
                || !trusted.documentId().equals(lineage.documentId())) {
            throw new IllegalArgumentException("generation does not reference a trusted document version");
        }
        if (!requireBuildable) {
            return;
        }
        boolean currentVersion = lineage.documentVersionId().equals(document.activeDocumentVersionId());
        if (currentVersion && trusted.status() != SourceVersionStatus.ACTIVE) {
            throw new IllegalStateException("current document version must remain ACTIVE during reindex");
        }
        if (!currentVersion && trusted.status() != SourceVersionStatus.PROCESSING) {
            throw new IllegalStateException("new document version must be PROCESSING while building");
        }
    }

    private void requireDocumentLineage(SearchIndexGeneration.ArtifactLineage lineage) {
        if (lineage.ownerUserId() != document.ownerUserId()
                || !lineage.documentId().equals(document.documentId())) {
            throw new IllegalArgumentException("generation lineage is outside owner or document scope");
        }
    }

    private void validateTrustedVersions() {
        DocumentVersionRef activeSource = sourceVersions.get(document.activeDocumentVersionId());
        if (activeSource == null || activeSource.status() != SourceVersionStatus.ACTIVE) {
            throw new IllegalStateException("document pointer must reference a trusted ACTIVE source version");
        }
        for (Map.Entry<String, DocumentVersionRef> entry : sourceVersions.entrySet()) {
            DocumentVersionRef source = entry.getValue();
            if (!entry.getKey().equals(source.documentVersionId())
                    || source.ownerUserId() != document.ownerUserId()
                    || !source.documentId().equals(document.documentId())) {
                throw new IllegalStateException("trusted source version escapes document scope");
            }
        }
    }

    private void validateActivePointer() {
        String activeGenerationId = document.activeGenerationId();
        long activeCount = generations.values().stream().filter(SearchIndexGeneration::searchable).count();
        if (activeGenerationId == null) {
            if (activeCount != 0) {
                throw new IllegalStateException("ACTIVE generation exists without an active pointer");
            }
            return;
        }
        SearchIndexGeneration active = generations.get(activeGenerationId);
        if (active == null || !active.searchable() || activeCount != 1) {
            throw new IllegalStateException("exactly one pointed ACTIVE generation is required");
        }
        SearchIndexGeneration.Metadata metadata = active.metadata();
        if (metadata.ownerUserId() != document.ownerUserId()
                || !metadata.documentId().equals(document.documentId())
                || !metadata.documentVersionId().equals(document.activeDocumentVersionId())) {
            throw new IllegalStateException("active generation does not match document scope or active version");
        }
    }

    private static Map<String, DocumentVersionRef> indexVersions(List<DocumentVersionRef> versions) {
        Objects.requireNonNull(versions, "sourceVersions");
        Map<String, DocumentVersionRef> indexed = new LinkedHashMap<>();
        for (DocumentVersionRef version : versions) {
            Objects.requireNonNull(version, "sourceVersion");
            if (indexed.putIfAbsent(version.documentVersionId(), version) != null) {
                throw new IllegalArgumentException("duplicate trusted document version ID");
            }
        }
        return indexed;
    }

    private Map<String, SearchIndexGeneration> mutableGenerations() {
        return new LinkedHashMap<>(generations);
    }

    private Map<String, DocumentVersionRef> mutableSourceVersions() {
        return new LinkedHashMap<>(sourceVersions);
    }

    private SearchV3IndexLifecycle next(
            DocumentSlot nextDocument,
            Map<String, DocumentVersionRef> nextSourceVersions,
            Map<String, SearchIndexGeneration> nextGenerations) {
        return new SearchV3IndexLifecycle(nextDocument, nextSourceVersions, nextGenerations, revision + 1);
    }

    StateToken stateToken() {
        Map<String, SearchIndexGeneration.StateToken> generationTokens = new LinkedHashMap<>();
        generations.forEach((id, generation) -> generationTokens.put(id, generation.stateToken()));
        return new StateToken(document, sourceVersions, generationTokens, revision);
    }

    DocumentSlot document() {
        return document;
    }

    DocumentVersionRef sourceVersion(String versionId) {
        DocumentVersionRef source = sourceVersions.get(versionId);
        if (source == null) {
            throw new IllegalArgumentException("trusted document version was not found");
        }
        return source;
    }

    SearchIndexGeneration generation(String generationId) {
        SearchIndexGeneration generation = generations.get(generationId);
        if (generation == null) {
            throw new IllegalArgumentException("Search V3 generation was not found");
        }
        return generation;
    }

    long revision() {
        return revision;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
