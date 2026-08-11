package com.prizm.changelog.worker;

import com.prizm.changelog.service.ChangeLogDispatchTransaction;
import com.prizm.changelog.service.ChangeLogDispatchFailureClassifier;
import com.prizm.changelog.service.ChangeLogDispatchFailureException;
import com.prizm.changelog.service.ChangeLogDispatchFailureRecorder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduler는 transaction 밖에서 한 건의 ChangeLog dispatch만 요청한다. */
@Component
@ConditionalOnProperty(
        prefix = "prizm.change-log.scheduler",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ChangeLogDispatchScheduler {

    private static final Logger log = LoggerFactory.getLogger(ChangeLogDispatchScheduler.class);

    private final ChangeLogDispatchTransaction dispatchTransaction;
    private final ChangeLogDispatchFailureClassifier failureClassifier;
    private final ChangeLogDispatchFailureRecorder failureRecorder;

    public ChangeLogDispatchScheduler(
            ChangeLogDispatchTransaction dispatchTransaction,
            ChangeLogDispatchFailureClassifier failureClassifier,
            ChangeLogDispatchFailureRecorder failureRecorder) {
        this.dispatchTransaction = dispatchTransaction;
        this.failureClassifier = failureClassifier;
        this.failureRecorder = failureRecorder;
    }

    @Scheduled(fixedDelayString = "${prizm.change-log.scheduler.poll-delay-ms:1000}")
    public void poll() {
        try {
            dispatchTransaction.dispatchNext();
        }
        catch (ChangeLogDispatchFailureException failure) {
            recordKnownDispatchFailure(failure);
        }
        catch (RuntimeException failure) {
            log.warn("ChangeLog dispatch failed before a ChangeLog ID was known; preserving its last committed state.",
                    failure);
        }
    }

    private void recordKnownDispatchFailure(ChangeLogDispatchFailureException failure) {
        try {
            failureRecorder.record(
                    failure.getChangeLogId(),
                    failureClassifier.classify(failure.getCause()),
                    failure.getCause());
        }
        catch (RuntimeException recorderFailure) {
            log.warn("ChangeLog dispatch failure could not be recorded; preserving its last committed state.",
                    recorderFailure);
        }
    }
}
