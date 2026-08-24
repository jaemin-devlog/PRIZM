package com.prizm.documenttag.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 표시 이름과 중복 판정용 이름을 같은 Unicode 규칙으로 정규화한다.
 * 표시는 대소문자를 보존하고, 비교 키는 NFKC·공백 정리 뒤 {@link Locale#ROOT} 기준 소문자로 바꾼다.
 * 소문자 변환으로 길이가 늘어날 수 있어 DB 길이 제한은 비교 키에도 다시 적용한다.
 */
public final class TagNameNormalizer {

    public static final int MAX_NAME_LENGTH = 100;

    private TagNameNormalizer() {
    }

    /** 앞뒤·연속 Unicode 공백을 정리하되 사용자가 입력한 대소문자는 보존한다. */
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

    /** 검색과 소유자별 중복 판정에 쓰는, locale에 좌우되지 않는 비교 키를 만든다. */
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
