package com.prizm.search.v3.indexing.worker;

import com.prizm.search.v3.indexing.service.SearchV3IndexingCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** PENDING 또는 due retry Search V3 job을 한 건씩 실행한다. */
@Component
@ConditionalOnProperty(prefix = "prizm.search-v3", name = "worker-enabled", havingValue = "true")
public class SearchV3IndexingScheduler {

    private final SearchV3IndexingCoordinator coordinator;

    public SearchV3IndexingScheduler(SearchV3IndexingCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Scheduled(fixedDelayString = "${prizm.search-v3.poll-delay-ms:1000}")
    public void poll() {
        coordinator.processNext();
    }
}
