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

/**
 * ChangeLog 전달 트랜잭션이 롤백된 뒤, 실패 상태만 새 트랜잭션에서 기록한다.
 *
 * <p>작업 생성과 전달 상태 갱신이 실패하면 먼저 둘 다 롤백해야 한다. 그다음 같은 ChangeLog를 다시 잠가
 * 재시도 또는 최종 실패를 커밋하므로, 실패 기록 때문에 원래 원자성이 깨지지 않는다. 이미 다른 Dispatcher가
 * 전달을 마친 행은 되돌리지 않는다.</p>
 *
 * <p>재시도 예산이 없거나 영구 실패이면 아직 {@code QUARANTINED}인 문서 버전만 실패 처리한다.
 * 문서의 활성 버전 포인터는 바꾸지 않아 기존 ACTIVE 버전이 계속 검색 대상에 남는다.</p>
 */
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

    /** 롤백된 전달 시도의 실패를 현재 커밋 상태와 재시도 예산에 맞춰 기록한다. */
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
