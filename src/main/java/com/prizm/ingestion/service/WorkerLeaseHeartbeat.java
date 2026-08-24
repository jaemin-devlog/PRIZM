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

/**
 * 외부 문서 처리 중에도 현재 Worker의 작업 임대가 만료되지 않도록 주기적으로 갱신한다.
 *
 * <p>별도 daemon 스레드가 임대 시간의 3분의 1 간격으로 갱신한다. 갱신 실패는 heartbeat 안에 보관했다가
 * 처리 스레드가 다음 소유권 확인 지점에서 예외로 받으므로, 임대를 잃은 작업은 완료 트랜잭션으로 넘어가지
 * 않는다.</p>
 */
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

    /** 처리 시도에 묶인 heartbeat를 시작한다. 반환값을 닫으면 예약된 갱신도 중단된다. */
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

    /** heartbeat 갱신 실패와 처리 스레드의 중단 여부를 완료 전에 확인하는 소유권 보호 객체다. */
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

        /** 마지막 임대 갱신이 실패했거나 처리 스레드가 중단됐으면 더 진행하지 않는다. */
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
