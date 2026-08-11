package com.prizm.changelog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.prizm.changelog.entity.ChangeLogDispatchStatus;
import com.prizm.changelog.entity.DocumentChangeLog;
import com.prizm.changelog.repository.DocumentChangeLogRepository;
import com.prizm.document.entity.DocumentFileType;
import com.prizm.document.entity.DocumentVersion;
import com.prizm.document.entity.DocumentVersionStatus;
import com.prizm.document.repository.DocumentVersionRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ChangeLogDispatchFailureRecorderTest {

    @Mock DocumentChangeLogRepository documentChangeLogRepository;
    @Mock DocumentVersionRepository documentVersionRepository;

    ChangeLogDispatchFailureRecorder failureRecorder;

    @BeforeEach
    void setUp() {
        failureRecorder = new ChangeLogDispatchFailureRecorder(
                documentChangeLogRepository,
                documentVersionRepository,
                new ChangeLogDispatchRetryPolicy());
    }

    @Test
    void recordsThreeCommittedRetriesWithOneFiveAndFifteenMinuteBackoffThenFails() {
        DocumentChangeLog changeLog = changeLog(10L, 7L, 22L);
        DocumentVersion version = version(22L, 7L);
        when(documentChangeLogRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(changeLog));
        when(documentVersionRepository.findByIdAndOwnerUserIdForUpdate(22L, 7L)).thenReturn(Optional.of(version));

        Instant firstBefore = Instant.now();
        failureRecorder.record(10L, ChangeLogDispatchFailureDisposition.RETRYABLE,
                new DataAccessResourceFailureException("temporary database failure"));
        assertThat(changeLog.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.RETRY_WAIT);
        assertThat(changeLog.getRetryCount()).isEqualTo(1);
        assertThat(Duration.between(firstBefore, changeLog.getNextRetryAt())).isBetween(
                Duration.ofSeconds(59), Duration.ofSeconds(61));

        Instant secondBefore = Instant.now();
        failureRecorder.record(10L, ChangeLogDispatchFailureDisposition.RETRYABLE,
                new DataAccessResourceFailureException("temporary database failure"));
        assertThat(changeLog.getRetryCount()).isEqualTo(2);
        assertThat(Duration.between(secondBefore, changeLog.getNextRetryAt())).isBetween(
                Duration.ofMinutes(4).plusSeconds(59), Duration.ofMinutes(5).plusSeconds(1));

        Instant thirdBefore = Instant.now();
        failureRecorder.record(10L, ChangeLogDispatchFailureDisposition.RETRYABLE,
                new DataAccessResourceFailureException("temporary database failure"));
        assertThat(changeLog.getRetryCount()).isEqualTo(3);
        assertThat(Duration.between(thirdBefore, changeLog.getNextRetryAt())).isBetween(
                Duration.ofMinutes(14).plusSeconds(59), Duration.ofMinutes(15).plusSeconds(1));

        failureRecorder.record(10L, ChangeLogDispatchFailureDisposition.RETRYABLE,
                new DataAccessResourceFailureException("temporary database failure"));

        assertThat(changeLog.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.FAILED);
        assertThat(changeLog.getRetryCount()).isEqualTo(3);
        assertThat(changeLog.getNextRetryAt()).isNull();
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.FAILED);
    }

    @Test
    void recordsPermanentFailureWithoutConsumingRetryBudget() {
        DocumentChangeLog changeLog = changeLog(10L, 7L, 22L);
        DocumentVersion version = version(22L, 7L);
        when(documentChangeLogRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(changeLog));
        when(documentVersionRepository.findByIdAndOwnerUserIdForUpdate(22L, 7L)).thenReturn(Optional.of(version));

        failureRecorder.record(10L, ChangeLogDispatchFailureDisposition.PERMANENT,
                new IllegalStateException("owner/version mismatch"));

        assertThat(changeLog.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.FAILED);
        assertThat(changeLog.getRetryCount()).isZero();
        assertThat(changeLog.getFailedAt()).isNotNull();
        assertThat(version.getStatus()).isEqualTo(DocumentVersionStatus.FAILED);
    }

    @Test
    void doesNotRegressADispatchedChangeLog() {
        DocumentChangeLog changeLog = changeLog(10L, 7L, 22L);
        changeLog.markDispatched(30L, Instant.now());
        when(documentChangeLogRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(changeLog));

        failureRecorder.record(10L, ChangeLogDispatchFailureDisposition.PERMANENT,
                new IllegalStateException("stale failure"));

        assertThat(changeLog.getDispatchStatus()).isEqualTo(ChangeLogDispatchStatus.DISPATCHED);
        verify(documentVersionRepository, never()).findByIdAndOwnerUserIdForUpdate(any(), any());
    }

    @Test
    void boundsTheStoredErrorMessage() {
        DocumentChangeLog changeLog = changeLog(10L, 7L, 22L);
        DocumentVersion version = version(22L, 7L);
        when(documentChangeLogRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(changeLog));
        when(documentVersionRepository.findByIdAndOwnerUserIdForUpdate(22L, 7L)).thenReturn(Optional.of(version));

        failureRecorder.record(10L, ChangeLogDispatchFailureDisposition.PERMANENT,
                new IllegalArgumentException("x".repeat(1200) + "\nunsafe line"));

        assertThat(changeLog.getLastErrorMessage()).hasSize(1000).doesNotContain("\n");
    }

    @Test
    void classifiesTransientDatabaseFailuresAsRetryableAndContractFailuresAsPermanent() {
        ChangeLogDispatchFailureClassifier classifier = new ChangeLogDispatchFailureClassifier();

        assertThat(classifier.classify(new DataAccessResourceFailureException("temporary")))
                .isEqualTo(ChangeLogDispatchFailureDisposition.RETRYABLE);
        assertThat(classifier.classify(new IllegalStateException("owner/version mismatch")))
                .isEqualTo(ChangeLogDispatchFailureDisposition.PERMANENT);
    }

    private DocumentChangeLog changeLog(long id, long ownerUserId, long documentVersionId) {
        DocumentChangeLog changeLog = DocumentChangeLog.pendingDocumentVersionCreated(ownerUserId, documentVersionId);
        ReflectionTestUtils.setField(changeLog, "id", id);
        return changeLog;
    }

    private DocumentVersion version(long id, long ownerUserId) {
        DocumentVersion version = DocumentVersion.quarantined(
                ownerUserId, 3L, "resume.txt", DocumentFileType.TXT, "a".repeat(64));
        ReflectionTestUtils.setField(version, "id", id);
        return version;
    }
}
