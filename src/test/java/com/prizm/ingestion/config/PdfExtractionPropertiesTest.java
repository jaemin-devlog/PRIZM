package com.prizm.ingestion.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PdfExtractionPropertiesTest {

    @Test
    void rejectsNonPositivePageLimit() {
        PdfExtractionProperties properties = new PdfExtractionProperties();
        properties.setMaxPages(0);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxPages must be at least 1");
    }

    @Test
    void rejectsNonPositiveExtractedCharacterLimit() {
        PdfExtractionProperties properties = new PdfExtractionProperties();
        properties.setMaxExtractedCharacters(-1);

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("maxExtractedCharacters must be at least 1");
    }
}
