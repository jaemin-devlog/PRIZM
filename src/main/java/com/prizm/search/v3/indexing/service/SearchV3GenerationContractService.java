package com.prizm.search.v3.indexing.service;

import com.prizm.search.v3.indexing.exception.SearchV3InventoryActivationException;
import com.prizm.search.v3.indexing.exception.StaleSearchV3IndexingJobClaimException;
import com.prizm.search.v3.indexing.model.SearchV3EmbeddingModelContract;
import com.prizm.search.v3.indexing.model.SearchV3GenerationBuildContract;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import com.prizm.search.v3.indexing.model.SearchV3IndexingPolicies;
import com.prizm.search.v3.indexing.repository.SearchV3GenerationContractRepository;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Full current claim 아래 build policy와 claim-first expected manifest를 검증·동결한다. */
@Service
public class SearchV3GenerationContractService {

    private final SearchV3GenerationContractRepository repository;

    public SearchV3GenerationContractService(SearchV3GenerationContractRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public SearchV3GenerationBuildContract loadCurrent(SearchV3IndexingJobClaim claim) {
        Objects.requireNonNull(claim, "claim");
        return repository.lockCurrentContract(claim)
                .orElseThrow(() -> new StaleSearchV3IndexingJobClaimException(claim));
    }

    @Transactional
    public SearchV3GenerationBuildContract freezeExpectedManifest(
            SearchV3IndexingJobClaim claim,
            int passageCount,
            int childCount,
            String manifestSha256) {
        if (passageCount < 1 || childCount < 1) {
            throw rejected("Search V3 expected inventory counts must be positive.");
        }
        if (manifestSha256 == null || !manifestSha256.matches("[0-9a-f]{64}")) {
            throw rejected("Search V3 expected manifest must be lowercase SHA-256.");
        }

        SearchV3GenerationBuildContract generation = loadCurrent(claim);
        if (!"BUILDING".equals(generation.status())) {
            throw rejected("Only a BUILDING Search V3 generation can freeze its expected manifest.");
        }
        requireSupportedPolicies(generation);

        if (generation.manifestFrozen()) {
            if (generation.expectedPassageCount() == passageCount
                    && generation.expectedChildCount() == childCount
                    && generation.expectedManifestSha256().equals(manifestSha256)) {
                return generation;
            }
            throw rejected("Search V3 expected manifest is already frozen with different values.");
        }
        if (generation.expectedPassageCount() != null
                || generation.expectedChildCount() != null
                || generation.expectedManifestSha256() != null) {
            throw rejected("Search V3 expected manifest is partially initialized.");
        }
        if (repository.countInventory(claim) != 0L) {
            throw rejected("Search V3 expected manifest must be frozen before artifact storage.");
        }
        if (!repository.freezeExpectedManifest(claim, passageCount, childCount, manifestSha256)) {
            throw new StaleSearchV3IndexingJobClaimException(claim);
        }
        return repository.lockCurrentContract(claim)
                .orElseThrow(() -> new StaleSearchV3IndexingJobClaimException(claim));
    }

    public void requireSupportedPolicies(SearchV3GenerationBuildContract generation) {
        if (!SearchV3IndexingPolicies.STRUCTURE.equals(generation.structurePolicyVersion())
                || !SearchV3IndexingPolicies.PASSAGE.equals(generation.passagePolicyVersion())
                || !SearchV3IndexingPolicies.CHILD.equals(generation.childPolicyVersion())
                || !SearchV3IndexingPolicies.PASSAGE_INPUT.equals(generation.passageInputPolicyVersion())
                || !SearchV3IndexingPolicies.CHILD_INPUT.equals(generation.childInputPolicyVersion())) {
            throw rejected("Search V3 generation policy does not match the PRZ-040 Worker.");
        }
    }

    public void requireEmbeddingContract(
            SearchV3GenerationBuildContract generation,
            SearchV3EmbeddingModelContract actual) {
        if (!generation.embeddingContract().equals(actual)) {
            throw rejected("Search V3 generation embedding contract does not match the resolved local model.");
        }
    }

    private static SearchV3InventoryActivationException rejected(String message) {
        return new SearchV3InventoryActivationException(message);
    }
}
