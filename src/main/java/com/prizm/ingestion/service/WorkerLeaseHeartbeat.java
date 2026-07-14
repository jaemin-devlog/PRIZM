package com.prizm.ingestion.service;

import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.exception.DocumentIndexingException;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Keeps a claimed processing job alive while the worker performs external document processing. */
@Service
public class WorkerLeaseHeartbeat {

    private static final Duration MINIMUM_LEASE_DURATION = Duration.ofNanos(3);

    private final ProcessingJobLeaseService leaseService;
    private final Duration interval;
    private final ScheduledExecutorService executor;

    @Autowired
    public WorkerLeaseHeartbeat(ProcessingJobLeaseService leaseService, IngestionProperties properties) {
        this(leaseService, properties, Executors.newSingleThreadScheduledExecutor(heartbeatThreadFactory()));
    }

    WorkerLeaseHeartbeat(
            ProcessingJobLeaseService leaseService,
            IngestionProperties properties,
            ScheduledExecutorService executor) {
        this.leaseService = leaseService;
        this.interval = calculateInterval(properties.getLeaseDuration());
        this.executor = executor;
    }

    public LeaseHeartbeat start(ClaimedProcessingJob claimedJob) {
        LeaseHeartbeat heartbeat = new LeaseHeartbeat(claimedJob, leaseService);
        ScheduledFuture<?> scheduledTask = executor.scheduleAtFixedRate(
                heartbeat::renewLease,
                interval.toNanos(),
                interval.toNanos(),
                TimeUnit.NANOSECONDS);
        heartbeat.attach(scheduledTask);
        return heartbeat;
    }

    static Duration calculateInterval(Duration leaseDuration) {
        if (leaseDuration == null || leaseDuration.compareTo(MINIMUM_LEASE_DURATION) < 0) {
            throw new IllegalArgumentException("leaseDuration must allow a positive heartbeat interval");
        }
        return leaseDuration.dividedBy(3);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static ThreadFactory heartbeatThreadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "prizm-worker-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        };
    }

    public static class LeaseHeartbeat implements AutoCloseable {

        private final ClaimedProcessingJob claimedJob;
        private final ProcessingJobLeaseService leaseService;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicReference<RuntimeException> renewalFailure = new AtomicReference<>();

        private volatile ScheduledFuture<?> scheduledTask;

        private LeaseHeartbeat(ClaimedProcessingJob claimedJob, ProcessingJobLeaseService leaseService) {
            this.claimedJob = claimedJob;
            this.leaseService = leaseService;
        }

        private void attach(ScheduledFuture<?> scheduledTask) {
            this.scheduledTask = scheduledTask;
            if (!active.get()) {
                scheduledTask.cancel(false);
            }
        }

        private void renewLease() {
            if (!active.get()) {
                return;
            }
            try {
                leaseService.renew(claimedJob);
            }
            catch (RuntimeException exception) {
                if (renewalFailure.compareAndSet(null, exception)) {
                    stop();
                }
            }
        }

        public void assertOwnership() {
            RuntimeException failure = renewalFailure.get();
            if (failure != null) {
                throw failure;
            }
            if (Thread.currentThread().isInterrupted()) {
                stop();
                throw new DocumentIndexingException("Indexing worker thread was interrupted.", true);
            }
        }

        @Override
        public void close() {
            stop();
        }

        private void stop() {
            if (!active.getAndSet(false)) {
                return;
            }
            ScheduledFuture<?> task = scheduledTask;
            if (task != null) {
                task.cancel(false);
            }
        }
    }
}
