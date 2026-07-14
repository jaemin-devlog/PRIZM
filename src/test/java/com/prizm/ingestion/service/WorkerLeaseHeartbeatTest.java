package com.prizm.ingestion.service;

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
import com.prizm.ingestion.exception.DocumentIndexingException;
import com.prizm.ingestion.exception.StaleProcessingJobClaimException;
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
class WorkerLeaseHeartbeatTest {

    @Mock
    ProcessingJobLeaseService leaseService;

    @Mock
    ScheduledExecutorService executor;

    @Mock
    ScheduledFuture<?> scheduledTask;

    WorkerLeaseHeartbeat heartbeatService;
    ClaimedProcessingJob claimedJob;

    @BeforeEach
    void setUp() {
        IngestionProperties properties = new IngestionProperties();
        properties.setLeaseDuration(Duration.ofSeconds(9));
        heartbeatService = new WorkerLeaseHeartbeat(leaseService, properties, executor);
        claimedJob = new ClaimedProcessingJob(12L, 22L, 32L, 4L, Instant.parse("2026-07-14T00:00:00Z"));
    }

    private void stubSchedule() {
        when(executor.scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), eq(TimeUnit.NANOSECONDS)))
                .thenAnswer(invocation -> scheduledTask);
    }

    @Test
    void renewsAtOneThirdOfTheLeaseDurationAndStopsAfterClose() {
        stubSchedule();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);

        WorkerLeaseHeartbeat.LeaseHeartbeat heartbeat = heartbeatService.start(claimedJob);

        verify(executor).scheduleAtFixedRate(
                task.capture(), eq(Duration.ofSeconds(3).toNanos()), eq(Duration.ofSeconds(3).toNanos()),
                eq(TimeUnit.NANOSECONDS));
        task.getValue().run();
        heartbeat.assertOwnership();
        heartbeat.close();

        verify(leaseService).renew(claimedJob);
        verify(scheduledTask).cancel(false);
    }

    @Test
    void recordsStaleLeaseLossAndBlocksFurtherCompletion() {
        stubSchedule();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        StaleProcessingJobClaimException staleClaim = new StaleProcessingJobClaimException(
                claimedJob.processingJobId(), claimedJob.claimVersion());
        doThrow(staleClaim).when(leaseService).renew(claimedJob);

        WorkerLeaseHeartbeat.LeaseHeartbeat heartbeat = heartbeatService.start(claimedJob);
        verify(executor).scheduleAtFixedRate(task.capture(), anyLong(), anyLong(), eq(TimeUnit.NANOSECONDS));
        task.getValue().run();

        assertThatThrownBy(heartbeat::assertOwnership).isSameAs(staleClaim);
        verify(scheduledTask).cancel(false);
    }

    @Test
    void doesNotRenewAfterTheHeartbeatIsClosed() {
        stubSchedule();
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        WorkerLeaseHeartbeat.LeaseHeartbeat heartbeat = heartbeatService.start(claimedJob);
        verify(executor).scheduleAtFixedRate(task.capture(), anyLong(), anyLong(), eq(TimeUnit.NANOSECONDS));

        heartbeat.close();
        task.getValue().run();

        verify(leaseService, never()).renew(claimedJob);
        verify(scheduledTask).cancel(false);
    }

    @Test
    void stopsWhenTheProcessingThreadIsInterrupted() {
        stubSchedule();
        WorkerLeaseHeartbeat.LeaseHeartbeat heartbeat = heartbeatService.start(claimedJob);

        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(heartbeat::assertOwnership)
                    .isInstanceOf(DocumentIndexingException.class)
                    .extracting(exception -> ((DocumentIndexingException) exception).isRetryable())
                    .isEqualTo(true);
        }
        finally {
            Thread.interrupted();
        }

        verify(scheduledTask).cancel(false);
    }

    @Test
    void usesANonZeroIntervalThatIsShorterThanTheLeaseDuration() {
        Duration leaseDuration = Duration.ofMillis(1);

        Duration interval = WorkerLeaseHeartbeat.calculateInterval(leaseDuration);

        assertThat(interval).isPositive();
        assertThat(interval).isLessThan(leaseDuration);
    }

    @Test
    void rejectsLeaseDurationThatCannotContainAShorterPositiveInterval() {
        assertThatThrownBy(() -> WorkerLeaseHeartbeat.calculateInterval(Duration.ofNanos(2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("heartbeat interval");
    }
}
