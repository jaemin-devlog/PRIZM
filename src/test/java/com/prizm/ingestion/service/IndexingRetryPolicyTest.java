package com.prizm.ingestion.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class IndexingRetryPolicyTest {

    @Test
    void usesOneFiveAndFifteenMinuteRetryIntervals() {
        IndexingRetryPolicy policy = new IndexingRetryPolicy();
        Instant now = Instant.parse("2026-07-13T00:00:00Z");

        assertThat(policy.nextRetryAt(0, now)).isEqualTo(now.plusSeconds(60));
        assertThat(policy.nextRetryAt(1, now)).isEqualTo(now.plusSeconds(300));
        assertThat(policy.nextRetryAt(2, now)).isEqualTo(now.plusSeconds(900));
        assertThat(policy.canRetry(2)).isTrue();
        assertThat(policy.canRetry(3)).isFalse();
    }
}
