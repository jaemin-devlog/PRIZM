package com.prizm.search.v3.indexing.service;

import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.search.v3.indexing.exception.SearchV3IndexingWorkerException;
import com.prizm.search.v3.indexing.model.SearchV3IndexingFailureStage;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
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

/** 외부 I/O와 embedding 동안 Search V3 full-lineage claim lease를 주기적으로 갱신한다. */
@Service
public class SearchV3WorkerLeaseHeartbeat {

    private static final Duration MINIMUM_LEASE_DURATION = Duration.ofNanos(3);

    private final SearchV3IndexingJobService jobService;
    private final Duration interval;
    private final ScheduledExecutorService executor;

    @Autowired
    public SearchV3WorkerLeaseHeartbeat(
            SearchV3IndexingJobService jobService,
            IngestionProperties properties) {
        this(jobService, properties, Executors.newSingleThreadScheduledExecutor(threadFactory()));
    }

    SearchV3WorkerLeaseHeartbeat(
            SearchV3IndexingJobService jobService,
            IngestionProperties properties,
            ScheduledExecutorService executor) {
        this.jobService = jobService;
        this.interval = calculateInterval(properties.getLeaseDuration());
        this.executor = executor;
    }

    public LeaseHeartbeat start(SearchV3IndexingJobClaim claim) {
        LeaseHeartbeat heartbeat = new LeaseHeartbeat(claim, jobService);
        ScheduledFuture<?> task = executor.scheduleAtFixedRate(
                heartbeat::renewLease,
                interval.toNanos(),
                interval.toNanos(),
                TimeUnit.NANOSECONDS);
        heartbeat.attach(task);
        return heartbeat;
    }

    static Duration calculateInterval(Duration leaseDuration) {
        if (leaseDuration == null || leaseDuration.compareTo(MINIMUM_LEASE_DURATION) < 0) {
            throw new IllegalArgumentException("leaseDuration must allow a positive Search V3 heartbeat interval");
        }
        return leaseDuration.dividedBy(3);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static ThreadFactory threadFactory() {
        return runnable -> {
            Thread thread = new Thread(runnable, "prizm-search-v3-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        };
    }

    public static class LeaseHeartbeat implements AutoCloseable {

        private final SearchV3IndexingJobClaim claim;
        private final SearchV3IndexingJobService jobService;
        private final AtomicBoolean active = new AtomicBoolean(true);
        private final AtomicReference<RuntimeException> renewalFailure = new AtomicReference<>();
        private volatile ScheduledFuture<?> task;

        private LeaseHeartbeat(
                SearchV3IndexingJobClaim claim,
                SearchV3IndexingJobService jobService) {
            this.claim = claim;
            this.jobService = jobService;
        }

        private void attach(ScheduledFuture<?> task) {
            this.task = task;
            if (!active.get()) {
                task.cancel(false);
            }
        }

        private void renewLease() {
            if (!active.get()) {
                return;
            }
            try {
                jobService.renewLease(claim);
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
                throw new SearchV3IndexingWorkerException(
                        SearchV3IndexingFailureStage.STORAGE,
                        true,
                        "Search V3 indexing worker thread was interrupted.",
                        null);
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
            ScheduledFuture<?> scheduled = task;
            if (scheduled != null) {
                scheduled.cancel(false);
            }
        }
    }
}
