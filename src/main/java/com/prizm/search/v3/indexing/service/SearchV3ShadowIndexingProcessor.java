package com.prizm.search.v3.indexing.service;

import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.embedding.service.EmbeddingService;
import com.prizm.embedding.service.EmbeddingValidator;
import com.prizm.infrastructure.storage.FileStorage;
import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.service.DocumentTextExtractor;
import com.prizm.ingestion.service.PageText;
import com.prizm.search.v3.indexing.exception.SearchV3ActivationDeferredException;
import com.prizm.search.v3.indexing.exception.SearchV3IndexingWorkerException;
import com.prizm.search.v3.indexing.exception.StaleSearchV3IndexingJobClaimException;
import com.prizm.search.v3.indexing.model.SearchV3EmbeddingModelContract;
import com.prizm.search.v3.indexing.model.SearchV3DocumentSource;
import com.prizm.search.v3.indexing.model.SearchV3GenerationBuildContract;
import com.prizm.search.v3.indexing.model.SearchV3IndexGenerationStatus;
import com.prizm.search.v3.indexing.model.SearchV3IndexingFailureStage;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import com.prizm.search.v3.indexing.model.SearchV3LogicalInventoryPlan;
import com.prizm.search.v3.indexing.model.SearchV3PreparedInventory;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.ChildRow;
import com.prizm.search.v3.indexing.repository.SearchV3InventoryActivationRepository.PassageRow;
import com.prizm.search.v3.indexing.repository.SearchV3DocumentSourceRepository;
import com.prizm.search.v3.indexing.structure.ExtractedDocumentSource;
import com.prizm.search.v3.indexing.structure.SearchV3Structure;
import com.prizm.search.v3.indexing.structure.SearchV3StructureBuilder;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Claimed Search V3 job을 실제 원문에서 shadow inventory와 READY/activation까지 처리한다. */
@Service
public class SearchV3ShadowIndexingProcessor {

    private final SearchV3DocumentSourceRepository documentSourceRepository;
    private final FileStorage fileStorage;
    private final DocumentTextExtractor textExtractor;
    private final SearchV3StructureBuilder structureBuilder;
    private final SearchV3LogicalInventoryPlanner inventoryPlanner;
    private final SearchV3GenerationContractService generationContractService;
    private final SearchV3EmbeddingModelContractProvider modelContractProvider;
    private final EmbeddingService embeddingService;
    private final EmbeddingValidator embeddingValidator;
    private final SearchV3ArtifactStorageService storageService;
    private final SearchV3InventoryActivationService activationService;
    private final SearchV3IndexingJobService jobService;
    private final SearchV3WorkerLeaseHeartbeat leaseHeartbeat;
    private final SearchV3IndexingFailureClassifier failureClassifier;
    private final int leaseRefreshArtifactInterval;

    @Autowired
    public SearchV3ShadowIndexingProcessor(
            SearchV3DocumentSourceRepository documentSourceRepository,
            FileStorage fileStorage,
            DocumentTextExtractor textExtractor,
            SearchV3LogicalInventoryPlanner inventoryPlanner,
            SearchV3GenerationContractService generationContractService,
            SearchV3EmbeddingModelContractProvider modelContractProvider,
            EmbeddingService embeddingService,
            EmbeddingValidator embeddingValidator,
            SearchV3ArtifactStorageService storageService,
            SearchV3InventoryActivationService activationService,
            SearchV3IndexingJobService jobService,
            SearchV3WorkerLeaseHeartbeat leaseHeartbeat,
            SearchV3IndexingFailureClassifier failureClassifier,
            IngestionProperties ingestionProperties) {
        this(
                documentSourceRepository,
                fileStorage,
                textExtractor,
                new SearchV3StructureBuilder(),
                inventoryPlanner,
                generationContractService,
                modelContractProvider,
                embeddingService,
                embeddingValidator,
                storageService,
                activationService,
                jobService,
                leaseHeartbeat,
                failureClassifier,
                ingestionProperties.getLeaseRefreshChunkInterval());
    }

    SearchV3ShadowIndexingProcessor(
            SearchV3DocumentSourceRepository documentSourceRepository,
            FileStorage fileStorage,
            DocumentTextExtractor textExtractor,
            SearchV3StructureBuilder structureBuilder,
            SearchV3LogicalInventoryPlanner inventoryPlanner,
            SearchV3GenerationContractService generationContractService,
            SearchV3EmbeddingModelContractProvider modelContractProvider,
            EmbeddingService embeddingService,
            EmbeddingValidator embeddingValidator,
            SearchV3ArtifactStorageService storageService,
            SearchV3InventoryActivationService activationService,
            SearchV3IndexingJobService jobService,
            SearchV3WorkerLeaseHeartbeat leaseHeartbeat,
            SearchV3IndexingFailureClassifier failureClassifier,
            int leaseRefreshArtifactInterval) {
        this.documentSourceRepository = documentSourceRepository;
        this.fileStorage = fileStorage;
        this.textExtractor = textExtractor;
        this.structureBuilder = structureBuilder;
        this.inventoryPlanner = inventoryPlanner;
        this.generationContractService = generationContractService;
        this.modelContractProvider = modelContractProvider;
        this.embeddingService = embeddingService;
        this.embeddingValidator = embeddingValidator;
        this.storageService = storageService;
        this.activationService = activationService;
        this.jobService = jobService;
        this.leaseHeartbeat = leaseHeartbeat;
        this.failureClassifier = failureClassifier;
        if (leaseRefreshArtifactInterval < 1) {
            throw new IllegalArgumentException("Search V3 lease refresh artifact interval must be positive.");
        }
        this.leaseRefreshArtifactInterval = leaseRefreshArtifactInterval;
    }

