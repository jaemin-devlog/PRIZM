package com.prizm.changelog.service;

import com.prizm.changelog.entity.DocumentChangeLog;
import com.prizm.changelog.entity.ChangeLogEventType;
import com.prizm.changelog.repository.DocumentChangeLogRepository;
import com.prizm.ingestion.entity.ProcessingJob;
import com.prizm.ingestion.repository.ProcessingJobRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 준비된 ChangeLog 하나를 선점해 {@code INDEXING} 작업과 원자적으로 연결한다.
 *
 * <p>ChangeLog 선점, 작업의 멱등 생성, 소유자·버전 검증, {@code DISPATCHED} 전환을 한 트랜잭션에서
 * 처리한다. 중간에 실패하면 작업 생성과 상태 변경이 함께 롤백되며, 실패 상태는 이 경계가 끝난 뒤 별도
 * 트랜잭션에서 기록한다.</p>
 */
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
     * 가장 오래된 전달 가능 ChangeLog 한 건을 처리한다.
     *
     * <p>작업 생성은 문서 버전과 작업 유형의 고유 조건을 이용해 멱등하게 수행한다. 실패 정보는 이
     * 트랜잭션 안에 남기지 않으므로 예외가 발생하면 작업과 ChangeLog가 모두 직전 커밋 상태로 돌아간다.</p>
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
