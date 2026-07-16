package com.prizm.cleanup.worker;

import com.prizm.cleanup.config.CleanupProperties;
import com.prizm.cleanup.service.FileCleanupJobRecoveryService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "prizm.cleanup",
        name = "worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class FileCleanupRecoveryScheduler {

    private final FileCleanupJobRecoveryService recoveryService;
    private final CleanupProperties properties;

    public FileCleanupRecoveryScheduler(FileCleanupJobRecoveryService recoveryService, CleanupProperties properties) {
        this.recoveryService = recoveryService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${prizm.cleanup.recovery-delay-ms:60000}")
    public void recoverExpiredJobs() {
        int recovered = 0;
        while (recovered < properties.getBatchSize() && recoveryService.recoverNext()) {
            recovered++;
        }
    }
}
