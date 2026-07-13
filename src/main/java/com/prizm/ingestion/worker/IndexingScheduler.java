package com.prizm.ingestion.worker;

import com.prizm.ingestion.service.IndexingCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 같은 애플리케이션 안에서 대기 중인 색인 작업을 한 건씩 실행한다. */
@Component
@ConditionalOnProperty(
        prefix = "prizm.ingestion",
        name = "worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class IndexingScheduler {

    private final IndexingCoordinator coordinator;

    public IndexingScheduler(IndexingCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Scheduled(fixedDelayString = "${prizm.ingestion.poll-delay-ms:1000}")
    public void poll() {
        coordinator.processNext();
    }
}
