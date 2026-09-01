package com.prizm.search.v3.indexing.service;

import com.prizm.search.v3.indexing.exception.SearchV3InventoryActivationException;
import com.prizm.search.v3.indexing.exception.SearchV3ActivationDeferredException;
import com.prizm.search.v3.indexing.exception.SearchV3ActivationDeferredException.Reason;
import com.prizm.search.v3.indexing.exception.StaleSearchV3IndexingJobClaimException;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.ActiveGeneration;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.DocumentContract;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.DocumentVersionContract;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.GenerationContract;
import com.prizm.search.v3.indexing.service.SearchV3InventoryVerifier.VerifiedInventory;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Search V3 exact inventory READY와 같은-version 원자 활성화를 조정한다. */
@Service
public class SearchV3InventoryActivationService {

    private final SearchV3InventoryActivationRepository repository;
    private final SearchV3InventoryVerifier verifier;

    public SearchV3InventoryActivationService(
            SearchV3InventoryActivationRepository repository,
            SearchV3InventoryVerifier verifier) {
        this.repository = repository;
        this.verifier = verifier;
    }

    @Transactional
    public ReadyResult markReady(SearchV3IndexingJobClaim claim) {
        requireCurrentClaim(claim);
        GenerationContract generation = repository.lockGeneration(claim, "BUILDING")
                .orElseThrow(() -> rejected("Generation is not BUILDING for the current claim."));
        DocumentVersionContract version = repository.lockDocumentVersion(claim)
                .orElseThrow(() -> rejected("Document version lineage does not exist."));

        VerifiedInventory verified = verifier.verify(
                generation,
                repository.lockInventory(claim));
        if (!repository.markReady(claim, verified.verifiedInventorySha256())) {
            throw new StaleSearchV3IndexingJobClaimException(claim);
        }
        return new ReadyResult(
                claim.generationId(),
                verified.logicalManifestSha256(),
                verified.verifiedInventorySha256(),
                verified.passageCount(),
                verified.childCount());
    }

    @Transactional
    public ActivationResult activate(SearchV3IndexingJobClaim claim) {
        requireCurrentClaim(claim);
        GenerationContract generation = repository.lockGeneration(claim, "READY")
                .orElseThrow(() -> rejected("Generation is not READY for the current claim."));
        if (generation.verifiedInventorySha256() == null) {
            throw rejected("READY generation does not have a verified inventory fingerprint.");
        }

        DocumentVersionContract version = repository.lockDocumentVersion(claim)
                .orElseThrow(() -> rejected("Document version lineage does not exist."));
        VerifiedInventory verified = verifier.verify(
                generation,
                repository.lockInventory(claim));
        if (!generation.verifiedInventorySha256().equals(verified.verifiedInventorySha256())) {
            throw rejected("READY inventory changed after verification.");
        }

        DocumentContract document;
        try {
            document = repository.lockDocument(claim)
                    .orElseThrow(() -> rejected("Document ownership does not exist."));
        }
        catch (DataAccessException exception) {
            if (hasSqlState(exception, "55P03")) {
                throw deferred(Reason.DOCUMENT_LOCKED, "Document is locked by another lifecycle transaction.");
            }
            throw exception;
        }
        List<ActiveGeneration> activeGenerations = repository.lockActiveGenerations(claim);
        if (activeGenerations.size() > 1) {
            throw rejected("More than one ACTIVE Search V3 generation exists for the document.");
        }
        ActiveGeneration previous = activeGenerations.stream().findFirst().orElse(null);
        validateSameVersionBoundary(claim, version, document, previous);

        if (previous != null && !repository.supersede(claim, previous)) {
            throw rejected("The previous ACTIVE Search V3 generation changed during activation.");
        }
        if (!repository.activate(claim, verified.verifiedInventorySha256())) {
            throw new StaleSearchV3IndexingJobClaimException(claim);
        }
        if (!repository.completeJob(claim)) {
            throw new StaleSearchV3IndexingJobClaimException(claim);
        }
        Long previousId = previous == null ? null : previous.id();
        if (!repository.updatePointer(claim, previousId)) {
            throw rejected("Search V3 active pointer changed during activation.");
        }

        return new ActivationResult(
                claim.generationId(),
                previousId,
                verified.verifiedInventorySha256());
    }

    private void requireCurrentClaim(SearchV3IndexingJobClaim claim) {
        Objects.requireNonNull(claim, "claim");
        if (!repository.lockCurrentJob(claim)) {
            throw new StaleSearchV3IndexingJobClaimException(claim);
        }
    }

    private static void validateSameVersionBoundary(
            SearchV3IndexingJobClaim claim,
            DocumentVersionContract version,
            DocumentContract document,
            ActiveGeneration previous) {
        if (!"ACTIVE".equals(version.status())) {
            throw deferred(
                    Reason.NOT_CURRENT_VERSION,
                    "Candidate document version is not ACTIVE in Production Search V2.");
        }
        if (document.activeVersionId() == null) {
            throw deferred(
                    Reason.NOT_CURRENT_VERSION,
                    "Document does not have an ACTIVE Production version.");
        }
        if (document.activeVersionId() != claim.documentVersionId()) {
            throw deferred(
                    Reason.NOT_CURRENT_VERSION,
                    "Candidate Search V3 generation is not for the current ACTIVE document version.");
        }

        if (previous == null) {
            if (document.activeSearchV3GenerationId() != null) {
                throw rejected("Search V3 pointer does not resolve to an ACTIVE generation.");
            }
            return;
        }

        if (!Objects.equals(document.activeSearchV3GenerationId(), previous.id())) {
            throw rejected("Search V3 pointer and ACTIVE generation do not match.");
        }
        if (previous.documentVersionId() != claim.documentVersionId()) {
            throw rejected("Previous ACTIVE Search V3 generation belongs to another document version.");
        }
        if (!"COMPLETED".equals(previous.jobStatus())) {
            throw rejected("Previous ACTIVE Search V3 generation does not have a COMPLETED job.");
        }
    }

    private static SearchV3InventoryActivationException rejected(String message) {
        return new SearchV3InventoryActivationException(message);
    }

    private static SearchV3ActivationDeferredException deferred(Reason reason, String message) {
        return new SearchV3ActivationDeferredException(reason, message);
    }

    private static boolean hasSqlState(Throwable failure, String expectedSqlState) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && expectedSqlState.equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public record ReadyResult(
            long generationId,
            String logicalManifestSha256,
            String verifiedInventorySha256,
            int passageCount,
            int childCount) {
    }

    public record ActivationResult(
            long generationId,
            Long supersededGenerationId,
            String verifiedInventorySha256) {
    }
}