    public ProcessResult process(SearchV3IndexingJobClaim claim) {
        try (SearchV3WorkerLeaseHeartbeat.LeaseHeartbeat heartbeat = leaseHeartbeat.start(claim)) {
            checkpoint(claim, heartbeat);
            SearchV3IndexGenerationStatus status = execute(
                    SearchV3IndexingFailureStage.STORAGE,
                    () -> jobService.currentGenerationStatus(claim));
            if (status == SearchV3IndexGenerationStatus.READY) {
                return activateOrDefer(claim, heartbeat);
            }

            SearchV3GenerationBuildContract generation = execute(
                    SearchV3IndexingFailureStage.STORAGE,
                    () -> generationContractService.loadCurrent(claim));
            generationContractService.requireSupportedPolicies(generation);
            SearchV3DocumentSource version = execute(
                    SearchV3IndexingFailureStage.STORAGE,
                    () -> documentSourceRepository.find(claim)
                            .orElseThrow(() -> new DocumentVersionNotFoundException(claim.documentVersionId())));
            requireProcessableVersion(version);

            checkpoint(claim, heartbeat);
            byte[] content = execute(
                    SearchV3IndexingFailureStage.STORAGE,
                    () -> fileStorage.read(version.storedFilePath()));
            checkpoint(claim, heartbeat);
            List<PageText> pages = execute(
                    SearchV3IndexingFailureStage.PASSAGE_GENERATION,
                    () -> textExtractor.extract(version.fileType(), content));
            checkpoint(claim, heartbeat);

            ExtractedDocumentSource extracted = execute(
                    SearchV3IndexingFailureStage.PASSAGE_GENERATION,
                    () -> ExtractedDocumentSource.from(
                            claim.documentId(),
                            claim.documentVersionId(),
                            version.storedFilePath(),
                            version.fileType(),
                            pages));
            SearchV3Structure structure = execute(
                    SearchV3IndexingFailureStage.PASSAGE_GENERATION,
                    () -> structureBuilder.build(extracted));
            checkpoint(claim, heartbeat);
            SearchV3LogicalInventoryPlan plan = execute(
                    SearchV3IndexingFailureStage.CHILD_GENERATION,
                    () -> inventoryPlanner.plan(structure));

            generation = execute(
                    SearchV3IndexingFailureStage.STORAGE,
                    () -> generationContractService.freezeExpectedManifest(
                            claim,
                            plan.passages().size(),
                            plan.children().size(),
                            plan.logicalManifestSha256()));
            SearchV3GenerationBuildContract frozenGeneration = generation;
            SearchV3EmbeddingModelContract actualModel = execute(
                    SearchV3IndexingFailureStage.PASSAGE_EMBEDDING,
                    modelContractProvider::resolve);
            execute(
                    SearchV3IndexingFailureStage.PASSAGE_EMBEDDING,
                    () -> {
                        generationContractService.requireEmbeddingContract(frozenGeneration, actualModel);
                        return null;
                    });

            List<SearchV3PreparedInventory.EmbeddedPassage> passages = embedPassages(
                    claim, heartbeat, plan.passages());
            List<SearchV3PreparedInventory.EmbeddedChild> children = embedChildren(
                    claim, heartbeat, plan.children());
            checkpoint(claim, heartbeat);
            SearchV3EmbeddingModelContract finalModel = execute(
                    SearchV3IndexingFailureStage.CHILD_EMBEDDING,
                    modelContractProvider::resolve);
            execute(
                    SearchV3IndexingFailureStage.CHILD_EMBEDDING,
                    () -> {
                        generationContractService.requireEmbeddingContract(frozenGeneration, finalModel);
                        return null;
                    });
            SearchV3PreparedInventory prepared = new SearchV3PreparedInventory(
                    passages,
                    children,
                    plan.logicalManifestSha256());

            checkpoint(claim, heartbeat);
            execute(
                    SearchV3IndexingFailureStage.STORAGE,
                    () -> storageService.replaceAll(claim, prepared));
            checkpoint(claim, heartbeat);
            execute(
                    SearchV3IndexingFailureStage.ACTIVATION,
                    () -> activationService.markReady(claim));
            checkpoint(claim, heartbeat);
            return activateOrDefer(claim, heartbeat);
        }
    }

