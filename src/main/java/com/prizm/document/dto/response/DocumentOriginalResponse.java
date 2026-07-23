package com.prizm.document.dto.response;

/** Owner-scoped PDF bytes and the safe display name needed for an inline response. */
public record DocumentOriginalResponse(byte[] pdfBytes, String originalFileName) {
}
