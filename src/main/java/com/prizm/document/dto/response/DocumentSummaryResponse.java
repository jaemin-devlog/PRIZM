package com.prizm.document.dto.response;

import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentType;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.ingestion.entity.ProcessingJobStatus;
import java.time.Instant;

public record DocumentSummaryResponse(
        Long documentId,
        String title,
        DocumentType documentType,
        Long activeVersionId,
        Long latestVersionId,
        DocumentVersionStatus latestVersionStatus,
        String latestOriginalFileName,
        DocumentFileType latestFileType,
        ProcessingJobStatus latestProcessingStatus,
        String latestProcessingErrorCode,
        DocumentVersionStatus activeVersionStatus,
        int versionCount,
        Instant createdAt,
        Instant updatedAt) {
}
