package com.prizm.search.v3.indexing.worker;

import com.prizm.search.v3.indexing.service.SearchV3JobDispatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** current Search V3 계약이 없는 ACTIVE version을 한 건씩 shadow job으로 dispatch한다. */
@Component
@ConditionalOnProperty(prefix = "prizm.search-v3", name = "worker-enabled", havingValue = "true")
public class SearchV3JobDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(SearchV3JobDispatchScheduler.class);
    private final SearchV3JobDispatchService dispatchService;

    public SearchV3JobDispatchScheduler(SearchV3JobDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Scheduled(fixedDelayString = "${prizm.search-v3.dispatch-delay-ms:5000}")
    public void poll() {
        try {
            dispatchService.dispatchNext();
        }
        catch (RuntimeException failure) {
            log.warn("Search V3 shadow job dispatch failed; preserving the last committed state.", failure);
        }
    }
}
