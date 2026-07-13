package com.prizm.document;

import java.time.Instant;

public record DocumentVersionResponse(
        Long versionId,
        int versionNo,
        String originalFileName,
        String storedFilePath,
        DocumentFileType fileType,
        DocumentVersionStatus status,
        Instant createdAt) {
}
