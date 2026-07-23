package com.prizm.document.dto.response;

/** A generated preview that never exposes the stored original-file path. */
public record DocumentThumbnailResponse(byte[] pngBytes, String contentHash) {
}
