package com.prizm.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.exception.StaleProcessingJobClaimException;
import com.prizm.ingestion.repository.ProcessingJobClaimRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessingJobLeaseServiceTest {

    @Mock
    ProcessingJobClaimRepository claimRepository;

    ProcessingJobLeaseService leaseService;
    IngestionProperties properties;
    ClaimedProcessingJob claimedJob;

    @BeforeEach
    void setUp() {
        properties = new IngestionProperties();
        properties.setLeaseDuration(Duration.ofSeconds(30));
        leaseService = new ProcessingJobLeaseService(claimRepository, properties);
        claimedJob = new ClaimedProcessingJob(15L, 25L, 35L, 5L, Instant.parse("2026-07-14T00:00:00Z"));
    }

    @Test
    void renewsOnlyTheCurrentClaimWithoutChangingItsClaimVersion() {
        Instant renewedAt = Instant.parse("2026-07-14T00:00:30Z");
        when(claimRepository.renewLease(
                        claimedJob.processingJobId(), claimedJob.claimVersion(), properties.getLeaseDuration()))
                .thenReturn(Optional.of(renewedAt));

        Instant result = leaseService.renew(claimedJob);

        assertThat(result).isEqualTo(renewedAt);
        verify(claimRepository).renewLease(
                claimedJob.processingJobId(), claimedJob.claimVersion(), properties.getLeaseDuration());
    }

    @Test
    void treatsZeroUpdatedRowsAsAStaleClaim() {
        when(claimRepository.renewLease(
                        claimedJob.processingJobId(), claimedJob.claimVersion(), properties.getLeaseDuration()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> leaseService.renew(claimedJob))
                .isInstanceOf(StaleProcessingJobClaimException.class);
    }
}
