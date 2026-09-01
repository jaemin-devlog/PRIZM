package com.prizm.search.v3.indexing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.prizm.ingestion.config.IngestionProperties;
import com.prizm.ingestion.service.IndexingRetryPolicy;
import com.prizm.search.v3.indexing.exception.StaleSearchV3IndexingJobClaimException;
import com.prizm.search.v3.indexing.model.SearchV3IndexGenerationStatus;
import com.prizm.search.v3.indexing.model.SearchV3IndexingJobClaim;
import com.prizm.search.v3.indexing.repository.SearchV3IndexingJobRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchV3IndexingJobServiceTest {

    @Mock
    SearchV3IndexingJobRepository repository;

    @Mock
    IndexingRetryPolicy retryPolicy;

    SearchV3IndexingJobService service;
    SearchV3IndexingJobClaim claim;

    @BeforeEach
    void setUp() {
        service = new SearchV3IndexingJobService(repository, new IngestionProperties(), retryPolicy);
        claim = new SearchV3IndexingJobClaim(
                11L,
                12L,
                13L,
                14L,
                15L,
                2L,
                4,
                Instant.parse("2026-09-02T00:10:00Z"));
    }

    @Test
    void reportsOnlyTheGenerationStatusBoundToTheCurrentClaim() {
        when(repository.findCurrentGenerationStatus(claim))
                .thenReturn(Optional.of(SearchV3IndexGenerationStatus.READY));

        assertThat(service.currentGenerationStatus(claim))
                .isEqualTo(SearchV3IndexGenerationStatus.READY);

        when(repository.findCurrentGenerationStatus(claim)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.currentGenerationStatus(claim))
                .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);
    }

    @Test
    void defersActivationWithAnIndependentDelayAndBoundedMessage() {
        Instant nextRetryAt = Instant.parse("2026-09-02T00:01:00Z");
        String oversizedReason = "x".repeat(2_100);
        when(repository.deferActivation(
                eq(claim),
                eq(Duration.ofMinutes(1)),
                argThat(message -> message.length() == 2_000)))
                .thenReturn(Optional.of(nextRetryAt));

        assertThat(service.deferActivation(claim, oversizedReason)).isEqualTo(nextRetryAt);

        verify(repository).deferActivation(
                eq(claim),
                eq(Duration.ofMinutes(1)),
                argThat(message -> message.length() == 2_000));
        verifyNoInteractions(retryPolicy);
    }

    @Test
    void rejectsActivationDeferralWhenTheClaimIsNoLongerCurrent() {
        when(repository.deferActivation(
                claim,
                Duration.ofMinutes(1),
                "Search V3 activation was deferred."))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deferActivation(claim, null))
                .isInstanceOf(StaleSearchV3IndexingJobClaimException.class);
        verifyNoInteractions(retryPolicy);
    }
}
