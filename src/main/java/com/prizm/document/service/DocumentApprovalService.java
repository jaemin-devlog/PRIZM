package com.prizm.document.service;

import com.prizm.document.dto.response.DocumentApprovalResponse;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.exception.DocumentVersionNotFoundException;
import com.prizm.document.repository.DocumentVersionRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.entity.ProcessingJobType;
import com.prizm.ingestion.exception.DuplicateProcessingJobException;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 격리된 문서 버전을 승인하고 하나의 색인 작업을 원자적으로 생성한다. */
@Service
public class DocumentApprovalService {

    private final DocumentVersionRepository documentVersionRepository;
    private final ProcessingJobRepository processingJobRepository;

    public DocumentApprovalService(
            DocumentVersionRepository documentVersionRepository,
            ProcessingJobRepository processingJobRepository) {
        this.documentVersionRepository = documentVersionRepository;
        this.processingJobRepository = processingJobRepository;
    }

    @Transactional
    public DocumentApprovalResponse approve(Long versionId) {
        DocumentVersion version = documentVersionRepository.findByIdForUpdate(versionId)
                .orElseThrow(() -> new DocumentVersionNotFoundException(versionId));
        if (processingJobRepository.existsByDocumentVersionIdAndJobType(versionId, ProcessingJobType.INDEXING)) {
            throw new DuplicateProcessingJobException(versionId);
        }

        version.approve();
        ProcessingJob job;
        try {
            job = processingJobRepository.saveAndFlush(ProcessingJob.pendingIndexing(versionId));
        }
        catch (DataIntegrityViolationException exception) {
            // 애플리케이션 검사와 UNIQUE 제약을 함께 사용해 동시 승인도 하나만 성공시킨다.
            throw new DuplicateProcessingJobException(versionId, exception);
        }
        return new DocumentApprovalResponse(version.getId(), version.getStatus(), job.getId(), job.getStatus());
    }
}
