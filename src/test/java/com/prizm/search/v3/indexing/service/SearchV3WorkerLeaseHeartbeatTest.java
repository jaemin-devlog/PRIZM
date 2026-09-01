package com.prizm.search.v3.indexing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.search.v3.indexing.exception.SearchV3IndexingWorkerException;
import com.prizm.search.v3.indexing.exception.StaleSearchV3IndexingJobClaimException;
import com.prizm.search.v3.indexing.model.SearchV3IndexingFailureStage;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchV3WorkerLeaseHeartbeatTest {

    @Mock
    SearchV3IndexingJobService jobService;

    @Mock
    ScheduledExecutorService executor;

    @Mock
    ScheduledFuture<?> scheduledTask;

    SearchV3WorkerLeaseHeartbeat heartbeatService;
    SearchV3IndexingJobClaim claim;

    @BeforeEach
    void setUp() {
        IngestionProperties properties = new IngestionProperties();
        properties.setLeaseDuration(Duration.ofSeconds(9));
        heartbeatService = new SearchV3WorkerLeaseHeartbeat(jobService, properties, executor);
        claim = new SearchV3IndexingJobClaim(
                11L,
                12L,
                13L,
                14L,
                15L,
                2L,
                1,
                Instant.parse("2026-09-02T00:00:09Z"));
    }

    @Test
    void renewsAtOneThirdOfTheLeaseDuration() {
        stubSchedule();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);

        SearchV3WorkerLeaseHeartbeat.LeaseHeartbeat heartbeat = heartbeatService.start(claim);

        verify(executor).scheduleAtFixedRate(
                task.capture(),
                eq(Duration.ofSeconds(3).toNanos()),
                eq(Duration.ofSeconds(3).toNanos()),
                eq(TimeUnit.NANOSECONDS));
        task.getValue().run();
        heartbeat.assertOwnership();

        verify(jobService).renewLease(claim);
        heartbeat.close();
    }

    @Test
    void closeCancelsTheTaskAndPreventsFurtherRenewal() {
        stubSchedule();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        SearchV3WorkerLeaseHeartbeat.LeaseHeartbeat heartbeat = heartbeatService.start(claim);
        verify(executor).scheduleAtFixedRate(task.capture(), anyLong(), anyLong(), eq(TimeUnit.NANOSECONDS));

        heartbeat.close();
        task.getValue().run();

        verify(scheduledTask).cancel(false);
        verify(jobService, never()).renewLease(claim);
    }

    @Test
    void renewalFailureStopsTheHeartbeatAndIsReportedAtTheNextOwnershipCheck() {
        stubSchedule();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        StaleSearchV3IndexingJobClaimException failure = new StaleSearchV3IndexingJobClaimException(claim);
        doThrow(failure).when(jobService).renewLease(claim);
        SearchV3WorkerLeaseHeartbeat.LeaseHeartbeat heartbeat = heartbeatService.start(claim);
        verify(executor).scheduleAtFixedRate(task.capture(), anyLong(), anyLong(), eq(TimeUnit.NANOSECONDS));

        task.getValue().run();

        assertThatThrownBy(heartbeat::assertOwnership).isSameAs(failure);
        verify(scheduledTask).cancel(false);
    }

    @Test
    void interruptStopsTheHeartbeatAndRaisesARetryableStorageFailure() {
        stubSchedule();
        SearchV3WorkerLeaseHeartbeat.LeaseHeartbeat heartbeat = heartbeatService.start(claim);

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(heartbeat::assertOwnership)
                    .isInstanceOfSatisfying(SearchV3IndexingWorkerException.class, failure -> {
                        assertThat(failure.failureStage()).isEqualTo(SearchV3IndexingFailureStage.STORAGE);
                        assertThat(failure.retryable()).isTrue();
                        assertThat(failure).hasMessageContaining("interrupted");
                    });
        }
        finally {
            Thread.interrupted();
        }

        verify(scheduledTask).cancel(false);
    }

    private void stubSchedule() {
        when(executor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenAnswer(invocation -> scheduledTask);
    }
}
