package com.prizm.documenttag.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TagNameNormalizerTest {

    @Test
    void normalizesUnicodeSpacingAndCaseWithoutAWhitelist() {
        assertThat(TagNameNormalizer.displayName("  Spring   BOOT  ")).isEqualTo("Spring BOOT");
        assertThat(TagNameNormalizer.normalizedName("  Spring   BOOT  ")).isEqualTo("spring boot");
        assertThat(TagNameNormalizer.normalizedName("Spring\u2028Boot")).isEqualTo("spring boot");
        assertThat(TagNameNormalizer.normalizedName("Spring\u2029Boot")).isEqualTo("spring boot");
        assertThat(TagNameNormalizer.normalizedName("Ｔａｕｒｉ")).isEqualTo("tauri");
    }

    @Test
    void rejectsBlankAndOversizedNames() {
        assertThatThrownBy(() -> TagNameNormalizer.normalizedName("  "))
                .isInstanceOf(InvalidDocumentTagException.class);
        assertThatThrownBy(() -> TagNameNormalizer.displayName("\u00A0"))
                .isInstanceOf(InvalidDocumentTagException.class)
                .extracting(exception -> ((InvalidDocumentTagException) exception).code())
                .isEqualTo(DocumentTagErrorCode.INVALID_TAG_NAME);
        assertThatThrownBy(() -> TagNameNormalizer.normalizedName("a".repeat(101)))
                .isInstanceOf(InvalidDocumentTagException.class);
    }

    @Test
    void rejectsWhenUnicodeLowercasingExpandsTheNormalizedNameBeyondTheDatabaseLimit() {
        assertThat(TagNameNormalizer.normalizedName("İ".repeat(50))).hasSize(100);

        assertThatThrownBy(() -> TagNameNormalizer.normalizedName("İ".repeat(51)))
                .isInstanceOf(InvalidDocumentTagException.class)
                .extracting(exception -> ((InvalidDocumentTagException) exception).code())
                .isEqualTo(DocumentTagErrorCode.INVALID_TAG_NAME);
    }
}
