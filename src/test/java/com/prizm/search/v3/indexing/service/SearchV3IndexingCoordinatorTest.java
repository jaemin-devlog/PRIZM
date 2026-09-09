package com.prizm.search.v3.indexing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.search.v3.indexing.exception.SearchV3IndexingWorkerException;
import com.prizm.search.v3.indexing.exception.StaleSearchV3IndexingJobClaimException;
import com.prizm.search.v3.indexing.model.SearchV3IndexingFailureStage;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import com.prizm.search.v3.indexing.model.SearchV3RecoveryLock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchV3IndexingCoordinatorTest {

    @Mock
    SearchV3IndexingJobService jobService;

    @Mock
    SearchV3ShadowIndexingProcessor processor;

    SearchV3IndexingCoordinator coordinator;
    SearchV3IndexingJobClaim claim;

    @BeforeEach
    void setUp() {
        coordinator = new SearchV3IndexingCoordinator(jobService, processor);
        claim = new SearchV3IndexingJobClaim(
                21L,
                22L,
                23L,
                24L,
                25L,
                3L,
                2,
                Instant.parse("2026-09-02T00:01:00Z"));
    }

    @Test
    void returnsFalseWhenNoJobCanBeClaimed() {
        when(jobService.claimNext()).thenReturn(Optional.empty());

        assertThat(coordinator.processNext()).isFalse();

        verify(processor, never()).process(any());
    }

    @Test
    void processesAClaimedJobSuccessfully() {
        when(jobService.claimNext()).thenReturn(Optional.of(claim));

        assertThat(coordinator.processNext()).isTrue();

        verify(processor).process(claim);
        verify(jobService, never()).handleFailure(any(), anyBoolean(), any(), anyString());
    }

    @Test
    void ignoresAStaleClaimWithoutRecordingFailure() {
        when(jobService.claimNext()).thenReturn(Optional.of(claim));
        doThrow(new StaleSearchV3IndexingJobClaimException(claim)).when(processor).process(claim);

        assertThat(coordinator.processNext()).isTrue();

        verify(jobService, never()).handleFailure(any(), anyBoolean(), any(), anyString());
    }

    @Test
    void forwardsTypedFailureStageAndRetryabilityToTheJobService() {
        when(jobService.claimNext()).thenReturn(Optional.of(claim));
        SearchV3IndexingWorkerException failure = new SearchV3IndexingWorkerException(
                SearchV3IndexingFailureStage.CHILD_EMBEDDING,
                true,
                "Child embedding provider is temporarily unavailable.",
                new IllegalStateException("temporary"));
        doThrow(failure).when(processor).process(claim);

        assertThat(coordinator.processNext()).isTrue();

        verify(jobService).handleFailure(
                claim,
                true,
                SearchV3IndexingFailureStage.CHILD_EMBEDDING,
                "Child embedding provider is temporarily unavailable.");
    }

    @Test
    void mapsAnUntypedRuntimeFailureToPermanentStorageFailure() {
        when(jobService.claimNext()).thenReturn(Optional.of(claim));
        doThrow(new IllegalStateException()).when(processor).process(claim);

        assertThat(coordinator.processNext()).isTrue();

        verify(jobService).handleFailure(
                claim,
                false,
                SearchV3IndexingFailureStage.STORAGE,
                "Search V3 indexing failed.");
    }

    @Test
    void ignoresAClaimThatBecomesStaleWhileRecordingFailure() {
        when(jobService.claimNext()).thenReturn(Optional.of(claim));
        SearchV3IndexingWorkerException failure = new SearchV3IndexingWorkerException(
                SearchV3IndexingFailureStage.PASSAGE_GENERATION,
                false,
                "Invalid document structure.",
                null);
        doThrow(failure).when(processor).process(claim);
        doThrow(new StaleSearchV3IndexingJobClaimException(claim))
                .when(jobService)
                .handleFailure(
                        claim,
                        false,
                        SearchV3IndexingFailureStage.PASSAGE_GENERATION,
                        "Invalid document structure.");

        assertThat(coordinator.processNext()).isTrue();

        verify(jobService).handleFailure(
                claim,
                false,
                SearchV3IndexingFailureStage.PASSAGE_GENERATION,
                "Invalid document structure.");
    }

    @Test
    void returnsFalseWhenNoExpiredJobCanBeRecoveryLocked() {
        when(jobService.acquireNextRecoveryLock()).thenReturn(Optional.empty());

        assertThat(coordinator.recoverNext()).isFalse();

        verify(processor, never()).process(any());
    }

    @Test
    void reclaimsAndImmediatelyProcessesTheNewClaim() {
        SearchV3RecoveryLock lock = new SearchV3RecoveryLock(
                claim,
                UUID.fromString("4ae0a508-9f54-4ea5-a953-7da88a5af760"),
                Instant.parse("2026-09-02T00:02:00Z"));
        SearchV3IndexingJobClaim reclaimed = new SearchV3IndexingJobClaim(
                claim.jobId(), claim.generationId(), claim.ownerUserId(), claim.documentId(),
                claim.documentVersionId(), claim.claimVersion() + 1, claim.attemptCount() + 1,
                Instant.parse("2026-09-02T00:12:00Z"));
        when(jobService.acquireNextRecoveryLock()).thenReturn(Optional.of(lock));
        when(jobService.reclaim(lock)).thenReturn(reclaimed);

        assertThat(coordinator.recoverNext()).isTrue();

        verify(processor).process(reclaimed);
        verify(processor, never()).process(claim);
    }
}
