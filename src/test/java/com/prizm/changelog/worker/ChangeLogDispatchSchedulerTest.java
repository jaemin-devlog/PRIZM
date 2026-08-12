package com.prizm.changelog.worker;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prizm.changelog.service.ChangeLogDispatchFailureClassifier;
import com.prizm.changelog.service.ChangeLogDispatchFailureDisposition;
import com.prizm.changelog.service.ChangeLogDispatchFailureException;
import com.prizm.changelog.service.ChangeLogDispatchFailureRecorder;
import com.prizm.changelog.service.ChangeLogDispatchTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

@ExtendWith(MockitoExtension.class)
class ChangeLogDispatchSchedulerTest {

    @Mock ChangeLogDispatchTransaction dispatchTransaction;
    @Mock ChangeLogDispatchFailureClassifier failureClassifier;
    @Mock ChangeLogDispatchFailureRecorder failureRecorder;

    ChangeLogDispatchScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new ChangeLogDispatchScheduler(dispatchTransaction, failureClassifier, failureRecorder);
    }

    @Test
    void recordsKnownTransactionAFailuresOutsideTransactionA() {
        DataAccessResourceFailureException cause = new DataAccessResourceFailureException("temporary database failure");
        when(dispatchTransaction.dispatchNext()).thenThrow(new ChangeLogDispatchFailureException(10L, cause));
        when(failureClassifier.classify(cause)).thenReturn(ChangeLogDispatchFailureDisposition.RETRYABLE);

        scheduler.poll();

        verify(failureRecorder).record(10L, ChangeLogDispatchFailureDisposition.RETRYABLE, cause);
    }

    @Test
    void leavesLastCommittedStateWhenTheChangeLogIdIsUnknown() {
        when(dispatchTransaction.dispatchNext())
                .thenThrow(new DataAccessResourceFailureException("claim database failure"));

        assertThatCode(scheduler::poll).doesNotThrowAnyException();

        verifyNoInteractions(failureClassifier, failureRecorder);
    }

    @Test
    void leavesLastCommittedStateWhenTransactionBFails() {
        DataAccessResourceFailureException cause = new DataAccessResourceFailureException("temporary database failure");
        when(dispatchTransaction.dispatchNext()).thenThrow(new ChangeLogDispatchFailureException(10L, cause));
        when(failureClassifier.classify(cause)).thenReturn(ChangeLogDispatchFailureDisposition.RETRYABLE);
        doThrow(new DataAccessResourceFailureException("failure recorder database failure"))
                .when(failureRecorder)
                .record(any(), any(), any());

        assertThatCode(scheduler::poll).doesNotThrowAnyException();

        verify(failureRecorder).record(10L, ChangeLogDispatchFailureDisposition.RETRYABLE, cause);
        verify(failureClassifier).classify(cause);
    }
}
