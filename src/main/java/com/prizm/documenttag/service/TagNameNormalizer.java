package com.prizm.documenttag.service;

import java.text.Normalizer;
import java.util.Locale;

public final class TagNameNormalizer {

    public static final int MAX_NAME_LENGTH = 100;

    private TagNameNormalizer() {
    }

    public static String displayName(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidDocumentTagException(DocumentTagErrorCode.INVALID_TAG_NAME, "tag name must not be blank");
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("(?U)\\s+", " ")
                .strip();
        if (normalized.isBlank()) {
            throw new InvalidDocumentTagException(DocumentTagErrorCode.INVALID_TAG_NAME, "tag name must not be blank");
        }
        if (normalized.length() > MAX_NAME_LENGTH) {
            throw new InvalidDocumentTagException(
                    DocumentTagErrorCode.INVALID_TAG_NAME,
                    "tag name must be at most 100 characters");
        }
        return normalized;
    }

    public static String normalizedName(String value) {
        String normalizedName = displayName(value).toLowerCase(Locale.ROOT);
        if (normalizedName.length() > MAX_NAME_LENGTH) {
            throw new InvalidDocumentTagException(
                    DocumentTagErrorCode.INVALID_TAG_NAME,
                    "normalized tag name must be at most 100 characters");
        }
        return normalizedName;
    }
}
