package com.prizm.changelog.service;

import com.prizm.changelog.entity.DocumentChangeLog;
import com.prizm.changelog.entity.ChangeLogEventType;
import com.prizm.changelog.repository.DocumentChangeLogRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** PENDING 또는 준비된 RETRY_WAIT ChangeLog 하나를 INDEXING Job으로 원자적으로 전달한다. */
@Service
public class ChangeLogDispatchTransaction {

    private final DocumentChangeLogRepository documentChangeLogRepository;
    private final ProcessingJobRepository processingJobRepository;

    public ChangeLogDispatchTransaction(
            DocumentChangeLogRepository documentChangeLogRepository,
            ProcessingJobRepository processingJobRepository) {
        this.documentChangeLogRepository = documentChangeLogRepository;
        this.processingJobRepository = processingJobRepository;
    }

    /**
     * Transaction A의 전체 경계다. 실패하면 Job upsert와 ChangeLog 갱신이 함께 rollback된다.
     * P4 전까지 실패 상태나 retry 정보를 기록하지 않는다.
     */
    @Transactional
    public boolean dispatchNext() {
        DocumentChangeLog changeLog = documentChangeLogRepository.claimNextDispatchable(Instant.now())
                .orElse(null);
        if (changeLog == null) {
            return false;
        }
        try {
            return dispatch(changeLog);
        }
        catch (RuntimeException exception) {
            throw new ChangeLogDispatchFailureException(changeLog.getId(), exception);
        }
    }

    private boolean dispatch(DocumentChangeLog changeLog) {
        if (changeLog.getEventType() != ChangeLogEventType.DOCUMENT_VERSION_CREATED) {
            throw new IllegalStateException("Unsupported ChangeLog event type: " + changeLog.getEventType());
        }
        processingJobRepository.insertIndexingIfAbsent(
                changeLog.getOwnerUserId(), changeLog.getDocumentVersionId());
        ProcessingJob job = processingJobRepository.findByDocumentVersionId(changeLog.getDocumentVersionId())
                .orElseThrow(() -> new IllegalStateException(
                        "INDEXING job was not available after atomic insert for version %s."
                                .formatted(changeLog.getDocumentVersionId())));
        requireMatchingOwnerAndVersion(changeLog, job);
        changeLog.markDispatched(job.getId(), Instant.now());
        documentChangeLogRepository.flush();
        return true;
    }

    private void requireMatchingOwnerAndVersion(DocumentChangeLog changeLog, ProcessingJob job) {
        if (!changeLog.getOwnerUserId().equals(job.getOwnerUserId())
                || !changeLog.getDocumentVersionId().equals(job.getDocumentVersionId())) {
            throw new IllegalStateException(
                    "INDEXING job owner or version did not match ChangeLog %s.".formatted(changeLog.getId()));
        }
    }
}
