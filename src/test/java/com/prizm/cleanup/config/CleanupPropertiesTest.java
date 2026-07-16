package com.prizm.cleanup.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class CleanupPropertiesTest {

    @Test
    void rejectsNonPositiveWorkerSettings() {
        CleanupProperties properties = new CleanupProperties();
        properties.setBatchSize(0);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);

        properties = new CleanupProperties();
        properties.setLeaseDuration(Duration.ZERO);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalArgumentException.class);
    }
}
