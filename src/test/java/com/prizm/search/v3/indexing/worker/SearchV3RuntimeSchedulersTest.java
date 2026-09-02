package com.prizm.search.v3.indexing.worker;

import static org.mockito.Mockito.verify;

import com.prizm.search.v3.indexing.service.SearchV3IndexingCoordinator;
import com.prizm.search.v3.indexing.service.SearchV3JobDispatchService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchV3RuntimeSchedulersTest {

    @Mock SearchV3JobDispatchService dispatchService;
    @Mock SearchV3IndexingCoordinator coordinator;

    @Test
    void dispatchAndPendingSchedulersDelegateOneBoundedOperation() {
        new SearchV3JobDispatchScheduler(dispatchService).poll();
        new SearchV3IndexingScheduler(coordinator).poll();

        verify(dispatchService).dispatchNext();
        verify(coordinator).processNext();
    }

    @Test
    void recoverySchedulerRunsOneBoundedRecoveryOperation() {
        new SearchV3RecoveryScheduler(coordinator).recoverExpiredJobs();

        verify(coordinator).recoverNext();
    }
}