    private List<SearchV3PreparedInventory.EmbeddedPassage> embedPassages(
            SearchV3IndexingJobClaim claim,
            SearchV3WorkerLeaseHeartbeat.LeaseHeartbeat heartbeat,
            List<PassageRow> rows) {
        List<SearchV3PreparedInventory.EmbeddedPassage> embedded = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            heartbeat.assertOwnership();
            PassageRow row = rows.get(index);
            float[] vector = execute(
                    SearchV3IndexingFailureStage.PASSAGE_EMBEDDING,
                    () -> embeddingService.embed(row.retrievalText()));
            execute(
                    SearchV3IndexingFailureStage.PASSAGE_EMBEDDING,
                    () -> {
                        embeddingValidator.validate(vector);
                        return null;
                    });
            heartbeat.assertOwnership();
            embedded.add(new SearchV3PreparedInventory.EmbeddedPassage(row, vector));
            refreshDuringEmbedding(claim, heartbeat, index + 1);
        }
        return List.copyOf(embedded);
    }

    private List<SearchV3PreparedInventory.EmbeddedChild> embedChildren(
            SearchV3IndexingJobClaim claim,
            SearchV3WorkerLeaseHeartbeat.LeaseHeartbeat heartbeat,
            List<ChildRow> rows) {
        List<SearchV3PreparedInventory.EmbeddedChild> embedded = new ArrayList<>(rows.size());
        for (int index = 0; index < rows.size(); index++) {
            heartbeat.assertOwnership();
            ChildRow row = rows.get(index);
            float[] vector = execute(
                    SearchV3IndexingFailureStage.CHILD_EMBEDDING,
                    () -> embeddingService.embed(row.sourceText()));
            execute(
                    SearchV3IndexingFailureStage.CHILD_EMBEDDING,
                    () -> {
                        embeddingValidator.validate(vector);
                        return null;
                    });
            heartbeat.assertOwnership();
            embedded.add(new SearchV3PreparedInventory.EmbeddedChild(row, vector));
            refreshDuringEmbedding(claim, heartbeat, index + 1);
        }
        return List.copyOf(embedded);
    }

    private void refreshDuringEmbedding(
            SearchV3IndexingJobClaim claim,
            SearchV3WorkerLeaseHeartbeat.LeaseHeartbeat heartbeat,
            int completedArtifacts) {
        if (completedArtifacts % leaseRefreshArtifactInterval == 0) {
            checkpoint(claim, heartbeat);
        }
    }

    private ProcessResult activateOrDefer(
            SearchV3IndexingJobClaim claim,
            SearchV3WorkerLeaseHeartbeat.LeaseHeartbeat heartbeat) {
        checkpoint(claim, heartbeat);
        try {
            activationService.activate(claim);
            return ProcessResult.ACTIVATED;
        }
        catch (SearchV3ActivationDeferredException exception) {
            deferActivation(claim, exception.getMessage());
            return ProcessResult.READY_DEFERRED;
        }
        catch (StaleSearchV3IndexingJobClaimException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            if (failureClassifier.isRetryable(exception)) {
                deferActivation(claim, exception.getMessage());
                return ProcessResult.READY_DEFERRED;
            }
            throw wrap(SearchV3IndexingFailureStage.ACTIVATION, exception);
        }
    }

    private void checkpoint(
            SearchV3IndexingJobClaim claim,
            SearchV3WorkerLeaseHeartbeat.LeaseHeartbeat heartbeat) {
        execute(
                SearchV3IndexingFailureStage.STORAGE,
                () -> {
                    heartbeat.assertOwnership();
                    jobService.renewLease(claim);
                    heartbeat.assertOwnership();
                    return null;
                });
    }

    private void deferActivation(SearchV3IndexingJobClaim claim, String reason) {
        execute(
                SearchV3IndexingFailureStage.ACTIVATION,
                () -> jobService.deferActivation(claim, reason));
    }

    private void requireProcessableVersion(SearchV3DocumentSource version) {
        if (version.status() != DocumentVersionStatus.ACTIVE
                && version.status() != DocumentVersionStatus.PROCESSING) {
            throw wrap(
                    SearchV3IndexingFailureStage.STORAGE,
                    new IllegalStateException("Search V3 source version is not processable."));
        }
    }

    private <T> T execute(SearchV3IndexingFailureStage stage, Supplier<T> operation) {
        try {
            return operation.get();
        }
        catch (StaleSearchV3IndexingJobClaimException | SearchV3IndexingWorkerException exception) {
            throw exception;
        }
        catch (RuntimeException exception) {
            throw wrap(stage, exception);
        }
    }

    private SearchV3IndexingWorkerException wrap(
            SearchV3IndexingFailureStage stage,
            RuntimeException exception) {
        return new SearchV3IndexingWorkerException(
                stage,
                failureClassifier.isRetryable(exception),
                exception.getMessage() == null ? "Search V3 indexing failed." : exception.getMessage(),
                exception);
    }

    public enum ProcessResult {
        ACTIVATED,
        READY_DEFERRED
    }
}
