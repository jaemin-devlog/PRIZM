package com.prizm.document.dto.response;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import java.time.Instant;

public record DocumentVersionResponse(
        Long versionId,
        int versionNo,
        String originalFileName,
        DocumentFileType fileType,
        DocumentVersionStatus status,
        ProcessingJobStatus processingStatus,
        String processingErrorCode,
        boolean retryScheduled,
        Instant createdAt) {
}
