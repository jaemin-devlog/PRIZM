package com.prizm.document.dto.response;

import com.prizm.document.entity.DocumentFileType;

/** Owner-scoped original bytes and safe metadata needed for an inline response. */
public record DocumentOriginalResponse(
        byte[] bytes,
        String originalFileName,
        DocumentFileType fileType) {
}
