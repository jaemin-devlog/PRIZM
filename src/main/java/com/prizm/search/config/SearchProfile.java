package com.prizm.search.config;

import java.util.Arrays;

/** Selectable, versioned product search profiles. */
public enum SearchProfile {
    LEGACY_DENSE_V1("legacy-dense-v1"),
    SOURCE_DEDUP_EVIDENCE_SIGNALS_V1("source-dedup-evidence-signals-v1");

    private final String propertyValue;

    SearchProfile(String propertyValue) {
        this.propertyValue = propertyValue;
    }

    public String propertyValue() {
        return propertyValue;
    }

    public static SearchProfile fromProperty(String value) {
        return Arrays.stream(values())
                .filter(profile -> profile.propertyValue.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unsupported search profile: " + value));
    }
}
