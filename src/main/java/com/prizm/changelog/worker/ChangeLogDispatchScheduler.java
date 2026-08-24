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

/**
 * ChangeLog 전달 트랜잭션과 실패 기록 트랜잭션을 스케줄러 경계에서 분리한다.
 *
 * <p>전달 예외를 트랜잭션 밖에서 받은 뒤에만 실패 기록을 시작해야 작업 생성과 ChangeLog 갱신이 먼저
 * 롤백된다. ChangeLog 식별자를 알 수 없거나 실패 기록도 커밋하지 못하면 직전 커밋 상태를 그대로 둔다.</p>
 */
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
