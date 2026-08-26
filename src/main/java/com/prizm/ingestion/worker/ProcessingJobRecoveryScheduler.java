package com.prizm.ingestion.worker;

import com.prizm.ingestion.service.ProcessingJobRecoveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 중단되거나 응답하지 않는 Worker의 임대 만료 작업을 짧은 트랜잭션으로 복구한다. */
@Component
@ConditionalOnProperty(
        prefix = "prizm.ingestion",
        name = "worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class ProcessingJobRecoveryScheduler {

    private final ProcessingJobRecoveryService recoveryService;

    public ProcessingJobRecoveryScheduler(ProcessingJobRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @Scheduled(fixedDelayString = "${prizm.ingestion.recovery-delay-ms:60000}")
    public void recoverExpiredJobs() {
        while (recoveryService.recoverNext()) {
            // 한 건씩 짧은 트랜잭션으로 복구하고 현재 만료 작업을 모두 소진한다.
        }
    }
}
