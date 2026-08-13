package com.prizm.document.dto.response;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import com.prizm.ingestion.entity.ProcessingProgressStage;
import java.time.Instant;

public record DocumentVersionResponse(
        Long versionId,
        int versionNo,
        String originalFileName,
        DocumentFileType fileType,
        DocumentVersionStatus status,
        ProcessingJobStatus processingStatus,
        ProcessingProgressStage processingStage,
        Integer completedChunks,
        Integer totalChunks,
        Integer progressPercent,
        String processingErrorCode,
        boolean retryScheduled,
        int retryCount,
        int maxRetries,
        Instant nextRetryAt,
        Instant createdAt) {
}
