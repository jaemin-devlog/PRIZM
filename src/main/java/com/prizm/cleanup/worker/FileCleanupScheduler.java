package com.prizm.cleanup.worker;

import com.prizm.cleanup.config.CleanupProperties;
import com.prizm.cleanup.service.FileCleanupCoordinator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 실행 가능한 cleanup 작업을 주기적으로 설정된 batch 크기만큼 처리한다. */
@Component
@ConditionalOnProperty(
        prefix = "prizm.cleanup",
        name = "worker-enabled",
        havingValue = "true",
        matchIfMissing = true)
public class FileCleanupScheduler {

    private final FileCleanupCoordinator coordinator;
    private final CleanupProperties properties;

    public FileCleanupScheduler(FileCleanupCoordinator coordinator, CleanupProperties properties) {
        this.coordinator = coordinator;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${prizm.cleanup.poll-delay-ms:5000}")
    public void poll() {
        coordinator.processBatch(properties.getBatchSize());
    }
}
