package com.prizm.search.v3.indexing.service;

import com.prizm.search.v3.indexing.exception.SearchV3InventoryActivationException;
import com.prizm.search.v3.indexing.model.SearchV3GenerationBuildContract;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import com.prizm.search.v3.indexing.model.SearchV3PreparedInventory;
import com.prizm.search.v3.indexing.repository.SearchV3ArtifactStorageRepository;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.GenerationContract;
import com.prizm.search.v3.indexing.service.SearchV3InventoryVerifier.VerifiedInventory;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Current claim과 frozen manifest 아래 shadow artifact 네 계열을 원자적으로 저장한다. */
@Service
public class SearchV3ArtifactStorageService {

    private final SearchV3GenerationContractService generationContractService;
    private final SearchV3ArtifactStorageRepository storageRepository;
    private final SearchV3InventoryActivationRepository inventoryRepository;
    private final SearchV3InventoryVerifier verifier;

    public SearchV3ArtifactStorageService(
            SearchV3GenerationContractService generationContractService,
            SearchV3ArtifactStorageRepository storageRepository,
            SearchV3InventoryActivationRepository inventoryRepository,
            SearchV3InventoryVerifier verifier) {
        this.generationContractService = generationContractService;
        this.storageRepository = storageRepository;
        this.inventoryRepository = inventoryRepository;
        this.verifier = verifier;
    }

    @Transactional
    public VerifiedInventory replaceAll(
            SearchV3IndexingJobClaim claim,
            SearchV3PreparedInventory prepared) {
        SearchV3GenerationBuildContract generation = generationContractService.loadCurrent(claim);
        if (!"BUILDING".equals(generation.status()) || !generation.manifestFrozen()) {
            throw rejected("Search V3 inventory storage requires a BUILDING generation with a frozen manifest.");
        }
        generationContractService.requireSupportedPolicies(generation);
        if (generation.expectedPassageCount() != prepared.passages().size()
                || generation.expectedChildCount() != prepared.children().size()
                || !generation.expectedManifestSha256().equals(prepared.logicalManifestSha256())) {
            throw rejected("Prepared Search V3 inventory does not match the frozen generation manifest.");
        }

        List<SearchV3InventoryActivationRepository.PassageRow> passages = prepared.passages().stream()
                .map(SearchV3PreparedInventory.EmbeddedPassage::row)
                .toList();
        List<SearchV3InventoryActivationRepository.ChildRow> children = prepared.children().stream()
                .map(SearchV3PreparedInventory.EmbeddedChild::row)
                .toList();
        String calculatedManifest = verifier.logicalManifestSha256(passages, children);
        if (!prepared.logicalManifestSha256().equals(calculatedManifest)) {
            throw rejected("Prepared Search V3 logical manifest changed before storage.");
        }

        storageRepository.replaceAll(claim, generation, prepared);
        VerifiedInventory persisted = verifier.verify(
                toActivationContract(generation),
                inventoryRepository.lockInventory(claim));
        if (!prepared.logicalManifestSha256().equals(persisted.logicalManifestSha256())) {
            throw rejected("Persisted Search V3 inventory differs from the pre-insert manifest.");
        }
        return persisted;
    }

    private static GenerationContract toActivationContract(SearchV3GenerationBuildContract generation) {
        return new GenerationContract(
                generation.generationId(),
                generation.status(),
                generation.embeddingModelId(),
                generation.resolvedModelDigest(),
                generation.embeddingDimension(),
                generation.passageInputPolicyVersion(),
                generation.childInputPolicyVersion(),
                generation.expectedPassageCount(),
                generation.expectedChildCount(),
                generation.expectedManifestSha256(),
                null);
    }

    private static SearchV3InventoryActivationException rejected(String message) {
        return new SearchV3InventoryActivationException(message);
    }
}
