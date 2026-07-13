package com.prizm.document.dto.response;

import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.ingestion.entity.ProcessingJobStatus;

public record DocumentApprovalResponse(
        Long versionId,
        DocumentVersionStatus status,
        Long jobId,
        ProcessingJobStatus jobStatus) {
}
