package com.prizm.search.v3.indexing.worker;

import com.prizm.search.v3.indexing.service.SearchV3IndexingCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 한 번의 poll에서 만료된 Search V3 job 하나를 exact token으로 reclaim해 처리한다. */
@Component
@ConditionalOnProperty(prefix = "prizm.search-v3", name = "worker-enabled", havingValue = "true")
public class SearchV3RecoveryScheduler {

    private final SearchV3IndexingCoordinator coordinator;

    public SearchV3RecoveryScheduler(SearchV3IndexingCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Scheduled(fixedDelayString = "${prizm.search-v3.recovery-delay-ms:60000}")
    public void recoverExpiredJobs() {
        coordinator.recoverNext();
    }
}
