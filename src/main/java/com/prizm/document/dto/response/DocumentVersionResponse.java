package com.prizm.document.dto.response;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentVersionStatus;
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
