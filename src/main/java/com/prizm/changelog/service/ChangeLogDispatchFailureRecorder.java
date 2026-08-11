package com.prizm.changelog.service;

import com.prizm.changelog.entity.ChangeLogDispatchStatus;
import com.prizm.changelog.entity.DocumentChangeLog;
import com.prizm.changelog.repository.DocumentChangeLogRepository;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.repository.DocumentVersionRepository;
import java.time.Duration;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Transaction A가 rollback된 뒤 실패 기록만 별도 Transaction B에서 확정한다. */
@Service
public class ChangeLogDispatchFailureRecorder {

    private static final int MAX_ERROR_MESSAGE_LENGTH = 1000;

    private final DocumentChangeLogRepository documentChangeLogRepository;
    private final DocumentVersionRepository documentVersionRepository;
    private final ChangeLogDispatchRetryPolicy retryPolicy;

    public ChangeLogDispatchFailureRecorder(
            DocumentChangeLogRepository documentChangeLogRepository,
            DocumentVersionRepository documentVersionRepository,
            ChangeLogDispatchRetryPolicy retryPolicy) {
        this.documentChangeLogRepository = documentChangeLogRepository;
        this.documentVersionRepository = documentVersionRepository;
        this.retryPolicy = retryPolicy;
    }

    @Transactional
    public void record(
            Long changeLogId,
            ChangeLogDispatchFailureDisposition disposition,
            Throwable failure) {
        DocumentChangeLog changeLog = documentChangeLogRepository.findByIdForUpdate(changeLogId).orElse(null);
        if (changeLog == null
                || changeLog.getDispatchStatus() == ChangeLogDispatchStatus.DISPATCHED
                || changeLog.getDispatchStatus() == ChangeLogDispatchStatus.FAILED) {
            return;
        }

        String errorMessage = safeErrorMessage(failure);
        Duration retryDelay = disposition == ChangeLogDispatchFailureDisposition.RETRYABLE
                ? retryPolicy.nextDelay(changeLog.getRetryCount()).orElse(null)
                : null;
        if (retryDelay != null) {
            changeLog.scheduleDispatchRetry(Instant.now().plus(retryDelay), errorMessage);
            return;
        }

        changeLog.markDispatchFailed(Instant.now(), errorMessage);
        DocumentVersion version = documentVersionRepository.findByIdAndOwnerUserIdForUpdate(
                        changeLog.getDocumentVersionId(), changeLog.getOwnerUserId())
                .orElseThrow(() -> new IllegalStateException(
                        "ChangeLog %s did not have its owner-scoped document version."
                                .formatted(changeLog.getId())));
        if (version.getStatus() == DocumentVersionStatus.QUARANTINED) {
            version.failDispatch();
        }
    }

    private String safeErrorMessage(Throwable failure) {
        String type = failure == null ? "DispatchFailure" : failure.getClass().getSimpleName();
        String detail = failure == null || failure.getMessage() == null ? "" : ": " + failure.getMessage();
        String message = (type + detail).replaceAll("[\\r\\n\\t]+", " ");
        return message.length() <= MAX_ERROR_MESSAGE_LENGTH
                ? message
                : message.substring(0, MAX_ERROR_MESSAGE_LENGTH);
    }
}
